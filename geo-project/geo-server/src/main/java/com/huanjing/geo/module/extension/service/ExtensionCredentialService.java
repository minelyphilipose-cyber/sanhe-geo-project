package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.extension.dto.ExtensionFillTokenConsumeResponse;
import com.huanjing.geo.module.extension.dto.FillTokenConsumeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private final DistributionTaskMapper taskMapper;
    private final ExtensionTaskStateService taskStateService;

    public ExtensionFillTokenConsumeResponse consumeFillToken(
            String fillToken,
            Long expectedOperatorId,
            Long extensionSessionId
    ) {
        FillTokenConsumeResponse consumed = fillTokenService.consume(fillToken, expectedOperatorId, extensionSessionId);
        DistributionTask task = taskMapper.selectExtensionFillContext(consumed.taskTargetId());
        if (task == null || !StringUtils.hasText(task.getFillPayload())) {
            throw new BizException(TASK_NOT_FOUND, "fill payload not found");
        }
        taskStateService.markFillingFromFillTokenConsume(
                consumed.taskTargetId(),
                consumed.operatorId(),
                extensionSessionId
        );

        return new ExtensionFillTokenConsumeResponse(
                consumed.taskTargetId(),
                consumed.expiresAt(),
                consumed.nonce(),
                task.getFillPayload()
        );
    }
}
