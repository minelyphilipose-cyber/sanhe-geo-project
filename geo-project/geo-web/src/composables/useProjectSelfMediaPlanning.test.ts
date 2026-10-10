import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, ref } from 'vue'
import { flushPromises } from '@vue/test-utils'
import { useProjectSelfMediaPlanning } from './useProjectSelfMediaPlanning'
import type { ProjectSelfMediaPlanningTask } from '@/api/project'

const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/api/project', () => ({ getProjectSelfMediaPlanningTask: api.get }))

function pending(month = '2026-10'): ProjectSelfMediaPlanningTask {
  return { id: 42, projectId: 7, targetMonth: month, status: 'pending', supplementExistingBatch: true }
}

function response(task: ProjectSelfMediaPlanningTask | null) {
  return { data: { data: task } }
}

function setup() {
  const scope = effectScope()
  const month = ref('2026-10')
  const finished = vi.fn()
  const planning = scope.run(() => useProjectSelfMediaPlanning(() => ({ projectId: 7, month: month.value }), finished))!
  return { scope, month, finished, planning }
}

describe('project self-media async planning', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    api.get.mockReset().mockResolvedValue(response(null))
  })
  afterEach(() => { vi.useRealTimers() })

  it('recovers a pending task after reopening the page and refreshes once when completed', async () => {
    api.get.mockResolvedValueOnce(response(pending()))
    const { scope, planning, finished } = setup()
    await flushPromises()
    expect(planning.pending.value).toBe(true)
    expect(planning.checking.value).toBe(false)
    api.get.mockResolvedValueOnce(response({ ...pending(), status: 'success', result: {
      brandId: 8, targetMonth: '2026-10', scheduleStrategy: 'platform_specific',
      requestedCount: 30, plannedCount: 30, rejectedCount: 0, created: true,
    } }))
    await vi.advanceTimersByTimeAsync(3000)
    expect(planning.pending.value).toBe(false)
    expect(planning.task.value?.result?.plannedCount).toBe(30)
    expect(finished).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(9000)
    expect(api.get).toHaveBeenCalledTimes(2)
    scope.stop()
  })

  it('tracks submission without waiting for planning and reports business failure', async () => {
    const { scope, planning, finished } = setup()
    await flushPromises()
    planning.track(pending())
    expect(planning.pending.value).toBe(true)
    api.get.mockResolvedValueOnce(response({ ...pending(), status: 'failed', failureMessage: '剩余额度不足' }))
    await vi.advanceTimersByTimeAsync(3000)
    expect(planning.task.value?.failureMessage).toBe('剩余额度不足')
    expect(planning.pending.value).toBe(false)
    expect(finished).toHaveBeenCalledTimes(1)
    scope.stop()
  })

  it('keeps the pending state during a transient network failure and recovers by polling', async () => {
    api.get.mockResolvedValueOnce(response(pending()))
    const { scope, planning } = setup()
    await flushPromises()
    api.get.mockRejectedValueOnce(new Error('network'))
    await vi.advanceTimersByTimeAsync(3000)
    expect(planning.pending.value).toBe(true)
    expect(planning.error.value).toContain('请勿重复提交')
    api.get.mockResolvedValueOnce(response({ ...pending(), status: 'success' }))
    await vi.advanceTimersByTimeAsync(3000)
    expect(planning.pending.value).toBe(false)
    expect(planning.error.value).toBe('')
    scope.stop()
  })

  it('does not use a delayed response from another month', async () => {
    let resolveOld!: (value: ReturnType<typeof response>) => void
    api.get.mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve }))
    const { scope, planning, month } = setup()
    month.value = '2026-11'
    await flushPromises()
    resolveOld(response(pending()))
    await flushPromises()
    expect(planning.task.value).toBeNull()
    planning.track(pending())
    expect(planning.task.value).toBeNull()
    scope.stop()
  })

  it('stops polling and ignores requests that complete after leaving the page', async () => {
    api.get.mockResolvedValueOnce(response(pending()))
    const { scope, planning, finished } = setup()
    await flushPromises()
    let resolveQuery!: (value: ReturnType<typeof response>) => void
    api.get.mockReturnValueOnce(new Promise((resolve) => { resolveQuery = resolve }))
    await vi.advanceTimersByTimeAsync(3000)
    scope.stop()
    resolveQuery(response({ ...pending(), status: 'success' }))
    await flushPromises()
    await vi.advanceTimersByTimeAsync(9000)
    expect(finished).not.toHaveBeenCalled()
    expect(planning.pending.value).toBe(true)
    expect(api.get).toHaveBeenCalledTimes(2)
  })

  it('an accepted task takes priority over an earlier status query', async () => {
    let resolveQuery!: (value: ReturnType<typeof response>) => void
    api.get.mockReturnValueOnce(new Promise((resolve) => { resolveQuery = resolve }))
    const { scope, planning } = setup()
    planning.track(pending())
    resolveQuery(response(null))
    await flushPromises()
    expect(planning.pending.value).toBe(true)
    scope.stop()
  })

  it('blocks uncertain initial status and retries instead of inviting duplicate submissions', async () => {
    api.get.mockRejectedValueOnce(new Error('timeout'))
    const { scope, planning } = setup()
    await flushPromises()
    expect(planning.error.value).toBeTruthy()
    await vi.advanceTimersByTimeAsync(3000)
    expect(planning.error.value).toBe('')
    expect(planning.task.value).toBeNull()
    scope.stop()
  })

  it('preserves confirmed completion if refreshing the remaining quota fails', async () => {
    api.get.mockResolvedValueOnce(response(pending()))
    const { scope, planning, finished } = setup()
    await flushPromises()
    finished.mockRejectedValueOnce(new Error('preview unavailable'))
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => {})
    api.get.mockResolvedValueOnce(response({ ...pending(), status: 'success' }))
    await vi.advanceTimersByTimeAsync(3000)
    expect(planning.task.value?.status).toBe('success')
    expect(planning.error.value).toBe('')
    expect(planning.pending.value).toBe(false)
    warning.mockRestore()
    scope.stop()
  })
})
