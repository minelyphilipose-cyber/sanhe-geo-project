import { computed, onScopeDispose, ref, watch } from 'vue'
import { getProjectSelfMediaPlanningTask, type ProjectSelfMediaPlanningTask } from '@/api/project'

export function useProjectSelfMediaPlanning(
  scope: () => { projectId: number; month: string } | null,
  onFinished: (task: ProjectSelfMediaPlanningTask) => void | Promise<void>,
) {
  const task = ref<ProjectSelfMediaPlanningTask | null>(null)
  const checking = ref(false)
  const error = ref('')
  const pending = computed(() => task.value?.status === 'pending')
  let timer: ReturnType<typeof setTimeout> | undefined
  let revision = 0
  let disposed = false

  function clearTimer() {
    if (timer !== undefined) clearTimeout(timer)
    timer = undefined
  }

  function poll() {
    clearTimer()
    if (!disposed && (pending.value || error.value)) timer = setTimeout(() => { void refresh() }, 3000)
  }

  async function refresh() {
    const current = scope()
    if (!current || disposed) return
    const token = ++revision
    const previous = task.value
    checking.value = true
    try {
      const { data } = await getProjectSelfMediaPlanningTask(current.projectId, current.month)
      if (token !== revision || disposed) return
      task.value = data.data
      error.value = ''
      if (previous?.status === 'pending' && task.value && task.value.status !== 'pending') {
        try { await onFinished(task.value) }
        catch (refreshError) { console.warn('Failed to refresh completed self-media planning results', refreshError) }
      }
    } catch {
      if (token === revision && !disposed) error.value = '暂时无法获取排期结果，正在重新查询。请勿重复提交。'
    } finally {
      if (token === revision && !disposed) {
        checking.value = false
        poll()
      }
    }
  }

  function track(accepted: ProjectSelfMediaPlanningTask) {
    const current = scope()
    if (!current || accepted.projectId !== current.projectId || accepted.targetMonth !== current.month || disposed) return
    ++revision
    task.value = accepted
    error.value = ''
    checking.value = false
    poll()
  }

  watch(() => {
    const current = scope()
    return current ? `${current.projectId}/${current.month}` : ''
  }, () => {
    ++revision
    clearTimer()
    task.value = null
    error.value = ''
    checking.value = false
    void refresh()
  }, { immediate: true, flush: 'sync' })

  onScopeDispose(() => {
    disposed = true
    ++revision
    clearTimer()
  })

  return { task, checking, pending, error, refresh, track }
}
