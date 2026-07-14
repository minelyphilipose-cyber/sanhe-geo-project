import assert from 'node:assert/strict'
import test from 'node:test'
import { selectUploadTargetPage } from '../src/browser-target.js'

const DOUYIN_EDITOR_URL = 'https://creator.douyin.com/creator-micro/content/post/article?media_type=article&type=new&enter_from=publish_page'

function page(targetId, url = DOUYIN_EDITOR_URL) {
  return {
    target: () => ({ _targetId: targetId }),
    url: () => url,
  }
}

test('douyin upload locks the exact browser target when editor urls are identical', () => {
  const stale = page('target-stale')
  const active = page('target-active')

  const selected = selectUploadTargetPage([stale, active], {
    platform: 'douyin',
    targetPageUrl: DOUYIN_EDITOR_URL,
    browserTargetId: 'target-active',
  })

  assert.equal(selected, active)
})

test('douyin upload refuses an ambiguous url-only tab selection', () => {
  const selected = selectUploadTargetPage([page('target-a'), page('target-b')], {
    platform: 'douyin',
    targetPageUrl: DOUYIN_EDITOR_URL,
  })

  assert.equal(selected, null)
})

test('douyin upload keeps the single-page compatibility fallback', () => {
  const only = page('target-only')
  const selected = selectUploadTargetPage([only], {
    platform: 'douyin',
    targetPageUrl: DOUYIN_EDITOR_URL,
  })

  assert.equal(selected, only)
})

test('douyin upload falls back only when an unavailable target id still has one unique editor', () => {
  const only = page('target-only')
  const selected = selectUploadTargetPage([only], {
    platform: 'douyin',
    targetPageUrl: DOUYIN_EDITOR_URL,
    browserTargetId: 'target-id-not-exposed-by-this-browser-version',
  })

  assert.equal(selected, only)
})
