import ElementPlus, { ElMessageBox } from 'element-plus'
import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DiagnosticRunView } from '@/api/modelDiagnostic'
import ModelDiagnosticConsole from './ModelDiagnosticConsole.vue'

const api = vi.hoisted(() => ({
  getDiagnosticPlatforms: vi.fn(),
  getDiagnosticProbes: vi.fn(),
  executeDiagnostic: vi.fn(),
  getDiagnosticHistory: vi.fn(),
  getDiagnosticSessionRuns: vi.fn(),
}))

vi.mock('@/api/modelDiagnostic', () => api)
enableAutoUnmount(afterEach)

describe('ModelDiagnosticConsole', () => {
  it('explains the scope of each diagnostic test mode', async () => {
    const wrapper = await renderConsole()
    expect(wrapper.text()).toContain('手工输入问题；仅成功的自由对话会进入后续上下文。')

    const vm = wrapper.vm as unknown as {
      testMode: 'FREE_CHAT' | 'STANDARD_PROBE' | 'PRODUCTION_POLL_TEMPLATE'
      onTestModeChanged: () => Promise<void>
    }
    vm.testMode = 'STANDARD_PROBE'
    await vm.onTestModeChanged()
    expect(wrapper.text()).toContain('运行服务端固定问题，用于重复验证生成、联网和引用能力。')

    vm.testMode = 'PRODUCTION_POLL_TEMPLATE'
    await vm.onTestModeChanged()
    expect(wrapper.text()).toContain('复用正式轮询提示词验证真实问题，不执行批次、分片和统计。')
  })

  beforeEach(() => {
    api.getDiagnosticPlatforms.mockResolvedValue({ data: { data: platforms } })
    api.getDiagnosticProbes.mockResolvedValue({ data: { data: [] } })
    api.getDiagnosticHistory.mockResolvedValue({
      data: { data: { records: [], total: 0, page: 1, size: 20 } },
    })
    api.getDiagnosticSessionRuns.mockResolvedValue({ data: { data: [] } })
  })

  it('submits system prompt with mode and renders structured usage, source and citation details', async () => {
    api.executeDiagnostic.mockResolvedValue({ data: { data: run({ diagnosticMode: 'WEB_SEARCH' }) } })
    const wrapper = await renderConsole()
    const vm = wrapper.vm as unknown as {
      diagnosticMode: 'BASIC_CHAT' | 'WEB_SEARCH'
      onModeChanged: () => Promise<void>
    }
    vm.diagnosticMode = 'WEB_SEARCH'
    await vm.onModeChanged()
    const send = findButton(wrapper, '发送诊断')
    expect(send.attributes('disabled')).toBeDefined()

    await wrapper.find('input[placeholder*="简洁中文"]').setValue('  system instruction  ')
    await wrapper.find('textarea').setValue('hello')
    expect(findButton(wrapper, '发送诊断').attributes('disabled')).toBeUndefined()
    await findButton(wrapper, '发送诊断').trigger('click')
    await flushPromises()

    expect(api.executeDiagnostic).toHaveBeenCalledWith(expect.objectContaining({
      mode: 'WEB_SEARCH',
      systemPrompt: 'system instruction',
      userMessage: 'hello',
    }))
    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('')
    expect(wrapper.text()).toContain('响应方式：同步')
    expect(wrapper.text()).toContain('Token 11 / 5 / 16')
    expect(wrapper.text()).toContain('搜索调用 1')
    const sourceLink = wrapper.get('a.source-title-link')
    expect(sourceLink.text()).toBe('新闻来源')
    expect(sourceLink.attributes('href')).toBe('https://news.example.com/a?id=1')
    expect(sourceLink.attributes('target')).toBe('_blank')
    expect(sourceLink.attributes('rel')).toBe('noopener noreferrer')
    expect(wrapper.text()).not.toContain('https://news.example.com/a?id=1')
    expect(wrapper.text()).toContain('回答位置 4–7 → 新闻来源')
    expect(wrapper.text()).toContain('诊断通过')
    await findButton(wrapper, '查看诊断详情').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('{"authorization":"***"}')
    expect(document.body.textContent).toContain('{"answer":"answer"}')
    expect(document.body.textContent).not.toContain('"providerRequestId"')
  })

  it.each(['FAILED', 'REJECTED', 'ABANDONED'] as const)(
    'keeps retryable input when server returns %s',
    async (status) => {
      api.executeDiagnostic.mockResolvedValue({ data: { data: run({
        status,
        conclusion: status === 'FAILED' ? 'FAIL' : null,
        assistantMessage: null,
        error: { code: status, message: 'request failed' },
      }) } })
      const wrapper = await renderConsole()
      await wrapper.find('textarea').setValue('retry me')
      await findButton(wrapper, '发送诊断').trigger('click')
      await flushPromises()

      expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('retry me')
    },
  )

  it('keeps retryable input when HTTP request rejects', async () => {
    api.executeDiagnostic.mockRejectedValue(new Error('network down'))
    const wrapper = await renderConsole()
    await wrapper.find('textarea').setValue('retry after network')
    await findButton(wrapper, '发送诊断').trigger('click')
    await flushPromises()

    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value)
      .toBe('retry after network')
  })

  it('selects and submits the configured low-performance model tier', async () => {
    api.executeDiagnostic.mockResolvedValue({ data: { data: run({
      requestedModelId: 'model-basic-low',
      responseModelId: 'model-basic-low',
    }) } })
    const wrapper = await renderConsole()
    const vm = wrapper.vm as unknown as {
      platformSelectionKey: string
      onPlatformChanged: () => Promise<void>
    }

    vm.platformSelectionKey = '1:LOW'
    await vm.onPlatformChanged()
    await wrapper.find('textarea').setValue('low model check')
    await findButton(wrapper, '发送诊断').trigger('click')
    await flushPromises()

    expect(api.executeDiagnostic).toHaveBeenCalledWith(expect.objectContaining({
      platformConfigId: 1,
      modelTier: 'LOW',
      userMessage: 'low model check',
    }))
    expect(wrapper.text()).toContain('model-basic-low')
    expect(wrapper.text()).toContain('低性能模型')
  })

  it('presents capability status as localized product language', async () => {
    api.executeDiagnostic.mockResolvedValue({ data: { data: run({
      capabilities: {
        authentication: 'PASS',
        generation: 'PASS',
        webSearch: 'NOT_APPLICABLE',
        sourceParsing: 'NOT_APPLICABLE',
        citationParsing: 'NOT_APPLICABLE',
      },
      webSearchCallCount: 0,
      searchStatus: 'NOT_CONFIRMED',
      sources: [],
      citations: [],
    }) } })
    const wrapper = await renderConsole()
    await wrapper.find('textarea').setValue('basic question')
    await findButton(wrapper, '发送诊断').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('不适用')
    expect(wrapper.text()).not.toContain('NOT_APPLICABLE')
    expect(wrapper.text()).not.toContain('NOT_CONFIRMED')
  })

  it('renders a compact localized markdown result with collapsible evidence', async () => {
    api.executeDiagnostic.mockResolvedValue({ data: { data: run({
      diagnosticMode: 'WEB_SEARCH',
      conclusion: 'WARNING',
      conclusionReason: 'The request completed with capability warnings',
      assistantMessage: '## 餐厅推荐\n\n| 餐厅 | 特点 |\n| --- | --- |\n| 茶院 | 安静 |',
      citations: [{
        citationText: '[1]', sourceOccurrenceIndex: 0,
        answerStart: null, answerEnd: null, confidence: 'PROBABLE',
      }],
      validCitationCount: 0,
    }) } })
    const wrapper = await renderConsole()
    const vm = wrapper.vm as unknown as {
      diagnosticMode: 'BASIC_CHAT' | 'WEB_SEARCH'
      onModeChanged: () => Promise<void>
    }
    vm.diagnosticMode = 'WEB_SEARCH'
    await vm.onModeChanged()
    await wrapper.find('textarea').setValue('restaurant')
    await findButton(wrapper, '发送诊断').trigger('click')
    await flushPromises()

    expect(wrapper.find('.message-body--markdown h2').text()).toBe('餐厅推荐')
    expect(wrapper.find('.message-body--markdown table').exists()).toBe(true)
    expect(wrapper.text()).toContain('请求已完成，但部分能力仍需人工确认。')
    expect(wrapper.text()).toContain('来源证据')
    expect(wrapper.text()).toContain('引用映射')
    expect(wrapper.text()).toContain('可能匹配')
    expect(wrapper.text()).not.toContain('The request completed with capability warnings')
    expect(wrapper.text()).not.toContain('PROBABLE')
  })

  it('collapses a long provider answer and lets the operator expand it', async () => {
    api.executeDiagnostic.mockResolvedValue({ data: { data: run({
      assistantMessage: `## 长回答\n\n${'详细内容'.repeat(500)}`,
    }) } })
    const wrapper = await renderConsole()
    await wrapper.find('textarea').setValue('long answer')
    await findButton(wrapper, '发送诊断').trigger('click')
    await flushPromises()

    expect(wrapper.get('.answer-shell').classes()).toContain('is-collapsed')
    const toggle = findButton(wrapper, '展开完整回答')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    await toggle.trigger('click')
    expect(wrapper.get('.answer-shell').classes()).not.toContain('is-collapsed')
    expect(findButton(wrapper, '收起回答').attributes('aria-expanded')).toBe('true')
  })

  it('shows a cost-aware gray recommendation for a high-token successful run', async () => {
    api.executeDiagnostic.mockResolvedValue({ data: { data: run({
      diagnosticMode: 'WEB_SEARCH',
      conclusion: 'PASS',
      promptTokens: 33_130,
      webSearchCallCount: 3,
      capabilities: {
        authentication: 'PASS', generation: 'PASS', webSearch: 'PASS',
        sourceParsing: 'PASS', citationParsing: 'PASS',
      },
    }) } })
    const wrapper = await renderConsole()
    const vm = wrapper.vm as unknown as {
      diagnosticMode: 'BASIC_CHAT' | 'WEB_SEARCH'
      onModeChanged: () => Promise<void>
    }
    vm.diagnosticMode = 'WEB_SEARCH'
    await vm.onModeChanged()
    await wrapper.find('textarea').setValue('cost check')
    await findButton(wrapper, '发送诊断').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('可小流量灰度')
    expect(wrapper.text()).toContain('33,130 Token')
    expect(wrapper.text()).toContain('1 域名')
  })

  it('replaces visible citation numbers with clickable source-domain pills', async () => {
    api.executeDiagnostic.mockResolvedValue({ data: { data: run({
      diagnosticMode: 'WEB_SEARCH',
      assistantMessage: '推荐新闻来源[1]。',
      webSearchCallCount: null,
      usage: {
        input_tokens: 33130,
        input_tokens_details: { cached_tokens: 8640 },
        tool_usage: { web_search_call: 3 },
      },
    }) } })
    const wrapper = await renderConsole()
    const vm = wrapper.vm as unknown as {
      diagnosticMode: 'BASIC_CHAT' | 'WEB_SEARCH'
      onModeChanged: () => Promise<void>
    }
    vm.diagnosticMode = 'WEB_SEARCH'
    await vm.onModeChanged()
    await wrapper.find('textarea').setValue('news')
    await findButton(wrapper, '发送诊断').trigger('click')
    await flushPromises()

    const citation = wrapper.get('.message-body--markdown a.inline-citation')
    expect(citation.text()).toBe('news.example.com')
    expect(citation.attributes('href')).toBe('https://news.example.com/a?id=1')
    expect(wrapper.find('.message-body--markdown').text()).not.toContain('[1]')
    expect(wrapper.text()).toContain('搜索调用3')
    expect(wrapper.text()).toContain('其中缓存 Token8640')
  })

  it('requires confirmation before switching a conversation mode', async () => {
    api.executeDiagnostic.mockResolvedValue({ data: { data: run() } })
    const wrapper = await renderConsole()
    await wrapper.find('textarea').setValue('hello')
    await findButton(wrapper, '发送诊断').trigger('click')
    await flushPromises()
    const confirm = vi.spyOn(ElMessageBox, 'confirm')
      .mockRejectedValueOnce(new Error('cancelled'))

    const vm = wrapper.vm as unknown as {
      diagnosticMode: 'BASIC_CHAT' | 'WEB_SEARCH'
      onModeChanged: () => Promise<void>
    }
    vm.diagnosticMode = 'WEB_SEARCH'
    await vm.onModeChanged()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(vm.diagnosticMode).toBe('BASIC_CHAT')
    expect(wrapper.text()).toContain('answer')

    confirm.mockResolvedValueOnce(undefined as never)
    vm.diagnosticMode = 'WEB_SEARCH'
    await vm.onModeChanged()
    expect(vm.diagnosticMode).toBe('WEB_SEARCH')
    expect(wrapper.text()).not.toContain('answer')
  })

  it('restores a server-owned session history', async () => {
    api.getDiagnosticSessionRuns.mockResolvedValue({ data: { data: [run()] } })
    const wrapper = await renderConsole()
    const vm = wrapper.vm as unknown as { restoreSession: (id: string) => Promise<void> }

    await vm.restoreSession('00000000-0000-4000-8000-000000000001')
    await flushPromises()

    expect(wrapper.text()).toContain('question')
    expect(wrapper.text()).toContain('answer')
  })
})

