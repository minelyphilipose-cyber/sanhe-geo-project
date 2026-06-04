package com.huanjing.geo.module.extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.service.CompanyChannelQuotaService;
import com.huanjing.geo.module.content.service.SelfMediaPublishScheduleService;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.extension.ExtensionErrorCodes;
import com.huanjing.geo.test.AbstractAuditDbIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExtensionTaskStateServiceHeartbeatAuditDbTest extends AbstractAuditDbIntegrationTest {

    @Test
    void firstHeartbeatWritesSingleStartedAuditLogRow() {
        insertSemiAutoTaskFixture("filling", false);
        DistributionTaskMapper taskMapper = mock(DistributionTaskMapper.class);
        ArticleDraftMapper articleDraftMapper = mock(ArticleDraftMapper.class);
        SemiAutoTaskAccessService semiAutoTaskAccessService = mock(SemiAutoTaskAccessService.class);
        CompanyChannelQuotaService companyChannelQuotaService = mock(CompanyChannelQuotaService.class);
        ExtensionRedisStore redisStore = mock(ExtensionRedisStore.class);
        DistributionTask firstLoad = task("filling", null);
        DistributionTask secondLoad = task("filling", LocalDateTime.now());
        when(semiAutoTaskAccessService.requireOperableTask(TEST_TASK_ID, TEST_OPERATOR_ID))
                .thenReturn(new SemiAutoTaskAccessService.SemiAutoTaskContext(firstLoad, TEST_BRAND_ID))
                .thenReturn(new SemiAutoTaskAccessService.SemiAutoTaskContext(secondLoad, TEST_BRAND_ID));
        when(taskMapper.touchSemiAutoHeartbeat(eq(TEST_TASK_ID), any())).thenReturn(1);
        when(redisStore.incrementWithTtl(eq("extension:task:heartbeat:" + TEST_TASK_ID), any(Duration.class)))
                .thenReturn(1L);
        ExtensionTaskStateService taskStateService = service(taskMapper, articleDraftMapper, semiAutoTaskAccessService, companyChannelQuotaService, redisStore);

        assertEquals("filling", taskStateService.heartbeat(TEST_TASK_ID, TEST_OPERATOR_ID, null).status());
        assertEquals("filling", taskStateService.heartbeat(TEST_TASK_ID, TEST_OPERATOR_ID, null).status());

        assertEquals(1, queryAuditLog("SEMI_AUTO_TASK_HEARTBEAT_STARTED").size());
        Map<String, Object> audit = requireSingleAudit("SEMI_AUTO_TASK_HEARTBEAT_STARTED");
        assertEquals(TEST_OPERATOR_ID, ((Number) audit.get("actor_id")).longValue());
        assertEquals(TEST_BRAND_ID, ((Number) audit.get("brand_id")).longValue());
        assertEquals(TEST_ACCOUNT_ID, ((Number) audit.get("account_id")).longValue());
        assertEquals(TEST_TASK_ID, ((Number) audit.get("task_id")).longValue());
        assertEquals("SUCCESS", audit.get("result"));
        assertFalse(Boolean.TRUE.equals(audit.get("sensitive")));
    }

    @Test
    void heartbeatStateConflictWritesDeniedAuditLogRow() {
        insertSemiAutoTaskFixture("pending", false);
        DistributionTaskMapper taskMapper = mock(DistributionTaskMapper.class);
        ArticleDraftMapper articleDraftMapper = mock(ArticleDraftMapper.class);
        SemiAutoTaskAccessService semiAutoTaskAccessService = mock(SemiAutoTaskAccessService.class);
        CompanyChannelQuotaService companyChannelQuotaService = mock(CompanyChannelQuotaService.class);
        ExtensionRedisStore redisStore = mock(ExtensionRedisStore.class);
        when(semiAutoTaskAccessService.requireOperableTask(TEST_TASK_ID, TEST_OPERATOR_ID))
                .thenReturn(new SemiAutoTaskAccessService.SemiAutoTaskContext(task("pending", null), TEST_BRAND_ID));
        when(taskMapper.touchSemiAutoHeartbeat(eq(TEST_TASK_ID), any())).thenReturn(0);
        when(redisStore.incrementWithTtl(eq("extension:task:heartbeat:" + TEST_TASK_ID), any(Duration.class)))
                .thenReturn(1L);
        ExtensionTaskStateService taskStateService = service(taskMapper, articleDraftMapper, semiAutoTaskAccessService, companyChannelQuotaService, redisStore);

        BizException ex = assertThrows(BizException.class,
                () -> taskStateService.heartbeat(TEST_TASK_ID, TEST_OPERATOR_ID, null));

        assertEquals(ExtensionErrorCodes.TASK_STATE_CONFLICT, ex.getCode());
        Map<String, Object> audit = requireSingleAudit("SEMI_AUTO_TASK_HEARTBEAT_DENIED");
        assertEquals("DENIED", audit.get("result"));
        assertEquals(String.valueOf(ExtensionErrorCodes.TASK_STATE_CONFLICT), audit.get("error_code"));
        assertEquals(TEST_TASK_ID, ((Number) audit.get("task_id")).longValue());
    }

    private ExtensionTaskStateService service(
            DistributionTaskMapper taskMapper,
            ArticleDraftMapper articleDraftMapper,
            SemiAutoTaskAccessService semiAutoTaskAccessService,
            CompanyChannelQuotaService companyChannelQuotaService,
            ExtensionRedisStore redisStore
    ) {
        return new ExtensionTaskStateService(
                taskMapper,
                articleDraftMapper,
                semiAutoTaskAccessService,
                mock(InternalScopeService.class),
                companyChannelQuotaService,
                mock(SelfMediaPublishScheduleService.class),
                redisStore,
                new ExtensionAuditSupport(auditService),
                auditService,
                new ObjectMapper()
        );
    }

    private DistributionTask task(String status, LocalDateTime lastHeartbeatAt) {
        DistributionTask task = new DistributionTask();
        task.setId(TEST_TASK_ID);
        task.setProjectId(TEST_PROJECT_ID);
        task.setSelfMediaAccountId(TEST_ACCOUNT_ID);
        task.setDispatchMode("SEMI_AUTO");
        task.setStatus(status);
        task.setLastHeartbeatAt(lastHeartbeatAt);
        return task;
    }

}
