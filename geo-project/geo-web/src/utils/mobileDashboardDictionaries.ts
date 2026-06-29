export const aiPlatformLabels: Record<string, string> = {
  all: '全平台',
  doubao: '豆包',
  deepseek: 'DeepSeek',
  tongyi: '通义千问',
  qwen: '通义千问',
  wenxin: '文心一言',
  ernie: '文心一言',
  yuanbao: '元宝',
  hunyuan: '元宝'
}

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

export function contentPlatformLabel(code?: string | null) {
  if (!code) return '平台待定'
  return contentPlatformLabels[code] || '平台待定'
}

export function contentPlatformIcon(code?: string | null) {
  if (!code) return 'globe'
  return contentPlatformIcons[code] || 'globe'
}

export function sceneLabel(code?: string | null) {
  if (!code) return '其他场景'
  return sceneLabels[code] || '其他场景'
}
