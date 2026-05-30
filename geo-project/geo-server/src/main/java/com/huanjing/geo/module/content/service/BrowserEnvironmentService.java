package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.BrowserEnvironmentConstants;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentAccountCreateRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentAccountUpdateRequest;
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
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
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
        environmentMapper.insert(row);
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
        environmentMapper.updateById(row);
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
        row.setExpectedPlatformAccountId(trimToNull(request.expectedPlatformAccountId()));
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
            row.setExpectedPlatformAccountId(trimToNull(request.expectedPlatformAccountId()));
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

    private BrowserEnvironmentAccountVO reportLoginStatusForOperator(Long id,
                                                                     BrowserEnvironmentLoginStatusRequest request,
                                                                     Long operatorId) {
        BrowserEnvironmentAccount row = requireEnvironmentAccount(id);
        brandAccessService.requireBrandAccess(row.getBrandId(), operatorId, BrandAccessAction.OPERATE);
        BrowserEnvironment environment = requireEnvironment(row.getBrowserEnvironmentId());
        SelfMediaAccount account = requireSelfMediaAccount(row.getSelfMediaAccountId());
        validateStatusReportOwnership(row, environment, account, request);
        String incoming = normalizeLoginStatus(request.loginStatus());
        String target = resolveReportedStatus(row, incoming, request);
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

    public BrowserEnvironmentAccount getActiveBinding(Long selfMediaAccountId) {
        return environmentAccountMapper.selectActiveBySelfMediaAccountId(selfMediaAccountId);
    }

    public BrowserEnvironment getEnvironmentForBinding(BrowserEnvironmentAccount binding) {
        if (binding == null) return null;
        return requireEnvironment(binding.getBrowserEnvironmentId());
    }

    public BrowserEnvironmentAccount validateForTaskCreation(SelfMediaAccount account) {
        BrowserEnvironmentAccount binding = getActiveBinding(account.getId());
        if (binding == null) return null;
        BrowserEnvironment environment = requireEnvironment(binding.getBrowserEnvironmentId());
        if (!BrowserEnvironmentConstants.ENV_STATUS_ACTIVE.equalsIgnoreCase(environment.getStatus())) {
            fail(BrowserEnvironmentConstants.ERR_ENVIRONMENT_DISABLED, "指纹浏览器环境已停用");
        }
        if (!StringUtils.hasText(binding.getExpectedPlatformAccountId())
                && !StringUtils.hasText(binding.getExpectedAccountName())) {
            fail(BrowserEnvironmentConstants.ERR_IDENTITY_EXPECTATION_MISSING, "自媒体账号缺少环境账号身份预期值");
        }
        if (BrowserEnvironmentConstants.LOGIN_MISMATCH.equals(binding.getLoginStatus())) {
            fail(BrowserEnvironmentConstants.ERR_ENVIRONMENT_ACCOUNT_MISMATCH, "环境内登录账号与绑定账号不一致");
        }
        if (!BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(binding.getLoginStatus())) {
            fail(BrowserEnvironmentConstants.ERR_ENVIRONMENT_LOGIN_REQUIRED, "指纹浏览器环境账号未登录或需重新验证");
        }
        return binding;
    }

    private String resolveReportedStatus(BrowserEnvironmentAccount row,
                                         String incoming,
                                         BrowserEnvironmentLoginStatusRequest request) {
        if (!BrowserEnvironmentConstants.LOGIN_LOGGED_IN.equals(incoming)) {
            return incoming;
        }
        String actualId = trimToNull(request.actualPlatformAccountId());
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
        boolean nameMatches = StringUtils.hasText(row.getExpectedAccountName())
                && row.getExpectedAccountName().equals(actualName);
        if (idMatches || nameMatches) {
            return BrowserEnvironmentConstants.LOGIN_LOGGED_IN;
        }
        return BrowserEnvironmentConstants.LOGIN_MISMATCH;
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
                && !BrowserEnvironmentConstants.LOGIN_UNKNOWN.equals(to)) {
            fail(BrowserEnvironmentConstants.ERR_ENVIRONMENT_ACCOUNT_MISMATCH, "账号不一致状态必须先人工重置为 unknown");
        }
    }

    private BrowserEnvironmentAccountVO toAccountVO(BrowserEnvironmentAccount row) {
        return BrowserEnvironmentAccountVO.from(row, environmentMapper.selectById(row.getBrowserEnvironmentId()));
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

    private void fail(String code, String message) {
        throw new BizException(ERROR_CODE, message, 200, Map.of("code", code));
    }
}
