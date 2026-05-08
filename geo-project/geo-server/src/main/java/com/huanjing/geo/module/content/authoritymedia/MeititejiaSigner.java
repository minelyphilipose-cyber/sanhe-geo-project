package com.huanjing.geo.module.content.authoritymedia;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Signature and form-body helpers for Meititejia requests.
 *
 * <p>Invariant: field set in the HTTP body equals the field set in the
 * canonical signature string union {@code signature}. Empty values and legacy
 * signature keys are excluded from both places.</p>
 */
public final class MeititejiaSigner {

    public static final String SECRET_ID = "secret_id";
    public static final String TIMESTAMP = "timestamp";
    public static final String SIGNATURE = "signature";
    private static final String LEGACY_SIGN = "sign";

    private MeititejiaSigner() {
    }

    public static Map<String, String> signedParameters(Map<String, ?> source,
                                                       String secretId,
                                                       String secretKey,
                                                       long epochSeconds) {
        if (!StringUtils.hasText(secretId)) {
            throw new IllegalArgumentException("secretId is required");
        }
        if (!StringUtils.hasText(secretKey)) {
            throw new IllegalArgumentException("secretKey is required");
        }
        Map<String, String> params = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((key, value) -> {
                String normalized = normalizeValue(value);
                if (StringUtils.hasText(key) && normalized != null) {
                    params.put(key, normalized);
                }
            });
        }
        params.entrySet().removeIf(entry -> isSignatureKey(entry.getKey()));
        params.put(SECRET_ID, secretId.trim());
        params.put(TIMESTAMP, String.valueOf(epochSeconds));
        params.put(SIGNATURE, signNormalized(params, secretKey));
        return params;
    }

    public static String signNormalized(Map<String, String> params, String secretKey) {
        if (!StringUtils.hasText(secretKey)) {
            throw new IllegalArgumentException("secretKey is required");
        }
        String canonical = canonicalString(params);
        return md5Upper(canonical + "&key=" + secretKey.trim());
    }

    public static String canonicalString(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        return new TreeMap<>(params).entrySet().stream()
                .filter(entry -> includeInCanonical(entry.getKey(), entry.getValue()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    public static String formBody(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        return params.entrySet().stream()
                .filter(entry -> includeInBody(entry.getKey(), entry.getValue()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    /**
     * Matches the vendor document's PHP-style urlencode expectation for fields
     * that must be encoded before signing and before form-body construction:
     * spaces become '+', and '*' / '~' remain readable.
     */
    public static String phpUrlencode(String value) {
        if (value == null) {
            return null;
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("%7E", "~")
                .replace("%2A", "*");
    }

    public static String normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return normalizeDecimal(decimal);
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof CharSequence) {
            String text = String.valueOf(value);
            return text.isEmpty() ? null : text;
        }
        String text = value.toString();
        return text.isEmpty() ? null : text;
    }

    public static String normalizeDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static boolean includeInCanonical(String key, String value) {
        return includeBusinessField(key, value) && !isSignatureKey(key);
    }

    private static boolean includeInBody(String key, String value) {
        if (!includeBusinessField(key, value)) {
            return false;
        }
        String normalizedKey = normalizedKey(key);
        if (LEGACY_SIGN.equals(normalizedKey)) {
            return false;
        }
        if (SIGNATURE.equals(normalizedKey)) {
            return SIGNATURE.equals(key);
        }
        return true;
    }

    private static boolean includeBusinessField(String key, String value) {
        if (!StringUtils.hasText(key) || value == null || value.isEmpty()) {
            return false;
        }
        return true;
    }

    private static boolean isSignatureKey(String key) {
        String normalizedKey = normalizedKey(key);
        return SIGNATURE.equals(normalizedKey) || LEGACY_SIGN.equals(normalizedKey);
    }

    private static String normalizedKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private static String md5Upper(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(String.format("%02x", b & 0xff));
            }
            return out.toString().toUpperCase(Locale.ROOT);
        } catch (Exception ex) {
            throw new IllegalStateException("MD5 signature failed", ex);
        }
    }
}
