package com.huanjing.geo.module.content.service.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.service.render.MarkdownToBbcodeRenderer;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DiscuzHttpForumAdapter implements SiteAdapter {

    public static final String PLATFORM = "forum_site";
    public static final String INTEGRATION_METHOD = "discuz_http";

    private final ObjectMapper objectMapper;
    private final PlatformCredentialService platformCredentialService;
    private final MarkdownToBbcodeRenderer bbcodeRenderer;
    private final DiscuzHttpForumPublisher publisher;

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
        List<String> errors = new ArrayList<>();
        if (article == null || !StringUtils.hasText(article.getTitle())) {
            errors.add("文章标题不能为空");
        } else if (article.getTitle().trim().length() < 8 || article.getTitle().trim().length() > 80) {
            errors.add("Discuz 标题长度需为 8-80 个字符");
        }
        if (!StringUtils.hasText(contentMarkdown)) {
            errors.add("文章正文不能为空");
        }
        if (site == null) {
            errors.add("论坛站点不能为空");
            return ValidationResult.fail(errors);
        }
        DiscuzForumProfile profile = parseProfile(site, errors);
        if (profile != null) {
            validateBoards(profile, errors);
            if (!profile.hasBoards() && (profile.getFid() == null || profile.getFid() <= 0)) {
                errors.add("Discuz 版块 fid 不能为空");
            }
            if (!StringUtils.hasText(profile.getBaseUrl()) && !StringUtils.hasText(site.getApiEndpoint())) {
                errors.add("Discuz 站点地址或发帖接口不能为空");
            }
        }
        if (!hasUsableCredential(site, errors)) {
            errors.add("论坛登录信息不能为空");
        }
        return errors.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(errors);
    }

    @Override
    public SubmitResult submit(ArticleDraft article, String contentMarkdown, PublishSite site) {
        return submit(article, contentMarkdown, site, null);
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
        SubmitResult result = submit(article, contentMarkdown, forumTarget.site(), forumTarget.forumFid());
        if (project != null && result.getRequestPayload() != null) {
            result.setRequestPayload(appendProjectContext(result.getRequestPayload(), project));
        }
        return result;
    }

    private SubmitResult submit(ArticleDraft article, String contentMarkdown, PublishSite site, Integer requestedFid) {
        ValidationResult validation = validate(article, contentMarkdown, site);
        if (!validation.isPassed()) {
            return SubmitResult.failure(400, null, null, String.join("; ", validation.getErrors()),
                    FailureKind.VALIDATION, false);
        }
        try {
            DiscuzForumProfile profile = resolveProfile(site, requestedFid);
            ForumCredential credential = objectMapper.readValue(resolveCredential(site), ForumCredential.class);
            String bbcode = bbcodeRenderer.render(contentMarkdown);
            ForumPublishPayload payload = new ForumPublishPayload(
                    article.getId(),
                    article.getProjectId(),
                    article.getTitle(),
                    contentMarkdown,
                    bbcode,
                    article.getCategory(),
                    parseTags(article.getTagsJson())
            );
            return publisher.publish(site.getId(), profile, credential, payload, bbcode);
        } catch (Exception ex) {
            return SubmitResult.failure(statusCode(ex), null, null, safeMessage(ex), classifyFailure(ex), retryable(ex));
        }
    }

    private DiscuzForumProfile parseProfile(PublishSite site, List<String> errors) {
        try {
            DiscuzForumProfile profile = StringUtils.hasText(site.getContentConstraints())
                    ? objectMapper.readValue(site.getContentConstraints(), DiscuzForumProfile.class)
                    : new DiscuzForumProfile();
            if (!StringUtils.hasText(profile.getBaseUrl())) {
                profile.setBaseUrl(resolveBaseUrl(site));
            }
            if (!StringUtils.hasText(profile.getPostPageUrl()) && StringUtils.hasText(site.getApiEndpoint())) {
                profile.setPostPageUrl(site.getApiEndpoint().trim());
            }
            return profile;
        } catch (Exception ex) {
            errors.add("Discuz 发布配置不是合法 JSON");
            return null;
        }
    }

    private DiscuzForumProfile resolveProfile(PublishSite site, Integer requestedFid) {
        List<String> errors = new ArrayList<>();
        DiscuzForumProfile profile = parseProfile(site, errors);
        if (profile == null) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        DiscuzForumProfile.Board board = profile.resolveBoard(requestedFid)
                .orElseThrow(() -> new IllegalArgumentException(requestedFid == null
                        ? "Discuz 至少需要一个启用版块"
                        : "Discuz 版块 fid 不存在或未启用：" + requestedFid));
        Integer fid = board.getFid() == null ? profile.getFid() : board.getFid();
        if (fid == null || fid <= 0) {
            throw new IllegalArgumentException("Discuz 版块 fid 不能为空");
        }
        return profile.withFid(fid);
    }

    private void validateBoards(DiscuzForumProfile profile, List<String> errors) {
        if (!profile.hasBoards()) {
            return;
        }
        long defaultCount = profile.getBoards().stream()
                .filter(java.util.Objects::nonNull)
                .filter(DiscuzForumProfile.Board::isEnabled)
                .filter(board -> Boolean.TRUE.equals(board.getDefaultBoard()))
                .count();
        if (defaultCount > 1) {
            errors.add("Discuz 只能设置一个启用的默认版块");
        }
        boolean hasEnabled = false;
        java.util.Set<Integer> fids = new java.util.HashSet<>();
        for (DiscuzForumProfile.Board board : profile.getBoards()) {
            if (board == null) {
                continue;
            }
            if (board.getFid() == null || board.getFid() <= 0) {
                errors.add("Discuz 版块 fid 必须为正整数");
                continue;
            }
            if (!fids.add(board.getFid())) {
                errors.add("Discuz 版块 fid 重复：" + board.getFid());
            }
            if (board.isEnabled()) {
                hasEnabled = true;
            }
        }
        if (!hasEnabled) {
            errors.add("Discuz 至少需要一个启用版块");
        }
    }

    private String resolveBaseUrl(PublishSite site) {
        if (StringUtils.hasText(site.getApiEndpoint())) {
            String endpoint = site.getApiEndpoint().trim();
            int index = endpoint.indexOf("/forum.php");
            if (index > 0) {
                return endpoint.substring(0, index + 1);
            }
            return endpoint;
        }
        if (StringUtils.hasText(site.getDomain())) {
            String domain = site.getDomain().trim();
            if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
                domain = "https://" + domain;
            }
            return domain.endsWith("/") ? domain : domain + "/";
        }
        return null;
    }

    private TargetContext.ForumSiteTarget requireTarget(TargetContext target) {
        if (!(target instanceof TargetContext.ForumSiteTarget forumTarget)) {
            throw new IllegalArgumentException("DiscuzHttpForumAdapter requires ForumSiteTarget");
        }
        return forumTarget;
    }

    private String resolveCredential(PublishSite site) {
        if (site == null) {
            return null;
        }
        return platformCredentialService.resolveCredential(site.getCredentialRef(), site.getApiCredentialEncrypted());
    }

    private boolean hasUsableCredential(PublishSite site, List<String> errors) {
        String raw = resolveCredential(site);
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        try {
            ForumCredential credential = objectMapper.readValue(raw, ForumCredential.class);
            return credential.hasUsableCredential();
        } catch (Exception ex) {
            errors.add("论坛登录信息不是合法 JSON");
            return false;
        }
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

    private String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private int statusCode(Exception ex) {
        return ex instanceof BizException bizException ? bizException.getCode() : 500;
    }

    private boolean retryable(Exception ex) {
        String failureKind = classifyFailure(ex);
        return !FailureKind.AUTH.equals(failureKind) && !FailureKind.AUTH_EXPIRED.equals(failureKind);
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
