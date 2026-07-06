package com.huanjing.geo.module.content.schedule;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticleDraftVersion;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.content.service.ArticleImagePublicUrlRewriter;
import com.huanjing.geo.module.content.service.SelfMediaPublishMaterialSelectionService;
import com.huanjing.geo.module.content.service.SelfMediaScheduleCapabilityService;
import com.huanjing.geo.module.content.service.adapter.AutoSelfMediaAdapter;
import com.huanjing.geo.module.content.service.adapter.ReviewStatusResult;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformCapabilityContract;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformPublishChannel;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleMode;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleRules;
import com.huanjing.geo.module.content.service.adapter.SubmitResult;
import com.huanjing.geo.module.content.service.adapter.ValidationResult;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfficialApiSelfMediaPublishScheduleAdapterTest {
    private SelfMediaPlatformScheduleAdapterRouter platformRouter;
    private SelfMediaPublishScheduleMapper scheduleMapper;
    private DistributionTaskMapper distributionTaskMapper;
    private ArticleDraftMapper articleDraftMapper;
    private ArticleDraftVersionMapper articleDraftVersionMapper;
    private SelfMediaAccountMapper selfMediaAccountMapper;
    private ProjectMapper projectMapper;
    private ArticleImagePublicUrlRewriter imagePublicUrlRewriter;
    private SelfMediaPublishMaterialSelectionService materialSelectionService;
    private SelfMediaScheduleCapabilityService capabilityService;
    private OfficialApiSelfMediaPublishScheduleAdapter adapter;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(DistributionTask.class);
        initTableInfo(ArticleDraftVersion.class);
        initTableInfo(SelfMediaPublishSchedule.class);
    }

    private static void initTableInfo(Class<?> entityType) {
        try {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        } catch (Exception ignored) {
            // Some larger test runs initialize table metadata before this class is loaded.
        }
    }

    @BeforeEach
    void setUp() {
        platformRouter = mock(SelfMediaPlatformScheduleAdapterRouter.class);
        scheduleMapper = mock(SelfMediaPublishScheduleMapper.class);
        distributionTaskMapper = mock(DistributionTaskMapper.class);
        articleDraftMapper = mock(ArticleDraftMapper.class);
        articleDraftVersionMapper = mock(ArticleDraftVersionMapper.class);
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        projectMapper = mock(ProjectMapper.class);
        imagePublicUrlRewriter = mock(ArticleImagePublicUrlRewriter.class);
        materialSelectionService = mock(SelfMediaPublishMaterialSelectionService.class);
        capabilityService = mock(SelfMediaScheduleCapabilityService.class);
        adapter = new OfficialApiSelfMediaPublishScheduleAdapter(
                platformRouter,
                List.of(new FakeOfficialApiAdapter()),
                scheduleMapper,
                distributionTaskMapper,
                articleDraftMapper,
                articleDraftVersionMapper,
                selfMediaAccountMapper,
                projectMapper,
                imagePublicUrlRewriter,
                materialSelectionService,
                capabilityService,
                new ObjectMapper()
        );
    }

    @Test
    void supportsOnlyOfficialApiPlatformsWithAdapter() {
        when(platformRouter.contract("wechat_mp")).thenReturn(Optional.of(contract(SelfMediaPlatformPublishChannel.OFFICIAL_API)));
        when(platformRouter.contract("toutiao")).thenReturn(Optional.of(contract(SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION)));

        assertTrue(adapter.supports("wechat_mp"));
        org.junit.jupiter.api.Assertions.assertFalse(adapter.supports("toutiao"));
    }

    @Test
    void scheduleCreatesDistributionTaskAndReturnsScheduled() {
        SelfMediaPublishSchedule row = scheduleRow();
        when(scheduleMapper.selectById(10L)).thenReturn(row);
        when(distributionTaskMapper.selectOne(any())).thenReturn(null);
        when(articleDraftMapper.selectById(20L)).thenReturn(article());
        when(projectMapper.selectById(30L)).thenReturn(project());
        when(selfMediaAccountMapper.selectById(40L)).thenReturn(account());
        ArticleDraftVersion version = new ArticleDraftVersion();
        version.setContentMarkdown("content");
        when(articleDraftVersionMapper.selectOne(any())).thenReturn(version);
        when(materialSelectionService.select(any(), any(), eq("content")))
                .thenReturn(new SelfMediaPublishMaterialSelectionService.Selection(88L, List.of(101L)));
        when(imagePublicUrlRewriter.rewrite(any(), eq("content"))).thenReturn("content");
        when(capabilityService.automationOptions("wechat_mp")).thenReturn(Map.of());
        when(distributionTaskMapper.insert(any())).thenAnswer(invocation -> {
            DistributionTask task = invocation.getArgument(0);
            task.setId(99L);
            return 1;
        });
        when(distributionTaskMapper.selectById(99L)).thenAnswer(invocation -> {
            DistributionTask task = new DistributionTask();
            task.setId(99L);
            task.setStatus("submitted");
            task.setPlatformPublishId("pub-1");
            return task;
        });

        ScheduleExecutionResult result = adapter.schedule(SelfMediaPublishScheduleVO.from(row));

        assertTrue(result.success());
        verify(distributionTaskMapper).insert(ArgumentMatchers.argThat(task ->
                "AUTO".equals(task.getDispatchMode())
                        && "mp_account".equals(task.getTargetKind())
                        && "self-media-schedule:10".equals(task.getRequestId())
        ));
        verify(scheduleMapper).updateById(ArgumentMatchers.argThat(schedule ->
                Long.valueOf(99L).equals(schedule.getDistributionTaskId())
        ));
    }

    @Test
    void schedulePreflightCredentialFailureDoesNotCreateDistributionTask() {
        adapter = new OfficialApiSelfMediaPublishScheduleAdapter(
                platformRouter,
                List.of(new PreflightFailingAdapter(new BizException(401, "credential expired"))),
                scheduleMapper,
                distributionTaskMapper,
                articleDraftMapper,
                articleDraftVersionMapper,
                selfMediaAccountMapper,
                projectMapper,
                imagePublicUrlRewriter,
                materialSelectionService,
                capabilityService,
                new ObjectMapper()
        );
        SelfMediaPublishSchedule row = scheduleRow();
        when(scheduleMapper.selectById(10L)).thenReturn(row);
        when(distributionTaskMapper.selectOne(any())).thenReturn(null);
        when(selfMediaAccountMapper.selectById(40L)).thenReturn(account());

        ScheduleExecutionResult result = adapter.schedule(SelfMediaPublishScheduleVO.from(row));

        assertEquals(false, result.success());
        assertEquals("OFFICIAL_API_CREDENTIAL_EXPIRED", result.failureCode());
        verify(distributionTaskMapper, never()).insert(any());
        verify(articleDraftMapper, never()).selectById(any());
    }

    @Test
    void schedulePreflightCredentialRefreshingReturnsRetry() {
        adapter = new OfficialApiSelfMediaPublishScheduleAdapter(
                platformRouter,
                List.of(new PreflightFailingAdapter(new BizException(429, "token refreshing"))),
                scheduleMapper,
                distributionTaskMapper,
                articleDraftMapper,
                articleDraftVersionMapper,
                selfMediaAccountMapper,
                projectMapper,
                imagePublicUrlRewriter,
                materialSelectionService,
                capabilityService,
                new ObjectMapper()
        );
        SelfMediaPublishSchedule row = scheduleRow();
        when(scheduleMapper.selectById(10L)).thenReturn(row);
        when(distributionTaskMapper.selectOne(any())).thenReturn(null);
        when(selfMediaAccountMapper.selectById(40L)).thenReturn(account());

        ScheduleExecutionResult result = adapter.schedule(SelfMediaPublishScheduleVO.from(row));

        assertEquals(false, result.success());
        assertEquals("OFFICIAL_API_CREDENTIAL_REFRESHING", result.failureCode());
        assertTrue(result.nextAttemptAt() != null);
        verify(distributionTaskMapper, never()).insert(any());
    }

    @Test
    void scheduleAutoSelectsCoverAndContentImagesWhenOptionsAreMissing() {
        adapter = new OfficialApiSelfMediaPublishScheduleAdapter(
                platformRouter,
                List.of(new MaterialCheckingAdapter()),
                scheduleMapper,
                distributionTaskMapper,
                articleDraftMapper,
                articleDraftVersionMapper,
                selfMediaAccountMapper,
                projectMapper,
                imagePublicUrlRewriter,
                materialSelectionService,
                capabilityService,
                new ObjectMapper()
        );
        SelfMediaPublishSchedule row = scheduleRow();
        when(scheduleMapper.selectById(10L)).thenReturn(row);
        when(distributionTaskMapper.selectOne(any())).thenReturn(null);
        when(articleDraftMapper.selectById(20L)).thenReturn(article());
        when(projectMapper.selectById(30L)).thenReturn(project());
        when(selfMediaAccountMapper.selectById(40L)).thenReturn(account());
        ArticleDraftVersion version = new ArticleDraftVersion();
        version.setContentMarkdown("![a](http://img.local/a.png)");
        when(articleDraftVersionMapper.selectOne(any())).thenReturn(version);
        when(capabilityService.automationOptions("wechat_mp")).thenReturn(Map.of());
        when(materialSelectionService.select(any(), any(), eq("![a](http://img.local/a.png)")))
                .thenReturn(new SelfMediaPublishMaterialSelectionService.Selection(88L, List.of(101L, 102L)));
        when(imagePublicUrlRewriter.rewrite(any(), eq("![a](http://img.local/a.png)"))).thenReturn("rewritten");
        when(distributionTaskMapper.insert(any())).thenAnswer(invocation -> {
            DistributionTask task = invocation.getArgument(0);
            task.setId(99L);
            return 1;
        });
        when(distributionTaskMapper.selectById(99L)).thenAnswer(invocation -> {
            DistributionTask task = new DistributionTask();
            task.setId(99L);
            task.setStatus("submitted");
            task.setPlatformPublishId("pub-1");
            return task;
        });

        ScheduleExecutionResult result = adapter.schedule(SelfMediaPublishScheduleVO.from(row));

        assertTrue(result.success());
    }

    @Test
    void scheduleUsesSubjectBrandAsMaterialBrandForThirdPartyArticles() {
        adapter = new OfficialApiSelfMediaPublishScheduleAdapter(
                platformRouter,
                List.of(new SubjectMaterialCheckingAdapter()),
                scheduleMapper,
                distributionTaskMapper,
                articleDraftMapper,
                articleDraftVersionMapper,
                selfMediaAccountMapper,
                projectMapper,
                imagePublicUrlRewriter,
                materialSelectionService,
                capabilityService,
                new ObjectMapper()
        );
        SelfMediaPublishSchedule row = scheduleRow();
        ArticleDraft article = article();
        article.setSubjectBrandId(60L);
        article.setSubjectProjectId(31L);
        when(scheduleMapper.selectById(10L)).thenReturn(row);
        when(distributionTaskMapper.selectOne(any())).thenReturn(null);
        when(articleDraftMapper.selectById(20L)).thenReturn(article);
        when(projectMapper.selectById(30L)).thenReturn(project());
        when(selfMediaAccountMapper.selectById(40L)).thenReturn(account());
        ArticleDraftVersion version = new ArticleDraftVersion();
        version.setContentMarkdown("![subject](https://cdn.local/subject.png)");
        when(articleDraftVersionMapper.selectOne(any())).thenReturn(version);
        when(capabilityService.automationOptions("wechat_mp")).thenReturn(Map.of());
        when(materialSelectionService.select(any(), eq(article), eq("![subject](https://cdn.local/subject.png)")))
                .thenReturn(new SelfMediaPublishMaterialSelectionService.Selection(188L, List.of(201L)));
        when(imagePublicUrlRewriter.rewriteForBrand(eq(60L), eq("![subject](https://cdn.local/subject.png)")))
                .thenReturn("rewritten-subject");
        when(distributionTaskMapper.insert(any())).thenAnswer(invocation -> {
            DistributionTask task = invocation.getArgument(0);
            task.setId(99L);
            return 1;
        });
        when(distributionTaskMapper.selectById(99L)).thenAnswer(invocation -> {
            DistributionTask task = new DistributionTask();
            task.setId(99L);
            task.setStatus("submitted");
            task.setPlatformPublishId("pub-1");
            return task;
        });

        ScheduleExecutionResult result = adapter.schedule(SelfMediaPublishScheduleVO.from(row));

        assertTrue(result.success());
        verify(imagePublicUrlRewriter).rewriteForBrand(60L, "![subject](https://cdn.local/subject.png)");
    }

    @Test
    void scheduleFailureDiagnosticsIncludeOperationStage() {
        adapter = new OfficialApiSelfMediaPublishScheduleAdapter(
                platformRouter,
                List.of(new SubmitFailingAdapter()),
                scheduleMapper,
                distributionTaskMapper,
                articleDraftMapper,
                articleDraftVersionMapper,
                selfMediaAccountMapper,
                projectMapper,
                imagePublicUrlRewriter,
                materialSelectionService,
                capabilityService,
                new ObjectMapper()
        );
        SelfMediaPublishSchedule row = scheduleRow();
        when(scheduleMapper.selectById(10L)).thenReturn(row);
        when(distributionTaskMapper.selectOne(any())).thenReturn(null);
        when(articleDraftMapper.selectById(20L)).thenReturn(article());
        when(projectMapper.selectById(30L)).thenReturn(project());
        when(selfMediaAccountMapper.selectById(40L)).thenReturn(account());
        ArticleDraftVersion version = new ArticleDraftVersion();
        version.setContentMarkdown("content");
        when(articleDraftVersionMapper.selectOne(any())).thenReturn(version);
        when(materialSelectionService.select(any(), any(), eq("content")))
                .thenReturn(new SelfMediaPublishMaterialSelectionService.Selection(88L, List.of()));
        when(imagePublicUrlRewriter.rewrite(any(), eq("content"))).thenReturn("content");
        when(capabilityService.automationOptions("wechat_mp")).thenReturn(Map.of());

        ScheduleExecutionResult result = adapter.schedule(SelfMediaPublishScheduleVO.from(row));

        assertEquals(false, result.success());
        assertEquals("WECHAT_API_UNAUTHORIZED", result.failureCode());
        assertTrue(result.diagnosticsJson().contains("\"operationStage\":\"WECHAT_ADD_DRAFT\""));
        assertTrue(result.diagnosticsJson().contains("\"operationStageLabel\":\"新增公众号草稿\""));
        assertTrue(result.diagnosticsJson().contains("\"platformRawError\":\"api unauthorized rid: draft-rid\""));
    }

    @Test
    void checkPublishResultReturnsRetryWhenReviewApiFails() {
        adapter = new OfficialApiSelfMediaPublishScheduleAdapter(
                platformRouter,
                List.of(new ThrowingReviewAdapter()),
                scheduleMapper,
                distributionTaskMapper,
                articleDraftMapper,
                articleDraftVersionMapper,
                selfMediaAccountMapper,
                projectMapper,
                imagePublicUrlRewriter,
                materialSelectionService,
                capabilityService,
                new ObjectMapper()
        );
        SelfMediaPublishSchedule row = scheduleRow();
        row.setDistributionTaskId(99L);
        DistributionTask task = new DistributionTask();
        task.setId(99L);
        task.setSelfMediaAccountId(40L);
        task.setStatus("submitted");
        when(scheduleMapper.selectById(10L)).thenReturn(row);
        when(distributionTaskMapper.selectById(99L)).thenReturn(task);
        when(selfMediaAccountMapper.selectById(40L)).thenReturn(account());

        PublishCheckResult result = adapter.checkPublishResult(SelfMediaPublishScheduleVO.from(row));

        assertEquals(PublishCheckResult.Outcome.RETRY, result.outcome());
        assertEquals("OFFICIAL_API_REVIEW_PENDING", result.failureCode());
        verify(distributionTaskMapper).updateById(ArgumentMatchers.argThat(updated ->
                "unknown".equals(updated.getReviewStatus())
                        && updated.getNextReviewCheckAt() != null
        ));
    }

    @Test
    void checkPublishResultReturnsWechatArticleUrlWhenReviewPublished() {
        adapter = new OfficialApiSelfMediaPublishScheduleAdapter(
                platformRouter,
                List.of(new PublishedReviewAdapter()),
                scheduleMapper,
                distributionTaskMapper,
                articleDraftMapper,
                articleDraftVersionMapper,
                selfMediaAccountMapper,
                projectMapper,
                imagePublicUrlRewriter,
                materialSelectionService,
                capabilityService,
                new ObjectMapper()
        );
        SelfMediaPublishSchedule row = scheduleRow();
        row.setDistributionTaskId(99L);
        DistributionTask task = new DistributionTask();
        task.setId(99L);
        task.setSelfMediaAccountId(40L);
        task.setStatus("submitted");
        task.setReviewStatus("under_review");
        when(scheduleMapper.selectById(10L)).thenReturn(row);
        when(distributionTaskMapper.selectById(99L)).thenReturn(task);
        when(selfMediaAccountMapper.selectById(40L)).thenReturn(account());

        PublishCheckResult result = adapter.checkPublishResult(SelfMediaPublishScheduleVO.from(row));

        assertEquals(PublishCheckResult.Outcome.PUBLISHED, result.outcome());
        assertEquals("https://mp.weixin.qq.com/s/article-1", result.platformPublishedUrl());
        verify(distributionTaskMapper).updateById(ArgumentMatchers.argThat(updated ->
                "published".equals(updated.getStatus())
                        && "article-1".equals(updated.getPlatformArticleId())
                        && "https://mp.weixin.qq.com/s/article-1".equals(updated.getPublishedUrl())
        ));
    }

    private SelfMediaPublishSchedule scheduleRow() {
        SelfMediaPublishSchedule row = new SelfMediaPublishSchedule();
        row.setId(10L);
        row.setArticleId(20L);
        row.setBrandId(50L);
        row.setSelfMediaAccountId(40L);
        row.setPlatform("wechat_mp");
        row.setScheduleStrategy("backend_delayed_publish");
        row.setPlannedPublishAt(LocalDateTime.now().plusMinutes(5));
        row.setPlatformScheduledAt(LocalDateTime.now().plusMinutes(5));
        row.setCreatedBy(7L);
        return row;
    }

    private ArticleDraft article() {
        ArticleDraft article = new ArticleDraft();
        article.setId(20L);
        article.setProjectId(30L);
        article.setTitle("title");
        return article;
    }

    private Project project() {
        Project project = new Project();
        project.setId(30L);
        project.setBrandId(50L);
        return project;
    }

    private SelfMediaAccount account() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(40L);
        account.setBrandId(50L);
        account.setPlatform("wechat_mp");
        account.setStatus("active");
        return account;
    }

    private SelfMediaPlatformCapabilityContract contract(SelfMediaPlatformPublishChannel channel) {
        return new SelfMediaPlatformCapabilityContract(
                channel == SelfMediaPlatformPublishChannel.OFFICIAL_API ? "wechat_mp" : "toutiao",
                "平台",
                channel,
                SelfMediaPlatformScheduleMode.BACKEND_DELAYED,
                SelfMediaPlatformScheduleRules.defaults(),
                false,
                false,
                false,
                true
        );
    }

    private static class FakeOfficialApiAdapter implements AutoSelfMediaAdapter {
        @Override
        public String platform() {
            return "wechat_mp";
        }

        @Override
        public ValidationResult validate(ArticleDraft article, String contentMarkdown, TargetContext.SelfMediaTarget target) {
            return ValidationResult.pass();
        }

        @Override
        public SubmitResult submitToTarget(ArticleDraft article, String contentMarkdown, TargetContext.SelfMediaTarget target) {
            SubmitResult result = SubmitResult.success(200, "{\"ok\":true}", "{\"ok\":true}", null);
            result.setPlatformPublishId("pub-1");
            result.setReviewStatus(ReviewStatusResult.ReviewStatus.UNDER_REVIEW);
            return result;
        }

        @Override
        public ReviewStatusResult refreshReviewStatus(DistributionTask task, SelfMediaAccount account) {
            return ReviewStatusResult.unknown(null, null, true, null);
        }
    }

    private static class ThrowingReviewAdapter extends FakeOfficialApiAdapter {
        @Override
        public ReviewStatusResult refreshReviewStatus(DistributionTask task, SelfMediaAccount account) {
            throw new IllegalStateException("api unavailable");
        }
    }

    private static class PublishedReviewAdapter extends FakeOfficialApiAdapter {
        @Override
        public ReviewStatusResult refreshReviewStatus(DistributionTask task, SelfMediaAccount account) {
            return new ReviewStatusResult(
                    ReviewStatusResult.ReviewStatus.PUBLISHED,
                    "0",
                    null,
                    false,
                    "{\"publish_status\":0}",
                    "article-1",
                    "https://mp.weixin.qq.com/s/article-1"
            );
        }
    }

    private static class PreflightFailingAdapter extends FakeOfficialApiAdapter {
        private final BizException exception;

        private PreflightFailingAdapter(BizException exception) {
            this.exception = exception;
        }

        @Override
        public void preflightCredential(SelfMediaAccount account) {
            throw exception;
        }
    }

    private static class MaterialCheckingAdapter extends FakeOfficialApiAdapter {
        @Override
        public SubmitResult submitToTarget(ArticleDraft article, String contentMarkdown, TargetContext.SelfMediaTarget target) {
            assertEquals(88L, target.coverMaterialId());
            assertEquals(List.of(101L, 102L), target.imageMaterialIds());
            assertEquals(Boolean.TRUE, target.platformOptions().get("coverMaterialAutoSelected"));
            assertEquals(Boolean.TRUE, target.platformOptions().get("imageMaterialAutoSelected"));
            return super.submitToTarget(article, contentMarkdown, target);
        }
    }

    private static class SubjectMaterialCheckingAdapter extends FakeOfficialApiAdapter {
        @Override
        public SubmitResult submitToTarget(ArticleDraft article, String contentMarkdown, TargetContext.SelfMediaTarget target) {
            assertEquals(188L, target.coverMaterialId());
            assertEquals(List.of(201L), target.imageMaterialIds());
            assertEquals(60L, target.platformOptions().get("materialBrandId"));
            assertEquals("rewritten-subject", contentMarkdown);
            return super.submitToTarget(article, contentMarkdown, target);
        }
    }

    private static class SubmitFailingAdapter extends FakeOfficialApiAdapter {
        @Override
        public SubmitResult submitToTarget(ArticleDraft article, String contentMarkdown, TargetContext.SelfMediaTarget target) {
            SubmitResult result = SubmitResult.failure(
                    48001,
                    "{}",
                    "api unauthorized rid: draft-rid",
                    "当前公众号缺少新增草稿权限",
                    "WECHAT_API_UNAUTHORIZED",
                    false);
            result.setOperationStage("WECHAT_ADD_DRAFT");
            return result;
        }
    }
}
