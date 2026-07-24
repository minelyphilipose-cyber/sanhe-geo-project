package com.huanjing.geo.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedProxyClientIpTest {

    @Test
    void ignoresClientControlledForwardedForChain() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 198.51.100.2");

        assertThat(TrustedProxyClientIp.resolve(request)).isEqualTo("172.18.0.5");
    }

    @Test
    void acceptsTheSingleRealIpHeaderOverwrittenByTrustedNginx() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Real-IP", "203.0.113.9");
        request.addHeader("X-Forwarded-For", "198.51.100.2");

        assertThat(TrustedProxyClientIp.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void rejectsNonIpRealIpValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Real-IP", "attacker.example");

        assertThat(TrustedProxyClientIp.resolve(request)).isEqualTo("172.18.0.5");
    }
}
