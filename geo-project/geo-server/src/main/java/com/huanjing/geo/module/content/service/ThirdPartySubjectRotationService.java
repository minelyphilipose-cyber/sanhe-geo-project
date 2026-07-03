package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.dto.SubjectBrandLastSelectedRow;
import com.huanjing.geo.module.content.dto.ThirdPartySubjectPoolBrandRow;
import com.huanjing.geo.module.content.dto.ThirdPartySubjectPoolDtos;
import com.huanjing.geo.module.content.dto.ThirdPartySubjectPoolPreviewResponse;
import com.huanjing.geo.module.content.entity.ThirdPartySubjectPoolItem;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.ThirdPartySubjectPoolItemMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThirdPartySubjectRotationService {

    private static final String ALL_INDUSTRIES = "__ALL__";
    private static final String MATCH_DIRECT = "direct";
    private static final String MATCH_LLM = "llm";
    private static final String MATCH_MANUAL = "manual";
    private static final int DEFAULT_CANDIDATE_LIMIT = 200;
    private static final int DEFAULT_EXCLUDED_LIMIT = 50;
    private static final int MAX_PREVIEW_LIMIT = 500;

    private static final String INDUSTRY_MATCH_SYSTEM_PROMPT = """
            你只负责判断行业集合是否被信源覆盖行业覆盖。
            只能从 candidateIndustries 中选择匹配项，必须原样返回行业名称，不得改写、扩写、解释。
            只输出 JSON，格式为 {"matchedIndustries":["候选行业名称"]}。
            如果没有匹配项，输出 {"matchedIndustries":[]}。
            """;

    private final BrandMapper brandMapper;
    private final BatchArticleGenerationTaskMapper taskMapper;
    private final ThirdPartySubjectPoolItemMapper poolItemMapper;
    private final SysDictItemMapper sysDictItemMapper;
    private final SpecialIndustryService specialIndustryService;
    private final ArticleModelResolver articleModelResolver;
    private final LlmCallFacade llmCallFacade;
    private final CurrentUserService currentUserService;

    public ThirdPartySubjectPoolPreviewResponse previewPool(Long sourceBrandId) {
        return previewPool(sourceBrandId, DEFAULT_CANDIDATE_LIMIT, DEFAULT_EXCLUDED_LIMIT);
    }

    public ThirdPartySubjectPoolPreviewResponse previewPool(Long sourceBrandId,
                                                            Integer candidateLimit,
                                                            Integer excludedLimit) {
        Brand sourceBrand = requireActiveBrand(sourceBrandId);
        int safeCandidateLimit = normalizeLimit(candidateLimit, DEFAULT_CANDIDATE_LIMIT);
        int safeExcludedLimit = normalizeLimit(excludedLimit, DEFAULT_EXCLUDED_LIMIT);
        List<String> coverableIndustries = coverableIndustries(sourceBrand.getCoverableIndustries());
        boolean includeAll = includeAll(coverableIndustries);
        List<ThirdPartySubjectPoolBrandRow> rows = brandMapper.selectThirdPartySubjectPoolRows();
        Map<Long, ThirdPartySubjectPoolBrandRow> rowMap = rows.stream()
                .collect(Collectors.toMap(ThirdPartySubjectPoolBrandRow::getBrandId, Function.identity(), (a, b) -> a));
        Map<Long, LastSelection> lastSelectedMap = lastSelectedMapByIds(sourceBrandId, confirmedSubjectIds(sourceBrandId));
        List<ThirdPartySubjectPoolItem> confirmedItems = confirmedItems(sourceBrandId);

        List<ThirdPartySubjectPoolPreviewResponse.Item> confirmed = confirmedItems.stream()
                .map(item -> toConfirmedPreviewItem(sourceBrand, coverableIndustries, includeAll, item, rowMap.get(item.getSubjectBrandId()), lastSelectedMap))
                .sorted(Comparator
                        .comparing((ThirdPartySubjectPoolPreviewResponse.Item item) -> Boolean.FALSE.equals(item.available()))
                        .thenComparing(ThirdPartySubjectPoolPreviewResponse.Item::brandId))
                .toList();
        List<ThirdPartySubjectPoolPreviewResponse.Item> availableConfirmed = confirmed.stream()
                .filter(item -> Boolean.TRUE.equals(item.available()))
                .toList();
        List<ThirdPartySubjectPoolPreviewResponse.Item> excluded = rows.stream()
                .map(row -> toExcludedPreviewItem(sourceBrand, coverableIndustries, includeAll, row, Set.of()))
                .filter(item -> item.reasonCode() != null)
                .limit(safeExcludedLimit)
                .toList();
        List<ThirdPartySubjectPoolPreviewResponse.Item> availableSubjects = rows.stream()
                .filter(row -> hardRule(sourceBrand, coverableIndustries, includeAll, row).candidate())
                .filter(row -> confirmedItems.stream().noneMatch(item -> Objects.equals(item.getSubjectBrandId(), row.getBrandId())))
                .map(row -> toAvailableSubjectItem(row))
                .toList();
        LocalDateTime lastConfirmedAt = confirmedItems.stream()
                .map(ThirdPartySubjectPoolItem::getConfirmedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return new ThirdPartySubjectPoolPreviewResponse(
                sourceBrand.getId(),
                sourceBrand.getBrandName(),
                coverableIndustries,
                includeAll,
                !coverableIndustries.isEmpty(),
                !confirmedItems.isEmpty(),
                lastConfirmedAt,
                false,
                null,
                availableConfirmed.size(),
                excluded.size(),
                confirmed.size() - availableConfirmed.size(),
                confirmed.size(),
                Math.min(confirmed.size(), safeCandidateLimit),
                excluded.size(),
                confirmed.stream().limit(safeCandidateLimit).toList(),
                excluded,
                availableSubjects
        );
    }

    public ThirdPartySubjectPoolPreviewResponse suggestPool(Long sourceBrandId,
                                                            ThirdPartySubjectPoolDtos.SuggestRequest request) {
        Brand sourceBrand = requireActiveBrand(sourceBrandId);
        List<String> coverableIndustries = normalizeCoverableIndustries(request == null ? null : request.coverableIndustries());
        boolean includeAll = includeAll(coverableIndustries);
        List<ThirdPartySubjectPoolBrandRow> rows = brandMapper.selectThirdPartySubjectPoolRows();
        Set<Long> existingSubjectIds = "incremental".equalsIgnoreCase(request == null ? null : request.mode())
                ? new HashSet<>(confirmedSubjectIds(sourceBrandId))
                : Set.of();
        Map<String, String> industryLabels = industryLabels();

        List<ThirdPartySubjectPoolBrandRow> eligibleRows = rows.stream()
                .filter(row -> !existingSubjectIds.contains(row.getBrandId()))
                .filter(row -> hardRule(sourceBrand, coverableIndustries, includeAll, row).candidate())
                .toList();
        Map<String, MatchDecision> directMatches = new HashMap<>();
        List<String> llmCandidateIndustries = new ArrayList<>();
        for (ThirdPartySubjectPoolBrandRow row : eligibleRows) {
            String industryLabel = industryLabel(row.getIndustry(), industryLabels);
            if (includeAll || directIndustryMatched(coverableIndustries, row.getIndustry(), industryLabel)) {
                directMatches.put(row.getIndustry(), new MatchDecision(MATCH_DIRECT, industryLabel));
            } else if (StringUtils.hasText(industryLabel) && !llmCandidateIndustries.contains(industryLabel)) {
                llmCandidateIndustries.add(industryLabel);
            }
        }

        boolean llmFailed = false;
        String llmFailureMessage = null;
        Set<String> llmMatchedLabels = Set.of();
        if (!llmCandidateIndustries.isEmpty()) {
            try {
                llmMatchedLabels = matchIndustriesByLlm(toIndustryLabels(coverableIndustries, industryLabels), llmCandidateIndustries);
            } catch (Exception ex) {
                llmFailed = true;
                llmFailureMessage = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "模型匹配失败";
                log.warn("third-party subject pool LLM industry match failed sourceBrandId={}", sourceBrandId, ex);
            }
        }

        Set<String> matchedIndustryKeys = new HashSet<>();
        Map<String, MatchDecision> matchDecisions = new HashMap<>(directMatches);
        for (ThirdPartySubjectPoolBrandRow row : eligibleRows) {
            String industryLabel = industryLabel(row.getIndustry(), industryLabels);
            if (llmMatchedLabels.contains(industryLabel)) {
                matchDecisions.put(row.getIndustry(), new MatchDecision(MATCH_LLM, industryLabel));
            }
            if (matchDecisions.containsKey(row.getIndustry())) {
                matchedIndustryKeys.add(row.getIndustry());
            }
        }

        List<ThirdPartySubjectPoolPreviewResponse.Item> candidates = eligibleRows.stream()
                .filter(row -> matchedIndustryKeys.contains(row.getIndustry()))
                .map(row -> toSuggestedItem(row, matchDecisions.get(row.getIndustry())))
                .toList();
        Set<Long> suggestedIds = candidates.stream()
                .map(ThirdPartySubjectPoolPreviewResponse.Item::brandId)
                .collect(Collectors.toSet());
        List<ThirdPartySubjectPoolPreviewResponse.Item> excluded = rows.stream()
                .filter(row -> !existingSubjectIds.contains(row.getBrandId()))
                .map(row -> toExcludedPreviewItem(sourceBrand, coverableIndustries, includeAll, row, suggestedIds))
                .filter(item -> item.reasonCode() != null)
                .toList();
        List<ThirdPartySubjectPoolPreviewResponse.Item> availableSubjects = eligibleRows.stream()
                .filter(row -> !suggestedIds.contains(row.getBrandId()))
                .map(row -> toAvailableSubjectItem(row))
                .toList();

        return new ThirdPartySubjectPoolPreviewResponse(
                sourceBrand.getId(),
                sourceBrand.getBrandName(),
                coverableIndustries,
                includeAll,
                !coverableIndustries.isEmpty(),
                false,
                null,
                llmFailed,
                llmFailureMessage,
                candidates.size(),
                excluded.size(),
                0,
                candidates.size(),
                candidates.size(),
                excluded.size(),
                candidates,
                excluded,
                availableSubjects
        );
    }

    @Transactional
    public ThirdPartySubjectPoolPreviewResponse savePool(Long sourceBrandId,
                                                         ThirdPartySubjectPoolDtos.SaveRequest request) {
        currentUserService.ensurePermission("brand.update");
        SysUser operator = currentUserService.requireCurrentUser();
        Brand sourceBrand = requireActiveBrand(sourceBrandId);
        List<String> coverableIndustries = normalizeCoverableIndustries(request == null ? null : request.coverableIndustries());
        boolean includeAll = includeAll(coverableIndustries);
        List<ThirdPartySubjectPoolBrandRow> rows = brandMapper.selectThirdPartySubjectPoolRows();
        Map<Long, ThirdPartySubjectPoolBrandRow> rowMap = rows.stream()
                .collect(Collectors.toMap(ThirdPartySubjectPoolBrandRow::getBrandId, Function.identity(), (a, b) -> a));

        List<ThirdPartySubjectPoolDtos.SaveItem> subjects = request == null || request.subjects() == null
                ? List.of()
                : request.subjects();
        List<ThirdPartySubjectPoolItem> items = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (ThirdPartySubjectPoolDtos.SaveItem item : subjects) {
            if (item == null || item.brandId() == null || !seen.add(item.brandId())) {
                continue;
            }
            ThirdPartySubjectPoolBrandRow row = rowMap.get(item.brandId());
            PoolDecision decision = row == null
                    ? excluded("brand_not_found", "品牌不存在或已停用")
                    : hardRule(sourceBrand, coverableIndustries, includeAll, row);
            if (!decision.candidate()) {
                throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST,
                        "主体品牌不可加入：" + (row == null ? item.brandId() : row.getBrandName()) + "，" + decision.reason());
            }
            ThirdPartySubjectPoolItem entity = new ThirdPartySubjectPoolItem();
            entity.setSourceBrandId(sourceBrandId);
            entity.setSubjectBrandId(row.getBrandId());
            entity.setSubjectProjectId(row.getSubjectProjectId());
            entity.setMatchSource(normalizeMatchSource(item.matchSource()));
            entity.setMatchedIndustry(StringUtils.hasText(item.matchedIndustry()) ? item.matchedIndustry().trim() : row.getIndustry());
            entity.setCoverageTermsSnapshot(JSONUtil.toJsonStr(coverableIndustries));
            entity.setConfirmedAt(now);
            entity.setConfirmedBy(operator.getId());
            items.add(entity);
        }

        sourceBrand.setCoverableIndustries(coverableIndustries.isEmpty() ? null : JSONUtil.toJsonStr(coverableIndustries));
        brandMapper.updateById(sourceBrand);
        poolItemMapper.delete(new LambdaQueryWrapper<ThirdPartySubjectPoolItem>()
                .eq(ThirdPartySubjectPoolItem::getSourceBrandId, sourceBrandId));
        for (ThirdPartySubjectPoolItem item : items) {
            poolItemMapper.insert(item);
        }
        return previewPool(sourceBrandId);
    }

    public RotationResult resolve(Project sourceProject,
                                  Brand sourceBrand,
                                  String channelGroupCode,
                                  String perspectiveCode) {
        Long sourceProjectId = sourceProject == null ? null : sourceProject.getId();
        Long sourceBrandId = sourceBrand == null ? null : sourceBrand.getId();
        if (!shouldRotate(sourceBrand, channelGroupCode, perspectiveCode)) {
            return new RotationResult(sourceBrandId, sourceProjectId, sourceBrandId, sourceProjectId, false);
        }

        brandMapper.lockActiveBrandById(sourceBrandId);
        List<String> coverableIndustries = coverableIndustries(sourceBrand.getCoverableIndustries());
        boolean includeAll = includeAll(coverableIndustries);
        List<ThirdPartySubjectPoolItem> confirmedItems = confirmedItems(sourceBrandId);
        if (confirmedItems.isEmpty()) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "请先生成并确认第三方主体池");
        }

        Map<Long, ThirdPartySubjectPoolBrandRow> rowMap = brandMapper.selectThirdPartySubjectPoolRows().stream()
                .collect(Collectors.toMap(ThirdPartySubjectPoolBrandRow::getBrandId, Function.identity(), (a, b) -> a));
        List<ThirdPartySubjectPoolBrandRow> candidates = confirmedItems.stream()
                .map(item -> rowMap.get(item.getSubjectBrandId()))
                .filter(Objects::nonNull)
                .filter(row -> hardRule(sourceBrand, coverableIndustries, includeAll, row).candidate())
                .toList();
        if (candidates.isEmpty()) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "已确认的第三方主体当前均不可用，请刷新覆盖并确认主体池");
        }

        Map<Long, LastSelection> lastSelectedMap = lastSelectedMap(sourceBrandId, candidates);
        ThirdPartySubjectPoolBrandRow selected = candidates.stream()
                .min(Comparator
                        .comparing((ThirdPartySubjectPoolBrandRow row) -> lastSelectedMap.get(row.getBrandId()),
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ThirdPartySubjectPoolBrandRow::getBrandId))
                .orElseThrow(() -> new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "已确认的第三方主体当前均不可用，请刷新覆盖并确认主体池"));
        Long subjectProjectId = selected.getSubjectProjectId();
        if (subjectProjectId == null) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "轮换主体缺少有效项目");
        }
        return new RotationResult(sourceBrandId, sourceProjectId, selected.getBrandId(), subjectProjectId, true);
    }

    private Brand requireActiveBrand(Long sourceBrandId) {
        Brand sourceBrand = brandMapper.selectById(sourceBrandId);
        if (sourceBrand == null || sourceBrand.getDeletedAt() != null) {
            throw new BizException(404, "Brand not found");
        }
        return sourceBrand;
    }

    private boolean shouldRotate(Brand sourceBrand, String channelGroupCode, String perspectiveCode) {
        return sourceBrand != null
                && ArticlePromptChannels.SELF_MEDIA.equals(channelGroupCode)
                && TemplatePerspectiveCodes.isThirdParty(perspectiveCode)
                && !coverableIndustries(sourceBrand.getCoverableIndustries()).isEmpty();
    }

    private List<ThirdPartySubjectPoolItem> confirmedItems(Long sourceBrandId) {
        return poolItemMapper.selectList(new LambdaQueryWrapper<ThirdPartySubjectPoolItem>()
                .eq(ThirdPartySubjectPoolItem::getSourceBrandId, sourceBrandId)
                .orderByAsc(ThirdPartySubjectPoolItem::getId));
    }

    private List<Long> confirmedSubjectIds(Long sourceBrandId) {
        return confirmedItems(sourceBrandId).stream()
                .map(ThirdPartySubjectPoolItem::getSubjectBrandId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private int normalizeLimit(Integer requested, int fallback) {
        int value = requested == null || requested <= 0 ? fallback : requested;
        return Math.min(value, MAX_PREVIEW_LIMIT);
    }

    private ThirdPartySubjectPoolPreviewResponse.Item toConfirmedPreviewItem(Brand sourceBrand,
                                                                            List<String> coverableIndustries,
                                                                            boolean includeAll,
                                                                            ThirdPartySubjectPoolItem item,
                                                                            ThirdPartySubjectPoolBrandRow row,
                                                                            Map<Long, LastSelection> lastSelectedMap) {
        if (row == null) {
            return new ThirdPartySubjectPoolPreviewResponse.Item(
                    item.getSubjectBrandId(), null, null, null, null, item.getSubjectProjectId(),
                    null, false, item.getMatchSource(), item.getMatchedIndustry(),
                    "brand_not_found", "品牌不存在或已停用");
        }
        PoolDecision decision = hardRule(sourceBrand, coverableIndustries, includeAll, row);
        LastSelection lastSelection = lastSelectedMap.get(row.getBrandId());
        return new ThirdPartySubjectPoolPreviewResponse.Item(
                row.getBrandId(),
                row.getBrandName(),
                row.getIndustry(),
                row.getCompanyId(),
                row.getCompanyName(),
                row.getSubjectProjectId(),
                decision.candidate() && lastSelection != null ? lastSelection.lastSelectedAt() : null,
                decision.candidate(),
                item.getMatchSource(),
                StringUtils.hasText(item.getMatchedIndustry()) ? item.getMatchedIndustry() : row.getIndustry(),
                decision.candidate() ? null : decision.reasonCode(),
                decision.candidate() ? null : decision.reason()
        );
    }

    private ThirdPartySubjectPoolPreviewResponse.Item toExcludedPreviewItem(Brand sourceBrand,
                                                                           List<String> coverableIndustries,
                                                                           boolean includeAll,
                                                                           ThirdPartySubjectPoolBrandRow row,
                                                                           Set<Long> suggestedIds) {
        PoolDecision decision = hardRule(sourceBrand, coverableIndustries, includeAll, row);
        if (decision.candidate()) {
            if (suggestedIds.contains(row.getBrandId())) {
                return toSuggestedItem(row, new MatchDecision(MATCH_DIRECT, row.getIndustry()));
            }
            return new ThirdPartySubjectPoolPreviewResponse.Item(
                    row.getBrandId(), row.getBrandName(), row.getIndustry(), row.getCompanyId(), row.getCompanyName(),
                    row.getSubjectProjectId(), null, true, null, null, null, null);
        }
        return new ThirdPartySubjectPoolPreviewResponse.Item(
                row.getBrandId(), row.getBrandName(), row.getIndustry(), row.getCompanyId(), row.getCompanyName(),
                row.getSubjectProjectId(), null, false, null, null, decision.reasonCode(), decision.reason());
    }

    private ThirdPartySubjectPoolPreviewResponse.Item toSuggestedItem(ThirdPartySubjectPoolBrandRow row, MatchDecision match) {
        return new ThirdPartySubjectPoolPreviewResponse.Item(
                row.getBrandId(), row.getBrandName(), row.getIndustry(), row.getCompanyId(), row.getCompanyName(),
                row.getSubjectProjectId(), null, true,
                match == null ? MATCH_MANUAL : match.source(),
                match == null ? row.getIndustry() : match.matchedIndustry(),
                null, null);
    }

    private ThirdPartySubjectPoolPreviewResponse.Item toAvailableSubjectItem(ThirdPartySubjectPoolBrandRow row) {
        return new ThirdPartySubjectPoolPreviewResponse.Item(
                row.getBrandId(), row.getBrandName(), row.getIndustry(), row.getCompanyId(), row.getCompanyName(),
                row.getSubjectProjectId(), null, true, MATCH_MANUAL, row.getIndustry(), null, null);
    }

    private PoolDecision hardRule(Brand sourceBrand,
                                  List<String> coverableIndustries,
                                  boolean includeAll,
                                  ThirdPartySubjectPoolBrandRow row) {
        if (coverableIndustries.isEmpty()) {
            return excluded("source_not_configured", "当前品牌尚未配置可覆盖行业");
        }
        if (row == null) {
            return excluded("brand_not_found", "品牌不存在或已停用");
        }
        if (Objects.equals(sourceBrand.getId(), row.getBrandId())) {
            return excluded("source_self", "信源品牌自身不进入轮换池");
        }
        if (!Boolean.TRUE.equals(row.getAllowThirdPartyPromotion())) {
            return excluded("promotion_disabled", "品牌关闭了第三方主体推广开关");
        }
        if (!"signed".equals(row.getCompanyStatus())) {
            return excluded("company_not_signed", "所属客户不是已签约状态");
        }
        if (!Boolean.TRUE.equals(row.getHasActivePackage())) {
            return excluded("no_active_package", "所属客户没有启用中的套餐");
        }
        if (row.getSubjectProjectId() == null) {
            return excluded("no_active_project", "品牌没有可用的 active 项目");
        }
        if (isMedicalOrOral(row)) {
            return excluded("medical_or_oral_excluded", "医疗/口腔暂不进入第三方轮换池");
        }
        return new PoolDecision(true, null, null);
    }

    private boolean isMedicalOrOral(ThirdPartySubjectPoolBrandRow row) {
        return specialIndustryService.detectMedicalIndustryCode(
                row.getComplianceIndustryCode(),
                row.getIndustry()
        ).isPresent();
    }

    private boolean directIndustryMatched(List<String> coverableIndustries, String industryKey, String industryLabel) {
        Set<String> terms = coverableIndustries.stream()
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return terms.contains(normalizeText(industryKey)) || terms.contains(normalizeText(industryLabel));
    }

    private Set<String> matchIndustriesByLlm(List<String> coverableIndustries, List<String> candidateIndustries) throws Exception {
        if (coverableIndustries.isEmpty() || candidateIndustries.isEmpty()) {
            return Set.of();
        }
        List<String> distinctCandidates = candidateIndustries.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        JSONObject payload = JSONUtil.createObj()
                .set("sourceCoverageIndustries", coverableIndustries)
                .set("candidateIndustries", distinctCandidates);
        String prompt = """
                请根据下面 JSON 判断 candidateIndustries 中哪些行业属于 sourceCoverageIndustries 的内容覆盖范围。
                只返回 JSON：{"matchedIndustries":["候选行业名称"]}。
                输入：
                %s
                """.formatted(payload.toString());
        ArticleModelResolver.ModelSelection model = articleModelResolver.resolve(null, null, INDUSTRY_MATCH_SYSTEM_PROMPT, false);
        String response = llmCallFacade.execute(LlmCallRequest.direct(prompt, model.config())).invokeResult().responseText();
        return parseMatchedIndustries(response, distinctCandidates);
    }

    Set<String> parseMatchedIndustries(String response, List<String> distinctCandidates) {
        JSONObject parsed = JSONUtil.parseObj(extractJsonObject(response));
        JSONArray array = parsed.getJSONArray("matchedIndustries");
        if (array == null || array.isEmpty()) {
            return Set.of();
        }
        Set<String> allowed = new HashSet<>(distinctCandidates);
        Set<String> matched = new LinkedHashSet<>();
        for (Object item : array) {
            if (item != null) {
                String value = String.valueOf(item).trim();
                if (allowed.contains(value)) {
                    matched.add(value);
                }
            }
        }
        return matched;
    }

    private String extractJsonObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "{}";
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private Map<String, String> industryLabels() {
        return sysDictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictType, "industry_tag")
                        .eq(SysDictItem::getEnabled, true))
                .stream()
                .collect(Collectors.toMap(SysDictItem::getDictKey, SysDictItem::getDictValue, (a, b) -> a));
    }

    List<String> toIndustryLabels(List<String> values, Map<String, String> labels) {
        return values.stream()
                .map(value -> StringUtils.hasText(labels.get(value)) ? labels.get(value).trim() : value)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String industryLabel(String industryKey, Map<String, String> labels) {
        if (!StringUtils.hasText(industryKey)) {
            return "";
        }
        return StringUtils.hasText(labels.get(industryKey)) ? labels.get(industryKey) : industryKey;
    }

    private String normalizeMatchSource(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : MATCH_MANUAL;
        if (MATCH_DIRECT.equals(normalized) || MATCH_LLM.equals(normalized) || MATCH_MANUAL.equals(normalized)) {
            return normalized;
        }
        return MATCH_MANUAL;
    }

    private boolean includeAll(List<String> coverableIndustries) {
        return coverableIndustries.stream().anyMatch(ALL_INDUSTRIES::equalsIgnoreCase);
    }

    private List<String> coverableIndustries(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return normalizeCoverableIndustries(JSONUtil.parseArray(raw).stream()
                    .map(item -> item == null ? null : String.valueOf(item))
                    .toList());
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> normalizeCoverableIndustries(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> normalized = values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (normalized.stream().anyMatch(ALL_INDUSTRIES::equalsIgnoreCase)) {
            return List.of(ALL_INDUSTRIES);
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value)
                ? value.trim().replace(" ", "").replace("　", "").toLowerCase(Locale.ROOT)
                : "";
    }

    private PoolDecision excluded(String code, String reason) {
        return new PoolDecision(false, code, reason);
    }

    private Map<Long, LastSelection> lastSelectedMap(Long sourceBrandId, List<ThirdPartySubjectPoolBrandRow> candidates) {
        List<Long> candidateIds = candidates.stream().map(ThirdPartySubjectPoolBrandRow::getBrandId).distinct().toList();
        return lastSelectedMapByIds(sourceBrandId, candidateIds);
    }

    private Map<Long, LastSelection> lastSelectedMapByIds(Long sourceBrandId, List<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, LastSelection> result = new HashMap<>();
        for (SubjectBrandLastSelectedRow row : taskMapper.selectLastSelectedBySourceBrand(sourceBrandId, candidateIds)) {
            if (row.getSubjectBrandId() != null) {
                result.put(row.getSubjectBrandId(), new LastSelection(row.getLastSelectedAt(), row.getLastSelectedTaskId()));
            }
        }
        return result;
    }

    public record RotationResult(Long sourceBrandId,
                                 Long sourceProjectId,
                                 Long subjectBrandId,
                                 Long subjectProjectId,
                                 boolean rotated) {
    }

    private record PoolDecision(boolean candidate, String reasonCode, String reason) {
    }

    private record MatchDecision(String source, String matchedIndustry) {
    }

    private record LastSelection(LocalDateTime lastSelectedAt, Long lastSelectedTaskId) implements Comparable<LastSelection> {
        @Override
        public int compareTo(LastSelection other) {
            if (other == null) {
                return 1;
            }
            int idCompare = compareNullable(lastSelectedTaskId, other.lastSelectedTaskId);
            if (idCompare != 0) {
                return idCompare;
            }
            return compareNullable(lastSelectedAt, other.lastSelectedAt);
        }

        private static <T extends Comparable<T>> int compareNullable(T left, T right) {
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return -1;
            }
            if (right == null) {
                return 1;
            }
            return left.compareTo(right);
        }
    }
}
