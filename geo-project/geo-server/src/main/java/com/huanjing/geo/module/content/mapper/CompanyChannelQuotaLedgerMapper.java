package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.CompanyChannelQuotaLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CompanyChannelQuotaLedgerMapper extends BaseMapper<CompanyChannelQuotaLedger> {

    @Update("UPDATE company_channel_quota_ledger " +
            "SET status = #{status}, " +
            "    confirmed_at = CASE WHEN #{status} = 'confirmed' THEN #{now} ELSE confirmed_at END, " +
            "    refunded_at = CASE WHEN #{status} = 'refunded' THEN #{now} ELSE refunded_at END, " +
            "    expire_checked_at = CASE WHEN #{status} = 'expired' THEN #{now} ELSE expire_checked_at END " +
            "WHERE id = #{id} AND status = 'reserved'")
    int updateStatusFromReserved(@Param("id") Long id,
                                 @Param("status") String status,
                                 @Param("now") LocalDateTime now);

    @Update("UPDATE company_channel_quota_ledger " +
            "SET status = 'refunded', refunded_at = #{now} " +
            "WHERE id = #{id} AND status = 'confirmed'")
    int refundConfirmed(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE company_channel_quota_ledger " +
            "SET expire_checked_at = #{now} " +
            "WHERE id = #{id} AND status = 'reserved'")
    int touchExpireCheckedAt(@Param("id") Long id,
                             @Param("now") LocalDateTime now);

    @Select("SELECT * FROM company_channel_quota_ledger " +
            "WHERE status = 'reserved' AND reserved_at < #{before} " +
            "ORDER BY reserved_at ASC LIMIT #{limit}")
    List<CompanyChannelQuotaLedger> selectTimedOutReserved(@Param("before") LocalDateTime before,
                                                           @Param("limit") int limit);

    @Select("SELECT * FROM company_channel_quota_ledger " +
            "WHERE company_id = #{companyId} AND status = 'reserved'")
    List<CompanyChannelQuotaLedger> selectReservedByCompany(@Param("companyId") Long companyId);

    @Select("SELECT COUNT(1) FROM company_channel_quota_ledger " +
            "WHERE company_id = #{companyId} AND status = 'reserved'")
    long countReservedByCompany(@Param("companyId") Long companyId);
}
