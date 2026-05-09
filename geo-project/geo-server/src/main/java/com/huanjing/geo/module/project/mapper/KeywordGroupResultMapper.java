package com.huanjing.geo.module.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.project.dto.LlmQuestionItemDTO;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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

    @Select("""
            SELECT COUNT(1)
            FROM project_keyword_group_rel rel
            JOIN project p ON p.id = rel.project_id
            JOIN keyword_group kg ON kg.id = rel.keyword_group_id
            JOIN keyword_group_result r ON r.group_id = rel.keyword_group_id
            WHERE p.company_id = #{companyId}
              AND p.deleted_at IS NULL
              AND p.status = 'active'
              AND COALESCE(kg.deleted, 0) = 0
              AND (#{excludeProjectId} IS NULL OR p.id <> #{excludeProjectId})
            """)
    long countSavedKeywordsByCompanyActiveProjects(@Param("companyId") Long companyId,
                                                   @Param("excludeProjectId") Long excludeProjectId);

    @Select("""
            SELECT COUNT(1)
            FROM project_keyword_group_rel rel
            JOIN keyword_group kg ON kg.id = rel.keyword_group_id
            JOIN keyword_group_result r ON r.group_id = rel.keyword_group_id
            WHERE rel.project_id = #{projectId}
              AND COALESCE(kg.deleted, 0) = 0
            """)
    long countSavedKeywordsByProject(@Param("projectId") Long projectId);
}
