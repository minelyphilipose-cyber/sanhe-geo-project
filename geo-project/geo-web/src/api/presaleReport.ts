import request from '@/api/request'
import { unwrap } from '@/api/presale/unwrap'
import type { R } from '@/types'

// ============================================================================
// Response VO(与后端一一对应)
// ============================================================================

/**
 * 版本元信息 VO。用于列表行、版本管理、进度轮询。
 */
export interface ReportVersionMetaVO {
  versionId: number
  versionNo: number
  generationStatus: 'INIT' | 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED'
  generationStage?:
    | 'BATCH1'
    | 'JUDGE_COGNITIVE'
    | 'COMPETITOR_EXTRACT'
    | 'BATCH2'
    | 'JUDGE_COMPARISON'
    | 'L1_AGGREGATE'
    | 'L2_COMPUTE'
    | 'L3_INIT'
    | null
  totalLlmCalls?: number
  completedLlmCalls?: number
  batch1TotalCalls?: number | null
  batch1CompletedCalls?: number | null
  batch2TotalCalls?: number | null
  batch2CompletedCalls?: number | null
  extractedCompetitorCount?: number | null
  isDegraded?: boolean
  degradedPlatforms?: string[]
  failureReason?: string | null
  frozen: boolean
  frozenAt: string | null
  contentUpdatedAt: string | null
  exportSuccessCount: number
  exportSuccessAt: string | null
  createdAt: string
}

/**
 * 列表行 VO。
 */
export interface ReportListItemVO {
  reportId: number
  brandName: string
  industry: string
  industryRole: string
  region: string
  versionCount: number
  latestVersion: ReportVersionMetaVO | null
  canEdit: boolean
  canEditReason: string | null
  createdAt: string
}

/**
 * 详情 VO。含 L1/L2/L3 JSON 字符串,由前端 mergeSnapshot 合成 MergedView。
 */
export interface ReportDetailVO {
  reportId: number
  brandName: string
  industry: string
  industryRole: string
  region: string
  userDemand: string | null
  createdAt: string
  version: ReportVersionMetaVO
  rawSnapshotJson: string | null
  computedSnapshotJson: string | null
  editableContentJson: string | null
}

/**
 * 新建报告页诊断范围预览。
 */
export interface ReportScopePreviewVO {
  platformCount: number
  genericPromptCount: number
  competitorPromptCount: number
  promptQueryCount: number
  llmCallUpperBound: number
  dimensionCount: number
}

export interface PromptTemplateVO {
  id: number
  promptCode: string
  category: string
  businessValue: string
  promptContent: string
  hasCompetitorVar: boolean
  sortOrder: number
  remark: string | null
  templateVersion: string
}

export type PromptSourceMode = 'template' | 'llm'
export type PresalePromptCategoryCode =
  | 'RECOMMENDATION'
  | 'COMPARISON'
  | 'PROBLEM'
  | 'COGNITIVE'
  | 'SCENARIO'

export interface LlmPromptQuestionPlan {
  totalCount: number
  categoryCounts: Record<PresalePromptCategoryCode, number>
}

export interface LlmPromptQuestionDraft {
  categoryCode: PresalePromptCategoryCode
  promptContent: string
}

export interface LlmPromptQuestionGenerateRequest {
  brandName: string
  industry: string
  industryRole: string
  region: string
  userType?: string
  userDemand?: string
  totalCount: number
  categoryCounts: Record<PresalePromptCategoryCode, number>
  existingQuestions?: LlmPromptQuestionDraft[]
}

export interface LlmPromptQuestionGenerateVO {
  requestedTotal: number
  generatedTotal: number
  missingTotal: number
  requestedCategoryCounts: Record<PresalePromptCategoryCode, number>
  generatedCategoryCounts: Record<PresalePromptCategoryCode, number>
  missingCategoryCounts: Record<PresalePromptCategoryCode, number>
  questions: Array<LlmPromptQuestionDraft & {
    categoryLabel: string
    hasCompetitorVar: boolean
  }>
  warnings?: string[]
}

