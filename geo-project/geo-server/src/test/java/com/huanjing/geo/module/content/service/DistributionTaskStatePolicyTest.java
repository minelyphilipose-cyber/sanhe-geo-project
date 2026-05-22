package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributionTaskStatePolicyTest {

    @Test
    void allowsDocumentedApiAutoStatePairs() {
        assertTrue(DistributionTaskStatePolicy.isAllowed("AUTO", "pending", null));
        assertTrue(DistributionTaskStatePolicy.isAllowed("AUTO", "submitted", "under_review"));
        assertTrue(DistributionTaskStatePolicy.isAllowed("AUTO", "submitted", "unknown"));
        assertTrue(DistributionTaskStatePolicy.isAllowed("AUTO", "published", "published"));
        assertTrue(DistributionTaskStatePolicy.isAllowed("AUTO", "failed", "rejected"));
        assertTrue(DistributionTaskStatePolicy.isAllowed("AUTO", "published", "offline"));
    }

    @Test
    void rejectsSemiAutoPublishedReviewStatus() {
        assertFalse(DistributionTaskStatePolicy.isAllowed("SEMI_AUTO", "published", "published"));
        assertThrows(IllegalArgumentException.class,
                () -> DistributionTaskStatePolicy.requireAllowed("SEMI_AUTO", "published", "published"));
    }

    @Test
    void allowsOnlyNotApplicableReviewForSemiAutoStates() {
        assertTrue(DistributionTaskStatePolicy.isAllowed("SEMI_AUTO", "token_issued", "not_applicable"));
        assertTrue(DistributionTaskStatePolicy.isAllowed("SEMI_AUTO", "filling", "not_applicable"));
        assertTrue(DistributionTaskStatePolicy.isAllowed("SEMI_AUTO", "filled", "not_applicable"));
        assertTrue(DistributionTaskStatePolicy.isAllowed("SEMI_AUTO", "failed", "not_applicable"));
        assertFalse(DistributionTaskStatePolicy.isAllowed("SEMI_AUTO", "filled", "under_review"));
    }

    @Test
    void automaticReviewCandidatesExcludeSemiAutoAndTerminalPublished() {
        assertTrue(DistributionTaskStatePolicy.isAutomaticReviewCandidate("AUTO", "mp_account", "submitted", "under_review"));
        assertTrue(DistributionTaskStatePolicy.isAutomaticReviewCandidate("AUTO", "mp_account", "submitted", "unknown"));
        assertFalse(DistributionTaskStatePolicy.isAutomaticReviewCandidate("SEMI_AUTO", "mp_account", "filled", "not_applicable"));
        assertFalse(DistributionTaskStatePolicy.isAutomaticReviewCandidate("AUTO", "mp_account", "published", "published"));
        assertFalse(DistributionTaskStatePolicy.isAutomaticReviewCandidate("AUTO", "mp_account", "published", "offline"));
    }

    @Test
    void reviewTransitionMapsTerminalPlatformReviewToTaskStatus() {
        assertEquals("published", DistributionTaskStatePolicy.transitionForReview("published").taskStatus());
        assertEquals("failed", DistributionTaskStatePolicy.transitionForReview("rejected").taskStatus());
        assertEquals("published", DistributionTaskStatePolicy.transitionForReview("offline").taskStatus());
        assertEquals("submitted", DistributionTaskStatePolicy.transitionForReview("under_review").taskStatus());
        assertEquals("submitted", DistributionTaskStatePolicy.transitionForReview("unknown").taskStatus());
    }
}
