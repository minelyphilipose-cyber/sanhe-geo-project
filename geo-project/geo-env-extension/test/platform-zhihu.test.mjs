import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const source = fs.readFileSync(new URL('../platform-zhihu.js', import.meta.url), 'utf8')

function loadPlatform(overrides = {}) {
  const context = {
    URL,
    document: {},
    location: new URL('https://zhuanlan.zhihu.com/write'),
    ...overrides,
  }
  context.globalThis = context
  vm.createContext(context)
  vm.runInContext(source, context)
  return context.__GEO_ZHIHU_PLATFORM__
}

function loadIdentityReader({ href, organizationName, organizationHref }) {
  const organizationLink = {
    getAttribute: (name) => name === 'href' ? organizationHref : '',
    textContent: organizationName,
  }
  const organizationRoot = {
    querySelector: (selector) => selector === 'a[href*="/org/"]' ? organizationLink : organizationLink,
  }
  const document = {
    querySelector: (selector) => selector === '.OrgVerifyDesc' ? organizationRoot : null,
  }
  return loadPlatform({ document, location: new URL(href) }).createIdentityReader().readIdentity()
}

test('reads enterprise identity from the Zhihu organization verification page', () => {
  const identity = loadIdentityReader({
    href: 'https://www.zhihu.com/organization/verify/levelup?geoEnvLoginReport=1',
    organizationName: '得闲SPA',
    organizationHref: '/org/de-xian-spa',
  })

  assert.deepEqual(Array.from(identity.accountIds), ['de-xian-spa'])
  assert.deepEqual(Array.from(identity.accountNames), ['得闲SPA'])
  assert.equal(identity.profileUrls[0], 'https://www.zhihu.com/org/de-xian-spa')
  assert.match(identity.diagnostics, /source=organization_verify/)
})

test('treats an uncertain result after clicking publish as verification-only recovery', () => {
  const platform = loadPlatform()

  assert.equal(
    platform.classifyFailureCode('知乎发布后未检测到完成状态', 'zhihu'),
    'ZHIHU_PUBLISH_NOT_CONFIRMED',
  )
  assert.equal(platform.isRetryableFailureCode('ZHIHU_PUBLISH_NOT_CONFIRMED'), true)
})
