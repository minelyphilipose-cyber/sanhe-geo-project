package com.huanjing.geo.module.presale.persist.mapper;

import com.huanjing.geo.module.presale.generate.PromptTemplateIntentStatRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PresaleAiPromptResultMapperIntegrationTest {

    @Autowired
    private PresaleAiPromptResultMapper mapper;

    @Test
    @Sql(scripts = "/sql/presale_prompt_template_c5_cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/presale_prompt_template_mixed_competitor_var.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/presale_prompt_template_c5_cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void selectTemplateIntentStats_includesBothCompetitorVarRows() {
        List<PromptTemplateIntentStatRow> rows = mapper.selectTemplateIntentStats("v2");
        assertThat(rows).isNotEmpty();

        List<PromptTemplateIntentStatRow> c5Rows = rows.stream()
                .filter(row -> "C5_TEST_INTENT".equals(row.getIntentLabel()))
                .toList();
        assertThat(c5Rows).hasSize(2);
        assertThat(c5Rows).anyMatch(row -> row.getHasCompetitorVar() != null
                && row.getHasCompetitorVar() == 0
                && row.getTemplateCount() == 1);
        assertThat(c5Rows).anyMatch(row -> row.getHasCompetitorVar() != null
                && row.getHasCompetitorVar() == 1
                && row.getTemplateCount() == 1);
    }
}
