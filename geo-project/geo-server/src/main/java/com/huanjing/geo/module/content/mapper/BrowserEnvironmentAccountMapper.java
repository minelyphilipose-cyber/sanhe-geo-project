package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BrowserEnvironmentAccountMapper extends BaseMapper<BrowserEnvironmentAccount> {

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
              AND be.environment_key = #{environmentKey}
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
