package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.service.DouyinArticleImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@RequiredArgsConstructor
public class DouyinArticleImageJob {
    private final DouyinArticleImageService service;

    @Bean("douyinImageScheduler")
    public ThreadPoolTaskScheduler douyinImageScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("douyin-image-");
        return scheduler;
    }

    @Scheduled(fixedDelayString = "${geo.douyin-images.poll-ms:10000}", scheduler = "douyinImageScheduler")
    public void process() {
        service.processNext();
    }
}
