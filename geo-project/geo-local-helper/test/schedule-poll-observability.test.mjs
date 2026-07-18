import assert from 'node:assert/strict'
import test from 'node:test'

import {
  preferScheduleClaimBlock,
  schedulePollBlockLogDecision,
} from '../src/schedule-poll-observability.js'

test('specific backend blocker is preserved when another platform reports no due task', () => {
  const selected = preferScheduleClaimBlock('NO_AUTHORIZED_BRAND', 30, 'NO_DUE_TASK', null)

  assert.deepEqual(selected, { reason: 'NO_AUTHORIZED_BRAND', retryAfterSeconds: 30 })
})

test('missing backend reason falls back to no due task at the health boundary', () => {
  const selected = preferScheduleClaimBlock('', null, '', null)

  assert.equal(selected.reason || 'NO_DUE_TASK', 'NO_DUE_TASK')
})

test('repeated blocker logs only after the throttle window', () => {
  const first = schedulePollBlockLogDecision(
    { reason: null, at: 0 },
    { claimed: false, claimBlockedReason: 'HELPER_CAPACITY_FULL' },
    1_000,
    300_000,
  )
  const repeated = schedulePollBlockLogDecision(
    first.state,
    { claimed: false, claimBlockedReason: 'HELPER_CAPACITY_FULL' },
    2_000,
    300_000,
  )
  const afterThrottle = schedulePollBlockLogDecision(
    first.state,
    { claimed: false, claimBlockedReason: 'HELPER_CAPACITY_FULL' },
    301_001,
    300_000,
  )

  assert.equal(first.shouldLog, true)
  assert.equal(repeated.shouldLog, false)
  assert.equal(afterThrottle.shouldLog, true)
})

test('successful claim resets blocker log state', () => {
  const decision = schedulePollBlockLogDecision(
    { reason: 'ADSPOWER_API_DOWN', at: 1_000 },
    { claimed: true },
    2_000,
    300_000,
  )

  assert.deepEqual(decision, {
    shouldLog: false,
    state: { reason: null, at: 0 },
  })
})
