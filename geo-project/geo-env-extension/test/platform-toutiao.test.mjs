import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const source = fs.readFileSync(new URL('../platform-toutiao.js', import.meta.url), 'utf8')

function loadPlatform() {
  const context = { URL }
  context.globalThis = context
  vm.createContext(context)
  vm.runInContext(source, context)
  return context.__GEO_TOUTIAO_PLATFORM__
}

test('recognizes the actual Toutiao graphic works list URL after publish redirect', () => {
  const platform = loadPlatform()

  assert.equal(platform.WORKS_LIST_URL, 'https://mp.toutiao.com/profile_v4/graphic/articles')
  assert.equal(platform.isWorksManageUrl(platform.WORKS_LIST_URL), true)
  assert.equal(platform.isWorksManageUrl('https://mp.toutiao.com/profile_v4/manage/content/all'), true)
  assert.equal(platform.isWorksManageUrl('https://mp.toutiao.com/profile_v4/graphic/publish'), false)
})
