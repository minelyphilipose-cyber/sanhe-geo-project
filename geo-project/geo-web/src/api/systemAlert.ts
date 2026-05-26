import request from './request'
import type { PageResult, R, SystemAlertTodoItem } from '@/types'

export interface SystemAlertTodoQuery {
  current?: number
  size?: number
}

export function getMySystemAlertTodos(params?: SystemAlertTodoQuery) {
  return request.get<R<PageResult<SystemAlertTodoItem>>>('/system/alerts/my-todos', { params })
}

export function resolveSystemAlert(id: number) {
  return request.post<R<void>>(`/system/alerts/${id}/resolve`)
}
