package com.huanjing.geo.module.presale.export.service;

import com.huanjing.geo.module.presale.export.config.PresaleExportProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresaleExportDebugPackageService {
    private static final DateTimeFormatter DEBUG_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final PresaleExportStorageService storageService;
    private final PresaleExportProperties properties;

    public String retainFailureDebugPackage(Long exportId, Path debugDir, String errorText) {
        if (debugDir == null) {
            return null;
        }
        try {
            Files.createDirectories(debugDir);
            Files.writeString(debugDir.resolve("error.txt"),
                    StringUtils.hasText(errorText) ? errorText : "Presale export failed",
                    StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("Write presale export debug error.txt failed, exportId={}", exportId, ex);
        }

        String prefix = "presale/exports/" + exportId + "/debug/" + DEBUG_TS.format(LocalDateTime.now()) + "/";
        try (Stream<Path> files = Files.walk(debugDir)) {
            files.filter(Files::isRegularFile)
                    .forEach(file -> uploadDebugFile(prefix, debugDir, file));
            log.info("Presale export debug package uploaded: exportId={}, debugKey={}", exportId, prefix);
            return prefix;
        } catch (Exception ex) {
            log.warn("Upload presale export debug package failed, keep local fallback: exportId={}, debugDir={}",
                    exportId, debugDir, ex);
            return "local:" + debugDir.toAbsolutePath();
        }
    }

    @Scheduled(cron = "0 20 3 * * *")
    public void cleanupLocalDebugFallbacks() {
        Path root = Path.of(properties.getStorage().getLocalRoot());
        if (!Files.isDirectory(root)) {
            return;
        }
        long cutoff = System.currentTimeMillis()
                - properties.getStorage().getDebugRetentionDays() * 24L * 60L * 60L * 1000L;
        try (Stream<Path> dirs = Files.walk(root, 2)) {
            dirs.filter(path -> path.getFileName() != null && "debug".equals(path.getFileName().toString()))
                    .filter(path -> lastModifiedMillis(path) < cutoff)
                    .forEach(this::deleteQuietly);
        } catch (Exception ex) {
            log.warn("Cleanup presale export local debug fallback failed", ex);
        }
    }

    private void uploadDebugFile(String prefix, Path debugDir, Path file) {
        try {
            String relative = debugDir.relativize(file).toString().replace('\\', '/');
            storageService.uploadDebugFile(Files.readAllBytes(file), prefix + relative, contentType(file));
        } catch (Exception ex) {
            throw new IllegalStateException("Upload debug file failed: " + file, ex);
        }
    }

    private String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        return "text/plain; charset=utf-8";
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ex) {
            return Long.MAX_VALUE;
        }
    }

    private void deleteQuietly(Path path) {
        try (Stream<Path> files = Files.walk(path)) {
            files.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (Exception ex) {
                    log.warn("Delete presale export local debug file failed: {}", item, ex);
                }
            });
        } catch (Exception ex) {
            log.warn("Delete presale export local debug dir failed: {}", path, ex);
        }
    }
}
