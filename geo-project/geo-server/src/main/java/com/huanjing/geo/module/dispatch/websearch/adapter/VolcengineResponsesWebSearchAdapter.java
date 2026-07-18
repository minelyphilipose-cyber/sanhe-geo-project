package com.huanjing.geo.module.dispatch.websearch.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.codec.VolcengineResponsesWebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.transport.WebSearchProviderCallExecutor;
import org.springframework.stereotype.Component;

@Component
public class VolcengineResponsesWebSearchAdapter extends AbstractJsonWebSearchAdapter {

    public VolcengineResponsesWebSearchAdapter(ObjectMapper objectMapper,
                                               WebSearchProviderCallExecutor callExecutor,
                                               VolcengineResponsesWebSearchCodec codec) {
        super(objectMapper, callExecutor, codec);
    }
}
