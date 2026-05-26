package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.extension.ExtensionErrorCodes;
import com.huanjing.geo.module.extension.dto.ExtensionFillTokenConsumeResponse;
import com.huanjing.geo.module.extension.dto.FillTokenConsumeResponse;
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
    private DistributionTaskMapper taskMapper;
    private ExtensionTaskStateService taskStateService;
    private ExtensionCredentialService service;

    @BeforeEach
    void setUp() {
        fillTokenService = mock(FillTokenService.class);
        taskMapper = mock(DistributionTaskMapper.class);
        taskStateService = mock(ExtensionTaskStateService.class);
        service = new ExtensionCredentialService(fillTokenService, taskMapper, taskStateService);
    }

    @Test
    void consumeFillTokenReturnsFillPayloadWithoutCookieSecrets() {
        FillTokenConsumeResponse consumed = new FillTokenConsumeResponse(
                20L,
                10L,
                99L,
                30L,
                200L,
                "nonce-1"
        );
        when(fillTokenService.consume("fill-token", 99L, 7L)).thenReturn(consumed);
        mockFillPayload();

        ExtensionFillTokenConsumeResponse response = service.consumeFillToken("fill-token", 99L, 7L);

        assertEquals(30L, response.taskTargetId());
        assertEquals(200L, response.expiresAt());
        assertEquals("nonce-1", response.nonce());
        assertEquals("{\"title\":\"draft\"}", response.fillPayload());
        verify(taskStateService).markFillingFromFillTokenConsume(30L, 99L, 7L);
    }

    @Test
    void fillTokenConsumeFailureDoesNotMarkTaskFilling() {
        when(fillTokenService.consume("fill-token", 99L, 7L))
                .thenThrow(new BizException(ExtensionErrorCodes.FILL_TOKEN_USED_OR_EXPIRED, "used"));

        BizException ex = assertThrows(BizException.class,
                () -> service.consumeFillToken("fill-token", 99L, 7L));

        assertEquals(ExtensionErrorCodes.FILL_TOKEN_USED_OR_EXPIRED, ex.getCode());
        verify(taskStateService, never()).markFillingFromFillTokenConsume(any(), any(), any());
    }

    @Test
    void missingFillPayloadDoesNotMarkTaskFilling() {
        FillTokenConsumeResponse consumed = new FillTokenConsumeResponse(20L, 10L, 99L, 30L, 200L, "nonce-1");
        when(fillTokenService.consume("fill-token", 99L, 7L)).thenReturn(consumed);
        when(taskMapper.selectExtensionFillContext(30L)).thenReturn(new DistributionTask());

        BizException ex = assertThrows(BizException.class,
                () -> service.consumeFillToken("fill-token", 99L, 7L));

        assertEquals(ExtensionErrorCodes.TASK_NOT_FOUND, ex.getCode());
        verify(taskStateService, never()).markFillingFromFillTokenConsume(any(), any(), any());
    }

    private void mockFillPayload() {
        DistributionTask task = new DistributionTask();
        task.setId(30L);
        task.setFillPayload("{\"title\":\"draft\"}");
        when(taskMapper.selectExtensionFillContext(30L)).thenReturn(task);
    }
}
