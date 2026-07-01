package com.huanjing.geo.module.project.service;

import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectStartRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectDisplayStatusResolverTest {

    private final ProjectDisplayStatusResolver resolver = new ProjectDisplayStatusResolver();

    @Test
    void pendingStartWithoutRequest_displaysDraft() {
        ProjectDisplayStatusResult result = resolver.resolve(project("pending_start"), null);

        assertEquals("draft", result.projectDisplayStatus());
        assertTrue(result.editable());
        assertTrue(result.submittable());
    }

    @Test
    void expired_displaysArchived() {
        ProjectDisplayStatusResult result = resolver.resolve(project("expired"), request("submitted"));

        assertEquals("archived", result.projectDisplayStatus());
        assertFalse(result.editable());
        assertFalse(result.submittable());
    }

    @Test
    void latestSubmittedRequest_displaysSubmitted() {
        ProjectDisplayStatusResult result = resolver.resolve(project("draft"), request("submitted"));

        assertEquals("submitted", result.projectDisplayStatus());
        assertFalse(result.editable());
        assertFalse(result.submittable());
    }

    @Test
    void latestRejectedRequest_displaysRejected() {
        ProjectDisplayStatusResult result = resolver.resolve(project("draft"), request("rejected"));

        assertEquals("rejected", result.projectDisplayStatus());
        assertTrue(result.editable());
        assertTrue(result.submittable());
    }

    @Test
    void latestCancelledRequest_returnsDraft() {
        ProjectDisplayStatusResult result = resolver.resolve(project("submitted"), request("cancelled"));

        assertEquals("draft", result.projectDisplayStatus());
    }

    @Test
    void latestApprovedRequest_displaysApprovedPendingSetup() {
        ProjectDisplayStatusResult result = resolver.resolve(project("submitted"), request("approved"));

        assertEquals("approved_pending_setup", result.projectDisplayStatus());
    }

    @Test
    void activeProjectOverridesHistoricalRequest() {
        ProjectDisplayStatusResult result = resolver.resolve(project("active"), request("rejected"));

        assertEquals("active", result.projectDisplayStatus());
    }

    @Test
    void setupReadyProject_displaysSetupReady() {
        ProjectDisplayStatusResult result = resolver.resolve(project("setup_ready"), request("approved"));

        assertEquals("setup_ready", result.projectDisplayStatus());
    }

    private Project project(String status) {
        Project project = new Project();
        project.setStatus(status);
        return project;
    }

    private ProjectStartRequest request(String status) {
        ProjectStartRequest request = new ProjectStartRequest();
        request.setStatus(status);
        return request;
    }
}
