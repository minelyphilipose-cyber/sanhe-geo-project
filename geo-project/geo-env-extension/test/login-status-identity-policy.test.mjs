import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const source = fs.readFileSync(new URL('../service-worker.js', import.meta.url), 'utf8')

function functionBlock(startMarker, endMarker) {
  const start = source.indexOf(startMarker)
  const end = source.indexOf(endMarker, start)
  assert.notEqual(start, -1)
  assert.notEqual(end, -1)
  return source.slice(start, end)
}

test('login report treats account name as authoritative and platform ids as diagnostics', () => {
  const block = functionBlock('async function reportLoginStatus', '\nfunction firstArray')

  assert.match(block, /if \(accountNames\.length > 1\)/)
  assert.match(block, /accountNames\.length === 1/)
  assert.doesNotMatch(block, /accountIds\.length > 1/)
  assert.doesNotMatch(block, /accountIds\.length > 0 \|\| accountNames\.length > 0/)
  assert.match(block, /actualPlatformAccountId: accountIds\[0\] \|\| null/)
})

test('manual login report can explicitly bypass a stale configured account id', () => {
  const block = functionBlock('async function reportLoginStatus', '\nfunction firstArray')

  assert.match(block, /hasOwnProperty\.call\(report, 'selfMediaAccountId'\)/)
  assert.doesNotMatch(block, /report\.selfMediaAccountId \|\| config\.selfMediaAccountId/)
})

test('automatic page detection skips non-authoritative negative login reports', () => {
  const block = functionBlock('async function autoReportLoginStatusFromTab', '\nasync function resolveLoginReportTab')

  assert.match(block, /!identityHasSingleCandidate\(identity\) && !options\.loginVerificationId/)
  assert.match(block, /普通被动检测未读取到唯一账号名称/)
  assert.match(block, /return null/)
})

test('client reports never self-declare authority and server derives it from verification evidence', () => {
  const manualBlock = functionBlock('async function reportActiveTabLoginStatus', '\nfunction identityHasCandidates')
  const reportBlock = functionBlock('async function reportLoginStatus', '\nfunction firstArray')

  assert.doesNotMatch(manualBlock, /authoritative: true/)
  assert.match(manualBlock, /reportSource: 'operator_check'/)
  assert.match(reportBlock, /authoritative: false/)
})

test('verification task reports an explicit platform login page as server-verifiable evidence', () => {
  const block = functionBlock('async function autoReportLoginStatusFromTab', '\nasync function resolveLoginReportTab')

  assert.match(block, /options\.loginVerificationId && platform && isExplicitLoginPageUrl/)
  assert.match(block, /forcedLoginStatus: 'login_required'/)
  assert.match(block, /forcedErrorCode: 'LOGIN_PAGE_CONFIRMED'/)
})

test('supported platforms always require a name identity precheck', () => {
  const block = functionBlock('function requiresIdentityPrecheck', '\nfunction assertExpectedIdentityPresent')

  assert.match(block, /return IDENTITY_PRECHECK_PLATFORMS\.has\(task\.platform\)/)
  assert.doesNotMatch(block, /expectedPlatformAccountId/)
})

test('Zhihu personal creator root is accepted as an identity report page', () => {
  const block = functionBlock('function isZhihuCreatorCenterUrl', '\nfunction inferPlatformFromUrl')
  const context = { URL }
  vm.createContext(context)
  vm.runInContext(`${block}\nthis.isZhihuIdentityUrl = isZhihuIdentityUrl`, context)

  assert.equal(context.isZhihuIdentityUrl(new URL('https://www.zhihu.com/creator')), true)
  assert.equal(context.isZhihuIdentityUrl(new URL('https://zhihu.com/creator/')), true)
  assert.equal(context.isZhihuIdentityUrl(new URL('https://www.zhihu.com/creator/manage/creation/article')), true)
})
