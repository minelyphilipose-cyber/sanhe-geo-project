package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.DecisionCode;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Payload;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Snapshot;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SnapshotIntegrity;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SnapshotPair;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRetentionDynamicSwitchModelsTest {

    private static final String ID = "0f4e3df1-331b-4c61-9b94-8f53866d02da";
    private static final Instant AT = Instant.parse("2026-08-12T06:30:45.123Z");

    @Test
    void keyAndDomainMappingIsClosed() {
        assertEquals(SwitchKey.WEBSITE_PUBLISHED_CONTENT_EXECUTE,
                SwitchKey.forDomain(RetentionDomain.WEBSITE_PUBLISHED_CONTENT));
        assertEquals(SwitchKey.POLL_RESULTS_EXECUTE, SwitchKey.forDomain(RetentionDomain.POLL_RESULTS));
        assertThrows(RuntimeException.class, () -> SwitchKey.fromDatabaseValue(" poll_results_execute"));
        assertTrue(SwitchKey.GLOBAL_EXECUTE.redisKey().contains("{execute-switch}"));
    }

    @Test
    void payloadRequiresCanonicalBytesAndLongRange() {
        Payload max = Payload.decode(ID, Long.toString(Long.MAX_VALUE), "1", "2026-08-12T06:30:45.123Z");
        assertEquals(Long.MAX_VALUE, max.version());
        Payload aboveJsSafeInteger = Payload.decode(ID, "9007199254740993", "0", "2026-08-12T06:30:45.123Z");
        assertEquals(9_007_199_254_740_993L, aboveJsSafeInteger.version());
        assertThrows(IllegalArgumentException.class, () -> Payload.decode(ID, "01", "0", "2026-08-12T06:30:45.123Z"));
        assertThrows(RuntimeException.class, () -> Payload.decode(ID.toUpperCase(), "1", "0", "2026-08-12T06:30:45.123Z"));
        assertThrows(IllegalArgumentException.class, () -> Payload.decode(ID, "1", "true", "2026-08-12T06:30:45.123Z"));
        assertThrows(IllegalArgumentException.class, () -> Payload.decode(ID, "1", "0", "2026-08-12T06:30:45Z"));
        assertThrows(IllegalArgumentException.class, () -> Payload.decode(ID, "1", "0", "0999-08-12T06:30:45.123Z"));
        Payload exactMillis = Payload.decode(ID, "1", "0", "2026-08-12T06:30:45.000Z");
        assertEquals("2026-08-12T06:30:45.000Z", exactMillis.changedAtText());
    }

    @Test
    void gateDecisionPriorityDoesNotHideMalformedDomain() {
        Snapshot globalFalse = valid(SwitchKey.GLOBAL_EXECUTE, false);
        Snapshot domainMalformed = Snapshot.malformed(SwitchKey.POLL_RESULTS_EXECUTE);
        assertEquals(DecisionCode.MALFORMED_DOMAIN_STATE,
                DataRetentionDynamicSwitchGate.RedisGate.decide(new SnapshotPair(globalFalse, domainMalformed)));

        Snapshot globalMalformed = Snapshot.malformed(SwitchKey.GLOBAL_EXECUTE);
        assertEquals(DecisionCode.MALFORMED_GLOBAL_STATE,
                DataRetentionDynamicSwitchGate.RedisGate.decide(new SnapshotPair(globalMalformed, domainMalformed)));
        assertEquals(DecisionCode.GLOBAL_DISABLED, DataRetentionDynamicSwitchGate.RedisGate.decide(
                new SnapshotPair(Snapshot.missing(SwitchKey.GLOBAL_EXECUTE),
                        Snapshot.missing(SwitchKey.POLL_RESULTS_EXECUTE))));
        assertEquals(DecisionCode.ENABLED, DataRetentionDynamicSwitchGate.RedisGate.decide(
                new SnapshotPair(valid(SwitchKey.GLOBAL_EXECUTE, true),
                        valid(SwitchKey.POLL_RESULTS_EXECUTE, true))));
    }

    @Test
    void errorTruncationRespectsUtf8AndSurrogateBoundaries() {
        String source = "测".repeat(1000) + "\uD83D\uDE80";
        String safe = DataRetentionDynamicSwitchChangeRepository.safeError(source);
        assertTrue(safe.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 2048);
        assertFalse(Character.isHighSurrogate(safe.charAt(safe.length() - 1)));
    }

    @Test
    void divergenceClassificationIsStable() {
        var missing = Snapshot.missing(SwitchKey.POLL_RESULTS_EXECUTE);
        assertEquals("NONE", DataRetentionDynamicSwitchManagementService.divergence(null, missing));
        var runtime = valid(SwitchKey.POLL_RESULTS_EXECUTE, false);
        assertEquals("REDIS_AHEAD_OF_AUDIT",
                DataRetentionDynamicSwitchManagementService.divergence(null, runtime));
    }

    private static Snapshot valid(SwitchKey key, boolean enabled) {
        return new Snapshot(key, SnapshotIntegrity.VALID, new Payload(ID, 1, enabled, AT), null);
    }
}
