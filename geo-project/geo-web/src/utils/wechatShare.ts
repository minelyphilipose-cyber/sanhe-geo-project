import type { MobileDashboardWechatErrorCode } from '@/types/mobileDashboard'

export function isWechatBrowser(userAgent = navigator.userAgent) {
  return /MicroMessenger/i.test(userAgent) && !/wxwork/i.test(userAgent)
}

export function isIosWechat(userAgent = navigator.userAgent) {
  return isWechatBrowser(userAgent) && /iPhone|iPad|iPod/i.test(userAgent)
}

export function stripUrlFragment(url: string) {
  const fragmentIndex = url.indexOf('#')
  return fragmentIndex >= 0 ? url.slice(0, fragmentIndex) : url
}

export function resolveWechatSignatureUrl(
  currentUrl: string,
  entryUrl: string,
  userAgent = navigator.userAgent,
) {
  return stripUrlFragment(isIosWechat(userAgent) ? entryUrl : currentUrl)
}

export function classifyWechatSdkError(message?: string): MobileDashboardWechatErrorCode {
  const normalized = message?.toLowerCase() || ''
  if (normalized.includes('invalid signature')) return 'invalid_signature'
  if (normalized.includes('invalid url domain')) return 'invalid_url_domain'
  if (normalized.includes('permission denied')) return 'permission_denied'
  if (normalized.includes('timeout')) return 'timeout'
  if (normalized) return 'sdk_error'
  return 'unknown'
}
