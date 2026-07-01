package com.huanjing.geo.module.project.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectFlowPolicyTest {

    @Test
    void externalStatusSet_excludesApprovalWorkflowStatuses() {
        assertFalse(ProjectFlowPolicy.isExternalStatus("draft"));
        assertFalse(ProjectFlowPolicy.isExternalStatus("submitted"));
        assertFalse(ProjectFlowPolicy.isExternalStatus("rejected"));
        assertFalse(ProjectFlowPolicy.isExternalStatus("approved_pending_setup"));
        assertFalse(ProjectFlowPolicy.isExternalStatus("setup_ready"));
        assertFalse(ProjectFlowPolicy.isExternalStatus("cancelled"));
    }

    @Test
    void externalStatusSet_allowsLegacyOperationalStatuses() {
        assertTrue(ProjectFlowPolicy.isExternalStatus("pending_start"));
        assertTrue(ProjectFlowPolicy.isExternalStatus("active"));
        assertTrue(ProjectFlowPolicy.isExternalStatus("paused"));
        assertTrue(ProjectFlowPolicy.isExternalStatus("completed"));
        assertTrue(ProjectFlowPolicy.isExternalStatus("archived"));
        assertTrue(ProjectFlowPolicy.isExternalStatus("expired"));
    }

    @Test
    void deliveryProgressStatusSet_includesPartnerApprovalAndSetupStatuses() {
        assertTrue(ProjectFlowPolicy.DELIVERY_PROGRESS_STATUS_SET.contains("pending_start"));
        assertTrue(ProjectFlowPolicy.DELIVERY_PROGRESS_STATUS_SET.contains("submitted"));
        assertTrue(ProjectFlowPolicy.DELIVERY_PROGRESS_STATUS_SET.contains("approved_pending_setup"));
        assertTrue(ProjectFlowPolicy.DELIVERY_PROGRESS_STATUS_SET.contains("setup_ready"));
        assertTrue(ProjectFlowPolicy.DELIVERY_PROGRESS_STATUS_SET.contains("active"));
        assertTrue(ProjectFlowPolicy.DELIVERY_PROGRESS_STATUS_SET.contains("paused"));
        assertFalse(ProjectFlowPolicy.DELIVERY_PROGRESS_STATUS_SET.contains("draft"));
        assertFalse(ProjectFlowPolicy.DELIVERY_PROGRESS_STATUS_SET.contains("rejected"));
        assertFalse(ProjectFlowPolicy.DELIVERY_PROGRESS_STATUS_SET.contains("archived"));
    }
}
