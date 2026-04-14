import request from './request'
import type {
  R,
  PageResult,
  Company,
  Brand,
  BrandMaterial,
  BrandProfileVersion,
  CompanyAccount,
  CompanyAccountTxn,
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

export function createBrand(data: Record<string, any>) {
  return request.post<R<Brand>>('/brands', data)
}

export function updateBrand(id: number, data: Record<string, any>) {
  return request.put<R<Brand>>(`/brands/${id}`, data)
}

export function deleteBrand(id: number) {
  return request.delete<R<void>>(`/brands/${id}`)
}

export function uploadBrandMaterial(brandId: number, category: string, file: File) {
  const formData = new FormData()
  formData.append('category', category)
  formData.append('file', file)
  return request.post<R<BrandMaterial>>(`/brands/${brandId}/materials/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function getBrandMaterials(brandId: number, category?: string) {
  return request.get<R<BrandMaterial[]>>(`/brands/${brandId}/materials`, { params: { category } })
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
