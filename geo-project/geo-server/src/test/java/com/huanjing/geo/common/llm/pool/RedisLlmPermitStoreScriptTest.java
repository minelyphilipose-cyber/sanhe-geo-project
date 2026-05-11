package com.huanjing.geo.common.llm.pool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisLlmPermitStoreScriptTest {

    @Test
    void acquireScriptUsesRuntimeLimitAndMaxScoreTtl() {
        String script = RedisLlmPermitStore.acquireScript();

        assertTrue(script.contains("local limit = tonumber(ARGV[2])"));
        assertTrue(script.contains("local active = redis.call('ZCARD', key)"));
        assertTrue(script.contains("if active >= limit then"));
        assertTrue(script.contains("ZREVRANGE"));
        assertTrue(script.contains("maxScore - now + safety"));
    }

    @Test
    void releaseScriptReturnsZremResult() {
        String script = RedisLlmPermitStore.releaseScript();

        assertTrue(script.contains("local removed = redis.call('ZREM', key, member)"));
        assertTrue(script.contains("return removed"));
    }
}
