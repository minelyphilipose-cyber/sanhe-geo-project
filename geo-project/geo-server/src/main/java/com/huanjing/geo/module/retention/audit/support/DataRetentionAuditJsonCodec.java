package com.huanjing.geo.module.retention.audit.support;

import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ExecutionBudgetUsage;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.EvidenceDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class DataRetentionAuditJsonCodec {

    private static final int MAX_JSON_BYTES = 8192;
    private static final int MAX_DEPTH = 3;
    private static final int MAX_KEYS = 64;
    private static final Set<String> DENIED_KEYS = Set.of(
            "cookie", "authorization", "token", "secret", "password", "credential",
            "payload", "prompt", "rawresponse");

    private final ObjectMapper stableMapper;
    private final ObjectMapper strictEvidenceMapper;

    public DataRetentionAuditJsonCodec(ObjectMapper objectMapper) {
        this.stableMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        JsonFactory strictFactory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.strictEvidenceMapper = new ObjectMapper(strictFactory);
    }

    public String writeStableObject(Map<String, Object> value, boolean nullable) {
        if (value == null || value.isEmpty()) {
            return nullable ? null : "{}";
        }
        try {
            validateGeneratedValue(value, 1);
            String json = stableMapper.writeValueAsString(value);
            ensureByteLimit(json.getBytes(StandardCharsets.UTF_8), DataRetentionAuditErrors.JSON_TOO_LARGE);
            return json;
        } catch (JsonProcessingException ex) {
            throw invalid(DataRetentionAuditErrors.CONTEXT_INVALID, "failed to serialize retention audit JSON", ex);
        }
    }

    public ValidatedEvidence validateEvidence(EvidenceDocument document) {
        if (document == null || document.evidenceUtf8JsonBytes() == null || document.sha256Digest() == null
                || document.sha256Digest().length != 32) {
            throw invalid(DataRetentionAuditErrors.RECONCILIATION_EVIDENCE_INVALID,
                    "evidence document is incomplete", null);
        }
        byte[] bytes = document.evidenceUtf8JsonBytes();
        ensureByteLimit(bytes, DataRetentionAuditErrors.RECONCILIATION_EVIDENCE_INVALID);
        if (bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) {
            throw invalid(DataRetentionAuditErrors.RECONCILIATION_EVIDENCE_INVALID,
                    "evidence must not contain a UTF-8 BOM", null);
        }
        decodeStrictUtf8(bytes);
        byte[] calculated = DataRetentionCanonicalEncoder.sha256(bytes);
        if (!MessageDigestSupport.constantTimeEquals(calculated, document.sha256Digest())) {
            throw invalid(DataRetentionAuditErrors.RECONCILIATION_EVIDENCE_INVALID,
                    "evidence SHA-256 digest does not match the supplied bytes", null);
        }
        try (JsonParser parser = strictEvidenceMapper.getFactory().createParser(bytes)) {
            JsonNode root = strictEvidenceMapper.readTree(parser);
            if (root == null || !root.isObject() || parser.nextToken() != null) {
                throw invalid(DataRetentionAuditErrors.RECONCILIATION_EVIDENCE_INVALID,
                        "evidence must contain exactly one JSON object", null);
            }
            validateNode(root, 1);
            return new ValidatedEvidence(calculated, strictEvidenceMapper.writeValueAsString(root));
        } catch (DataRetentionAuditException ex) {
            throw ex;
        } catch (IOException ex) {
            throw invalid(DataRetentionAuditErrors.RECONCILIATION_EVIDENCE_INVALID,
                    "evidence is not valid strict JSON", ex);
        }
    }

    public String truncateUtf8(String value, int maxBytes) {
        if (value == null) {
            return null;
        }
        if (value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        int bytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String unit = new String(Character.toChars(codePoint));
            int unitBytes = unit.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + unitBytes > maxBytes) {
                break;
            }
            result.append(unit);
            bytes += unitBytes;
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private void validateNode(JsonNode node, int depth) {
        if (depth > MAX_DEPTH) {
            throw invalid(DataRetentionAuditErrors.RECONCILIATION_EVIDENCE_INVALID,
                    "evidence JSON nesting exceeds the permitted depth", null);
        }
        if (node.isObject()) {
            int count = 0;
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                count++;
                if (count > MAX_KEYS || DENIED_KEYS.contains(field.getKey().toLowerCase(Locale.ROOT))) {
                    throw invalid(DataRetentionAuditErrors.RECONCILIATION_EVIDENCE_INVALID,
                            "evidence contains too many keys or a sensitive key", null);
                }
                validateNode(field.getValue(), depth + 1);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                validateNode(element, depth + 1);
            }
        }
    }

    private void validateGeneratedValue(Object value, int depth) {
        if (depth > MAX_DEPTH) {
            throw invalid(DataRetentionAuditErrors.CONTEXT_INVALID,
                    "retention audit JSON nesting exceeds the permitted depth", null);
        }
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return;
        }
        if (value instanceof ExecutionBudgetUsage usage) {
            if (usage.schemaVersion() != 1 || usage.committedTransactionCount() < 0
                    || usage.committedTransactionCount() > 1 || usage.chargedCandidateItems() < 0) {
                throw invalid(DataRetentionAuditErrors.CONTEXT_INVALID,
                        "retention execution budget usage cannot be negative", null);
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > MAX_KEYS) {
                throw invalid(DataRetentionAuditErrors.CONTEXT_INVALID,
                        "retention audit JSON contains too many keys", null);
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)
                        || DENIED_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                    throw invalid(DataRetentionAuditErrors.CONTEXT_INVALID,
                            "retention audit JSON contains an invalid or sensitive key", null);
                }
                validateGeneratedValue(entry.getValue(), depth + 1);
            }
            return;
        }
        if (value instanceof List<?> list) {
            if (list.stream().anyMatch(item -> item instanceof Map<?, ?> || item instanceof List<?>)) {
                throw invalid(DataRetentionAuditErrors.CONTEXT_INVALID,
                        "retention audit JSON arrays must contain scalars only", null);
            }
            for (Object item : list) {
                validateGeneratedValue(item, depth + 1);
            }
            return;
        }
        throw invalid(DataRetentionAuditErrors.CONTEXT_INVALID,
                "retention audit JSON accepts only maps, scalar arrays and scalar values", null);
    }

    private void decodeStrictUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException ex) {
            throw invalid(DataRetentionAuditErrors.RECONCILIATION_EVIDENCE_INVALID,
                    "evidence is not strict UTF-8", ex);
        }
    }

    private void ensureByteLimit(byte[] bytes, String errorCode) {
        if (bytes.length > MAX_JSON_BYTES) {
            throw invalid(errorCode, "retention audit JSON exceeds 8192 UTF-8 bytes", null);
        }
    }

    private DataRetentionAuditException invalid(String code, String message, Throwable cause) {
        return cause == null ? new DataRetentionAuditException(code, message)
                : new DataRetentionAuditException(code, message, cause);
    }

    public record ValidatedEvidence(byte[] sha256Digest, String diagnosticJson) {
        public ValidatedEvidence {
            sha256Digest = sha256Digest.clone();
        }

        @Override
        public byte[] sha256Digest() {
            return sha256Digest.clone();
        }
    }

    private static final class MessageDigestSupport {
        private MessageDigestSupport() {
        }

        private static boolean constantTimeEquals(byte[] left, byte[] right) {
            return java.security.MessageDigest.isEqual(left, right);
        }
    }
}
