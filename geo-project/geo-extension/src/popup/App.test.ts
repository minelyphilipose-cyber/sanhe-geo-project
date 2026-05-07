import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
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
})

function flushPromises() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
