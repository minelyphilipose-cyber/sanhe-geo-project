import type { ExtensionTaskListItem } from '@/types/extension'

export interface TaskListState {
  tasks: ExtensionTaskListItem[]
  expandedTaskId: number | null
}

const ACTIVE_STATUSES = new Set(['token_issued', 'filling', 'filled'])

export function createTaskListState(): TaskListState {
  return {
    tasks: [],
    expandedTaskId: null,
  }
}

export function mergeTasks(
  state: TaskListState,
  incoming: ExtensionTaskListItem[],
  now: Date = new Date(),
): TaskListState {
  const byId = new Map<number, ExtensionTaskListItem>()
  for (const task of incoming) {
    if (!ACTIVE_STATUSES.has(task.status)) continue
    if (isExpired(task, now)) continue
    const existed = byId.get(task.taskId)
    if (!existed || Date.parse(task.fillTokenIssuedAt) >= Date.parse(existed.fillTokenIssuedAt)) {
      byId.set(task.taskId, task)
    }
  }

  state.tasks = Array.from(byId.values()).sort((a, b) =>
    Date.parse(b.fillTokenIssuedAt) - Date.parse(a.fillTokenIssuedAt),
  )
  if (state.expandedTaskId !== null && !byId.has(state.expandedTaskId)) {
    state.expandedTaskId = null
  }
  return state
}

export function toggleTaskExpanded(state: TaskListState, taskId: number): TaskListState {
  state.expandedTaskId = state.expandedTaskId === taskId ? null : taskId
  return state
}

export function isExpired(task: Pick<ExtensionTaskListItem, 'expiresAt'>, now: Date = new Date()): boolean {
  return Date.parse(task.expiresAt) <= now.getTime()
}

export function formatCountdown(expiresAt: string, now: Date = new Date()): string {
  const remainingSeconds = Math.max(0, Math.floor((Date.parse(expiresAt) - now.getTime()) / 1000))
  const minutes = Math.floor(remainingSeconds / 60)
  const seconds = remainingSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}
