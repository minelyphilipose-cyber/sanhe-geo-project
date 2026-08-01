import assert from 'node:assert/strict'
import test from 'node:test'
import {
  evaluateBaijiahaoPublishSignals,
  evaluateDouyinPublishSignals,
  evaluateXiaohongshuPublishSignals,
} from '../src/publish-check.js'

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

test('xiaohongshu publish check accepts a manually published note before the expected time', () => {
  const result = evaluateXiaohongshuPublishSignals(
    {
      title: '用户手动提前发布的小红书文章',
      platformScheduledAt: '2026-07-14T20:00:00',
    },
    {
      url: 'https://creator.xiaohongshu.com/new/note-manager',
      text: '笔记管理\n用户手动提前发布的小红书文章\n已发布\n2026-07-14 18:10',
      xiaohongshuCards: [{
        title: '用户手动提前发布的小红书文章',
        publishedAt: '2026-07-14 18:10',
        noteId: 'manual-early-note',
      }],
    },
    {
      nowMs: new Date(2026, 6, 14, 18, 20, 0).getTime(),
    },
  )

  assert.equal(result.found, true)
  assert.equal(result.pendingScheduled, false)
  assert.equal(result.isBeforeScheduledAt, true)
  assert.equal(result.hasScheduleTime, false)
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
  assert.equal(result.url, 'https://creator.xiaohongshu.com/new/note-manager')
  assert.equal(result.platformPublishedUrl, 'https://www.xiaohongshu.com/explore/abc123')
})

test('xiaohongshu publish check confirms note-manager card without explicit published label', () => {
  const result = evaluateXiaohongshuPublishSignals(
    {
      title: '[杂谈] 在阜阳装修，我几乎把 “全屋智能有线和无线哪个” 这题做成了毕业论文',
      platformScheduledAt: '2026-06-23T16:48:00',
    },
    {
      url: 'https://creator.xiaohongshu.com/new/note-manager',
      text: '创作服务平台\n发布笔记\n笔记管理\n全部 8 已发布 审核中 未通过\n[杂谈] 在阜阳装修，我几乎把 “全屋智能有线和无线哪个” 这题做成了毕业论文\n2026-06-23 16:48\n1 0 0 0 0',
    },
    {
      nowMs: new Date(2026, 5, 23, 17, 10, 0).getTime(),
    },
  )

  assert.equal(result.found, true)
  assert.equal(result.pendingScheduled, false)
  assert.equal(result.hasPublishedCard, true)
  assert.equal(result.platformStatus, 'published')
  assert.equal(result.reason, 'matched published note card')
  assert.equal(result.platformPublishedUrl, '')
  assert.equal(result.matchStrategy, 'note_manager_card')
  assert.equal(result.candidateCount, 0)
})

test('xiaohongshu publish check extracts note id from a truncated note card title', () => {
  const result = evaluateXiaohongshuPublishSignals(
    {
      title: '💰预算1万和5万的全屋智能，差别到底在哪？阜阳跑了3个月的真实对比',
      platformScheduledAt: '2026-07-11T12:13:00',
    },
    {
      url: 'https://creator.xiaohongshu.com/new/note-manager',
      text: '笔记管理\n💰预算1万和5万的全屋智能，差别到底在哪\n2026-07-11 12:13',
      xiaohongshuCards: [{
        title: '💰预算1万和5万的全屋智能，差别到底在哪',
        publishedAt: '2026-07-11 12:13',
        noteId: '6a51addc0000000022014aba',
      }],
    },
    {
      nowMs: new Date(2026, 6, 13, 11, 0, 0).getTime(),
    },
  )

  assert.equal(result.found, true)
  assert.equal(result.platformStatus, 'published')
  assert.equal(result.platformPublishId, '6a51addc0000000022014aba')
  assert.equal(result.matchedCard.noteId, '6a51addc0000000022014aba')
})

