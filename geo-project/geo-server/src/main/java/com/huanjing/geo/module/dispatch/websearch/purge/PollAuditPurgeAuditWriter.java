package com.huanjing.geo.module.dispatch.websearch.purge;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.entity.PollAuditPurgeRun;
import com.huanjing.geo.module.dispatch.mapper.PollAuditPurgeRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PollAuditPurgeAuditWriter {

    private final PollAuditPurgeRunMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PollAuditPurgeRun prepare(PollPurgeRequest request, LocalDateTime now) {
        PollAuditPurgeRun run = new PollAuditPurgeRun();
        run.setProjectId(request.projectId());
        run.setRequestedBy(request.requestedBy());
        run.setPurgeReason(request.reason());
        run.setScopeJson(request.scopeJson());
        run.setStatus("PREPARED");
        run.setAuditCommittedAt(now);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        if (mapper.insert(run) != 1) {
            throw new BizException(500, "Failed to commit purge audit before deletion");
        }
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(Long runId, LocalDateTime now) {
        PollAuditPurgeRun update = new PollAuditPurgeRun();
        update.setId(runId);
        update.setStatus("RUNNING");
        update.setStartedAt(now);
        update.setUpdatedAt(now);
        requireUpdated(mapper.updateById(update), runId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(Long runId, String affectedRowsJson, LocalDateTime now) {
        PollAuditPurgeRun update = new PollAuditPurgeRun();
        update.setId(runId);
        update.setStatus("SUCCEEDED");
        update.setAffectedRowsJson(affectedRowsJson);
        update.setCompletedAt(now);
        update.setUpdatedAt(now);
        requireUpdated(mapper.updateById(update), runId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long runId, String errorMessage, LocalDateTime now) {
        PollAuditPurgeRun update = new PollAuditPurgeRun();
        update.setId(runId);
        update.setStatus("FAILED");
        update.setErrorMessage(truncate(errorMessage));
        update.setCompletedAt(now);
        update.setUpdatedAt(now);
        requireUpdated(mapper.updateById(update), runId);
    }

    private void requireUpdated(int rows, Long runId) {
        if (rows != 1) {
            throw new BizException(409, "Purge audit run changed concurrently: " + runId);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "Unknown purge failure";
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
