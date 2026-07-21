import type { KeywordGroupQuestion } from '@/types'

export function isQuestionPollingEnabled(question: Pick<KeywordGroupQuestion, 'pollingEnabled'>) {
  return question.pollingEnabled !== false
}

export function questionPollingLabel(question: Pick<KeywordGroupQuestion, 'questionTier' | 'pollingEnabled'>) {
  if (question.questionTier !== 'A') return '不适用'
  return isQuestionPollingEnabled(question) ? '轮询' : '不轮询'
}

export function canManageQuestionPolling(status: string | undefined, hasWritePermission: boolean) {
  return hasWritePermission
    && ['pending_start', 'active', 'paused'].includes(status || '')
}
