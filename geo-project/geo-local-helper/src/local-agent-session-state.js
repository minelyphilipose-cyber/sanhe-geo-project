export function hasRuntimeSessionCredentials(session) {
  return Boolean(session?.sessionId && session?.hmacSecret)
}

export function isRuntimeSessionExpired(session, now = Date.now()) {
  if (!hasRuntimeSessionCredentials(session)) return false
  if (session?.sessionExpiredAt) return true
  const expiresAt = Date.parse(String(session?.expiresAt || ''))
  return Number.isFinite(expiresAt) && expiresAt <= now
}

export function isRuntimeSessionUsable(session, now = Date.now()) {
  return hasRuntimeSessionCredentials(session) && !isRuntimeSessionExpired(session, now)
}

export function isLocalAgentSessionExpiredError(error) {
  return String(error?.message || error || '').toLowerCase().includes('local agent session expired')
}
