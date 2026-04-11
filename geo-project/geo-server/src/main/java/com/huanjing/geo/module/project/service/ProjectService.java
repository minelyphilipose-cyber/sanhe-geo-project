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
import com.huanjing.geo.module.project.dto.ProjectStageUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectStatusUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectUpdateRequest;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private static final Set<String> OWNER_TYPES = Set.of("direct", "partner", "joint");
    private static final Set<String> PACKAGE_TYPES = Set.of("trial_6980", "standard_12800", "growth_26800");

    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final CompanyMapper companyMapper;
    private final CurrentUserService currentUserService;
    private final ActivityLogService activityLogService;

    public Page<Project> page(long current, long size, String keyword, String status, String stage, Long partnerId) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
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

        Long scopePartnerId = currentUserService.resolvePartnerQueryScope(user, partnerId);
        if (scopePartnerId != null) {
            wrapper.eq(Project::getPartnerId, scopePartnerId);
        }

        return projectMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public Project detail(Long id) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        Project project = requireProject(id);
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        return project;
    }

    public Project create(ProjectCreateRequest req) {
        currentUserService.ensurePermission("project.write");
        SysUser operator = currentUserService.requireCurrentUser();
        Company company = validateBrand(req.getBrandId());
        validateOwnerBinding(req.getOwnerType(), req.getPartnerId());
        validateProjectBase(req.getPackageType(), req.getPackagePrice(), req.getServiceMonths());
        validateProjectCompanyPartnerConsistency(req.getOwnerType(), req.getPartnerId(), company.getPartnerId());

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
        project.setCreatedBy(operator.getId());
        project.setRemark(req.getRemark());
        projectMapper.insert(project);
        activityLogService.logAction(
                operator.getId(),
                "project.create",
                "project",
                project.getId(),
                null,
                snapshotProject(project),
                Map.of("brandId", project.getBrandId())
        );

        return project;
    }

    public Project update(Long id, ProjectUpdateRequest req) {
        currentUserService.ensurePermission("project.write");
        SysUser operator = currentUserService.requireCurrentUser();
        validateOwnerBinding(req.getOwnerType(), req.getPartnerId());

        Project project = requireProject(id);
        Map<String, Object> before = snapshotProject(project);
        Company company = validateBrand(project.getBrandId());
        validateProjectCompanyPartnerConsistency(req.getOwnerType(), req.getPartnerId(), company.getPartnerId());

        Long targetPrice = req.getPackagePrice() == null ? project.getPackagePrice() : req.getPackagePrice();
        Integer targetMonths = req.getServiceMonths() == null ? project.getServiceMonths() : req.getServiceMonths();
        validateProjectBase(req.getPackageType(), targetPrice, targetMonths);

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
        activityLogService.logAction(
                operator.getId(),
                "project.update",
                "project",
                project.getId(),
                before,
                snapshotProject(project),
                Map.of("brandId", project.getBrandId())
        );

        return project;
    }

    public void updateStage(Long id, ProjectStageUpdateRequest req) {
        currentUserService.ensurePermission("project.write");
        SysUser operator = currentUserService.requireCurrentUser();
        validateStage(req.getStage());
        Project project = requireProject(id);
        ensureStageBoundary(project.getStatus(), project.getStage(), req.getStage());
        if (req.getStage().equals(project.getStage())) {
            return;
        }
        String fromStage = project.getStage();
        project.setStage(req.getStage());
        projectMapper.updateById(project);
        activityLogService.logAction(
                operator.getId(),
                "project.stage.update",
                "project",
                project.getId(),
                Map.of("stage", fromStage),
                Map.of("stage", project.getStage()),
                Map.of("status", project.getStatus(), "from", fromStage, "to", project.getStage())
        );
    }

    public void updateStatus(Long id, ProjectStatusUpdateRequest req) {
        currentUserService.ensurePermission("project.write");
        SysUser operator = currentUserService.requireCurrentUser();
        validateStatus(req.getStatus());
        Project project = requireProject(id);
        ensureStatusTransition(project.getStatus(), req.getStatus());
        if (req.getStatus().equals(project.getStatus())) {
            return;
        }
        String fromStatus = project.getStatus();
        project.setStatus(req.getStatus());
        projectMapper.updateById(project);
        activityLogService.logAction(
                operator.getId(),
                "project.status.update",
                "project",
                project.getId(),
                Map.of("status", fromStatus),
                Map.of("status", project.getStatus()),
                Map.of("from", fromStatus, "to", project.getStatus())
        );
    }

    public void delete(Long id) {
        currentUserService.ensurePermission("project.write");
        SysUser operator = currentUserService.requireCurrentUser();
        Project project = requireProject(id);
        projectMapper.deleteById(id);
        activityLogService.logAction(
                operator.getId(),
                "project.delete",
                "project",
                id,
                snapshotProject(project),
                null,
                null
        );
    }

    private Project requireProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        return project;
    }

    private Company validateBrand(Long brandId) {
        Brand brand = brandMapper.selectById(brandId);
        if (brand == null) {
            throw new BizException(404, "Brand not found");
        }

        Company company = companyMapper.selectById(brand.getCompanyId());
        if (company == null) {
            throw new BizException(400, "Brand has no valid company");
        }
        return company;
    }

    private void validateOwnerBinding(String ownerType, Long partnerId) {
        if (!OWNER_TYPES.contains(ownerType)) {
            throw new BizException(400, "Invalid owner_type");
        }
        if ("direct".equals(ownerType) && partnerId != null) {
            throw new BizException(400, "direct project must not bind partner_id");
        }
        if (("partner".equals(ownerType) || "joint".equals(ownerType)) && partnerId == null) {
            throw new BizException(400, "partner/joint project must bind partner_id");
        }
    }

    private void validateProjectBase(String packageType, Long packagePrice, Integer serviceMonths) {
        if (!PACKAGE_TYPES.contains(packageType)) {
            throw new BizException(400, "Invalid package_type");
        }
        if (packagePrice == null || packagePrice <= 0) {
            throw new BizException(400, "package_price must be positive");
        }
        if (serviceMonths == null || serviceMonths <= 0) {
            throw new BizException(400, "service_months must be positive");
        }
    }

    private void validateStatus(String status) {
        if (!ProjectFlowPolicy.STATUS_SET.contains(status)) {
            throw new BizException(400, "Invalid project status");
        }
    }

    private void validateStage(String stage) {
        if (!ProjectFlowPolicy.STAGE_SET.contains(stage)) {
            throw new BizException(400, "Invalid project stage");
        }
    }

    private void ensureStatusTransition(String currentStatus, String targetStatus) {
        if (currentStatus.equals(targetStatus)) {
            return;
        }
        Set<String> allowedTargets = ProjectFlowPolicy.STATUS_TRANSITION.getOrDefault(currentStatus, Set.of());
        if (!allowedTargets.contains(targetStatus)) {
            throw new BizException(400, "Illegal status transition: " + currentStatus + " -> " + targetStatus);
        }
    }

    private void ensureStageBoundary(String status, String currentStage, String targetStage) {
        if (currentStage.equals(targetStage)) {
            return;
        }
        if ("archived".equals(status)) {
            throw new BizException(400, "Archived project cannot change stage");
        }
        if ("draft".equals(status) && !ProjectFlowPolicy.DRAFT_ALLOWED_STAGES.contains(targetStage)) {
            throw new BizException(400, "Draft project only allows pending_start or collecting_materials stage");
        }
    }

    private void validateProjectCompanyPartnerConsistency(String ownerType, Long projectPartnerId, Long companyPartnerId) {
        if ("direct".equals(ownerType)) {
            return;
        }
        if (companyPartnerId == null) {
            throw new BizException(400, "Selected brand belongs to direct company, cannot create partner/joint project");
        }
        if (!companyPartnerId.equals(projectPartnerId)) {
            throw new BizException(400, "Project partner_id must match company partner_id");
        }
    }

    private String buildProjectCode() {
        return "PRJ" + System.currentTimeMillis() + RandomUtil.randomNumbers(4);
    }

    private Map<String, Object> snapshotProject(Project project) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", project.getId());
        snapshot.put("projectCode", project.getProjectCode());
        snapshot.put("projectName", project.getProjectName());
        snapshot.put("brandId", project.getBrandId());
        snapshot.put("ownerType", project.getOwnerType());
        snapshot.put("partnerId", project.getPartnerId());
        snapshot.put("status", project.getStatus());
        snapshot.put("stage", project.getStage());
        snapshot.put("packageType", project.getPackageType());
        snapshot.put("packagePrice", project.getPackagePrice());
        snapshot.put("serviceMonths", project.getServiceMonths());
        return snapshot;
    }
}
