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
    ElAlert: { props: ['title', 'description', 'type'], template: '<div :data-type="type">{{ title }} {{ description }}</div>' },
    ElButton: { template: '<button><slot /></button>' },
  } },
}

describe('Douyin article image progress', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(listDouyinImages).mockResolvedValue(response([running]) as never)
  })
  afterEach(() => { vi.useRealTimers(); vi.resetAllMocks() })

  it('never polls and refreshes the article only after a manual status refresh finds applied images', async () => {
    const wrapper = mount(DouyinImageProgress, options)
    await flushPromises()
    expect(wrapper.text()).toContain('生成中 · 1/4 张')
    await vi.advanceTimersByTimeAsync(60000)
    expect(listDouyinImages).toHaveBeenCalledTimes(1)
    vi.mocked(listDouyinImages).mockResolvedValue(response([{ ...running, status: 'succeeded', imageUrls: ['1', '2', '3', '4'] }]) as never)
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('applied')).toBeUndefined()
    vi.mocked(listDouyinImages).mockResolvedValue(response([{ ...running, status: 'applied', appliedVersionNo: 2, imageUrls: ['1', '2', '3', '4'] }]) as never)
    await wrapper.find('button').trigger('click')
    await flushPromises()
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

  it('shows queued waits as information and cancelled work with an operator-friendly reason', async () => {
    vi.mocked(listDouyinImages).mockResolvedValueOnce(response([{
      ...running, status: 'queued', errorMessage: '图片生成正在等待模型资源，系统会自动重试，无需操作',
    }]) as never)
    const wrapper = mount(DouyinImageProgress, options)
    await flushPromises()
    expect(wrapper.find('[data-type]').attributes('data-type')).toBe('info')
    expect(wrapper.text()).toContain('系统会自动重试，无需操作')

    vi.mocked(listDouyinImages).mockResolvedValueOnce(response([{
      ...running, status: 'cancelled', errorMessage: null,
    }]) as never)
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('豆包图片已取消')
    expect(wrapper.text()).toContain('项目已停用，图片生成已取消')
    expect(wrapper.find('[data-type]').attributes('data-type')).toBe('info')
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
