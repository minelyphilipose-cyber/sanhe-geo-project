package com.huanjing.geo.module.project.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.dto.ProjectCreateRequest;
import com.huanjing.geo.module.project.dto.ProjectStatusUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectStageUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectUpdateRequest;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final CompanyMapper companyMapper;
    private final CurrentUserService currentUserService;

    public Page<Project> page(long current, long size, String keyword, String status, String stage, Long partnerId) {
        SysUser user = currentUserService.requireCurrentUser();
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .orderByDesc(Project::getCreatedAt);

        if (StringUtils.hasText(keyword)) {
            wrapper.like(Project::getProjectName, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Project::getStatus, status);
        }
        if (StringUtils.hasText(stage)) {
            wrapper.eq(Project::getStage, stage);
        }

        Long scopePartnerId = currentUserService.requirePartnerScope(user);
        if (scopePartnerId != null) {
            wrapper.eq(Project::getPartnerId, scopePartnerId);
        } else if (partnerId != null) {
            wrapper.eq(Project::getPartnerId, partnerId);
        }

        return projectMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public Project detail(Long id) {
        SysUser user = currentUserService.requireCurrentUser();
        Project project = requireProject(id);

        Long scopePartnerId = currentUserService.requirePartnerScope(user);
        if (scopePartnerId != null && !scopePartnerId.equals(project.getPartnerId())) {
            throw new BizException(403, "No permission to access this project");
        }

        return project;
    }

    public Project create(ProjectCreateRequest req) {
        currentUserService.ensureInternalOperator();
        validateBrand(req.getBrandId());

        Project project = new Project();
        project.setProjectCode(buildProjectCode());
        project.setBrandId(req.getBrandId());
        project.setProjectName(req.getProjectName());
        project.setPackageType(req.getPackageType());
        project.setPackagePrice(req.getPackagePrice());
        project.setServiceMonths(req.getServiceMonths());
        project.setStatus("draft");
        project.setStage("pending_start");
        project.setOwnerType(req.getOwnerType());
        project.setPartnerId(req.getPartnerId());
        project.setDeliveryMode(StringUtils.hasText(req.getDeliveryMode()) ? req.getDeliveryMode() : "managed");
        project.setSignedAt(req.getSignedAt());
        project.setStartDate(req.getStartDate());
        project.setEndDate(req.getEndDate());
        project.setPrimaryGoal(req.getPrimaryGoal());
        project.setCreatedBy(currentUserService.requireCurrentUser().getId());
        project.setRemark(req.getRemark());
        projectMapper.insert(project);

        return project;
    }

    public Project update(Long id, ProjectUpdateRequest req) {
        currentUserService.ensureInternalOperator();

        Project project = requireProject(id);
        project.setProjectName(req.getProjectName());
        project.setPackageType(req.getPackageType());
        if (req.getPackagePrice() != null) {
            project.setPackagePrice(req.getPackagePrice());
        }
        if (req.getServiceMonths() != null) {
            project.setServiceMonths(req.getServiceMonths());
        }
        project.setOwnerType(req.getOwnerType());
        project.setPartnerId(req.getPartnerId());
        project.setDeliveryMode(StringUtils.hasText(req.getDeliveryMode()) ? req.getDeliveryMode() : project.getDeliveryMode());
        project.setSignedAt(req.getSignedAt());
        project.setStartDate(req.getStartDate());
        project.setEndDate(req.getEndDate());
        project.setPrimaryGoal(req.getPrimaryGoal());
        project.setRemark(req.getRemark());
        projectMapper.updateById(project);

        return project;
    }

    public void updateStage(Long id, ProjectStageUpdateRequest req) {
        currentUserService.ensureInternalOperator();
        Project project = requireProject(id);
        project.setStage(req.getStage());
        projectMapper.updateById(project);
    }

    public void updateStatus(Long id, ProjectStatusUpdateRequest req) {
        currentUserService.ensureInternalOperator();
        Project project = requireProject(id);
        project.setStatus(req.getStatus());
        projectMapper.updateById(project);
    }

    private Project requireProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        return project;
    }

    private void validateBrand(Long brandId) {
        Brand brand = brandMapper.selectById(brandId);
        if (brand == null) {
            throw new BizException(404, "Brand not found");
        }

        Company company = companyMapper.selectById(brand.getCompanyId());
        if (company == null) {
            throw new BizException(400, "Brand has no valid company");
        }
    }

    private String buildProjectCode() {
        return "PRJ" + System.currentTimeMillis() + RandomUtil.randomNumbers(4);
    }
}
