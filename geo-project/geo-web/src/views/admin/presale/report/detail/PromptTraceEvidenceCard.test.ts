import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PromptTraceEvidenceCard from './PromptTraceEvidenceCard.vue'
import type { PresalePromptTraceEvidenceVO } from '@/api/presaleReport'

const stubs = {
  ElCard: { template: '<section><header><slot name="header" /></header><slot /></section>' },
  ElTag: { template: '<span><slot /></span>' },
  ElIcon: { template: '<i><slot /></i>' },
  ElButton: { template: '<button type="button"><slot /></button>' },
  ElCollapse: { template: '<div><slot /></div>' },
  ElCollapseItem: { template: '<div><slot name="title" /><slot /></div>' },
  ArrowDown: true,
  InfoFilled: true,
  Link: true,
  Search: true,
  TopRight: true,
}

function evidence(overrides: Partial<PresalePromptTraceEvidenceVO> = {}): PresalePromptTraceEvidenceVO {
  return {
    webSearch: true,
    queryContractVersion: 'WEB_SEARCH_V1',
    searchTriggered: true,
    searchStatus: 'SUCCEEDED',
    searchStatusText: '联网检索完成',
    evidenceLevel: 'SOURCES',
    evidenceLevelText: '含来源链接',
    failureCode: null,
    notice: null,
    searchQueries: ['新能源汽车市场份额'],
    sources: Array.from({ length: 6 }, (_, index) => ({
      index: index + 1,
      rank: index + 1,
      title: `来源标题 ${index + 1}`,
      url: `https://example${index + 1}.com/article`,
      domain: `example${index + 1}.com`,
      media: '示例媒体',
      snippet: `来源摘要 ${index + 1}`,
      publishTime: '2026-08-05T10:00:00',
      query: '新能源汽车市场份额',
    })),
    citations: [],
    ...overrides,
  }
}

describe('PromptTraceEvidenceCard', () => {
  it('shows four sources by default and expands all sources on demand', async () => {
    const wrapper = mount(PromptTraceEvidenceCard, {
      props: { evidence: evidence() },
      global: { stubs },
    })

    expect(wrapper.findAll('.source-item')).toHaveLength(4)
    expect(wrapper.text()).toContain('平台返回 6 条可核验来源')
    expect(wrapper.get('.source-title').attributes('target')).toBe('_blank')
    expect(wrapper.get('.source-title').attributes('rel')).toBe('noopener noreferrer')

    await wrapper.get('.expand-row button').trigger('click')

    expect(wrapper.findAll('.source-item')).toHaveLength(6)
    expect(wrapper.text()).toContain('收起来源')
  })

  it('explains a native API response without presenting it as a failure', () => {
    const wrapper = mount(PromptTraceEvidenceCard, {
      props: {
        evidence: evidence({
          webSearch: false,
          searchTriggered: false,
          searchStatus: 'NOT_APPLICABLE',
          searchStatusText: '原生 API 回答',
          notice: '本次问题通过平台原生 API 回答，未配置独立的联网引用来源。',
          searchQueries: [],
          sources: [],
        }),
      },
      global: { stubs },
    })

    expect(wrapper.text()).toContain('本次未使用独立联网搜索')
    expect(wrapper.text()).toContain('本次问题通过平台原生 API 回答')
    expect(wrapper.find('.source-item').exists()).toBe(false)
  })
})
