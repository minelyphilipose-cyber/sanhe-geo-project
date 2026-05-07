package com.huanjing.geo.module.presale.persist.mapper;

import com.huanjing.geo.module.presale.generate.PlatformIntentJudgeAggregateRow;
import com.huanjing.geo.module.presale.generate.PlatformIntentSampleRow;
import com.huanjing.geo.module.content.credential.crypto.LocalMasterKeyProvider;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptJudgeResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.validate-on-migrate=false",
        "geo.extension.fill-token.hmac-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Transactional
class PresaleAiPromptResultMapperPr3aIntegrationTest {

    @Autowired
    private PresaleAiPromptResultMapper resultMapper;
    @Autowired
    private PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper;
    @Autowired
    private PresaleAiPromptJudgeResultMapper judgeResultMapper;
    @MockBean
    private LocalMasterKeyProvider localMasterKeyProvider;

    @Test
    void selectIntentSamplesByVersionId_shouldSplitBatchByCategory() {
        long versionId = 991001L;
        PresaleReportVersionPromptTemplate recTemplate = insertTemplate(versionId, "推荐型", 0, "UT_PR3A_REC_A");
        PresaleReportVersionPromptTemplate cmpTemplate = insertTemplate(versionId, "对比型", 1, "UT_PR3A_CMP_A");

        insertPromptResult(versionId, 1, "ut-platform", recTemplate.getId(), "", 1L, 1);
        insertPromptResult(versionId, 2, "ut-platform", recTemplate.getId(), "", 2L, 0);
        insertPromptResult(versionId, 2, "ut-platform", cmpTemplate.getId(), "cmp-a", 3L, 1);
        insertPromptResult(versionId, 1, "ut-platform", cmpTemplate.getId(), "cmp-b", 4L, 0);

        List<PlatformIntentSampleRow> rows = resultMapper.selectIntentSamplesByVersionId(versionId);
        assertThat(rows).hasSize(2);
        assertThat(rows).anyMatch(row -> "推荐型".equals(row.getIntentLabel()) && Integer.valueOf(1).equals(row.getIsMentioned()));
        assertThat(rows).anyMatch(row -> "对比型".equals(row.getIntentLabel()) && Integer.valueOf(1).equals(row.getIsMentioned()));
    }

    @Test
    void selectJudgeAggregatesByVersionId_shouldMatchFormulaExamples() {
        long versionId = 991002L;
        PresaleReportVersionPromptTemplate cmpTemplate = insertTemplate(versionId, "对比型", 1, "UT_PR3A_CMP_B");

        double[] cognitiveScores = {0.8, 0.5, 0.2, -0.1, 0.6, 0.3, 0.7};
        for (int i = 0; i < cognitiveScores.length; i++) {
            PresaleReportVersionPromptTemplate cogTemplate = insertTemplate(versionId, "认知型", 0, "UT_PR3A_COG_" + i);
            PresaleAiPromptResult promptResult = insertPromptResult(versionId, 1, "platform-cog", cogTemplate.getId(), "", 1000L + i, 1);
            insertJudge(promptResult, "COGNITIVE", "SUCCESS", BigDecimal.valueOf(cognitiveScores[i]), null);
        }

        for (int i = 0; i < 8; i++) {
            PresaleAiPromptResult promptResult = insertPromptResult(versionId, 2, "platform-cmp", cmpTemplate.getId(), "target-" + i, 2000L + i, 1);
            insertJudge(promptResult, "COMPARISON", "SUCCESS", null, "target");
        }
        for (int i = 0; i < 5; i++) {
            PresaleAiPromptResult promptResult = insertPromptResult(versionId, 2, "platform-cmp", cmpTemplate.getId(), "competitor-" + i, 3000L + i, 1);
            insertJudge(promptResult, "COMPARISON", "SUCCESS", null, "competitor");
        }
        for (int i = 0; i < 4; i++) {
            PresaleAiPromptResult promptResult = insertPromptResult(versionId, 2, "platform-cmp", cmpTemplate.getId(), "tie-" + i, 4000L + i, 1);
            insertJudge(promptResult, "COMPARISON", "SUCCESS", null, "tie");
        }
        for (int i = 0; i < 4; i++) {
            PresaleAiPromptResult promptResult = insertPromptResult(versionId, 2, "platform-cmp", cmpTemplate.getId(), "unclear-" + i, 5000L + i, 1);
            insertJudge(promptResult, "COMPARISON", "SUCCESS", null, "unclear");
        }

        for (int i = 0; i < 21; i++) {
            PresaleAiPromptResult promptResult = insertPromptResult(versionId, 2, "platform-unclear", cmpTemplate.getId(), "u-" + i, 6000L + i, 1);
            insertJudge(promptResult, "COMPARISON", "SUCCESS", null, "unclear");
        }

        List<PlatformIntentJudgeAggregateRow> rows = resultMapper.selectJudgeAggregatesByVersionId(versionId);
        assertThat(rows).isNotEmpty();

        PlatformIntentJudgeAggregateRow cognitive = findRow(rows, "platform-cog", "COGNITIVE");
        assertThat(cognitive.getCellScore()).isEqualByComparingTo("71.43");
        assertThat(cognitive.getSampleCount()).isEqualTo(7);
        assertThat(cognitive.getStance()).isNull();

        PlatformIntentJudgeAggregateRow comparison = findRow(rows, "platform-cmp", "COMPARISON");
        assertThat(comparison.getCellScore()).isEqualByComparingTo("58.82");
        assertThat(comparison.getSampleCount()).isEqualTo(17);
        assertThat(comparison.getStance()).isEqualTo("target");

        PlatformIntentJudgeAggregateRow fullUnclear = findRow(rows, "platform-unclear", "COMPARISON");
        assertThat(fullUnclear.getCellScore()).isNull();
        assertThat(fullUnclear.getSampleCount()).isEqualTo(0);
        assertThat(fullUnclear.getStance()).isNull();
    }

