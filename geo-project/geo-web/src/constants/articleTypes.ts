export const ARTICLE_TYPE_OPTIONS = [
  { value: 'general_article', label: '通用文章' },
  { value: 'faq', label: '问答文章' },
  { value: 'scenario_content', label: '场景内容' },
  { value: 'industry_article', label: '行业文章' },
  { value: 'stage_advice', label: '阶段建议' },
  { value: 'buying_guide', label: '选择指南' },
  { value: 'comparison', label: '对比评测' },
  { value: 'cost_analysis', label: '费用解析' },
  { value: 'pitfall_guide', label: '避坑指南' },
  { value: 'social_note', label: '经验笔记' },
  { value: 'news_brief', label: '资讯简讯' },
  { value: 'forum_discussion', label: '讨论帖' },
] as const

export const GENERATED_ARTICLE_TYPE_OPTIONS = ARTICLE_TYPE_OPTIONS.filter((item) => item.value !== 'general_article')

const ARTICLE_TYPE_LABELS: Readonly<Record<string, string>> = Object.fromEntries(
  ARTICLE_TYPE_OPTIONS.map((item) => [item.value, item.label]),
)

export function articleTypeLabel(value?: string | null): string {
  return value ? ARTICLE_TYPE_LABELS[value] || value : '-'
}
