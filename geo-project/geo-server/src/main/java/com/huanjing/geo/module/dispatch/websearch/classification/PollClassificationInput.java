package com.huanjing.geo.module.dispatch.websearch.classification;

import com.huanjing.geo.module.dispatch.websearch.enums.CitationConfidence;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;

public record PollClassificationInput(boolean callSucceeded,
                                      boolean answerRequired,
                                      boolean answerComplete,
                                      SearchStatus searchStatus,
                                      boolean brandInSearch,
                                      boolean brandInAnswer,
                                      CitationConfidence citationConfidence) {
}
