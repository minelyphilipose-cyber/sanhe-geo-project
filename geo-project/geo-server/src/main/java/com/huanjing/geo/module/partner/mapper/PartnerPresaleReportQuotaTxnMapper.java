package com.huanjing.geo.module.partner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.partner.entity.PartnerPresaleReportQuotaTxn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PartnerPresaleReportQuotaTxnMapper extends BaseMapper<PartnerPresaleReportQuotaTxn> {

    @Select("""
            SELECT *
            FROM partner_presale_report_quota_txn
            WHERE partner_id = #{partnerId}
              AND request_id = #{requestId}
            LIMIT 1
            """)
    PartnerPresaleReportQuotaTxn selectByPartnerAndRequestId(@Param("partnerId") Long partnerId,
                                                             @Param("requestId") String requestId);

    @Select("""
            SELECT *
            FROM partner_presale_report_quota_txn
            WHERE report_id = #{reportId}
            LIMIT 1
            """)
    PartnerPresaleReportQuotaTxn selectByReportId(@Param("reportId") Long reportId);

    @Select("""
            SELECT COUNT(1)
            FROM partner_presale_report_quota_txn
            WHERE partner_id = #{partnerId}
              AND biz_type = 'free_quota'
              AND status IN ('reserved', 'confirmed')
            """)
    Long countReservedOrConfirmedFreeQuota(@Param("partnerId") Long partnerId);
}
