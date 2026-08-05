package com.huanjing.geo.module.retention.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PollRetentionSliceLockServiceTest {

    @Test
    void boundsLockWaitAndRestoresSessionDefaultAroundAtomicUpsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                "SELECT @@SESSION.innodb_lock_wait_timeout", Integer.class)).thenReturn(37);
        PollRetentionSliceLockService service = new PollRetentionSliceLockService(jdbcTemplate);

        service.lockSlice(100L, LocalDate.of(2026, 8, 5), "a");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
        verify(jdbcTemplate).execute("SET SESSION innodb_lock_wait_timeout = 1");
        verify(jdbcTemplate).execute("SET SESSION innodb_lock_wait_timeout = 37");
        assertTrue(sql.getValue().contains("ON DUPLICATE KEY UPDATE id = id"));
        assertFalse(sql.getValue().contains("INSERT IGNORE"));
        assertFalse(sql.getValue().contains("FOR UPDATE"));
    }
}
