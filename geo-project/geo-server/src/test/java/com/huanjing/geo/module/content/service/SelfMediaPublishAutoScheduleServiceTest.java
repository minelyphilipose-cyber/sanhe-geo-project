package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.dto.SelfMediaPublishAutoScheduleRequest;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.content.vo.SelfMediaPublishAutoScheduleResponse;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfMediaPublishAutoScheduleServiceTest {
    private SelfMediaPublishScheduleService scheduleService;
    private SelfMediaPublishScheduleMapper scheduleMapper;
    private SelfMediaAccountMapper accountMapper;
    private BrandMapper brandMapper;
    private CompanyChannelQuotaService quotaService;
    private BrandAccessService brandAccessService;
    private SelfMediaPublishAutoScheduleService service;

    @BeforeEach
    void setUp() {
        scheduleService = mock(SelfMediaPublishScheduleService.class);
        scheduleMapper = mock(SelfMediaPublishScheduleMapper.class);
        accountMapper = mock(SelfMediaAccountMapper.class);
        brandMapper = mock(BrandMapper.class);
        quotaService = mock(CompanyChannelQuotaService.class);
        brandAccessService = mock(BrandAccessService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser user = new SysUser();
        user.setId(99L);
        when(currentUserService.requireCurrentUser()).thenReturn(user);

        Brand brand = new Brand();
        brand.setId(8L);
        brand.setCompanyId(6L);
        when(brandMapper.selectById(8L)).thenReturn(brand);
        when(accountMapper.selectById(20L)).thenReturn(account(20L, "toutiao"));
        when(accountMapper.selectById(21L)).thenReturn(account(21L, "baijiahao"));
        when(scheduleMapper.countActiveByBrandPlatformAndPeriod(anyLong(), anyString(), any(), any(), any()))
                .thenReturn(0L);
        when(quotaService.selfMediaDistributionQuota(6L, "toutiao"))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView("self_media:toutiao", "month", "2026-06", 0, 2));
        when(quotaService.selfMediaDistributionQuota(6L, "baijiahao"))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView("self_media:baijiahao", "month", "2026-06", 0, 2));

        service = new SelfMediaPublishAutoScheduleService(
                new BusinessCalendarService(new ObjectMapper()),
                scheduleService,
                scheduleMapper,
                accountMapper,
                brandMapper,
                quotaService,
                brandAccessService,
                currentUserService
        );
    }

    @Test
    void previewSpreadsPlannedItemsAcrossAllowedWorkdays() {
        SelfMediaPublishAutoScheduleResponse response = service.preview(request(List.of(10L, 11L), List.of(20L, 21L)));

        assertEquals(4, response.getRequestedCount());
        assertEquals(4, response.getPlannedCount());
        assertEquals(0, response.getRejectedCount());
        assertTrue(response.getPlannedItems().stream()
                .allMatch(item -> item.getPlannedPublishAt().getDayOfWeek() != DayOfWeek.SATURDAY
                        && item.getPlannedPublishAt().getDayOfWeek() != DayOfWeek.SUNDAY));
        assertTrue(response.getPlannedItems().stream()
                .allMatch(item -> item.getPlannedPublishAt().getHour() == 10
                        || item.getPlannedPublishAt().getHour() == 15));
        verify(brandAccessService).requireBrandAccess(8L, 99L, BrandAccessAction.OPERATE);
    }

    @Test
    void previewRejectsItemsWhenMonthlyPlatformQuotaIsExhausted() {
        when(quotaService.selfMediaDistributionQuota(6L, "toutiao"))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView("self_media:toutiao", "month", "2026-06", 1, 1));

        SelfMediaPublishAutoScheduleResponse response = service.preview(request(List.of(10L, 11L), List.of(20L)));

        assertEquals(2, response.getRequestedCount());
        assertEquals(0, response.getPlannedCount());
        assertEquals(2, response.getRejectedCount());
        assertTrue(response.getPlannedItems().stream()
                .allMatch(item -> "CHANNEL_QUOTA_EXHAUSTED".equals(item.getRejectionCode())));
    }

    private SelfMediaPublishAutoScheduleRequest request(List<Long> articleIds, List<Long> accountIds) {
        SelfMediaPublishAutoScheduleRequest request = new SelfMediaPublishAutoScheduleRequest();
        request.setBrandId(8L);
        request.setTargetMonth("2026-06");
        request.setArticleIds(articleIds);
        request.setSelfMediaAccountIds(accountIds);
        return request;
    }

    private SelfMediaAccount account(Long id, String platform) {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(id);
        account.setPlatform(platform);
        return account;
    }
}
