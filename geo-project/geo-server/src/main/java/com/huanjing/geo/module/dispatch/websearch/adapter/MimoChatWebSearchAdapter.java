package com.huanjing.geo.module.dispatch.websearch.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.codec.MimoChatWebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.transport.WebSearchProviderCallExecutor;
import org.springframework.stereotype.Component;

@Component
public class MimoChatWebSearchAdapter extends AbstractJsonWebSearchAdapter {
    public MimoChatWebSearchAdapter(ObjectMapper objectMapper,
                                    WebSearchProviderCallExecutor callExecutor,
                                    MimoChatWebSearchCodec codec) {
        super(objectMapper, callExecutor, codec);
    }
}
