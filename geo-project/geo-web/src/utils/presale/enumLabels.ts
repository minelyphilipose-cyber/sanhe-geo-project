import type { IntentCode, PlatformIntentCell } from '@/types/presale/computed'
import type { Sentiment } from '@/types/presale/raw'

export const INTENT_CODE_LABEL_MAP: Record<IntentCode, string> = {
  RECOMMENDATION: '推荐型',
  COMPARISON: '对比型',
  INQUIRY: '问题型',
  COGNITIVE: '认知型',
  SCENARIO: '场景型'
}

export const SENTIMENT_LABEL_MAP: Record<Sentiment, string> = {
  POSITIVE: '正面',
  NEUTRAL: '中性',
  NEGATIVE: '负面'
}

export const STANCE_LABEL_MAP: Record<
  Exclude<PlatformIntentCell['stance'], null | undefined>,
  '我方领先' | '竞品领先' | '持平'
> = {
  target: '我方领先',
  competitor: '竞品领先',
  tie: '持平'
}

export function toStanceLabel(
  stance: PlatformIntentCell['stance']
): '我方领先' | '竞品领先' | '持平' | null {
  if (!stance) return null
  return STANCE_LABEL_MAP[stance] ?? null
}

export function toIntentLabel(intentCode: IntentCode): string {
  return INTENT_CODE_LABEL_MAP[intentCode] ?? intentCode
}

