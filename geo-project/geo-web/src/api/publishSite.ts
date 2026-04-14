import request from './request'
import type { PublishSite, R } from '@/types'

export function getPublishSites(params?: {
  tier?: string
  status?: string
  industry?: string
}) {
  return request.get<R<PublishSite[]>>('/system/publish-sites', { params })
}

export function getPublishSiteDetail(id: number) {
  return request.get<R<PublishSite>>(`/system/publish-sites/${id}`)
}

export function createPublishSite(data: Record<string, any>) {
  return request.post<R<PublishSite>>('/system/publish-sites', data)
}

export function updatePublishSite(id: number, data: Record<string, any>) {
  return request.put<R<PublishSite>>(`/system/publish-sites/${id}`, data)
}

export function updatePublishSiteStatus(id: number, status: string) {
  return request.patch<R<PublishSite>>(`/system/publish-sites/${id}/status`, { status })
}

export function testPublishSite(id: number) {
  return request.post<R<Record<string, any>>>(`/system/publish-sites/${id}/test`)
}
