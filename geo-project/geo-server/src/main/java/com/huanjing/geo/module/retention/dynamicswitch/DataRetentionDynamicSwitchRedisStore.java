package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.CasResult;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.CasResultCode;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.DynamicSwitchException;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Payload;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Snapshot;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SnapshotIntegrity;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SnapshotPair;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
public class DataRetentionDynamicSwitchRedisStore {

    private static final DefaultRedisScript<String> READ_PAIR = script(
            "redis/data-retention-dynamic-switch-read-pair-v1.lua");
    private static final DefaultRedisScript<String> REDIS_TIME = script(
            "redis/data-retention-dynamic-switch-time-v1.lua");
    private static final DefaultRedisScript<String> ENABLE_CAS = script(
            "redis/data-retention-dynamic-switch-enable-cas-v1.lua");
    private static final DefaultRedisScript<String> DISABLE_CAS = script(
            "redis/data-retention-dynamic-switch-disable-cas-v1.lua");

    private final StringRedisTemplate redisTemplate;
    private final DataRetentionDynamicSwitchMetrics metrics;

    @Autowired
    public DataRetentionDynamicSwitchRedisStore(StringRedisTemplate redisTemplate,
                                                DataRetentionDynamicSwitchMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
    }

    public SnapshotPair readPair(SwitchKey domainSwitch) {
        if (domainSwitch == null || domainSwitch == SwitchKey.GLOBAL_EXECUTE) {
            throw new IllegalArgumentException("domain switch required");
        }
        String response = execute("read_pair", () -> redisTemplate.execute(READ_PAIR,
                List.of(SwitchKey.GLOBAL_EXECUTE.redisKey(), domainSwitch.redisKey())));
        String[] fields = split(response, 10, "read-pair");
        return new SnapshotPair(parseSnapshot(SwitchKey.GLOBAL_EXECUTE, fields, 0),
                parseSnapshot(domainSwitch, fields, 5));
    }

    public Snapshot read(SwitchKey switchKey) {
        Objects.requireNonNull(switchKey, "switchKey");
        SwitchKey second = switchKey == SwitchKey.GLOBAL_EXECUTE
                ? SwitchKey.WEBSITE_PUBLISHED_CONTENT_EXECUTE : switchKey;
        SnapshotPair pair = readPair(second);
        return switchKey == SwitchKey.GLOBAL_EXECUTE ? pair.global() : pair.domain();
    }

    public Instant redisTime() {
        String response = execute("time", () -> redisTemplate.execute(
                REDIS_TIME, List.of(SwitchKey.GLOBAL_EXECUTE.redisKey())));
        if (response == null || !response.matches("[0-9]+:[0-9]{1,6}")) {
            throw new DynamicSwitchException(503, "REDIS_UNAVAILABLE", "Redis TIME returned malformed data");
        }
        int separator = response.indexOf(':');
        try {
            long seconds = Long.parseLong(response.substring(0, separator));
            String microsText = response.substring(separator + 1);
            long micros = Long.parseLong(microsText);
            if (micros < 0 || micros > 999_999) {
                throw new NumberFormatException("microseconds out of range");
            }
            return Instant.ofEpochMilli(Math.addExact(Math.multiplyExact(seconds, 1000L), micros / 1000L));
        } catch (ArithmeticException | NumberFormatException e) {
            throw new DynamicSwitchException(503, "REDIS_UNAVAILABLE", "Redis TIME is out of range", e);
        }
    }

    public CasResult enableCas(SwitchKey switchKey, Payload target, Snapshot expected, Instant approvedAt) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(approvedAt, "approvedAt");
        if (!target.enabled()) {
            throw new IllegalArgumentException("enable target required");
        }
        if (expected.integrity() != SnapshotIntegrity.VALID && expected.integrity() != SnapshotIntegrity.MISSING) {
            return new CasResult(CasResultCode.EXPECTED_MISMATCH, expected);
        }
        String expectedState = expected.integrity() == SnapshotIntegrity.MISSING ? "M" : "V";
        Payload expectedPayload = expected.payload();
        String response = execute("enable_cas", () -> redisTemplate.execute(ENABLE_CAS, List.of(switchKey.redisKey()),
                target.changeId(), target.versionText(), target.enabledText(), target.changedAtText(),
                expectedState,
                expectedPayload == null ? "" : expectedPayload.changeId(),
                expectedPayload == null ? "" : expectedPayload.versionText(),
                expectedPayload == null ? "" : expectedPayload.enabledText(),
                expectedPayload == null ? "" : expectedPayload.changedAtText(),
                Long.toString(approvedAt.toEpochMilli())));
        return parseCasResult(switchKey, response);
    }

    public CasResult disableCas(SwitchKey switchKey, Payload target) {
        Objects.requireNonNull(target, "target");
        if (target.enabled()) {
            throw new IllegalArgumentException("disable target required");
        }
        String response = execute("disable_cas", () -> redisTemplate.execute(DISABLE_CAS, List.of(switchKey.redisKey()),
                target.changeId(), target.versionText(), target.enabledText(), target.changedAtText()));
        return parseCasResult(switchKey, response);
    }

    private CasResult parseCasResult(SwitchKey switchKey, String response) {
        String[] fields = split(response, 6, "cas");
        final CasResultCode code;
        try {
            code = CasResultCode.valueOf(fields[0]);
        } catch (IllegalArgumentException e) {
            throw new DynamicSwitchException(503, "REDIS_UNAVAILABLE", "Redis CAS returned unknown result", e);
        }
        return new CasResult(code, parseSnapshot(switchKey, fields, 1));
    }

    private Snapshot parseSnapshot(SwitchKey key, String[] fields, int offset) {
        return switch (fields[offset]) {
            case "M" -> Snapshot.missing(key);
            case "X" -> Snapshot.malformed(key);
            case "V" -> decodeOrMalformed(key, fields, offset);
            default -> throw new DynamicSwitchException(503, "REDIS_UNAVAILABLE", "Redis script returned unknown state");
        };
    }

    private Snapshot decodeOrMalformed(SwitchKey key, String[] fields, int offset) {
        try {
            return new Snapshot(key, SnapshotIntegrity.VALID,
                    Payload.decode(fields[offset + 1], fields[offset + 2], fields[offset + 3], fields[offset + 4]), null);
        } catch (IllegalArgumentException | DynamicSwitchException invalid) {
            return Snapshot.malformed(key);
        }
    }

    private static String[] split(String response, int expected, String operation) {
        if (response == null) {
            throw new DynamicSwitchException(503, "REDIS_UNAVAILABLE", "Redis " + operation + " returned no data");
        }
        String[] fields = response.split("\\t", -1);
        if (fields.length != expected) {
            throw new DynamicSwitchException(503, "REDIS_UNAVAILABLE", "Redis " + operation + " returned malformed data");
        }
        return fields;
    }

    private static DefaultRedisScript<String> script(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try {
            String source = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return new DefaultRedisScript<>(source, String.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load Redis script " + path, e);
        }
    }

    private <T> T execute(String operation, java.util.function.Supplier<T> action) {
        long started = System.nanoTime();
        try {
            return action.get();
        } finally {
            metrics.redisLatency(operation, System.nanoTime() - started);
        }
    }
}
