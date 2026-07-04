package com.huanjing.geo.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    @Update("""
            UPDATE sys_user
               SET token_version = IFNULL(token_version, 0) + 1,
                   last_login_at = NOW()
             WHERE id = #{userId}
               AND is_active = 1
            """)
    int incrementTokenVersionForLogin(@Param("userId") Long userId);
}
