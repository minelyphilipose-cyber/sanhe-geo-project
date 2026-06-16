package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SelfMediaPlatformScheduleAdapterRouter {
    private final Map<String, SelfMediaPlatformScheduleAdapter> adapters;

    public SelfMediaPlatformScheduleAdapterRouter(List<SelfMediaPlatformScheduleAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toUnmodifiableMap(
                        adapter -> normalize(adapter.platform()),
                        Function.identity()
                ));
    }

    public Optional<SelfMediaPlatformScheduleAdapter> find(String platform) {
        String normalized = normalize(platform);
        return StringUtils.hasText(normalized) ? Optional.ofNullable(adapters.get(normalized)) : Optional.empty();
    }

    public SelfMediaPlatformScheduleRules rules(String platform, String strategy) {
        return find(platform)
                .map(adapter -> adapter.scheduleRules(strategy))
                .orElseGet(SelfMediaPlatformScheduleRules::defaults);
    }

    public Optional<SelfMediaPlatformCapabilityContract> contract(String platform) {
        return find(platform).map(SelfMediaPlatformScheduleAdapter::capabilityContract);
    }

    public List<SelfMediaPlatformCapabilityContract> contracts() {
        return adapters.values().stream()
                .map(SelfMediaPlatformScheduleAdapter::capabilityContract)
                .toList();
    }

    public Set<String> platformsByChannel(SelfMediaPlatformPublishChannel channel) {
        if (channel == null) {
            return Set.of();
        }
        return adapters.values().stream()
                .map(SelfMediaPlatformScheduleAdapter::capabilityContract)
                .filter(contract -> channel.equals(contract.publishChannel()))
                .map(SelfMediaPlatformCapabilityContract::platform)
                .filter(StringUtils::hasText)
                .map(SelfMediaPlatformScheduleAdapterRouter::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        String publishPlatform = ArticlePromptChannels.canonicalSelfMediaPublishPlatform(normalized);
        return StringUtils.hasText(publishPlatform) ? publishPlatform : normalized;
    }
}
