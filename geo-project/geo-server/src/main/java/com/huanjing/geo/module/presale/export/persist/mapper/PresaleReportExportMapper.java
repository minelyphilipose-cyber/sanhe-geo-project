package com.huanjing.geo.module.presale.export.persist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.presale.export.persist.entity.PresaleReportExport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PresaleReportExportMapper extends BaseMapper<PresaleReportExport> {
    PresaleReportExport selectByIdempotencyKeyForUpdate(@Param("idempotencyKey") String idempotencyKey);

    PresaleReportExport selectByIdForUpdate(@Param("id") Long id);

    PresaleReportExport selectActiveByUserReportVersion(@Param("triggerUserId") Long triggerUserId,
                                                        @Param("reportId") Long reportId,
                                                        @Param("versionId") Long versionId);

    PresaleReportExport selectOnePendingForUpdateSkipLocked();

    int markClaimed(@Param("id") Long id, @Param("workerId") String workerId);

    int heartbeat(@Param("id") Long id, @Param("workerId") String workerId);

    List<PresaleReportExport> selectRunningByWorker(@Param("workerId") String workerId);

    List<PresaleReportExport> selectStaleRunning(@Param("deadline") LocalDateTime deadline);

    int markInterruptedByRestartById(@Param("id") Long id,
                                     @Param("workerId") String workerId,
                                     @Param("metricsJson") String metricsJson);

    int markStaleFailed(@Param("id") Long id, @Param("metricsJson") String metricsJson);

    int incrementVersionExportSuccess(@Param("versionId") Long versionId);

    int archiveExpiredIdempotencyKey(@Param("id") Long id, @Param("archivedKey") String archivedKey);
}
