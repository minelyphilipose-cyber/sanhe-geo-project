import { describe, expect, it } from 'vitest'
import type { QuestionMonitorItem } from '@/types/mobileDashboard'
import { isSearchNotTriggered, questionMonitorStatus } from './mobileDashboardQuestionStatus'

function item(values: Partial<QuestionMonitorItem>): QuestionMonitorItem {
  return {
    platformCode: 'doubao',
    questionTitle: '测试问题',
    mentioned: false,
    recommended: { available: false },
    firstRecommend: { available: false },
    rankPosition: { available: false },
    tags: [],
    ...values,
  }
}

describe('mobile dashboard question status', () => {
  it('does not collapse a completed untriggered search into building', () => {
    const row = item({ pollResultId: 10, monitorStatus: 'search_not_triggered' })

    expect(questionMonitorStatus(row)).toBe('search_not_triggered')
    expect(isSearchNotTriggered(row)).toBe(true)
  })

  it('keeps backward compatible fallbacks for cached rows', () => {
    expect(questionMonitorStatus(item({ pollResultId: 10 }))).toBe('not_mentioned')
    expect(questionMonitorStatus(item({ pollResultId: null }))).toBe('pending')
  })
})
