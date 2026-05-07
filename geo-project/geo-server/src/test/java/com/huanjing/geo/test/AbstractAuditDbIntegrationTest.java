package com.huanjing.geo.test;

import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.credential.crypto.MasterKeyProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = {
        "geo.extension.fill-token.hmac-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractAuditDbIntegrationTest {

    protected static final long TEST_ID_BASE = 990_006_000L;
    protected static final long TEST_OPERATOR_ID = TEST_ID_BASE + 1;
    protected static final long TEST_BRAND_ID = TEST_ID_BASE + 2;
    protected static final long TEST_ACCOUNT_ID = TEST_ID_BASE + 3;
    protected static final long TEST_PROJECT_ID = TEST_ID_BASE + 4;
    protected static final long TEST_ARTICLE_ID = TEST_ID_BASE + 5;
    protected static final long TEST_TASK_ID = TEST_ID_BASE + 6;

    @Autowired
    protected AuditService auditService;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @MockBean
    protected MasterKeyProvider masterKeyProvider;

    @BeforeEach
    @AfterEach
    void cleanAuditDbFixtures() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        jdbcTemplate.update("""
                DELETE FROM audit_log
                WHERE actor_id BETWEEN ? AND ?
                   OR brand_id BETWEEN ? AND ?
                   OR account_id BETWEEN ? AND ?
                   OR task_id BETWEEN ? AND ?
                """, TEST_ID_BASE, TEST_ID_BASE + 999, TEST_ID_BASE, TEST_ID_BASE + 999,
                TEST_ID_BASE, TEST_ID_BASE + 999, TEST_ID_BASE, TEST_ID_BASE + 999);
        jdbcTemplate.update("DELETE FROM distribution_tasks WHERE id BETWEEN ? AND ?", TEST_ID_BASE, TEST_ID_BASE + 999);
        jdbcTemplate.update("DELETE FROM article_draft WHERE id BETWEEN ? AND ?", TEST_ID_BASE, TEST_ID_BASE + 999);
        jdbcTemplate.update("DELETE FROM project WHERE id BETWEEN ? AND ?", TEST_ID_BASE, TEST_ID_BASE + 999);
        jdbcTemplate.update("DELETE FROM self_media_account WHERE id BETWEEN ? AND ?", TEST_ID_BASE, TEST_ID_BASE + 999);
        jdbcTemplate.update("DELETE FROM brand WHERE id BETWEEN ? AND ?", TEST_ID_BASE, TEST_ID_BASE + 999);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN ? AND ?", TEST_ID_BASE, TEST_ID_BASE + 999);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
    }

    protected List<Map<String, Object>> queryAuditLog(String eventType) {
        return jdbcTemplate.queryForList("""
                SELECT event_type, actor_id, brand_id, account_id, task_id, target_type, target_id,
                       `sensitive`, result, mode, detail_json, error_code, error_message
                FROM audit_log
                WHERE event_type = ?
                  AND (
                       actor_id BETWEEN ? AND ?
                    OR brand_id BETWEEN ? AND ?
                    OR account_id BETWEEN ? AND ?
                    OR task_id BETWEEN ? AND ?
                  )
                ORDER BY id
                """, eventType,
                TEST_ID_BASE, TEST_ID_BASE + 999, TEST_ID_BASE, TEST_ID_BASE + 999,
                TEST_ID_BASE, TEST_ID_BASE + 999, TEST_ID_BASE, TEST_ID_BASE + 999);
    }

    protected Map<String, Object> requireSingleAudit(String eventType) {
        List<Map<String, Object>> rows = queryAuditLog(eventType);
        assertEquals(1, rows.size(), "expected one audit row for " + eventType);
        return rows.get(0);
    }

    protected void assertAuditDetailNotContains(Map<String, Object> audit, String forbidden) {
        Object detail = audit.get("detail_json");
        if (detail == null) {
            return;
        }
        assertFalse(String.valueOf(detail).contains(forbidden));
    }

    protected void insertAuditPrincipalFixture() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, password_hash, display_name, role, is_active, token_version)
                VALUES (?, 'b6b-audit-operator', 'noop', 'B6b Audit Operator', 'operator', 1, 0)
                """, TEST_OPERATOR_ID);
        jdbcTemplate.update("""
                INSERT INTO brand (id, company_id, industry, brand_name, brand_slug, status)
                VALUES (?, ?, 'general', 'B6b Audit Brand', 'b6b-audit-brand', 'active')
                """, TEST_BRAND_ID, TEST_BRAND_ID);
        jdbcTemplate.update("""
                INSERT INTO self_media_account (id, brand_id, platform, platform_account_id, account_name, status)
                VALUES (?, ?, 'toutiao', 'b6b-audit-account', 'B6b Audit Account', 'active')
                """, TEST_ACCOUNT_ID, TEST_BRAND_ID);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
    }

    protected void insertSemiAutoTaskFixture(String status, boolean withLastHeartbeat) {
        insertAuditPrincipalFixture();
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        jdbcTemplate.update("""
                INSERT INTO project (id, project_code, brand_id, project_name, owner_type, source_type, created_by)
                VALUES (?, 'B6B-AUDIT', ?, 'B6b Audit Project', 'direct', 'internal', ?)
                """, TEST_PROJECT_ID, TEST_BRAND_ID, TEST_OPERATOR_ID);
        jdbcTemplate.update("""
                INSERT INTO article_draft (id, batch_id, project_id, article_type, title, status)
                VALUES (?, ?, ?, 'manual', 'B6b Audit Article', 'approved')
                """, TEST_ARTICLE_ID, TEST_ARTICLE_ID, TEST_PROJECT_ID);
        jdbcTemplate.update("""
                INSERT INTO distribution_tasks (
                    id, article_id, project_id, target_kind, self_media_account_id, attempt_no,
                    status, integration_method, dispatch_mode, operator_id, last_heartbeat_at
                )
                VALUES (?, ?, ?, 'mp_account', ?, 1, ?, 'toutiao', 'SEMI_AUTO', ?,
                        CASE WHEN ? THEN NOW() ELSE NULL END)
                """, TEST_TASK_ID, TEST_ARTICLE_ID, TEST_PROJECT_ID, TEST_ACCOUNT_ID, status,
                TEST_OPERATOR_ID, withLastHeartbeat);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
    }
}
