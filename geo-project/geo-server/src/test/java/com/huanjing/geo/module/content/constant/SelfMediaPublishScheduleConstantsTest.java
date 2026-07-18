package com.huanjing.geo.module.content.constant;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfMediaPublishScheduleConstantsTest {

    @Test
    void activeStatusesStayAlignedWithMigrationGeneratedColumns() throws Exception {
        String foundationMigration = Files.readString(Path.of(
                "src/main/resources/db/migration/V203__self_media_publish_schedule.sql"
        ));
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V311__self_media_published_url_pending_active_status.sql"
        ));

        List<Set<String>> generatedColumnStatusSets = extractGeneratedColumnStatusSets(migration);

        assertEquals(2, generatedColumnStatusSets.size(),
                "migration must define active status generated columns for schedule key and task binding");
        generatedColumnStatusSets.forEach(statuses ->
                assertEquals(SelfMediaPublishScheduleConstants.ACTIVE_STATUSES, statuses));
        assertTrue(foundationMigration.contains("UNIQUE KEY uk_self_media_schedule_active_task (active_distribution_task_id)"),
                "migration must keep distribution_task_id uniqueness scoped to active rows");
        assertFalse(Pattern.compile("UNIQUE KEY\\s+\\w+\\s*\\(\\s*distribution_task_id\\s*\\)", Pattern.CASE_INSENSITIVE)
                        .matcher(foundationMigration)
                        .find(),
                "migration must not add a full-scope unique key on distribution_task_id");
    }

    private static List<Set<String>> extractGeneratedColumnStatusSets(String migration) {
        Matcher matcher = Pattern.compile("IF\\(status IN \\((.*?)\\),\\s*(?:1|distribution_task_id),\\s*NULL\\)", Pattern.DOTALL)
                .matcher(migration);

        return matcher.results()
                .map(match -> Pattern.compile("'([^']+)'")
                        .matcher(match.group(1))
                        .results()
                        .map(status -> status.group(1))
                        .collect(Collectors.toSet()))
                .toList();
    }
}