/**
 * MyBatis-Plus Page 响应结构。
 */
export interface Page<T> {
  records: T[]
  total: number
  size: number
  current: number
}

// ============================================================================
// Request 类型
// ============================================================================

export interface CreateReportRequest {
  brandName: string
  industry: string
  industryRole: string
  region: string
  userDemand?: string
  userType?: string
  promptSourceMode?: PromptSourceMode
  promptTemplateVersion?: string
  promptTemplates?: PromptTemplateDraftRequest[]
  llmQuestionPlan?: LlmPromptQuestionPlan
  llmPromptQuestions?: LlmPromptQuestionDraft[]
}

export interface PromptTemplateDraftRequest {
  sourceTemplateId: number
  promptContent: string
}

export interface ReportListQueryRequest {
  page?: number
  pageSize?: number
  keyword?: string
  industry?: string
  industryRole?: string
  generationStatus?: string
  frozen?: boolean
  /** RFC3339 含时区,如 2026-04-01T00:00:00+08:00 */
  startAt?: string
  endAt?: string
  sortBy?: 'createdAt' | 'brandName'
  sortDir?: 'asc' | 'desc'
}

/** PATCH L3 编辑请求。 */
export interface EditVersionContentRequest {
  editableContentJson: string
  expectedContentUpdatedAt?: string | null
  forceOverwrite?: boolean
}

/** POST 派生请求(v1 空壳,预留扩展)。 */
export type DeriveVersionRequest = Record<string, never>

/** POST 冻结请求。 */
export interface FreezeVersionRequest {
  reason?: string
}

// ============================================================================
// 写动作响应 VO(P1·F·1·b·1 后端新增)
// ============================================================================

/**
 * edit / freeze / unfreeze 通用响应。
 */
export interface VersionActionResponseVO {
  versionId: number
  versionNo: number
  generationStatus: ReportVersionMetaVO['generationStatus']
  frozen: boolean
  /** 仅 freeze 响应回填,其他操作为 null。 */
  frozenAt: string | null
  updatedAt: string
}

/**
 * derive 响应。latestVersionId 应等于 newVersionId(定稿条款:派生后自动切新版为当前)。
 */
export interface DeriveVersionResponseVO {
  newVersionId: number
  newVersionNo: number
  sourceVersionId: number
  sourceVersionNo: number
  latestVersionId: number
}

/**
 * retry 响应。
 */
export interface RetryVersionResponseVO {
  versionId: number
  versionNo: number
  /** 重置目标固定为 QUEUED。 */
  generationStatus: 'QUEUED'
}

// ============================================================================
// 读模型 API(P1·F·1·a,unwrap 口径修复)
// ============================================================================

/**
 * 列表查询。
 */
export function listReports(params: ReportListQueryRequest) {
  return unwrap(
    request.get<R<Page<ReportListItemVO>>>('/presale/reports', { params })
  )
}

/**
 * 新建报告,返回 reportId。
 */
export function createReport(data: CreateReportRequest) {
  return unwrap(request.post<R<number>>('/presale/reports', data))
}

/**
 * 删除报告。后端当前为软删除:列表/详情不可见,版本与导出审计数据保留。
 */
export function deleteReport(reportId: number) {
  return unwrap(request.delete<R<void>>(`/presale/reports/${reportId}`))
}

/**
 * 新建报告页:取实时诊断范围预览。
 */
export function getReportScopePreview() {
  return unwrap(
    request.get<R<ReportScopePreviewVO>>('/presale/reports/scope-preview')
  )
}

/**
 * 新建报告页:反显当前 active version 的 Prompt 原文模板。
 */
export function listPromptTemplates() {
  return unwrap(
    request.get<R<PromptTemplateVO[]>>('/presale/reports/prompt-templates')
  )
}

export function generateLlmPromptQuestions(data: LlmPromptQuestionGenerateRequest) {
  return unwrap(
    request.post<R<LlmPromptQuestionGenerateVO>>(
      '/presale/reports/llm-prompt-questions/generate',
      data,
      { timeout: 60000 }
    )
  )
}

