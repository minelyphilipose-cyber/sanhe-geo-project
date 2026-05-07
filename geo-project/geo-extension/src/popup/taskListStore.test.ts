import { describe, expect, it } from 'vitest'
import {
  createTaskListState,
  formatCountdown,
  mergeTasks,
  toggleTaskExpanded,
} from './taskListStore'
import type { ExtensionTaskListItem } from '@/types/extension'

describe('taskListStore', () => {
  it('deduplicates by taskId and keeps the newest status', () => {
    const state = createTaskListState()

    mergeTasks(state, [
      task(1, 'token_issued', '2026-05-07T10:00:00Z'),
      task(1, 'filling', '2026-05-07T10:01:00Z'),
      task(1, 'token_issued', '2026-05-07T09:59:00Z'),
    ], new Date('2026-05-07T10:02:00Z'))

    expect(state.tasks).toHaveLength(1)
    expect(state.tasks[0].status).toBe('filling')
  })

  it('updates task state on refresh', () => {
    const state = createTaskListState()
    mergeTasks(state, [task(1, 'token_issued', '2026-05-07T10:00:00Z')], new Date('2026-05-07T10:01:00Z'))
    mergeTasks(state, [task(1, 'filled', '2026-05-07T10:00:00Z')], new Date('2026-05-07T10:02:00Z'))

    expect(state.tasks[0].status).toBe('filled')
  })

  it('hides expired tasks and clears stale expansion', () => {
    const state = createTaskListState()
    state.expandedTaskId = 1

    mergeTasks(state, [task(1, 'filling', '2026-05-07T10:00:00Z')], new Date('2026-05-07T10:06:00Z'))

    expect(state.tasks).toEqual([])
    expect(state.expandedTaskId).toBeNull()
  })

  it('toggles expanded task without triggering fill behavior', () => {
    const state = createTaskListState()

    toggleTaskExpanded(state, 1)
    expect(state.expandedTaskId).toBe(1)
    toggleTaskExpanded(state, 1)
    expect(state.expandedTaskId).toBeNull()
  })

  it('formats countdown from expiresAt', () => {
    expect(formatCountdown('2026-05-07T10:05:09Z', new Date('2026-05-07T10:04:00Z'))).toBe('1:09')
  })
})

function task(
  taskId: number,
  status: ExtensionTaskListItem['status'],
  fillTokenIssuedAt: string,
): ExtensionTaskListItem {
  return {
    taskId,
    platform: 'douyin',
    status,
    publishUrl: null,
    title: '标题',
    createdAt: '2026-05-07T09:59:00Z',
    fillTokenIssuedAt,
    expiresAt: '2026-05-07T10:05:00Z',
  }
}
