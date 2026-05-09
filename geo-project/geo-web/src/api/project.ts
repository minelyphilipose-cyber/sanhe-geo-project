import request from './request'
import type {
  R,
  PageResult,
  Project,
  KeywordGroup,
  KeywordLlmQuestionGenerateResult,
  KeywordGroupPayload,
  KeywordPreviewResult,
  KeywordTypeConfig,
} from '@/types'

export function getProjectList(params: {
  current?: number
  size?: number
  keyword?: string
  status?: string
  stage?: string
  partnerId?: number
  brandId?: number
}) {
  return request.get<R<PageResult<Project>>>('/projects', { params })
}

export function getProjectDetail(id: number) {
  return request.get<R<Project>>(`/projects/${id}`)
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

export function getKeywordGroupPage(params: { current?: number; size?: number; keyword?: string; companyId?: number; type?: string }) {
  return request.get<R<PageResult<KeywordGroup>>>('/keyword-groups', { params })
}

export function getKeywordGroupTypeConfigs() {
  return request.get<R<KeywordTypeConfig[]>>('/keyword-groups/type-configs')
}

export function getKeywordGroupDetail(id: number) {
  return request.get<R<KeywordGroup>>(`/keyword-groups/${id}`)
}

export function createKeywordGroup(data: KeywordGroupPayload) {
  return request.post<R<KeywordGroup>>('/keyword-groups', data)
}

export function updateKeywordGroup(id: number, data: KeywordGroupPayload) {
  return request.put<R<KeywordGroup>>(`/keyword-groups/${id}`, data)
}

export function deleteKeywordGroup(id: number) {
  return request.delete<R<void>>(`/keyword-groups/${id}`)
}

export function previewKeywordGroup(data: KeywordGroupPayload) {
  return request.post<R<KeywordPreviewResult>>('/keyword-groups/preview', data)
}

export function generateKeywordGroupLlmQuestions(data: { companyId: number; seedText: string; currentToken?: string; count?: number; currentLlmCount?: number; targetCount?: number }) {
  return request.post<R<KeywordLlmQuestionGenerateResult>>('/keyword-groups/llm-questions/generate', data)
}

