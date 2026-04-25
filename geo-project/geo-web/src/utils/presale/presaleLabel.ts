import { useDictStore } from '@/stores/dict'

export const PRESALE_LABEL_FALLBACK: Record<string, Record<string, string>> = {
  presale_industry_role: {
    chain_brand: '连锁品牌',
    single_store: '单店',
    franchise: '加盟商',
    manufacturer: '生产厂家',
    dealer: '经销商',
    platform: '平台方',
    service_provider: '服务商',
    kol: '个人/KOL',
  },
  presale_industry: {
    restaurant: '餐饮',
    education: '教育培训',
    medical_beauty: '医美',
    healthcare: '医疗健康',
    retail: '零售',
    real_estate: '房产',
    automotive: '汽车',
    finance: '金融',
    tourism: '旅游',
    b2b_service: 'B2B 服务',
    beauty_care: '美妆个护',
    tech_software: '科技软件',
  },
}

const CJK_RE = /[\u3400-\u9fff]/

export function presaleLabel(dictType: string, value?: string | null): string {
  if (value == null) return ''
  const raw = String(value).trim()
  if (!raw) return ''
  if (CJK_RE.test(raw)) return raw
  if (raw === '_ALL_') return '通用'

  const dictStore = useDictStore()
  const dictLabel = dictStore.label(dictType, raw)
  if (dictLabel && dictLabel !== raw && dictLabel !== '-') {
    return dictLabel
  }
  return PRESALE_LABEL_FALLBACK[dictType]?.[raw] ?? raw
}
