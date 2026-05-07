import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { ExtensionApiError, extensionApi } from '@/shared/api'
import App from './App.vue'

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
      bind: vi.fn(),
      revoke: vi.fn(),
      refresh: vi.fn(),
    },
  }
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

    expect(wrapper.text()).toContain('操作过于频繁，请稍后再试。')
    expect(wrapper.text()).not.toContain('请重新绑定后再试')
  })
})

function flushPromises() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
