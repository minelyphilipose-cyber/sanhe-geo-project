import request from './request'
import type { R } from '@/types'

export interface PresalePage03MarketConfig {
  id: number
  marketLabel: string
  marketSource: string
  appMonthlyActiveValue: string
  appMonthlyActiveUnit: string
  dailyActiveUsersValue: string
  dailyActiveUsersUnit: string
  dailyQuestionTotalValue: string
  dailyQuestionTotalUnit: string
  doubaoMonthlyUsageValue: string
  doubaoMonthlyUsageUnit: string
  platform1Name: string
  platform1Value: string
  platform2Name: string
  platform2Value: string
  platform3Name: string
  platform3Value: string
  platformSuffix: string
  page03DataSource: string
  footnote: string
  questionCount: number
}

export type PresalePage03MarketConfigPayload = Omit<PresalePage03MarketConfig, 'id'>

export function getPresalePage03MarketConfig() {
  return request.get<R<PresalePage03MarketConfig>>('/presale/page03-market-config')
}

export function updatePresalePage03MarketConfig(data: PresalePage03MarketConfigPayload) {
  return request.put<R<PresalePage03MarketConfig>>('/presale/page03-market-config', data)
}
