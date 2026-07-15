package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.BrowserEnvironment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface BrowserEnvironmentMapper extends BaseMapper<BrowserEnvironment> {

    @Select("""
            SELECT *
            FROM browser_environment
            WHERE brand_id = #{brandId}
              AND provider = #{provider}
              AND (environment_key = #{environmentKey} OR provider_profile_id = #{providerProfileId})
            ORDER BY (deleted_at IS NULL) DESC, id ASC
            LIMIT 1
            """)
    BrowserEnvironment selectOldestByIdentityIncludingDeleted(@Param("brandId") Long brandId,
                                                               @Param("provider") String provider,
                                                               @Param("environmentKey") String environmentKey,
                                                               @Param("providerProfileId") String providerProfileId);

    @Update("UPDATE browser_environment SET deleted_at = NULL WHERE id = #{id}")
    int restoreDeletedById(@Param("id") Long id);

    @Update("""
            INSERT INTO browser_environment_agent_binding (
              browser_environment_id,
              machine_id,
              active_profile,
              bound_session_id,
              status,
              binding_version,
              bound_by,
              bound_at,
              last_verified_at,
              created_at,
              updated_at
            ) VALUES (
              #{browserEnvironmentId},
              #{machineId},
              #{activeProfile},
              #{sessionId},
              'active',
              1,
              #{operatorId},
              #{now},
              #{lastVerifiedAt},
              #{now},
              #{now}
            )
            ON DUPLICATE KEY UPDATE
              machine_id = VALUES(machine_id),
              active_profile = VALUES(active_profile),
              bound_session_id = VALUES(bound_session_id),
              status = 'active',
              binding_version = binding_version + 1,
              bound_by = VALUES(bound_by),
              bound_at = VALUES(bound_at),
              last_verified_at = VALUES(last_verified_at),
              updated_at = VALUES(updated_at)
            """)
    int upsertLocalAgentBinding(@Param("browserEnvironmentId") Long browserEnvironmentId,
                                @Param("machineId") String machineId,
                                @Param("activeProfile") String activeProfile,
                                @Param("sessionId") Long sessionId,
                                @Param("operatorId") Long operatorId,
                                @Param("lastVerifiedAt") LocalDateTime lastVerifiedAt,
                                @Param("now") LocalDateTime now);

    @Update("""
            UPDATE browser_environment_agent_binding
            SET status = 'disabled',
                bound_session_id = NULL,
                binding_version = binding_version + 1,
                updated_at = #{now}
            WHERE browser_environment_id = #{browserEnvironmentId}
              AND status = 'active'
            """)
    int disableLocalAgentBinding(@Param("browserEnvironmentId") Long browserEnvironmentId,
                                 @Param("now") LocalDateTime now);
}