    private PresaleReportVersionPromptTemplate insertTemplate(long versionId,
                                                              String category,
                                                              int hasCompetitorVar,
                                                              String codePrefix) {
        PresaleReportVersionPromptTemplate template = new PresaleReportVersionPromptTemplate();
        template.setReportId(versionId + 100000L);
        template.setReportVersionId(versionId);
        template.setSourceTemplateId(System.nanoTime());
        template.setSourcePromptCode(codePrefix + "_" + System.nanoTime());
        template.setSourceTemplateVersion("v3");
        template.setCategory(category);
        template.setBusinessValue("中");
        template.setPromptContent("UT template " + category);
        template.setHasCompetitorVar(hasCompetitorVar);
        template.setSortOrderInVersion(9000 + hasCompetitorVar);
        template.setRemark("ut");
        template.setIsUserAdded(0);
        versionPromptTemplateMapper.insert(template);
        return template;
    }

    private PresaleAiPromptResult insertPromptResult(long versionId,
                                                     int batchNo,
                                                     String platformCode,
                                                     long templateId,
                                                     String competitorName,
                                                     long queryCallId,
                                                     Integer isMentioned) {
        PresaleAiPromptResult row = new PresaleAiPromptResult();
        row.setVersionId(versionId);
        row.setBatchNo(batchNo);
        row.setPlatformCode(platformCode);
        row.setPromptTemplateId(templateId);
        row.setCompetitorName(competitorName);
        row.setQueryCallId(queryCallId);
        row.setAnalyzeCallId(null);
        row.setRequestPromptContent("ut prompt");
        row.setIsMentioned(isMentioned);
        row.setRanking(null);
        row.setSentiment("NEUTRAL");
        row.setMentionedCompetitors("[]");
        row.setSceneAdvantages("[]");
        row.setTopKeywordsJson("[]");
        row.setNegativeEvidenceJson("{}");
        resultMapper.insert(row);
        return row;
    }

    private void insertJudge(PresaleAiPromptResult promptResult,
                             String category,
                             String judgeStatus,
                             BigDecimal sentimentScore,
                             String preferredBrand) {
        PresaleAiPromptJudgeResult judge = new PresaleAiPromptJudgeResult();
        judge.setPromptResultId(promptResult.getId());
        judge.setVersionId(promptResult.getVersionId());
        judge.setBatchNo(promptResult.getBatchNo());
        judge.setPlatformCode(promptResult.getPlatformCode());
        judge.setPromptTemplateId(promptResult.getPromptTemplateId());
        judge.setCategory(category);
        judge.setCompetitorName(promptResult.getCompetitorName() == null ? "" : promptResult.getCompetitorName());
        judge.setJudgeStatus(judgeStatus);
        judge.setJudgeAttemptCount(1);
        judge.setJudgeModelId("ut-model");
        judge.setJudgeTemperature(BigDecimal.ZERO);
        judge.setJudgeError(null);
        judge.setSentiment(sentimentScore == null ? null : "NEUTRAL");
        judge.setSentimentScore(sentimentScore);
        judge.setAttributeHitRate(sentimentScore == null ? null : BigDecimal.ONE);
        judge.setTone(null);
        judge.setPreferredBrand(preferredBrand);
        judge.setTargetSentiment(null);
        judge.setReasoningQuality(null);
        judge.setAttributesHit("[]");
        judge.setFactualErrors("[]");
        judge.setTargetAdvantages("[]");
        judge.setTargetDisadvantages("[]");
        judge.setCompetitorAdvantages("[]");
        judge.setJudgePayloadJson("{}");
        judge.setRawJudgeResponse("{}");
        judgeResultMapper.insert(judge);
    }

    private PlatformIntentJudgeAggregateRow findRow(List<PlatformIntentJudgeAggregateRow> rows, String platformCode, String category) {
        Optional<PlatformIntentJudgeAggregateRow> found = rows.stream()
                .filter(row -> platformCode.equals(row.getPlatformCode()) && category.equals(row.getCategory()))
                .findFirst();
        assertThat(found).isPresent();
        return found.get();
    }
}
