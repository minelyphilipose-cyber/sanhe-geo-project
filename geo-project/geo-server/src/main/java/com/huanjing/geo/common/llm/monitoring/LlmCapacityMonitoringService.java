package com.huanjing.geo.common.llm.monitoring;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.llm.alert.LlmCapacityAlertProperties;
import com.huanjing.geo.common.llm.measurement.LlmCapacityMinuteMetric;
import com.huanjing.geo.common.llm.monitoring.dto.HunyuanCapacityVO;
import com.huanjing.geo.common.llm.monitoring.dto.LlmRuntimeConfigVO;
import com.huanjing.geo.common.llm.pool.LlmPoolProperties;
import com.huanjing.geo.common.llm.router.LlmRoutingRuntimeConfig;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import com.huanjing.geo.module.dispatch.dto.PollPlatformSliceProgressRow;
import com.huanjing.geo.module.mobiledashboard.service.MobileEntityJudgeRuntimeConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.entity.SystemAlert;
import com.huanjing.geo.module.system.mapper.SystemAlertMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.common.llm.LlmCapacityView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LlmCapacityMonitoringService {
    private static final Set<String> INTERNAL_MONITOR_ROLES = Set.of("super_admin", "manager", "delivery_manager", "operator");
    private static final String HUNYUAN_ACTIVE_DEDUPE = "llm_capacity:hunyuan:active_peak";
    private static final String HUNYUAN_PROGRESS_DEDUPE = "llm_capacity:hunyuan:slice_progress";
    private static final String HUNYUAN_EXHAUSTED_DEDUPE = "llm_capacity:hunyuan:capacity_retry_exhausted";

    private final CurrentUserService currentUserService;
    private final DispatchProperties dispatchProperties;
    private final LlmPoolProperties llmPoolProperties;
    private final MobileEntityJudgeRuntimeConfig mobileJudgeRuntimeConfig;
    private final LlmRoutingRuntimeConfig routingRuntimeConfig;
    private final LlmCapacityAlertProperties alertProperties;
    private final LlmCapacityQueryService capacityQueryService;
    private final LlmCapacityView llmCapacityView;
    private final SystemAlertMapper systemAlertMapper;

    public LlmRuntimeConfigVO runtimeConfig() {
        ensureMonitorAccess();
        LlmRuntimeConfigVO vo = new LlmRuntimeConfigVO();
        LlmRuntimeConfigVO.DispatchConfig dispatch = vo.getDispatch();
        dispatch.setQuestionPollCycleDays(dispatchProperties.getQuestionPollCycleDays());
        dispatch.setWorkerPollConcurrency(dispatchProperties.getWorkerPollConcurrency());
        dispatch.setWorkerPopAdmissionEnabled(dispatchProperties.isWorkerPopAdmissionEnabled());
        dispatch.setWorkerMaxInFlight(dispatchProperties.getWorkerMaxInFlight());
        dispatch.setWorkerPermitGovernorEnabled(dispatchProperties.isWorkerPermitGovernorEnabled());
        dispatch.setWorkerPermitGovernorBusyRatio(dispatchProperties.getWorkerPermitGovernorBusyRatio());
        dispatch.setCapacityFailureClassificationEnabled(dispatchProperties.isCapacityFailureClassificationEnabled());
        dispatch.setResourceBusyRetryAfterEnabled(dispatchProperties.isResourceBusyRetryAfterEnabled());
        dispatch.setResourceBusyRetryMinSeconds(dispatchProperties.getResourceBusyRetryMinSeconds());
        dispatch.setResourceBusyRetryJitterSeconds(dispatchProperties.getResourceBusyRetryJitterSeconds());
        dispatch.setResourceBusyRetryMaxSeconds(dispatchProperties.getResourceBusyRetryMaxSeconds());
        dispatch.setResourceBusyMaxAttempts(dispatchProperties.getResourceBusyMaxAttempts());
        LlmRuntimeConfigVO.StaggerConfig stagger = dispatch.getStagger();
        stagger.setEnabled(dispatchProperties.getStagger().isEnabled());
        stagger.setTaskTypes(dispatchProperties.getStagger().getTaskTypes());
        stagger.setWindowMinutes(dispatchProperties.getStagger().getWindowMinutes());
        stagger.setMaxDelayMinutes(dispatchProperties.getStagger().getMaxDelayMinutes());
        stagger.setJitterSeconds(dispatchProperties.getStagger().getJitterSeconds());
        stagger.setCapJitterSeconds(dispatchProperties.getStagger().getCapJitterSeconds());
        stagger.setMaxQueueSize(dispatchProperties.getStagger().getMaxQueueSize());
        stagger.setOverflowPolicy(dispatchProperties.getStagger().getOverflowPolicy());
        stagger.setPlatforms(dispatchProperties.getStagger().getPlatforms());

        LlmRuntimeConfigVO.LlmPoolConfig pool = vo.getLlmPool();
        pool.setEnabled(llmPoolProperties.isEnabled());
        pool.setGlobalConcurrency(llmPoolProperties.getGlobalConcurrency());
        pool.setBlockingAcquireFailFastEnabled(llmPoolProperties.isBlockingAcquireFailFastEnabled());
        pool.setBlockingAcquireFailFastFeatures(llmPoolProperties.getBlockingAcquireFailFastFeatures());
        pool.setFeatureConcurrency(llmPoolProperties.getFeatureConcurrency());

        LlmRuntimeConfigVO.MobileJudgeConfig mobileJudge = vo.getMobileJudge();
        mobileJudge.setEnabled(mobileJudgeRuntimeConfig.isEnabled());
        mobileJudge.setMaxProjectsPerRun(mobileJudgeRuntimeConfig.getMaxProjectsPerRun());
        mobileJudge.setPerProjectLimit(mobileJudgeRuntimeConfig.getPerProjectLimit());
        mobileJudge.setWorkerMs(mobileJudgeRuntimeConfig.getWorkerMs());
        mobileJudge.setPlatformCodes(mobileJudgeRuntimeConfig.platformCodeList());

        vo.getArticleRouting().setExcludedPlatformCodes(routingRuntimeConfig.articleExcludedPlatformCodeList());
        return vo;
    }

    public HunyuanCapacityVO hunyuanCapacity() {
        ensureMonitorAccess();
        LocalDateTime now = LocalDateTime.now(LlmCapacityQueryService.ZONE);
        LocalDateTime since = now.minusMinutes(capacityQueryService.scanWindowMinutes());
        List<String> platformCodes = capacityQueryService.hunyuanPlatformCodes();
        LlmCapacityQueryService.PlatformLimitRatioSnapshot limitRatio = capacityQueryService.platformLimitRatio(since, platformCodes);
        LlmCapacityQueryService.PollSliceProgressSnapshot progress = capacityQueryService.platformSliceProgress(now.toLocalDate(), "A", platformCodes, now);

        HunyuanCapacityVO vo = new HunyuanCapacityVO();
        vo.setPlatformCodes(platformCodes);
        vo.setActiveLimit(alertProperties.getHunyuan().getActivePeakThreshold());
        vo.setCurrentActive(platformCodes.stream().mapToLong(llmCapacityView::activePlatformCount).sum());
        vo.setActivePeak(loadActivePeak(since, platformCodes));
        vo.setTotalCount(limitRatio.totalCount());
        vo.setLimitedCount(limitRatio.limitedCount());
        vo.setLimitRatio(limitRatio.ratio());
        vo.setLimitRatioThreshold(alertProperties.getRateLimitRatioThreshold());
        vo.setLimitCategories(LlmCapacityQueryService.LIMIT_CATEGORIES.stream().toList());
        vo.setSliceProgress(toSliceProgress(progress));
        HunyuanCapacityVO.RetryExhausted exhausted = new HunyuanCapacityVO.RetryExhausted();
        exhausted.setCount(capacityQueryService.capacityRetryExhaustedCount(since));
        exhausted.setWindowStart(since);
        exhausted.setWindowEnd(now);
        vo.setRetryExhausted(exhausted);
        vo.setOpenAlert(loadOpenCapacityAlert());
        return vo;
    }

    private long loadActivePeak(LocalDateTime since, List<String> platformCodes) {
        List<LlmCapacityMinuteMetric> metrics = capacityQueryService.loadRecentMetrics(since);
        return metrics.stream()
                .filter(metric -> platformCodes.contains(capacityQueryService.normalize(metric.getPlatformCode())))
                .mapToLong(metric -> capacityQueryService.safe(metric.getPlatformActivePeak()))
                .max()
                .orElse(0L);
    }

    private HunyuanCapacityVO.SliceProgress toSliceProgress(LlmCapacityQueryService.PollSliceProgressSnapshot progress) {
        HunyuanCapacityVO.SliceProgress vo = new HunyuanCapacityVO.SliceProgress();
        vo.setBatchDate(progress.batchDate());
        vo.setQuestionTier(progress.questionTier());
        vo.setExpectedCount(progress.expectedCount());
        vo.setCompletedCount(progress.completedCount());
        vo.setFailedCount(progress.failedCount());
        vo.setResourceWaitCount(progress.resourceWaitCount());
        vo.setActualProgress(progress.actualProgress());
        vo.setExpectedProgress(progress.expectedProgress());
        vo.setLag(progress.lag());
        vo.setWindowMinutes(progress.windowMinutes());
        vo.setSliceStart(progress.sliceStart());
        vo.setRows(progress.rows());
        return vo;
    }

    private HunyuanCapacityVO.OpenAlert loadOpenCapacityAlert() {
        List<String> keys = List.of(HUNYUAN_ACTIVE_DEDUPE, HUNYUAN_PROGRESS_DEDUPE, HUNYUAN_EXHAUSTED_DEDUPE, "llm_capacity:hunyuan:rate_limit_ratio");
        List<SystemAlert> alerts = systemAlertMapper.selectList(new LambdaQueryWrapper<SystemAlert>()
                .in(SystemAlert::getDedupeKey, keys)
                .eq(SystemAlert::getIsResolved, false));
        SystemAlert alert = alerts.stream()
                .max(Comparator.comparing(SystemAlert::getCreatedAt))
                .orElse(null);
        HunyuanCapacityVO.OpenAlert vo = new HunyuanCapacityVO.OpenAlert();
        vo.setOpen(alert != null);
        if (alert != null) {
            vo.setDedupeKey(alert.getDedupeKey());
            vo.setAlertType(alert.getAlertType());
            vo.setSeverity(alert.getSeverity());
            vo.setMessage(alert.getMessage());
        }
        return vo;
    }

    private void ensureMonitorAccess() {
        SysUser user = currentUserService.requireCurrentUser();
        String role = user == null ? null : user.getRole();
        if (!StringUtils.hasText(role) || !INTERNAL_MONITOR_ROLES.contains(role.trim().toLowerCase(Locale.ROOT))) {
            currentUserService.ensurePermission("dispatch.monitor.view");
        }
    }
}
