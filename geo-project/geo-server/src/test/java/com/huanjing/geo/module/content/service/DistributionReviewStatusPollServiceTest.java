package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.service.adapter.AutoSelfMediaAdapter;
import com.huanjing.geo.module.content.service.adapter.ReviewStatusResult;
import com.huanjing.geo.module.content.service.adapter.SubmitResult;
import com.huanjing.geo.module.content.service.adapter.ValidationResult;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributionReviewStatusPollServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DistributionTask.class);
    }

    private final DistributionTaskMapper taskMapper = mock(DistributionTaskMapper.class);
    private final SelfMediaAccountMapper accountMapper = mock(SelfMediaAccountMapper.class);
    private final StubAdapter adapter = new StubAdapter();
    private final DistributionReviewStatusPollService service = new DistributionReviewStatusPollService(
            taskMapper,
            accountMapper,
            List.of(adapter),
            new ObjectMapper()
    );

    @Test
    void refreshTask_publishedWritesTerminalStatusAndPlatformArticleId() {
        DistributionTask task = task(2);
        SelfMediaAccount account = account();
        adapter.result = new ReviewStatusResult(
                ReviewStatusResult.ReviewStatus.PUBLISHED,
                "0",
                null,
                false,
                "{\"publish_status\":0,\"article_id\":\"article-1\"}",
                "article-1",
                "https://mp.weixin.qq.com/s/article-1"
        );
        when(accountMapper.selectById(40L)).thenReturn(account);
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(taskMapper.claimReviewTask(eq(100L), any(), any())).thenReturn(1);

        service.refreshTask(task);

        LambdaUpdateWrapper<DistributionTask> wrapper = capturedUpdate();
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains("published", "article-1", "https://mp.weixin.qq.com/s/article-1", 3);
        assertThat(wrapper.getSqlSet()).contains("platform_article_id", "published_url", "finished_at");
    }

    @Test
    void refreshTask_unknownDoesNotMarkTaskFailedAndKeepsCountingForward() {
        DistributionTask task = task(4);
        SelfMediaAccount account = account();
        adapter.result = ReviewStatusResult.unknown("temporary", "not ready", true, "{\"status\":\"temporary\"}");
        when(accountMapper.selectById(40L)).thenReturn(account);
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(taskMapper.claimReviewTask(eq(100L), any(), any())).thenReturn(1);

        service.refreshTask(task);

        LambdaUpdateWrapper<DistributionTask> wrapper = capturedUpdate();
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains("submitted", "unknown", 5)
                .doesNotContain("failed");
        assertThat(wrapper.getSqlSet()).contains("next_review_check_at");
    }

    @Test
    void pollDueTasksSkipsWhenClaimFails() {
        DistributionTask task = task(0);
        when(taskMapper.selectDueReviewTasks(any(), eq(50))).thenReturn(List.of(task));
        when(taskMapper.claimReviewTask(eq(100L), any(), any())).thenReturn(0);

        int processed = service.pollDueTasks();

        assertThat(processed).isZero();
    }

    @Test
    void refreshTaskSkipsWhenManualRefreshIsInCooldown() {
        DistributionTask task = task(1);
        task.setReviewCheckedAt(LocalDateTime.now().minusSeconds(10));
        when(taskMapper.selectById(100L)).thenReturn(task);

        DistributionTask refreshed = service.refreshTask(task);

        assertThat(refreshed).isSameAs(task);
        assertThat(adapter.calls).isZero();
        verify(taskMapper, never()).claimReviewTask(eq(100L), any(), any());
    }

    @Test
    void refreshTaskSkipsWhenClaimFails() {
        DistributionTask task = task(1);
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(taskMapper.claimReviewTask(eq(100L), any(), any())).thenReturn(0);

        service.refreshTask(task);

        assertThat(adapter.calls).isZero();
        verify(taskMapper, never()).update(eq(null), any());
    }

    @Test
    void refreshTaskSkipsTerminalPublishedTask() {
        DistributionTask task = task(1);
        task.setStatus("published");
        task.setReviewStatus("published");
        when(taskMapper.selectById(100L)).thenReturn(task);

        service.refreshTask(task);

        assertThat(adapter.calls).isZero();
        verify(taskMapper, never()).claimReviewTask(eq(100L), any(), any());
    }

    private LambdaUpdateWrapper<DistributionTask> capturedUpdate() {
        ArgumentCaptor<LambdaUpdateWrapper<DistributionTask>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(taskMapper).update(eq(null), captor.capture());
        return captor.getValue();
    }

    private DistributionTask task(int reviewCheckCount) {
        DistributionTask task = new DistributionTask();
        task.setId(100L);
        task.setTargetKind("mp_account");
        task.setDispatchMode("AUTO");
        task.setStatus("submitted");
        task.setReviewStatus("under_review");
        task.setSelfMediaAccountId(40L);
        task.setPlatformPublishId("publish-1");
        task.setSubmittedAt(LocalDateTime.now().minusMinutes(5));
        task.setCreatedAt(LocalDateTime.now().minusMinutes(6));
        task.setReviewCheckCount(reviewCheckCount);
        return task;
    }

    private SelfMediaAccount account() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(40L);
        account.setPlatform("wechat_mp");
        return account;
    }

    private static class StubAdapter implements AutoSelfMediaAdapter {
        private ReviewStatusResult result = ReviewStatusResult.unknown(null, null, true, null);
        private int calls;

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
            return SubmitResult.success(200, null, null, null);
        }

        @Override
        public ReviewStatusResult refreshReviewStatus(DistributionTask task, SelfMediaAccount account) {
            calls++;
            return result;
        }
    }
}
