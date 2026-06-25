package com.huanjing.geo.common.llm.alert;

import com.huanjing.geo.common.llm.measurement.LlmCallObservationMapper;
import com.huanjing.geo.common.llm.measurement.LlmCapacityMinuteMetric;
import com.huanjing.geo.common.llm.measurement.LlmCapacityMinuteMetricMapper;
import com.huanjing.geo.common.llm.monitoring.LlmCapacityQueryService;
import com.huanjing.geo.common.llm.pool.LlmPoolProperties;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import com.huanjing.geo.module.dispatch.dto.PollPlatformSliceProgressRow;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardMapper;
import com.huanjing.geo.module.system.service.SystemAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmCapacityAlertScannerTest {

    private LlmCapacityAlertProperties alertProperties;
    private LlmCapacityMinuteMetricMapper minuteMetricMapper;
    private LlmCallObservationMapper observationMapper;
    private PollBatchShardMapper pollBatchShardMapper;
    private DispatchTaskMapper dispatchTaskMapper;
    private SystemAlertService systemAlertService;
    private LlmCapacityAlertScanner scanner;

    @BeforeEach
    void setUp() {
        alertProperties = new LlmCapacityAlertProperties();
        alertProperties.getHunyuan().setActivePeakSustainedMinutes(3);
        minuteMetricMapper = mock(LlmCapacityMinuteMetricMapper.class);
        observationMapper = mock(LlmCallObservationMapper.class);
        pollBatchShardMapper = mock(PollBatchShardMapper.class);
        dispatchTaskMapper = mock(DispatchTaskMapper.class);
        systemAlertService = mock(SystemAlertService.class);
        LlmPoolProperties poolProperties = new LlmPoolProperties();
        DispatchProperties dispatchProperties = new DispatchProperties();
        when(observationMapper.aggregateLimitRatio(any(), any())).thenReturn(List.of());
        when(pollBatchShardMapper.aggregatePlatformSliceProgress(any(), eq("A"), any())).thenReturn(List.of());
        when(dispatchTaskMapper.selectCount(any())).thenReturn(0L);
        scanner = new LlmCapacityAlertScanner(
                alertProperties,
                systemAlertService,
                poolProperties,
                new LlmCapacityQueryService(
                        alertProperties,
                        minuteMetricMapper,
                        observationMapper,
                        pollBatchShardMapper,
                        dispatchTaskMapper,
                        dispatchProperties
                )
        );
    }

    @Test
    void sustainedHunyuanActivePeakCreatesDecisionAlert() {
        when(minuteMetricMapper.selectList(any())).thenReturn(hunyuanPeakRows(3, 5));

        scanner.scan();

        verify(systemAlertService).createOrRefreshRecipientAlert(
                eq("LLM_CAPACITY_HUNYUAN_ACTIVE_PEAK"),
                eq("critical"),
                eq("llm_capacity_alert"),
                eq("混元/元宝平台 active peak 持续顶格"),
                any(Map.class),
                eq(null),
                eq("super_admin"),
                eq("llm_capacity:hunyuan:active_peak")
        );
        verify(systemAlertService, never()).resolveOpenByDedupeKey(eq("llm_capacity:hunyuan:active_peak"), any());
    }

    @Test
    void finalHunyuanSliceShortfallCreatesCriticalAlert() {
        when(minuteMetricMapper.selectList(any())).thenReturn(List.of());
        PollPlatformSliceProgressRow row = new PollPlatformSliceProgressRow();
        row.setPlatformCode("hunyuan");
        row.setExpectedCount(100L);
        row.setCompletedCount(80L);
        row.setFailedCount(0L);
        row.setResourceWaitCount(2L);
        when(pollBatchShardMapper.aggregatePlatformSliceProgress(any(), eq("A"), any())).thenReturn(List.of(row));

        scanner.scan();

        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(systemAlertService).createOrRefreshRecipientAlert(
                eq("LLM_CAPACITY_HUNYUAN_SLICE_PROGRESS"),
                eq("critical"),
                eq("llm_capacity_alert"),
                eq("混元/元宝当日切片窗口结束后完成度低于红线"),
                contextCaptor.capture(),
                eq(null),
                eq("super_admin"),
                eq("llm_capacity:hunyuan:slice_progress")
        );
        assertEquals("decision", contextCaptor.getValue().get("category"));
        assertEquals(100L, contextCaptor.getValue().get("expectedCount"));
        assertEquals(80L, contextCaptor.getValue().get("completedCount"));
    }

    private List<LlmCapacityMinuteMetric> hunyuanPeakRows(int minutes, long peak) {
        LocalDateTime base = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        List<LlmCapacityMinuteMetric> rows = new ArrayList<>();
        for (int i = 0; i < minutes; i++) {
            LlmCapacityMinuteMetric metric = new LlmCapacityMinuteMetric();
            metric.setBucketMinute(base.minusMinutes(i));
            metric.setPlatformCode("hunyuan");
            metric.setFeature("all");
            metric.setPlatformActivePeak(peak);
            rows.add(metric);
        }
        return rows;
    }
}
