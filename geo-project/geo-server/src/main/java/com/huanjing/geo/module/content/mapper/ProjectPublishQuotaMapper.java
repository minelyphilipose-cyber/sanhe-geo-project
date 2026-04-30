package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.ProjectPublishQuota;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProjectPublishQuotaMapper extends BaseMapper<ProjectPublishQuota> {

    /**
     * Atomic quota reservation. Returns affected rows.
     * Returns 0 if quota would exceed limit; caller should treat as "quota exhausted".
     */
    @Update("UPDATE project_publish_quota " +
            "SET used_count = used_count + 1 " +
            "WHERE project_id = #{projectId} " +
            "  AND quota_month = #{monthKey} " +
            "  AND used_count + 1 <= #{monthlyLimit}")
    int tryReserve(@Param("projectId") Long projectId,
                   @Param("monthKey") String monthKey,
                   @Param("monthlyLimit") Integer monthlyLimit);

    @Update("UPDATE project_publish_quota " +
            "SET used_count = used_count - 1 " +
            "WHERE project_id = #{projectId} " +
            "  AND quota_month = #{monthKey} " +
            "  AND used_count > 0")
    int refundReserved(@Param("projectId") Long projectId,
                       @Param("monthKey") String monthKey);
}
