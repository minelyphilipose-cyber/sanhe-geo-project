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

function loadTitleNormalizer() {
  const start = contentScriptSource.indexOf('function normalizeToutiaoTitle(')
  const end = contentScriptSource.indexOf('function assertToutiaoTitleReadyForPublish(', start)
  const context = {}
  vm.createContext(context)
  vm.runInContext(contentScriptSource.slice(start, end), context)
  return context.normalizeToutiaoTitle
}

test('Toutiao preserves natural question and statement titles without requiring a brand', () => {
  const normalize = loadTitleNormalizer()
  for (const title of [
    '阜阳选SPA，服务项目和收费怎么核对？',
    '阜阳SPA价格不同，差别主要在哪里？',
    '阜阳预约SPA前，需要确认哪些服务信息？',
    '合肥企业建设知识库，先梳理哪些业务资料？',
    '阜阳SPA服务的项目设置与流程说明',
  ]) assert.equal(normalize(title), title)
})

test('Toutiao rejects overlong titles instead of silently truncating or stripping punctuation', () => {
  const normalize = loadTitleNormalizer()
  const title = '阜阳' + '😀'.repeat(27) + '？'
  assert.equal(Array.from(title).length, 30)
  assert.equal(normalize(title), title)
  assert.throws(() => normalize(title + '续'), /TOUTIAO_TITLE_INVALID.*不会自动截断/)
  assert.throws(() => normalize(' '), /TOUTIAO_TITLE_INVALID/)
  assert.throws(() => normalize('阜阳SPA闭眼选这家'), /强推荐/)
})

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
