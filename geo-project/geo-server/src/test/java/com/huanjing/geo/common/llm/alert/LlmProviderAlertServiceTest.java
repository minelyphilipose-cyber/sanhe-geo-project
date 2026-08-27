package com.huanjing.geo.common.llm.alert;

import com.huanjing.geo.common.llm.measurement.LlmErrorCategory;
import com.huanjing.geo.common.llm.measurement.LlmHttpErrorException;
import com.huanjing.geo.common.llm.router.LlmPlatformCandidate;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.service.SystemAlertService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LlmProviderAlertServiceTest {

    @Test
    void quotaFailureCreatesOneStableCriticalRootAlert() {
        SystemAlertService systemAlertService = mock(SystemAlertService.class);
        LlmProviderAlertService service = new LlmProviderAlertService(systemAlertService);
        LlmRouteRequest request = new LlmRouteRequest(
                "monitoring", null, "probe", 0D, 1_000, 5_000, 60_000,
                0, 16, false, 1, 0, List.of()
        );
        AiPlatformConfig config = new AiPlatformConfig();
        LlmPlatformCandidate candidate = new LlmPlatformCandidate(
                config, "kimi", "Kimi", "primary", "https://example.com", "kimi-test", "Kimi", "secret"
        );

        service.recordFailure(request, candidate, new LlmHttpErrorException(
                429,
                "insufficient balance",
                "exceeded_current_quota_error",
                null,
                LlmErrorCategory.ACCOUNT_QUOTA_EXHAUSTED
        ));

        verify(systemAlertService).createOrRefreshRecipientAlert(
                eq("LLM_PROVIDER_FAILURE"),
                eq("critical"),
                eq("llm_provider_health"),
                eq("Kimi 账户额度不足，模型调用已暂停"),
                argThat((Map<String, Object> context) -> "kimi".equals(context.get("platformCode"))
                        && "exceeded_current_quota_error".equals(context.get("providerErrorCode"))),
                isNull(),
                eq("super_admin"),
                eq("llm_provider:monitoring:kimi:exceeded_current_quota_error")
        );
    }

    @Test
    void successResolvesAllOpenReasonsForFeatureAndPlatform() {
        SystemAlertService systemAlertService = mock(SystemAlertService.class);
        LlmProviderAlertService service = new LlmProviderAlertService(systemAlertService);
        LlmRouteRequest request = new LlmRouteRequest(
                "monitoring", null, "probe", 0D, 1_000, 5_000, 60_000,
                0, 16, false, 1, 0, List.of()
        );
        LlmPlatformCandidate candidate = new LlmPlatformCandidate(
                new AiPlatformConfig(), "kimi", "Kimi", "primary",
                "https://example.com", "kimi-test", "Kimi", "secret"
        );

        service.recordSuccess(request, candidate);

        verify(systemAlertService).resolveOpenByDedupeKeyPrefix(
                eq("llm_provider:monitoring:kimi:"), isNull());
    }
}
