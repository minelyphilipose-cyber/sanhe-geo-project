package com.huanjing.geo.module.dispatch.websearch.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.module.dispatch.websearch.WebSearchAdapter;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchCodecRequest;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchMessage;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchRequest;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import com.huanjing.geo.module.dispatch.websearch.transport.ProviderExchange;
import com.huanjing.geo.module.dispatch.websearch.transport.WebSearchProviderCallExecutor;

import java.util.ArrayList;
import java.util.List;

abstract class AbstractJsonWebSearchAdapter implements WebSearchAdapter {

    private final ObjectMapper objectMapper;
    private final WebSearchProviderCallExecutor callExecutor;
    private final WebSearchCodec codec;

    protected AbstractJsonWebSearchAdapter(ObjectMapper objectMapper,
                                           WebSearchProviderCallExecutor callExecutor,
                                           WebSearchCodec codec) {
        this.objectMapper = objectMapper;
        this.callExecutor = callExecutor;
        this.codec = codec;
    }

    @Override
    public final IntegrationType integrationType() {
        return codec.integrationType();
    }

    @Override
    public final WebSearchResponse execute(WebSearchRequest request) {
        ProviderExchange exchange = null;
        try {
            WebSearchCodecRequest codecRequest = codecRequest(request);
            String requestBody = objectMapper.writeValueAsString(codec.encode(codecRequest));
            exchange = callExecutor.postJson(request, requestBody);
            JsonNode root = objectMapper.readTree(exchange.responseBody());
            WebSearchResponse response = codec.decode(root, codecRequest);
            callExecutor.completeSuccess(exchange, response.providerRequestId(),
                    objectMapper.writeValueAsString(response.usage()));
            return response;
        } catch (com.huanjing.geo.module.dispatch.websearch.transport.WebSearchProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            if (exchange != null) {
                throw callExecutor.completeParseFailure(exchange, ex);
            }
            throw new IllegalStateException("Failed to build web-search request", ex);
        }
    }

    final ObjectNode buildRequest(WebSearchRequest request) {
        return codec.encode(codecRequest(request));
    }

    final WebSearchResponse parseResponse(JsonNode root, WebSearchRequest request) {
        return codec.decode(root, codecRequest(request));
    }

    private WebSearchCodecRequest codecRequest(WebSearchRequest request) {
        List<WebSearchMessage> messages = new ArrayList<>();
        if (!request.systemPrompt().isBlank()) {
            messages.add(new WebSearchMessage("system", request.systemPrompt()));
        }
        messages.add(new WebSearchMessage("user", request.originalQuestion()));
        return new WebSearchCodecRequest(
                request.profile().requestedModelId(),
                messages,
                request.profile().providerConfigSnapshotJson()
        );
    }
}
