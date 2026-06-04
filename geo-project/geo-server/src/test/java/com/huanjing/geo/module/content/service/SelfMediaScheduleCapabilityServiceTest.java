package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.SelfMediaScheduleCapabilityUpsertRequest;
import com.huanjing.geo.module.content.entity.SelfMediaScheduleCapability;
import com.huanjing.geo.module.content.mapper.SelfMediaScheduleCapabilityMapper;
import com.huanjing.geo.module.content.vo.SelfMediaScheduleCapabilityVO;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfMediaScheduleCapabilityServiceTest {
    private SelfMediaScheduleCapabilityMapper mapper;
    private SelfMediaScheduleCapabilityService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SelfMediaScheduleCapabilityMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser user = new SysUser();
        user.setId(99L);
        when(currentUserService.requireCurrentUser()).thenReturn(user);
        service = new SelfMediaScheduleCapabilityService(mapper, currentUserService);
    }

    @Test
    void upsertVerifiedPlatformScheduleRequiresDelayRangeAndStoresVerifier() {
        SelfMediaScheduleCapabilityUpsertRequest request = verifiedRequest();

        SelfMediaScheduleCapabilityVO response = service.upsert(request);

        assertEquals("toutiao", response.getPlatform());
        assertEquals("verified", response.getVerificationStatus());
        assertTrue(response.getSupportsSchedule());
        assertEquals("platform_schedule", response.getV1Strategy());
        assertEquals(99L, response.getVerifiedBy());
        assertNotNull(response.getVerifiedAt());
        verify(mapper).insert(any(SelfMediaScheduleCapability.class));
    }

    @Test
    void upsertRejectsVerifiedScheduleWithoutDelayRange() {
        SelfMediaScheduleCapabilityUpsertRequest request = verifiedRequest();
        request.setMinDelayMinutes(null);

        BizException error = assertThrows(BizException.class, () -> service.upsert(request));

        assertEquals("DELAY_RANGE_REQUIRED", ((Map<?, ?>) error.getData()).get("code"));
    }

    @Test
    void readinessRequiresVerifiedPlatformScheduleStrategy() {
        when(mapper.selectByPlatform("zhihu")).thenReturn(capability("zhihu", "verified", true, "platform_schedule"));

        SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness = service.readiness("zhihu");

        assertTrue(readiness.ready());
    }

    @Test
    void readinessRejectsUnverifiedPlatform() {
        when(mapper.selectByPlatform("xiaohongshu"))
                .thenReturn(capability("xiaohongshu", "unverified", false, "pending"));

        SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness = service.readiness("xiaohongshu");

        assertFalse(readiness.ready());
        assertEquals("PLATFORM_CAPABILITY_UNVERIFIED", readiness.code());
    }

    private SelfMediaScheduleCapabilityUpsertRequest verifiedRequest() {
        SelfMediaScheduleCapabilityUpsertRequest request = new SelfMediaScheduleCapabilityUpsertRequest();
        request.setPlatform("Toutiao");
        request.setVerificationStatus("verified");
        request.setSupportsSchedule(true);
        request.setMinDelayMinutes(10);
        request.setMaxDelayMinutes(10080);
        request.setV1Strategy("platform_schedule");
        request.setSupportsPublishCheck(true);
        return request;
    }

    private SelfMediaScheduleCapability capability(String platform,
                                                   String verificationStatus,
                                                   boolean supportsSchedule,
                                                   String strategy) {
        SelfMediaScheduleCapability row = new SelfMediaScheduleCapability();
        row.setPlatform(platform);
        row.setVerificationStatus(verificationStatus);
        row.setSupportsSchedule(supportsSchedule);
        row.setV1Strategy(strategy);
        return row;
    }
}
