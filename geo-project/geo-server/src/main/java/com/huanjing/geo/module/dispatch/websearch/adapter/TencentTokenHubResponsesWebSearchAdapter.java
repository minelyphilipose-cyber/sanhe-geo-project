package com.huanjing.geo.module.dispatch.websearch.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.codec.TencentTokenHubResponsesWebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.transport.WebSearchProviderCallExecutor;
import org.springframework.stereotype.Component;

@Component
public class TencentTokenHubResponsesWebSearchAdapter extends AbstractJsonWebSearchAdapter {

    public TencentTokenHubResponsesWebSearchAdapter(ObjectMapper objectMapper,
                                                     WebSearchProviderCallExecutor callExecutor,
                                                     TencentTokenHubResponsesWebSearchCodec codec) {
        super(objectMapper, callExecutor, codec);
    }
}
