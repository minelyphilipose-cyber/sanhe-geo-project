package com.huanjing.geo.module.project.controller;

import com.huanjing.geo.common.exception.GlobalExceptionHandler;
import com.huanjing.geo.module.content.service.ProjectSelfMediaPlanningService;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaPlanningTaskVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProjectSelfMediaPlanningControllerTest {
    private final ProjectSelfMediaPlanningService service = mock(ProjectSelfMediaPlanningService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ProjectSelfMediaPlanningController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void acceptsSupplementRequestWithIdempotencyKeyAndReturnsTaskStatus() throws Exception {
        var task = new ProjectSelfMediaPlanningTaskVO();
        task.setId(42L);
        task.setStatus("pending");
        when(service.submit(eq(7L), any(), eq("test-request"))).thenReturn(task);
        mvc.perform(post("/api/projects/7/self-media-schedules/auto-create-async")
                        .header("Idempotency-Key", "test-request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMonth\":\"2026-10\",\"supplementExistingBatch\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.status").value("pending"));
        verify(service).submit(eq(7L), argThat(request -> Boolean.TRUE.equals(request.getSupplementExistingBatch())), eq("test-request"));
    }

    @Test
    void invalidMonthIsRejectedBeforeSubmitting() throws Exception {
        mvc.perform(post("/api/projects/7/self-media-schedules/auto-create-async")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetMonth\":\"2026/10\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void readsLatestTaskForTheSelectedProjectAndMonth() throws Exception {
        mvc.perform(get("/api/projects/7/self-media-planning-tasks/2026-10/latest"))
                .andExpect(status().isOk());
        verify(service).latest(7L, "2026-10");
    }
}
