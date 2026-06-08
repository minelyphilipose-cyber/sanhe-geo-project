package com.huanjing.geo.module.content.service.adapter;

public interface SelfMediaPlatformScheduleAdapter extends SelfMediaAdapter {

    SelfMediaPlatformScheduleRules scheduleRules(String strategy);

    default SelfMediaPlatformCapabilityContract capabilityContract() {
        return SelfMediaPlatformCapabilityContract.unsupported(platform());
    }
}
