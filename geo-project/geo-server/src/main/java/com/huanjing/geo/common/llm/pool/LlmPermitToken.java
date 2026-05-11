package com.huanjing.geo.common.llm.pool;

public record LlmPermitToken(
        String key,
        String member,
        String scope,
        String platformCode,
        long leaseUntilMillis
) implements LeaseToken {
    @Override
    public LlmPermitToken renewUntil(long newLeaseUntilMillis) {
        return new LlmPermitToken(key, member, scope, platformCode, newLeaseUntilMillis);
    }
}
