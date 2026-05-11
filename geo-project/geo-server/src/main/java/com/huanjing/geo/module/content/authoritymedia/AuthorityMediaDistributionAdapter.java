package com.huanjing.geo.module.content.authoritymedia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.AuthorityMediaOrder;
import com.huanjing.geo.module.content.entity.AuthorityMediaResource;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.AuthorityMediaOrderMapper;
import com.huanjing.geo.module.content.mapper.AuthorityMediaResourceMapper;
import com.huanjing.geo.module.content.service.adapter.FailureKind;
import com.huanjing.geo.module.content.service.adapter.SubmitResult;
import com.huanjing.geo.module.project.entity.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorityMediaDistributionAdapter {

    private static final long BALANCE_CACHE_SECONDS = 30L;
    private static final long BALANCE_FAILURE_CACHE_SECONDS = 10L;
    private static final DateTimeFormatter VENDOR_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MeititejiaClient client;
    private final AuthorityMediaResourceSyncService resourceSyncService;
    private final AuthorityMediaResourceMapper resourceMapper;
    private final AuthorityMediaOrderMapper orderMapper;
    private final AuthorityMediaPreviewTokenService previewTokenService;
    private final MeititejiaProperties properties;
    private final ObjectMapper objectMapper;

    private volatile CachedBalance cachedBalance;

    public AuthorityMediaResource validateBeforeCreatingTask(ArticleDraft article,
                                                             TargetContext.AuthorityMediaTarget target) {
        AuthorityMediaResource resource = resourceMapper.selectById(target.resourceId());
        if (resource == null || resource.getDeletedAt() != null) {
            throw new BizException(400, "authority media resource is unavailable");
        }
        if (!MeititejiaResourceType.NEWS_MEDIA.name().equals(resource.getResourceType())) {
            throw new BizException(400, "only NEWS_MEDIA resources are supported in phase 1");
        }
        AuthorityMediaResourceSyncService.RefreshResult refresh = resourceSyncService.refreshNewsMediaResourceIfStale(resource.getId());
        if (refresh.deleted()) {
            throw new BizException(400, "authority media resource has been removed by vendor");
        }
        resource = resourceMapper.selectById(resource.getId());
        if (resource == null || resource.getDeletedAt() != null) {
            throw new BizException(400, "authority media resource is unavailable after refresh");
        }
        SubmitResult validation = validateBeforeOrder(target, resource, normalizeSalingPrice(target.salingPrice(), resource));
        if (validation != null) {
            throw new BizException(validation.getStatusCode() == null ? 400 : validation.getStatusCode(), validation.getErrorMessage());
        }
        List<AuthorityMediaOrder> unfinished = orderMapper.selectUnfinishedByArticleAndResource(article.getId(), resource.getId());
        if (unfinished != null && !unfinished.isEmpty()) {
            throw new BizException(409, "已有进行中的同媒体订单，请等待状态确认后再分发");
        }
        return resource;
    }

    public SubmitResult submitNewsMedia(ArticleDraft article,
                                        Project project,
                                        DistributionTask task,
                                        Long operatorId,
                                        TargetContext.AuthorityMediaTarget target,
                                        String contentMarkdown) {
        AuthorityMediaResource resource = resourceMapper.selectById(target.resourceId());
        if (resource == null || resource.getDeletedAt() != null) {
            return failure(404, "authority media resource is unavailable", FailureKind.VALIDATION, false);
        }
        if (!MeititejiaResourceType.NEWS_MEDIA.name().equals(resource.getResourceType())) {
            return failure(400, "only NEWS_MEDIA resources are supported in phase 1", FailureKind.VALIDATION, false);
        }

        AuthorityMediaResourceSyncService.RefreshResult refresh = resourceSyncService.refreshNewsMediaResourceIfStale(resource.getId());
        if (refresh.deleted()) {
            return failure(400, "authority media resource has been removed by vendor", FailureKind.VALIDATION, false);
        }
        resource = resourceMapper.selectById(resource.getId());
        if (resource == null || resource.getDeletedAt() != null) {
            return failure(400, "authority media resource is unavailable after refresh", FailureKind.VALIDATION, false);
        }

        BigDecimal salingPrice = normalizeSalingPrice(target.salingPrice(), resource);
        SubmitResult validation = validateBeforeOrder(target, resource, salingPrice);
        if (validation != null) {
            return validation;
        }

        SubmitResult balanceFailure = precheckBalance(resource, salingPrice);
        if (balanceFailure != null) {
            return balanceFailure;
        }

        AuthorityMediaOrder order = ensureOrder(article, project, task, resource, operatorId);
        String externalNo = ensureExternalNo(order);
        String previewUrl = previewTokenService.issuePreviewUrl(order, article, target.previewUrl());
        String content = buildContent(contentMarkdown, previewUrl);
        MeititejiaClient.NewsMediaOrderRequest request = new MeititejiaClient.NewsMediaOrderRequest(
                article.getTitle(),
                content,
                mediaId(resource),
                externalNo,
                target.remark(),
                target.publishedAt(),
                salingPrice
        );
        Map<String, Object> auditParams = orderParams(request);
        String requestPayload = requestPayload(auditParams);

        try {
            JsonNode response = client.createNewsMediaOrder(request);
            String responsePayload = objectMapper.writeValueAsString(response);
            try {
                orderMapper.updateSubmissionResult(order.getId(), "submitted", LocalDateTime.now(),
                        0, "未处理", requestPayload, responsePayload);
            } catch (Exception ex) {
                log.error("CRITICAL: Meititejia order submitted to vendor but local DB update failed, taskId={}, orderId={}, externalNo={}, response={}",
                        task.getId(), order.getId(), externalNo, responsePayload, ex);
                throw ex;
            }
            SubmitResult result = SubmitResult.success(200, requestPayload, responsePayload, null, null);
            return result;
        } catch (MeititejiaApiException ex) {
            String responsePayload = ex.getResponseBody();
            if (Integer.valueOf(201).equals(ex.getBizCode()) && contains(ex.getBizMsg(), "已提交")) {
                log.error("Meititejia reports duplicated order submission, taskId={}, orderId={}, externalNo={}, bizMsg={}",
                        task.getId(), order.getId(), externalNo, ex.getBizMsg());
            } else {
                log.warn("Meititejia order submit API failed, taskId={}, orderId={}, externalNo={}, httpStatus={}, bizCode={}, bizMsg={}",
                        task.getId(), order.getId(), externalNo, ex.getHttpStatus(), ex.getBizCode(), ex.getBizMsg());
            }
            orderMapper.updateSubmissionResult(order.getId(), "submit_failed", null,
                    null, null, requestPayload, responsePayload);
            return SubmitResult.failure(
                    ex.getHttpStatus() == 0 ? 500 : ex.getHttpStatus(),
                    requestPayload,
                    responsePayload,
                    vendorError(ex),
                    failureKind(ex),
                    ex.isRetryable()
            );
        } catch (Exception ex) {
            log.error("Authority media order submit unexpected error, taskId={}, orderId={}", task.getId(), order.getId(), ex);
            orderMapper.updateSubmissionResult(order.getId(), "submit_failed", null,
                    null, null, requestPayload, null);
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return SubmitResult.failure(500, requestPayload, null,
                    "authority media order submit failed: " + message, FailureKind.UNKNOWN, false);
        }
    }

    private SubmitResult validateBeforeOrder(TargetContext.AuthorityMediaTarget target,
                                             AuthorityMediaResource resource,
                                             BigDecimal salingPrice) {
        if (StringUtils.hasText(target.previewUrl()) && !isValidHttpUrl(target.previewUrl())) {
            return failure(400, "previewUrlBase is not a valid http(s) URL", FailureKind.VALIDATION, false);
        }
        if (!StringUtils.hasText(resource.getExternalResourceId()) || !resource.getExternalResourceId().matches("\\d+")) {
            return failure(400, "authority media resource id is invalid", FailureKind.VALIDATION, false);
        }
        BigDecimal resourcePrice = defaultPrice(resource);
        if (salingPrice.compareTo(resourcePrice) < 0) {
            return failure(400, "saling_price must be greater than or equal to resource price", FailureKind.VALIDATION, false);
        }
        if (StringUtils.hasText(target.publishedAt())) {
            try {
                LocalDateTime publishedAt = LocalDateTime.parse(target.publishedAt().trim(), VENDOR_DATE_TIME);
                if (publishedAt.isBefore(LocalDateTime.now().plusHours(1))) {
                    return failure(400, "publishedAt must be at least 1 hour from now", FailureKind.VALIDATION, false);
                }
            } catch (DateTimeParseException ex) {
                return failure(400, "publishedAt format invalid, expected yyyy-MM-dd HH:mm:ss", FailureKind.VALIDATION, false);
            }
        }
        return null;
    }

    private SubmitResult precheckBalance(AuthorityMediaResource resource, BigDecimal salingPrice) {
        BigDecimal balance = cachedBalance();
        if (balance == null) {
            return null;
        }
        BigDecimal required = salingPrice.max(defaultPrice(resource))
                .multiply(properties.getBalanceSafetyFactor() == null ? BigDecimal.ONE : properties.getBalanceSafetyFactor())
                .setScale(2, RoundingMode.HALF_UP);
        if (balance.compareTo(required) < 0) {
            return failure(400, "Meititejia balance is insufficient", FailureKind.VALIDATION, false);
        }
        return null;
    }

    private BigDecimal cachedBalance() {
        CachedBalance current = cachedBalance;
        long now = Instant.now().getEpochSecond();
        if (current != null && current.expiresAtEpochSecond() > now) {
            return current.balance();
        }
        try {
            JsonNode response = client.userInfo();
            BigDecimal balance = parseBalance(response);
            cachedBalance = new CachedBalance(balance, now + BALANCE_CACHE_SECONDS);
            return balance;
        } catch (Exception ex) {
            log.warn("Meititejia balance precheck failed; continue order submission with degraded balance check: {}", ex.getMessage());
            cachedBalance = new CachedBalance(null, now + BALANCE_FAILURE_CACHE_SECONDS);
            return null;
        }
    }

    private BigDecimal parseBalance(JsonNode response) {
        JsonNode data = response == null ? null : response.path("data");
        for (JsonNode node : new JsonNode[]{data, response}) {
            for (String field : new String[]{"money", "balance", "user_money", "amount"}) {
                BigDecimal value = decimal(node, field);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode child = node == null ? null : node.get(field);
        if (child == null || child.isNull() || !StringUtils.hasText(child.asText())) {
            return null;
        }
        try {
            return new BigDecimal(child.asText().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private AuthorityMediaOrder ensureOrder(ArticleDraft article,
                                            Project project,
                                            DistributionTask task,
                                            AuthorityMediaResource resource,
                                            Long operatorId) {
        AuthorityMediaOrder existing = orderMapper.selectByDistributionTaskId(task.getId());
        if (existing != null) {
            return existing;
        }
        AuthorityMediaOrder order = new AuthorityMediaOrder();
        order.setDistributionTaskId(task.getId());
        order.setArticleId(article.getId());
        order.setProjectId(project.getId());
        order.setResourceId(resource.getId());
        order.setResourceType(resource.getResourceType());
        order.setSubmitStatus("created");
        order.setLockVersion(0);
        order.setCreatedBy(operatorId);
        orderMapper.insert(order);
        return order;
    }

    private String ensureExternalNo(AuthorityMediaOrder order) {
        if (StringUtils.hasText(order.getExternalNo())) {
            return order.getExternalNo();
        }
        String externalNo = "AM-" + order.getId();
        orderMapper.assignExternalNoIfAbsent(order.getId(), externalNo);
        order.setExternalNo(externalNo);
        return externalNo;
    }

    private BigDecimal normalizeSalingPrice(BigDecimal salingPrice, AuthorityMediaResource resource) {
        if (salingPrice != null && salingPrice.compareTo(BigDecimal.ZERO) > 0) {
            return salingPrice;
        }
        return defaultPrice(resource);
    }

    private BigDecimal defaultPrice(AuthorityMediaResource resource) {
        return resource.getPrice() == null ? BigDecimal.ZERO : resource.getPrice();
    }

    private String buildContent(String contentMarkdown, String previewUrl) {
        String safeUrl = htmlEscape(previewUrl.trim());
        String link = "稿件链接 : <a href=\"" + safeUrl + "\">" + safeUrl + "</a>";
        if (properties.getContentMode() == MeititejiaProperties.ContentMode.BODY_WITH_LINK && StringUtils.hasText(contentMarkdown)) {
            return contentMarkdown.trim() + "\n\n" + link;
        }
        return link;
    }

    private Map<String, Object> orderParams(MeititejiaClient.NewsMediaOrderRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", request.title());
        params.put("content", request.content());
        params.put("mid", request.mediaId());
        params.put("no", request.externalNo());
        params.put("remark", request.remark());
        params.put("published_at", request.publishedAt());
        params.put("saling_price", request.salingPrice());
        return params;
    }

    private String requestPayload(Map<String, Object> params) {
        try {
            return objectMapper.writeValueAsString(client.buildAuditPayload(params));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build authority media request payload", ex);
        }
    }

    private SubmitResult failure(int statusCode, String message, String failureKind, boolean retryable) {
        return SubmitResult.failure(statusCode, null, null, message, failureKind, retryable);
    }

    private String vendorError(MeititejiaApiException ex) {
        if (StringUtils.hasText(ex.getBizMsg())) {
            return ex.getBizMsg();
        }
        if (StringUtils.hasText(ex.getMessage())) {
            return ex.getMessage();
        }
        return "Meititejia order submit failed";
    }

    private String failureKind(MeititejiaApiException ex) {
        if (ex.getHttpStatus() == 401 || ex.getHttpStatus() == 403) {
            return FailureKind.AUTH;
        }
        if (ex.getHttpStatus() == 429) {
            return FailureKind.RATE_LIMIT;
        }
        if (ex.getHttpStatus() >= 500 || ex.getHttpStatus() == 0) {
            return ex.isRetryable() ? FailureKind.NETWORK_ERROR : FailureKind.SERVER_ERROR;
        }
        if (Integer.valueOf(201).equals(ex.getBizCode())) {
            String msg = ex.getBizMsg() == null ? "" : ex.getBizMsg();
            if (msg.contains("签名")) {
                return FailureKind.AUTH;
            }
            if (msg.contains("余额") || msg.contains("媒体") || msg.contains("下架") || msg.contains("已提交")) {
                return FailureKind.VALIDATION;
            }
            return FailureKind.PLATFORM;
        }
        return FailureKind.PLATFORM;
    }

    private Long mediaId(AuthorityMediaResource resource) {
        try {
            return Long.parseLong(resource.getExternalResourceId());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("authority media resource id is not numeric: " + resource.getExternalResourceId(), ex);
        }
    }

    private boolean isValidHttpUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && StringUtils.hasText(uri.getHost());
        } catch (Exception ex) {
            return false;
        }
    }

    private String htmlEscape(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private boolean contains(String value, String pattern) {
        return value != null && value.contains(pattern);
    }

    private record CachedBalance(BigDecimal balance, long expiresAtEpochSecond) {
    }
}
