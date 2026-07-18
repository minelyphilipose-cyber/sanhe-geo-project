package com.huanjing.geo.module.dispatch.websearch.transport;

import java.util.List;
import java.util.Map;

public record ProviderExchange(Long callId,
                               int httpStatus,
                               String responseBody,
                               Map<String, List<String>> responseHeaders,
                               long latencyMs) {
}
