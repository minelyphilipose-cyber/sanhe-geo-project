import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useArticleDistributionChannels } from './useArticleDistributionChannels'
import { checkDouyinDistributionImages } from '@/api/douyinImages'
import type { ArticleDraft } from '@/types'

vi.mock('@/api/douyinImages', () => ({ checkDouyinDistributionImages: vi.fn() }))
vi.mock('@/api/content', () => ({}))
vi.mock('@/api/customer', () => ({}))
vi.mock('@/api/publishSite', () => ({}))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))

const article = { id: 42, channelGroupCode: 'self_media', channelSubCode: 'douyin' } as ArticleDraft
const setup = () => useArticleDistributionChannels({ load: vi.fn(), submitting: ref(false), openSelfMediaDistribute: vi.fn() })

describe('article distribution image gate', () => {
  beforeEach(() => { vi.resetAllMocks() })

  it('blocks opening distribution when image readiness is rejected or cannot be verified', async () => {
    vi.mocked(checkDouyinDistributionImages).mockRejectedValue(new Error('图片尚未生成并应用'))
    const flow = setup()
    await flow.openDistributionChannel(article)
    expect(checkDouyinDistributionImages).toHaveBeenCalledWith(42)
    expect(flow.distributionChannelVisible.value).toBe(false)
  })

  it('waits for readiness before opening and suppresses duplicate clicks', async () => {
    let finish: (value: never) => void = () => {}
    vi.mocked(checkDouyinDistributionImages).mockReturnValue(new Promise(resolve => { finish = resolve }) as never)
    const flow = setup()
    const pending = flow.openDistributionChannel(article)
    expect(flow.distributionChannelVisible.value).toBe(false)
    await flow.openDistributionChannel(article)
    expect(checkDouyinDistributionImages).toHaveBeenCalledTimes(1)
    finish({ data: { data: true } } as never)
    await pending
    expect(flow.distributionChannelVisible.value).toBe(true)
  })

  it.each(['wechat', 'xiaohongshu', 'toutiao'])('does not change the %s distribution flow', async channelSubCode => {
    const flow = setup()
    await flow.openDistributionChannel({ ...article, channelSubCode })
    expect(checkDouyinDistributionImages).not.toHaveBeenCalled()
    expect(flow.distributionChannelVisible.value).toBe(true)
  })
})
