package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.SelfMediaAuthWorkOrderUpdateRequest;
import com.huanjing.geo.module.content.dto.SelfMediaAuthWorkOrderEventVO;
import com.huanjing.geo.module.content.dto.SelfMediaAuthWorkOrderPageVO;
import com.huanjing.geo.module.content.dto.SelfMediaAuthWorkOrderVO;
import com.huanjing.geo.module.content.entity.BrowserEnvironment;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaAuthWorkOrder;
import com.huanjing.geo.module.content.entity.SelfMediaAuthWorkOrderEvent;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentMapper;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAuthWorkOrderEventMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAuthWorkOrderMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SelfMediaAuthWorkOrderService {
    private static final Set<String> OPERABLE_STATUSES = Set.of("operator_verification", "resolved");
    private static final Set<String> EXPLICIT_LOGIN_STATUSES = Set.of("login_required", "expired");
    private static final Set<String> EXPLICIT_FAILURE_METHODS = Set.of(
            SelfMediaLoginVerificationService.METHOD_SYSTEM_TRIGGERED,
            SelfMediaLoginVerificationService.METHOD_PUBLISH_TASK_FAILURE);
    private static final Set<String> PRIORITIES = Set.of("low", "normal", "high", "urgent");
    private static final Set<String> RESOLUTION_MODES = Set.of("login_restored", "special");
    private static final Set<String> DISABLE_ACCOUNT_REASONS = Set.of(
            "账号已停用", "账号停止运营", "品牌不再运营该平台");

    private final SelfMediaAuthWorkOrderMapper workOrderMapper;
    private final SelfMediaAuthWorkOrderEventMapper eventMapper;
    private final BrowserEnvironmentMapper environmentMapper;
    private final BrowserEnvironmentAccountMapper environmentAccountMapper;
    private final SelfMediaAccountMapper accountMapper;
    private final BrandAccessService brandAccessService;
    private final CurrentUserService currentUserService;
    private final SysUserMapper sysUserMapper;

    public List<SelfMediaAuthWorkOrderVO> list(String status, Long ownerUserId) {
        SysUser operator = currentUserService.requireCurrentUser();
        requirePermission("self-media.auth-health.read", "content.read");
        LambdaQueryWrapper<SelfMediaAuthWorkOrder> wrapper = new LambdaQueryWrapper<SelfMediaAuthWorkOrder>()
                .eq(StringUtils.hasText(status), SelfMediaAuthWorkOrder::getStatus, normalize(status))
                .eq(ownerUserId != null, SelfMediaAuthWorkOrder::getOwnerUserId, ownerUserId)
                .orderByAsc(SelfMediaAuthWorkOrder::getNextFollowUpAt)
                .orderByDesc(SelfMediaAuthWorkOrder::getUpdatedAt);
        List<SelfMediaAuthWorkOrder> rows = workOrderMapper.selectList(wrapper).stream()
                .filter(row -> canRead(row, operator))
                .toList();
        Map<Long, SysUser> owners = usersById(rows.stream().map(SelfMediaAuthWorkOrder::getOwnerUserId).toList());
        return rows.stream().map(row -> SelfMediaAuthWorkOrderVO.from(row, displayName(ownerById(owners, row.getOwnerUserId())))).toList();
    }

    public SelfMediaAuthWorkOrderPageVO page(String stage, Long ownerUserId, long current, long size) {
        SysUser operator = currentUserService.requireCurrentUser();
        requirePermission("self-media.auth-health.read", "content.read");
        long safeCurrent = Math.max(1, current);
        long safeSize = Math.max(1, Math.min(size, 100));
        List<Long> accessibleBrandIds = brandAccessService.listAccessibleBrandIds(operator.getId(), BrandAccessAction.READ);
        if (accessibleBrandIds == null || accessibleBrandIds.isEmpty()) {
            return new SelfMediaAuthWorkOrderPageVO(List.of(), 0, safeCurrent, safeSize, 0, 0, 0);
        }
        LambdaQueryWrapper<SelfMediaAuthWorkOrder> wrapper = new LambdaQueryWrapper<SelfMediaAuthWorkOrder>()
                .in(SelfMediaAuthWorkOrder::getBrandId, accessibleBrandIds)
                .eq(ownerUserId != null, SelfMediaAuthWorkOrder::getOwnerUserId, ownerUserId);
        applyStageFilter(wrapper, stage);
        // Keep overdue work visible on the first page. MySQL sorts NULL first for ASC,
        // so a plain next_follow_up_at sort would bury overdue work after pagination.
        wrapper.last("ORDER BY CASE "
                + "WHEN next_follow_up_at IS NOT NULL AND next_follow_up_at <= NOW() THEN 0 "
                + "WHEN next_follow_up_at IS NOT NULL THEN 1 ELSE 2 END, "
                + "CASE priority WHEN 'urgent' THEN 0 WHEN 'high' THEN 1 WHEN 'normal' THEN 2 ELSE 3 END, "
                + "next_follow_up_at ASC, updated_at DESC");
        Page<SelfMediaAuthWorkOrder> page = workOrderMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
        Map<Long, SysUser> owners = usersById(page.getRecords().stream().map(SelfMediaAuthWorkOrder::getOwnerUserId).toList());
        List<SelfMediaAuthWorkOrderVO> records = page.getRecords().stream()
                .map(row -> SelfMediaAuthWorkOrderVO.from(row, displayName(ownerById(owners, row.getOwnerUserId())))).toList();
        long unclaimed = workOrderMapper.selectCount(new LambdaQueryWrapper<SelfMediaAuthWorkOrder>()
                .in(SelfMediaAuthWorkOrder::getBrandId, accessibleBrandIds)
                .isNull(SelfMediaAuthWorkOrder::getOwnerUserId)
                .ne(SelfMediaAuthWorkOrder::getStatus, "resolved"));
        long processing = workOrderMapper.selectCount(new LambdaQueryWrapper<SelfMediaAuthWorkOrder>()
                .in(SelfMediaAuthWorkOrder::getBrandId, accessibleBrandIds)
                .isNotNull(SelfMediaAuthWorkOrder::getOwnerUserId)
                .ne(SelfMediaAuthWorkOrder::getStatus, "resolved"));
        long resolved = workOrderMapper.selectCount(new LambdaQueryWrapper<SelfMediaAuthWorkOrder>()
                .in(SelfMediaAuthWorkOrder::getBrandId, accessibleBrandIds)
                .eq(SelfMediaAuthWorkOrder::getStatus, "resolved"));
        return new SelfMediaAuthWorkOrderPageVO(records, page.getTotal(), page.getCurrent(), page.getSize(),
                unclaimed, processing, resolved);
    }

    private void applyStageFilter(LambdaQueryWrapper<SelfMediaAuthWorkOrder> wrapper, String stage) {
        String normalized = normalize(stage);
        if ("unclaimed".equals(normalized)) {
            wrapper.isNull(SelfMediaAuthWorkOrder::getOwnerUserId)
                    .ne(SelfMediaAuthWorkOrder::getStatus, "resolved");
        } else if ("processing".equals(normalized)) {
            wrapper.isNotNull(SelfMediaAuthWorkOrder::getOwnerUserId)
                    .ne(SelfMediaAuthWorkOrder::getStatus, "resolved");
        } else if ("resolved".equals(normalized)) {
            wrapper.eq(SelfMediaAuthWorkOrder::getStatus, "resolved");
        }
    }

    public List<SelfMediaAuthWorkOrderEventVO> events(Long id) {
        SysUser operator = currentUserService.requireCurrentUser();
        requirePermission("self-media.auth-health.read", "content.read");
        SelfMediaAuthWorkOrder row = require(id);
        brandAccessService.requireBrandAccess(row.getBrandId(), operator.getId(), BrandAccessAction.READ);
        List<SelfMediaAuthWorkOrderEvent> events = eventMapper.selectList(new LambdaQueryWrapper<SelfMediaAuthWorkOrderEvent>()
                        .eq(SelfMediaAuthWorkOrderEvent::getWorkOrderId, id)
                        .orderByDesc(SelfMediaAuthWorkOrderEvent::getCreatedAt));
        Map<Long, SysUser> operators = usersById(events.stream().map(SelfMediaAuthWorkOrderEvent::getOperatorId).toList());
        return events.stream().map(event -> SelfMediaAuthWorkOrderEventVO.from(
                event, displayName(operators.get(event.getOperatorId())))).toList();
    }

    @Transactional
    public SelfMediaAuthWorkOrderVO getOrCreate(Long brandId,
                                                Long browserEnvironmentId,
                                                String issueSummary) {
        SysUser operator = currentUserService.requireCurrentUser();
        requirePermission("self-media.auth-health.verify", "company.update");
        brandAccessService.requireBrandAccess(brandId, operator.getId(), BrandAccessAction.OPERATE);
        BrowserEnvironment environment = requireEnvironment(brandId, browserEnvironmentId);
        SelfMediaAuthWorkOrder existing = find(brandId);
        if (existing != null) {
            if ("resolved".equals(existing.getStatus())) {
                requireExplicitLoginEvidence(brandId);
                reopen(existing, issueSummary, operator.getId());
            }
            return SelfMediaAuthWorkOrderVO.from(existing);
        }
        requireExplicitLoginEvidence(brandId);
        LocalDateTime now = LocalDateTime.now();
        SelfMediaAuthWorkOrder row = new SelfMediaAuthWorkOrder();
        row.setBrandId(brandId);
        row.setBrowserEnvironmentId(environment == null ? null : environment.getId());
        row.setStatus("system_diagnosis");
        row.setPriority("normal");
        row.setCustomerActionRequired(false);
        row.setIssueSummary(limit(issueSummary, 1000));
        row.setCreatedBy(operator.getId());
        row.setUpdatedBy(operator.getId());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        try {
            workOrderMapper.insert(row);
        } catch (DuplicateKeyException ignored) {
            return SelfMediaAuthWorkOrderVO.from(find(brandId));
        }
        appendEvent(row.getId(), "created", null, row.getStatus(), issueSummary, operator.getId());
        return SelfMediaAuthWorkOrderVO.from(row);
    }

    @Transactional
    public SelfMediaAuthWorkOrderVO update(Long id, SelfMediaAuthWorkOrderUpdateRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        requirePermission("self-media.auth-health.verify", "company.update");
        SelfMediaAuthWorkOrder row = require(id);
        brandAccessService.requireBrandAccess(row.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        if (row.getOwnerUserId() == null) {
            throw new BizException(409, "请先开始处理该账号登录待办");
        }
        if (!Objects.equals(row.getOwnerUserId(), operator.getId())) {
            throw new BizException(409, "该账号登录待办已由其他运营人员处理，当前仅可查看");
        }
        String fromStatus = row.getStatus();
        String targetStatus = StringUtils.hasText(request.status()) ? normalize(request.status()) : fromStatus;
        if (!OPERABLE_STATUSES.contains(targetStatus)) throw new BizException(400, "仅支持运营人员处理或处理结束");
        if ("resolved".equals(fromStatus)) throw new BizException(400, "已结束工单不能直接更新");
        String note = limit(request.note(), 1000);
        rejectSensitiveNote(note);
        if ("resolved".equals(targetStatus) && !StringUtils.hasText(note)) {
            throw new BizException(400, "处理结束前必须填写处理结果");
        }
        Resolution resolution = "resolved".equals(targetStatus)
                ? validateResolution(row.getBrandId(), request.resolutionMode(), request.resolutionReason(),
                request.affectedAccountIds(), note, operator.getId())
                : Resolution.none();
        String previousPriority = row.getPriority();
        Boolean previousCustomerActionRequired = row.getCustomerActionRequired();
        LocalDateTime previousNextFollowUpAt = row.getNextFollowUpAt();
        if (StringUtils.hasText(request.priority())) {
            String priority = normalize(request.priority());
            if (!PRIORITIES.contains(priority)) throw new BizException(400, "不支持的优先级");
            row.setPriority(priority);
        }
        row.setStatus(targetStatus);
        row.setCustomerActionRequired(false);
        if (StringUtils.hasText(note)) row.setLatestNote(note);
        row.setNextFollowUpAt("resolved".equals(targetStatus) ? null : request.nextFollowUpAt());
        row.setResolvedAt("resolved".equals(targetStatus) ? LocalDateTime.now() : null);
        row.setResolutionMode("resolved".equals(targetStatus) ? resolution.mode() : null);
        row.setResolutionReason("resolved".equals(targetStatus) ? resolution.reason() : null);
        row.setUpdatedBy(operator.getId());
        row.setUpdatedAt(LocalDateTime.now());
        workOrderMapper.updateById(row);
        if (!"resolved".equals(targetStatus)) {
            workOrderMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SelfMediaAuthWorkOrder>()
                    .eq(SelfMediaAuthWorkOrder::getId, row.getId())
                    .set(SelfMediaAuthWorkOrder::getResolvedAt, null)
                    .set(SelfMediaAuthWorkOrder::getResolutionMode, null)
                    .set(SelfMediaAuthWorkOrder::getResolutionReason, null));
        }
        boolean stateChanged = !Objects.equals(fromStatus, targetStatus)
                || !Objects.equals(previousPriority, row.getPriority())
                || !Objects.equals(previousCustomerActionRequired, row.getCustomerActionRequired())
                || !Objects.equals(previousNextFollowUpAt, row.getNextFollowUpAt());
        if (stateChanged || StringUtils.hasText(note)) {
            appendEvent(row.getId(), "updated", fromStatus, targetStatus, note, operator.getId());
        }
        return SelfMediaAuthWorkOrderVO.from(row, displayName(operator));
    }

    @Transactional
    public SelfMediaAuthWorkOrderVO claim(Long id) {
        SysUser operator = currentUserService.requireCurrentUser();
        requirePermission("self-media.auth-health.verify", "company.update");
        SelfMediaAuthWorkOrder row = require(id);
        brandAccessService.requireBrandAccess(row.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        if (row.getOwnerUserId() != null && !row.getOwnerUserId().equals(operator.getId())) {
            throw new BizException(409, "该账号登录待办已由其他运营人员处理");
        }
        if ("resolved".equals(row.getStatus())) throw new BizException(400, "已结束待办无需再次处理");
        if (row.getOwnerUserId() == null) {
            LocalDateTime now = LocalDateTime.now();
            String fromStatus = row.getStatus();
            if (workOrderMapper.claimIfUnowned(id, operator.getId(), now) == 0) {
                SelfMediaAuthWorkOrder latest = require(id);
                if (!operator.getId().equals(latest.getOwnerUserId())) {
                    throw new BizException(409, "该账号登录待办已由其他运营人员处理");
                }
                return SelfMediaAuthWorkOrderVO.from(latest);
            }
            row.setOwnerUserId(operator.getId());
            row.setStatus("operator_verification");
            row.setCustomerActionRequired(false);
            row.setUpdatedBy(operator.getId());
            row.setUpdatedAt(now);
            appendEvent(row.getId(), "claimed", fromStatus, row.getStatus(), "运营人员开始处理", operator.getId());
        }
        return SelfMediaAuthWorkOrderVO.from(row);
    }

    /**
     * Reopens an existing resolved order when a subsequent health scan finds a new risk.
     * The order is deliberately left unassigned so any on-duty operator can claim it.
     */
    @Transactional
    public boolean reopenResolvedForBrand(Long brandId, String issueSummary) {
        if (brandId == null) return false;
        SelfMediaAuthWorkOrder row = find(brandId);
        if (row == null || !"resolved".equals(row.getStatus())) return false;
        reopen(row, issueSummary, null);
        return true;
    }

    private void reopen(SelfMediaAuthWorkOrder row, String issueSummary, Long operatorId) {
        String fromStatus = row.getStatus();
        row.setStatus("system_diagnosis");
        row.setCustomerActionRequired(false);
        row.setIssueSummary(limit(issueSummary, 1000));
        row.setLatestNote("系统检测到该品牌再次出现账号授权风险，工单已自动复开");
        row.setOwnerUserId(null);
        row.setNextFollowUpAt(null);
        row.setResolvedAt(null);
        row.setResolutionMode(null);
        row.setResolutionReason(null);
        row.setUpdatedBy(operatorId);
        row.setUpdatedAt(LocalDateTime.now());
        workOrderMapper.updateById(row);
        workOrderMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SelfMediaAuthWorkOrder>()
                .eq(SelfMediaAuthWorkOrder::getId, row.getId())
                .set(SelfMediaAuthWorkOrder::getOwnerUserId, null)
                .set(SelfMediaAuthWorkOrder::getResolvedAt, null)
                .set(SelfMediaAuthWorkOrder::getResolutionMode, null)
                .set(SelfMediaAuthWorkOrder::getResolutionReason, null)
                .set(SelfMediaAuthWorkOrder::getNextFollowUpAt, null));
        appendEvent(row.getId(), "reopened", fromStatus, row.getStatus(), row.getLatestNote(), operatorId);
    }

    private BrowserEnvironment requireEnvironment(Long brandId, Long environmentId) {
        if (environmentId == null) return null;
        BrowserEnvironment row = environmentMapper.selectById(environmentId);
        if (row == null || !brandId.equals(row.getBrandId())) throw new BizException(404, "品牌指纹环境不存在");
        return row;
    }

    private void requireExplicitLoginEvidence(Long brandId) {
        if (!hasExplicitLoginEvidence(brandId)) {
            throw new BizException(409, "当前品牌没有明确的账号登录失效事实，请先完成内部核验");
        }
    }

    private boolean hasExplicitLoginEvidence(Long brandId) {
        LocalDateTime now = LocalDateTime.now();
        List<SelfMediaAccount> activeAccounts = accountMapper.selectList(new LambdaQueryWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getBrandId, brandId)
                .eq(SelfMediaAccount::getStatus, "active")
                .isNull(SelfMediaAccount::getDeletedAt));
        if (activeAccounts.isEmpty()) return false;
        List<Long> activeAccountIds = activeAccounts.stream().map(SelfMediaAccount::getId).filter(Objects::nonNull).toList();
        boolean bindingRequiresLogin = !activeAccountIds.isEmpty() && environmentAccountMapper.selectList(
                        new LambdaQueryWrapper<BrowserEnvironmentAccount>()
                                .eq(BrowserEnvironmentAccount::getBrandId, brandId)
                                .in(BrowserEnvironmentAccount::getSelfMediaAccountId, activeAccountIds)
                                .in(BrowserEnvironmentAccount::getLoginStatus, EXPLICIT_LOGIN_STATUSES)
                                .isNull(BrowserEnvironmentAccount::getDeletedAt))
                .stream().findAny().isPresent();
        if (bindingRequiresLogin) return true;
        return activeAccounts.stream().anyMatch(account -> accountHasExplicitLoginFailure(account, now));
    }

    private Resolution validateResolution(Long brandId,
                                          String requestedMode,
                                          String requestedReason,
                                          List<Long> affectedAccountIds,
                                          String note,
                                          Long operatorId) {
        String mode = normalize(requestedMode);
        if (!StringUtils.hasText(mode)) {
            mode = StringUtils.hasText(note) && note.startsWith("特殊结束：") ? "special" : "login_restored";
        }
        if (!RESOLUTION_MODES.contains(mode)) throw new BizException(400, "不支持的结束处理方式");
        if ("special".equals(mode)) {
            String reason = StringUtils.hasText(requestedReason) ? requestedReason.trim() : null;
            if (!DISABLE_ACCOUNT_REASONS.contains(reason)) {
                throw new BizException(409, "系统误报或其他原因不能消除仍存在的登录异常，请保存处理进展");
            }
            disableAffectedAccounts(brandId, affectedAccountIds, operatorId);
            if (hasExplicitLoginEvidence(brandId)) {
                throw new BizException(409, "当前品牌仍有需要登录的启用账号，请全部处理后再结束");
            }
            return new Resolution(mode, reason);
        }
        if (hasExplicitLoginEvidence(brandId)) {
            throw new BizException(409, "当前仍有需要登录的启用账号；停止运营的账号可使用特殊结束，误报请保存处理进展");
        }
        return new Resolution(mode, "登录状态已恢复");
    }

    private void disableAffectedAccounts(Long brandId, List<Long> affectedAccountIds, Long operatorId) {
        Set<Long> ids = affectedAccountIds == null ? Set.of() : affectedAccountIds.stream()
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) throw new BizException(400, "请选择需要停止运营的账号");
        List<SelfMediaAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getBrandId, brandId)
                .in(SelfMediaAccount::getId, ids)
                .eq(SelfMediaAccount::getStatus, "active")
                .isNull(SelfMediaAccount::getDeletedAt));
        if (accounts.size() != ids.size()) throw new BizException(400, "所选账号不存在、已停用或不属于当前品牌");
        LocalDateTime now = LocalDateTime.now();
        for (SelfMediaAccount account : accounts) {
            if (!accountHasExplicitLoginFailure(account, now) && !bindingHasExplicitLoginFailure(account.getId())) {
                throw new BizException(409, "只能在特殊结束时停用明确需要登录的账号");
            }
            account.setStatus("disabled");
            account.setUpdatedAt(now);
            accountMapper.updateById(account);
        }
    }

    private boolean bindingHasExplicitLoginFailure(Long accountId) {
        return environmentAccountMapper.selectList(new LambdaQueryWrapper<BrowserEnvironmentAccount>()
                        .eq(BrowserEnvironmentAccount::getSelfMediaAccountId, accountId)
                        .in(BrowserEnvironmentAccount::getLoginStatus, EXPLICIT_LOGIN_STATUSES)
                        .isNull(BrowserEnvironmentAccount::getDeletedAt))
                .stream().findAny().isPresent();
    }

    private boolean accountHasExplicitLoginFailure(SelfMediaAccount account, LocalDateTime now) {
        if ("expired".equalsIgnoreCase(account.getStatus())) return true;
        if (account.getRefreshTokenExpiresAt() != null && !account.getRefreshTokenExpiresAt().isAfter(now)) return true;
        if (!"failed".equalsIgnoreCase(account.getLastLoginVerificationResult())
                || !EXPLICIT_FAILURE_METHODS.contains(account.getLastLoginVerificationMethod())) return false;
        return account.getLastLoginVerificationCheckedAt() != null
                && (account.getLastLoginVerifiedAt() == null
                || !account.getLastLoginVerificationCheckedAt().isBefore(account.getLastLoginVerifiedAt()));
    }

    private SelfMediaAuthWorkOrder find(Long brandId) {
        return workOrderMapper.selectOne(new LambdaQueryWrapper<SelfMediaAuthWorkOrder>()
                .eq(SelfMediaAuthWorkOrder::getBrandId, brandId)
                .last("LIMIT 1"));
    }

    private SelfMediaAuthWorkOrder require(Long id) {
        SelfMediaAuthWorkOrder row = workOrderMapper.selectById(id);
        if (row == null) throw new BizException(404, "账号登录待办不存在");
        return row;
    }

    private boolean canRead(SelfMediaAuthWorkOrder row, SysUser operator) {
        try {
            brandAccessService.requireBrandAccess(row.getBrandId(), operator.getId(), BrandAccessAction.READ);
            return true;
        } catch (BizException ignored) {
            return false;
        }
    }

    private Map<Long, SysUser> usersById(List<Long> ids) {
        Set<Long> distinctIds = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinctIds.isEmpty()) return Map.of();
        List<SysUser> users = sysUserMapper.selectBatchIds(distinctIds);
        if (users == null) return Map.of();
        return users.stream().collect(Collectors.toMap(SysUser::getId, Function.identity(), (left, right) -> left));
    }

    private String displayName(SysUser user) {
        if (user == null) return null;
        return StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getUsername();
    }

    private SysUser ownerById(Map<Long, SysUser> owners, Long ownerUserId) {
        return ownerUserId == null ? null : owners.get(ownerUserId);
    }

    private void appendEvent(Long orderId, String action, String fromStatus, String toStatus,
                             String note, Long operatorId) {
        SelfMediaAuthWorkOrderEvent event = new SelfMediaAuthWorkOrderEvent();
        event.setWorkOrderId(orderId);
        event.setAction(action);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setNote(limit(note, 1000));
        event.setOperatorId(operatorId);
        event.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(event);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String limit(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String text = value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }

    private void rejectSensitiveNote(String note) {
        if (!StringUtils.hasText(note)) return;
        String normalized = note.toLowerCase(Locale.ROOT);
        if (normalized.matches("(?s).*(密码|验证码|cookie|token|pwd|password)\\s*[:：=]\\s*\\S+.*")
                || normalized.contains("sessionid=") || normalized.contains("sid_tt=")) {
            throw new BizException(400, "工单备注禁止填写账号密码、验证码、Cookie 或 Token，请通过安全渠道临时使用");
        }
    }

    private void requirePermission(String permission, String legacyPermission) {
        if (!currentUserService.hasPermission(permission) && !currentUserService.hasPermission(legacyPermission)) {
            throw new BizException(403, "No permission: " + permission);
        }
    }

    private record Resolution(String mode, String reason) {
        static Resolution none() {
            return new Resolution(null, null);
        }
    }
}
