package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.service.BrandMaterialPublicUrlService;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.dto.ContentUrlRewriteItemVO;
import com.huanjing.geo.module.retention.dto.ContentUrlRewriteRequest;
import com.huanjing.geo.module.retention.dto.ContentUrlRewriteResponse;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentUrlRewriteService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1_000;
    private static final String TABLE_NAME = "article_draft_version";
    private static final String COLUMN_NAME = "content_markdown";
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)\"'<>]+", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final DataRetentionRunAuditService auditService;
    private final DataRetentionProperties retentionProperties;
    private final BrandMaterialMapper brandMaterialMapper;
    private final BrandMaterialPublicUrlService publicUrlService;

    public ContentUrlRewriteResponse dryRun(ContentUrlRewriteRequest request) {
        if (request == null) {
            request = new ContentUrlRewriteRequest();
        }
        request.setDryRun(true);
        return rewrite(request);
    }

    public ContentUrlRewriteResponse rewrite(ContentUrlRewriteRequest request) {
        if (request == null) {
            request = new ContentUrlRewriteRequest();
        }
        currentUserService.ensurePermission("dispatch.task.release");
        boolean dryRun = request.getDryRun() == null || Boolean.TRUE.equals(request.getDryRun());
        if (!dryRun && !retentionProperties.getContentUrlRewrite().isExecuteEnabled()) {
            throw new BizException(403, "Content URL rewrite execute is disabled by geo.retention.content-url-rewrite.execute-enabled");
        }

        int limit = normalizeLimit(request.getLimit());
        ContentUrlRewriteResponse response = new ContentUrlRewriteResponse();
        response.setDryRun(dryRun);
        response.setLimit(limit);
        response.setArticleId(request.getArticleId());
        response.setVersionId(request.getVersionId());
        response.setScannedFields(List.of(TABLE_NAME + "." + COLUMN_NAME));

        Map<String, Object> startMetrics = new LinkedHashMap<>();
        startMetrics.put("articleId", request.getArticleId());
        startMetrics.put("versionId", request.getVersionId());
        startMetrics.put("limit", limit);
        Long runId = auditService.startRun("content_url_rewrite", dryRun ? "dry_run" : "execute", null, null, startMetrics);
        response.setRetentionRunId(runId);

        try {
            List<RewriteCandidate> candidates = loadCandidates(request.getArticleId(), request.getVersionId(), limit);
            for (RewriteCandidate candidate : candidates) {
                ContentUrlRewriteItemVO item = analyze(candidate);
                if (!dryRun && Boolean.TRUE.equals(item.getChanged())) {
                    executeOne(candidate, item);
                }
                response.getItems().add(item);
            }
            summarize(response);
            auditService.finishRun(runId, "succeeded",
                    response.getCandidateCount(),
                    dryRun ? response.getChangedRowCount() : response.getChangedRowCount(),
                    response.getSkippedCount(),
                    response.getOrphanUrlCount() + response.getFailedCount(),
                    metrics(response),
                    null);
            return response;
        } catch (Exception ex) {
            auditService.finishRun(runId, "failed",
                    response.getCandidateCount(),
                    response.getChangedRowCount(),
                    response.getSkippedCount(),
                    response.getOrphanUrlCount() + response.getFailedCount() + 1,
                    metrics(response),
                    ex.getMessage());
            throw ex;
        }
    }

    private List<RewriteCandidate> loadCandidates(Long articleId, Long versionId, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id,
                       article_id,
                       version_no,
                       content_markdown,
                       content_object_key
                  FROM article_draft_version
                 WHERE content_markdown IS NOT NULL
                   AND (content_markdown LIKE '%/geo-files/%' OR content_markdown LIKE '%:9000/%')
                """);
        List<Object> args = new ArrayList<>();
        if (articleId != null) {
            sql.append("   AND article_id = ?\n");
            args.add(articleId);
        }
        if (versionId != null) {
            sql.append("   AND id = ?\n");
            args.add(versionId);
        }
        sql.append("""
                 ORDER BY id ASC
                 LIMIT ?
                """);
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new RewriteCandidate(
                rs.getLong("id"),
                rs.getLong("article_id"),
                rs.getObject("version_no", Integer.class),
                rs.getString("content_markdown"),
                rs.getString("content_object_key")
        ), args.toArray());
    }

    private ContentUrlRewriteItemVO analyze(RewriteCandidate candidate) {
        RewritePlan plan = buildPlan(candidate.content());
        ContentUrlRewriteItemVO item = new ContentUrlRewriteItemVO();
        item.setVersionId(candidate.versionId());
        item.setArticleId(candidate.articleId());
        item.setVersionNo(candidate.versionNo());
        item.setTableName(TABLE_NAME);
        item.setColumnName(COLUMN_NAME);
        item.setMatchedUrlCount(plan.matchedUrls());
        item.setRewriteUrlCount(plan.rewriteCount());
        item.setOrphanUrlCount(plan.orphanUrls().size());
        item.setChanged(plan.changed());
        item.setContentAlreadyArchived(StringUtils.hasText(candidate.contentObjectKey()));
        item.setRequiresRearchive(plan.changed() && StringUtils.hasText(candidate.contentObjectKey()));
        item.setResult(plan.changed() ? "pending" : "skipped");
        item.setReplacements(plan.samples());
        item.setOrphanUrls(plan.orphanUrls());
        return item;
    }

    private RewritePlan buildPlan(String content) {
        Matcher matcher = URL_PATTERN.matcher(content);
        List<String> urls = new ArrayList<>();
        while (matcher.find()) {
            urls.add(trimTrailingPunctuation(matcher.group()));
        }

        Map<String, String> replacements = new LinkedHashMap<>();
        List<ContentUrlRewriteItemVO.ReplacementSample> samples = new ArrayList<>();
        List<String> orphanUrls = new ArrayList<>();
        int matched = 0;
        int rewrittenCount = 0;
        for (String url : urls) {
            String objectKey = extractGeoFilesObjectKey(url);
            if (!StringUtils.hasText(objectKey)) {
                continue;
            }
            matched++;
            BrandMaterial material = findMaterial(objectKey);
            if (material == null) {
                orphanUrls.add(url);
                continue;
            }
            try {
                String publicUrl = publicUrlService.buildPublicStreamUrl(material);
                String existing = replacements.putIfAbsent(url, publicUrl);
                rewrittenCount++;
                if (existing == null && samples.size() < 20) {
                    ContentUrlRewriteItemVO.ReplacementSample sample = new ContentUrlRewriteItemVO.ReplacementSample();
                    sample.setObjectKey(objectKey);
                    sample.setOldUrl(url);
                    sample.setNewUrl(publicUrl);
                    samples.add(sample);
                }
            } catch (Exception ex) {
                orphanUrls.add(url);
                log.warn("Skip material URL rewrite, objectKey={}, url={}", objectKey, url, ex);
            }
        }

        String rewritten = content;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            rewritten = rewritten.replace(entry.getKey(), entry.getValue());
        }
        return new RewritePlan(rewritten, matched, rewrittenCount, !rewritten.equals(content), samples, orphanUrls);
    }

    private BrandMaterial findMaterial(String objectKey) {
        List<BrandMaterial> materials = brandMaterialMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BrandMaterial>()
                        .eq(BrandMaterial::getObjectKey, objectKey)
                        .last("LIMIT 1"));
        return materials.isEmpty() ? null : materials.get(0);
    }

    private void executeOne(RewriteCandidate candidate, ContentUrlRewriteItemVO item) {
        RewritePlan plan = buildPlan(candidate.content());
        if (!plan.changed()) {
            item.setResult("skipped");
            return;
        }
        int updated = jdbcTemplate.update("""
                UPDATE article_draft_version
                   SET content_markdown = ?
                 WHERE id = ?
                   AND content_markdown = ?
                """, plan.rewrittenContent(), candidate.versionId(), candidate.content());
        if (updated == 1) {
            item.setResult("rewritten");
            return;
        }
        item.setResult("failed");
        item.setErrorMessage("content_changed_during_rewrite");
    }

    private String extractGeoFilesObjectKey(String value) {
        try {
            URI uri = URI.create(value);
            String path = URLDecoder.decode(uri.getRawPath(), StandardCharsets.UTF_8);
            String marker = "/geo-files/";
            int index = path.indexOf(marker);
            if (index < 0) {
                return null;
            }
            String key = path.substring(index + marker.length());
            return StringUtils.hasText(key) ? key : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String trimTrailingPunctuation(String url) {
        String result = url;
        while (result.endsWith(".") || result.endsWith(",") || result.endsWith(";") || result.endsWith(":")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private void summarize(ContentUrlRewriteResponse response) {
        response.setCandidateCount(response.getItems().size());
        int changed = 0;
        int rewritten = 0;
        int orphan = 0;
        int skipped = 0;
        int failed = 0;
        int rearchive = 0;
        for (ContentUrlRewriteItemVO item : response.getItems()) {
            if (Boolean.TRUE.equals(item.getChanged())) {
                changed++;
            } else {
                skipped++;
            }
            rewritten += item.getRewriteUrlCount() == null ? 0 : item.getRewriteUrlCount();
            orphan += item.getOrphanUrlCount() == null ? 0 : item.getOrphanUrlCount();
            if ("failed".equals(item.getResult())) {
                failed++;
            }
            if (Boolean.TRUE.equals(item.getRequiresRearchive())) {
                rearchive++;
            }
        }
        response.setChangedRowCount(changed);
        response.setRewrittenUrlCount(rewritten);
        response.setOrphanUrlCount(orphan);
        response.setSkippedCount(skipped);
        response.setFailedCount(failed);
        response.setRearchiveRequiredCount(rearchive);
    }

    private Map<String, Object> metrics(ContentUrlRewriteResponse response) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("articleId", response.getArticleId());
        metrics.put("versionId", response.getVersionId());
        metrics.put("limit", response.getLimit());
        metrics.put("scannedFields", response.getScannedFields());
        metrics.put("candidateCount", response.getCandidateCount());
        metrics.put("changedRowCount", response.getChangedRowCount());
        metrics.put("rewrittenUrlCount", response.getRewrittenUrlCount());
        metrics.put("orphanUrlCount", response.getOrphanUrlCount());
        metrics.put("rearchiveRequiredCount", response.getRearchiveRequiredCount());
        metrics.put("sampleCount", response.getItems().stream()
                .mapToInt(item -> item.getReplacements() == null ? 0 : item.getReplacements().size())
                .sum());
        return metrics;
    }

    private int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private record RewriteCandidate(Long versionId,
                                    Long articleId,
                                    Integer versionNo,
                                    String content,
                                    String contentObjectKey) {
    }

    private record RewritePlan(String rewrittenContent,
                               int matchedUrls,
                               int rewriteCount,
                               boolean changed,
                               List<ContentUrlRewriteItemVO.ReplacementSample> samples,
                               List<String> orphanUrls) {
    }
}
