package com.huanjing.geo.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.dto.ThirdPartySubjectPoolBrandRow;
import com.huanjing.geo.module.customer.entity.Brand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BrandMapper extends BaseMapper<Brand> {

    @Select("""
            SELECT id
            FROM brand
            WHERE deleted_at IS NULL
            ORDER BY id DESC
            """)
    List<Long> selectActiveBrandIds();

    @Select("""
            SELECT id
            FROM brand
            WHERE id = #{id}
              AND deleted_at IS NULL
            FOR UPDATE
            """)
    Long lockActiveBrandById(@Param("id") Long id);

    @Select("""
            SELECT DISTINCT b.*
            FROM brand b
            JOIN brand_channel_template_perspective p
              ON p.brand_id = b.id
             AND p.enabled = 1
             AND p.channel_group_code = 'self_media'
             AND p.perspective_code IN ('industry_neutral', 'review_recommend')
            WHERE b.deleted_at IS NULL
              AND b.status = 'active'
            ORDER BY b.id ASC
            """)
    List<Brand> selectThirdPartySourceBrands();

    @Select("""
            SELECT b.id AS brandId,
                   b.brand_name AS brandName,
                   b.industry AS industry,
                   b.compliance_industry_code AS complianceIndustryCode,
                   b.allow_third_party_promotion AS allowThirdPartyPromotion,
                   b.company_id AS companyId,
                   c.company_name AS companyName,
                   c.status AS companyStatus,
                   EXISTS (
                     SELECT 1
                     FROM company_package_binding cpb
                     WHERE cpb.company_id = b.company_id
                       AND cpb.status = 'active'
                       AND cpb.active_flag = 1
                   ) AS hasActivePackage,
                   (
                     SELECT MIN(p.id)
                     FROM project p
                     WHERE p.brand_id = b.id
                       AND p.deleted_at IS NULL
                       AND p.status = 'active'
                   ) AS subjectProjectId
            FROM brand b
            LEFT JOIN company c ON c.id = b.company_id AND c.deleted_at IS NULL
            WHERE b.deleted_at IS NULL
              AND b.status = 'active'
            ORDER BY b.id ASC
            """)
    List<ThirdPartySubjectPoolBrandRow> selectThirdPartySubjectPoolRows();
}
