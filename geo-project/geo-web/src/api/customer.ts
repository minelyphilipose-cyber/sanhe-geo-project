import request from './request'
import type {
  R,
  PageResult,
  Company,
  Brand,
  BrandOffering,
  BrandImageFolder,
  BrandMaterial,
  BrandProfileVersion,
  CompanyAccount,
  CompanyAccountTxn,
  CompanyPackageBinding,
  CompanyDistributionQuota,
  CompanyKeywordGroupQuota,
  BrandStatementView,
  DispatchTaskItem,
} from '@/types'

export function getCompanyList(params: {
  current?: number
  size?: number
  keyword?: string
  ownerType?: string
  partnerId?: number
}) {
  return request.get<R<PageResult<Company>>>('/companies', { params })
}

export function getCompanyDetail(id: number) {
  return request.get<R<Company>>(`/companies/${id}`)
}

export function createCompany(data: Record<string, any>) {
  return request.post<R<Company>>('/companies', data)
}

export function updateCompany(id: number, data: Record<string, any>) {
  return request.put<R<Company>>(`/companies/${id}`, data)
}

export function deleteCompany(id: number) {
  return request.delete<R<void>>(`/companies/${id}`)
}

export interface SalesOwnerOption {
  id: number
  displayName: string
  username: string
}

export function getSalesOwnerOptions() {
  return request.get<R<SalesOwnerOption[]>>('/companies/sales-owner-options')
}

export function getCompanyAccount(id: number) {
  return request.get<R<CompanyAccount>>(`/companies/${id}/account`)
}

export function getCompanyAccountTxns(
  id: number,
  params: {
    current?: number
    size?: number
    txnType?: string
    bizType?: string
    dateFrom?: string
    dateTo?: string
  },
) {
  return request.get<R<PageResult<CompanyAccountTxn>>>(`/companies/${id}/account/txns`, { params })
}

export function rechargeCompanyAccount(
  id: number,
  data: { amount: number; reason: string; offlineReference?: string; remark?: string },
) {
  return request.post<R<CompanyAccountTxn>>(`/companies/${id}/account/recharge`, data)
}

export function deductCompanyAccount(
  id: number,
  data: { amount: number; reason: string; remark?: string },
) {
  return request.post<R<CompanyAccountTxn>>(`/companies/${id}/account/deduct`, data)
}

export function getCompanyPackageBindings(id: number) {
  return request.get<R<CompanyPackageBinding[]>>(`/companies/${id}/package-bindings`)
}

export function getActiveCompanyPackageBinding(id: number) {
  return request.get<R<CompanyPackageBinding | null>>(`/companies/${id}/package-binding/active`)
}

export function getCompanyKeywordGroupQuota(id: number) {
  return request.get<R<CompanyKeywordGroupQuota>>(`/companies/${id}/keyword-group-quota`)
}

export function getCompanyDistributionQuotas(id: number) {
  return request.get<R<CompanyDistributionQuota>>(`/companies/${id}/distribution-quotas`)
}

export function bindCompanyPackage(id: number, packagePlanId: number) {
  return request.post<R<CompanyPackageBinding>>(`/companies/${id}/package-binding`, { packagePlanId })
}

export function unbindCompanyPackage(id: number) {
  return request.delete<R<void>>(`/companies/${id}/package-binding`)
}

export function getBrandList(params: {
  current?: number
  size?: number
  companyId?: number
  keyword?: string
}) {
  return request.get<R<PageResult<Brand>>>('/brands', { params })
}

export function getBrandDetail(id: number) {
  return request.get<R<Brand>>(`/brands/${id}`)
}

export interface ThirdPartySubjectPoolItem {
  brandId: number
  brandName?: string | null
  industry?: string | null
  companyId?: number | null
  companyName?: string | null
  subjectProjectId?: number | null
  lastSelectedAt?: string | null
  reasonCode?: string | null
  reason?: string | null
}

export interface ThirdPartySubjectPoolPreview {
  sourceBrandId: number
  sourceBrandName?: string | null
  coverableIndustries: string[]
  includeAllIndustries: boolean
  validSource: boolean
  candidateCount: number
  excludedCount: number
  candidateDisplayCount: number
  excludedDisplayCount: number
  candidates: ThirdPartySubjectPoolItem[]
  excluded: ThirdPartySubjectPoolItem[]
}

export function getThirdPartySubjectPool(
  brandId: number,
  params?: { candidateLimit?: number; excludedLimit?: number },
) {
  return request.get<R<ThirdPartySubjectPoolPreview>>(`/brands/${brandId}/third-party-subject-pool`, { params })
}

