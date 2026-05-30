package com.huanjing.geo.common.log;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpLogSanitizerTest {

    @Test
    void masksExtensionAndHelperHeaders() {
        Map<String, String> sanitized = HttpLogSanitizer.maskHeaders(Map.of(
                "x-ext-token", "ext.secret",
                "X-Geo-Helper-Access", "helper.session.1",
                "X-Geo-Helper-Signature", "signature",
                "X-Geo-Helper-Nonce", "nonce"
        ));

        assertThat(sanitized.get("x-ext-token")).isEqualTo("***");
        assertThat(sanitized.get("X-Geo-Helper-Access")).isEqualTo("***");
        assertThat(sanitized.get("X-Geo-Helper-Signature")).isEqualTo("***");
        assertThat(sanitized.get("X-Geo-Helper-Nonce")).isEqualTo("nonce");
    }

    @Test
    void masksSensitiveJsonFields() {
        String body = """
                {
                  "accessToken": "access-secret",
                  "backendToken": "backend-secret",
                  "fillToken": "fill-secret",
                  "hmacSecret": "hmac-secret",
                  "pairingCode": "ABCDE-12345",
                  "cookie": "a=b"
                }
                """;

        String sanitized = HttpLogSanitizer.maskBody(body, "application/json");

        assertThat(sanitized).doesNotContain("access-secret")
                .doesNotContain("backend-secret")
                .doesNotContain("fill-secret")
                .doesNotContain("hmac-secret")
                .doesNotContain("ABCDE-12345")
                .doesNotContain("a=b");
        assertThat(sanitized).contains("\"accessToken\": \"***\"")
                .contains("\"backendToken\": \"***\"")
                .contains("\"fillToken\": \"***\"")
                .contains("\"hmacSecret\": \"***\"")
                .contains("\"pairingCode\": \"***\"")
                .contains("\"cookie\": \"***\"");
    }
}
