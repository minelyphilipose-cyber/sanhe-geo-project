package com.huanjing.geo.module.presale.generate.web;

import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PresaleWebEndpointPolicyTest {

    @Test
    void allowsOnlyOfficialZhipuHttpsEndpoint() {
        assertDoesNotThrow(() -> PresaleWebEndpointPolicy.validate(
                IntegrationType.ZHIPU_CHAT_WEB,
                "https://open.bigmodel.cn/api/paas/v4/chat/completions"));
        assertThrows(IllegalArgumentException.class, () -> PresaleWebEndpointPolicy.validate(
                IntegrationType.ZHIPU_CHAT_WEB,
                "https://open.bigmodel.cn.attacker.example/api/paas/v4/chat/completions"));
    }
}
