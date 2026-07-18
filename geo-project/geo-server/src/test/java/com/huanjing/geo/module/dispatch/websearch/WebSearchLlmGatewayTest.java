package com.huanjing.geo.module.dispatch.websearch;

import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchPlatformProfile;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchRequest;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSearchLlmGatewayTest {

    @Test
    void routesOnlyToTheAdapterDeclaredByThePinnedProfile() {
        FakeAdapter volcengine = new FakeAdapter(IntegrationType.VOLCENGINE_RESPONSES_WEB, "volcengine");
        FakeAdapter qwen = new FakeAdapter(IntegrationType.DASHSCOPE_NATIVE_WEB, "qwen");
        WebSearchLlmGateway gateway = new WebSearchLlmGateway(List.of(volcengine, qwen));

        WebSearchResponse response = gateway.execute(request(IntegrationType.DASHSCOPE_NATIVE_WEB));

        assertEquals("qwen", response.answer());
        assertEquals(0, volcengine.callCount);
        assertEquals(1, qwen.callCount);
    }

    @Test
    void failsInsteadOfFallingBackToAnotherProvider() {
        WebSearchLlmGateway gateway = new WebSearchLlmGateway(List.of(
                new FakeAdapter(IntegrationType.DASHSCOPE_NATIVE_WEB, "qwen")
        ));

        assertThrows(IllegalStateException.class,
                () -> gateway.execute(request(IntegrationType.VOLCENGINE_RESPONSES_WEB)));
    }

    private WebSearchRequest request(IntegrationType integrationType) {
        WebSearchPlatformProfile profile = new WebSearchPlatformProfile(
                1L, "test_web", "test", "provider", integrationType,
                "https://example.test/invoke", "model", "env://TEST_API_KEY",
                null, 1L, "{}", "hash", 3_000, 60_000
        );
        return new WebSearchRequest(1L, 2L, "question", "system", profile, LocalDateTime.now().plusMinutes(5));
    }

    private static final class FakeAdapter implements WebSearchAdapter {
        private final IntegrationType integrationType;
        private final String answer;
        private int callCount;

        private FakeAdapter(IntegrationType integrationType, String answer) {
            this.integrationType = integrationType;
            this.answer = answer;
        }

        @Override
        public IntegrationType integrationType() {
            return integrationType;
        }

        @Override
        public WebSearchResponse execute(WebSearchRequest request) {
            callCount++;
            return new WebSearchResponse(null, "model", "model", answer, SearchStatus.TRIGGERED,
                    false, List.of(), List.of(), List.of(), Map.of(), "stop");
        }
    }
}
