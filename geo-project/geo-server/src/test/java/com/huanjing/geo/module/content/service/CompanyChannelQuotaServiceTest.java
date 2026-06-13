package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.distribution.DistributionTargetKind;
import com.huanjing.geo.module.content.entity.CompanyChannelQuotaLedger;
import com.huanjing.geo.module.content.entity.CompanyChannelQuotaUsage;
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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyChannelQuotaServiceTest {

    private CompanyPackageBindingService bindingService;
    private CompanyChannelQuotaUsageMapper usageMapper;
    private CompanyChannelQuotaLedgerMapper ledgerMapper;
    private DistributionTaskMapper distributionTaskMapper;
    private SelfMediaAccountMapper selfMediaAccountMapper;
    private PlatformTransactionManager transactionManager;
    private CompanyChannelQuotaService service;

    @BeforeEach
    void setUp() {
        bindingService = mock(CompanyPackageBindingService.class);
        usageMapper = mock(CompanyChannelQuotaUsageMapper.class);
        ledgerMapper = mock(CompanyChannelQuotaLedgerMapper.class);
        distributionTaskMapper = mock(DistributionTaskMapper.class);
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        transactionManager = mock(PlatformTransactionManager.class);
        SystemAlertService systemAlertService = mock(SystemAlertService.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        service = new CompanyChannelQuotaService(
                bindingService,
                usageMapper,
                ledgerMapper,
                distributionTaskMapper,
                selfMediaAccountMapper,
                systemAlertService,
                transactionManager
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

    @Test
    void selfMediaDistributionQuotaReadsUsageWithoutWritingQuotaRow() {
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setCompanyId(7L);
        binding.setChannelQuotaSnapshot("""
                [
                  {"channelCode":"self_media:toutiao","periodType":"month","quotaLimit":5,"enabled":true}
                ]
                """);
        CompanyChannelQuotaUsage usage = new CompanyChannelQuotaUsage();
        usage.setUsedCount(2);
        when(bindingService.requireActiveBinding(7L)).thenReturn(binding);
        when(usageMapper.selectOne(any())).thenReturn(usage);

        CompanyChannelQuotaService.DistributionQuotaView quota = service.selfMediaDistributionQuota(7L, "toutiao");

        assertEquals(2, quota.usedCount());
        assertEquals(5, quota.quotaLimit());
        verify(usageMapper, never()).insertIgnore(any(), any(), any(), any(), any());
        verify(usageMapper, never()).updateQuotaLimit(any(), any(), any(), any(), any());
    }

    @Test
    void reserveSelfMediaSchedulesAggregatesSamePlatformQuotaReservation() {
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setCompanyId(7L);
        binding.setChannelQuotaSnapshot("""
                [
                  {"channelCode":"self_media:toutiao","periodType":"month","quotaLimit":5,"enabled":true}
                ]
                """);
        when(bindingService.requireActiveBinding(7L)).thenReturn(binding);
        when(ledgerMapper.selectByBiz(any(), any())).thenReturn(null);
        when(usageMapper.tryReserveAmount(eq(7L), eq("self_media:toutiao"), eq("month"), any(), eq(2))).thenReturn(1);

        service.reserveSelfMediaSchedules(7L, java.util.List.of(
                new CompanyChannelQuotaService.SelfMediaScheduleQuotaReservation(9L, "toutiao", 300L),
                new CompanyChannelQuotaService.SelfMediaScheduleQuotaReservation(9L, "toutiao", 301L)
        ));

        verify(usageMapper, times(1)).insertIgnore(eq(7L), eq("self_media:toutiao"), eq("month"), any(), eq(5));
        verify(usageMapper, times(1)).updateQuotaLimit(eq(7L), eq("self_media:toutiao"), eq("month"), any(), eq(5));
        verify(usageMapper, times(1)).tryReserveAmount(eq(7L), eq("self_media:toutiao"), eq("month"), any(), eq(2));
        verify(usageMapper, never()).tryReserve(eq(7L), eq("self_media:toutiao"), eq("month"), any());
        verify(ledgerMapper, times(2)).insert(any(CompanyChannelQuotaLedger.class));
    }

    @Test
    void reserveSelfMediaScheduleConvertsLockWaitTimeoutToRetryableBusinessError() {
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setCompanyId(7L);
        binding.setChannelQuotaSnapshot("""
                [
                  {"channelCode":"self_media:toutiao","periodType":"month","quotaLimit":5,"enabled":true}
                ]
                """);
        when(bindingService.requireActiveBinding(7L)).thenReturn(binding);
        when(ledgerMapper.selectByBiz("self_media_schedule", "300")).thenReturn(null);
        when(usageMapper.insertIgnore(eq(7L), eq("self_media:toutiao"), eq("month"), any(), eq(5)))
                .thenThrow(new CannotAcquireLockException("Lock wait timeout exceeded; try restarting transaction"));

        BizException ex = assertThrows(BizException.class,
                () -> service.reserveSelfMediaSchedule(7L, 9L, "toutiao", 300L));

        assertEquals(409, ex.getCode());
        assertEquals("渠道配额正在被其它排期任务占用，请稍后重试", ex.getMessage());
        verify(usageMapper, times(1)).insertIgnore(eq(7L), eq("self_media:toutiao"), eq("month"), any(), eq(5));
        verify(usageMapper).setSessionLockWaitTimeout(2);
        verify(usageMapper).resetSessionLockWaitTimeout();
    }

    @Test
    void reserveSelfMediaScheduleConvertsStatementTimeoutToRetryableBusinessError() {
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setCompanyId(7L);
        binding.setChannelQuotaSnapshot("""
                [
                  {"channelCode":"self_media:toutiao","periodType":"month","quotaLimit":5,"enabled":true}
                ]
                """);
        when(bindingService.requireActiveBinding(7L)).thenReturn(binding);
        when(ledgerMapper.selectByBiz("self_media_schedule", "302")).thenReturn(null);
        when(usageMapper.insertIgnore(eq(7L), eq("self_media:toutiao"), eq("month"), any(), eq(5)))
                .thenThrow(new QueryTimeoutException("Statement cancelled due to timeout or client request"));

        BizException ex = assertThrows(BizException.class,
                () -> service.reserveSelfMediaSchedule(7L, 9L, "toutiao", 302L));

        assertEquals(409, ex.getCode());
        assertEquals("渠道配额正在被其它排期任务占用，请稍后重试", ex.getMessage());
        verify(usageMapper, times(1)).insertIgnore(eq(7L), eq("self_media:toutiao"), eq("month"), any(), eq(5));
        verify(usageMapper).resetSessionLockWaitTimeout();
    }

    @Test
    void reserveSelfMediaScheduleRetriesDeadlockInFreshTransaction() {
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setCompanyId(7L);
        binding.setChannelQuotaSnapshot("""
                [
                  {"channelCode":"self_media:toutiao","periodType":"month","quotaLimit":5,"enabled":true}
                ]
                """);
        when(bindingService.requireActiveBinding(7L)).thenReturn(binding);
        when(ledgerMapper.selectByBiz("self_media_schedule", "301")).thenReturn(null);
        when(usageMapper.insertIgnore(eq(7L), eq("self_media:toutiao"), eq("month"), any(), eq(5)))
                .thenThrow(new DeadlockLoserDataAccessException("Deadlock found when trying to get lock", null))
                .thenReturn(1);
        when(usageMapper.tryReserve(eq(7L), eq("self_media:toutiao"), eq("month"), any())).thenReturn(1);

        CompanyChannelQuotaLedger ledger = service.reserveSelfMediaSchedule(7L, 9L, "toutiao", 301L);

        assertEquals("self_media:toutiao", ledger.getChannelCode());
        verify(usageMapper, times(2)).insertIgnore(eq(7L), eq("self_media:toutiao"), eq("month"), any(), eq(5));
        verify(usageMapper, times(2)).setSessionLockWaitTimeout(2);
        verify(usageMapper, times(2)).resetSessionLockWaitTimeout();
        verify(transactionManager, times(2)).getTransaction(any(TransactionDefinition.class));
    }
}
