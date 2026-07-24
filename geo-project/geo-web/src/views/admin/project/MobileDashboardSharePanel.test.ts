import ElementPlus from 'element-plus'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MobileDashboardSharePanel from './MobileDashboardSharePanel.vue'

const api = vi.hoisted(() => ({
  createMobileDashboardShare: vi.fn(),
  deleteMobileDashboardShare: vi.fn(),
  disableMobileDashboardShare: vi.fn(),
  getMobileDashboardShareAccessSummary: vi.fn(),
  getMobileDashboardShares: vi.fn(),
  getMobileDashboardWechatSharePreview: vi.fn(),
}))

vi.mock('@/api/mobileDashboard', () => api)
enableAutoUnmount(afterEach)

describe('MobileDashboardSharePanel', () => {
  beforeEach(() => {
    api.getMobileDashboardShares.mockResolvedValue({ data: { data: [] } })
    api.getMobileDashboardShareAccessSummary.mockResolvedValue({ data: { data: [] } })
    api.getMobileDashboardWechatSharePreview.mockResolvedValue({
      data: {
        data: {
          title: '移动数据看板 | 三河市示例客户有限公司',
          description: '手机数据看板｜查看核心问题监测与内容数据',
          imageUrl: '/favicon.png',
          wechatJsSdkEnabled: true,
          rolloutMode: 'allowlist',
        },
      },
    })
    api.createMobileDashboardShare.mockResolvedValue({
      data: {
        data: {
          shareUrl: 'http://localhost:3000/m/MAHEKSKZ',
        },
      },
    })
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
    })
  })

  it('shows the customer name while preserving the original share URL', async () => {
    const wrapper = mount(MobileDashboardSharePanel, {
      props: {
        projectId: 11,
        editable: true,
        customerName: '三河市示例客户有限公司',
      },
      attachTo: document.body,
      global: {
        plugins: [ElementPlus],
      },
    })
    await flushPromises()

    const createButton = wrapper.findAll('button')
      .find((button) => button.text().includes('生成新链接'))
    expect(createButton).toBeDefined()
    await createButton!.trigger('click')
    await flushPromises()

    const namedLink = wrapper.get('.created-share-link')
    expect(namedLink.text()).toBe('三河市示例客户有限公司')
    expect(namedLink.attributes('href')).toBe('http://localhost:3000/m/MAHEKSKZ')
    expect(wrapper.text()).not.toContain('http://localhost:3000/m/MAHEKSKZ')

    const copyButton = wrapper.findAll('button')
      .find((button) => button.text().trim() === '复制链接')
    expect(copyButton).toBeDefined()
    await copyButton!.trigger('click')

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
      'http://localhost:3000/m/MAHEKSKZ',
    )
  })
})
