import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const source = fs.readFileSync(new URL('../content-script.js', import.meta.url), 'utf8')

function loadContentScript({ href, document }) {
  const window = {
    addEventListener: () => {},
    getComputedStyle: () => ({ display: 'block', visibility: 'visible', opacity: '1' }),
  }
  window.top = window
  const context = {
    URL,
    URLSearchParams,
    console,
    document,
    location: new URL(href),
    window,
    chrome: {
      runtime: {
        onMessage: { addListener: () => {} },
        sendMessage: () => Promise.resolve(),
      },
    },
    setTimeout: () => 0,
    setInterval: () => 0,
    clearInterval: () => {},
  }
  context.globalThis = context
  vm.createContext(context)
  vm.runInContext(source, context)
  context.isVisibleElement = () => true
  context.hasVisibleAncestor = () => true
  return context
}

function baseDocument() {
  return {
    addEventListener: () => {},
    body: { innerText: '', textContent: '' },
    scripts: [],
    visibilityState: 'visible',
    querySelector: () => null,
    querySelectorAll: () => [],
  }
}

test('Douyin creator home precise name suppresses product navigation candidates', () => {
  const document = baseDocument()
  const preciseName = {
    textContent: '吃梨',
    getAttribute: () => '',
  }
  const homeRoot = {
    innerText: '吃梨 抖音号：53785383663 抖音官网 巨量星图 企业号 直播开放平台',
    textContent: '吃梨抖音号：53785383663抖音官网巨量星图企业号直播开放平台',
  }
  document.body = homeRoot
  document.querySelector = (selector) => selector === '[id^="garfish_app_for_douyin_creator_pc_home_"]' ? homeRoot : null
  document.querySelectorAll = (selector) => selector.includes('[class*="header-"]') ? [preciseName] : []
  const context = loadContentScript({
    href: 'https://creator.douyin.com/creator-micro/home',
    document,
  })

  const identity = context.readDouyinIdentity()

  assert.deepEqual(Array.from(identity.accountNames), ['吃梨'])
  assert.deepEqual(Array.from(identity.accountIds), ['53785383663'])
  assert.match(identity.diagnostics, /nameSource=creator_home_precise/)
})

test('Toutiao profile precise username suppresses concatenated profile text', () => {
  const document = baseDocument()
  const nameElement = {
    textContent: 'jnhbdxh',
    getAttribute: () => '',
  }
  const idRow = {
    querySelector: (selector) => selector.includes('label')
      ? { textContent: '头条号ID' }
      : { textContent: '1866844757921799' },
  }
  document.body = {
    innerText: '用户名 jnhbdxh 编辑用户简介 填写简介更容易获得大家的关注哦 头条号ID 1866844757921799',
    textContent: '用户名jnhbdxh编辑用户简介填写简介更容易获得大家的关注哦头条号ID1866844757921799',
  }
  document.querySelector = (selector) => selector.includes('user-detail-block') ? nameElement : null
  document.querySelectorAll = (selector) => selector.includes('block-item') ? [idRow] : []
  const context = loadContentScript({
    href: 'https://mp.toutiao.com/profile_v4/personal/info',
    document,
  })

  const identity = context.readToutiaoIdentity()

  assert.deepEqual(Array.from(identity.accountNames), ['jnhbdxh'])
  assert.deepEqual(Array.from(identity.accountIds), ['1866844757921799'])
  assert.match(identity.diagnostics, /nameSource=profile_detail_precise/)
})
