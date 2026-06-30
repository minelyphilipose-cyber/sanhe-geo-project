package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardContentPlatformVO;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class MobileDashboardContentChannelCatalog {

    private static final List<Channel> CHANNELS = List.of(
            new Channel("official_site", "Agent官网", "globe", List.of("agent_site", "agent_site_article", "brand_geo_site", "agent_official_site")),
            new Channel("douyin", "抖音", "movie", List.of("self_media:douyin")),
            new Channel("xiaohongshu", "小红书", "favorite", List.of("self_media:xiaohongshu")),
            new Channel("wechat_mp", "公众号", "chat", List.of("wechat", "self_media:wechat", "self_media:wechat_mp")),
            new Channel("toutiao", "今日头条", "newspaper", List.of("self_media:toutiao")),
            new Channel("baijiahao", "百家号", "article", List.of("self_media:baijiahao")),
            new Channel("zhihu", "知乎", "question", List.of("self_media:zhihu"))
    );

    private MobileDashboardContentChannelCatalog() {
    }

    public static List<String> canonicalCodes() {
        return CHANNELS.stream().map(Channel::code).toList();
    }

    public static List<MobileDashboardContentPlatformVO> platformOptions() {
        return CHANNELS.stream()
                .map(channel -> new MobileDashboardContentPlatformVO(channel.code(), channel.label(), channel.icon()))
                .toList();
    }

    public static String normalize(String code) {
        String value = StringUtils.hasText(code) ? code.trim().toLowerCase(Locale.ROOT) : "";
        if (!StringUtils.hasText(value)) {
            return "";
        }
        for (Channel channel : CHANNELS) {
            if (channel.code().equals(value) || channel.aliases().contains(value)) {
                return channel.code();
            }
        }
        return value;
    }

    public static Set<String> detailChannelCodesWithAliases() {
        return CHANNELS.stream()
                .flatMap(channel -> {
                    List<String> values = new java.util.ArrayList<>();
                    values.add(channel.code());
                    values.addAll(channel.aliases());
                    return values.stream();
                })
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public static String quotedSql(Collection<String> values) {
        return values.stream()
                .map(v -> "'" + v.replace("'", "''") + "'")
                .reduce((a, b) -> a + "," + b)
                .orElse("''");
    }

    private record Channel(String code, String label, String icon, List<String> aliases) {
    }
}
