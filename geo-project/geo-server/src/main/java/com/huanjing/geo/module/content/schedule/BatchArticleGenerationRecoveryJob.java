package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.service.BatchArticleGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "article.ai-draft.recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BatchArticleGenerationRecoveryJob {
    private final BatchArticleGenerationService generationService;

    @Value("${article.ai-draft.recovery.limit:20}")
    private int limit;
    @Value("${article.ai-draft.recovery.stale-minutes:15}")
    private long staleMinutes;

    @Scheduled(fixedDelayString = "${article.ai-draft.recovery.fixed-delay-ms:60000}")
    public void run() {
        try {
            int recovered = generationService.recoverStalledBatches(limit, Duration.ofMinutes(staleMinutes));
            if (recovered > 0) {
                log.info("batch article generation recovery recovered batchCount={}", recovered);
            }
        } catch (Exception ex) {
            log.warn("batch article generation recovery failed error={}", ex.getMessage(), ex);
        }
    }
}
