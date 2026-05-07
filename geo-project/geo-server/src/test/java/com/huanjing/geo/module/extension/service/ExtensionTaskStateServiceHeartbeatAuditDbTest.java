package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.extension.ExtensionErrorCodes;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class ExtensionTaskStateServiceHeartbeatAuditDbTest extends AbstractAuditDbIntegrationTest {

    @Test
    void firstHeartbeatWritesSingleStartedAuditLogRow() {
        insertSemiAutoTaskFixture("filling", false);
        DistributionTaskMapper taskMapper = mock(DistributionTaskMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        BrandAccessService brandAccessService = mock(BrandAccessService.class);
        ExtensionRedisStore redisStore = mock(ExtensionRedisStore.class);
        DistributionTask firstLoad = task("filling", null);
        DistributionTask secondLoad = task("filling", LocalDateTime.now());
        when(taskMapper.selectById(TEST_TASK_ID)).thenReturn(firstLoad, secondLoad);
        when(taskMapper.touchSemiAutoHeartbeat(eq(TEST_TASK_ID), any())).thenReturn(1);
        when(projectMapper.selectById(TEST_PROJECT_ID)).thenReturn(project());
        when(redisStore.incrementWithTtl(eq("extension:task:heartbeat:" + TEST_TASK_ID), any(Duration.class)))
                .thenReturn(1L);
        ExtensionTaskStateService taskStateService = service(taskMapper, projectMapper, brandAccessService, redisStore);

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
        verify(brandAccessService, times(2)).requireBrandAccess(TEST_BRAND_ID, TEST_OPERATOR_ID, BrandAccessAction.OPERATE);
    }

    @Test
    void heartbeatStateConflictWritesDeniedAuditLogRow() {
        insertSemiAutoTaskFixture("pending", false);
        DistributionTaskMapper taskMapper = mock(DistributionTaskMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        BrandAccessService brandAccessService = mock(BrandAccessService.class);
        ExtensionRedisStore redisStore = mock(ExtensionRedisStore.class);
        when(taskMapper.selectById(TEST_TASK_ID)).thenReturn(task("pending", null));
        when(taskMapper.touchSemiAutoHeartbeat(eq(TEST_TASK_ID), any())).thenReturn(0);
        when(projectMapper.selectById(TEST_PROJECT_ID)).thenReturn(project());
        when(redisStore.incrementWithTtl(eq("extension:task:heartbeat:" + TEST_TASK_ID), any(Duration.class)))
                .thenReturn(1L);
        ExtensionTaskStateService taskStateService = service(taskMapper, projectMapper, brandAccessService, redisStore);

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
            ProjectMapper projectMapper,
            BrandAccessService brandAccessService,
            ExtensionRedisStore redisStore
    ) {
        return new ExtensionTaskStateService(
                taskMapper,
                projectMapper,
                brandAccessService,
                redisStore,
                new ExtensionAuditSupport(auditService),
                auditService
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

    private Project project() {
        Project project = new Project();
        project.setId(TEST_PROJECT_ID);
        project.setBrandId(TEST_BRAND_ID);
        return project;
    }
}
