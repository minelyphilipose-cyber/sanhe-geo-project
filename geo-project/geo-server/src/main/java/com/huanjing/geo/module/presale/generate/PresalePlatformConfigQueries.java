package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;

public final class PresalePlatformConfigQueries {

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
                .isNotNull(AiPlatformConfig::getLowModelId)
                .apply("TRIM(low_model_id) <> ''")
                .orderByAsc(AiPlatformConfig::getPlatformCode);
    }
}
