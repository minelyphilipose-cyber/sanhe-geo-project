import request from '@/api/request'
import { unwrap } from '@/api/presale/unwrap'
import type { R } from '@/types'
import type { ReportDetailVO } from '@/api/presaleReport'

/**
 * PoC only: dev-profile unauthenticated print snapshot endpoint.
 */
export function getPresalePrintPocDetail(reportId: number) {
  return unwrap(
    request.get<R<ReportDetailVO>>(`/dev/presale-print-poc/${reportId}`)
  )
}
