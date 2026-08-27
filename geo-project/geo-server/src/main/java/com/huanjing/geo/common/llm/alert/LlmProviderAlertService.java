package com.huanjing.geo.common.llm.alert;

import com.huanjing.geo.common.llm.measurement.LlmErrorCategory;
import com.huanjing.geo.common.llm.measurement.LlmStructuredException;
import com.huanjing.geo.common.llm.router.LlmPlatformCandidate;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LlmProviderAlertService {

    private static final String SOURCE = "llm_provider_health";
    private static final String RECIPIENT_ROLE = "super_admin";

    private final SystemAlertService systemAlertService;

    public void recordFailure(LlmRouteRequest request,
                              LlmPlatformCandidate candidate,
                              Throwable error) {
        LlmStructuredException structured = findStructured(error);
        LlmErrorCategory category = structured == null || structured.errorCategory() == null
                ? LlmErrorCategory.INVOKE_FAILED
                : structured.errorCategory();
        String providerErrorCode = structured == null ? null : structured.providerErrorCode();
        String reasonKey = StringUtils.hasText(providerErrorCode)
                ? normalize(providerErrorCode)
                : category.name().toLowerCase(Locale.ROOT);
        String prefix = dedupePrefix(request.feature(), candidate.platformCode());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("category", "operations");
        context.put("feature", request.feature());
        context.put("platformCode", candidate.platformCode());
        context.put("platformName", candidate.platformName());
        context.put("channel", candidate.channel());
        context.put("modelId", candidate.modelId());
        context.put("errorCategory", category.name());
        context.put("providerErrorCode", providerErrorCode);
        context.put("httpStatusCode", structured == null ? null : structured.httpStatusCode());
        context.put("retryAfterMs", structured == null ? null : structured.retryAfterMs());
        context.put("reason", limit(error == null ? null : error.getMessage(), 500));
        context.put("observedAt", LocalDateTime.now().toString());

        systemAlertService.createOrRefreshRecipientAlert(
                "LLM_PROVIDER_FAILURE",
                isCritical(category) ? "critical" : "warn",
                SOURCE,
                message(candidate, category),
                context,
                null,
                RECIPIENT_ROLE,
                prefix + reasonKey
        );
    }

    public void recordSuccess(LlmRouteRequest request, LlmPlatformCandidate candidate) {
        systemAlertService.resolveOpenByDedupeKeyPrefix(
                dedupePrefix(request.feature(), candidate.platformCode()),
                null
        );
    }

    private String dedupePrefix(String feature, String platformCode) {
        return "llm_provider:" + normalize(feature) + ":" + normalize(platformCode) + ":";
    }

    private boolean isCritical(LlmErrorCategory category) {
        return category == LlmErrorCategory.ACCOUNT_QUOTA_EXHAUSTED
                || category == LlmErrorCategory.AUTHENTICATION_FAILED;
    }

    private String message(LlmPlatformCandidate candidate, LlmErrorCategory category) {
        String platform = StringUtils.hasText(candidate.platformName())
                ? candidate.platformName()
                : candidate.platformCode();
        return switch (category) {
            case ACCOUNT_QUOTA_EXHAUSTED -> platform + " 账户额度不足，模型调用已暂停";
            case AUTHENTICATION_FAILED -> platform + " 调用凭证失效";
            case EMPTY_RESPONSE -> platform + " 返回空模型响应";
            case PLATFORM_429 -> platform + " 持续触发供应商限流";
            case HTTP_5XX -> platform + " 供应商服务异常";
            case TIMEOUT -> platform + " 模型调用持续超时";
            default -> platform + " 模型调用失败";
        };
    }

    private LlmStructuredException findStructured(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof LlmStructuredException structured) {
                return structured;
            }
            current = current.getCause();
        }
        return null;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private String limit(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