/**
 * 取最新版本详情。
 */
export function getLatestDetail(reportId: number) {
  return unwrap(
    request.get<R<ReportDetailVO>>(
      `/presale/reports/${reportId}/versions/latest`
    )
  )
}

/**
 * 取指定版本详情。
 */
export function getVersionDetail(reportId: number, versionNo: number) {
  return unwrap(
    request.get<R<ReportDetailVO>>(
      `/presale/reports/${reportId}/versions/${versionNo}`
    )
  )
}

/**
 * 进度页轮询:只取版本元信息,响应体小,3 秒轮询不影响性能。
 */
export function getLatestVersionMeta(reportId: number) {
  return unwrap(
    request.get<R<ReportVersionMetaVO>>(
      `/presale/reports/${reportId}/versions/latest/meta`
    )
  )
}

// ============================================================================
// 写动作 API(P1·F·1·b·1 后端新增)
// ============================================================================

/**
 * PATCH:编辑 L3 内容。
 * 权限:presale.report.edit_content
 * 409:版本已冻结 / 版本非 DONE
 */
export function editVersionContent(
  reportId: number,
  versionNo: number,
  data: EditVersionContentRequest
) {
  return unwrap(
    request.patch<R<VersionActionResponseVO>>(
      `/presale/reports/${reportId}/versions/${versionNo}/content`,
      data
    )
  )
}

/**
 * POST:派生新版本。
 * 权限:presale.report.edit_content
 * 409:源版本非 DONE
 * 副作用:派生后自动切 latestVersionId 到新版
 */
export function deriveVersion(
  reportId: number,
  versionNo: number,
  data?: DeriveVersionRequest
) {
  return unwrap(
    request.post<R<DeriveVersionResponseVO>>(
      `/presale/reports/${reportId}/versions/${versionNo}/derive`,
      data ?? {}
    )
  )
}

/**
 * POST:冻结版本(sales/manager 均可)。
 * 权限:presale.report.edit_content
 * 409:版本已冻结 / 版本非 DONE
 */
export function freezeVersion(
  reportId: number,
  versionNo: number,
  data?: FreezeVersionRequest
) {
  return unwrap(
    request.post<R<VersionActionResponseVO>>(
      `/presale/reports/${reportId}/versions/${versionNo}/freeze`,
      data ?? {}
    )
  )
}

/**
 * POST:解冻版本(manager only)。
 * 权限:presale.report.manage
 * 409:版本未冻结
 */
export function unfreezeVersion(reportId: number, versionNo: number) {
  return unwrap(
    request.post<R<VersionActionResponseVO>>(
      `/presale/reports/${reportId}/versions/${versionNo}/unfreeze`
    )
  )
}

/**
 * DELETE:物理删除版本(manager only)。
 * 权限:presale.report.manage
 * 409:版本已导出过(export_success_count > 0)
 * 副作用:删除当前 latest 时自动回退 latestVersionId 到最大存活版,无存活置 null
 */
export function deleteVersion(reportId: number, versionNo: number) {
  return unwrap(
    request.delete<R<void>>(
      `/presale/reports/${reportId}/versions/${versionNo}`
    )
  )
}

/**
 * POST:重试 FAILED 版本(复用 versionNo,覆盖失败数据)。
 * 权限:presale.report.edit_content
 * 409:版本非 FAILED
 */
export function retryVersion(reportId: number, versionNo: number) {
  return unwrap(
    request.post<R<RetryVersionResponseVO>>(
      `/presale/reports/${reportId}/versions/${versionNo}/retry`
    )
  )
}

/**
 * POST:重跑 DONE/FAILED 版本(同版本重跑)。
 */
export function regenerateVersion(reportId: number, versionNo: number) {
  return unwrap(
    request.post<R<RetryVersionResponseVO>>(
      `/presale/reports/${reportId}/versions/${versionNo}/regenerate`
    )
  )
}
