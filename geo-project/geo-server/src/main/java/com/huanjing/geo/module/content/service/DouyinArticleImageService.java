package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.common.storage.ObjectStorageService;
import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.entity.*;
import com.huanjing.geo.module.content.mapper.*;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.service.BrandMaterialPublicUrlService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class DouyinArticleImageService {
    private final DouyinArticleImageBatchMapper batches;
    private final ArticleDraftMapper articles;
    private final ArticleDraftVersionMapper versions;
    private final BrandMapper brands;
    private final ProjectMapper projects;
    private final BrandMaterialMapper materials;
    private final AiPlatformConfigMapper models;
    private final SelfMediaPublishScheduleMapper schedules;
    private final ArticleBodyProvider bodies;
    private final BrandMaterialPublicUrlService publicUrls;
    private final ObjectStorageService storage;
    private final CurrentUserService users;
    private final BrandAccessService brandAccess;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final LlmCallFacade llm;
    private final ArkImageClient imageClient;

    @Value("${geo.douyin-images.auto-enabled:false}")
    private boolean autoEnabled;

    public record GenerateRequest(String requestKey, Integer imageCount, String styleCode, Long modelConfigId) { }
    public record BatchView(Long id, Long articleId, int sourceVersionNo, Integer appliedVersionNo,
                            String status, int imageCount, String styleCode, List<String> imageUrls,
                            JsonNode plan, String errorMessage, LocalDateTime createdAt) { }

    public List<BatchView> list(Long articleId) {
        authorize(articleId, false);
        return batches.selectList(new LambdaQueryWrapper<DouyinArticleImageBatch>()
                .eq(DouyinArticleImageBatch::getArticleId, articleId)
                .orderByDesc(DouyinArticleImageBatch::getId).last("LIMIT 20")).stream().map(this::view).toList();
    }

    public BatchView generate(Long articleId, GenerateRequest request) {
        Long operatorId = authorize(articleId, true);
        if (request == null || request.requestKey() == null || !request.requestKey().matches("[A-Za-z0-9_-]{8,100}")) {
            throw new BizException(400, "请提供有效的生图请求标识");
        }
        return view(create(articleId, request, operatorId, false));
    }

    public BatchView retry(Long articleId, Long batchId) {
        authorize(articleId, true);
        DouyinArticleImageBatch batch = requireBatch(articleId, batchId);
        requireCurrentSource(batch, requireArticle(articleId));
        if (!"failed".equals(batch.getStatus())) throw new BizException(409, "只有失败的生图任务可以重试");
        requireModel(batch.getModelConfigId());
        batches.update(null, new LambdaUpdateWrapper<DouyinArticleImageBatch>()
                .eq(DouyinArticleImageBatch::getId, batchId).eq(DouyinArticleImageBatch::getStatus, "failed")
                .set(DouyinArticleImageBatch::getStatus, "queued").set(DouyinArticleImageBatch::getErrorMessage, null)
                .set(DouyinArticleImageBatch::getUpdatedAt, LocalDateTime.now()));
        return view(requireBatch(articleId, batchId));
    }

    public BatchView apply(Long articleId, Long batchId) {
        Long operatorId = authorize(articleId, true);
        applyBatch(requireBatch(articleId, batchId), operatorId);
        return view(requireBatch(articleId, batchId));
    }

    public boolean automaticEnabledFor(String group, String channel) {
        return autoEnabled && "self_media".equals(group)
                && ("douyin".equals(channel) || "douyin_image_text".equals(channel));
    }

    /** Enqueue in the same transaction as the newly generated article. */
    public void enqueueForGeneratedArticle(Long articleId, Long operatorId) {
        if (!autoEnabled) return;
        ArticleDraft article = requireArticle(articleId);
        if (!isDouyin(article)) return;
        create(articleId, new GenerateRequest("auto-v" + article.getCurrentVersionNo(), 4, "auto", null), operatorId, true);
    }

    /** Monthly plans wait for the same image batch before creating a publish task. */
    public boolean prepareAutomatic(Long articleId, Long operatorId) {
        if (!autoEnabled) return true;
        ArticleDraft article = requireArticle(articleId);
        if (!isDouyin(article)) return true;
        if (article.getDouyinImageBatchId() != null) {
            DouyinArticleImageBatch applied = requireBatch(articleId, article.getDouyinImageBatchId());
            if (Objects.equals(applied.getAppliedVersionNo(), article.getCurrentVersionNo())) return true;
        }
        GenerateRequest request = new GenerateRequest("auto-v" + article.getCurrentVersionNo(), 4, "auto", null);
        DouyinArticleImageBatch batch = create(articleId, request, operatorId, true);
        if ("failed".equals(batch.getStatus())) throw new BizException(400, "抖音图片生成失败，请在文章详情中重试：" + batch.getErrorMessage());
        if (!"succeeded".equals(batch.getStatus()) && !"applied".equals(batch.getStatus())) return false;
        applyBatch(batch, operatorId);
        return true;
    }

    private DouyinArticleImageBatch create(Long articleId, GenerateRequest request, Long operatorId, boolean automatic) {
        return Objects.requireNonNull(transactions.execute(tx -> {
            ArticleDraft article = articles.selectOne(new LambdaQueryWrapper<ArticleDraft>()
                    .eq(ArticleDraft::getId, articleId).last("FOR UPDATE"));
            if (article == null || !isDouyin(article) || "deleted".equals(article.getStatus())) {
                throw new BizException(400, "仅支持有效的抖音图文文章");
            }
            DouyinArticleImageBatch existing = batches.selectOne(new LambdaQueryWrapper<DouyinArticleImageBatch>()
                    .eq(DouyinArticleImageBatch::getArticleId, articleId).eq(DouyinArticleImageBatch::getRequestKey, request.requestKey()));
            if (existing != null) return existing;
            int count = request.imageCount() == null ? 4 : request.imageCount();
            if (count < 1 || count > 6) throw new BizException(400, "图片数量必须为1至6张");
            AiPlatformConfig model = requireModel(request.modelConfigId());
            ArticleDraftVersion version = currentVersion(article);
            Long brandId = article.getSubjectBrandId();
            if (brandId == null) {
                Project project = projects.selectById(article.getSubjectProjectId() == null ? article.getProjectId() : article.getSubjectProjectId());
                brandId = project == null ? null : project.getBrandId();
            }
            Brand brand = brandId == null ? null : brands.selectById(brandId);
            if (brand == null || brand.getDeletedAt() != null) throw new BizException(400, "文章主体品牌不存在");
            String markdown = cleanImages(bodies.getArticleBody(version).markdown());
            if (markdown.isBlank() || markdown.length() > 60_000) throw new BizException(400, "文章正文为空或超过生图输入长度");
            DouyinArticleImageBatch batch = new DouyinArticleImageBatch();
            batch.setArticleId(articleId);
            batch.setSourceVersionNo(article.getCurrentVersionNo());
            batch.setSubjectBrandId(brandId);
            batch.setModelConfigId(model.getId());
            batch.setRequestKey(request.requestKey());
            batch.setStatus("queued");
            batch.setImageCount(count);
            batch.setStyleCode(selectStyle(brandId, request.styleCode()));
            batch.setSourceTitle(article.getTitle());
            batch.setSourceMarkdown(markdown);
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("companyName", brand.getBrandName());
            facts.put("industry", brand.getIndustry());
            facts.put("business", brand.getBusinessIntro());
            facts.put("description", brand.getDescription());
            facts.put("qualifications", brand.getBrandQualificationDescription());
            facts.put("cases", brand.getBrandCaseDescription());
            facts.put("revision", DouyinImagePrompts.REVISION);
            batch.setBrandFactsJson(writeJson(facts));
            batch.setMaterialIdsJson("[]");
            batch.setAutomatic(automatic);
            batch.setCreatedBy(operatorId);
            batch.setCreatedAt(LocalDateTime.now());
            batch.setUpdatedAt(LocalDateTime.now());
            batches.insert(batch);
            return batch;
        }));
    }

    String selectStyle(Long brandId, String requested) {
        if (requested != null && !requested.equals("auto")) {
            if (!DouyinImagePrompts.STYLES.contains(requested)) throw new BizException(400, "图片风格无效");
            return requested;
        }
        List<String> recent = batches.selectList(new LambdaQueryWrapper<DouyinArticleImageBatch>()
                .eq(DouyinArticleImageBatch::getSubjectBrandId, brandId)
                .orderByDesc(DouyinArticleImageBatch::getId).last("LIMIT 3"))
                .stream().map(DouyinArticleImageBatch::getStyleCode).toList();
        List<String> available = DouyinImagePrompts.STYLES.stream().filter(style -> !recent.contains(style)).toList();
        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }

    public synchronized void processNext() {
        // An abandoned network call is not automatically replayed: retry is an explicit action.
        batches.update(null, new LambdaUpdateWrapper<DouyinArticleImageBatch>()
                .eq(DouyinArticleImageBatch::getStatus, "running")
                .lt(DouyinArticleImageBatch::getUpdatedAt, LocalDateTime.now().minusMinutes(20))
                .set(DouyinArticleImageBatch::getStatus, "failed")
                .set(DouyinArticleImageBatch::getErrorMessage, "任务中断或超时，可重试未完成图片"));
        DouyinArticleImageBatch batch = batches.selectOne(new LambdaQueryWrapper<DouyinArticleImageBatch>()
                .eq(DouyinArticleImageBatch::getStatus, "queued").orderByAsc(DouyinArticleImageBatch::getId).last("LIMIT 1"));
        if (batch == null) return;
        String runToken = UUID.randomUUID().toString();
        if (batches.update(null, new LambdaUpdateWrapper<DouyinArticleImageBatch>()
                .eq(DouyinArticleImageBatch::getId, batch.getId()).eq(DouyinArticleImageBatch::getStatus, "queued")
                .set(DouyinArticleImageBatch::getStatus, "running")
                .set(DouyinArticleImageBatch::getRunToken, runToken)
                .set(DouyinArticleImageBatch::getUpdatedAt, LocalDateTime.now())) != 1) return;
        batch.setStatus("running");
        batch.setRunToken(runToken);
        try {
            requireCurrentSource(batch, requireArticle(batch.getArticleId()));
            AiPlatformConfig model = requireModel(batch.getModelConfigId());
            JsonNode plan;
            if (batch.getPlanJson() == null) {
                String response = llm.execute(LlmCallRequest.routed(new LlmRouteRequest(
                        "article", "你是中文图文编辑。严格根据材料规划图片，只输出指定JSON。",
                        DouyinImagePrompts.planning(batch), 0.7, 20_000, 300_000, 300_000,
                        0, null, true, 1, 0, List.of()))).invokeResult().responseText();
                plan = parsePlan(response, batch.getImageCount());
                batch.setPlanJson(writeJson(plan));
                saveProgress(batch);
            } else {
                plan = parsePlan(batch.getPlanJson(), batch.getImageCount());
            }
            List<Long> ids = new ArrayList<>(readIds(batch.getMaterialIdsJson()));
            for (int page = ids.size(); page < batch.getImageCount(); page++) {
                requireCurrentSource(batch, requireArticle(batch.getArticleId()));
                ArkImageClient.GeneratedImage image = imageClient.generate(model, DouyinImagePrompts.image(batch, plan, page));
                String objectKey = "brands/" + batch.getSubjectBrandId() + "/douyin-articles/" + batch.getArticleId()
                        + "/" + batch.getId() + "/" + (page + 1) + "." + image.extension();
                storage.putBytes(objectKey, image.bytes(), image.contentType());
                int pageNo = page + 1;
                transactions.executeWithoutResult(tx -> {
                    BrandMaterial material = new BrandMaterial();
                    material.setBrandId(batch.getSubjectBrandId());
                    material.setSourceArticleId(batch.getArticleId());
                    material.setCategory("brand_image");
                    material.setFileName("抖音图文-" + batch.getId() + "-" + pageNo + "." + image.extension());
                    material.setFileType(image.extension());
                    material.setObjectKey(objectKey);
                    // The signed URL requires the generated ID; file_url is NOT NULL in MySQL.
                    // Replace this placeholder below before committing the transaction.
                    material.setFileUrl("");
                    material.setFileSize((long) image.bytes().length);
                    material.setCreatedBy(batch.getCreatedBy());
                    material.setCreatedAt(LocalDateTime.now());
                    material.setUpdatedAt(LocalDateTime.now());
                    materials.insert(material);
                    material.setFileUrl(publicUrls.buildPublicStreamUrl(material));
                    materials.updateById(material);
                    ids.add(material.getId());
                    batch.setMaterialIdsJson(writeJson(ids));
                    saveProgress(batch);
                });
            }
            batch.setStatus("succeeded");
            saveProgress(batch);
            if (Boolean.TRUE.equals(batch.getAutomatic())) {
                try {
                    applyBatch(batch, batch.getCreatedBy());
                } catch (Exception ex) {
                    // Keep generated pages available; an application conflict must not repeat paid generation.
                    batches.update(null, new LambdaUpdateWrapper<DouyinArticleImageBatch>()
                            .eq(DouyinArticleImageBatch::getId, batch.getId())
                            .eq(DouyinArticleImageBatch::getStatus, "succeeded")
                            .set(DouyinArticleImageBatch::getErrorMessage,
                                    ex instanceof BizException ? ex.getMessage() : "图片已生成，自动应用失败，请在文章详情中应用这一组"));
                }
            }
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            batch.setStatus("failed");
            batch.setErrorMessage(ex instanceof BizException ? ex.getMessage() : "生图或存储失败，请检查模型、网络和对象存储配置后重试");
            batches.update(null, new LambdaUpdateWrapper<DouyinArticleImageBatch>()
                    .eq(DouyinArticleImageBatch::getId, batch.getId()).eq(DouyinArticleImageBatch::getStatus, "running")
                    .eq(DouyinArticleImageBatch::getRunToken, batch.getRunToken())
                    .set(DouyinArticleImageBatch::getStatus, "failed")
                    .set(DouyinArticleImageBatch::getErrorMessage, batch.getErrorMessage())
                    .set(DouyinArticleImageBatch::getUpdatedAt, LocalDateTime.now()));
            log.warn("Douyin image generation failed batchId={} type={}", batch.getId(), ex.getClass().getSimpleName());
        }
    }

    private void saveProgress(DouyinArticleImageBatch batch) {
        int changed = batches.update(null, new LambdaUpdateWrapper<DouyinArticleImageBatch>()
                .eq(DouyinArticleImageBatch::getId, batch.getId()).eq(DouyinArticleImageBatch::getStatus, "running")
                .eq(DouyinArticleImageBatch::getRunToken, batch.getRunToken())
                .set(DouyinArticleImageBatch::getStatus, batch.getStatus())
                .set(DouyinArticleImageBatch::getPlanJson, batch.getPlanJson())
                .set(DouyinArticleImageBatch::getMaterialIdsJson, batch.getMaterialIdsJson())
                .set(DouyinArticleImageBatch::getUpdatedAt, LocalDateTime.now()));
        if (changed != 1) throw new BizException(409, "生图任务状态已变化，停止当前处理");
    }

    private void applyBatch(DouyinArticleImageBatch batch, Long operatorId) {
        transactions.executeWithoutResult(tx -> {
            ArticleDraft article = articles.selectOne(new LambdaQueryWrapper<ArticleDraft>()
                    .eq(ArticleDraft::getId, batch.getArticleId()).last("FOR UPDATE"));
            DouyinArticleImageBatch current = requireBatch(batch.getArticleId(), batch.getId());
            if (Objects.equals(article.getDouyinImageBatchId(), current.getId())
                    && Objects.equals(article.getCurrentVersionNo(), current.getAppliedVersionNo())) return;
            requireCurrentSource(current, article);
            if (!"succeeded".equals(current.getStatus())) throw new BizException(409, "请等待整组图片生成成功再应用");
            if (List.of("distributed", "published", "distributing", "deleted").contains(article.getStatus())
                    || schedules.selectCount(new LambdaQueryWrapper<SelfMediaPublishSchedule>()
                    .eq(SelfMediaPublishSchedule::getArticleId, article.getId())
                    .in(SelfMediaPublishSchedule::getStatus, SelfMediaPublishScheduleConstants.DUPLICATE_PROTECTED_STATUSES)) > 0) {
                throw new BizException(409, "文章已有发布任务或已发布，不能替换其图片；测试结果仍可预览");
            }
            List<String> urls = urls(current);
            if (urls.size() != current.getImageCount()) throw new BizException(409, "生成图片不完整");
            ArticleDraftVersion source = currentVersion(article);
            ArticleDraftVersion next = new ArticleDraftVersion();
            next.setArticleId(article.getId());
            next.setVersionNo(article.getCurrentVersionNo() + 1);
            next.setTitle(current.getSourceTitle());
            next.setContentMarkdown(insertImages(current.getSourceMarkdown(), urls));
            next.setGeneratedBy("douyin_image");
            next.setPromptSnapshot(source.getPromptSnapshot());
            next.setInputSnapshot(source.getInputSnapshot());
            next.setModelId(source.getModelId());
            next.setModelPlatformCode(source.getModelPlatformCode());
            next.setCreatedBy(operatorId);
            versions.insert(next);
            int changed = articles.update(null, new LambdaUpdateWrapper<ArticleDraft>()
                    .eq(ArticleDraft::getId, article.getId()).eq(ArticleDraft::getCurrentVersionNo, current.getSourceVersionNo())
                    .set(ArticleDraft::getCurrentVersionNo, next.getVersionNo())
                    .set(ArticleDraft::getCoverImageUrl, urls.get(0)).set(ArticleDraft::getDouyinImageBatchId, current.getId()));
            if (changed != 1) throw new BizException(409, "文章版本已变化，请刷新后重试");
            batches.update(null, new LambdaUpdateWrapper<DouyinArticleImageBatch>()
                    .eq(DouyinArticleImageBatch::getId, current.getId()).set(DouyinArticleImageBatch::getStatus, "applied")
                    .set(DouyinArticleImageBatch::getAppliedVersionNo, next.getVersionNo())
                    .set(DouyinArticleImageBatch::getUpdatedAt, LocalDateTime.now()));
        });
    }

    static void requireCurrentSource(DouyinArticleImageBatch batch, ArticleDraft article) {
        if (article == null || !isDouyin(article) || "deleted".equals(article.getStatus())
                || !Objects.equals(batch.getSourceVersionNo(), article.getCurrentVersionNo())
                || (article.getSubjectBrandId() != null && !Objects.equals(batch.getSubjectBrandId(), article.getSubjectBrandId()))
                || !Objects.equals(batch.getSourceTitle(), article.getTitle())) {
            throw new BizException(409, "文章版本已变化，请根据最新文章重新生成图片");
        }
    }

    static String cleanImages(String markdown) {
        return (markdown == null ? "" : markdown).replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "")
                .replaceAll("(?is)<img\\b[^>]*>", "").replaceAll("\\n{3,}", "\n\n").trim();
    }

    static String insertImages(String markdown, List<String> urls) {
        List<String> blocks = new ArrayList<>(Arrays.asList(cleanImages(markdown).split("\\n\\s*\\n")));
        int originalBlockCount = blocks.size();
        // Reverse insertion preserves both page order and the article's original paragraph order.
        for (int i = urls.size() - 1; i >= 0; i--) {
            int position = (int) Math.ceil((i + 1D) * originalBlockCount / (urls.size() + 1D));
            blocks.add(Math.min(position, blocks.size()), "![图文第" + (i + 1) + "页](" + urls.get(i) + ")");
        }
        return String.join("\n\n", blocks);
    }

    JsonNode parsePlan(String value, int count) throws Exception {
        String raw = value.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        JsonNode plan = json.readTree(raw);
        if (!plan.path("pages").isArray() || plan.path("pages").size() != count
                || plan.path("visualDirection").asText().isBlank()) throw new BizException(502, "分图方案格式或张数不正确，请重试");
        for (JsonNode page : plan.path("pages")) {
            if (page.path("title").asText().isBlank() || page.path("text").asText().isBlank()
                    || page.path("visual").asText().isBlank()) throw new BizException(502, "分图方案缺少标题、正文或画面说明");
        }
        return plan;
    }

    private Long authorize(Long articleId, boolean write) {
        var operator = users.requireCurrentUser();
        if (write) users.ensurePermissionOrLegacy("content.article.write", "project.update", Set.of("operator", "delivery_manager", "partner", "partner_staff"));
        else users.ensurePermission("project.read");
        ArticleDraft article = requireArticle(articleId);
        if (!isDouyin(article)) throw new BizException(400, "生图测试仅支持抖音图文文章");
        Project project = projects.selectById(article.getProjectId());
        if (project == null || project.getDeletedAt() != null) throw new BizException(404, "项目不存在");
        users.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        if (write) brandAccess.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        return operator.getId();
    }

    private ArticleDraft requireArticle(Long id) {
        ArticleDraft article = articles.selectById(id);
        if (article == null || "deleted".equals(article.getStatus())) throw new BizException(404, "文章不存在");
        return article;
    }

    private ArticleDraftVersion currentVersion(ArticleDraft article) {
        ArticleDraftVersion version = versions.selectOne(new LambdaQueryWrapper<ArticleDraftVersion>()
                .eq(ArticleDraftVersion::getArticleId, article.getId()).eq(ArticleDraftVersion::getVersionNo, article.getCurrentVersionNo()));
        if (version == null) throw new BizException(404, "文章版本不存在");
        return version;
    }

    private AiPlatformConfig requireModel(Long id) {
        LambdaQueryWrapper<AiPlatformConfig> query = new LambdaQueryWrapper<AiPlatformConfig>()
                .eq(AiPlatformConfig::getUsageScene, "IMAGE_GENERATION")
                .eq(AiPlatformConfig::getIntegrationType, "OPENAI_IMAGE")
                .eq(AiPlatformConfig::getEnabled, true).eq(AiPlatformConfig::getDegraded, false)
                .eq(id != null, AiPlatformConfig::getId, id).orderByAsc(AiPlatformConfig::getId).last("LIMIT 1");
        AiPlatformConfig model = models.selectOne(query);
        if (model == null) throw new BizException(400, "请先新增并启用图片生成模型配置");
        ArkImageClient.endpoint(model.getApiUrl());
        return model;
    }

    private DouyinArticleImageBatch requireBatch(Long articleId, Long id) {
        DouyinArticleImageBatch batch = batches.selectById(id);
        if (batch == null || !Objects.equals(batch.getArticleId(), articleId)) throw new BizException(404, "生图任务不存在");
        return batch;
    }

    private BatchView view(DouyinArticleImageBatch batch) {
        try {
            return new BatchView(batch.getId(), batch.getArticleId(), batch.getSourceVersionNo(), batch.getAppliedVersionNo(),
                    batch.getStatus(), batch.getImageCount(), batch.getStyleCode(), urls(batch),
                    batch.getPlanJson() == null ? null : json.readTree(batch.getPlanJson()), batch.getErrorMessage(), batch.getCreatedAt());
        } catch (Exception ex) { throw new BizException(500, "生图任务读取失败"); }
    }

    private List<String> urls(DouyinArticleImageBatch batch) {
        return readIds(batch.getMaterialIdsJson()).stream().map(id -> {
            BrandMaterial material = materials.selectById(id);
            if (material == null || !Objects.equals(material.getBrandId(), batch.getSubjectBrandId())
                    || !Objects.equals(material.getSourceArticleId(), batch.getArticleId())) throw new BizException(409, "文章生成图片已不可用");
            return publicUrls.buildPublicStreamUrl(material);
        }).toList();
    }

    private List<Long> readIds(String value) {
        try { return json.readValue(value, new TypeReference<List<Long>>() { }); }
        catch (Exception ex) { throw new BizException(500, "文章图片列表读取失败"); }
    }

    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new BizException(500, "生图任务序列化失败"); }
    }

    static boolean isDouyin(ArticleDraft article) {
        return "self_media".equals(article.getChannelGroupCode())
                && ("douyin".equals(article.getChannelSubCode()) || "douyin_image_text".equals(article.getChannelSubCode()));
    }
}
