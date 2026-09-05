import request from './request'
import type { R } from '@/types'

export interface DouyinImageBatch {
  id: number
  articleId: number
  sourceVersionNo: number
  appliedVersionNo: number | null
  status: 'queued' | 'running' | 'succeeded' | 'failed' | 'applied'
  imageCount: number
  styleCode: string
  imageUrls: string[]
  plan: { visualDirection: string; pages: { title: string; subtitle: string; text: string; source: string; visual: string }[] } | null
  errorMessage: string | null
  createdAt: string
}

const path = (articleId: number) => `/content/articles/${articleId}/douyin-images`
export const listDouyinImages = (articleId: number) => request.get<R<DouyinImageBatch[]>>(path(articleId))
export const generateDouyinImages = (articleId: number, data: { requestKey: string; imageCount: number; styleCode: string }) =>
  request.post<R<DouyinImageBatch>>(path(articleId), data)
export const retryDouyinImages = (articleId: number, batchId: number) =>
  request.post<R<DouyinImageBatch>>(`${path(articleId)}/${batchId}/retry`)
export const applyDouyinImages = (articleId: number, batchId: number) =>
  request.post<R<DouyinImageBatch>>(`${path(articleId)}/${batchId}/apply`)
