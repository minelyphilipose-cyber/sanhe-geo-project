package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.ProjectSelfMediaAutoScheduleRequest;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleBatch;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleConfig;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaScheduleBatchMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaScheduleConfigMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectSelfMediaScheduleServiceTest {
    private ProjectMapper projectMapper;
    private ProjectSelfMediaScheduleConfigMapper configMapper;
    private ProjectSelfMediaScheduleBatchMapper batchMapper;
    private SelfMediaPublishAutoScheduleService autoScheduleService;
    private BrandAccessService brandAccessService;
    private ProjectSelfMediaScheduleService service;

    @BeforeEach
    void setUp() {
        projectMapper = mock(ProjectMapper.class);
        configMapper = mock(ProjectSelfMediaScheduleConfigMapper.class);
        batchMapper = mock(ProjectSelfMediaScheduleBatchMapper.class);
        autoScheduleService = mock(SelfMediaPublishAutoScheduleService.class);
        brandAccessService = mock(BrandAccessService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser user = new SysUser();
        user.setId(99L);
        when(currentUserService.requireCurrentUser()).thenReturn(user);
        when(projectMapper.selectById(7L)).thenReturn(project());

        service = new ProjectSelfMediaScheduleService(
                projectMapper,
                configMapper,
                batchMapper,
                mock(ArticleDraftMapper.class),
                mock(SelfMediaAccountMapper.class),
                autoScheduleService,
                brandAccessService,
                currentUserService,
                new ObjectMapper()
        );
    }

    @Test
    void createForProjectRejectsWhenSwitchDisabled() {
        ProjectSelfMediaScheduleConfig config = config(false);
        when(configMapper.selectByProjectId(7L)).thenReturn(config);

        assertThrows(BizException.class, () -> service.createForProject(7L, request(), "manual"));
        verify(brandAccessService).requireBrandAccess(8L, 99L, BrandAccessAction.OPERATE);
    }

    @Test
    void createForProjectRejectsWhenMonthAlreadyCreated() {
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        ProjectSelfMediaScheduleBatch batch = new ProjectSelfMediaScheduleBatch();
        batch.setStatus("created");
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);

        assertThrows(BizException.class, () -> service.createForProject(7L, request(), "manual"));
    }

    private ProjectSelfMediaAutoScheduleRequest request() {
        ProjectSelfMediaAutoScheduleRequest request = new ProjectSelfMediaAutoScheduleRequest();
        request.setTargetMonth("2026-06");
        request.setArticleIds(List.of(10L));
        request.setSelfMediaAccountIds(List.of(20L));
        return request;
    }

    private ProjectSelfMediaScheduleConfig config(boolean enabled) {
        ProjectSelfMediaScheduleConfig config = new ProjectSelfMediaScheduleConfig();
        config.setProjectId(7L);
        config.setBrandId(8L);
        config.setCompanyId(6L);
        config.setAutoScheduleEnabled(enabled);
        config.setDefaultScheduleStrategy("platform_schedule");
        config.setIncludeAdjustedWorkdays(false);
        return config;
    }

    private Project project() {
        Project project = new Project();
        project.setId(7L);
        project.setBrandId(8L);
        project.setCompanyId(6L);
        project.setCreatedBy(99L);
        return project;
    }
}