test('douyin publish check keeps a published structured card wider than 1600px', () => {
  const result = evaluateDouyinPublishSignals(
    {
      title: '在阜阳问全屋智能哪家专业，不如先把“它能帮家里解决什么”捋明白',
      platformScheduledAt: '2026-07-11T13:00:00',
    },
    {
      url: 'https://creator.douyin.com/creator-micro/content/manage?enter_from=publish',
      text: '作品管理\n共 6 个作品',
      douyinCards: [{
        title: '在阜阳问全屋智能哪家专业，不如先把“它能帮家里解决什么”捋明',
        publishedAt: '2026年07月11日 13:00',
        status: '已发布',
        text: '在阜阳问全屋智能哪家专业，不如先把“它能帮家里解决什么”捋明 2026年07月11日 13:00 已发布',
        width: 1728.8,
        height: 232,
        links: [],
      }],
    },
  )

  assert.equal(result.found, true)
  assert.equal(result.platformStatus, 'published')
  assert.equal(result.candidateCount, 1)
  assert.equal(result.matchedCard.width, 1728.8)
  assert.ok(result.matchedCard.titleScore > 0.9)
})

test('douyin publish check accepts manual early publish with a different actual time', () => {
  const result = evaluateDouyinPublishSignals(
    {
      title: '用户手动提前发布的抖音图文',
      platformScheduledAt: '2026-07-14T20:00:00',
    },
    {
      url: 'https://creator.douyin.com/creator-micro/content/manage?enter_from=publish',
      text: '作品管理',
      douyinCards: [{
        title: '用户手动提前发布的抖音图文',
        publishedAt: '2026年07月14日 18:10',
        status: '已发布',
        text: '用户手动提前发布的抖音图文 2026年07月14日 18:10 已发布',
        links: [],
      }],
    },
  )

  assert.equal(result.found, true)
  assert.equal(result.pendingScheduled, false)
  assert.equal(result.scheduledAtText, '')
})

test('douyin image-text publish check requires image count and task time window', () => {
  const target = {
    title: '阜阳全屋定制案例',
    contentKind: 'image_text',
    expectedImageCount: 4,
    taskStartedAt: '2020-07-30T16:40:00',
  }
  const pageState = {
    url: 'https://creator.douyin.com/creator-micro/content/manage?enter_from=publish',
    douyinCards: [{
      title: '阜阳全屋定制案例',
      publishedAt: '2020年07月30日 16:53',
      status: '审核中',
      text: '阜阳全屋定制案例 2020年07月30日 16:53 审核中 4张',
      links: [],
    }],
  }

  assert.equal(evaluateDouyinPublishSignals(target, pageState).found, true)
  assert.equal(evaluateDouyinPublishSignals({ ...target, expectedImageCount: 6 }, pageState).found, false)
})

test('douyin image-text publish check reports rejected work', () => {
  const result = evaluateDouyinPublishSignals(
    {
      title: '阜阳全屋定制案例',
      contentKind: 'image_text',
      expectedImageCount: 4,
      taskStartedAt: '2020-07-30T16:40:00',
    },
    {
      douyinCards: [{
        title: '阜阳全屋定制案例',
        publishedAt: '2020年07月30日 16:53',
        status: '未通过',
        text: '阜阳全屋定制案例 2020年07月30日 16:53 未通过 4张',
        links: [],
      }],
    },
  )

  assert.equal(result.found, false)
  assert.equal(result.failed, true)
  assert.equal(result.platformStatus, 'rejected')
  assert.equal(result.failureCode, 'DOUYIN_REVIEW_REJECTED')
})

