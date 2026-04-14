package com.huanjing.geo.module.dispatch.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskStatus;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BrandStatementDispatchService {

    private static final List<String> UNFINISHED_STATUS = List.of(
            DispatchTaskStatus.PENDING.value(),
            DispatchTaskStatus.RUNNING.value(),
            DispatchTaskStatus.RETRY_PENDING.value()
    );

    private final BrandMapper brandMapper;
    private final ProjectMapper projectMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchTaskService dispatchTaskService;

    @Transactional
    public DispatchTask maybeEnqueueOnProjectActivated(Project project) {
        if (project == null || project.getBrandId() == null) {
            return null;
        }
        Brand brand = brandMapper.selectById(project.getBrandId());
        if (brand == null) {
            return null;
        }
        if ("locked".equalsIgnoreCase(brand.getStatementStatus())
                || "draft".equalsIgnoreCase(brand.getStatementStatus())
                || "pending".equalsIgnoreCase(brand.getStatementStatus())) {
            return null;
        }
        DispatchTask existing = findUnfinishedTaskByBrand(brand.getId());
        if (existing != null) {
            if (!"pending".equalsIgnoreCase(brand.getStatementStatus())) {
                brand.setStatementStatus("pending");
                brandMapper.updateById(brand);
            }
            return existing;
        }
        brand.setStatementStatus("pending");
        brandMapper.updateById(brand);
        return createBrandStatementTask(project.getId(), brand.getId(), "event", "project_activation");
    }

    @Transactional
    public DispatchTask enqueueRegeneration(Long brandId, Long projectId, String remark) {
        Brand brand = brandMapper.selectById(brandId);
        if (brand == null) {
            throw new BizException(404, "Brand not found");
        }
        DispatchTask existing = findUnfinishedTaskByBrand(brandId);
        if (existing != null) {
            if (!"pending".equalsIgnoreCase(brand.getStatementStatus())) {
                brand.setStatementStatus("pending");
                brandMapper.updateById(brand);
            }
            return existing;
        }
        Project project = resolveProjectForBrand(brandId, projectId);
        brand.setStatementStatus("pending");
        brandMapper.updateById(brand);
        return createBrandStatementTask(project.getId(), brandId, "manual", remark);
    }

    private DispatchTask createBrandStatementTask(Long projectId, Long brandId, String triggerSource, String remark) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", "brand-statement-generation");
        payload.put("brandId", brandId);
        payload.put("triggerSource", StringUtils.hasText(triggerSource) ? triggerSource : "event");
        if (StringUtils.hasText(remark)) {
            payload.put("remark", remark.trim());
        }
        return dispatchTaskService.createTaskAndEnqueue(
                projectId,
                DispatchTaskType.BRAND_STATEMENT_GENERATION,
                LocalDate.now(),
                LocalDate.now(),
                LocalDateTime.now(),
                payload
        );
    }

    private DispatchTask findUnfinishedTaskByBrand(Long brandId) {
        List<DispatchTask> tasks = dispatchTaskMapper.selectList(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(DispatchTask::getTaskType, DispatchTaskType.BRAND_STATEMENT_GENERATION.name())
                        .in(DispatchTask::getStatus, UNFINISHED_STATUS)
                        .orderByDesc(DispatchTask::getId)
        );
        String marker = "\"brandId\":" + brandId;
        for (DispatchTask task : tasks) {
            if (StringUtils.hasText(task.getPayloadJson()) && task.getPayloadJson().contains(marker)) {
                return task;
            }
            // backward compatibility: payload maybe not compact JSON
            try {
                Object rawBrandId = JSONUtil.parseObj(task.getPayloadJson()).get("brandId");
                if (rawBrandId != null && String.valueOf(brandId).equals(String.valueOf(rawBrandId))) {
                    return task;
                }
            } catch (Exception ignore) {
                // ignore invalid payload
            }
        }
        return null;
    }

    private Project resolveProjectForBrand(Long brandId, Long projectId) {
        if (projectId != null) {
            Project target = projectMapper.selectById(projectId);
            if (target == null) {
                throw new BizException(404, "Project not found");
            }
            if (!brandId.equals(target.getBrandId())) {
                throw new BizException(400, "Project does not belong to brand");
            }
            return target;
        }
        Project active = projectMapper.selectOne(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getBrandId, brandId)
                        .eq(Project::getStatus, "active")
                        .orderByDesc(Project::getActivatedAt, Project::getId)
                        .last("LIMIT 1")
        );
        if (active != null) {
            return active;
        }
        Project fallback = projectMapper.selectOne(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getBrandId, brandId)
                        .orderByDesc(Project::getId)
                        .last("LIMIT 1")
        );
        if (fallback == null) {
            throw new BizException(400, "Brand has no project to bind dispatch task");
        }
        return fallback;
    }
}
