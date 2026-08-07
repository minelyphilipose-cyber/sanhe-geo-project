import request from './request'
import type { R } from '@/types'

export interface PresaleBenchmark {
  id: number
  industry: string
  industryRole: string
  avgOverall: number
  avgMention: number
  avgRanking: number
  avgSentiment: number
  avgCoverage: number
  top1Overall: number
  top1Mention: number
  top1Ranking: number
  top1Sentiment: number
  top1Coverage: number
  top10Score: number
  confidenceLevel: string
  source: string
  sampleSize: number
  enabled: boolean
  effectiveFrom: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export type PresaleBenchmarkPayload = Omit<PresaleBenchmark, 'id' | 'source' | 'createdAt' | 'updatedAt'>

export interface PresaleBenchmarkHistory {
  id: number
  benchmarkId: number
  industry: string
  industryRole: string
  operation: string
  beforeSnapshot?: string
  afterSnapshot?: string
  operatorName?: string
  remark?: string
  createdAt: string
}

export function listPresaleBenchmarks() {
  return request.get<R<PresaleBenchmark[]>>('/presale/benchmarks')
}

export function createPresaleBenchmark(data: PresaleBenchmarkPayload) {
  return request.post<R<PresaleBenchmark>>('/presale/benchmarks', data)
}

export function updatePresaleBenchmark(id: number, data: PresaleBenchmarkPayload) {
  return request.put<R<PresaleBenchmark>>(`/presale/benchmarks/${id}`, data)
}

export function updatePresaleBenchmarkStatus(id: number, enabled: boolean, remark?: string) {
  return request.put<R<PresaleBenchmark>>(`/presale/benchmarks/${id}/status`, { enabled, remark })
}

export function listPresaleBenchmarkHistory(benchmarkId?: number) {
  return request.get<R<PresaleBenchmarkHistory[]>>('/presale/benchmarks/history', {
    params: benchmarkId ? { benchmarkId } : undefined,
  })
}
