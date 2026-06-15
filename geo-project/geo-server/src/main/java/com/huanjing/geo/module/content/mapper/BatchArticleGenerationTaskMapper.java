package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.dto.SubjectBrandLastSelectedRow;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BatchArticleGenerationTaskMapper extends BaseMapper<BatchArticleGenerationTask> {

    @Select("""
            <script>
            SELECT subject_brand_id AS subjectBrandId,
                   MAX(id) AS lastSelectedTaskId,
                   MAX(created_at) AS lastSelectedAt
            FROM batch_article_generation_task
            WHERE source_brand_id = #{sourceBrandId}
              AND subject_brand_id IN
              <foreach collection='subjectBrandIds' item='subjectBrandId' open='(' separator=',' close=')'>
                #{subjectBrandId}
              </foreach>
            GROUP BY subject_brand_id
            </script>
            """)
    List<SubjectBrandLastSelectedRow> selectLastSelectedBySourceBrand(@Param("sourceBrandId") Long sourceBrandId,
                                                                      @Param("subjectBrandIds") List<Long> subjectBrandIds);
}
