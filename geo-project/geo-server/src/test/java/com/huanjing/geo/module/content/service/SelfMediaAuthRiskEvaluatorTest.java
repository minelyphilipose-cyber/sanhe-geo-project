package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.entity.SelfMediaAuthHealthPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SelfMediaAuthRiskEvaluatorTest {
    private final SelfMediaAuthRiskEvaluator evaluator = new SelfMediaAuthRiskEvaluator();
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 11, 12, 0);

    @Test
    void choosesEarlierCredentialReferenceCandidate() {
        var result = evaluator.evaluate(input(policy("declared_then_reference", 14, 3, 7),
                now.minusDays(2), now.minusDays(6), null));

        assertEquals(now.plusDays(1), result.recommendedReverifyAt());
        assertEquals("credential_reference", result.recommendedReverifySource());
        assertEquals("reverify_due_soon", result.riskStatus());
    }

    @Test
    void successfulVerificationSupersedesOldDeclaredExpiry() {
        var result = evaluator.evaluate(input(policy("declared_then_reference", 14, 3, 30),
                now.minusDays(1), now.minusDays(20), now.minusDays(2)));

        assertTrue(result.credentialCandidateSuperseded());
        assertEquals(now.minusDays(2), result.credentialReverifyAt());
        assertEquals("cookie_declared_expiry", result.credentialReverifySource());
        assertEquals(now.plusDays(13), result.recommendedReverifyAt());
        assertEquals("last_verification", result.recommendedReverifySource());
        assertTrue(result.cookieDeclaredExpiryPassed());
        assertEquals("normal", result.riskStatus());
    }

    @Test
    void nullReferenceDoesNotCreateImplicitCandidate() {
        var result = evaluator.evaluate(input(policy("reference_only", 14, 3, null),
                now.minusDays(1), now.minusDays(100), null));

        assertEquals(now.plusDays(13), result.recommendedReverifyAt());
        assertEquals("last_verification", result.recommendedReverifySource());
    }

    @Test
    void disabledPolicyReturnsMonitoringDisabled() {
        SelfMediaAuthHealthPolicy policy = policy("periodic_only", 14, 3, null);
        policy.setEnabled(false);
        var result = evaluator.evaluate(input(policy, now, now, null));
        assertEquals("monitoring_disabled", result.riskStatus());
        assertNull(result.recommendedReverifyAt());
    }

    @Test
    void declaredOnlyIgnoresReferenceAndUsesDeclaredExpiry() {
        var result = evaluator.evaluate(input(policy("declared_only", 14, 3, 1),
                null, now.minusDays(10), now.plusDays(2)));

        assertEquals(now.plusDays(2), result.credentialReverifyAt());
        assertEquals("cookie_declared_expiry", result.recommendedReverifySource());
        assertEquals("reverify_due_soon", result.riskStatus());
    }

    @Test
    void referenceOnlyIgnoresDeclaredExpiry() {
        var result = evaluator.evaluate(input(policy("reference_only", 30, 3, 20),
                null, now.minusDays(10), now.plusDays(1)));

        assertEquals(now.plusDays(10), result.credentialReverifyAt());
        assertEquals("credential_reference", result.recommendedReverifySource());
        assertEquals("normal", result.riskStatus());
    }

    @Test
    void periodicOnlyNeverCreatesCredentialCandidate() {
        var result = evaluator.evaluate(input(policy("periodic_only", 14, 3, 1),
                now.minusDays(2), now.minusDays(10), now.plusDays(1)));

        assertNull(result.credentialReverifyAt());
        assertEquals(now.plusDays(12), result.periodicReverifyAt());
        assertEquals("last_verification", result.recommendedReverifySource());
    }

    @Test
    void missingCredentialWithoutTrustedLoginFactNeedsVerification() {
        var input = new SelfMediaAuthRiskEvaluator.Input(policy("periodic_only", 14, 3, null), now,
                true, false, null, null, null, now.minusDays(10));

        assertEquals("credential_missing", evaluator.evaluate(input).riskStatus());
    }

    @Test
    void trustedLoginFactMakesMissingCookieNonBlocking() {
        var input = new SelfMediaAuthRiskEvaluator.Input(policy("periodic_only", 14, 3, null), now,
                true, false, now.minusDays(1), null, null, now.minusDays(10));

        var result = evaluator.evaluate(input);
        assertEquals("normal", result.riskStatus());
        assertEquals(now.plusDays(13), result.recommendedReverifyAt());
        assertTrue(result.riskReasonCodes().contains("CREDENTIAL_NOT_STORED_NON_BLOCKING"));
    }

    private SelfMediaAuthRiskEvaluator.Input input(SelfMediaAuthHealthPolicy policy,
                                                   LocalDateTime verifiedAt,
                                                   LocalDateTime capturedAt,
                                                   LocalDateTime declaredAt) {
        return new SelfMediaAuthRiskEvaluator.Input(policy, now, true, true, verifiedAt, capturedAt, declaredAt,
                now.minusDays(30));
    }

    private SelfMediaAuthHealthPolicy policy(String mode, int interval, int warning, Integer reference) {
        SelfMediaAuthHealthPolicy policy = new SelfMediaAuthHealthPolicy();
        policy.setEnabled(true);
        policy.setCredentialExpiryMode(mode);
        policy.setReverifyIntervalDays(interval);
        policy.setWarningDays(warning);
        policy.setCredentialReferenceDays(reference);
        return policy;
    }
}
