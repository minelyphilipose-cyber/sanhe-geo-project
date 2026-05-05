package com.huanjing.geo.module.content.distribution;

import com.huanjing.geo.module.content.entity.BrandOfficialSite;
import com.huanjing.geo.module.content.entity.MpAccount;
import com.huanjing.geo.module.system.entity.PublishSite;

import java.util.List;
import java.util.Map;

/**
 * Sealed target for multichannel distribution (C0–C4).
 */
public sealed interface TargetContext
        permits TargetContext.SiteTarget, TargetContext.BrandOfficialSiteTarget,
        TargetContext.BrandGeoSiteTarget, TargetContext.SelfMediaTarget {

    record SiteTarget(PublishSite site) implements TargetContext {}

    /**
     * TODO Step 2: replace MpAccount with SelfMediaAccount after account table abstraction.
     */
    record SelfMediaTarget(MpAccount account,
                           Long coverMaterialId,
                           List<Long> imageMaterialIds,
                           List<String> hashtags,
                           Integer privateStatus,
                           Integer downloadType,
                           String requestId,
                           Map<String, Object> platformOptions) implements TargetContext {}

    record BrandOfficialSiteTarget(BrandOfficialSite site) implements TargetContext {}

    record BrandGeoSiteTarget(Long brandId, String siteCode) implements TargetContext {}
}
