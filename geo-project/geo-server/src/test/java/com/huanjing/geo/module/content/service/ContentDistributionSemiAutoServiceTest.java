package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticleDraftVersion;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.mapper.PackagePublishConfigMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.service.adapter.AutoSelfMediaAdapter;
import com.huanjing.geo.module.content.service.adapter.PlatformFillProfile;
import com.huanjing.geo.module.content.service.adapter.SemiAutoFillTask;
import com.huanjing.geo.module.content.service.adapter.SemiAutoSelfMediaAdapter;
import com.huanjing.geo.module.content.service.adapter.SubmitResult;
import com.huanjing.geo.module.content.service.adapter.ValidationResult;
import com.huanjing.geo.module.content.service.render.MarkdownToHtmlRenderer;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessErrorCodes;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.service.BrandService;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.extension.ExtensionErrorCodes;
import com.huanjing.geo.module.extension.dto.FillTokenIssueResponse;
import com.huanjing.geo.module.extension.service.FillTokenService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.SystemAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentDistributionSemiAutoServiceTest {

    private ArticleDraftMapper articleDraftMapper;
    private ArticleDraftVersionMapper articleDraftVersionMapper;
    private DistributionTaskMapper distributionTaskMapper;
    private ProjectMapper projectMapper;
    private CurrentUserService currentUserService;
    private AutoSelfMediaAdapter autoSelfMediaAdapter;
    private BrandAccessService brandAccessService;
    private FillTokenService fillTokenService;
    private CompanyChannelQuotaService companyChannelQuotaService;
    private AuditService auditService;
    private ContentDistributionService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DistributionTask.class);
        articleDraftMapper = mock(ArticleDraftMapper.class);
        articleDraftVersionMapper = mock(ArticleDraftVersionMapper.class);
        distributionTaskMapper = mock(DistributionTaskMapper.class);
        projectMapper = mock(ProjectMapper.class);
        currentUserService = mock(CurrentUserService.class);
        autoSelfMediaAdapter = mock(AutoSelfMediaAdapter.class);
        brandAccessService = mock(BrandAccessService.class);
        fillTokenService = mock(FillTokenService.class);
        companyChannelQuotaService = mock(CompanyChannelQuotaService.class);
        auditService = mock(AuditService.class);

        service = newService(List.of(new TestSemiAutoAdapter()));
    }

    private ContentDistributionService newService(List<SemiAutoSelfMediaAdapter> semiAutoAdapters) {
        return new ContentDistributionService(
                articleDraftMapper,
                articleDraftVersionMapper,
                distributionTaskMapper,
                mock(SelfMediaAccountMapper.class),
                mock(PackagePublishConfigMapper.class),
                projectMapper,
                mock(PublishSiteMapper.class),
                currentUserService,
                mock(SystemAlertService.class),
                List.of(),
                List.of(autoSelfMediaAdapter),
                semiAutoAdapters,
                mock(BrandService.class),
                mock(CompanyPackageBindingService.class),
                companyChannelQuotaService,
                brandAccessService,
                fillTokenService,
                auditService,
                new ObjectMapper()
        );
    }

    @Test
    void cookieAuthSelfMediaCreatesSemiAutoTaskAndIssuesFillToken() {
        SysUser operator = new SysUser();
        operator.setId(99L);
        operator.setRole("operator");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);

        ArticleDraft article = new ArticleDraft();
        article.setId(20L);
        article.setProjectId(30L);
        article.setTitle("Title");
        article.setStatus("approved");
        when(articleDraftMapper.selectById(20L)).thenReturn(article);

        Project project = new Project();
        project.setId(30L);
        project.setCompanyId(40L);
        project.setBrandId(10L);
        when(projectMapper.selectById(30L)).thenReturn(project);

        ArticleDraftVersion version = new ArticleDraftVersion();
        version.setContentMarkdown("# hello");
        when(articleDraftVersionMapper.selectOne(any())).thenReturn(version);

        when(distributionTaskMapper.selectList(any())).thenReturn(List.of());
        when(distributionTaskMapper.insert(any())).thenAnswer(invocation -> {
            DistributionTask task = invocation.getArgument(0);
            task.setId(50L);
            return 1;
        });
        when(fillTokenService.issueInternalWithoutVersionCheck(60L, 10L, 99L, 50L))
                .thenReturn(new FillTokenIssueResponse("ft.token", 200L, "nonce"));

        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(60L);
        account.setBrandId(10L);
        account.setPlatform("toutiao");
        account.setStatus("active");
        account.setAuthMode("COOKIE");

        DistributionTask task = service.distributeTo(
                20L,
                new TargetContext.SelfMediaTarget(account, null, List.of(), List.of(), null, null, "req-1", Map.of())
        );

        assertEquals("SEMI_AUTO", task.getDispatchMode());
        assertEquals("token_issued", task.getStatus());
        assertNotNull(task.getFillPayload());
        assertTrue(task.getFillPayload().contains("\"platform\":\"toutiao\""));
        assertNull(task.getRequestPayload());
        assertEquals("ft.token", task.getFillToken());
        assertEquals(200L, task.getFillTokenExpiresAt());
        assertEquals("nonce", task.getFillTokenNonce());
        verify(brandAccessService).requireBrandAccess(10L, 99L, BrandAccessAction.OPERATE);
        verify(fillTokenService).issueInternalWithoutVersionCheck(60L, 10L, 99L, 50L);
        verify(autoSelfMediaAdapter, never()).submitToTarget(any(), any(), any());
        verify(auditService).record(argThat(event ->
                "SEMI_AUTO_TASK_CREATED".equals(event.getEventType())
                        && AuditResult.SUCCESS == event.getResult()
                        && Long.valueOf(50L).equals(event.getTaskId())
        ));
    }

    @Test
    void brandAccessDeniedStopsBeforeTaskCreation() {
        stubApprovedArticleProjectAndContent();
        doThrow(new BizException(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, "denied"))
                .when(brandAccessService).requireBrandAccess(10L, 99L, BrandAccessAction.OPERATE);

        BizException ex = assertThrows(BizException.class, () -> service.distributeTo(
                20L,
                new TargetContext.SelfMediaTarget(cookieAccount(), null, List.of(), List.of(), null, null, "req-1", Map.of())
        ));

        assertEquals(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, ex.getCode());
        verify(distributionTaskMapper, never()).insert(any());
        verify(fillTokenService, never()).issueInternalWithoutVersionCheck(any(), any(), any(), any());
    }

    @Test
    void oversizedFillPayloadIsRejectedBeforeTaskCreation() {
        service = newService(List.of(new OversizedSemiAutoAdapter()));
        stubApprovedArticleProjectAndContent();

        BizException ex = assertThrows(BizException.class, () -> service.distributeTo(
                20L,
                new TargetContext.SelfMediaTarget(cookieAccount(), null, List.of(), List.of(), null, null, "req-1", Map.of())
        ));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("payload too large"));
        verify(distributionTaskMapper, never()).insert(any());
        verify(fillTokenService, never()).issueInternalWithoutVersionCheck(any(), any(), any(), any());
    }

    @Test
    void missingSemiAutoAdapterIsRejectedBeforeTaskCreation() {
        service = newService(List.of());
        stubApprovedArticleProjectAndContent();

        BizException ex = assertThrows(BizException.class, () -> service.distributeTo(
                20L,
                new TargetContext.SelfMediaTarget(cookieAccount(), null, List.of(), List.of(), null, null, "req-1", Map.of())
        ));

        assertEquals(501, ex.getCode());
        assertTrue(ex.getMessage().contains("Semi-auto self-media platform not implemented"));
        verify(distributionTaskMapper, never()).insert(any());
        verify(fillTokenService, never()).issueInternalWithoutVersionCheck(any(), any(), any(), any());
    }

    @Test
    void fillTokenIssueFailureRefundsQuotaAuditsFailureAndRethrows() {
        stubApprovedArticleProjectAndContent();
        when(distributionTaskMapper.selectList(any())).thenReturn(List.of());
        when(distributionTaskMapper.insert(any())).thenAnswer(invocation -> {
            DistributionTask task = invocation.getArgument(0);
            task.setId(50L);
            return 1;
        });
        doThrow(new BizException(ExtensionErrorCodes.FILL_TOKEN_INVALID, "fill token failed"))
                .when(fillTokenService).issueInternalWithoutVersionCheck(60L, 10L, 99L, 50L);

        BizException ex = assertThrows(BizException.class, () -> service.distributeTo(
                20L,
                new TargetContext.SelfMediaTarget(cookieAccount(), null, List.of(), List.of(), null, null, "req-1", Map.of())
        ));

        assertEquals(ExtensionErrorCodes.FILL_TOKEN_INVALID, ex.getCode());
        verify(companyChannelQuotaService).refundDistribution(50L);
        verify(auditService).record(argThat(event ->
                "SEMI_AUTO_TASK_CREATION_FAILED".equals(event.getEventType())
                        && AuditResult.DENIED == event.getResult()
                        && String.valueOf(ExtensionErrorCodes.FILL_TOKEN_INVALID).equals(event.getErrorCode())
                        && Long.valueOf(50L).equals(event.getTaskId())
        ));
    }

    private void stubApprovedArticleProjectAndContent() {
        SysUser operator = new SysUser();
        operator.setId(99L);
        operator.setRole("operator");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);

        ArticleDraft article = new ArticleDraft();
        article.setId(20L);
        article.setProjectId(30L);
        article.setTitle("Title");
        article.setStatus("approved");
        when(articleDraftMapper.selectById(20L)).thenReturn(article);

        Project project = new Project();
        project.setId(30L);
        project.setCompanyId(40L);
        project.setBrandId(10L);
        when(projectMapper.selectById(30L)).thenReturn(project);

        ArticleDraftVersion version = new ArticleDraftVersion();
        version.setContentMarkdown("# hello");
        when(articleDraftVersionMapper.selectOne(any())).thenReturn(version);
    }

    private SelfMediaAccount cookieAccount() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(60L);
        account.setBrandId(10L);
        account.setPlatform("toutiao");
        account.setStatus("active");
        account.setAuthMode("COOKIE");
        return account;
    }

    private static class TestSemiAutoAdapter implements SemiAutoSelfMediaAdapter {
        private final MarkdownToHtmlRenderer renderer = new MarkdownToHtmlRenderer();

        @Override
        public String platform() {
            return "toutiao";
        }

        @Override
        public PlatformFillProfile fillProfile() {
            return new PlatformFillProfile(
                    "toutiao",
                    "https://example.test/publish",
                    List.of(".toutiao.com"),
                    List.of("sessionid"),
                    Map.of("content", ".ProseMirror"),
                    List.of("p", "h1"),
                    Map.of()
            );
        }

        @Override
        public MarkdownToHtmlRenderer markdownToHtmlRenderer() {
            return renderer;
        }

        @Override
        public SemiAutoFillTask prepareFillTask(ArticleDraft article, String contentMarkdown, PlatformFillProfile profile) {
            return SemiAutoSelfMediaAdapter.super.prepareFillTask(article, contentMarkdown, profile);
        }
    }

    private static final class OversizedSemiAutoAdapter extends TestSemiAutoAdapter {
        @Override
        public SemiAutoFillTask prepareFillTask(ArticleDraft article, String contentMarkdown, PlatformFillProfile profile) {
            return new SemiAutoFillTask(
                    "toutiao",
                    "https://example.test/publish",
                    "Title",
                    "x".repeat(17 * 1024),
                    null,
                    List.of(),
                    null,
                    profile
            );
        }
    }
}
