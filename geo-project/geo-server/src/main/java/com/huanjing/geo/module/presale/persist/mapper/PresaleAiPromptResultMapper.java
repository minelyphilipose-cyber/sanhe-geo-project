package com.huanjing.geo.module.presale.persist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.presale.generate.PlatformIntentSampleRow;
import com.huanjing.geo.module.presale.generate.PromptTemplateIntentStatRow;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PresaleAiPromptResultMapper extends BaseMapper<PresaleAiPromptResult> {

    @Select("SELECT " +
            "r.platform_code AS platformCode, " +
            "pt.category AS intentLabel, " +
            "CASE WHEN r.is_mentioned IS NULL THEN 'FAILED' ELSE 'SUCCESS' END AS callStatus, " +
            "0 AS isExcluded, " +
            "r.is_mentioned AS isMentioned " +
            "FROM presale_ai_prompt_result r " +
            "LEFT JOIN presale_prompt_template pt ON pt.id = r.prompt_template_id " +
            "WHERE r.version_id = #{versionId}")
    List<PlatformIntentSampleRow> selectIntentSamplesByVersionId(@Param("versionId") Long versionId);

    @Select("SELECT " +
            "category AS intentLabel, " +
            "has_competitor_var AS hasCompetitorVar, " +
            "COUNT(*) AS templateCount " +
            "FROM presale_prompt_template " +
            "WHERE enabled = 1 " +
            "AND has_competitor_var = 0 " +
            "GROUP BY category, has_competitor_var")
    List<PromptTemplateIntentStatRow> selectTemplateIntentStats();
}
