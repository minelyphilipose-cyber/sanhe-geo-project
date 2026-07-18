package com.huanjing.geo.module.system.modeldiagnostic.history;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticSession;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticRunMapper;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticSessionMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDiagnosticHistoryServiceTest {

    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final AiModelDiagnosticRunMapper runMapper = mock(AiModelDiagnosticRunMapper.class);
    private final AiModelDiagnosticSessionMapper sessionMapper =
            mock(AiModelDiagnosticSessionMapper.class);
    private final ModelDiagnosticHistoryService service =
            new ModelDiagnosticHistoryService(currentUserService, runMapper, sessionMapper);

    @BeforeEach
    void setUp() {
        SysUser operator = new SysUser();
        operator.setId(42L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
    }

    @Test
    void pageIsOperatorScopedAndUsesBoundedLightProjection() {
        ModelDiagnosticHistoryQuery query = new ModelDiagnosticHistoryQuery(
                0, 1_000, 3L, " model-a ", " WEB_SEARCH ",
                " SUCCEEDED ", " PASS ", null, null);
        when(runMapper.countOwnedHistory(eq(42L), any())).thenReturn(1L);
        ModelDiagnosticRunSummary summary = new ModelDiagnosticRunSummary();
        summary.setId(7L);
        when(runMapper.selectOwnedHistory(eq(42L), any(), eq(0L), eq(100)))
                .thenReturn(List.of(summary));

        ModelDiagnosticHistoryPage result = service.page(query);

        assertEquals(1, result.page());
        assertEquals(100, result.size());
        assertEquals(1L, result.total());
        assertEquals(7L, result.records().get(0).getId());
        verify(currentUserService).ensurePermission("ai.platform.diagnose");
    }

    @Test
    void detailNeverFallsBackToUnscopedPrimaryKeyLookup() {
        AiModelDiagnosticRun run = new AiModelDiagnosticRun();
        run.setId(8L);
        when(runMapper.selectOwnedRun(8L, 42L)).thenReturn(run);

        assertSame(run, service.detail(8L));
        verify(runMapper).selectOwnedRun(8L, 42L);
        verify(runMapper, never()).selectById(8L);
    }

    @Test
    void sessionRestoreValidatesUuidAndOwnership() {
        String sessionId = UUID.randomUUID().toString();
        AiModelDiagnosticSession session = new AiModelDiagnosticSession();
        when(sessionMapper.selectOwned(42L, sessionId)).thenReturn(session);
        AiModelDiagnosticRun run = new AiModelDiagnosticRun();
        run.setId(10L);
        when(runMapper.selectOwnedSessionRuns(42L, sessionId)).thenReturn(List.of(run));

        assertEquals(List.of(run), service.sessionRuns(sessionId));

        BizException invalid = assertThrows(
                BizException.class, () -> service.sessionRuns("not-a-uuid"));
        assertEquals(400, invalid.getCode());
    }

    @Test
    void rejectsInvertedHistoryRangeBeforeDatabaseAccess() {
        ModelDiagnosticHistoryQuery query = new ModelDiagnosticHistoryQuery(
                1, 20, null, null, null, null, null,
                LocalDateTime.of(2026, 7, 16, 0, 0),
                LocalDateTime.of(2026, 7, 15, 0, 0));

        BizException error = assertThrows(BizException.class, () -> service.page(query));

        assertEquals(400, error.getCode());
        verify(runMapper, never()).countOwnedHistory(any(), any());
    }
}
