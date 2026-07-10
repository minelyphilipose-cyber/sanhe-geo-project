import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(here, '..', '..')

function readProjectFile(path) {
  return readFileSync(resolve(repoRoot, path), 'utf8')
}

test('extension task polling never claims by platform without environmentKey', () => {
  const serviceWorker = readProjectFile('geo-env-extension/service-worker.js')

  assert.equal(
    serviceWorker.includes('/v1/extension/tasks/next?platform='),
    false,
    'platform-only helper task claims can let one browser profile claim another profile task',
  )
  assert.match(
    serviceWorker,
    /function assertTaskEnvironmentMatchesConfig[\s\S]+TASK_ENVIRONMENT_MISMATCH/,
    'extension must reject tasks whose environmentKey differs from the active browser environment',
  )
  assert.match(
    serviceWorker,
    /providerProfileId: selected\.providerProfileId[\s\S]+TASK_PROVIDER_PROFILE_MISMATCH/,
    'extension must preserve and validate the AdsPower provider profile id when available',
  )
  assert.match(
    serviceWorker,
    /const candidatePlatformTabId = options\.identityTabId \|\| await findActivePlatformTabId\(next\.task\.platform\)[\s\S]+await ensureTaskIdentityTab\(activeConfig, session, next\.task, candidatePlatformTabId\)[\s\S]+handleTask\(activeConfig, session, next\.task, \{[\s\S]+identityTabId,[\s\S]+fillTabId: candidatePlatformTabId,[\s\S]+\}\)/,
    'extension must open or reuse the stable identity page before filling protected self-media tasks',
  )
  assert.match(
    serviceWorker,
    /prepareFillTab\(options\.fillTabId \|\| null, task\.platform, payload\.publishUrl\)[\s\S]+resolveFillTab\(candidateTabId, platform, publishUrl\)/,
    'extension must prepare the original publish tab separately from the identity tab',
  )
  assert.match(
    serviceWorker,
    /function removedListener\(removedTabId\)[\s\S]+PLATFORM_TAB_GONE[\s\S]+chrome\.tabs\.onRemoved\.addListener/,
    'extension must stop waiting immediately when a platform tab is closed',
  )
  assert.match(
    serviceWorker,
    /isPreFillTabLifecycleError[\s\S]+chrome\.tabs\.create\(\{ url: publishUrl, active: true \}\)/,
    'extension may recreate a vanished publish tab once before filling',
  )
  assert.doesNotMatch(
    serviceWorker,
    /if \(normalizePlatform\(platform\) === 'xiaohongshu'\) \{\s+return chrome\.tabs\.create\(\{ url: publishUrl, active: true \}\)/,
    'xiaohongshu should reuse the helper-opened publish tab when possible',
  )
  assert.match(
    serviceWorker,
    /url\.searchParams\.get\('published'\) !== 'true'/,
    'xiaohongshu published completion pages must not trigger another automatic task claim',
  )
})

test('local helper requires environmentKey for extension task claims and completion', () => {
  const server = readProjectFile('geo-local-helper/src/server.js')

  assert.match(
    server,
    /environmentKey is required for extension task claims/,
    'helper must reject extension task claims that are not scoped to an environment',
  )
  assert.match(
    server,
    /environmentKey is required for task completion/,
    'helper must reject task completion reports that are not scoped to an environment',
  )
  assert.match(
    server,
    /function findNextClaimableTask\(environmentKey, platform = ''\) \{\s+if \(!environmentKey\) return null/,
    'helper must not fall back to platform-only task matching',
  )
})

test('extension targets the current local helper session and serializes token refresh', () => {
  const serviceWorker = readProjectFile('geo-env-extension/service-worker.js')

  assert.match(
    serviceWorker,
    /const extensionSessionRefreshInFlight = new Map\(\)[\s\S]+extensionSessionRefreshInFlight\.get\(refreshKey\)[\s\S]+refreshExtensionSessionOnce/,
    'concurrent heartbeat and task requests must share one token refresh',
  )
  assert.match(
    serviceWorker,
    /helperSigningContext\(config\)[\s\S]+localAgentSessionId: helperContext\.sessionId/,
    'backend signing must target the helper session exposed by localhost health',
  )
  assert.match(
    serviceWorker,
    /LOCAL_HELPER_CLOCK_SKEW/,
    'extension must report an actionable clock skew error before helper signature validation fails',
  )
})

test('0.1.10 packages expose and enforce the same build revision', () => {
  const manifest = JSON.parse(readProjectFile('geo-env-extension/manifest.json'))
  const helperPackage = JSON.parse(readProjectFile('geo-local-helper/package.json'))
  const serviceWorker = readProjectFile('geo-env-extension/service-worker.js')
  const contentScript = readProjectFile('geo-env-extension/content-script.js')

  assert.equal(manifest.version, '0.1.10')
  assert.equal(helperPackage.version, '0.1.10')
  assert.match(manifest.version_name, /0\.1\.10-hotfix-20260710\.6/)
  assert.equal(helperPackage.buildRevision, '20260710.6')
  assert.match(contentScript, /GEO_ENV_CONTENT_SCRIPT_VERSION = '0\.1\.10'/)
  assert.match(serviceWorker, /EXTENSION_HELPER_BUILD_MISMATCH/)
})

test('helper retries a failed schedule report with a bounded minimal payload after backend 5xx', () => {
  const server = readProjectFile('geo-local-helper/src/server.js')

  assert.match(
    server,
    /async function reportScheduleExecutionFailed[\s\S]+Number\(error\?\.statusCode \|\| 0\) < 500[\s\S]+fallbackReport: true/,
  )
})

test('douyin channel-close recovery opens the works manager and recreates a vanished tab', () => {
  const serviceWorker = readProjectFile('geo-env-extension/service-worker.js')

  assert.match(
    serviceWorker,
    /DOUYIN_MANAGE_URL = 'https:\/\/creator\.douyin\.com\/creator-micro\/content\/manage/,
    'douyin recovery must use the works manager instead of the account home page',
  )
  assert.match(
    serviceWorker,
    /recoverDouyinPublishAfterMessageChannelClosed[\s\S]+openDouyinManageVerifyTab\(recoveryTabId\)[\s\S]+chrome\.tabs\.create\(\{ url: DOUYIN_MANAGE_URL/,
    'douyin recovery must recreate the verification tab when the original publish tab vanished',
  )
  assert.match(
    serviceWorker,
    /message port closed[\s\S]+back\/forward cache[\s\S]+no tab with id/,
    'known Chrome message-port lifecycle errors must enter publish-result recovery',
  )
  assert.match(serviceWorker, /DOUYIN_PUBLISH_NOT_CONFIRMED/)
})

test('all browser platforms recover channel-close through result pages without resubmitting content', () => {
  const serviceWorker = readProjectFile('geo-env-extension/service-worker.js')

  assert.match(serviceWorker, /TOUTIAO_MANAGE_URL = 'https:\/\/mp\.toutiao\.com\/profile_v4\/manage\/content\/all'/)
  assert.match(serviceWorker, /ZHIHU_MANAGE_URL = 'https:\/\/www\.zhihu\.com\/creator\/manage\/creation\/article'/)
  assert.match(serviceWorker, /XIAOHONGSHU_MANAGE_URL = 'https:\/\/creator\.xiaohongshu\.com\/new\/note-manager'/)
  assert.match(serviceWorker, /BAIJIAHAO_MANAGE_URL = 'https:\/\/baijiahao\.baidu\.com\/builder\/rc\/content'/)
  assert.match(
    serviceWorker,
    /recoverXiaohongshuPublishAfterMessageChannelClosed[\s\S]+findVerifiedPlatformTab\('xiaohongshu'[\s\S]+openPlatformManageVerifyTab\(recoveryTabId, 'xiaohongshu'/,
  )
  assert.match(
    serviceWorker,
    /recoverZhihuPublishAfterMessageChannelClosed[\s\S]+findVerifiedPlatformTab\('zhihu'[\s\S]+openPlatformManageVerifyTab\(recoveryTabId, 'zhihu'/,
  )
  assert.match(
    serviceWorker,
    /recoverBaijiahaoAfterMessageChannelClosed[\s\S]+findVerifiedPlatformTab\('baijiahao'[\s\S]+openPlatformManageVerifyTab\(recoveryTabId, 'baijiahao'/,
  )
  assert.doesNotMatch(serviceWorker, /retryFillMessageAfterChannelRecovery|isRecoverableFillChannelError/)
  assert.match(serviceWorker, /publishNotConfirmedError\('TOUTIAO'/)
  assert.match(serviceWorker, /publishNotConfirmedError\('ZHIHU'/)
  assert.match(serviceWorker, /publishNotConfirmedError\('XIAOHONGSHU'/)
  assert.match(serviceWorker, /publishNotConfirmedError\('BAIJIAHAO'/)
})

test('AdsPower extension status refreshes a stale dynamic DevTools endpoint once', () => {
  const server = readProjectFile('geo-local-helper/src/server.js')

  assert.match(
    server,
    /function isStaleAdspowerBrowserSessionError[\s\S]+ECONNREFUSED[\s\S]+ECONNRESET/,
    'helper must recognize connection failures caused by an expired AdsPower DevTools port',
  )
  assert.match(
    server,
    /handleAdspowerExtensionStatus[\s\S]+isStaleAdspowerBrowserSessionError\(error\)[\s\S]+startAdspowerBrowser\(config, environment\.providerProfileId, \{ forceRefresh: true \}\)[\s\S]+inspectGeoEnvExtension/,
    'extension status must discard the cached session, obtain the current dynamic port, and retry once',
  )
})

test('official api schedule retries reuse the existing distribution task request id', () => {
  const adapter = readProjectFile('geo-server/src/main/java/com/huanjing/geo/module/content/schedule/OfficialApiSelfMediaPublishScheduleAdapter.java')

  assert.match(
    adapter,
    /prepareTaskForSubmit\(row, article, account, existing\)/,
    'official API retries must reuse the existing task instead of inserting a duplicate requestId',
  )
  assert.match(
    adapter,
    /if \(existing != null\) \{[\s\S]+distributionTaskMapper\.updateById\(existing\);[\s\S]+return existing;/,
    'failed official API tasks should be reset and updated for retry',
  )
})

test('extension injects scripts into the top frame before best-effort all-frame injection', () => {
  const serviceWorker = readProjectFile('geo-env-extension/service-worker.js')

  assert.match(
    serviceWorker,
    /target: \{ tabId, frameIds: \[0\] \}[\s\S]+target: \{ tabId, allFrames: true \}/,
    'dynamic script injection must not fail the task only because a cross-origin iframe is inaccessible',
  )
  assert.match(
    serviceWorker,
    /isCannotAccessContentsError[\s\S]+Extension manifest must request permission/,
    'extension permission errors from inaccessible frames should be detected explicitly',
  )
  assert.match(
    serviceWorker,
    /contentScriptVersion !== EXTENSION_VERSION[\s\S]+chrome\.tabs\.reload/,
    'tabs with stale content scripts should be refreshed before filling',
  )
})

test('xiaohongshu scheduled publish retries the real switch click target', () => {
  const platform = readProjectFile('geo-env-extension/platform-xiaohongshu.js')

  assert.match(
    platform,
    /async function clickXiaohongshuScheduleToggle/,
    'xiaohongshu schedule flow should isolate toggle-click recovery',
  )
  assert.match(
    platform,
    /小红书定时发布开关坐标/,
    'xiaohongshu schedule flow should retry by direct switch coordinates',
  )
  assert.match(
    platform,
    /小红书定时发布行右侧开关/,
    'xiaohongshu schedule flow should retry by the right side of the schedule row',
  )
  assert.match(
    platform,
    /findXiaohongshuScheduleCard[\s\S]+closest\?\.\('\.custom-switch-card'\)/,
    'schedule recovery must scope the toggle to the card that owns the 定时发布 label',
  )
  assert.match(
    platform,
    /\.custom-switch-switch \.d-switch\.d-clickable/,
    'the actual Xiaohongshu d-switch clickable root must be preferred over decorative switch-named nodes',
  )
  assert.doesNotMatch(
    platform,
    /function normalizeXiaohongshuScheduleToggleTarget[\s\S]{0,900}\/switch\|toggle\|checkbox\/i/,
    'custom-switch-icon and custom-switch-text-content must not be accepted merely because their class contains switch',
  )
})

test('douyin article head upload never falls back to every image input', () => {
  const platform = readProjectFile('geo-env-extension/platform-douyin.js')
  const contentScript = readProjectFile('geo-env-extension/content-script.js')
  const serviceWorker = readProjectFile('geo-env-extension/service-worker.js')
  const helper = readProjectFile('geo-local-helper/src/server.js')

  assert.match(platform, /uploadTarget: 'douyin_article_head_image'/)
  assert.match(contentScript, /uploadTarget: options\.uploadTarget \|\| null/)
  assert.match(
    serviceWorker,
    /uploadTarget: options\.uploadTarget \|\| null,[\s\S]+click: options\.click \|\| null/,
    'the exact page target and click point must reach the local helper',
  )
  assert.match(helper, /douyin upload target is not allowed/)
  assert.match(
    helper,
    /platform === 'douyin'[\s\S]+chooseDouyinArticleHeadImageInputs\(inputs\)/,
    'douyin must use its strict article-head input chooser',
  )
  assert.match(
    helper,
    /function chooseDouyinArticleHeadImageInputs[\s\S]+contextKind !== 'article_head'[\s\S]+return candidates\.length \? \[candidates\[0\]\.input\] : \[\]/,
    'the strict fallback must return at most one confirmed head-image input',
  )
  assert.match(helper, /文章正文\|prosemirror\|tiptap\|toolbar\|editor\|contenteditable\|插入图片/)
  assert.match(
    helper,
    /extra\.uploadTarget === 'douyin_article_head_image'[\s\S]+readFileInputState\(input\)[\s\S]+readAndDispatchFileInputState\(input\)/,
    'after accepting the head-image chooser, unrelated empty file inputs must not receive synthetic change events',
  )
})
