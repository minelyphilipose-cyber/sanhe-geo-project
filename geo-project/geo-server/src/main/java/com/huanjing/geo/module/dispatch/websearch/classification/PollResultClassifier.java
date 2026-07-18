package com.huanjing.geo.module.dispatch.websearch.classification;

import com.huanjing.geo.module.dispatch.websearch.enums.CitationConfidence;
import com.huanjing.geo.module.dispatch.websearch.enums.ResultCode;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import org.springframework.stereotype.Component;

@Component
public class PollResultClassifier {
    public static final String VERSION = "web-poll-r0-r5-v1";

    public ResultCode classify(PollClassificationInput input) {
        if (!input.callSucceeded()
                || input.searchStatus() == SearchStatus.FAILED
                || (input.answerRequired() && !input.answerComplete())) {
            return ResultCode.R0;
        }
        if (input.searchStatus() != SearchStatus.TRIGGERED) {
            return ResultCode.R1;
        }
        if (input.brandInAnswer()) {
            return input.brandInSearch() && input.citationConfidence() == CitationConfidence.CONFIRMED
                    ? ResultCode.R5
                    : ResultCode.R4;
        }
        if (input.brandInSearch()) {
            return ResultCode.R3;
        }
        return ResultCode.R2;
    }
}
