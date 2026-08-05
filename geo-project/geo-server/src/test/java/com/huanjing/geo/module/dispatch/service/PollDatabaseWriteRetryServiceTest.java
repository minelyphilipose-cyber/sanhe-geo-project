package com.huanjing.geo.module.dispatch.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DeadlockLoserDataAccessException;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PollDatabaseWriteRetryServiceTest {

    private final PollDatabaseWriteRetryService service = new PollDatabaseWriteRetryService();

    @Test
    void retriesTransientLockFailureAndReturnsRecoveredValue() {
        AtomicInteger calls = new AtomicInteger();

        String result = service.execute("test projection", () -> {
            if (calls.incrementAndGet() == 1) {
                throw new DeadlockLoserDataAccessException("Deadlock found", null);
            }
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(2, calls.get());
    }

    @Test
    void recognizesMysqlNowaitAndTimeoutCodesThroughCauseChain() {
        assertTrue(service.isTransientLockFailure(
                new RuntimeException(new SQLException("timeout", "HY000", 1205))));
        assertTrue(service.isTransientLockFailure(
                new RuntimeException(new SQLException("nowait", "HY000", 3572))));
    }

    @Test
    void doesNotRetryNonLockFailure() {
        AtomicInteger calls = new AtomicInteger();
        IllegalArgumentException expected = new IllegalArgumentException("invalid payload");

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class,
                () -> service.execute("test projection", () -> {
                    calls.incrementAndGet();
                    throw expected;
                }));

        assertSame(expected, actual);
        assertEquals(1, calls.get());
    }
}
