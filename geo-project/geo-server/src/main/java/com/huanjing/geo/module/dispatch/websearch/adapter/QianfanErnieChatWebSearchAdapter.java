package com.huanjing.geo.module.dispatch.websearch.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.codec.QianfanErnieChatWebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.transport.WebSearchProviderCallExecutor;
import org.springframework.stereotype.Component;

@Component
public class QianfanErnieChatWebSearchAdapter extends AbstractJsonWebSearchAdapter {

    public QianfanErnieChatWebSearchAdapter(ObjectMapper objectMapper,
                                            WebSearchProviderCallExecutor callExecutor,
                                            QianfanErnieChatWebSearchCodec codec) {
        super(objectMapper, callExecutor, codec);
    }
}
