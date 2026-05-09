package com.huanjing.geo.module.presale.persist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PresaleReportVersionMapper extends BaseMapper<PresaleReportVersion> {
    int tryTransitionToRunning(@Param("versionId") Long versionId);

    /**
     * 取指定 report 的最大 version_no(用于派生新版本递增编号)。
     * 不存在时返回 null。
     */
    @Select("SELECT MAX(version_no) FROM presale_report_version WHERE report_id = #{reportId}")
    Integer selectMaxVersionNo(@Param("reportId") Long reportId);

    /**
     * 统计 report 的版本数(列表页 versionCount 列)。
     */
    @Select("SELECT COUNT(*) FROM presale_report_version WHERE report_id = #{reportId}")
    int countByReportId(@Param("reportId") Long reportId);

    /**
     * 按多个 reportId 批量查版本数,列表页避免 N+1。
     * 返回 List<Map>,每项含 report_id + count。
     */
    @Select("<script>" +
            "SELECT report_id, COUNT(*) as cnt FROM presale_report_version " +
            "WHERE report_id IN " +
            "<foreach collection='reportIds' item='id' open='(' close=')' separator=','>#{id}</foreach>" +
            " GROUP BY report_id" +
            "</script>")
    List<java.util.Map<String, Object>> countByReportIds(@Param("reportIds") List<Long> reportIds);

    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM presale_report r " +
            "JOIN presale_report_version v ON v.id = r.current_version_id " +
            "WHERE r.deleted_at IS NULL " +
            "AND v.generation_status = 'DONE' " +
            "AND v.updated_at &gt;= #{startAt} " +
            "AND v.updated_at &lt;= #{endAt} " +
            "<if test='createdBy != null'>AND r.created_by = #{createdBy}</if>" +
            "</script>")
    Long countGeneratedCurrentReports(@Param("startAt") LocalDateTime startAt,
                                      @Param("endAt") LocalDateTime endAt,
                                      @Param("createdBy") Long createdBy);

    @Select("<script>" +
            "SELECT v.updated_at " +
            "FROM presale_report r " +
            "JOIN presale_report_version v ON v.id = r.current_version_id " +
            "WHERE r.deleted_at IS NULL " +
            "AND v.generation_status = 'DONE' " +
            "AND v.updated_at &gt;= #{startAt} " +
            "AND v.updated_at &lt;= #{endAt} " +
            "<if test='createdBy != null'>AND r.created_by = #{createdBy}</if>" +
            "</script>")
    List<LocalDateTime> selectGeneratedCurrentReportTimes(@Param("startAt") LocalDateTime startAt,
                                                          @Param("endAt") LocalDateTime endAt,
                                                          @Param("createdBy") Long createdBy);
}
