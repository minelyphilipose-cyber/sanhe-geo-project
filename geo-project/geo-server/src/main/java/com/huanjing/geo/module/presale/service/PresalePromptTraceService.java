package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.access.PresaleAccessService;
import com.huanjing.geo.module.presale.dto.request.PresalePromptTraceQueryRequest;
import com.huanjing.geo.module.presale.dto.response.PresalePromptTraceDetailVO;
import com.huanjing.geo.module.presale.dto.response.PresalePromptTraceFilterOptionsVO;
import com.huanjing.geo.module.presale.dto.response.PresalePromptTraceListItemVO;
import com.huanjing.geo.module.presale.dto.response.PresalePromptTracePageVO;
import com.huanjing.geo.module.presale.dto.response.PresalePromptTraceParseViewVO;
import com.huanjing.geo.module.presale.dto.response.ReportVersionOptionVO;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PresalePromptTraceService {
    private static final String PERM_VIEW = "presale.report.view";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ANALYZE_FAILED = "ANALYZE_FAILED";
    private static final String STATUS_QUERY_FAILED = "QUERY_FAILED";

    private final PresaleReportVersionMapper versionMapper;
    private final PresaleAiPromptResultMapper promptResultMapper;
    private final PresaleAccessService accessService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public PresalePromptTraceService(PresaleReportVersionMapper versionMapper,
                                     PresaleAiPromptResultMapper promptResultMapper,
                                     PresaleAccessService accessService,
                                     CurrentUserService currentUserService,
                                     ObjectMapper objectMapper) {
        this.versionMapper = versionMapper;
        this.promptResultMapper = promptResultMapper;
        this.accessService = accessService;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    public List<ReportVersionOptionVO> listVersions(Long reportId) {
        currentUserService.ensurePermission(PERM_VIEW);
        accessService.requireReportWithAccess(reportId);
        List<PresaleReportVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<PresaleReportVersion>()
                        .eq(PresaleReportVersion::getReportId, reportId)
                        .orderByDesc(PresaleReportVersion::getVersionNo)
        );
        if (versions.isEmpty()) {
            return List.of();
        }
        List<Long> versionIds = versions.stream().map(PresaleReportVersion::getId).toList();
        Map<Long, Long> countByVersionId = promptResultMapper.selectPromptTraceCountsByVersionIds(versionIds)
                .stream()
                .collect(Collectors.toMap(VersionPromptTraceCountRow::getVersionId,
                        VersionPromptTraceCountRow::getPromptTraceCount));
        return versions.stream()
                .map(v -> toVersionOption(v, countByVersionId.getOrDefault(v.getId(), 0L)))
                .toList();
    }

    public PresalePromptTracePageVO list(Long reportId, Integer versionNo, PresalePromptTraceQueryRequest req) {
        currentUserService.ensurePermission(PERM_VIEW);
        PresaleReport report = accessService.requireReportWithAccess(reportId);
        PresaleReportVersion version = requireVersion(report, versionNo);
        PresalePromptTraceQueryRequest normalized = normalizeRequest(req);
        Page<PresalePromptTraceRow> rawPage = promptResultMapper.selectPromptTracePage(
                new Page<>(normalized.getCurrent(), normalized.getSize()),
                reportId,
                version.getVersionNo(),
                normalized
        );
        Page<PresalePromptTraceListItemVO> voPage = new Page<>(
                rawPage.getCurrent(),
                rawPage.getSize(),
                rawPage.getTotal()
        );
        voPage.setRecords(rawPage.getRecords().stream().map(this::toListItem).toList());
        return PresalePromptTracePageVO.builder()
                .page(voPage)
                .filterOptions(buildFilterOptions(version.getId()))
                .build();
    }

    public PresalePromptTraceDetailVO detail(Long reportId, Integer versionNo, Long promptResultId) {
        currentUserService.ensurePermission(PERM_VIEW);
        PresaleReport report = accessService.requireReportWithAccess(reportId);
        PresaleReportVersion version = requireVersion(report, versionNo);
        PresalePromptTraceRow row = promptResultMapper.selectPromptTraceDetail(
                reportId,
                version.getVersionNo(),
                promptResultId
        );
        if (row == null) {
            throw new BizException(404, "Prompt trace not found");
        }
        return PresalePromptTraceDetailVO.builder()
                .summary(toListItem(row))
                .queryPromptContent(row.getQueryPromptContent())
                .queryRawResponse(row.getQueryRawResponse())
                .queryCallStatus(row.getQueryCallStatus())
                .queryFailureReason(row.getQueryFailureReason())
                .queryDurationMs(row.getQueryDurationMs())
                .queryModelSnapshotInferred(Boolean.TRUE.equals(row.getQueryModelSnapshotInferred()))
                .analyzePromptContent(row.getAnalyzePromptContent())
                .analyzeRawResponse(row.getAnalyzeRawResponse())
                .analyzeCallStatus(row.getAnalyzeCallStatus())
                .analyzeFailureReason(row.getAnalyzeFailureReason())
                .analyzeDurationMs(row.getAnalyzeDurationMs())
                .analyzeModelSnapshotInferred(Boolean.TRUE.equals(row.getAnalyzeModelSnapshotInferred()))
                .parseView(toParseView(row))
                .build();
    }

    private PresaleReportVersion requireVersion(PresaleReport report, Integer versionNo) {
        if (report == null) {
            throw new BizException(404, "Report not found");
        }
        PresaleReportVersion version;
        if (versionNo == null) {
            version = report.getLatestVersionId() == null ? null : versionMapper.selectById(report.getLatestVersionId());
        } else {
            version = versionMapper.selectOne(
                    new LambdaQueryWrapper<PresaleReportVersion>()
                            .eq(PresaleReportVersion::getReportId, report.getId())
                            .eq(PresaleReportVersion::getVersionNo, versionNo)
                            .last("LIMIT 1")
            );
        }
        if (version == null) {
            throw new BizException(404, "Version not found");
        }
        return version;
    }

    private PresalePromptTraceQueryRequest normalizeRequest(PresalePromptTraceQueryRequest req) {
        PresalePromptTraceQueryRequest out = req == null ? new PresalePromptTraceQueryRequest() : req;
        if (out.getCurrent() == null || out.getCurrent() < 1) {
            out.setCurrent(1);
        }
        if (out.getSize() == null || out.getSize() < 1) {
            out.setSize(20);
        }
        if (out.getSize() > 100) {
            out.setSize(100);
        }
        out.setPlatformCode(trimToNull(out.getPlatformCode()));
        out.setCategory(trimToNull(out.getCategory()));
        out.setKeyword(trimToNull(out.getKeyword()));
        out.setStatus(trimToNull(out.getStatus()));
        if (out.getBatchNo() != null && out.getBatchNo() != 1 && out.getBatchNo() != 2) {
            out.setBatchNo(null);
        }
        if (!STATUS_SUCCESS.equals(out.getStatus())
                && !STATUS_ANALYZE_FAILED.equals(out.getStatus())
                && !STATUS_QUERY_FAILED.equals(out.getStatus())) {
            out.setStatus(null);
        }
        return out;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private ReportVersionOptionVO toVersionOption(PresaleReportVersion version, Long promptTraceCount) {
        boolean hasPromptTrace = promptTraceCount != null && promptTraceCount > 0;
        boolean done = "DONE".equals(version.getGenerationStatus());
        boolean disabled = !done || !hasPromptTrace;
        return ReportVersionOptionVO.builder()
                .versionId(version.getId())
                .versionNo(version.getVersionNo())
                .generationStatus(version.getGenerationStatus())
                .generationStatusText(statusText(version.getGenerationStatus()))
                .createdAt(version.getCreatedAt())
                .hasPromptTrace(hasPromptTrace)
                .disabled(disabled)
                .disabledReason(disabled ? "该版本无 Prompt 数据" : null)
                .build();
    }

    private PresalePromptTraceFilterOptionsVO buildFilterOptions(Long versionId) {
        List<PromptTraceFilterOptionRow> rows = promptResultMapper.selectPromptTraceFilterOptions(versionId);
        Map<String, String> platformByCode = new LinkedHashMap<>();
        Set<String> categories = new LinkedHashSet<>();
        for (PromptTraceFilterOptionRow row : rows) {
            if (hasText(row.getPlatformCode())) {
                platformByCode.putIfAbsent(row.getPlatformCode(), hasText(row.getPlatformName())
                        ? row.getPlatformName() : row.getPlatformCode());
            }
            if (hasText(row.getCategory())) {
                categories.add(row.getCategory());
            }
        }
        return PresalePromptTraceFilterOptionsVO.builder()
                .platforms(platformByCode.entrySet().stream()
                        .map(e -> PresalePromptTraceFilterOptionsVO.PlatformOption.builder()
                                .label(e.getValue())
                                .value(e.getKey())
                                .build())
                        .toList())
                .categories(new ArrayList<>(categories))
                .build();
    }

    private PresalePromptTraceListItemVO toListItem(PresalePromptTraceRow row) {
        String status = traceStatus(row);
        return PresalePromptTraceListItemVO.builder()
                .promptResultId(row.getPromptResultId())
                .reportId(row.getReportId())
                .versionId(row.getVersionId())
                .versionNo(row.getVersionNo())
                .batchNo(row.getBatchNo())
                .category(hasText(row.getCategory()) ? row.getCategory() : "—")
                .platformCode(row.getPlatformCode())
                .platformName(row.getPlatformName())
                .traceStatus(status)
                .traceStatusText(traceStatusText(status))
                .requestPromptContent(row.getRequestPromptContent())
                .queryAnswerBrief(queryAnswerBrief(row, status))
                .queryModelName(row.getQueryModelName())
                .analyzeModelName(row.getAnalyzeModelName())
                .totalDurationMs(totalDuration(row.getQueryDurationMs(), row.getAnalyzeDurationMs()))
                .build();
    }

    private String traceStatus(PresalePromptTraceRow row) {
        if (row == null || !"SUCCESS".equals(row.getQueryCallStatus())) {
            return STATUS_QUERY_FAILED;
        }
        if (!"SUCCESS".equals(row.getAnalyzeCallStatus()) || row.getIsMentioned() == null) {
            return STATUS_ANALYZE_FAILED;
        }
        return STATUS_SUCCESS;
    }

    private String traceStatusText(String status) {
        return switch (status) {
            case STATUS_SUCCESS -> "成功";
            case STATUS_ANALYZE_FAILED -> "解析失败";
            case STATUS_QUERY_FAILED -> "调用失败";
            default -> "未知";
        };
    }

    private String queryAnswerBrief(PresalePromptTraceRow row, String status) {
        if (STATUS_QUERY_FAILED.equals(status)) {
            return "调用失败：" + abbreviate(row == null ? null : row.getQueryFailureReason(), 30);
        }
        return abbreviate(row == null ? null : row.getQueryRawResponse(), 30);
    }

    private Integer totalDuration(Integer queryDurationMs, Integer analyzeDurationMs) {
        int total = 0;
        boolean hasValue = false;
        if (queryDurationMs != null) {
            total += queryDurationMs;
            hasValue = true;
        }
        if (analyzeDurationMs != null) {
            total += analyzeDurationMs;
            hasValue = true;
        }
        return hasValue ? total : null;
    }

    private PresalePromptTraceParseViewVO toParseView(PresalePromptTraceRow row) {
        return PresalePromptTraceParseViewVO.builder()
                .mentionedText(row.getIsMentioned() == null ? "未识别" : row.getIsMentioned() == 1 ? "已提及" : "未提及")
                .rankingText(row.getRanking() == null ? "未给出排名" : "第 " + row.getRanking() + " 位")
                .sentimentText(sentimentText(row.getSentiment()))
                .sentimentType(sentimentType(row.getSentiment()))
                .mentionedCompetitors(readTextArray(row.getMentionedCompetitors()))
                .sceneAdvantages(readTextArray(row.getSceneAdvantages()))
                .topKeywords(readKeywords(row.getTopKeywordsJson()))
                .negativeEvidence(readNegativeEvidence(row.getNegativeEvidenceJson()))
                .build();
    }

    private List<String> readTextArray(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                if (item != null && item.isTextual() && hasText(item.asText())) {
                    values.add(item.asText());
                }
            }
            return values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<PresalePromptTraceParseViewVO.KeywordView> readKeywords(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<PresalePromptTraceParseViewVO.KeywordView> values = new ArrayList<>();
            for (JsonNode item : node) {
                if (item == null || !item.isObject()) {
                    continue;
                }
                String keyword = text(item.get("keyword"));
                if (!hasText(keyword)) {
                    continue;
                }
                String sentiment = text(item.get("sentiment"));
                values.add(PresalePromptTraceParseViewVO.KeywordView.builder()
                        .keyword(keyword)
                        .sentimentText(sentimentText(sentiment))
                        .sentimentType(sentimentType(sentiment))
                        .build());
            }
            return values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private PresalePromptTraceParseViewVO.NegativeEvidenceView readNegativeEvidence(String json) {
        if (!hasText(json)) {
            return PresalePromptTraceParseViewVO.NegativeEvidenceView.builder()
                    .hasNegativeText("未识别")
                    .hasNegative(null)
                    .snippet(null)
                    .build();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            Boolean hasNegative = node.has("has_negative") && node.get("has_negative").isBoolean()
                    ? node.get("has_negative").asBoolean() : null;
            String snippet = text(node.get("snippet"));
            return PresalePromptTraceParseViewVO.NegativeEvidenceView.builder()
                    .hasNegative(hasNegative)
                    .hasNegativeText(hasNegative == null ? "未识别" : hasNegative ? "有负面证据" : "未发现负面证据")
                    .snippet(snippet)
                    .build();
        } catch (Exception ex) {
            return PresalePromptTraceParseViewVO.NegativeEvidenceView.builder()
                    .hasNegativeText("未识别")
                    .hasNegative(null)
                    .snippet(null)
                    .build();
        }
    }

    private String sentimentText(String sentiment) {
        return switch (sentiment == null ? "" : sentiment) {
            case "POSITIVE" -> "正向";
            case "NEUTRAL" -> "中性";
            case "NEGATIVE" -> "负向";
            default -> "未识别";
        };
    }

    private String sentimentType(String sentiment) {
        return switch (sentiment == null ? "" : sentiment) {
            case "POSITIVE" -> "success";
            case "NEGATIVE" -> "danger";
            case "NEUTRAL" -> "info";
            default -> "warning";
        };
    }

    private String statusText(String status) {
        return switch (status == null ? "" : status) {
            case "INIT" -> "初始化";
            case "QUEUED" -> "排队中";
            case "RUNNING" -> "生成中";
            case "DONE" -> "已完成";
            case "FAILED" -> "失败";
            default -> "未知";
        };
    }

    private String abbreviate(String value, int maxChars) {
        String normalized = normalizeSingleLine(value);
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "......";
    }

    private String normalizeSingleLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim()
                .replaceAll(" +", " ");
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText(null);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
