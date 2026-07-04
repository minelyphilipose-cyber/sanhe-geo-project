package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.credential.entity.SelfMediaCookieCredential;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SelfMediaCookieCredentialMapper extends BaseMapper<SelfMediaCookieCredential> {

    @Select("""
            SELECT id, self_media_account_id, brand_id, platform, version,
                   master_key_id, cipher_alg, cookie_iv_base64, aad_context, user_agent,
                   captured_fingerprint_json, required_cookie_status,
                   captured_by, captured_at, valid_from, valid_until, expires_at, expiry_source,
                   destroyed_at, created_at
            FROM self_media_cookie_credential
            WHERE self_media_account_id = #{accountId}
              AND valid_until IS NULL
              AND destroyed_at IS NULL
            ORDER BY version DESC
            LIMIT 1
            """)
    SelfMediaCookieCredential selectActiveMetaByAccountId(@Param("accountId") Long accountId);

    @Select("""
            <script>
            SELECT id, self_media_account_id, brand_id, platform, version,
                   master_key_id, cipher_alg, cookie_iv_base64, aad_context, user_agent,
                   captured_fingerprint_json, required_cookie_status,
                   captured_by, captured_at, valid_from, valid_until, expires_at, expiry_source,
                   destroyed_at, created_at
            FROM self_media_cookie_credential
            WHERE valid_until IS NULL
              AND destroyed_at IS NULL
              AND self_media_account_id IN
              <foreach collection="accountIds" item="accountId" open="(" separator="," close=")">
                #{accountId}
              </foreach>
            ORDER BY self_media_account_id ASC, version DESC
            </script>
            """)
    List<SelfMediaCookieCredential> selectActiveMetaByAccountIds(@Param("accountIds") List<Long> accountIds);

    @Select("""
            SELECT *
            FROM self_media_cookie_credential
            WHERE self_media_account_id = #{accountId}
              AND valid_until IS NULL
              AND destroyed_at IS NULL
            ORDER BY version DESC
            LIMIT 1
            """)
    SelfMediaCookieCredential selectActiveFullByAccountId(@Param("accountId") Long accountId);

    @Select("""
            SELECT id, version
            FROM self_media_cookie_credential
            WHERE self_media_account_id = #{accountId}
              AND valid_until IS NULL
              AND destroyed_at IS NULL
            ORDER BY version DESC
            LIMIT 1
            FOR UPDATE
            """)
    SelfMediaCookieCredential selectLatestByAccountIdForUpdate(@Param("accountId") Long accountId);

    @Select("""
            SELECT id, self_media_account_id, brand_id, platform, version,
                   master_key_id, cipher_alg, cookie_iv_base64, aad_context, user_agent,
                   captured_fingerprint_json, required_cookie_status,
                   captured_by, captured_at, valid_from, valid_until, expires_at, expiry_source,
                   destroyed_at, created_at
            FROM self_media_cookie_credential
            WHERE self_media_account_id = #{accountId}
            ORDER BY version DESC
            """)
    List<SelfMediaCookieCredential> selectMetaHistoryByAccountId(@Param("accountId") Long accountId);

    @Update("""
            UPDATE self_media_cookie_credential
            SET valid_until = #{validUntil}
            WHERE self_media_account_id = #{accountId}
              AND valid_until IS NULL
              AND destroyed_at IS NULL
            """)
    int closeActiveVersions(@Param("accountId") Long accountId, @Param("validUntil") LocalDateTime validUntil);

    @Update("""
            UPDATE self_media_cookie_credential
            SET valid_until = IFNULL(valid_until, #{destroyedAt}),
                destroyed_at = #{destroyedAt},
                cookies_ciphertext = '',
                cookie_iv_base64 = '',
                encrypted_dek = ''
            WHERE self_media_account_id = #{accountId}
              AND destroyed_at IS NULL
            """)
    int destroyByAccountId(@Param("accountId") Long accountId, @Param("destroyedAt") LocalDateTime destroyedAt);
}
