export interface IndustryOption {
  value: string
  label: string
}

export const REPORT_MARKET_INDUSTRY_LABEL_MAX_LENGTH = 9
export const REPORT_MARKET_LABEL_PAIR_MAX_LENGTH = 11

const LEGACY_INDUSTRY_LABELS: Record<string, string> = {
  medical_beauty_hospital: '医美',
  医美: '医美',
  医疗美容: '医美',
  dental: '口腔',
  口腔: '口腔',
  hair_transplant: '植发',
  植发: '植发',
  home_decoration: '家装',
  decoration: '家装',
  家装: '家装',
  装修: '家装',
  教育: '教培',
  local_food: '餐饮',
  餐饮: '餐饮',
  本地餐饮: '餐饮',
  auto_service: '汽车服务',
  汽车服务: '汽车服务'
}

export function resolveMarketIndustryLabel(value: string | undefined, options: IndustryOption[]): string {
  const input = (value || '').trim()
  const key = input.toLowerCase()
  const selectedOption = options.find((item) => item.value.toLowerCase() === key)
  return selectedOption?.label.trim() || LEGACY_INDUSTRY_LABELS[key] || input
}

export function countMarketLabelPair(region: string | undefined, industryLabel: string): number {
  return (region || '').trim().length + industryLabel.trim().length
}
