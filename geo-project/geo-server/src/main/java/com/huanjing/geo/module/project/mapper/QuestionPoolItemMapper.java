package com.huanjing.geo.module.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.project.entity.QuestionPoolItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QuestionPoolItemMapper extends BaseMapper<QuestionPoolItem> {

    @Select("SELECT COUNT(1) " +
            "FROM question_pool_item qpi " +
            "JOIN question_pool_version qpv ON qpv.id = qpi.version_id " +
            "JOIN (SELECT project_id, MAX(version_no) AS version_no FROM question_pool_version GROUP BY project_id) latest " +
            "  ON latest.project_id = qpv.project_id AND latest.version_no = qpv.version_no " +
            "JOIN project p ON p.id = qpv.project_id " +
            "WHERE p.company_id = #{companyId} " +
            "  AND p.deleted_at IS NULL")
    int countLatestItemsByCompany(@Param("companyId") Long companyId);

    @Select("SELECT COUNT(1) " +
            "FROM question_pool_item qpi " +
            "JOIN question_pool_version qpv ON qpv.id = qpi.version_id " +
            "JOIN (SELECT project_id, MAX(version_no) AS version_no FROM question_pool_version GROUP BY project_id) latest " +
            "  ON latest.project_id = qpv.project_id AND latest.version_no = qpv.version_no " +
            "JOIN project p ON p.id = qpv.project_id " +
            "WHERE p.company_id = #{companyId} " +
            "  AND p.deleted_at IS NULL " +
            "  AND p.id <> #{excludeProjectId}")
    int countLatestItemsByCompanyExcludingProject(@Param("companyId") Long companyId,
                                                  @Param("excludeProjectId") Long excludeProjectId);
}
