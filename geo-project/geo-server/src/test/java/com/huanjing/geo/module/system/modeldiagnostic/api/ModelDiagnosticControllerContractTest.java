package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.exception.GlobalExceptionHandler;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticModelTier;
import com.huanjing.geo.module.system.modeldiagnostic.execution.ModelDiagnosticIdempotencyConflictException;
import com.huanjing.geo.module.system.modeldiagnostic.history.ModelDiagnosticHistoryService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModelDiagnosticControllerContractTest {

    private final ModelDiagnosticApiService apiService = mock(ModelDiagnosticApiService.class);
    private final ModelDiagnosticHistoryService historyService = mock(ModelDiagnosticHistoryService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ModelDiagnosticController controller = new ModelDiagnosticController(
                mock(ModelDiagnosticPlatformCatalogService.class),
                mock(ModelDiagnosticProbeCatalog.class), apiService, historyService,
                mock(ModelDiagnosticRunViewMapper.class), mock(CurrentUserService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void frozenModePropertyBindsToInternalDiagnosticMode() throws Exception {
        when(apiService.execute(any())).thenReturn(null);
        ArgumentCaptor<ModelDiagnosticRunRequest> request =
                ArgumentCaptor.forClass(ModelDiagnosticRunRequest.class);

        mockMvc.perform(post("/api/admin/model-diagnostics/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("WEB_SEARCH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        org.mockito.Mockito.verify(apiService).execute(request.capture());
        assertEquals(ModelDiagnosticMode.WEB_SEARCH, request.getValue().diagnosticMode());
        assertEquals(ModelDiagnosticModelTier.LOW, request.getValue().modelTier());
    }

    @Test
    void missingFrozenModePropertyReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/model-diagnostics/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void permissionFailureReturns403() throws Exception {
        when(apiService.execute(any())).thenThrow(new AccessDeniedException("denied"));

        mockMvc.perform(post("/api/admin/model-diagnostics/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("BASIC_CHAT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void ownedDetailNotFoundReturns404() throws Exception {
        when(historyService.detail(88L)).thenThrow(
                new BizException(404, "Diagnostic run not found", 404, null));

        mockMvc.perform(get("/api/admin/model-diagnostics/runs/88"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void idempotencyConflictReturns409() throws Exception {
        when(apiService.execute(any())).thenThrow(
                new ModelDiagnosticIdempotencyConflictException());

        mockMvc.perform(post("/api/admin/model-diagnostics/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("BASIC_CHAT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    private String validBody(String mode) {
        String modeField = mode == null ? "" : ",\"mode\":\"" + mode + "\"";
        return "{\"sessionId\":\"" + UUID.randomUUID()
                + "\",\"clientRequestId\":\"" + UUID.randomUUID()
                + "\",\"platformConfigId\":2,\"modelTier\":\"LOW\"" + modeField
                + ",\"testMode\":\"FREE_CHAT\",\"userMessage\":\"hello\"}";
    }
}
