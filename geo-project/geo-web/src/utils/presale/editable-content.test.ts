import { describe, expect, it } from 'vitest'
import type { EditableContentDTO, MarketBattleground } from '@/types/presale/editable'
import { validateEditableContent } from './editable-content'

describe('editable content validation', () => {
  it('accepts a valid fixed-layout market page', () => {
    expect(validateEditableContent(editable())).toEqual([])
  })

  it('rejects changed fixed text and invalid page-title semantics', () => {
    const value = editable()
    value.market_battleground.topbar_title = '自定义标题'
    value.market_battleground.page_title = '普通标题'

    const messages = validateEditableContent(value).map((error) => error.message)
    expect(messages).toContain('顶部章节标题为固定文案，不能修改')
    expect(messages).toContain('AI搜索新战场 页面主标题至少 12 字')
    expect(messages).toContain('AI搜索新战场 页面主标题必须包含 AI')
  })

  it('rejects blank market fields and narrative questions containing the brand', () => {
    const value = editable()
    value.market_battleground.market_card.label = '   '
    value.market_battleground.narrative.questions[0] = '测试品牌怎么样？'

    const messages = validateEditableContent(value).map((error) => error.message)
    expect(messages).toContain('市场卡标签不能为空')
    expect(messages).toContain('示例问题 1不能包含品牌名')
  })
})

function editable(): EditableContentDTO {
  return {
    report_title: null,
    report_subtitle: null,
    executive_summary: null,
    market_battleground: market(),
    key_takeaways: [],
    optimization_findings_content: [],
    phase_descriptions: [],
    competitor_scene_descriptions: [],
    roi_disclaimer: null
  }
}

function market(): MarketBattleground {
  return {
    topbar_title: 'MARKET BATTLEGROUND · AI 搜索新战场',
    topbar_right: 'GEO · CONFIDENTIAL',
    page_title: '每天，有数千万次消费决策正在 AI 上发生',
    page_kicker: 'THE NEW BATTLEGROUND FOR YOUR BRAND',
    market_card: {
      label: 'AI 搜索流量总览',
      source: '公开数据',
      stats: Array.from({ length: 4 }, () => ({ value: '12', unit: '亿次', label: '日均提问' })),
      platform_label: 'TOP 平台',
      platforms: Array.from({ length: 3 }, () => ({ name: '豆包', value: '5.8亿' })),
      platform_suffix: '元宝 / Kimi 等'
    },
    national_card: calculationCard('NATIONAL · 全国汽车每天'),
    bridge_text: '↓ 聚焦到您的核心市场',
    regional_card: calculationCard('REGIONAL · 亳州汽车每天'),
    narrative: {
      intro: '这意味着，消费者正在通过 AI 持续询问：',
      questions: ['“附近哪家更靠谱？”', '“哪家性价比高？”', '“本地推荐哪家？”'],
      conclusion: '而 AI 给出的答案，正在影响他们下一步选择。',
      brand_line_prefix: '→',
      brand_name: '测试品牌',
      brand_line_suffix: '在这些场景中的真实可见度如何？详见下章诊断结果。'
    },
    footnote: '数据仅供参考',
    footer_brand: 'GEO · CONFIDENTIAL'
  }
}

function calculationCard(label: string): MarketBattleground['national_card'] {
  return {
    label,
    value_prefix: '约',
    value: '12',
    unit: '万次',
    subtitle: '条 / 天 · 消费者向 AI 提问',
    calculation_label: 'CALCULATION · 推导口径',
    rows: Array.from({ length: 4 }, (_, index) => ({
      label: `推导项${index + 1}`,
      value: '约 12 万 / 天',
      is_total: index === 3
    }))
  }
}
