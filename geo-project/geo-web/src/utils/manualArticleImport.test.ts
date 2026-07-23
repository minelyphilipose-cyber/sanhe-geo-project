import { describe, expect, it } from 'vitest'
import {
  calculateManualArticleStats,
  composeManualArticleMarkdown,
  evaluateManualArticleSubmission,
  normalizePastedArticle,
  removeSuggestedLeadingTitle,
} from './manualArticleImport'

describe('manualArticleImport', () => {
  it('extracts the first H1, demotes later H1s, and omits images', () => {
    const result = normalizePastedArticle([
      '# 文章标题',
      '',
      '第一段',
      '',
      '![图片](https://example.test/a.png)',
      '',
      '# 第二部分',
    ].join('\n'))

    expect(result.title).toBe('文章标题')
    expect(result.bodyMarkdown).toContain('## 第二部分')
    expect(result.bodyMarkdown).not.toContain('example.test')
    expect(result.omittedImages).toBe(1)
    expect(result.demotedHeadings).toBe(1)
  })

  it('keeps a possible plain-text title until the user accepts it', () => {
    const result = normalizePastedArticle('可能的标题\n\n正文内容')

    expect(result.title).toBe('')
    expect(result.suggestedTitle).toBe('可能的标题')
    expect(result.bodyMarkdown).toBe('可能的标题\n\n正文内容')
    expect(removeSuggestedLeadingTitle(result.bodyMarkdown, result.suggestedTitle)).toBe('正文内容')
  })

  it('composes one canonical H1 and normalizes H1s in the body', () => {
    expect(composeManualArticleMarkdown('文章标题', '# 文章标题\n\n# 小节\n\n正文')).toBe(
      '# 文章标题\n\n## 小节\n\n正文',
    )
  })

  it('uses one stable definition for current content stats', () => {
    expect(calculateManualArticleStats('第一段\n\n## 小节\n\n第二段')).toEqual({
      characters: 10,
      blocks: 3,
      images: 0,
    })
    expect(calculateManualArticleStats('正文\n\n![配图](https://example.test/a.png)')).toMatchObject({ images: 1 })
    expect(calculateManualArticleStats('   \n')).toEqual({ characters: 0, blocks: 0, images: 0 })
  })


  it('blocks direct save only while required fields remain incomplete', () => {
    const completeInput = {
      hasProject: true,
      hasArticleType: true,
      hasContentStyle: true,
      hasTopic: true,
      hasTitle: true,
      hasBody: true,
      withinContentLimit: true,
      hasRequiredCover: true,
    }

    expect(evaluateManualArticleSubmission(completeInput).canSubmit).toBe(true)
    expect(evaluateManualArticleSubmission({ ...completeInput, hasTitle: false })).toMatchObject({
      missingRequiredCount: 1,
      canSubmit: false,
    })
  })
})
