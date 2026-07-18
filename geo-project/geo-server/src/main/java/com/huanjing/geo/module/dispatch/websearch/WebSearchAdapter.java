package com.huanjing.geo.module.dispatch.websearch;

import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchRequest;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;

public interface WebSearchAdapter {
    IntegrationType integrationType();

    WebSearchResponse execute(WebSearchRequest request);
}
