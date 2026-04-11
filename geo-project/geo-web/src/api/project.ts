import request from './request'
import type { R, PageResult, Project } from '@/types'

export function getProjectList(params: {
  current?: number
  size?: number
  keyword?: string
  status?: string
  stage?: string
  partnerId?: number
}) {
  return request.get<R<PageResult<Project>>>('/projects', { params })
}

export function getProjectDetail(id: number) {
  return request.get<R<Project>>(`/projects/${id}`)
}

export function createProject(data: Partial<Project>) {
  return request.post<R<Project>>('/projects', data)
}

export function updateProject(id: number, data: Partial<Project>) {
  return request.put<R<Project>>(`/projects/${id}`, data)
}

export function updateProjectStage(id: number, stage: string) {
  return request.put<R<void>>(`/projects/${id}/stage`, { stage })
}
