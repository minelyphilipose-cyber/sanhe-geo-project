import { describe, expect, it } from 'vitest'
import {
  batchProgress,
  createClientRequestId,
  estimateLogicalResults,
  estimateMaxProviderCalls,
  isTerminalBatchStatus,
  safeVerificationSourceUrl,
} from './questionPollVerification'

describe('question poll verification helpers', () => {
  it('calculates logical results and the retry-aware provider call ceiling', () => {
    expect(estimateLogicalResults(4, 3)).toBe(12)
    expect(estimateMaxProviderCalls(4, 3)).toBe(24)
  })

  it('recognizes only completed batch states as terminal', () => {
    expect(isTerminalBatchStatus('ready')).toBe(false)
    expect(isTerminalBatchStatus('planning')).toBe(false)
    expect(isTerminalBatchStatus('finished')).toBe(true)
    expect(isTerminalBatchStatus('finished_with_failures')).toBe(true)
    expect(isTerminalBatchStatus('failed')).toBe(true)
  })

  it('calculates terminal shard progress safely', () => {
    expect(batchProgress(null)).toBe(0)
    expect(batchProgress({ shardCount: 4, terminalShardCount: 3 } as never)).toBe(75)
    expect(batchProgress({ shardCount: 2, terminalShardCount: 3 } as never)).toBe(100)
  })

  it('creates a UUID-compatible idempotency key', () => {
    expect(createClientRequestId()).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    )
  })

  it('allows only safe HTTP source links', () => {
    expect(safeVerificationSourceUrl('https://example.com/source')).toBe('https://example.com/source')
    expect(safeVerificationSourceUrl('javascript:alert(1)')).toBe('')
    expect(safeVerificationSourceUrl('not-a-url')).toBe('')
  })
})
