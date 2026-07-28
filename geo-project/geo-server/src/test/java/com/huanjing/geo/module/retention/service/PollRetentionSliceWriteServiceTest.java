package com.huanjing.geo.module.retention.service;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class PollRetentionSliceWriteServiceTest {

    @Test
    void locksAndChecksSliceBeforeRunningWrite() {
        PollRetentionSliceGuardService guard = mock(PollRetentionSliceGuardService.class);
        Runnable operation = mock(Runnable.class);
        PollRetentionSliceWriteService service = new PollRetentionSliceWriteService(guard);
        LocalDate batchDate = LocalDate.of(2026, 7, 1);

        service.execute(11L, batchDate, "A", operation);

        InOrder ordered = inOrder(guard, operation);
        ordered.verify(guard).lockAndRequireWritable(11L, batchDate, "A");
        ordered.verify(operation).run();
    }

    @Test
    void supplierVariantReturnsValueAndDefinesTransactionBoundary() throws Exception {
        PollRetentionSliceGuardService guard = mock(PollRetentionSliceGuardService.class);
        PollRetentionSliceWriteService service = new PollRetentionSliceWriteService(guard);
        AtomicBoolean invoked = new AtomicBoolean();

        String value = service.execute(11L, LocalDate.of(2026, 7, 1), "A", () -> {
            invoked.set(true);
            return "written";
        });

        assertThat(value).isEqualTo("written");
        assertThat(invoked).isTrue();
        assertNotNull(PollRetentionSliceWriteService.class
                .getMethod("execute", Long.class, LocalDate.class, String.class,
                        java.util.function.Supplier.class)
                .getAnnotation(Transactional.class));
    }
}
