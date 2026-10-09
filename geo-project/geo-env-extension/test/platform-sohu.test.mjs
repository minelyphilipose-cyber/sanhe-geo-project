import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const source = fs.readFileSync(new URL('../platform-sohu.js', import.meta.url), 'utf8')
const policy = fs.readFileSync(new URL('../identity-policy.js', import.meta.url), 'utf8')
const visible = { getBoundingClientRect: () => ({ width: 100, height: 30 }) }

function load({ names = ['吃饱了就困'], href = 'https://mp.sohu.com/mpfe/v4/contentManagement/news/addarticle?contentStatus=1', message = '发布成功，正在审核中', rows = [], afterClick, quota = '您今天还能发 4 篇文章', images = [{ src: 'https://res.mp.sohu.com/test', complete: true, naturalWidth: 1280 }] } = {}) {
  let clicks = 0
  const storage = new Map()
  const radio = { ...visible, textContent: '含有AI生成内容', classList: { contains: () => true }, querySelector: () => ({ checked: true }) }
  const button = { ...visible, textContent: '发布', click: () => { clicks += 1; afterClick?.(context) } }
  const success = { ...visible, textContent: message }
  const document = {
    body: { innerText: quota },
    querySelectorAll: selector => selector.includes('#header-user .user-name') ? names.map(textContent => ({ ...visible, textContent }))
      : selector === '.info-radio-label' ? [radio]
        : selector === '.publish-report-btn' ? [button]
          : selector === '.article-content-info' ? rows
            : selector === '#editor .ql-editor img' ? images
            : selector.includes('.el-message--success') && clicks && message ? [success] : [],
    querySelector: selector => selector === '.publish-title input' ? { value: '如何整理一份清晰的工作交接清单' }
      : selector === '#editor .ql-editor' ? { textContent: '这是一篇中立的工作交接建议文章。' } : null,
  }
  const context = { document, location: new URL(href), URL, console, Event, Date,
    getComputedStyle: () => ({ display: 'block', visibility: 'visible', opacity: '1' }),
    sessionStorage: { getItem: key => storage.get(key), setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) },
  }
  context.globalThis = context
  vm.createContext(context)
  vm.runInContext(policy, context)
  vm.runInContext(source, context)
  return { platform: context.__GEO_SOHU_PLATFORM__, clicks: () => clicks, storage }
}

const payload = { title: '如何整理一份清晰的工作交接清单', expectedAccountName: '吃饱了就困', autoPublish: true }

test('Sohu blocks depleted or unknown daily quota and unuploaded illustrations before submitting', async () => {
  for (const options of [{ quota: '您今天还能发 0 篇文章' }, { quota: '' }, { images: [{ src: 'http://localhost/test.png', complete: true, naturalWidth: 1280 }] }]) {
    const state = load(options)
    await assert.rejects(state.platform.createPublishOptionsAdapter({ delay: async () => {} }).fillPublishOptions(payload), /SOHU_DAILY_|SOHU_IMAGE_UPLOAD_NOT_CONFIRMED/)
    assert.equal(state.clicks(), 0)
  }
})

test('Sohu permits text-only publishing and still prevents duplicate submissions', async () => {
  const state = load({ images: [] })
  const adapter = state.platform.createPublishOptionsAdapter({ delay: async () => {} })
  const result = await adapter.fillPublishOptions(payload)
  assert.equal(result.submitted, true)
  assert.equal(result.published, false)
  assert.equal(state.clicks(), 1)
  await assert.rejects(adapter.fillPublishOptions(payload), /SOHU_ALREADY_SUBMITTED/)
  assert.equal(state.clicks(), 1)
})

