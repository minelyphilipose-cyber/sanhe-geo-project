export function preferScheduleClaimBlock(
  currentReason,
  currentRetryAfterSeconds,
  nextReason,
  nextRetryAfterSeconds,
) {
  const current = String(currentReason || '').trim()
  const next = String(nextReason || '').trim()
  if (!next) return { reason: current, retryAfterSeconds: currentRetryAfterSeconds }
  if (current && current !== 'NO_DUE_TASK' && next === 'NO_DUE_TASK') {
    return { reason: current, retryAfterSeconds: currentRetryAfterSeconds }
  }
  return {
    reason: next,
    retryAfterSeconds: Number(nextRetryAfterSeconds) || null,
  }
}

export function schedulePollBlockLogDecision(previous, result, now, throttleMs) {
  if (result?.claimed) {
    return { shouldLog: false, state: { reason: null, at: 0 } }
  }
  const reason = String(result?.claimBlockedReason || 'NO_DUE_TASK').trim() || 'NO_DUE_TASK'
  const state = previous || { reason: null, at: 0 }
  if (reason === 'NO_DUE_TASK') {
    return { shouldLog: false, state }
  }
  if (state.reason === reason && now - state.at < throttleMs) {
    return { shouldLog: false, state }
  }
  return { shouldLog: true, state: { reason, at: now } }
}
