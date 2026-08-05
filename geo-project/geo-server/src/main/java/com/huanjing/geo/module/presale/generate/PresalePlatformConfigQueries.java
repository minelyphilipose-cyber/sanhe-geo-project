package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.dispatch.websearch.enums.UsageScene;

public final class PresalePlatformConfigQueries {

    private static final String ENABLED_WEB_COMPANION_EXISTS = "EXISTS (" +
            "SELECT 1 FROM ai_platform_config web_profile " +
            "WHERE web_profile.channel_code = ai_platform_config.channel_code " +
            "AND web_profile.usage_scene = 'QUESTION_POLL_WEB' " +
            "AND web_profile.enabled = 1 " +
            "AND web_profile.enabled_for_presale = 1)";

    private PresalePlatformConfigQueries() {
    }

    /**
     * Presale 平台查询过滤器:
     * - enabled = 1
     * - enabled_for_presale = 1
     * - low_model_id IS NOT NULL AND TRIM(low_model_id) <> ''
     */
    public static LambdaQueryWrapper<AiPlatformConfig> presaleEnabledWrapper() {
        return new LambdaQueryWrapper<AiPlatformConfig>()
                .eq(AiPlatformConfig::getEnabled, true)
                .eq(AiPlatformConfig::getEnabledForPresale, true)
                .eq(AiPlatformConfig::getUsageScene, UsageScene.STANDARD_CHAT.name())
                .isNotNull(AiPlatformConfig::getLowModelId)
                .apply("TRIM(low_model_id) <> ''")
                .orderByAsc(AiPlatformConfig::getPlatformCode);
    }

    /**
     * REQUIRED 模式的逻辑报告平台。基础模型自身可入池，或由同渠道已开启的 Web companion
     * 独立把该逻辑平台带入池；实际 QUERY 是否走 companion 由 ReadinessChecker 决定。
     */
    public static LambdaQueryWrapper<AiPlatformConfig> requiredReportPlatformWrapper() {
        return new LambdaQueryWrapper<AiPlatformConfig>()
                .eq(AiPlatformConfig::getUsageScene, UsageScene.STANDARD_CHAT.name())
                .and(group -> group
                        .nested(nativeProfile -> nativeProfile
                                .eq(AiPlatformConfig::getEnabled, true)
                                .eq(AiPlatformConfig::getEnabledForPresale, true)
                                .isNotNull(AiPlatformConfig::getLowModelId)
                                .apply("TRIM(low_model_id) <> ''"))
                        .or()
                        .apply(ENABLED_WEB_COMPANION_EXISTS))
                .orderByAsc(AiPlatformConfig::getPlatformCode);
    }
}
