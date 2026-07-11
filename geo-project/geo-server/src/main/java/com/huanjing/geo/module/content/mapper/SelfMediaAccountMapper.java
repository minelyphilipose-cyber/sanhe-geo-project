package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SelfMediaAccountMapper extends BaseMapper<SelfMediaAccount> {

    @Select("""
            SELECT id
            FROM self_media_account
            WHERE id = #{id}
              AND deleted_at IS NULL
            FOR UPDATE
            """)
    Long lockById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT id, brand_id, platform, account_name, account_identity, status
            FROM self_media_account
            WHERE deleted_at IS NULL
              AND brand_id IN
              <foreach collection="brandIds" item="brandId" open="(" separator="," close=")">
                #{brandId}
              </foreach>
            ORDER BY updated_at DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<SelfMediaAccount> selectExtensionAccountsByBrandIds(@Param("brandIds") List<Long> brandIds,
                                                             @Param("limit") int limit);

    @Update("""
            UPDATE self_media_account
            SET last_login_verification_warning = #{warning},
                recommended_reverify_at = #{recommendedReverifyAt}
            WHERE id = #{id}
              AND deleted_at IS NULL
            """)
    int updateNullableLoginHealthFields(@Param("id") Long id,
                                        @Param("warning") String warning,
                                        @Param("recommendedReverifyAt") LocalDateTime recommendedReverifyAt);

    @Select("""
            SELECT id, brand_id, platform, platform_account_id, account_name, account_identity, status, auth_mode,
                   scope_json, refresh_token_cipher, credential_key_version, avatar_url, qrcode_url,
                   last_auth_checked_at, last_auth_error, created_at, updated_at, deleted_at, deleted_by
            FROM self_media_account
            WHERE platform = #{platform}
              AND platform_account_id = #{platformAccountId}
            LIMIT 1
            """)
    SelfMediaAccount selectByPlatformAccountIncludingDeleted(@Param("platform") String platform,
                                                            @Param("platformAccountId") String platformAccountId);

    @Update("""
            UPDATE self_media_account
            SET brand_id = #{account.brandId},
                platform = #{account.platform},
                platform_account_id = #{account.platformAccountId},
                account_name = #{account.accountName},
                account_identity = #{account.accountIdentity},
                status = #{account.status},
                auth_mode = #{account.authMode},
                scope_json = #{account.scopeJson},
                refresh_token_cipher = #{account.refreshTokenCipher},
                credential_key_version = #{account.credentialKeyVersion},
                avatar_url = #{account.avatarUrl},
                qrcode_url = #{account.qrcodeUrl},
                last_auth_checked_at = #{account.lastAuthCheckedAt},
                last_auth_error = #{account.lastAuthError},
                updated_at = #{now},
                deleted_at = NULL,
                deleted_by = NULL
            WHERE id = #{account.id}
            """)
    int restoreWechatAuthorization(@Param("account") SelfMediaAccount account,
                                   @Param("now") LocalDateTime now);
}
