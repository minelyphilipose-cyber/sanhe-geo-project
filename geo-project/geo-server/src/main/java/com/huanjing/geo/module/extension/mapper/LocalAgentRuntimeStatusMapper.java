package com.huanjing.geo.module.extension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.extension.entity.LocalAgentRuntimeStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface LocalAgentRuntimeStatusMapper extends BaseMapper<LocalAgentRuntimeStatus> {

    @Update("""
            UPDATE local_agent_runtime_status
            SET session_id = #{row.sessionId},
                operator_id = #{row.operatorId},
                helper_version = #{row.helperVersion},
                protocol_version = #{row.protocolVersion},
                helper_name = #{row.helperName},
                adspower_api_ok = #{row.adspowerApiOk},
                adspower_api_base = #{row.adspowerApiBase},
                running_task_count = #{row.runningTaskCount},
                capacity = #{row.capacity},
                supported_platforms_json = #{row.supportedPlatformsJson},
                capabilities_json = #{row.capabilitiesJson},
                runtime_state = #{row.runtimeState},
                resource_metrics_json = #{row.resourceMetricsJson},
                last_cleanup_at = #{row.lastCleanupAt},
                helper_boot_id = #{row.helperBootId},
                policy_version = #{row.policyVersion},
                last_error_code = #{row.lastErrorCode},
                last_error_message = #{row.lastErrorMessage},
                last_seen_at = #{row.lastSeenAt},
                updated_at = #{row.updatedAt}
            WHERE machine_id = #{row.machineId}
              AND active_profile = #{row.activeProfile}
            """)
    int updateByMachineIdAndActiveProfile(@Param("row") LocalAgentRuntimeStatus row);

    @Select("""
            SELECT *
            FROM local_agent_runtime_status
            WHERE session_id = #{sessionId}
            ORDER BY last_seen_at DESC, updated_at DESC
            LIMIT 1
            """)
    LocalAgentRuntimeStatus selectLatestBySessionId(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT *
            FROM local_agent_runtime_status
            WHERE session_id = #{sessionId}
            ORDER BY last_seen_at DESC, updated_at DESC
            LIMIT 1
            FOR UPDATE
            """)
    LocalAgentRuntimeStatus selectLatestBySessionIdForUpdate(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT *
            FROM local_agent_runtime_status
            WHERE operator_id = #{operatorId}
            ORDER BY last_seen_at DESC, updated_at DESC
            LIMIT #{limit}
            """)
    List<LocalAgentRuntimeStatus> selectRecentByOperatorId(@Param("operatorId") Long operatorId,
                                                           @Param("limit") int limit);

    @Select("""
            SELECT DISTINCT lar.*, environment.brand_id AS brandId
            FROM local_agent_runtime_status lar
            JOIN local_agent_session las
              ON las.id = lar.session_id
             AND las.operator_id = lar.operator_id
             AND las.status = 'active'
             AND las.expires_at > CURRENT_TIMESTAMP
            JOIN browser_environment_agent_binding agent_binding
              ON agent_binding.machine_id = lar.machine_id
             AND agent_binding.active_profile = lar.active_profile
             AND agent_binding.status = 'active'
             AND agent_binding.bound_by = las.operator_id
            JOIN browser_environment environment
              ON environment.id = agent_binding.browser_environment_id
             AND environment.status = 'active'
             AND environment.deleted_at IS NULL
            WHERE environment.brand_id = #{brandId}
              AND (las.brand_id IS NULL OR las.brand_id = environment.brand_id)
            ORDER BY lar.last_seen_at DESC, lar.updated_at DESC
            LIMIT 1
            """)
    LocalAgentRuntimeStatus selectLatestByBrandId(@Param("brandId") Long brandId);

    @Select("""
            <script>
            SELECT DISTINCT lar.*, environment.brand_id AS brandId
            FROM local_agent_runtime_status lar
            JOIN local_agent_session las
              ON las.id = lar.session_id
             AND las.operator_id = lar.operator_id
             AND las.status = 'active'
             AND las.expires_at > CURRENT_TIMESTAMP
            JOIN browser_environment_agent_binding agent_binding
              ON agent_binding.machine_id = lar.machine_id
             AND agent_binding.active_profile = lar.active_profile
             AND agent_binding.status = 'active'
             AND agent_binding.bound_by = las.operator_id
            JOIN browser_environment environment
              ON environment.id = agent_binding.browser_environment_id
             AND environment.status = 'active'
             AND environment.deleted_at IS NULL
            WHERE environment.brand_id IN
            <foreach collection="brandIds" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
              AND (las.brand_id IS NULL OR las.brand_id = environment.brand_id)
            ORDER BY environment.brand_id ASC, lar.last_seen_at DESC, lar.updated_at DESC
            </script>
            """)
    List<LocalAgentRuntimeStatus> selectLatestByBrandIds(@Param("brandIds") List<Long> brandIds);
}
