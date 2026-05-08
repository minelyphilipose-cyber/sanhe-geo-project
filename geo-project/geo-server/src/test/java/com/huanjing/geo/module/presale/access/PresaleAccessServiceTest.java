package com.huanjing.geo.module.presale.access;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresaleAccessServiceTest {

    private static final Long OWNER_USER_ID = 1_000_001L;
    private static final Long OTHER_USER_ID = 1_000_101L;
    private static final Long REPORT_ID = 9_000_001L;

    @Mock
    private PresaleReportMapper reportMapper;
    @Mock
    private PresaleReportVersionMapper versionMapper;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private PresaleAccessService accessService;

    @Test
    void getAccessScope_managerReturnsALL() {
        when(currentUserService.requireCurrentUser()).thenReturn(user(OWNER_USER_ID));
        when(currentUserService.hasPermission("presale.report.manage")).thenReturn(true);

        AccessScope scope = accessService.getAccessScope();

        assertEquals(AccessScope.ALL, scope);
    }

    @Test
    void getAccessScope_normalUserReturnsOWN_ONLY() {
        when(currentUserService.requireCurrentUser()).thenReturn(user(OWNER_USER_ID));
        when(currentUserService.hasPermission("presale.report.manage")).thenReturn(false);

        AccessScope scope = accessService.getAccessScope();

        assertEquals(AccessScope.OWN_ONLY, scope);
    }

    @Test
    void requireReportWithAccess_ownerCanAccess() {
        SysUser owner = user(OWNER_USER_ID);
        PresaleReport report = report(REPORT_ID, OWNER_USER_ID);
        when(reportMapper.selectById(REPORT_ID)).thenReturn(report);
        when(currentUserService.requireCurrentUser()).thenReturn(owner);
        when(currentUserService.hasPermission("presale.report.manage")).thenReturn(false);

        PresaleReport actual = accessService.requireReportWithAccess(REPORT_ID);

        assertEquals(REPORT_ID, actual.getId());
    }

    @Test
    void requireReportWithAccess_nonOwnerForbidden() {
        SysUser nonOwner = user(OTHER_USER_ID);
        PresaleReport report = report(REPORT_ID, OWNER_USER_ID);
        when(reportMapper.selectById(REPORT_ID)).thenReturn(report);
        when(currentUserService.requireCurrentUser()).thenReturn(nonOwner);
        when(currentUserService.hasPermission("presale.report.manage")).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> accessService.requireReportWithAccess(REPORT_ID));

        assertEquals(403, ex.getCode());
    }

    @Test
    void requireReportWithAccess_managerBypassOwnerLimit() {
        SysUser manager = user(OTHER_USER_ID);
        PresaleReport report = report(REPORT_ID, OWNER_USER_ID);
        when(reportMapper.selectById(REPORT_ID)).thenReturn(report);
        when(currentUserService.requireCurrentUser()).thenReturn(manager);
        when(currentUserService.hasPermission("presale.report.manage")).thenReturn(true);

        PresaleReport actual = accessService.requireReportWithAccess(REPORT_ID);

        assertEquals(REPORT_ID, actual.getId());
    }

    @Test
    void canEditCurrentUser_ownerCanEditWithoutEditPermission() {
        SysUser owner = user(OWNER_USER_ID);
        PresaleReport report = report(REPORT_ID, OWNER_USER_ID);
        when(currentUserService.requireCurrentUser()).thenReturn(owner);
        when(currentUserService.hasPermission("presale.report.manage")).thenReturn(false);

        assertEquals(true, accessService.canEditCurrentUser(report));
    }

    @Test
    void canEditCurrentUser_nonOwnerCannotEditWithoutManagePermission() {
        SysUser nonOwner = user(OTHER_USER_ID);
        PresaleReport report = report(REPORT_ID, OWNER_USER_ID);
        when(currentUserService.requireCurrentUser()).thenReturn(nonOwner);
        when(currentUserService.hasPermission("presale.report.manage")).thenReturn(false);

        assertEquals(false, accessService.canEditCurrentUser(report));
    }

    @Test
    void requireReportWithAccess_reportNotFoundReturns404() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> accessService.requireReportWithAccess(REPORT_ID));

        assertEquals(404, ex.getCode());
    }

    @Test
    void requireReportWithAccess_deletedReportReturns404() {
        PresaleReport report = report(REPORT_ID, OWNER_USER_ID);
        report.setDeletedAt(LocalDateTime.now());
        when(reportMapper.selectById(REPORT_ID)).thenReturn(report);

        BizException ex = assertThrows(BizException.class,
                () -> accessService.requireReportWithAccess(REPORT_ID));

        assertEquals(404, ex.getCode());
    }

    @Test
    void requireVersionWithAccess_validVersionReturnsIt() {
        SysUser owner = user(OWNER_USER_ID);
        PresaleReport report = report(REPORT_ID, OWNER_USER_ID);
        PresaleReportVersion version = version(REPORT_ID, 2, 8_000_001L);
        when(reportMapper.selectById(REPORT_ID)).thenReturn(report);
        when(currentUserService.requireCurrentUser()).thenReturn(owner);
        when(currentUserService.hasPermission("presale.report.manage")).thenReturn(false);
        when(versionMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(version);

        PresaleReportVersion actual = accessService.requireVersionWithAccess(REPORT_ID, 2);

        assertEquals(8_000_001L, actual.getId());
        assertEquals(2, actual.getVersionNo());
    }

    @Test
    void requireVersionWithAccess_invalidVersionNoReturns400() {
        SysUser owner = user(OWNER_USER_ID);
        PresaleReport report = report(REPORT_ID, OWNER_USER_ID);
        when(reportMapper.selectById(REPORT_ID)).thenReturn(report);
        when(currentUserService.requireCurrentUser()).thenReturn(owner);
        when(currentUserService.hasPermission("presale.report.manage")).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> accessService.requireVersionWithAccess(REPORT_ID, 0));

        assertEquals(400, ex.getCode());
    }

    @Test
    void requireVersionWithAccess_versionNotFoundReturns404() {
        SysUser owner = user(OWNER_USER_ID);
        PresaleReport report = report(REPORT_ID, OWNER_USER_ID);
        when(reportMapper.selectById(REPORT_ID)).thenReturn(report);
        when(currentUserService.requireCurrentUser()).thenReturn(owner);
        when(currentUserService.hasPermission("presale.report.manage")).thenReturn(false);
        when(versionMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> accessService.requireVersionWithAccess(REPORT_ID, 3));

        assertEquals(404, ex.getCode());
    }

    private static PresaleReport report(Long reportId, Long createdBy) {
        PresaleReport report = new PresaleReport();
        report.setId(reportId);
        report.setCreatedBy(createdBy);
        return report;
    }

    private static PresaleReportVersion version(Long reportId, Integer versionNo, Long versionId) {
        PresaleReportVersion version = new PresaleReportVersion();
        version.setId(versionId);
        version.setReportId(reportId);
        version.setVersionNo(versionNo);
        return version;
    }

    private static SysUser user(Long userId) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setIsActive(true);
        return user;
    }
}
