import { describe, expect, it } from 'vitest'
import {
  groupSelfMediaSchedulePlanFailureBatches,
  requiresManualPlatformVerification,
} from './selfMediaScheduleRetry'

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

  it('groups retryable plan failures by project and target month', () => {
    expect(groupSelfMediaSchedulePlanFailureBatches([
      { projectId: 7, projectName: '项目甲', status: 'generation_failed', plannedPublishAt: '2026-09-01T10:00:00' },
      { projectId: 7, projectName: '项目甲', status: 'schedule_failed', plannedPublishAt: '2026-09-02T10:00:00' },
      { projectId: 7, projectName: '项目甲', status: 'generation_failed', plannedExecutionAt: '2026-10-01T06:00:00' },
      { projectId: 7, projectName: '项目甲', status: 'scheduled', plannedPublishAt: '2026-09-03T10:00:00' },
    ])).toEqual([
      { key: '7-2026-09', projectId: 7, projectName: '项目甲', targetMonth: '2026-09', visibleFailureCount: 2 },
      { key: '7-2026-10', projectId: 7, projectName: '项目甲', targetMonth: '2026-10', visibleFailureCount: 1 },
    ])
  })
})
