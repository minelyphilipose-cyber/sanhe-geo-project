package com.huanjing.geo.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.customer.entity.BrandOperatorAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    @Select("""
            <script>
            SELECT DISTINCT brand_id
            FROM brand_operator_assignment
            WHERE operator_id = #{operatorId}
              AND status = 'active'
              AND role IN
              <foreach collection="roles" item="role" open="(" separator="," close=")">
                #{role}
              </foreach>
            </script>
            """)
    List<Long> selectActiveBrandIdsByRoles(@Param("operatorId") Long operatorId, @Param("roles") List<String> roles);
}
