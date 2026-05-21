package com.huanjing.geo.module.content.authoritymedia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.entity.AuthorityMediaResource;
import com.huanjing.geo.module.content.mapper.AuthorityMediaResourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorityMediaResourceSyncService {

    private static final MeititejiaResourceType NEWS_MEDIA = MeititejiaResourceType.NEWS_MEDIA;
    private static final int DEFAULT_PAGE_LIMIT = 200;
    private static final long INCREMENTAL_LOOKBACK_SECONDS = 60L;
    private static final int LARGE_RECONCILE_ID_COUNT = 5000;
    private static final ZoneId VENDOR_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter VENDOR_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MeititejiaClient client;
    private final AuthorityMediaResourceMapper resourceMapper;
    private final MeititejiaProperties properties;
    private final ObjectMapper objectMapper;

    public SyncResult syncNewsMediaFull() {
        return syncNewsMediaPages(null);
    }

    public SyncResult syncNewsMediaIncremental() {
        Long maxUptime = resourceMapper.selectMaxUptime(NEWS_MEDIA.name());
        Long cursor = maxUptime == null || maxUptime <= INCREMENTAL_LOOKBACK_SECONDS
                ? null
                : maxUptime - INCREMENTAL_LOOKBACK_SECONDS;
        return syncNewsMediaPages(cursor);
    }

    public ReconcileResult reconcileNewsMediaIds() {
        JsonNode response = client.getIds(NEWS_MEDIA);
        Set<String> activeIds = extractIds(response);
        if (activeIds.isEmpty()) {
            log.warn("Meititejia get_ids returned no NEWS_MEDIA ids; skip soft-delete reconciliation to avoid accidental mass delete");
            return new ReconcileResult(0, 0);
        }
        if (activeIds.size() > LARGE_RECONCILE_ID_COUNT) {
            log.warn("Meititejia NEWS_MEDIA get_ids returned {} ids; current NOT IN reconciliation may need temp-table optimization",
                    activeIds.size());
        }
        int marked = resourceMapper.markDeletedExcept(NEWS_MEDIA.name(), activeIds, LocalDateTime.now());
        return new ReconcileResult(activeIds.size(), marked);
    }

    public RefreshResult refreshNewsMediaResourceIfStale(Long resourceId) {
        if (resourceId == null) {
            throw new IllegalArgumentException("resourceId is required");
        }
        AuthorityMediaResource existing = resourceMapper.selectById(resourceId);
        if (existing == null) {
            return RefreshResult.notFound(resourceId);
        }
        if (!NEWS_MEDIA.name().equals(existing.getResourceType())) {
            return RefreshResult.skipped(resourceId, "not NEWS_MEDIA");
        }
        if (!isStale(existing.getUpdatedAt())) {
            return RefreshResult.skipped(resourceId, "fresh");
        }
        Long remoteId = parseRemoteId(existing.getExternalResourceId());
        if (remoteId == null) {
            return RefreshResult.skipped(resourceId, "external resource id is not numeric");
        }

        JsonNode response;
        try {
            response = client.listResources(NEWS_MEDIA, 1, DEFAULT_PAGE_LIMIT, remoteId, null);
        } catch (MeititejiaApiException ex) {
            log.warn("Meititejia NEWS_MEDIA resource refresh failed; continue with local cache, resourceId={}, externalResourceId={}, httpStatus={}, bizCode={}, bizMsg={}",
                    resourceId, existing.getExternalResourceId(), ex.getHttpStatus(), ex.getBizCode(), ex.getBizMsg());
            return RefreshResult.skipped(resourceId, "remote refresh failed");
        }
        List<JsonNode> items = extractResourceItems(response);
        if (items.isEmpty()) {
            int marked = resourceMapper.markDeletedById(resourceId, LocalDateTime.now());
            return new RefreshResult(resourceId, false, marked > 0, "remote missing");
        }
        int upserted = upsertItems(items);
        return new RefreshResult(resourceId, upserted > 0, false, "refreshed");
    }

    private SyncResult syncNewsMediaPages(Long uptime) {
        int limit = properties.getSyncPageLimit() <= 0 ? DEFAULT_PAGE_LIMIT : properties.getSyncPageLimit();
        int page = 1;
        int pages = 0;
        int fetched = 0;
        int processed = 0;

        while (true) {
            JsonNode response = client.listResources(NEWS_MEDIA, page, limit, null, uptime);
            List<JsonNode> items = extractResourceItems(response);
            pages++;
            if (items.isEmpty()) {
                break;
            }
            fetched += items.size();
            processed += upsertItems(items);
            if (items.size() < limit) {
                break;
            }
            page++;
        }
        return new SyncResult(pages, fetched, processed, uptime);
    }

    private int upsertItems(List<JsonNode> items) {
        int upserted = 0;
        for (JsonNode item : items) {
            AuthorityMediaResource resource = toResource(item);
            if (!StringUtils.hasText(resource.getExternalResourceId())) {
                log.warn("Skip NEWS_MEDIA resource without id: {}", item);
                continue;
            }
            resourceMapper.upsert(resource);
            upserted++;
        }
        return upserted;
    }

    private AuthorityMediaResource toResource(JsonNode node) {
        AuthorityMediaResource resource = new AuthorityMediaResource();
        String externalId = text(node, "id");
        resource.setResourceType(NEWS_MEDIA.name());
        resource.setExternalResourceId(externalId);
        resource.setName(defaultString(firstText(node, "media_name", "name", "title"), "NEWS_MEDIA-" + externalId));
        resource.setPlatform(firstText(node, "platform", "media_type"));
        resource.setIndustry(firstText(node, "industry", "channel_type", "category"));
        resource.setProvince(firstText(node, "province", "area", "region"));
        resource.setPrice(decimal(node, "price"));
        resource.setStatus(integer(node, "status", 1));
        resource.setPcWeight(integer(node, "pc_weight", null));
        resource.setMWeight(integer(node, "m_weight", null));
        resource.setNewsResource(integer(node, "news_resource", null));
        resource.setEntranceLevel(integer(node, "entrance_level", null));
        resource.setIncludeCondition(integer(node, "include_condition", null));
        resource.setPublicationTime(integer(node, "publication_time", null));
        resource.setWeekendPublish(integer(node, "weekend_publish", null));
        resource.setPublishRate(firstText(node, "publish_rate", "publishRate"));
        resource.setInclusionRate(integer(node, "inclusion_rate", null));
        resource.setRemark(firstText(node, "remark", "remarks", "description"));
        resource.setUptime(longValue(node, "uptime"));
        resource.setRawPayload(rawJson(node));
        return resource;
    }

    private List<JsonNode> extractResourceItems(JsonNode root) {
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
        log.warn("Meititejia NEWS_MEDIA list response shape unexpected: {}", root);
        throw new IllegalStateException("Unexpected Meititejia NEWS_MEDIA list response shape");
    }

    private Set<String> extractIds(JsonNode root) {
        JsonNode data = root == null ? null : root.path("data");
        JsonNode array = firstArray(data, data == null ? null : data.path("ids"), data == null ? null : data.path("data"),
                data == null ? null : data.path("list"), root == null ? null : root.path("ids"));
        Set<String> ids = new LinkedHashSet<>();
        if (array == null || !array.isArray()) {
            return ids;
        }
        array.forEach(item -> {
            if (item == null || item.isNull()) {
                return;
            }
            String id;
            if (item.isObject()) {
                id = firstText(item, "id", "mid", "media_id");
            } else if (item.isNumber() || item.isTextual()) {
                id = item.asText();
            } else {
                return;
            }
            if (StringUtils.hasText(id)) {
                ids.add(id.trim());
            }
        });
        return ids;
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

    private boolean isStale(LocalDateTime updatedAt) {
        if (updatedAt == null) {
            return true;
        }
        long threshold = Math.max(properties.getResourceStalenessThresholdMinutes(), 1);
        return updatedAt.plus(Duration.ofMinutes(threshold)).isBefore(LocalDateTime.now());
    }

    private Long parseRemoteId(String externalResourceId) {
        if (!StringUtils.hasText(externalResourceId)) {
            return null;
        }
        try {
            return Long.parseLong(externalResourceId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String text(JsonNode node, String field) {
        JsonNode child = node == null ? null : node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return child.asText();
    }

    private Integer integer(JsonNode node, String field, Integer defaultValue) {
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
            log.warn("NEWS_MEDIA resource {} field={} has non-integer value: {}", text(node, "id"), field, child.asText());
            return defaultValue;
        }
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode child = node == null ? null : node.get(field);
        if (child == null || child.isNull() || !StringUtils.hasText(child.asText())) {
            return null;
        }
        if (child.canConvertToLong()) {
            return child.asLong();
        }
        try {
            return Long.parseLong(child.asText().trim());
        } catch (NumberFormatException ex) {
            Long timestamp = dateTimeAsEpochSecond(child.asText());
            if (timestamp != null) {
                return timestamp;
            }
            log.warn("NEWS_MEDIA resource {} field={} has non-long value: {}", text(node, "id"), field, child.asText());
            return null;
        }
    }

    private Long dateTimeAsEpochSecond(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), VENDOR_DATE_TIME)
                    .atZone(VENDOR_ZONE)
                    .toEpochSecond();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode child = node == null ? null : node.get(field);
        if (child == null || child.isNull() || !StringUtils.hasText(child.asText())) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(child.asText().trim());
        } catch (NumberFormatException ex) {
            log.warn("NEWS_MEDIA resource {} field={} has non-decimal value: {}", text(node, "id"), field, child.asText());
            return BigDecimal.ZERO;
        }
    }

    private String rawJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize Meititejia resource payload", ex);
        }
    }

    /**
     * pages includes the final request used to confirm pagination completion.
     */
    public record SyncResult(int pages, int fetched, int processed, Long uptime) {
    }

    public record ReconcileResult(int activeIds, int markedDeleted) {
    }

    public record RefreshResult(Long resourceId, boolean refreshed, boolean deleted, String reason) {
        static RefreshResult notFound(Long resourceId) {
            return new RefreshResult(resourceId, false, false, "not found");
        }

        static RefreshResult skipped(Long resourceId, String reason) {
            return new RefreshResult(resourceId, false, false, reason);
        }
    }
}
