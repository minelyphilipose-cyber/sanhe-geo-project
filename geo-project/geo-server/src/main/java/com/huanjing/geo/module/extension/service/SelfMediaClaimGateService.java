package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.module.extension.config.SelfMediaRuntimeProperties;
import com.huanjing.geo.module.extension.dto.ClaimGateEvaluation;
import com.huanjing.geo.module.extension.dto.RuntimeReadinessQuery;
import com.huanjing.geo.module.extension.dto.RuntimeReadinessResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SelfMediaClaimGateService {

    public static final String MODE_OBSERVE_ONLY = "observe_only";
    public static final String MODE_BLOCK_NON_DESTRUCTIVE = "block_non_destructive";
    public static final String MODE_MANUAL_REQUIRED_TERMINAL = "manual_required_terminal";

    private static final Set<String> TERMINAL_REASONS = Set.of(
            "ACCOUNT_MISMATCH",
            "EXTENSION_VERSION_TOO_LOW",
            "HELPER_VERSION_TOO_LOW",
            "BROWSER_ENVIRONMENT_DISABLED"
    );

    private final SelfMediaRuntimeReadinessService readinessService;
    private final SelfMediaRuntimeProperties properties;

    public ClaimGateEvaluation evaluate(RuntimeReadinessQuery query) {
        RuntimeReadinessResult readiness = readinessService.evaluate(query);
        String mode = properties.getGate().modeFor(
                query == null ? null : query.brandId(),
                query == null ? null : query.platform()
        );
        boolean wouldBlock = !readiness.ready();
        boolean blockClaim = wouldBlock && MODE_BLOCK_NON_DESTRUCTIVE.equalsIgnoreCase(mode);
        boolean markManualRequired = wouldBlock
                && MODE_MANUAL_REQUIRED_TERMINAL.equalsIgnoreCase(mode)
                && readiness.blockedReasons().stream().anyMatch(TERMINAL_REASONS::contains);
        return new ClaimGateEvaluation(
                mode,
                wouldBlock,
                blockClaim,
                markManualRequired,
                readiness.blockedReasons(),
                readiness.retryAfterSeconds(),
                LocalDateTime.now()
        );
    }
}