async function renderConsole(): Promise<VueWrapper> {
  const wrapper = mount(ModelDiagnosticConsole, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      stubs: {
        teleport: true,
        ElDrawer: { template: '<section data-testid="drawer"><slot /></section>' },
      },
    },
  })
  await flushPromises()
  return wrapper
}

function findButton(wrapper: VueWrapper, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text))
  if (!button) throw new Error(`Button not found: ${text}`)
  return button
}

function run(overrides: Partial<DiagnosticRunView> = {}): DiagnosticRunView {
  return {
    runId: 8,
    sessionId: '00000000-0000-4000-8000-000000000001',
    turnNo: 1,
    platformConfigId: 1,
    platformName: 'Basic Platform',
    diagnosticMode: 'BASIC_CHAT',
    testMode: 'FREE_CHAT',
    status: 'SUCCEEDED',
    conclusion: 'PASS',
    conclusionReason: 'ok',
    userMessage: 'question',
    assistantMessage: 'answer',
    providerRequestId: 'request-secret-id',
    requestedModelId: 'model-basic',
    responseModelId: 'model-basic',
    httpStatus: 200,
    durationMs: 100,
    responseMode: 'SYNC',
    promptTokens: 11,
    completionTokens: 5,
    totalTokens: 16,
    webSearchCallCount: 1,
    searchStatus: 'TRIGGERED',
    sourceCount: 1,
    validSourceCount: 1,
    citationCount: 1,
    validCitationCount: 1,
    capabilities: { authentication: 'PASS', generation: 'PASS' },
    searchEvidence: [],
    sources: [{
      title: '新闻来源',
      normalizedUrl: 'https://news.example.com/a?id=1',
      domain: 'news.example.com',
    }],
    citations: [{
      citationText: '[1]', sourceOccurrenceIndex: 0,
      answerStart: 4, answerEnd: 7, confidence: 'CONFIRMED',
    }],
    usage: { total_tokens: 16 },
    sanitizedRequest: '{"authorization":"***"}',
    sanitizedResponse: '{"answer":"answer"}',
    error: null,
    startedAt: '2026-07-15T12:00:00',
    completedAt: '2026-07-15T12:00:00',
    createdAt: '2026-07-15T12:00:00',
    ...overrides,
  }
}

