package com.huanjing.geo.module.content.service.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForumBrowserPublisher {

    private static final String CHROMIUM_LAUNCH_ARGS_ENV = "PLAYWRIGHT_CHROMIUM_LAUNCH_ARGS";
    private static final int MAX_CONCURRENT_PUBLISHES = 2;
    private static final Semaphore BROWSER_SLOTS = new Semaphore(MAX_CONCURRENT_PUBLISHES);

    private final ObjectMapper objectMapper;
    private final AtomicInteger workerCursor = new AtomicInteger();
    private final ThreadLocal<BrowserWorkerState> currentWorkerState = new ThreadLocal<>();
    private final List<BrowserWorker> workers = List.of(
            new BrowserWorker("forum-browser-worker-1"),
            new BrowserWorker("forum-browser-worker-2")
    );

    public SubmitResult publish(ForumPublishProfile profile,
                                ForumCredential credential,
                                ForumPublishPayload payload) {
        long started = System.nanoTime();
        String requestPayload = requestPayload(profile, payload);
        if (!acquireBrowserSlot(profile, payload, requestPayload)) {
            return SubmitResult.failure(429, requestPayload, null,
                    "forum browser concurrency limit reached",
                    FailureKind.RATE_LIMIT, true);
        }
        try {
            return nextWorker().publish(() -> publishWithBrowser(profile, credential, payload, started, requestPayload));
        } catch (ForumPublishException ex) {
            return SubmitResult.failure(ex.statusCode(), requestPayload, null, ex.getMessage(), ex.failureKind(), ex.retryable());
        } catch (TimeoutError ex) {
            log.warn("forum publish timeout articleId={} error={}", payload.articleId(), safeMessage(ex));
            return SubmitResult.failure(504, requestPayload, null, safeMessage(ex), FailureKind.NETWORK_ERROR, true);
        } catch (Exception ex) {
            log.warn("forum publish failed articleId={} error={}", payload.articleId(), safeMessage(ex), ex);
            return SubmitResult.failure(500, requestPayload, null, safeMessage(ex), FailureKind.UNKNOWN, false);
        } finally {
            BROWSER_SLOTS.release();
        }
    }

    @PreDestroy
    public void shutdown() {
        workers.forEach(BrowserWorker::shutdown);
    }

    private SubmitResult publishWithBrowser(ForumPublishProfile profile,
                                            ForumCredential credential,
                                            ForumPublishPayload payload,
                                            long started,
                                            String requestPayload) throws Exception {
        Browser browser = currentWorkerState().browser(profile);
        try (BrowserContext context = browser.newContext()) {
            routeHeavyResources(context, profile);
            Page page = context.newPage();
            int timeoutMs = timeoutMs(profile);
            page.setDefaultTimeout(timeoutMs);
            page.setDefaultNavigationTimeout(timeoutMs);

            login(page, profile, credential);
            page.navigate(profile.getPostUrl());
            waitNetworkIdle(page, timeoutMs);
            fillPostForm(page, profile, payload);
            clickRequired(page, profile.getSelectors().getSubmit(), "submit");
            waitNetworkIdle(page, timeoutMs);

            String publishedUrl = resolvePublishedUrl(page, profile);
            String responseBody = objectMapper.writeValueAsString(Map.of(
                    "currentUrl", page.url(),
                    "publishedUrl", publishedUrl == null ? "" : publishedUrl,
                    "elapsedMs", (System.nanoTime() - started) / 1_000_000
            ));
            return SubmitResult.success(200, requestPayload, responseBody, publishedUrl, platformArticleId(publishedUrl));
        }
    }

    private BrowserWorker nextWorker() {
        int index = Math.floorMod(workerCursor.getAndIncrement(), workers.size());
        return workers.get(index);
    }

    private BrowserWorkerState currentWorkerState() {
        BrowserWorkerState state = currentWorkerState.get();
        if (state == null) {
            throw new IllegalStateException("forum browser worker state is not bound");
        }
        return state;
    }

    private boolean acquireBrowserSlot(ForumPublishProfile profile,
                                       ForumPublishPayload payload,
                                       String requestPayload) {
        try {
            boolean acquired = BROWSER_SLOTS.tryAcquire(acquireTimeoutMs(profile), TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("forum publish rejected by concurrency limit articleId={} maxConcurrent={} request={}",
                        payload.articleId(), MAX_CONCURRENT_PUBLISHES, requestPayload);
            }
            return acquired;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("forum publish interrupted while waiting browser slot articleId={}", payload.articleId());
            return false;
        }
    }

    private void routeHeavyResources(BrowserContext context, ForumPublishProfile profile) {
        if (Boolean.FALSE.equals(profile.getBlockHeavyResources())) {
            return;
        }
        Set<String> blockedTypes = blockedResourceTypes(profile);
        if (blockedTypes.isEmpty()) {
            return;
        }
        context.route("**/*", route -> {
            Request request = route.request();
            if (blockedTypes.contains(request.resourceType())) {
                route.abort();
                return;
            }
            route.resume();
        });
    }

    private void login(Page page, ForumPublishProfile profile, ForumCredential credential) {
        ForumPublishProfile.Selectors selectors = profile.getSelectors();
        page.navigate(profile.getLoginUrl());
        waitNetworkIdle(page, timeoutMs(profile));
        fillRequired(page, selectors.getUsername(), credential.getUsername(), "username");
        fillRequired(page, selectors.getPassword(), credential.getPassword(), "password");
        clickRequired(page, selectors.getLoginSubmit(), "loginSubmit");
        waitNetworkIdle(page, timeoutMs(profile));
        verifyLoggedIn(page, selectors.getLoggedInSignals());
    }

    private void verifyLoggedIn(Page page, List<String> loggedInSignals) {
        if (loggedInSignals == null || loggedInSignals.isEmpty()) {
            return;
        }
        boolean matched = loggedInSignals.stream()
                .filter(StringUtils::hasText)
                .anyMatch(selector -> page.locator(selector).count() > 0);
        if (!matched) {
            throw new ForumPublishException(401, FailureKind.AUTH, false, "forum login did not reach an authenticated page");
        }
    }

    private void fillPostForm(Page page, ForumPublishProfile profile, ForumPublishPayload payload) {
        ForumPublishProfile.Selectors selectors = profile.getSelectors();
        fillRequired(page, selectors.getTitle(), payload.title(), "title");
        fillOptional(page, selectors.getCategory(), payload.category());
        if (StringUtils.hasText(selectors.getTags()) && payload.tags() != null && !payload.tags().isEmpty()) {
            fillOptional(page, selectors.getTags(), String.join(",", payload.tags()));
        }
        fillEditor(page, profile, payload);
    }

    private void fillEditor(Page page, ForumPublishProfile profile, ForumPublishPayload payload) {
        String selector = profile.getSelectors().getEditor();
        if (!StringUtils.hasText(selector)) {
            throw new ForumPublishException(400, FailureKind.VALIDATION, false, "forum editor selector is required");
        }
        String value = "markdown".equalsIgnoreCase(profile.getContentMode())
                ? payload.contentMarkdown()
                : payload.contentHtml();
        Locator editor = editorLocator(page, profile);
        if ("html".equalsIgnoreCase(profile.getContentMode())) {
            editor.evaluate("""
                    (element, value) => {
                      element.innerHTML = value || '';
                      element.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: value || '' }));
                      element.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                    """, value);
            return;
        }
        editor.fill(value == null ? "" : value);
    }

    private Locator editorLocator(Page page, ForumPublishProfile profile) {
        String selector = profile.getSelectors().getEditor();
        String frameSelector = profile.getSelectors().getEditorFrame();
        if (StringUtils.hasText(frameSelector)) {
            FrameLocator frame = page.frameLocator(frameSelector);
            return frame.locator(selector);
        }
        return page.locator(selector);
    }

    private void fillRequired(Page page, String selector, String value, String name) {
        if (!StringUtils.hasText(selector)) {
            throw new ForumPublishException(400, FailureKind.VALIDATION, false, "forum " + name + " selector is required");
        }
        if (!StringUtils.hasText(value)) {
            throw new ForumPublishException(400, FailureKind.VALIDATION, false, "forum " + name + " value is required");
        }
        page.locator(selector).fill(value);
    }

    private void fillOptional(Page page, String selector, String value) {
        if (StringUtils.hasText(selector) && StringUtils.hasText(value)) {
            page.locator(selector).fill(value);
        }
    }

    private void clickRequired(Page page, String selector, String name) {
        if (!StringUtils.hasText(selector)) {
            throw new ForumPublishException(400, FailureKind.VALIDATION, false, "forum " + name + " selector is required");
        }
        page.locator(selector).click();
    }

    private String resolvePublishedUrl(Page page, ForumPublishProfile profile) {
        String explicitSelector = profile.getSelectors().getPublishedUrl();
        if (StringUtils.hasText(explicitSelector)) {
            String href = page.locator(explicitSelector).first().getAttribute("href");
            if (StringUtils.hasText(href)) {
                return URI.create(page.url()).resolve(href).toString();
            }
        }
        String currentUrl = page.url();
        if (!StringUtils.hasText(profile.getPublishedUrlRegex())) {
            return currentUrl;
        }
        Matcher matcher = Pattern.compile(profile.getPublishedUrlRegex()).matcher(currentUrl);
        return matcher.find() ? currentUrl : null;
    }

    private String platformArticleId(String publishedUrl) {
        if (!StringUtils.hasText(publishedUrl)) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d+)(?:\\D*)$").matcher(publishedUrl);
        return matcher.find() ? matcher.group(1) : null;
    }

    private int timeoutMs(ForumPublishProfile profile) {
        Integer value = profile.getTimeoutMs();
        if (value == null || value < 5000) {
            return 30000;
        }
        return Math.min(value, 120000);
    }

    private int acquireTimeoutMs(ForumPublishProfile profile) {
        Integer value = profile.getAcquireTimeoutMs();
        if (value == null || value < 1000) {
            return 30000;
        }
        return Math.min(value, 120000);
    }

    private Set<String> blockedResourceTypes(ForumPublishProfile profile) {
        List<String> configuredTypes = profile.getBlockedResourceTypes();
        if (configuredTypes == null || configuredTypes.isEmpty()) {
            configuredTypes = List.of("image", "media", "font");
        }
        return configuredTypes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void waitNetworkIdle(Page page, int timeoutMs) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout((double) timeoutMs));
        } catch (Exception ignored) {
            // Some forums keep long polling connections open. Continue after the normal action timeout.
        }
    }

    private String requestPayload(ForumPublishProfile profile, ForumPublishPayload payload) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("articleId", payload.articleId());
            request.put("projectId", payload.projectId());
            request.put("title", payload.title());
            request.put("postUrl", profile.getPostUrl());
            request.put("boardId", profile.getBoardId());
            request.put("contentMode", profile.getContentMode());
            return objectMapper.writeValueAsString(request);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private List<String> chromiumLaunchArgs() {
        String raw = System.getenv(CHROMIUM_LAUNCH_ARGS_ENV);
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.trim().split("\\s+"))
                .filter(StringUtils::hasText)
                .toList();
    }

    private String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private class BrowserWorker {
        private final ExecutorService executor;
        private BrowserWorkerState state;

        BrowserWorker(String threadName) {
            this.executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, threadName);
                thread.setDaemon(true);
                return thread;
            });
        }

        SubmitResult publish(Callable<SubmitResult> task) throws Exception {
            Future<SubmitResult> future = executor.submit(() -> {
                BrowserWorkerState boundState = state();
                currentWorkerState.set(boundState);
                try {
                    return task.call();
                } catch (RuntimeException ex) {
                    if (!boundState.isBrowserConnected()) {
                        boundState.closeQuietly();
                    }
                    throw ex;
                } finally {
                    currentWorkerState.remove();
                }
            });
            try {
                return future.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ForumPublishException(500, FailureKind.UNKNOWN, true,
                        "interrupted while waiting forum browser worker");
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(cause);
            }
        }

        void shutdown() {
            Future<?> future = executor.submit(() -> {
                if (state != null) {
                    state.closeQuietly();
                }
            });
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (Exception ex) {
                log.warn("forum browser worker shutdown did not finish cleanly: {}", ex.getMessage());
            } finally {
                executor.shutdownNow();
            }
        }

        private BrowserWorkerState state() {
            if (state == null) {
                state = new BrowserWorkerState();
            }
            return state;
        }
    }

    private class BrowserWorkerState {
        private Playwright playwright;
        private Browser browser;
        private Boolean headless;
        private List<String> launchArgs = List.of();

        Browser browser(ForumPublishProfile profile) {
            Boolean nextHeadless = !Boolean.FALSE.equals(profile.getHeadless());
            List<String> nextLaunchArgs = chromiumLaunchArgs();
            if (browser != null && browser.isConnected()
                    && nextHeadless.equals(headless)
                    && nextLaunchArgs.equals(launchArgs)) {
                return browser;
            }

            closeQuietly();
            long started = System.nanoTime();
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(nextHeadless)
                    .setArgs(nextLaunchArgs));
            headless = nextHeadless;
            launchArgs = nextLaunchArgs;
            log.info("forum browser worker started chromium in {}ms", (System.nanoTime() - started) / 1_000_000);
            return browser;
        }

        boolean isBrowserConnected() {
            return browser != null && browser.isConnected();
        }

        void closeQuietly() {
            if (browser != null) {
                try {
                    browser.close();
                } catch (Exception ignored) {
                    // Shutdown best effort.
                }
                browser = null;
            }
            if (playwright != null) {
                try {
                    playwright.close();
                } catch (Exception ignored) {
                    // Shutdown best effort.
                }
                playwright = null;
            }
        }
    }

    private static class ForumPublishException extends RuntimeException {
        private final int statusCode;
        private final String failureKind;
        private final boolean retryable;

        ForumPublishException(int statusCode, String failureKind, boolean retryable, String message) {
            super(message);
            this.statusCode = statusCode;
            this.failureKind = failureKind;
            this.retryable = retryable;
        }

        int statusCode() {
            return statusCode;
        }

        String failureKind() {
            return failureKind;
        }

        boolean retryable() {
            return retryable;
        }
    }
}
