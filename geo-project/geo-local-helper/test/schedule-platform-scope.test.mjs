import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const server = fs.readFileSync(new URL('../src/server.js', import.meta.url), 'utf8')
const start = server.indexOf('function normalizePlatformList(')
const end = server.indexOf('\nstartLocalAgentSessionRefresher(config)', start)
const context = vm.createContext({})
vm.runInContext(server.slice(start, end), context)
const restrict = context.restrictSchedulePlatforms

test('without a local scope all backend-authorized platforms remain available', () => {
  const platforms = ['toutiao', 'sohu']
  assert.equal(restrict(platforms, undefined), platforms)
})

test('a Sohu scope excludes other platforms and normalizes configuration', () => {
  assert.deepEqual(Array.from(restrict(['toutiao', 'sohu', 'baijiahao'], [' SOHU ', 'sohu'])), ['sohu'])
})

test('a local scope cannot enable a platform excluded by the backend', () => {
  assert.deepEqual(Array.from(restrict(['toutiao'], ['sohu'])), [])
})

test('an empty scope disables claims and invalid configuration fails closed', () => {
  assert.deepEqual(Array.from(restrict(['sohu'], [])), [])
  assert.throws(() => restrict(['sohu'], 'sohu'), /must be an array/)
})
