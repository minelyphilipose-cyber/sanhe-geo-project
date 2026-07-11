package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BrowserEnvironmentAccountMapper extends BaseMapper<BrowserEnvironmentAccount> {

    @Select("""
            SELECT *
            FROM browser_environment_account
            WHERE self_media_account_id = #{selfMediaAccountId}
            ORDER BY (deleted_at IS NULL) DESC, id ASC
            LIMIT 1
            """)
    BrowserEnvironmentAccount selectOldestBySelfMediaAccountIdIncludingDeleted(
            @Param("selfMediaAccountId") Long selfMediaAccountId);

    @Update("UPDATE browser_environment_account SET deleted_at = NULL WHERE id = #{id}")
    int restoreDeletedById(@Param("id") Long id);

    @Update("""
            UPDATE browser_environment_account
            SET last_error_code = #{errorCode},
                last_error_message = #{errorMessage}
            WHERE id = #{id}
              AND deleted_at IS NULL
            """)
    int updateNullableLoginErrors(@Param("id") Long id,
                                  @Param("errorCode") String errorCode,
                                  @Param("errorMessage") String errorMessage);

    @Select("""
            SELECT bea.*
            FROM browser_environment_account bea
            JOIN browser_environment be
              ON be.id = bea.browser_environment_id
             AND be.deleted_at IS NULL
            JOIN self_media_account sma
              ON sma.id = bea.self_media_account_id
             AND sma.deleted_at IS NULL
            WHERE bea.self_media_account_id = #{selfMediaAccountId}
              AND bea.deleted_at IS NULL
              AND bea.brand_id = sma.brand_id
              AND be.brand_id = sma.brand_id
            LIMIT 1
            """)
    BrowserEnvironmentAccount selectActiveBySelfMediaAccountId(@Param("selfMediaAccountId") Long selfMediaAccountId);

    @Select("""
            SELECT bea.*
            FROM browser_environment_account bea
            JOIN browser_environment be
              ON be.id = bea.browser_environment_id
             AND be.deleted_at IS NULL
            WHERE bea.deleted_at IS NULL
              AND (
                    be.environment_key = #{environmentKey}
                 OR be.name = #{environmentKey}
                 OR REPLACE(be.environment_key, '-', '_') = REPLACE(#{environmentKey}, '-', '_')
                 OR REPLACE(be.name, '-', '_') = REPLACE(#{environmentKey}, '-', '_')
              )
              AND bea.platform = #{platform}
            ORDER BY bea.id ASC
            LIMIT 2
            """)
    List<BrowserEnvironmentAccount> selectActiveByEnvironmentKeyAndPlatform(@Param("environmentKey") String environmentKey,
                                                                            @Param("platform") String platform);

    @Select("""
            SELECT bea.*
            FROM browser_environment_account bea
            JOIN browser_environment be
              ON be.id = bea.browser_environment_id
             AND be.deleted_at IS NULL
            WHERE bea.deleted_at IS NULL
              AND bea.brand_id = #{brandId}
              AND be.brand_id = #{brandId}
              AND bea.platform = #{platform}
            ORDER BY bea.id ASC
            LIMIT 2
            """)
    List<BrowserEnvironmentAccount> selectActiveByBrandIdAndPlatform(@Param("brandId") Long brandId,
                                                                     @Param("platform") String platform);

    @Select("""
            SELECT bea.*
            FROM browser_environment_account bea
            JOIN browser_environment be
              ON be.id = bea.browser_environment_id
             AND be.deleted_at IS NULL
            WHERE bea.deleted_at IS NULL
              AND bea.brand_id = #{brandId}
              AND be.brand_id = #{brandId}
              AND bea.platform = #{platform}
            ORDER BY bea.id ASC
            """)
    List<BrowserEnvironmentAccount> selectAllActiveByBrandIdAndPlatform(@Param("brandId") Long brandId,
                                                                        @Param("platform") String platform);

    @Select("""
            SELECT bea.*
            FROM browser_environment_account bea
            JOIN browser_environment be
              ON be.id = bea.browser_environment_id
             AND be.deleted_at IS NULL
             AND be.status = 'active'
            WHERE bea.deleted_at IS NULL
              AND bea.brand_id = #{brandId}
              AND be.brand_id = #{brandId}
            ORDER BY be.environment_key ASC, bea.platform ASC, bea.id ASC
            """)
    List<BrowserEnvironmentAccount> selectActiveRuntimeConfigsByBrandId(@Param("brandId") Long brandId);
}
