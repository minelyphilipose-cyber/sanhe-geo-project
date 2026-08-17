package com.huanjing.geo.module.retention.detailcleanup;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DataRetentionDetailCleanupArchitectureTest {
    private static final Path MAIN=Path.of("src/main/java");

    @Test void onlyTypedWp8RepositoryDeletesMutationDetail() throws Exception {
        try(Stream<Path> files=Files.walk(MAIN)) {
            List<Path> writers=files.filter(path->path.toString().endsWith(".java"))
                    .filter(path->{try {String source=Files.readString(path).toLowerCase();return source.contains("delete from data_retention_batch_attempt")||source.contains("delete from data_retention_batch_item");}catch(Exception e){throw new RuntimeException(e);}})
                    .toList();
            assertThat(writers).containsExactly(MAIN.resolve("com/huanjing/geo/module/retention/detailcleanup/DataRetentionDetailCleanupRepository.java"));
        }
    }

    @Test void enableCreateApprovalAndCanaryAllCallWp8Gate() throws Exception {
        String management=Files.readString(MAIN.resolve("com/huanjing/geo/module/retention/dynamicswitch/DataRetentionDynamicSwitchManagementService.java"));
        String coordinator=Files.readString(MAIN.resolve("com/huanjing/geo/module/retention/execute/DataRetentionExecuteCoordinator.java"));
        assertThat(occurrences(management,"requireDetailCleanupReady();")).isGreaterThanOrEqualTo(3);
        assertThat(coordinator).contains("command.source() == ExecutionSource.CANARY", "requireDetailCleanupReady();");
    }

    @Test void migrationNumberIsUniqueAndForwardOnly() throws Exception {
        Path migrations=Path.of("src/main/resources/db/migration");
        try(Stream<Path> files=Files.list(migrations)) {
            assertThat(files.filter(path->path.getFileName().toString().startsWith("V368__")).count()).isEqualTo(1);
        }
        String migration=Files.readString(migrations.resolve("V368__data_retention_detail_cleanup_readiness.sql")).toUpperCase();
        assertThat(migration).doesNotContain("DROP TABLE", "DROP COLUMN");
    }

    private static int occurrences(String value,String needle){int count=0,index=0;while((index=value.indexOf(needle,index))>=0){count++;index+=needle.length();}return count;}
}
