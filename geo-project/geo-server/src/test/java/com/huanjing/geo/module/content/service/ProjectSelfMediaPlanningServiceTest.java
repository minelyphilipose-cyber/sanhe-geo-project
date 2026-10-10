package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.security.LoginUser;
import com.huanjing.geo.common.util.SecurityUtils;
import com.huanjing.geo.module.content.dto.ProjectSelfMediaAutoScheduleRequest;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaPlanningTask;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaPlanningTaskMapper;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaScheduleConfigVO;
import com.huanjing.geo.module.content.vo.SelfMediaPublishAutoScheduleResponse;
import com.huanjing.geo.module.partner.service.PartnerFeatureAccessGuard;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.apache.ibatis.mapping.Environment;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectSelfMediaPlanningServiceTest {
    private ProjectSelfMediaPlanningService service;
    private ProjectSelfMediaPlanningTaskMapper mapper;
    private ProjectSelfMediaScheduleService scheduling;
    private CurrentUserService users;
    private PartnerFeatureAccessGuard guard;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private JdbcDataSource dataSource;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setup() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:self_media_planning;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=2000");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        try (var stream = getClass().getResourceAsStream("/db/migration/V375__project_self_media_async_planning.sql")) {
            assertNotNull(stream);
            String ddl = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace(" STORED", "").replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
            jdbc.execute(ddl);
        }
        jdbc.execute("CREATE TABLE test_quota_reservation (id BIGINT PRIMARY KEY)");
        var configuration = new MybatisConfiguration(new Environment("test", new SpringManagedTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ProjectSelfMediaPlanningTaskMapper.class);
        mapper = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration))
                .getMapper(ProjectSelfMediaPlanningTaskMapper.class);
        var manager = new DataSourceTransactionManager(dataSource);
        transaction = new TransactionTemplate(manager);
        scheduling = mock(ProjectSelfMediaScheduleService.class);
        users = mock(CurrentUserService.class);
        guard = mock(PartnerFeatureAccessGuard.class);
        var config = new ProjectSelfMediaScheduleConfigVO();
        config.setAutoScheduleEnabled(true);
        when(scheduling.getConfig(anyLong())).thenReturn(config);
        SysUser operator = new SysUser();
        operator.setId(99L);
        operator.setUsername("operator");
        operator.setRole("admin");
        operator.setIsActive(true);
        operator.setTokenVersion(3);
        when(users.requireCurrentUser()).thenReturn(operator);
        when(users.requireById(99L)).thenReturn(operator);
        service = new ProjectSelfMediaPlanningService(mapper, scheduling, users, guard, json, manager);
    }

    @Test
    void acceptsImmediatelyAndReplaysWithoutExecutingOrCreatingDuplicatePlans() {
        var task = service.submit(7L, request(), "request-1");
        assertEquals("pending", task.getStatus());
        assertTrue(task.getSupplementExistingBatch());
        assertEquals(task.getId(), service.submit(7L, request(), "request-1").getId());
        assertEquals(task.getId(), service.submit(7L, request(), "another-click").getId());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM project_self_media_planning_task", Integer.class));
        verify(scheduling, never()).createForProject(any(), any(), anyString(), anyString());
    }

    @Test
    void rejectsIdempotencyKeyUsedWithDifferentParameters() {
        service.submit(7L, request(), "request-1");
        var changed = request();
        changed.setSelfMediaAccountIds(List.of(21L));
        assertThrows(BizException.class, () -> service.submit(7L, changed, "request-1"));
    }

    @Test
    void completionCommitsQuotaAndResultTogetherAndRestoresWorkerSecurityContext() throws Exception {
        var accepted = service.submit(7L, request(), "request-1");
        var previous = SecurityContextHolder.createEmptyContext();
        previous.setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(123L, "other", "admin", 1), null, List.of()));
        SecurityContextHolder.setContext(previous);
        try {
            when(scheduling.createForProject(eq(7L), any(), eq("manual"), eq("request-1"))).thenAnswer(invocation -> {
                assertEquals(99L, SecurityUtils.getCurrentUserId());
                assertEquals(3, SecurityUtils.getCurrentTokenVersion());
                assertEquals(request(), invocation.getArgument(1));
                jdbc.update("INSERT INTO test_quota_reservation VALUES (1)");
                try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT status FROM project_self_media_planning_task")) {
                    assertTrue(rows.next());
                    assertEquals("pending", rows.getString(1));
                }
                var result = new SelfMediaPublishAutoScheduleResponse();
                result.setPlannedCount(30);
                result.setCreated(true);
                return result;
            });
            assertTrue(service.processNext());
            assertSame(previous, SecurityContextHolder.getContext());
            var result = service.latest(7L, "2026-10");
            assertEquals("success", result.getStatus());
            assertEquals(30, result.getResult().getPlannedCount());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM test_quota_reservation", Integer.class));
            assertFalse(service.processNext());
            assertEquals(accepted.getId(), service.submit(7L, request(), "request-1").getId());
            verify(scheduling, times(1)).createForProject(any(), any(), anyString(), anyString());
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void failureRollsBackQuotaAndAllowsAnotherRequestWithoutTouchingPriorPlans() {
        service.submit(7L, request(), "request-1");
        when(scheduling.createForProject(any(), any(), anyString(), anyString())).thenAnswer(invocation -> {
            jdbc.update("INSERT INTO test_quota_reservation VALUES (1)");
            throw new BizException("剩余额度不足");
        });
        assertTrue(service.processNext());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM test_quota_reservation", Integer.class));
        var failed = service.latest(7L, "2026-10");
        assertEquals("failed", failed.getStatus());
        assertEquals("剩余额度不足", failed.getFailureMessage());
        assertNotEquals(failed.getId(), service.submit(7L, request(), "retry-2").getId());
    }

    @Test
    void transactionRollbackOnInterruptedWorkerLeavesRequestPendingForRecovery() {
        service.submit(7L, request(), "request-1");
        transaction.executeWithoutResult(status -> {
            var task = mapper.selectPendingForUpdate();
            task.setStatus("success");
            mapper.updateById(task);
            jdbc.update("INSERT INTO test_quota_reservation VALUES (1)");
            status.setRollbackOnly();
        });
        assertEquals("pending", service.latest(7L, "2026-10").getStatus());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM test_quota_reservation", Integer.class));
        assertTrue(service.processNext());
    }

    @Test
    void anotherWorkerDoesNotExecuteLockedRequest() {
        service.submit(7L, request(), "request-1");
        transaction.executeWithoutResult(status -> {
            var locked = mapper.selectPendingForUpdate();
            assertEquals(7L, locked.getProjectId());
            assertDoesNotThrow(() -> assertFalse(CompletableFuture.supplyAsync(service::processNext).get(3, TimeUnit.SECONDS)));
            assertEquals("pending", mapper.selectById(locked.getId()).getStatus());
        });
        verify(scheduling, never()).createForProject(eq(7L), any(), anyString(), anyString());
        assertTrue(service.processNext());
        assertEquals("success", service.latest(7L, "2026-10").getStatus());
    }

    @Test
    void databasePreventsConcurrentPendingRequestsButKeepsHistory() {
        service.submit(7L, request(), "request-1");
        ProjectSelfMediaPlanningTask duplicate = new ProjectSelfMediaPlanningTask();
        duplicate.setProjectId(7L);
        duplicate.setTargetMonth("2026-10");
        duplicate.setIdempotencyKey("request-2");
        duplicate.setRequestPayload("{}");
        duplicate.setCreatedBy(99L);
        duplicate.setStatus("pending");
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> mapper.insert(duplicate));
        assertTrue(service.processNext());
        assertEquals("pending", service.submit(7L, request(), "request-2").getStatus());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM project_self_media_planning_task", Integer.class));
    }

    @Test
    void checksAccessForSubmissionAndStatusAndRejectsDisabledOperatorsInBackground() {
        doThrow(new BizException("无品牌权限")).when(scheduling).getConfig(7L);
        assertThrows(BizException.class, () -> service.submit(7L, request(), "request-1"));
        assertThrows(BizException.class, () -> service.latest(7L, "2026-10"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM project_self_media_planning_task", Integer.class));
        reset(scheduling);
        var config = new ProjectSelfMediaScheduleConfigVO();
        config.setAutoScheduleEnabled(true);
        when(scheduling.getConfig(anyLong())).thenReturn(config);
        service.submit(7L, request(), "request-1");
        users.requireById(99L).setIsActive(false);
        assertTrue(service.processNext());
        assertEquals("failed", service.latest(7L, "2026-10").getStatus());
        verify(scheduling, never()).createForProject(any(), any(), anyString(), anyString());
    }

    private ProjectSelfMediaAutoScheduleRequest request() {
        var request = new ProjectSelfMediaAutoScheduleRequest();
        request.setTargetMonth("2026-10");
        request.setSelfMediaAccountIds(List.of(20L));
        request.setSupplementExistingBatch(true);
        request.setScheduleStrategy("platform_specific");
        return request;
    }
}
