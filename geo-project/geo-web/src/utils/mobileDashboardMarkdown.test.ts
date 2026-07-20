import { describe, expect, it } from 'vitest'
import { replaceSourceCitationMarkers, sourceReferenceNumber } from './mobileDashboardMarkdown'

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
})
