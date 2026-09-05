package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.config.SchedulingConfig;
import com.huanjing.geo.module.content.schedule.DouyinArticleImageJob;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.TaskScheduler;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class DouyinImageSchedulerTest {
    @Test
    void retainsDefaultSchedulerForExistingServicesAndIsolatesImageWorker() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DouyinArticleImageService.class, () -> mock(DouyinArticleImageService.class));
            context.register(SchedulingConfig.class, DouyinArticleImageJob.class);
            context.refresh();
            assertSame(context.getBean("taskScheduler"), context.getBean(TaskScheduler.class));
            assertNotSame(context.getBean("taskScheduler"), context.getBean("douyinImageScheduler"));
        }
    }
}
