package com.huanjing.geo.module.content.service.adapter;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
                .orElseGet(() -> new SelfMediaPlatformScheduleRules(10, 0, 1));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
