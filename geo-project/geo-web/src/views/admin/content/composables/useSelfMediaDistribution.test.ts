import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import type { ArticleDraft } from '@/types'
import { useSelfMediaDistribution } from './useSelfMediaDistribution'

const mocks = vi.hoisted(() => ({
  dispatch: vi.fn(),
  environment: vi.fn(async () => ({ data: { data: { loginStatus: 'matched', expectedAccountName: '测试搜狐账号' } } })),
  success: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ isPartner: false }) }))
vi.mock('@/api/request', () => ({ default: {} }))
vi.mock('element-plus', () => ({ ElMessage: { success: mocks.success, error: vi.fn(), warning: vi.fn(), info: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }))
vi.mock('@/api/content', async importOriginal => ({
  ...await importOriginal<typeof import('@/api/content')>(),
  getContentArticleDetail: vi.fn(async () => ({ data: { data: { project: { brandId: 20, companyId: 25 }, article: { title: '测试标题', status: 'ready' }, versions: [] } } })),
  getWechatMpCapability: vi.fn(async () => ({ data: { data: {} } })),
  getDouyinCapability: vi.fn(async () => ({ data: { data: {} } })),
  getArticleDistribution: vi.fn(async () => ({ data: { data: { attempts: [] } } })),
  getSelfMediaAccountsByBrand: vi.fn(async () => ({ data: { data: [
    { id: 30, platform: 'sohu', status: 'active', accountName: '测试搜狐账号' },
    { id: 31, platform: 'toutiao', status: 'active', accountName: '其他平台账号' },
  ] } })),
  dispatchSelfMediaPlatformQuickSchedule: mocks.dispatch,
}))
vi.mock('@/api/customer', async importOriginal => ({
  ...await importOriginal<typeof import('@/api/customer')>(),
  getBrandImageFolders: vi.fn(async () => ({ data: { data: [] } })),
  getCompanyDistributionQuotas: vi.fn(async () => ({ data: { data: { items: [
    { channelCode: 'self_media:sohu', enabled: true, quotaLimit: 13, usedCount: 4, periodType: 'month' },
  ] } } })),
}))
vi.mock('@/api/browserEnvironment', async importOriginal => ({
  ...await importOriginal<typeof import('@/api/browserEnvironment')>(),
  getBrowserEnvironmentAccountBySelfMedia: mocks.environment,
}))
vi.mock('@/api/localAgent', () => ({ listLocalAgentSessions: vi.fn(async () => ({ data: { data: [] } })) }))

let wrapper: ReturnType<typeof mount> | undefined
afterEach(() => { wrapper?.unmount(); vi.clearAllMocks() })

describe('Sohu manual dispatch', () => {
  it('loads Sohu identity/quota and sends a single platform dispatch when clicked twice', async () => {
    const article = { id: 10, projectId: 17, title: '测试标题', status: 'ready' } as ArticleDraft
    const load = vi.fn(async () => {})
    let flow!: ReturnType<typeof useSelfMediaDistribution>
    wrapper = mount(defineComponent({
      setup() { flow = useSelfMediaDistribution({ rows: ref([article]), load }); return () => null },
    }))
    await flow.openMediaDistribute(article)
    expect(flow.sohuAccounts.value.map(account => account.id)).toEqual([30])
    expect(mocks.environment).toHaveBeenCalledWith(30)
    expect(flow.selfMediaDistributeActions.isSemiAutoPlatform('sohu')).toBe(true)
    expect(flow.selfMediaDistributeActions.semiAutoPlatformLabel('sohu')).toBe('搜狐')
    let finish!: (value: unknown) => void
    mocks.dispatch.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    const pending = flow.selfMediaDistributeActions.handleSemiAutoPlatformClick('sohu')
    expect(flow.currentPlatformAccounts.value.map(account => account.id)).toEqual([30])
    expect(flow.selectedSelfMediaQuotaHint.value).toContain('搜狐')
    await flow.selfMediaDistributeActions.handleSemiAutoPlatformClick('sohu')
    expect(mocks.dispatch).toHaveBeenCalledTimes(1)
    expect(mocks.dispatch).toHaveBeenCalledWith({ articleId: 10, platform: 'sohu', replaceNextScheduled: false })
    finish({ data: { data: { action: 'created', plannedPublishAt: '2026-10-09T10:00:00', nextAttemptAt: '2026-10-09T10:00:00' } } })
    await pending
    expect(flow.mediaDistributeVisible.value).toBe(false)
    expect(load).toHaveBeenCalledTimes(1)
    expect(mocks.success.mock.calls[0][0]).toContain('搜狐排期已创建')
  })
})
