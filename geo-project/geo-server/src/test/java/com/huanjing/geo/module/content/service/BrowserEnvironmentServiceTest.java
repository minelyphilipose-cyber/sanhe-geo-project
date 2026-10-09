package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.BrowserEnvironmentConstants;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentCreateRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentAccountCreateRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentLoginStatusRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentUpdateRequest;
import com.huanjing.geo.module.content.entity.BrowserEnvironment;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentAccountMapper;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.extension.config.ExtensionProperties;
import com.huanjing.geo.module.extension.entity.ExtensionSession;
import com.huanjing.geo.module.extension.entity.LocalAgentRuntimeStatus;
import com.huanjing.geo.module.extension.entity.LocalAgentSession;
import com.huanjing.geo.module.extension.mapper.ExtensionSessionMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentRuntimeStatusMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentSessionMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class BrowserEnvironmentServiceTest {
    private BrowserEnvironmentAccountMapper environmentAccountMapper;
    private BrowserEnvironmentMapper environmentMapper;
    private SelfMediaAccountMapper selfMediaAccountMapper;
    private LocalAgentSessionMapper localAgentSessionMapper;
    private LocalAgentRuntimeStatusMapper localAgentRuntimeStatusMapper;
    private ExtensionSessionMapper extensionSessionMapper;
    private ExtensionProperties extensionProperties;
    private SelfMediaLoginVerificationService loginVerificationService;
    private BrowserEnvironmentService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BrowserEnvironment.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BrowserEnvironmentAccount.class);
        environmentMapper = mock(BrowserEnvironmentMapper.class);
        environmentAccountMapper = mock(BrowserEnvironmentAccountMapper.class);
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        localAgentSessionMapper = mock(LocalAgentSessionMapper.class);
        localAgentRuntimeStatusMapper = mock(LocalAgentRuntimeStatusMapper.class);
        extensionSessionMapper = mock(ExtensionSessionMapper.class);
        extensionProperties = new ExtensionProperties();
        loginVerificationService = mock(SelfMediaLoginVerificationService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser operator = new SysUser();
        operator.setId(99L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        service = new BrowserEnvironmentService(
                environmentMapper,
                environmentAccountMapper,
                selfMediaAccountMapper,
                localAgentSessionMapper,
                localAgentRuntimeStatusMapper,
                extensionSessionMapper,
                mock(BrandAccessService.class),
                currentUserService,
                extensionProperties,
                loginVerificationService,
                mock(SelfMediaLoginObservationService.class)
        );
    }

    @Test
    void reportLoginStatus_firstSuccessfulSeenIdentityLocksExpectedNameOnly() {
        when(environmentAccountMapper.selectById(30L)).thenReturn(binding(null, null, BrowserEnvironmentConstants.LOGIN_UNKNOWN));
        when(environmentAccountMapper.selectOne(any())).thenReturn(null);
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account());

        service.reportLoginStatus(30L, new BrowserEnvironmentLoginStatusRequest(
                "geo_b",
                10L,
                "toutiao",
                "1865234056392716",
                "阜阳全屋智能家居",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ));

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        BrowserEnvironmentAccount updated = captor.getValue();
        assertNull(updated.getExpectedPlatformAccountId());
        assertEquals("阜阳全屋智能家居", updated.getExpectedAccountName());
        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, updated.getLoginStatus());
    }

    @Test
    void reportLoginStatus_ignoresSyntheticActualPlatformAccountId() {
        when(environmentAccountMapper.selectById(30L)).thenReturn(binding(null, null, BrowserEnvironmentConstants.LOGIN_UNKNOWN, "zhihu"));
        when(environmentAccountMapper.selectOne(any())).thenReturn(null);
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account("jnhbdxh", "zhihu"));

        service.reportLoginStatus(30L, new BrowserEnvironmentLoginStatusRequest(
                "geo_b",
                10L,
                "zhihu",
                "geo-zhihu-990006013-5c1e1fe979ab4941",
                "jnhbdxh",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ));

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        BrowserEnvironmentAccount updated = captor.getValue();
        assertNull(updated.getExpectedPlatformAccountId());
        assertEquals("jnhbdxh", updated.getExpectedAccountName());
        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, updated.getLoginStatus());
    }

    @Test
    void passiveLoggedInReportIsOfferedToHealthFactRecorder() {
        BrowserEnvironmentAccount row = binding(null, "三和口腔", BrowserEnvironmentConstants.LOGIN_UNKNOWN);
        row.setLastErrorCode("MULTIPLE_IDENTITIES");
        row.setLastErrorMessage("读取到多个账号身份");
        SelfMediaAccount account = account("三和口腔");
        when(environmentAccountMapper.selectById(30L)).thenReturn(row);
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account);

        BrowserEnvironmentLoginStatusRequest request = new BrowserEnvironmentLoginStatusRequest(
                "geo_b", 10L, "toutiao", null, "三和口腔",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN, null, null);
        service.reportLoginStatusForExtension(30L, request, 99L);

        assertNull(row.getLastErrorCode());
        assertNull(row.getLastErrorMessage());
        verify(environmentAccountMapper).updateNullableLoginErrors(30L, null, null);
        verify(loginVerificationService).recordTrustedPassiveHealthReport(row, account, request);
    }

    @Test
    void legacyPassiveLoginRequiredPreservesExistingLoggedInBinding() {
        BrowserEnvironmentAccount row = binding(null, "三和口腔", BrowserEnvironmentConstants.LOGIN_LOGGED_IN);
        LocalDateTime lastVerifiedAt = LocalDateTime.now().minusDays(2);
        LocalDateTime lastLoginSeenAt = LocalDateTime.now().minusDays(2);
        row.setLastVerifiedAt(lastVerifiedAt);
        row.setLastLoginSeenAt(lastLoginSeenAt);
        SelfMediaAccount account = account("三和口腔");
        when(environmentAccountMapper.selectById(30L)).thenReturn(row);
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account);

        service.reportLoginStatusForExtension(30L, new BrowserEnvironmentLoginStatusRequest(
                "geo_b", 10L, "toutiao", null, null,
                BrowserEnvironmentConstants.LOGIN_REQUIRED, "IDENTITY_UNREADABLE", "selector miss"), 99L);

        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, row.getLoginStatus());
        assertEquals(lastVerifiedAt, row.getLastVerifiedAt());
        assertEquals(lastLoginSeenAt, row.getLastLoginSeenAt());
        assertEquals("PASSIVE_LOGIN_UNCONFIRMED", row.getLastErrorCode());
        assertTrue(row.getLastErrorMessage().contains("已保留原登录状态"));
        verify(environmentAccountMapper).updateById(row);
        verify(environmentAccountMapper).updateNullableLoginErrors(
                30L, "PASSIVE_LOGIN_UNCONFIRMED", row.getLastErrorMessage());
        verify(loginVerificationService, never()).recordTrustedPassiveHealthReport(any(), any(), any());
    }

    @Test
    void verificationTaskExplicitLoginPageCanDowngradeBinding() {
        BrowserEnvironmentAccount row = binding(null, "三和口腔", BrowserEnvironmentConstants.LOGIN_LOGGED_IN);
        when(environmentAccountMapper.selectById(30L)).thenReturn(row);
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account("三和口腔"));

        service.reportLoginStatusForExtension(30L, new BrowserEnvironmentLoginStatusRequest(
                "geo_b", 10L, "toutiao", null, null,
                BrowserEnvironmentConstants.LOGIN_REQUIRED, "LOGIN_PAGE_CONFIRMED", "平台明确要求登录",
                88L, false, "verification_task", "https://sso.toutiao.com/login", "0.2.5", "1"), 99L);

        assertEquals(BrowserEnvironmentConstants.LOGIN_REQUIRED, row.getLoginStatus());
        assertEquals("LOGIN_PAGE_CONFIRMED", row.getLastErrorCode());
        verify(loginVerificationService, never()).recordTrustedPassiveHealthReport(any(), any(), any());
        verify(loginVerificationService).completeFromLoginReport(eq(88L), eq(row), any(), any(),
                eq(BrowserEnvironmentConstants.LOGIN_REQUIRED));
    }

    @Test
    void clientClaimedAuthorityWithoutVerifiedLoginPageCannotDowngradeBinding() {
        BrowserEnvironmentAccount row = binding(null, "三和口腔", BrowserEnvironmentConstants.LOGIN_LOGGED_IN);
        when(environmentAccountMapper.selectById(30L)).thenReturn(row);
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account("三和口腔"));

        service.reportLoginStatusForExtension(30L, new BrowserEnvironmentLoginStatusRequest(
                "geo_b", 10L, "toutiao", null, null,
                BrowserEnvironmentConstants.LOGIN_REQUIRED, "LOGIN_PAGE_CONFIRMED", "客户端声称已退出",
                88L, true, "verification_task", "https://mp.toutiao.com/profile_v4/index",
                "0.2.5", "1"), 99L);

        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, row.getLoginStatus());
        assertEquals("PASSIVE_LOGIN_UNCONFIRMED", row.getLastErrorCode());
        verify(loginVerificationService).completeFromLoginReport(eq(88L), eq(row), any(), any(),
                eq(BrowserEnvironmentConstants.LOGIN_REQUIRED));
    }

    @Test
    void publishTaskLoginFailureUpdatesBindingAndHealthFact() {
        BrowserEnvironmentAccount row = binding(null, "三和口腔", BrowserEnvironmentConstants.LOGIN_LOGGED_IN);
        when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L)).thenReturn(row);

        service.recordPublishTaskLoginFailure(10L, "ACCOUNT_MISMATCH", "当前登录账号不一致");

        assertEquals(BrowserEnvironmentConstants.LOGIN_MISMATCH, row.getLoginStatus());
        assertEquals("ACCOUNT_MISMATCH", row.getLastErrorCode());
        assertEquals("当前登录账号不一致", row.getLastErrorMessage());
        verify(environmentAccountMapper).updateById(row);
        verify(loginVerificationService).recordPublishTaskLoginFailure(10L, "当前登录账号不一致");
    }

    @Test
    void reportLoginStatusByBrandAndPlatformUsesSelfMediaAccountIdWhenProvided() {
        BrowserEnvironmentAccount row = binding(null, "jnhbdxh", BrowserEnvironmentConstants.LOGIN_UNKNOWN, "zhihu");
        when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L)).thenReturn(row);
        when(environmentAccountMapper.selectById(30L)).thenReturn(row);
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account("jnhbdxh", "zhihu"));

        service.reportLoginStatusForExtensionByBrandAndPlatform(1L, new com.huanjing.geo.module.content.dto.BrowserEnvironmentBrandLoginStatusRequest(
                10L,
                "zhihu",
                null,
                "jnhbdxh",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ), 99L);

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, captor.getValue().getLoginStatus());
    }

    @Test
    void reportLoginStatusByBrandAndPlatformUsesEnvironmentKeyBeforeBrandWideIdentityMatching() {
        BrowserEnvironmentAccount target = binding(null, "jnhbdxh", BrowserEnvironmentConstants.LOGIN_UNKNOWN, "zhihu");
        when(environmentAccountMapper.selectActiveByEnvironmentKeyAndPlatform("geo_b", "zhihu"))
                .thenReturn(List.of(target));
        when(environmentAccountMapper.selectById(30L)).thenReturn(target);
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account("jnhbdxh", "zhihu"));

        service.reportLoginStatusForExtensionByBrandAndPlatform(1L,
                new com.huanjing.geo.module.content.dto.BrowserEnvironmentBrandLoginStatusRequest(
                        null,
                        "zhihu",
                        null,
                        "jnhbdxh",
                        BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                        null,
                        null,
                        null,
                        "geo_b"
                ), 99L);

        verify(environmentAccountMapper, never()).selectAllActiveByBrandIdAndPlatform(any(), any());
        verify(environmentAccountMapper).updateById(target);
    }

    @Test
    void expiredActiveVerificationFallsBackToTrustedPassiveSuccess() {
        BrowserEnvironmentAccount row = binding(null, "三和口腔", BrowserEnvironmentConstants.LOGIN_UNKNOWN);
        SelfMediaAccount account = account("三和口腔");
        BrowserEnvironmentLoginStatusRequest request = new BrowserEnvironmentLoginStatusRequest(
                "geo_b", 10L, "toutiao", null, "三和口腔",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN, null, null, 88L);
        when(environmentAccountMapper.selectById(30L)).thenReturn(row);
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account);
        when(loginVerificationService.completeFromLoginReport(
                88L, row, account, request, BrowserEnvironmentConstants.LOGIN_LOGGED_IN)).thenReturn(false);

        service.reportLoginStatusForExtension(30L, request, 99L);

        ArgumentCaptor<BrowserEnvironmentLoginStatusRequest> passiveRequest =
                ArgumentCaptor.forClass(BrowserEnvironmentLoginStatusRequest.class);
        verify(loginVerificationService).recordTrustedPassiveHealthReport(eq(row), eq(account), passiveRequest.capture());
        assertNull(passiveRequest.getValue().loginVerificationId());
    }

    @Test
    void reportLoginStatusByBrandAndPlatformResolvesTargetByDetectedAccountName() {
        BrowserEnvironmentAccount other = binding(null, "三合星链-小编", BrowserEnvironmentConstants.LOGIN_UNKNOWN, "zhihu");
        other.setId(31L);
        other.setSelfMediaAccountId(11L);
        BrowserEnvironmentAccount target = binding(null, null, BrowserEnvironmentConstants.LOGIN_UNKNOWN, "zhihu");
        target.setId(32L);
        target.setSelfMediaAccountId(12L);
        when(environmentAccountMapper.selectAllActiveByBrandIdAndPlatform(1L, "zhihu"))
                .thenReturn(List.of(other, target));
        when(environmentAccountMapper.selectById(32L)).thenReturn(target);
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(11L)).thenReturn(account("三合星链-小编", "zhihu"));
        when(selfMediaAccountMapper.selectById(12L)).thenReturn(account("jnhbdxh", "zhihu"));

        service.reportLoginStatusForExtensionByBrandAndPlatform(1L, new com.huanjing.geo.module.content.dto.BrowserEnvironmentBrandLoginStatusRequest(
                null,
                "zhihu",
                null,
                "jnhbdxh",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ), 99L);

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        BrowserEnvironmentAccount updated = captor.getValue();
        assertEquals(32L, updated.getId());
        assertEquals("jnhbdxh", updated.getExpectedAccountName());
        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, updated.getLoginStatus());
    }

    @Test
    void reportLoginStatusByBrandAndPlatformResolvesTargetByBaijiahaoAccountName() {
        BrowserEnvironmentAccount target = binding(null, "yqx2002528", BrowserEnvironmentConstants.LOGIN_UNKNOWN, "baijiahao");
        target.setId(32L);
        target.setSelfMediaAccountId(12L);
        when(environmentAccountMapper.selectAllActiveByBrandIdAndPlatform(1L, "baijiahao"))
                .thenReturn(List.of(target));
        when(environmentAccountMapper.selectById(32L)).thenReturn(target);
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(12L))
                .thenReturn(account("yqx2002528", "baijiahao", "1869569183682287"));

        service.reportLoginStatusForExtensionByBrandAndPlatform(1L, new com.huanjing.geo.module.content.dto.BrowserEnvironmentBrandLoginStatusRequest(
                null,
                "baijiahao",
                "1869569183682287",
                "yqx2002528",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ), 99L);

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        BrowserEnvironmentAccount updated = captor.getValue();
        assertNull(updated.getExpectedPlatformAccountId());
        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, updated.getLoginStatus());
    }

    @Test
    void reportLoginStatusByBrandAndPlatformResolvesLegacyDouyinImageTextBinding() {
        BrowserEnvironmentAccount target = binding(null, "王恒", BrowserEnvironmentConstants.LOGIN_UNKNOWN, "douyin_image_text");
        target.setId(32L);
        target.setSelfMediaAccountId(12L);
        when(environmentAccountMapper.selectAllActiveByBrandIdAndPlatform(1L, "douyin"))
                .thenReturn(List.of());
        when(environmentAccountMapper.selectAllActiveByBrandIdAndPlatform(1L, "douyin_image_text"))
                .thenReturn(List.of(target));
        when(environmentAccountMapper.selectById(32L)).thenReturn(target);
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(12L))
                .thenReturn(account("王恒", "douyin_image_text", "1529218551"));

        service.reportLoginStatusForExtensionByBrandAndPlatform(1L, new com.huanjing.geo.module.content.dto.BrowserEnvironmentBrandLoginStatusRequest(
                null,
                "douyin",
                "1529218551",
                "王恒",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ), 99L);

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        BrowserEnvironmentAccount updated = captor.getValue();
        assertNull(updated.getExpectedPlatformAccountId());
        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, updated.getLoginStatus());
    }

    @Test
    void reportLoginStatus_mismatchCanRecoverToLoggedInWhenIdentityMatchesExpected() {
        when(environmentAccountMapper.selectById(30L)).thenReturn(binding("random-system-id", "name", BrowserEnvironmentConstants.LOGIN_MISMATCH));
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account("name", "toutiao", "another-random-system-id"));

        service.reportLoginStatus(30L, new BrowserEnvironmentLoginStatusRequest(
                "geo_b",
                10L,
                "toutiao",
                "real-platform-id",
                "name",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ));

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, captor.getValue().getLoginStatus());
        assertNull(captor.getValue().getLastErrorCode());
        assertNull(captor.getValue().getLastErrorMessage());
    }

    @Test
    void reportLoginStatus_doesNotIgnoreMeaningfulAccountNameWhitespace() {
        when(environmentAccountMapper.selectById(30L)).thenReturn(binding(null, "静姐建乡墅 家",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN, "douyin"));
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account("静姐建乡墅 家", "douyin"));

        service.reportLoginStatus(30L, new BrowserEnvironmentLoginStatusRequest(
                "geo_b",
                10L,
                "douyin",
                null,
                "静姐建乡墅家",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ));

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        assertEquals(BrowserEnvironmentConstants.LOGIN_MISMATCH, captor.getValue().getLoginStatus());
    }

    @Test
    void reportLoginStatus_matchesPlatformPrefixedDisplayName() {
        when(environmentAccountMapper.selectById(30L)).thenReturn(binding(null, "知乎 / jnhbdxh", BrowserEnvironmentConstants.LOGIN_MISMATCH, "zhihu"));
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account("jnhbdxh", "zhihu"));

        service.reportLoginStatus(30L, new BrowserEnvironmentLoginStatusRequest(
                "geo_b",
                10L,
                "zhihu",
                null,
                "jnhbdxh",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ));

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, captor.getValue().getLoginStatus());
    }

    @Test
    void reportLoginStatus_matchesDouyinPrefixedDisplayName() {
        when(environmentAccountMapper.selectById(30L)).thenReturn(binding(null, "王恒", BrowserEnvironmentConstants.LOGIN_MISMATCH, "douyin"));
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account("王恒", "douyin"));

        service.reportLoginStatus(30L, new BrowserEnvironmentLoginStatusRequest(
                "geo_b",
                10L,
                "douyin",
                null,
                "抖音图文 / 王恒",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ));

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, captor.getValue().getLoginStatus());
    }

    @Test
    void reportLoginStatus_backfillsReadableAccountNameWhenSyntheticPlatformIdCannotBeRead() {
        when(environmentAccountMapper.selectById(30L)).thenReturn(binding("geo-zhihu-990006013-5c1e1fe979ab4941", null,
                BrowserEnvironmentConstants.LOGIN_MISMATCH, "zhihu"));
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account("jnhbdxh", "zhihu"));
        when(environmentAccountMapper.selectOne(any())).thenReturn(null);

        service.reportLoginStatus(30L, new BrowserEnvironmentLoginStatusRequest(
                "geo_b",
                10L,
                "zhihu",
                null,
                "jnhbdxh",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ));

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        BrowserEnvironmentAccount updated = captor.getValue();
        assertEquals("jnhbdxh", updated.getExpectedAccountName());
        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, updated.getLoginStatus());
    }

    @Test
    void reportLoginStatus_mismatchReportIsIdempotentWhenIdentityStillDoesNotMatchExpected() {
        when(environmentAccountMapper.selectById(30L)).thenReturn(binding("expected", "name", BrowserEnvironmentConstants.LOGIN_MISMATCH));
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account());

        service.reportLoginStatus(30L, new BrowserEnvironmentLoginStatusRequest(
                "geo_b",
                10L,
                "toutiao",
                "other",
                "other-name",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ));

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        assertEquals(BrowserEnvironmentConstants.LOGIN_MISMATCH, captor.getValue().getLoginStatus());
    }

    @Test
    void resetLoginIdentityClearsExpectedIdentityAndMismatchState() {
        BrowserEnvironmentAccount row = binding("expected", "三合星链-小编", BrowserEnvironmentConstants.LOGIN_MISMATCH);
        row.setLastVerifiedAt(LocalDateTime.now().minusHours(1));
        row.setLastLoginSeenAt(LocalDateTime.now().minusHours(2));
        row.setLastErrorCode("ENVIRONMENT_ACCOUNT_MISMATCH");
        row.setLastErrorMessage("账号不一致");
        when(environmentAccountMapper.selectById(30L)).thenReturn(row);
        when(environmentMapper.selectById(20L)).thenReturn(environment());

        var response = service.resetLoginIdentity(30L);

        verify(environmentAccountMapper).update(any(), any());
        assertNull(response.expectedPlatformAccountId());
        assertNull(response.expectedAccountName());
        assertNull(response.lastVerifiedAt());
        assertNull(response.lastLoginSeenAt());
        assertNull(response.lastErrorCode());
        assertNull(response.lastErrorMessage());
        assertEquals(BrowserEnvironmentConstants.LOGIN_UNKNOWN, response.loginStatus());
    }

    @Test
    void validateForTaskCreation_backfillsMissingExpectedIdentityFromSelfMediaAccount() {
        when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L))
                .thenReturn(binding(null, null, BrowserEnvironmentConstants.LOGIN_LOGGED_IN));
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(environmentAccountMapper.selectOne(any())).thenReturn(null);

        BrowserEnvironmentAccount response = service.validateForTaskCreation(account("三合星链-小编"));

        assertEquals("三合星链-小编", response.getExpectedAccountName());
        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        assertEquals("三合星链-小编", captor.getValue().getExpectedAccountName());
    }

    @Test
    void validateForTaskCreation_backfillsMissingExpectedNameWhenPlatformIdAlreadyExists() {
        when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L))
                .thenReturn(binding("geo-zhihu-990006013-5c1e1fe979ab4941", null,
                        BrowserEnvironmentConstants.LOGIN_LOGGED_IN, "zhihu"));
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(environmentAccountMapper.selectOne(any())).thenReturn(null);

        BrowserEnvironmentAccount response = service.validateForTaskCreation(account("jnhbdxh", "zhihu"));

        assertEquals("jnhbdxh", response.getExpectedAccountName());
        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        assertEquals("jnhbdxh", captor.getValue().getExpectedAccountName());
    }

    @Test
    void validateForTaskCreation_rejectsMissingExpectedIdentityWhenAccountHasNoIdentity() {
        when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L))
                .thenReturn(binding(null, null, BrowserEnvironmentConstants.LOGIN_LOGGED_IN));
        when(environmentMapper.selectById(20L)).thenReturn(environment());

        assertThrows(BizException.class, () -> service.validateForTaskCreation(account()));
    }

    @Test
    void validateForTaskCreation_strictModeAllowsUnknownLoginStatus() {
        when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L))
                .thenReturn(binding(null, "三合星链-小编", BrowserEnvironmentConstants.LOGIN_UNKNOWN));
        when(environmentMapper.selectById(20L)).thenReturn(environment());

        BrowserEnvironmentAccount response = service.validateForTaskCreation(account("三合星链-小编"));

        assertEquals(BrowserEnvironmentConstants.LOGIN_UNKNOWN, response.getLoginStatus());
    }

    @Test
    void validateForTaskCreation_strictModeTreatsMissingLegacyStatusAsUnknown() {
        BrowserEnvironmentAccount binding = binding(null, "三合星链-小编",
                BrowserEnvironmentConstants.LOGIN_UNKNOWN);
        binding.setLoginStatus(null);
        when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L)).thenReturn(binding);
        when(environmentMapper.selectById(20L)).thenReturn(environment());

        BrowserEnvironmentAccount response = service.validateForTaskCreation(account("三合星链-小编"));

        assertSame(binding, response);
    }

    @Test
    void validateForTaskCreation_relaxedModeAllowsUnknownLoginStatus() {
        when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L))
                .thenReturn(binding(null, "三合星链-小编", BrowserEnvironmentConstants.LOGIN_UNKNOWN));
        when(environmentMapper.selectById(20L)).thenReturn(environment());

        BrowserEnvironmentAccount response = service.validateForTaskCreation(account("三合星链-小编"), false);

        assertEquals(BrowserEnvironmentConstants.LOGIN_UNKNOWN, response.getLoginStatus());
    }

    @Test
    void validateForTaskCreation_strictModeRejectsMismatchLoginStatus() {
        when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L))
                .thenReturn(binding(null, "三合星链-小编", BrowserEnvironmentConstants.LOGIN_MISMATCH));
        when(environmentMapper.selectById(20L)).thenReturn(environment());

        BizException ex = assertThrows(BizException.class,
                () -> service.validateForTaskCreation(account("三合星链-小编")));

        assertEquals(BrowserEnvironmentConstants.ERR_ENVIRONMENT_ACCOUNT_MISMATCH,
                ((java.util.Map<?, ?>) ex.getData()).get("code"));
    }

    @Test
    void validateForTaskCreation_relaxedModeAllowsMismatchLoginStatus() {
        when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L))
                .thenReturn(binding(null, "三合星链-小编", BrowserEnvironmentConstants.LOGIN_MISMATCH));
        when(environmentMapper.selectById(20L)).thenReturn(environment());

        BrowserEnvironmentAccount response = service.validateForTaskCreation(account("三合星链-小编"), false);

        assertEquals(BrowserEnvironmentConstants.LOGIN_MISMATCH, response.getLoginStatus());
    }

    @Test
    void validateForTaskCreation_strictModeRejectsOtherExplicitLoginFailures() {
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        java.util.Map<String, String> expectedCodes = java.util.Map.of(
                BrowserEnvironmentConstants.LOGIN_REQUIRED,
                BrowserEnvironmentConstants.ERR_ENVIRONMENT_LOGIN_REQUIRED,
                BrowserEnvironmentConstants.LOGIN_EXPIRED,
                BrowserEnvironmentConstants.ERR_ENVIRONMENT_LOGIN_EXPIRED,
                BrowserEnvironmentConstants.LOGIN_ERROR,
                BrowserEnvironmentConstants.ERR_ENVIRONMENT_LOGIN_ERROR
        );
        for (var entry : expectedCodes.entrySet()) {
            when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L))
                    .thenReturn(binding(null, "三合星链-小编", entry.getKey()));

            BizException ex = assertThrows(BizException.class,
                    () -> service.validateForTaskCreation(account("三合星链-小编")));

            assertEquals(entry.getValue(), ((java.util.Map<?, ?>) ex.getData()).get("code"));
        }
    }

    @Test
    void validateForTaskCreation_relaxedModeAllowsAllExplicitLoginFailuresForScheduling() {
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        for (String status : List.of(
                BrowserEnvironmentConstants.LOGIN_REQUIRED,
                BrowserEnvironmentConstants.LOGIN_EXPIRED,
                BrowserEnvironmentConstants.LOGIN_MISMATCH,
                BrowserEnvironmentConstants.LOGIN_ERROR)) {
            when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L))
                    .thenReturn(binding(null, "三合星链-小编", status));

            BrowserEnvironmentAccount response =
                    service.validateForTaskCreation(account("三合星链-小编"), false);

            assertEquals(status, response.getLoginStatus());
        }
    }

    @Test
    void reportLoginStatus_firstSuccessfulSeenIdentityRejectsAlreadyClaimedIdentity() {
        when(environmentAccountMapper.selectById(30L)).thenReturn(binding(null, null, BrowserEnvironmentConstants.LOGIN_UNKNOWN));
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account());
        when(environmentAccountMapper.selectOne(any())).thenReturn(binding("1865234056392716", "other", BrowserEnvironmentConstants.LOGIN_LOGGED_IN));

        assertThrows(BizException.class, () -> service.reportLoginStatus(30L, new BrowserEnvironmentLoginStatusRequest(
                "geo_b",
                10L,
                "toutiao",
                "1865234056392716",
                "阜阳全屋智能家居",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        )));
    }

    @Test
    void reportLoginStatus_allowsMissingSelfMediaAccountIdBecausePathIdIdentifiesBinding() {
        when(environmentAccountMapper.selectById(30L)).thenReturn(binding(null, null, BrowserEnvironmentConstants.LOGIN_UNKNOWN));
        when(environmentAccountMapper.selectOne(any())).thenReturn(null);
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account());

        service.reportLoginStatus(30L, new BrowserEnvironmentLoginStatusRequest(
                "geo_b",
                null,
                "toutiao",
                "1865234056392716",
                "阜阳全屋智能家居",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ));

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, captor.getValue().getLoginStatus());
    }

    @Test
    void reportLoginStatusByEnvironmentAndPlatformRoutesToSingleBinding() {
        when(environmentAccountMapper.selectActiveByEnvironmentKeyAndPlatform("geo_b", "toutiao"))
                .thenReturn(List.of(binding(null, null, BrowserEnvironmentConstants.LOGIN_UNKNOWN)));
        when(environmentAccountMapper.selectById(30L)).thenReturn(binding(null, null, BrowserEnvironmentConstants.LOGIN_UNKNOWN));
        when(environmentAccountMapper.selectOne(any())).thenReturn(null);
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account());

        service.reportLoginStatusForExtensionByEnvironmentAndPlatform(new BrowserEnvironmentLoginStatusRequest(
                "geo_b",
                null,
                "toutiao",
                "1865234056392716",
                "阜阳全屋智能家居",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ), 99L);

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, captor.getValue().getLoginStatus());
    }

    @Test
    void reportLoginStatusByEnvironmentAndPlatformResolvesLegacyDouyinImageTextBinding() {
        BrowserEnvironmentAccount target = binding(null, "王恒", BrowserEnvironmentConstants.LOGIN_UNKNOWN, "douyin_image_text");
        target.setSelfMediaAccountId(12L);
        when(environmentAccountMapper.selectActiveByEnvironmentKeyAndPlatform("geo_huawei", "douyin"))
                .thenReturn(List.of());
        when(environmentAccountMapper.selectActiveByEnvironmentKeyAndPlatform("geo_huawei", "douyin_image_text"))
                .thenReturn(List.of(target));
        when(environmentAccountMapper.selectById(30L)).thenReturn(target);
        BrowserEnvironment environment = environment();
        environment.setEnvironmentKey("brand_990006013_adspower");
        environment.setName("geo-huawei");
        when(environmentMapper.selectById(20L)).thenReturn(environment);
        when(selfMediaAccountMapper.selectById(12L))
                .thenReturn(account("王恒", "douyin_image_text", "1529218551"));

        service.reportLoginStatusForExtensionByEnvironmentAndPlatform(new BrowserEnvironmentLoginStatusRequest(
                "geo_huawei",
                null,
                "douyin",
                "1529218551",
                "王恒",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ), 99L);

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, captor.getValue().getLoginStatus());
    }

    @Test
    void reportLoginStatusByEnvironmentAndPlatformRejectsAmbiguousBindings() {
        when(environmentAccountMapper.selectActiveByEnvironmentKeyAndPlatform("geo_b", "toutiao"))
                .thenReturn(List.of(
                        binding(null, null, BrowserEnvironmentConstants.LOGIN_UNKNOWN),
                        binding(null, null, BrowserEnvironmentConstants.LOGIN_UNKNOWN)
                ));

        assertThrows(BizException.class, () -> service.reportLoginStatusForExtensionByEnvironmentAndPlatform(
                new BrowserEnvironmentLoginStatusRequest(
                        "geo_b",
                        null,
                        "toutiao",
                        "1865234056392716",
                        "阜阳全屋智能家居",
                        BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                        null,
                        null
                ),
                99L
        ));
    }

    @Test
    void extensionRuntimeConfigSelectsSingleMatchingBinding() {
        when(environmentAccountMapper.selectActiveRuntimeConfigsByBrandId(1L))
                .thenReturn(List.of(binding("expected", "name", BrowserEnvironmentConstants.LOGIN_LOGGED_IN)));
        when(environmentMapper.selectById(20L)).thenReturn(environment());

        var response = service.extensionRuntimeConfig(1L, 99L, "geo_b", "toutiao");

        assertEquals("selected", response.selectionStatus());
        assertEquals("http://127.0.0.1:17891", response.helperBase());
        assertEquals(30L, response.selected().browserEnvironmentAccountId());
        assertEquals("geo_b", response.selected().environmentKey());
        assertEquals("toutiao", response.selected().platform());
    }

    @Test
    void extensionRuntimeConfigDoesNotGuessWhenMultipleBindingsMatch() {
        BrowserEnvironmentAccount left = binding("left", "left-name", BrowserEnvironmentConstants.LOGIN_LOGGED_IN);
        BrowserEnvironmentAccount right = binding("right", "right-name", BrowserEnvironmentConstants.LOGIN_UNKNOWN);
        right.setId(31L);
        when(environmentAccountMapper.selectActiveRuntimeConfigsByBrandId(1L)).thenReturn(List.of(left, right));
        when(environmentMapper.selectById(20L)).thenReturn(environment());

        var response = service.extensionRuntimeConfig(1L, 99L, "geo_b", "toutiao");

        assertEquals("ambiguous", response.selectionStatus());
        assertNull(response.selected());
        assertEquals(2, response.candidates().size());
    }

    @Test
    void deleteEnvironmentSoftDeletesWithDeletedAtInsteadOfPhysicalDelete() {
        when(environmentMapper.selectById(20L)).thenReturn(environment());

        service.deleteEnvironment(20L);

        verify(environmentMapper).update(any(), any());
        verify(environmentMapper).disableLocalAgentBinding(eq(20L), any(LocalDateTime.class));
    }

    @Test
    void createEnvironment_duplicateProviderProfileReturnsBusinessMessage() {
        doThrow(new DuplicateKeyException("duplicate")).when(environmentMapper).insert(any(BrowserEnvironment.class));

        BizException ex = assertThrows(BizException.class, () -> service.createEnvironment(new BrowserEnvironmentCreateRequest(
                1L,
                BrowserEnvironmentConstants.PROVIDER_ADSPOWER,
                "geo_b",
                "profile-1",
                "环境",
                null
        )));

        assertTrue(ex.getMessage().contains("AdsPower 浏览器编号或环境代号已被其他启用环境使用"));
    }

    @Test
    void createEnvironmentReusesExistingIdentityInsteadOfChangingDatabaseId() {
        BrowserEnvironment existing = environment();
        existing.setDeletedAt(LocalDateTime.now().minusMinutes(1));
        when(environmentMapper.selectOldestByIdentityIncludingDeleted(1L,
                BrowserEnvironmentConstants.PROVIDER_ADSPOWER, "geo_b", "profile-1"))
                .thenReturn(existing);

        var response = service.createEnvironment(new BrowserEnvironmentCreateRequest(
                1L,
                BrowserEnvironmentConstants.PROVIDER_ADSPOWER,
                "geo_b",
                "profile-1",
                "环境",
                null
        ));

        assertEquals(20L, response.id());
        assertNull(existing.getDeletedAt());
        assertEquals(BrowserEnvironmentConstants.ENV_STATUS_ACTIVE, existing.getStatus());
        verify(environmentMapper).restoreDeletedById(20L);
        verify(environmentMapper).updateById(existing);
        verify(environmentMapper, times(0)).insert(any(BrowserEnvironment.class));
    }

    @Test
    void createEnvironmentUpdatesExistingBrandEnvironmentInsteadOfCreatingSecondEnvironment() {
        BrowserEnvironment existing = environment();
        BrowserEnvironmentAccount binding = binding(null, "现有账号", BrowserEnvironmentConstants.LOGIN_LOGGED_IN);
        when(environmentMapper.selectActiveByBrandId(1L)).thenReturn(existing);
        when(environmentAccountMapper.selectList(any())).thenReturn(List.of(binding));

        var response = service.createEnvironment(new BrowserEnvironmentCreateRequest(
                1L,
                BrowserEnvironmentConstants.PROVIDER_ADSPOWER,
                "geo_new",
                "profile-new",
                "新环境名称",
                null
        ));

        assertEquals(20L, response.id());
        assertEquals("geo_new", existing.getEnvironmentKey());
        assertEquals("profile-new", existing.getProviderProfileId());
        assertEquals("新环境名称", existing.getName());
        verify(environmentMapper).updateById(existing);
        verify(environmentMapper, never()).insert(any(BrowserEnvironment.class));
        verify(environmentMapper, never()).selectOldestByIdentityIncludingDeleted(any(), any(), any(), any());
        verify(environmentAccountMapper).update(eq(null), any());
        verify(loginVerificationService).invalidateAccountHealth(
                eq(10L),
                eq(SelfMediaLoginVerificationService.METHOD_BINDING_INVALIDATED),
                any());
    }

    @Test
    void createEnvironmentKeepsLoginFactsWhenRuntimeIdentityIsUnchanged() {
        BrowserEnvironment existing = environment();
        when(environmentMapper.selectActiveByBrandId(1L)).thenReturn(existing);

        service.createEnvironment(new BrowserEnvironmentCreateRequest(
                1L,
                BrowserEnvironmentConstants.PROVIDER_ADSPOWER,
                existing.getEnvironmentKey(),
                existing.getProviderProfileId(),
                "仅修改显示名称",
                null
        ));

        verify(environmentMapper).updateById(existing);
        verify(environmentAccountMapper, never()).selectList(any());
        verify(loginVerificationService, never()).invalidateAccountHealth(any(), any(), any());
    }

    @Test
    void createEnvironmentImportedFromLocalHelperBindsStableMachineOwnership() {
        BrowserEnvironment existing = environment();
        LocalAgentSession session = localAgentSession();
        session.setStatus("active");
        LocalAgentRuntimeStatus runtime = new LocalAgentRuntimeStatus();
        runtime.setSessionId(40L);
        runtime.setMachineId("machine-a");
        runtime.setActiveProfile("prod");
        runtime.setLastSeenAt(LocalDateTime.now().minusSeconds(5));
        when(environmentMapper.selectOldestByIdentityIncludingDeleted(1L,
                BrowserEnvironmentConstants.PROVIDER_ADSPOWER, "geo_b", "profile-1"))
                .thenReturn(existing);
        when(localAgentSessionMapper.selectById(40L)).thenReturn(session);
        when(localAgentRuntimeStatusMapper.selectLatestBySessionId(40L)).thenReturn(runtime);

        service.createEnvironment(new BrowserEnvironmentCreateRequest(
                1L,
                BrowserEnvironmentConstants.PROVIDER_ADSPOWER,
                "geo_b",
                "profile-1",
                "环境",
                40L
        ));

        verify(environmentMapper).upsertLocalAgentBinding(
                eq(20L), eq("machine-a"), eq("prod"), eq(40L), eq(99L),
                eq(runtime.getLastSeenAt()), any(LocalDateTime.class));
    }

    @Test
    void createEnvironmentAccountReusesDeletedBindingWithSameIdentityInsteadOfChangingDatabaseId() {
        BrowserEnvironmentAccount existing = binding(null, "阜阳全屋智能家居", BrowserEnvironmentConstants.LOGIN_UNKNOWN);
        existing.setDeletedAt(LocalDateTime.now().minusMinutes(1));
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account());
        when(environmentAccountMapper.selectOldestBySelfMediaAccountIdIncludingDeleted(10L)).thenReturn(existing);
        when(environmentAccountMapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<BrowserEnvironmentAccount> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            // Restoring the row makes its unchanged identity visible to the uniqueness lookup.
            return sql.contains("id <>") && query.getParamNameValuePairs().containsValue(existing.getId())
                    ? null : existing;
        });

        var response = service.createEnvironmentAccount(new BrowserEnvironmentAccountCreateRequest(
                20L, 10L, null, "阜阳全屋智能家居"));

        assertEquals(30L, response.id());
        assertNull(existing.getDeletedAt());
        assertEquals("阜阳全屋智能家居", existing.getExpectedAccountName());
        verify(environmentAccountMapper).restoreDeletedById(30L);
        verify(environmentAccountMapper).updateById(existing);
        verify(environmentAccountMapper, times(0)).insert(any(BrowserEnvironmentAccount.class));
    }

    @Test
    void updateEnvironment_duplicateProviderProfileReturnsBusinessMessage() {
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        doThrow(new DuplicateKeyException("duplicate")).when(environmentMapper).updateById(any(BrowserEnvironment.class));

        BizException ex = assertThrows(BizException.class, () -> service.updateEnvironment(20L, new BrowserEnvironmentUpdateRequest(
                "profile-2",
                "环境",
                BrowserEnvironmentConstants.ENV_STATUS_ACTIVE,
                null,
                null,
                null
        )));

        assertTrue(ex.getMessage().contains("AdsPower 浏览器编号或环境代号已被其他启用环境使用"));
    }

    @Test
    void selfMediaAutomationReadinessUsesUtcForExtensionExpiryAndHeartbeat() {
        TimeZone originalZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            ExtensionSession session = extensionSession();
            session.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
            session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(20));
            when(localAgentSessionMapper.selectActiveByOperatorId(eq(99L), any(LocalDateTime.class)))
                    .thenReturn(List.of(localAgentSession()));
            when(extensionSessionMapper.selectActiveByBrandId(1L)).thenReturn(List.of(session));
            when(environmentMapper.selectList(any())).thenReturn(List.of(environment()));
            when(selfMediaAccountMapper.selectList(any())).thenReturn(List.of());

            var response = service.selfMediaAutomationReadiness(1L);

            assertTrue(response.localAgent().online());
            assertTrue(response.extensionBinding().bound());
            assertTrue(response.extensionBinding().online());
            assertTrue(response.issues().stream().noneMatch(issue ->
                    "EXTENSION_NOT_BOUND".equals(issue.code()) || "EXTENSION_OFFLINE".equals(issue.code())));
        } finally {
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void selfMediaAutomationReadinessKeepsStaleExtensionBoundAndOffersOpenWithoutRebinding() {
        ExtensionSession session = extensionSession();
        session.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(11));
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        when(localAgentSessionMapper.selectActiveByOperatorId(eq(99L), any(LocalDateTime.class)))
                .thenReturn(List.of(localAgentSession()));
        when(extensionSessionMapper.selectActiveByBrandId(1L)).thenReturn(List.of(session));
        when(environmentMapper.selectList(any())).thenReturn(List.of(environment()));
        when(selfMediaAccountMapper.selectList(any())).thenReturn(List.of());

        var response = service.selfMediaAutomationReadiness(1L);

        assertTrue(response.extensionBinding().bound());
        assertTrue(!response.extensionBinding().online());
        assertTrue(response.issues().stream().anyMatch(issue ->
                "EXTENSION_OFFLINE".equals(issue.code()) && "OPEN_ADSPOWER_ENVIRONMENT".equals(issue.actionKey())));
    }

    @Test
    void selfMediaAutomationReadinessRejectsExpiredUtcExtension() {
        ExtensionSession session = extensionSession();
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(localAgentSessionMapper.selectActiveByOperatorId(eq(99L), any(LocalDateTime.class)))
                .thenReturn(List.of(localAgentSession()));
        when(extensionSessionMapper.selectActiveByBrandId(1L)).thenReturn(List.of(session));
        when(environmentMapper.selectList(any())).thenReturn(List.of(environment()));
        when(selfMediaAccountMapper.selectList(any())).thenReturn(List.of());

        var response = service.selfMediaAutomationReadiness(1L);

        assertTrue(!response.extensionBinding().bound());
        assertTrue(response.issues().stream().anyMatch(issue -> "EXTENSION_NOT_BOUND".equals(issue.code())));
    }

    @Test
    void selfMediaAutomationReadinessReturnsReadyWhenAllRuntimeLinksAreHealthy() {
        when(localAgentSessionMapper.selectActiveByOperatorId(eq(99L), any(LocalDateTime.class)))
                .thenReturn(List.of(localAgentSession()));
        when(extensionSessionMapper.selectActiveByBrandId(1L)).thenReturn(List.of(extensionSession()));
        when(environmentMapper.selectList(any())).thenReturn(List.of(environment()));
        when(selfMediaAccountMapper.selectList(any())).thenReturn(List.of(account("阜阳全屋智能家居", "toutiao")));
        when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L))
                .thenReturn(binding("1865234056392716", "阜阳全屋智能家居", BrowserEnvironmentConstants.LOGIN_LOGGED_IN));

        var response = service.selfMediaAutomationReadiness(1L);

        assertEquals("ready", response.status());
        assertTrue(response.ready());
        assertTrue(response.localAgent().online());
        assertTrue(response.browserEnvironment().active());
        assertTrue(response.extensionBinding().online());
        assertNull(response.extensionBinding().expectedVersion());
        assertTrue(response.extensionBinding().versionSupported());
        assertEquals(1, response.accounts().size());
        assertTrue(response.accounts().get(0).loginReady());
        assertTrue(response.issues().isEmpty());
    }

    @Test
    void selfMediaAutomationReadinessReportsDisabledEnvironmentSeparatelyFromMissingConfig() {
        BrowserEnvironment disabled = environment();
        disabled.setStatus(BrowserEnvironmentConstants.ENV_STATUS_DISABLED);
        when(localAgentSessionMapper.selectActiveByOperatorId(eq(99L), any(LocalDateTime.class)))
                .thenReturn(List.of(localAgentSession()));
        when(extensionSessionMapper.selectActiveByBrandId(1L)).thenReturn(List.of(extensionSession()));
        when(environmentMapper.selectList(any())).thenReturn(List.of(disabled));
        when(selfMediaAccountMapper.selectList(any())).thenReturn(List.of());

        var response = service.selfMediaAutomationReadiness(1L);

        assertEquals("blocked", response.status());
        assertTrue(response.browserEnvironment().configured());
        assertTrue(response.issues().stream().anyMatch(issue -> "ADSPOWER_ENVIRONMENT_DISABLED".equals(issue.code())));
    }

    @Test
    void selfMediaAutomationReadinessWarnsWhenExtensionVersionIsOutdated() {
        extensionProperties.getEnv().setExpectedVersion("0.1.0");
        ExtensionSession outdated = extensionSession();
        outdated.setExtensionVersion("0.0.9");
        when(localAgentSessionMapper.selectActiveByOperatorId(eq(99L), any(LocalDateTime.class)))
                .thenReturn(List.of(localAgentSession()));
        when(extensionSessionMapper.selectActiveByBrandId(1L)).thenReturn(List.of(outdated));
        when(environmentMapper.selectList(any())).thenReturn(List.of(environment()));
        when(selfMediaAccountMapper.selectList(any())).thenReturn(List.of(account("阜阳全屋智能家居", "toutiao")));
        when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L))
                .thenReturn(binding("1865234056392716", "阜阳全屋智能家居", BrowserEnvironmentConstants.LOGIN_LOGGED_IN));

        var response = service.selfMediaAutomationReadiness(1L);

        assertEquals("warning", response.status());
        assertTrue(response.ready());
        assertEquals("0.1.0", response.extensionBinding().expectedVersion());
        assertTrue(!response.extensionBinding().versionSupported());
        assertTrue(response.issues().stream().anyMatch(issue -> "EXTENSION_VERSION_OUTDATED".equals(issue.code())));
    }

    @Test
    void selfMediaAutomationReadinessPrefersSessionMatchingDefaultEnvironment() {
        ExtensionSession otherEnvironment = extensionSession();
        otherEnvironment.setId(51L);
        otherEnvironment.setEnvironmentKey("other_env");
        otherEnvironment.setProviderProfileId("other-profile");
        otherEnvironment.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(10));
        ExtensionSession matching = extensionSession();
        matching.setId(52L);
        matching.setEnvironmentKey("geo_b");
        matching.setProviderProfileId("profile-1");
        matching.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2));
        when(localAgentSessionMapper.selectActiveByOperatorId(eq(99L), any(LocalDateTime.class)))
                .thenReturn(List.of(localAgentSession()));
        when(extensionSessionMapper.selectActiveByBrandId(1L)).thenReturn(List.of(otherEnvironment, matching));
        when(environmentMapper.selectList(any())).thenReturn(List.of(environment()));
        when(selfMediaAccountMapper.selectList(any())).thenReturn(List.of(account("阜阳全屋智能家居", "toutiao")));
        when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L))
                .thenReturn(binding("1865234056392716", "阜阳全屋智能家居", BrowserEnvironmentConstants.LOGIN_LOGGED_IN));

        var response = service.selfMediaAutomationReadiness(1L);

        assertEquals(52L, response.extensionBinding().sessionId());
        assertEquals("geo_b", response.extensionBinding().environmentKey());
        assertEquals("profile-1", response.extensionBinding().providerProfileId());
    }

    @Test
    void selfMediaAutomationReadinessIssueProvidesActionKey() {
        when(localAgentSessionMapper.selectActiveByOperatorId(eq(99L), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(extensionSessionMapper.selectActiveByBrandId(1L)).thenReturn(List.of());
        when(environmentMapper.selectList(any())).thenReturn(List.of());
        when(selfMediaAccountMapper.selectList(any())).thenReturn(List.of());

        var response = service.selfMediaAutomationReadiness(1L);

        assertTrue(response.issues().stream()
                .anyMatch(issue -> "LOCAL_AGENT_NOT_BOUND".equals(issue.code())
                        && "OPEN_LOCAL_HELPER_SETUP".equals(issue.actionKey())));
        assertTrue(response.issues().stream()
                .anyMatch(issue -> "ADSPOWER_ENVIRONMENT_NOT_CONFIGURED".equals(issue.code())
                        && "IMPORT_ADSPOWER_ENVIRONMENT".equals(issue.actionKey())));
    }

    private BrowserEnvironment environment() {
        BrowserEnvironment row = new BrowserEnvironment();
        row.setId(20L);
        row.setBrandId(1L);
        row.setEnvironmentKey("geo_b");
        row.setProvider(BrowserEnvironmentConstants.PROVIDER_ADSPOWER);
        row.setProviderProfileId("profile-1");
        row.setStatus(BrowserEnvironmentConstants.ENV_STATUS_ACTIVE);
        return row;
    }

    private BrowserEnvironmentAccount binding(String expectedId, String expectedName, String status) {
        return binding(expectedId, expectedName, status, "toutiao");
    }

    private BrowserEnvironmentAccount binding(String expectedId, String expectedName, String status, String platform) {
        BrowserEnvironmentAccount row = new BrowserEnvironmentAccount();
        row.setId(30L);
        row.setBrandId(1L);
        row.setBrowserEnvironmentId(20L);
        row.setSelfMediaAccountId(10L);
        row.setPlatform(platform);
        row.setExpectedPlatformAccountId(expectedId);
        row.setExpectedAccountName(expectedName);
        row.setLoginStatus(status);
        return row;
    }

    private SelfMediaAccount account() {
        return account(null);
    }

    private SelfMediaAccount account(String accountName) {
        return account(accountName, "toutiao");
    }

    private SelfMediaAccount account(String accountName, String platform) {
        return account(accountName, platform, null);
    }

    private SelfMediaAccount account(String accountName, String platform, String platformAccountId) {
        SelfMediaAccount row = new SelfMediaAccount();
        row.setId(10L);
        row.setBrandId(1L);
        row.setPlatform(platform);
        row.setPlatformAccountId(platformAccountId);
        row.setAccountName(accountName);
        return row;
    }

    private LocalAgentSession localAgentSession() {
        LocalAgentSession row = new LocalAgentSession();
        row.setId(40L);
        row.setOperatorId(99L);
        row.setHelperName("local-helper");
        row.setLastSeenAt(LocalDateTime.now().minusMinutes(1));
        row.setExpiresAt(LocalDateTime.now().plusHours(1));
        return row;
    }

    private ExtensionSession extensionSession() {
        ExtensionSession row = new ExtensionSession();
        row.setId(50L);
        row.setBrandId(1L);
        row.setOperatorId(99L);
        row.setExtensionVersion("1.0.0");
        row.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        row.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
        return row;
    }
}
