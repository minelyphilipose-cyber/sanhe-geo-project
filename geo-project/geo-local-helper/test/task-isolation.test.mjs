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
    /resolveFillTab\(options\.fillTabId \|\| null, task\.platform, payload\.publishUrl\)/,
    'extension must fill the original publish tab instead of reusing the identity tab',
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
})
