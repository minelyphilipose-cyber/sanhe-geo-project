package com.huanjing.geo.module.content.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DistributionReviewStatusPollJob {

    private static final String LOCK_KEY = "content:distribution_review_poll:lock";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final DistributionReviewStatusPollService pollService;
    private final StringRedisTemplate redisTemplate;
    private final String lockValue = UUID.randomUUID().toString();

    @Scheduled(fixedDelayString = "${geo.content.review-poll.fixed-delay-ms:60000}")
    public void poll() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockValue, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            int processed = pollService.pollDueTasks();
            if (processed > 0) {
                log.info("polled distribution review status count={}", processed);
            }
        } catch (Exception ex) {
            log.error("distribution review status poll failed", ex);
        } finally {
            releaseLockSafely();
        }
    }

    private void releaseLockSafely() {
        try {
            String current = redisTemplate.opsForValue().get(LOCK_KEY);
            if (lockValue.equals(current)) {
                redisTemplate.delete(LOCK_KEY);
            }
        } catch (Exception ex) {
            log.warn("failed to release distribution review poll lock", ex);
        }
    }
}
