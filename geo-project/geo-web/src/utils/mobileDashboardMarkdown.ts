import type { QuestionSearchSource } from '@/types/mobileDashboard'
import { buildDisplaySearchSources } from '@/utils/mobileDashboardSources'

const SOURCE_CITATION_PATTERN = /\[ref_(\d+)]/gi

export function sourceReferenceNumber(source: QuestionSearchSource, index: number) {
  const rank = Number(source.rankNo)
  return Number.isInteger(rank) && rank > 0 ? rank : index + 1
}

export function replaceSourceCitationMarkers(
  value: string,
  searchSources?: QuestionSearchSource[],
) {
  const references = new Set(
    buildDisplaySearchSources(searchSources).map((source, index) => sourceReferenceNumber(source, index)),
  )

  return value.replace(SOURCE_CITATION_PATTERN, (_marker, rawNumber: string) => {
    const reference = Number(rawNumber)
    return references.has(reference)
      ? `[[${reference}]](#reference-${reference})`
      : `[${reference}]`
  })
}
