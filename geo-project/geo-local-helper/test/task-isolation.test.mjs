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

test('Douyin image-text helper keeps temporary files until the detail page confirms every image', () => {
  const server = readProjectFile('geo-local-helper/src/server.js')

  assert.match(
    server,
    /await target\.uploadFile\(\.\.\.filePaths\)[\s\S]+await waitForDouyinImageTextUploadCompleted\(page, filePaths\.length\)/,
    'setting the file input is not upload completion; the helper must wait for the Douyin detail page',
  )
  assert.match(
    server,
    /async function handleUploadImagesToPage[\s\S]+const upload = await uploadDouyinImageTextFilesToAdsPowerPage[\s\S]+sendJson[\s\S]+finally \{[\s\S]+fs\.unlink/,
    'temporary files must remain readable until the page-level upload wait has completed',
  )
  assert.match(
    server,
    /已添加\\s\*\(\\d\+\)\\s\*张图片[\s\S]+confirmedCount !== expected \|\| pending/,
    'a 6/6 progress counter alone must not be treated as confirmed image content',
  )
  assert.match(
    server,
    /readDouyinUnpublishedDraftState\(page\)[\s\S]+DOUYIN_UNPUBLISHED_DRAFT_BLOCKED/,
    'a previous unpublished draft must stop the upload without choosing continue or discard',
  )
  assert.doesNotMatch(
    server,
    /unpublishedDraft[\s\S]{0,500}\.(click|evaluateHandle)/,
    'the helper must not make a destructive choice for a previous unpublished draft',
  )
})

test('local helper releases unclaimed schedules and reports every occupied schedule slot', () => {
  const server = readProjectFile('geo-local-helper/src/server.js')

  assert.match(
    server,
    /const EXTENSION_CLAIM_TIMEOUT_MS = 90_000/,
    'a browser extension must claim a launched schedule within a bounded handoff window',
  )
  assert.match(
    server,
    /function activeRuntimeTaskCount\(\) \{\s+return occupiedScheduleClaimSlotCount\(\)/,
    'backend capacity reporting must match the pending and claimed slots enforced locally',
  )
  assert.match(
    server,
    /async function expireTimedOutPendingScheduleTasks\(config\)[\s\S]+EXTENSION_CLAIM_TIMEOUT[\s\S]+reportScheduleExecutionFailed/,
    'an unclaimed schedule must be failed back to the backend so its lock is released and retry policy can run',
  )
  assert.match(
    server,
    /async function handleTaskComplete\(req, res, config, taskId, status\)[\s\S]+await saveRuntimeTasks\(\)[\s\S]+reportLocalAgentRuntimeStatus\(config, \{[\s\S]+reason: `task_\$\{status\}`/,
    'task completion must immediately publish the released helper capacity',
  )
  assert.doesNotMatch(
    server,
    /async function handleNextTask\([\s\S]+?reason: `task_\$\{status\}`[\s\S]+?async function requeueTimedOutClaims/,
    'task claiming must not reference the completion-only status variable',
  )
  assert.match(
    server,
    /task\.status === 'pending' \? EXTENSION_CLAIM_TIMEOUT_MS : SCHEDULE_PROGRESS_STALL_TIMEOUT_MS/,
    'pending handoffs and claimed execution stalls must use separate timeout boundaries',
  )
  assert.match(server, /function taskProgressAgeMs\(task\)[\s\S]+lastProgressAt/)
  assert.match(server, /async function handleTaskProgress\([\s\S]+task\.lastProgressAt = reportedAt/)
  assert.match(server, /\/v1\\\/extension\\\/tasks\\\/\(\\d\+\)\\\/progress/)
  assert.match(server, /diagnosticsJson: shortDiagnosticsJson\(\{[\s\S]+lastStage: task\?\.lastStage/)
  assert.match(
    server,
    /awaitingExtension[\s\S]+running[\s\S]+oldestAwaitingExtensionAgeSeconds/,
    'health output must distinguish helper process health from extension handoff health',
  )
  assert.match(
    server,
    /function claimAttemptOfTask\(task\)[\s\S]+attemptCount[\s\S]+claimAttempt/,
    'every helper callback must retain the backend claim generation',
  )
  assert.match(
    server,
    /function terminateTaskForScheduleClaimError\(task, error\)[\s\S]+backendSuccessReportRejectedAt[\s\S]+backendFailureReportRejectedAt/,
    'deterministic ownership errors must stop every later report for the stale task',
  )
})

test('local helper applies capacity locally and rotates platform polling fairly', () => {
  const server = readProjectFile('geo-local-helper/src/server.js')

  assert.match(
    server,
    /function occupiedScheduleClaimSlotCount\(\)[\s\S]+task\.status === 'pending' \|\| task\.status === 'claimed'/,
    'pending and claimed schedules must occupy the local admission slot',
  )
  assert.match(
    server,
    /if \(!hasAvailableScheduleClaimSlot\(config\)\)[\s\S]+HELPER_CAPACITY_FULL/,
    'the helper must not claim more backend schedules than it can hand to the extension',
  )
  assert.match(
    server,
    /function rotateSchedulePlatforms\(platforms\)[\s\S]+schedulePlatformCursor[\s\S]+platforms\.slice\(offset\)\.concat\(platforms\.slice\(0, offset\)\)/,
    'each polling cycle must start from the next platform instead of starving later platforms',
  )
  assert.match(
    server,
    /const claimedAttempt = Number\(claim\.schedule\?\.attemptCount \|\| 0\) \|\| null[\s\S]+isReusableActiveTask\(existing\) && claimAttemptOfTask\(existing\) === claimedAttempt/,
    'a reused local task must belong to the same backend claim generation',
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

test('extension distinguishes backend security failures and recovers login report tabs', () => {
  const serviceWorker = readProjectFile('geo-env-extension/service-worker.js')

  assert.match(
    serviceWorker,
    /function isExtensionUnauthorized\(error\)[\s\S]+error\?\.code === 70002/,
    'only the extension-token error code should invalidate a successful binding',
  )
  assert.doesNotMatch(
    serviceWorker,
    /function isExtensionUnauthorized\(error\) \{[\s\S]{0,300}error\?\.status === 401/,
    'a generic Spring Security 401 must not clear the extension binding',
  )
  assert.match(
    serviceWorker,
    /async function resolveLoginReportTab\(originalTab, platform\)[\s\S]+chrome\.tabs\.query\(\{\}\)[\s\S]+isAllowedLoginReportUrl\(platform, tab\.url\)/,
    'login reporting should recover another valid platform tab after the original tab closes or redirects',
  )
  assert.match(
    serviceWorker,
    /const environmentAccountId = report\.environmentAccountId \|\| null/,
    'automatic login reporting must not reuse a stale environment-account id from extension config',
  )
  assert.doesNotMatch(
    serviceWorker,
    /report\.environmentAccountId \|\| config\.environmentAccountId/,
    'a rebound environment must route login reports by the current task binding or brand account name',
  )
  assert.match(
    serviceWorker,
    /const brandId = session\?\.brandId \|\| report\.brandId \|\| config\.brandId \|\| null/,
    'automatic tab login reports must retain the configured brand route',
  )
  assert.match(
    serviceWorker,
    /const reportBindingKey = options\.environmentAccountId \|\| options\.selfMediaAccountId \|\| 'auto'[\s\S]+const throttleKey = `\$\{environmentKey\}:\$\{platform\}:\$\{tab\.id\}:\$\{reportBindingKey\}`/,
    'a generic tab report must not throttle the task-scoped binding report that follows it',
  )
})

test('baijiahao login reporting reads the current account page structure', () => {
  const contentScript = readProjectFile('geo-env-extension/content-script.js')
  const serviceWorker = readProjectFile('geo-env-extension/service-worker.js')

  assert.match(
    contentScript,
    /\[class\*="userInfoBox"\] \[class\*="userName"\]/,
    'the identity reader must target the current Baijiahao account-name container',
  )
  assert.match(
    contentScript,
    /collectBaijiahaoExactAccountName\(document, accountNames\)/,
    'the account name selector must run against the whole page before relying on a heuristic account root',
  )
  assert.match(
    contentScript,
    /百家号ID\\s\*\[:：\]\?\\s\*\(\\d\{6,\}\)/,
    'the identity reader must recognize the Baijiahao ID label shown on the account page',
  )
  assert.ok(
    contentScript.includes('if (/^\\d{6,}$/.test(text)) return false'),
    'numeric-only Baijiahao IDs must be rejected without rejecting names such as yqx2002528',
  )
  assert.match(
    serviceWorker,
    /function isBaijiahaoIdentityUrl\(url\)[\s\S]+\/builder\/rc\/settings\/accountSet/,
    'Baijiahao identity checks must use the authoritative account settings page',
  )
  assert.match(
    serviceWorker,
    /verifyTaskIdentityWithRetry[\s\S]+isIdentityReaderNotReadyError[\s\S]+chrome\.tabs\.reload/,
    'Baijiahao identity checks must wait for React hydration and recover once from an empty identity page',
  )
  assert.match(
    serviceWorker,
    /tabs\.find\(\(tab\) => tab\.id && tab\.url && isIdentityPrecheckUrl\(platform, tab\.url\)\)/,
    'Baijiahao identity precheck must not reuse an arbitrary works-list or editor tab',
  )
})

test('helper emergency build revision targets the deployed extension', () => {
  const manifest = JSON.parse(readProjectFile('geo-env-extension/manifest.json'))
  const helperPackage = JSON.parse(readProjectFile('geo-local-helper/package.json'))
  const serviceWorker = readProjectFile('geo-env-extension/service-worker.js')
  const contentScript = readProjectFile('geo-env-extension/content-script.js')

  assert.equal(manifest.version, helperPackage.version)
  assert.equal(manifest.version, '0.2.0')
  assert.match(
    manifest.version_name,
    new RegExp(`${helperPackage.version.replaceAll('.', '\\.')}.+${helperPackage.buildRevision.replaceAll('.', '\\.')}`),
  )
  assert.match(contentScript, new RegExp(`GEO_ENV_CONTENT_SCRIPT_VERSION = '${helperPackage.version.replaceAll('.', '\\.')}'`))
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

test('scheduled browser platforms recover channel-close through result pages without resubmitting content', () => {
  const serviceWorker = readProjectFile('geo-env-extension/service-worker.js')

  assert.match(serviceWorker, /TOUTIAO_MANAGE_URL = 'https:\/\/mp\.toutiao\.com\/profile_v4\/graphic\/articles'/)
  assert.match(
    serviceWorker,
    /recoverToutiaoScheduleAfterWorksListTimeout[\s\S]+isToutiaoWorksManageUrl\(current\.url\)[\s\S]+refreshAndInspectPlatformWorksList\([\s\S]+inspectToutiaoWorksListTab/,
    'toutiao recovery must preserve the actual redirected works page and refresh it before matching',
  )
  assert.match(
    serviceWorker,
    /recoverDouyinPublishAfterMessageChannelClosed[\s\S]+isDouyinWorksManageUrl\(current\.url\)[\s\S]+refreshAndInspectPlatformWorksList\([\s\S]+inspectDouyinManageTab/,
    'douyin recovery must refresh the actual redirected works page before matching',
  )
  assert.match(
    serviceWorker,
    /async function refreshAndInspectPlatformWorksList[\s\S]+await reloadPlatformWorksList\(tabId,[\s\S]+latest = await inspector\(tabId, context, refreshAttempt\)/,
    'browser platforms must share one refresh-before-inspection helper',
  )
  assert.match(serviceWorker, /XIAOHONGSHU_MANAGE_URL = 'https:\/\/creator\.xiaohongshu\.com\/new\/note-manager'/)
  assert.match(serviceWorker, /BAIJIAHAO_MANAGE_URL = 'https:\/\/baijiahao\.baidu\.com\/builder\/rc\/content'/)
  assert.match(
    serviceWorker,
    /recoverXiaohongshuPublishAfterMessageChannelClosed[\s\S]+findVerifiedPlatformTab\('xiaohongshu'[\s\S]+openPlatformManageVerifyTab\(recoveryTabId, 'xiaohongshu'/,
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
  assert.match(
    serviceWorker,
    /if \(isAutomatedBrowserPublishPlatform\(options\.platform\)\)[\s\S]+throw error[\s\S]+waitForFillContentScriptReadyWithRecovery/,
    'a closed post-submit message channel must enter result recovery instead of replaying the full fill command',
  )
  assert.match(serviceWorker, /baijiahao: 'BAIJIAHAO_PUBLISH_NOT_CONFIRMED'/)
})

test('zhihu only accepts immediate-page confirmation and never opens a delayed result-check page', () => {
  const platform = readProjectFile('geo-env-extension/platform-zhihu.js')
  const contentScript = readProjectFile('geo-env-extension/content-script.js')
  const serviceWorker = readProjectFile('geo-env-extension/service-worker.js')

  assert.match(platform, /\u53d1\u5e03\u540e\u672a\u68c0\u6d4b\u5230\u5b8c\u6210\u72b6\u6001[\s\S]+ZHIHU_PUBLISH_NOT_CONFIRMED/)
  assert.match(platform, /updateStage\(deps, 'submitting_publish'\)[\s\S]+updateStage\(deps, 'verifying_publish_result'\)/)
  assert.match(platform, /async function clickPublishAction[\s\S]+clickTrustedActionOnce[\s\S]+function updateStage/)
  assert.match(contentScript, /updateStage: updateActiveFillStage/)
  assert.match(serviceWorker, /isRecoverableZhihuPublishVerifyError[\s\S]+ZHIHU_PUBLISH_NOT_SUBMITTED[\s\S]+ZHIHU_PUBLISH_NOT_CONFIRMED/)
  assert.match(serviceWorker, /zhihu: 'ZHIHU_PUBLISH_NOT_CONFIRMED'/)
  assert.match(
    serviceWorker,
    /recoverZhihuPublishAfterFillError[\s\S]+findVerifiedPlatformTab\('zhihu'[\s\S]+publishNotConfirmedError\('ZHIHU'/,
  )
  assert.doesNotMatch(
    serviceWorker,
    /recoverZhihuPublishAfterFillError[\s\S]+openPlatformManageVerifyTab\([^)]*'zhihu'/,
    '知乎无法在当前页面确认时必须转人工，不得自动打开管理页回查',
  )
})

test('publish-result checks do not steal focus or leave temporary management tabs open', () => {
  const server = readProjectFile('geo-local-helper/src/server.js')

  assert.doesNotMatch(
    server,
    /reusablePage\.bringToFront/,
    'an automatic result check must not force a creator management page to the foreground',
  )
  assert.match(
    server,
    /const checkPage = await reuseOrCreatePublishCheckPage[\s\S]+if \(checkPage\.created && !page\.isClosed\(\)\)[\s\S]+await page\.close/,
    'a temporary works-list page must be closed after the automatic result check completes',
  )
})

test('toutiao cover selection and post-submit timeout cannot fall back to republishing', () => {
  const contentScript = readProjectFile('geo-env-extension/content-script.js')
  const serviceWorker = readProjectFile('geo-env-extension/service-worker.js')
  const helper = readProjectFile('geo-local-helper/src/server.js')
  const scheduleService = readProjectFile('geo-server/src/main/java/com/huanjing/geo/module/content/service/SelfMediaPublishScheduleService.java')

  assert.match(
    contentScript,
    /optionText === '单图' \? '2' : optionText === '三图' \? '3' : optionText === '无封面' \? '1'/,
    'toutiao cover mode must use the platform radio values instead of clicking approximate text coordinates',
  )
  assert.match(contentScript, /\.article-cover-radio-group/)
  assert.match(contentScript, /uploadTarget: 'toutiao_article_cover'/)
  assert.match(helper, /platform === 'toutiao' && body\.uploadTarget !== 'toutiao_article_cover'/)
  assert.match(contentScript, /updateActiveFillStage\('submitting_publish'\)/)
  assert.match(contentScript, /updateActiveFillStage\('verifying_publish_result'\)/)
  assert.match(contentScript, /type: 'GEO_ENV_TASK_PROGRESS'/)
  assert.match(serviceWorker, /message\?\.type === 'GEO_ENV_TASK_PROGRESS'[\s\S]+\/progress/)
  assert.match(
    serviceWorker,
    /toutiao: 'TOUTIAO_PUBLISH_NOT_CONFIRMED'[\s\S]+\['submitting_publish', 'verifying_publish_result'\]\.includes\(activeStage\)[\s\S]+\? postSubmissionCode/,
    'a timeout after the final publish click must be classified as result uncertainty, not as a fresh fill failure',
  )
  assert.match(
    scheduleService,
    /isPostSubmissionVerificationFailure\(failureCode\)[\s\S]+queueUncertainSubmissionForPublishResultCheck/,
    'the backend must move an uncertain submission to result checking instead of schedule execution retry',
  )
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
    /function isStaleAdspowerBrowserSessionError[\s\S]+Network\\\.enable timed out[\s\S]+protocolTimeout/,
    'helper must also refresh AdsPower sessions when the cached DevTools endpoint stops answering CDP commands',
  )
  assert.match(
    server,
    /handleAdspowerExtensionStatus[\s\S]+isStaleAdspowerBrowserSessionError\(error\)[\s\S]+startAdspowerBrowser\(config, environment\.providerProfileId, \{ forceRefresh: true \}\)[\s\S]+inspectGeoEnvExtension/,
    'extension status must discard the cached session, obtain the current dynamic port, and retry once',
  )
  assert.match(
    server,
    /claimAndLaunchScheduledTask[\s\S]+isStaleAdspowerBrowserSessionError\(error\)[\s\S]+startAdspowerBrowser\(config, environment\.providerProfileId, \{ forceRefresh: true \}\)[\s\S]+runtimeTask\.openResult/,
    'schedule launch must refresh the AdsPower session and retry the page open once after a stale CDP failure',
  )
  assert.match(
    server,
    /failureTask\.status = 'failed'[\s\S]+failureTask\.claimedAt = null[\s\S]+upsertTask\(failureTask\)[\s\S]+saveRuntimeTasks/,
    'a failed page launch must immediately persist a terminal local task and release its capacity slot',
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
  assert.match(
    platform,
    /function findXiaohongshuPublishHost[\s\S]+querySelectorAll\('xhs-publish-btn'\)[\s\S]+getAttribute\('submit-text'\)[\s\S]+getAttribute\('submit-disabled'\)[\s\S]+getAttribute\('submit-loading'\)/,
    'the closed-shadow publish component must be located through its public host attributes',
  )
  assert.match(
    platform,
    /function findXiaohongshuPublishHostClickPoint[\s\S]+xiaohongshuPublishHostPrimaryButtonPoint\(rect\)/,
    'the trusted click must derive the real primary button position from the xhs-publish-btn host layout',
  )
  assert.match(
    platform,
    /function xiaohongshuPublishHostPrimaryButtonPoint[\s\S]+width \/ 2 \+ \(actionWidth \+ actionGap\) \/ 2/,
    'the closed-shadow fallback must target the centered primary action instead of the host right edge',
  )
  assert.match(
    platform,
    /function waitForXiaohongshuPublishHostAccepted[\s\S]+submit-loading/,
    'the host loading state must confirm whether the trusted click was accepted',
  )
  assert.match(
    platform,
    /const accepted = await waitForXiaohongshuPublishHostAccepted[\s\S]+if \(!accepted\)[\s\S]+按钮补点/,
    'a missed trusted click may be retried once only after the host confirms it stayed idle',
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
  assert.match(
    platform,
    /function findDouyinSubmitButton[\s\S]+querySelectorAll\('button'\)[\s\S]+=== '发布'/,
    'douyin must click the exact final publish button instead of a publish-labelled container',
  )
  assert.doesNotMatch(
    serviceWorker,
    /if \(isManagePage && explicitSuccess\)/,
    'douyin success toast is auxiliary and must not confirm a publish without a matching work record',
  )
  assert.match(
    serviceWorker,
    /expectedImageCount[\s\S]+new RegExp\(`\$\{expectedImageCount\}\\\\s\*张`\)/,
    'douyin image-text recovery must match the expected image badge count',
  )
  assert.match(
    serviceWorker,
    /dispatchTrustedClick[\s\S]+isAllowedPlatformUrl\('douyin', tab\.url\)/,
    'douyin final submission must be allowed to use a trusted browser-level click',
  )
  assert.match(
    serviceWorker,
    /browserTargetIdForTab\(tabId\)[\s\S]+browserTargetId: browserTargetId \|\| null/,
    'the extension must pass the exact browser target id to the local helper',
  )
  assert.match(
    helper,
    /selectUploadTargetPage\(pages,[\s\S]+browserTargetId: body\.browserTargetId \|\| ''/,
    'the local helper must select the upload page by browser target id',
  )
  assert.match(helper, /uploadTarget !== 'douyin_image_text_images'/)
  assert.match(helper, /puppeteerPageTargetId\(page\) !== String\(body\.browserTargetId\)/)
  assert.match(helper, /verifyDownloadedImageSignature\(image\)/)
  assert.match(helper, /buffer\.subarray\(8, 12\)\.toString\('ascii'\) === 'WEBP'/)
  assert.match(helper, /douyinImageText:\s*true/)
  assert.match(
    contentScript,
    /\['toutiao', 'baijiahao', 'douyin'\]\.includes\(platform\)[\s\S]+querySelectorAll\('img'\)[\s\S]+platform !== 'douyin' && !isUnsupportedPlatformImageUrl\(src\)/,
    'Douyin article content must be text-only so body images cannot masquerade as the article head image',
  )
  assert.match(
    platform,
    /function hasSectionImage[\s\S]+const section = findSection\(label, deps\)[\s\S]+section\.querySelectorAll\('img'\)/,
    'Douyin upload verification must inspect the matching upload section',
  )
  assert.doesNotMatch(
    platform,
    /function hasSectionImage[\s\S]{0,300}hasUploadResultNearLabel/,
    'Douyin upload verification must not use viewport proximity because an editor image can align with the head-image label',
  )
  assert.match(
    platform,
    /closest\('\[contenteditable="true"\], \.ProseMirror, \[class\*="editor"\], \[class\*="Editor"\]'\)/,
    'editor-owned images must never satisfy Douyin head-image verification',
  )
  assert.match(
    platform,
    /DOUYIN_BODY_IMAGE_NOT_ALLOWED[\s\S]+function hasEditorBodyImage/,
    'the final Douyin preflight must stop if an upload was accidentally routed into the article body',
  )
})

test('publish checks let an explicit published status override the expected schedule time', () => {
  const publishCheck = readProjectFile('geo-local-helper/src/publish-check.js')
  const server = readProjectFile('geo-local-helper/src/server.js')

  assert.match(publishCheck, /const found = hasTitle && hasConfirmedPublishedEvidence/)
  assert.match(publishCheck, /const found = hasTitle && hasPublishedNearTitle/)
  assert.match(server, /const found = hasTitle && hasLocation && hasPublishedSignal/)
  assert.doesNotMatch(server, /const found = hasTitle && hasLocation && !isBeforeScheduledAt && hasPublishedSignal/)
})

test('handled publish-check timeouts are counted against the claimed browser environment', () => {
  const server = readProjectFile('geo-local-helper/src/server.js')

  assert.match(
    server,
    /catch \(error\) \{\s*browserRuntimeErrorCounter\.record\(\s*error,\s*runtimeTask\?\.providerProfileId \|\| claimedProviderProfileId,\s*\)[\s\S]+if \(isPublishCheckTimeoutError\(error\)\)/,
    'publish-check errors must be attributed before timeout is converted to an unknown result',
  )
})
