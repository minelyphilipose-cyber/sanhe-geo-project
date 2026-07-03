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
            WHERE operator_id = #{operatorId}
            ORDER BY last_seen_at DESC, updated_at DESC
            LIMIT #{limit}
            """)
    List<LocalAgentRuntimeStatus> selectRecentByOperatorId(@Param("operatorId") Long operatorId,
                                                           @Param("limit") int limit);

    @Select("""
            SELECT lar.*
            FROM local_agent_runtime_status lar
            JOIN local_agent_session las
              ON las.id = lar.session_id
             AND las.status = 'active'
            WHERE las.brand_id = #{brandId}
            ORDER BY lar.last_seen_at DESC, lar.updated_at DESC
            LIMIT 1
            """)
    LocalAgentRuntimeStatus selectLatestByBrandId(@Param("brandId") Long brandId);
}
