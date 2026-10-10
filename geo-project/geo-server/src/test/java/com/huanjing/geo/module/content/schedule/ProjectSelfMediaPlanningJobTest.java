package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.service.ProjectSelfMediaPlanningService;
import org.junit.jupiter.api.Test;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectSelfMediaPlanningJobTest {
    @Test
    void schedulerOnlyDispatchesAndDoesNotQueueDuplicateWorkersWhileBusy() {
        var service = mock(ProjectSelfMediaPlanningService.class);
        var runnable = new AtomicReference<Runnable>();
        Executor executor = runnable::set;
        var job = new ProjectSelfMediaPlanningJob(service, executor);
        job.processPending();
        var first = runnable.get();
        assertNotNull(first);
        job.processPending();
        assertSame(first, runnable.get());
        verifyNoInteractions(service);
        first.run();
        verify(service, times(1)).processNext();
        job.processPending();
        assertNotSame(first, runnable.get());
    }

    @Test
    void executorRejectionDoesNotPreventTheNextPollFromRecoveringPersistedWork() {
        var service = mock(ProjectSelfMediaPlanningService.class);
        var executor = mock(Executor.class);
        doThrow(new RejectedExecutionException("busy")).doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        var job = new ProjectSelfMediaPlanningJob(service, executor);
        assertDoesNotThrow(job::processPending);
        verifyNoInteractions(service);
        job.processPending();
        verify(service).processNext();
    }
}
