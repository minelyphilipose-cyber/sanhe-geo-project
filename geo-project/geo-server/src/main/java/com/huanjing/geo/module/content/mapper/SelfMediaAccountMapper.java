package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SelfMediaAccountMapper extends BaseMapper<SelfMediaAccount> {

    @Select("""
            SELECT id
            FROM self_media_account
            WHERE id = #{id}
              AND deleted_at IS NULL
            FOR UPDATE
            """)
    Long lockById(@Param("id") Long id);
}
