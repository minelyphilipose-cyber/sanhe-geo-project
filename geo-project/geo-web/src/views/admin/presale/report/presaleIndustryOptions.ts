export interface PresaleIndustryOption {
  value: string
  label: string
  /** 仅用于报告紧凑版面；不参与行业归档、基准匹配或问题生成。 */
  shortLabel: string
}

/**
 * 售前报告、行业基准和手输行业 LLM 分类共享同一套标准行业 key。
 * _ALL_ 仅用于基准配置，不应出现在创建报告的行业下拉中。
 */
export const PRESALE_INDUSTRY_OPTIONS: PresaleIndustryOption[] = [
  { value: 'automotive', label: '汽车整车及经销服务', shortLabel: '汽车经销' },
  { value: 'new_energy_vehicle', label: '新能源汽车', shortLabel: '新能源汽车' },
  { value: 'auto_aftermarket', label: '汽车后市场', shortLabel: '汽车后市场' },
  { value: 'real_estate', label: '房地产与房产中介', shortLabel: '房产中介' },
  { value: 'home_decoration', label: '家装建材与全屋定制', shortLabel: '家装建材' },
  { value: 'home_appliance', label: '家电与智能家居', shortLabel: '智能家居' },
  { value: 'furniture_home', label: '家具与家居零售', shortLabel: '家具家居' },
  { value: 'education', label: '教育培训与留学服务', shortLabel: '教育培训' },
  { value: 'healthcare', label: '医疗服务与专科诊所', shortLabel: '医疗服务' },
  { value: 'medical_beauty', label: '医美与健康管理', shortLabel: '医美健康' },
  { value: 'pharma_health', label: '药品保健品与医疗器械', shortLabel: '医药健康' },
  { value: 'restaurant', label: '餐饮与连锁餐饮', shortLabel: '餐饮' },
  { value: 'food_beverage', label: '食品饮料', shortLabel: '食品饮料' },
  { value: 'alcohol_tea', label: '酒类与茶叶', shortLabel: '酒类茶叶' },
  { value: 'retail', label: '商超零售与便利店', shortLabel: '商超零售' },
  { value: 'ecommerce', label: '电商与跨境电商', shortLabel: '电商零售' },
  { value: 'beauty_care', label: '美妆个护与母婴', shortLabel: '美妆个护' },
  { value: 'fashion_jewelry', label: '服饰鞋包与珠宝', shortLabel: '服饰珠宝' },
  { value: 'finance', label: '金融保险与财富管理', shortLabel: '金融保险' },
  { value: 'tech_software', label: '企业服务软件与 SaaS', shortLabel: '企业软件' },
  { value: 'marketing_services', label: '广告营销品牌策划与公关', shortLabel: '营销公关' },
  { value: 'logistics', label: '物流货运与供应链', shortLabel: '物流供应链' },
  { value: 'tourism', label: '旅游酒店与本地生活', shortLabel: '旅游酒店' },
  { value: 'hr_recruitment', label: '招聘人力资源与职业服务', shortLabel: '人力资源' }
]

export const PRESALE_BENCHMARK_INDUSTRY_OPTIONS: PresaleIndustryOption[] = [
  { value: '_ALL_', label: '全部行业（全局回退）', shortLabel: '全部行业' },
  ...PRESALE_INDUSTRY_OPTIONS
]
