package com.huanjing.geo.common.llm.pool;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LlmExecutionPermit implements AutoCloseable {
    private final List<LlmPermitToken> tokens;
    private final String requestId;
    private final String feature;
    private final long acquiredAtMillis;
    private final LlmExecutionGateway owner;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    LlmExecutionPermit(List<LlmPermitToken> tokens,
                       String requestId,
                       String feature,
                       long acquiredAtMillis,
                       LlmExecutionGateway owner) {
        this.tokens = List.copyOf(tokens);
        this.requestId = requestId;
        this.feature = feature;
        this.acquiredAtMillis = acquiredAtMillis;
        this.owner = owner;
    }

    public List<LlmPermitToken> tokens() {
        return tokens;
    }

    public String requestId() {
        return requestId;
    }

    public String feature() {
        return feature;
    }

    public long acquiredAtMillis() {
        return acquiredAtMillis;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            owner.release(this);
        }
    }
}
