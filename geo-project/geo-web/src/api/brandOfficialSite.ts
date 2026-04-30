import request from './request'
import type { R } from '@/types'

// Legacy C1 customer official CMS API. Backend controller is cold-stored in Phase 2A;
// keep this file for a future restore, but do not wire it into active UI flows.

export interface BrandOfficialSite {
  id: number
  brandId: number
  siteName: string
  siteDomain?: string | null
  cmsFrameworkCode: string
  tenantKey: string
  apiEndpoint: string
  authType?: string | null
  status: string
  lastCheckAt?: string | null
  lastCheckResult?: string | null
  remark?: string | null
  createdAt: string
  updatedAt: string
}

export interface BrandOfficialSiteCreateRequest {
  siteName: string
  siteDomain?: string
  cmsFrameworkCode: string
  tenantKey: string
  apiEndpoint: string
  authType?: string
  credentials: string
  remark?: string
}

export interface BrandOfficialSiteUpdateRequest {
  siteName?: string
  siteDomain?: string
  cmsFrameworkCode?: string
  tenantKey?: string
  apiEndpoint?: string
  authType?: string
  credentials?: string
  remark?: string
}

export interface AuthCheckResult {
  success: boolean
  failureKind?: string | null
  message?: string | null
}

export interface DistributionTask {
  id: number
  articleId?: number
  projectId?: number
  targetKind?: string
  brandOfficialSiteId?: number
  attemptNo?: number
  status: string
  failureKind?: string | null
  platformArticleId?: string | null
  publishedUrl?: string | null
  errorMessage?: string | null
}

export async function listBrandOfficialSites(brandId: number): Promise<BrandOfficialSite[]> {
  const { data } = await request.get<R<BrandOfficialSite[]>>('/brand-official-sites', { params: { brandId } })
  return data.data
}

export async function getBrandOfficialSite(id: number): Promise<BrandOfficialSite> {
  const { data } = await request.get<R<BrandOfficialSite>>(`/brand-official-sites/${id}`)
  return data.data
}

export async function createBrandOfficialSite(
  brandId: number,
  req: BrandOfficialSiteCreateRequest,
): Promise<BrandOfficialSite> {
  const { data } = await request.post<R<BrandOfficialSite>>('/brand-official-sites', req, { params: { brandId } })
  return data.data
}

export async function updateBrandOfficialSite(
  id: number,
  req: BrandOfficialSiteUpdateRequest,
): Promise<BrandOfficialSite> {
  const { data } = await request.put<R<BrandOfficialSite>>(`/brand-official-sites/${id}`, req)
  return data.data
}

export async function deleteBrandOfficialSite(id: number): Promise<void> {
  await request.delete<R<void>>(`/brand-official-sites/${id}`)
}

export async function checkAuthBrandOfficialSite(id: number): Promise<AuthCheckResult> {
  const { data } = await request.post<R<AuthCheckResult>>(`/brand-official-sites/${id}/check-auth`)
  return data.data
}

export async function distributeArticleToBrandOfficialSite(
  siteId: number,
  articleId: number,
): Promise<DistributionTask> {
  const { data } = await request.post<R<DistributionTask>>(`/brand-official-sites/${siteId}/distribute`, null, {
    params: { articleId },
  })
  return data.data
}