export function createBrand(data: Record<string, any>) {
  return request.post<R<Brand>>('/brands', data)
}

export function updateBrand(id: number, data: Record<string, any>) {
  return request.put<R<Brand>>(`/brands/${id}`, data)
}

export function deleteBrand(id: number) {
  return request.delete<R<void>>(`/brands/${id}`)
}

export function getBrandOfferings(brandId: number, status?: string) {
  return request.get<R<BrandOffering[]>>(`/brands/${brandId}/offerings`, { params: { status } })
}

export function createBrandOffering(brandId: number, data: Record<string, any>) {
  return request.post<R<BrandOffering>>(`/brands/${brandId}/offerings`, data)
}

export function updateBrandOffering(brandId: number, offeringId: number, data: Record<string, any>) {
  return request.put<R<BrandOffering>>(`/brands/${brandId}/offerings/${offeringId}`, data)
}

export function deleteBrandOffering(brandId: number, offeringId: number) {
  return request.delete<R<void>>(`/brands/${brandId}/offerings/${offeringId}`)
}

export function uploadBrandMaterial(brandId: number, category: string, file: File, folderId?: number) {
  const formData = new FormData()
  formData.append('category', category)
  if (folderId) {
    formData.append('folderId', String(folderId))
  }
  formData.append('file', file)
  return request.post<R<BrandMaterial>>(`/brands/${brandId}/materials/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function getBrandMaterials(brandId: number, category?: string, folderId?: number) {
  return request.get<R<BrandMaterial[]>>(`/brands/${brandId}/materials`, { params: { category, folderId } })
}

export function getBrandImageFolders(brandId: number, params?: {
  projectId?: number
  tag?: string
  activeOnly?: boolean
  includeMaterials?: boolean
}) {
  return request.get<R<BrandImageFolder[]>>(`/brands/${brandId}/image-folders`, { params })
}

export function createBrandImageFolder(brandId: number, data: {
  folderName: string
  description?: string
  status?: string
  projectIds?: number[]
  tags?: string[]
}) {
  return request.post<R<BrandImageFolder>>(`/brands/${brandId}/image-folders`, data)
}

export function updateBrandImageFolder(brandId: number, folderId: number, data: {
  folderName: string
  description?: string
  status?: string
  projectIds?: number[]
  tags?: string[]
}) {
  return request.put<R<BrandImageFolder>>(`/brands/${brandId}/image-folders/${folderId}`, data)
}

export function suggestBrandImageFolderTags(brandId: number, keyword?: string) {
  return request.get<R<string[]>>(`/brands/${brandId}/image-folder-tags`, { params: { keyword } })
}

export function deleteBrandMaterial(brandId: number, materialId: number) {
  return request.delete<R<void>>(`/brands/${brandId}/materials/${materialId}`)
}

export function getBrandVersions(brandId: number, params: { current?: number; size?: number }) {
  return request.get<R<PageResult<BrandProfileVersion>>>(`/brands/${brandId}/versions`, { params })
}

export function getBrandVersionDetail(brandId: number, versionNo: number) {
  return request.get<R<BrandProfileVersion>>(`/brands/${brandId}/versions/${versionNo}`)
}

export function getBrandMaterialStream(brandId: number, materialId: number, download = false) {
  return request.get<Blob>(`/brands/${brandId}/materials/${materialId}/stream`, {
    params: { download },
    responseType: 'blob',
  })
}

export function getBrandMaterialPreviewUrl(brandId: number, materialId: number) {
  return request.get<R<{ url: string }>>(`/brands/${brandId}/materials/${materialId}/preview-url`)
}

export function getBrandStatementDetail(brandId: number) {
  return request.get<R<BrandStatementView>>(`/brands/${brandId}/statement`)
}

export function saveBrandStatementDraft(
  brandId: number,
  data: {
    positioning: string
    sellingPoints: string[]
    differentiation: string
    brandParagraph: string
  },
) {
  return request.put<R<BrandStatementView>>(`/brands/${brandId}/statement`, data)
}

export function lockBrandStatement(brandId: number) {
  return request.post<R<BrandStatementView>>(`/brands/${brandId}/statement/lock`)
}

export function unlockBrandStatement(brandId: number) {
  return request.post<R<BrandStatementView>>(`/brands/${brandId}/statement/unlock`)
}

export function regenerateBrandStatement(brandId: number, data?: { projectId?: number; remark?: string }) {
  return request.post<R<DispatchTaskItem>>(`/brands/${brandId}/statement/regenerate`, data || {})
}
