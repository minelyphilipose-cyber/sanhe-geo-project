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

function visibleElement(textContent = '') {
  return {
    textContent,
    parentElement: null,
    children: [],
    getBoundingClientRect: () => ({ width: 430, height: 496 }),
    querySelectorAll() {
      return this.children.flatMap((child) => [child, ...child.querySelectorAll('*')])
    },
  }
}

function append(parent, child) {
  parent.children.push(child)
  child.parentElement = parent
  return child
}

test('matches the Zhihu publish-success modal by stable semantic signals', () => {
  const body = visibleElement()
  const modal = append(body, visibleElement('发布成功 感谢你的第 24 篇创作！ 转发到想法 更多分享'))
  append(modal, visibleElement('发布成功'))
  const closeButton = append(modal, visibleElement(''))
  const document = {
    body,
    documentElement: visibleElement(),
    querySelectorAll: (selector) => selector === 'button[aria-label="关闭"]' ? [closeButton] : [],
  }
  const platform = loadPlatform({
    document,
    getComputedStyle: () => ({ display: 'block', visibility: 'visible', opacity: '1' }),
  })

  assert.deepEqual(
    JSON.parse(JSON.stringify(platform.detectPublishSuccessModal({ isVisibleElement: () => true }))),
    {
      detected: true,
      title: '发布成功',
      creationCount: 24,
      confirmationText: '感谢你的第24篇创作！',
      closeButtonPresent: true,
      forwardToIdeaPresent: true,
      moreSharePresent: true,
      signature: 'title+close_button+creation_or_share',
    },
  )
})

test('matches the observed Zhihu success title even when the close button is not yet detectable', () => {
  const body = visibleElement()
  const modal = append(body, visibleElement('发布成功 感谢你的第 52 篇创作！ 转发到想法 更多分享'))
  const title = append(modal, visibleElement('发布成功'))
  const document = {
    body,
    documentElement: visibleElement(),
    querySelectorAll: (selector) => selector.includes('.css-t5fqv4') ? [title] : [],
  }
  const platform = loadPlatform({
    document,
    getComputedStyle: () => ({ display: 'block', visibility: 'visible', opacity: '1' }),
  })

  assert.deepEqual(
    JSON.parse(JSON.stringify(platform.detectPublishSuccessModal({ isVisibleElement: () => true }))),
    {
      detected: true,
      title: '发布成功',
      creationCount: 52,
      confirmationText: '感谢你的第52篇创作！',
      closeButtonPresent: false,
      forwardToIdeaPresent: true,
      moreSharePresent: true,
      signature: 'title+creation_or_share',
    },
  )
})

test('does not treat unrelated page text as the Zhihu publish-success modal', () => {
  const body = visibleElement('发布成功')
  const document = {
    body,
    documentElement: visibleElement(),
    querySelectorAll: () => [],
  }
  const platform = loadPlatform({ document })

  assert.equal(platform.detectPublishSuccessModal({ isVisibleElement: () => true }), null)
})
