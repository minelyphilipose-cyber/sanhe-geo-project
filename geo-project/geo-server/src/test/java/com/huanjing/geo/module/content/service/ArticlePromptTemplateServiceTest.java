package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.dto.ArticlePromptTemplateDtos.TemplateSaveRequest;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateMapper;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateVersionMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticlePromptTemplateServiceTest {

    private ArticlePromptTemplateMapper templateMapper;
    private ArticlePromptTemplateVersionMapper versionMapper;
    private ArticlePromptTemplateService service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(ArticlePromptTemplateMapper.class);
        versionMapper = mock(ArticlePromptTemplateVersionMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        AuditService auditService = mock(AuditService.class);
        SysUser user = new SysUser();
        user.setId(7L);
        when(currentUserService.requireCurrentUser()).thenReturn(user);
        service = new ArticlePromptTemplateService(templateMapper, versionMapper, currentUserService, auditService);
    }

    @Test
    void updateOverwritesCurrentPublishedVersionInsteadOfCreatingDraftVersion() {
        ArticlePromptTemplate template = template();
        ArticlePromptTemplateVersion current = currentVersion();
        when(templateMapper.selectById(10L)).thenReturn(template);
        when(versionMapper.selectById(20L)).thenReturn(current);
        when(versionMapper.selectList(any())).thenReturn(List.of(current));

        service.update(10L, request("new system", "new user"));

        ArgumentCaptor<ArticlePromptTemplateVersion> versionCaptor = ArgumentCaptor.forClass(ArticlePromptTemplateVersion.class);
        verify(versionMapper).updateById(versionCaptor.capture());
        verify(versionMapper, never()).insert(any());
        ArticlePromptTemplateVersion saved = versionCaptor.getValue();
        assertEquals(20L, saved.getId());
        assertEquals(1, saved.getVersionNo());
        assertEquals("new system", saved.getSystemPrompt());
        assertEquals("new user", saved.getUserPromptTemplate());
        assertEquals(ArticlePromptTemplateService.VERSION_PUBLISHED, saved.getStatus());
        assertNotNull(saved.getPublishedAt());
    }

    private ArticlePromptTemplate template() {
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setName("论坛问答模板");
        template.setChannelGroupCode("forum");
        template.setArticleTypeCode("faq");
        template.setQuestionSceneCode("qa");
        template.setWeight(10);
        template.setSortOrder(0);
        template.setStatus(ArticlePromptTemplateService.STATUS_ACTIVE);
        template.setContactDisclosureMode(ArticlePromptTemplateService.CONTACT_FULL);
        template.setCurrentVersionId(20L);
        return template;
    }

    private ArticlePromptTemplateVersion currentVersion() {
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(20L);
        version.setTemplateId(10L);
        version.setVersionNo(1);
        version.setSystemPrompt("old system");
        version.setUserPromptTemplate("old user");
        version.setStatus(ArticlePromptTemplateService.VERSION_PUBLISHED);
        return version;
    }

    private TemplateSaveRequest request(String systemPrompt, String userPromptTemplate) {
        return new TemplateSaveRequest(
                "论坛问答模板",
                "desc",
                "forum",
                null,
                null,
                "faq",
                "qa",
                10,
                0,
                ArticlePromptTemplateService.STATUS_ACTIVE,
                null,
                ArticlePromptTemplateService.CONTACT_FULL,
                systemPrompt,
                userPromptTemplate,
                "{}",
                "{}",
                null
        );
    }
}
