import { describe, expect, it } from 'vitest'
import {
  canManageQuestionPolling,
  isQuestionPollingEnabled,
  questionPollingLabel,
} from './keywordQuestionPolling'

describe('keyword question polling', () => {
  it('keeps missing values enabled for rolling deployment compatibility', () => {
    expect(isQuestionPollingEnabled({})).toBe(true)
    expect(isQuestionPollingEnabled({ pollingEnabled: false })).toBe(false)
  })

  it('only exposes polling labels for tier A questions', () => {
    expect(questionPollingLabel({ questionTier: 'A', pollingEnabled: true })).toBe('轮询')
    expect(questionPollingLabel({ questionTier: 'A', pollingEnabled: false })).toBe('不轮询')
    expect(questionPollingLabel({ questionTier: 'B', pollingEnabled: true })).toBe('不适用')
    expect(questionPollingLabel({ questionTier: 'C', pollingEnabled: false })).toBe('不适用')
  })

  it('allows write-authorized internal users to manage active projects', () => {
    expect(canManageQuestionPolling('active', true)).toBe(true)
    expect(canManageQuestionPolling('pending_start', true)).toBe(true)
    expect(canManageQuestionPolling('paused', true)).toBe(true)
    expect(canManageQuestionPolling('expired', true)).toBe(false)
    expect(canManageQuestionPolling('active', false)).toBe(false)
  })
})
