export interface IndustryOption {
  value: string
  label: string
}

const LEGACY_INDUSTRY_LABELS: Record<string, string> = {
  automotive: '汽车',
  new_energy_vehicle: '新能源汽车',
  auto_aftermarket: '汽车后市场',
  real_estate: '房产中介',
  home_decoration: '家装建材',
  home_appliance: '智能家居',
  furniture_home: '家具家居',
  education: '教培',
  healthcare: '医疗服务',
  medical_beauty: '医美',
  pharma_health: '医药健康',
  restaurant: '餐饮',
  food_beverage: '食品饮料',
  alcohol_tea: '酒类茶叶',
  retail: '零售',
  ecommerce: '电商',
  beauty_care: '美妆个护',
  fashion_jewelry: '服饰珠宝',
  finance: '金融',
  tech_software: 'SaaS 企业软件',
  marketing_services: '营销公关',
  logistics: '物流供应链',
  tourism: '旅游酒店',
  hr_recruitment: '人力资源',
  medical_beauty_hospital: '医美',
  医美: '医美',
  医疗美容: '医美',
  dental: '口腔',
  口腔: '口腔',
  hair_transplant: '植发',
  植发: '植发',
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
  return LEGACY_INDUSTRY_LABELS[key] || selectedOption?.label.trim() || input
}

export function countMarketLabelPair(region: string | undefined, industryLabel: string): number {
  return (region || '').trim().length + industryLabel.trim().length
}
