import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ExtensionApiError, extensionApi } from '@/shared/api'
import { sessionStorage } from '@/shared/storage'
import App from './App.vue'

let runtimeListener: ((message: unknown) => boolean) | undefined

vi.mock('@/shared/env', () => ({
  EXTENSION_VERSION: '0.1.0',
}))

vi.mock('@/shared/storage', () => ({
  sessionStorage: {
    get: vi.fn(async () => ({
      token: 'ext.secret',
      sessionId: 88,
      extensionVersion: '0.1.0',
      expiresAt: '2026-05-14T00:00:00Z',
      boundAt: '2026-05-07T00:00:00Z',
    })),
    set: vi.fn(),
    clear: vi.fn(),
    getOrCreateInstallId: vi.fn(async () => 'install-1'),
  },
}))

vi.mock('@/shared/api', async () => {
  const actual = await vi.importActual<typeof import('@/shared/api')>('@/shared/api')
  return {
    ...actual,
    extensionApi: {
      versionCheck: vi.fn(async () => ({ supported: true })),
      tasks: vi.fn(async () => []),
      selfMediaAccounts: vi.fn(async () => []),
      bind: vi.fn(),
      revoke: vi.fn(),
      refresh: vi.fn(),
    },
  }
})

beforeEach(() => {
  runtimeListener = undefined
  vi.mocked(sessionStorage.get).mockResolvedValue({
    token: 'ext.secret',
    sessionId: 88,
    extensionVersion: '0.1.0',
    expiresAt: '2026-05-14T00:00:00Z',
    boundAt: '2026-05-07T00:00:00Z',
  })
  vi.mocked(sessionStorage.set).mockResolvedValue(undefined)
  vi.mocked(sessionStorage.clear).mockResolvedValue(undefined)
  vi.mocked(sessionStorage.getOrCreateInstallId).mockResolvedValue('install-1')
  vi.mocked(extensionApi.bind).mockReset()
  vi.mocked(extensionApi.revoke).mockResolvedValue(undefined)
  vi.mocked(extensionApi.tasks).mockResolvedValue([])
  vi.mocked(extensionApi.selfMediaAccounts).mockResolvedValue([])
  vi.stubGlobal('confirm', vi.fn(() => true))
  vi.stubGlobal('chrome', {
    runtime: {
      onMessage: {
        addListener: vi.fn((listener) => { runtimeListener = listener }),
        removeListener: vi.fn(),
      },
      sendMessage: vi.fn(),
    },
  })
})

describe('popup task list', () => {
  it('renders empty task message for bound session', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无可处理的半自动发布任务')
  })

  it('does not append rebind hint for non-auth task errors', async () => {
    vi.mocked(extensionApi.tasks).mockRejectedValueOnce(
      new ExtensionApiError(429, 70013, 'rate limited'),
    )

    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.text()).toContain('操作太频繁，系统正在保护任务状态，请稍后再试。')
    expect(wrapper.text()).not.toContain('请重新绑定后再试')
  })

  it('shows lifecycle messages without replacing bound state', async () => {
    const wrapper = mount(App)
    await flushPromises()

    runtimeListener?.({
      type: 'GEO_TASK_LIFECYCLE_EVENT',
      payload: { taskId: 30, kind: 'published', message: '任务已上报 published。' },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('任务已上报 published。')
  })

  it('renders account brand name instead of raw brand id', async () => {
    vi.mocked(extensionApi.selfMediaAccounts).mockResolvedValueOnce([
      {
        accountId: 20,
        platform: 'toutiao',
        accountName: '头条账号',
        brandId: 8,
        brandName: '三合星链',
      },
    ])

    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.text()).toContain('toutiao / 头条账号 / 三合星链')
    expect(wrapper.text()).not.toContain('brand 8')
  })

  it('leaves binding state immediately after bind succeeds even if account refresh is slow', async () => {
    vi.mocked(sessionStorage.get).mockResolvedValueOnce(null)
    vi.mocked(extensionApi.bind).mockResolvedValueOnce({
      token: 'ext.new',
      sessionId: 90,
      expiresAt: '2026-05-15T00:00:00Z',
    })
    vi.mocked(extensionApi.selfMediaAccounts).mockReturnValueOnce(new Promise(() => []))

    const wrapper = mount(App)
    await flushPromises()

    await wrapper.find('input').setValue('ABCD-EFGH')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('绑定成功，sessionId 90')
    expect(wrapper.text()).not.toContain('绑定中...')
  })

  it('returns to bind form after unbind even when remote revoke fails', async () => {
    vi.mocked(extensionApi.revoke).mockRejectedValueOnce(new Error('network failed'))

    const wrapper = mount(App)
    await flushPromises()

    const unbindButton = wrapper.findAll('button').find(button => button.text() === '解绑')
    expect(unbindButton).toBeTruthy()
    await unbindButton!.trigger('click')
    await flushPromises()

    expect(sessionStorage.clear).toHaveBeenCalled()
    expect(wrapper.text()).toContain('绑定码')
    expect(wrapper.text()).toContain('本地已解绑')
    expect(wrapper.text()).not.toContain('Session88')
    expect(wrapper.text()).not.toContain('捕获凭证')
  })
})

function flushPromises() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
