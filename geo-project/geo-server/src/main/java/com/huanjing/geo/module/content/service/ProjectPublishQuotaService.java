package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.ProjectPublishQuota;
import com.huanjing.geo.module.content.mapper.ProjectPublishQuotaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectPublishQuotaService {

    private final ProjectPublishQuotaMapper projectPublishQuotaMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(Long projectId, String monthKey, Integer monthlyLimit) {
        ensureQuotaRow(projectId, monthKey, monthlyLimit);
        int affected = projectPublishQuotaMapper.tryReserve(projectId, monthKey, monthlyLimit);
        if (affected == 0) {
            throw new BizException(400, "Monthly publishing quota exhausted (project=" + projectId + ", month=" + monthKey + ")");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refund(Long projectId, String monthKey) {
        projectPublishQuotaMapper.refundReserved(projectId, monthKey);
    }

    private void ensureQuotaRow(Long projectId, String monthKey, Integer monthlyLimit) {
        ProjectPublishQuota existing = projectPublishQuotaMapper.selectOne(
                new LambdaQueryWrapper<ProjectPublishQuota>()
                        .eq(ProjectPublishQuota::getProjectId, projectId)
                        .eq(ProjectPublishQuota::getQuotaMonth, monthKey)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            return;
        }
        ProjectPublishQuota row = new ProjectPublishQuota();
        row.setProjectId(projectId);
        row.setQuotaMonth(monthKey);
        row.setUsedCount(0);
        row.setMonthlyLimit(monthlyLimit);
        projectPublishQuotaMapper.insert(row);
    }
}
