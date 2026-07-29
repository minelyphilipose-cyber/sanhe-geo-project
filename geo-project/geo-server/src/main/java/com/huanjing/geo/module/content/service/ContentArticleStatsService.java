package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.dto.ArticleExecutionStatsResponse;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ContentArticleStatsService {

    private static final String VISIBLE_PUBLISH_STATUS_SQL =
            "'published','published_confirmed','distributed'";

    private final JdbcTemplate jdbcTemplate;
    private final ProjectMapper projectMapper;
    private final CurrentUserService currentUserService;

    public ArticleExecutionStatsResponse loadGlobalStats() {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        if (!currentUserService.isPartnerUser(operator)) {
            return new ArticleExecutionStatsResponse(countPublished(null));
        }
        List<Long> readableProjectIds = projectMapper.selectList(
                        new LambdaQueryWrapper<Project>()
                                .isNull(Project::getDeletedAt)
                                .eq(Project::getPartnerId, operator.getPartnerId())
                                .select(Project::getId))
                .stream()
                .map(Project::getId)
                .filter(Objects::nonNull)
                .toList();
        if (readableProjectIds.isEmpty()) {
            return new ArticleExecutionStatsResponse(0);
        }
        return new ArticleExecutionStatsResponse(countPublished(readableProjectIds));
    }

    private long countPublished(List<Long> projectIds) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT article_id)
                  FROM article_publish_record
                 WHERE publish_status IN (%s)
                """.formatted(VISIBLE_PUBLISH_STATUS_SQL));
        Object[] args = new Object[0];
        if (projectIds != null) {
            sql.append("   AND project_id IN (")
                    .append(String.join(",", java.util.Collections.nCopies(projectIds.size(), "?")))
                    .append(")");
            args = projectIds.toArray();
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args);
        return count == null ? 0 : count;
    }
}
