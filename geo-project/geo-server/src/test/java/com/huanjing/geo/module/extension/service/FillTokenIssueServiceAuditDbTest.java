package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.test.AbstractAuditDbIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class FillTokenIssueServiceAuditDbTest extends AbstractAuditDbIntegrationTest {

    @Autowired
    private FillTokenService fillTokenService;
    @MockBean
    private ExtensionRedisStore redisStore;
    @MockBean
    private ExtensionVersionService versionService;

    @Test
    void fillTokenIssueWritesAuditLogRow() {
        insertSemiAutoTaskFixture("token_issued", false);

        fillTokenService.issue(TEST_ACCOUNT_ID, TEST_BRAND_ID, TEST_OPERATOR_ID, TEST_TASK_ID, "toutiao", "0.1.0");

        Map<String, Object> audit = requireSingleAudit("FILL_TOKEN_ISSUE");
        assertEquals(TEST_OPERATOR_ID, ((Number) audit.get("actor_id")).longValue());
        assertEquals(TEST_BRAND_ID, ((Number) audit.get("brand_id")).longValue());
        assertEquals(TEST_ACCOUNT_ID, ((Number) audit.get("account_id")).longValue());
        assertEquals(TEST_TASK_ID, ((Number) audit.get("task_id")).longValue());
        assertEquals("SUCCESS", audit.get("result"));
        assertEquals("SYNC", audit.get("mode"));
        assertTrue(Boolean.TRUE.equals(audit.get("sensitive")));
        assertAuditDetailNotContains(audit, "ft.");
        verify(versionService).requireSupported("toutiao", "0.1.0");
        verify(redisStore).set(any(), eq("1"), any(Duration.class));
    }
}
