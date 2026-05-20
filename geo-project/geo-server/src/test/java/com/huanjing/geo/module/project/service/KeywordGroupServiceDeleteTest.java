package com.huanjing.geo.module.project.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.mapper.CompanyPackageBindingMapper;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupWordMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeywordGroupServiceDeleteTest {

    @Mock
    private KeywordGroupMapper keywordGroupMapper;
    @Mock
    private KeywordGroupResultMapper keywordGroupResultMapper;
    @Mock
    private KeywordGroupWordMapper keywordGroupWordMapper;
    @Mock
    private CompanyMapper companyMapper;
    @Mock
    private CompanyPackageBindingMapper companyPackageBindingMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private KeywordTypeConfigService keywordTypeConfigService;
    @Mock
    private KeywordLlmQuestionService keywordLlmQuestionService;

    @InjectMocks
    private KeywordGroupService keywordGroupService;

    @Test
    void delete_hardDeletesKeywordGroupAfterCleaningChildrenAndRelations() {
        KeywordGroup group = new KeywordGroup();
        group.setId(10L);
        group.setName("项目A_拓词组");
        group.setProjectId(20L);
        group.setDeleted(false);
        Project project = new Project();
        project.setId(20L);
        project.setStatus("pending_start");

        when(keywordGroupMapper.selectById(10L)).thenReturn(group);
        when(projectMapper.selectById(20L)).thenReturn(project);

        keywordGroupService.delete(10L);

        verify(currentUserService).ensurePermission("keyword_group.write");
        verify(keywordGroupResultMapper).delete(any());
        verify(keywordGroupWordMapper).delete(any());
        verify(projectKeywordGroupRelMapper).delete(any());
        verify(keywordGroupMapper).deleteById(10L);
        verify(keywordGroupMapper, never()).updateById(any(KeywordGroup.class));
    }

    @Test
    void delete_rejectsActiveProjectKeywordGroup() {
        KeywordGroup group = new KeywordGroup();
        group.setId(10L);
        group.setProjectId(20L);
        group.setDeleted(false);
        Project project = new Project();
        project.setId(20L);
        project.setStatus("active");

        when(keywordGroupMapper.selectById(10L)).thenReturn(group);
        when(projectMapper.selectById(20L)).thenReturn(project);

        assertThrows(BizException.class, () -> keywordGroupService.delete(10L));

        verify(currentUserService).ensurePermission("keyword_group.write");
        verify(keywordGroupResultMapper, never()).delete(any());
        verify(keywordGroupWordMapper, never()).delete(any());
        verify(projectKeywordGroupRelMapper, never()).delete(any());
        verify(keywordGroupMapper, never()).deleteById(10L);
    }
}
