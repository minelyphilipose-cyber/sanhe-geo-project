package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.CompanyChannelQuotaUsage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CompanyChannelQuotaUsageMapper extends BaseMapper<CompanyChannelQuotaUsage> {

    @Insert("INSERT IGNORE INTO company_channel_quota_usage " +
            "(company_id, channel_code, period_type, period_key, quota_limit, used_count) " +
            "VALUES (#{companyId}, #{channelCode}, #{periodType}, #{periodKey}, #{quotaLimit}, 0)")
    int insertIgnore(@Param("companyId") Long companyId,
                     @Param("channelCode") String channelCode,
                     @Param("periodType") String periodType,
                     @Param("periodKey") String periodKey,
                     @Param("quotaLimit") Integer quotaLimit);

    @Update("UPDATE company_channel_quota_usage " +
            "SET used_count = used_count + 1 " +
            "WHERE company_id = #{companyId} " +
            "  AND channel_code = #{channelCode} " +
            "  AND period_type = #{periodType} " +
            "  AND period_key = #{periodKey} " +
            "  AND used_count < quota_limit")
    int tryReserve(@Param("companyId") Long companyId,
                   @Param("channelCode") String channelCode,
                   @Param("periodType") String periodType,
                   @Param("periodKey") String periodKey);

    @Update("UPDATE company_channel_quota_usage " +
            "SET used_count = used_count - 1 " +
            "WHERE company_id = #{companyId} " +
            "  AND channel_code = #{channelCode} " +
            "  AND period_type = #{periodType} " +
            "  AND period_key = #{periodKey} " +
            "  AND used_count > 0")
    int releaseReserved(@Param("companyId") Long companyId,
                        @Param("channelCode") String channelCode,
                        @Param("periodType") String periodType,
                        @Param("periodKey") String periodKey);

    @Update("UPDATE company_channel_quota_usage " +
            "SET quota_limit = #{quotaLimit} " +
            "WHERE company_id = #{companyId} " +
            "  AND channel_code = #{channelCode} " +
            "  AND period_type = #{periodType} " +
            "  AND period_key = #{periodKey}")
    int updateQuotaLimit(@Param("companyId") Long companyId,
                         @Param("channelCode") String channelCode,
                         @Param("periodType") String periodType,
                         @Param("periodKey") String periodKey,
                         @Param("quotaLimit") Integer quotaLimit);
}
