export interface DictOptionLike {
  dictKey?: string | null
  value?: string | null
}

export function specialIndustryCodesFromOptions(options: DictOptionLike[]) {
  return options
    .map((item) => item.dictKey || item.value || '')
    .filter((code) => isSpecialIndustryCode(code))
}

export function isSpecialIndustryCode(code?: string | null) {
  return !!code && code !== 'none'
}
