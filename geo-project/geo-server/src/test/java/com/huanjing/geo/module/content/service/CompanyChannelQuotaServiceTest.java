package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.distribution.DistributionTargetKind;
import com.huanjing.geo.module.content.entity.CompanyChannelQuotaLedger;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.CompanyChannelQuotaLedgerMapper;
import com.huanjing.geo.module.content.mapper.CompanyChannelQuotaUsageMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.system.service.SystemAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyChannelQuotaServiceTest {

    private CompanyPackageBindingService bindingService;
    private CompanyChannelQuotaUsageMapper usageMapper;
    private CompanyChannelQuotaLedgerMapper ledgerMapper;
    private DistributionTaskMapper distributionTaskMapper;
    private SelfMediaAccountMapper selfMediaAccountMapper;
    private CompanyChannelQuotaService service;

    @BeforeEach
    void setUp() {
        bindingService = mock(CompanyPackageBindingService.class);
        usageMapper = mock(CompanyChannelQuotaUsageMapper.class);
        ledgerMapper = mock(CompanyChannelQuotaLedgerMapper.class);
        distributionTaskMapper = mock(DistributionTaskMapper.class);
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        SystemAlertService systemAlertService = mock(SystemAlertService.class);
        service = new CompanyChannelQuotaService(
                bindingService,
                usageMapper,
                ledgerMapper,
                distributionTaskMapper,
                selfMediaAccountMapper,
                systemAlertService
        );
    }

    @Test
    void reserveDistributionUsesSelfMediaAccountPlatformAsQuotaChannel() {
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setCompanyId(7L);
        binding.setChannelQuotaSnapshot("""
                [
                  {"channelCode":"self_media:zhihu","periodType":"week","quotaLimit":3,"enabled":true}
                ]
                """);
        DistributionTask task = new DistributionTask();
        task.setId(200L);
        task.setSelfMediaAccountId(88L);
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(88L);
        account.setPlatform("zhihu");
        when(distributionTaskMapper.selectById(200L)).thenReturn(task);
        when(selfMediaAccountMapper.selectById(88L)).thenReturn(account);
        when(bindingService.requireActiveBinding(7L)).thenReturn(binding);
        when(ledgerMapper.selectOne(any())).thenReturn(null);
        when(usageMapper.tryReserve(eq(7L), eq("self_media:zhihu"), eq("week"), any())).thenReturn(1);

        CompanyChannelQuotaLedger ledger = service.reserveDistribution(7L, 9L, DistributionTargetKind.MP_ACCOUNT, 200L);

        assertEquals("self_media:zhihu", ledger.getChannelCode());
        verify(usageMapper).insertIgnore(eq(7L), eq("self_media:zhihu"), eq("week"), any(), eq(3));
        verify(usageMapper).updateQuotaLimit(eq(7L), eq("self_media:zhihu"), eq("week"), any(), eq(3));
        verify(ledgerMapper).insert(any(CompanyChannelQuotaLedger.class));
    }

    @Test
    void reserveDistributionMapsWechatMpAccountToWechatQuotaChannel() {
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setCompanyId(7L);
        binding.setChannelQuotaSnapshot("""
                [
                  {"channelCode":"self_media:wechat","periodType":"week","quotaLimit":2,"enabled":true}
                ]
                """);
        DistributionTask task = new DistributionTask();
        task.setId(201L);
        task.setSelfMediaAccountId(89L);
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(89L);
        account.setPlatform("wechat_mp");
        when(distributionTaskMapper.selectById(201L)).thenReturn(task);
        when(selfMediaAccountMapper.selectById(89L)).thenReturn(account);
        when(bindingService.requireActiveBinding(7L)).thenReturn(binding);
        when(ledgerMapper.selectOne(any())).thenReturn(null);
        when(usageMapper.tryReserve(eq(7L), eq("self_media:wechat"), eq("week"), any())).thenReturn(1);

        CompanyChannelQuotaLedger ledger = service.reserveDistribution(7L, 9L, DistributionTargetKind.MP_ACCOUNT, 201L);

        assertEquals("self_media:wechat", ledger.getChannelCode());
        verify(usageMapper).insertIgnore(eq(7L), eq("self_media:wechat"), eq("week"), any(), eq(2));
        verify(usageMapper).updateQuotaLimit(eq(7L), eq("self_media:wechat"), eq("week"), any(), eq(2));
    }
}
