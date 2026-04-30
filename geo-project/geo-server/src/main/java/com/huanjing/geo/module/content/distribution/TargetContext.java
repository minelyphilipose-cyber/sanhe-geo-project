package com.huanjing.geo.module.content.distribution;

import com.huanjing.geo.module.content.entity.BrandOfficialSite;
import com.huanjing.geo.module.content.entity.MpAccount;
import com.huanjing.geo.module.system.entity.PublishSite;

/**
 * Sealed target for multichannel distribution (C0–C4).
 */
public sealed interface TargetContext
        permits TargetContext.SiteTarget, TargetContext.MpAccountTarget, TargetContext.BrandOfficialSiteTarget,
        TargetContext.BrandGeoSiteTarget {

    record SiteTarget(PublishSite site) implements TargetContext {}

    record MpAccountTarget(MpAccount account) implements TargetContext {}

    record BrandOfficialSiteTarget(BrandOfficialSite site) implements TargetContext {}

    record BrandGeoSiteTarget(Long brandId, String siteCode) implements TargetContext {}
}
