package com.huanjing.geo.module.content.authoritymedia;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * These tests intentionally assert UTF-8 percent-encoding output. The module
 * build pins sourceEncoding and compiler encoding to UTF-8 in pom.xml.
 */
class MeititejiaSignerTest {

    @Test
    void canonicalString_sortsByAsciiAndSkipsEmptyAndSignatureFields() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("c", "1");
        params.put("signature", "ignored");
        params.put("Signature", "ignored");
        params.put("empty", "");
        params.put("a", "2");
        params.put("sign", "ignored");
        params.put("b", "3");

        assertThat(MeititejiaSigner.canonicalString(params)).isEqualTo("a=2&b=3&c=1");
    }

    @Test
    void formBody_usesCanonicalFilteringButKeepsLowercaseSignature() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("a", "2");
        params.put("empty", "");
        params.put("Signature", "ignored");
        params.put("SIGN", "ignored");
        params.put("signature", "abc");

        assertThat(MeititejiaSigner.canonicalString(params)).isEqualTo("a=2");
        assertThat(MeititejiaSigner.formBody(params)).isEqualTo("a=2&signature=abc");
    }

    @Test
    void signNormalized_matchesVendorExampleShape() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("a", "2");
        params.put("b", "3");
        params.put("c", "1");

        assertThat(MeititejiaSigner.signNormalized(params, "xxxx"))
                .isEqualTo("E30D11CE0A7FF06C99E90E4076B08192");
    }

    @Test
    void signedParameters_addsCommonParamsAndSignatureLast() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "test");
        params.put("phone", "13800138000");

        Map<String, String> signed = MeititejiaSigner.signedParameters(params, "sidsidsid", "keykeykey", 1710000000L);

        assertThat(signed).containsEntry("secret_id", "sidsidsid");
        assertThat(signed).containsEntry("timestamp", "1710000000");
        assertThat(signed).containsKey("signature");
        assertThat(signed.keySet()).containsExactly("name", "phone", "secret_id", "timestamp", "signature");
    }

    @Test
    void signedParameters_matchesGoldenSignature() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "test");
        params.put("phone", "13800138000");

        Map<String, String> signed = MeititejiaSigner.signedParameters(params, "sidsidsid", "keykeykey", 1710000000L);

        assertThat(signed.get("signature")).isEqualTo("51B5DB8CAD135B9E7836E05BB8C70924");
    }

    @Test
    void signedParameters_removesSignatureKeysCaseInsensitivelyBeforeSigning() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "test");
        params.put("Signature", "bad1");
        params.put("SIGN", "bad2");
        params.put("signature", "bad3");

        Map<String, String> signed = MeititejiaSigner.signedParameters(params, "sid", "key", 1710000000L);

        assertThat(signed).doesNotContainKeys("Signature", "SIGN");
        assertThat(signed).containsKeys("name", "secret_id", "timestamp", "signature");
        assertThat(MeititejiaSigner.canonicalString(signed)).isEqualTo("name=test&secret_id=sid&timestamp=1710000000");
    }

    @Test
    void normalizeDecimal_usesPlainStringAndSpecialCasesZero() {
        assertThat(MeititejiaSigner.normalizeDecimal(new BigDecimal("100.00"))).isEqualTo("100");
        assertThat(MeititejiaSigner.normalizeDecimal(new BigDecimal("100.10"))).isEqualTo("100.1");
        assertThat(MeititejiaSigner.normalizeDecimal(new BigDecimal("0.00"))).isEqualTo("0");
    }

    @Test
    void formEncode_matchesPhpFormEncoding() {
        assertThat(MeititejiaSigner.formEncode("标题 A*~"))
                .isEqualTo("%E6%A0%87%E9%A2%98+A*~");
    }

    @Test
    void formBody_encodesValuesForTransport() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("title", "标题 A*~");
        params.put("content", "\u7a3f\u4ef6\u94fe\u63a5 : <a>");

        assertThat(MeititejiaSigner.formBody(params))
                .isEqualTo("title=%E6%A0%87%E9%A2%98+A*~&content=%E7%A8%BF%E4%BB%B6%E9%93%BE%E6%8E%A5+%3A+%3Ca%3E");
    }

    @Test
    void formBody_protectsEqualsAndAmpersandInsideValues() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("title", "A=B&C=D");

        assertThat(MeititejiaSigner.signNormalized(params, "key")).isEqualTo("0C5F6042EC0D343CB8F966E335EA03B7");
        assertThat(MeititejiaSigner.formBody(params)).isEqualTo("title=A%3DB%26C%3DD");
    }

    @Test
    void signedParameters_rejectsMissingCredentials() {
        assertThatThrownBy(() -> MeititejiaSigner.signedParameters(Map.of(), "", "key", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secretId");
        assertThatThrownBy(() -> MeititejiaSigner.signedParameters(Map.of(), "id", "", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secretKey");
    }
}
