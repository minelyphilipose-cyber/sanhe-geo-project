package com.huanjing.geo.module.content.vo;

import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleConfig;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectSelfMediaScheduleConfigVO {
    private Long projectId;
    private Long brandId;
    private Long companyId;
    private Boolean autoScheduleEnabled;
    private Boolean includeAdjustedWorkdays;
    private String remark;
    private LocalDateTime updatedAt;

    public static ProjectSelfMediaScheduleConfigVO from(ProjectSelfMediaScheduleConfig row) {
        ProjectSelfMediaScheduleConfigVO vo = new ProjectSelfMediaScheduleConfigVO();
        if (row == null) {
            vo.setAutoScheduleEnabled(false);
            vo.setIncludeAdjustedWorkdays(false);
            return vo;
        }
        vo.setProjectId(row.getProjectId());
        vo.setBrandId(row.getBrandId());
        vo.setCompanyId(row.getCompanyId());
        vo.setAutoScheduleEnabled(Boolean.TRUE.equals(row.getAutoScheduleEnabled()));
        vo.setIncludeAdjustedWorkdays(Boolean.TRUE.equals(row.getIncludeAdjustedWorkdays()));
        vo.setRemark(row.getRemark());
        vo.setUpdatedAt(row.getUpdatedAt());
        return vo;
    }
}
