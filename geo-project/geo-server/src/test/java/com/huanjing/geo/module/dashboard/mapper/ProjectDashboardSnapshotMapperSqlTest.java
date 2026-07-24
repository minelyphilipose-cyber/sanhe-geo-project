package com.huanjing.geo.module.dashboard.mapper;

import com.huanjing.geo.module.dashboard.entity.ProjectDashboardSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectDashboardSnapshotMapperSqlTest {

    @Test
    void batchInsertRendersOneValueTuplePerSnapshot() throws Exception {
        Method method = ProjectDashboardSnapshotMapper.class.getMethod("insertBatch", List.class);
        Insert insert = method.getAnnotation(Insert.class);
        String script = String.join("\n", insert.value());
        SqlSource sqlSource = new XMLLanguageDriver().createSqlSource(
                new Configuration(),
                script,
                Map.class
        );

        ProjectDashboardSnapshot first = snapshot("summary");
        ProjectDashboardSnapshot second = snapshot("content_progress");
        BoundSql boundSql = sqlSource.getBoundSql(Map.of("snapshots", List.of(first, second)));
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();

        assertTrue(sql.startsWith("INSERT INTO project_dashboard_snapshot"));
        assertTrue(sql.contains("VALUES (?, ?, ?, ?, ?, ?) , (?, ?, ?, ?, ?, ?)"));
        assertEquals(12, boundSql.getParameterMappings().size());
    }

    private ProjectDashboardSnapshot snapshot(String type) {
        ProjectDashboardSnapshot snapshot = new ProjectDashboardSnapshot();
        snapshot.setProjectId(8L);
        snapshot.setSnapshotType(type);
        snapshot.setSnapshotValue("{}");
        snapshot.setRefreshedAt(LocalDateTime.of(2026, 7, 18, 15, 5));
        return snapshot;
    }
}
