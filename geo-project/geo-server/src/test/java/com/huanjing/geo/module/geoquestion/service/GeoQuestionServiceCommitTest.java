package com.huanjing.geo.module.geoquestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.LlmInvoker;
import com.huanjing.geo.common.llm.LlmProperties;
import com.huanjing.geo.module.customer.dto.CompanyKeywordGroupQuotaVO;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.service.CompanyService;
import com.huanjing.geo.module.geoquestion.dto.GeoQuestionDtos.CommitRequest;
import com.huanjing.geo.module.geoquestion.entity.GeoQuestionItem;
import com.huanjing.geo.module.geoquestion.entity.GeoQuestionVersion;
import com.huanjing.geo.module.geoquestion.entity.GeoQuestionWorkorder;
import com.huanjing.geo.module.geoquestion.mapper.GeoQuestionBatchLogMapper;
import com.huanjing.geo.module.geoquestion.mapper.GeoQuestionBatchMapper;
import com.huanjing.geo.module.geoquestion.mapper.GeoQuestionItemMapper;
import com.huanjing.geo.module.geoquestion.mapper.GeoQuestionProfileDraftMapper;
import com.huanjing.geo.module.geoquestion.mapper.GeoQuestionReplaceHistoryMapper;
import com.huanjing.geo.module.geoquestion.mapper.GeoQuestionVersionMapper;
import com.huanjing.geo.module.geoquestion.mapper.GeoQuestionWorkorderMapper;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectCustomerRequirementMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class GeoQuestionServiceCommitTest {

    @Mock
    private CompanyMapper companyMapper;
    @Mock
    private BrandMapper brandMapper;
    @Mock
    private CompanyService companyService;
    @Mock
    private AiPlatformConfigMapper aiPlatformConfigMapper;
    @Mock
    private PlatformCredentialService platformCredentialService;
    @Mock
    private LlmInvoker llmInvoker;
    @Mock
    private LlmProperties llmProperties;
    @Mock
    private GeoQuestionWorkorderMapper workorderMapper;
    @Mock
    private GeoQuestionProfileDraftMapper draftMapper;
    @Mock
    private GeoQuestionBatchMapper batchMapper;
    @Mock
    private GeoQuestionItemMapper itemMapper;
    @Mock
    private GeoQuestionReplaceHistoryMapper replaceHistoryMapper;
    @Mock
    private GeoQuestionVersionMapper versionMapper;
    @Mock
    private GeoQuestionBatchLogMapper logMapper;
    @Mock
    private KeywordGroupMapper keywordGroupMapper;
    @Mock
    private KeywordGroupResultMapper keywordGroupResultMapper;
    @Mock
    private ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ProjectCustomerRequirementMapper projectCustomerRequirementMapper;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private GeoQuestionService geoQuestionService;

    @Test
    void commit_allowsPausedProjectCommittedWorkorderToRefreshOfficialKeywordGroup() {
        Project project = new Project();
        project.setId(20L);
        project.setCompanyId(30L);
        project.setProjectName("项目A");
        project.setStatus("paused");
        project.setPlanKeywordGroupLimitA(1);
        project.setPlanKeywordGroupLimitB(1);
        project.setPlanKeywordGroupLimitC(1);

        Company company = new Company();
        company.setId(30L);
        company.setCompanyName("客户A");

        GeoQuestionWorkorder workorder = new GeoQuestionWorkorder();
        workorder.setId(10L);
        workorder.setCompanyId(30L);
        workorder.setProjectId(20L);
        workorder.setPackageName("套餐A");
        workorder.setStatus("committed");
        workorder.setLegacyKeywordGroupId(40L);
        workorder.setCommittedVersionId(50L);

        KeywordGroup group = new KeywordGroup();
        group.setId(40L);
        group.setCompanyId(30L);
        group.setProjectId(20L);
        group.setName("项目A_拓词组");
        group.setDeleted(false);

        CompanyKeywordGroupQuotaVO quota = new CompanyKeywordGroupQuotaVO();
        quota.setPackageName("套餐A");

        List<GeoQuestionItem> questions = List.of(
                question(1L, "A", "A 类问题"),
                question(2L, "B", "B 类问题"),
                question(3L, "C", "C 类问题")
        );

        CommitRequest request = new CommitRequest();
        request.setVersionLabel("v1.0");

        when(workorderMapper.selectById(10L)).thenReturn(workorder);
        when(projectMapper.selectById(20L)).thenReturn(project);
        when(companyMapper.selectById(30L)).thenReturn(company);
        when(companyService.keywordGroupQuota(30L)).thenReturn(quota);
        when(itemMapper.selectList(any())).thenReturn(questions);
        when(batchMapper.selectOne(any())).thenReturn(null);
        when(batchMapper.selectList(any())).thenReturn(List.of());
        when(keywordGroupMapper.selectById(40L)).thenReturn(group);
        when(keywordGroupResultMapper.selectList(any())).thenReturn(List.of(
                result(101L, "A"),
                result(102L, "B"),
                result(103L, "C")
        ));

        GeoQuestionVersion version = geoQuestionService.commit(10L, request);

        assertNotNull(version);
        assertEquals(40L, version.getLegacyKeywordGroupId());
        verify(keywordGroupMapper).updateById(group);
        verify(keywordGroupResultMapper, never()).delete(any());
        verify(versionMapper).insert(any(GeoQuestionVersion.class));
        verify(versionMapper).updateById(any(GeoQuestionVersion.class));
        verify(workorderMapper).updateById(workorder);

        ArgumentCaptor<KeywordGroupResult> resultCaptor = ArgumentCaptor.forClass(KeywordGroupResult.class);
        verify(keywordGroupResultMapper, org.mockito.Mockito.times(3)).updateById(resultCaptor.capture());
        assertEquals(List.of("A 类问题", "B 类问题", "C 类问题"),
                resultCaptor.getAllValues().stream().map(KeywordGroupResult::getKeywordText).toList());
        assertEquals(List.of(101L, 102L, 103L),
                resultCaptor.getAllValues().stream().map(KeywordGroupResult::getId).toList());
    }

    private GeoQuestionItem question(Long id, String tier, String text) {
        GeoQuestionItem item = new GeoQuestionItem();
        item.setId(id);
        item.setWorkorderId(10L);
        item.setBatchId(100L + id);
        item.setTier(tier);
        item.setQuestionText(text);
        item.setSceneCode("brand");
        item.setStatus("pending_review");
        item.setSortOrder(id.intValue());
        return item;
    }

    private KeywordGroupResult result(Long id, String tier) {
        KeywordGroupResult result = new KeywordGroupResult();
        result.setId(id);
        result.setGroupId(40L);
        result.setSourceWorkorderId(10L);
        result.setQuestionTier(tier);
        result.setSortOrder(id.intValue());
        return result;
    }
}
