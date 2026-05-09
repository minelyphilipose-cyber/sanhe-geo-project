package com.huanjing.geo.module.project.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.service.BrandStatementDispatchService;
import com.huanjing.geo.module.project.dto.ProjectStatusUpdateRequest;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.flyway.validate-on-migrate=false",
        "geo.extension.fill-token.hmac-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class ProjectServiceChannelAllocationConcurrencyIT {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private CurrentUserService currentUserService;
    @MockBean
    private BrandStatementDispatchService brandStatementDispatchService;

    private String marker;
    private Long companyId;
    private Long brandId;
    private Long packagePlanId;
    private Long operatorId;

    @AfterEach
    void cleanup() {
        if (marker == null) {
            return;
        }
        jdbcTemplate.update("""
                DELETE FROM activity_log
                WHERE user_id = ? OR target_id IN (
                    SELECT id FROM project WHERE project_code LIKE ?
                )
                """, operatorId, marker + "%");
        jdbcTemplate.update("""
                DELETE FROM project_channel_allocation_audit
                WHERE project_id IN (SELECT id FROM project WHERE project_code LIKE ?)
                """, marker + "%");
        jdbcTemplate.update("""
                DELETE FROM project_channel_allocation
                WHERE project_id IN (SELECT id FROM project WHERE project_code LIKE ?)
                """, marker + "%");
        jdbcTemplate.update("DELETE FROM project WHERE project_code LIKE ?", marker + "%");
        jdbcTemplate.update("DELETE FROM company_package_binding WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM package_channel_quota_config WHERE package_plan_id = ?", packagePlanId);
        jdbcTemplate.update("DELETE FROM brand WHERE id = ?", brandId);
        jdbcTemplate.update("DELETE FROM company WHERE id = ?", companyId);
        jdbcTemplate.update("DELETE FROM package_plan WHERE id = ?", packagePlanId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", operatorId);
    }

    @Test
    void concurrentProjectActivationCannotOversellChannelAllocation() throws Exception {
        TestData data = prepareData();
        SysUser operator = new SysUser();
        operator.setId(operatorId);
        operator.setRole("manager");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(currentUserService.isPartnerUser(any())).thenReturn(false);
        doNothing().when(currentUserService).ensurePermission(any());
        doNothing().when(currentUserService).ensurePartnerResourceAccess(any(), any(), any());

        var quota = projectService.channelAllocationQuota(companyId, null);
        assertThat(quota.getItems()).anySatisfy(item -> {
            assertThat(item.getChannelCode()).isEqualTo("official_site");
            assertThat(item.getQuotaLimit()).isEqualTo(1);
            assertThat(item.getActiveAllocatedCount()).isEqualTo(0);
            assertThat(item.getRemainingCount()).isEqualTo(1);
            assertThat(item.getInputMax()).isEqualTo(1);
        });

        ProjectStatusUpdateRequest request = new ProjectStatusUpdateRequest();
        request.setStatus("active");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = executor.submit(() -> activateAfterStart(data.projectAId(), request, ready, start));
            Future<Throwable> second = executor.submit(() -> activateAfterStart(data.projectBId(), request, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Throwable> results = Arrays.asList(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);
            assertThat(results.stream()
                    .filter(BizException.class::isInstance)
                    .map(BizException.class::cast)
                    .map(BizException::getMessage)
                    .filter("PROJECT_CHANNEL_ALLOCATION_EXCEEDED"::equals)
                    .count()).isEqualTo(1);

            Integer activeCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM project WHERE id IN (?, ?) AND status = 'active'",
                    Integer.class,
                    data.projectAId(),
                    data.projectBId()
            );
            assertThat(activeCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable activateAfterStart(Long projectId,
                                         ProjectStatusUpdateRequest request,
                                         CountDownLatch ready,
                                         CountDownLatch start) {
        ready.countDown();
        try {
            start.await(5, TimeUnit.SECONDS);
            projectService.updateStatus(projectId, request);
            return null;
        } catch (Throwable ex) {
            return ex;
        }
    }

    private TestData prepareData() {
        marker = "IT" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        operatorId = insertAndReturnId("""
                INSERT INTO sys_user (username, password_hash, display_name, role)
                VALUES (?, '{noop}pwd', 'IT User', 'manager')
                """, marker + "_user");
        companyId = insertAndReturnId("""
                INSERT INTO company (company_name, owner_type, source_type, status)
                VALUES (?, 'direct', 'internal', 'signed')
                """, marker + "_company");
        brandId = insertAndReturnId("""
                INSERT INTO brand (company_id, brand_name, brand_slug, status)
                VALUES (?, ?, ?, 'active')
                """, companyId, marker + "_brand", marker + "_brand");
        packagePlanId = insertAndReturnId("""
                INSERT INTO package_plan (package_type, package_name, standard_price, service_months, enabled)
                VALUES (?, 'IT Plan', 1000, 1, 1)
                """, marker + "_plan");
        jdbcTemplate.update("""
                INSERT INTO package_channel_quota_config (package_plan_id, channel_code, period_type, quota_limit, enabled)
                VALUES (?, 'official_site', 'month', 1, 1)
                """, packagePlanId);
        jdbcTemplate.update("""
                INSERT INTO company_package_binding
                    (company_id, package_plan_id, package_type, package_name, standard_price, service_months,
                     keyword_group_limit, channel_quota_snapshot, status, active_flag)
                VALUES (?, ?, 'it_plan', 'IT Plan', 1000, 1, 100,
                        '[{"channelCode":"official_site","periodType":"month","quotaLimit":1,"enabled":true}]',
                        'active', 1)
                """, companyId, packagePlanId);
        Long projectAId = insertProject(marker + "A");
        Long projectBId = insertProject(marker + "B");
        insertAllocation(projectAId);
        insertAllocation(projectBId);
        return new TestData(projectAId, projectBId);
    }

    private Long insertProject(String code) {
        return insertAndReturnId("""
                INSERT INTO project
                    (project_code, company_id, company_name, brand_id, brand_name, project_name,
                     status, stage, owner_type,
                     source_type, content_generation_enabled, delivery_mode, signed_at, start_date,
                     end_date, primary_goal, created_by)
                VALUES (?, ?, ?, ?, ?, ?, 'paused', 'pending_start',
                        'direct', 'internal', 0, 'managed', NOW(), ?, ?, 'IT', ?)
                """,
                code,
                companyId,
                marker + "_company",
                brandId,
                marker + "_brand",
                code + "_project",
                LocalDate.now(),
                LocalDate.now().plusMonths(1),
                operatorId);
    }

    private void insertAllocation(Long projectId) {
        jdbcTemplate.update("""
                INSERT INTO project_channel_allocation
                    (project_id, company_id, channel_code, period_type_snapshot, package_quota_limit_snapshot, allocated_count, revision)
                VALUES (?, ?, 'official_site', 'month', 1, 1, 1)
                """, projectId, companyId);
    }

    private Long insertAndReturnId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private record TestData(Long projectAId, Long projectBId) {
    }
}
