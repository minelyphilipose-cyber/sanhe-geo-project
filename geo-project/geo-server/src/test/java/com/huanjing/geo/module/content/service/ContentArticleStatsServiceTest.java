package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.module.content.dto.ArticleExecutionStatsResponse;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentArticleStatsServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ProjectMapper projectMapper;
    private CurrentUserService currentUserService;
    private ContentArticleStatsService service;
    private SysUser operator;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Project.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        projectMapper = mock(ProjectMapper.class);
        currentUserService = mock(CurrentUserService.class);
        operator = new SysUser();
        operator.setId(7L);
        operator.setPartnerId(77L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        service = new ContentArticleStatsService(jdbcTemplate, projectMapper, currentUserService);
    }

    @Test
    void directOperatorCountsDistinctPublishedEvidenceGlobally() {
        when(currentUserService.isPartnerUser(operator)).thenReturn(false);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(19L);

        ArticleExecutionStatsResponse result = service.loadGlobalStats();

        assertEquals(19L, result.publishedCount());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertTrue(sql.getValue().contains("COUNT(DISTINCT article_id)"));
        assertTrue(sql.getValue().contains("'published','published_confirmed','distributed'"));
        verify(projectMapper, never()).selectList(any());
        verify(currentUserService).ensurePermission("project.read");
    }

    @Test
    void partnerCountIsRestrictedToReadableProjects() {
        when(currentUserService.isPartnerUser(operator)).thenReturn(true);
        Project first = new Project();
        first.setId(101L);
        Project second = new Project();
        second.setId(102L);
        when(projectMapper.selectList(any())).thenReturn(List.of(first, second));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(5L);

        ArticleExecutionStatsResponse result = service.loadGlobalStats();

        assertEquals(5L, result.publishedCount());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), args.capture());
        assertTrue(sql.getValue().contains("project_id IN (?,?)"));
        assertEquals(List.of(101L, 102L), List.of(args.getValue()));
    }

    @Test
    void partnerWithoutProjectsReturnsZeroWithoutScanningPublishRecords() {
        when(currentUserService.isPartnerUser(operator)).thenReturn(true);
        when(projectMapper.selectList(any())).thenReturn(List.of());

        ArticleExecutionStatsResponse result = service.loadGlobalStats();

        assertEquals(0L, result.publishedCount());
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class));
    }
}