const platforms = [
  {
    platformConfigId: 1,
    channelCode: 'TEST',
    platformCode: 'basic',
    platformName: 'Basic Platform',
    modelId: 'model-basic',
    modelTier: 'PRIMARY',
    usageScene: null,
    integrationType: 'OPENAI_CHAT',
    enabled: true,
    enabledForQuestionPoll: false,
    credentialAvailable: true,
    supportedModes: ['BASIC_CHAT'],
    responseModes: ['SYNC'],
    selectable: true,
    unavailableReason: null,
  },
  {
    platformConfigId: 1,
    channelCode: 'TEST',
    platformCode: 'basic',
    platformName: 'Basic Platform',
    modelId: 'model-basic-low',
    modelTier: 'LOW',
    usageScene: null,
    integrationType: 'OPENAI_CHAT',
    enabled: true,
    enabledForQuestionPoll: false,
    credentialAvailable: true,
    supportedModes: ['BASIC_CHAT'],
    responseModes: ['SYNC'],
    selectable: true,
    unavailableReason: null,
  },
  {
    platformConfigId: 2,
    channelCode: 'TEST',
    platformCode: 'web',
    platformName: 'Web Platform',
    modelId: 'model-web',
    modelTier: 'PRIMARY',
    usageScene: null,
    integrationType: 'VOLCENGINE_RESPONSES_WEB',
    enabled: true,
    enabledForQuestionPoll: true,
    credentialAvailable: true,
    supportedModes: ['WEB_SEARCH'],
    responseModes: ['SYNC'],
    selectable: true,
    unavailableReason: null,
  },
]
