package com.huanjing.geo.module.dispatch.websearch.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;

/**
 * Pure provider protocol boundary. Implementations must not perform I/O or audit writes.
 */
public interface WebSearchCodec {

    IntegrationType integrationType();

    ObjectNode encode(WebSearchCodecRequest request);

    WebSearchResponse decode(JsonNode responseBody, WebSearchCodecRequest request);
}
