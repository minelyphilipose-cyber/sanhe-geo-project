package com.huanjing.geo.module.mobiledashboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.mobiledashboard.dto.ProjectCompetitorConfigRequest;
import com.huanjing.geo.module.mobiledashboard.dto.ProjectCompetitorConfigVO;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectCompetitorConfigService {
    private static final int MAX_COMPETITORS = 3;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final String QA_STATUS_PASSED = "passed";

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final InternalScopeService internalScopeService;
    private final ProjectMapper projectMapper;
    private final ObjectMapper objectMapper;

    public List<ProjectCompetitorConfigVO> list(Long projectId) {
        currentUserService.ensurePermission("project.read");
        requireAccessibleProject(projectId);
        return loadAll(projectId);
    }

    @Transactional
    public List<ProjectCompetitorConfigVO> replace(Long projectId, ProjectCompetitorConfigRequest request) {
        currentUserService.ensurePermission("project.competitor.manage");
        requireAccessibleProject(projectId);
        List<ProjectCompetitorConfigRequest.Item> items = request == null || request.getItems() == null
                ? List.of()
                : request.getItems();
        return replaceInternal(projectId, items);
    }

    public List<Map<String, Object>> profileCompetitors(Long projectId, List<Map<String, Object>> fallback) {
        List<ProjectCompetitorConfigVO> active = loadAll(projectId).stream()
                .filter(item -> !"disabled".equals(item.getStatus()))
                .limit(MAX_COMPETITORS)
                .toList();
        if (!active.isEmpty()) {
            return active.stream()
                    .map(this::profileCompetitor)
                    .toList();
        }
        return fallback == null ? List.of() : fallback.stream()
                .filter(item -> StringUtils.hasText(mapString(item, "competitorName", mapString(item, "competitor_name", ""))))
                .limit(MAX_COMPETITORS)
                .map(item -> profileCompetitor(
                        mapString(item, "competitorName", mapString(item, "competitor_name", "")),
                        mapString(item, "advantages", ""),
                        mapString(item, "disadvantages", "")
                ))
                .toList();
    }

    @Transactional
    public void syncFromGeoQuestionProfile(Long projectId, Object competitorsValue) {
        requireProject(projectId);
        if (!(competitorsValue instanceof List<?> list)) {
            replaceInternal(projectId, List.of());
            return;
        }
        Map<String, ProjectCompetitorConfigVO> existingByName = new LinkedHashMap<>();
        for (ProjectCompetitorConfigVO row : loadAll(projectId)) {
            existingByName.put(normalizeNameKey(row.getCompetitorName()), row);
        }
        List<ProjectCompetitorConfigRequest.Item> items = new ArrayList<>();
        int order = 1;
        Set<String> seen = new HashSet<>();
        for (Object raw : list) {
            if (!(raw instanceof Map<?, ?> map)) {
                continue;
            }
            String name = mapString(map, "competitorName", mapString(map, "competitor_name", ""));
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String key = normalizeNameKey(name);
            if (!seen.add(key)) {
                continue;
            }
            ProjectCompetitorConfigVO existing = existingByName.get(key);
            ProjectCompetitorConfigRequest.Item item = new ProjectCompetitorConfigRequest.Item();
            item.setId(existing == null ? null : existing.getId());
            item.setCompetitorName(name.trim());
            item.setAliases(existing == null ? List.of() : existing.getAliases());
            item.setAdvantages(mapString(map, "advantages", existing == null ? null : existing.getAdvantages()));
            item.setDisadvantages(mapString(map, "disadvantages", existing == null ? null : existing.getDisadvantages()));
            item.setDisplayOrder(order++);
            item.setActive(true);
            item.setQaStatus(QA_STATUS_PASSED);
            items.add(item);
            if (items.size() >= MAX_COMPETITORS) {
                break;
            }
        }
        replaceInternal(projectId, items);
    }

    private List<ProjectCompetitorConfigVO> replaceInternal(Long projectId, List<ProjectCompetitorConfigRequest.Item> items) {
        validateItems(items);
        Map<Long, ProjectCompetitorConfigVO> existing = new LinkedHashMap<>();
        for (ProjectCompetitorConfigVO row : loadAll(projectId)) {
            existing.put(row.getId(), row);
        }
        Set<Long> retained = new HashSet<>();
        for (ProjectCompetitorConfigRequest.Item item : items) {
            if (item.getId() != null && existing.containsKey(item.getId())) {
                updateExisting(projectId, item, existing.get(item.getId()));
                retained.add(item.getId());
            } else {
                insertNew(projectId, item);
            }
        }
        for (ProjectCompetitorConfigVO row : existing.values()) {
            if (!retained.contains(row.getId())) {
                jdbcTemplate.update("""
                        UPDATE project_competitor_config
                           SET status = 'disabled',
                               updated_at = CURRENT_TIMESTAMP
                         WHERE id = ? AND project_id = ?
                        """, row.getId(), projectId);
            }
        }
        return loadAll(projectId);
    }

    List<CompetitorEntity> activeCompetitors(Long projectId) {
        return jdbcTemplate.query("""
                SELECT id, competitor_name, aliases_json, display_order, config_version, qa_status
                  FROM project_competitor_config
                 WHERE project_id = ?
                   AND status = 'active'
                 ORDER BY display_order ASC, id ASC
                """, (rs, rowNum) -> new CompetitorEntity(
                rs.getLong("id"),
                rs.getString("competitor_name"),
                parseAliases(rs.getString("aliases_json")),
                rs.getInt("display_order"),
                rs.getInt("config_version"),
                rs.getString("qa_status")
        ), projectId);
    }

    int entityConfigVersion(Long projectId) {
        Integer version = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(config_version), 1)
                  FROM project_competitor_config
                 WHERE project_id = ?
                """, Integer.class, projectId);
        return version == null || version <= 0 ? 1 : version;
    }

    private Project requireAccessibleProject(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new BizException(400, "projectId is required");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        SysUser user = currentUserService.requireCurrentUser();
        internalScopeService.ensureProjectAccess(user, project, "project");
        return project;
    }

    private void requireProject(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new BizException(400, "projectId is required");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
    }

    private List<ProjectCompetitorConfigVO> loadAll(Long projectId) {
        return jdbcTemplate.query("""
                SELECT id, project_id, competitor_name, aliases_json, advantages, disadvantages, display_order, status,
                       qa_status, qa_checked_at, config_version, updated_at
                  FROM project_competitor_config
                 WHERE project_id = ?
                 ORDER BY display_order ASC, id ASC
                """, (rs, rowNum) -> mapRow(rs), projectId);
    }

    private ProjectCompetitorConfigVO mapRow(ResultSet rs) throws SQLException {
        ProjectCompetitorConfigVO vo = new ProjectCompetitorConfigVO();
        vo.setId(rs.getLong("id"));
        vo.setProjectId(rs.getLong("project_id"));
        vo.setCompetitorName(rs.getString("competitor_name"));
        vo.setAliases(parseAliases(rs.getString("aliases_json")));
        vo.setAdvantages(rs.getString("advantages"));
        vo.setDisadvantages(rs.getString("disadvantages"));
        vo.setDisplayOrder(rs.getInt("display_order"));
        vo.setStatus(rs.getString("status"));
        vo.setQaStatus(rs.getString("qa_status"));
        Timestamp qaCheckedAt = rs.getTimestamp("qa_checked_at");
        vo.setQaCheckedAt(qaCheckedAt == null ? null : qaCheckedAt.toLocalDateTime());
        vo.setConfigVersion(rs.getInt("config_version"));
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        vo.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
        return vo;
    }

    private void validateItems(List<ProjectCompetitorConfigRequest.Item> items) {
        if (items.size() > MAX_COMPETITORS) {
            throw new BizException(400, "竞品最多配置3个");
        }
        Set<Integer> orders = new HashSet<>();
        for (ProjectCompetitorConfigRequest.Item item : items) {
            if (!StringUtils.hasText(item.getCompetitorName())) {
                throw new BizException(400, "competitorName is required");
            }
            Integer displayOrder = item.getDisplayOrder();
            if (displayOrder == null || displayOrder <= 0) {
                throw new BizException(400, "displayOrder must be positive");
            }
            if (displayOrder > MAX_COMPETITORS) {
                throw new BizException(400, "displayOrder must be within 1-3");
            }
            if (!orders.add(displayOrder)) {
                throw new BizException(400, "displayOrder duplicated: " + displayOrder);
            }
            item.setQaStatus(QA_STATUS_PASSED);
        }
    }

    private void insertNew(Long projectId, ProjectCompetitorConfigRequest.Item item) {
        jdbcTemplate.update("""
                INSERT INTO project_competitor_config (
                  project_id, competitor_name, aliases_json, advantages, disadvantages, display_order, status, qa_status, qa_checked_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CASE WHEN ? = 'passed' THEN CURRENT_TIMESTAMP ELSE NULL END)
                """,
                projectId,
                item.getCompetitorName().trim(),
                aliasesJson(item.getAliases()),
                textOrNull(item.getAdvantages()),
                textOrNull(item.getDisadvantages()),
                item.getDisplayOrder(),
                Boolean.FALSE.equals(item.getActive()) ? "disabled" : "active",
                normalizeQaStatus(item.getQaStatus()),
                normalizeQaStatus(item.getQaStatus()));
    }

    private void updateExisting(Long projectId, ProjectCompetitorConfigRequest.Item item, ProjectCompetitorConfigVO existing) {
        String aliasesJson = aliasesJson(item.getAliases());
        String status = Boolean.FALSE.equals(item.getActive()) ? "disabled" : "active";
        boolean matchingChanged = !existing.getCompetitorName().equals(item.getCompetitorName().trim())
                || !existing.getAliases().equals(normalizeAliases(item.getAliases()));
        String qaStatus = normalizeQaStatus(item.getQaStatus());
        jdbcTemplate.update("""
                UPDATE project_competitor_config
                   SET competitor_name = ?,
                       aliases_json = ?,
                       advantages = ?,
                       disadvantages = ?,
                       display_order = ?,
                       status = ?,
                       qa_status = ?,
                       qa_checked_at = CASE
                           WHEN ? THEN NULL
                           WHEN ? = 'passed' AND qa_status <> 'passed' THEN CURRENT_TIMESTAMP
                           ELSE qa_checked_at
                       END,
                       config_version = config_version + ?
                 WHERE id = ? AND project_id = ?
                """,
                item.getCompetitorName().trim(),
                aliasesJson,
                textOrNull(item.getAdvantages()),
                textOrNull(item.getDisadvantages()),
                item.getDisplayOrder(),
                status,
                qaStatus,
                matchingChanged,
                qaStatus,
                matchingChanged ? 1 : 0,
                item.getId(),
                projectId);
    }

    private String normalizeQaStatus(String value) {
        return QA_STATUS_PASSED;
    }

    private List<String> parseAliases(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return normalizeAliases(objectMapper.readValue(raw, STRING_LIST));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String aliasesJson(List<String> aliases) {
        try {
            return objectMapper.writeValueAsString(normalizeAliases(aliases));
        } catch (Exception ex) {
            throw new BizException(400, "Invalid aliases");
        }
    }

    private List<String> normalizeAliases(List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String alias : aliases) {
            if (!StringUtils.hasText(alias)) {
                continue;
            }
            String trimmed = alias.trim();
            String key = trimmed.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private Map<String, Object> profileCompetitor(String name) {
        return profileCompetitor(name, null, null);
    }

    private Map<String, Object> profileCompetitor(ProjectCompetitorConfigVO item) {
        return profileCompetitor(item.getCompetitorName(), item.getAdvantages(), item.getDisadvantages());
    }

    private Map<String, Object> profileCompetitor(String name, String advantages, String disadvantages) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("competitorName", name);
        item.put("advantages", StringUtils.hasText(advantages) ? advantages : "请补充竞品优势");
        item.put("disadvantages", StringUtils.hasText(disadvantages) ? disadvantages : "请补充竞品劣势");
        return item;
    }

    private String mapString(Map<?, ?> item, String key, String fallback) {
        Object value = item.get(key);
        return value != null && StringUtils.hasText(String.valueOf(value)) ? String.valueOf(value).trim() : fallback;
    }

    private String normalizeNameKey(String name) {
        return StringUtils.hasText(name) ? name.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String textOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    record CompetitorEntity(Long id,
                            String name,
                            List<String> aliases,
                            int displayOrder,
                            int configVersion,
                            String qaStatus) {
    }
}
