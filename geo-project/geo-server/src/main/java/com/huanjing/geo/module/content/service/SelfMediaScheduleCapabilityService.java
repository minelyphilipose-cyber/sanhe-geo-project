package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.SelfMediaScheduleCapabilityUpsertRequest;
import com.huanjing.geo.module.content.entity.SelfMediaScheduleCapability;
import com.huanjing.geo.module.content.mapper.SelfMediaScheduleCapabilityMapper;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.content.vo.SelfMediaScheduleCapabilityVO;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SelfMediaScheduleCapabilityService {
    private static final int ERROR_CODE = 70041;
    public static final String STATUS_UNVERIFIED = "unverified";
    public static final String STATUS_VERIFIED = "verified";
    public static final String STATUS_FAILED = "failed";
    public static final String STRATEGY_PENDING = "pending";
    public static final String STRATEGY_PLATFORM_SCHEDULE = "platform_schedule";
    public static final String STRATEGY_SEMI_AUTO = "semi_auto";

    private final SelfMediaScheduleCapabilityMapper mapper;
    private final CurrentUserService currentUserService;
    private final SelfMediaPlatformScheduleAdapterRouter scheduleAdapterRouter;

    public List<SelfMediaScheduleCapabilityVO> list() {
        currentUserService.requireCurrentUser();
        return mapper.selectList(new LambdaQueryWrapper<SelfMediaScheduleCapability>()
                        .orderByAsc(SelfMediaScheduleCapability::getPlatform))
                .stream()
                .map(SelfMediaScheduleCapabilityVO::from)
                .toList();
    }

    public SelfMediaScheduleCapabilityVO detail(String platform) {
        currentUserService.requireCurrentUser();
        return SelfMediaScheduleCapabilityVO.from(mapper.selectByPlatform(normalizePlatform(platform)));
    }

    public PlatformScheduleReadiness readiness(String platform) {
        String normalized = normalizePlatform(platform);
        SelfMediaScheduleCapability row = mapper.selectByPlatform(normalized);
        if (row == null) {
            return PlatformScheduleReadiness.rejected("PLATFORM_CAPABILITY_UNVERIFIED", "平台定时发布能力尚未验证");
        }
        if (!STATUS_VERIFIED.equals(normalize(row.getVerificationStatus()))) {
            return PlatformScheduleReadiness.rejected("PLATFORM_CAPABILITY_UNVERIFIED", "平台定时发布能力尚未完成验证");
        }
        if (!Boolean.TRUE.equals(row.getSupportsSchedule())) {
            return PlatformScheduleReadiness.rejected("PLATFORM_SCHEDULE_UNSUPPORTED", "平台不支持稳定的原生定时发布，当前只能走半自动");
        }
        if (!STRATEGY_PLATFORM_SCHEDULE.equals(normalize(row.getV1Strategy()))) {
            return PlatformScheduleReadiness.rejected("PLATFORM_SCHEDULE_STRATEGY_DISABLED", "平台 v1 策略未启用自动定时发布");
        }
        if (scheduleAdapterRouter.find(normalized).isEmpty()) {
            return PlatformScheduleReadiness.rejected("PLATFORM_SCHEDULE_ADAPTER_MISSING", "平台自动定时发布适配器尚未接入");
        }
        return PlatformScheduleReadiness.ready(row);
    }

    @Transactional
    public SelfMediaScheduleCapabilityVO upsert(SelfMediaScheduleCapabilityUpsertRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        ValidatedCapability validated = validate(request);
        SelfMediaScheduleCapability row = mapper.selectByPlatform(validated.platform());
        LocalDateTime now = LocalDateTime.now();
        if (row == null) {
            row = new SelfMediaScheduleCapability();
            row.setPlatform(validated.platform());
            row.setCreatedAt(now);
        }
        row.setVerificationStatus(validated.verificationStatus());
        row.setSupportsSchedule(validated.supportsSchedule());
        row.setMinDelayMinutes(request.getMinDelayMinutes());
        row.setMaxDelayMinutes(request.getMaxDelayMinutes());
        row.setSaveCreatesSchedule(request.getSaveCreatesSchedule());
        row.setSupportsCancel(request.getSupportsCancel());
        row.setSupportsModify(request.getSupportsModify());
        row.setSupportsPublishCheck(request.getSupportsPublishCheck());
        row.setV1Strategy(validated.v1Strategy());
        row.setSelectorStatus(trimToNull(request.getSelectorStatus()));
        row.setEvidenceJson(trimToNull(request.getEvidenceJson()));
        row.setNotes(trimToNull(request.getNotes()));
        row.setVerifiedAt(STATUS_VERIFIED.equals(validated.verificationStatus()) ? now : null);
        row.setVerifiedBy(STATUS_VERIFIED.equals(validated.verificationStatus()) ? operator.getId() : null);
        row.setUpdatedAt(now);

        if (row.getId() == null) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
        return SelfMediaScheduleCapabilityVO.from(row);
    }

    private ValidatedCapability validate(SelfMediaScheduleCapabilityUpsertRequest request) {
        if (request == null) {
            fail("INVALID_REQUEST", "request body is required");
        }
        String platform = normalizePlatform(request.getPlatform());
        String status = normalize(request.getVerificationStatus());
        if (!List.of(STATUS_UNVERIFIED, STATUS_VERIFIED, STATUS_FAILED).contains(status)) {
            fail("INVALID_VERIFICATION_STATUS", "未知平台能力验证状态");
        }
        Boolean supportsSchedule = request.getSupportsSchedule();
        if (supportsSchedule == null) {
            fail("INVALID_SUPPORTS_SCHEDULE", "supportsSchedule is required");
        }
        String strategy = StringUtils.hasText(request.getV1Strategy())
                ? normalize(request.getV1Strategy())
                : (Boolean.TRUE.equals(supportsSchedule) ? STRATEGY_PLATFORM_SCHEDULE : STRATEGY_SEMI_AUTO);
        if (!List.of(STRATEGY_PENDING, STRATEGY_PLATFORM_SCHEDULE, STRATEGY_SEMI_AUTO).contains(strategy)) {
            fail("INVALID_V1_STRATEGY", "未知 v1 策略");
        }
        if (request.getMinDelayMinutes() != null && request.getMinDelayMinutes() < 0) {
            fail("INVALID_MIN_DELAY", "最小可定时延迟不能小于 0");
        }
        if (request.getMaxDelayMinutes() != null && request.getMaxDelayMinutes() < 0) {
            fail("INVALID_MAX_DELAY", "最大可定时延迟不能小于 0");
        }
        if (request.getMinDelayMinutes() != null
                && request.getMaxDelayMinutes() != null
                && request.getMaxDelayMinutes() < request.getMinDelayMinutes()) {
            fail("INVALID_DELAY_RANGE", "最大可定时延迟不能小于最小延迟");
        }
        if (STATUS_VERIFIED.equals(status) && Boolean.TRUE.equals(supportsSchedule)) {
            if (request.getMinDelayMinutes() == null || request.getMaxDelayMinutes() == null) {
                fail("DELAY_RANGE_REQUIRED", "支持定时发布的平台必须填写最小和最大可定时延迟");
            }
            if (!STRATEGY_PLATFORM_SCHEDULE.equals(strategy)) {
                fail("INVALID_VERIFIED_STRATEGY", "支持定时发布且验证通过的平台，v1 策略必须为 platform_schedule");
            }
        }
        return new ValidatedCapability(platform, status, supportsSchedule, strategy);
    }

    private String normalizePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            fail("INVALID_PLATFORM", "platform must not be blank");
        }
        return platform.trim().toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void fail(String code, String message) {
        throw new BizException(ERROR_CODE, message, 200, Map.of("code", code));
    }

    private record ValidatedCapability(String platform,
                                       String verificationStatus,
                                       Boolean supportsSchedule,
                                       String v1Strategy) {
    }

    public record PlatformScheduleReadiness(boolean ready, String code, String message) {
        static PlatformScheduleReadiness ready(SelfMediaScheduleCapability row) {
            return new PlatformScheduleReadiness(true, null, null);
        }

        static PlatformScheduleReadiness rejected(String code, String message) {
            return new PlatformScheduleReadiness(false, code, message);
        }
    }
}
