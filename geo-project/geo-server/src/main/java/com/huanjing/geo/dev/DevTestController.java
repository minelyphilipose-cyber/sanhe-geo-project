package com.huanjing.geo.dev;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.BrandOfficialSite;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.service.BrandOfficialSiteService;
import com.huanjing.geo.module.content.service.ContentDistributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only scaffold for invoking distributeTo before P1.5 controller is in place.
 * Will be removed in P1.5 commit.
 *
 * <p>Active only under "dev" profile. Production startup must NOT load this.
 */
@RestController
@RequestMapping("/dev/test")
@Profile("dev")
@RequiredArgsConstructor
public class DevTestController {

    private final ContentDistributionService contentDistributionService;
    private final BrandOfficialSiteService brandOfficialSiteService;

    @PostMapping("/distribute-to-brand-official-site")
    public R<DistributionTask> testDistributeTo(
            @RequestParam Long articleId,
            @RequestParam Long brandOfficialSiteId) {
        BrandOfficialSite site = brandOfficialSiteService.getSite(brandOfficialSiteId);
        TargetContext.BrandOfficialSiteTarget target =
            new TargetContext.BrandOfficialSiteTarget(site);
        return R.ok(contentDistributionService.distributeTo(articleId, target));
    }
}
