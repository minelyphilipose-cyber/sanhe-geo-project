package com.huanjing.geo.module.content.authoritymedia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.entity.AuthorityMediaOrder;
import com.huanjing.geo.module.content.mapper.AuthorityMediaOrderMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.service.CompanyChannelQuotaService;
import com.huanjing.geo.module.content.service.adapter.FailureKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorityMediaOrderStatusService {

    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final int MAX_BATCH_SIZE = 100;
    private static final int STALE_ALERT_DAYS = 7;

    private final AuthorityMediaOrderMapper orderMapper;
    private final DistributionTaskMapper distributionTaskMapper;
    private final CompanyChannelQuotaService companyChannelQuotaService;
    private final MeititejiaClient client;
    private final ObjectMapper objectMapper;

    public StatusCheckResult checkDueNewsMediaOrders(int limit) {
        int batchSize = Math.min(Math.max(limit <= 0 ? DEFAULT_BATCH_SIZE : limit, 1), MAX_BATCH_SIZE);
        List<AuthorityMediaOrder> orders = orderMapper.selectDueForStatusCheck(LocalDateTime.now(), batchSize);
        if (orders.isEmpty()) {
            return new StatusCheckResult(0, 0, 0, 0);
        }
        Map<String, AuthorityMediaOrder> byNo = orders.stream()
                .filter(order -> StringUtils.hasText(order.getExternalNo()))
                .collect(Collectors.toMap(
                        order -> MeititejiaClient.vendorOrderNo(order.getExternalNo()),
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        if (byNo.isEmpty()) {
            return new StatusCheckResult(orders.size(), 0, 0, 0);
        }
        JsonNode response = client.queryOrders(MeititejiaResourceType.NEWS_MEDIA, new ArrayList<>(byNo.keySet()));
        List<JsonNode> items = extractOrderItems(response);
        int updated = 0;
        int terminal = 0;
        int missing = 0;
        for (JsonNode item : items) {
            String externalNo = MeititejiaClient.vendorOrderNo(externalNo(item));
            AuthorityMediaOrder order = byNo.remove(externalNo);
            if (order == null) {
                continue;
            }
            RemoteOrderStatus status = toRemoteStatus(item);
            if (applyRemoteStatus(order, status, item)) {
                updated++;
                if (status.isTerminal()) {
                    terminal++;
                }
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (AuthorityMediaOrder missingOrder : byNo.values()) {
            missing++;
            scheduleNextCheck(missingOrder, now, "remote order not returned");
        }
        return new StatusCheckResult(orders.size(), updated, terminal, missing);
    }

    @Transactional
    public boolean applyRemoteStatus(AuthorityMediaOrder order, RemoteOrderStatus status, JsonNode raw) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextCheckAt = status.isTerminal() ? null : nextCheckAt(order, now);
        int affected = orderMapper.updateRemoteStatus(
                order.getId(),
                order.getLockVersion(),
                status.status(),
                status.text(),
                status.publishedUrl(),
                status.rejectReason(),
                status.status() == 2 ? now : null,
                now,
                nextCheckAt,
                rawJson(raw),
                null
        );
        if (affected != 1) {
            log.warn("Authority media order status update skipped due to lock version mismatch, orderId={}", order.getId());
            return false;
        }
        if (status.status() == 2) {
            distributionTaskMapper.markAuthorityMediaPublished(order.getDistributionTaskId(), status.publishedUrl(), now);
        } else if (status.status() == -1 || status.status() == -2) {
            int taskUpdated = distributionTaskMapper.markAuthorityMediaFailed(
                    order.getDistributionTaskId(),
                    status.status() == -1 ? FailureKind.VALIDATION : FailureKind.PLATFORM,
                    trimError(status.rejectReason() == null ? status.text() : status.rejectReason()),
                    now
            );
            if (taskUpdated == 1) {
                companyChannelQuotaService.refundConfirmedDistribution(order.getDistributionTaskId());
            }
        } else if (isStale(order, now)) {
            log.error("Authority media order still not terminal after {} days, orderId={}, externalNo={}",
                    STALE_ALERT_DAYS, order.getId(), order.getExternalNo());
        }
        return true;
    }

    @Transactional
    public void scheduleNextCheck(AuthorityMediaOrder order, LocalDateTime now, String reason) {
        int affected = orderMapper.updateRemoteStatus(
                order.getId(),
                order.getLockVersion(),
                order.getRemoteStatus(),
                order.getRemoteStatusText(),
                order.getPublishedUrl(),
                order.getRejectReason(),
                order.getRemotePublishedAt(),
                now,
                nextCheckAt(order, now),
                reason,
                null
        );
        if (affected != 1) {
            log.warn("Authority media missing-order next check update skipped due to lock version mismatch, orderId={}", order.getId());
        }
    }

    private RemoteOrderStatus toRemoteStatus(JsonNode item) {
        int status = intValue(item, "status", 0);
        String text = switch (status) {
            case -2 -> "已删除";
            case -1 -> "已拒稿";
            case 0 -> "未处理";
            case 1 -> "发布中";
            case 2 -> "已完成";
            default -> "未知";
        };
        String publishedUrl = firstText(item, "url", "published_url", "publish_url", "link");
        String rejectReason = firstText(item, "reason", "reject_reason", "msg", "remark");
        return new RemoteOrderStatus(status, text, publishedUrl, rejectReason);
    }

    private List<JsonNode> extractOrderItems(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return List.of();
        }
        JsonNode data = root.path("data");
        if (data.isArray()) {
            return toList(data);
        }
        JsonNode nested = firstArray(data.path("data"), data.path("list"), data.path("rows"), root.path("list"));
        if (nested != null) {
            return toList(nested);
        }
        if (data.isMissingNode() || data.isNull() || (data.isObject() && data.isEmpty())) {
            return List.of();
        }
        log.warn("Meititejia query_media_order response shape unexpected: {}", root);
        throw new IllegalStateException("Unexpected Meititejia query_media_order response shape");
    }

    private List<JsonNode> toList(JsonNode array) {
        List<JsonNode> result = new ArrayList<>();
        array.forEach(result::add);
        return result;
    }

    private JsonNode firstArray(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (candidate != null && candidate.isArray()) {
                return candidate;
            }
        }
        return null;
    }

    private String externalNo(JsonNode item) {
        return firstText(item, "no3", "no", "order_no", "external_no");
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode child = node == null ? null : node.get(field);
            if (child != null && !child.isNull() && StringUtils.hasText(child.asText())) {
                return child.asText().trim();
            }
        }
        return null;
    }

    private int intValue(JsonNode node, String field, int defaultValue) {
        JsonNode child = node == null ? null : node.get(field);
        if (child == null || child.isNull() || !StringUtils.hasText(child.asText())) {
            return defaultValue;
        }
        if (child.canConvertToInt()) {
            return child.asInt();
        }
        try {
            return Integer.parseInt(child.asText().trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private LocalDateTime nextCheckAt(AuthorityMediaOrder order, LocalDateTime now) {
        LocalDateTime submittedAt = Objects.requireNonNullElse(order.getSubmittedAt(), order.getCreatedAt());
        Duration age = submittedAt == null ? Duration.ZERO : Duration.between(submittedAt, now);
        if (age.compareTo(Duration.ofHours(1)) < 0) {
            return now.plusMinutes(5);
        }
        if (age.compareTo(Duration.ofHours(24)) < 0) {
            return now.plusMinutes(30);
        }
        return now.plusHours(2);
    }

    private boolean isStale(AuthorityMediaOrder order, LocalDateTime now) {
        LocalDateTime submittedAt = Objects.requireNonNullElse(order.getSubmittedAt(), order.getCreatedAt());
        return submittedAt != null && submittedAt.plusDays(STALE_ALERT_DAYS).isBefore(now);
    }

    private String trimError(String value) {
        if (!StringUtils.hasText(value)) {
            return "authority media order failed";
        }
        String text = value.trim();
        return text.length() <= 900 ? text : text.substring(0, 900);
    }

    private String rawJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            return String.valueOf(node);
        }
    }

    public record StatusCheckResult(int selected, int updated, int terminal, int missing) {
    }

    public record RemoteOrderStatus(int status, String text, String publishedUrl, String rejectReason) {
        boolean isTerminal() {
            return status == -2 || status == -1 || status == 2;
        }
    }
}
