import type { ComputedSnapshotDTO } from '@/types/presale/computed'
import type {
  CompetitorSceneDescription,
  EditableContentDTO,
  FindingContent,
  KeyTakeaway,
  MarketBattleground,
  PhaseDescription
} from '@/types/presale/editable'
import type { RawSnapshotDTO } from '@/types/presale/raw'

const TOP_LEVEL_ORDER: Array<keyof EditableContentDTO> = [
  'report_title',
  'report_subtitle',
  'market_battleground',
  'executive_summary',
  'key_takeaways',
  'optimization_findings_content',
  'phase_descriptions',
  'competitor_scene_descriptions',
  'roi_disclaimer'
]

const MARKET_FIXED_TEXT = {
  topbarTitle: 'MARKET BATTLEGROUND · AI 搜索新战场',
  topbarRight: 'GEO · CONFIDENTIAL',
  pageKicker: 'THE NEW BATTLEGROUND FOR YOUR BRAND',
  bridgeText: '↓ 聚焦到您的核心市场',
  platformLabel: 'TOP 平台',
  brandLinePrefix: '→'
} as const

export interface EditableValidationError {
  field: string
  message: string
}

export function parseEditableContent(
  json: string | null | undefined,
  raw: RawSnapshotDTO,
  computed: ComputedSnapshotDTO
): EditableContentDTO {
  const parsed = json && json.trim() ? JSON.parse(json) : {}
  return normalizeEditableContent(parsed, raw, computed)
}

export function normalizeEditableContent(
  value: Partial<EditableContentDTO>,
  raw: RawSnapshotDTO,
  computed: ComputedSnapshotDTO
): EditableContentDTO {
  void raw
  void computed

  const phasesByNo = new Map<number, PhaseDescription>()
  for (const item of Array.isArray(value.phase_descriptions) ? value.phase_descriptions : []) {
    if (item?.phase_no) phasesByNo.set(item.phase_no, item)
  }

  return {
    report_title: value.report_title ?? null,
    report_subtitle: value.report_subtitle ?? null,
    market_battleground: normalizeMarketBattleground(value.market_battleground),
    executive_summary: value.executive_summary ?? null,
    key_takeaways: Array.isArray(value.key_takeaways)
      ? value.key_takeaways.map((item, idx) => ({
          order_no: item.order_no ?? idx + 1,
          title: item.title ?? '',
          description: item.description ?? ''
        }))
      : [],
    optimization_findings_content: Array.isArray(value.optimization_findings_content)
      ? value.optimization_findings_content
          .filter((item) => item?.finding_id)
          .map((item) => ({
            finding_id: item.finding_id,
            title: item.title ?? null,
            description: item.description ?? null,
            evidence_text: item.evidence_text ?? null,
            sort_order: item.sort_order ?? null,
            is_hidden: item.is_hidden
          }))
      : [],
    phase_descriptions: ([1, 2, 3] as const).map((phaseNo) => {
      const existing = phasesByNo.get(phaseNo)
      return {
        phase_no: phaseNo,
        title: existing?.title ?? null,
        description: existing?.description ?? null
      }
    }),
    competitor_scene_descriptions: Array.isArray(value.competitor_scene_descriptions)
      ? value.competitor_scene_descriptions
          .filter((item) => item?.competitor_rank)
          .map((item) => ({
            competitor_rank: item.competitor_rank,
            scene_advantages_polished:
              'scene_advantages_polished' in item ? item.scene_advantages_polished ?? null : null
          }))
      : [],
    roi_disclaimer: value.roi_disclaimer ?? null
  }
}

export function serializeEditableContent(value: EditableContentDTO): string {
  return stableStringify(toOrderedEditableContent(value))
}

export function toOrderedEditableContent(value: EditableContentDTO): EditableContentDTO {
  const ordered = {} as EditableContentDTO
  for (const key of TOP_LEVEL_ORDER) {
    ;(ordered as unknown as Record<string, unknown>)[key] = value[key]
  }
  ordered.key_takeaways = value.key_takeaways.map((item, idx) => ({
    order_no: idx + 1,
    title: item.title,
    description: item.description
  }))
  ordered.market_battleground = normalizeMarketBattleground(value.market_battleground)
  return ordered
}

