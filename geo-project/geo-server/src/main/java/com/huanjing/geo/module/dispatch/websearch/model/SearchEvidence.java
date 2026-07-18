package com.huanjing.geo.module.dispatch.websearch.model;

import java.util.Map;

public record SearchEvidence(int eventIndex,
                             String evidenceType,
                             String query,
                             Map<String, Object> attributes) {
    public SearchEvidence {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
