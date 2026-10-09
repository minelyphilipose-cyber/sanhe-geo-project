import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const worker = fs.readFileSync(new URL('../service-worker.js', import.meta.url), 'utf8')
function extract(name, nextName) {
  return worker.slice(worker.indexOf(`async function ${name}(`), worker.indexOf(`async function ${nextName}(`))
}
function recovery({ url = 'https://mp.sohu.com/mpfe/v4/contentManagement/first/page', verification = { found: true, submitted: true, platformStatus: 'reviewing', verified: false } } = {}) {
  const messages = []
  const context = {
    chrome: { tabs: {
      get: async id => { assert.equal(id, 77); return { id, url } },
      sendMessage: async (id, message) => { assert.equal(id, 77); messages.push(message); return { ok: true, result: verification } },
    } },
    __GEO_SOHU_PLATFORM__: { isWorksManageUrl: value => value === 'https://mp.sohu.com/mpfe/v4/contentManagement/first/page' },
    ensureContentScript: async () => {}, delay: async () => {},
  }
  vm.createContext(context)
  vm.runInContext(extract('recoverSohuPublishAfterFillError', 'recoverBaijiahaoAfterMessageChannelClosed'), context)
  return { recover: context.recoverSohuPublishAfterFillError, messages }
}
const payload = { autoPublish: true, title: '测试文章', expectedAccountName: '测试账号' }
const error = new Error('message port closed after navigation')

test('only an assigned Sohu backend schedule enables automatic submission', () => {
  const context = { normalizePlatform: value => value }
  vm.createContext(context)
  const start = worker.indexOf('function applySohuScheduleOptions(')
  const end = worker.indexOf('function applyDouyinImageTextPlatformOptions(', start)
  vm.runInContext(worker.slice(start, end), context)
  const task = { platform: 'sohu', schedule: { id: 483, scheduleStrategy: 'backend_delayed_publish' } }
  const input = { platformOptions: { scheduleId: 483, scheduleStrategy: 'backend_delayed_publish' }, scheduleRequired: true }
  const result = context.applySohuScheduleOptions(input, task)
  assert.equal(result.autoPublish, true)
  assert.equal(result.scheduleRequired, false)
  assert.equal(context.applySohuScheduleOptions({ platformOptions: input.platformOptions }, { platform: 'sohu' }).autoPublish, true)
  assert.equal(context.applySohuScheduleOptions({}, task).autoPublish, undefined)
  for (const candidate of [{}, { ...task, platform: 'zhihu' }, { ...task, schedule: { ...task.schedule, id: 484 } }]) {
    assert.equal(context.applySohuScheduleOptions({ platformOptions: input.platformOptions }, candidate).autoPublish, undefined)
  }
})

test('Sohu navigation recovery reads the same tab and keeps review pending', async () => {
  const state = recovery()
  const result = await state.recover(77, { taskId: 'task-1' }, payload, error)
  assert.equal(result.publishOptions.submitted, true)
  assert.equal(result.publishOptions.published, false)
  assert.equal(result.publishOptions.publishVerification.verified, false)
  assert.equal(state.messages.length, 1)
  assert.equal(state.messages[0].type, 'GEO_ENV_READ_SOHU_PUBLISH_RESULT')
  assert.equal(state.messages[0].payload.requireSubmissionAttempt, true)
})

test('Sohu recovery retains errors for absent, rejected, or unrelated records without refilling', async () => {
  for (const options of [
    { verification: { found: false } },
    { verification: { found: true, submitted: true, platformStatus: 'rejected' } },
    { url: 'https://mp.sohu.com/mpfe/v4/contentManagement/news/addarticle' },
  ]) {
    const state = recovery(options)
    await assert.rejects(state.recover(77, { taskId: 'task-1' }, payload, error), value => value === error)
    assert.ok(state.messages.every(message => message.type === 'GEO_ENV_READ_SOHU_PUBLISH_RESULT'))
  }
  const state = recovery()
  await assert.rejects(state.recover(77, {}, { ...payload, autoPublish: false }, error), value => value === error)
  assert.equal(state.messages.length, 0)
})

test('Sohu fill message failures cannot replay the whole publish operation', () => {
  const start = worker.indexOf('function isAutomatedBrowserPublishPlatform(')
  const end = worker.indexOf('function fillMessageTimeoutMs(', start)
  const context = { normalizePlatform: value => value }
  vm.createContext(context)
  vm.runInContext(worker.slice(start, end), context)
  assert.equal(context.isAutomatedBrowserPublishPlatform('sohu'), true)
})

test('Sohu submission reservation is durable and serializes concurrent attempts', async () => {
  const stored = {}
  function loadWorker() {
    const context = { chrome: { storage: { local: {
      get: async key => ({ [key]: stored[key] }),
      set: async value => Object.assign(stored, value),
    } } }, Date }
    vm.createContext(context)
    const start = worker.indexOf('async function reserveSohuSubmissionAttempt(')
    const end = worker.indexOf('async function recoverBaijiahaoAfterMessageChannelClosed(', start)
    vm.runInContext(`let sohuSubmissionWriteChain = Promise.resolve();\n${worker.slice(start, end)}`, context)
    return context.reserveSohuSubmissionAttempt
  }
  const reserve = loadWorker()
  const task = { taskId: 10001, title: '测试文章', expectedAccountName: '测试账号' }
  const results = await Promise.allSettled([reserve(task), reserve(task)])
  assert.equal(results.filter(result => result.status === 'fulfilled').length, 1)
  assert.equal(results.filter(result => result.status === 'rejected').length, 1)
  await assert.rejects(loadWorker()(task), /SOHU_ALREADY_SUBMITTED/)
  await assert.rejects(reserve({ ...task, taskId: null }), /SOHU_TASK_ID_REQUIRED/)
})

test('Sohu editor readiness accepts a rendered editor while the tab is still loading', async () => {
  let completeCalls = 0
  const context = {
    normalizePlatform: value => value,
    chrome: { tabs: { get: async () => ({ status: 'loading', url: 'sohu-editor' }) } },
    __GEO_SOHU_PLATFORM__: { isPublishUrl: url => url === 'sohu-editor' },
    executeScriptOnPlatformTab: async () => [{ result: true }],
    waitForTabComplete: async () => { completeCalls++ },
    delay: async () => {}, codedError: (code, message) => new Error(`${code}:${message}`),
  }
  vm.createContext(context)
  const start = worker.indexOf('async function waitForPublishEditorReady(')
  const end = worker.indexOf('function isReusablePublishTab(', start)
  vm.runInContext(worker.slice(start, end), context)
  await context.waitForPublishEditorReady(77, 'sohu')
  assert.equal(completeCalls, 0)
  await context.waitForPublishEditorReady(77, 'toutiao')
  assert.equal(completeCalls, 1)
  context.chrome.tabs.get = async () => ({ url: 'unexpected-page' })
  await assert.rejects(context.waitForPublishEditorReady(77, 'sohu'), /PLATFORM_TAB_REDIRECTED/)
})
