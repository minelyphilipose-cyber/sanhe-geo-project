package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.mobiledashboard.entity.MobileDashboardShare;
import com.huanjing.geo.module.mobiledashboard.mapper.MobileDashboardAccessLogMapper;
import com.huanjing.geo.module.mobiledashboard.mapper.MobileDashboardShareMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardShareVO;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MobileDashboardShareServiceTest {

    @Test
    void createShareStoresOnlyHashAndPrefixButReturnsShortCodeUrl() {
        MobileDashboardShareMapper shareMapper = mock(MobileDashboardShareMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        Project project = new Project();
        project.setId(11L);
        project.setPartnerId(1L);
        when(projectMapper.selectById(11L)).thenReturn(project);
        SysUser user = new SysUser();
        user.setId(99L);
        when(currentUserService.requireCurrentUser()).thenReturn(user);
        when(shareMapper.selectList(any())).thenReturn(java.util.List.of());
        MobileDashboardShareService service = new MobileDashboardShareService(
                shareMapper,
                mock(MobileDashboardAccessLogMapper.class),
                projectMapper,
                currentUserService,
                mock(ActivityLogService.class),
                mock(InternalScopeService.class),
                mock(MobileDashboardSessionTokenService.class)
        );
        ReflectionTestUtils.setField(service, "tokenSalt", "unit-test-salt");
        ReflectionTestUtils.setField(service, "webBaseUrl", "https://example.test");
        ReflectionTestUtils.setField(service, "defaultShareTtlDays", 90L);

        MobileDashboardShareVO vo = service.createShare(11L, null);

        ArgumentCaptor<MobileDashboardShare> captor = ArgumentCaptor.forClass(MobileDashboardShare.class);
        verify(shareMapper).insert(captor.capture());
        MobileDashboardShare stored = captor.getValue();
        assertThat(vo.getShareCode()).isNotBlank();
        assertThat(vo.getShareUrl()).isEqualTo("https://example.test/m/" + vo.getShareCode());
        assertThat(stored.getShareCode()).isEqualTo(vo.getShareCode());
        assertThat(stored.getTokenHash()).isNotBlank();
        assertThat(stored.getTokenHash()).doesNotContain(stored.getTokenPrefix());
        assertThat(stored.getTokenPrefix()).startsWith("mdb_");
    }

    @Test
    void requireValidSessionRejectsDisabledShare() {
        MobileDashboardShareMapper shareMapper = mock(MobileDashboardShareMapper.class);
        MobileDashboardSessionTokenService tokenService = mock(MobileDashboardSessionTokenService.class);
        when(tokenService.parse("session")).thenReturn(new MobileDashboardSessionTokenService.SessionClaims(5L, 11L));
        when(shareMapper.selectById(5L)).thenReturn(share("disabled", 11L, LocalDateTime.now().plusDays(1)));

        BizException ex = assertThrows(BizException.class, () -> service(shareMapper, tokenService).requireValidSession("Bearer session"));

        assertThat(ex.getMessage()).contains("no longer valid");
    }

    @Test
    void resolveShareCardTitleUsesCompanyNameForActiveShare() {
        MobileDashboardShareMapper shareMapper = mock(MobileDashboardShareMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        MobileDashboardShare share = share("active", 11L, LocalDateTime.now().plusDays(1));
        Project project = project();
        project.setCompanyName("华为鸿蒙智家");
        project.setProjectName("鸿蒙智家项目");
        when(shareMapper.selectOne(any())).thenReturn(share);
        when(projectMapper.selectById(11L)).thenReturn(project);

        MobileDashboardShareService service = new MobileDashboardShareService(
                shareMapper,
                mock(MobileDashboardAccessLogMapper.class),
                projectMapper,
                mock(CurrentUserService.class),
                mock(ActivityLogService.class),
                mock(InternalScopeService.class),
                mock(MobileDashboardSessionTokenService.class)
        );

        assertThat(service.resolveShareCardTitle("mahekskz")).isEqualTo("华为鸿蒙智家");
    }

    @Test
    void resolveShareCardTitlePrefersBrandName() {
        MobileDashboardShareMapper shareMapper = mock(MobileDashboardShareMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        MobileDashboardShare share = share("active", 11L, LocalDateTime.now().plusDays(1));
        Project project = project();
        project.setBrandName("华为鸿蒙智家");
        project.setCompanyName("阜阳某某智能家居有限公司");
        project.setProjectName("鸿蒙智家项目");
        when(shareMapper.selectOne(any())).thenReturn(share);
        when(projectMapper.selectById(11L)).thenReturn(project);

        MobileDashboardShareService service = new MobileDashboardShareService(
                shareMapper,
                mock(MobileDashboardAccessLogMapper.class),
                projectMapper,
                mock(CurrentUserService.class),
                mock(ActivityLogService.class),
                mock(InternalScopeService.class),
                mock(MobileDashboardSessionTokenService.class)
        );

        assertThat(service.resolveShareCardTitle("MAHEKSKZ")).isEqualTo("华为鸿蒙智家");
    }

    @Test
    void resolveShareCardTitleDoesNotExposeCustomerForDisabledShare() {
        MobileDashboardShareMapper shareMapper = mock(MobileDashboardShareMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        when(shareMapper.selectOne(any())).thenReturn(
                share("disabled", 11L, LocalDateTime.now().plusDays(1))
        );

        MobileDashboardShareService service = new MobileDashboardShareService(
                shareMapper,
                mock(MobileDashboardAccessLogMapper.class),
                projectMapper,
                mock(CurrentUserService.class),
                mock(ActivityLogService.class),
                mock(InternalScopeService.class),
                mock(MobileDashboardSessionTokenService.class)
        );

        assertThat(service.resolveShareCardTitle("MAHEKSKZ")).isEqualTo("移动数据看板");
        verify(projectMapper, never()).selectById(any());
    }

    @Test
    void requireValidSessionRejectsExpiredShare() {
        MobileDashboardShareMapper shareMapper = mock(MobileDashboardShareMapper.class);
        MobileDashboardSessionTokenService tokenService = mock(MobileDashboardSessionTokenService.class);
        when(tokenService.parse("session")).thenReturn(new MobileDashboardSessionTokenService.SessionClaims(5L, 11L));
        when(shareMapper.selectById(5L)).thenReturn(share("active", 11L, LocalDateTime.now().minusSeconds(1)));

        BizException ex = assertThrows(BizException.class, () -> service(shareMapper, tokenService).requireValidSession("session"));

        assertThat(ex.getMessage()).contains("no longer valid");
    }

    @Test
    void requireValidSessionRejectsProjectMismatch() {
        MobileDashboardShareMapper shareMapper = mock(MobileDashboardShareMapper.class);
        MobileDashboardSessionTokenService tokenService = mock(MobileDashboardSessionTokenService.class);
        when(tokenService.parse("session")).thenReturn(new MobileDashboardSessionTokenService.SessionClaims(5L, 11L));
        when(shareMapper.selectById(5L)).thenReturn(share("active", 99L, LocalDateTime.now().plusDays(1)));

        BizException ex = assertThrows(BizException.class, () -> service(shareMapper, tokenService).requireValidSession("session"));

        assertThat(ex.getMessage()).contains("project mismatch");
    }

    @Test
    void requireValidSessionAcceptsActiveUnexpiredMatchingShare() {
        MobileDashboardShareMapper shareMapper = mock(MobileDashboardShareMapper.class);
        MobileDashboardSessionTokenService tokenService = mock(MobileDashboardSessionTokenService.class);
        when(tokenService.parse("session")).thenReturn(new MobileDashboardSessionTokenService.SessionClaims(5L, 11L));
        when(shareMapper.selectById(5L)).thenReturn(share("active", 11L, LocalDateTime.now().plusDays(1)));

        MobileDashboardSessionTokenService.SessionClaims claims = service(shareMapper, tokenService).requireValidSession("Bearer session");

        assertThat(claims.shareId()).isEqualTo(5L);
        assertThat(claims.projectId()).isEqualTo(11L);
    }

    @Test
    void deleteShareRejectsActiveShare() {
        MobileDashboardShareMapper shareMapper = mock(MobileDashboardShareMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        MobileDashboardShare share = share("active", 11L, LocalDateTime.now().plusDays(1));
        share.setTokenPrefix("mdb_active");
        when(shareMapper.selectById(5L)).thenReturn(share);
        when(projectMapper.selectById(11L)).thenReturn(project());
        when(currentUserService.requireCurrentUser()).thenReturn(user());

        MobileDashboardShareService service = new MobileDashboardShareService(
                shareMapper,
                mock(MobileDashboardAccessLogMapper.class),
                projectMapper,
                currentUserService,
                mock(ActivityLogService.class),
                mock(InternalScopeService.class),
                mock(MobileDashboardSessionTokenService.class)
        );

        BizException ex = assertThrows(BizException.class, () -> service.deleteShare(5L));

        assertThat(ex.getMessage()).contains("disabled before deletion");
        verify(shareMapper, never()).deleteById(5L);
    }

    @Test
    void deleteShareDeletesDisabledShare() {
        MobileDashboardShareMapper shareMapper = mock(MobileDashboardShareMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        MobileDashboardShare share = share("disabled", 11L, LocalDateTime.now().plusDays(1));
        share.setTokenPrefix("mdb_disabled");
        when(shareMapper.selectById(5L)).thenReturn(share);
        when(projectMapper.selectById(11L)).thenReturn(project());
        when(currentUserService.requireCurrentUser()).thenReturn(user());

        MobileDashboardShareService service = new MobileDashboardShareService(
                shareMapper,
                mock(MobileDashboardAccessLogMapper.class),
                projectMapper,
                currentUserService,
                activityLogService,
                mock(InternalScopeService.class),
                mock(MobileDashboardSessionTokenService.class)
        );

        service.deleteShare(5L);

        verify(shareMapper).deleteById(5L);
        verify(activityLogService).logAction(any(), any(), any(), any(), any(), any(), any());
    }

    private MobileDashboardShareService service(MobileDashboardShareMapper shareMapper,
                                                MobileDashboardSessionTokenService tokenService) {
        return new MobileDashboardShareService(
                shareMapper,
                mock(MobileDashboardAccessLogMapper.class),
                mock(ProjectMapper.class),
                mock(CurrentUserService.class),
                mock(ActivityLogService.class),
                mock(InternalScopeService.class),
                tokenService
        );
    }

    private MobileDashboardShare share(String status, Long projectId, LocalDateTime expiresAt) {
        MobileDashboardShare share = new MobileDashboardShare();
        share.setId(5L);
        share.setProjectId(projectId);
        share.setStatus(status);
        share.setExpiresAt(expiresAt);
        return share;
    }

    private Project project() {
        Project project = new Project();
        project.setId(11L);
        project.setPartnerId(1L);
        return project;
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(99L);
        return user;
    }
}
