package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    @Select("""
            SELECT id, brand_id, platform, account_name, status
            FROM self_media_account
            WHERE deleted_at IS NULL
            ORDER BY updated_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<SelfMediaAccount> selectExtensionAccountCandidates(@Param("limit") int limit);
}
