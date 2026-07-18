import MarkdownIt from 'markdown-it'
import type { DiagnosticRunView } from '@/api/modelDiagnostic'

const diagnosticMarkdown = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true,
  typographer: false,
})

export interface DiagnosticConversationMessage {
  key: string
  role: 'user' | 'assistant'
  content: string
  platformName?: string
  run?: DiagnosticRunView
}

export interface DiagnosticReleaseRecommendation {
  tone: 'pass' | 'warning' | 'fail' | 'pending'
  label: string
  description: string
  notices: string[]
}

export function diagnosticSourceUrl(source: Record<string, unknown>): string {
  const candidate = source.normalizedUrl ?? source.originalUrl ?? source.url
  return typeof candidate === 'string' ? candidate : ''
}

export function safeDiagnosticSourceUrl(source: Record<string, unknown>): string {
  try {
    const url = new URL(diagnosticSourceUrl(source))
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : ''
  } catch {
    return ''
  }
}

export function buildDiagnosticConversation(
  runs: DiagnosticRunView[],
): DiagnosticConversationMessage[] {
  return runs.flatMap((run) => [
    { key: `user-${run.runId}`, role: 'user' as const, content: run.userMessage },
    ...(run.assistantMessage || run.error ? [{
      key: `assistant-${run.runId}`,
      role: 'assistant' as const,
      content: run.assistantMessage || diagnosticErrorMessage(run.error?.message) || '未生成回答',
      platformName: run.platformName,
      run,
    }] : []),
  ])
}

export function diagnosticErrorMessage(value?: string | null): string {
  if (!value) return ''
  if (value.includes('ModelNotOpen')) {
    return '供应商尚未开通当前模型，请先在火山方舟控制台激活对应模型服务。'
  }
  return value
}

export function shouldRestoreDiagnosticInput(status: DiagnosticRunView['status']): boolean {
  return status !== 'SUCCEEDED'
}

export function renderDiagnosticMarkdown(value: string): string {
  return diagnosticMarkdown.render(value || '')
}

export function renderDiagnosticAnswer(
  value: string,
  run?: Pick<DiagnosticRunView, 'sources' | 'citations'>,
): string {
  if (!run?.citations.length) return renderDiagnosticMarkdown(value)

  let markdown = value || ''
  const replacements: Array<{ placeholder: string; html: string }> = []
  const seenMarkers = new Set<string>()

  run.citations.forEach((citation, index) => {
    const marker = citationMarker(citation)
    if (!marker || seenMarkers.has(marker) || !markdown.includes(marker)) return

    const placeholder = `DIAGNOSTICCITATIONTOKEN${index}X`
    const source = citationSource(run.sources, citation)
    markdown = markdown.split(marker).join(placeholder)
    replacements.push({ placeholder, html: citationBadge(source, citation, index) })
    seenMarkers.add(marker)
  })

  let html = diagnosticMarkdown.render(markdown)
  replacements.forEach((replacement) => {
    html = html.split(replacement.placeholder).join(replacement.html)
  })
  return html
}

export function diagnosticWebSearchCallCount(
  run: Pick<DiagnosticRunView, 'webSearchCallCount' | 'usage'>,
): number {
  if (typeof run.webSearchCallCount === 'number') return Math.max(0, run.webSearchCallCount)
  const toolUsage = recordValue(run.usage.tool_usage)
  return nonNegativeInteger(toolUsage.web_search_call)
    ?? nonNegativeInteger(toolUsage.web_search)
    ?? 0
}

export function diagnosticCachedInputTokens(
  run: Pick<DiagnosticRunView, 'usage'>,
): number | null {
  const details = recordValue(run.usage.input_tokens_details)
  return nonNegativeInteger(details.cached_tokens)
}

export function diagnosticSearchCallLabel(
  run: Pick<DiagnosticRunView, 'diagnosticMode' | 'webSearchCallCount' | 'usage' | 'searchStatus' | 'sourceCount' | 'sources'>,
): string {
  const count = diagnosticWebSearchCallCount(run)
  const searchConfirmed = run.diagnosticMode === 'WEB_SEARCH'
    && (run.searchStatus === 'TRIGGERED' || (run.sourceCount ?? run.sources.length) > 0)
  return count === 0 && searchConfirmed ? '已搜索，次数未上报' : String(count)
}

export function diagnosticUniqueSourceDomainCount(
  sources: Array<Record<string, unknown>>,
): number {
  const domains = sources.map((source) => {
    if (typeof source.domain === 'string' && source.domain.trim()) {
      return source.domain.trim().toLowerCase().replace(/^www\./, '')
    }
    try {
      return new URL(safeDiagnosticSourceUrl(source)).hostname.toLowerCase().replace(/^www\./, '')
    } catch {
      return ''
    }
  }).filter(Boolean)
  return new Set(domains).size
}

export function isLongDiagnosticAnswer(value: string, threshold = 1600): boolean {
  return value.trim().length > threshold
}

