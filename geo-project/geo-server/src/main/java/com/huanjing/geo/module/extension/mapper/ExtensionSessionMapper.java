package com.huanjing.geo.module.extension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.extension.entity.ExtensionSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ExtensionSessionMapper extends BaseMapper<ExtensionSession> {

    @Select("""
            SELECT *
            FROM extension_session
            WHERE token_lookup_hash = #{lookupHash}
              AND status = 'active'
            LIMIT 1
            """)
    ExtensionSession selectActiveByLookupHash(@Param("lookupHash") String lookupHash);

    @Update("""
            UPDATE extension_session
            SET status = 'revoked',
                revoked_at = #{revokedAt},
                revoked_by = #{revokedBy}
            WHERE id = #{id}
              AND status = 'active'
            """)
    int revokeActive(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt, @Param("revokedBy") Long revokedBy);

    @Update("""
            UPDATE extension_session
            SET last_seen_at = #{lastSeenAt},
                extension_version = #{extensionVersion},
                user_agent = #{userAgent}
            WHERE id = #{id}
              AND status = 'active'
            """)
    int touchActive(
            @Param("id") Long id,
            @Param("lastSeenAt") LocalDateTime lastSeenAt,
            @Param("extensionVersion") String extensionVersion,
            @Param("userAgent") String userAgent
    );
}
