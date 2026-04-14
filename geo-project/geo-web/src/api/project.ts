import request from './request'
import type {
  R,
  PageResult,
  Project,
  ProjectPlatformOption,
  QuestionPoolManageItemVO,
  QuestionPoolVersionVO,
} from '@/types'

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

export function getProjectPlatformOptions() {
  return request.get<R<Record<string, ProjectPlatformOption[]>>>('/projects/platform-options')
}

export function createProject(data: Record<string, any>) {
  return request.post<R<Project>>('/projects', data)
}

export function updateProject(id: number, data: Record<string, any>) {
  return request.put<R<Project>>(`/projects/${id}`, data)
}

export function updateProjectStage(id: number, stage: string) {
  return request.put<R<void>>(`/projects/${id}/stage`, { stage })
}

export function updateProjectStatus(id: number, status: string) {
  return request.put<R<void>>(`/projects/${id}/status`, { status })
}

export function updateProjectFlow(id: number, status: string, stage: string) {
  return request.put<R<void>>(`/projects/${id}/flow`, { status, stage })
}

export function deleteProject(id: number) {
  return request.delete<R<void>>(`/projects/${id}`)
}

export function getCurrentQuestionPool(projectId: number) {
  return request.get<R<QuestionPoolVersionVO | null>>(`/projects/${projectId}/question-pool/current`)
}

export function getQuestionPoolVersions(projectId: number, params: { current?: number; size?: number }) {
  return request.get<R<PageResult<QuestionPoolVersionVO>>>(`/projects/${projectId}/question-pool/versions`, { params })
}

export function getQuestionPoolVersionDetail(projectId: number, versionNo: number) {
  return request.get<R<QuestionPoolVersionVO>>(`/projects/${projectId}/question-pool/versions/${versionNo}`)
}

export function getQuestionPoolManagePage(params: { current?: number; size?: number; keyword?: string; projectId?: number }) {
  return request.get<R<PageResult<QuestionPoolManageItemVO>>>('/question-pools', { params })
}

