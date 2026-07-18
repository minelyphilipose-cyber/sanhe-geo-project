package com.huanjing.geo.module.content.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfMediaPublishScheduleMapperXmlTest {

    @Test
    void accountWideScopeIsPresentInClaimCapacityOwnershipAndRenewStatements() throws Exception {
        Configuration configuration = mapperConfiguration();
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 16, 0);
        Map<String, Object> common = new HashMap<>();
        common.put("queueKind", "schedule_execution");
        common.put("statuses", List.of("pending"));
        common.put("runningStatuses", List.of("filling"));
        common.put("now", now);
        common.put("lockedUntil", now.plusMinutes(3));
        common.put("limit", 10);
        common.put("localAgentSessionId", 5L);
        common.put("operatorId", 13L);
        common.put("accessibleBrandIds", List.of(15L));
        common.put("platform", "toutiao");
        common.put("platforms", Set.of("toutiao"));
        common.put("browserEnvironmentId", 21L);
        common.put("brandId", 15L);
        common.put("scheduleId", 1066L);
        common.put("runtimeWorkerId", "13");

        assertAccountWideScope(configuration, "selectDueQueueCandidatesForLocalAgent", common);
        assertAccountWideScope(configuration, "isBrowserEnvironmentOwnedByLocalAgent", common);
        assertAccountWideScope(configuration, "countLockedByLocalAgentSessionAndStatuses", common);
        assertAccountWideScope(configuration, "renewLocalAgentLock", common);

        String capacitySql = sql(configuration, "sumOnlineLocalAgentCapacityByBrand", common);
        assertTrue(capacitySql.contains("SELECT DISTINCT helper_runtime.id"));
        assertTrue(capacitySql.contains("helper_session.brand_id IS NULL"));
    }

    private void assertAccountWideScope(Configuration configuration,
                                        String statement,
                                        Map<String, Object> parameters) {
        String sql = sql(configuration, statement, parameters);
        assertTrue(sql.contains("helper_session.brand_id IS NULL"), statement);
        assertTrue(sql.contains("agent_binding.bound_by = ?"), statement);
        assertTrue(sql.contains("helper_runtime.operator_id = ?"), statement);
    }

    private String sql(Configuration configuration,
                       String statement,
                       Map<String, Object> parameters) {
        String namespace = SelfMediaPublishScheduleMapper.class.getName();
        MappedStatement mappedStatement = configuration.getMappedStatement(namespace + "." + statement);
        BoundSql boundSql = mappedStatement.getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }

    private Configuration mapperConfiguration() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/content/SelfMediaPublishScheduleMapper.xml";
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing mapper resource " + resource);
            }
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }
}
