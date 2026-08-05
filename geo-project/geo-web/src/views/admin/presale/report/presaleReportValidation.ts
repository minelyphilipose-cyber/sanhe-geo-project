export const REPORT_BRAND_NAME_MAX_LENGTH = 18
export const REPORT_INDUSTRY_ROLE_MAX_LENGTH = 50
export const REPORT_COMPETITOR_GROUP_MAX_LENGTH = 100
export const REPORT_PROMPT_MAX_LENGTH = 1000

const COMPETITOR_SEPARATOR = '、'
const TEMPLATE_ALLOWED_VARIABLES = new Set([
  '{brand}',
  '{product}',
  '{industry}',
  '{industry_role}',
  '{region}',
  '{user_type}',
  '{competitor}'
])
const BRACED_TOKEN_PATTERN = /\{[^{}]*\}/g
const VALID_TOKEN_PATTERN = /^\{[a-z_]+\}$/
const AGENT_ROLE_MARKERS = [
  '代理', '经销', '分销', '渠道', '授权', '加盟',
  'dealer', 'agent', 'distributor', 'reseller', 'franchise'
]

export interface PromptQuestionLike {
  categoryCode: string
  promptContent: string
}

export function competitorGroupLength(values: string[] | undefined): number {
  return (values || [])
    .map((value) => value.trim())
    .filter(Boolean)
    .join(COMPETITOR_SEPARATOR).length
}

export function supportsRepresentedBrands(roleKey: string | undefined, roleLabel?: string): boolean {
  const roleText = `${roleKey || ''} ${roleLabel || ''}`.trim().toLowerCase()
  return AGENT_ROLE_MARKERS.some((marker) => roleText.includes(marker))
}

export function templatePromptError(content: string, requiresCompetitor: boolean): string {
  const value = content.trim()
  if (!value) return '内容不能为空'
  if (content.length > REPORT_PROMPT_MAX_LENGTH) return `内容最多 ${REPORT_PROMPT_MAX_LENGTH} 字`

  const tokens = value.match(BRACED_TOKEN_PATTERN) || []
  for (const token of tokens) {
    if (!VALID_TOKEN_PATTERN.test(token)) return `变量格式不合法：${token}`
    if (!TEMPLATE_ALLOWED_VARIABLES.has(token)) return `未知变量：${token}`
  }
  if (tokens.length === 0 && (value.includes('{') || value.includes('}'))) {
    return '花括号必须使用合法变量格式'
  }

  const hasCompetitor = value.includes('{competitor}')
  if (requiresCompetitor && !hasCompetitor) return '对比型 Prompt 必须包含 {competitor}'
  if (!requiresCompetitor && hasCompetitor) return '通用 Prompt 不能包含 {competitor}'
  return ''
}

export function findDuplicateLlmQuestion<T extends PromptQuestionLike>(questions: T[]): T | null {
  const seen = new Set<string>()
  for (const question of questions) {
    const key = `${question.categoryCode}\n${question.promptContent.trim().toLowerCase()}`
    if (seen.has(key)) return question
    seen.add(key)
  }
  return null
}
