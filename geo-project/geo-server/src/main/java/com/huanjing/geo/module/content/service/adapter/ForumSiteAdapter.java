package com.huanjing.geo.module.content.service.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.service.render.MarkdownToHtmlRenderer;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ForumSiteAdapter implements SiteAdapter {

    public static final String PLATFORM = "forum_site";
    public static final String INTEGRATION_METHOD = "forum_playwright";

    private final ObjectMapper objectMapper;
    private final MarkdownToHtmlRenderer markdownRenderer;
    private final PlatformCredentialService platformCredentialService;
    private final ForumBrowserPublisher browserPublisher;

    @Override
    public boolean supports(String integrationMethod) {
        return INTEGRATION_METHOD.equalsIgnoreCase(integrationMethod);
    }

    @Override
    public boolean supportsPlatform(String platform) {
        return PLATFORM.equalsIgnoreCase(platform);
    }

    @Override
    public ValidationResult validate(ArticleDraft article, String contentMarkdown, PublishSite site) {
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();
        if (article == null || !StringUtils.hasText(article.getTitle())) {
            errors.add("文章标题不能为空");
        }
        if (!StringUtils.hasText(contentMarkdown)) {
            errors.add("文章正文不能为空");
        }
        if (site == null) {
            errors.add("论坛站点不能为空");
            return ValidationResult.fail(errors);
        }
        ForumPublishProfile profile = parseProfile(site, errors);
        if (profile != null) {
            requireText(profile.getLoginUrl(), "论坛登录页地址不能为空", errors);
            requireText(profile.getPostUrl(), "论坛发帖页地址不能为空", errors);
            requireText(profile.getSelectors().getUsername(), "论坛账号输入框选择器不能为空", errors);
            requireText(profile.getSelectors().getPassword(), "论坛密码输入框选择器不能为空", errors);
            requireText(profile.getSelectors().getLoginSubmit(), "论坛登录按钮选择器不能为空", errors);
            requireText(profile.getSelectors().getTitle(), "论坛标题输入框选择器不能为空", errors);
            requireText(profile.getSelectors().getEditor(), "论坛正文编辑器选择器不能为空", errors);
            requireText(profile.getSelectors().getSubmit(), "论坛提交按钮选择器不能为空", errors);
        }
        if (!StringUtils.hasText(resolveCredential(site))) {
            errors.add("论坛登录信息不能为空");
        }
        return errors.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(errors);
    }

    @Override
    public SubmitResult submit(ArticleDraft article, String contentMarkdown, PublishSite site) {
        ValidationResult validation = validate(article, contentMarkdown, site);
        if (!validation.isPassed()) {
            return SubmitResult.failure(400, null, null, String.join("; ", validation.getErrors()),
                    FailureKind.VALIDATION, false);
        }
        try {
            ForumPublishProfile profile = objectMapper.readValue(site.getContentConstraints(), ForumPublishProfile.class);
            ForumCredential credential = objectMapper.readValue(resolveCredential(site), ForumCredential.class);
            ForumPublishPayload payload = new ForumPublishPayload(
                    article.getId(),
                    article.getProjectId(),
                    article.getTitle(),
                    contentMarkdown,
                    markdownRenderer.render(contentMarkdown),
                    article.getCategory(),
                    parseTags(article.getTagsJson())
            );
            return browserPublisher.publish(profile, credential, payload);
        } catch (Exception ex) {
            return SubmitResult.failure(statusCode(ex), null, null, safeMessage(ex), classifyFailure(ex), false);
        }
    }

    @Override
    public String parsePublishedUrl(String responseBody, PublishSite site) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            return objectMapper.readTree(responseBody).path("publishedUrl").asText(null);
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public SubmitResult submitToTarget(ArticleDraft article, String contentMarkdown, TargetContext target) {
        TargetContext.ForumSiteTarget forumTarget = requireTarget(target);
        Project project = forumTarget.project();
        SubmitResult result = submit(article, contentMarkdown, forumTarget.site());
        if (project != null && result.getRequestPayload() != null) {
            result.setRequestPayload(appendProjectContext(result.getRequestPayload(), project));
        }
        return result;
    }

    private TargetContext.ForumSiteTarget requireTarget(TargetContext target) {
        if (!(target instanceof TargetContext.ForumSiteTarget forumTarget)) {
            throw new IllegalArgumentException("ForumSiteAdapter requires ForumSiteTarget");
        }
        return forumTarget;
    }

    private ForumPublishProfile parseProfile(PublishSite site, List<String> errors) {
        if (!StringUtils.hasText(site.getContentConstraints())) {
            errors.add("论坛发布配置不能为空");
            return null;
        }
        try {
            ForumPublishProfile profile = objectMapper.readValue(site.getContentConstraints(), ForumPublishProfile.class);
            if (profile.getSelectors() == null) {
                profile.setSelectors(new ForumPublishProfile.Selectors());
            }
            return profile;
        } catch (Exception ex) {
            errors.add("论坛发布配置不是合法 JSON");
            return null;
        }
    }

    private String resolveCredential(PublishSite site) {
        if (site == null) {
            return null;
        }
        return platformCredentialService.resolveCredential(site.getCredentialRef(), site.getApiCredentialEncrypted());
    }

    private List<String> parseTags(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return List.of();
        }
        try {
            List<String> tags = objectMapper.readValue(tagsJson, new TypeReference<>() {});
            return tags == null ? List.of() : tags.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList();
        } catch (Exception ex) {
            return List.of(tagsJson.trim());
        }
    }

    private String appendProjectContext(String requestPayload, Project project) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(requestPayload);
            root.put("projectName", project.getProjectName());
            root.put("brandName", project.getBrandName());
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            return requestPayload;
        }
    }

    private void requireText(String value, String message, List<String> errors) {
        if (!StringUtils.hasText(value)) {
            errors.add(message);
        }
    }

    private String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private int statusCode(Exception ex) {
        return ex instanceof BizException bizException ? bizException.getCode() : 500;
    }

    private String classifyFailure(Exception ex) {
        if (ex instanceof BizException bizException && (bizException.getCode() == 401 || bizException.getCode() == 403)) {
            return FailureKind.AUTH_EXPIRED;
        }
        String message = safeMessage(ex).toLowerCase();
        if (message.contains("auth") || message.contains("login") || message.contains("cookie")
                || message.contains("认证") || message.contains("登录")) {
            return FailureKind.AUTH_EXPIRED;
        }
        return FailureKind.UNKNOWN;
    }
}
