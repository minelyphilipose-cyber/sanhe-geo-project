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
  ownerType?: string
  partnerId?: number
  companyId?: number
  brandId?: number
  excludeThirdPartySource?: boolean
}) {
  return request.get<R<PageResult<Project>>>('/projects', { params })
}

export function getProjectDetail(id: number) {
  return request.get<R<Project>>(`/projects/${id}`)
}

export interface PartnerProjectStartRequest {
  id: number
  requestNo: string
  projectId: number
  companyId: number
  partnerId: number
  status: string
  submittedAt?: string | null
}

export function submitPartnerProjectStartRequest(id: number, data?: { requestId?: string; remark?: string }) {
  return request.post<R<PartnerProjectStartRequest>>(`/partner/projects/${id}/start-requests`, data || {})
}

export interface AdminProjectStartRequest {
  id: number
  requestNo: string
  status: string
  projectId: number
  projectStatus?: string | null
  projectDisplayStatus?: string | null
  projectName?: string | null
  companyId: number
  companyName?: string | null
  partnerId: number
  partnerName?: string | null
  brandId?: number | null
  brandName?: string | null
  applicantUserId?: number | null
  applicantUserName?: string | null
  submittedAt?: string | null
  reviewedBy?: number | null
  reviewerName?: string | null
  reviewedAt?: string | null
  assignedInternalOwnerId?: number | null
  assignedInternalOwnerName?: string | null
  defaultInternalOwnerId?: number | null
  defaultInternalOwnerName?: string | null
  pointsRequiredSnapshot?: number | string | null
  discountRateSnapshot?: number | string | null
  packageSnapshotJson?: string | null
  partnerAllocatedQuotaJson?: string | null
  internalDeliverySnapshotJson?: string | null
  rejectReasonCode?: string | null
  rejectReasonText?: string | null
  quotaSnapshotStatus?: string | null
  quotaLockedAt?: string | null
  quotaReleasedAt?: string | null
  pointsTxnId?: number | null
  pointsTxnNo?: string | null
  pointsTxnAmount?: number | string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export function getAdminProjectStartRequests(params: {
  current?: number
  size?: number
  status?: string
  partnerId?: number
  companyId?: number
  projectId?: number
}) {
  return request.get<R<PageResult<AdminProjectStartRequest>>>('/admin/project-start-requests', { params })
}

export function getAdminProjectStartRequestDetail(id: number) {
  return request.get<R<AdminProjectStartRequest>>(`/admin/project-start-requests/${id}`)
}

export function approveAdminProjectStartRequest(id: number, data: { assignedInternalOwnerId?: number | null; reviewRemark?: string }) {
  return request.post<R<AdminProjectStartRequest>>(`/admin/project-start-requests/${id}/approve`, data)
}

export function rejectAdminProjectStartRequest(id: number, data: { rejectReasonCode?: string; rejectReasonText?: string }) {
  return request.post<R<AdminProjectStartRequest>>(`/admin/project-start-requests/${id}/reject`, data)
}

export function markAdminProjectStartRequestSetupReady(id: number, data?: { remark?: string }, silentError = false) {
  return request.post<R<AdminProjectStartRequest>>(`/admin/project-start-requests/${id}/setup-ready`, data || {}, { silentError } as any)
}

export interface ProjectSelfMediaScheduleConfig {
  id?: number
  projectId: number
  brandId?: number
  companyId?: number
  autoScheduleEnabled: boolean
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
  requestedCount?: number | null
  deficitCount?: number | null
  carryOverCount?: number | null
  decisionOperatorId?: number | null
  decisionReason?: string | null
  capacitySnapshotJson?: string | null
  generationBatchIds?: string | null
  failureMessage?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface ProjectSelfMediaScheduleBatchDetailItem {
  generationBatchId?: number
  generationTaskId?: number
  sourceBrandId?: number | null
  sourceBrandName?: string | null
  subjectBrandId?: number | null
  subjectBrandName?: string | null
  subjectProjectId?: number | null
  generationStatus?: string | null
  generationErrorMessage?: string | null
  generationTopic?: string | null
  generationArticleType?: string | null
  generationCreatedAt?: string | null
  generationUpdatedAt?: string | null
  generationStartedAt?: string | null
  generationFinishedAt?: string | null
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
  claimDiagnosticCode?: string | null
  claimDiagnosticMessage?: string | null
  failureGroupCode?: string | null
  failureGroupLabel?: string | null
  operatorActionHint?: string | null
  allowedActions?: string[]
  autoCompensationAvailable?: boolean | null
  autoCompensationRemaining?: number | null
}

export interface ProjectSelfMediaScheduleBatchDetail {
  batch?: ProjectSelfMediaScheduleBatch | null
  items: ProjectSelfMediaScheduleBatchDetailItem[]
  failureSummaries?: ProjectSelfMediaScheduleFailureSummary[]
  statusRules?: ProjectSelfMediaScheduleStatusRule[]
  actionPreview?: ProjectSelfMediaScheduleActionPreview | null
}

export interface ProjectSelfMediaScheduleFailureSummary {
  code?: string | null
  label?: string | null
  category?: string | null
  count: number
  retryable?: boolean | null
  actionHint?: string | null
  firstMessage?: string | null
  groupCode?: string | null
  groupLabel?: string | null
  operatorAction?: string | null
}

export interface ProjectSelfMediaScheduleStatusRule {
  status: string
  label?: string | null
  meaning?: string | null
  allowedActions?: string[]
  operatorHint?: string | null
}

export interface ProjectSelfMediaScheduleActionPreview {
  retryFailedCount?: number | null
  retryAbnormalCount?: number | null
  manualCount?: number | null
  rescheduleNextMonthCount?: number | null
  ignoreCount?: number | null
  unableCount?: number | null
  nextMonth?: string | null
  messages?: string[]
}

export interface ProjectSelfMediaAutoScheduleResponse {
  brandId: number
  targetMonth: string
  scheduleStrategy: string
  requestedCount: number
  normalRequiredCount?: number | null
  pendingCarryOverCount?: number | null
  availableSlotCount?: number | null
  deficitCount?: number | null
  enough?: boolean | null
  recommendedStrategy?: string | null
  decisionStrategy?: string | null
  plannedCount: number
  rejectedCount: number
  created: boolean
  carryOverCreated?: boolean | null
  carryOverCount?: number | null
  carryOverTargetMonth?: string | null
  unavailableCarryOverCount?: number | null
  warnings?: string[]
  carryOverSources?: ProjectSelfMediaCarryOverSource[]
  plannedItems?: ProjectSelfMediaAutoSchedulePreviewItem[]
  slotGroups?: ProjectSelfMediaAutoScheduleSlotGroup[]
}

export interface ProjectSelfMediaCarryOverSource {
  id?: number | null
  sourceMonth?: string | null
  targetMonth?: string | null
  carryOverCount?: number | null
  consumedCount?: number | null
  pendingCount?: number | null
  status?: string | null
}

export interface ProjectSelfMediaAutoSchedulePreviewItem {
  articleId?: number | null
  selfMediaAccountId?: number | null
  platform?: string | null
  calendarDate?: string | null
  plannedPublishAt?: string | null
  windowName?: string | null
  status?: string | null
  rejectionCode?: string | null
  rejectionMessage?: string | null
}

export interface ProjectSelfMediaAutoScheduleSlotGroup {
  platform?: string | null
  platformLabel?: string | null
  scheduleStrategy?: string | null
  requestedCount: number
  availableSlotCount: number
  deficitCount?: number | null
  remainingWorkdayCount?: number | null
  enough: boolean
  message?: string | null
  selectedSlots?: Array<{
    executionAt?: string | null
    plannedPublishAt?: string | null
    windowName?: string | null
  }>
}

export interface ProjectSelfMediaAutoSchedulePayload {
  targetMonth: string
  selfMediaAccountIds?: number[]
  scheduleStrategy?: string
  includeAdjustedWorkdays?: boolean
  decisionStrategy?: string
  decisionReason?: string
  supplementExistingBatch?: boolean
}

export interface ProjectBusinessCalendarStatus {
  year: number
  exists: boolean
  activeSource: 'runtime' | 'classpath' | 'missing' | string
  runtimePath: string
  classpathLocation: string
  sourceUrl: string | null
  updatedAt: string | null
  publishAllowedDays: number
  adjustedWorkdays: number
  holidays: number
}

export function getProjectSelfMediaScheduleConfig(id: number) {
  return request.get<R<ProjectSelfMediaScheduleConfig>>(`/projects/${id}/self-media-schedule-config`)
}

export function updateProjectSelfMediaScheduleConfig(id: number, data: {
  autoScheduleEnabled?: boolean
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

export function getProjectSelfMediaScheduleCalendarStatus(id: number, targetMonth: string) {
  return request.get<R<ProjectBusinessCalendarStatus>>(`/projects/${id}/self-media-schedule-calendar-status`, {
    params: { targetMonth },
  })
}

export function retryProjectSelfMediaScheduleBatchFailedItems(id: number, targetMonth: string) {
  return request.post<R<ProjectSelfMediaScheduleBatchDetail>>(`/projects/${id}/self-media-schedule-batches/${targetMonth}/retry-failed`)
}

export function retryProjectSelfMediaScheduleBatchAbnormalSchedules(id: number, targetMonth: string) {
  return request.post<R<ProjectSelfMediaScheduleBatchDetail>>(`/projects/${id}/self-media-schedule-batches/${targetMonth}/retry-abnormal-schedules`)
}

export function markProjectSelfMediaScheduleBatchAbnormalManualRequired(id: number, targetMonth: string) {
  return request.post<R<ProjectSelfMediaScheduleBatchDetail>>(`/projects/${id}/self-media-schedule-batches/${targetMonth}/mark-abnormal-manual-required`)
}

export function rescheduleProjectSelfMediaScheduleBatchAbnormalNextMonth(id: number, targetMonth: string) {
  return request.post<R<ProjectSelfMediaScheduleBatchDetail>>(`/projects/${id}/self-media-schedule-batches/${targetMonth}/reschedule-abnormal-next-month`)
}

export function ignoreProjectSelfMediaScheduleBatchAbnormalSchedules(id: number, targetMonth: string) {
  return request.post<R<ProjectSelfMediaScheduleBatchDetail>>(`/projects/${id}/self-media-schedule-batches/${targetMonth}/ignore-abnormal-schedules`)
}

export function previewProjectSelfMediaAutoSchedule(id: number, data: ProjectSelfMediaAutoSchedulePayload) {
  return request.post<R<ProjectSelfMediaAutoScheduleResponse>>(`/projects/${id}/self-media-schedules/auto-preview`, data)
}

export function createProjectSelfMediaAutoSchedule(id: number, data: ProjectSelfMediaAutoSchedulePayload, idempotencyKey?: string) {
  return request.post<R<ProjectSelfMediaAutoScheduleResponse>>(`/projects/${id}/self-media-schedules/auto-create`, data, {
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
  })
}

export function getProjectChannelAllocationQuota(params: { companyId: number; excludeProjectId?: number }, silentError = false) {
  return request.get<R<ProjectChannelAllocationQuota>>('/projects/channel-allocation-quota', { params, silentError } as any)
}

export function getProjectKeywordGroupQuota(params: { companyId: number; excludeProjectId?: number }, silentError = false) {
  return request.get<R<ProjectKeywordGroupQuota>>('/projects/keyword-group-quota', { params, silentError } as any)
}

export function createProject(data: Record<string, any>, silentError = false) {
  return request.post<R<Project>>('/projects', data, { silentError } as any)
}

export function updateProject(id: number, data: Record<string, any>, silentError = false) {
  return request.put<R<Project>>(`/projects/${id}`, data, { silentError } as any)
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