test('Sohu illustrations require supported files and a loaded platform-hosted image', async () => {
  const { platform } = load()
  for (const file of [{ type: 'image/svg+xml', size: 100 }, { type: 'image/png', size: 11 * 1024 * 1024 }, { type: 'image/png', size: 0 }]) {
    await assert.rejects(platform.fillContentImages({ images: [{ url: 'https://example.test/a.png' }] }, payload, { fetchImageAsFile: async () => file }), /SOHU_IMAGE_FILE_INVALID/)
  }
  assert.equal(platform.isUploadedContentImage({ src: 'https://res.mp.sohu.com/test', complete: true, naturalWidth: 1280 }), true)
  assert.equal(platform.isUploadedContentImage({ src: 'https://res.mp.sohu.com.evil.test/test', complete: true, naturalWidth: 1280 }), false)
  assert.equal(platform.isUploadedContentImage({ src: 'https://res.mp.sohu.com/test', complete: true, naturalWidth: 0 }), false)
})

test('Sohu identity reads only the signed-in account controls', () => {
  assert.deepEqual(Array.from(load().platform.readIdentity().accountNames), ['吃饱了就困'])
  assert.deepEqual(Array.from(load({ href: 'https://mp.sohu.com/profile?xpt=other' }).platform.readIdentity().accountNames), [])
  assert.deepEqual(Array.from(load({ href: 'https://mp.sohu.com.evil.example/mpfe/v4/personalCenter/account' }).platform.readIdentity().accountNames), [])
})

test('Sohu URL boundaries accept the supplied account and editor pages only', () => {
  const { platform } = load()
  assert.equal(platform.isAccountUrl(platform.ACCOUNT_URL), true)
  assert.equal(platform.isAccountUrl(platform.PUBLISH_URL), false)
  assert.equal(platform.isPublishUrl(platform.PUBLISH_URL), true)
  assert.equal(platform.isPublishUrl('http://mp.sohu.com/mpfe/v4/contentManagement/news/addarticle'), false)
  assert.equal(platform.isPublishUrl('https://mp.sohu.com.evil.example/mpfe/v4/contentManagement/news/addarticle'), false)
})

test('Sohu blocks missing, mismatched and ambiguous account names before submitting', async () => {
  for (const names of [[], ['其他账号'], ['吃饱了就困', '其他账号']]) {
    const state = load({ names })
    await assert.rejects(state.platform.createPublishOptionsAdapter({ delay: async () => {} }).fillPublishOptions(payload), /IDENTITY|MULTIPLE_IDENTITIES/)
    assert.equal(state.clicks(), 0)
  }
})

test('Sohu uses AI declaration and records submission without claiming published status', async () => {
  const state = load()
  const adapter = state.platform.createPublishOptionsAdapter({ delay: async () => {} })
  const result = await adapter.fillPublishOptions(payload)
  assert.equal(result.creationStatement, '含有AI生成内容')
  assert.equal(result.submitted, true)
  assert.equal(result.published, false)
  assert.equal(result.publishVerification.verified, false)
  assert.equal(state.clicks(), 1)
  await assert.rejects(adapter.fillPublishOptions(payload), /SOHU_ALREADY_SUBMITTED/)
  assert.equal(state.clicks(), 1)
})

test('Sohu preserves unknown submission state and never retries after a timeout', async () => {
  const state = load({ message: '' })
  const adapter = state.platform.createPublishOptionsAdapter({ delay: async () => {} })
  await assert.rejects(adapter.fillPublishOptions(payload), /SOHU_PUBLISH_NOT_CONFIRMED/)
  await assert.rejects(adapter.fillPublishOptions(payload), /SOHU_ALREADY_SUBMITTED/)
  assert.equal(state.clicks(), 1)
})

test('Sohu filling without autoPublish never submits, and title mismatch is blocked', async () => {
  const state = load()
  const adapter = state.platform.createPublishOptionsAdapter({ delay: async () => {} })
  await adapter.fillPublishOptions({ ...payload, autoPublish: false })
  assert.equal(state.clicks(), 0)
  await assert.rejects(adapter.fillPublishOptions({ ...payload, title: '另一篇未核对的文章标题' }), /SOHU_TITLE_NOT_CONFIRMED/)
  assert.equal(state.clicks(), 0)
})

const worksUrl = 'https://mp.sohu.com/mpfe/v4/contentManagement/first/page'
function row({ title = payload.title, status = '审核中', href = '/mpfe/v4/contentManagement/news/articlepreview?id=1084790665&accountId=122895192', date = '2026-10-07 17:17' } = {}) {
  return { ...visible, querySelector: selector => selector === 'h3.title a' ? { textContent: title, getAttribute: () => href }
    : selector === '.status' ? { textContent: status } : selector === '.date-common' ? { textContent: date } : null }
}

