import ElementPlus from 'element-plus'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PlatformHealth from './PlatformHealth.vue'

const router = vi.hoisted(() => ({
  push: vi.fn(),
}))
const permissionState = vi.hoisted(() => ({
  manualVerificationAllowed: true,
}))
const dispatchApi = vi.hoisted(() => ({
  getDispatchPlatforms: vi.fn(),
  getHunyuanCapacity: vi.fn(),
  getLlmPoolSnapshot: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => router,
}))
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    hasPermission: () => permissionState.manualVerificationAllowed,
  }),
}))
vi.mock('@/api/dispatch', () => dispatchApi)
enableAutoUnmount(afterEach)

describe('PlatformHealth', () => {
  beforeEach(() => {
    router.push.mockReset()
    permissionState.manualVerificationAllowed = true
    dispatchApi.getDispatchPlatforms.mockResolvedValue({
      data: { data: [] },
    })
    dispatchApi.getHunyuanCapacity.mockResolvedValue({
      data: { data: null },
    })
    dispatchApi.getLlmPoolSnapshot.mockResolvedValue({
      data: { data: null },
    })
  })

  it('shows the manual verification entry for authorized users and opens the hidden route', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const entry = wrapper.findAll('button')
      .find((candidate) => candidate.text().includes('手工验链'))
    expect(entry).toBeDefined()

    await entry!.trigger('click')

    expect(router.push).toHaveBeenCalledWith({
      name: 'QuestionPollVerification',
    })
  })

  it('does not expose the manual verification entry without permission', async () => {
    permissionState.manualVerificationAllowed = false

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).not.toContain('手工验链')
  })
})

function mountPage() {
  return mount(PlatformHealth, {
    global: {
      plugins: [ElementPlus],
    },
  })
}
