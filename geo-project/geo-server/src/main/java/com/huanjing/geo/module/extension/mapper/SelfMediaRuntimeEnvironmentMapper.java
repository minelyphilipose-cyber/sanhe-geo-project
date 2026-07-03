package com.huanjing.geo.module.extension.mapper;

import com.huanjing.geo.module.extension.dto.SelfMediaRuntimeEnvironmentBaseRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SelfMediaRuntimeEnvironmentMapper {

    @Select("""
            <script>
            SELECT
                bea.brand_id AS brandId,
                b.brand_name AS brandName,
                bea.platform AS platform,
                sma.id AS selfMediaAccountId,
                sma.account_name AS accountName,
                sma.platform_account_id AS platformAccountId,
                be.id AS browserEnvironmentId,
                be.name AS environmentName,
                be.environment_key AS environmentKey,
                be.provider_profile_id AS providerProfileId,
                bea.id AS browserEnvironmentAccountId,
                bea.login_status AS loginStatus,
                bea.expected_account_name AS expectedAccountName,
                bea.expected_platform_account_id AS expectedPlatformAccountId
            FROM browser_environment_account bea
            JOIN browser_environment be
              ON be.id = bea.browser_environment_id
             AND be.deleted_at IS NULL
            JOIN self_media_account sma
              ON sma.id = bea.self_media_account_id
             AND sma.deleted_at IS NULL
            JOIN brand b
              ON b.id = bea.brand_id
            WHERE bea.deleted_at IS NULL
              AND bea.brand_id = be.brand_id
              AND bea.brand_id = sma.brand_id
              <if test="brandId != null">
                AND bea.brand_id = #{brandId}
              </if>
              <if test="platform != null and platform != ''">
                AND bea.platform = #{platform}
              </if>
              <if test="keyword != null and keyword != ''">
                AND (
                     b.brand_name LIKE CONCAT('%', #{keyword}, '%')
                  OR sma.account_name LIKE CONCAT('%', #{keyword}, '%')
                  OR sma.platform_account_id LIKE CONCAT('%', #{keyword}, '%')
                  OR be.name LIKE CONCAT('%', #{keyword}, '%')
                  OR be.environment_key LIKE CONCAT('%', #{keyword}, '%')
                  OR be.provider_profile_id LIKE CONCAT('%', #{keyword}, '%')
                  OR bea.expected_account_name LIKE CONCAT('%', #{keyword}, '%')
                  OR bea.expected_platform_account_id LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
            ORDER BY b.brand_name ASC, be.environment_key ASC, bea.platform ASC, bea.id ASC
            </script>
            """)
    List<SelfMediaRuntimeEnvironmentBaseRow> selectRuntimeEnvironmentRows(@Param("brandId") Long brandId,
                                                                          @Param("platform") String platform,
                                                                          @Param("keyword") String keyword);
}
