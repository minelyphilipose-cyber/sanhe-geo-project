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
            "EXTENSION_CAPABILITY_UNSUPPORTED",
            "HELPER_CAPABILITY_UNSUPPORTED",
            "BROWSER_ENVIRONMENT_DISABLED"
    );
    private static final Set<String> EXTENSION_BOOTSTRAP_REASONS = Set.of(
            SelfMediaRuntimeReadinessService.EXTENSION_NOT_SEEN,
            SelfMediaRuntimeReadinessService.EXTENSION_STALE,
            SelfMediaRuntimeReadinessService.ACCOUNT_NOT_VERIFIED
    );

    private final SelfMediaRuntimeReadinessService readinessService;
    private final SelfMediaRuntimeProperties properties;

    public ClaimGateEvaluation evaluate(RuntimeReadinessQuery query) {
        return evaluate(query, false);
    }

    public ClaimGateEvaluation evaluateForBrowserLaunch(RuntimeReadinessQuery query) {
        return evaluate(query, true);
    }

    private ClaimGateEvaluation evaluate(RuntimeReadinessQuery query, boolean allowExtensionBootstrap) {
        RuntimeReadinessResult readiness = readinessService.evaluate(query);
        var blockedReasons = allowExtensionBootstrap
                ? readiness.blockedReasons().stream()
                    .filter(reason -> !EXTENSION_BOOTSTRAP_REASONS.contains(reason))
                    .toList()
                : readiness.blockedReasons();
        String mode = properties.getGate().modeFor(
                query == null ? null : query.brandId(),
                query == null ? null : query.platform()
        );
        boolean wouldBlock = !blockedReasons.isEmpty();
        boolean blockClaim = wouldBlock && MODE_BLOCK_NON_DESTRUCTIVE.equalsIgnoreCase(mode);
        boolean markManualRequired = wouldBlock
                && MODE_MANUAL_REQUIRED_TERMINAL.equalsIgnoreCase(mode)
                && blockedReasons.stream().anyMatch(TERMINAL_REASONS::contains);
        return new ClaimGateEvaluation(
                mode,
                wouldBlock,
                blockClaim,
                markManualRequired,
                blockedReasons,
                readiness.retryAfterSeconds(),
                LocalDateTime.now()
        );
    }
}
