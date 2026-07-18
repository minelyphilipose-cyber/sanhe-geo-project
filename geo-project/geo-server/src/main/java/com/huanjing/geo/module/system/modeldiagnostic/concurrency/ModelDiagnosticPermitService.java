package com.huanjing.geo.module.system.modeldiagnostic.concurrency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelDiagnosticPermitService {

    public static final String BUSY_CODE = "DIAGNOSTIC_BUSY";
    public static final String UNAVAILABLE_CODE = "DIAGNOSTIC_PERMIT_UNAVAILABLE";
    public static final String INTERRUPTED_CODE = "DIAGNOSTIC_PERMIT_INTERRUPTED";

    static final long ACQUIRE_WAIT_MILLIS = 1_000L;
    static final long RETRY_INTERVAL_MILLIS = 50L;
    static final long LEASE_SAFETY_MILLIS = 30_000L;
    private static final String HASH_TAG = "{model-diagnostic}";
    private static final String KEY_PREFIX = "geo:diagnostic:" + HASH_TAG + ":permits";

    private final ModelDiagnosticPermitStore store;

    public ModelDiagnosticPermit tryAcquire(Long operatorId, LocalDateTime deadlineAt) {
        if (operatorId == null || operatorId < 1 || deadlineAt == null) {
            throw new IllegalArgumentException("operatorId and deadlineAt are required for diagnostic permit");
        }
        String globalKey = globalKey();
        String operatorKey = operatorKey(operatorId);
        String ownerToken = UUID.randomUUID().toString();
        long leaseUntil = deadlineAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long initialNow = System.currentTimeMillis();
        if (leaseUntil <= initialNow) {
            return null;
        }
        long waitUntil = Math.min(initialNow + ACQUIRE_WAIT_MILLIS, leaseUntil);

        while (true) {
            long now = System.currentTimeMillis();
            if (now >= waitUntil || leaseUntil <= now) {
                return null;
            }
            try {
                if (store.acquire(globalKey, operatorKey, ownerToken,
                        now, leaseUntil, LEASE_SAFETY_MILLIS)) {
                    ModelDiagnosticPermit permit = new ModelDiagnosticPermit(
                            globalKey, operatorKey, ownerToken, this);
                    long acquiredAt = System.currentTimeMillis();
                    if (acquiredAt >= waitUntil || acquiredAt >= leaseUntil) {
                        permit.close();
                        return null;
                    }
                    return permit;
                }
            } catch (RuntimeException ex) {
                throw new ModelDiagnosticPermitAccessException(
                        UNAVAILABLE_CODE, "Diagnostic permit store is unavailable", ex);
            }
            long afterAttempt = System.currentTimeMillis();
            if (afterAttempt >= waitUntil || leaseUntil <= afterAttempt) {
                return null;
            }
            long sleepMillis = Math.min(
                    RETRY_INTERVAL_MILLIS, Math.max(1L, waitUntil - afterAttempt));
            try {
                TimeUnit.MILLISECONDS.sleep(sleepMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ModelDiagnosticPermitAccessException(
                        INTERRUPTED_CODE, "Interrupted while acquiring diagnostic permit", ex);
            }
        }
    }

    void release(ModelDiagnosticPermit permit) {
        try {
            store.release(permit.globalKey(), permit.operatorKey(), permit.ownerToken(),
                    System.currentTimeMillis(), LEASE_SAFETY_MILLIS);
        } catch (RuntimeException ex) {
            log.warn("Failed to release diagnostic permit; TTL recovery will reclaim it");
        }
    }

    static String globalKey() {
        return KEY_PREFIX + ":global";
    }

    static String operatorKey(Long operatorId) {
        return KEY_PREFIX + ":operator:" + operatorId;
    }
}
