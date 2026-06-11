import request from './request'
import type {
  R,
  PageResult,
  Project,
  KeywordGroup,
  KeywordGroupImportResult,
  KeywordLlmQuestionGenerateResult,
  KeywordGroupQuestion,
  KeywordGroupPayload,
  KeywordPreviewResult,
  KeywordTypeConfig,
  ProjectChannelAllocationQuota,
  ProjectKeywordGroupQuota,
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

export interface ProjectSelfMediaScheduleConfig {
  id?: number
  projectId: number
  brandId?: number
  companyId?: number
  autoScheduleEnabled: boolean
  defaultScheduleStrategy: string
  includeAdjustedWorkdays: boolean
  remark?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface ProjectSelfMediaScheduleBatch {
  id: number
  projectId: number
  brandId: number
  companyId: number
  targetMonth: string
  triggerMode: string
  status: string
  scheduleStrategy: string
  articleCount: number
  accountCount: number
  plannedCount: number
  createdCount: number
  rejectedCount: number
  generationBatchIds?: string | null
  failureMessage?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface ProjectSelfMediaScheduleBatchDetailItem {
  generationBatchId?: number
  generationTaskId?: number
  generationStatus?: string | null
  generationErrorMessage?: string | null
  articleId?: number | null
  articleTitle?: string | null
  selfMediaAccountId?: number
  selfMediaAccountName?: string | null
  platform?: string | null
  scheduleId?: number | null
  scheduleStatus?: string | null
  plannedPublishAt?: string | null
  queueKind?: string | null
  attemptCount?: number | null
  maxAttempts?: number | null
  lastAttemptAt?: string | null
  nextAttemptAt?: string | null
  lockedUntil?: string | null
  scheduleFailureCode?: string | null
  scheduleFailureMessage?: string | null
}

export interface ProjectSelfMediaScheduleBatchDetail {
  batch?: ProjectSelfMediaScheduleBatch | null
  items: ProjectSelfMediaScheduleBatchDetailItem[]
}

export interface ProjectSelfMediaAutoScheduleResponse {
  brandId: number
  targetMonth: string
  scheduleStrategy: string
  requestedCount: number
  plannedCount: number
  rejectedCount: number
  created: boolean
}

export interface ProjectSelfMediaAutoSchedulePayload {
  targetMonth: string
  selfMediaAccountIds?: number[]
  scheduleStrategy?: string
  includeAdjustedWorkdays?: boolean
}

export function getProjectSelfMediaScheduleConfig(id: number) {
  return request.get<R<ProjectSelfMediaScheduleConfig>>(`/projects/${id}/self-media-schedule-config`)
}

export function updateProjectSelfMediaScheduleConfig(id: number, data: {
  autoScheduleEnabled?: boolean
  defaultScheduleStrategy?: string
  includeAdjustedWorkdays?: boolean
  remark?: string | null
}) {
  return request.put<R<ProjectSelfMediaScheduleConfig>>(`/projects/${id}/self-media-schedule-config`, data)
}

export function getProjectSelfMediaScheduleBatch(id: number, targetMonth: string) {
  return request.get<R<ProjectSelfMediaScheduleBatch | null>>(`/projects/${id}/self-media-schedule-batches/${targetMonth}`)
}

export function getProjectSelfMediaScheduleBatchDetail(id: number, targetMonth: string) {
  return request.get<R<ProjectSelfMediaScheduleBatchDetail | null>>(`/projects/${id}/self-media-schedule-batches/${targetMonth}/detail`)
}

export function previewProjectSelfMediaAutoSchedule(id: number, data: ProjectSelfMediaAutoSchedulePayload) {
  return request.post<R<ProjectSelfMediaAutoScheduleResponse>>(`/projects/${id}/self-media-schedules/auto-preview`, data)
}

export function createProjectSelfMediaAutoSchedule(id: number, data: ProjectSelfMediaAutoSchedulePayload) {
  return request.post<R<ProjectSelfMediaAutoScheduleResponse>>(`/projects/${id}/self-media-schedules/auto-create`, data)
}

export function getProjectChannelAllocationQuota(params: { companyId: number; excludeProjectId?: number }) {
  return request.get<R<ProjectChannelAllocationQuota>>('/projects/channel-allocation-quota', { params })
}

export function getProjectKeywordGroupQuota(params: { companyId: number; excludeProjectId?: number }) {
  return request.get<R<ProjectKeywordGroupQuota>>('/projects/keyword-group-quota', { params })
}

export function createProject(data: Record<string, any>) {
  return request.post<R<Project>>('/projects', data)
}

export function updateProject(id: number, data: Record<string, any>) {
  return request.put<R<Project>>(`/projects/${id}`, data)
}

export function updateProjectChannelAllocations(id: number, data: {
  allocationVersion?: number | null
  channelAllocations: Array<{ channelCode: string; allocatedCount: number }>
}) {
  return request.put<R<Project>>(`/projects/${id}/channel-allocations`, data)
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

export function getKeywordGroupPage(params: { current?: number; size?: number; keyword?: string; companyId?: number; projectId?: number; type?: string }) {
  return request.get<R<PageResult<KeywordGroup>>>('/keyword-groups', { params })
}

export function getKeywordGroupTypeConfigs() {
  return request.get<R<KeywordTypeConfig[]>>('/keyword-groups/type-configs')
}

export function getKeywordGroupDetail(id: number) {
  return request.get<R<KeywordGroup>>(`/keyword-groups/${id}`)
}

export function getKeywordGroupQuestions(id: number, params: { current?: number; size?: number; tier?: string }) {
  return request.get<R<PageResult<KeywordGroupQuestion>>>(`/keyword-groups/${id}/questions`, { params })
}

export function updateKeywordGroupQuestion(groupId: number, questionId: number, data: Partial<KeywordGroupQuestion>) {
  return request.put<R<KeywordGroupQuestion>>(`/keyword-groups/${groupId}/questions/${questionId}`, data)
}

export function importProjectKeywordGroup(projectId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<R<KeywordGroupImportResult>>(`/projects/${projectId}/keyword-groups/import`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
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

export function generateKeywordGroupLlmQuestions(data: { companyId?: number; projectId?: number; seedText: string; currentToken?: string; count?: number; currentLlmCount?: number; targetCount?: number }) {
  return request.post<R<KeywordLlmQuestionGenerateResult>>('/keyword-groups/llm-questions/generate', data)
}

