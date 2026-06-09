package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.module.content.douyin.DouyinWebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "DouyinWebhook")
@RestController
@RequestMapping("/api/douyin/open-platform")
@RequiredArgsConstructor
public class DouyinWebhookController {
    private final DouyinWebhookService webhookService;

    @PostMapping(value = "/webhooks", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String receiveWebhook(@RequestBody(required = false) String rawBody) {
        try {
            return webhookService.handleWebhook(rawBody);
        } catch (Exception ex) {
            log.error("Douyin webhook callback failed rawBody={}", rawBody, ex);
            return "success";
        }
    }
}
