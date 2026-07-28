package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface PollResultMapper extends BaseMapper<PollResult> {

    @Select("""
            SELECT *
            FROM poll_results
            WHERE id = #{id}
            FOR UPDATE
            """)
    PollResult selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT keyword_text_snapshot AS keywordText,
                   COALESCE(SUM(hit_count), 0) AS hitCount
              FROM poll_keyword_daily_summary
             WHERE project_id = #{projectId}
               AND keyword_text_snapshot IS NOT NULL
               AND keyword_text_snapshot <> ''
             GROUP BY keyword_text_snapshot
            """)
    List<Map<String, Object>> selectKeywordFrequencySummary(@Param("projectId") Long projectId);
}
