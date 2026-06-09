package com.huanjing.geo.module.content.douyin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.entity.DouyinWebhookEvent;
import com.huanjing.geo.module.content.mapper.DouyinWebhookEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DouyinWebhookService {
    private static final String SUCCESS = "success";
    private static final String STATUS_RECEIVED = "received";

    private final ObjectMapper objectMapper;
    private final DouyinWebhookEventMapper eventMapper;

    public String handleWebhook(String rawBody) {
        JsonNode root = parseJson(rawBody);
        String challenge = findChallenge(root);
        persistEvent(root, rawBody, challenge);
        if (StringUtils.hasText(challenge)) {
            log.info("Douyin webhook challenge verified");
            return challenge;
        }

        log.info("Douyin webhook event received event={} rawBody={}", findFirstText(root, "event", "event_type", "type"), rawBody);
        return SUCCESS;
    }

    private void persistEvent(JsonNode root, String rawBody, String challenge) {
        DouyinWebhookEvent event = new DouyinWebhookEvent();
        event.setEventId(findFirstText(root, "event_id", "eventId", "event_id_str", "msg_id", "message_id"));
        event.setEventType(findFirstText(root, "event", "event_type", "eventType", "type"));
        event.setChallenge(challenge);
        event.setRawPayload(StringUtils.hasText(rawBody) ? rawBody : "{}");
        event.setProcessStatus(STATUS_RECEIVED);
        eventMapper.insert(event);
    }

    private JsonNode parseJson(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            log.warn("Douyin webhook payload is not valid JSON rawBody={}", rawBody);
            return objectMapper.createObjectNode();
        }
    }

    private String findChallenge(JsonNode root) {
        return findFirstText(root, "CHALLENGE", "challenge", "Challenge");
    }

    private String findFirstText(JsonNode root, String... names) {
        if (root == null || !root.isObject()) {
            return null;
        }
        for (String name : names) {
            JsonNode direct = root.get(name);
            if (direct != null && direct.isValueNode() && StringUtils.hasText(direct.asText())) {
                return direct.asText();
            }
        }
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            for (String name : names) {
                if (name.equalsIgnoreCase(field.getKey())
                        && field.getValue().isValueNode()
                        && StringUtils.hasText(field.getValue().asText())) {
                    return field.getValue().asText();
                }
            }
        }
        return null;
    }
}