export function diagnosticReleaseRecommendation(
  run: Pick<DiagnosticRunView,
    'status' | 'conclusion' | 'diagnosticMode' | 'promptTokens' | 'webSearchCallCount' | 'usage' |
    'searchStatus' | 'sourceCount' | 'sources' | 'capabilities'>,
): DiagnosticReleaseRecommendation {
  if (run.status !== 'SUCCEEDED') {
    return {
      tone: run.status === 'RUNNING' ? 'pending' : 'fail',
      label: run.status === 'RUNNING' ? '等待执行完成' : '暂缓接入轮询',
      description: '本轮执行链路尚未完整成功，不能作为生产放量依据。',
      notices: [],
    }
  }

  const notices: string[] = []
  const promptTokens = run.promptTokens ?? 0
  if (promptTokens >= 30_000) {
    notices.push(`单次输入 ${promptTokens.toLocaleString()} Token，正式轮询前需核算费用与吞吐预算。`)
  } else if (promptTokens >= 12_000) {
    notices.push(`单次输入 ${promptTokens.toLocaleString()} Token，建议在灰度期持续观察成本。`)
  }
  if (run.diagnosticMode === 'WEB_SEARCH'
    && diagnosticWebSearchCallCount(run) === 0
    && (run.searchStatus === 'TRIGGERED' || (run.sourceCount ?? run.sources.length) > 0)) {
    notices.push('供应商未上报搜索调用次数，本轮以结构化搜索证据确认已联网。')
  }
  Object.entries(run.capabilities).forEach(([key, value]) => {
    if (value !== 'WARNING') return
    const label = ({
      authentication: '鉴权', generation: '内容生成', webSearch: '联网搜索',
      sourceParsing: '来源解析', citationParsing: '引用解析',
    } as Record<string, string>)[key] || key
    notices.push(`${label}仍需人工复核。`)
  })

  if (run.conclusion === 'FAIL') {
    return {
      tone: 'fail',
      label: '暂缓接入轮询',
      description: '至少一项必要能力未通过，请修复后重新执行生产轮询模板。',
      notices,
    }
  }
  if (run.conclusion === 'WARNING' || notices.length > 0) {
    return {
      tone: 'warning',
      label: '可小流量灰度',
      description: '核心调用已完成，但应带着下列观察项进入灰度，暂不建议全量放开。',
      notices,
    }
  }
  if (run.conclusion === 'PASS') {
    return {
      tone: 'pass',
      label: '建议进入小流量灰度',
      description: '本轮能力验证通过；该结论只代表当前模型配置，不替代生产批次监控。',
      notices,
    }
  }
  return {
    tone: 'pending',
    label: '等待完整判定',
    description: '供应商请求已完成，但尚未形成可用于放量的诊断结论。',
    notices,
  }
}

function citationMarker(citation: Record<string, unknown>): string {
  if (typeof citation.citationText === 'string' && citation.citationText.trim()) {
    return citation.citationText.trim()
  }
  const index = nonNegativeInteger(citation.citationIndex)
  return index === null ? '' : `[${index}]`
}

function citationSource(
  sources: Array<Record<string, unknown>>,
  citation: Record<string, unknown>,
): Record<string, unknown> | undefined {
  const occurrenceIndex = nonNegativeInteger(citation.sourceOccurrenceIndex)
  if (occurrenceIndex !== null && occurrenceIndex < sources.length) return sources[occurrenceIndex]
  const citationIndex = nonNegativeInteger(citation.citationIndex)
  return citationIndex === null ? undefined : sources[citationIndex - 1]
}

function citationBadge(
  source: Record<string, unknown> | undefined,
  citation: Record<string, unknown>,
  fallbackIndex: number,
): string {
  const citationIndex = nonNegativeInteger(citation.citationIndex) ?? fallbackIndex + 1
  const url = source ? safeDiagnosticSourceUrl(source) : ''
  const label = sourceLabel(source, citationIndex)
  const title = typeof source?.title === 'string' && source.title.trim()
    ? source.title.trim()
    : `来源 ${citationIndex}`
  const escapedLabel = diagnosticMarkdown.utils.escapeHtml(label)
  const escapedTitle = diagnosticMarkdown.utils.escapeHtml(`来源 ${citationIndex}：${title}`)
  if (!url) {
    return `<span class="inline-citation inline-citation--disabled" title="${escapedTitle}">${escapedLabel}</span>`
  }
  const escapedUrl = diagnosticMarkdown.utils.escapeHtml(url)
  return `<a class="inline-citation" href="${escapedUrl}" target="_blank" rel="noopener noreferrer" title="${escapedTitle}">${escapedLabel}</a>`
}

function sourceLabel(source: Record<string, unknown> | undefined, citationIndex: number): string {
  if (typeof source?.domain === 'string' && source.domain.trim()) {
    return source.domain.trim().replace(/^www\./i, '')
  }
  if (source) {
    try {
      return new URL(safeDiagnosticSourceUrl(source)).hostname.replace(/^www\./i, '')
    } catch {
      // Fall through to a stable, non-clickable label.
    }
  }
  return `来源 ${citationIndex}`
}

function recordValue(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

function nonNegativeInteger(value: unknown): number | null {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0 ? value : null
}
