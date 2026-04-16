import request from './request'
import type {
  R,
  PageResult,
  Project,
  ProjectPlatformOption,
  QuestionPoolManageItemVO,
  QuestionPoolVersionVO,
  KeywordGroup,
  KeywordGroupPayload,
  KeywordPreviewResult,
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

export function generateProjectQuestionStrategies(projectId: number) {
  return request.post<R<any>>(`/projects/${projectId}/generate-question-strategies`)
}

export function generateSingleQuestionStrategy(questionId: number) {
  return request.post<R<any>>(`/question-pools/questions/${questionId}/generate-strategy`)
}

export function updateQuestionStrategy(questionId: number, data: {
  contentStrategy: string
  strategyKeywords?: string[]
  strategySuggestedType: 'faq' | 'scenario_content' | 'industry_article'
}) {
  return request.put<R<void>>(`/question-pools/questions/${questionId}/strategy`, data)
}

export function getKeywordGroupPage(params: { current?: number; size?: number; keyword?: string; companyId?: number; type?: string }) {
  return request.get<R<PageResult<KeywordGroup>>>('/keyword-groups', { params })
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

