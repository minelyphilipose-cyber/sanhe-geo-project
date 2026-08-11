import type { LocalAgentSession } from '@/api/localAgent'

export function isLocalAgentSessionUsable(session?: LocalAgentSession | null, now = Date.now()) {
  if (!session || session.status !== 'active' || !session.expiresAt) return false
  const expiresAt = Date.parse(session.expiresAt)
  return Number.isFinite(expiresAt) && expiresAt > now
}

export function localAgentSessionStatusLabel(session: LocalAgentSession, now = Date.now()) {
  if (session.status !== 'active') return session.status
  return isLocalAgentSessionUsable(session, now) ? '已绑定' : '已过期'
}
