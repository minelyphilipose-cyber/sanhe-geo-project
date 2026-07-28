import type { ContentPlatform } from '@/types/mobileDashboard'

export const aiPlatformLabels: Record<string, string> = {
  all: '全平台',
  doubao: '豆包',
  deepseek: 'DeepSeek',
  tongyi: '通义千问',
  qwen: '通义千问',
  wenxin: '文心一言',
  ernie: '文心一言',
  wenxin_web: '文心一言',
  yuanbao: '元宝',
  hunyuan: '元宝'
}

const hiddenMobileDashboardAiPlatforms = new Set(['yuanbao', 'hunyuan', 'tencent_search_web'])

export const contentPlatformLabels: Record<string, string> = {
  official_site: 'Agent官网',
  douyin: '抖音',
  xiaohongshu: '小红书',
  wechat_mp: '公众号',
  toutiao: '今日头条',
  baijiahao: '百家号',
  zhihu: '知乎'
}

export const contentPlatformIcons: Record<string, string> = {
  official_site: 'globe',
  douyin: 'movie',
  xiaohongshu: 'favorite',
  wechat_mp: 'chat',
  toutiao: 'newspaper',
  baijiahao: 'article',
  zhihu: 'question'
}

export const sceneLabels: Record<string, string> = {
  brand_awareness: '品牌认知',
  regional_recommendation: '地域推荐',
  decision_scenario: '决策场景',
  purchase_consultation: '选购咨询',
  conversion: '成交转化',
  qa: '问答场景'
}

export function aiPlatformLabel(code?: string | null) {
  if (!code) return '未知平台'
  return aiPlatformLabels[code] || '未知平台'
}

export function isMobileDashboardAiPlatformVisible(code?: string | null) {
  if (!code) return true
  return !hiddenMobileDashboardAiPlatforms.has(code.trim().toLowerCase())
}

function contentPlatformEntry(code?: string | null, platforms?: ContentPlatform[]) {
  if (!code || !platforms?.length) return undefined
  return platforms.find((item) => item.code === code)
}

export function contentPlatformLabel(code?: string | null, platforms?: ContentPlatform[]) {
  if (!code) return '平台待定'
  const entry = contentPlatformEntry(code, platforms)
  if (entry?.label) return entry.label
  return contentPlatformLabels[code] || '平台待定'
}

export function contentPlatformIcon(code?: string | null, platforms?: ContentPlatform[]) {
  if (!code) return 'globe'
  const entry = contentPlatformEntry(code, platforms)
  if (entry?.icon) return entry.icon
  return contentPlatformIcons[code] || 'globe'
}

export function sceneLabel(code?: string | null) {
  if (!code) return '其他场景'
  return sceneLabels[code] || '其他场景'
}
