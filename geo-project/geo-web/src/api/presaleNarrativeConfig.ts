import request from './request'
import type { R } from '@/types'

export interface PresaleNarrativeFindingCopy {
  id: number
  configVersion: string
  code: string
  tier: string
  bandOverride?: string | null
  archetypeOverride?: string | null
  titleTemplate: string
  bodyTemplate: string
  evidenceTemplate: string
  priority: number
  enabled: boolean
  remark?: string | null
}

export interface PresaleHeatmapSummaryConfig {
  id: number
  configVersion: string
  heatmapPattern: string
  bandOverride?: string | null
  summaryTemplate: string
  colorLegendTemplate: string
  sortOrder: number
  enabled: boolean
  remark?: string | null
}

export interface PresaleIndustryLexiconReviewTask {
  id: number
  industry: string
  industryKey: string
  draftJson?: string | null
  status: string
  source?: string | null
  draftSource?: string | null
  rejectReason?: string | null
  fallbackHitCount?: number | null
  draftedBy?: number | null
  draftedAt?: string | null
  approvedBy?: number | null
  approvedAt?: string | null
  rejectedBy?: number | null
  rejectedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface PresaleLexiconBucket {
  id: number
  bucketCode: string
  bucketName: string
  customerTerm: string
  conversionTerm: string
  defaultIndustryShort?: string | null
  enabled: boolean
  source?: string | null
  remark?: string | null
}

export interface PresaleIndustryBucketMapping {
  id: number
  industry: string
  industryKey: string
  bucketCode: string
  industryShort?: string | null
  approved: boolean
  source?: string | null
  originTaskId?: number | null
  approvedBy?: number | null
  approvedAt?: string | null
}

export interface PresaleNarrativeConfigAdminResponse {
  configVersion: string
  findingCopies: PresaleNarrativeFindingCopy[]
  heatmapSummaries: PresaleHeatmapSummaryConfig[]
  lexiconBuckets: PresaleLexiconBucket[]
  industryBucketMappings: PresaleIndustryBucketMapping[]
  lexiconReviewTasks: PresaleIndustryLexiconReviewTask[]
}

export type PresaleNarrativeFindingCopyPayload = Pick<
  PresaleNarrativeFindingCopy,
  'titleTemplate' | 'bodyTemplate' | 'evidenceTemplate' | 'priority' | 'enabled' | 'remark'
>

export type PresaleHeatmapSummaryPayload = Pick<
  PresaleHeatmapSummaryConfig,
  'summaryTemplate' | 'colorLegendTemplate' | 'sortOrder' | 'enabled' | 'remark'
>

export interface PresaleIndustryBucketDraftPayload {
  bucketCode: string
  industryShort?: string | null
  suggestNewBucket?: boolean
  reason?: string | null
}

export interface PresaleIndustryBucketRejectPayload {
  reason?: string | null
}

export type PresaleLexiconBucketPayload = Pick<
  PresaleLexiconBucket,
  'bucketName' | 'customerTerm' | 'conversionTerm' | 'defaultIndustryShort' | 'enabled' | 'remark'
>

export type PresaleLexiconBucketCreatePayload = PresaleLexiconBucketPayload & Pick<PresaleLexiconBucket, 'bucketCode'>

export type PresaleIndustryBucketMappingPayload = Pick<
  PresaleIndustryBucketMapping,
  'bucketCode' | 'industryShort'
> & {
  remark?: string | null
}

export function getPresaleNarrativeConfig() {
  return request.get<R<PresaleNarrativeConfigAdminResponse>>('/presale/narrative-config')
}

export function updatePresaleNarrativeFindingCopy(id: number, data: PresaleNarrativeFindingCopyPayload) {
  return request.put<R<PresaleNarrativeFindingCopy>>(`/presale/narrative-config/finding-copy/${id}`, data)
}

export function updatePresaleHeatmapSummary(id: number, data: PresaleHeatmapSummaryPayload) {
  return request.put<R<PresaleHeatmapSummaryConfig>>(`/presale/narrative-config/heatmap-summary/${id}`, data)
}

export function draftPresaleIndustryBucket(id: number) {
  return request.post<R<PresaleIndustryLexiconReviewTask>>(`/presale/narrative-config/lexicon-task/${id}/draft`)
}

export function updatePresaleIndustryBucketDraft(id: number, data: PresaleIndustryBucketDraftPayload) {
  return request.put<R<PresaleIndustryLexiconReviewTask>>(`/presale/narrative-config/lexicon-task/${id}/draft`, data)
}

export function approvePresaleIndustryBucketTask(id: number) {
  return request.post<R<PresaleIndustryLexiconReviewTask>>(`/presale/narrative-config/lexicon-task/${id}/approve`)
}

export function rejectPresaleIndustryBucketTask(id: number, data: PresaleIndustryBucketRejectPayload) {
  return request.post<R<PresaleIndustryLexiconReviewTask>>(`/presale/narrative-config/lexicon-task/${id}/reject`, data)
}

export function createPresaleLexiconBucket(data: PresaleLexiconBucketCreatePayload) {
  return request.post<R<PresaleLexiconBucket>>('/presale/narrative-config/lexicon-bucket', data)
}

export function updatePresaleLexiconBucket(id: number, data: PresaleLexiconBucketPayload) {
  return request.put<R<PresaleLexiconBucket>>(`/presale/narrative-config/lexicon-bucket/${id}`, data)
}

export function updatePresaleIndustryBucketMapping(id: number, data: PresaleIndustryBucketMappingPayload) {
  return request.put<R<PresaleIndustryBucketMapping>>(`/presale/narrative-config/industry-bucket-mapping/${id}`, data)
}
