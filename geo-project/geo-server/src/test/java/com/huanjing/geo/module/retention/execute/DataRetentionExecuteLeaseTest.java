package com.huanjing.geo.module.retention.execute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRetentionExecuteLeaseTest {
    @Test
    void renewAndReleaseAreOwnerGuarded() {
        assertTrue(DataRetentionExecuteLease.renewScript().contains("GET', KEYS[1]) == ARGV[1]"));
        assertTrue(DataRetentionExecuteLease.renewScript().contains("PEXPIRE"));
        assertTrue(DataRetentionExecuteLease.releaseScript().contains("GET', KEYS[1]) == ARGV[1]"));
        assertTrue(DataRetentionExecuteLease.releaseScript().contains("DEL"));
    }
}
