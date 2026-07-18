package com.huanjing.geo.module.dispatch.websearch;

import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchRequest;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class WebSearchLlmGateway {
    private final Map<IntegrationType, WebSearchAdapter> adapters;

    public WebSearchLlmGateway(List<WebSearchAdapter> adapters) {
        EnumMap<IntegrationType, WebSearchAdapter> registered = new EnumMap<>(IntegrationType.class);
        for (WebSearchAdapter adapter : adapters) {
            WebSearchAdapter previous = registered.put(adapter.integrationType(), adapter);
            if (previous != null) {
                throw new IllegalStateException("Duplicate web-search adapter for " + adapter.integrationType());
            }
        }
        this.adapters = Map.copyOf(registered);
    }

    public WebSearchResponse execute(WebSearchRequest request) {
        IntegrationType integrationType = request.profile().integrationType();
        WebSearchAdapter adapter = adapters.get(integrationType);
        if (adapter == null) {
            throw new IllegalStateException("No web-search adapter registered for " + integrationType);
        }
        return adapter.execute(request);
    }
}
