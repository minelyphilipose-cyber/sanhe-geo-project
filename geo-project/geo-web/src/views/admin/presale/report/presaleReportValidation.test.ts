import { describe, expect, it } from 'vitest'
import {
  competitorGroupLength,
  findDuplicateLlmQuestion,
  templatePromptError
} from './presaleReportValidation'

describe('presale report validation', () => {
  it('counts the stored competitor group including separators', () => {
    expect(competitorGroupLength(['甲'.repeat(32), '乙'.repeat(32), '丙'.repeat(34)])).toBe(100)
    expect(competitorGroupLength(['甲'.repeat(33), '乙'.repeat(33), '丙'.repeat(33)])).toBe(101)
  })

  it('validates template variables and competitor semantics', () => {
    expect(templatePromptError('{brand} 和 {competitor} 相比如何？', true)).toBe('')
    expect(templatePromptError('{brand} 怎么样？', true)).toContain('必须包含')
    expect(templatePromptError('{brand} 和 {foo} 怎么样？', false)).toContain('未知变量')
    expect(templatePromptError('   ', false)).toContain('不能为空')
  })

  it('finds duplicates only within the same LLM question category', () => {
    const duplicate = findDuplicateLlmQuestion([
      { categoryCode: 'COGNITIVE', promptContent: '品牌怎么样？' },
      { categoryCode: 'COMPARISON', promptContent: '品牌怎么样？' },
      { categoryCode: 'COGNITIVE', promptContent: '  品牌怎么样？  ' }
    ])

    expect(duplicate?.categoryCode).toBe('COGNITIVE')
  })
})
