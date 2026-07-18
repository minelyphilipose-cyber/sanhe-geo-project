package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.dispatch.dto.ManualQuestionPollBatchView;
import com.huanjing.geo.module.dispatch.dto.ManualQuestionPollRequest;
import com.huanjing.geo.module.dispatch.entity.PollBatch;
import com.huanjing.geo.module.dispatch.entity.PollBatchShard;
import com.huanjing.geo.module.dispatch.entity.PollCitation;
import com.huanjing.geo.module.dispatch.entity.PollInvocationAttempt;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.entity.PollSearchSource;
import com.huanjing.geo.module.dispatch.mapper.PollBatchMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardMapper;
import com.huanjing.geo.module.dispatch.mapper.PollCitationMapper;
import com.huanjing.geo.module.dispatch.mapper.PollInvocationAttemptMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.dispatch.mapper.PollSearchSourceMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ManualQuestionPollServiceTest {

    private static final String REQUEST_ID = "7ea365c2-0b9a-4cc6-8e11-6d1d89927f0f";

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PollBatch.class);
        initTableInfo(PollResult.class);
        initTableInfo(PollSearchSource.class);
        initTableInfo(PollCitation.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void startsManualBatchInReservedNumberRange() {
        Fixture fixture = new Fixture();
        ManualQuestionPollRequest request = request();
        Project project = project();
        AiPlatformConfig platform = platform();
        PollBatch planned = batch(501L, sha256("100|A|55|1"));

        when(fixture.currentUserService.requireCurrentUser()).thenReturn(operator());
        when(fixture.projectMapper.selectById(100L)).thenReturn(project);
        when(fixture.batchMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(fixture.batchMapper.lockProjectForManualPoll(100L)).thenReturn(100L);
        when(fixture.platformMapper.selectBatchIds(List.of(55L))).thenReturn(List.of(platform));
        when(fixture.batchMapper.selectMaxBatchNo(eq(100L), any(), eq("A"))).thenReturn(4);
        when(fixture.planningService.planManualProjectTierPoll(
                eq(project),
                any(),
                eq("A"),
                eq(1_000_000),
                anyList(),
                eq(1),
                eq(9L),
                eq(REQUEST_ID),
                anyString()
        )).thenReturn(planned);
        when(fixture.batchMapper.selectById(501L)).thenReturn(planned);
        when(fixture.shardMapper.selectByBatchId(501L)).thenReturn(List.of());
        when(fixture.resultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        ManualQuestionPollBatchView view = fixture.service.start(request);

        assertEquals(501L, view.getBatchId());
        assertEquals("MANUAL", view.getTriggerType());
        verify(fixture.currentUserService).ensurePermission("dispatch.question_poll.manual");
        verify(fixture.planningService).planManualProjectTierPoll(
                eq(project),
                any(),
                eq("A"),
                eq(1_000_000),
                anyList(),
                eq(1),
                eq(9L),
                eq(REQUEST_ID),
                eq(sha256("100|A|55|1"))
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void idempotentReplayReturnsOriginalBatchBeforeReadingMutablePlatformConfig() {
        Fixture fixture = new Fixture();
        PollBatch existing = batch(501L, sha256("100|A|55|1"));

        when(fixture.currentUserService.requireCurrentUser()).thenReturn(operator());
        when(fixture.batchMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(fixture.batchMapper.selectById(501L)).thenReturn(existing);
        when(fixture.projectMapper.selectById(100L)).thenReturn(project());
        when(fixture.shardMapper.selectByBatchId(501L)).thenReturn(List.of());
        when(fixture.resultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        ManualQuestionPollBatchView view = fixture.service.start(request());

        assertEquals(501L, view.getBatchId());
        verifyNoInteractions(fixture.platformMapper);
        verifyNoInteractions(fixture.planningService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsRecentManualBatchesCreatedByCurrentOperator() {
        Fixture fixture = new Fixture();
        PollBatch latest = batch(502L, sha256("100|A|55|1"));
        PollBatch previous = batch(501L, sha256("100|A|55|1"));
        when(fixture.currentUserService.requireCurrentUser()).thenReturn(operator());
        when(fixture.batchMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(latest, previous));
        when(fixture.projectMapper.selectById(100L)).thenReturn(project());
        when(fixture.shardMapper.selectByBatchId(any())).thenReturn(List.of());
        when(fixture.resultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<ManualQuestionPollBatchView> history = fixture.service.listRecent(20);

        assertEquals(List.of(502L, 501L), history.stream()
                .map(ManualQuestionPollBatchView::getBatchId)
                .toList());
        verify(fixture.currentUserService).ensurePermission("dispatch.question_poll.manual");
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsQuestionLevelAnswerSourcesAndCitationsForBatchDetail() {
        Fixture fixture = new Fixture();
        PollBatch batch = batch(501L, sha256("100|A|55|1"));
        batch.setStatus("finished");
        PollBatchShard shard = new PollBatchShard();
        shard.setPlatformId(55L);
        shard.setPlatformCode("doubao_web");
        shard.setChannelCode("doubao");
        shard.setPlatformName("豆包联网问答");
        shard.setStatus("completed");
        shard.setExpectedCount(1);
        shard.setCompletedCount(1);
        shard.setFailedCount(0);

        PollResult result = new PollResult();
        result.setId(700L);
        result.setBatchId(501L);
        result.setPlatformId(55L);
        result.setPlatformCode("doubao_web");
        result.setKeywordTextSnapshot("阜阳环境好的餐厅推荐");
        result.setStatus("completed");
        result.setEffectiveAttemptId(800L);
        result.setSearchStatus("TRIGGERED");
        result.setSearchTriggered(true);
        result.setExecutionFinalized(true);
        result.setConfirmedCitationExposure(true);

        PollInvocationAttempt attempt = new PollInvocationAttempt();
        attempt.setId(800L);
        attempt.setAnswer("推荐海上1912文化餐厅。");
        attempt.setLatencyMs(1200L);

        PollSearchSource source = new PollSearchSource();
        source.setId(900L);
        source.setAttemptId(800L);
        source.setRankNo(1);
        source.setTitle("餐厅推荐来源");
        source.setNormalizedUrl("https://example.com/restaurant");
        source.setDomain("example.com");

        PollCitation citation = new PollCitation();
        citation.setAttemptId(800L);
        citation.setSourceId(900L);
        citation.setCitationIndex(1);
        citation.setConfidence("CONFIRMED");

        when(fixture.currentUserService.requireCurrentUser()).thenReturn(operator());
        when(fixture.batchMapper.selectById(501L)).thenReturn(batch);
        when(fixture.projectMapper.selectById(100L)).thenReturn(project());
        when(fixture.shardMapper.selectByBatchId(501L)).thenReturn(List.of(shard));
        when(fixture.resultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(result));
        when(fixture.attemptMapper.selectBatchIds(List.of(800L))).thenReturn(List.of(attempt));
        when(fixture.sourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(source));
        when(fixture.citationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(citation));

        ManualQuestionPollBatchView view = fixture.service.get(501L);

        assertEquals(1, view.getResults().size());
        assertEquals("豆包联网问答", view.getResults().get(0).getPlatformName());
        assertEquals("推荐海上1912文化餐厅。", view.getResults().get(0).getAnswer());
        assertEquals("https://example.com/restaurant", view.getResults().get(0).getSources().get(0).getUrl());
        assertEquals("CONFIRMED", view.getResults().get(0).getCitations().get(0).getConfidence());
    }

    private static ManualQuestionPollRequest request() {
        ManualQuestionPollRequest request = new ManualQuestionPollRequest();
        request.setProjectId(100L);
        request.setQuestionTier("A");
        request.setPlatformIds(List.of(55L));
        request.setQuestionLimit(1);
        request.setClientRequestId(REQUEST_ID);
        return request;
    }

    private static SysUser operator() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setRole("manager");
        return user;
    }

    private static Project project() {
        Project project = new Project();
        project.setId(100L);
        project.setProjectName("手工轮询测试项目");
        project.setStatus("active");
        project.setActivatedAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        return project;
    }

    private static AiPlatformConfig platform() {
        AiPlatformConfig platform = new AiPlatformConfig();
        platform.setId(55L);
        platform.setPlatformCode("doubao_web");
        platform.setChannelCode("doubao");
        platform.setPlatformName("豆包联网问答");
        platform.setUsageScene("QUESTION_POLL_WEB");
        platform.setEnabled(true);
        platform.setEnabledForQuestionPoll(true);
        return platform;
    }

    private static PollBatch batch(Long id, String fingerprint) {
        PollBatch batch = new PollBatch();
        batch.setId(id);
        batch.setProjectId(100L);
        batch.setBatchNo(1_000_000);
        batch.setQuestionTier("A");
        batch.setTriggerType("MANUAL");
        batch.setStatus("ready");
        batch.setClientRequestId(REQUEST_ID);
        batch.setRequestFingerprint(fingerprint);
        batch.setManualQuestionLimit(1);
        batch.setTotalPlatformCount(1);
        batch.setTotalShardCount(1);
        batch.setTriggeredAt(LocalDateTime.of(2026, 7, 16, 10, 0));
        return batch;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }

    private static final class Fixture {
        private final PollBatchMapper batchMapper = mock(PollBatchMapper.class);
        private final PollBatchShardMapper shardMapper = mock(PollBatchShardMapper.class);
        private final PollResultMapper resultMapper = mock(PollResultMapper.class);
        private final PollInvocationAttemptMapper attemptMapper = mock(PollInvocationAttemptMapper.class);
        private final PollSearchSourceMapper sourceMapper = mock(PollSearchSourceMapper.class);
        private final PollCitationMapper citationMapper = mock(PollCitationMapper.class);
        private final ProjectMapper projectMapper = mock(ProjectMapper.class);
        private final AiPlatformConfigMapper platformMapper = mock(AiPlatformConfigMapper.class);
        private final DispatchQuestionPollPlanningService planningService =
                mock(DispatchQuestionPollPlanningService.class);
        private final CurrentUserService currentUserService = mock(CurrentUserService.class);
        private final InternalScopeService internalScopeService = mock(InternalScopeService.class);
        private final ActivityLogService activityLogService = mock(ActivityLogService.class);
        private final ManualQuestionPollService service = new ManualQuestionPollService(
                batchMapper,
                shardMapper,
                resultMapper,
                attemptMapper,
                sourceMapper,
                citationMapper,
                projectMapper,
                platformMapper,
                planningService,
                currentUserService,
                internalScopeService,
                activityLogService
        );
    }
}
