import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const source = fs.readFileSync(new URL('../platform-xiaohongshu.js', import.meta.url), 'utf8')

function loadIdentityReader() {
  const nameElement = {
    textContent: 'wohaiyjhm',
    getAttribute: () => '',
  }
  const personalRoot = {
    innerText: 'wohaiyjhm 1 关注数 3 粉丝数 2 获赞与收藏 小红书账号：176977383',
    textContent: 'wohaiyjhm1关注数3粉丝数2获赞与收藏小红书账号：176977383',
    querySelectorAll: () => [nameElement],
  }
  const document = {
    body: personalRoot,
    scripts: [{ textContent: '{"nickname":"wohaiyjhm1关注数3粉丝数2获赞与收藏"}' }],
    querySelector: (selector) => selector.includes('personal') ? personalRoot : null,
    querySelectorAll: () => [],
  }
  const location = new URL('https://creator.xiaohongshu.com/new/home?source=official')
  const context = {
    URL,
    document,
    location,
    window: {},
  }
  context.globalThis = context
  vm.createContext(context)
  vm.runInContext(source, context)
  return context.__GEO_XIAOHONGSHU_PLATFORM__.createIdentityReader({
    normalizeText: (value) => String(value || '').replace(/\s+/g, ' ').trim(),
    normalizeAccountName: (value) => String(value || '').replace(/\s+/g, '').trim(),
    isVisibleElement: () => true,
    hasVisibleAncestor: () => true,
    isTopRightAccountElement: () => false,
  })
}

test('creator home precise account name suppresses polluted script candidates', () => {
  const identity = loadIdentityReader().readIdentity()

  assert.deepEqual(Array.from(identity.accountNames), ['wohaiyjhm'])
  assert.deepEqual(Array.from(identity.accountIds), ['176977383'])
  assert.match(identity.diagnostics, /nameSource=creator_home_precise/)
})
