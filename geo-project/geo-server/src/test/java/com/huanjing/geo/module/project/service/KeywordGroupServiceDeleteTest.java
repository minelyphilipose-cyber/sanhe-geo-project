package com.huanjing.geo.module.project.service;

import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.mapper.CompanyPackageBindingMapper;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupWordMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void delete_softDeletesKeywordGroupAfterCleaningRelationsAndRetainingHistory() {
        KeywordGroup group = new KeywordGroup();
        group.setId(10L);
        group.setName("项目A_拓词组");
        group.setProjectId(20L);
        group.setDeleted(false);

        when(keywordGroupMapper.selectById(10L)).thenReturn(group);

        keywordGroupService.delete(10L);

        ArgumentCaptor<KeywordGroup> captor = ArgumentCaptor.forClass(KeywordGroup.class);
        verify(currentUserService).ensurePermission("keyword_group.write");
        verify(keywordGroupResultMapper, never()).delete(any());
        verify(keywordGroupWordMapper, never()).delete(any());
        verify(projectKeywordGroupRelMapper).delete(any());
        verify(keywordGroupMapper).updateById(captor.capture());
        verify(keywordGroupMapper, never()).deleteById(10L);
        assertTrue(captor.getValue().getDeleted());
        assertEquals("项目A_拓词组_deleted_10", captor.getValue().getName());
    }

    @Test
    void delete_allowsActiveProjectKeywordGroupBecauseHistoryIsRetained() {
        KeywordGroup group = new KeywordGroup();
        group.setId(10L);
        group.setName("项目A_拓词组");
        group.setProjectId(20L);
        group.setDeleted(false);

        when(keywordGroupMapper.selectById(10L)).thenReturn(group);

        keywordGroupService.delete(10L);

        verify(currentUserService).ensurePermission("keyword_group.write");
        verify(keywordGroupResultMapper, never()).delete(any());
        verify(keywordGroupWordMapper, never()).delete(any());
        verify(projectKeywordGroupRelMapper).delete(any());
        verify(projectMapper, never()).selectById(20L);
        verify(keywordGroupMapper, never()).deleteById(10L);
        verify(keywordGroupMapper).updateById(any(KeywordGroup.class));
    }
}
