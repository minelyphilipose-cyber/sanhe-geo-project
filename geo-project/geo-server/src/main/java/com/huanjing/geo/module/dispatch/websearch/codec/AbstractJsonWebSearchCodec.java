package com.huanjing.geo.module.dispatch.websearch.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

abstract class AbstractJsonWebSearchCodec implements WebSearchCodec {

    protected final ObjectMapper objectMapper;

    protected AbstractJsonWebSearchCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected JsonNode providerConfig(WebSearchCodecRequest request) {
        try {
            return objectMapper.readTree(request.providerConfigSnapshotJson());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid provider config snapshot", ex);
        }
    }
}
