import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DouyinImageProgress from './DouyinImageProgress.vue'
import { listDouyinImages } from '@/api/douyinImages'

vi.mock('@/api/douyinImages', () => ({ listDouyinImages: vi.fn() }))
const response = (data: unknown) => ({ data: { code: 0, data } })
const running = { id: 9, sourceVersionNo: 1, appliedVersionNo: null, status: 'running', imageCount: 4, imageUrls: ['one'] }
const options = {
  props: { articleId: 42, currentVersionNo: 1, active: true },
  global: { stubs: {
    ElAlert: { props: ['title', 'description'], template: '<div>{{ title }} {{ description }}</div>' },
    ElButton: { template: '<button><slot /></button>' },
  } },
}

describe('Douyin article image progress', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(listDouyinImages).mockResolvedValue(response([running]) as never)
  })
  afterEach(() => { vi.useRealTimers(); vi.resetAllMocks() })

  it('shows progress and requests one article refresh after automatic application', async () => {
    const wrapper = mount(DouyinImageProgress, options)
    await flushPromises()
    expect(wrapper.text()).toContain('生成中 · 1/4 张')
    vi.mocked(listDouyinImages).mockResolvedValue(response([{ ...running, status: 'succeeded', imageUrls: ['1', '2', '3', '4'] }]) as never)
    await vi.advanceTimersByTimeAsync(8000)
    expect(wrapper.emitted('applied')).toBeUndefined()
    vi.mocked(listDouyinImages).mockResolvedValue(response([{ ...running, status: 'applied', appliedVersionNo: 2, imageUrls: ['1', '2', '3', '4'] }]) as never)
    await vi.advanceTimersByTimeAsync(8000)
    expect(wrapper.text()).toContain('已插入文章 · 4/4 张')
    expect(wrapper.emitted('applied')).toHaveLength(1)
    await vi.advanceTimersByTimeAsync(16000)
    expect(listDouyinImages).toHaveBeenCalledTimes(3)
    wrapper.unmount()
  })

  it('ignores a late response from another article and stops polling when closed', async () => {
    let finish: (result: never) => void = () => {}
    vi.mocked(listDouyinImages).mockReturnValueOnce(new Promise(resolve => { finish = resolve }) as never)
    const wrapper = mount(DouyinImageProgress, options)
    vi.mocked(listDouyinImages).mockResolvedValue(response([]) as never)
    await wrapper.setProps({ articleId: 43 })
    await flushPromises()
    finish(response([{ ...running, status: 'applied', appliedVersionNo: 2 }]) as never)
    await flushPromises()
    expect(wrapper.text()).toBe('')
    expect(wrapper.emitted('applied')).toBeUndefined()
    await wrapper.setProps({ active: false })
    await vi.advanceTimersByTimeAsync(16000)
    expect(listDouyinImages).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('shows generation failures without creating a replacement paid task', async () => {
    vi.mocked(listDouyinImages).mockResolvedValue(response([{ ...running, status: 'failed', errorMessage: '模型请求超时' }]) as never)
    const wrapper = mount(DouyinImageProgress, options)
    await flushPromises()
    expect(wrapper.text()).toContain('生成失败 · 1/4 张')
    expect(wrapper.text()).toContain('模型请求超时')
    await vi.advanceTimersByTimeAsync(16000)
    expect(listDouyinImages).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('allows retrying a failed status read and hides obsolete image batches', async () => {
    vi.mocked(listDouyinImages).mockRejectedValueOnce(new Error('network'))
    const wrapper = mount(DouyinImageProgress, options)
    await flushPromises()
    expect(wrapper.text()).toContain('暂时无法读取豆包图片进度')
    vi.mocked(listDouyinImages).mockResolvedValue(response([{ ...running, sourceVersionNo: 0 }]) as never)
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toBe('')
    wrapper.unmount()
  })
})
