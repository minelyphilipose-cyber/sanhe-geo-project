package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.extension.ExtensionErrorCodes;
import com.huanjing.geo.module.extension.dto.ExtensionFillTokenConsumeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtensionCredentialServiceTest {

    private FillTokenService fillTokenService;
    private ExtensionTaskStateService taskStateService;
    private SemiAutoTaskAccessService semiAutoTaskAccessService;
    private ExtensionCredentialService service;

    @BeforeEach
    void setUp() {
        fillTokenService = mock(FillTokenService.class);
        taskStateService = mock(ExtensionTaskStateService.class);
        semiAutoTaskAccessService = mock(SemiAutoTaskAccessService.class);
        service = new ExtensionCredentialService(fillTokenService, taskStateService, semiAutoTaskAccessService);
    }

    @Test
    void consumeFillTokenReturnsFillPayloadWithoutCookieSecrets() {
        FillTokenPayload payload = new FillTokenPayload(1, 20, 10, 99, 30, 200, 100, "nonce-1");
        when(fillTokenService.verify("fill-token")).thenReturn(payload);
        when(semiAutoTaskAccessService.validateFillTokenTask(payload, 99L)).thenReturn(contextWithFillPayload());

        ExtensionFillTokenConsumeResponse response = service.consumeFillToken("fill-token", 99L, 7L);

        assertEquals(30L, response.taskTargetId());
        assertEquals(200L, response.expiresAt());
        assertEquals("nonce-1", response.nonce());
        assertEquals("{\"title\":\"draft\"}", response.fillPayload());
        verify(fillTokenService).reserveConsume("fill-token", payload);
        verify(taskStateService).markFillingFromFillTokenConsume(30L, 99L, 7L);
        verify(fillTokenService).completeConsume("fill-token", payload);
    }

    @Test
    void fillTokenConsumeFailureDoesNotMarkTaskFilling() {
        when(fillTokenService.verify("fill-token"))
                .thenThrow(new BizException(ExtensionErrorCodes.FILL_TOKEN_USED_OR_EXPIRED, "used"));

        BizException ex = assertThrows(BizException.class,
                () -> service.consumeFillToken("fill-token", 99L, 7L));

        assertEquals(ExtensionErrorCodes.FILL_TOKEN_USED_OR_EXPIRED, ex.getCode());
        verify(taskStateService, never()).markFillingFromFillTokenConsume(any(), any(), any());
    }

    @Test
    void missingFillPayloadDoesNotMarkTaskFilling() {
        FillTokenPayload payload = new FillTokenPayload(1, 20, 10, 99, 30, 200, 100, "nonce-1");
        when(fillTokenService.verify("fill-token")).thenReturn(payload);
        DistributionTask task = new DistributionTask();
        task.setId(30L);
        when(semiAutoTaskAccessService.validateFillTokenTask(payload, 99L))
                .thenReturn(new SemiAutoTaskAccessService.SemiAutoTaskContext(task, 10L));

        BizException ex = assertThrows(BizException.class,
                () -> service.consumeFillToken("fill-token", 99L, 7L));

        assertEquals(ExtensionErrorCodes.TASK_NOT_FOUND, ex.getCode());
        verify(taskStateService, never()).markFillingFromFillTokenConsume(any(), any(), any());
    }

    private SemiAutoTaskAccessService.SemiAutoTaskContext contextWithFillPayload() {
        DistributionTask task = new DistributionTask();
        task.setId(30L);
        task.setArticleId(50L);
        task.setFillPayload("{\"title\":\"draft\"}");
        return new SemiAutoTaskAccessService.SemiAutoTaskContext(task, 10L);
    }
}
