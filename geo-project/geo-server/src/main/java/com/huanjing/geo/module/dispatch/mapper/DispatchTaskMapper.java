package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DispatchTaskMapper extends BaseMapper<DispatchTask> {

    @Update("""
            UPDATE dispatch_task
            SET status = #{targetStatus},
                finished_at = #{finishedAt},
                last_error = #{lastError},
                error_context = #{errorContext},
                updated_at = NOW()
            WHERE id = #{taskId}
              AND status = #{expectedStatus}
              AND timeout_at IS NOT NULL
              AND timeout_at < NOW()
            """)
    int claimTimedOutRunningTask(@Param("taskId") Long taskId,
                                 @Param("expectedStatus") String expectedStatus,
                                 @Param("targetStatus") String targetStatus,
                                 @Param("finishedAt") java.time.LocalDateTime finishedAt,
                                 @Param("lastError") String lastError,
                                 @Param("errorContext") String errorContext);
}
