package com.huanjing.geo.module.content.service.adapter;

import lombok.Data;

import java.util.List;

@Data
public class ForumPublishProfile {
    private String loginUrl;
    private String postUrl;
    private String boardId;
    private Integer timeoutMs = 30000;
    private Integer acquireTimeoutMs = 30000;
    private Boolean headless = true;
    private Boolean blockHeavyResources = true;
    private List<String> blockedResourceTypes = List.of("image", "media", "font");
    private String contentMode = "html";
    private String publishedUrlRegex;
    private String canonicalSelector;
    private String publishedTitleSelector;
    private String publishedContentSelector = "[id^=\"postmessage_\"]";
    private String moderationSelector;
    private String moderationPendingText = "审核中";
    private String moderationPolicy = "none";
    private Integer moderationGraceHours = 24;
    private Selectors selectors = new Selectors();

    @Data
    public static class Selectors {
        private String username;
        private String password;
        private String loginSubmit;
        private String title;
        private String editor;
        private String editorFrame;
        private String category;
        private String tags;
        private String submit;
        private String publishedUrl;
        private List<String> loggedInSignals;
    }
}
