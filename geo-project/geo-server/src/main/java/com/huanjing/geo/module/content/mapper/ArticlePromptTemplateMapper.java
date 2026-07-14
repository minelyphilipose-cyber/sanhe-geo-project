package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ArticlePromptTemplateMapper extends BaseMapper<ArticlePromptTemplate> {

    @Update("UPDATE article_prompt_template SET current_version_id = NULL WHERE id = #{id}")
    int clearCurrentVersionId(@Param("id") Long id);
}