test('xiaohongshu publish check returns current detail url after opening published note', () => {
  const result = evaluateXiaohongshuPublishSignals(
    {
      title: '看到好多阜阳姐妹在装修群里问“全屋智能哪家好”',
      platformScheduledAt: '2026-06-24T17:05:00',
    },
    {
      url: 'https://www.xiaohongshu.com/explore/6a3b88d2000000000803d330?xsec_token=abc%3D&xsec_source=pc_creatormng',
      text: 'wohaiyjhm\n看到好多阜阳姐妹在装修群里问“全屋智能哪家好”\n夏天美甲款式推荐',
      anchors: [],
    },
    {
      nowMs: new Date(2026, 5, 24, 17, 20, 0).getTime(),
    },
  )

  assert.equal(result.found, true)
  assert.equal(result.pendingScheduled, false)
  assert.equal(result.platformStatus, 'published')
  assert.equal(
    result.platformPublishedUrl,
    'https://www.xiaohongshu.com/explore/6a3b88d2000000000803d330?xsec_token=abc%3D&xsec_source=pc_creatormng',
  )
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

test('baijiahao publish check accepts a manually published article before the expected time', () => {
  const result = evaluateBaijiahaoPublishSignals(
    {
      title: '用户手动提前发布的百家号文章',
      platformScheduledAt: '2026-07-14T20:00:00',
    },
    {
      url: 'https://baijiahao.baidu.com/builder/rc/content?app_id=123',
      text: '作品管理\n用户手动提前发布的百家号文章\n已发布\n2026-07-14 18:10',
      baijiahaoCards: [{
        title: '用户手动提前发布的百家号文章',
        status: '已发布',
        publishedAt: '2026-07-14 18:10',
        publishedUrl: 'https://baijiahao.baidu.com/s?id=manual-early',
        text: '用户手动提前发布的百家号文章 已发布 2026-07-14 18:10',
      }],
    },
    {
      nowMs: new Date(2026, 6, 14, 18, 20, 0).getTime(),
    },
  )

  assert.equal(result.found, true)
  assert.equal(result.pendingScheduled, false)
  assert.equal(result.isBeforeScheduledAt, true)
  assert.equal(result.hasScheduleTime, false)
  assert.equal(result.platformStatus, 'published')
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
  assert.equal(result.url, 'https://baijiahao.baidu.com/builder/rc/content?app_id=1867055852901021')
  assert.equal(result.platformPublishedUrl, '')
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
          href: 'http://baijiahao.baidu.com/s?id=1868043633218529302',
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
  assert.equal(result.url, 'https://baijiahao.baidu.com/builder/rc/content?app_id=1867055852901021')
  assert.equal(result.platformPublishedUrl, 'http://baijiahao.baidu.com/s?id=1868043633218529302')
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
          href: 'http://baijiahao.baidu.com/s?id=1868043633218529303',
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
  assert.equal(result.url, 'https://baijiahao.baidu.com/builder/rc/content?currentPage=1&pageSize=10&app_id=1867055852901021')
  assert.equal(result.platformPublishedUrl, 'http://baijiahao.baidu.com/s?id=1868043633218529303')
})

test('baijiahao publish check matches title when platform rewrites bracket punctuation', () => {
  const result = evaluateBaijiahaoPublishSignals(
    {
      title: '[分析] 在阜阳做全屋智能，别只看 “谁家报价低”，先把这笔 “本地交付” 的账盘清楚',
      platformScheduledAt: '2026-06-30T18:27:00',
    },
    {
      url: 'https://baijiahao.baidu.com/builder/rc/content?currentPage=1&pageSize=10&search=&type=&collection=&app_id=1867055852901021&startDate=&endDate=',
      text: '作品管理\n全部 图文 视频 小视频 动态 直播 合集 图集\n全部 已发布 待发布 未通过 已撤回\n共9篇\n「分析」在阜阳做全屋智能，别只看“谁家报价低”，先把这笔“本地交付”的账盘清楚\n已发布\n0 0 0 0 0 0',
      anchors: [
        {
          text: '「分析」在阜阳做全屋智能，别只看“谁家报价低”，先把这笔“本地交付”的账盘清楚',
          href: 'http://baijiahao.baidu.com/s?id=1869000000000000002',
        },
      ],
    },
    {
      nowMs: new Date(2026, 6, 1, 10, 6, 0).getTime(),
    },
  )

  assert.equal(result.found, true)
  assert.equal(result.hasTitle, true)
  assert.equal(result.platformStatus, 'published')
  assert.equal(result.platformPublishedUrl, 'http://baijiahao.baidu.com/s?id=1869000000000000002')
  assert.equal(result.matchStrategy, 'anchor_title_public_url')
  assert.equal(result.candidateCount, 1)
  assert.equal(result.topCandidates[0].titleMatched, true)
  assert.equal(result.topCandidates[0].isPublicUrl, true)
})

test('baijiahao publish check matches current article list DOM public title anchor', () => {
  const result = evaluateBaijiahaoPublishSignals(
    {
      title: '【分析】在阜阳做全屋智能，别只看“谁家报价低”，先把这笔“本地交付”的账盘清楚',
      platformScheduledAt: '2026-06-30T20:44:00',
    },
    {
      url: 'https://baijiahao.baidu.com/builder/rc/content?currentPage=1&pageSize=10&search=&type=&collection=&app_id=1867055852901021&startDate=&endDate=',
      text: [
        '全部 图文 视频 小视频 动态 直播 合集 图集',
        '共9篇',
        '「分析」在阜阳做全屋智能，别只看“谁家报价低”，先把这笔“本地交付”的账盘清楚',
        '2026-06-30 20:44:21',
        '已发布',
        '内容加热 查看评论 详细数据 更多',
      ].join('\n'),
      anchors: [
        {
          text: '',
          href: 'http://baijiahao.baidu.com/s?id=1869412693227144748',
        },
        {
          text: '「分析」在阜阳做全屋智能，别只看“谁家报价低”，先把这笔“本地交付”的账盘清楚',
          href: 'http://baijiahao.baidu.com/s?id=1869412693227144748',
        },
      ],
      baijiahaoCards: [
        {
          title: '「分析」在阜阳做全屋智能，别只看“谁家报价低”，先把这笔“本地交付”的账盘清楚',
          status: '已发布',
          publishedAt: '2026-06-30 20:44:21',
          publishedUrl: 'http://baijiahao.baidu.com/s?id=1869412693227144748',
          text: '「分析」在阜阳做全屋智能，别只看“谁家报价低”，先把这笔“本地交付”的账盘清楚\n2026-06-30 20:44:21\n已发布\n内容加热 查看评论 详细数据 更多',
          anchors: [
            {
              text: '',
              href: 'http://baijiahao.baidu.com/s?id=1869412693227144748',
            },
            {
              text: '「分析」在阜阳做全屋智能，别只看“谁家报价低”，先把这笔“本地交付”的账盘清楚',
              href: 'http://baijiahao.baidu.com/s?id=1869412693227144748',
            },
          ],
        },
      ],
    },
    {
      nowMs: new Date(2026, 6, 1, 10, 6, 0).getTime(),
    },
  )

  assert.equal(result.found, true)
  assert.equal(result.platformStatus, 'published')
  assert.equal(result.platformPublishedUrl, 'http://baijiahao.baidu.com/s?id=1869412693227144748')
  assert.equal(result.matchStrategy, 'article_card_public_url')
  assert.equal(result.checkStages.listLoaded, true)
  assert.equal(result.checkStages.listItemCount, 1)
  assert.equal(result.checkStages.publicUrlMatched, true)
  assert.equal(result.evidence.bestTitleScore, 1)
  assert.equal(result.evidence.matchedPublishedUrl, 'http://baijiahao.baidu.com/s?id=1869412693227144748')
  assert.equal(result.candidateCount, 2)
  assert.equal(result.cardCandidateCount, 1)
  assert.equal(result.matchedCard.publishedAt, '2026-06-30 20:44:21')
  assert.equal(result.topCandidates[1].titleMatched, true)
  assert.equal(result.topCandidates[1].isPublicUrl, true)
})

test('baijiahao publish check matches rewritten title with minor prefix differences', () => {
  const result = evaluateBaijiahaoPublishSignals(
    {
      title: '阜阳做全屋智能，先别问“要花多少钱”，这三个交付的“底”得先摸清楚',
      platformScheduledAt: '2026-07-01T12:54:00',
    },
    {
      url: 'https://baijiahao.baidu.com/builder/rc/content?currentPage=1&pageSize=10&search=&type=&collection=&app_id=1867055852901021&startDate=&endDate=',
      text: [
        '在阜阳做全屋智能，先别问要花多少钱，这三个交付底得先摸清楚',
        '2026-07-01 14:12:00',
        '已发布',
      ].join('\n'),
      anchors: [
        {
          text: '在阜阳做全屋智能，先别问要花多少钱，这三个交付底得先摸清楚',
          href: 'http://baijiahao.baidu.com/s?id=1869412693227144999',
        },
      ],
      baijiahaoCards: [
        {
          title: '在阜阳做全屋智能，先别问要花多少钱，这三个交付底得先摸清楚',
          status: '已发布',
          publishedAt: '2026-07-01 14:12:00',
          publishedUrl: 'http://baijiahao.baidu.com/s?id=1869412693227144999',
          text: '在阜阳做全屋智能，先别问要花多少钱，这三个交付底得先摸清楚\n2026-07-01 14:12:00\n已发布',
        },
      ],
    },
    {
      nowMs: new Date(2026, 6, 1, 14, 20, 0).getTime(),
    },
  )

  assert.equal(result.found, true)
  assert.equal(result.hasTitle, true)
  assert.equal(result.platformStatus, 'published')
  assert.equal(result.platformPublishedUrl, 'http://baijiahao.baidu.com/s?id=1869412693227144999')
  assert.equal(result.matchStrategy, 'article_card_public_url')
})

test('baijiahao publish check prefers matched article card url over earlier page anchor', () => {
  const result = evaluateBaijiahaoPublishSignals(
    {
      title: '在阜阳问全屋智能哪家好之前，先把有线无线和预算这3笔账盘一遍',
      platformScheduledAt: '2026-06-30T17:39:00',
    },
    {
      url: 'https://baijiahao.baidu.com/builder/rc/content?currentPage=1&pageSize=10&search=&type=&collection=&app_id=1867055852901021&startDate=&endDate=',
      text: [
        '在阜阳问全屋智能哪家好之前，先把有线无线和预算这3笔账盘一遍',
        '已发布',
        '在阜阳问全屋智能哪家好之前，先把有线无线和预算这3笔账盘一遍',
        '审核中',
      ].join('\n'),
      anchors: [
        {
          text: '在阜阳问全屋智能哪家好之前，先把有线无线和预算这3笔账盘一遍',
          href: 'http://baijiahao.baidu.com/s?id=1869000000000000001',
        },
        {
          text: '在阜阳问全屋智能哪家好之前，先把有线无线和预算这3笔账盘一遍',
          href: 'http://baijiahao.baidu.com/s?id=1869000000000000002',
        },
      ],
      baijiahaoCards: [
        {
          title: '在阜阳问全屋智能哪家好之前，先把有线无线和预算这3笔账盘一遍',
          status: '已发布',
          publishedAt: '2026-06-30 20:40:42',
          publishedUrl: 'http://baijiahao.baidu.com/s?id=1869000000000000002',
          text: '在阜阳问全屋智能哪家好之前，先把有线无线和预算这3笔账盘一遍\n2026-06-30 20:40:42\n已发布',
        },
      ],
    },
    {
      nowMs: new Date(2026, 6, 1, 10, 6, 0).getTime(),
    },
  )

  assert.equal(result.found, true)
  assert.equal(result.platformPublishedUrl, 'http://baijiahao.baidu.com/s?id=1869000000000000002')
  assert.equal(result.matchStrategy, 'article_card_public_url')
})

test('baijiahao publish check ignores status tabs when matching published card', () => {
  const result = evaluateBaijiahaoPublishSignals(
    {
      title: '在阜阳装修，总怕“全屋智能”装完变“全屋智障”，问题到底出在哪？',
      platformScheduledAt: '2026-06-23T20:34:00',
    },
    {
      url: 'https://baijiahao.baidu.com/builder/rc/content?currentPage=1&pageSize=10&search=&type=&collection=&app_id=1867055852901021&startDate=&endDate=',
      text: '全部 图文 视频 小视频 动态 直播 合集 图集\n全部 已发布 待发布 未通过 已撤回\n共5篇\n在阜阳装修，总怕“全屋智能”装完变“全屋智障”，问题到底出在哪？\n已发布\n0 0 0 0 0 0\n在阜阳装修，全屋智能到底值不值？',
      anchors: [
        {
          text: '在阜阳装修，总怕“全屋智能”装完变“全屋智障”，问题到底出在哪？',
          href: 'http://baijiahao.baidu.com/s?id=1869000000000000001',
        },
      ],
    },
    {
      nowMs: new Date(2026, 5, 24, 9, 40, 0).getTime(),
    },
  )

  assert.equal(result.found, true)
  assert.equal(result.failed, false)
  assert.equal(result.hasRejectedSignal, false)
  assert.equal(result.platformStatus, 'published')
  assert.equal(result.platformPublishedUrl, 'http://baijiahao.baidu.com/s?id=1869000000000000001')
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
