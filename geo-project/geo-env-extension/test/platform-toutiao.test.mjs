import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const source = fs.readFileSync(new URL('../platform-toutiao.js', import.meta.url), 'utf8')
const contentScriptSource = fs.readFileSync(new URL('../content-script.js', import.meta.url), 'utf8')
const serviceWorkerSource = fs.readFileSync(new URL('../service-worker.js', import.meta.url), 'utf8')

function loadPlatform() {
  const context = { URL }
  context.globalThis = context
  vm.createContext(context)
  vm.runInContext(source, context)
  return context.__GEO_TOUTIAO_PLATFORM__
}

test('recognizes the actual Toutiao graphic works list URL after publish redirect', () => {
  const platform = loadPlatform()

  assert.equal(platform.WORKS_LIST_URL, 'https://mp.toutiao.com/profile_v4/graphic/articles')
  assert.equal(platform.isWorksManageUrl(platform.WORKS_LIST_URL), true)
  assert.equal(platform.isWorksManageUrl('https://mp.toutiao.com/profile_v4/manage/content/all'), true)
  assert.equal(platform.isWorksManageUrl('https://mp.toutiao.com/profile_v4/graphic/publish'), false)
})

test('Toutiao delegates the cached redirected list to refresh-first service-worker verification', () => {
  assert.match(
    contentScriptSource,
    /async function verifyToutiaoScheduledWorkInList[\s\S]+头条已进入作品管理页，需刷新列表后确认定时文章/,
  )
  assert.doesNotMatch(
    contentScriptSource,
    /async function verifyToutiaoScheduledWorkInList[\s\S]+findToutiaoScheduledWorkMatch\(value, context\)[\s\S]+return \{\s*verified: true/,
  )
  assert.match(
    serviceWorkerSource,
    /recoverToutiaoScheduleAfterWorksListTimeout[\s\S]+refreshAndInspectPlatformWorksList\([\s\S]+inspectToutiaoWorksListTab/,
  )
  assert.match(
    serviceWorkerSource,
    /async function refreshAndInspectPlatformWorksList[\s\S]+await reloadPlatformWorksList\(tabId,[\s\S]+latest = await inspector\(tabId, context, refreshAttempt\)/,
  )
})
