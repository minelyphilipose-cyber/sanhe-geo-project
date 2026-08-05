package com.huanjing.geo.module.presale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.access.PresaleAccessService;
import com.huanjing.geo.module.presale.dto.response.PresalePromptTraceDetailVO;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresalePromptTraceServiceTest {

    @Mock
    private PresaleReportVersionMapper versionMapper;
    @Mock
    private PresaleAiPromptResultMapper promptResultMapper;
    @Mock
    private PresaleAccessService accessService;
    @Mock
    private CurrentUserService currentUserService;

    private PresalePromptTraceService service;

    @BeforeEach
    void setUp() {
        service = new PresalePromptTraceService(
                versionMapper,
                promptResultMapper,
                accessService,
                currentUserService,
                new ObjectMapper()
        );
        PresaleReport report = new PresaleReport();
        report.setId(10L);
        PresaleReportVersion version = new PresaleReportVersion();
        version.setId(20L);
        version.setReportId(10L);
        version.setVersionNo(1);
        lenient().when(accessService.requireReportWithAccess(10L)).thenReturn(report);
        lenient().when(versionMapper.selectOne(any())).thenReturn(version);
    }

    @Test
    void detailExposesOnlySafeProductEvidenceFields() {
        PresalePromptTraceRow row = baseRow();
        row.setQueryContractVersion("WEB_SEARCH_V1");
        row.setSearchEvidenceJson("""
                {
                  "queryContractVersion":"WEB_SEARCH_V1",
                  "searchTriggered":true,
                  "searchStatus":"SUCCEEDED",
                  "evidenceLevel":"CITATIONS",
                  "providerRequestIds":["secret-provider-request-id"],
                  "searchEvidence":[{"query":"新能源汽车市场份额"}],
                  "sources":[
                    {"title":"可信来源","normalizedUrl":"https://news.example.com/a","domain":"spoofed.example.net","snippet":"摘要"},
                    {"title":"危险来源","normalizedUrl":"javascript:alert(1)"}
                  ],
                  "citations":[{"citationIndex":0,"citationText":"回答引用片段","confidence":"HIGH"}]
                }
                """);
        when(promptResultMapper.selectPromptTraceDetail(10L, 1, 30L)).thenReturn(row);

        PresalePromptTraceDetailVO detail = service.detail(10L, 1, 30L);

        assertTrue(detail.getEvidence().getWebSearch());
        assertTrue(detail.getEvidence().getSearchTriggered());
        assertEquals(1, detail.getEvidence().getSources().size());
        assertEquals("news.example.com", detail.getEvidence().getSources().get(0).getDomain());
        assertEquals("https://news.example.com/a", detail.getEvidence().getSources().get(0).getUrl());
        assertEquals("新能源汽车市场份额", detail.getEvidence().getSearchQueries().get(0));
        assertEquals("回答引用片段", detail.getEvidence().getCitations().get(0).getText());
    }

    @Test
    void detailKeepsLegacyQueryOutOfWebSearchSemantics() {
        PresalePromptTraceRow row = baseRow();
        row.setQueryContractVersion("LEGACY_QUERY_V1");
        row.setSearchEvidenceJson("{\"searchTriggered\":true,\"sources\":[]}");
        when(promptResultMapper.selectPromptTraceDetail(10L, 1, 30L)).thenReturn(row);

        PresalePromptTraceDetailVO detail = service.detail(10L, 1, 30L);

        assertFalse(detail.getEvidence().getWebSearch());
        assertEquals("NOT_APPLICABLE", detail.getEvidence().getSearchStatus());
        assertTrue(detail.getEvidence().getSources().isEmpty());
    }

    @Test
    void promptTraceSelectLoadsEvidenceColumns() {
        assertTrue(PresaleAiPromptResultMapper.PROMPT_TRACE_SELECT.contains("qc.query_contract_version AS queryContractVersion"));
        assertTrue(PresaleAiPromptResultMapper.PROMPT_TRACE_SELECT.contains("qc.search_evidence_json AS searchEvidenceJson"));
    }

    private PresalePromptTraceRow baseRow() {
        PresalePromptTraceRow row = new PresalePromptTraceRow();
        row.setPromptResultId(30L);
        row.setReportId(10L);
        row.setVersionId(20L);
        row.setVersionNo(1);
        row.setBatchNo(1);
        row.setPlatformCode("doubao");
        row.setPlatformName("豆包");
        row.setQueryCallStatus("SUCCESS");
        row.setAnalyzeCallStatus("SUCCESS");
        row.setIsMentioned(1);
        return row;
    }
}
