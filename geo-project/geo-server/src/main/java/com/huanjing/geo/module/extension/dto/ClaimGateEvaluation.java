package com.huanjing.geo.module.extension.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ClaimGateEvaluation(
        String gateMode,
        boolean wouldBlock,
        boolean blockClaim,
        boolean markManualRequired,
        List<String> blockedReasons,
        Integer retryAfterSeconds,
        LocalDateTime evaluatedAt
) {
}
