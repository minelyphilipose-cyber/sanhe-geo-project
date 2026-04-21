package com.huanjing.geo.module.presale.persist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PresaleReportVersionMapper extends BaseMapper<PresaleReportVersion> {

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
}
