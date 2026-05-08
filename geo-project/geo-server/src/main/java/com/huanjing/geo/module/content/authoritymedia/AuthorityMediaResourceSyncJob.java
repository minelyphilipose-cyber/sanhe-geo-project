package com.huanjing.geo.module.content.authoritymedia;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorityMediaResourceSyncJob {

    private final MeititejiaProperties properties;
    private final AuthorityMediaResourceSyncService syncService;

    @Scheduled(cron = "${geo.meititejia.news-media-incremental-cron:0 0 * * * *}", zone = "${geo.dispatch.timezone:Asia/Shanghai}")
    public void syncNewsMediaIncremental() {
        if (!syncEnabled()) {
            return;
        }
        try {
            AuthorityMediaResourceSyncService.SyncResult result = syncService.syncNewsMediaIncremental();
            log.info("Meititejia NEWS_MEDIA incremental sync finished: {}", result);
        } catch (Exception ex) {
            log.error("Meititejia NEWS_MEDIA incremental sync failed", ex);
        }
    }

    @Scheduled(cron = "${geo.meititejia.news-media-full-cron:0 30 2 * * *}", zone = "${geo.dispatch.timezone:Asia/Shanghai}")
    public void syncNewsMediaFull() {
        if (!syncEnabled()) {
            return;
        }
        try {
            AuthorityMediaResourceSyncService.SyncResult result = syncService.syncNewsMediaFull();
            log.info("Meititejia NEWS_MEDIA full sync finished: {}", result);
        } catch (Exception ex) {
            log.error("Meititejia NEWS_MEDIA full sync failed", ex);
        }
    }

    @Scheduled(cron = "${geo.meititejia.news-media-reconcile-cron:0 30 3 * * *}", zone = "${geo.dispatch.timezone:Asia/Shanghai}")
    public void reconcileNewsMediaIds() {
        if (!syncEnabled()) {
            return;
        }
        try {
            AuthorityMediaResourceSyncService.ReconcileResult result = syncService.reconcileNewsMediaIds();
            log.info("Meititejia NEWS_MEDIA id reconciliation finished: {}", result);
        } catch (Exception ex) {
            log.error("Meititejia NEWS_MEDIA id reconciliation failed", ex);
        }
    }

    private boolean syncEnabled() {
        return properties.isEnabled() && properties.isSyncEnabled();
    }
}
