import assert from 'node:assert/strict'
import test from 'node:test'

import {
  hasRuntimeSessionCredentials,
  isLocalAgentSessionExpiredError,
  isRuntimeSessionExpired,
  isRuntimeSessionUsable,
} from '../src/local-agent-session-state.js'

const SESSION = {
  sessionId: 23,
  hmacSecret: 'secret',
  expiresAt: '2026-08-09T09:13:17+08:00',
}

test('expired credentials are not presented as a usable pairing', () => {
  const now = Date.parse('2026-08-10T10:00:00+08:00')

  assert.equal(hasRuntimeSessionCredentials(SESSION), true)
  assert.equal(isRuntimeSessionExpired(SESSION, now), true)
  assert.equal(isRuntimeSessionUsable(SESSION, now), false)
})

test('backend invalidation overrides a future local expiration', () => {
  const session = {
    ...SESSION,
    expiresAt: '2026-09-01T09:13:17+08:00',
    sessionExpiredAt: '2026-08-10T10:00:00+08:00',
  }

  assert.equal(isRuntimeSessionUsable(session, Date.parse('2026-08-10T10:00:01+08:00')), false)
})

test('legacy credentials without an expiration can contact the backend for reconciliation', () => {
  const session = { sessionId: 23, hmacSecret: 'secret' }

  assert.equal(isRuntimeSessionUsable(session), true)
})

test('recognizes the backend session expiration response', () => {
  assert.equal(isLocalAgentSessionExpiredError(new Error('local agent session expired')), true)
  assert.equal(isLocalAgentSessionExpiredError(new Error('local helper signature invalid')), false)
})
