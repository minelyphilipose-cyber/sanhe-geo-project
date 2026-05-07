package com.huanjing.geo.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.customer.entity.BrandOperatorAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BrandOperatorAssignmentMapper extends BaseMapper<BrandOperatorAssignment> {

    @Select("""
            SELECT role
            FROM brand_operator_assignment
            WHERE brand_id = #{brandId}
              AND operator_id = #{operatorId}
              AND status = 'active'
            ORDER BY assigned_at DESC, id DESC
            LIMIT 1
            """)
    String selectActiveRole(@Param("brandId") Long brandId, @Param("operatorId") Long operatorId);
}
