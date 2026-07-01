package com.huanjing.geo.module.partner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.partner.entity.PartnerAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PartnerAccountMapper extends BaseMapper<PartnerAccount> {

    @Select("""
            SELECT *
            FROM partner_account
            WHERE partner_id = #{partnerId}
            FOR UPDATE
            """)
    PartnerAccount selectByPartnerIdForUpdate(@Param("partnerId") Long partnerId);
}
