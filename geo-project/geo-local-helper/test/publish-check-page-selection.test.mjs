import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const source = readFileSync(new URL('../src/server.js', import.meta.url), 'utf8')
function load() {
  const context = vm.createContext({
    URL, Date, PUBLISH_CHECK_PAGE_ATTACH_TIMEOUT_MS: 5000,
    withTimeout: (promise, timeout) => Promise.race([
      promise, new Promise((_, reject) => setTimeout(() => reject(new Error('attachment timeout')), Math.min(timeout, 5))),
    ]),
  })
  for (const name of ['reuseOrCreatePublishCheckPage', 'isReusablePublishCheckPage']) {
    const match = new RegExp(`^(?:async )?function ${name}\\(`, 'm').exec(source)
    vm.runInContext(source.slice(match.index, source.indexOf('\n}', match.index) + 2), context)
  }
  return context.reuseOrCreatePublishCheckPage
}

const worksUrls = {
  toutiao: 'https://mp.toutiao.com/profile_v4/graphic/articles',
  baijiahao: 'https://baijiahao.baidu.com/builder/rc/content?app_id=1',
  xiaohongshu: 'https://creator.xiaohongshu.com/new/note-manager',
  zhihu: 'https://www.zhihu.com/creator/manage/creation/article',
  douyin: 'https://creator.douyin.com/creator-micro/content/manage',
  sohu: 'https://mp.sohu.com/mpfe/v4/contentManagement/first/page',
}
function target(url, page) {
  return { type: () => 'page', url: () => url, page }
}
function browser(targets, newPage = async () => { throw new Error('Should reuse the works page') }) {
  return { targets: () => targets, newPage, pages: async () => { throw new Error('Unrelated browser page initialization failed') } }
}
for (const [platform, url] of Object.entries(worksUrls)) {
  test(`${platform}: a broken unrelated tab does not block the publication check`, async () => {
    let unrelatedConnections = 0
    const page = { url: () => url, isClosed: () => false }
    const state = browser([
      target('http://localhost:3000/login', async () => { unrelatedConnections++; throw new Error('Page.enable timed out') }),
      target(url, async () => page),
    ])
    const result = await load()(state, platform, url)
    assert.equal(result.page, page)
    assert.equal(result.created, false)
    assert.equal(unrelatedConnections, 0)
  })
}
test('a hung matching tab falls back to a temporary check page within the attachment budget', async () => {
  const url = worksUrls.sohu
  const page = { isClosed: () => false }
  const result = await load()(browser([target(url, () => new Promise(() => {}))], async () => page), 'sohu', url)
  assert.equal(result.page, page)
  assert.equal(result.created, true)
})
test('a disappeared works tab is skipped and a healthy matching tab is reused', async () => {
  const url = worksUrls.sohu
  const page = { isClosed: () => false }
  const result = await load()(browser([
    target(url, async () => { throw new Error('Target closed') }),
    target(url, async () => page),
  ]), 'sohu', url)
  assert.equal(result.page, page)
  assert.equal(result.created, false)
})
test('a missing works page creates a temporary page without touching editor tabs', async () => {
  const page = { isClosed: () => false }
  const result = await load()(browser([
    target('https://mp.sohu.com/mpfe/v4/contentManagement/news/addarticle', async () => { throw new Error('Editor must remain untouched') }),
  ], async () => page), 'sohu', worksUrls.sohu)
  assert.equal(result.page, page)
  assert.equal(result.created, true)
})
