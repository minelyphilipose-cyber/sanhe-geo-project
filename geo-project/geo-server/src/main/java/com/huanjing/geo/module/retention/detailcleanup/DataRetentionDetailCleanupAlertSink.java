package com.huanjing.geo.module.retention.detailcleanup;

import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchAlertSink;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import org.springframework.stereotype.Component;

@Component
public class DataRetentionDetailCleanupAlertSink {
    private final DataRetentionDynamicSwitchAlertSink delegate;
    public DataRetentionDetailCleanupAlertSink(DataRetentionDynamicSwitchAlertSink delegate) { this.delegate = delegate; }
    public void alert(String code, String detail) {
        delegate.alert("WP8_" + code, SwitchKey.GLOBAL_EXECUTE, detail);
    }
}
