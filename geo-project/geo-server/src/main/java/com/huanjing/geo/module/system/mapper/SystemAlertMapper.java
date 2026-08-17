package com.huanjing.geo.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.system.entity.SystemAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SystemAlertMapper extends BaseMapper<SystemAlert> {
    @Update("""
            UPDATE system_alerts
            SET claimed_by = #{operatorId}, claimed_at = #{now}, read_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND is_resolved = 0 AND claimed_by IS NULL
            """)
    int claimIfUnowned(@Param("id") Long id,
                       @Param("operatorId") Long operatorId,
                       @Param("now") LocalDateTime now);
}
