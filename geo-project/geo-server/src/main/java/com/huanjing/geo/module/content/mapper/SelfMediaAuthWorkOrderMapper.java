package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.SelfMediaAuthWorkOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SelfMediaAuthWorkOrderMapper extends BaseMapper<SelfMediaAuthWorkOrder> {
    @Update("""
            UPDATE self_media_auth_work_order
            SET owner_user_id = #{operatorId}, status = 'operator_verification',
                customer_action_required = FALSE,
                updated_by = #{operatorId}, updated_at = #{now}
            WHERE id = #{id} AND owner_user_id IS NULL AND status <> 'resolved'
            """)
    int claimIfUnowned(@Param("id") Long id,
                       @Param("operatorId") Long operatorId,
                       @Param("now") LocalDateTime now);
}
