package com.huanjing.geo.module.extension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.extension.entity.ExtensionRuntimeStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ExtensionRuntimeStatusMapper extends BaseMapper<ExtensionRuntimeStatus> {

    @Update("""
            UPDATE extension_runtime_status
            SET extension_session_id = #{row.extensionSessionId},
                browser_environment_id = #{row.browserEnvironmentId},
                browser_environment_account_id = #{row.browserEnvironmentAccountId},
                brand_id = #{row.brandId},
                platform = #{row.platform},
                environment_key = #{row.environmentKey},
                extension_version = #{row.extensionVersion},
                protocol_version = #{row.protocolVersion},
                current_url = #{row.currentUrl},
                detected_platform = #{row.detectedPlatform},
                detected_account_name = #{row.detectedAccountName},
                detected_platform_account_id = #{row.detectedPlatformAccountId},
                login_status = #{row.loginStatus},
                runtime_stage = #{row.runtimeStage},
                runtime_stage_at = #{row.runtimeStageAt},
                runtime_stage_message = #{row.runtimeStageMessage},
                capabilities_json = #{row.capabilitiesJson},
                last_task_id = #{row.lastTaskId},
                last_error_code = #{row.lastErrorCode},
                last_error_message = #{row.lastErrorMessage},
                last_seen_at = #{row.lastSeenAt},
                updated_at = #{row.updatedAt}
            WHERE provider_profile_id = #{row.providerProfileId}
              AND install_id = #{row.installId}
            """)
    int updateByProviderProfileIdAndInstallId(@Param("row") ExtensionRuntimeStatus row);

    @Select("""
            SELECT *
            FROM extension_runtime_status
            WHERE browser_environment_id = #{browserEnvironmentId}
              AND (#{platform} IS NULL OR detected_platform = #{platform} OR platform = #{platform})
            ORDER BY last_seen_at DESC, updated_at DESC
            LIMIT 2
            """)
    List<ExtensionRuntimeStatus> selectLatestByEnvironmentAndPlatform(@Param("browserEnvironmentId") Long browserEnvironmentId,
                                                                      @Param("platform") String platform);
}
