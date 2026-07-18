package com.huanjing.geo.module.content.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlatformAccountIdentityPolicyTest {
    @Test
    void comparablePlatformAccountIdKeepsBaijiahaoAppId() {
        assertEquals("1867055852901021",
                PlatformAccountIdentityPolicy.comparablePlatformAccountId("baijiahao", "1867055852901021"));
    }

    @Test
    void comparablePlatformAccountIdKeepsReadablePlatformIds() {
        assertEquals("1865234056392716",
                PlatformAccountIdentityPolicy.comparablePlatformAccountId("toutiao", "1865234056392716"));
    }

    @Test
    void comparablePlatformAccountIdSkipsSyntheticIds() {
        assertNull(PlatformAccountIdentityPolicy.comparablePlatformAccountId(
                "baijiahao", "geo-baijiahao-990006013-5d42b6194aa240c8"));
    }
}
