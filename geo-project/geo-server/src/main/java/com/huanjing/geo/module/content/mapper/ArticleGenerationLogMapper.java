package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.ArticleGenerationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ArticleGenerationLogMapper extends BaseMapper<ArticleGenerationLog> {

    @Select("SELECT article_angle FROM article_generation_log " +
            "WHERE project_id = #{projectId} AND article_angle IS NOT NULL AND article_angle <> '' " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<String> selectRecentAngles(@Param("projectId") Long projectId, @Param("limit") int limit);
}
