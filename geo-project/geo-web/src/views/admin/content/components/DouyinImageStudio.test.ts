import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DouyinImageStudio from './DouyinImageStudio.vue'
import { applyDouyinImages, generateDouyinImages, listDouyinImages } from '@/api/douyinImages'

vi.mock('@/api/douyinImages', () => ({
  listDouyinImages: vi.fn(), generateDouyinImages: vi.fn(), retryDouyinImages: vi.fn(), applyDouyinImages: vi.fn(),
}))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn() } }))

const slots = { template: '<div><slot /></div>' }
const options = {
  props: { modelValue: true, articleId: 42, currentVersionNo: 1 },
  global: { stubs: {
    ElDialog: slots, ElForm: slots, ElFormItem: slots, ElSelect: slots, ElOption: true,
    ElInputNumber: true, ElEmpty: true, ElAlert: true, ElTag: slots, ElImage: true,
    ElCollapse: slots, ElCollapseItem: slots,
    ElButton: { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot /></button>' },
  } },
}
const response = (data: unknown) => ({ data: { code: 0, message: '', data } })

describe('Douyin image testing flow', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(listDouyinImages).mockResolvedValue(response([]) as never)
    vi.mocked(generateDouyinImages).mockResolvedValue(response({ id: 9 }) as never)
  })
  afterEach(() => { vi.useRealTimers(); vi.resetAllMocks() })

  it('creates a preview batch without applying or publishing it', async () => {
    const wrapper = mount(DouyinImageStudio, options)
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '生成一组测试图')!.trigger('click')
    await flushPromises()
    expect(generateDouyinImages).toHaveBeenCalledWith(42, { requestKey: expect.any(String), imageCount: 4, styleCode: 'auto' })
    expect(applyDouyinImages).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('keeps the idempotency key after a failed network response', async () => {
    vi.mocked(generateDouyinImages).mockRejectedValueOnce(new Error('network'))
    const wrapper = mount(DouyinImageStudio, options)
    await flushPromises()
    const button = wrapper.findAll('button').find(button => button.text() === '生成一组测试图')!
    await button.trigger('click'); await flushPromises()
    await button.trigger('click'); await flushPromises()
    expect(vi.mocked(generateDouyinImages).mock.calls[0]![1].requestKey)
      .toBe(vi.mocked(generateDouyinImages).mock.calls[1]![1].requestKey)
    wrapper.unmount()
  })

  it('stops polling when closed and blocks applying stale article images', async () => {
    vi.mocked(listDouyinImages).mockResolvedValue(response([
      { id: 9, sourceVersionNo: 1, status: 'running', imageCount: 4, imageUrls: [], styleCode: 'minimal' },
      { id: 10, sourceVersionNo: 0, status: 'succeeded', imageCount: 4, imageUrls: [], styleCode: 'editorial' },
    ]) as never)
    const wrapper = mount(DouyinImageStudio, options)
    await flushPromises()
    expect(wrapper.findAll('button').find(button => button.text() === '应用这一组')!.attributes('disabled')).toBeDefined()
    await vi.advanceTimersByTimeAsync(60000)
    expect(listDouyinImages).toHaveBeenCalledTimes(1)
    await wrapper.findAll('button').find(button => button.text() === '刷新')!.trigger('click')
    await flushPromises()
    expect(listDouyinImages).toHaveBeenCalledTimes(2)
    await wrapper.setProps({ modelValue: false })
    await vi.advanceTimersByTimeAsync(9000)
    expect(listDouyinImages).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
