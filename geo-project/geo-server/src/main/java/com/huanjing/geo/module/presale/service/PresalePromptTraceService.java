package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.access.PresaleAccessService;
import com.huanjing.geo.module.presale.dto.request.PresalePromptTraceQueryRequest;
import com.huanjing.geo.module.presale.dto.response.PresalePromptTraceDetailVO;
import com.huanjing.geo.module.presale.dto.response.PresalePromptTraceEvidenceVO;
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

import java.net.URI;
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
    private static final String WEB_SEARCH_CONTRACT = "WEB_SEARCH_V1";
    private static final int MAX_VISIBLE_EVIDENCE_ITEMS = 20;
    private static final int MAX_VISIBLE_SEARCH_QUERIES = 5;

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
                .evidence(toEvidenceView(row))
                .analyzePromptContent(row.getAnalyzePromptContent())
                .analyzeRawResponse(row.getAnalyzeRawResponse())
                .analyzeCallStatus(row.getAnalyzeCallStatus())
                .analyzeFailureReason(row.getAnalyzeFailureReason())
                .analyzeDurationMs(row.getAnalyzeDurationMs())
                .analyzeModelSnapshotInferred(Boolean.TRUE.equals(row.getAnalyzeModelSnapshotInferred()))
                .parseView(toParseView(row))
                .build();
    }

    private PresalePromptTraceEvidenceVO toEvidenceView(PresalePromptTraceRow row) {
        String contractVersion = trimToNull(row == null ? null : row.getQueryContractVersion());
        String evidenceJson = row == null ? null : row.getSearchEvidenceJson();
        JsonNode root = readEvidenceRoot(evidenceJson);
        String resolvedContract = firstText(contractVersion,
                root == null ? null : text(root.get("queryContractVersion")));
        if (!WEB_SEARCH_CONTRACT.equals(resolvedContract)) {
            return PresalePromptTraceEvidenceVO.builder()
                    .webSearch(false)
                    .queryContractVersion(resolvedContract)
                    .searchTriggered(false)
                    .searchStatus("NOT_APPLICABLE")
                    .searchStatusText("原生 API 回答")
                    .evidenceLevel("NONE")
                    .evidenceLevelText("无联网证据")
                    .notice("本次问题通过平台原生 API 回答，未配置独立的联网引用来源。")
                    .searchQueries(List.of())
                    .sources(List.of())
                    .citations(List.of())
                    .build();
        }
        if (!hasText(evidenceJson)) {
            return emptyWebEvidence(resolvedContract, "联网调用未保存可展示的引用证据。");
        }
        if (root == null || !root.isObject()) {
            return emptyWebEvidence(resolvedContract, "引用证据暂无法解析，但不影响查看该次模型回答。");
        }
        try {
            boolean triggered = root.path("searchTriggered").asBoolean(false);
            String searchStatus = firstText(text(root.get("searchStatus")), "UNKNOWN");
            String evidenceLevel = firstText(text(root.get("evidenceLevel")), "NONE");
            List<PresalePromptTraceEvidenceVO.SourceView> sources = readEvidenceSources(root.path("sources"));
            List<PresalePromptTraceEvidenceVO.CitationView> citations = readEvidenceCitations(root.path("citations"));
            List<String> queries = readSearchQueries(root, sources);
            return PresalePromptTraceEvidenceVO.builder()
                    .webSearch(true)
                    .queryContractVersion(resolvedContract)
                    .searchTriggered(triggered)
                    .searchStatus(searchStatus)
                    .searchStatusText(searchStatusText(searchStatus, triggered))
                    .evidenceLevel(evidenceLevel)
                    .evidenceLevelText(evidenceLevelText(evidenceLevel))
                    .failureCode(trimToNull(text(root.get("failureCode"))))
                    .notice(evidenceNotice(triggered, searchStatus, sources))
                    .searchQueries(queries)
                    .sources(sources)
                    .citations(citations)
                    .build();
        } catch (Exception ex) {
            return emptyWebEvidence(resolvedContract, "引用证据暂无法解析，但不影响查看该次模型回答。");
        }
    }

    private JsonNode readEvidenceRoot(String evidenceJson) {
        if (!hasText(evidenceJson)) {
            return null;
        }
        try {
            return objectMapper.readTree(evidenceJson);
        } catch (Exception ex) {
            return null;
        }
    }

    private PresalePromptTraceEvidenceVO emptyWebEvidence(String contractVersion, String notice) {
        return PresalePromptTraceEvidenceVO.builder()
                .webSearch(true)
                .queryContractVersion(contractVersion)
                .searchTriggered(false)
                .searchStatus("UNKNOWN")
                .searchStatusText("证据不可用")
                .evidenceLevel("NONE")
                .evidenceLevelText("无可展示来源")
                .notice(notice)
                .searchQueries(List.of())
                .sources(List.of())
                .citations(List.of())
                .build();
    }

    private List<PresalePromptTraceEvidenceVO.SourceView> readEvidenceSources(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<PresalePromptTraceEvidenceVO.SourceView> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (out.size() >= MAX_VISIBLE_EVIDENCE_ITEMS) {
                break;
            }
            if (item == null || !item.isObject()) continue;
            String url = safeHttpUrl(firstText(text(item.get("normalizedUrl")), text(item.get("originalUrl"))));
            if (!hasText(url)) {
                continue;
            }
            // 展示域名必须从最终可点击 URL 推导，避免证据中的 domain 与真实链接不一致。
            String domain = safeHost(url);
            String title = firstText(trimToNull(text(item.get("title"))), domain, "来源 " + (out.size() + 1));
            out.add(PresalePromptTraceEvidenceVO.SourceView.builder()
                    .index(out.size() + 1)
                    .rank(nullableInt(item.get("rank")))
                    .title(title)
                    .url(url)
                    .domain(domain)
                    .media(trimToNull(text(item.get("media"))))
                    .snippet(trimToNull(text(item.get("snippet"))))
                    .publishTime(trimToNull(text(item.get("publishTime"))))
                    .query(trimToNull(text(item.get("query"))))
                    .build());
        }
        return out;
    }

    private List<PresalePromptTraceEvidenceVO.CitationView> readEvidenceCitations(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<PresalePromptTraceEvidenceVO.CitationView> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (out.size() >= MAX_VISIBLE_EVIDENCE_ITEMS) {
                break;
            }
            if (item == null || !item.isObject()) continue;
            String citationText = trimToNull(text(item.get("citationText")));
            if (!hasText(citationText)) {
                continue;
            }
            Integer citationIndex = nullableInt(item.get("citationIndex"));
            out.add(PresalePromptTraceEvidenceVO.CitationView.builder()
                    .index(citationIndex == null ? out.size() + 1 : citationIndex + 1)
                    .text(citationText)
                    .confidence(trimToNull(text(item.get("confidence"))))
                    .validationStatus(trimToNull(text(item.get("validationStatus"))))
                    .build());
        }
        return out;
    }

    private List<String> readSearchQueries(JsonNode root,
                                           List<PresalePromptTraceEvidenceVO.SourceView> sources) {
        Set<String> queries = new LinkedHashSet<>();
        JsonNode events = root.path("searchEvidence");
        if (events.isArray()) {
            for (JsonNode event : events) {
                addQuery(queries, text(event == null ? null : event.get("query")));
            }
        }
        for (PresalePromptTraceEvidenceVO.SourceView source : sources) {
            addQuery(queries, source.getQuery());
        }
        return queries.stream().limit(MAX_VISIBLE_SEARCH_QUERIES).toList();
    }

    private void addQuery(Set<String> queries, String query) {
        if (queries.size() < MAX_VISIBLE_SEARCH_QUERIES && hasText(query)) {
            queries.add(query.trim());
        }
    }

    private String safeHttpUrl(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            return (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && hasText(uri.getHost()))
                    ? uri.toString() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String safeHost(String value) {
        try {
            return URI.create(value).getHost();
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer nullableInt(JsonNode node) {
        return node != null && node.isIntegralNumber() ? node.intValue() : null;
    }

    private String evidenceNotice(boolean triggered,
                                  String searchStatus,
                                  List<PresalePromptTraceEvidenceVO.SourceView> sources) {
        if (!triggered) {
            return "本次联网搜索未实际触发，回答仍按真实 QUERY 结果保留。";
        }
        if (sources != null && !sources.isEmpty()) {
            return null;
        }
        if ("SUCCEEDED".equalsIgnoreCase(searchStatus)) {
            return "平台已完成搜索，但供应商未返回可展示的来源链接。";
        }
        return "联网搜索未获取到可展示来源，回答结果未因此阻塞。";
    }

    private String searchStatusText(String status, boolean triggered) {
        if (!triggered) return "未触发搜索";
        return switch (status == null ? "" : status.toUpperCase()) {
            case "SUCCEEDED" -> "联网检索完成";
            case "FAILED" -> "联网检索失败";
            default -> "已发起联网检索";
        };
    }

    private String evidenceLevelText(String level) {
        return switch (level == null ? "" : level.toUpperCase()) {
            case "CITATIONS" -> "含回答引用片段";
            case "SOURCES" -> "含来源链接";
            case "TOOL_EVENT" -> "仅记录检索事件";
            default -> "无可展示来源";
        };
    }

    private String firstText(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (hasText(value)) return value.trim();
        }
        return null;
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
                .attributionType(row.getAttributionType())
                .targetEntityHit(toBoolean(row.getTargetEntityHit()))
                .representedBrandHit(toBoolean(row.getRepresentedBrandHit()))
                .targetBrandRelationHit(toBoolean(row.getTargetBrandRelationHit()))
                .rankingText(row.getRanking() == null ? "未给出排名" : "第 " + row.getRanking() + " 位")
                .sentimentText(sentimentText(row.getSentiment()))
                .sentimentType(sentimentType(row.getSentiment()))
                .mentionedCompetitors(readTextArray(row.getMentionedCompetitors()))
                .sceneAdvantages(readTextArray(row.getSceneAdvantages()))
                .topKeywords(readKeywords(row.getTopKeywordsJson()))
                .negativeEvidence(readNegativeEvidence(row.getNegativeEvidenceJson()))
                .build();
    }

    private Boolean toBoolean(Integer value) {
        return value == null ? null : value == 1;
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
