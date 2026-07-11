package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.BrowserEnvironment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BrowserEnvironmentMapper extends BaseMapper<BrowserEnvironment> {

    @Select("""
            SELECT *
            FROM browser_environment
            WHERE brand_id = #{brandId}
              AND provider = #{provider}
              AND (environment_key = #{environmentKey} OR provider_profile_id = #{providerProfileId})
            ORDER BY (deleted_at IS NULL) DESC, id ASC
            LIMIT 1
            """)
    BrowserEnvironment selectOldestByIdentityIncludingDeleted(@Param("brandId") Long brandId,
                                                               @Param("provider") String provider,
                                                               @Param("environmentKey") String environmentKey,
                                                               @Param("providerProfileId") String providerProfileId);

    @Update("UPDATE browser_environment SET deleted_at = NULL WHERE id = #{id}")
    int restoreDeletedById(@Param("id") Long id);
}
