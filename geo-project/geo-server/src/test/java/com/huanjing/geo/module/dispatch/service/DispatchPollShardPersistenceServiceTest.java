package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.entity.PollBatchShardItem;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardItemMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.retention.service.PollRetentionSliceGuardService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchPollShardPersistenceServiceTest {

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(PollResult.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    PollResult.class
            );
        }
    }

    @Test
    void fillsDatabaseRequiredIdentityBeforePendingResultInsert() {
        Fixture fixture = new Fixture();
        PollResult result = result();
        result.setChannelCode(null);
        result.setTriggerType(null);
        when(fixture.resultMapper.selectOne(any())).thenReturn(null);

        fixture.service.ensurePollResult(result);

        verify(fixture.retentionSliceGuardService).lockAndRequireWritable(result);
        ArgumentCaptor<PollResult> captor = ArgumentCaptor.forClass(PollResult.class);
        verify(fixture.resultMapper).insert(captor.capture());
        assertEquals("doubao_web", captor.getValue().getChannelCode());
        assertEquals("SCHEDULED", captor.getValue().getTriggerType());
    }

    @Test
    void preservesManualChannelIdentityWhenFinalResultIsWritten() {
        Fixture fixture = new Fixture();
        PollResult result = result();
        result.setChannelCode(" doubao ");
        result.setTriggerType(" MANUAL ");
        result.setStatus("completed");
        PollBatchShardItem item = new PollBatchShardItem();
        item.setId(91L);
        when(fixture.resultMapper.selectOne(any())).thenReturn(null);

        fixture.service.upsertPollResultAndMarkItem(result, item);

        ArgumentCaptor<PollResult> captor = ArgumentCaptor.forClass(PollResult.class);
        verify(fixture.resultMapper).insert(captor.capture());
        assertEquals("doubao", captor.getValue().getChannelCode());
        assertEquals("MANUAL", captor.getValue().getTriggerType());
        assertEquals("completed", item.getStatus());
        verify(fixture.itemMapper).updateById(item);
    }

    @Test
    void rejectsPendingResultWriteAfterRetentionSliceWasPurged() {
        Fixture fixture = new Fixture();
        PollResult result = result();
        doThrow(new BizException(409, "Poll retention slice was already purged"))
                .when(fixture.retentionSliceGuardService)
                .lockAndRequireWritable(result);

        BizException error = assertThrows(BizException.class, () -> fixture.service.ensurePollResult(result));

        assertEquals(409, error.getCode());
        verify(fixture.resultMapper, never()).insert(any());
    }

    private static PollResult result() {
        PollResult result = new PollResult();
        result.setProjectId(100L);
        result.setPlatformId(55L);
        result.setPlatformCode("doubao_web");
        result.setKeywordResultId(200L);
        result.setKeywordTextSnapshot("测试问题");
        result.setBatchDate(LocalDate.of(2026, 7, 16));
        result.setBatchNo(1_000_000);
        result.setQuestionTier("A");
        return result;
    }

    private static final class Fixture {
        private final PollBatchShardMapper shardMapper = mock(PollBatchShardMapper.class);
        private final PollBatchShardItemMapper itemMapper = mock(PollBatchShardItemMapper.class);
        private final PollResultMapper resultMapper = mock(PollResultMapper.class);
        private final PollRetentionSliceGuardService retentionSliceGuardService =
                mock(PollRetentionSliceGuardService.class);
        private final DispatchPollShardPersistenceService service =
                new DispatchPollShardPersistenceService(
                        shardMapper,
                        itemMapper,
                        resultMapper,
                        retentionSliceGuardService
                );
    }
}
