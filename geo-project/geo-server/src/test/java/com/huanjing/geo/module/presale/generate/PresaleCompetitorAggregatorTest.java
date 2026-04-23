package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresaleCompetitorAggregatorTest {

    @Mock
    private PresaleAiPromptResultMapper aiPromptResultMapper;

    @Test
    void extractTop3AndFilterBrand() {
        PresaleCompetitorAggregator aggregator = new PresaleCompetitorAggregator(
                aiPromptResultMapper, new ObjectMapper());
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                promptResult(1L, "[\"ChatGPT\", \"Claude\", \"Acme AI\"]"),
                promptResult(2L, "[\"claude\", \"Gemini\"]"),
                promptResult(3L, "[\"  CHAT GPT  \", \"Gemini\", \"Doubao\"]"),
                promptResult(4L, "[\"Doubao\", \"Claude\"]"),
                promptResult(5L, "[\"AcmeAI\", \"Acme AI\"]")
        ));

        List<String> competitors = aggregator.extractTopCompetitorsFromBatch1(9201L, "Acme  AI");

        assertEquals(List.of("Claude", "ChatGPT", "Doubao"), competitors);
    }

    @Test
    void lessThan3Candidates_returnsActualSize() {
        PresaleCompetitorAggregator aggregator = new PresaleCompetitorAggregator(
                aiPromptResultMapper, new ObjectMapper());
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                promptResult(11L, "[\"Claude\"]"),
                promptResult(12L, "[\"ChatGPT\"]"),
                promptResult(13L, "[\"Claude\"]")
        ));

        List<String> competitors = aggregator.extractTopCompetitorsFromBatch1(9301L, "Acme");

        assertEquals(List.of("Claude", "ChatGPT"), competitors);
    }

    @Test
    void invalidJsonSkipped_withoutBreakingOtherRows() {
        PresaleCompetitorAggregator aggregator = new PresaleCompetitorAggregator(
                aiPromptResultMapper, new ObjectMapper());
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                promptResult(21L, "{broken json"),
                promptResult(22L, "[\"Claude\", \"Gemini\"]"),
                promptResult(23L, "[1,2,3]")
        ));

        List<String> competitors = aggregator.extractTopCompetitorsFromBatch1(9302L, "Acme");

        assertEquals(List.of("Claude", "Gemini"), competitors);
    }

    @Test
    void rowLevelDedup_countsSameNameOncePerRow() {
        PresaleCompetitorAggregator aggregator = new PresaleCompetitorAggregator(
                aiPromptResultMapper, new ObjectMapper());
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                promptResult(31L, "[\"Claude\", \"claude\", \"CLAUDE\"]"),
                promptResult(32L, "[\"Claude\"]"),
                promptResult(33L, "[\"Gemini\"]")
        ));

        List<String> competitors = aggregator.extractTopCompetitorsFromBatch1(9303L, "Acme");

        assertEquals(List.of("Claude", "Gemini"), competitors);
    }

    private PresaleAiPromptResult promptResult(Long id, String mentionedCompetitorsJson) {
        PresaleAiPromptResult row = new PresaleAiPromptResult();
        row.setId(id);
        row.setMentionedCompetitors(mentionedCompetitorsJson);
        row.setIsMentioned(1);
        return row;
    }
}
