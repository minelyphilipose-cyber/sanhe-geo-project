package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.BrowserEnvironmentConstants;
import com.huanjing.geo.module.content.constant.PlatformAccountIdentityPolicy;
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
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.extension.dto.ExtensionRuntimeConfigResponse;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BrowserEnvironmentService {
    private static final int ERROR_CODE = 70030;

    private final BrowserEnvironmentMapper environmentMapper;
    private final BrowserEnvironmentAccountMapper environmentAccountMapper;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final BrandAccessService brandAccessService;
    private final CurrentUserService currentUserService;

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

    @Transactional
    public BrowserEnvironmentVO createEnvironment(BrowserEnvironmentCreateRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        brandAccessService.requireBrandAccess(request.brandId(), operator.getId(), BrandAccessAction.MANAGE);
        LocalDateTime now = LocalDateTime.now();
        BrowserEnvironment row = new BrowserEnvironment();
        row.setBrandId(request.brandId());
        row.setProvider(normalizeProvider(request.provider()));
        row.setEnvironmentKey(requireTrimmed(request.environmentKey(), "environmentKey is required"));
        row.setProviderProfileId(requireTrimmed(request.providerProfileId(), "providerProfileId is required"));
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
        return BrowserEnvironmentVO.from(row);
    }

    @Transactional
    public BrowserEnvironmentVO updateEnvironment(Long id, BrowserEnvironmentUpdateRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        BrowserEnvironment row = requireEnvironment(id);
        brandAccessService.requireBrandAccess(row.getBrandId(), operator.getId(), BrandAccessAction.MANAGE);
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
        return BrowserEnvironmentVO.from(row);
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
        softDeleteActiveBindingsForAccount(account.getId(), operator.getId(), now);
        BrowserEnvironmentAccount row = new BrowserEnvironmentAccount();
        row.setBrandId(account.getBrandId());
        row.setBrowserEnvironmentId(environment.getId());
        row.setSelfMediaAccountId(account.getId());
        row.setPlatform(account.getPlatform());
        row.setExpectedPlatformAccountId(PlatformAccountIdentityPolicy.comparablePlatformAccountId(
                account.getPlatform(), request.expectedPlatformAccountId()));
        row.setExpectedAccountName(trimToNull(request.expectedAccountName()));
        ensureExpectedIdentityNotClaimed(null, account.getBrandId(), account.getPlatform(),
                row.getExpectedPlatformAccountId(), row.getExpectedAccountName());
        row.setLoginStatus(BrowserEnvironmentConstants.LOGIN_UNKNOWN);
        row.setCreatedBy(operator.getId());
        row.setUpdatedBy(operator.getId());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        environmentAccountMapper.insert(row);
        return BrowserEnvironmentAccountVO.from(row, environment);
    }

    private void softDeleteActiveBindingsForAccount(Long selfMediaAccountId, Long operatorId, LocalDateTime now) {
        environmentAccountMapper.update(null, new LambdaUpdateWrapper<BrowserEnvironmentAccount>()
                .eq(BrowserEnvironmentAccount::getSelfMediaAccountId, selfMediaAccountId)
                .isNull(BrowserEnvironmentAccount::getDeletedAt)
                .set(BrowserEnvironmentAccount::getUpdatedBy, operatorId)
                .set(BrowserEnvironmentAccount::getUpdatedAt, now)
                .set(BrowserEnvironmentAccount::getDeletedAt, now));
    }

    @Transactional
    public BrowserEnvironmentAccountVO updateEnvironmentAccount(Long id, BrowserEnvironmentAccountUpdateRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        BrowserEnvironmentAccount row = requireEnvironmentAccount(id);
        brandAccessService.requireBrandAccess(row.getBrandId(), operator.getId(), BrandAccessAction.MANAGE);
        if (request.expectedPlatformAccountId() != null) {
            row.setExpectedPlatformAccountId(PlatformAccountIdentityPolicy.comparablePlatformAccountId(
                    row.getPlatform(), request.expectedPlatformAccountId()));
        }
        if (request.expectedAccountName() != null) {
            row.setExpectedAccountName(trimToNull(request.expectedAccountName()));
        }
        ensureExpectedIdentityNotClaimed(row.getId(), row.getBrandId(), row.getPlatform(),
                row.getExpectedPlatformAccountId(), row.getExpectedAccountName());
        if (StringUtils.hasText(request.loginStatus())) {
            String target = normalizeLoginStatus(request.loginStatus());
            assertTransitionAllowed(row.getLoginStatus(), target);
            row.setLoginStatus(target);
        }
        row.setUpdatedBy(operator.getId());
        row.setUpdatedAt(LocalDateTime.now());
        environmentAccountMapper.updateById(row);
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
        return toAccountVO(row);
    }

    @Transactional
    public BrowserEnvironmentAccountVO reportLoginStatus(Long id, BrowserEnvironmentLoginStatusRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        return reportLoginStatusForOperator(id, request, operator.getId());
    }

    @Transactional
    public BrowserEnvironmentAccountVO reportLoginStatusForExtension(Long id,
                                                                     BrowserEnvironmentLoginStatusRequest request,
                                                                     Long operatorId) {
        return reportLoginStatusForOperator(id, request, operatorId);
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
        List<BrowserEnvironmentAccount> rows =
                environmentAccountMapper.selectActiveByEnvironmentKeyAndPlatform(environmentKey, platform);
        if (rows == null || rows.isEmpty()) {
            fail("ENVIRONMENT_ACCOUNT_BINDING_NOT_FOUND", "未找到环境与平台对应的账号绑定");
        }
        if (rows.size() > 1) {
            fail("ENVIRONMENT_PLATFORM_BINDING_AMBIGUOUS", "同一环境与平台存在多个账号绑定，请改用环境账号ID上报");
        }
        return reportLoginStatusForOperator(rows.get(0).getId(), request, operatorId);
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
        if (request.selfMediaAccountId() != null) {
            BrowserEnvironmentAccount row = environmentAccountMapper.selectActiveBySelfMediaAccountId(request.selfMediaAccountId());
            if (row == null || !brandId.equals(row.getBrandId()) || !platform.equals(row.getPlatform())) {
                fail("ENVIRONMENT_ACCOUNT_BINDING_NOT_FOUND", "未找到指定自媒体账号对应的环境账号绑定");
            }
            BrowserEnvironment environment = requireEnvironment(row.getBrowserEnvironmentId());
            BrowserEnvironmentLoginStatusRequest normalizedRequest = new BrowserEnvironmentLoginStatusRequest(
                    environment.getEnvironmentKey(),
                    request.selfMediaAccountId(),
                    platform,
                    request.actualPlatformAccountId(),
                    request.actualAccountName(),
                    request.loginStatus(),
                    request.errorCode(),
                    request.errorMessage()
            );
            return reportLoginStatusForOperator(row.getId(), normalizedRequest, operatorId);
        }
        BrowserEnvironmentAccount target = resolveBrandPlatformReportTarget(brandId, platform, request);
        if (target == null) {
            fail("BRAND_PLATFORM_BINDING_NOT_FOUND", "未找到品牌与平台对应的环境账号绑定");
        }
        BrowserEnvironment environment = requireEnvironment(target.getBrowserEnvironmentId());
        BrowserEnvironmentLoginStatusRequest normalizedRequest = new BrowserEnvironmentLoginStatusRequest(
                environment.getEnvironmentKey(),
                request.selfMediaAccountId(),
                platform,
                request.actualPlatformAccountId(),
                request.actualAccountName(),
                request.loginStatus(),
                request.errorCode(),
                request.errorMessage()
        );
        return reportLoginStatusForOperator(target.getId(), normalizedRequest, operatorId);
    }

    private BrowserEnvironmentAccount resolveBrandPlatformReportTarget(Long brandId,
                                                                       String platform,
                                                                       BrowserEnvironmentBrandLoginStatusRequest request) {
        String actualId = PlatformAccountIdentityPolicy.comparablePlatformAccountId(platform, request.actualPlatformAccountId());
        String actualName = trimToNull(request.actualAccountName());
        List<BrowserEnvironmentAccount> rows =
                environmentAccountMapper.selectAllActiveByBrandIdAndPlatform(brandId, platform);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        if (rows.size() == 1) {
            return rows.get(0);
        }
        List<BrowserEnvironmentAccount> matched = rows.stream()
                .filter(row -> reportIdentityMatches(row, actualId, actualName))
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

    private boolean reportIdentityMatches(BrowserEnvironmentAccount row, String actualId, String actualName) {
        if (StringUtils.hasText(actualId)) {
            String expectedId = PlatformAccountIdentityPolicy.comparablePlatformAccountId(
                    row.getPlatform(), row.getExpectedPlatformAccountId());
            if (StringUtils.hasText(expectedId) && expectedId.equals(actualId)) {
                return true;
            }
        }
        if (accountNameMatches(row.getPlatform(), row.getExpectedAccountName(), actualName)) {
            return true;
        }
        SelfMediaAccount account = selfMediaAccountMapper.selectById(row.getSelfMediaAccountId());
        return accountNameMatches(row.getPlatform(), account == null ? null : account.getAccountName(), actualName);
    }

    private BrowserEnvironmentAccountVO reportLoginStatusForOperator(Long id,
                                                                     BrowserEnvironmentLoginStatusRequest request,
                                                                     Long operatorId) {
        BrowserEnvironmentAccount row = requireEnvironmentAccount(id);
        brandAccessService.requireBrandAccess(row.getBrandId(), operatorId, BrandAccessAction.OPERATE);
        BrowserEnvironment environment = requireEnvironment(row.getBrowserEnvironmentId());
        SelfMediaAccount account = requireSelfMediaAccount(row.getSelfMediaAccountId());
        validateStatusReportOwnership(row, environment, account, request);
        String incoming = normalizeLoginStatus(request.loginStatus());
        String target = resolveReportedStatus(row, incoming, request, account);
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
        return BrowserEnvironmentAccountVO.from(row, environment);
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
        if (!StringUtils.hasText(binding.getExpectedPlatformAccountId())
                && !StringUtils.hasText(binding.getExpectedAccountName())) {
            applyAccountIdentityExpectation(binding, account);
        } else if (!StringUtils.hasText(binding.getExpectedAccountName())
                && StringUtils.hasText(account.getAccountName())) {
            applyAccountNameExpectation(binding, account);
        }
        if (requireLoggedIn && BrowserEnvironmentConstants.LOGIN_MISMATCH.equals(binding.getLoginStatus())) {
            fail(BrowserEnvironmentConstants.ERR_ENVIRONMENT_ACCOUNT_MISMATCH, "环境内登录账号与绑定账号不一致");
        }
        if (requireLoggedIn && !BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(binding.getLoginStatus())) {
            fail(BrowserEnvironmentConstants.ERR_ENVIRONMENT_LOGIN_REQUIRED, "指纹浏览器环境账号未登录或需重新验证");
        }
        return binding;
    }

    private void applyAccountIdentityExpectation(BrowserEnvironmentAccount binding, SelfMediaAccount account) {
        String expectedPlatformAccountId = PlatformAccountIdentityPolicy.comparablePlatformAccountId(
                account.getPlatform(), account.getPlatformAccountId());
        String expectedAccountName = trimToNull(account.getAccountName());
        if (!StringUtils.hasText(expectedPlatformAccountId) && !StringUtils.hasText(expectedAccountName)) {
            fail(BrowserEnvironmentConstants.ERR_IDENTITY_EXPECTATION_MISSING, "自媒体账号缺少环境账号身份预期值");
        }
        ensureExpectedIdentityNotClaimed(binding.getId(), binding.getBrandId(), binding.getPlatform(),
                expectedPlatformAccountId, expectedAccountName);
        binding.setExpectedPlatformAccountId(expectedPlatformAccountId);
        binding.setExpectedAccountName(expectedAccountName);
        binding.setUpdatedAt(LocalDateTime.now());
        environmentAccountMapper.updateById(binding);
    }

    private void applyAccountNameExpectation(BrowserEnvironmentAccount binding, SelfMediaAccount account) {
        String expectedAccountName = trimToNull(account.getAccountName());
        if (!StringUtils.hasText(expectedAccountName)) {
            return;
        }
        ensureExpectedIdentityNotClaimed(binding.getId(), binding.getBrandId(), binding.getPlatform(),
                null, expectedAccountName);
        binding.setExpectedAccountName(expectedAccountName);
        binding.setUpdatedAt(LocalDateTime.now());
        environmentAccountMapper.updateById(binding);
    }

    private String resolveReportedStatus(BrowserEnvironmentAccount row,
                                         String incoming,
                                         BrowserEnvironmentLoginStatusRequest request,
                                         SelfMediaAccount account) {
        if (!BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(incoming)) {
            return incoming;
        }
        String actualId = PlatformAccountIdentityPolicy.comparablePlatformAccountId(
                row.getPlatform(), request.actualPlatformAccountId());
        String actualName = trimToNull(request.actualAccountName());
        if (!StringUtils.hasText(row.getExpectedPlatformAccountId())
                && !StringUtils.hasText(row.getExpectedAccountName())) {
            if (!StringUtils.hasText(actualId) && !StringUtils.hasText(actualName)) {
                fail(BrowserEnvironmentConstants.ERR_IDENTITY_EXPECTATION_MISSING, "首次登记缺少平台账号身份");
            }
            ensureExpectedIdentityNotClaimed(row.getId(), row.getBrandId(), row.getPlatform(), actualId, actualName);
            row.setExpectedPlatformAccountId(actualId);
            row.setExpectedAccountName(actualName);
            return BrowserEnvironmentConstants.LOGIN_LOGGED_IN;
        }
        boolean idMatches = StringUtils.hasText(row.getExpectedPlatformAccountId())
                && row.getExpectedPlatformAccountId().equals(actualId);
        boolean nameMatches = accountNameMatches(row.getPlatform(), row.getExpectedAccountName(), actualName);
        if (!nameMatches
                && !StringUtils.hasText(row.getExpectedAccountName())
                && accountNameMatches(row.getPlatform(), account.getAccountName(), actualName)) {
            ensureExpectedIdentityNotClaimed(row.getId(), row.getBrandId(), row.getPlatform(), null, actualName);
            row.setExpectedAccountName(actualName);
            nameMatches = true;
        }
        if (idMatches || nameMatches) {
            return BrowserEnvironmentConstants.LOGIN_LOGGED_IN;
        }
        return BrowserEnvironmentConstants.LOGIN_MISMATCH;
    }

    private boolean accountNameMatches(String platform, String expectedAccountName, String actualAccountName) {
        String expected = normalizeAccountNameForMatch(platform, expectedAccountName);
        String actual = normalizeAccountNameForMatch(platform, actualAccountName);
        return StringUtils.hasText(expected) && expected.equals(actual);
    }

    private String normalizeAccountNameForMatch(String platform, String value) {
        String text = trimToNull(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", "");
        String platformName = platformDisplayName(platform);
        if (StringUtils.hasText(platformName)) {
            String prefix = platformName + "/";
            if (normalized.startsWith(prefix) && normalized.length() > prefix.length()) {
                normalized = normalized.substring(prefix.length());
            }
        }
        return normalized;
    }

    private String platformDisplayName(String platform) {
        String normalized = trimToNull(platform);
        if (!StringUtils.hasText(normalized)) return null;
        return switch (normalized) {
            case "toutiao" -> "头条";
            case "zhihu" -> "知乎";
            case "xiaohongshu" -> "小红书";
            case "baijiahao" -> "百家号";
            default -> normalized;
        };
    }

    private void ensureExpectedIdentityNotClaimed(Long currentId,
                                                  Long brandId,
                                                  String platform,
                                                  String expectedPlatformAccountId,
                                                  String expectedAccountName) {
        LambdaQueryWrapper<BrowserEnvironmentAccount> wrapper = new LambdaQueryWrapper<BrowserEnvironmentAccount>()
                .eq(BrowserEnvironmentAccount::getBrandId, brandId)
                .eq(BrowserEnvironmentAccount::getPlatform, platform)
                .isNull(BrowserEnvironmentAccount::getDeletedAt);
        if (currentId != null) {
            wrapper.ne(BrowserEnvironmentAccount::getId, currentId);
        }
        if (StringUtils.hasText(expectedPlatformAccountId)) {
            wrapper.eq(BrowserEnvironmentAccount::getExpectedPlatformAccountId, expectedPlatformAccountId);
        } else if (StringUtils.hasText(expectedAccountName)) {
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
        if (!environment.getEnvironmentKey().equals(request.environmentKey())) {
            throw new BizException(403, "environmentKey does not match environment account binding");
        }
        if (request.selfMediaAccountId() != null
                && !Objects.equals(row.getSelfMediaAccountId(), request.selfMediaAccountId())) {
            throw new BizException(403, "selfMediaAccountId does not match environment account binding");
        }
        if (!row.getPlatform().equals(request.platform()) || !account.getPlatform().equals(request.platform())) {
            throw new BizException(403, "platform does not match environment account binding");
        }
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
