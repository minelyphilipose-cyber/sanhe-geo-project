package com.huanjing.geo.module.dispatch.websearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.entity.PollCitation;
import com.huanjing.geo.module.dispatch.entity.PollInvocationAttempt;
import com.huanjing.geo.module.dispatch.entity.PollSearchSource;
import com.huanjing.geo.module.dispatch.mapper.PollCitationMapper;
import com.huanjing.geo.module.dispatch.mapper.PollInvocationAttemptMapper;
import com.huanjing.geo.module.dispatch.mapper.PollSearchSourceMapper;
import com.huanjing.geo.module.dispatch.websearch.classification.PollResultClassifier;
import com.huanjing.geo.module.dispatch.websearch.enums.BrandMatchStrength;
import com.huanjing.geo.module.dispatch.websearch.enums.CitationConfidence;
import com.huanjing.geo.module.dispatch.websearch.enums.ResultCode;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchCitation;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSearchAttemptResultWriterTest {

    private final PollInvocationAttemptMapper attemptMapper = mock(PollInvocationAttemptMapper.class);
    private final PollSearchSourceMapper sourceMapper = mock(PollSearchSourceMapper.class);
    private final PollCitationMapper citationMapper = mock(PollCitationMapper.class);
    private WebSearchAttemptResultWriter writer;

    @BeforeEach
    void setUp() {
        writer = new WebSearchAttemptResultWriter(
                attemptMapper, sourceMapper, citationMapper, new PollResultClassifier(), new ObjectMapper());
        AtomicLong sourceIds = new AtomicLong(10);
        when(sourceMapper.insert(any())).thenAnswer(invocation -> {
            PollSearchSource source = invocation.getArgument(0);
            source.setId(sourceIds.incrementAndGet());
            return 1;
        });
        when(citationMapper.insert(any())).thenReturn(1);
        when(attemptMapper.updateById(any())).thenReturn(1);
    }

    @Test
    void emitsR5OnlyWhenConfirmedCitationPointsToStrongBrandSource() {
        ResultCode result = writer.writeSuccess(
                attempt(), response("测试品牌发布了新产品[1]", "测试品牌官网"),
                Set.of("测试品牌"), LocalDateTime.now());

        assertEquals(ResultCode.R5, result);
        ArgumentCaptor<PollCitation> citation = ArgumentCaptor.forClass(PollCitation.class);
        verify(citationMapper).insert(citation.capture());
        assertEquals(CitationConfidence.CONFIRMED.name(), citation.getValue().getConfidence());
        assertEquals("VALID_BRAND_STRONG", citation.getValue().getValidationStatus());
    }

    @Test
    void downgradesProviderCitationWhenSourceDoesNotStronglyMatchBrand() {
        ResultCode result = writer.writeSuccess(
                attempt(), response("测试品牌发布了新产品[1]", "行业新闻"),
                Set.of("测试品牌"), LocalDateTime.now());

        assertEquals(ResultCode.R4, result);
        ArgumentCaptor<PollCitation> citation = ArgumentCaptor.forClass(PollCitation.class);
        verify(citationMapper).insert(citation.capture());
        assertEquals(CitationConfidence.PROBABLE.name(), citation.getValue().getConfidence());
        assertEquals("SOURCE_BRAND_NOT_STRONG", citation.getValue().getValidationStatus());
    }

    private PollInvocationAttempt attempt() {
        PollInvocationAttempt attempt = new PollInvocationAttempt();
        attempt.setId(1L);
        return attempt;
    }

    private WebSearchResponse response(String answer, String sourceTitle) {
        WebSearchSource source = new WebSearchSource(
                1, 1, "问题", sourceTitle, "https://example.com/source",
                "https://example.com/source", "example.com", "摘要", null,
                BrandMatchStrength.NONE, List.of());
        WebSearchCitation citation = new WebSearchCitation(
                1, 0, answer.length() - 3, answer.length(), "[1]",
                CitationConfidence.CONFIRMED, "VALID");
        return new WebSearchResponse(
                "request", "model", "model", answer, SearchStatus.TRIGGERED, false,
                List.of(), List.of(source), List.of(citation), Map.of(), "completed");
    }
}
