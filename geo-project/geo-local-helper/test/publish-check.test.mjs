import assert from 'node:assert/strict'
import test from 'node:test'
import { evaluateBaijiahaoPublishSignals, evaluateXiaohongshuPublishSignals } from '../src/publish-check.js'

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

test('baijiahao publish check keeps future scheduled article pending', () => {
  const result = evaluateBaijiahaoPublishSignals(
    {
      title: '装修前根本没想过这事，阜阳一业主在水电进场时才慌了',
      platformScheduledAt: '2026-06-10T12:58:00',
    },
    {
      url: 'https://baijiahao.baidu.com/builder/rc/content?app_id=1867055852901021',
      text: '作品管理\n装修前根本没想过这事，阜阳一业主在水电进场时才慌了\n审核中\n预计 2026-06-10 12:58 发布',
    },
    {
      nowMs: new Date(2026, 5, 10, 11, 50, 0).getTime(),
    },
  )

  assert.equal(result.found, false)
  assert.equal(result.pendingScheduled, true)
  assert.equal(result.platformStatus, 'reviewing')
  assert.equal(result.platformScheduledText, '预计 2026-06-10 12:58 发布')
})

test('baijiahao publish check keeps reviewing article non-final after scheduled time', () => {
  const result = evaluateBaijiahaoPublishSignals(
    {
      title: '装修前根本没想过这事，阜阳一业主在水电进场时才慌了',
      platformScheduledAt: '2026-06-10T12:58:00',
    },
    {
      url: 'https://baijiahao.baidu.com/builder/rc/content?app_id=1867055852901021',
      text: '作品管理\n装修前根本没想过这事，阜阳一业主在水电进场时才慌了\n审核中\n预计 2026-06-10 12:58 发布',
      anchors: [
        {
          text: '装修前根本没想过这事，阜阳一业主在水电进场时才慌了',
          href: 'https://baijiahao.baidu.com/builder/rc/content/detail?id=abc',
        },
      ],
    },
    {
      nowMs: new Date(2026, 5, 10, 13, 10, 0).getTime(),
    },
  )

  assert.equal(result.found, false)
  assert.equal(result.pendingScheduled, false)
  assert.equal(result.platformStatus, 'reviewing')
  assert.equal(result.reason, 'title matched and platform is still reviewing')
  assert.equal(result.url, 'https://baijiahao.baidu.com/builder/rc/content/detail?id=abc')
})

test('baijiahao publish check confirms published article on works list', () => {
  const result = evaluateBaijiahaoPublishSignals(
    {
      title: '装修前根本没想过这事，阜阳一业主在水电进场时才慌了',
      platformScheduledAt: '2026-06-10T12:58:00',
    },
    {
      url: 'https://baijiahao.baidu.com/builder/rc/content?app_id=1867055852901021',
      text: '作品管理\n装修前根本没想过这事，阜阳一业主在水电进场时才慌了\n已发布\n预计 2026-06-10 12:58 发布',
      anchors: [
        {
          text: '装修前根本没想过这事，阜阳一业主在水电进场时才慌了',
          href: 'https://baijiahao.baidu.com/builder/rc/content/detail?id=abc',
        },
      ],
    },
    {
      nowMs: new Date(2026, 5, 10, 13, 10, 0).getTime(),
    },
  )

  assert.equal(result.found, true)
  assert.equal(result.pendingScheduled, false)
  assert.equal(result.platformStatus, 'published')
  assert.equal(result.url, 'https://baijiahao.baidu.com/builder/rc/content/detail?id=abc')
})

test('baijiahao publish check confirms published article when list omits scheduled time', () => {
  const result = evaluateBaijiahaoPublishSignals(
    {
      title: '「讨论」在阜阳一聊“全屋智能哪家好” 就被问预算？不如先把这四笔成本结构拆透再挑店',
      platformScheduledAt: '2026-06-13T20:24:00',
    },
    {
      url: 'https://baijiahao.baidu.com/builder/rc/content?currentPage=1&pageSize=10&app_id=1867055852901021',
      text: '作品管理\n全部 图文 视频 小视频 动态 直播 合集 图集\n「讨论」在阜阳一聊“全屋智能哪家好” 就被问预算？不如先把这四笔成本结构拆透再挑店\n已发布\n0 0 0 0',
      anchors: [
        {
          text: '「讨论」在阜阳一聊“全屋智能哪家好” 就被问预算？不如先把这四笔成本结构拆透再挑店',
          href: 'https://baijiahao.baidu.com/builder/rc/content/detail?id=published',
        },
      ],
    },
    {
      nowMs: new Date(2026, 5, 15, 9, 55, 0).getTime(),
    },
  )

  assert.equal(result.found, true)
  assert.equal(result.pendingScheduled, false)
  assert.equal(result.hasScheduleTime, false)
  assert.equal(result.hasPublishedNearTitle, true)
  assert.equal(result.platformStatus, 'published')
  assert.equal(result.url, 'https://baijiahao.baidu.com/builder/rc/content/detail?id=published')
})

test('baijiahao publish check reports rejected article as failed', () => {
  const result = evaluateBaijiahaoPublishSignals(
    {
      title: '装修前根本没想过这事，阜阳一业主在水电进场时才慌了',
      platformScheduledAt: '2026-06-10T12:58:00',
    },
    {
      url: 'https://baijiahao.baidu.com/builder/rc/content?app_id=1867055852901021',
      text: '作品管理\n装修前根本没想过这事，阜阳一业主在水电进场时才慌了\n审核未通过',
    },
    {
      nowMs: new Date(2026, 5, 10, 13, 10, 0).getTime(),
    },
  )

  assert.equal(result.found, false)
  assert.equal(result.failed, true)
  assert.equal(result.platformStatus, 'rejected')
  assert.equal(result.failureCode, 'BAIJIAHAO_REVIEW_REJECTED')
})
