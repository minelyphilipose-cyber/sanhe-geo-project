package com.huanjing.geo.module.content.distribution;

import com.huanjing.geo.module.content.entity.BrandOfficialSite;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.PublishSite;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Sealed target for multichannel distribution (C0–C4).
 */
public sealed interface TargetContext
        permits TargetContext.SiteTarget, TargetContext.BrandOfficialSiteTarget,
        TargetContext.BrandGeoSiteTarget, TargetContext.SelfMediaTarget,
        TargetContext.IndustrySiteTarget, TargetContext.ForumSiteTarget,
        TargetContext.AuthorityMediaTarget {

    record SiteTarget(PublishSite site) implements TargetContext {}

    record SelfMediaTarget(SelfMediaAccount account,
                           Long coverMaterialId,
                           List<Long> imageMaterialIds,
                           List<String> hashtags,
                           Integer privateStatus,
                           Integer downloadType,
                           String requestId,
                           Map<String, Object> platformOptions) implements TargetContext {}

    record BrandOfficialSiteTarget(BrandOfficialSite site) implements TargetContext {}

    record BrandGeoSiteTarget(Long brandId, String siteCode) implements TargetContext {}

    record IndustrySiteTarget(PublishSite site, Project project) implements TargetContext {
        public IndustrySiteTarget(PublishSite site) {
            this(site, null);
        }
    }

    record ForumSiteTarget(PublishSite site, Project project) implements TargetContext {
        public ForumSiteTarget(PublishSite site) {
            this(site, null);
        }
    }

    record AuthorityMediaTarget(Long resourceId,
                                BigDecimal salingPrice,
                                String previewUrl,
                                String publishedAt,
                                String remark) implements TargetContext {}
}
