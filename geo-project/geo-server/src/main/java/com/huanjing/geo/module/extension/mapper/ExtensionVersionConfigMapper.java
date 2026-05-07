package com.huanjing.geo.module.extension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.extension.entity.ExtensionVersionConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExtensionVersionConfigMapper extends BaseMapper<ExtensionVersionConfig> {

    @Select("""
            SELECT *
            FROM extension_version_config
            WHERE platform = #{platform}
              AND status = 'active'
            ORDER BY updated_at DESC, id DESC
            LIMIT 1
            """)
    ExtensionVersionConfig selectActiveByPlatform(@Param("platform") String platform);
}
