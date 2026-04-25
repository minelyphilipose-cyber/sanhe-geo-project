package com.huanjing.geo.module.presale.export.service;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ChromiumMemorySampler {
    private final ProcessHandle root;
    private final ScheduledExecutorService executor;
    private final Set<Long> loggedPids = new HashSet<>();
    private volatile long peakMb;
    private volatile long sampleCount;

    public ChromiumMemorySampler(ProcessHandle root) {
        this.root = root;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "presale-export-memory-sampler");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        executor.scheduleAtFixedRate(this::sampleQuietly, 0, 500, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public long getPeakMb() {
        return peakMb;
    }

    public long getSampleCount() {
        return sampleCount;
    }

    private void sampleQuietly() {
        try {
            long totalBytes = root.descendants()
                    .filter(this::isChromiumProcess)
                    .mapToLong(this::sampleProcessBytes)
                    .sum();
            sampleCount++;
            peakMb = Math.max(peakMb, totalBytes / 1024 / 1024);
        } catch (Exception ex) {
            log.debug("Sample Chromium memory failed", ex);
        }
    }

    private boolean isChromiumProcess(ProcessHandle handle) {
        String command = handle.info().command().orElse("").toLowerCase(Locale.ROOT);
        String normalized = command.replace('\\', '/');
        return normalized.endsWith("/chrome.exe")
                || normalized.endsWith("/chrome")
                || normalized.contains("/chromium")
                || normalized.contains("chromium");
    }

    private long sampleProcessBytes(ProcessHandle handle) {
        long bytes = residentBytes(handle);
        if (loggedPids.add(handle.pid())) {
            log.info("Sampled chromium process: pid={}, command={}, bytes={}",
                    handle.pid(), handle.info().command().orElse(""), bytes);
        }
        return bytes;
    }

    private long residentBytes(ProcessHandle handle) {
        if (isWindows()) {
            return windowsPrivateBytes(handle.pid());
        }
        return linuxResidentBytes(handle.pid());
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private long linuxResidentBytes(long pid) {
        Path status = Path.of("/proc", String.valueOf(pid), "status");
        if (!Files.isRegularFile(status)) {
            return 0L;
        }
        try {
            for (String line : Files.readAllLines(status)) {
                if (line.startsWith("VmRSS:")) {
                    String value = line.replaceAll("[^0-9]", "");
                    return value.isEmpty() ? 0L : Long.parseLong(value) * 1024L;
                }
            }
        } catch (Exception ignored) {
            return 0L;
        }
        return 0L;
    }

    private long windowsPrivateBytes(long pid) {
        Process process = null;
        try {
            process = new ProcessBuilder("wmic", "process", "where", "ProcessId=" + pid,
                    "get", "PrivatePageCount", "/value")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("PrivatePageCount=")) {
                        String value = line.substring("PrivatePageCount=".length()).trim();
                        return value.isEmpty() ? 0L : Long.parseLong(value);
                    }
                }
                return 0L;
            }
        } catch (Exception ignored) {
            return 0L;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
