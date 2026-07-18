import { describe, expect, it } from 'vitest'
import type { DiagnosticRunView } from '@/api/modelDiagnostic'
import {
  buildDiagnosticConversation,
  diagnosticCachedInputTokens,
  diagnosticErrorMessage,
  diagnosticReleaseRecommendation,
  diagnosticSearchCallLabel,
  diagnosticSourceUrl,
  diagnosticUniqueSourceDomainCount,
  diagnosticWebSearchCallCount,
  isLongDiagnosticAnswer,
  renderDiagnosticAnswer,
  renderDiagnosticMarkdown,
  safeDiagnosticSourceUrl,
  shouldRestoreDiagnosticInput,
} from './modelDiagnosticConsole'

describe('model diagnostic console logic', () => {
  it('allows only HTTP(S) source links and keeps the full normalized URL', () => {
    const source = {
      normalizedUrl: 'https://news.example.com/article?id=7',
      originalUrl: 'https://redirect.example.com/7',
    }

    expect(diagnosticSourceUrl(source)).toBe('https://news.example.com/article?id=7')
    expect(safeDiagnosticSourceUrl(source)).toBe('https://news.example.com/article?id=7')
    expect(safeDiagnosticSourceUrl({ normalizedUrl: 'javascript:alert(1)' })).toBe('')
    expect(safeDiagnosticSourceUrl({ originalUrl: 'file:///etc/passwd' })).toBe('')
  })

  it('restores input for every non-success terminal state', () => {
    expect(shouldRestoreDiagnosticInput('SUCCEEDED')).toBe(false)
    expect(shouldRestoreDiagnosticInput('FAILED')).toBe(true)
    expect(shouldRestoreDiagnosticInput('REJECTED')).toBe(true)
    expect(shouldRestoreDiagnosticInput('ABANDONED')).toBe(true)
    expect(shouldRestoreDiagnosticInput('RUNNING')).toBe(true)
  })

  it('turns ModelNotOpen into an actionable product message', () => {
    expect(diagnosticErrorMessage(
      'Provider returned HTTP 404 (ModelNotOpen): activate the requested model',
    )).toBe('供应商尚未开通当前模型，请先在火山方舟控制台激活对应模型服务。')
  })

  it('renders model markdown while escaping embedded HTML and unsafe links', () => {
    const html = renderDiagnosticMarkdown(`## 推荐\n\n| 餐厅 | 特点 |\n| --- | --- |\n| 茶院 | 安静 |\n\n<script>alert(1)</script>\n\n[危险链接](javascript:alert(1))`)

    expect(html).toContain('<h2>推荐</h2>')
    expect(html).toContain('<table>')
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
    expect(html).not.toContain('<script>')
    expect(html).not.toContain('href="javascript:')
  })

  it('renders provider citation markers as safe domain pills', () => {
    const html = renderDiagnosticAnswer(
      '推荐桃花潭餐厅[1]，也可以考虑豆蔻餐厅[2]。',
      {
        sources: [
          { title: '桃花潭餐厅', domain: 'www.example.com', normalizedUrl: 'https://example.com/a' },
          { title: '豆蔻餐厅', domain: 'unsafe.example', normalizedUrl: 'javascript:alert(1)' },
        ],
        citations: [
          { citationIndex: 1, citationText: '[1]', sourceOccurrenceIndex: 0 },
          { citationIndex: 2, citationText: '[2]', sourceOccurrenceIndex: 1 },
        ],
      },
    )

    expect(html).toContain('class="inline-citation"')
    expect(html).toContain('href="https://example.com/a"')
    expect(html).toContain('>example.com</a>')
    expect(html).toContain('inline-citation--disabled')
    expect(html).toContain('>unsafe.example</span>')
    expect(html).not.toContain('[1]')
    expect(html).not.toContain('href="javascript:')
  })

  it('reads TokenHub search and cache usage from the provider response', () => {
    const usage = {
      tool_usage: { web_search_call: 3 },
      input_tokens_details: { cached_tokens: 8640 },
    }

    expect(diagnosticWebSearchCallCount({ webSearchCallCount: null, usage })).toBe(3)
    expect(diagnosticCachedInputTokens({ usage })).toBe(8640)
  })

  it('distinguishes a confirmed search from an unreported search count', () => {
    const searched = run({
      diagnosticMode: 'WEB_SEARCH',
      webSearchCallCount: 0,
      searchStatus: 'TRIGGERED',
      sourceCount: 2,
    })

    expect(diagnosticSearchCallLabel(searched)).toBe('已搜索，次数未上报')
    expect(diagnosticSearchCallLabel(run({ webSearchCallCount: 0 }))).toBe('0')
  })

  it('summarizes unique source domains without changing source evidence', () => {
    expect(diagnosticUniqueSourceDomainCount([
      { domain: 'www.example.com' },
      { normalizedUrl: 'https://example.com/second' },
      { normalizedUrl: 'https://other.example/a' },
      { normalizedUrl: 'javascript:alert(1)' },
    ])).toBe(2)
  })

  it('turns a high-token pass into a cost-aware gray release recommendation', () => {
    const recommendation = diagnosticReleaseRecommendation(run({
      diagnosticMode: 'WEB_SEARCH',
      promptTokens: 33_130,
      webSearchCallCount: 3,
      conclusion: 'PASS',
      capabilities: {
        authentication: 'PASS', generation: 'PASS', webSearch: 'PASS',
        sourceParsing: 'PASS', citationParsing: 'PASS',
      },
    }))

    expect(recommendation.tone).toBe('warning')
    expect(recommendation.label).toBe('可小流量灰度')
    expect(recommendation.notices[0]).toContain('33,130 Token')
  })

  it('collapses only materially long answers by default', () => {
    expect(isLongDiagnosticAnswer('a'.repeat(1600))).toBe(false)
    expect(isLongDiagnosticAnswer('a'.repeat(1601))).toBe(true)
  })

  it('restores audited failed turns but omits an empty assistant placeholder', () => {
    const succeeded = run({ runId: 1, assistantMessage: 'answer' })
    const failed = run({
      runId: 2,
      status: 'FAILED',
      assistantMessage: null,
      error: { code: 'HTTP_401', message: 'authentication failed' },
    })
    const running = run({ runId: 3, status: 'RUNNING', assistantMessage: null })

    const messages = buildDiagnosticConversation([succeeded, failed, running])

    expect(messages.map((message) => message.content)).toEqual([
      'question', 'answer', 'question', 'authentication failed', 'question',
    ])
  })
})

function run(overrides: Partial<DiagnosticRunView>): DiagnosticRunView {
  return {
    runId: 1,
    sessionId: '00000000-0000-4000-8000-000000000001',
    turnNo: 1,
    platformConfigId: 1,
    platformName: 'Platform',
    diagnosticMode: 'BASIC_CHAT',
    testMode: 'FREE_CHAT',
    status: 'SUCCEEDED',
    conclusion: 'PASS',
    conclusionReason: null,
    userMessage: 'question',
    assistantMessage: 'answer',
    providerRequestId: null,
    requestedModelId: 'model',
    responseModelId: 'model',
    httpStatus: 200,
    durationMs: 10,
    responseMode: 'SYNC',
    promptTokens: 3,
    completionTokens: 4,
    totalTokens: 7,
    webSearchCallCount: 0,
    searchStatus: null,
    sourceCount: 0,
    validSourceCount: 0,
    citationCount: 0,
    validCitationCount: 0,
    capabilities: {},
    searchEvidence: [],
    sources: [],
    citations: [],
    usage: {},
    sanitizedRequest: null,
    sanitizedResponse: null,
    error: null,
    startedAt: null,
    completedAt: null,
    createdAt: '2026-07-15T12:00:00',
    ...overrides,
  }
}
