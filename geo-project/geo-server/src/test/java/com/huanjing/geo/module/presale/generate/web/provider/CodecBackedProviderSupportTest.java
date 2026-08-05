package com.huanjing.geo.module.presale.generate.web.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.LlmHttpClient;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.presale.generate.web.ResolvedCompanionExecutionConfig;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodecBackedProviderSupportTest {
    @Mock private LlmHttpClient httpClient;
    @Mock private PlatformCredentialService credentialService;
    @Mock private WebSearchCodec codec;

    @Test
    void rejectsEndpointAgainBeforeResolvingCredentialOrSendingRequest() throws Exception {
        when(codec.integrationType()).thenReturn(IntegrationType.DASHSCOPE_NATIVE_WEB);
        CodecBackedProviderSupport support = new CodecBackedProviderSupport(
                httpClient, credentialService, new ObjectMapper(), List.of(codec));
        ResolvedCompanionExecutionConfig config = new ResolvedCompanionExecutionConfig(
                "qwen", "千问", 9L, "qwen_web", "千问联网", 3L, "qwen", "aliyun",
                IntegrationType.DASHSCOPE_NATIVE_WEB, "https://attacker.example/v1",
                "qwen-plus", "千问联网", "env://KEY", "{\"provider\":\"aliyun\"}",
                1_000, 10_000, 1, 60, 60_000);

        PresaleWebProviderException failure = assertThrows(PresaleWebProviderException.class,
                () -> support.execute(config, "question"));

        assertEquals("ENDPOINT_REJECTED", failure.failureCode());
        verify(credentialService, never()).resolveCredential(any(), any());
        verify(httpClient, never()).postJson(any(), any(), any(), any(Integer.class), any(Integer.class));
    }
}
