package com.huanjing.geo.module.presale.access;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PresaleAccessService {

    private static final String PERM_MANAGE = "presale.report.manage";
    private static final String PERM_EDIT = "presale.report.edit_content";

    private final PresaleReportMapper reportMapper;
    private final PresaleReportVersionMapper versionMapper;
    private final CurrentUserService currentUserService;

    public AccessScope getAccessScope() {
        currentUserService.requireCurrentUser();
        return canManage() ? AccessScope.ALL : AccessScope.OWN_ONLY;
    }

    public PresaleReport requireReportWithAccess(Long reportId) {
        PresaleReport report = reportMapper.selectById(reportId);
        if (report == null || report.getDeletedAt() != null) {
            throw new BizException(404, "Report not found: " + reportId);
        }
        SysUser current = currentUserService.requireCurrentUser();
        if (getAccessScope() == AccessScope.ALL || current.getId().equals(report.getCreatedBy())) {
            return report;
        }
        throw new BizException(403, "No access to this report");
    }

    public PresaleReportVersion requireVersionWithAccess(Long reportId, Integer versionNo) {
        requireReportWithAccess(reportId);
        if (versionNo == null || versionNo <= 0) {
            throw new BizException(400, "Invalid versionNo");
        }
        PresaleReportVersion version = versionMapper.selectOne(
                new LambdaQueryWrapper<PresaleReportVersion>()
                        .eq(PresaleReportVersion::getReportId, reportId)
                        .eq(PresaleReportVersion::getVersionNo, versionNo)
        );
        if (version == null) {
            throw new BizException(404, "Version not found: report=" + reportId + " versionNo=" + versionNo);
        }
        return version;
    }

    public boolean canManageCurrentUser() {
        return getAccessScope() == AccessScope.ALL;
    }

    public boolean canEditCurrentUser(PresaleReport report) {
        if (report == null || !currentUserService.hasPermission(PERM_EDIT)) {
            return false;
        }
        if (getAccessScope() == AccessScope.ALL) {
            return true;
        }
        SysUser current = currentUserService.requireCurrentUser();
        return current.getId().equals(report.getCreatedBy());
    }

    public Long currentUserId() {
        return currentUserService.requireCurrentUser().getId();
    }

    private boolean canManage() {
        return currentUserService.hasPermission(PERM_MANAGE);
    }
}
