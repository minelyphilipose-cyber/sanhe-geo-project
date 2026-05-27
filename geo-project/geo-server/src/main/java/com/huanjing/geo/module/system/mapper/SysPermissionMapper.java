package com.huanjing.geo.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.system.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    @Select("""
        SELECT DISTINCT p.perm_key
        FROM sys_user_role ur
        JOIN sys_role r ON r.id = ur.role_id AND r.status = 'active'
        JOIN sys_role_permission rp ON rp.role_id = ur.role_id
        JOIN sys_permission p ON p.id = rp.permission_id AND p.status IN ('active', 'deprecated')
        WHERE ur.user_id = #{userId}
        """)
    List<String> selectPermKeysByUserId(@Param("userId") Long userId);

    @Select("""
        SELECT COUNT(DISTINCT p.id)
        FROM sys_permission p
        JOIN sys_role_permission rp ON rp.permission_id = p.id
        WHERE p.status = 'deprecated'
        """)
    Long countDeprecatedBoundPermissions();
}
