package com.huanjing.geo.module.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.project.entity.BaselineReportExport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BaselineReportExportMapper extends BaseMapper<BaselineReportExport> {
    BaselineReportExport selectByIdempotencyKeyForUpdate(@Param("idempotencyKey") String idempotencyKey);

    BaselineReportExport selectActiveByUserBaseline(@Param("triggerUserId") Long triggerUserId,
                                                    @Param("projectId") Long projectId,
                                                    @Param("baselineId") Long baselineId);

    BaselineReportExport selectOnePendingForUpdateSkipLocked();

    int markClaimed(@Param("id") Long id, @Param("workerId") String workerId);

    int heartbeat(@Param("id") Long id, @Param("workerId") String workerId);
}
