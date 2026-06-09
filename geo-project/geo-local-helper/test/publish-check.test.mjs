import assert from 'node:assert/strict'
import test from 'node:test'
import { evaluateXiaohongshuPublishSignals } from '../src/publish-check.js'

test('xiaohongshu publish check keeps future scheduled note pending', () => {
  const result = evaluateXiaohongshuPublishSignals(
    {
      title: '三河 GEO 项目自动分发测试文章',
      platformScheduledAt: '2026-06-09T16:50:00',
    },
    {
      url: 'https://creator.xiaohongshu.com/new/note-manager',
      text: '笔记管理\n三河 GEO 项目自动分发测试文章\n定时发布\n发布时间 2026-06-09 16:50',
    },
    {
      nowMs: new Date(2026, 5, 9, 16, 0, 0).getTime(),
    },
  )

  assert.equal(result.found, false)
  assert.equal(result.pendingScheduled, true)
  assert.equal(result.reason, 'platform schedule time not due')
})

test('xiaohongshu publish check confirms published note after scheduled time', () => {
  const result = evaluateXiaohongshuPublishSignals(
    {
      title: '三河 GEO 项目自动分发测试文章',
      platformScheduledAt: '2026-06-09T16:50:00',
    },
    {
      url: 'https://creator.xiaohongshu.com/new/note-manager',
      text: '笔记管理\n三河 GEO 项目自动分发测试文章\n已发布\n发布时间 2026-06-09 16:50',
      anchors: [
        {
          text: '三河 GEO 项目自动分发测试文章',
          href: 'https://www.xiaohongshu.com/explore/abc123',
        },
      ],
    },
    {
      nowMs: new Date(2026, 5, 9, 17, 10, 0).getTime(),
    },
  )

  assert.equal(result.found, true)
  assert.equal(result.pendingScheduled, false)
  assert.equal(result.url, 'https://www.xiaohongshu.com/explore/abc123')
})

test('xiaohongshu publish check does not treat note manager route as published signal', () => {
  const result = evaluateXiaohongshuPublishSignals(
    {
      title: '三河 GEO 项目自动分发测试文章',
      platformScheduledAt: '2026-06-09T16:50:00',
    },
    {
      url: 'https://creator.xiaohongshu.com/new/note-manager',
      text: '笔记管理\n三河 GEO 项目自动分发测试文章\n定时发布\n发布时间 2026-06-09 16:50',
    },
    {
      nowMs: new Date(2026, 5, 9, 17, 10, 0).getTime(),
    },
  )

  assert.equal(result.found, false)
  assert.equal(result.pendingScheduled, false)
  assert.equal(result.reason, 'title matched but published signal missing')
})
