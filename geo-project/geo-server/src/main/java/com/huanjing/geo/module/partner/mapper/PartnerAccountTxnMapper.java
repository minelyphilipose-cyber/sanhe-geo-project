package com.huanjing.geo.module.partner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.partner.entity.PartnerAccountTxn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PartnerAccountTxnMapper extends BaseMapper<PartnerAccountTxn> {

    @Select("""
            SELECT *
            FROM partner_account_txn
            WHERE biz_type = #{bizType}
              AND related_project_id = #{projectId}
            LIMIT 1
            """)
    PartnerAccountTxn selectByBizTypeAndProjectId(@Param("bizType") String bizType,
                                                  @Param("projectId") Long projectId);
}
