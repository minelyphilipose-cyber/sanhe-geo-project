package com.huanjing.geo.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Select("""
        SELECT r.role_key
        FROM sys_user_role ur
        JOIN sys_role r ON r.id = ur.role_id
        WHERE ur.user_id = #{userId}
        ORDER BY r.sort_order ASC, r.id ASC
        """)
    List<String> selectRoleKeysByUserId(@Param("userId") Long userId);

    @Select("""
        SELECT ur.user_id
        FROM sys_user_role ur
        JOIN sys_role r ON r.id = ur.role_id
        WHERE r.role_key = #{roleKey}
        """)
    List<Long> selectUserIdsByRoleKey(@Param("roleKey") String roleKey);
}
