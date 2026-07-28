package com.huanjing.geo.module.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.dto.PollSummaryBackfillResponse;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardEntityJudgeService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PollSummaryBackfillServiceTest {

    @Test
    void purgedSliceSkipDoesNotRecomputeEntitySummaryFromRetainedSubset() throws Exception {
        PollSummaryRecomputeService pollSummary = mock(PollSummaryRecomputeService.class);
        MobileDashboardEntityJudgeService entitySummary = mock(MobileDashboardEntityJudgeService.class);
        LocalDate batchDate = LocalDate.of(2026, 6, 1);
        when(pollSummary.recomputeSlice(11L, batchDate, "A"))
                .thenReturn(new PollSummaryRecomputeService.RecomputeResult(
                        11L, batchDate, "A", true, "slice_already_purged",
                        0, 0, 0, 0, 0, 0, 0));
        PollSummaryBackfillService service = new PollSummaryBackfillService(
                mock(JdbcTemplate.class),
                pollSummary,
                entitySummary,
                mock(CurrentUserService.class),
                new ObjectMapper());
        PollSummaryBackfillResponse response = new PollSummaryBackfillResponse();

        invokeRecomputeCandidate(service, response, 11L, batchDate, "A", 20L);

        verify(entitySummary, never()).recomputeSummarySlice(11L, batchDate, "A");
        assertThat(response.getSkippedSlices()).isEqualTo(1);
        assertThat(response.getSlices()).singleElement()
                .satisfies(slice -> {
                    assertThat(slice.getSkipped()).isTrue();
                    assertThat(slice.getSkipReason()).isEqualTo("slice_already_purged");
                });
    }

    private void invokeRecomputeCandidate(PollSummaryBackfillService service,
                                          PollSummaryBackfillResponse response,
                                          Long projectId,
                                          LocalDate batchDate,
                                          String questionTier,
                                          long sourceRows) throws Exception {
        Class<?> candidateType = Class.forName(
                PollSummaryBackfillService.class.getName() + "$CandidateSlice");
        Constructor<?> constructor = candidateType.getDeclaredConstructor(
                Long.class, LocalDate.class, String.class, long.class);
        constructor.setAccessible(true);
        Object candidate = constructor.newInstance(projectId, batchDate, questionTier, sourceRows);
        Method method = PollSummaryBackfillService.class.getDeclaredMethod(
                "recomputeCandidate", PollSummaryBackfillResponse.class, candidateType);
        method.setAccessible(true);
        method.invoke(service, response, candidate);
    }
}
