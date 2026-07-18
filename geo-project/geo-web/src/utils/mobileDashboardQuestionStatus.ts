import type { QuestionMonitorItem } from '@/types/mobileDashboard'

export type QuestionMonitorStatus = 'mentioned' | 'not_mentioned' | 'search_not_triggered' | 'pending'

export function questionMonitorStatus(item: QuestionMonitorItem): QuestionMonitorStatus {
  if (item.monitorStatus === 'mentioned'
    || item.monitorStatus === 'not_mentioned'
    || item.monitorStatus === 'search_not_triggered'
    || item.monitorStatus === 'pending') {
    return item.monitorStatus
  }
  if (item.mentioned) return 'mentioned'
  return item.pollResultId ? 'not_mentioned' : 'pending'
}

export function isSearchNotTriggered(item: QuestionMonitorItem) {
  return questionMonitorStatus(item) === 'search_not_triggered'
}
