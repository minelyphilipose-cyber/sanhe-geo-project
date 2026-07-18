package com.huanjing.geo.module.system.modeldiagnostic.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelDiagnosticCleanupLockService {

    static final String LOCK_KEY = "geo:model-diagnostic:cleanup:lock";
    static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final ModelDiagnosticCleanupLockStore lockStore;

    public boolean tryAcquire(String ownerToken) {
        try {
            return lockStore.tryAcquire(LOCK_KEY, ownerToken, LOCK_TTL);
        } catch (RuntimeException ex) {
            log.warn("Diagnostic cleanup lock acquisition failed; cleanup skipped", ex);
            return false;
        }
    }

    public void release(String ownerToken) {
        try {
            lockStore.release(LOCK_KEY, ownerToken);
        } catch (RuntimeException ex) {
            log.warn("Diagnostic cleanup lock release failed", ex);
        }
    }
}
