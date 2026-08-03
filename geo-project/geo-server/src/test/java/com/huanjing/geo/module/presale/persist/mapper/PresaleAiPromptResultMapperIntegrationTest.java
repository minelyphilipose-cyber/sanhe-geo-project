package com.huanjing.geo.module.presale.persist.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.module.presale.dto.request.PresalePromptTraceQueryRequest;
import com.huanjing.geo.module.presale.generate.PromptTemplateIntentStatRow;
import com.huanjing.geo.module.content.credential.crypto.LocalMasterKeyProvider;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import com.huanjing.geo.module.presale.service.PresalePromptTraceRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.validate-on-migrate=false",
        "geo.extension.fill-token.hmac-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Transactional
class PresaleAiPromptResultMapperIntegrationTest {

    @Autowired
    private PresaleAiPromptResultMapper mapper;
    @Autowired
    private PresaleAiCallMapper callMapper;
    @Autowired
    private PresaleReportMapper reportMapper;
    @Autowired
    private PresaleReportVersionMapper versionMapper;
    @Autowired
    private PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper;
    @MockBean
    private LocalMasterKeyProvider localMasterKeyProvider;

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

    @Test
    void selectPromptTracePage_appliesAllSupportedFilters() {
        PresaleReport report = insertReport();
        PresaleReportVersion version = insertVersion(report.getId());
        PresaleReportVersionPromptTemplate recommendation = insertVersionTemplate(
                report.getId(), version.getId(), "推荐型", "UT_TRACE_REC");
        PresaleReportVersionPromptTemplate comparison = insertVersionTemplate(
                report.getId(), version.getId(), "对比型", "UT_TRACE_CMP");

        insertTrace(version.getId(), 1, "ut-platform-a", recommendation.getId(), "alpha question",
                "SUCCESS", "SUCCESS", 1);
        insertTrace(version.getId(), 2, "ut-platform-a", comparison.getId(), "beta question",
                "SUCCESS", "FAILED", null);
        insertTrace(version.getId(), 1, "ut-platform-b", recommendation.getId(), "gamma question",
                "FAILED", null, null);

        assertThat(selectTracePage(report.getId(), requestWithCategory("推荐型")).getRecords())
                .hasSize(2)
                .allMatch(row -> "推荐型".equals(row.getCategory()));
        assertThat(selectTracePage(report.getId(), requestWithBatch(2)).getRecords())
                .singleElement()
                .satisfies(row -> assertThat(row.getCategory()).isEqualTo("对比型"));
        assertThat(selectTracePage(report.getId(), requestWithPlatform("ut-platform-b")).getRecords())
                .singleElement()
                .satisfies(row -> assertThat(row.getRequestPromptContent()).isEqualTo("gamma question"));
        assertThat(selectTracePage(report.getId(), requestWithKeyword("beta")).getRecords())
                .singleElement()
                .satisfies(row -> assertThat(row.getRequestPromptContent()).isEqualTo("beta question"));
        assertThat(selectTracePage(report.getId(), requestWithStatus("SUCCESS")).getRecords())
                .singleElement()
                .satisfies(row -> assertThat(row.getRequestPromptContent()).isEqualTo("alpha question"));
        assertThat(selectTracePage(report.getId(), requestWithStatus("ANALYZE_FAILED")).getRecords())
                .singleElement()
                .satisfies(row -> assertThat(row.getRequestPromptContent()).isEqualTo("beta question"));
        assertThat(selectTracePage(report.getId(), requestWithStatus("QUERY_FAILED")).getRecords())
                .singleElement()
                .satisfies(row -> assertThat(row.getRequestPromptContent()).isEqualTo("gamma question"));
    }

    private PresaleReport insertReport() {
        PresaleReport report = new PresaleReport();
        report.setBrandName("UT Prompt Trace Brand");
        report.setIndustry("education");
        report.setIndustryRole("school");
        report.setRegion("全国");
        report.setCreatedBy(1L);
        report.setCurrentVersionNo(1);
        report.setStatus("DONE");
        reportMapper.insert(report);
        return report;
    }

    private PresaleReportVersion insertVersion(Long reportId) {
        PresaleReportVersion version = new PresaleReportVersion();
        version.setReportId(reportId);
        version.setVersionNo(1);
        version.setGenerationStatus("DONE");
        version.setIsDegraded(false);
        version.setExportSuccessCount(0);
        versionMapper.insert(version);
        return version;
    }

