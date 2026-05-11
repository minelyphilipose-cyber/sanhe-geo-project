package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.AuthorityMediaPreviewToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AuthorityMediaPreviewTokenMapper extends BaseMapper<AuthorityMediaPreviewToken> {

    @Select("SELECT * FROM authority_media_preview_token WHERE token_hash = #{tokenHash} LIMIT 1")
    AuthorityMediaPreviewToken selectByTokenHash(@Param("tokenHash") String tokenHash);

    @Update("""
            UPDATE authority_media_preview_token
            SET revoked_at = #{revokedAt}
            WHERE order_id = #{orderId}
              AND revoked_at IS NULL
            """)
    int revokeByOrderId(@Param("orderId") Long orderId, @Param("revokedAt") LocalDateTime revokedAt);

    @Update("""
            UPDATE authority_media_preview_token
            SET access_count = access_count + 1,
                last_accessed_at = #{accessedAt},
                last_access_ip = #{ip},
                last_user_agent = #{userAgent}
            WHERE id = #{id}
            """)
    int recordAccess(@Param("id") Long id,
                     @Param("accessedAt") LocalDateTime accessedAt,
                     @Param("ip") String ip,
                     @Param("userAgent") String userAgent);
}
