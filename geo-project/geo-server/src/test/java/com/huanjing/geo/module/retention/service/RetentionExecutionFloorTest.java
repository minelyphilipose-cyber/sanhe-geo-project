package com.huanjing.geo.module.retention.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.huanjing.geo.module.dispatch.websearch.purge.PollAuditPurgeService;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardEntityJudgeService;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

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
        PollRetentionDryRunResponse poll = new PollRetentionDryRunService(
                jdbcTemplate,
                mock(TransactionTemplate.class),
                mock(CurrentUserService.class),
                auditService,
                mock(PollAuditPurgeService.class),
                mock(PollSummaryRecomputeService.class),
                mock(MobileDashboardEntityJudgeService.class),
                properties,
                new ObjectMapper()
        ).runScheduled(pollRequest, false, 1L);

        assertEquals(90, archive.getMinPublishedAgeDays());
        assertEquals(90, purge.getRetentionDays());
        assertEquals(24, purge.getArchiveGraceHours());
        assertEquals(120, poll.getHotRetentionDays());
        assertFalse(archive.getSimulationOnly());
        assertFalse(purge.getSimulationOnly());
        assertFalse(poll.getSimulationOnly());
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
        PollRetentionDryRunResponse poll = new PollRetentionDryRunService(
                jdbcTemplate,
                mock(TransactionTemplate.class),
                mock(CurrentUserService.class),
                auditService,
                mock(PollAuditPurgeService.class),
                mock(PollSummaryRecomputeService.class),
                mock(MobileDashboardEntityJudgeService.class),
                properties,
                new ObjectMapper()
        ).runScheduled(pollRequest, true, 1L);

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
        PollRetentionDryRunService pollPurge = new PollRetentionDryRunService(
                jdbcTemplate,
                mock(TransactionTemplate.class),
                mock(CurrentUserService.class),
                auditService,
                mock(PollAuditPurgeService.class),
                mock(PollSummaryRecomputeService.class),
                mock(MobileDashboardEntityJudgeService.class),
                properties,
                new ObjectMapper()
        );

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

    private DataRetentionProperties executeEnabledProperties() {
        DataRetentionProperties properties = new DataRetentionProperties();
        properties.getArticleArchive().setExecuteEnabled(true);
        properties.getArticlePurge().setExecuteEnabled(true);
        properties.getPollResults().setExecuteEnabled(true);
        return properties;
    }
}
