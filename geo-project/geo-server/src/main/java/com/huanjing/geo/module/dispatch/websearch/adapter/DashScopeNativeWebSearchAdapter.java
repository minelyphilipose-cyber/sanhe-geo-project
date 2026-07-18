package com.huanjing.geo.module.dispatch.websearch.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.codec.DashScopeNativeWebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.transport.WebSearchProviderCallExecutor;
import org.springframework.stereotype.Component;

@Component
public class DashScopeNativeWebSearchAdapter extends AbstractJsonWebSearchAdapter {

    public DashScopeNativeWebSearchAdapter(ObjectMapper objectMapper,
                                           WebSearchProviderCallExecutor callExecutor,
                                           DashScopeNativeWebSearchCodec codec) {
        super(objectMapper, callExecutor, codec);
    }
}
