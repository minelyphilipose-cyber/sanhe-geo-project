package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.dto.ChannelQuotaSnapshotItem;
import com.huanjing.geo.module.content.vo.SelfMediaAccountPlatformOptionVO;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SelfMediaAccountPlatformEligibilityService {
    private static final String SELF_MEDIA_CHANNEL_PREFIX = ArticlePromptChannels.SELF_MEDIA + ":";

    private final BrandMapper brandMapper;
    private final CompanyPackageBindingService companyPackageBindingService;
    private final SelfMediaScheduleCapabilityService scheduleCapabilityService;

    public List<SelfMediaAccountPlatformOptionVO> listByBrand(Long brandId) {
        Brand brand = requireBrand(brandId);
        CompanyPackageBinding binding = companyPackageBindingService.activeBinding(brand.getCompanyId());
        Map<String, ChannelQuotaSnapshotItem> quotaByChannel = parseChannelQuotaSnapshot(binding);

        return ArticlePromptChannels.SELF_MEDIA_SUB_CODES.stream()
                .map(platform -> buildOption(platform, binding, quotaByChannel.get(SELF_MEDIA_CHANNEL_PREFIX + platform)))
                .toList();
    }

    public void requireEligible(Long brandId, String platform) {
        String normalized = normalizePlatform(platform);
        SelfMediaAccountPlatformOptionVO option = listByBrand(brandId).stream()
                .filter(item -> normalized.equals(item.getPlatform()))
                .findFirst()
                .orElse(null);
        if (option == null || !Boolean.TRUE.equals(option.getEligible())) {
            String reason = option == null || !StringUtils.hasText(option.getReason())
                    ? "当前平台未开放给该品牌"
                    : option.getReason();
            throw new BizException(400, reason, 200, Map.of(
                    "code", "SELF_MEDIA_PLATFORM_NOT_ELIGIBLE",
                    "platform", normalized
            ));
        }
    }

    private SelfMediaAccountPlatformOptionVO buildOption(String platform,
                                                        CompanyPackageBinding binding,
                                                        ChannelQuotaSnapshotItem quota) {
        SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness =
                scheduleCapabilityService.readiness(platform);
        boolean quotaEnabled = quota != null && quota.isEnabled() && quota.getQuotaLimit() > 0;
        boolean scheduleReady = readiness.ready();

        SelfMediaAccountPlatformOptionVO option = new SelfMediaAccountPlatformOptionVO();
        option.setPlatform(platform);
        option.setLabel(ArticlePromptChannels.channelName(ArticlePromptChannels.SELF_MEDIA, platform));
        option.setQuotaEnabled(quotaEnabled);
        option.setQuotaLimit(quota == null ? 0 : quota.getQuotaLimit());
        option.setQuotaStatus(quotaStatus(binding, quota));
        option.setScheduleReady(scheduleReady);
        option.setScheduleCode(readiness.code());
        option.setEligible(quotaEnabled && scheduleReady);
        option.setReason(reason(binding, quota, readiness));
        return option;
    }

    private String quotaStatus(CompanyPackageBinding binding, ChannelQuotaSnapshotItem quota) {
        if (binding == null) {
            return "no_active_package";
        }
        if (quota == null || !quota.isEnabled()) {
            return "not_enabled";
        }
        if (quota.getQuotaLimit() <= 0) {
            return "quota_zero";
        }
        return "enabled";
    }

    private String reason(CompanyPackageBinding binding,
                          ChannelQuotaSnapshotItem quota,
                          SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness) {
        if (binding == null) {
            return "客户未绑定启用中的套餐";
        }
        if (quota == null || !quota.isEnabled()) {
            return "当前客户套餐未开通该自媒体平台";
        }
        if (quota.getQuotaLimit() <= 0) {
            return "当前客户套餐该平台额度为 0";
        }
        if (!readiness.ready()) {
            return StringUtils.hasText(readiness.message()) ? readiness.message() : "平台排期能力未就绪";
        }
        return null;
    }

    private Map<String, ChannelQuotaSnapshotItem> parseChannelQuotaSnapshot(CompanyPackageBinding binding) {
        Map<String, ChannelQuotaSnapshotItem> snapshot = new LinkedHashMap<>();
        if (binding == null || !StringUtils.hasText(binding.getChannelQuotaSnapshot())) {
            return snapshot;
        }
        JSONArray arr = JSONUtil.parseArray(binding.getChannelQuotaSnapshot());
        for (Object obj : arr) {
            ChannelQuotaSnapshotItem item = JSONUtil.toBean(JSONUtil.parseObj(obj), ChannelQuotaSnapshotItem.class);
            if (!StringUtils.hasText(item.getChannelCode())) {
                continue;
            }
            snapshot.put(item.getChannelCode().trim(), item);
        }
        return snapshot;
    }

    private Brand requireBrand(Long brandId) {
        Brand brand = brandId == null ? null : brandMapper.selectById(brandId);
        if (brand == null) {
            throw new BizException(404, "brand not found");
        }
        return brand;
    }

    private String normalizePlatform(String platform) {
        String canonical = ArticlePromptChannels.canonicalSelfMediaQuotaPlatform(platform);
        if (!StringUtils.hasText(canonical)) {
            throw new BizException(400, "unsupported self-media platform");
        }
        return canonical.trim().toLowerCase(Locale.ROOT);
    }
}
