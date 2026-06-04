package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.BrowserEnvironmentConstants;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentCreateRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentLoginStatusRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentUpdateRequest;
import com.huanjing.geo.module.content.entity.BrowserEnvironment;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentAccountMapper;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrowserEnvironmentServiceTest {
    private BrowserEnvironmentAccountMapper environmentAccountMapper;
    private BrowserEnvironmentMapper environmentMapper;
    private SelfMediaAccountMapper selfMediaAccountMapper;
    private BrowserEnvironmentService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BrowserEnvironment.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BrowserEnvironmentAccount.class);
        environmentMapper = mock(BrowserEnvironmentMapper.class);
        environmentAccountMapper = mock(BrowserEnvironmentAccountMapper.class);
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser operator = new SysUser();
        operator.setId(99L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        service = new BrowserEnvironmentService(
                environmentMapper,
                environmentAccountMapper,
                selfMediaAccountMapper,
                mock(BrandAccessService.class),
                currentUserService
        );
    }

    @Test
    void reportLoginStatus_firstSuccessfulSeenIdentityLocksExpectedFields() {
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
        assertEquals("1865234056392716", updated.getExpectedPlatformAccountId());
        assertEquals("阜阳全屋智能家居", updated.getExpectedAccountName());
        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, updated.getLoginStatus());
    }

    @Test
    void reportLoginStatus_mismatchCanRecoverToLoggedInWhenIdentityMatchesExpected() {
        when(environmentAccountMapper.selectById(30L)).thenReturn(binding("expected", "name", BrowserEnvironmentConstants.LOGIN_MISMATCH));
        when(environmentMapper.selectById(20L)).thenReturn(environment());
        when(selfMediaAccountMapper.selectById(10L)).thenReturn(account());

        service.reportLoginStatus(30L, new BrowserEnvironmentLoginStatusRequest(
                "geo_b",
                10L,
                "toutiao",
                "expected",
                "name",
                BrowserEnvironmentConstants.LOGIN_LOGGED_IN,
                null,
                null
        ));

        ArgumentCaptor<BrowserEnvironmentAccount> captor = ArgumentCaptor.forClass(BrowserEnvironmentAccount.class);
        verify(environmentAccountMapper).updateById(captor.capture());
        assertEquals(BrowserEnvironmentConstants.LOGIN_LOGGED_IN, captor.getValue().getLoginStatus());
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
    void validateForTaskCreation_rejectsMissingExpectedIdentity() {
        when(environmentAccountMapper.selectActiveBySelfMediaAccountId(10L))
                .thenReturn(binding(null, null, BrowserEnvironmentConstants.LOGIN_LOGGED_IN));
        when(environmentMapper.selectById(20L)).thenReturn(environment());

        assertThrows(BizException.class, () -> service.validateForTaskCreation(account()));
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
    void deleteEnvironmentSoftDeletesWithDeletedAtInsteadOfPhysicalDelete() {
        when(environmentMapper.selectById(20L)).thenReturn(environment());

        service.deleteEnvironment(20L);

        verify(environmentMapper).update(any(), any());
    }

    @Test
    void createEnvironment_duplicateProviderProfileReturnsBusinessMessage() {
        doThrow(new DuplicateKeyException("duplicate")).when(environmentMapper).insert(any(BrowserEnvironment.class));

        BizException ex = assertThrows(BizException.class, () -> service.createEnvironment(new BrowserEnvironmentCreateRequest(
                1L,
                BrowserEnvironmentConstants.PROVIDER_ADSPOWER,
                "geo_b",
                "profile-1",
                "环境"
        )));

        assertTrue(ex.getMessage().contains("AdsPower 浏览器编号或环境代号已被其他启用环境使用"));
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
                null
        )));

        assertTrue(ex.getMessage().contains("AdsPower 浏览器编号或环境代号已被其他启用环境使用"));
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
        BrowserEnvironmentAccount row = new BrowserEnvironmentAccount();
        row.setId(30L);
        row.setBrandId(1L);
        row.setBrowserEnvironmentId(20L);
        row.setSelfMediaAccountId(10L);
        row.setPlatform("toutiao");
        row.setExpectedPlatformAccountId(expectedId);
        row.setExpectedAccountName(expectedName);
        row.setLoginStatus(status);
        return row;
    }

    private SelfMediaAccount account() {
        SelfMediaAccount row = new SelfMediaAccount();
        row.setId(10L);
        row.setBrandId(1L);
        row.setPlatform("toutiao");
        return row;
    }
}
