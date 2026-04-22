package com.huanjing.geo.module.presale.persist.mapper;

import com.huanjing.geo.module.presale.generate.llm.PresaleLlmPlatformConfigRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PresaleLlmConfigMapper {

    @Select("SELECT " +
            "a.platform_code AS platformCode, " +
            "a.api_url AS apiUrl, " +
            "a.model_id AS modelId, " +
            "a.api_key AS apiKey, " +
            "a.primary_key_ref AS primaryKeyRef, " +
            "COALESCE(p.max_retry, 2) AS maxRetry, " +
            "COALESCE(p.timeout_ms, 60000) AS timeoutMs, " +
            "COALESCE(NULLIF(p.rate_limit_qps, 0), GREATEST(1, FLOOR(COALESCE(a.rpm_limit, 60) / 60))) AS rateLimitQps, " +
            "COALESCE(p.in_whitelist, 0) AS inWhitelist " +
            "FROM ai_platform_config a " +
            "LEFT JOIN presale_platform_config p ON p.platform_code = a.platform_code " +
            "WHERE a.platform_code = #{platformCode} AND a.enabled = 1 " +
            "LIMIT 1")
    PresaleLlmPlatformConfigRow selectRuntimeConfig(@Param("platformCode") String platformCode);
}

