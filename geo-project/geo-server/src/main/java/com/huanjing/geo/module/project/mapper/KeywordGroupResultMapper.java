package com.huanjing.geo.module.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.project.dto.LlmQuestionItemDTO;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KeywordGroupResultMapper extends BaseMapper<KeywordGroupResult> {
    @Select("""
            SELECT keyword_text AS questionText, seed_text AS seedText
            FROM keyword_group_result
            WHERE group_id = #{groupId}
              AND source_type = 'llm'
            ORDER BY sort_order ASC, id ASC
            """)
    List<LlmQuestionItemDTO> selectLlmQuestionsByGroupId(Long groupId);
}
