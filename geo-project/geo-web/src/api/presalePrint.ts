import request from '@/api/request'
import { unwrap } from '@/api/presale/unwrap'
import type { R } from '@/types'
import type { ReportDetailVO } from '@/api/presaleReport'

export interface PresalePrintRenderResponse {
  exportId: number
  reportId: number
  versionId: number
  snapshot: ReportDetailVO
  renderProfile: {
    deviceScaleFactor: number
    pageFormat: string
    expectedPages: number
  }
}

/**
 * Formal print endpoint. PR-B will provide the renderToken-backed backend API.
 */
export function getPresalePrintRenderDetail(renderToken: string) {
  return unwrap(
    request.get<R<PresalePrintRenderResponse>>(
      `/presale/exports/render/${encodeURIComponent(renderToken)}`
    )
  )
}
