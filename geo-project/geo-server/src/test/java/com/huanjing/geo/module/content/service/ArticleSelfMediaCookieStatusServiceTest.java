package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.credential.entity.SelfMediaCookieCredential;
import com.huanjing.geo.module.content.dto.SelfMediaCookieStatusBatchRequest;
import com.huanjing.geo.module.content.dto.SelfMediaCookieStatusBatchResponse;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaCookieCredentialMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleSelfMediaCookieStatusServiceTest {

    private ArticleDraftMapper articleDraftMapper;
    private ProjectMapper projectMapper;
    private SelfMediaAccountMapper accountMapper;
    private SelfMediaCookieCredentialMapper credentialMapper;
    private CurrentUserService currentUserService;
    private BrandAccessService brandAccessService;
    private ArticleSelfMediaCookieStatusService service;

    @BeforeEach
    void setUp() {
        articleDraftMapper = mock(ArticleDraftMapper.class);
        projectMapper = mock(ProjectMapper.class);
        accountMapper = mock(SelfMediaAccountMapper.class);
        credentialMapper = mock(SelfMediaCookieCredentialMapper.class);
        currentUserService = mock(CurrentUserService.class);
        brandAccessService = mock(BrandAccessService.class);
        service = new ArticleSelfMediaCookieStatusService(
                articleDraftMapper,
                projectMapper,
                accountMapper,
                credentialMapper,
                currentUserService,
                brandAccessService
        );

        SysUser operator = new SysUser();
        operator.setId(99L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
    }

    @Test
    void batchReturnsAccountCookieStatusForAuthorizedArticles() {
        LocalDateTime capturedAt = LocalDateTime.of(2026, 5, 8, 10, 30);
        when(articleDraftMapper.selectBatchIds(List.of(20L, 21L))).thenReturn(List.of(article(20L, 30L), article(21L, 30L)));
        when(projectMapper.selectBatchIds(List.of(30L))).thenReturn(List.of(project(30L, 10L, 40L)));
        when(accountMapper.selectList(any())).thenReturn(List.of(
                account(60L, 10L, "toutiao", "头条主号", "active"),
                account(61L, 10L, "zhihu", "知乎主号", "disabled")
        ));
        when(credentialMapper.selectActiveMetaByAccountIds(List.of(60L, 61L))).thenReturn(List.of(
                credential(60L, 1, capturedAt.minusDays(1)),
                credential(60L, 2, capturedAt)
        ));

        SelfMediaCookieStatusBatchResponse response = service.batch(
                new SelfMediaCookieStatusBatchRequest(List.of(20L, 20L, 21L), List.of("Toutiao", "zhihu"))
        );

        assertEquals(2, response.items().size());
        assertEquals(20L, response.items().get(0).articleId());
        assertEquals(10L, response.items().get(0).brandId());
        assertEquals(2, response.items().get(0).accounts().size());
        assertEquals("头条主号", response.items().get(0).accounts().get(0).accountName());
        assertEquals("VALID", response.items().get(0).accounts().get(0).credentialStatus());
        assertEquals(true, response.items().get(0).accounts().get(0).canStartFill());
        assertEquals(capturedAt, response.items().get(0).accounts().get(0).lastCapturedAt());
        assertEquals("ACCOUNT_DISABLED", response.items().get(0).accounts().get(1).reason());
        assertEquals(false, response.items().get(0).accounts().get(1).canStartFill());

        verify(currentUserService).ensurePermission("project.read");
        verify(currentUserService, times(2)).ensurePartnerResourceAccess(any(SysUser.class), any(), any());
        verify(brandAccessService, times(2)).requireBrandAccess(10L, 99L, BrandAccessAction.OPERATE);
    }

    @Test
    void batchMarksCookieMissingWhenNoActiveCredentialExists() {
        when(articleDraftMapper.selectBatchIds(List.of(20L))).thenReturn(List.of(article(20L, 30L)));
        when(projectMapper.selectBatchIds(List.of(30L))).thenReturn(List.of(project(30L, 10L, 40L)));
        when(accountMapper.selectList(any())).thenReturn(List.of(account(60L, 10L, "toutiao", "头条主号", "active")));
        when(credentialMapper.selectActiveMetaByAccountIds(List.of(60L))).thenReturn(List.of());

        SelfMediaCookieStatusBatchResponse response = service.batch(
                new SelfMediaCookieStatusBatchRequest(List.of(20L), null)
        );

        assertEquals("MISSING", response.items().get(0).accounts().get(0).credentialStatus());
        assertEquals("COOKIE_MISSING", response.items().get(0).accounts().get(0).reason());
        assertEquals(false, response.items().get(0).accounts().get(0).canStartFill());
    }

    @Test
    void batchRejectsTooManyArticleIdsBeforeQueryingDatabase() {
        List<Long> articleIds = java.util.stream.LongStream.rangeClosed(1, 51)
                .boxed()
                .toList();

        BizException ex = assertThrows(BizException.class, () ->
                service.batch(new SelfMediaCookieStatusBatchRequest(articleIds, List.of("toutiao")))
        );

        assertEquals(400, ex.getCode());
        verify(articleDraftMapper, never()).selectBatchIds(any());
        verify(projectMapper, never()).selectBatchIds(any());
    }

    @Test
    void batchRejectsMissingArticle() {
        when(articleDraftMapper.selectBatchIds(List.of(20L))).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () ->
                service.batch(new SelfMediaCookieStatusBatchRequest(List.of(20L), List.of("toutiao")))
        );

        assertEquals(404, ex.getCode());
        verify(accountMapper, never()).selectList(any());
        verify(credentialMapper, never()).selectActiveMetaByAccountIds(any());
    }

    private ArticleDraft article(Long id, Long projectId) {
        ArticleDraft article = new ArticleDraft();
        article.setId(id);
        article.setProjectId(projectId);
        return article;
    }

    private Project project(Long id, Long brandId, Long partnerId) {
        Project project = new Project();
        project.setId(id);
        project.setBrandId(brandId);
        project.setPartnerId(partnerId);
        return project;
    }

    private SelfMediaAccount account(Long id, Long brandId, String platform, String accountName, String status) {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(id);
        account.setBrandId(brandId);
        account.setPlatform(platform);
        account.setAccountName(accountName);
        account.setPlatformAccountId(platform + "-id");
        account.setStatus(status);
        account.setAuthMode("COOKIE");
        return account;
    }

    private SelfMediaCookieCredential credential(Long accountId, Integer version, LocalDateTime capturedAt) {
        SelfMediaCookieCredential credential = new SelfMediaCookieCredential();
        credential.setSelfMediaAccountId(accountId);
        credential.setVersion(version);
        credential.setCapturedAt(capturedAt);
        return credential;
    }
}
