package com.huanjing.geo.module.dispatch.websearch.purge;

import com.huanjing.geo.module.dispatch.entity.PollAuditPurgeRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class PollAuditPurgeService {

    private final PollAuditPurgeAuditWriter auditWriter;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public PollAuditPurgeService(PollAuditPurgeAuditWriter auditWriter,
                                 TransactionTemplate transactionTemplate) {
        this(auditWriter, transactionTemplate, Clock.systemDefaultZone());
    }

    PollAuditPurgeService(PollAuditPurgeAuditWriter auditWriter,
                          TransactionTemplate transactionTemplate,
                          Clock clock) {
        this.auditWriter = auditWriter;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public Long execute(PollPurgeRequest request, PollPurgeOperation operation) {
        PollAuditPurgeRun run = auditWriter.prepare(request, now());
        auditWriter.markRunning(run.getId(), now());
        try {
            String affectedRowsJson = transactionTemplate.execute(status ->
                    operation.executeAndReturnAffectedRowsJson());
            auditWriter.markSucceeded(run.getId(), affectedRowsJson, now());
            return run.getId();
        } catch (RuntimeException ex) {
            auditWriter.markFailed(run.getId(), ex.getMessage(), now());
            throw ex;
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
