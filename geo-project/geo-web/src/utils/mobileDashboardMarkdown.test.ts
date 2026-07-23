import { describe, expect, it } from 'vitest'
import {
  replaceSourceCitationMarkers,
  sourceReferenceNumber,
  stripSourceReferencesForDisplay,
} from './mobileDashboardMarkdown'

describe('mobile dashboard markdown citations', () => {
  it('turns provider reference markers into links to valid source cards', () => {
    const markdown = replaceSourceCitationMarkers(
      '正文[ref_1]，补充[ref_3]，缺失[ref_9]。',
      [
        { sourceId: 1, rankNo: 1, url: 'https://example.com/one' },
        { sourceId: 3, rankNo: 3, url: 'https://example.com/three' },
      ],
    )

    expect(markdown).toBe('正文[[1]](#reference-1)，补充[[3]](#reference-3)，缺失[9]。')
  })

  it('falls back to display order when a source rank is missing', () => {
    expect(sourceReferenceNumber({ sourceId: 7, url: 'https://example.com' }, 2)).toBe(3)
  })

  it('prefers the provider citation index over the search result rank', () => {
    expect(sourceReferenceNumber({
      sourceId: 7,
      citationIndex: 4,
      rankNo: 1,
      url: 'https://example.com',
    }, 0)).toBe(4)
  })

  it.each([
    ['DeepSeek numeric citation', '正文[2]\n\n> 来源：[2]', '正文[[2]](#reference-2)'],
    ['Qwen provider citation', '正文[ref_2]', '正文[[2]](#reference-2)'],
    ['Doubao markdown link', '正文（信息来源：[搜狐](https://example.com/a)）', '正文[[2]](#reference-2)'],
    ['Yuanbao source-only link', '正文\n来源：https://example.com/a', '正文'],
    ['Yuanbao inline plain link', '正文（信息来源：https://example.com/a）', '正文[[2]](#reference-2)'],
  ])('normalizes %s to the shared citation style', (_name, input, expected) => {
    expect(replaceSourceCitationMarkers(input, [{
      sourceId: 2,
      citationIndex: 2,
      rankNo: 1,
      url: 'https://example.com/a',
    }])).toBe(expected)
  })

  it('keeps an unrelated markdown link unchanged', () => {
    expect(replaceSourceCitationMarkers(
      '[官网](https://other.example.com)',
      [{ sourceId: 1, rankNo: 1, url: 'https://example.com/a' }],
    )).toBe('[官网](https://other.example.com)')
  })

  it('removes source markers and attribution when reference display is hidden', () => {
    expect(stripSourceReferencesForDisplay(
      '回答结论[ref_1]。\n\n信息来源：https://example.com/a',
      [{ sourceId: 1, citationIndex: 1, url: 'https://example.com/a' }],
    )).toBe('回答结论。')
  })
})
