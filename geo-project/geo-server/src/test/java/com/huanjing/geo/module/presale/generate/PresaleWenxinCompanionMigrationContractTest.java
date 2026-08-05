package com.huanjing.geo.module.presale.generate;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresaleWenxinCompanionMigrationContractTest {

    @Test
    void migrationAlignsWenxinWebWithErnieLogicalChannel() throws Exception {
        String path = "db/migration/V345__align_wenxin_web_companion_channel.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, "missing migration " + path);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("base.platform_code = 'ernie'"));
            assertTrue(sql.contains("companion.platform_code = 'wenxin_web'"));
            assertTrue(sql.contains("companion.channel_code = base.channel_code"));
        }
    }
}
