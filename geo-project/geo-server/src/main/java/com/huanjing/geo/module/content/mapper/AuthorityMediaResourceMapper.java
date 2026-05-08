package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.AuthorityMediaResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;

import java.time.LocalDateTime;
import java.util.Collection;

@Mapper
public interface AuthorityMediaResourceMapper extends BaseMapper<AuthorityMediaResource> {

    @Select("""
            SELECT COALESCE(MAX(uptime), 0)
            FROM authority_media_resource
            WHERE resource_type = #{resourceType}
              AND uptime IS NOT NULL
            """)
    Long selectMaxUptime(@Param("resourceType") String resourceType);

    @Select("""
            SELECT *
            FROM authority_media_resource
            WHERE resource_type = #{resourceType}
              AND external_resource_id = #{externalResourceId}
            LIMIT 1
            """)
    AuthorityMediaResource selectByTypeAndExternalId(@Param("resourceType") String resourceType,
                                                     @Param("externalResourceId") String externalResourceId);

    @Insert("""
            INSERT INTO authority_media_resource (
              resource_type, external_resource_id, name, platform, industry, province,
              price, status, pc_weight, m_weight, news_resource, entrance_level,
              include_condition, publication_time, weekend_publish, publish_rate,
              inclusion_rate, remark, uptime, raw_payload, deleted_at
            ) VALUES (
              #{resource.resourceType}, #{resource.externalResourceId}, #{resource.name},
              #{resource.platform}, #{resource.industry}, #{resource.province},
              #{resource.price}, #{resource.status}, #{resource.pcWeight}, #{resource.mWeight},
              #{resource.newsResource}, #{resource.entranceLevel}, #{resource.includeCondition},
              #{resource.publicationTime}, #{resource.weekendPublish}, #{resource.publishRate},
              #{resource.inclusionRate}, #{resource.remark}, #{resource.uptime},
              #{resource.rawPayload}, NULL
            )
            ON DUPLICATE KEY UPDATE
              name = VALUES(name),
              platform = VALUES(platform),
              industry = VALUES(industry),
              province = VALUES(province),
              price = VALUES(price),
              status = VALUES(status),
              pc_weight = VALUES(pc_weight),
              m_weight = VALUES(m_weight),
              news_resource = VALUES(news_resource),
              entrance_level = VALUES(entrance_level),
              include_condition = VALUES(include_condition),
              publication_time = VALUES(publication_time),
              weekend_publish = VALUES(weekend_publish),
              publish_rate = VALUES(publish_rate),
              inclusion_rate = VALUES(inclusion_rate),
              remark = VALUES(remark),
              uptime = VALUES(uptime),
              raw_payload = VALUES(raw_payload),
              deleted_at = NULL
            """)
    int upsert(@Param("resource") AuthorityMediaResource resource);

    @Update("""
            <script>
            UPDATE authority_media_resource
            SET deleted_at = #{deletedAt}
            WHERE resource_type = #{resourceType}
              AND deleted_at IS NULL
            <if test="activeIds != null and activeIds.size() > 0">
              AND external_resource_id NOT IN
              <foreach collection="activeIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </if>
            </script>
            """)
    int markDeletedExcept(@Param("resourceType") String resourceType,
                          @Param("activeIds") Collection<String> activeIds,
                          @Param("deletedAt") LocalDateTime deletedAt);

    @Update("""
            UPDATE authority_media_resource
            SET deleted_at = #{deletedAt}
            WHERE id = #{id}
              AND deleted_at IS NULL
            """)
    int markDeletedById(@Param("id") Long id, @Param("deletedAt") LocalDateTime deletedAt);
}
