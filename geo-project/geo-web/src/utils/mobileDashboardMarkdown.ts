import type { QuestionSearchSource } from '@/types/mobileDashboard'
import {
  buildDisplaySearchSources,
  safePublicSourceUrl,
} from '@/utils/mobileDashboardSources'

const SOURCE_CITATION_PATTERN = /\[ref_(\d+)]/gi
const MARKDOWN_SOURCE_PATTERN = /\[[^\]]*]\(\s*(https?:\/\/[^)\s]+)\s*\)/gi
const ANGLE_BRACKET_SOURCE_PATTERN = /<(https?:\/\/[^\s>]+)>/gi
const PLAIN_SOURCE_PATTERN = /https?:\/\/[^\s<>"'\u3000]+/gi
const NUMBERED_SOURCE_PATTERN = /\[(\d+)]/g
const CITATION_PLACEHOLDER_PATTERN = /\uE000citation-(\d+)\uE001/g
const SOURCE_ATTRIBUTION_LINE_PATTERN = /^(?:[ \t]*>[ \t]*)?(?:[ \t]*[-*][ \t]*)?(?:(?:数据|信息|参考)?来源)[ \t]*[:：].*$/gm
const TRAILING_URL_PUNCTUATION = new Set([')', '）', ']', '】', ',', '，', '。', ';', '；', '!', '！', '?', '？'])

export function sourceReferenceNumber(source: QuestionSearchSource, index: number) {
  const citationIndex = Number(source.citationIndex)
  if (Number.isInteger(citationIndex) && citationIndex > 0) return citationIndex
  const rank = Number(source.rankNo)
  return Number.isInteger(rank) && rank > 0 ? rank : index + 1
}

export function replaceSourceCitationMarkers(
  value: string,
  searchSources?: QuestionSearchSource[],
) {
  const references = new Set<number>()
  const referencesByUrl = new Map<string, number>()
  buildDisplaySearchSources(searchSources).forEach((source, index) => {
    const reference = sourceReferenceNumber(source, index)
    references.add(reference)
    const key = sourceUrlKey(source.safeUrl)
    if (key) referencesByUrl.set(key, reference)
  })

  const citationPlaceholder = (rawNumber: string | number) => {
    const reference = Number(rawNumber)
    return references.has(reference)
      ? `\uE000citation-${reference}\uE001`
      : `[${reference}]`
  }
  const replaceSourceUrl = (url: string, fallback: string) => {
    const reference = referencesByUrl.get(sourceUrlKey(url))
    return reference ? citationPlaceholder(reference) : fallback
  }

  let normalized = value
    .replace(SOURCE_CITATION_PATTERN, (_marker, reference: string) => citationPlaceholder(reference))
    .replace(MARKDOWN_SOURCE_PATTERN, (marker, url: string) => replaceSourceUrl(url, marker))
    .replace(ANGLE_BRACKET_SOURCE_PATTERN, (marker, url: string) => replaceSourceUrl(url, marker))
    .replace(PLAIN_SOURCE_PATTERN, (marker) => {
      const { url, trailing } = splitTrailingPunctuation(marker)
      return `${replaceSourceUrl(url, url)}${trailing}`
    })
    .replace(NUMBERED_SOURCE_PATTERN, (_marker, reference: string) => citationPlaceholder(reference))

  if (references.size) {
    normalized = normalized.replace(SOURCE_ATTRIBUTION_LINE_PATTERN, '')
  }

  normalized = normalized.replace(
      /[（(]\s*(?:(?:数据|信息|参考)?来源)\s*[:：]\s*(\uE000citation-\d+\uE001)\s*[\/／]?\s*[）)]/g,
      '$1',
  )

  return normalized
    .replace(/\n{3,}/g, '\n\n')
    .replace(CITATION_PLACEHOLDER_PATTERN, (_marker, reference: string) =>
      `[[${reference}]](#reference-${reference})`,
    )
    .trimEnd()
}

function sourceUrlKey(value: string) {
  const safeUrl = safePublicSourceUrl(value)
  if (!safeUrl) return ''
  const url = new URL(safeUrl)
  url.hash = ''
  url.searchParams.sort()
  return url.toString()
}

function splitTrailingPunctuation(value: string) {
  let url = value
  let trailing = ''
  while (url && TRAILING_URL_PUNCTUATION.has(url.at(-1) || '')) {
    trailing = `${url.at(-1)}${trailing}`
    url = url.slice(0, -1)
  }
  return { url, trailing }
}
