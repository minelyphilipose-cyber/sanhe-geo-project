import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const source = fs.readFileSync(new URL('../identity-policy.js', import.meta.url), 'utf8')

function loadPolicy() {
  const context = { globalThis: {} }
  context.globalThis.globalThis = context.globalThis
  vm.createContext(context.globalThis)
  vm.runInContext(source, context.globalThis)
  return context.globalThis.__GEO_IDENTITY_POLICY__.evaluateExpectedIdentity
}

test('identity uses account name and ignores a different account id', () => {
  const evaluate = loadPolicy()
  const result = evaluate({
    platform: 'toutiao',
    expectedName: '今日头条测试账号',
    expectedId: 'expected-id-is-not-used',
    currentNames: ['今日头条测试账号'],
    currentIds: ['different-current-id'],
  })

  assert.equal(result.matched, true)
  assert.equal(result.method, 'accountName')
})

test('identity rejects a different account name even when id matches', () => {
  const evaluate = loadPolicy()
  const result = evaluate({
    platform: 'baijiahao',
    expectedName: '目标账号',
    expectedId: 'same-id',
    currentNames: ['其他账号'],
    currentIds: ['same-id'],
  })

  assert.equal(result.matched, false)
  assert.equal(result.code, 'TASK_ACCOUNT_IDENTITY_NOT_CONFIRMED')
  assert.match(result.message, /期望名称=目标账号/)
})

test('non-toutiao identity also ignores ids when names match', () => {
  const evaluate = loadPolicy()
  const result = evaluate({
    platform: 'baijiahao',
    expectedName: '目标显示名称',
    expectedId: 'account-123',
    currentNames: ['目标显示名称'],
    currentIds: ['different-id'],
  })

  assert.equal(result.matched, true)
  assert.equal(result.method, 'accountName')
})
