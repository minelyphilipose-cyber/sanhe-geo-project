import ElementPlus from 'element-plus'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import QuestionPollVerification from './QuestionPollVerification.vue'
import type { ManualQuestionPollBatchView } from '@/api/dispatch'

const router = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
}))
const route = vi.hoisted(() => ({ query: {} as Record<string, string> }))
const dispatchApi = vi.hoisted(() => ({
  getManualQuestionPollBatch: vi.fn(),
  getManualQuestionPollPlatforms: vi.fn(),
  getRecentManualQuestionPollBatches: vi.fn(),
  startManualQuestionPoll: vi.fn(),
}))
const projectApi = vi.hoisted(() => ({
  getProjectList: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => router,
  useRoute: () => route,
}))
vi.mock('@/api/dispatch', () => dispatchApi)
vi.mock('@/api/project', () => projectApi)
enableAutoUnmount(afterEach)

describe('QuestionPollVerification', () => {
  beforeEach(() => {
    router.push.mockReset()
    router.replace.mockReset()
    route.query = {}
    projectApi.getProjectList.mockResolvedValue({
      data: { data: { records: [] } },
    })
    dispatchApi.getManualQuestionPollPlatforms.mockResolvedValue({
      data: { data: [] },
    })
    dispatchApi.getRecentManualQuestionPollBatches.mockResolvedValue({
      data: { data: [batch] },
    })
    dispatchApi.getManualQuestionPollBatch.mockResolvedValue({
      data: { data: batch },
    })
  })

  it('opens recent verification history and restores the selected batch', async () => {
    const wrapper = mount(QuestionPollVerification, {
      global: {
        plugins: [ElementPlus],
        stubs: {
          ElDrawer: {
            props: ['modelValue'],
            template: '<section v-if="modelValue" data-testid="history-drawer"><slot /></section>',
          },
        },
      },
    })
    await flushPromises()

    const historyButton = wrapper.findAll('button')
      .find((candidate) => candidate.text().includes('验证记录'))
    expect(historyButton).toBeDefined()
    await historyButton!.trigger('click')
    await flushPromises()

    expect(dispatchApi.getRecentManualQuestionPollBatches).toHaveBeenCalledWith(20)
    expect(wrapper.text()).toContain('手工验证测试项目')
    await wrapper.get('.history-card').trigger('click')
    await flushPromises()

    expect(dispatchApi.getManualQuestionPollBatch).toHaveBeenCalledWith(502)
    expect(router.replace).toHaveBeenCalledWith({
      query: { batchId: '502' },
    })
    expect(wrapper.text()).toContain('批次 #502')
    expect(wrapper.text()).toContain('问题级验证结果')
    await wrapper.get('.el-collapse-item__header').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('推荐海上1912文化餐厅。')
    expect(wrapper.get('.source-detail-card a').attributes('href'))
      .toBe('https://example.com/restaurant')
  })
})

const batch: ManualQuestionPollBatchView = {
  batchId: 502,
  projectId: 100,
  projectName: '手工验证测试项目',
  batchDate: '2026-07-16',
  batchNo: 1_000_001,
  questionTier: 'A',
  triggerType: 'MANUAL',
  status: 'finished',
  questionLimit: 1,
  platformCount: 4,
  shardCount: 4,
  terminalShardCount: 4,
  failedShardCount: 0,
  resultCount: 4,
  completedCount: 4,
  failedCount: 0,
  searchConfirmedCount: 4,
  confirmedCitationExposureCount: 3,
  triggeredAt: '2026-07-16T18:34:35',
  finishedAt: '2026-07-16T18:35:16',
  platforms: [],
  results: [{
    pollResultId: 700,
    platformId: 55,
    platformCode: 'doubao_web',
    platformName: '豆包联网问答',
    question: '阜阳环境好的餐厅推荐',
    status: 'completed',
    resultCode: 'R5',
    requestCount: 1,
    responseTimeMs: 1200,
    executionFinalized: true,
    searchStatus: 'TRIGGERED',
    searchTriggered: true,
    confirmedCitationExposure: true,
    answer: '推荐海上1912文化餐厅。',
    errorCategory: null,
    errorMessage: null,
    latencyMs: 1200,
    sources: [{
      sourceId: 900,
      rankNo: 1,
      title: '餐厅推荐来源',
      url: 'https://example.com/restaurant',
      domain: 'example.com',
      brandMatched: true,
      brandMatchStrength: 'STRONG',
    }],
    citations: [{
      citationIndex: 1,
      sourceId: 900,
      sourceTitle: '餐厅推荐来源',
      sourceUrl: 'https://example.com/restaurant',
      answerStart: 2,
      answerEnd: 8,
      confidence: 'CONFIRMED',
      validationStatus: 'VALID',
    }],
  }],
}
