import type { QuestionSearchSource } from '@/types/mobileDashboard'

const TRACKING_PARAMETERS = new Set([
  'spm',
  'gclid',
  'fbclid',
  'msclkid',
  'mc_cid',
  'mc_eid',
  '_hsenc',
  '_hsmi',
])

export interface DisplaySearchSource extends QuestionSearchSource {
  safeUrl: string
  host: string
}

export function buildDisplaySearchSources(
  candidates: QuestionSearchSource[] | undefined,
  limit = 6,
): DisplaySearchSource[] {
  const sources: DisplaySearchSource[] = []
  const seen = new Set<string>()
  for (const source of candidates || []) {
    const safeUrl = safePublicSourceUrl(source.url)
    if (!safeUrl) continue
    const url = new URL(safeUrl)
    const dedupeKey = canonicalSourceKey(url)
    if (seen.has(dedupeKey)) continue
    seen.add(dedupeKey)
    sources.push({
      ...source,
      safeUrl,
      host: normalizedHostname(url),
      domain: normalizedHostname(url),
    })
    if (sources.length >= limit) break
  }
  return sources
}

export function safePublicSourceUrl(value?: string | null) {
  if (!value) return ''
  try {
    const url = new URL(value)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return ''
    if (!isPublicHostname(url.hostname)) return ''
    url.hash = ''
    for (const name of [...url.searchParams.keys()]) {
      if (name.toLowerCase().startsWith('utm_') || TRACKING_PARAMETERS.has(name.toLowerCase())) {
        url.searchParams.delete(name)
      }
    }
    return url.toString()
  } catch {
    return ''
  }
}

function canonicalSourceKey(source: URL) {
  const url = new URL(source.toString())
  url.hash = ''
  for (const name of [...url.searchParams.keys()]) {
    if (name.toLowerCase().startsWith('utm_') || TRACKING_PARAMETERS.has(name.toLowerCase())) {
      url.searchParams.delete(name)
    }
  }
  url.searchParams.sort()
  if ((url.protocol === 'http:' && url.port === '80') || (url.protocol === 'https:' && url.port === '443')) {
    url.port = ''
  }
  return url.toString()
}

function normalizedHostname(url: URL) {
  return url.hostname.replace(/^www\./i, '').replace(/^\[|\]$/g, '').toLowerCase()
}

function isPublicHostname(value: string) {
  const host = value.replace(/^\[|\]$/g, '').toLowerCase()
  if (host === 'localhost' || host.endsWith('.localhost') || host.endsWith('.local')) return false
  if (host.includes(':')) {
    return !(
      host === '::'
      || host === '::1'
      || /^fe[89ab]/.test(host)
      || host.startsWith('fc')
      || host.startsWith('fd')
    )
  }
  const parts = host.split('.')
  if (parts.length !== 4 || parts.some((part) => !/^\d{1,3}$/.test(part))) return true
  const octets = parts.map(Number)
  if (octets.some((part) => part < 0 || part > 255)) return false
  return octets[0] !== 0
    && octets[0] !== 10
    && octets[0] !== 127
    && !(octets[0] === 100 && octets[1] >= 64 && octets[1] <= 127)
    && !(octets[0] === 169 && octets[1] === 254)
    && !(octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31)
    && !(octets[0] === 192 && octets[1] === 168)
    && !(octets[0] === 198 && [18, 19].includes(octets[1]))
    && octets[0] < 224
}
