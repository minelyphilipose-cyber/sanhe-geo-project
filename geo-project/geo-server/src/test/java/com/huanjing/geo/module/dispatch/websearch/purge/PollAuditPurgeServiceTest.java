package com.huanjing.geo.module.dispatch.websearch.purge;

import com.huanjing.geo.module.dispatch.entity.PollAuditPurgeRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollAuditPurgeServiceTest {

    @Mock
    private PollAuditPurgeAuditWriter auditWriter;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private TransactionStatus transactionStatus;
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-14T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void commitsAuditBeforeOpeningTheDestructiveTransaction() {
        PollAuditPurgeService service = new PollAuditPurgeService(auditWriter, transactionTemplate, clock);
        PollPurgeRequest request = new PollPurgeRequest(3L, 8L, "retention", "{\"attemptIds\":[9]}");
        PollAuditPurgeRun run = new PollAuditPurgeRun();
        run.setId(12L);
        when(auditWriter.prepare(eq(request), any())).thenReturn(run);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<String> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });

        Long runId = service.execute(request, () -> "{\"pollProviderCalls\":1}");

        assertThat(runId).isEqualTo(12L);
        InOrder order = inOrder(auditWriter, transactionTemplate);
        order.verify(auditWriter).prepare(eq(request), any());
        order.verify(auditWriter).markRunning(eq(12L), any());
        order.verify(transactionTemplate).execute(any());
        order.verify(auditWriter).markSucceeded(eq(12L), eq("{\"pollProviderCalls\":1}"), any());
    }
}
