package com.huanjing.geo.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiPlatformConfigMapper extends BaseMapper<AiPlatformConfig> {

    @Update("""
            UPDATE ai_platform_config
            SET api_key = #{apiKey},
                primary_key_ref = #{primaryKeyRef}
            WHERE id = #{id}
            """)
    int updateCredentialSources(@Param("id") Long id,
                                @Param("apiKey") String apiKey,
                                @Param("primaryKeyRef") String primaryKeyRef);
}
