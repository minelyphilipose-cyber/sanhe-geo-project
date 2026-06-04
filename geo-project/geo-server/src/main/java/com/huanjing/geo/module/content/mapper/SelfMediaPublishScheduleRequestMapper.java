package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.SelfMediaPublishScheduleRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SelfMediaPublishScheduleRequestMapper extends BaseMapper<SelfMediaPublishScheduleRequest> {

    @Select("""
            SELECT *
            FROM self_media_publish_schedule_request
            WHERE brand_id = #{brandId}
              AND request_idempotency_key = #{requestIdempotencyKey}
            LIMIT 1
            """)
    SelfMediaPublishScheduleRequest selectByRequestKey(@Param("brandId") Long brandId,
                                                       @Param("requestIdempotencyKey") String requestIdempotencyKey);
}
