package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.AccountAuthRiskScanBatch;
import com.huanjing.geo.module.content.mapper.AccountAuthRiskScanBatchMapper;
import com.huanjing.geo.module.extension.service.ExtensionRedisStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AccountAuthHealthScanCoordinator {
    private static final String LOCK_KEY = "geo:account-auth-health:manual-scan";

    private final SelfMediaAccountHealthAlertService selfMediaHealthAlertService;
    private final ForumCookieHealthAlertService forumCookieHealthAlertService;
    private final AccountAuthRiskScanBatchMapper scanBatchMapper;
    private final ExtensionRedisStore redisStore;
    private final Executor taskExecutor;
    private final TaskScheduler taskScheduler;

    public AccountAuthHealthScanCoordinator(SelfMediaAccountHealthAlertService selfMediaHealthAlertService,
                                            ForumCookieHealthAlertService forumCookieHealthAlertService,
                                            AccountAuthRiskScanBatchMapper scanBatchMapper,
                                            ExtensionRedisStore redisStore,
                                            @Qualifier("taskExecutor") Executor taskExecutor,
                                            @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
        this.selfMediaHealthAlertService = selfMediaHealthAlertService;
        this.forumCookieHealthAlertService = forumCookieHealthAlertService;
        this.scanBatchMapper = scanBatchMapper;
        this.redisStore = redisStore;
        this.taskExecutor = taskExecutor;
        this.taskScheduler = taskScheduler;
    }

    @Value("${geo.self-media-account-health.scan-lock-minutes:30}")
    private int scanLockMinutes;

    @Value("${geo.self-media-account-health.stale-running-minutes:60}")
    private int staleRunningMinutes;

    public ScanLaunchResult launch() {
        recoverStaleBatches();
        LockLease lease = tryAcquireLease("manual");
        if (lease == null) {
            throw new BizException(409, "账号授权风险扫描正在运行，请勿重复启动");
        }
        try {
            taskExecutor.execute(() -> {
                try (lease) {
                    lease.ensureHeld();
                    selfMediaHealthAlertService.scanOnce();
                    lease.ensureHeld();
                    forumCookieHealthAlertService.scanOnce();
                }
            });
        } catch (RuntimeException ex) {
            lease.close();
            throw new BizException(503, "扫描任务提交失败，请稍后重试");
        }
        return new ScanLaunchResult("accepted", LocalDateTime.now(), "扫描已在后台启动，可继续处理其他工作");
    }

    public boolean runScheduled(String scanType) {
        recoverStaleBatches();
        LockLease lease = tryAcquireLease("scheduled:" + scanType);
        if (lease == null) {
            return false;
        }
        try (lease) {
            lease.ensureHeld();
            if ("forum".equals(scanType)) {
                forumCookieHealthAlertService.scanOnce();
            } else {
                selfMediaHealthAlertService.scanOnce();
            }
            return true;
        }
    }

    private LockLease tryAcquireLease(String source) {
        String owner = source + ":" + UUID.randomUUID();
        Duration ttl = Duration.ofMinutes(Math.max(scanLockMinutes, 1));
        if (!redisStore.tryLock(LOCK_KEY, owner, ttl)) return null;
        Duration renewInterval = Duration.ofMillis(Math.max(10_000L, ttl.toMillis() / 3L));
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicBoolean valid = new AtomicBoolean(true);
        try {
            ScheduledFuture<?> renewal = taskScheduler.scheduleAtFixedRate(
                    () -> renewLease(owner, ttl, active, valid),
                    Instant.now().plus(renewInterval), renewInterval);
            return new LockLease(owner, ttl, active, valid, renewal);
        } catch (RuntimeException ex) {
            redisStore.releaseLock(LOCK_KEY, owner);
            throw ex;
        }
    }

    private void renewLease(String owner, Duration ttl, AtomicBoolean active, AtomicBoolean valid) {
        if (!active.get()) return;
        try {
            if (!redisStore.renewLock(LOCK_KEY, owner, ttl)) valid.set(false);
        } catch (RuntimeException ex) {
            valid.set(false);
        }
    }

    private final class LockLease implements AutoCloseable {
        private final String owner;
        private final Duration ttl;
        private final AtomicBoolean active;
        private final AtomicBoolean valid;
        private final ScheduledFuture<?> renewal;

        private LockLease(String owner, Duration ttl, AtomicBoolean active, AtomicBoolean valid,
                          ScheduledFuture<?> renewal) {
            this.owner = owner;
            this.ttl = ttl;
            this.active = active;
            this.valid = valid;
            this.renewal = renewal;
        }

        private void ensureHeld() {
            if (!active.get() || !valid.get() || !redisStore.renewLock(LOCK_KEY, owner, ttl)) {
                valid.set(false);
                throw new IllegalStateException("账号授权风险扫描锁已丢失，已停止后续扫描阶段");
            }
        }

        @Override
        public void close() {
            if (!active.compareAndSet(true, false)) return;
            if (renewal != null) renewal.cancel(false);
            redisStore.releaseLock(LOCK_KEY, owner);
        }
    }

    public int recoverStaleBatches() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(staleRunningMinutes, 1));
        List<AccountAuthRiskScanBatch> rows = scanBatchMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AccountAuthRiskScanBatch>()
                        .eq(AccountAuthRiskScanBatch::getStatus, "running")
                        .lt(AccountAuthRiskScanBatch::getStartedAt, cutoff));
        for (AccountAuthRiskScanBatch row : rows) {
            row.setStatus("failed");
            row.setFinishedAt(LocalDateTime.now());
            row.setErrorSummary("扫描进程异常中断，系统已自动结束；可重新发起扫描");
            row.setUpdatedAt(LocalDateTime.now());
            scanBatchMapper.updateById(row);
        }
        return rows.size();
    }

    public record ScanLaunchResult(String status, LocalDateTime acceptedAt, String message) {
    }
}
