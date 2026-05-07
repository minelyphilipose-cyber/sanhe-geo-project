import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ExtensionApiError, extensionApi } from '@/shared/api'
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
})

function flushPromises() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
