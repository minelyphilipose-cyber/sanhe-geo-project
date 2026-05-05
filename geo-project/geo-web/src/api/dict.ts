import request from './request'
import type { PageResult, R } from '@/types'

export interface DictItem {
  dictType: string
  dictKey: string
  dictValue: string
  sortOrder: number
  remark?: string | null
}

export type DictGroup = Record<string, DictItem[]>

export function getDictItems(types?: string[]) {
  return request.get<R<DictGroup>>('/dicts/items', {
    params: { types: types?.length ? types.join(',') : undefined },
  })
}

export interface DictAdminItem extends DictItem {
  id: number
  enabled: boolean
  remark?: string | null
  createdAt: string
  updatedAt: string
}

export interface DictAdminQuery {
  current?: number
  size?: number
  dictType?: string
  keyword?: string
  enabled?: boolean
}

export function getAdminDictPage(params: DictAdminQuery) {
  return request.get<R<PageResult<DictAdminItem>>>('/admin/dicts', { params })
}

export function getAdminDictTypes() {
  return request.get<R<string[]>>('/admin/dicts/types')
}

export function createAdminDictItem(payload: {
  dictType: string
  dictKey: string
  dictValue: string
  sortOrder: number
  enabled?: boolean
  remark?: string
}) {
  return request.post<R<DictAdminItem>>('/admin/dicts', payload)
}

export function updateAdminDictItem(
  id: number,
  payload: {
    dictType: string
    dictKey: string
    dictValue: string
    sortOrder: number
    remark?: string
  },
) {
  return request.put<R<DictAdminItem>>(`/admin/dicts/${id}`, payload)
}

export function updateAdminDictItemStatus(id: number, enabled: boolean) {
  return request.put<R<void>>(`/admin/dicts/${id}/status`, { enabled })
}
