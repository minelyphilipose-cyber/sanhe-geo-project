package com.huanjing.geo.module.dispatch.websearch.classification;

import com.huanjing.geo.module.dispatch.websearch.enums.CitationConfidence;
import com.huanjing.geo.module.dispatch.websearch.enums.ResultCode;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PollResultClassifierTest {
    private final PollResultClassifier classifier = new PollResultClassifier();

    @Test
    void classifiesFrozenR0ToR5Semantics() {
        assertEquals(ResultCode.R0, classify(false, true, false, SearchStatus.FAILED, false, false, CitationConfidence.NONE));
        assertEquals(ResultCode.R1, classify(true, false, false, SearchStatus.EMPTY, false, false, CitationConfidence.NONE));
        assertEquals(ResultCode.R1, classify(true, false, false, SearchStatus.NO_VALID_SOURCE, false, false, CitationConfidence.NONE));
        assertEquals(ResultCode.R1, classify(true, true, true, SearchStatus.NOT_CONFIRMED, false, true, CitationConfidence.NONE));
        assertEquals(ResultCode.R2, classify(true, true, true, SearchStatus.TRIGGERED, false, false, CitationConfidence.NONE));
        assertEquals(ResultCode.R3, classify(true, true, true, SearchStatus.TRIGGERED, true, false, CitationConfidence.NONE));
        assertEquals(ResultCode.R4, classify(true, true, true, SearchStatus.TRIGGERED, false, true, CitationConfidence.PROBABLE));
        assertEquals(ResultCode.R4, classify(true, true, true, SearchStatus.TRIGGERED, false, true, CitationConfidence.CONFIRMED));
        assertEquals(ResultCode.R5, classify(true, true, true, SearchStatus.TRIGGERED, true, true, CitationConfidence.CONFIRMED));
    }

    @Test
    void emptyTencentSearchIsR1EvenWhenGenerationWasCorrectlySkipped() {
        assertEquals(ResultCode.R1,
                classify(true, false, false, SearchStatus.EMPTY, false, false, CitationConfidence.NONE));
    }

    private ResultCode classify(boolean callSucceeded,
                                boolean answerRequired,
                                boolean answerComplete,
                                SearchStatus searchStatus,
                                boolean brandInSearch,
                                boolean brandInAnswer,
                                CitationConfidence confidence) {
        return classifier.classify(new PollClassificationInput(
                callSucceeded, answerRequired, answerComplete, searchStatus,
                brandInSearch, brandInAnswer, confidence
        ));
    }
}
