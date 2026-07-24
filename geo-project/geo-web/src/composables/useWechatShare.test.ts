import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useWechatShare } from './useWechatShare'

const api = vi.hoisted(() => ({
  getMobileDashboardWechatConfig: vi.fn(),
  reportMobileDashboardWechatError: vi.fn(),
}))

vi.mock('@/api/mobileDashboard', () => api)

function installSuccessfulWx() {
  let readyCallback: (() => void) | undefined
  const config = vi.fn(() => queueMicrotask(() => readyCallback?.()))
  const updateAppMessageShareData = vi.fn((options: { success?: () => void }) => options.success?.())
  const updateTimelineShareData = vi.fn((options: { success?: () => void }) => options.success?.())
  Object.defineProperty(window, 'wx', {
    configurable: true,
    value: {
      config,
      ready: (callback: () => void) => {
        readyCallback = callback
      },
      error: vi.fn(),
      checkJsApi: (options: { success: (result: { checkResult: Record<string, boolean> }) => void }) => {
        options.success({
          checkResult: {
            updateAppMessageShareData: true,
            updateTimelineShareData: true,
          },
        })
      },
      updateAppMessageShareData,
      updateTimelineShareData,
    },
  })
  return { config, updateAppMessageShareData }
}

function mountWechatShare() {
  let share: ReturnType<typeof useWechatShare> | undefined
  const wrapper = mount(defineComponent({
    setup() {
      share = useWechatShare({
        sessionToken: () => 'session',
        shareCode: () => 'MAHEKSKZ',
      })
      return () => h('div')
    },
  }))
  return { wrapper, share: share! }
}

describe('useWechatShare', () => {
  beforeEach(() => {
    vi.useRealTimers()
    Reflect.deleteProperty(window, 'wx')
    Object.defineProperty(navigator, 'userAgent', {
      configurable: true,
      value: 'Mozilla/5.0 Android MicroMessenger/8.0',
    })
    window.history.replaceState({}, '', '/m/MAHEKSKZ/monitor?source=wechat')
    sessionStorage.clear()
    api.getMobileDashboardWechatConfig.mockResolvedValue({
      data: {
        data: {
          enabled: true,
          appId: 'wx_test',
          timestamp: 1_700_000_000,
          nonceStr: 'nonce',
          signature: 'signature',
          share: {
            title: '华为鸿蒙智家',
            description: '手机数据看板',
            link: 'https://www.huanjingaigeo.com/m/MAHEKSKZ',
            imageUrl: 'https://www.huanjingaigeo.com/favicon.png',
          },
        },
      },
    })
    api.reportMobileDashboardWechatError.mockResolvedValue({ data: { data: null } })
  })

  it('configures friend sharing after checkJsApi succeeds', async () => {
    const { config, updateAppMessageShareData } = installSuccessfulWx()
    const { wrapper, share } = mountWechatShare()

    expect(await share.configure()).toBe(true)
    expect(config).toHaveBeenCalledWith(expect.objectContaining({
      appId: 'wx_test',
      jsApiList: expect.arrayContaining(['updateAppMessageShareData']),
    }))
    expect(updateAppMessageShareData).toHaveBeenCalledWith(expect.objectContaining({
      title: '华为鸿蒙智家',
      link: 'https://www.huanjingaigeo.com/m/MAHEKSKZ',
    }))
    expect(share.isReady.value).toBe(true)
    expect(share.guideVisible.value).toBe(true)

    wrapper.unmount()
  })

  it('retries a transient cold-cache signature failure', async () => {
    vi.useFakeTimers()
    installSuccessfulWx()
    api.getMobileDashboardWechatConfig
      .mockRejectedValueOnce({ status: 503 })
      .mockResolvedValueOnce({
        data: {
          data: {
            enabled: true,
            appId: 'wx_test',
            timestamp: 1_700_000_000,
            nonceStr: 'nonce',
            signature: 'signature',
            share: {
              title: '华为鸿蒙智家',
              description: '手机数据看板',
              link: 'https://www.huanjingaigeo.com/m/MAHEKSKZ',
              imageUrl: 'https://www.huanjingaigeo.com/favicon.png',
            },
          },
        },
      })
    const { wrapper, share } = mountWechatShare()

    const result = share.configure()
    await vi.advanceTimersByTimeAsync(500)

    expect(await result).toBe(true)
    expect(api.getMobileDashboardWechatConfig).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('ignores an older route request after a newer configuration starts', async () => {
    installSuccessfulWx()
    let resolveFirst: ((value: unknown) => void) | undefined
    let resolveSecond: ((value: unknown) => void) | undefined
    api.getMobileDashboardWechatConfig
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveFirst = resolve
      }))
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveSecond = resolve
      }))
    const response = {
      data: {
        data: {
          enabled: true,
          appId: 'wx_test',
          timestamp: 1_700_000_000,
          nonceStr: 'nonce',
          signature: 'signature',
          share: {
            title: '华为鸿蒙智家',
            description: '手机数据看板',
            link: 'https://www.huanjingaigeo.com/m/MAHEKSKZ',
            imageUrl: 'https://www.huanjingaigeo.com/favicon.png',
          },
        },
      },
    }
    const { wrapper, share } = mountWechatShare()

    const first = share.configure()
    window.history.replaceState({}, '', '/m/MAHEKSKZ/report')
    const second = share.configure()
    resolveSecond?.(response)
    expect(await second).toBe(true)
    resolveFirst?.(response)

    expect(await first).toBe(false)
    expect(share.isReady.value).toBe(true)
    wrapper.unmount()
  })

  it('reports a wx.config timeout without blocking the dashboard', async () => {
    vi.useFakeTimers()
    Object.defineProperty(window, 'wx', {
      configurable: true,
      value: {
        config: vi.fn(),
        ready: vi.fn(),
        error: vi.fn(),
        checkJsApi: vi.fn(),
        updateAppMessageShareData: vi.fn(),
        updateTimelineShareData: vi.fn(),
      },
    })
    const { wrapper, share } = mountWechatShare()

    const result = share.configure()
    await vi.advanceTimersByTimeAsync(10_000)

    expect(await result).toBe(false)
    expect(api.reportMobileDashboardWechatError).toHaveBeenCalledWith(
      'session',
      'config',
      'timeout',
    )
    expect(share.isReady.value).toBe(false)
    wrapper.unmount()
  })
})
