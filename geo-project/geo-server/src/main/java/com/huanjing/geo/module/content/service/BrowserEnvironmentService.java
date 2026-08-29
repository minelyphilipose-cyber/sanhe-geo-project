package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.BrowserEnvironmentConstants;
import com.huanjing.geo.module.content.constant.SelfMediaPublishFailureCodes;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentAccountCreateRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentAccountUpdateRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentBrandLoginStatusRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentCreateRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentLoginStatusRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentUpdateRequest;
import com.huanjing.geo.module.content.entity.BrowserEnvironment;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentAccountMapper;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.vo.BrowserEnvironmentAccountVO;
import com.huanjing.geo.module.content.vo.BrowserEnvironmentVO;
import com.huanjing.geo.module.content.vo.SelfMediaAutomationReadinessVO;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.extension.dto.ExtensionRuntimeConfigResponse;
import com.huanjing.geo.module.extension.config.ExtensionProperties;
import com.huanjing.geo.module.extension.entity.ExtensionSession;
import com.huanjing.geo.module.extension.entity.LocalAgentRuntimeStatus;
import com.huanjing.geo.module.extension.entity.LocalAgentSession;
import com.huanjing.geo.module.extension.mapper.ExtensionSessionMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentRuntimeStatusMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentSessionMapper;
import com.huanjing.geo.module.extension.service.SemverComparator;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BrowserEnvironmentService {
    private static final int ERROR_CODE = 70030;

    private final BrowserEnvironmentMapper environmentMapper;
    private final BrowserEnvironmentAccountMapper environmentAccountMapper;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final LocalAgentSessionMapper localAgentSessionMapper;
    private final LocalAgentRuntimeStatusMapper localAgentRuntimeStatusMapper;
    private final ExtensionSessionMapper extensionSessionMapper;
    private final BrandAccessService brandAccessService;
    private final CurrentUserService currentUserService;
    private final ExtensionProperties extensionProperties;
    private final SelfMediaLoginVerificationService loginVerificationService;
    private final SelfMediaLoginObservationService loginObservationService;
    private static final Set<String> SEMI_AUTO_PLATFORMS = Set.of("toutiao", "baijiahao", "zhihu", "xiaohongshu");

    public List<BrowserEnvironmentVO> listEnvironments(Long brandId) {
        SysUser operator = currentUserService.requireCurrentUser();
        brandAccessService.requireBrandAccess(brandId, operator.getId(), BrandAccessAction.READ);
        return environmentMapper.selectList(new LambdaQueryWrapper<BrowserEnvironment>()
                        .eq(BrowserEnvironment::getBrandId, brandId)
                        .orderByDesc(BrowserEnvironment::getUpdatedAt))
                .stream()
                .map(BrowserEnvironmentVO::from)
                .toList();
    }

    public SelfMediaAutomationReadinessVO selfMediaAutomationReadiness(Long brandId) {
        SysUser operator = currentUserService.requireCurrentUser();
        brandAccessService.requireBrandAccess(brandId, operator.getId(), BrandAccessAction.READ);
        LocalDateTime now = LocalDateTime.now();
        List<SelfMediaAutomationReadinessVO.Issue> issues = new ArrayList<>();

        LocalAgentSession localAgent = localAgentSessionMapper.selectActiveByOperatorId(operator.getId(), now).stream()
                .findFirst()
                .orElse(null);
        SelfMediaAutomationReadinessVO.LocalAgent localAgentStatus = new SelfMediaAutomationReadinessVO.LocalAgent(
                localAgent != null,
                localAgent != null && localAgent.getLastSeenAt() != null && localAgent.getLastSeenAt().isAfter(now.minusMinutes(5)),
                localAgent == null ? null : localAgent.getId(),
                localAgent == null ? null : localAgent.getHelperName(),
                localAgent == null ? null : localAgent.getLastSeenAt(),
                localAgent == null ? null : localAgent.getExpiresAt()
        );
        if (!localAgentStatus.bound()) {
            issues.add(issue("LOCAL_AGENT_NOT_BOUND", "error", "本地助手未绑定", "到个人中心绑定本机助手"));
        } else if (!localAgentStatus.online()) {
            issues.add(issue("LOCAL_AGENT_OFFLINE", "warning", "本地助手不在线", "启动本地助手并刷新状态"));
        }

        BrowserEnvironment defaultEnvironment = environmentMapper.selectList(new LambdaQueryWrapper<BrowserEnvironment>()
                        .eq(BrowserEnvironment::getBrandId, brandId)
                        .eq(BrowserEnvironment::getProvider, BrowserEnvironmentConstants.PROVIDER_ADSPOWER)
                        .isNull(BrowserEnvironment::getDeletedAt)
                        .orderByDesc(BrowserEnvironment::getUpdatedAt))
                .stream()
                .findFirst()
                .orElse(null);
        SelfMediaAutomationReadinessVO.BrowserEnvironment environmentStatus =
                new SelfMediaAutomationReadinessVO.BrowserEnvironment(
                        defaultEnvironment != null,
                        defaultEnvironment != null && BrowserEnvironmentConstants.ENV_STATUS_ACTIVE.equals(defaultEnvironment.getStatus()),
                        defaultEnvironment == null ? null : defaultEnvironment.getId(),
                        defaultEnvironment == null ? null : defaultEnvironment.getEnvironmentKey(),
                        defaultEnvironment == null ? null : defaultEnvironment.getProviderProfileId(),
                        defaultEnvironment == null ? null : defaultEnvironment.getName()
                );
        if (!environmentStatus.configured()) {
            issues.add(issue("ADSPOWER_ENVIRONMENT_NOT_CONFIGURED", "error", "未配置品牌 AdsPower 环境", "在品牌详情配置 AdsPower 浏览器环境"));
        } else if (!environmentStatus.active()) {
            issues.add(issue("ADSPOWER_ENVIRONMENT_DISABLED", "error", "品牌 AdsPower 环境未启用", "启用品牌默认 AdsPower 环境"));
        }

        List<ExtensionSession> activeExtensionSessions = extensionSessionMapper.selectActiveByBrandId(brandId).stream()
                .filter(item -> item.getExpiresAt() != null && item.getExpiresAt().isAfter(now))
                .toList();
        ExtensionSession extensionSession = activeExtensionSessions.stream()
                .filter(item -> extensionSessionMatchesEnvironment(item, defaultEnvironment))
                .findFirst()
                .orElseGet(() -> activeExtensionSessions.stream()
                        .filter(item -> !StringUtils.hasText(item.getEnvironmentKey())
                                && !StringUtils.hasText(item.getProviderProfileId()))
                        .findFirst()
                        .orElse(null));
        String expectedExtensionVersion = expectedGeoEnvExtensionVersion();
        SelfMediaAutomationReadinessVO.ExtensionBinding extensionStatus =
                new SelfMediaAutomationReadinessVO.ExtensionBinding(
                        extensionSession != null,
                        extensionSession != null && extensionSession.getLastSeenAt() != null
                                && extensionSession.getLastSeenAt().isAfter(now.minusMinutes(10)),
                        extensionSession == null ? null : extensionSession.getId(),
                        extensionSession == null ? null : extensionSession.getEnvironmentKey(),
                        extensionSession == null ? null : extensionSession.getProviderProfileId(),
                        extensionSession == null ? null : extensionSession.getExtensionVersion(),
                        expectedExtensionVersion,
                        extensionVersionSupportedForReadiness(
                                extensionSession == null ? null : extensionSession.getExtensionVersion(),
                                expectedExtensionVersion
                        ),
                        extensionSession == null ? null : extensionSession.getLastSeenAt(),
                        extensionSession == null ? null : extensionSession.getExpiresAt()
                );
        if (!extensionStatus.bound()) {
            issues.add(issue("EXTENSION_NOT_BOUND", "error", "AdsPower 环境扩展未绑定", "生成绑定码并在 AdsPower 环境扩展中绑定后台"));
        } else {
            if (!extensionStatus.online()) {
                issues.add(issue("EXTENSION_OFFLINE", "warning", "扩展最近未活跃", "打开 AdsPower 环境确认扩展已加载"));
            }
            if (!extensionStatus.versionSupported()) {
                issues.add(issue("EXTENSION_VERSION_OUTDATED", "warning", "环境扩展版本偏旧", "更新 AdsPower 环境中的 geo-env-extension 后重新打开环境"));
            }
        }

        List<SelfMediaAccount> accounts = selfMediaAccountMapper.selectList(new LambdaQueryWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getBrandId, brandId)
                .eq(SelfMediaAccount::getStatus, "active")
                .isNull(SelfMediaAccount::getDeletedAt)
                .in(SelfMediaAccount::getPlatform, SEMI_AUTO_PLATFORMS)
                .orderByAsc(SelfMediaAccount::getPlatform)
                .orderByDesc(SelfMediaAccount::getUpdatedAt));
        List<SelfMediaAutomationReadinessVO.AccountReadiness> accountStatuses = accounts.stream()
                .map(account -> accountReadiness(account, issues))
                .toList();
        if (accountStatuses.isEmpty()) {
            issues.add(issue("SELF_MEDIA_ACCOUNT_NOT_CONFIGURED", "warning", "未配置自媒体账号", "新增头条、百家号、知乎或小红书账号"));
        }

        boolean ready = issues.stream().noneMatch(item -> "error".equals(item.level()));
        String status = ready
                ? issues.isEmpty() ? "ready" : "warning"
                : "blocked";
        return new SelfMediaAutomationReadinessVO(
                brandId,
                status,
                ready,
                localAgentStatus,
                environmentStatus,
                extensionStatus,
                accountStatuses,
                issues
        );
    }

    private SelfMediaAutomationReadinessVO.AccountReadiness accountReadiness(SelfMediaAccount account,
                                                                             List<SelfMediaAutomationReadinessVO.Issue> issues) {
        BrowserEnvironmentAccount binding = environmentAccountMapper.selectActiveBySelfMediaAccountId(account.getId());
        if (binding == null) {
            issues.add(issue("ACCOUNT_ENVIRONMENT_NOT_BOUND", "error",
                    platformDisplayName(account.getPlatform()) + "账号未绑定浏览器环境",
                    "为账号「" + account.getAccountName() + "」绑定品牌默认 AdsPower 环境"));
            return new SelfMediaAutomationReadinessVO.AccountReadiness(
                    account.getId(),
                    account.getPlatform(),
                    account.getAccountName(),
                    false,
                    null,
                    null,
                    false,
                    "ACCOUNT_ENVIRONMENT_NOT_BOUND",
                    "账号未绑定浏览器环境"
            );
        }
        String loginStatus = StringUtils.hasText(binding.getLoginStatus())
                ? binding.getLoginStatus()
                : BrowserEnvironmentConstants.LOGIN_UNKNOWN;
        boolean loginReady = BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(loginStatus);
        String issueCode = null;
        String issueMessage = null;
        if (!loginReady) {
            issueCode = switch (loginStatus) {
                case BrowserEnvironmentConstants.LOGIN_MISMATCH -> "ACCOUNT_LOGIN_MISMATCH";
                case BrowserEnvironmentConstants.LOGIN_EXPIRED -> "ACCOUNT_LOGIN_EXPIRED";
                case BrowserEnvironmentConstants.LOGIN_REQUIRED -> "ACCOUNT_LOGIN_REQUIRED";
                case BrowserEnvironmentConstants.LOGIN_ERROR -> "ACCOUNT_LOGIN_ERROR";
                default -> "ACCOUNT_LOGIN_UNKNOWN";
            };
            issueMessage = switch (loginStatus) {
                case BrowserEnvironmentConstants.LOGIN_MISMATCH -> "环境内登录账号与绑定账号不一致";
                case BrowserEnvironmentConstants.LOGIN_EXPIRED -> "平台登录已过期";
                case BrowserEnvironmentConstants.LOGIN_REQUIRED -> "平台账号需要登录";
                case BrowserEnvironmentConstants.LOGIN_ERROR -> "平台登录状态检测异常";
                default -> "平台登录状态待确认";
            };
            issues.add(issue(issueCode, "warning",
                    platformDisplayName(account.getPlatform()) + "账号未就绪",
                    "打开 AdsPower 环境完成登录并等待扩展自动上报"));
        }
        return new SelfMediaAutomationReadinessVO.AccountReadiness(
                account.getId(),
                account.getPlatform(),
                account.getAccountName(),
                true,
                binding.getId(),
                loginStatus,
                loginReady,
                issueCode,
                issueMessage
        );
    }

    private SelfMediaAutomationReadinessVO.Issue issue(String code, String level, String title, String action) {
        return new SelfMediaAutomationReadinessVO.Issue(code, level, title, action, issueActionKey(code));
    }

    private String issueActionKey(String code) {
        return switch (code) {
            case "LOCAL_AGENT_NOT_BOUND", "LOCAL_AGENT_OFFLINE" -> "OPEN_LOCAL_HELPER_SETUP";
            case "ADSPOWER_ENVIRONMENT_NOT_CONFIGURED" -> "IMPORT_ADSPOWER_ENVIRONMENT";
            case "ADSPOWER_ENVIRONMENT_DISABLED" -> "EDIT_BROWSER_ENVIRONMENT";
            case "EXTENSION_NOT_BOUND", "EXTENSION_OFFLINE", "EXTENSION_VERSION_OUTDATED" -> "OPEN_AND_BIND_EXTENSION";
            case "ACCOUNT_ENVIRONMENT_NOT_BOUND" -> "BIND_UNBOUND_ACCOUNTS";
            case "ACCOUNT_LOGIN_MISMATCH", "ACCOUNT_LOGIN_EXPIRED", "ACCOUNT_LOGIN_REQUIRED",
                    "ACCOUNT_LOGIN_ERROR", "ACCOUNT_LOGIN_UNKNOWN" -> "OPEN_ADSPOWER_ENVIRONMENT";
            case "SELF_MEDIA_ACCOUNT_NOT_CONFIGURED" -> "CREATE_SELF_MEDIA_ACCOUNT";
            default -> null;
        };
    }

    private boolean extensionSessionMatchesEnvironment(ExtensionSession session, BrowserEnvironment environment) {
        if (session == null || environment == null) return false;
        if (StringUtils.hasText(session.getEnvironmentKey())
                && !Objects.equals(session.getEnvironmentKey(), environment.getEnvironmentKey())) {
            return false;
        }
        if (StringUtils.hasText(session.getProviderProfileId())
                && !Objects.equals(session.getProviderProfileId(), environment.getProviderProfileId())) {
            return false;
        }
        return StringUtils.hasText(session.getEnvironmentKey()) || StringUtils.hasText(session.getProviderProfileId());
    }

    private String expectedGeoEnvExtensionVersion() {
        if (extensionProperties == null || extensionProperties.getEnv() == null) return null;
        String expectedVersion = extensionProperties.getEnv().getExpectedVersion();
        return StringUtils.hasText(expectedVersion) ? expectedVersion.trim() : null;
    }

    private boolean extensionVersionSupportedForReadiness(String currentVersion, String expectedVersion) {
        if (!StringUtils.hasText(expectedVersion) || !StringUtils.hasText(currentVersion)) return true;
        try {
            return SemverComparator.compare(currentVersion, expectedVersion) >= 0;
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }

    @Transactional
    public BrowserEnvironmentVO createEnvironment(BrowserEnvironmentCreateRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        brandAccessService.requireBrandAccess(request.brandId(), operator.getId(), BrandAccessAction.MANAGE);
        LocalDateTime now = LocalDateTime.now();
        String provider = normalizeProvider(request.provider());
        String environmentKey = requireTrimmed(request.environmentKey(), "environmentKey is required");
        String providerProfileId = requireTrimmed(request.providerProfileId(), "providerProfileId is required");
        BrowserEnvironment brandEnvironment = environmentMapper.selectActiveByBrandId(request.brandId());
        if (brandEnvironment != null) {
            boolean runtimeIdentityChanged = !Objects.equals(brandEnvironment.getProvider(), provider)
                    || !Objects.equals(brandEnvironment.getEnvironmentKey(), environmentKey)
                    || !Objects.equals(brandEnvironment.getProviderProfileId(), providerProfileId);
            brandEnvironment.setProvider(provider);
            brandEnvironment.setEnvironmentKey(environmentKey);
            brandEnvironment.setProviderProfileId(providerProfileId);
            brandEnvironment.setName(trimToNull(request.name()));
            brandEnvironment.setStatus(BrowserEnvironmentConstants.ENV_STATUS_ACTIVE);
            brandEnvironment.setLastErrorCode(null);
            brandEnvironment.setLastErrorMessage(null);
            brandEnvironment.setUpdatedBy(operator.getId());
            brandEnvironment.setUpdatedAt(now);
            try {
                environmentMapper.updateById(brandEnvironment);
                environmentMapper.clearRuntimeErrors(brandEnvironment.getId());
            } catch (DuplicateKeyException ex) {
                throw duplicateEnvironmentException();
            }
            if (runtimeIdentityChanged) {
                invalidateEnvironmentBindingsAfterRuntimeIdentityChange(brandEnvironment.getId(), operator.getId(), now);
            }
            bindEnvironmentToLocalAgent(brandEnvironment.getId(), request.localAgentSessionId(), operator, now);
            return BrowserEnvironmentVO.from(brandEnvironment);
        }
        BrowserEnvironment existing = environmentMapper.selectOldestByIdentityIncludingDeleted(
                request.brandId(), provider, environmentKey, providerProfileId);
        if (existing != null) {
            if (existing.getDeletedAt() != null) {
                environmentMapper.restoreDeletedById(existing.getId());
            }
            existing.setEnvironmentKey(environmentKey);
            existing.setProviderProfileId(providerProfileId);
            existing.setName(trimToNull(request.name()));
            existing.setStatus(BrowserEnvironmentConstants.ENV_STATUS_ACTIVE);
            existing.setDeletedAt(null);
            existing.setUpdatedBy(operator.getId());
            existing.setUpdatedAt(now);
            environmentMapper.updateById(existing);
            bindEnvironmentToLocalAgent(existing.getId(), request.localAgentSessionId(), operator, now);
            return BrowserEnvironmentVO.from(existing);
        }
        BrowserEnvironment row = new BrowserEnvironment();
        row.setBrandId(request.brandId());
        row.setProvider(provider);
        row.setEnvironmentKey(environmentKey);
        row.setProviderProfileId(providerProfileId);
        row.setName(trimToNull(request.name()));
        row.setStatus(BrowserEnvironmentConstants.ENV_STATUS_ACTIVE);
        row.setCreatedBy(operator.getId());
        row.setUpdatedBy(operator.getId());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        try {
            environmentMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            throw duplicateEnvironmentException();
        }
        bindEnvironmentToLocalAgent(row.getId(), request.localAgentSessionId(), operator, now);
        return BrowserEnvironmentVO.from(row);
    }

    @Transactional
    public BrowserEnvironmentVO updateEnvironment(Long id, BrowserEnvironmentUpdateRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        BrowserEnvironment row = requireEnvironment(id);
        brandAccessService.requireBrandAccess(row.getBrandId(), operator.getId(), BrandAccessAction.MANAGE);
        String previousProviderProfileId = row.getProviderProfileId();
        if (StringUtils.hasText(request.providerProfileId())) {
            row.setProviderProfileId(request.providerProfileId().trim());
        }
        if (request.name() != null) {
            row.setName(trimToNull(request.name()));
        }
        if (StringUtils.hasText(request.status())) {
            row.setStatus(normalizeEnvironmentStatus(request.status()));
        }
        if (request.lastErrorCode() != null) {
            row.setLastErrorCode(trimToNull(request.lastErrorCode()));
        }
        if (request.lastErrorMessage() != null) {
            row.setLastErrorMessage(sanitizeErrorMessage(request.lastErrorMessage()));
        }
        row.setUpdatedBy(operator.getId());
        row.setUpdatedAt(LocalDateTime.now());
        try {
            environmentMapper.updateById(row);
        } catch (DuplicateKeyException ex) {
            throw duplicateEnvironmentException();
        }
        if (!Objects.equals(previousProviderProfileId, row.getProviderProfileId())) {
            invalidateEnvironmentBindingsAfterRuntimeIdentityChange(row.getId(), operator.getId(), row.getUpdatedAt());
        }
        bindEnvironmentToLocalAgent(row.getId(), request.localAgentSessionId(), operator, row.getUpdatedAt());
        return BrowserEnvironmentVO.from(row);
    }

    private void invalidateEnvironmentBindingsAfterRuntimeIdentityChange(Long browserEnvironmentId,
                                                                          Long operatorId,
                                                                          LocalDateTime now) {
        List<BrowserEnvironmentAccount> bindings = environmentAccountMapper.selectList(
                new LambdaQueryWrapper<BrowserEnvironmentAccount>()
                        .eq(BrowserEnvironmentAccount::getBrowserEnvironmentId, browserEnvironmentId)
                        .isNull(BrowserEnvironmentAccount::getDeletedAt));
        if (bindings.isEmpty()) {
            return;
        }
        environmentAccountMapper.update(null, new LambdaUpdateWrapper<BrowserEnvironmentAccount>()
                .eq(BrowserEnvironmentAccount::getBrowserEnvironmentId, browserEnvironmentId)
                .isNull(BrowserEnvironmentAccount::getDeletedAt)
                .set(BrowserEnvironmentAccount::getLoginStatus, BrowserEnvironmentConstants.LOGIN_REQUIRED)
                .set(BrowserEnvironmentAccount::getLastVerifiedAt, null)
                .set(BrowserEnvironmentAccount::getLastLoginSeenAt, null)
                .set(BrowserEnvironmentAccount::getLastErrorCode, "ENVIRONMENT_RUNTIME_IDENTITY_CHANGED")
                .set(BrowserEnvironmentAccount::getLastErrorMessage, "品牌指纹浏览器实例已变更，请重新验证登录状态")
                .set(BrowserEnvironmentAccount::getUpdatedBy, operatorId)
                .set(BrowserEnvironmentAccount::getUpdatedAt, now));
        bindings.stream()
                .map(BrowserEnvironmentAccount::getSelfMediaAccountId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(accountId -> loginVerificationService.invalidateAccountHealth(
                        accountId,
                        SelfMediaLoginVerificationService.METHOD_BINDING_INVALIDATED,
                        "品牌指纹浏览器实例已变更，请重新验证登录状态"));
    }

    private void bindEnvironmentToLocalAgent(Long browserEnvironmentId,
                                             Long localAgentSessionId,
                                             SysUser operator,
                                             LocalDateTime now) {
        if (browserEnvironmentId == null || localAgentSessionId == null) {
            return;
        }
        LocalAgentSession session = localAgentSessionMapper.selectById(localAgentSessionId);
        if (session == null
                || !"active".equalsIgnoreCase(session.getStatus())
                || !Objects.equals(session.getOperatorId(), operator.getId())
                || session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(now)) {
            throw new BizException(400, "本地助手会话无效或不属于当前操作员");
        }
        LocalAgentRuntimeStatus runtime = localAgentRuntimeStatusMapper.selectLatestBySessionId(localAgentSessionId);
        if (runtime == null
                || !StringUtils.hasText(runtime.getMachineId())
                || !StringUtils.hasText(runtime.getActiveProfile())) {
            throw new BizException(400, "本地助手尚未上报稳定的机器运行标识，请启动助手后重试");
        }
        environmentMapper.upsertLocalAgentBinding(
                browserEnvironmentId,
                runtime.getMachineId().trim(),
                runtime.getActiveProfile().trim(),
                session.getId(),
                operator.getId(),
                runtime.getLastSeenAt(),
                now
        );
    }

    @Transactional
    public void deleteEnvironment(Long id) {
        SysUser operator = currentUserService.requireCurrentUser();
        BrowserEnvironment row = requireEnvironment(id);
        brandAccessService.requireBrandAccess(row.getBrandId(), operator.getId(), BrandAccessAction.MANAGE);
        LocalDateTime now = LocalDateTime.now();
        environmentMapper.update(null, new LambdaUpdateWrapper<BrowserEnvironment>()
                .eq(BrowserEnvironment::getId, id)
                .isNull(BrowserEnvironment::getDeletedAt)
                .set(BrowserEnvironment::getStatus, BrowserEnvironmentConstants.ENV_STATUS_DELETED)
                .set(BrowserEnvironment::getUpdatedBy, operator.getId())
                .set(BrowserEnvironment::getUpdatedAt, now)
                .set(BrowserEnvironment::getDeletedAt, now));
        environmentMapper.disableLocalAgentBinding(id, now);
        invalidateEnvironmentBindingsAfterRuntimeIdentityChange(id, operator.getId(), now);
    }

    public List<BrowserEnvironmentAccountVO> listEnvironmentAccounts(Long brandId, String platform) {
        SysUser operator = currentUserService.requireCurrentUser();
        brandAccessService.requireBrandAccess(brandId, operator.getId(), BrandAccessAction.READ);
        LambdaQueryWrapper<BrowserEnvironmentAccount> wrapper = new LambdaQueryWrapper<BrowserEnvironmentAccount>()
                .eq(BrowserEnvironmentAccount::getBrandId, brandId)
                .orderByDesc(BrowserEnvironmentAccount::getUpdatedAt);
        if (StringUtils.hasText(platform)) {
            wrapper.eq(BrowserEnvironmentAccount::getPlatform, platform.trim());
        }
        return environmentAccountMapper.selectList(wrapper)
                .stream()
                .map(this::toAccountVO)
                .toList();
    }

    @Transactional
    public BrowserEnvironmentAccountVO createEnvironmentAccount(BrowserEnvironmentAccountCreateRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        BrowserEnvironment environment = requireEnvironment(request.browserEnvironmentId());
        SelfMediaAccount account = requireSelfMediaAccount(request.selfMediaAccountId());
        if (!Objects.equals(environment.getBrandId(), account.getBrandId())) {
            throw new BizException(400, "browser environment and self-media account brand mismatch");
        }
        brandAccessService.requireBrandAccess(account.getBrandId(), operator.getId(), BrandAccessAction.MANAGE);
        LocalDateTime now = LocalDateTime.now();
        BrowserEnvironmentAccount row = environmentAccountMapper
                .selectOldestBySelfMediaAccountIdIncludingDeleted(account.getId());
        if (row == null) {
            row = new BrowserEnvironmentAccount();
            row.setCreatedBy(operator.getId());
            row.setCreatedAt(now);
        } else if (row.getDeletedAt() != null) {
            environmentAccountMapper.restoreDeletedById(row.getId());
        }
        softDeleteActiveBindingsForAccountExcept(account.getId(), row.getId(), operator.getId(), now);
        row.setBrandId(account.getBrandId());
        row.setBrowserEnvironmentId(environment.getId());
        row.setSelfMediaAccountId(account.getId());
        row.setPlatform(account.getPlatform());
        row.setExpectedAccountName(trimToNull(request.expectedAccountName()));
        ensureExpectedIdentityNotClaimed(null, account.getBrandId(), account.getPlatform(), row.getExpectedAccountName());
        row.setLoginStatus(BrowserEnvironmentConstants.LOGIN_UNKNOWN);
        row.setUpdatedBy(operator.getId());
        row.setUpdatedAt(now);
        row.setDeletedAt(null);
        if (row.getId() == null) {
            environmentAccountMapper.insert(row);
        } else {
            environmentAccountMapper.updateById(row);
        }
        loginVerificationService.invalidateAccountHealth(
                account.getId(),
                SelfMediaLoginVerificationService.METHOD_BINDING_INVALIDATED,
                "账号已绑定到新的指纹浏览器环境，请重新验证登录状态"
        );
        return BrowserEnvironmentAccountVO.from(row, environment);
    }

    private void softDeleteActiveBindingsForAccount(Long selfMediaAccountId, Long operatorId, LocalDateTime now) {
        softDeleteActiveBindingsForAccountExcept(selfMediaAccountId, null, operatorId, now);
    }

    private void softDeleteActiveBindingsForAccountExcept(Long selfMediaAccountId,
                                                           Long preservedId,
                                                           Long operatorId,
                                                           LocalDateTime now) {
        LambdaUpdateWrapper<BrowserEnvironmentAccount> update = new LambdaUpdateWrapper<BrowserEnvironmentAccount>()
                .eq(BrowserEnvironmentAccount::getSelfMediaAccountId, selfMediaAccountId)
                .isNull(BrowserEnvironmentAccount::getDeletedAt);
        if (preservedId != null) {
            update.ne(BrowserEnvironmentAccount::getId, preservedId);
        }
        environmentAccountMapper.update(null, update
                .set(BrowserEnvironmentAccount::getUpdatedBy, operatorId)
                .set(BrowserEnvironmentAccount::getUpdatedAt, now)
                .set(BrowserEnvironmentAccount::getDeletedAt, now));
    }

    @Transactional
    public BrowserEnvironmentAccountVO updateEnvironmentAccount(Long id, BrowserEnvironmentAccountUpdateRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        BrowserEnvironmentAccount row = requireEnvironmentAccount(id);
        brandAccessService.requireBrandAccess(row.getBrandId(), operator.getId(), BrandAccessAction.MANAGE);
        String previousExpectedAccountName = row.getExpectedAccountName();
        String previousLoginStatus = row.getLoginStatus();
        if (request.expectedAccountName() != null) {
            row.setExpectedAccountName(trimToNull(request.expectedAccountName()));
        }
        ensureExpectedIdentityNotClaimed(row.getId(), row.getBrandId(), row.getPlatform(), row.getExpectedAccountName());
        if (StringUtils.hasText(request.loginStatus())) {
            String target = normalizeLoginStatus(request.loginStatus());
            assertTransitionAllowed(row.getLoginStatus(), target);
            row.setLoginStatus(target);
        }
        row.setUpdatedBy(operator.getId());
        row.setUpdatedAt(LocalDateTime.now());
        environmentAccountMapper.updateById(row);
        if (!Objects.equals(previousExpectedAccountName, row.getExpectedAccountName())
                || (StringUtils.hasText(request.loginStatus())
                && BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(previousLoginStatus)
                && !BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(row.getLoginStatus()))) {
            loginVerificationService.invalidateAccountHealth(
                    row.getSelfMediaAccountId(),
                    SelfMediaLoginVerificationService.METHOD_BINDING_INVALIDATED,
                    "指纹浏览器账号绑定信息已变更，请重新验证登录状态"
            );
        }
        return toAccountVO(row);
    }

    @Transactional
    public void deleteEnvironmentAccount(Long id) {
        SysUser operator = currentUserService.requireCurrentUser();
        BrowserEnvironmentAccount row = requireEnvironmentAccount(id);
        brandAccessService.requireBrandAccess(row.getBrandId(), operator.getId(), BrandAccessAction.MANAGE);
        LocalDateTime now = LocalDateTime.now();
        environmentAccountMapper.update(null, new LambdaUpdateWrapper<BrowserEnvironmentAccount>()
                .eq(BrowserEnvironmentAccount::getId, id)
                .isNull(BrowserEnvironmentAccount::getDeletedAt)
                .set(BrowserEnvironmentAccount::getUpdatedBy, operator.getId())
                .set(BrowserEnvironmentAccount::getUpdatedAt, now)
                .set(BrowserEnvironmentAccount::getDeletedAt, now));
        loginVerificationService.invalidateAccountHealth(
                row.getSelfMediaAccountId(),
                SelfMediaLoginVerificationService.METHOD_BINDING_INVALIDATED,
                "指纹浏览器环境已解绑，请重新绑定并验证登录状态"
        );
    }

    @Transactional
    public BrowserEnvironmentAccountVO markLoginExpired(Long id) {
        SysUser operator = currentUserService.requireCurrentUser();
        BrowserEnvironmentAccount row = requireEnvironmentAccount(id);
        brandAccessService.requireBrandAccess(row.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        assertTransitionAllowed(row.getLoginStatus(), BrowserEnvironmentConstants.LOGIN_EXPIRED);
        row.setLoginStatus(BrowserEnvironmentConstants.LOGIN_EXPIRED);
        row.setLastVerifiedAt(LocalDateTime.now());
        row.setLastErrorCode("MANUAL_LOGIN_EXPIRED");
        row.setLastErrorMessage("operator marked login expired");
        row.setUpdatedBy(operator.getId());
        row.setUpdatedAt(LocalDateTime.now());
        environmentAccountMapper.updateById(row);
        loginVerificationService.invalidateAccountHealth(
                row.getSelfMediaAccountId(),
                SelfMediaLoginVerificationService.METHOD_BINDING_INVALIDATED,
                "登录状态已被人工标记为过期，请重新登录并验证"
        );
        return toAccountVO(row);
    }

    @Transactional
    public BrowserEnvironmentAccountVO resetLoginIdentity(Long id) {
        SysUser operator = currentUserService.requireCurrentUser();
        BrowserEnvironmentAccount row = requireEnvironmentAccount(id);
        brandAccessService.requireBrandAccess(row.getBrandId(), operator.getId(), BrandAccessAction.MANAGE);
        LocalDateTime now = LocalDateTime.now();

        row.setExpectedPlatformAccountId(null);
        row.setExpectedAccountName(null);
        row.setLoginStatus(BrowserEnvironmentConstants.LOGIN_UNKNOWN);
        row.setLastVerifiedAt(null);
        row.setLastLoginSeenAt(null);
        row.setLastErrorCode(null);
        row.setLastErrorMessage(null);
        row.setUpdatedBy(operator.getId());
        row.setUpdatedAt(now);

        environmentAccountMapper.update(null, new LambdaUpdateWrapper<BrowserEnvironmentAccount>()
                .eq(BrowserEnvironmentAccount::getId, id)
                .isNull(BrowserEnvironmentAccount::getDeletedAt)
                .set(BrowserEnvironmentAccount::getExpectedPlatformAccountId, null)
                .set(BrowserEnvironmentAccount::getExpectedAccountName, null)
                .set(BrowserEnvironmentAccount::getLoginStatus, BrowserEnvironmentConstants.LOGIN_UNKNOWN)
                .set(BrowserEnvironmentAccount::getLastVerifiedAt, null)
                .set(BrowserEnvironmentAccount::getLastLoginSeenAt, null)
                .set(BrowserEnvironmentAccount::getLastErrorCode, null)
                .set(BrowserEnvironmentAccount::getLastErrorMessage, null)
                .set(BrowserEnvironmentAccount::getUpdatedBy, operator.getId())
                .set(BrowserEnvironmentAccount::getUpdatedAt, now));
        loginVerificationService.invalidateAccountHealth(
                row.getSelfMediaAccountId(),
                SelfMediaLoginVerificationService.METHOD_BINDING_INVALIDATED,
                "登录身份已重置，请重新确认账号身份并验证登录状态"
        );
        return toAccountVO(row);
    }

    @Transactional
    public BrowserEnvironmentAccountVO reportLoginStatus(Long id, BrowserEnvironmentLoginStatusRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        return reportLoginStatusForOperator(id, request, operator.getId(), false);
    }

    @Transactional
    public BrowserEnvironmentAccountVO reportLoginStatusForExtension(Long id,
                                                                     BrowserEnvironmentLoginStatusRequest request,
                                                                     Long operatorId) {
        return reportLoginStatusForOperator(id, request, operatorId, true);
    }

    @Transactional
    public BrowserEnvironmentAccountVO reportLoginStatusForExtensionByEnvironmentAndPlatform(
            BrowserEnvironmentLoginStatusRequest request,
            Long operatorId) {
        String environmentKey = trimToNull(request.environmentKey());
        String platform = trimToNull(request.platform());
        if (!StringUtils.hasText(environmentKey) || !StringUtils.hasText(platform)) {
            fail("ENVIRONMENT_AND_PLATFORM_REQUIRED", "环境标识和平台不能为空");
        }
        List<BrowserEnvironmentAccount> rows = selectActiveByEnvironmentKeyAndEquivalentPlatform(environmentKey, platform);
        if (rows == null || rows.isEmpty()) {
            fail("ENVIRONMENT_ACCOUNT_BINDING_NOT_FOUND", "未找到环境与平台对应的账号绑定");
        }
        if (rows.size() > 1) {
            fail("ENVIRONMENT_PLATFORM_BINDING_AMBIGUOUS", "同一环境与平台存在多个账号绑定，请改用环境账号ID上报");
        }
        return reportLoginStatusForOperator(rows.get(0).getId(), request, operatorId, true);
    }

    private List<BrowserEnvironmentAccount> selectActiveByEnvironmentKeyAndEquivalentPlatform(String environmentKey,
                                                                                              String platform) {
        List<BrowserEnvironmentAccount> rows = new ArrayList<>();
        for (String candidate : platformLookupValues(platform)) {
            List<BrowserEnvironmentAccount> candidateRows =
                    environmentAccountMapper.selectActiveByEnvironmentKeyAndPlatform(environmentKey, candidate);
            if (candidateRows == null || candidateRows.isEmpty()) {
                continue;
            }
            for (BrowserEnvironmentAccount row : candidateRows) {
                if (rows.stream().noneMatch(existing -> Objects.equals(existing.getId(), row.getId()))) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    @Transactional
    public BrowserEnvironmentAccountVO reportLoginStatusForExtensionByBrandAndPlatform(
            Long brandId,
            BrowserEnvironmentBrandLoginStatusRequest request,
            Long operatorId) {
        if (brandId == null) {
            fail("BRAND_REQUIRED", "品牌不能为空");
        }
        String platform = trimToNull(request.platform());
        if (!StringUtils.hasText(platform)) {
            fail("PLATFORM_REQUIRED", "平台不能为空");
        }
        brandAccessService.requireBrandAccess(brandId, operatorId, BrandAccessAction.OPERATE);
        String reportedEnvironmentKey = trimToNull(request.environmentKey());
        if (request.selfMediaAccountId() != null) {
            BrowserEnvironmentAccount row = environmentAccountMapper.selectActiveBySelfMediaAccountId(request.selfMediaAccountId());
            if (row == null || !brandId.equals(row.getBrandId()) || !platformEquivalent(platform, row.getPlatform())) {
                fail("ENVIRONMENT_ACCOUNT_BINDING_NOT_FOUND", "未找到指定自媒体账号对应的环境账号绑定");
            }
            BrowserEnvironment environment = requireEnvironment(row.getBrowserEnvironmentId());
            if (reportedEnvironmentKey != null && !environmentIdentityMatches(environment, reportedEnvironmentKey)) {
                fail("BROWSER_ENVIRONMENT_KEY_MISMATCH", "上报环境与自媒体账号绑定的指纹浏览器环境不一致");
            }
            BrowserEnvironmentLoginStatusRequest normalizedRequest = new BrowserEnvironmentLoginStatusRequest(
                    environment.getEnvironmentKey(),
                    request.selfMediaAccountId(),
                    row.getPlatform(),
                    request.actualPlatformAccountId(),
                    request.actualAccountName(),
                    request.loginStatus(),
                    request.errorCode(),
                    request.errorMessage(),
                    request.loginVerificationId(),
                    request.authoritative(),
                    request.reportSource(),
                    request.currentUrl(),
                    request.extensionVersion(),
                    request.protocolVersion()
            );
            return reportLoginStatusForOperator(row.getId(), normalizedRequest, operatorId, true);
        }
        BrowserEnvironmentAccount target = reportedEnvironmentKey == null
                ? resolveBrandPlatformReportTarget(brandId, platform, request)
                : resolveEnvironmentScopedBrandReportTarget(brandId, platform, reportedEnvironmentKey, request);
        if (target == null) {
            fail("BRAND_PLATFORM_BINDING_NOT_FOUND", "未找到品牌与平台对应的环境账号绑定");
        }
        BrowserEnvironment environment = requireEnvironment(target.getBrowserEnvironmentId());
        BrowserEnvironmentLoginStatusRequest normalizedRequest = new BrowserEnvironmentLoginStatusRequest(
                environment.getEnvironmentKey(),
                request.selfMediaAccountId(),
                target.getPlatform(),
                request.actualPlatformAccountId(),
                request.actualAccountName(),
                request.loginStatus(),
                request.errorCode(),
                request.errorMessage(),
                request.loginVerificationId(),
                request.authoritative(),
                request.reportSource(),
                request.currentUrl(),
                request.extensionVersion(),
                request.protocolVersion()
        );
        return reportLoginStatusForOperator(target.getId(), normalizedRequest, operatorId, true);
    }

    private BrowserEnvironmentAccount resolveEnvironmentScopedBrandReportTarget(
            Long brandId,
            String platform,
            String environmentKey,
            BrowserEnvironmentBrandLoginStatusRequest request) {
        List<BrowserEnvironmentAccount> rows = selectActiveByEnvironmentKeyAndEquivalentPlatform(environmentKey, platform)
                .stream()
                .filter(row -> Objects.equals(brandId, row.getBrandId()))
                .toList();
        if (rows.isEmpty()) {
            return null;
        }
        if (rows.size() == 1) {
            return rows.get(0);
        }
        String actualName = trimToNull(request.actualAccountName());
        List<BrowserEnvironmentAccount> matched = rows.stream()
                .filter(row -> reportIdentityMatches(row, actualName))
                .toList();
        if (matched.size() == 1) {
            return matched.get(0);
        }
        fail("ENVIRONMENT_PLATFORM_BINDING_AMBIGUOUS", "同一环境与平台存在多个账号绑定，请改用自媒体账号ID上报");
        return null;
    }

    private BrowserEnvironmentAccount resolveBrandPlatformReportTarget(Long brandId,
                                                                       String platform,
                                                                       BrowserEnvironmentBrandLoginStatusRequest request) {
        String actualName = trimToNull(request.actualAccountName());
        List<BrowserEnvironmentAccount> rows = selectAllActiveByBrandIdAndEquivalentPlatform(brandId, platform);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        if (rows.size() == 1) {
            return rows.get(0);
        }
        List<BrowserEnvironmentAccount> matched = rows.stream()
                .filter(row -> reportIdentityMatches(row, actualName))
                .toList();
        if (matched.size() == 1) {
            return matched.get(0);
        }
        if (matched.size() > 1) {
            fail("BRAND_PLATFORM_BINDING_AMBIGUOUS", "识别到的平台账号命中多个环境账号绑定，请检查自媒体账号名称是否重复");
        }
        fail("BRAND_PLATFORM_ACCOUNT_NOT_MATCHED", "当前登录的平台账号未匹配到品牌下的自媒体账号绑定");
        return null;
    }

    private List<BrowserEnvironmentAccount> selectAllActiveByBrandIdAndEquivalentPlatform(Long brandId, String platform) {
        List<BrowserEnvironmentAccount> rows = new ArrayList<>();
        for (String candidate : platformLookupValues(platform)) {
            List<BrowserEnvironmentAccount> candidateRows =
                    environmentAccountMapper.selectAllActiveByBrandIdAndPlatform(brandId, candidate);
            if (candidateRows == null || candidateRows.isEmpty()) {
                continue;
            }
            for (BrowserEnvironmentAccount row : candidateRows) {
                if (rows.stream().noneMatch(existing -> Objects.equals(existing.getId(), row.getId()))) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private List<String> platformLookupValues(String platform) {
        String normalized = trimToNull(platform);
        String canonical = canonicalSelfMediaPlatform(platform);
        List<String> values = new ArrayList<>();
        if (StringUtils.hasText(normalized)) {
            values.add(normalized);
        }
        if (StringUtils.hasText(canonical) && values.stream().noneMatch(canonical::equals)) {
            values.add(canonical);
        }
        if ("douyin".equals(canonical) && values.stream().noneMatch("douyin_image_text"::equals)) {
            values.add("douyin_image_text");
        }
        if ("wechat".equals(canonical) && values.stream().noneMatch("wechat_mp"::equals)) {
            values.add("wechat_mp");
        }
        return values;
    }

    private boolean reportIdentityMatches(BrowserEnvironmentAccount row, String actualName) {
        if (accountNameMatches(row.getPlatform(), row.getExpectedAccountName(), actualName)) {
            return true;
        }
        SelfMediaAccount account = selfMediaAccountMapper.selectById(row.getSelfMediaAccountId());
        return accountNameMatches(row.getPlatform(), account == null ? null : account.getAccountName(), actualName);
    }

    private BrowserEnvironmentAccountVO reportLoginStatusForOperator(Long id,
                                                                     BrowserEnvironmentLoginStatusRequest request,
                                                                     Long operatorId,
                                                                     boolean trustedExtensionReport) {
        BrowserEnvironmentAccount row = requireEnvironmentAccount(id);
        brandAccessService.requireBrandAccess(row.getBrandId(), operatorId, BrandAccessAction.OPERATE);
        BrowserEnvironment environment = requireEnvironment(row.getBrowserEnvironmentId());
        SelfMediaAccount account = requireSelfMediaAccount(row.getSelfMediaAccountId());
        validateStatusReportOwnership(row, environment, account, request);
        String incoming = normalizeLoginStatus(request.loginStatus());
        String target = resolveReportedStatus(row, incoming, request, account);
        boolean authoritativeNegative = hasServerAuthoritativeNegativeEvidence(request);
        loginObservationService.record(row, environment, account, request,
                observationResult(target, authoritativeNegative),
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(target) || authoritativeNegative,
                operatorId);
        if (isNonAuthoritativeExtensionFailure(trustedExtensionReport, request, target)) {
            if (request.loginVerificationId() != null) {
                loginVerificationService.completeFromLoginReport(
                        request.loginVerificationId(), row, account, request, target);
            }
            recordPassiveLoginDiagnostic(row, request, target, operatorId);
            return BrowserEnvironmentAccountVO.from(row, environment);
        }
        assertTransitionAllowed(row.getLoginStatus(), target);
        LocalDateTime now = LocalDateTime.now();
        row.setLoginStatus(target);
        row.setLastVerifiedAt(now);
        if (BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(target)) {
            row.setLastLoginSeenAt(now);
        }
        row.setLastErrorCode(trimToNull(request.errorCode()));
        row.setLastErrorMessage(sanitizeErrorMessage(request.errorMessage()));
        row.setUpdatedBy(operatorId);
        row.setUpdatedAt(now);
        environmentAccountMapper.updateById(row);
        // updateById 不会持久化 null，需显式清除上一次 MULTIPLE_IDENTITIES 等历史错误。
        environmentAccountMapper.updateNullableLoginErrors(
                row.getId(), row.getLastErrorCode(), row.getLastErrorMessage());
        if (request.loginVerificationId() != null) {
            boolean completed = loginVerificationService.completeFromLoginReport(
                    request.loginVerificationId(), row, account, request, target);
            if (!completed && trustedExtensionReport
                    && BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(target)) {
                loginVerificationService.recordTrustedPassiveHealthReport(
                        row, account, withoutVerificationId(request));
            }
        } else if (trustedExtensionReport && BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(target)) {
            loginVerificationService.recordTrustedPassiveHealthReport(row, account, request);
        }
        return BrowserEnvironmentAccountVO.from(row, environment);
    }

    private boolean isNonAuthoritativeExtensionFailure(boolean trustedExtensionReport,
                                                        BrowserEnvironmentLoginStatusRequest request,
                                                        String resolvedStatus) {
        return trustedExtensionReport
                && !hasServerAuthoritativeNegativeEvidence(request)
                && !BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(resolvedStatus);
    }

    private boolean hasServerAuthoritativeNegativeEvidence(BrowserEnvironmentLoginStatusRequest request) {
        if (request.loginVerificationId() == null) return false;
        String errorCode = trimToNull(request.errorCode());
        if (!"LOGIN_PAGE_CONFIRMED".equals(errorCode) && !"LOGIN_REQUIRED".equals(errorCode)) return false;
        String url = trimToNull(request.currentUrl());
        if (url == null) return false;
        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = String.valueOf(uri.getHost()).toLowerCase(Locale.ROOT);
            String path = String.valueOf(uri.getPath()).toLowerCase(Locale.ROOT);
            return host.contains("passport") || host.startsWith("sso.") || host.contains(".sso.")
                    || path.equals("/login") || path.startsWith("/login/")
                    || path.equals("/signin") || path.startsWith("/signin/");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String observationResult(String resolvedStatus, boolean authoritativeNegative) {
        if (BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(resolvedStatus)) return "login_confirmed";
        if (!authoritativeNegative) {
            return BrowserEnvironmentConstants.LOGIN_MISMATCH.equals(resolvedStatus)
                    ? "identity_mismatch" : "technical_failure";
        }
        return BrowserEnvironmentConstants.LOGIN_MISMATCH.equals(resolvedStatus)
                ? "identity_mismatch" : "login_required";
    }

    private void recordPassiveLoginDiagnostic(BrowserEnvironmentAccount row,
                                              BrowserEnvironmentLoginStatusRequest request,
                                              String resolvedStatus,
                                              Long operatorId) {
        String diagnosticCode = switch (resolvedStatus) {
            case BrowserEnvironmentConstants.LOGIN_MISMATCH -> "PASSIVE_ACCOUNT_MISMATCH";
            case BrowserEnvironmentConstants.LOGIN_EXPIRED -> "PASSIVE_LOGIN_EXPIRED";
            case BrowserEnvironmentConstants.LOGIN_REQUIRED -> "PASSIVE_LOGIN_UNCONFIRMED";
            default -> "PASSIVE_IDENTITY_UNREADABLE";
        };
        String detail = sanitizeErrorMessage(request.errorMessage());
        if (!StringUtils.hasText(detail) && BrowserEnvironmentConstants.LOGIN_MISMATCH.equals(resolvedStatus)) {
            detail = "页面读取账号“" + String.valueOf(trimToNull(request.actualAccountName())) + "”与系统账号不一致";
        }
        row.setLastErrorCode(diagnosticCode);
        row.setLastErrorMessage("被动页面检测暂未形成可信负向结论，已保留原登录状态"
                + (StringUtils.hasText(detail) ? "；" + detail : ""));
        row.setUpdatedBy(operatorId);
        row.setUpdatedAt(LocalDateTime.now());
        environmentAccountMapper.updateById(row);
        environmentAccountMapper.updateNullableLoginErrors(
                row.getId(), row.getLastErrorCode(), row.getLastErrorMessage());
    }

    private BrowserEnvironmentLoginStatusRequest withoutVerificationId(BrowserEnvironmentLoginStatusRequest request) {
        return new BrowserEnvironmentLoginStatusRequest(
                request.environmentKey(),
                request.selfMediaAccountId(),
                request.platform(),
                request.actualPlatformAccountId(),
                request.actualAccountName(),
                request.loginStatus(),
                request.errorCode(),
                request.errorMessage(),
                null,
                false,
                request.reportSource(),
                request.currentUrl(),
                request.extensionVersion(),
                request.protocolVersion()
        );
    }

    public BrowserEnvironmentAccountVO getBySelfMediaAccount(Long selfMediaAccountId) {
        BrowserEnvironmentAccount row = environmentAccountMapper.selectActiveBySelfMediaAccountId(selfMediaAccountId);
        if (row == null) return null;
        SysUser operator = currentUserService.requireCurrentUser();
        brandAccessService.requireBrandAccess(row.getBrandId(), operator.getId(), BrandAccessAction.READ);
        return toAccountVO(row);
    }

    public ExtensionRuntimeConfigResponse extensionRuntimeConfig(Long brandId,
                                                                 Long operatorId,
                                                                 String environmentKey,
                                                                 String platform) {
        if (brandId == null) {
            fail("BRAND_REQUIRED", "品牌不能为空");
        }
        brandAccessService.requireBrandAccess(brandId, operatorId, BrandAccessAction.OPERATE);
        String requestedEnvironmentKey = trimToNull(environmentKey);
        String requestedPlatform = trimToNull(platform);
        List<ExtensionRuntimeConfigResponse.RuntimeEnvironmentConfig> candidates =
                environmentAccountMapper.selectActiveRuntimeConfigsByBrandId(brandId).stream()
                        .map(this::toRuntimeEnvironmentConfig)
                        .filter(item -> requestedEnvironmentKey == null || requestedEnvironmentKey.equals(item.environmentKey()))
                        .filter(item -> requestedPlatform == null || requestedPlatform.equals(item.platform()))
                        .toList();
        ExtensionRuntimeConfigResponse.RuntimeEnvironmentConfig selected =
                candidates.size() == 1 ? candidates.get(0) : null;
        String selectionStatus = selected != null
                ? "selected"
                : candidates.isEmpty() ? "not_found" : "ambiguous";
        return new ExtensionRuntimeConfigResponse(
                brandId,
                "http://127.0.0.1:17891",
                selectionStatus,
                selected,
                candidates
        );
    }

    public BrowserEnvironmentAccount getActiveBinding(Long selfMediaAccountId) {
        return environmentAccountMapper.selectActiveBySelfMediaAccountId(selfMediaAccountId);
    }

    @Transactional
    public void recordPublishTaskLoginFailure(Long selfMediaAccountId,
                                              String failureCode,
                                              String failureMessage) {
        String normalizedFailureCode = SelfMediaPublishFailureCodes.normalize(failureCode);
        String loginStatus = switch (normalizedFailureCode == null ? "" : normalizedFailureCode) {
            case "LOGIN_REQUIRED" -> BrowserEnvironmentConstants.LOGIN_REQUIRED;
            case "ACCOUNT_MISMATCH" -> BrowserEnvironmentConstants.LOGIN_MISMATCH;
            default -> null;
        };
        if (loginStatus == null || selfMediaAccountId == null) {
            return;
        }
        BrowserEnvironmentAccount row = environmentAccountMapper.selectActiveBySelfMediaAccountId(selfMediaAccountId);
        if (row == null) {
            loginVerificationService.recordPublishTaskLoginFailure(selfMediaAccountId, failureMessage);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        row.setLoginStatus(loginStatus);
        row.setLastVerifiedAt(now);
        row.setLastErrorCode(normalizedFailureCode);
        row.setLastErrorMessage(sanitizeErrorMessage(failureMessage));
        row.setUpdatedAt(now);
        environmentAccountMapper.updateById(row);
        environmentAccountMapper.updateNullableLoginErrors(
                row.getId(), row.getLastErrorCode(), row.getLastErrorMessage());
        loginVerificationService.recordPublishTaskLoginFailure(selfMediaAccountId, failureMessage);
    }

    public BrowserEnvironment getEnvironmentForBinding(BrowserEnvironmentAccount binding) {
        if (binding == null) return null;
        return requireEnvironment(binding.getBrowserEnvironmentId());
    }

    public BrowserEnvironmentAccount validateForTaskCreation(SelfMediaAccount account) {
        return validateForTaskCreation(account, true);
    }

    public BrowserEnvironmentAccount validateForTaskCreation(SelfMediaAccount account, boolean requireLoggedIn) {
        BrowserEnvironmentAccount binding = getActiveBinding(account.getId());
        if (binding == null) return null;
        BrowserEnvironment environment = requireEnvironment(binding.getBrowserEnvironmentId());
        if (!BrowserEnvironmentConstants.ENV_STATUS_ACTIVE.equalsIgnoreCase(environment.getStatus())) {
            fail(BrowserEnvironmentConstants.ERR_ENVIRONMENT_DISABLED, "指纹浏览器环境已停用");
        }
        if (!StringUtils.hasText(binding.getExpectedAccountName())) {
            applyAccountIdentityExpectation(binding, account);
        }
        if (requireLoggedIn) {
            rejectExplicitLoginFailure(binding.getLoginStatus());
        }
        return binding;
    }

    private void rejectExplicitLoginFailure(String loginStatus) {
        String status = StringUtils.hasText(loginStatus)
                ? normalizeLoginStatus(loginStatus)
                : BrowserEnvironmentConstants.LOGIN_UNKNOWN;
        switch (status) {
            case BrowserEnvironmentConstants.LOGIN_REQUIRED ->
                    fail(BrowserEnvironmentConstants.ERR_ENVIRONMENT_LOGIN_REQUIRED,
                            "平台账号需要重新登录，请打开指纹浏览器完成登录后再发布");
            case BrowserEnvironmentConstants.LOGIN_EXPIRED ->
                    fail(BrowserEnvironmentConstants.ERR_ENVIRONMENT_LOGIN_EXPIRED,
                            "平台登录已过期，请重新登录并同步状态后再发布");
            case BrowserEnvironmentConstants.LOGIN_MISMATCH ->
                    fail(BrowserEnvironmentConstants.ERR_ENVIRONMENT_ACCOUNT_MISMATCH,
                            "当前登录账号与系统绑定账号不一致，请确认账号或重置账号校验");
            case BrowserEnvironmentConstants.LOGIN_ERROR ->
                    fail(BrowserEnvironmentConstants.ERR_ENVIRONMENT_LOGIN_ERROR,
                            "平台登录状态检测异常，请打开指纹浏览器重新同步后再发布");
            default -> {
                // unknown is intentionally compatible with historical production bindings.
            }
        }
    }

    private void applyAccountIdentityExpectation(BrowserEnvironmentAccount binding, SelfMediaAccount account) {
        String expectedAccountName = trimToNull(account.getAccountName());
        if (!StringUtils.hasText(expectedAccountName)) {
            fail(BrowserEnvironmentConstants.ERR_IDENTITY_EXPECTATION_MISSING, "自媒体账号缺少账号名称");
        }
        ensureExpectedIdentityNotClaimed(binding.getId(), binding.getBrandId(), binding.getPlatform(), expectedAccountName);
        binding.setExpectedAccountName(expectedAccountName);
        binding.setUpdatedAt(LocalDateTime.now());
        environmentAccountMapper.updateById(binding);
    }

    private void applyAccountNameExpectation(BrowserEnvironmentAccount binding, SelfMediaAccount account) {
        String expectedAccountName = trimToNull(account.getAccountName());
        if (!StringUtils.hasText(expectedAccountName)) {
            return;
        }
        ensureExpectedIdentityNotClaimed(binding.getId(), binding.getBrandId(), binding.getPlatform(), expectedAccountName);
        binding.setExpectedAccountName(expectedAccountName);
    }

    private String resolveReportedStatus(BrowserEnvironmentAccount row,
                                         String incoming,
                                         BrowserEnvironmentLoginStatusRequest request,
                                         SelfMediaAccount account) {
        if (!BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(incoming)) {
            return incoming;
        }
        String actualName = trimToNull(request.actualAccountName());
        if (!StringUtils.hasText(row.getExpectedAccountName())) {
            applyAccountNameExpectation(row, account);
            if (!StringUtils.hasText(row.getExpectedAccountName()) && StringUtils.hasText(actualName)) {
                ensureExpectedIdentityNotClaimed(row.getId(), row.getBrandId(), row.getPlatform(), actualName);
                row.setExpectedAccountName(actualName);
            }
        }
        if (!StringUtils.hasText(actualName)) {
            fail(BrowserEnvironmentConstants.ERR_IDENTITY_EXPECTATION_MISSING, "登录状态上报缺少平台账号名称");
        }
        boolean nameMatches = request.loginVerificationId() != null
                ? verificationAccountNameMatches(row.getExpectedAccountName(), actualName)
                : accountNameMatches(row.getPlatform(), row.getExpectedAccountName(), actualName);
        if (!nameMatches) {
            nameMatches = request.loginVerificationId() != null
                    ? verificationAccountNameMatches(account.getAccountName(), actualName)
                    : accountNameMatches(row.getPlatform(), account.getAccountName(), actualName);
        }
        if (nameMatches) {
            return BrowserEnvironmentConstants.LOGIN_LOGGED_IN;
        }
        return BrowserEnvironmentConstants.LOGIN_MISMATCH;
    }

    private boolean accountNameMatches(String platform, String expectedAccountName, String actualAccountName) {
        String expected = normalizeAccountNameForMatch(platform, expectedAccountName);
        String actual = normalizeAccountNameForMatch(platform, actualAccountName);
        return StringUtils.hasText(expected) && expected.equals(actual);
    }

    private boolean verificationAccountNameMatches(String expectedAccountName, String actualAccountName) {
        String expected = normalizeVerificationAccountName(expectedAccountName);
        String actual = normalizeVerificationAccountName(actualAccountName);
        return StringUtils.hasText(expected) && expected.equals(actual);
    }

    private String normalizeVerificationAccountName(String value) {
        if (!StringUtils.hasText(value)) return null;
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeAccountNameForMatch(String platform, String value) {
        String normalized = normalizeVerificationAccountName(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        for (String platformName : platformDisplayNames(platform)) {
            String normalizedPlatformName = normalizeVerificationAccountName(platformName);
            if (!StringUtils.hasText(normalizedPlatformName)) {
                continue;
            }
            int separator = normalized.indexOf('/');
            if (separator > 0
                    && normalized.substring(0, separator).trim().equals(normalizedPlatformName)
                    && StringUtils.hasText(normalized.substring(separator + 1))) {
                normalized = normalized.substring(separator + 1).trim();
                break;
            }
        }
        return normalized;
    }

    private List<String> platformDisplayNames(String platform) {
        String normalized = canonicalSelfMediaPlatform(platform);
        if (!StringUtils.hasText(normalized)) return List.of();
        return switch (normalized) {
            case "douyin" -> List.of("抖音图文", "抖音");
            default -> List.of(platformDisplayName(normalized));
        };
    }

    private String platformDisplayName(String platform) {
        String normalized = canonicalSelfMediaPlatform(platform);
        if (!StringUtils.hasText(normalized)) return null;
        return switch (normalized) {
            case "toutiao" -> "头条";
            case "zhihu" -> "知乎";
            case "xiaohongshu" -> "小红书";
            case "baijiahao" -> "百家号";
            case "douyin" -> "抖音图文";
            default -> normalized;
        };
    }

    private void ensureExpectedIdentityNotClaimed(Long currentId,
                                                  Long brandId,
                                                  String platform,
                                                  String expectedAccountName) {
        LambdaQueryWrapper<BrowserEnvironmentAccount> wrapper = new LambdaQueryWrapper<BrowserEnvironmentAccount>()
                .eq(BrowserEnvironmentAccount::getBrandId, brandId)
                .eq(BrowserEnvironmentAccount::getPlatform, platform)
                .isNull(BrowserEnvironmentAccount::getDeletedAt);
        if (currentId != null) {
            wrapper.ne(BrowserEnvironmentAccount::getId, currentId);
        }
        if (StringUtils.hasText(expectedAccountName)) {
            wrapper.eq(BrowserEnvironmentAccount::getExpectedAccountName, expectedAccountName);
        } else {
            return;
        }
        wrapper.last("LIMIT 1");
        BrowserEnvironmentAccount existing = environmentAccountMapper.selectOne(wrapper);
        if (existing != null) {
            fail("IDENTITY_EXPECTATION_ALREADY_BOUND", "平台账号身份已绑定到其它环境账号");
        }
    }

    private void validateStatusReportOwnership(BrowserEnvironmentAccount row,
                                               BrowserEnvironment environment,
                                               SelfMediaAccount account,
                                               BrowserEnvironmentLoginStatusRequest request) {
        if (!environmentIdentityMatches(environment, request.environmentKey())) {
            throw new BizException(403, "environmentKey does not match environment account binding");
        }
        if (request.selfMediaAccountId() != null
                && !Objects.equals(row.getSelfMediaAccountId(), request.selfMediaAccountId())) {
            throw new BizException(403, "selfMediaAccountId does not match environment account binding");
        }
        if (!platformEquivalent(row.getPlatform(), request.platform())
                || !platformEquivalent(account.getPlatform(), request.platform())) {
            throw new BizException(403, "platform does not match environment account binding");
        }
    }

    private boolean environmentIdentityMatches(BrowserEnvironment environment, String reportedEnvironmentKey) {
        String reported = normalizeEnvironmentIdentity(reportedEnvironmentKey);
        return StringUtils.hasText(reported)
                && (reported.equals(normalizeEnvironmentIdentity(environment.getEnvironmentKey()))
                || reported.equals(normalizeEnvironmentIdentity(environment.getName())));
    }

    private String normalizeEnvironmentIdentity(String value) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized.replace('-', '_');
    }

    private boolean platformEquivalent(String left, String right) {
        String normalizedLeft = canonicalSelfMediaPlatform(left);
        String normalizedRight = canonicalSelfMediaPlatform(right);
        return StringUtils.hasText(normalizedLeft) && normalizedLeft.equals(normalizedRight);
    }

    private String canonicalSelfMediaPlatform(String platform) {
        String normalized = trimToNull(platform);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String canonical = ArticlePromptChannels.normalizeSelfMediaPublishPlatform(normalized);
        return StringUtils.hasText(canonical) ? canonical : normalized;
    }

    private void assertTransitionAllowed(String from, String to) {
        String normalizedFrom = StringUtils.hasText(from) ? from : BrowserEnvironmentConstants.LOGIN_UNKNOWN;
        if (BrowserEnvironmentConstants.LOGIN_MISMATCH.equals(normalizedFrom)
                && !BrowserEnvironmentConstants.LOGIN_UNKNOWN.equals(to)
                && !BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(to)
                && !BrowserEnvironmentConstants.LOGIN_MISMATCH.equals(to)) {
            fail(BrowserEnvironmentConstants.ERR_ENVIRONMENT_ACCOUNT_MISMATCH, "账号不一致状态必须先人工重置为 unknown");
        }
    }

    private BrowserEnvironmentAccountVO toAccountVO(BrowserEnvironmentAccount row) {
        return BrowserEnvironmentAccountVO.from(row, environmentMapper.selectById(row.getBrowserEnvironmentId()));
    }

    private ExtensionRuntimeConfigResponse.RuntimeEnvironmentConfig toRuntimeEnvironmentConfig(BrowserEnvironmentAccount row) {
        BrowserEnvironment environment = environmentMapper.selectById(row.getBrowserEnvironmentId());
        return new ExtensionRuntimeConfigResponse.RuntimeEnvironmentConfig(
                row.getId(),
                row.getBrowserEnvironmentId(),
                environment == null ? null : environment.getEnvironmentKey(),
                environment == null ? null : environment.getName(),
                environment == null ? null : environment.getProvider(),
                environment == null ? null : environment.getProviderProfileId(),
                row.getSelfMediaAccountId(),
                row.getPlatform(),
                row.getExpectedPlatformAccountId(),
                row.getExpectedAccountName(),
                row.getLoginStatus()
        );
    }

    private BrowserEnvironment requireEnvironment(Long id) {
        BrowserEnvironment row = environmentMapper.selectById(id);
        if (row == null) {
            throw new BizException(404, "browser environment not found");
        }
        return row;
    }

    private BrowserEnvironmentAccount requireEnvironmentAccount(Long id) {
        BrowserEnvironmentAccount row = environmentAccountMapper.selectById(id);
        if (row == null) {
            throw new BizException(404, "browser environment account not found");
        }
        return row;
    }

    private SelfMediaAccount requireSelfMediaAccount(Long id) {
        SelfMediaAccount account = selfMediaAccountMapper.selectById(id);
        if (account == null) {
            throw new BizException(404, "self media account not found");
        }
        return account;
    }

    private String normalizeEnvironmentStatus(String value) {
        String status = requireTrimmed(value, "environment status is required");
        if (!BrowserEnvironmentConstants.ENV_STATUS_ACTIVE.equals(status)
                && !BrowserEnvironmentConstants.ENV_STATUS_DISABLED.equals(status)
                && !BrowserEnvironmentConstants.ENV_STATUS_DELETED.equals(status)) {
            throw new BizException(400, "unsupported browser environment status");
        }
        return status;
    }

    private String normalizeLoginStatus(String value) {
        String status = requireTrimmed(value, "loginStatus is required");
        if (!BrowserEnvironmentConstants.LOGIN_STATUSES.contains(status)) {
            throw new BizException(400, "unsupported browser environment account loginStatus");
        }
        return status;
    }

    private String normalizeProvider(String value) {
        String provider = StringUtils.hasText(value) ? value.trim() : BrowserEnvironmentConstants.PROVIDER_ADSPOWER;
        if (!BrowserEnvironmentConstants.PROVIDER_ADSPOWER.equals(provider)) {
            throw new BizException(400, "unsupported browser environment provider");
        }
        return provider;
    }

    private String requireTrimmed(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(400, message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String sanitizeErrorMessage(String value) {
        String text = trimToNull(value);
        if (text == null) return null;
        String sanitized = text
                .replaceAll("(?i)(bearer\\s+)[a-z0-9._\\-]+", "$1***")
                .replaceAll("(?i)(token[=:]\\s*)[^\\s,;]+", "$1***")
                .replaceAll("(?i)(cookie[=:]\\s*)[^\\s,;]+", "$1***");
        return sanitized.length() > 512 ? sanitized.substring(0, 512) : sanitized;
    }

    private BizException duplicateEnvironmentException() {
        return new BizException(400, "AdsPower 浏览器编号或环境代号已被其他启用环境使用，请编辑已有环境或换一个编号");
    }

    private void fail(String code, String message) {
        throw new BizException(ERROR_CODE, message, 200, Map.of("code", code));
    }
}
