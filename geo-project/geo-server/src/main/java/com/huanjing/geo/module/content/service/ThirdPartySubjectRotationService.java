package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONUtil;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.dto.SubjectBrandLastSelectedRow;
import com.huanjing.geo.module.content.dto.ThirdPartySubjectPoolBrandRow;
import com.huanjing.geo.module.content.dto.ThirdPartySubjectPoolPreviewResponse;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ThirdPartySubjectRotationService {

    private static final String ALL_INDUSTRIES = "__ALL__";
    private static final int DEFAULT_CANDIDATE_LIMIT = 200;
    private static final int DEFAULT_EXCLUDED_LIMIT = 50;
    private static final int MAX_PREVIEW_LIMIT = 500;

    private final BrandMapper brandMapper;
    private final BatchArticleGenerationTaskMapper taskMapper;
    private final SpecialIndustryService specialIndustryService;

    public ThirdPartySubjectPoolPreviewResponse previewPool(Long sourceBrandId) {
        return previewPool(sourceBrandId, DEFAULT_CANDIDATE_LIMIT, DEFAULT_EXCLUDED_LIMIT);
    }

    public ThirdPartySubjectPoolPreviewResponse previewPool(Long sourceBrandId,
                                                            Integer candidateLimit,
                                                            Integer excludedLimit) {
        Brand sourceBrand = brandMapper.selectById(sourceBrandId);
        if (sourceBrand == null || sourceBrand.getDeletedAt() != null) {
            throw new BizException(404, "Brand not found");
        }
        int safeCandidateLimit = normalizeLimit(candidateLimit, DEFAULT_CANDIDATE_LIMIT);
        int safeExcludedLimit = normalizeLimit(excludedLimit, DEFAULT_EXCLUDED_LIMIT);
        List<String> coverableIndustries = coverableIndustries(sourceBrand.getCoverableIndustries());
        boolean includeAll = coverableIndustries.stream().anyMatch(ALL_INDUSTRIES::equalsIgnoreCase);
        boolean validSource = !coverableIndustries.isEmpty();
        List<ThirdPartySubjectPoolBrandRow> rows = brandMapper.selectThirdPartySubjectPoolRows();
        List<Long> eligibleBrandIds = rows.stream()
                .filter(row -> classify(sourceBrand, coverableIndustries, includeAll, row).candidate())
                .map(ThirdPartySubjectPoolBrandRow::getBrandId)
                .distinct()
                .toList();
        Map<Long, LastSelection> lastSelectedMap = eligibleBrandIds.isEmpty()
                ? Map.of()
                : lastSelectedMapByIds(sourceBrandId, eligibleBrandIds);
        List<ThirdPartySubjectPoolPreviewResponse.Item> allCandidates = rows.stream()
                .map(row -> toPreviewItem(sourceBrand, coverableIndustries, includeAll, row, lastSelectedMap))
                .filter(item -> item.reasonCode() == null)
                .sorted(Comparator
                        .comparing((ThirdPartySubjectPoolPreviewResponse.Item item) -> lastSelectedMap.get(item.brandId()),
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ThirdPartySubjectPoolPreviewResponse.Item::brandId))
                .toList();
        List<ThirdPartySubjectPoolPreviewResponse.Item> allExcluded = rows.stream()
                .map(row -> toPreviewItem(sourceBrand, coverableIndustries, includeAll, row, lastSelectedMap))
                .filter(item -> item.reasonCode() != null)
                .toList();
        List<ThirdPartySubjectPoolPreviewResponse.Item> candidates = allCandidates.stream()
                .limit(safeCandidateLimit)
                .toList();
        List<ThirdPartySubjectPoolPreviewResponse.Item> excluded = allExcluded.stream()
                .limit(safeExcludedLimit)
                .toList();
        return new ThirdPartySubjectPoolPreviewResponse(
                sourceBrand.getId(),
                sourceBrand.getBrandName(),
                coverableIndustries,
                includeAll,
                validSource,
                allCandidates.size(),
                allExcluded.size(),
                candidates.size(),
                excluded.size(),
                candidates,
                excluded
        );
    }

    private int normalizeLimit(Integer requested, int fallback) {
        int value = requested == null || requested <= 0 ? fallback : requested;
        return Math.min(value, MAX_PREVIEW_LIMIT);
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
        boolean includeAll = coverableIndustries.stream().anyMatch(ALL_INDUSTRIES::equalsIgnoreCase);
        List<ThirdPartySubjectPoolBrandRow> candidates = brandMapper.selectThirdPartySubjectPoolRows().stream()
                .filter(row -> classify(sourceBrand, coverableIndustries, includeAll, row).candidate())
                .toList();
        if (candidates.isEmpty()) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "第三方信源没有可轮换的签约品牌主体");
        }

        Map<Long, LastSelection> lastSelectedMap = lastSelectedMap(sourceBrandId, candidates);
        ThirdPartySubjectPoolBrandRow selected = candidates.stream()
                .min(Comparator
                        .comparing((ThirdPartySubjectPoolBrandRow row) -> lastSelectedMap.get(row.getBrandId()),
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ThirdPartySubjectPoolBrandRow::getBrandId))
                .orElseThrow(() -> new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "第三方信源没有可轮换的签约品牌主体"));
        Long subjectProjectId = selected.getSubjectProjectId();
        if (subjectProjectId == null) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "轮换主体缺少有效项目");
        }
        return new RotationResult(sourceBrandId, sourceProjectId, selected.getBrandId(), subjectProjectId, true);
    }

    private boolean shouldRotate(Brand sourceBrand, String channelGroupCode, String perspectiveCode) {
        return sourceBrand != null
                && ArticlePromptChannels.SELF_MEDIA.equals(channelGroupCode)
                && TemplatePerspectiveCodes.isThirdParty(perspectiveCode)
                && !coverableIndustries(sourceBrand.getCoverableIndustries()).isEmpty();
    }

    private Map<Long, LastSelection> lastSelectedMap(Long sourceBrandId, List<ThirdPartySubjectPoolBrandRow> candidates) {
        List<Long> candidateIds = candidates.stream().map(ThirdPartySubjectPoolBrandRow::getBrandId).distinct().toList();
        if (candidateIds.isEmpty()) {
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

    private Map<Long, LastSelection> lastSelectedMapByIds(Long sourceBrandId, List<Long> candidateIds) {
        if (candidateIds.isEmpty()) {
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

    private ThirdPartySubjectPoolPreviewResponse.Item toPreviewItem(Brand sourceBrand,
                                                                    List<String> coverableIndustries,
                                                                    boolean includeAll,
                                                                    ThirdPartySubjectPoolBrandRow row,
                                                                    Map<Long, LastSelection> lastSelectedMap) {
        PoolDecision decision = classify(sourceBrand, coverableIndustries, includeAll, row);
        LastSelection lastSelection = lastSelectedMap.get(row.getBrandId());
        return new ThirdPartySubjectPoolPreviewResponse.Item(
                row.getBrandId(),
                row.getBrandName(),
                row.getIndustry(),
                row.getCompanyId(),
                row.getCompanyName(),
                row.getSubjectProjectId(),
                decision.candidate() && lastSelection != null ? lastSelection.lastSelectedAt() : null,
                decision.reasonCode(),
                decision.reason()
        );
    }

    private PoolDecision classify(Brand sourceBrand,
                                  List<String> coverableIndustries,
                                  boolean includeAll,
                                  ThirdPartySubjectPoolBrandRow row) {
        if (coverableIndustries.isEmpty()) {
            return excluded("source_not_configured", "当前品牌尚未配置可覆盖行业");
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
        if (!includeAll && !coverableIndustries.contains(row.getIndustry())) {
            return excluded("industry_not_matched", "品牌行业不在信源覆盖范围内");
        }
        return new PoolDecision(true, null, null);
    }

    private boolean isMedicalOrOral(ThirdPartySubjectPoolBrandRow row) {
        return specialIndustryService.detectMedicalIndustryCode(
                row.getComplianceIndustryCode(),
                row.getIndustry()
        ).isPresent();
    }

    private PoolDecision excluded(String code, String reason) {
        return new PoolDecision(false, code, reason);
    }

    private List<String> coverableIndustries(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return JSONUtil.parseArray(raw).stream()
                    .filter(item -> item != null && StringUtils.hasText(String.valueOf(item)))
                    .map(item -> String.valueOf(item).trim())
                    .distinct()
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    public record RotationResult(Long sourceBrandId,
                                 Long sourceProjectId,
                                 Long subjectBrandId,
                                 Long subjectProjectId,
                                 boolean rotated) {
    }

    private record PoolDecision(boolean candidate, String reasonCode, String reason) {
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
