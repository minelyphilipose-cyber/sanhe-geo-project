package com.huanjing.geo.module.presale.persist.mapper;

import com.huanjing.geo.module.presale.generate.llm.PresaleLlmPlatformConfigRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PresaleLlmConfigMapper {

    @Select("SELECT " +
            "p.platform_code AS platformCode, " +
            "p.api_url AS apiUrl, " +
            "p.model_id AS modelId, " +
            "p.api_key AS apiKey, " +
            "p.primary_key_ref AS primaryKeyRef, " +
            "COALESCE(p.max_retry, 2) AS maxRetry, " +
            "COALESCE(p.timeout_ms, 60000) AS timeoutMs, " +
            "COALESCE(NULLIF(p.rate_limit_qps, 0), 1) AS rateLimitQps, " +
            "COALESCE(p.in_whitelist, 0) AS inWhitelist " +
            "FROM presale_platform_config p " +
            "WHERE p.platform_code = #{platformCode} " +
            "LIMIT 1")
    PresaleLlmPlatformConfigRow selectRuntimeConfig(@Param("platformCode") String platformCode);

    @Select("SELECT p.platform_code " +
            "FROM presale_platform_config p " +
            "WHERE p.in_whitelist = 1 " +
            "ORDER BY p.platform_code")
    List<String> selectWhitelistedPlatformCodes();

    @Select("SELECT COUNT(1) " +
            "FROM presale_platform_config p " +
            "WHERE p.in_whitelist = 1")
    Long countWhitelistedPlatforms();
}
