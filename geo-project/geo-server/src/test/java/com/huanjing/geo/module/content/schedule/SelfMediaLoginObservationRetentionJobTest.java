package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.mapper.SelfMediaLoginObservationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfMediaLoginObservationRetentionJobTest {

    @Test
    void cleanupDeletesInBoundedBatches() {
        SelfMediaLoginObservationMapper mapper = mock(SelfMediaLoginObservationMapper.class);
        when(mapper.deleteExpiredBatch(any(LocalDateTime.class), eq(1000)))
                .thenReturn(1000, 1000, 15);
        SelfMediaLoginObservationRetentionJob job = new SelfMediaLoginObservationRetentionJob(mapper);
        ReflectionTestUtils.setField(job, "retentionDays", 180);
        ReflectionTestUtils.setField(job, "batchSize", 1000);
        ReflectionTestUtils.setField(job, "maxBatches", 20);

        job.cleanup();

        verify(mapper, times(3)).deleteExpiredBatch(any(LocalDateTime.class), eq(1000));
    }
}
