import assert from 'node:assert/strict'
import test from 'node:test'
import { evaluateSohuPublishSignals } from '../src/publish-check.js'

const target = { title: '如何整理一份清晰的工作交接清单', expectedAccountName: '吃饱了就困', platformPublishId: '1084790665' }
const card = { title: target.title, href: 'https://mp.sohu.com/mpfe/v4/contentManagement/news/articlepreview?id=1084790665&accountId=122895192', status: '审核中', publishedAt: '2026-10-07 17:17' }
const page = { url: 'https://mp.sohu.com/mpfe/v4/contentManagement/first/page', accountNames: ['吃饱了就困'], sohuCards: [card] }

test('Sohu matches the submitted record among older same-title drafts', () => {
  const draft = { ...card, status: '草稿', publishedAt: '2026-10-07 17:06', href: card.href.replace('1084790665', '1084790000') }
  const state = { ...page, sohuCards: [draft, card] }
  assert.equal(evaluateSohuPublishSignals(target, state).reviewing, true)
  const byTime = { ...target, platformPublishId: '', submittedAt: '2026-10-07T17:17:20' }
  assert.equal(evaluateSohuPublishSignals(byTime, state).reviewing, true)
  assert.equal(evaluateSohuPublishSignals(byTime, { ...page, sohuCards: [card, card] }).submissionVerified, undefined)
})

test('Sohu review remains pending and rejection is terminal', () => {
  const result = evaluateSohuPublishSignals(target, page)
  assert.equal(result.found, false)
  assert.equal(result.reviewing, true)
  assert.equal(result.platformPublishedUrl, '')
  const rejected = evaluateSohuPublishSignals(target, { ...page, sohuCards: [{ ...card, status: '审核未通过' }] })
  assert.equal(rejected.failed, true)
  assert.equal(rejected.failureCode, 'SOHU_REVIEW_REJECTED')
})

test('Sohu rejection reports only the matched article reason and keeps a fallback', () => {
  const rejectionReason = '文章被平台判定为广告软文，具体原因请查看系统消息'
  const rejectedCard = { ...card, status: '审核未通过', rejectionReason: `  ${rejectionReason}  ` }
  const otherCard = { ...rejectedCard, href: card.href.replace('1084790665', '999'), rejectionReason: '另一篇文章的原因' }
  const result = evaluateSohuPublishSignals(target, { ...page, sohuCards: [otherCard, rejectedCard] })
  assert.equal(result.failureMessage, rejectionReason)
  assert.equal(result.rejectionReason, rejectionReason)
  assert.equal(evaluateSohuPublishSignals(target, { ...page, sohuCards: [{ ...rejectedCard, rejectionReason: '' }] }).failureMessage,
    '搜狐文章审核未通过，请查看平台审核原因')
  assert.equal(evaluateSohuPublishSignals(target, { ...page, sohuCards: [otherCard] }).failed, false)
})

test('Sohu published verification requires the matching ID and public host', () => {
  const publishedCard = { ...card, status: '', href: 'https://www.sohu.com/a/1084790665_122895192' }
  assert.equal(evaluateSohuPublishSignals(target, { ...page, sohuCards: [publishedCard] }).found, true)
  for (const href of ['https://www.sohu.com.evil.example/a/1084790665_122895192', 'http://www.sohu.com/a/1084790665_122895192', 'https://www.sohu.com/a/999_122895192']) {
    assert.equal(evaluateSohuPublishSignals(target, { ...page, sohuCards: [{ ...publishedCard, href }] }).found, false)
  }
})

test('Sohu result never uses another account, duplicate title, or an old article', () => {
  for (const state of [{ ...page, accountNames: ['其他账号'] }, { ...page, accountNames: [] }, { ...page, accountNames: ['吃饱了就困', '其他账号'] }, { ...page, sohuCards: [card, card] }]) {
    assert.equal(evaluateSohuPublishSignals(target, state).found, false)
    assert.equal(evaluateSohuPublishSignals(target, state).submissionVerified, undefined)
  }
  const byTime = { ...target, platformPublishId: '', submittedAt: '2026-10-07T17:17:20' }
  assert.equal(evaluateSohuPublishSignals(byTime, page).reviewing, true)
  assert.equal(evaluateSohuPublishSignals({ ...byTime, submittedAt: '2026-10-08T17:17:20' }, page).reviewing, undefined)
})
