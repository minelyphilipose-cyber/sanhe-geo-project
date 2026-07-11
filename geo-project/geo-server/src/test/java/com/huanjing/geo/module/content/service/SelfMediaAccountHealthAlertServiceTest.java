package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.module.content.credential.entity.SelfMediaCookieCredential;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaCookieCredentialMapper;
import com.huanjing.geo.module.content.mapper.AccountAuthRiskScanBatchMapper;
import com.huanjing.geo.module.content.entity.SelfMediaAuthHealthPolicy;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformCapabilityContract;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformPublishChannel;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleMode;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleRules;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandOperatorAssignment;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandOperatorAssignmentMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.system.service.SystemAlertService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class SelfMediaAccountHealthAlertServiceTest {

    private SelfMediaAccountMapper accountMapper;
    private SelfMediaCookieCredentialMapper credentialMapper;
    private BrandMapper brandMapper;
    private BrandOperatorAssignmentMapper brandOperatorAssignmentMapper;
    private CompanyMapper companyMapper;
    private SelfMediaPlatformScheduleAdapterRouter platformRouter;
    private SystemAlertService systemAlertService;
    private AccountAuthRiskScanBatchMapper scanBatchMapper;
    private SelfMediaAccountHealthAlertService service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(SelfMediaAccount.class);
        initTableInfo(Brand.class);
        initTableInfo(BrandOperatorAssignment.class);
        initTableInfo(Company.class);
    }

    @BeforeEach
    void setUp() {
        accountMapper = mock(SelfMediaAccountMapper.class);
        credentialMapper = mock(SelfMediaCookieCredentialMapper.class);
        brandMapper = mock(BrandMapper.class);
        brandOperatorAssignmentMapper = mock(BrandOperatorAssignmentMapper.class);
        companyMapper = mock(CompanyMapper.class);
        platformRouter = mock(SelfMediaPlatformScheduleAdapterRouter.class);
        systemAlertService = mock(SystemAlertService.class);
        scanBatchMapper = mock(AccountAuthRiskScanBatchMapper.class);
        service = new SelfMediaAccountHealthAlertService(
                accountMapper,
                credentialMapper,
                brandMapper,
                brandOperatorAssignmentMapper,
                companyMapper,
                platformRouter,
                systemAlertService,
                policyService(),
                new SelfMediaAuthRiskEvaluator(),
                scanBatchMapper
        );
        ReflectionTestUtils.setField(service, "scanLimit", 500);
        ReflectionTestUtils.setField(service, "credentialExpiringDays", 7);
        ReflectionTestUtils.setField(service, "defaultCookieValidDays", 30);
        ReflectionTestUtils.setField(service, "cookiePlatformValidDays", "toutiao:30");
        when(platformRouter.contracts()).thenReturn(List.of(
                contract("douyin", "抖音图文", SelfMediaPlatformPublishChannel.OFFICIAL_API),
                contract("toutiao", "今日头条", SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION)
        ));
        when(platformRouter.contract("douyin")).thenReturn(Optional.of(
                contract("douyin", "抖音图文", SelfMediaPlatformPublishChannel.OFFICIAL_API)
        ));
        when(platformRouter.contract("toutiao")).thenReturn(Optional.of(
                contract("toutiao", "今日头条", SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION)
        ));
        when(brandOperatorAssignmentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
    }

    @Test
    void scanCreatesOwnerAlertWhenOfficialApiRefreshTokenExpired() {
        SelfMediaAccount account = officialAccount();
        account.setRefreshTokenExpiresAt(LocalDateTime.now().minusDays(1));
        when(accountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(account));
        when(brandMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(brand()));
        when(companyMapper.selectBatchIds(List.of(20L))).thenReturn(List.of(company(99L)));

        service.scanOnce();

        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(systemAlertService).createRecipientAlert(
                eq("SELF_MEDIA_ACCOUNT_AUTH_HEALTH"),
                eq("high"),
                eq("self_media_account_health"),
                eq("客户「三和医疗」品牌「三和口腔」的抖音图文账号「测试抖音」官方 API 长期授权已过期 1 天，请立即更新账号信息"),
                contextCaptor.capture(),
                eq(99L),
                eq(null),
                eq("self_media_auth:11:OFFICIAL_CREDENTIAL_EXPIRED")
        );
        assertEquals("OFFICIAL_CREDENTIAL_EXPIRED", contextCaptor.getValue().get("issueCode"));
        assertEquals("三和医疗", contextCaptor.getValue().get("companyName"));
        assertEquals("三和口腔", contextCaptor.getValue().get("brandName"));
        assertEquals("/admin/brands/10?tab=self-media&accountId=11", contextCaptor.getValue().get("route"));
    }

    @Test
    void scanCreatesOwnerAlertWhenCookieCredentialExpiredByPlatformTtl() {
        SelfMediaAccount account = cookieAccount();
        SelfMediaCookieCredential credential = credential(account.getId());
        credential.setCapturedAt(LocalDateTime.now().minusDays(31));
        when(accountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(account));
        when(credentialMapper.selectActiveMetaByAccountIds(List.of(12L))).thenReturn(List.of(credential));
        when(brandMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(brand()));
        when(companyMapper.selectBatchIds(List.of(20L))).thenReturn(List.of(company(99L)));

        service.scanOnce();

        verify(systemAlertService).createRecipientAlert(
                eq("SELF_MEDIA_ACCOUNT_AUTH_HEALTH"),
                eq("warn"),
                eq("self_media_account_health"),
                eq("客户「三和医疗」品牌「三和口腔」的今日头条账号「测试头条」已超过建议复验时间，请确认当前登录状态"),
                any(),
                eq(99L),
                eq(null),
                eq("self_media_auth:12:ACCOUNT_REVERIFY_OVERDUE")
        );
    }

    @Test
    void scanUsesUrgentToneWhenCookieCredentialExpiresWithinThreeDays() {
        SelfMediaAccount account = cookieAccount();
        SelfMediaCookieCredential credential = credential(account.getId());
        credential.setCapturedAt(LocalDateTime.now().minusDays(28));
        when(accountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(account));
        when(credentialMapper.selectActiveMetaByAccountIds(List.of(12L))).thenReturn(List.of(credential));
        when(brandMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(brand()));
        when(companyMapper.selectBatchIds(List.of(20L))).thenReturn(List.of(company(99L)));

        service.scanOnce();

        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(systemAlertService).createRecipientAlert(
                eq("SELF_MEDIA_ACCOUNT_AUTH_HEALTH"),
                eq("warn"),
                eq("self_media_account_health"),
                eq("客户「三和医疗」品牌「三和口腔」的今日头条账号「测试头条」即将需要复验，请提前确认当前登录状态"),
                contextCaptor.capture(),
                eq(99L),
                eq(null),
                eq("self_media_auth:12:ACCOUNT_REVERIFY_DUE_SOON")
        );
        assertEquals(2L, contextCaptor.getValue().get("daysUntilExpiry"));
    }

    @Test
    void scanResolvesExistingAlertsWhenCookieAccountHealthy() {
        SelfMediaAccount account = cookieAccount();
        SelfMediaCookieCredential credential = credential(account.getId());
        credential.setCapturedAt(LocalDateTime.now());
        when(accountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(account));
        when(credentialMapper.selectActiveMetaByAccountIds(List.of(12L))).thenReturn(List.of(credential));
        when(brandMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(brand()));
        when(companyMapper.selectBatchIds(List.of(20L))).thenReturn(List.of(company(99L)));

        service.scanOnce();

        verify(systemAlertService).resolveOpenByDedupeKeyPrefix("self_media_auth:12:", null);
    }

    @Test
    void trustedLoginFactKeepsMissingCookieNonBlocking() {
        SelfMediaAccount account = cookieAccount();
        account.setLastLoginVerifiedAt(LocalDateTime.now().minusDays(1));
        when(accountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(account));
        when(brandMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(brand()));
        when(companyMapper.selectBatchIds(List.of(20L))).thenReturn(List.of(company(99L)));

        service.scanOnce();

        verify(systemAlertService).resolveOpenByDedupeKeyPrefix("self_media_auth:12:", null);
        verify(systemAlertService, never()).createRecipientAlert(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scanUsesIdCursorUntilAllOneThousandAccountsAreCovered() {
        List<SelfMediaAccount> firstPage = new ArrayList<>();
        List<SelfMediaAccount> secondPage = new ArrayList<>();
        for (long id = 1; id <= 1_000; id++) {
            SelfMediaAccount account = officialAccount();
            account.setId(id);
            account.setRefreshTokenCipher(null);
            (id <= 500 ? firstPage : secondPage).add(account);
        }
        when(accountMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(firstPage, secondPage, List.of());
        when(brandMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(brand()));
        when(companyMapper.selectBatchIds(List.of(20L))).thenReturn(List.of(company(99L)));

        assertEquals(1_000, service.scanOnce());
        verify(accountMapper, times(3)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void oneAccountFailureDoesNotStopFollowingAccounts() {
        SelfMediaAccount first = officialAccount();
        first.setId(21L);
        first.setRefreshTokenCipher(null);
        SelfMediaAccount second = officialAccount();
        second.setId(22L);
        second.setRefreshTokenCipher(null);
        when(accountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, second));
        when(brandMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(brand()));
        when(companyMapper.selectBatchIds(List.of(20L))).thenReturn(List.of(company(99L)));
        when(platformRouter.contract("douyin"))
                .thenThrow(new RuntimeException("single account failure"))
                .thenReturn(Optional.of(contract("douyin", "抖音图文", SelfMediaPlatformPublishChannel.OFFICIAL_API)));

        assertEquals(1, service.scanOnce());

        ArgumentCaptor<com.huanjing.geo.module.content.entity.AccountAuthRiskScanBatch> batchCaptor =
                ArgumentCaptor.forClass(com.huanjing.geo.module.content.entity.AccountAuthRiskScanBatch.class);
        verify(scanBatchMapper).updateById(batchCaptor.capture());
        assertEquals(2, batchCaptor.getValue().getTotalCount());
        assertEquals(1, batchCaptor.getValue().getSuccessCount());
        assertEquals(1, batchCaptor.getValue().getFailureCount());
        assertEquals("partial_success", batchCaptor.getValue().getStatus());
    }

    private SelfMediaAccount officialAccount() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(11L);
        account.setBrandId(10L);
        account.setPlatform("douyin");
        account.setAccountName("测试抖音");
        account.setPlatformAccountId("douyin-open-id");
        account.setStatus("active");
        account.setRefreshTokenCipher("cipher");
        return account;
    }

    private SelfMediaAccount cookieAccount() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(12L);
        account.setBrandId(10L);
        account.setPlatform("toutiao");
        account.setAccountName("测试头条");
        account.setPlatformAccountId("toutiao-id");
        account.setStatus("active");
        account.setAuthMode("COOKIE");
        return account;
    }

    private SelfMediaCookieCredential credential(Long accountId) {
        SelfMediaCookieCredential credential = new SelfMediaCookieCredential();
        credential.setSelfMediaAccountId(accountId);
        credential.setVersion(1);
        return credential;
    }

    private SelfMediaAuthHealthPolicyService policyService() {
        SelfMediaAuthHealthPolicyService service = mock(SelfMediaAuthHealthPolicyService.class);
        SelfMediaAuthHealthPolicy policy = new SelfMediaAuthHealthPolicy();
        policy.setPlatformCode("toutiao");
        policy.setEnabled(true);
        policy.setReverifyIntervalDays(30);
        policy.setWarningDays(7);
        policy.setCredentialReferenceDays(30);
        policy.setCredentialExpiryMode("declared_then_reference");
        when(service.findPolicy("toutiao")).thenReturn(policy);
        return service;
    }

    private Brand brand() {
        Brand brand = new Brand();
        brand.setId(10L);
        brand.setCompanyId(20L);
        brand.setBrandName("三和口腔");
        return brand;
    }

    private Company company(Long ownerId) {
        Company company = new Company();
        company.setId(20L);
        company.setCompanyName("三和医疗");
        company.setOwnerId(ownerId);
        return company;
    }

    private SelfMediaPlatformCapabilityContract contract(String platform,
                                                         String displayName,
                                                         SelfMediaPlatformPublishChannel publishChannel) {
        return new SelfMediaPlatformCapabilityContract(
                platform,
                displayName,
                publishChannel,
                SelfMediaPlatformScheduleMode.PLATFORM_NATIVE,
                SelfMediaPlatformScheduleRules.defaults(),
                false,
                false,
                false,
                false
        );
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }
}
