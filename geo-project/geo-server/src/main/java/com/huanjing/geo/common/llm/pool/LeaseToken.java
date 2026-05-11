package com.huanjing.geo.common.llm.pool;

public interface LeaseToken {
    String key();

    String member();

    String scope();

    String platformCode();

    long leaseUntilMillis();

    LeaseToken renewUntil(long newLeaseUntilMillis);
}
