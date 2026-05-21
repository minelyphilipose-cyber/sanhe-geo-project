import request from './request'
import type { R } from '@/types'

export interface QuotaSnapshot {
  companyId: number
  projectId?: number
  workorderId?: number
  packageName?: string
  quotaA: number
  quotaB: number
  quotaC: number
  quotaTotal: number
  activeUsedA: number
  activeUsedB: number
  activeUsedC: number
  activeUsedTotal: number
  workorderCountA: number
  workorderCountB: number
  workorderCountC: number
  workorderCountTotal: number
  runningReservedA: number
  runningReservedB: number
  runningReservedC: number
  runningReservedTotal: number
  remainingA: number
  remainingB: number
  remainingC: number
  remainingTotal: number
}

export interface CustomerSearchItem {
  companyId: number
  companyName: string
  brandId?: number
  brandName?: string
  industry?: string
  packageName?: string
  activeBinding: boolean
}

export interface WorkorderVO {
  id: number
  companyId: number
  companyName: string
  brandId?: number
  brandName?: string
  projectId?: number
  projectName?: string
  packageName?: string
  status: string
  targetA: number
  targetB: number
  targetC: number
  quota: QuotaSnapshot
}

export interface WorkorderListItem {
  id: number
  companyId: number
  projectId?: number
  projectName?: string
  workorderNo: string
  packageName?: string
  status: string
  targetA: number
  targetB: number
  targetC: number
  countA: number
  countB: number
  countC: number
  countTotal: number
  batchCount: number
  latestBatchStatus?: string
  latestBatchAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface ProfileVO {
  companyId: number
  projectId?: number
  projectName?: string
  companyName: string
  brandName: string
  brandRelation: string
  coreBusiness: string[]
  targetRegion: string
  industry: string
  targetCustomer: string
  coreAdvantage: string
  benchmarkSpecs: string
  competitors: Array<Record<string, any>>
  coreNeeds: Array<Record<string, any>>
}

export interface ProviderVO {
  id: number
  platformCode: string
  platformName: string
  modelId: string
  modelName: string
}

export interface BatchVO {
  id: number
  workorderId: number
  batchNo: string
  requestA: number
  requestB: number
  requestC: number
  actualA: number
  actualB: number
  actualC: number
  batchType?: 'manual' | 'ai'
  modelName?: string
  status: string
  progressJson?: string
  errorMessage?: string
  replaceCountTotal?: number
  partialFlag: boolean
  cancelRequested: boolean
  createdAt?: string
  startedAt?: string
  finishedAt?: string
  logs?: Array<{ eventCode: string; message: string; createdAt: string }>
}

export interface QuestionVO {
  id: number
  batchId: number
  questionText: string
  sceneCode: string
  tier: 'A' | 'B' | 'C'
  priority?: string
  monitorFrequency?: string
  scoreRelevance?: number
  scoreIntent?: number
  scoreCompetition?: number
  scoreConversion?: number
  scoreCoverage?: number
  totalScore?: number
  relatedNeedText?: string
  designReason?: string
  status: string
  replaceCount: number
}

export interface ManualQuestionInput {
  questionText: string
  sceneCode: string
  tier: 'A' | 'B' | 'C'
  priority?: string
  monitorFrequency?: string
  scoreRelevance?: number
  scoreIntent?: number
  scoreCompetition?: number
  scoreConversion?: number
  scoreCoverage?: number
  totalScore?: number
  relatedNeedText?: string
  designReason?: string
}

export interface ReviewVO {
  workorder: WorkorderVO
  batches: BatchVO[]
  questions: QuestionVO[]
}

export interface QuestionPageVO {
  records: QuestionVO[]
  total: number
  current: number
  size: number
  pages: number
}

export function searchGeoCustomers(keyword?: string) {
  return request.get<R<CustomerSearchItem[]>>('/geo/customers/search', { params: { keyword } })
}

export function createOrGetWorkorder(companyId: number) {
  return request.post<R<WorkorderVO>>('/geo/workorder/create-or-get', { companyId })
}

export function createOrGetProjectWorkorder(projectId: number) {
  return request.post<R<WorkorderVO>>('/geo/workorder/create-or-get', { projectId })
}

export function getGeoCustomerProfile(companyId: number) {
  return request.get<R<ProfileVO>>(`/geo/customers/${companyId}/profile`)
}

export function getGeoProjectProfile(projectId: number) {
  return request.get<R<ProfileVO>>(`/geo/projects/${projectId}/profile`)
}

export function getGeoQuota(companyId: number, workorderId?: number) {
  return request.get<R<QuotaSnapshot>>(`/geo/customers/${companyId}/quota`, { params: { workorderId } })
}

export function getGeoProjectQuota(projectId: number, workorderId?: number) {
  return request.get<R<QuotaSnapshot>>(`/geo/projects/${projectId}/quota`, { params: { workorderId } })
}

export function getGeoWorkorders(companyId: number) {
  return request.get<R<WorkorderListItem[]>>(`/geo/customers/${companyId}/workorders`)
}

export function getGeoProjectWorkorders(projectId: number) {
  return request.get<R<WorkorderListItem[]>>(`/geo/projects/${projectId}/workorders`)
}

export function getLlmProviders() {
  return request.get<R<ProviderVO[]>>('/llm/providers')
}

export function saveGeoDraft(data: { workorderId: number; profileJson: string; syncToCustomerProfile: boolean; validationStatus: string }) {
  return request.post<R<any>>('/geo/draft/save', data)
}

export function getGeoDraft(workorderId: number) {
  return request.get<R<any>>(`/geo/draft/${workorderId}`)
}

export function startGeoBatch(data: Record<string, any>) {
  return request.post<R<BatchVO>>('/geo/batch/start', data)
}

export function getGeoBatch(id: number) {
  return request.get<R<BatchVO>>(`/geo/batch/${id}`)
}

export function cancelGeoBatch(id: number) {
  return request.post<R<void>>(`/geo/batch/${id}/cancel`)
}

export function deleteGeoBatch(id: number) {
  return request.delete<R<void>>(`/geo/batch/${id}`)
}

export function getGeoReview(workorderId: number) {
  return request.get<R<ReviewVO>>(`/geo/workorder/${workorderId}/review`)
}

export function getGeoQuestions(workorderId: number, params: { tier?: string; current?: number; size?: number }) {
  return request.get<R<QuestionPageVO>>(`/geo/workorder/${workorderId}/questions`, { params })
}

export function createManualGeoQuestions(workorderId: number, data: { items: ManualQuestionInput[]; manualReason?: string }) {
  return request.post<R<ReviewVO>>(`/geo/workorder/${workorderId}/questions/manual`, data)
}

export function regenerateGeoQuestion(id: number) {
  return request.post<R<any>>(`/geo/question/${id}/regenerate`, {})
}

export function updateGeoQuestion(id: number, data: Partial<QuestionVO>) {
  return request.put<R<QuestionVO>>(`/geo/question/${id}`, data)
}

export function deleteGeoQuestion(id: number) {
  return request.delete<R<void>>(`/geo/question/${id}`)
}

export function commitGeoWorkorder(id: number, versionLabel: string) {
  return request.post<R<any>>(`/geo/workorder/${id}/commit`, { versionLabel })
}

export function exportGeoWorkorder(id: number) {
  return request.get<Blob>(`/geo/workorder/${id}/export`, { responseType: 'blob' })
}
