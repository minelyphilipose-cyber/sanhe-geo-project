package com.huanjing.geo.module.dispatch.websearch.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class PollPayloadProtector {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "authorization", "api_key", "apikey", "token", "secret", "credential", "password");
    private static final int MAX_SANITIZED_LENGTH = 64_000;

    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec encryptionKey;

    public PollPayloadProtector(ObjectMapper objectMapper,
                                @Value("${geo.dispatch.poll-audit-payload-secret:}") String secret) {
        this.objectMapper = objectMapper;
        this.encryptionKey = secret == null || secret.isBlank() ? null : deriveKey(secret);
    }

    public String sanitize(String payload) {
        if (payload == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            redact(root);
            return truncate(objectMapper.writeValueAsString(root));
        } catch (Exception ignored) {
            return truncate(payload.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._-]+", "$1***"));
        }
    }

    public String encryptIfConfigured(String payload) {
        if (payload == null || encryptionKey == null) {
            return null;
        }
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return "ENCv1:" + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt poll audit payload", ex);
        }
    }

    public String keyVersion() {
        return encryptionKey == null ? null : "poll-audit-v1";
    }

    private void redact(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String normalized = field.getKey().toLowerCase(Locale.ROOT).replace("-", "_");
                if (SENSITIVE_KEYS.stream().anyMatch(normalized::contains)) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) node).put(field.getKey(), "***");
                } else {
                    redact(field.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::redact);
        }
    }

    private SecretKeySpec deriveKey(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize poll payload encryption", ex);
        }
    }

    private String truncate(String value) {
        return value.length() <= MAX_SANITIZED_LENGTH ? value : value.substring(0, MAX_SANITIZED_LENGTH);
    }
}
