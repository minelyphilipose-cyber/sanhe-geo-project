import { describe, expect, it } from 'vitest'

import { isLocalAgentSessionUsable, localAgentSessionStatusLabel } from './localAgentSessionState'

const NOW = Date.parse('2026-08-10T10:00:00+08:00')

describe('local agent session state', () => {
  it('treats a future active session as usable', () => {
    const session = { id: 1, status: 'active', expiresAt: '2026-08-11T10:00:00+08:00' }

    expect(isLocalAgentSessionUsable(session, NOW)).toBe(true)
    expect(localAgentSessionStatusLabel(session, NOW)).toBe('已绑定')
  })

  it('never presents an expired active row as usable', () => {
    const session = { id: 1, status: 'active', expiresAt: '2026-08-09T10:00:00+08:00' }

    expect(isLocalAgentSessionUsable(session, NOW)).toBe(false)
    expect(localAgentSessionStatusLabel(session, NOW)).toBe('已过期')
  })

  it('requires an explicit expiration time', () => {
    expect(isLocalAgentSessionUsable({ id: 1, status: 'active' }, NOW)).toBe(false)
  })
})
