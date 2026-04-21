package com.huanjing.geo.module.presale.persist.mapper;

import com.huanjing.geo.module.presale.generate.PlatformIntentSampleRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PresaleAiTestResultMapper {

    /**
     * 拉取版本下的平台 × 意图原子样本。
     */
    @Select("SELECT " +
            "r.platform_code AS platformCode, " +
            "pt.category AS intentLabel, " +
            "r.call_status AS callStatus, " +
            "r.is_excluded AS isExcluded, " +
            "r.is_mentioned AS isMentioned " +
            "FROM presale_ai_test_result r " +
            "LEFT JOIN presale_prompt_template pt ON pt.id = r.prompt_template_id " +
            "WHERE r.version_id = #{versionId}")
    List<PlatformIntentSampleRow> selectIntentSamplesByVersionId(@Param("versionId") Long versionId);
}

