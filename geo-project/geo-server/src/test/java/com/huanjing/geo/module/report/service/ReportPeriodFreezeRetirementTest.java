package com.huanjing.geo.module.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.ObjectStorageService;
import com.huanjing.geo.module.report.controller.ReportController;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ReportPeriodFreezeRetirementTest {

    @Test
    void controllerReturnsGoneForRetiredQuarterlyFreeze() {
        ReportController controller = new ReportController(mock(ReportService.class));

        assertRetired(controller::freezeQuarterly);
    }

    @Test
    void serviceCannotBeUsedToBypassRetirement() {
        ReportPeriodFreezeService service = new ReportPeriodFreezeService(
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                mock(ObjectStorageService.class),
                mock(PlatformTransactionManager.class));

        assertRetired(() -> service.freezeQuarter(1L, "2026Q2", false));
        assertRetired(() -> service.freezePreviousQuarterCandidates(100));
        assertThat(service.missingPollDetailFreezeTypes(1L, java.time.LocalDate.of(2026, 6, 1)))
                .isEmpty();
    }

    private void assertRetired(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(410);
                    assertThat(ex.getHttpStatus()).isEqualTo(410);
                    assertThat(ex.getMessage()).contains("permanently retired");
                });
    }
}
