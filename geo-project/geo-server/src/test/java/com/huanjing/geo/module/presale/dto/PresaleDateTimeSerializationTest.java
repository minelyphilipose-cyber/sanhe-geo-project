package com.huanjing.geo.module.presale.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.response.ReportDetailVO;
import com.huanjing.geo.module.presale.dto.snapshot.merged.MergedViewMeta;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PresaleDateTimeSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mergedViewMeta_generatedAt_done_serializesWithOffset() throws Exception {
        MergedViewMeta meta = MergedViewMeta.builder()
                .generationStatus("DONE")
                .generatedAt(LocalDateTime.of(2026, 4, 23, 14, 0, 0))
                .build();

        String json = objectMapper.writeValueAsString(meta);

        assertThat(json).contains("\"generated_at\":\"2026-04-23T14:00:00+08:00\"");
    }

    @Test
    // 本断言依赖 Jackson 默认 NON_NULL 包含策略,字段 null 时省略输出。
    // 若未来后端启用 ALWAYS 保留 null,断言需改为 contains("\"generated_at\":null")。
    void mergedViewMeta_generatedAt_nonDone_nullAllowed() throws Exception {
        MergedViewMeta meta = MergedViewMeta.builder()
                .generationStatus("RUNNING")
                .generatedAt(null)
                .build();

        String json = objectMapper.writeValueAsString(meta);

        assertThat(json).doesNotContain("\"generated_at\"");
    }

    @Test
    void reportDetail_createdAt_serializesWithOffset() throws Exception {
        ReportDetailVO vo = ReportDetailVO.builder()
                .reportId(1L)
                .brandName("Acme")
                .industry("tech_software")
                .industryRole("service_provider")
                .region("CN")
                .createdAt(LocalDateTime.of(2026, 4, 23, 14, 0, 0))
                .build();

        String json = objectMapper.writeValueAsString(vo);

        assertThat(json).contains("\"createdAt\":\"2026-04-23T14:00:00+08:00\"");
    }
}
