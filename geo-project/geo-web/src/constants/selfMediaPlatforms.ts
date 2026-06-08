export const SELF_MEDIA_PLATFORM_OPTIONS = [
  { platform: 'wechat', accountPlatform: 'wechat_mp', label: '公众号' },
  { platform: 'douyin', accountPlatform: 'douyin', label: '抖音图文' },
  { platform: 'baijiahao', accountPlatform: 'baijiahao', label: '百家号' },
  { platform: 'zhihu', accountPlatform: 'zhihu', label: '知乎' },
  { platform: 'xiaohongshu', accountPlatform: 'xiaohongshu', label: '小红书' },
  { platform: 'toutiao', accountPlatform: 'toutiao', label: '今日头条' },
  { platform: 'netease', accountPlatform: 'netease', label: '网易' },
  { platform: 'sohu', accountPlatform: 'sohu', label: '搜狐' },
] as const

export type SelfMediaQuotaPlatform = typeof SELF_MEDIA_PLATFORM_OPTIONS[number]['platform']
export type SelfMediaAccountPlatform = typeof SELF_MEDIA_PLATFORM_OPTIONS[number]['accountPlatform']

const LEGACY_PLATFORM_MAP: Record<string, SelfMediaQuotaPlatform> = {
  wechat_mp: 'wechat',
  douyin_image_text: 'douyin',
}

export function canonicalSelfMediaPlatform(platform?: string | null) {
  const value = (platform || '').trim().toLowerCase()
  if (!value) return ''
  return LEGACY_PLATFORM_MAP[value] || value
}

export function selfMediaQuotaChannel(platform?: string | null) {
  const canonical = canonicalSelfMediaPlatform(platform)
  return canonical ? `self_media:${canonical}` : ''
}

export function isSelfMediaQuotaChannel(channelCode?: string | null) {
  return !!channelCode?.startsWith('self_media:')
}

export function selfMediaPlatformFromChannel(channelCode?: string | null) {
  if (!isSelfMediaQuotaChannel(channelCode)) return ''
  return canonicalSelfMediaPlatform(channelCode!.slice('self_media:'.length))
}

export function selfMediaPlatformLabel(platform?: string | null) {
  const canonical = canonicalSelfMediaPlatform(platform)
  return SELF_MEDIA_PLATFORM_OPTIONS.find((item) => item.platform === canonical)?.label || platform || '-'
}
