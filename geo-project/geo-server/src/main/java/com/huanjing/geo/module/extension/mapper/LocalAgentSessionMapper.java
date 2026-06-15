package com.huanjing.geo.module.extension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.extension.entity.LocalAgentSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LocalAgentSessionMapper extends BaseMapper<LocalAgentSession> {

    @Select("""
            SELECT *
            FROM local_agent_session
            WHERE access_token_lookup_hash = #{lookupHash}
              AND status = 'active'
            LIMIT 1
            """)
    LocalAgentSession selectActiveByLookupHash(@Param("lookupHash") String lookupHash);

    @Select("""
            SELECT *
            FROM local_agent_session
            WHERE brand_id = #{brandId}
              AND status = 'active'
            ORDER BY last_seen_at DESC, updated_at DESC
            """)
    List<LocalAgentSession> selectActiveByBrandId(@Param("brandId") Long brandId);

    @Select("""
            SELECT *
            FROM local_agent_session
            WHERE operator_id = #{operatorId}
              AND status = 'active'
            ORDER BY last_seen_at DESC, updated_at DESC
            """)
    List<LocalAgentSession> selectActiveByOperatorId(@Param("operatorId") Long operatorId);

    @Select("""
            SELECT COUNT(1)
            FROM local_agent_session
            WHERE status = 'active'
              AND expires_at > #{now}
            """)
    long countActiveSessions(@Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(1)
            FROM local_agent_session
            WHERE status = 'active'
              AND expires_at > #{now}
              AND last_seen_at IS NOT NULL
              AND last_seen_at >= #{onlineSince}
            """)
    long countOnlineSessions(@Param("now") LocalDateTime now,
                             @Param("onlineSince") LocalDateTime onlineSince);

    @Select("""
            SELECT COUNT(1)
            FROM local_agent_session
            WHERE operator_id = #{operatorId}
              AND status = 'active'
              AND expires_at > #{now}
              AND last_seen_at IS NOT NULL
              AND last_seen_at >= #{onlineSince}
            """)
    long countOnlineSessionsByOperator(@Param("operatorId") Long operatorId,
                                       @Param("now") LocalDateTime now,
                                       @Param("onlineSince") LocalDateTime onlineSince);

    @Update("""
            UPDATE local_agent_session
            SET status = 'revoked',
                revoked_at = #{revokedAt},
                revoked_by = #{revokedBy},
                updated_at = #{revokedAt}
            WHERE operator_id = #{operatorId}
              AND status = 'active'
            """)
    int revokeActiveByOperatorId(@Param("operatorId") Long operatorId,
                                 @Param("revokedAt") LocalDateTime revokedAt,
                                 @Param("revokedBy") Long revokedBy);

    @Update("""
            UPDATE local_agent_session
            SET last_seen_at = #{lastSeenAt},
                user_agent = #{userAgent},
                updated_at = #{lastSeenAt}
            WHERE id = #{id}
              AND status = 'active'
            """)
    int touchActive(@Param("id") Long id,
                    @Param("lastSeenAt") LocalDateTime lastSeenAt,
                    @Param("userAgent") String userAgent);

    @Update("""
            UPDATE local_agent_session
            SET status = 'revoked',
                revoked_at = #{revokedAt},
                revoked_by = #{revokedBy},
                updated_at = #{revokedAt}
            WHERE id = #{id}
              AND status = 'active'
            """)
    int revokeActive(@Param("id") Long id,
                     @Param("revokedAt") LocalDateTime revokedAt,
                     @Param("revokedBy") Long revokedBy);
}
