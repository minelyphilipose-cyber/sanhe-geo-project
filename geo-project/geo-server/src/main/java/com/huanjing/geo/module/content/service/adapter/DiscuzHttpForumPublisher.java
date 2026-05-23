package com.huanjing.geo.module.content.service.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscuzHttpForumPublisher {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
    private static final Semaphore PUBLISH_SLOT = new Semaphore(1);
    private static final int POST_PAGE_NAVIGATION_ATTEMPTS = 2;

    private final ObjectMapper objectMapper;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Object browserLock = new Object();
    private Playwright playwright;
    private Browser browser;

    public SubmitResult publish(Long siteId,
                                DiscuzForumProfile profile,
                                ForumCredential credential,
                                ForumPublishPayload payload,
                                String bbcode) {
        long started = System.nanoTime();
        String requestPayload = requestPayload(profile, payload);
        ForumCredential.Account account = credential.pickAccount();
        boolean acquired = false;
        try {
            acquired = PUBLISH_SLOT.tryAcquire(requestTimeoutMs(profile), TimeUnit.MILLISECONDS);
            if (!acquired) {
                return SubmitResult.failure(429, requestPayload, null,
                        "forum publish queue is busy; retry later", FailureKind.RATE_LIMIT, true);
            }
            if (StringUtils.hasText(account.cookie())) {
                return publishWithBrowser(profile, account, payload, bbcode, requestPayload, started);
            }
            Session session = sessions.computeIfAbsent(sessionKey(siteId, account), ignored -> new Session(profile));
            synchronized (session) {
                session.ensureLoggedIn(profile, account);
                DiscuzPostForm form = session.loadPostForm(profile);
                HttpResponse<String> response = session.post(profile.postSubmitUri(), formBody(form, payload.title(), bbcode));
                if (isLoginPage(response.body())) {
                    session.reset();
                    session.ensureLoggedIn(profile, account);
                    form = session.loadPostForm(profile);
                    response = session.post(profile.postSubmitUri(), formBody(form, payload.title(), bbcode));
                }
                return toSubmitResult(profile, payload, requestPayload, response, started);
            }
        } catch (BizException ex) {
            String failureKind = ex.getCode() == 401 ? FailureKind.AUTH_EXPIRED : FailureKind.VALIDATION;
            return SubmitResult.failure(ex.getCode(), requestPayload, null, ex.getMessage(), failureKind, false);
        } catch (TimeoutError ex) {
            log.warn("discuz forum publish timeout articleId={} error={}", payload.articleId(), safeMessage(ex));
            return SubmitResult.failure(504, requestPayload, null, safeMessage(ex), FailureKind.NETWORK_ERROR, true);
        } catch (Exception ex) {
            log.warn("discuz forum publish failed articleId={} error={}", payload.articleId(), safeMessage(ex), ex);
            return SubmitResult.failure(500, requestPayload, null, safeMessage(ex), FailureKind.UNKNOWN, true);
        } finally {
            if (acquired) {
                PUBLISH_SLOT.release();
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        synchronized (browserLock) {
            if (browser != null) {
                browser.close();
                browser = null;
            }
            if (playwright != null) {
                playwright.close();
                playwright = null;
            }
        }
    }

    private SubmitResult publishWithBrowser(DiscuzForumProfile profile,
                                            ForumCredential.Account account,
                                            ForumPublishPayload payload,
                                            String bbcode,
                                            String requestPayload,
                                            long started) throws Exception {
        Browser chromium = browser();
        try (BrowserContext context = chromium.newContext(new Browser.NewContextOptions()
                .setUserAgent(resolveUserAgent(account))
                .setLocale("zh-CN"))) {
            routeHeavyResources(context);
            context.addCookies(toPlaywrightCookies(profile, account.cookie()));
            Page page = context.newPage();
            int timeoutMs = requestTimeoutMs(profile);
            page.setDefaultTimeout(timeoutMs);
            page.setDefaultNavigationTimeout(timeoutMs);
            openPostPage(page, profile, payload.articleId(), timeoutMs);
            if (!hasPostForm(page)) {
                throw new BizException(401, "平台网站登录 Cookie 已失效或发帖页被 WAF 拦截，请重新登录后更新该平台网站账号 Cookie");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate("""
                    async ({submitUrl, title, message}) => {
                      const formhash = document.querySelector('input[name=formhash]')?.value || '';
                      const posttime = document.querySelector('input[name=posttime]')?.value || Math.floor(Date.now() / 1000).toString();
                      const wysiwyg = document.querySelector('input[name=wysiwyg]')?.value || '1';
                      const params = new URLSearchParams();
                      params.set('formhash', formhash);
                      params.set('posttime', posttime);
                      params.set('wysiwyg', wysiwyg);
                      params.set('subject', title);
                      params.set('message', message);
                      params.set('topicsubmit', 'true');
                      params.set('save', '');
                      const response = await fetch(submitUrl, {
                        method: 'POST',
                        credentials: 'same-origin',
                        headers: {
                          'Content-Type': 'application/x-www-form-urlencoded',
                          'X-Requested-With': 'XMLHttpRequest'
                        },
                        body: params.toString()
                      });
                      return { status: response.status, url: response.url, body: await response.text() };
                    }
                    """, Map.of(
                    "submitUrl", profile.postSubmitUri().toString(),
                    "title", payload.title(),
                    "message", bbcode
            ));
            BrowserSubmitResponse response = new BrowserSubmitResponse(
                    ((Number) result.getOrDefault("status", 0)).intValue(),
                    String.valueOf(result.getOrDefault("url", profile.postSubmitUri().toString())),
                    String.valueOf(result.getOrDefault("body", ""))
            );
            return toSubmitResult(profile, payload, requestPayload, response, started);
        }
    }

    private void openPostPage(Page page, DiscuzForumProfile profile, Long articleId, int timeoutMs) {
        TimeoutError lastTimeout = null;
        String postPageUrl = profile.postPageUri().toString();
        for (int attempt = 1; attempt <= POST_PAGE_NAVIGATION_ATTEMPTS; attempt++) {
            try {
                page.navigate(postPageUrl, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout((double) timeoutMs));
                page.waitForTimeout(attempt == 1 ? 2000 : 4000);
                if (hasPostForm(page)) {
                    return;
                }
            } catch (TimeoutError ex) {
                lastTimeout = ex;
                log.warn("discuz post page navigation timeout articleId={} attempt={}/{} url={} error={}",
                        articleId, attempt, POST_PAGE_NAVIGATION_ATTEMPTS, postPageUrl, safeMessage(ex));
                if (hasPostForm(page)) {
                    return;
                }
            }
            if (attempt < POST_PAGE_NAVIGATION_ATTEMPTS) {
                page.waitForTimeout(1000);
            }
        }
        if (lastTimeout != null) {
            throw lastTimeout;
        }
    }

    private boolean hasPostForm(Page page) {
        return page.locator("input[name='formhash']").count() > 0
                && page.locator("input[name='subject']").count() > 0;
    }

    private void routeHeavyResources(BrowserContext context) {
        context.route("**/*", route -> {
            Request request = route.request();
            String resourceType = request.resourceType();
            if ("image".equals(resourceType) || "media".equals(resourceType) || "font".equals(resourceType)) {
                route.abort();
                return;
            }
            route.resume();
        });
    }

    private Browser browser() {
        synchronized (browserLock) {
            if (browser != null && browser.isConnected()) {
                return browser;
            }
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            return browser;
        }
    }

    private List<Cookie> toPlaywrightCookies(DiscuzForumProfile profile, String rawCookie) {
        List<Cookie> result = new ArrayList<>();
        String url = profile.baseUri().resolve("/").toString();
        for (String part : rawCookie.split(";")) {
            String pair = part.trim();
            if (!StringUtils.hasText(pair) || !pair.contains("=")) {
                continue;
            }
            int index = pair.indexOf('=');
            result.add(new Cookie(pair.substring(0, index), pair.substring(index + 1)).setUrl(url));
        }
        return result;
    }

    private String resolveUserAgent(ForumCredential.Account account) {
        return StringUtils.hasText(account.userAgent()) ? account.userAgent() : USER_AGENT;
    }

    private SubmitResult toSubmitResult(DiscuzForumProfile profile,
                                        ForumPublishPayload payload,
                                        String requestPayload,
                                        HttpResponse<String> response,
                                        long started) throws Exception {
        String publishedUrl = resolvePublishedUrl(profile, response);
        boolean success = isSuccessfulPost(response, publishedUrl);
        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("statusCode", response.statusCode());
        responseBody.put("location", firstHeader(response, "location"));
        responseBody.put("publishedUrl", publishedUrl == null ? "" : publishedUrl);
        responseBody.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
        responseBody.put("bodyPreview", preview(response.body()));
        String responseJson = objectMapper.writeValueAsString(responseBody);
        if (success) {
            return SubmitResult.success(response.statusCode(), requestPayload, responseJson, publishedUrl, platformArticleId(publishedUrl));
        }
        return SubmitResult.failure(response.statusCode(), requestPayload, responseJson,
                "discuz publish did not return a success redirect or success message",
                FailureKind.PLATFORM, true);
    }

    private SubmitResult toSubmitResult(DiscuzForumProfile profile,
                                        ForumPublishPayload payload,
                                        String requestPayload,
                                        BrowserSubmitResponse response,
                                        long started) throws Exception {
        String publishedUrl = resolvePublishedUrl(profile, response);
        boolean success = isSuccessfulPost(response, publishedUrl);
        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("statusCode", response.statusCode());
        responseBody.put("location", "");
        responseBody.put("publishedUrl", publishedUrl == null ? "" : publishedUrl);
        responseBody.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
        responseBody.put("bodyPreview", preview(response.body()));
        String responseJson = objectMapper.writeValueAsString(responseBody);
        if (success) {
            return SubmitResult.success(response.statusCode(), requestPayload, responseJson, publishedUrl, platformArticleId(publishedUrl));
        }
        return SubmitResult.failure(response.statusCode(), requestPayload, responseJson,
                "discuz publish did not return a success redirect or success message",
                FailureKind.PLATFORM, true);
    }

    private boolean isSuccessfulPost(HttpResponse<String> response, String publishedUrl) {
        if (response.statusCode() >= 300 && response.statusCode() < 400 && StringUtils.hasText(firstHeader(response, "location"))) {
            return true;
        }
        if (StringUtils.hasText(publishedUrl)) {
            return true;
        }
        String body = response.body();
        return body != null && (body.contains("succeedmessage") || body.contains("发布成功") || body.contains("<root><![CDATA[0]]></root>"));
    }

    private boolean isSuccessfulPost(BrowserSubmitResponse response, String publishedUrl) {
        if (StringUtils.hasText(publishedUrl)) {
            return true;
        }
        String body = response.body();
        return response.statusCode() >= 200 && response.statusCode() < 300
                && body != null
                && (body.contains("succeedmessage") || body.contains("发布成功") || body.contains("<root><![CDATA[0]]></root>"));
    }

    private String resolvePublishedUrl(DiscuzForumProfile profile, HttpResponse<String> response) {
        String location = firstHeader(response, "location");
        if (StringUtils.hasText(location)) {
            return profile.baseUri().resolve(location).toString();
        }
        String body = response.body();
        if (!StringUtils.hasText(body)) {
            return null;
        }
        Matcher hrefMatcher = Pattern.compile("href=[\"']([^\"']*(?:thread|forum)-\\d+[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
                .matcher(body);
        if (hrefMatcher.find()) {
            return profile.baseUri().resolve(hrefMatcher.group(1)).toString();
        }
        if (StringUtils.hasText(profile.getSuccessUrlRegex())) {
            Matcher matcher = Pattern.compile(profile.getSuccessUrlRegex(), Pattern.CASE_INSENSITIVE).matcher(body);
            if (matcher.find()) {
                return profile.baseUri().resolve(matcher.group()).toString();
            }
        }
        return null;
    }

    private String resolvePublishedUrl(DiscuzForumProfile profile, BrowserSubmitResponse response) {
        String body = response.body();
        if (StringUtils.hasText(body)) {
            Matcher hrefMatcher = Pattern.compile("href=[\"']([^\"']*(?:thread|forum)-\\d+[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
                    .matcher(body);
            if (hrefMatcher.find()) {
                return profile.baseUri().resolve(hrefMatcher.group(1)).toString();
            }
            if (StringUtils.hasText(profile.getSuccessUrlRegex())) {
                Matcher matcher = Pattern.compile(profile.getSuccessUrlRegex(), Pattern.CASE_INSENSITIVE).matcher(body);
                if (matcher.find()) {
                    return profile.baseUri().resolve(matcher.group()).toString();
                }
            }
        }
        return StringUtils.hasText(response.url()) && Pattern.compile("(?:thread|forum)-\\d+", Pattern.CASE_INSENSITIVE).matcher(response.url()).find()
                ? response.url()
                : null;
    }

    private String formBody(DiscuzPostForm form, String title, String bbcode) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("formhash", form.formhash());
        values.put("posttime", form.posttime());
        values.put("wysiwyg", form.wysiwyg());
        values.put("subject", title);
        values.put("message", bbcode);
        values.put("topicsubmit", "true");
        values.put("save", "");
        return urlEncode(values);
    }

    private String urlEncode(Map<String, String> values) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private String requestPayload(DiscuzForumProfile profile, ForumPublishPayload payload) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "articleId", payload.articleId(),
                    "projectId", payload.projectId(),
                    "title", payload.title(),
                    "fid", profile.getFid(),
                    "postPageUrl", profile.getPostPageUrl(),
                    "contentMode", "bbcode"
            ));
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String platformArticleId(String publishedUrl) {
        if (!StringUtils.hasText(publishedUrl)) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?:thread-|tid=)(\\d+)").matcher(publishedUrl);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean isLoginPage(String body) {
        return body != null && body.contains("name=\"password\"") && body.contains("mod=logging");
    }

    private String firstHeader(HttpResponse<String> response, String name) {
        List<String> values = response.headers().allValues(name);
        return values.isEmpty() ? null : values.get(0);
    }

    private String preview(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private String sessionKey(Long siteId, ForumCredential.Account account) {
        if (StringUtils.hasText(account.username())) {
            return siteId + ":" + account.username();
        }
        return siteId + ":cookie:" + Math.abs(String.valueOf(account.cookie()).hashCode());
    }

    private String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private record DiscuzPostForm(String formhash, String posttime, String wysiwyg) {
    }

    private record BrowserSubmitResponse(int statusCode, String url, String body) {
    }

    private class Session {
        private CookieManager cookieManager;
        private HttpClient client;
        private DiscuzForumProfile profile;

        Session(DiscuzForumProfile profile) {
            reset(profile);
        }

        void ensureLoggedIn(DiscuzForumProfile profile, ForumCredential.Account account) throws Exception {
            seedCookie(profile, account);
            HttpResponse<String> postPage = get(profile.postPageUri());
            if (isAuthenticated(postPage.body())) {
                return;
            }
            if (StringUtils.hasText(account.cookie())) {
                throw new BizException(401, "平台网站登录 Cookie 已失效或被 WAF 拦截，请重新登录后更新该平台网站账号 Cookie");
            }
            DiscuzLoginForm loginForm = loadLoginForm(profile);
            Map<String, String> values = new LinkedHashMap<>();
            values.put("username", account.username());
            values.put("password", account.password());
            values.put("formhash", loginForm.formhash());
            values.put("quickforward", "yes");
            values.put("handlekey", "ls");
            if (Boolean.TRUE.equals(profile.getRememberLogin())) {
                values.put("cookietime", "2592000");
            }
            post(profile.loginSubmitUri(), urlEncode(values));
            HttpResponse<String> verified = get(profile.postPageUri());
            if (!isAuthenticated(verified.body())) {
                throw new BizException(401, "平台网站账号登录失败或登录信息已过期，请检查账号密码或更新 Cookie");
            }
        }

        private void seedCookie(DiscuzForumProfile profile, ForumCredential.Account account) {
            if (!StringUtils.hasText(account.cookie())) {
                return;
            }
            URI baseUri = profile.baseUri();
            for (String part : account.cookie().split(";")) {
                String pair = part.trim();
                if (!StringUtils.hasText(pair) || !pair.contains("=")) {
                    continue;
                }
                int index = pair.indexOf('=');
                HttpCookie cookie = new HttpCookie(pair.substring(0, index), pair.substring(index + 1));
                cookie.setPath("/");
                cookieManager.getCookieStore().add(baseUri, cookie);
            }
        }

        DiscuzPostForm loadPostForm(DiscuzForumProfile profile) throws Exception {
            HttpResponse<String> response = get(profile.postPageUri());
            Document document = Jsoup.parse(response.body(), profile.baseUri().toString());
            Element form = document.selectFirst("form#postform");
            if (form == null) {
                throw new BizException(400, "discuz post form not found");
            }
            String formhash = valueOf(form, "input[name=formhash]");
            String posttime = valueOf(form, "input[name=posttime]");
            String wysiwyg = valueOf(form, "input[name=wysiwyg]");
            if (!StringUtils.hasText(formhash) || !StringUtils.hasText(posttime)) {
                throw new BizException(400, "discuz post formhash or posttime not found");
            }
            return new DiscuzPostForm(formhash, posttime, StringUtils.hasText(wysiwyg) ? wysiwyg : "1");
        }

        private DiscuzLoginForm loadLoginForm(DiscuzForumProfile profile) throws Exception {
            HttpResponse<String> response = get(profile.loginPageUri());
            Document document = Jsoup.parse(response.body(), profile.baseUri().toString());
            String formhash = valueOf(document, "form#lsform input[name=formhash]");
            if (!StringUtils.hasText(formhash)) {
                formhash = valueOf(document, "input[name=formhash]");
            }
            if (!StringUtils.hasText(formhash)) {
                throw new BizException(400, "discuz login formhash not found");
            }
            return new DiscuzLoginForm(formhash);
        }

        private boolean isAuthenticated(String body) {
            return body != null
                    && body.contains("discuz_uid = '")
                    && !body.contains("discuz_uid = '0'")
                    && body.contains("id=\"postform\"");
        }

        private HttpResponse<String> get(URI uri) throws Exception {
            HttpRequest request = baseRequest(uri)
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        private HttpResponse<String> post(URI uri, String body) throws Exception {
            HttpRequest request = baseRequest(uri)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        private HttpRequest.Builder baseRequest(URI uri) {
            return HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(requestTimeoutMs(profile)))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Referer", uri.resolve(".").toString());
        }

        void reset() {
            reset(null);
        }

        void reset(DiscuzForumProfile profile) {
            if (profile != null) {
                this.profile = profile;
            }
            DiscuzForumProfile effectiveProfile = this.profile;
            cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            HttpClient.Builder builder = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .cookieHandler(cookieManager);
            if (effectiveProfile != null) {
                builder.connectTimeout(Duration.ofMillis(connectTimeoutMs(effectiveProfile)));
            }
            client = builder.build();
        }
    }

    private int connectTimeoutMs(DiscuzForumProfile profile) {
        Integer value = profile.getConnectTimeoutMs();
        return value == null || value < 1000 ? 5000 : Math.min(value, 60000);
    }

    private int requestTimeoutMs(DiscuzForumProfile profile) {
        Integer value = profile.getRequestTimeoutMs();
        return value == null || value < 1000 ? 30000 : Math.min(value, 120000);
    }

    private String valueOf(Element root, String selector) {
        Element element = root.selectFirst(selector);
        return element == null ? null : element.attr("value");
    }

    private record DiscuzLoginForm(String formhash) {
    }
}
