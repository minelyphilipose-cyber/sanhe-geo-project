package com.huanjing.geo.module.report.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.util.HttpClientUtil;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.report.dto.PresaleQuestionItemUpsertRequest;
import com.huanjing.geo.module.report.entity.PresaleQuestionItem;
import com.huanjing.geo.module.report.entity.PresaleQuestionSet;
import com.huanjing.geo.module.report.mapper.PresaleQuestionItemMapper;
import com.huanjing.geo.module.report.mapper.PresaleQuestionSetMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresaleQuestionSetService {

    private final PresaleQuestionSetMapper setMapper;
    private final PresaleQuestionItemMapper itemMapper;
    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final CompanyMapper companyMapper;
    private final CurrentUserService currentUserService;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PlatformCredentialService platformCredentialService;

    public List<PresaleQuestionSet> listByProject(Long projectId) {
        currentUserService.ensurePermission("project.read");
        ensureProjectReadable(projectId);
        return setMapper.selectList(
                new LambdaQueryWrapper<PresaleQuestionSet>()
                        .eq(PresaleQuestionSet::getProjectId, projectId)
                        .orderByDesc(PresaleQuestionSet::getVersionNo)
        );
    }

    public Map<String, Object> detail(Long setId) {
        currentUserService.ensurePermission("project.read");
        PresaleQuestionSet set = requireSet(setId);
        ensureProjectReadable(set.getProjectId());
        List<PresaleQuestionItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PresaleQuestionItem>()
                        .eq(PresaleQuestionItem::getSetId, setId)
                        .orderByAsc(PresaleQuestionItem::getSortOrder, PresaleQuestionItem::getId)
        );
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("set", set);
        map.put("items", items);
        return map;
    }

    @Transactional
    public Map<String, Object> generate(Long projectId, boolean regenerate) {
        currentUserService.ensurePermission("project.write");
        Project project = ensureProjectWritable(projectId);
        SysUser user = currentUserService.requireCurrentUser();

        PresaleQuestionSet latest = latestSet(projectId);
        if (latest != null && !regenerate && "draft".equals(latest.getStatus())) {
            return detail(latest.getId());
        }
        if (latest != null && ("draft".equals(latest.getStatus()) || "locked".equals(latest.getStatus()))) {
            latest.setStatus("archived");
            latest.setArchivedAt(LocalDateTime.now());
            setMapper.updateById(latest);
        }

        PresaleQuestionSet set = new PresaleQuestionSet();
        set.setProjectId(projectId);
        set.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        set.setStatus("draft");
        set.setQuestionCount(0);
        set.setGeneratedAt(LocalDateTime.now());
        set.setCreatedBy(user.getId());
        setMapper.insert(set);

        List<PresaleQuestionItemUpsertRequest> generated = generateQuestionCandidates(project);
        int order = 1;
        for (PresaleQuestionItemUpsertRequest req : generated) {
            PresaleQuestionItem item = new PresaleQuestionItem();
            item.setSetId(set.getId());
            item.setProjectId(projectId);
            item.setContent(req.getContent().trim());
            item.setQuestionType(normalizeQuestionType(req.getQuestionType()));
            item.setSource(StringUtils.hasText(req.getSource()) ? req.getSource() : "auto");
            item.setSortOrder(order++);
            item.setIsActive(true);
            itemMapper.insert(item);
        }
        set.setQuestionCount(generated.size());
        setMapper.updateById(set);
        return detail(set.getId());
    }

    @Transactional
    public Map<String, Object> saveItems(Long setId, List<PresaleQuestionItemUpsertRequest> items) {
        currentUserService.ensurePermission("project.write");
        PresaleQuestionSet set = requireSet(setId);
        ensureProjectWritable(set.getProjectId());
        if (!"draft".equals(set.getStatus())) {
            throw new BizException(400, "Only draft question set can be edited");
        }
        itemMapper.delete(new LambdaQueryWrapper<PresaleQuestionItem>().eq(PresaleQuestionItem::getSetId, setId));
        int order = 1;
        for (PresaleQuestionItemUpsertRequest req : items) {
            PresaleQuestionItem item = new PresaleQuestionItem();
            item.setSetId(setId);
            item.setProjectId(set.getProjectId());
            item.setContent(req.getContent().trim());
            item.setQuestionType(normalizeQuestionType(req.getQuestionType()));
            item.setSource(StringUtils.hasText(req.getSource()) ? req.getSource() : "manual");
            item.setSortOrder(req.getSortOrder() == null ? order : req.getSortOrder());
            item.setIsActive(req.getIsActive() == null ? true : req.getIsActive());
            itemMapper.insert(item);
            order++;
        }
        set.setQuestionCount((int) items.stream().filter(i -> i.getIsActive() == null || i.getIsActive()).count());
        setMapper.updateById(set);
        return detail(setId);
    }

    @Transactional
    public PresaleQuestionSet lock(Long setId) {
        currentUserService.ensurePermission("project.write");
        SysUser user = currentUserService.requireCurrentUser();
        PresaleQuestionSet set = requireSet(setId);
        ensureProjectWritable(set.getProjectId());
        if ("archived".equals(set.getStatus())) {
            throw new BizException(400, "Archived question set cannot be locked");
        }
        long activeCount = itemMapper.selectCount(
                new LambdaQueryWrapper<PresaleQuestionItem>()
                        .eq(PresaleQuestionItem::getSetId, setId)
                        .eq(PresaleQuestionItem::getIsActive, true)
        );
        if (activeCount <= 0) {
            throw new BizException(400, "No active questions to lock");
        }
        setMapper.update(
                null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PresaleQuestionSet>()
                        .eq(PresaleQuestionSet::getProjectId, set.getProjectId())
                        .eq(PresaleQuestionSet::getStatus, "locked")
                        .set(PresaleQuestionSet::getStatus, "archived")
                        .set(PresaleQuestionSet::getArchivedAt, LocalDateTime.now())
        );
        set.setStatus("locked");
        set.setLockedAt(LocalDateTime.now());
        set.setLockedBy(user.getId());
        set.setQuestionCount((int) activeCount);
        setMapper.updateById(set);
        return set;
    }

    public PresaleQuestionSet latestLockedSet(Long projectId) {
        return setMapper.selectOne(
                new LambdaQueryWrapper<PresaleQuestionSet>()
                        .eq(PresaleQuestionSet::getProjectId, projectId)
                        .eq(PresaleQuestionSet::getStatus, "locked")
                        .orderByDesc(PresaleQuestionSet::getVersionNo)
                        .last("LIMIT 1")
        );
    }

    private PresaleQuestionSet latestSet(Long projectId) {
        return setMapper.selectOne(
                new LambdaQueryWrapper<PresaleQuestionSet>()
                        .eq(PresaleQuestionSet::getProjectId, projectId)
                        .orderByDesc(PresaleQuestionSet::getVersionNo)
                        .last("LIMIT 1")
        );
    }

    private PresaleQuestionSet requireSet(Long setId) {
        PresaleQuestionSet set = setMapper.selectById(setId);
        if (set == null) {
            throw new BizException(404, "Question set not found");
        }
        return set;
    }

    private void ensureProjectReadable(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        currentUserService.ensurePartnerResourceAccess(currentUserService.requireCurrentUser(), project.getPartnerId(), "project");
    }

    private Project ensureProjectWritable(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        currentUserService.ensurePartnerResourceAccess(currentUserService.requireCurrentUser(), project.getPartnerId(), "project");
        if (!isPendingStatus(project.getStatus())) {
            throw new BizException(400, "Presale diagnosis only allowed when project is pending");
        }
        return project;
    }

    private boolean isPendingStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String v = status.trim().toLowerCase(Locale.ROOT);
        return "pending".equals(v) || "draft".equals(v) || "not_started".equals(v) || "paused".equals(v);
    }

    private String normalizeQuestionType(String type) {
        if (!StringUtils.hasText(type)) {
            return "qa";
        }
        String v = type.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "brand", "industry", "decision", "competitor", "qa", "comparison", "location", "transaction" -> v;
            default -> "qa";
        };
    }

    private List<PresaleQuestionItemUpsertRequest> generateQuestionCandidates(Project project) {
        Brand brand = project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
        Company company = project.getCompanyId() == null ? null : companyMapper.selectById(project.getCompanyId());
        List<PresaleQuestionItemUpsertRequest> aiItems = invokeQuestionGeneratorModel(project, brand, company);
        if (!aiItems.isEmpty()) {
            return aiItems;
        }
        return fallbackQuestionCandidates(project, brand, company);
    }

    private List<PresaleQuestionItemUpsertRequest> invokeQuestionGeneratorModel(Project project, Brand brand, Company company) {
        AiPlatformConfig cfg = aiPlatformConfigMapper.selectOne(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .in(AiPlatformConfig::getPriorityLevel, List.of("P1", "P0"))
                        .orderByAsc(AiPlatformConfig::getPriorityLevel, AiPlatformConfig::getId)
                        .last("LIMIT 1")
        );
        if (cfg == null) {
            return List.of();
        }
        try {
            String apiKey = platformCredentialService.resolveApiKey(cfg.getPlatformCode(), cfg.getPrimaryKeyRef(), cfg.getApiKey());
            if (!StringUtils.hasText(apiKey)) {
                return List.of();
            }
            String prompt = buildQuestionPrompt(project, brand, company);
            String response = invokeModel(cfg.getApiUrl(), cfg.getModelId(), apiKey, prompt);
            return parseQuestionJson(response);
        } catch (Exception ex) {
            log.warn("generate presale questions by model failed, projectId={}, reason={}", project.getId(), ex.getMessage());
            return List.of();
        }
    }

    private String invokeModel(String apiUrl, String modelId, String apiKey, String prompt) {
        String targetUrl = apiUrl.endsWith("/chat/completions") ? apiUrl : (apiUrl.endsWith("/") ? apiUrl + "chat/completions" : apiUrl + "/chat/completions");
        JSONObject payload = new JSONObject();
        payload.set("model", modelId);
        payload.set("temperature", 0);
        payload.set("messages", List.of(
                Map.of("role", "system", "content", "You are a GEO presale question generator. Return strict JSON only."),
                Map.of("role", "user", "content", prompt)
        ));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("api-key", apiKey);
        headers.put("x-api-key", apiKey);
        HttpClientUtil.HttpResult result;
        try {
            result = HttpClientUtil.postJson(targetUrl, headers, payload.toString(), 5000, 20000);
        } catch (Exception ex) {
            throw new BizException(500, "model invoke failed: " + ex.getMessage());
        }
        if (result.statusCode() < 200 || result.statusCode() >= 300) {
            throw new BizException(result.statusCode(), "model invoke failed");
        }
        JSONObject json = JSONUtil.parseObj(result.body());
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return result.body();
        }
        JSONObject choice = choices.getJSONObject(0);
        JSONObject msg = choice.getJSONObject("message");
        return msg == null ? result.body() : msg.getStr("content");
    }

    private String buildQuestionPrompt(Project project, Brand brand, Company company) {
        return "生成15-25条售前GEO诊断问题，JSON数组格式[{content,question_type}]。question_type仅可为brand/industry/decision/competitor/qa。"
                + "品牌=" + (brand == null ? "" : brand.getBrandName())
                + " 行业=" + (brand != null ? brand.getIndustry() : (company == null ? "" : company.getIndustry()))
                + " 主营=" + (brand == null ? "" : brand.getMainBusiness())
                + " 服务区域=" + (brand == null ? "" : brand.getServiceArea())
                + " 竞品=" + (company == null ? "" : company.getCompetitors())
                + " 官网=" + (brand == null ? "" : brand.getWebsite());
    }

    private List<PresaleQuestionItemUpsertRequest> parseQuestionJson(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        String body = content.trim();
        int start = body.indexOf('[');
        int end = body.lastIndexOf(']');
        if (start >= 0 && end > start) {
            body = body.substring(start, end + 1);
        }
        JSONArray arr = JSONUtil.parseArray(body);
        List<PresaleQuestionItemUpsertRequest> items = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject it = arr.getJSONObject(i);
            if (it == null) {
                continue;
            }
            String contentText = it.getStr("content");
            if (!StringUtils.hasText(contentText)) {
                continue;
            }
            PresaleQuestionItemUpsertRequest req = new PresaleQuestionItemUpsertRequest();
            req.setContent(contentText.trim());
            req.setQuestionType(normalizeQuestionType(it.getStr("question_type")));
            req.setSource("auto");
            req.setIsActive(true);
            req.setSortOrder(i + 1);
            items.add(req);
        }
        return items;
    }

    private List<PresaleQuestionItemUpsertRequest> fallbackQuestionCandidates(Project project, Brand brand, Company company) {
        String brandName = brand == null ? project.getProjectName() : brand.getBrandName();
        List<String> base = List.of(
                brandName + " 是什么品牌？",
                brandName + " 在行业里的优势是什么？",
                "在" + (brand == null ? "本" : safeIndustry(brand, company)) + "领域，" + brandName + "靠谱吗？",
                "" + brandName + " 的官网和联系方式是什么？",
                "" + brandName + " 和同类品牌相比有什么区别？",
                "如果要选择" + safeIndustry(brand, company) + "服务，为什么考虑" + brandName + "？",
                brandName + " 适合哪些客户场景？",
                brandName + " 在" + safeIndustry(brand, company) + "相关问答里为什么曝光少？",
                "" + brandName + " 的核心能力和案例有哪些？",
                "用户在决策" + safeIndustry(brand, company) + "时会不会提到" + brandName + "？",
                "" + brandName + " 是否有官方渠道可咨询？",
                "" + brandName + " 在AI平台里容易被竞品替代吗？",
                "选择" + brandName + " 前应该先了解哪些信息？",
                "" + brandName + " 的品牌词和行业词覆盖情况怎么样？",
                "" + brandName + " 如何提升在AI回答中的可见度？"
        );
        List<PresaleQuestionItemUpsertRequest> items = new ArrayList<>();
        for (int i = 0; i < base.size(); i++) {
            PresaleQuestionItemUpsertRequest req = new PresaleQuestionItemUpsertRequest();
            req.setContent(base.get(i));
            req.setQuestionType(i % 5 == 0 ? "brand" : (i % 5 == 1 ? "industry" : (i % 5 == 2 ? "decision" : (i % 5 == 3 ? "competitor" : "qa"))));
            req.setSource("auto");
            req.setIsActive(true);
            req.setSortOrder(i + 1);
            items.add(req);
        }
        return items;
    }

    private String safeIndustry(Brand brand, Company company) {
        String industry = brand != null ? brand.getIndustry() : null;
        if (!StringUtils.hasText(industry) && company != null) {
            industry = company.getIndustry();
        }
        return StringUtils.hasText(industry) ? industry : "所在行业";
    }
}
