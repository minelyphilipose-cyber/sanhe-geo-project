package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.SelfMediaLoginObservation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface SelfMediaLoginObservationMapper extends BaseMapper<SelfMediaLoginObservation> {
    @Delete("""
            DELETE FROM self_media_login_observation
            WHERE created_at < #{cutoff}
            ORDER BY id
            LIMIT #{limit}
            """)
    int deleteExpiredBatch(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
