package com.huanjing.geo.module.dispatch.websearch.model;

import com.huanjing.geo.module.dispatch.websearch.enums.CitationConfidence;

public record WebSearchCitation(Integer citationIndex,
                                Integer sourceOccurrenceIndex,
                                Integer answerStart,
                                Integer answerEnd,
                                String citationText,
                                CitationConfidence confidence,
                                String validationStatus) {
    public WebSearchCitation {
        confidence = confidence == null ? CitationConfidence.NONE : confidence;
    }
}
