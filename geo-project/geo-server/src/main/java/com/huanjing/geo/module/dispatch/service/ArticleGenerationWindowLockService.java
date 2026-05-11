package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.common.llm.pool.LeaseRenewalService;
import com.huanjing.geo.common.llm.pool.LlmPermitScope;
import com.huanjing.geo.common.llm.pool.LlmPermitToken;
import com.huanjing.geo.common.llm.pool.LlmPoolProperties;
import com.huanjing.geo.common.llm.pool.RedisLlmPermitStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArticleGenerationWindowLockService {
    private final RedisLlmPermitStore permitStore;
    private final LeaseRenewalService leaseRenewalService;
    private final LlmPoolProperties properties;

    public WindowLock tryLock(Long projectId, String targetChannel, String periodKey) {
        String key = properties.getPermitKeyPrefix()
                + ":article-window:"
                + projectId
                + ":"
                + normalize(targetChannel)
                + ":"
                + normalize(periodKey);
        String member = Thread.currentThread().getId() + ":" + UUID.randomUUID();
        long now = System.currentTimeMillis();
        long leaseUntil = now + properties.getLeaseMs();
        boolean acquired = permitStore.acquire(key, member, 1, now, leaseUntil, properties.getLeaseSafetyMs());
        if (!acquired) {
            return null;
        }
        LlmPermitToken token = new LlmPermitToken(
                key,
                member,
                LlmPermitScope.ARTICLE_WINDOW.name(),
                null,
                leaseUntil
        );
        leaseRenewalService.register(List.of(token));
        return new WindowLock(token);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "_";
        }
        return value.trim().replaceAll("[^A-Za-z0-9_.:-]", "_");
    }

    public final class WindowLock implements AutoCloseable {
        private final LlmPermitToken token;
        private boolean closed;

        private WindowLock(LlmPermitToken token) {
            this.token = token;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            leaseRenewalService.unregister(List.of(token));
            permitStore.release(token.key(), token.member(), System.currentTimeMillis(), properties.getLeaseSafetyMs());
        }
    }
}
