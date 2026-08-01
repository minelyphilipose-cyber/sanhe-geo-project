package com.huanjing.geo.module.extension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.extension.entity.LocalAgentBrowserMetricSample;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface LocalAgentBrowserMetricSampleMapper extends BaseMapper<LocalAgentBrowserMetricSample> {

    @Insert("""
            INSERT INTO local_agent_browser_metric_sample (
              local_agent_session_id, machine_id, active_profile, helper_boot_id,
              browser_environment_id, environment_key, provider_profile_id,
              browser_session_epoch, observed_at, observed_at_epoch_ms,
              observation_status, last_successful_observed_at, failed_probe_duration_ms,
              helper_uptime_seconds, retained_task_count, active_task_count,
              claimed_total, execution_claimed_total, execution_started_total,
              publish_check_claimed_total, publish_check_started_total,
              completed_total, failed_total,
              total_target_count, managed_target_count, operator_target_count,
              unknown_target_count, process_rss_bytes, process_cpu_percent,
              process_handle_count, cdp_connect_ms, cdp_browser_get_version_ms,
              cdp_browser_pages_ms, network_enable_timeout_count,
              cdp_disconnect_count, extension_injection_error_count,
              page_timeout_count, cdp_protocol_timeout_count, metrics_json, created_at
            ) VALUES (
              #{row.localAgentSessionId}, #{row.machineId}, #{row.activeProfile}, #{row.helperBootId},
              #{row.browserEnvironmentId}, #{row.environmentKey}, #{row.providerProfileId},
              #{row.browserSessionEpoch}, #{row.observedAt}, #{row.observedAtEpochMs},
              #{row.observationStatus}, #{row.lastSuccessfulObservedAt}, #{row.failedProbeDurationMs},
              #{row.helperUptimeSeconds}, #{row.retainedTaskCount}, #{row.activeTaskCount},
              #{row.claimedTotal}, #{row.executionClaimedTotal}, #{row.executionStartedTotal},
              #{row.publishCheckClaimedTotal}, #{row.publishCheckStartedTotal},
              #{row.completedTotal}, #{row.failedTotal},
              #{row.totalTargetCount}, #{row.managedTargetCount}, #{row.operatorTargetCount},
              #{row.unknownTargetCount}, #{row.processRssBytes}, #{row.processCpuPercent},
              #{row.processHandleCount}, #{row.cdpConnectMs}, #{row.cdpBrowserGetVersionMs},
              #{row.cdpBrowserPagesMs}, #{row.networkEnableTimeoutCount},
              #{row.cdpDisconnectCount}, #{row.extensionInjectionErrorCount},
              #{row.pageTimeoutCount}, #{row.cdpProtocolTimeoutCount}, #{row.metricsJson}, #{row.createdAt}
            )
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(@Param("row") LocalAgentBrowserMetricSample row);

    @Delete("""
            DELETE FROM local_agent_browser_metric_sample
            WHERE created_at < #{cutoff}
            LIMIT #{limit}
            """)
    int deleteExpiredBatch(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
