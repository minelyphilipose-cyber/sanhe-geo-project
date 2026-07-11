package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.entity.SelfMediaAuthHealthPolicy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class SelfMediaAuthRiskEvaluator {

    public static final String MODE_DECLARED_THEN_REFERENCE = "declared_then_reference";
    public static final String MODE_DECLARED_ONLY = "declared_only";
    public static final String MODE_REFERENCE_ONLY = "reference_only";
    public static final String MODE_PERIODIC_ONLY = "periodic_only";
    public static final List<String> MODES = List.of(MODE_DECLARED_THEN_REFERENCE, MODE_DECLARED_ONLY,
            MODE_REFERENCE_ONLY, MODE_PERIODIC_ONLY);

    public Evaluation evaluate(Input input) {
        SelfMediaAuthHealthPolicy policy = input.policy();
        if (policy == null || !Boolean.TRUE.equals(policy.getEnabled())) {
            return new Evaluation("monitoring_disabled", null, null, null, null, null, null, null, null,
                    false, null, null, List.of("MONITORING_DISABLED"));
        }
        boolean credentialNotStored = input.credentialRequired() && !input.credentialPresent();
        if (credentialNotStored && input.lastLoginVerifiedAt() == null) {
            return new Evaluation("credential_missing", null, null, null, null, null, null, null, null,
                    false, input.cookieDeclaredExpiresAt(), expired(input.cookieDeclaredExpiresAt(), input.now()),
                    List.of("TRUSTED_LOGIN_FACT_MISSING"));
        }

        Base base = verificationBase(input);
        LocalDateTime periodic = base.time() == null ? null
                : base.time().plusDays(Math.max(policy.getReverifyIntervalDays(), 1));
        Candidate credential = credentialCandidate(input, policy);
        boolean superseded = credential.time() != null && input.lastLoginVerifiedAt() != null
                && !input.lastLoginVerifiedAt().isBefore(credential.time());
        Candidate effectiveCredential = superseded ? Candidate.empty() : credential;

        Candidate selected = selectEarlier(
                periodic == null ? Candidate.empty() : new Candidate(periodic, base.source()),
                effectiveCredential);
        if (selected.time() == null) {
            return new Evaluation("unknown", base.time(), base.source(), periodic, credential.time(), credential.source(), null, null, null,
                    superseded, input.cookieDeclaredExpiresAt(), expired(input.cookieDeclaredExpiresAt(), input.now()),
                    List.of("REVERIFY_TIME_UNKNOWN"));
        }
        LocalDateTime warningStart = selected.time().minusDays(Math.max(policy.getWarningDays(), 0));
        String status = !input.now().isBefore(selected.time()) ? "reverify_overdue"
                : !input.now().isBefore(warningStart) ? "reverify_due_soon" : "normal";
        List<String> reasons = new ArrayList<>();
        reasons.add("REVERIFY_SOURCE_" + selected.source().toUpperCase());
        if (credentialNotStored) reasons.add("CREDENTIAL_NOT_STORED_NON_BLOCKING");
        if (superseded) reasons.add("CREDENTIAL_TIME_SUPERSEDED_BY_VERIFICATION");
        if (expired(input.cookieDeclaredExpiresAt(), input.now())) reasons.add("COOKIE_DECLARED_EXPIRY_PASSED");
        return new Evaluation(status, base.time(), base.source(), periodic, credential.time(), credential.source(),
                selected.time(), selected.source(), warningStart, superseded, input.cookieDeclaredExpiresAt(), expired(input.cookieDeclaredExpiresAt(), input.now()), reasons);
    }

    private Base verificationBase(Input input) {
        if (input.lastLoginVerifiedAt() != null) return new Base(input.lastLoginVerifiedAt(), "last_verification");
        if (input.credentialCapturedAt() != null) return new Base(input.credentialCapturedAt(), "credential_capture");
        if (input.accountBoundAt() != null) return new Base(input.accountBoundAt(), "account_binding");
        return new Base(null, null);
    }

    private Candidate credentialCandidate(Input input, SelfMediaAuthHealthPolicy policy) {
        String mode = StringUtils.hasText(policy.getCredentialExpiryMode())
                ? policy.getCredentialExpiryMode() : MODE_DECLARED_THEN_REFERENCE;
        if (MODE_PERIODIC_ONLY.equals(mode)) return Candidate.empty();
        if ((MODE_DECLARED_THEN_REFERENCE.equals(mode) || MODE_DECLARED_ONLY.equals(mode))
                && input.cookieDeclaredExpiresAt() != null) {
            return new Candidate(input.cookieDeclaredExpiresAt(), "cookie_declared_expiry");
        }
        if ((MODE_DECLARED_THEN_REFERENCE.equals(mode) || MODE_REFERENCE_ONLY.equals(mode))
                && input.credentialCapturedAt() != null && policy.getCredentialReferenceDays() != null) {
            return new Candidate(input.credentialCapturedAt().plusDays(policy.getCredentialReferenceDays()), "credential_reference");
        }
        return Candidate.empty();
    }

    private Candidate selectEarlier(Candidate left, Candidate right) {
        if (left.time() == null) return right;
        if (right.time() == null) return left;
        return left.time().isAfter(right.time()) ? right : left;
    }

    private boolean expired(LocalDateTime value, LocalDateTime now) {
        return value != null && !value.isAfter(now);
    }

    private record Base(LocalDateTime time, String source) {}
    private record Candidate(LocalDateTime time, String source) {
        private static Candidate empty() { return new Candidate(null, null); }
    }

    public record Input(SelfMediaAuthHealthPolicy policy,
                        LocalDateTime now,
                        boolean credentialRequired,
                        boolean credentialPresent,
                        LocalDateTime lastLoginVerifiedAt,
                        LocalDateTime credentialCapturedAt,
                        LocalDateTime cookieDeclaredExpiresAt,
                        LocalDateTime accountBoundAt) {
    }

    public record Evaluation(String riskStatus,
                             LocalDateTime verificationBaseAt,
                             String verificationBaseSource,
                             LocalDateTime periodicReverifyAt,
                             LocalDateTime credentialReverifyAt,
                             String credentialReverifySource,
                             LocalDateTime recommendedReverifyAt,
                             String recommendedReverifySource,
                             LocalDateTime warningStartAt,
                             boolean credentialCandidateSuperseded,
                             LocalDateTime cookieDeclaredExpiresAt,
                             Boolean cookieDeclaredExpiryPassed,
                             List<String> riskReasonCodes) {
    }
}
