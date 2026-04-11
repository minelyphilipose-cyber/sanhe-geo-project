import request from './request'
import type { R, PageResult, Company, Brand } from '@/types'

/* ====================================================
   客户 API
   ==================================================== */
export function getCompanyList(params: {
  current?: number
  size?: number
  keyword?: string
  ownerType?: string
}) {
  return request.get<R<PageResult<Company>>>('/companies', { params })
}

export function getCompanyDetail(id: number) {
  return request.get<R<Company>>(`/companies/${id}`)
}

export function createCompany(data: Partial<Company>) {
  return request.post<R<Company>>('/companies', data)
}

export function updateCompany(id: number, data: Partial<Company>) {
  return request.put<R<Company>>(`/companies/${id}`, data)
}

/* ====================================================
   品牌 API
   ==================================================== */
export function getBrandList(params: { companyId?: number; keyword?: string }) {
  return request.get<R<PageResult<Brand>>>('/brands', { params })
}

export function getBrandDetail(id: number) {
  return request.get<R<Brand>>(`/brands/${id}`)
}

export function createBrand(data: Partial<Brand>) {
  return request.post<R<Brand>>('/brands', data)
}

export function updateBrand(id: number, data: Partial<Brand>) {
  return request.put<R<Brand>>(`/brands/${id}`, data)
}
