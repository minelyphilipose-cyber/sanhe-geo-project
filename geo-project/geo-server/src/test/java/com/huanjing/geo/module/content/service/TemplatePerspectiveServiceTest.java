package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.entity.BrandChannelTemplatePerspective;
import com.huanjing.geo.module.content.entity.ContentTemplatePerspective;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.BrandChannelTemplatePerspectiveMapper;
import com.huanjing.geo.module.content.mapper.ContentTemplatePerspectiveMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplatePerspectiveServiceTest {

    private ContentTemplatePerspectiveMapper perspectiveMapper;
    private BrandChannelTemplatePerspectiveMapper configMapper;
    private BatchArticleGenerationTaskMapper taskMapper;
    private TemplatePerspectiveService service;

    @BeforeEach
    void setUp() {
        perspectiveMapper = mock(ContentTemplatePerspectiveMapper.class);
        configMapper = mock(BrandChannelTemplatePerspectiveMapper.class);
        taskMapper = mock(BatchArticleGenerationTaskMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser user = new SysUser();
        user.setId(7L);
        when(currentUserService.requireCurrentUser()).thenReturn(user);
        service = new TemplatePerspectiveService(
                perspectiveMapper,
                configMapper,
                mock(ArticlePromptTemplateMapper.class),
                taskMapper,
                currentUserService
        );
    }

    @Test
    void resolveUsesExactEnabledConfigBeforeChannelAll() {
        BrandChannelTemplatePerspective exact = config(21L, "industry_neutral");
        when(configMapper.selectOne(any())).thenReturn(exact);
        when(perspectiveMapper.selectById("industry_neutral")).thenReturn(perspective("industry_neutral", true));

        TemplatePerspectiveService.ResolvedPerspective result = service.resolve(3L, "self_media", "wechat");

        assertEquals("industry_neutral", result.perspectiveCode());
        assertEquals(TemplatePerspectiveService.MATCH_SCOPE_EXACT, result.matchedScope());
        assertEquals(21L, result.matchedConfigId());
    }

    @Test
    void resolveFallsBackToChannelAllWhenExactIsDisabledOrMissing() {
        BrandChannelTemplatePerspective channelAll = config(22L, "review_recommend");
        when(configMapper.selectOne(any())).thenReturn(null, channelAll);
        when(perspectiveMapper.selectById("review_recommend")).thenReturn(perspective("review_recommend", false));

        TemplatePerspectiveService.ResolvedPerspective result = service.resolve(3L, "self_media", "wechat");

        assertEquals("review_recommend", result.perspectiveCode());
        assertEquals(TemplatePerspectiveService.MATCH_SCOPE_CHANNEL_ALL, result.matchedScope());
        assertEquals(22L, result.matchedConfigId());
    }

    @Test
    void resolveDefaultsToCustomerWithoutBrandOrConfig() {
        TemplatePerspectiveService.ResolvedPerspective noBrand = service.resolve(null, "self_media", "wechat");

        assertEquals(TemplatePerspectiveCodes.CUSTOMER, noBrand.perspectiveCode());
        assertEquals(TemplatePerspectiveService.MATCH_SCOPE_DEFAULT, noBrand.matchedScope());
        assertNull(noBrand.matchedConfigId());

        when(configMapper.selectOne(any())).thenReturn(null);
        TemplatePerspectiveService.ResolvedPerspective noConfig = service.resolve(3L, "self_media", "wechat");

        assertEquals(TemplatePerspectiveCodes.CUSTOMER, noConfig.perspectiveCode());
        assertEquals(TemplatePerspectiveService.MATCH_SCOPE_DEFAULT, noConfig.matchedScope());
        assertNull(noConfig.matchedConfigId());
    }

    @Test
    void resolveUsesBusinessDefaultsForThirdPartyChannels() {
        when(configMapper.selectOne(any())).thenReturn(null);

        assertEquals(TemplatePerspectiveCodes.INDUSTRY_NEUTRAL,
                service.resolve(3L, "industry_site", null).perspectiveCode());
        assertEquals(TemplatePerspectiveCodes.REVIEW_RECOMMEND,
                service.resolve(3L, "forum", null).perspectiveCode());
        assertEquals(TemplatePerspectiveCodes.INDUSTRY_NEUTRAL,
                service.resolve(null, "authority_media", "news_source").perspectiveCode());
    }

    @Test
    void deleteBrandConfigRejectsRowsReferencedByFrozenTasks() {
        when(taskMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.deleteBrandConfig(22L));

        assertEquals(400, ex.getCode());
        verify(configMapper, never()).deleteById(any(Long.class));
    }

    private BrandChannelTemplatePerspective config(Long id, String perspectiveCode) {
        BrandChannelTemplatePerspective row = new BrandChannelTemplatePerspective();
        row.setId(id);
        row.setBrandId(3L);
        row.setChannelGroupCode("self_media");
        row.setChannelSubCode(TemplatePerspectiveCodes.CHANNEL_SUB_ALL);
        row.setPerspectiveCode(perspectiveCode);
        row.setEnabled(true);
        return row;
    }

    private ContentTemplatePerspective perspective(String code, boolean enabled) {
        ContentTemplatePerspective row = new ContentTemplatePerspective();
        row.setCode(code);
        row.setName(code);
        row.setEnabled(enabled);
        return row;
    }
}