test('Sohu single page navigation returns the matched review record without another click', async () => {
  const date = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date())
  const state = load({ message: '', rows: [row({ date })], afterClick: context => { context.location = new URL(worksUrl) } })
  const result = await state.platform.createPublishOptionsAdapter({ delay: async () => {} }).fillPublishOptions(payload)
  assert.equal(result.submitted, true)
  assert.equal(result.publishVerification.submissionVerified, true)
  assert.equal(result.publishVerification.platformStatus, 'reviewing')
  assert.equal(result.publishVerification.platformPublishId, '1084790665')
  assert.equal(result.published, false)
  assert.equal(state.clicks(), 1)
})

test('Sohu works list distinguishes review, rejection and a public article', () => {
  const reviewing = load({ href: worksUrl, rows: [row()] }).platform.readPublishResult(payload)
  assert.equal(reviewing.platformStatus, 'reviewing')
  assert.equal(reviewing.platformPublishId, '1084790665')
  assert.equal(reviewing.verified, false)
  assert.equal(reviewing.platformPublishedUrl, null)
  assert.equal(load({ href: worksUrl, rows: [row({ status: '审核未通过' })] }).platform.readPublishResult(payload).platformStatus, 'rejected')
  const published = load({ href: worksUrl, rows: [row({ status: '已发布', href: 'https://www.sohu.com/a/1084790665_122895192' })] }).platform.readPublishResult(payload)
  assert.equal(published.platformStatus, 'published')
  assert.equal(published.verified, true)
  assert.equal(published.platformPublishedUrl, 'https://www.sohu.com/a/1084790665_122895192')
  assert.equal(load({ href: worksUrl, rows: [row({ status: '已发布', href: 'https://www.sohu.com.evil.example/a/1084790665_122895192' })] }).platform.readPublishResult(payload).verified, false)
})

test('Sohu result lookup requires the exact title and a unique row, with account checks', () => {
  for (const rows of [[], [row({ title: '其他文章' })], [row(), row()]]) {
    assert.equal(load({ href: worksUrl, rows }).platform.readPublishResult(payload).found, false)
  }
  assert.throws(() => load({ href: worksUrl, names: ['其他账号'], rows: [row()] }).platform.readPublishResult(payload), /IDENTITY/)
})

test('Sohu navigation recovery rejects old articles and requires the same tab submission marker', () => {
  const state = load({ href: worksUrl, rows: [row()] })
  const recovery = { ...payload, requireSubmissionAttempt: true }
  assert.equal(state.platform.readPublishResult(recovery).found, false)
  const key = `geo:sohu:submission:吃饱了就困:${payload.title}`
  state.storage.set(key, JSON.stringify({ attemptedAt: '2026-10-07T09:17:22Z' }))
  assert.equal(state.platform.readPublishResult(recovery).platformStatus, 'reviewing')
  state.storage.set(key, JSON.stringify({ attemptedAt: '2026-10-07T09:18:00Z' }))
  assert.equal(state.platform.readPublishResult(recovery).found, false)
})

test('Sohu navigation recovery ignores same-title drafts but blocks two submitted candidates', () => {
  const state = load({ href: worksUrl, rows: [row({ status: '草稿', date: '2026-10-07 17:06' }), row()] })
  const key = `geo:sohu:submission:吃饱了就困:${payload.title}`
  state.storage.set(key, JSON.stringify({ attemptedAt: '2026-10-07T09:17:22Z' }))
  assert.equal(state.platform.readPublishResult({ ...payload, requireSubmissionAttempt: true }).platformStatus, 'reviewing')
  const ambiguous = load({ href: worksUrl, rows: [row(), row()] })
  ambiguous.storage.set(key, state.storage.get(key))
  assert.equal(ambiguous.platform.readPublishResult({ ...payload, requireSubmissionAttempt: true }).found, false)
})
