package com.huanjing.geo.module.dispatch.websearch.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.codec.Qihoo360AiSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.transport.WebSearchProviderCallExecutor;
import org.springframework.stereotype.Component;

@Component
public class Qihoo360AiSearchAdapter extends AbstractJsonWebSearchAdapter {
    public Qihoo360AiSearchAdapter(ObjectMapper objectMapper,
                                   WebSearchProviderCallExecutor callExecutor,
                                   Qihoo360AiSearchCodec codec) {
        super(objectMapper, callExecutor, codec);
    }
}
