package com.huanjing.geo.module.audit;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.credential.CredentialErrorCodes;
import com.huanjing.geo.module.customer.access.BrandAccessErrorCodes;
import com.huanjing.geo.module.extension.ExtensionErrorCodes;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditOperationAspect {

    private static final Set<Integer> DENIED_CODES = Set.of(
            403,
            CredentialErrorCodes.CREDENTIAL_INTEGRITY_VIOLATION,
            BrandAccessErrorCodes.BRAND_ACCESS_DENIED,
            ExtensionErrorCodes.EXTENSION_DENIED,
            ExtensionErrorCodes.EXTENSION_UNAUTHORIZED,
            ExtensionErrorCodes.EXTENSION_VERSION_TOO_LOW,
            ExtensionErrorCodes.FILL_TOKEN_INVALID,
            ExtensionErrorCodes.FILL_TOKEN_USED_OR_EXPIRED,
            ExtensionErrorCodes.BIND_CODE_INVALID,
            ExtensionErrorCodes.BIND_RATE_LIMIT_EXCEEDED,
            ExtensionErrorCodes.TASK_STATE_CONFLICT,
            ExtensionErrorCodes.TASK_RATE_LIMITED,
            ExtensionErrorCodes.COOKIE_CAPTURE_CONFIRM_REQUIRED,
            ExtensionErrorCodes.COOKIE_CAPTURE_ACCOUNT_BRAND_MISMATCH,
            ExtensionErrorCodes.COOKIE_CAPTURE_NONCE_REPLAYED,
            ContentErrorCodes.ARTICLE_STATE_CONFLICT,
            ContentErrorCodes.ARTICLE_AUTHOR_CANNOT_REVIEW,
            ContentErrorCodes.ARTICLE_AI_DRAFT_RATE_LIMITED
    );

    private static final Set<Integer> NOT_FOUND_CODES = Set.of(
            404,
            CredentialErrorCodes.CREDENTIAL_NOT_FOUND,
            BrandAccessErrorCodes.BRAND_ACCESS_NOT_FOUND,
            ExtensionErrorCodes.EXTENSION_NOT_FOUND,
            ExtensionErrorCodes.TASK_NOT_FOUND,
            ContentErrorCodes.ARTICLE_NOT_FOUND
    );

    private final AuditService auditService;

    @Around("@annotation(operation)")
    public Object around(ProceedingJoinPoint joinPoint, AuditOperation operation) throws Throwable {
        long started = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            auditService.record(event(joinPoint, operation, AuditResult.SUCCESS, null, started));
            return result;
        } catch (Throwable ex) {
            AuditResult result = resolveResult(ex);
            auditService.record(event(joinPoint, operation, result, ex, started));
            throw ex;
        }
    }

    private AuditResult resolveResult(Throwable ex) {
        if (!(ex instanceof BizException bizException)) {
            return AuditResult.FAILURE;
        }
        if (DENIED_CODES.contains(bizException.getCode())) {
            return AuditResult.DENIED;
        }
        if (NOT_FOUND_CODES.contains(bizException.getCode())) {
            return AuditResult.NOT_FOUND;
        }
        return AuditResult.FAILURE;
    }

    private AuditEvent event(
            ProceedingJoinPoint joinPoint,
            AuditOperation operation,
            AuditResult result,
            Throwable error,
            long started
    ) {
        AuditEvent event = new AuditEvent();
        event.setEventType(operation.value());
        event.setMode(operation.mode());
        event.setSensitive(operation.sensitive());
        event.setResult(result);
        if (error != null) {
            event.setErrorCode(error instanceof BizException bizException ? String.valueOf(bizException.getCode()) : error.getClass().getSimpleName());
            event.setErrorMessage(truncateText(error.getMessage(), 512));
        }
        event.setDetail(detail(joinPoint, operation.sensitive(), started));
        return event;
    }

    private Map<String, Object> detail(ProceedingJoinPoint joinPoint, boolean sensitive, long started) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("className", signature.getDeclaringTypeName());
        detail.put("methodName", signature.getMethod().getName());
        detail.put("durationMs", (System.nanoTime() - started) / 1_000_000);
        if (!sensitive) {
            detail.put("argCount", joinPoint.getArgs() == null ? 0 : joinPoint.getArgs().length);
        }
        return detail;
    }

    private String truncateText(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        if (maxChars <= 3) {
            return value.substring(0, maxChars);
        }
        return value.substring(0, maxChars - 3) + "...";
    }
}
