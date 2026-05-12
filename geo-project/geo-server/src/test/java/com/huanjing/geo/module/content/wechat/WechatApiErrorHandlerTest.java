package com.huanjing.geo.module.content.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WechatApiErrorHandlerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WechatOutboundIpService outboundIpService = mock(WechatOutboundIpService.class);
    private final WechatApiErrorHandler handler = new WechatApiErrorHandler(outboundIpService);

    @Test
    void throwIfError_40164_throwsIpWhitelistExceptionWithOutboundIp() throws Exception {
        when(outboundIpService.currentOutboundIp()).thenReturn("1.2.3.4");

        WechatIpNotWhitelistedException ex = assertThrows(WechatIpNotWhitelistedException.class,
                () -> handler.throwIfError("test-api",
                        objectMapper.readTree("{\"errcode\":40164,\"errmsg\":\"invalid ip\"}")));

        assertEquals(40164, ex.getCode());
        assertEquals("1.2.3.4", ex.getOutboundIp());
    }

    @Test
    void throwIfError_nonZeroGenericCode_throwsBizException() throws Exception {
        BizException ex = assertThrows(BizException.class,
                () -> handler.throwIfError("test-api",
                        objectMapper.readTree("{\"errcode\":45009,\"errmsg\":\"rate limit\"}")));

        assertEquals(45009, ex.getCode());
        assertEquals("rate limit", ex.getMessage());
    }
}
