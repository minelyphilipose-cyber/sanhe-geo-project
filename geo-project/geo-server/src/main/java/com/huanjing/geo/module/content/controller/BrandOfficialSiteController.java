package com.huanjing.geo.module.content.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.dto.BrandOfficialSiteCreateRequest;
import com.huanjing.geo.module.content.dto.BrandOfficialSiteUpdateRequest;
import com.huanjing.geo.module.content.entity.BrandOfficialSite;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.BrandOfficialSiteMapper;
import com.huanjing.geo.module.content.service.BrandOfficialSiteService;
import com.huanjing.geo.module.content.service.ContentDistributionService;
import com.huanjing.geo.module.content.service.adapter.AuthCheckResult;
import com.huanjing.geo.module.content.service.adapter.OfficialCmsSiteAdapter;
import com.huanjing.geo.module.content.vo.BrandOfficialSiteVO;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "BrandOfficialSite")
@RestController
@RequestMapping("/api/brand-official-sites")
@RequiredArgsConstructor
public class BrandOfficialSiteController {

    private final BrandOfficialSiteService brandOfficialSiteService;
    private final BrandOfficialSiteMapper brandOfficialSiteMapper;
    private final CurrentUserService currentUserService;
    private final OfficialCmsSiteAdapter officialCmsSiteAdapter;
    private final ContentDistributionService contentDistributionService;

    @GetMapping
    public R<List<BrandOfficialSiteVO>> list(@RequestParam Long brandId) {
        ensureBrandAccess(brandId, "brand_official_site");
        return R.ok(brandOfficialSiteService.listByBrand(brandId).stream()
                .map(BrandOfficialSiteVO::from)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public R<BrandOfficialSiteVO> get(@PathVariable Long id) {
        BrandOfficialSite site = requireSiteAndAccess(id);
        return R.ok(BrandOfficialSiteVO.from(site));
    }

    @PostMapping
    public R<BrandOfficialSiteVO> create(@RequestParam Long brandId,
                                         @Valid @RequestBody BrandOfficialSiteCreateRequest req) {
        ensureBrandAccess(brandId, "brand_official_site");
        return R.ok(BrandOfficialSiteVO.from(brandOfficialSiteService.createSite(brandId, req)));
    }

    @PutMapping("/{id}")
    public R<BrandOfficialSiteVO> update(@PathVariable Long id,
                                         @RequestBody BrandOfficialSiteUpdateRequest req) {
        requireSiteAndAccess(id);
        return R.ok(BrandOfficialSiteVO.from(brandOfficialSiteService.updateSite(id, req)));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        requireSiteAndAccess(id);
        brandOfficialSiteService.deleteSite(id);
        return R.ok();
    }

    @PostMapping("/{id}/check-auth")
    public R<AuthCheckResult> checkAuth(@PathVariable Long id) {
        BrandOfficialSite site = requireSiteAndAccess(id);
        return R.ok(officialCmsSiteAdapter.checkAuth(new TargetContext.BrandOfficialSiteTarget(site)));
    }

    @PostMapping("/{id}/distribute")
    public R<DistributionTask> distribute(@PathVariable Long id,
                                          @RequestParam Long articleId) {
        BrandOfficialSite site = requireSiteAndAccess(id);
        TargetContext.BrandOfficialSiteTarget target = new TargetContext.BrandOfficialSiteTarget(site);
        return R.ok(contentDistributionService.distributeTo(articleId, target));
    }

    private BrandOfficialSite requireSiteAndAccess(Long id) {
        BrandOfficialSite site = brandOfficialSiteMapper.selectOne(
                new LambdaQueryWrapper<BrandOfficialSite>()
                        .eq(BrandOfficialSite::getId, id)
                        .last("LIMIT 1")
        );
        if (site == null) {
            throw new BizException(404, "Brand official site not found");
        }
        ensureBrandAccess(site.getBrandId(), "brand_official_site");
        return site;
    }

    private void ensureBrandAccess(Long brandId, String resourceTag) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensureBrandAccess(operator, brandId, resourceTag);
    }
}
