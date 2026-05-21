package com.huanjing.geo.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.mapper.CompanyPackageBindingMapper;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskStatus;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.service.ProjectDistributionChannelAllocationService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerPackageExpiryService {

    private static final List<String> GLOBAL_RECIPIENT_ROLES = List.of("super_admin", "manager", "delivery_manager");
    private static final List<String> ACTIVE_TASK_STATUSES = List.of(
            DispatchTaskStatus.PENDING.value(),
            DispatchTaskStatus.RUNNING.value(),
            DispatchTaskStatus.RETRY_PENDING.value()
    );

    private final CompanyPackageBindingMapper packageBindingMapper;
    private final CompanyMapper companyMapper;
    private final ProjectMapper projectMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final SysUserMapper sysUserMapper;
    private final SystemAlertService systemAlertService;
    private final ProjectDistributionChannelAllocationService channelAllocationService;

    @Transactional
    public void scanAndHandle(LocalDate today) {
        List<CompanyPackageBinding> bindings = packageBindingMapper.selectList(
                new LambdaQueryWrapper<CompanyPackageBinding>()
                        .eq(CompanyPackageBinding::getStatus, CompanyPackageBinding.STATUS_ACTIVE)
                        .eq(CompanyPackageBinding::getActiveFlag, 1)
                        .isNotNull(CompanyPackageBinding::getBoundAt)
                        .gt(CompanyPackageBinding::getServiceMonths, 0)
        );
        for (CompanyPackageBinding binding : bindings) {
            LocalDate expireDate = binding.getBoundAt().plusMonths(binding.getServiceMonths()).toLocalDate();
            long daysUntil = ChronoUnit.DAYS.between(today, expireDate);
            ReminderStage reminderStage = resolveReminderStage(today, expireDate);
            if (reminderStage != null) {
                notifyExpiry(binding, expireDate, daysUntil, reminderStage);
            }
            if (daysUntil <= 0) {
                expireCompany(binding, expireDate, today);
            }
        }
    }

    private ReminderStage resolveReminderStage(LocalDate today, LocalDate expireDate) {
        if (today.equals(expireDate.minusMonths(1))) {
            return ReminderStage.ONE_MONTH;
        }
        if (today.equals(expireDate.minusWeeks(1))) {
            return ReminderStage.ONE_WEEK;
        }
        if (today.equals(expireDate.minusDays(1))) {
            return ReminderStage.ONE_DAY;
        }
        if (today.equals(expireDate)) {
            return ReminderStage.TODAY;
        }
        return null;
    }

    private void notifyExpiry(CompanyPackageBinding binding, LocalDate expireDate, long daysUntil, ReminderStage reminderStage) {
        Company company = companyMapper.selectById(binding.getCompanyId());
        if (company == null || company.getDeletedAt() != null) {
            return;
        }
        Map<Long, SysUser> recipients = resolveRecipients(company);
        if (recipients.isEmpty()) {
            return;
        }
        String message = buildReminderMessage(company, expireDate, reminderStage);
        String severity = reminderStage == ReminderStage.TODAY ? "critical" : "warn";
        for (SysUser recipient : recipients.values()) {
            Map<String, Object> context = new HashMap<>();
            context.put("companyId", company.getId());
            context.put("companyName", safeText(company.getCompanyName()));
            context.put("bindingId", binding.getId());
            context.put("packagePlanId", binding.getPackagePlanId());
            context.put("packageName", safeText(binding.getPackageName()));
            context.put("expireDate", expireDate.toString());
            context.put("daysUntil", daysUntil);
            context.put("reminderStage", reminderStage.code);
            context.put("recipientUserId", recipient.getId());
            context.put("recipientRole", safeText(recipient.getRole()));
            systemAlertService.createRecipientAlert(
                    "customer_package_expiry",
                    severity,
                    "dispatch-planner",
                    message,
                    context,
                    recipient.getId(),
                    recipient.getRole(),
                    "customer-package-expiry:" + binding.getId() + ":" + reminderStage.code + ":" + recipient.getId()
            );
        }
    }

    private Map<Long, SysUser> resolveRecipients(Company company) {
        Map<Long, SysUser> recipients = new LinkedHashMap<>();
        List<SysUser> globalUsers = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getIsActive, true)
                        .in(SysUser::getRole, GLOBAL_RECIPIENT_ROLES)
        );
        addRecipients(recipients, globalUsers);

        if (company.getSalesOwnerId() != null) {
            SysUser salesOwner = sysUserMapper.selectById(company.getSalesOwnerId());
            if (isActiveUser(salesOwner)) {
                recipients.put(salesOwner.getId(), salesOwner);
            }
        }

        if (company.getPartnerId() != null) {
            List<SysUser> partnerUsers = sysUserMapper.selectList(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getIsActive, true)
                            .eq(SysUser::getRole, "partner")
                            .eq(SysUser::getPartnerId, company.getPartnerId())
            );
            addRecipients(recipients, partnerUsers);
        }
        return recipients;
    }

    private void addRecipients(Map<Long, SysUser> recipients, List<SysUser> users) {
        for (SysUser user : users) {
            if (isActiveUser(user)) {
                recipients.putIfAbsent(user.getId(), user);
            }
        }
    }

    private boolean isActiveUser(SysUser user) {
        return user != null && user.getId() != null && Boolean.TRUE.equals(user.getIsActive());
    }

    private String buildReminderMessage(Company company, LocalDate expireDate, ReminderStage reminderStage) {
        String companyName = safeText(company.getCompanyName());
        if (reminderStage == ReminderStage.ONE_MONTH) {
            return "客户「" + companyName + "」套餐将于 " + expireDate + " 到期（提前一个月提醒）";
        }
        if (reminderStage == ReminderStage.ONE_WEEK) {
            return "客户「" + companyName + "」套餐将于 " + expireDate + " 到期（提前一周提醒）";
        }
        if (reminderStage == ReminderStage.ONE_DAY) {
            return "客户「" + companyName + "」套餐将于 " + expireDate + " 到期（提前一天提醒）";
        }
        return "客户「" + companyName + "」套餐今日到期，系统将停止客户项目与跑批任务";
    }

    private void expireCompany(CompanyPackageBinding binding, LocalDate expireDate, LocalDate today) {
        Long lockedCompanyId = companyMapper.lockCompanyForUpdate(binding.getCompanyId());
        if (lockedCompanyId == null) {
            return;
        }
        Company company = companyMapper.selectById(binding.getCompanyId());
        if (company == null || company.getDeletedAt() != null) {
            return;
        }
        if (!"expired".equals(company.getStatus())) {
            Company update = new Company();
            update.setId(company.getId());
            update.setStatus("expired");
            companyMapper.updateById(update);
        }

        LocalDateTime expiredAt = LocalDateTime.of(today.isAfter(expireDate) ? today : expireDate, LocalTime.MIDNIGHT);
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getCompanyId, company.getId())
                        .isNull(Project::getDeletedAt)
                        .ne(Project::getStatus, "expired")
        );
        for (Project project : projects) {
            if ("active".equals(project.getStatus())) {
                channelAllocationService.auditCurrentAllocations(project, null, "customer.package.expire", true);
            }
            Project projectUpdate = new Project();
            projectUpdate.setId(project.getId());
            projectUpdate.setStatus("expired");
            projectUpdate.setExpiredAt(expiredAt);
            projectMapper.updateById(projectUpdate);
        }

        List<Long> projectIds = projects.stream().map(Project::getId).filter(Objects::nonNull).toList();
        if (!projectIds.isEmpty()) {
            dispatchTaskMapper.update(null, new LambdaUpdateWrapper<DispatchTask>()
                    .in(DispatchTask::getProjectId, projectIds)
                    .in(DispatchTask::getStatus, ACTIVE_TASK_STATUSES)
                    .set(DispatchTask::getStatus, DispatchTaskStatus.CANCELLED.value())
                    .set(DispatchTask::getFinishedAt, LocalDateTime.now())
                    .set(DispatchTask::getLastError, "Customer package expired")
            );
        }

        packageBindingMapper.markInactive(binding.getId(), LocalDateTime.now());
        log.info("Expired company {} package binding {} at {}", company.getId(), binding.getId(), expiredAt);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private enum ReminderStage {
        ONE_MONTH("one_month"),
        ONE_WEEK("one_week"),
        ONE_DAY("one_day"),
        TODAY("today");

        private final String code;

        ReminderStage(String code) {
            this.code = code;
        }
    }
}
