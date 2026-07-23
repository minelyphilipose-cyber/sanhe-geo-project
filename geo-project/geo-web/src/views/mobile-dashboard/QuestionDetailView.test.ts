import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import QuestionSearchSources from '@/components/mobile-dashboard/QuestionSearchSources.vue'
import QuestionDetailView from './QuestionDetailView.vue'

const mocks = vi.hoisted(() => ({
  getQuestionDetail: vi.fn(),
  route: { params: { shareCode: 'ABCD2345', pollResultId: '10' } },
  router: { back: vi.fn() },
  showToast: vi.fn(),
  store: {
    sessionToken: 'session-token',
    renewSession: vi.fn(),
    contentPlatforms: [],
  },
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => mocks.router,
}))

vi.mock('vant', () => ({
  showToast: mocks.showToast,
}))

vi.mock('@/stores/mobileDashboard', () => ({
  useMobileDashboardStore: () => mocks.store,
}))

vi.mock('@/api/mobileDashboard', () => ({
  getMobileDashboardQuestionDetail: mocks.getQuestionDetail,
  withRenewedMobileDashboardSession: (
    requestFn: (sessionToken: string) => Promise<unknown>,
    store: { sessionToken: string },
  ) => requestFn(store.sessionToken),
}))

describe('QuestionDetailView', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mocks.getQuestionDetail.mockReset()
    mocks.showToast.mockReset()
    mocks.router.back.mockReset()
  })

  it('keeps联网参考资料 hidden while retaining the answer content', async () => {
    mocks.getQuestionDetail.mockResolvedValue({
      data: {
        data: {
          keywordResultId: 1,
          pollResultId: 10,
          platformCode: 'doubao',
          questionTitle: '示例核心问题',
          mentioned: true,
          monitorStatus: 'mentioned',
          recommended: { available: true, value: true },
          firstRecommend: { available: true, value: false },
          rankPosition: { available: true, value: 2 },
          responseText: '回答结论[ref_1]。\n\n信息来源：https://example.com/a',
          tags: [],
          searchSources: [{
            sourceId: 1,
            citationIndex: 1,
            title: '示例来源',
            url: 'https://example.com/a',
          }],
          relatedContentTasks: [],
        },
      },
    })

    const wrapper = mount(QuestionDetailView, {
      global: {
        stubs: {
          MobileIcon: true,
          'van-skeleton': true,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('回答结论')
    expect(wrapper.text()).not.toContain('参考资料')
    expect(wrapper.text()).not.toContain('信息来源')
    expect(wrapper.text()).not.toContain('example.com')
    expect(wrapper.findComponent(QuestionSearchSources).exists()).toBe(false)
  })
})
