package com.huanjing.geo.module.content.service.adapter;

public interface SelfMediaAdapter {

    String platform();

    default boolean supportsPlatform(String platform) {
        return platform() != null && platform().equalsIgnoreCase(platform);
    }
}
