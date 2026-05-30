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
