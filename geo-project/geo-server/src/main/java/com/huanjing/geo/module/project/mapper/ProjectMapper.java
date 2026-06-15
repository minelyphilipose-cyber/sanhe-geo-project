package com.huanjing.geo.module.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.project.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    @Select("""
            SELECT id
            FROM project
            WHERE brand_id = #{brandId}
              AND deleted_at IS NULL
              AND status = 'active'
              AND (end_date IS NULL OR end_date >= CURRENT_DATE)
            ORDER BY id ASC
            LIMIT 1
            """)
    Long selectStableActiveProjectIdByBrandId(@Param("brandId") Long brandId);
}
