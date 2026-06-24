package com.huanjing.geo.common.llm.limiter;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PlatformConcurrencyLimiterService {

    private final ConcurrentMap<String, Slot> slots = new ConcurrentHashMap<>();

    public Permit acquire(AiPlatformConfig config) {
        if (config == null || !StringUtils.hasText(config.getPlatformCode())) {
            throw new BizException(500, "Platform code is required for concurrency limit");
        }
        String platformCode = config.getPlatformCode().trim();
        int limit = config.getConcurrencyLimit() == null || config.getConcurrencyLimit() <= 0
                ? 1
                : config.getConcurrencyLimit();
        Slot slot = slots.computeIfAbsent(platformCode, k -> new Slot());
        slot.acquire(limit);
        return new Permit(platformCode, slot);
    }

    public long waiterCount(String platformCode) {
        if (!StringUtils.hasText(platformCode)) {
            return 0L;
        }
        Slot slot = slots.get(platformCode.trim());
        return slot == null ? 0L : slot.waiters();
    }

    public static final class Permit implements AutoCloseable {
        private final String platformCode;
        private final Slot slot;
        private boolean released;

        private Permit(String platformCode, Slot slot) {
            this.platformCode = platformCode;
            this.slot = slot;
        }

        public String platformCode() {
            return platformCode;
        }

        @Override
        public void close() {
            if (released) {
                return;
            }
            released = true;
            slot.release();
        }
    }

    private static final class Slot {
        private final ReentrantLock lock = new ReentrantLock(true);
        private final Condition available = lock.newCondition();
        private int limit = 1;
        private int active = 0;
        private int waiters = 0;

        void acquire(int requestedLimit) {
            lock.lock();
            try {
                limit = Math.max(requestedLimit, 1);
                while (active >= limit) {
                    waiters++;
                    try {
                        available.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new BizException(500, "Interrupted while waiting for platform concurrency slot");
                    } finally {
                        waiters = Math.max(0, waiters - 1);
                    }
                    limit = Math.max(requestedLimit, 1);
                }
                active++;
            } finally {
                lock.unlock();
            }
        }

        void release() {
            lock.lock();
            try {
                if (active > 0) {
                    active--;
                }
                available.signal();
            } finally {
                lock.unlock();
            }
        }

        long waiters() {
            lock.lock();
            try {
                return waiters;
            } finally {
                lock.unlock();
            }
        }
    }
}
