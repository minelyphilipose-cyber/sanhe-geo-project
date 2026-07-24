export interface PastedArticleResult {
  title: string
  suggestedTitle: string
  bodyMarkdown: string
  omittedImages: number
  demotedHeadings: number
}

export interface ManualArticleContentStats {
  characters: number
  blocks: number
  images: number
}

export interface ManualArticleSubmissionInput {
  hasProject: boolean
  hasArticleType: boolean
  hasTargetChannel: boolean
  hasTopic: boolean
  hasTitle: boolean
  hasBody: boolean
  withinContentLimit: boolean
  hasRequiredCover: boolean
}

export interface ManualArticleSubmissionState {
  missingRequiredCount: number
  canSubmit: boolean
}

const MARKDOWN_IMAGE_PATTERN = /!\[[^\]]*]\([^\r\n)]*\)/g
const HTML_IMAGE_PATTERN = /<img\b[^>]*>/gi
const HEADING_PATTERN = /^(#{1,6})\s+(.+?)\s*$/

export function normalizePastedArticle(source: string): PastedArticleResult {
  const normalized = source.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n')
  let omittedImages = 0
  const withoutImages = normalized
    .replace(MARKDOWN_IMAGE_PATTERN, () => {
      omittedImages += 1
      return ''
    })
    .replace(HTML_IMAGE_PATTERN, () => {
      omittedImages += 1
      return ''
    })

  const body: string[] = []
  let title = ''
  let fenced = false
  let demotedHeadings = 0

  for (const line of withoutImages.split('\n')) {
    const trimmed = line.trim()
    if (trimmed.startsWith('```') || trimmed.startsWith('~~~')) {
      fenced = !fenced
      body.push(line)
      continue
    }
    const heading = fenced ? null : line.match(HEADING_PATTERN)
    if (heading?.[1] === '#' && !title) {
      title = heading[2].trim()
      continue
    }
    if (heading?.[1] === '#') {
      body.push(`## ${heading[2].trim()}`)
      demotedHeadings += 1
      continue
    }
    body.push(line)
  }

  const bodyMarkdown = trimBlankLines(body.join('\n'))
  return {
    title,
    suggestedTitle: title ? '' : leadingTitleCandidate(bodyMarkdown),
    bodyMarkdown,
    omittedImages,
    demotedHeadings,
  }
}

export function removeSuggestedLeadingTitle(bodyMarkdown: string, suggestedTitle: string): string {
  const lines = bodyMarkdown.split(/\r?\n/)
  const firstContentIndex = lines.findIndex((line) => line.trim())
  if (firstContentIndex < 0) return bodyMarkdown
  const candidate = plainTitle(lines[firstContentIndex])
  if (candidate !== suggestedTitle.trim()) return bodyMarkdown
  lines.splice(firstContentIndex, 1)
  return trimBlankLines(lines.join('\n'))
}

export function composeManualArticleMarkdown(title: string, bodyMarkdown: string): string {
  const normalizedTitle = title.trim()
  const normalizedBody = normalizeBodyHeadings(bodyMarkdown, normalizedTitle)
  if (!normalizedTitle) return normalizedBody
  return normalizedBody ? `# ${normalizedTitle}\n\n${normalizedBody}` : `# ${normalizedTitle}`
}

export function calculateManualArticleStats(bodyMarkdown: string): ManualArticleContentStats {
  const normalized = bodyMarkdown.trim()
  return {
    characters: bodyMarkdown.replace(/\s/g, '').length,
    blocks: normalized ? normalized.split(/\n\s*\n/).filter((block) => block.trim()).length : 0,
    images: (bodyMarkdown.match(MARKDOWN_IMAGE_PATTERN) || []).length + (bodyMarkdown.match(HTML_IMAGE_PATTERN) || []).length,
  }
}

export function evaluateManualArticleSubmission(input: ManualArticleSubmissionInput): ManualArticleSubmissionState {
  const missingRequiredCount = [
    input.hasProject,
    input.hasArticleType,
    input.hasTargetChannel,
    input.hasTopic,
    input.hasTitle,
    input.hasBody,
    input.withinContentLimit,
    input.hasRequiredCover,
  ].filter((valid) => !valid).length
  return {
    missingRequiredCount,
    canSubmit: missingRequiredCount === 0,
  }
}

function normalizeBodyHeadings(bodyMarkdown: string, title: string): string {
  const lines: string[] = []
  let fenced = false
  let removedMatchingTitle = false
  for (const line of bodyMarkdown.replace(/\r\n?/g, '\n').split('\n')) {
    const trimmed = line.trim()
    if (trimmed.startsWith('```') || trimmed.startsWith('~~~')) {
      fenced = !fenced
      lines.push(line)
      continue
    }
    const heading = fenced ? null : line.match(HEADING_PATTERN)
    if (heading?.[1] === '#') {
      const headingTitle = heading[2].trim()
      if (!removedMatchingTitle && headingTitle === title) {
        removedMatchingTitle = true
        continue
      }
      lines.push(`## ${headingTitle}`)
      continue
    }
    lines.push(line)
  }
  return trimBlankLines(lines.join('\n'))
}

function leadingTitleCandidate(markdown: string): string {
  for (const line of markdown.split('\n')) {
    const candidate = plainTitle(line)
    if (candidate && candidate.length <= 120) return candidate
  }
  return ''
}

function plainTitle(value: string): string {
  return value
    .trim()
    .replace(/^#{1,6}\s+/, '')
    .replace(/^[>*_`~\-\s]+|[*_`~\s]+$/g, '')
    .trim()
}

function trimBlankLines(value: string): string {
  return value.replace(/^(?:[ \t]*\n)+/, '').replace(/(?:\n[ \t]*)+$/, '').trim()
}
