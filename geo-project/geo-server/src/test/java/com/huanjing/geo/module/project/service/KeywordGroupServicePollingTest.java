package com.huanjing.geo.module.project.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.mapper.CompanyPackageBindingMapper;
import com.huanjing.geo.module.project.dto.KeywordGroupQuestionPollingUpdateRequest;
import com.huanjing.geo.module.project.dto.KeywordGroupQuestionVO;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupWordMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeywordGroupServicePollingTest {
    @Mock private KeywordGroupMapper keywordGroupMapper;
    @Mock private KeywordGroupResultMapper keywordGroupResultMapper;
    @Mock private KeywordGroupWordMapper keywordGroupWordMapper;
    @Mock private CompanyMapper companyMapper;
    @Mock private CompanyPackageBindingMapper companyPackageBindingMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper;
    @Mock private CurrentUserService currentUserService;
    @Mock private InternalScopeService internalScopeService;
    @Mock private KeywordTypeConfigService keywordTypeConfigService;
    @Mock private KeywordLlmQuestionService keywordLlmQuestionService;
    @Mock private ProjectStateGuard projectStateGuard;

    @InjectMocks private KeywordGroupService service;

    @Test
    void activeProjectAllowsInternalOperatorToDisableTierAQuestion() {
        SysUser operator = user("operator");
        Project project = project("active");
        KeywordGroup group = group();
        Company company = new Company();
        company.setId(30L);
        KeywordGroupResult question = question("A");
        KeywordGroupQuestionPollingUpdateRequest request = request(false);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(projectMapper.selectById(20L)).thenReturn(project);
        when(keywordGroupMapper.selectById(10L)).thenReturn(group);
        when(companyMapper.selectById(30L)).thenReturn(company);
        when(projectKeywordGroupRelMapper.selectCount(any())).thenReturn(1L);
        when(keywordGroupResultMapper.selectById(100L)).thenReturn(question);

        KeywordGroupQuestionVO result = service.updateQuestionPolling(20L, 10L, 100L, request);

        assertFalse(result.getPollingEnabled());
        verify(currentUserService).ensurePermission("keyword_group.write");
        verify(internalScopeService).ensureProjectAccess(operator, project, "project");
        verify(internalScopeService).ensureCompanyAccess(operator, company, "keyword group");
        verify(keywordGroupResultMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    void partnerCannotUpdatePollingEvenIfQuestionIsVisible() {
        SysUser partner = user("partner");
        when(currentUserService.requireCurrentUser()).thenReturn(partner);
        when(currentUserService.isPartnerUser(partner)).thenReturn(true);

        BizException error = assertThrows(BizException.class,
                () -> service.updateQuestionPolling(20L, 10L, 100L, request(false)));

        assertEquals(403, error.getCode());
        verify(keywordGroupResultMapper, never()).update(any(), any());
    }

    @Test
    void tierBQuestionIsRejected() {
        SysUser operator = user("operator");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(projectMapper.selectById(20L)).thenReturn(project("paused"));
        when(keywordGroupMapper.selectById(10L)).thenReturn(group());
        when(companyMapper.selectById(30L)).thenReturn(new Company());
        when(projectKeywordGroupRelMapper.selectCount(any())).thenReturn(1L);
        when(keywordGroupResultMapper.selectById(100L)).thenReturn(question("B"));

        BizException error = assertThrows(BizException.class,
                () -> service.updateQuestionPolling(20L, 10L, 100L, request(false)));

        assertEquals(400, error.getCode());
        verify(keywordGroupResultMapper, never()).update(any(), any());
    }

    private SysUser user(String role) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setRole(role);
        return user;
    }

    private Project project(String status) {
        Project project = new Project();
        project.setId(20L);
        project.setCompanyId(30L);
        project.setStatus(status);
        return project;
    }

    private KeywordGroup group() {
        KeywordGroup group = new KeywordGroup();
        group.setId(10L);
        group.setCompanyId(30L);
        group.setDeleted(false);
        return group;
    }

    private KeywordGroupResult question(String tier) {
        KeywordGroupResult question = new KeywordGroupResult();
        question.setId(100L);
        question.setGroupId(10L);
        question.setQuestionTier(tier);
        question.setKeywordText("测试问题");
        return question;
    }

    private KeywordGroupQuestionPollingUpdateRequest request(boolean enabled) {
        KeywordGroupQuestionPollingUpdateRequest request = new KeywordGroupQuestionPollingUpdateRequest();
        request.setPollingEnabled(enabled);
        return request;
    }
}
