package com.huanjing.geo.module.presale.export.service;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ChromiumMemorySampler {
    private final ProcessHandle root;
    private final ScheduledExecutorService executor;
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
        executor.shutdownNow();
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
                    .mapToLong(this::residentBytes)
                    .sum();
            sampleCount++;
            peakMb = Math.max(peakMb, totalBytes / 1024 / 1024);
        } catch (Exception ex) {
            log.debug("Sample Chromium memory failed", ex);
        }
    }

    private boolean isChromiumProcess(ProcessHandle handle) {
        String command = handle.info().command().orElse("").toLowerCase(Locale.ROOT);
        String commandLine = handle.info().commandLine().orElse("").toLowerCase(Locale.ROOT);
        return command.contains("chrome")
                || command.contains("chromium")
                || commandLine.contains("chrome")
                || commandLine.contains("chromium")
                || commandLine.contains("ms-playwright");
    }

    private long residentBytes(ProcessHandle handle) {
        if (isWindows()) {
            return windowsWorkingSetBytes(handle.pid());
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

    private long windowsWorkingSetBytes(long pid) {
        Process process = null;
        try {
            process = new ProcessBuilder("tasklist", "/FI", "PID eq " + pid, "/FO", "CSV", "/NH")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line == null || line.startsWith("INFO:")) {
                    return 0L;
                }
                String[] columns = line.split("\",\"");
                if (columns.length < 5) {
                    return 0L;
                }
                String mem = columns[4].replace("\"", "").replaceAll("[^0-9]", "");
                return mem.isEmpty() ? 0L : Long.parseLong(mem) * 1024L;
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
