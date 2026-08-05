package com.huanjing.geo.module.dispatch.websearch.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.codec.ZhipuChatWebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.transport.WebSearchProviderCallExecutor;
import org.springframework.stereotype.Component;

@Component
public class ZhipuChatWebSearchAdapter extends AbstractJsonWebSearchAdapter {

    public ZhipuChatWebSearchAdapter(ObjectMapper objectMapper,
                                     WebSearchProviderCallExecutor callExecutor,
                                     ZhipuChatWebSearchCodec codec) {
        super(objectMapper, callExecutor, codec);
    }
}
