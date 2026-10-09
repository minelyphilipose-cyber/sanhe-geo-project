import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const worker = fs.readFileSync(new URL('../service-worker.js', import.meta.url), 'utf8')
const source = worker.slice(worker.indexOf('async function apiRequest('), worker.indexOf('async function reportRuntimeStatus('))
const session = { sessionId: 56, extensionToken: 'ext.test', expiresAt: '2026-10-14T04:00:00', brandId: 99 }
const success = { code: 0, data: { sessionId: 56, expiresAt: '2026-11-07T04:00:00', renewed: true } }

function runtime(responses, overrides = {}) {
  const state = { sessions: { dev: { ...session } }, activeProfile: 'dev' }
  const requests = [], delays = []
  let saved = 0, cleared = 0
  const context = {
    EXTENSION_VERSION: '0.2.10', extensionSessionRefreshInFlight: new Map(),
    Headers, AbortController, setTimeout, clearTimeout,
    normalizeBaseUrl: value => value.replace(/\/$/, ''),
    delay: async ms => delays.push(ms),
    loadProfileStore: async () => state,
    saveProfileStore: async () => { cleared += 1 },
    saveActiveSession: async value => { saved += 1; state.sessions.dev = value },
    fetch: async (url, options) => {
      requests.push({ url, options })
      const result = responses.shift()
      if (result instanceof Error) throw result
      if (typeof result === 'function') return result(options)
      assert.ok(result, 'unexpected additional request')
      return { ok: (result.status || 200) < 400, status: result.status || 200, json: async () => result.body }
    },
    ...overrides,
  }
  vm.createContext(context)
  vm.runInContext(source, context)
  return { state, requests, delays, context, counts: () => ({ saved, cleared }),
    refresh: () => context.refreshExtensionSession({ apiBase: 'http://test/' }, state.sessions.dev, { force: true }) }
}

test('a temporary network failure retries refresh and keeps the same binding', async () => {
  const run = runtime([new TypeError('Failed to fetch'), { body: success }])
  const result = await run.refresh()
  assert.equal(result.extensionToken, session.extensionToken)
  assert.equal(result.sessionId, 56)
  assert.equal(result.expiresAt, success.data.expiresAt)
  assert.deepEqual(run.counts(), { saved: 1, cleared: 0 })
  assert.deepEqual(run.delays, [500])
  assert.ok(run.requests.every(request => request.url.endsWith('/extension/token/refresh')))
})

test('three failed refresh attempts retain authorization for the next heartbeat', async () => {
  for (const failure of [new TypeError('Failed to fetch'), new TypeError('extension token invalid'), { status: 503, body: {} }, { status: 500, body: { code: 70002, message: 'extension token invalid' } }, { status: 408, body: { code: 70002, message: 'extension token invalid' } }, { body: {} }]) {
    const run = runtime([failure, failure, failure])
    await assert.rejects(run.refresh())
    assert.deepEqual(run.state.sessions.dev, session)
    assert.deepEqual(run.counts(), { saved: 0, cleared: 0 })
    assert.equal(run.requests.length, 3)
    assert.deepEqual(run.delays, [500, 1000])
  }
})

test('a stalled refresh is aborted after ten seconds and retries are bounded', async () => {
  let expire, clearedTimers = 0
  const stalled = options => new Promise((resolve, reject) => {
    options.signal.addEventListener('abort', () => {
      const error = new Error('aborted'); error.name = 'AbortError'; reject(error)
    })
    expire()
  })
  const run = runtime([stalled, stalled, stalled], {
    setTimeout: (callback, ms) => { assert.equal(ms, 10000); expire = callback; return 1 },
    clearTimeout: () => { clearedTimers += 1 },
  })
  await assert.rejects(run.refresh(), error => error.name === 'AbortError')
  assert.equal(run.requests.length, 3)
  assert.equal(clearedTimers, 3)
  assert.deepEqual(run.state.sessions.dev, session)
})

test('only an explicit expired or invalid authorization clears the binding without retry', async () => {
  for (const message of ['extension token expired', 'extension token invalid']) {
    const run = runtime([{ body: { code: 70002, message } }])
    await assert.rejects(run.refresh(), error => error.code === 70002)
    assert.equal(run.state.sessions.dev, null)
    assert.equal(run.requests.length, 1)
    assert.deepEqual(run.counts(), { saved: 0, cleared: 1 })
  }
})

test('generic authentication and permission failures preserve the binding', async () => {
  for (const status of [401, 403]) {
    const run = runtime([{ status, body: { code: 403, message: 'Forbidden' } }])
    await assert.rejects(run.refresh())
    assert.deepEqual(run.state.sessions.dev, session)
    assert.equal(run.requests.length, 1)
    assert.equal(run.counts().cleared, 0)
  }
})

test('rate limiting and timeout are retried without dropping credentials', async () => {
  const timeout = new Error('request aborted'); timeout.name = 'AbortError'
  const run = runtime([{ status: 429, body: {} }, timeout, { body: success }])
  await run.refresh()
  assert.equal(run.requests.length, 3)
  assert.equal(run.counts().cleared, 0)
  assert.ok(run.requests.every(request => request.options.signal instanceof AbortSignal))
})

test('simultaneous heartbeat and task calls share a refresh request', async () => {
  let finish
  const run = runtime([async () => new Promise(resolve => { finish = () => resolve({ ok: true, status: 200, json: async () => success }) })])
  const first = run.refresh(), second = run.refresh()
  finish()
  const [a, b] = await Promise.all([first, second])
  assert.equal(a, b)
  assert.equal(run.requests.length, 1)
  assert.equal(run.counts().saved, 1)
})

test('a failed request cannot clear a newly rebound token', async () => {
  let finish
  const run = runtime([async () => new Promise(resolve => { finish = () => resolve({ ok: true, status: 200, json: async () => ({ code: 70002, message: 'extension token invalid' }) }) })])
  const pending = run.refresh()
  run.state.sessions.dev = { ...session, extensionToken: 'ext.rebound' }
  finish()
  await assert.rejects(pending)
  assert.equal(run.state.sessions.dev.extensionToken, 'ext.rebound')
  assert.equal(run.counts().cleared, 0)
})
