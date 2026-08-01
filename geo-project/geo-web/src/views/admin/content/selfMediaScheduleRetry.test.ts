import { describe, expect, it } from 'vitest'
import { requiresManualPlatformVerification } from './selfMediaScheduleRetry'

describe('self media schedule retry safety', () => {
  it('blocks retry after a verified fill or uncertain page mutation', () => {
    expect(requiresManualPlatformVerification({ status: 'filled_verified' })).toBe(true)
    expect(requiresManualPlatformVerification({
      status: 'manual_required',
      runtimeStage: 'execution_heartbeat_timeout_uncertain',
    })).toBe(true)
  })

  it('blocks heartbeat timeout tasks even when older responses omit runtime stage', () => {
    expect(requiresManualPlatformVerification({
      status: 'manual_required',
      failureCode: 'LOCAL_AGENT_HEARTBEAT_TIMEOUT',
    })).toBe(true)
  })

  it('recognizes mutation evidence stored in diagnostics', () => {
    expect(requiresManualPlatformVerification({
      status: 'manual_required',
      diagnosticsJson: JSON.stringify({ recoveryDecision: 'manual_review_without_refill' }),
    })).toBe(true)
    expect(requiresManualPlatformVerification({
      status: 'manual_required',
      diagnosticsJson: JSON.stringify({ lastStage: 'filling_location' }),
    })).toBe(true)
  })

  it('keeps retry available for a safe pre-fill failure', () => {
    expect(requiresManualPlatformVerification({
      status: 'schedule_failed',
      runtimeStage: 'opening_upload_page',
      failureCode: 'PAGE_LOAD_TIMEOUT',
    })).toBe(false)
  })
})
