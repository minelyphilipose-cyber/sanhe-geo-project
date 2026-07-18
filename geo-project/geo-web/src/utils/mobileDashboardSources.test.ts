import { describe, expect, it } from 'vitest'
import { buildDisplaySearchSources, safePublicSourceUrl } from './mobileDashboardSources'

describe('mobile dashboard search sources', () => {
  it('uses the actual URL host and deduplicates tracking variants', () => {
    const sources = buildDisplaySearchSources([
      {
        sourceId: 1,
        title: '来源一',
        url: 'https://www.example.com/article?utm_source=search&id=7',
        domain: 'trusted-but-wrong.example',
      },
      {
        sourceId: 2,
        title: '重复来源',
        url: 'https://www.example.com/article?id=7#answer',
      },
    ])

    expect(sources).toHaveLength(1)
    expect(sources[0].host).toBe('example.com')
    expect(sources[0].domain).toBe('example.com')
    expect(sources[0].safeUrl).toBe('https://www.example.com/article?id=7')
  })

  it.each([
    'javascript:alert(1)',
    'http://127.0.0.1/internal',
    'http://192.168.1.5/internal',
    'https://trusted.example.com@evil.example.net/article',
  ])('rejects unsafe source URL %s', (url) => {
    expect(safePublicSourceUrl(url)).toBe('')
  })
})
