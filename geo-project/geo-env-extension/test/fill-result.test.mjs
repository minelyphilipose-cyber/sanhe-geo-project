import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const source = fs.readFileSync(new URL('../fill-result.js', import.meta.url), 'utf8')

function loadNormalizer() {
  const context = { globalThis: {} }
  context.globalThis.globalThis = context.globalThis
  vm.createContext(context.globalThis)
  vm.runInContext(source, context.globalThis)
  return context.globalThis.__GEO_FILL_RESULT__.normalizeFillResult
}

test('normalizes verified scheduled fill result', () => {
  const normalizeFillResult = loadNormalizer()
  const result = normalizeFillResult({
    titleFilled: true,
    contentFilled: true,
    publishOptions: {
      scheduled: true,
      publishVerification: {
        verified: true,
        platformStatus: 'scheduled',
        platformScheduledAt: '2026-06-12 09:30',
      },
    },
  }, { platform: 'toutiao', taskId: 10 })

  assert.equal(result.platform, 'toutiao')
  assert.equal(result.taskId, 10)
  assert.equal(result.publishOptions.filled, true)
  assert.equal(result.publishOptions.scheduled, true)
  assert.equal(result.publishOptions.published, false)
  assert.equal(result.publishOptions.publishVerification.verified, true)
})

test('normalizes verified published fill result', () => {
  const normalizeFillResult = loadNormalizer()
  const result = normalizeFillResult({
    publishOptions: {
      publishVerification: {
        verified: 'true',
        platformStatus: 'published',
        publishedUrl: 'https://example.test/article/1',
      },
    },
  }, { platform: 'zhihu' })

  assert.equal(result.publishOptions.scheduled, false)
  assert.equal(result.publishOptions.published, true)
  assert.equal(result.publishOptions.publishVerification.verified, true)
})

test('does not promote unverified scheduled signal to backend success', () => {
  const normalizeFillResult = loadNormalizer()
  const result = normalizeFillResult({
    publishOptions: {
      scheduled: true,
      publishVerification: {
        platformStatus: 'scheduled',
      },
    },
  }, { platform: 'baijiahao' })

  assert.equal(result.publishOptions.scheduled, true)
  assert.equal(result.publishOptions.published, false)
  assert.equal(result.publishOptions.publishVerification.verified, false)
})

test('adds complete publish options shape for fill-only result', () => {
  const normalizeFillResult = loadNormalizer()
  const result = normalizeFillResult({
    titleFilled: true,
    contentFilled: true,
  }, { platform: 'xiaohongshu' })

  assert.equal(result.publishOptions.filled, true)
  assert.equal(result.publishOptions.scheduled, false)
  assert.equal(result.publishOptions.published, false)
  assert.equal(result.publishOptions.publishVerification.verified, false)
  assert.equal(result.publishOptions.publishVerification.platformStatus, '')
})
