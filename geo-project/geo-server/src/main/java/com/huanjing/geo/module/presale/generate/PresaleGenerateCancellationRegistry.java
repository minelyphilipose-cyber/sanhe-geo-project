package com.huanjing.geo.module.presale.generate;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PresaleGenerateCancellationRegistry {

    private final Set<Long> canceledVersionIds = ConcurrentHashMap.newKeySet();

    public void cancel(Long versionId) {
        if (versionId != null) {
            canceledVersionIds.add(versionId);
        }
    }

    public boolean isCanceled(Long versionId) {
        return versionId != null && canceledVersionIds.contains(versionId);
    }

    public void clear(Long versionId) {
        if (versionId != null) {
            canceledVersionIds.remove(versionId);
        }
    }
}
