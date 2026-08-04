package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.dto.SelfMediaScheduleCapabilityUpsertRequest;
import com.huanjing.geo.module.content.entity.SelfMediaScheduleCapability;
import com.huanjing.geo.module.content.mapper.SelfMediaScheduleCapabilityMapper;
import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformCapabilityContract;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformPublishChannel;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleRules;
import com.huanjing.geo.module.content.vo.SelfMediaScheduleCapabilityVO;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SelfMediaScheduleCapabilityService {
    private static final int ERROR_CODE = 70041;
    public static final String STATUS_UNVERIFIED = "unverified";
    public static final String STATUS_VERIFIED = "verified";
    public static final String STATUS_FAILED = "failed";
    public static final String STRATEGY_PENDING = "pending";
    public static final String STRATEGY_PLATFORM_SCHEDULE = SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE;
    public static final String STRATEGY_BACKEND_DELAYED_PUBLISH = SelfMediaPublishScheduleConstants.STRATEGY_BACKEND_DELAYED_PUBLISH;
    public static final String STRATEGY_SEMI_AUTO = SelfMediaPublishScheduleConstants.STRATEGY_SEMI_AUTO;

    private final SelfMediaScheduleCapabilityMapper mapper;
    private final CurrentUserService currentUserService;
    private final SelfMediaPlatformScheduleAdapterRouter scheduleAdapterRouter;
    private final ObjectMapper objectMapper;

    public List<SelfMediaScheduleCapabilityVO> list() {
        currentUserService.requireCurrentUser();
        Map<String, SelfMediaScheduleCapability> rowsByPlatform = mapper.selectList(
                        new LambdaQueryWrapper<SelfMediaScheduleCapability>()
                                .orderByAsc(SelfMediaScheduleCapability::getPlatform))
                .stream()
                .collect(Collectors.toMap(
                        row -> normalize(row.getPlatform()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, SelfMediaScheduleCapabilityVO> capabilities = new LinkedHashMap<>();
        scheduleAdapterRouter.contracts().stream()
                .sorted(Comparator.comparing(SelfMediaPlatformCapabilityContract::platform))
                .forEach(contract -> {
                    String platform = normalize(contract.platform());
                    SelfMediaScheduleCapability row = rowsByPlatform.remove(platform);
                    SelfMediaScheduleCapabilityVO vo = row == null ? defaultVo(contract) : SelfMediaScheduleCapabilityVO.from(row);
                    vo.applyContract(contract);
                    applyScheduleRules(vo, contract);
                    capabilities.put(platform, vo);
                });
        rowsByPlatform.values().stream()
                .map(this::toVo)
                .filter(vo -> vo != null && StringUtils.hasText(vo.getPlatform()))
                .forEach(vo -> capabilities.putIfAbsent(normalize(vo.getPlatform()), vo));
        return capabilities.values()
                .stream()
                .sorted(Comparator.comparing(vo -> normalize(vo.getPlatform())))
                .toList();
    }

    public SelfMediaScheduleCapabilityVO detail(String platform) {
        currentUserService.requireCurrentUser();
        return toVo(mapper.selectByPlatform(normalizePlatform(platform)));
    }

    public PlatformScheduleReadiness readiness(String platform) {
        return readiness(platform, null);
    }

    /**
     * Resolve the execution strategy from the platform contract instead of
     * duplicating platform-specific switches in callers.
     */
    public String automaticScheduleStrategy(String platform) {
        String normalized = normalizePlatform(platform);
        return defaultStrategy(scheduleAdapterRouter.contract(normalized).orElse(null));
    }

    /**
     * Automatic distribution uses the same readiness boundary as the actual
     * execution channel. AdsPower immediate publishing is intentionally not
     * validated as a native platform scheduled-publish request.
     */
    public PlatformScheduleReadiness automaticPublishReadiness(String platform) {
        String normalized = normalizePlatform(platform);
        String strategy = automaticScheduleStrategy(normalized);
        SelfMediaPlatformCapabilityContract contract = scheduleAdapterRouter.contract(normalized).orElse(null);
        if (contract != null
                && STRATEGY_BACKEND_DELAYED_PUBLISH.equals(strategy)
                && SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION.equals(contract.publishChannel())) {
            return immediatePublishReadiness(normalized, strategy);
        }
        return readiness(normalized, strategy);
    }

    public PlatformScheduleReadiness readiness(String platform, String requestedStrategy) {
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
        SelfMediaPlatformCapabilityContract contract = scheduleAdapterRouter.contract(normalized).orElse(null);
        if (contract == null) {
            return PlatformScheduleReadiness.rejected("PLATFORM_CONTRACT_MISSING", "平台发布能力契约尚未接入");
        }
        String strategy = StringUtils.hasText(requestedStrategy) ? normalize(requestedStrategy) : normalize(row.getV1Strategy());
        if (!strategy.equals(normalize(row.getV1Strategy()))) {
            return PlatformScheduleReadiness.rejected(
                    "PLATFORM_SCHEDULE_STRATEGY_MISMATCH",
                    "请求排期策略与平台已验证策略不一致"
            );
        }
        if (STRATEGY_PLATFORM_SCHEDULE.equals(strategy) && !contract.supportsPlatformSchedule()) {
            return PlatformScheduleReadiness.rejected("PLATFORM_SCHEDULE_UNSUPPORTED", "平台契约声明不支持原生定时发布");
        }
        if (STRATEGY_BACKEND_DELAYED_PUBLISH.equals(strategy) && !contract.supportsBackendDelayedPublish()) {
            return PlatformScheduleReadiness.rejected("PLATFORM_BACKEND_DELAYED_UNSUPPORTED", "平台契约声明不支持后台延迟发布");
        }
        if (!List.of(STRATEGY_PLATFORM_SCHEDULE, STRATEGY_BACKEND_DELAYED_PUBLISH).contains(strategy)) {
            return PlatformScheduleReadiness.rejected("PLATFORM_SCHEDULE_STRATEGY_DISABLED", "平台 v1 策略未启用自动定时发布");
        }
        return PlatformScheduleReadiness.ready(row, contract);
    }

    /**
     * Immediate browser publishing is not a platform scheduled-publish request.
     * It may therefore use the backend execution queue even when the platform's
     * verified scheduled-publish strategy is different.
     */
    public PlatformScheduleReadiness immediatePublishReadiness(String platform, String requestedStrategy) {
        String normalized = normalizePlatform(platform);
        SelfMediaScheduleCapability row = mapper.selectByPlatform(normalized);
        if (row == null || !STATUS_VERIFIED.equals(normalize(row.getVerificationStatus()))) {
            return PlatformScheduleReadiness.rejected(
                    "PLATFORM_CAPABILITY_UNVERIFIED",
                    "平台浏览器发布能力尚未完成验证"
            );
        }
        SelfMediaPlatformCapabilityContract contract = scheduleAdapterRouter.contract(normalized).orElse(null);
        if (contract == null) {
            return PlatformScheduleReadiness.rejected("PLATFORM_CONTRACT_MISSING", "平台发布能力契约尚未接入");
        }
        String strategy = normalize(requestedStrategy);
        if (STRATEGY_BACKEND_DELAYED_PUBLISH.equals(strategy)
                && SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION.equals(contract.publishChannel())) {
            return PlatformScheduleReadiness.ready(row, contract);
        }
        return PlatformScheduleReadiness.rejected(
                "PLATFORM_BACKEND_DELAYED_UNSUPPORTED",
                "平台契约声明不支持指纹浏览器立即发布"
        );
    }

    public Map<String, Object> automationOptions(String platform) {
        String normalized = normalizePlatform(platform);
        SelfMediaScheduleCapability row = mapper.selectByPlatform(normalized);
        if (row == null || !StringUtils.hasText(row.getEvidenceJson())) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(row.getEvidenceJson());
            JsonNode options = root.path("automationOptions");
            if (!options.isObject()) {
                return Map.of();
            }
            return objectMapper.convertValue(options, new TypeReference<>() {
            });
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            return Map.of();
        }
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
        return toVo(row);
    }

    private SelfMediaScheduleCapabilityVO toVo(SelfMediaScheduleCapability row) {
        SelfMediaScheduleCapabilityVO vo = SelfMediaScheduleCapabilityVO.from(row);
        if (vo != null) {
            SelfMediaPlatformCapabilityContract contract = scheduleAdapterRouter.contract(vo.getPlatform()).orElse(null);
            vo.applyContract(contract);
            applyScheduleRules(vo, contract);
        }
        return vo;
    }

    private SelfMediaScheduleCapabilityVO defaultVo(SelfMediaPlatformCapabilityContract contract) {
        SelfMediaScheduleCapabilityVO vo = new SelfMediaScheduleCapabilityVO();
        vo.setPlatform(normalize(contract.platform()));
        vo.setVerificationStatus(STATUS_UNVERIFIED);
        vo.setSupportsSchedule(false);
        vo.setSaveCreatesSchedule(true);
        vo.setSupportsCancel(false);
        vo.setSupportsModify(false);
        vo.setSupportsPublishCheck(contract.supportsPublishCheck());
        vo.setV1Strategy(STRATEGY_PENDING);
        vo.applyContract(contract);
        applyScheduleRules(vo, contract);
        return vo;
    }

    private void applyScheduleRules(SelfMediaScheduleCapabilityVO vo, SelfMediaPlatformCapabilityContract contract) {
        if (vo == null || !StringUtils.hasText(vo.getPlatform())) {
            return;
        }
        String strategy = StringUtils.hasText(vo.getV1Strategy()) && !STRATEGY_PENDING.equals(normalize(vo.getV1Strategy()))
                ? normalize(vo.getV1Strategy())
                : defaultStrategy(contract);
        SelfMediaPlatformScheduleRules rules = scheduleAdapterRouter.rules(vo.getPlatform(), strategy);
        if (rules == null && contract != null) {
            rules = contract.scheduleRules();
        }
        if (rules == null) {
            rules = SelfMediaPlatformScheduleRules.defaults();
        }
        vo.setFillLeadMinutes(rules.fillLeadMinutes());
        vo.setMinRemainingMinutes(rules.minRemainingMinutes());
        vo.setMaxAttempts(rules.maxAttempts());
        vo.setMaxRemainingMinutes(rules.maxRemainingMinutes());
    }

    private String defaultStrategy(SelfMediaPlatformCapabilityContract contract) {
        if (contract == null) {
            return STRATEGY_PLATFORM_SCHEDULE;
        }
        return switch (contract.scheduleMode()) {
            case BACKEND_DELAYED -> STRATEGY_BACKEND_DELAYED_PUBLISH;
            case PLATFORM_NATIVE -> STRATEGY_PLATFORM_SCHEDULE;
            default -> STRATEGY_SEMI_AUTO;
        };
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
        if (!List.of(STRATEGY_PENDING, STRATEGY_PLATFORM_SCHEDULE, STRATEGY_BACKEND_DELAYED_PUBLISH, STRATEGY_SEMI_AUTO)
                .contains(strategy)) {
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
        SelfMediaPlatformCapabilityContract contract = scheduleAdapterRouter.contract(platform).orElse(null);
        if (STATUS_VERIFIED.equals(status) && contract == null) {
            fail("PLATFORM_CONTRACT_MISSING", "平台发布能力契约尚未接入");
        }
        if (STATUS_VERIFIED.equals(status) && Boolean.TRUE.equals(supportsSchedule)) {
            if (request.getMinDelayMinutes() == null || request.getMaxDelayMinutes() == null) {
                fail("DELAY_RANGE_REQUIRED", "支持定时发布的平台必须填写最小和最大可定时延迟");
            }
            if (!List.of(STRATEGY_PLATFORM_SCHEDULE, STRATEGY_BACKEND_DELAYED_PUBLISH).contains(strategy)) {
                fail("INVALID_VERIFIED_STRATEGY", "支持定时发布且验证通过的平台，v1 策略必须为自动定时策略");
            }
            if (STRATEGY_PLATFORM_SCHEDULE.equals(strategy) && contract != null && !contract.supportsPlatformSchedule()) {
                fail("PLATFORM_SCHEDULE_UNSUPPORTED", "平台契约声明不支持原生定时发布");
            }
            if (STRATEGY_BACKEND_DELAYED_PUBLISH.equals(strategy)
                    && contract != null
                    && !contract.supportsBackendDelayedPublish()) {
                fail("PLATFORM_BACKEND_DELAYED_UNSUPPORTED", "平台契约声明不支持后台延迟发布");
            }
        }
        return new ValidatedCapability(platform, status, supportsSchedule, strategy);
    }

    private String normalizePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            fail("INVALID_PLATFORM", "platform must not be blank");
        }
        String normalized = platform.trim().toLowerCase(Locale.ROOT);
        String publishPlatform = ArticlePromptChannels.normalizeSelfMediaPublishPlatform(normalized);
        return StringUtils.hasText(publishPlatform) ? publishPlatform : normalized;
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

    public record PlatformScheduleReadiness(boolean ready,
                                            String code,
                                            String message,
                                            SelfMediaPlatformCapabilityContract contract) {
        static PlatformScheduleReadiness ready(SelfMediaScheduleCapability row,
                                               SelfMediaPlatformCapabilityContract contract) {
            return new PlatformScheduleReadiness(true, null, null, contract);
        }

        static PlatformScheduleReadiness rejected(String code, String message) {
            return new PlatformScheduleReadiness(false, code, message, null);
        }
    }
}
