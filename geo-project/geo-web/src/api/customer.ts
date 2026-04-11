import request from './request'
import type { R, PageResult, Company, Brand } from '@/types'

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

