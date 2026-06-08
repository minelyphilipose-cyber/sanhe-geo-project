import { SELF_MEDIA_PLATFORM_OPTIONS, isSelfMediaQuotaChannel, selfMediaPlatformLabel } from './selfMediaPlatforms'

export interface DistributionChannelOption {
  code: string
  label: string
  periodType: 'day' | 'week' | 'month' | 'total'
  quotaLimit: number
  enabled: boolean
}

export const SELF_MEDIA_PLATFORMS = SELF_MEDIA_PLATFORM_OPTIONS

export const SELF_MEDIA_CHANNELS = SELF_MEDIA_PLATFORMS.map((item) => ({
  code: `self_media:${item.platform}`,
  label: item.label,
  periodType: 'week' as const,
  quotaLimit: 1,
  enabled: true,
})) satisfies DistributionChannelOption[]

export const DISTRIBUTION_CHANNELS: DistributionChannelOption[] = [
  { code: 'official_site', label: '官网', periodType: 'week', quotaLimit: 1, enabled: true },
  { code: 'industry_site', label: '行业资讯站', periodType: 'week', quotaLimit: 1, enabled: true },
  { code: 'forum', label: '平台网站', periodType: 'week', quotaLimit: 1, enabled: true },
  ...SELF_MEDIA_CHANNELS,
  { code: 'authority_media', label: '权重媒体平台', periodType: 'total', quotaLimit: 0, enabled: true },
]

export function distributionChannelLabel(code?: string | null) {
  if (!code) return '-'
  if (isSelfMediaQuotaChannel(code)) {
    return selfMediaPlatformLabel(code.slice('self_media:'.length))
  }
  return DISTRIBUTION_CHANNELS.find((item) => item.code === code)?.label || code
}

export function distributionChannelGroupLabel(code?: string | null) {
  return isSelfMediaQuotaChannel(code) ? '自媒体平台' : distributionChannelLabel(code)
}

export { isSelfMediaQuotaChannel, selfMediaPlatformFromChannel, selfMediaPlatformLabel } from './selfMediaPlatforms'
