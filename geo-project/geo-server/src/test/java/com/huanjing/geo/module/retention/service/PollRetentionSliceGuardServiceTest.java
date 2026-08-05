package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PollRetentionSliceGuardServiceTest {

    @Test
    void allowsWritesWhenSliceHasNotBeenPurged() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        PollRetentionSliceLockService lockService = mock(PollRetentionSliceLockService.class);
        PollRetentionSliceGuardService service = new PollRetentionSliceGuardService(jdbcTemplate, lockService);

        assertDoesNotThrow(() -> service.lockAndRequireWritable(
                100L, LocalDate.of(2026, 1, 1), "a"));

        verify(lockService).lockSlice(100L, LocalDate.of(2026, 1, 1), "A");
    }

    @Test
    void rejectsLateWritesAfterSliceWasPurged() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        PollRetentionSliceGuardService service = new PollRetentionSliceGuardService(
                jdbcTemplate, mock(PollRetentionSliceLockService.class));

        BizException error = assertThrows(BizException.class, () -> service.lockAndRequireWritable(
                100L, LocalDate.of(2026, 1, 1), "A"));

        assertEquals(409, error.getCode());
        assertEquals("Poll retention slice was already purged", error.getMessage());
    }
}
