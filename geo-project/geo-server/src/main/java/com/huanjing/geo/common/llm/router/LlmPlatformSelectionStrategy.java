package com.huanjing.geo.common.llm.router;

import java.util.List;

public interface LlmPlatformSelectionStrategy {
    List<LlmPlatformCandidate> selectCandidates(LlmRouteRequest request);
}
