package com.huanjing.geo.module.partner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.partner.entity.Partner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PartnerMapper extends BaseMapper<Partner> {

    @Select("""
            SELECT *
            FROM partner
            WHERE id = #{partnerId}
            FOR UPDATE
            """)
    Partner selectByIdForUpdate(@Param("partnerId") Long partnerId);
}