export function stableStringify(value: unknown): string {
  if (Array.isArray(value)) {
    return `[${value.map(stableStringify).join(',')}]`
  }
  if (value && typeof value === 'object') {
    const obj = value as Record<string, unknown>
    return `{${Object.keys(obj)
      .filter((key) => obj[key] !== undefined)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableStringify(obj[key])}`)
      .join(',')}}`
  }
  return JSON.stringify(value)
}

export function validateEditableContent(value: EditableContentDTO): EditableValidationError[] {
  const errors: EditableValidationError[] = []
  checkText(errors, '报告标题', value.report_title, 40)
  checkText(errors, '报告副标题', value.report_subtitle, 80)
  if (value.executive_summary != null) {
    checkRequiredText(errors, '摘要标题', value.executive_summary.headline, 60)
    checkRequiredText(errors, '摘要正文', value.executive_summary.paragraph, 500)
  }
  validateMarketBattleground(errors, value.market_battleground)
  if (value.key_takeaways.length > 8) {
    errors.push({ field: 'key_takeaways', message: '关键结论最多 8 条' })
  }
  value.key_takeaways.forEach((item, idx) => {
    checkRequiredText(errors, `关键结论 ${idx + 1} 标题`, item.title, 30)
    checkRequiredText(errors, `关键结论 ${idx + 1} 描述`, item.description, 500)
  })
  value.optimization_findings_content.forEach((item, idx) => {
    checkRequiredText(errors, `优化建议 ${idx + 1} ID`, item.finding_id, 64)
    checkText(errors, `优化建议 ${idx + 1} 标题`, item.title, 50)
    checkText(errors, `优化建议 ${idx + 1} 描述`, item.description, 500)
    checkText(errors, `优化建议 ${idx + 1} 证据`, item.evidence_text, 300)
  })
  value.phase_descriptions.forEach((item) => {
    checkText(errors, `阶段 ${item.phase_no} 标题`, item.title, 30)
    checkText(errors, `阶段 ${item.phase_no} 描述`, item.description, 300)
  })
  value.competitor_scene_descriptions.forEach((item) => {
    const list = item.scene_advantages_polished
    if (list == null) return
    if (list.length > 6) {
      errors.push({
        field: `competitor_${item.competitor_rank}`,
        message: `竞品 ${item.competitor_rank} 场景最多 6 条`
      })
    }
    list.forEach((text, idx) =>
      checkRequiredText(errors, `竞品 ${item.competitor_rank} 场景 ${idx + 1}`, text, 100)
    )
  })
  checkText(errors, 'ROI 免责声明', value.roi_disclaimer, 200)
  return errors
}

export function collectClearedFields(value: EditableContentDTO): string[] {
  const fields: string[] = []
  if (value.report_title === '') fields.push('报告标题')
  if (value.report_subtitle === '') fields.push('报告副标题')
  if (value.executive_summary?.headline === '') fields.push('摘要标题')
  if (value.executive_summary?.paragraph === '') fields.push('摘要正文')
  collectMarketClearedFields(value.market_battleground, fields)
  if (value.key_takeaways.length === 0) fields.push('关键结论')
  for (const item of value.optimization_findings_content) {
    if (item.title === '' || item.description === '' || item.evidence_text === '') {
      fields.push(`优化建议 ${item.finding_id}`)
    }
  }
  for (const item of value.phase_descriptions) {
    if (item.title === '' || item.description === '') fields.push(`阶段 ${item.phase_no}`)
  }
  for (const item of value.competitor_scene_descriptions) {
    if (Array.isArray(item.scene_advantages_polished) && item.scene_advantages_polished.length === 0) {
      fields.push(`竞品 ${item.competitor_rank} 场景`)
    }
  }
  if (value.roi_disclaimer === '') fields.push('ROI 免责声明')
  return Array.from(new Set(fields))
}

function normalizeMarketBattleground(value: Partial<MarketBattleground> | null | undefined): MarketBattleground {
  const marketCard = value?.market_card
  const narrative = value?.narrative
  return {
    topbar_title: value?.topbar_title ?? '',
    topbar_right: value?.topbar_right ?? '',
    page_title: value?.page_title ?? '',
    page_kicker: value?.page_kicker ?? '',
    market_card: {
      label: marketCard?.label ?? '',
      source: marketCard?.source ?? '',
      stats: [0, 1, 2, 3].map((idx) => {
        const item = Array.isArray(marketCard?.stats) ? marketCard?.stats[idx] : undefined
        return {
          value: item?.value ?? '',
          unit: item?.unit ?? '',
          label: item?.label ?? ''
        }
      }),
      platform_label: marketCard?.platform_label ?? '',
      platforms: [0, 1, 2].map((idx) => {
        const item = Array.isArray(marketCard?.platforms) ? marketCard?.platforms[idx] : undefined
        return {
          name: item?.name ?? '',
          value: item?.value ?? ''
        }
      }),
      platform_suffix: marketCard?.platform_suffix ?? ''
    },
    national_card: normalizeCalculationCard(value?.national_card),
    bridge_text: value?.bridge_text ?? '',
    regional_card: normalizeCalculationCard(value?.regional_card),
    narrative: {
      intro: narrative?.intro ?? '',
      questions: [0, 1, 2].map((idx) => (Array.isArray(narrative?.questions) ? narrative?.questions[idx] ?? '' : '')),
      conclusion: narrative?.conclusion ?? '',
      brand_line_prefix: narrative?.brand_line_prefix ?? '',
      brand_name: narrative?.brand_name ?? '',
      brand_line_suffix: narrative?.brand_line_suffix ?? ''
    },
    footnote: value?.footnote ?? '',
    footer_brand: value?.footer_brand ?? ''
  }
}

function normalizeCalculationCard(value: Partial<MarketBattleground['national_card']> | null | undefined) {
  return {
    label: value?.label ?? '',
    value_prefix: value?.value_prefix ?? '',
    value: value?.value ?? '',
    unit: value?.unit ?? '',
    subtitle: value?.subtitle ?? '',
    calculation_label: value?.calculation_label ?? '',
    rows: [0, 1, 2, 3].map((idx) => {
      const row = Array.isArray(value?.rows) ? value?.rows[idx] : undefined
      return {
        label: row?.label ?? '',
        value: row?.value ?? '',
        is_total: row?.is_total ?? idx === 3
      }
    })
  }
}

function validateMarketBattleground(errors: EditableValidationError[], value: MarketBattleground) {
  checkMarketLiteral(errors, '顶部章节标题', value.topbar_title, MARKET_FIXED_TEXT.topbarTitle)
  checkMarketLiteral(errors, '顶部右侧标识', value.topbar_right, MARKET_FIXED_TEXT.topbarRight)
  validateMarketPageTitle(errors, value.page_title)
  checkMarketLiteral(errors, 'AI搜索新战场 英文副标题', value.page_kicker, MARKET_FIXED_TEXT.pageKicker)
  checkRequiredMarketText(errors, '市场卡标签', value.market_card.label, 32)
  checkRequiredMarketText(errors, '市场卡来源', value.market_card.source, 32)
  value.market_card.stats.forEach((item, idx) => {
    checkRequiredMarketText(errors, `市场数据 ${idx + 1} 数值`, item.value, 12)
    checkRequiredMarketText(errors, `市场数据 ${idx + 1} 单位`, item.unit, 8)
    checkRequiredMarketText(errors, `市场数据 ${idx + 1} 说明`, item.label, 24)
  })
  checkMarketLiteral(errors, '平台列表标签', value.market_card.platform_label, MARKET_FIXED_TEXT.platformLabel)
  value.market_card.platforms.forEach((item, idx) => {
    checkRequiredMarketText(errors, `平台 ${idx + 1} 名称`, item.name, 12)
    checkRequiredMarketText(errors, `平台 ${idx + 1} 数值`, item.value, 12)
  })
  checkRequiredMarketText(errors, '其他平台说明', value.market_card.platform_suffix, 18)
  validateCalculationCard(errors, '全国推导卡', value.national_card)
  checkMarketLiteral(errors, '过渡文案', value.bridge_text, MARKET_FIXED_TEXT.bridgeText)
  validateCalculationCard(errors, '区域推导卡', value.regional_card)
  checkRequiredMarketText(errors, '问题场景引导', value.narrative.intro, 56)
  value.narrative.questions.forEach((item, idx) => {
    checkRequiredMarketText(errors, `示例问题 ${idx + 1}`, item, 34)
    if (value.narrative.brand_name && item.includes(value.narrative.brand_name)) {
      errors.push({ field: `示例问题 ${idx + 1}`, message: `示例问题 ${idx + 1}不能包含品牌名` })
    }
  })
  checkRequiredMarketText(errors, '结论句', value.narrative.conclusion, 44)
  checkMarketLiteral(errors, '品牌句前缀', value.narrative.brand_line_prefix, MARKET_FIXED_TEXT.brandLinePrefix)
  checkRequiredMarketText(errors, '品牌句品牌名', value.narrative.brand_name, 18)
  checkRequiredMarketText(errors, '品牌句后缀', value.narrative.brand_line_suffix, 48)
  checkRequiredMarketText(errors, '数据脚注', value.footnote, 150)
  checkRequiredMarketText(errors, '页脚品牌', value.footer_brand, 24)
}

function validateCalculationCard(errors: EditableValidationError[], label: string, value: MarketBattleground['national_card']) {
  checkRequiredMarketText(errors, `${label} 标签`, value.label, 36)
  checkRequiredMarketText(errors, `${label} 大数字前缀`, value.value_prefix, 6)
  checkRequiredMarketText(errors, `${label} 大数字`, value.value, 12)
  checkRequiredMarketText(errors, `${label} 大数字单位`, value.unit, 8)
  checkRequiredMarketText(errors, `${label} 大数字说明`, value.subtitle, 28)
  checkRequiredMarketText(errors, `${label} 推导标题`, value.calculation_label, 24)
  value.rows.forEach((row, idx) => {
    checkRequiredMarketText(errors, `${label} 推导行 ${idx + 1} 标签`, row.label, 18)
    checkRequiredMarketText(errors, `${label} 推导行 ${idx + 1} 数值`, row.value, 30)
  })
}

function validateMarketPageTitle(errors: EditableValidationError[], value: string) {
  checkRequiredMarketText(errors, 'AI搜索新战场 页面主标题', value, 22)
  if (value.length < 12) {
    errors.push({ field: 'AI搜索新战场 页面主标题', message: 'AI搜索新战场 页面主标题至少 12 字' })
  }
  if (!value.includes('AI')) {
    errors.push({ field: 'AI搜索新战场 页面主标题', message: 'AI搜索新战场 页面主标题必须包含 AI' })
  }
  if (!['每天', '数', '万', '亿'].some((word) => value.includes(word))) {
    errors.push({ field: 'AI搜索新战场 页面主标题', message: 'AI搜索新战场 页面主标题必须包含数量表达' })
  }
  if (value.includes('!') || value.includes('！')) {
    errors.push({ field: 'AI搜索新战场 页面主标题', message: 'AI搜索新战场 页面主标题不能包含感叹号' })
  }
}

function checkMarketLiteral(
  errors: EditableValidationError[],
  field: string,
  value: string,
  expected: string
) {
  if (value !== expected) {
    errors.push({ field, message: `${field}为固定文案，不能修改` })
  }
}

function checkRequiredMarketText(
  errors: EditableValidationError[],
  field: string,
  value: string,
  maxLength: number
) {
  if (!value.trim()) {
    errors.push({ field, message: `${field}不能为空` })
    return
  }
  checkText(errors, field, value, maxLength)
}

function collectMarketClearedFields(value: MarketBattleground, fields: string[]) {
  if (value.topbar_title === '' || value.topbar_right === '') fields.push('AI搜索新战场 顶部条')
  if (value.page_title === '') fields.push('AI搜索新战场 页面主标题')
  if (value.page_kicker === '') fields.push('AI搜索新战场 英文副标题')
  if (value.market_card.label === '' || value.market_card.source === '') fields.push('深色市场卡')
  value.market_card.stats.forEach((item, idx) => {
    if (item.label === '' || item.value === '' || item.unit === '') fields.push(`市场数据 ${idx + 1}`)
  })
  if (value.market_card.platform_label === '' || value.market_card.platform_suffix === '') fields.push('平台列表')
  value.market_card.platforms.forEach((item, idx) => {
    if (item.name === '' || item.value === '') fields.push(`平台 ${idx + 1}`)
  })
  collectCalculationClearedFields(value.national_card, '全国推导卡', fields)
  if (value.bridge_text === '') fields.push('过渡文案')
  collectCalculationClearedFields(value.regional_card, '区域推导卡', fields)
  if (
    value.narrative.intro === '' ||
    value.narrative.questions.some((item) => item === '') ||
    value.narrative.conclusion === '' ||
    value.narrative.brand_line_prefix === '' ||
    value.narrative.brand_name === '' ||
    value.narrative.brand_line_suffix === ''
  ) {
    fields.push('底部叙事')
  }
  if (value.footnote === '' || value.footer_brand === '') fields.push('AI搜索新战场脚注')
}

function collectCalculationClearedFields(value: MarketBattleground['national_card'], label: string, fields: string[]) {
  if (
    value.label === '' ||
    value.value === '' ||
    value.unit === '' ||
    value.subtitle === '' ||
    value.calculation_label === '' ||
    value.rows.some((row) => row.label === '' || row.value === '')
  ) {
    fields.push(label)
  }
}

function checkRequiredText(
  errors: EditableValidationError[],
  field: string,
  value: string | null | undefined,
  maxLength: number
) {
  if (value == null) {
    errors.push({ field, message: `${field}不能为空` })
    return
  }
  checkText(errors, field, value, maxLength)
}

function checkText(
  errors: EditableValidationError[],
  field: string,
  value: string | null | undefined,
  maxLength: number
) {
  if (value != null && value.length > maxLength) {
    errors.push({ field, message: `${field}不能超过 ${maxLength} 字` })
  }
}
