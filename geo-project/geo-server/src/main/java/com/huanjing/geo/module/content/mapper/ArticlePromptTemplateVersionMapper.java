package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import org.apache.ibatis.annotations.Select;

public interface ArticlePromptTemplateVersionMapper extends BaseMapper<ArticlePromptTemplateVersion> {

    @Select("SELECT COALESCE(MAX(version_no), 0) FROM article_prompt_template_version WHERE template_id = #{templateId}")
    int maxVersionNo(Long templateId);
}
