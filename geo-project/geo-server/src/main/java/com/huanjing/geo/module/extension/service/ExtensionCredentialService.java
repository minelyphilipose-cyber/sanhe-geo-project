package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.extension.dto.ExtensionFillTokenConsumeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.FILL_TOKEN_BINDING_MISMATCH;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.TASK_NOT_FOUND;

/**
 * Bridges extension fill-token verification to the semi-auto editor fill payload.
 *
 * <p>SECURITY CONTRACT: callers pass the operator id from an already validated extension session.
 * This service consumes the fill token exactly once and returns only the fill payload needed by the
 * extension. Login cookie loading is intentionally not part of this flow.</p>
 */
@Service
@RequiredArgsConstructor
public class ExtensionCredentialService {

    private final FillTokenService fillTokenService;
    private final ExtensionTaskStateService taskStateService;
    private final SemiAutoTaskAccessService semiAutoTaskAccessService;

    public ExtensionFillTokenConsumeResponse consumeFillToken(
            String fillToken,
            Long expectedOperatorId,
            Long extensionSessionId
    ) {
        FillTokenPayload payload = fillTokenService.verify(fillToken);
        SemiAutoTaskAccessService.SemiAutoTaskContext context =
                semiAutoTaskAccessService.validateFillTokenTask(payload, expectedOperatorId);
        if (!StringUtils.hasText(context.task().getFillPayload())) {
            throw new BizException(TASK_NOT_FOUND, "fill payload not found");
        }
        if (context.task().getArticleId() == null) {
            throw new BizException(FILL_TOKEN_BINDING_MISMATCH, "task article binding missing");
        }
        fillTokenService.reserveConsume(fillToken, payload);
        try {
            taskStateService.markFillingFromFillTokenConsume(
                    payload.tid(),
                    payload.op(),
                    extensionSessionId
            );
            fillTokenService.completeConsume(fillToken, payload);
        } catch (RuntimeException ex) {
            fillTokenService.restoreConsume(fillToken, payload);
            throw ex;
        }

        return new ExtensionFillTokenConsumeResponse(
                payload.tid(),
                payload.exp(),
                payload.n(),
                context.task().getFillPayload()
        );
    }
}