    private PresaleReportVersionPromptTemplate insertVersionTemplate(Long reportId,
                                                                     Long versionId,
                                                                     String category,
                                                                     String promptCode) {
        PresaleReportVersionPromptTemplate template = new PresaleReportVersionPromptTemplate();
        template.setReportId(reportId);
        template.setReportVersionId(versionId);
        template.setSourceTemplateId(System.nanoTime());
        template.setSourcePromptCode(promptCode + "_" + System.nanoTime());
        template.setSourceTemplateVersion("v3");
        template.setCategory(category);
        template.setBusinessValue("中");
        template.setPromptContent("template " + category);
        template.setHasCompetitorVar("对比型".equals(category) ? 1 : 0);
        template.setSortOrderInVersion(1);
        template.setRemark("ut");
        template.setIsUserAdded(0);
        versionPromptTemplateMapper.insert(template);
        return template;
    }

    private void insertTrace(Long versionId,
                             int batchNo,
                             String platformCode,
                             Long promptTemplateId,
                             String promptContent,
                             String queryStatus,
                             String analyzeStatus,
                             Integer isMentioned) {
        PresaleAiCall queryCall = insertCall(versionId, batchNo, platformCode, promptTemplateId,
                "QUERY", queryStatus, promptContent);
        PresaleAiCall analyzeCall = analyzeStatus == null ? null : insertCall(
                versionId, batchNo, platformCode, promptTemplateId, "ANALYZE", analyzeStatus, promptContent);

        PresaleAiPromptResult result = new PresaleAiPromptResult();
        result.setVersionId(versionId);
        result.setBatchNo(batchNo);
        result.setPlatformCode(platformCode);
        result.setPromptTemplateId(promptTemplateId);
        result.setCompetitorName("");
        result.setQueryCallId(queryCall.getId());
        result.setAnalyzeCallId(analyzeCall == null ? null : analyzeCall.getId());
        result.setRequestPromptContent(promptContent);
        result.setIsMentioned(isMentioned);
        result.setMentionedCompetitors("[]");
        result.setSceneAdvantages("[]");
        result.setTopKeywordsJson("[]");
        result.setNegativeEvidenceJson("{}");
        mapper.insert(result);
    }

    private PresaleAiCall insertCall(Long versionId,
                                     int batchNo,
                                     String platformCode,
                                     Long promptTemplateId,
                                     String stage,
                                     String status,
                                     String promptContent) {
        PresaleAiCall call = new PresaleAiCall();
        call.setVersionId(versionId);
        call.setBatchNo(batchNo);
        call.setPlatformCode(platformCode);
        call.setPlatformCodeSnapshot(platformCode);
        call.setPlatformNameSnapshot(platformCode);
        call.setModelIdSnapshot("ut-model");
        call.setModelNameSnapshot("UT Model");
        call.setPromptTemplateId(promptTemplateId);
        call.setCompetitorName("");
        call.setStage(stage);
        call.setRequestPromptContent(promptContent);
        call.setCallStatus(status);
        call.setRetryCount(0);
        call.setRawResponse("response");
        callMapper.insert(call);
        return call;
    }

    private Page<PresalePromptTraceRow> selectTracePage(Long reportId, PresalePromptTraceQueryRequest request) {
        return mapper.selectPromptTracePage(new Page<>(1, 20), reportId, 1, request);
    }

    private PresalePromptTraceQueryRequest requestWithCategory(String category) {
        PresalePromptTraceQueryRequest request = new PresalePromptTraceQueryRequest();
        request.setCategory(category);
        return request;
    }

    private PresalePromptTraceQueryRequest requestWithBatch(int batchNo) {
        PresalePromptTraceQueryRequest request = new PresalePromptTraceQueryRequest();
        request.setBatchNo(batchNo);
        return request;
    }

    private PresalePromptTraceQueryRequest requestWithPlatform(String platformCode) {
        PresalePromptTraceQueryRequest request = new PresalePromptTraceQueryRequest();
        request.setPlatformCode(platformCode);
        return request;
    }

    private PresalePromptTraceQueryRequest requestWithKeyword(String keyword) {
        PresalePromptTraceQueryRequest request = new PresalePromptTraceQueryRequest();
        request.setKeyword(keyword);
        return request;
    }

    private PresalePromptTraceQueryRequest requestWithStatus(String status) {
        PresalePromptTraceQueryRequest request = new PresalePromptTraceQueryRequest();
        request.setStatus(status);
        return request;
    }
}
