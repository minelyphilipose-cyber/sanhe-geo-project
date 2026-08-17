package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.common.storage.ObjectStorageService;
import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunRequest;
import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunResponse;
import com.huanjing.geo.module.content.dto.ArticleBodyPurgeRequest;
import com.huanjing.geo.module.content.dto.ArticleBodyPurgeResponse;
import com.huanjing.geo.module.content.service.ArticleBodyPurgeService;
import com.huanjing.geo.module.content.service.ArticleRetentionDryRunService;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunRequest;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunResponse;
import com.huanjing.geo.module.dispatch.service.PollSummaryRecomputeService;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardEntityJudgeService;
import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetentionExecutionFloorTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void executeClampsRetentionWindowsToProductionMinimums() {
        JdbcTemplate jdbcTemplate = emptyJdbcTemplate();
        DataRetentionRunAuditService auditService = auditService();
        DataRetentionProperties properties = executeEnabledProperties();

        ArticleArchiveDryRunRequest archiveRequest = new ArticleArchiveDryRunRequest();
        archiveRequest.setMinPublishedAgeDays(1);
        archiveRequest.setReason("retention floor test");
        ArticleArchiveDryRunResponse archive = new ArticleRetentionDryRunService(
                jdbcTemplate,
                mock(CurrentUserService.class),
                auditService,
                mock(ObjectStorageService.class),
                properties
        ).runScheduled(archiveRequest, false);

        ArticleBodyPurgeRequest purgeRequest = new ArticleBodyPurgeRequest();
        purgeRequest.setRetentionDays(1);
        purgeRequest.setArchiveGraceHours(1);
        purgeRequest.setReason("retention floor test");
        ArticleBodyPurgeResponse purge = new ArticleBodyPurgeService(
                jdbcTemplate,
                mock(CurrentUserService.class),
                mock(ObjectStorageService.class),
                auditService,
                properties
        ).runScheduled(purgeRequest, false);

        PollRetentionDryRunRequest pollRequest = new PollRetentionDryRunRequest();
        pollRequest.setHotRetentionDays(1);
        pollRequest.setReason("retention floor test");
        PollRetentionDryRunService pollService = pollDryRunService(jdbcTemplate, properties);
        assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> pollService.runScheduled(pollRequest, false, 1L));

        assertEquals(90, archive.getMinPublishedAgeDays());
        assertEquals(90, purge.getRetentionDays());
        assertEquals(24, purge.getArchiveGraceHours());
        assertFalse(archive.getSimulationOnly());
        assertFalse(purge.getSimulationOnly());
    }

    @Test
    void dryRunAllowsShorterSimulationButMarksItSimulationOnly() {
        JdbcTemplate jdbcTemplate = emptyJdbcTemplate();
        DataRetentionRunAuditService auditService = auditService();
        DataRetentionProperties properties = executeEnabledProperties();

        ArticleArchiveDryRunRequest archiveRequest = new ArticleArchiveDryRunRequest();
        archiveRequest.setMinPublishedAgeDays(1);
        ArticleArchiveDryRunResponse archive = new ArticleRetentionDryRunService(
                jdbcTemplate,
                mock(CurrentUserService.class),
                auditService,
                mock(ObjectStorageService.class),
                properties
        ).runScheduled(archiveRequest, true);

        ArticleBodyPurgeRequest purgeRequest = new ArticleBodyPurgeRequest();
        purgeRequest.setRetentionDays(1);
        purgeRequest.setArchiveGraceHours(1);
        ArticleBodyPurgeResponse purge = new ArticleBodyPurgeService(
                jdbcTemplate,
                mock(CurrentUserService.class),
                mock(ObjectStorageService.class),
                auditService,
                properties
        ).runScheduled(purgeRequest, true);

        PollRetentionDryRunRequest pollRequest = new PollRetentionDryRunRequest();
        pollRequest.setHotRetentionDays(1);
        PollRetentionDryRunResponse poll = pollDryRunService(jdbcTemplate, properties)
                .runScheduled(pollRequest, true, 1L);

        assertEquals(1, archive.getMinPublishedAgeDays());
        assertEquals(1, purge.getRetentionDays());
        assertEquals(1, purge.getArchiveGraceHours());
        assertEquals(1, poll.getHotRetentionDays());
        assertTrue(archive.getSimulationOnly());
        assertTrue(purge.getSimulationOnly());
        assertTrue(poll.getSimulationOnly());
    }

    @Test
    void scheduledExecuteCannotBypassReasonRequirement() {
        JdbcTemplate jdbcTemplate = emptyJdbcTemplate();
        DataRetentionRunAuditService auditService = auditService();
        DataRetentionProperties properties = executeEnabledProperties();

        ArticleBodyPurgeService articlePurge = new ArticleBodyPurgeService(
                jdbcTemplate,
                mock(CurrentUserService.class),
                mock(ObjectStorageService.class),
                auditService,
                properties
        );
        PollRetentionDryRunService pollPurge = pollDryRunService(jdbcTemplate, properties);

        assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> articlePurge.runScheduled(new ArticleBodyPurgeRequest(), false));
        assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> pollPurge.runScheduled(new PollRetentionDryRunRequest(), false, 1L));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private JdbcTemplate emptyJdbcTemplate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        return jdbcTemplate;
    }

    private DataRetentionRunAuditService auditService() {
        DataRetentionRunAuditService auditService = mock(DataRetentionRunAuditService.class);
        when(auditService.startRun(anyString(), anyString(), any(), any(), any())).thenReturn(1L);
        return auditService;
    }

    private PollRetentionDryRunService pollDryRunService(JdbcTemplate jdbcTemplate,
                                                         DataRetentionProperties properties) {
        DataRetentionP0AuditService typedAudit = mock(DataRetentionP0AuditService.class);
        RetentionCompatibilityContract contract = new RetentionCompatibilityContract(
                "poll_results", 1, new byte[32], "poll-results-v2");
        when(typedAudit.startRun(any())).thenReturn(new RetentionRunContext(1L,
                "00000000-0000-0000-0000-000000000001", "scheduler", "dry_run", contract,
                "test", "test", RunReplayStatus.CREATED, "running", Instant.now()));
        when(typedAudit.createOperationlessAttempt(any(), any()))
                .thenReturn(mock(RetentionAttemptContext.class));
        return new PollRetentionDryRunService(jdbcTemplate, mock(CurrentUserService.class),
                mock(PollSummaryRecomputeService.class), mock(MobileDashboardEntityJudgeService.class),
                properties, typedAudit, new DataRetentionCanonicalEncoder());
    }

    private DataRetentionProperties executeEnabledProperties() {
        DataRetentionProperties properties = new DataRetentionProperties();
        properties.getArticleArchive().setExecuteEnabled(true);
        properties.getArticlePurge().setExecuteEnabled(true);
        properties.getPollResults().setExecuteEnabled(true);
        return properties;
    }
}
