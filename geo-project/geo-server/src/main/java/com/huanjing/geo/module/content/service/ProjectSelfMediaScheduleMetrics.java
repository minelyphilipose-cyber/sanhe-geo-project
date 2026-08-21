package com.huanjing.geo.module.content.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ProjectSelfMediaScheduleMetrics {
    private final MeterRegistry registry;
    private final AtomicLong pendingItems = new AtomicLong();
    private final AtomicLong overdueItems = new AtomicLong();
    private final AtomicLong reservedQuota = new AtomicLong();

    public ProjectSelfMediaScheduleMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("project_self_media_schedule_pending_items", pendingItems, AtomicLong::get).register(registry);
        Gauge.builder("project_self_media_schedule_overdue_items", overdueItems, AtomicLong::get).register(registry);
        Gauge.builder("project_self_media_schedule_reserved_quota", reservedQuota, AtomicLong::get).register(registry);
    }

    public void backlog(long pending, long overdue, long reserved) {
        pendingItems.set(Math.max(0, pending));
        overdueItems.set(Math.max(0, overdue));
        reservedQuota.set(Math.max(0, reserved));
    }

    public void generation(String result) {
        registry.counter("project_self_media_schedule_generation_total", "result", result).increment();
    }

    public void generationLatency(Duration duration) {
        if (duration == null || duration.isNegative()) return;
        Timer.builder("project_self_media_schedule_generation_latency")
                .publishPercentileHistogram().register(registry).record(duration);
    }

    public void generationRetry() {
        registry.counter("project_self_media_schedule_generation_retry_total").increment();
    }

    public void schedule(String result) {
        registry.counter("project_self_media_schedule_creation_total", "result", result).increment();
    }

    public void scheduleRetry() {
        registry.counter("project_self_media_schedule_creation_retry_total").increment();
    }

    public void templateSelected(Long templateId) {
        registry.counter("project_self_media_schedule_template_selected_total",
                "template_id", templateId == null ? "none" : String.valueOf(templateId)).increment();
    }

    public void quotaReconciled(String result) {
        registry.counter("project_self_media_schedule_quota_reconcile_total", "result", result).increment();
    }
}
