const ERROR_CATEGORIES = [
  'networkEnableTimeout',
  'cdpDisconnected',
  'extensionInjectionError',
  'pageTimeout',
  'cdpProtocolTimeout',
]

const TASK_THROUGHPUT_COUNTERS = [
  'claimedTotal',
  'executionClaimedTotal',
  'executionStartedTotal',
  'publishCheckClaimedTotal',
  'publishCheckStartedTotal',
  'completedTotal',
  'failedTotal',
]

function emptyCounts() {
  return Object.fromEntries(ERROR_CATEGORIES.map((category) => [category, 0]))
}

export function classifyBrowserRuntimeError(error) {
  const message = String(error?.message || error || '')
  if (/Network\.enable[\s\S]*timed out/i.test(message)) return 'networkEnableTimeout'
  if (/(target|session|connection|websocket|browser)[\s\S]*(closed|disconnected|not connected)/i.test(message)
    || /Protocol error[\s\S]*(Target closed|Session closed)/i.test(message)) {
    return 'cdpDisconnected'
  }
  if (/(chrome\.scripting|content script|script inject|injection failed|inject.*failed)/i.test(message)) {
    return 'extensionInjectionError'
  }
  if (/(navigation|waiting for|page|selector)[\s\S]*(timeout|timed out)/i.test(message)) {
    return 'pageTimeout'
  }
  if (/(protocol error|cdp)[\s\S]*(timeout|timed out)/i.test(message)) {
    return 'cdpProtocolTimeout'
  }
  return null
}

export class BrowserRuntimeErrorCounter {
  constructor() {
    this.total = emptyCounts()
    this.byEnvironment = new Map()
    this.seenErrors = new WeakSet()
  }

  record(error, providerProfileId = null) {
    if (error && typeof error === 'object') {
      if (this.seenErrors.has(error)) return null
      this.seenErrors.add(error)
    }
    const category = classifyBrowserRuntimeError(error)
    if (!category) return null
    this.total[category] += 1
    const profileId = String(providerProfileId || '').trim()
    if (profileId) {
      const counts = this.byEnvironment.get(profileId) || emptyCounts()
      counts[category] += 1
      this.byEnvironment.set(profileId, counts)
    }
    return category
  }

  snapshot(providerProfileId = null) {
    const profileId = String(providerProfileId || '').trim()
    const counts = profileId
      ? (this.byEnvironment.get(profileId) || emptyCounts())
      : this.total
    return {
      ...counts,
      total: Object.values(counts).reduce((sum, value) => sum + value, 0),
    }
  }
}

export class HelperTaskThroughputCounter {
  constructor() {
    this.total = this.#empty()
    this.byEnvironment = new Map()
  }

  increment(counter, providerProfileId = null) {
    if (!TASK_THROUGHPUT_COUNTERS.includes(counter)) {
      throw new Error(`unknown helper task throughput counter: ${counter}`)
    }
    this.total[counter] += 1
    const profileId = String(providerProfileId || '').trim()
    if (profileId) {
      const counts = this.byEnvironment.get(profileId) || this.#empty()
      counts[counter] += 1
      this.byEnvironment.set(profileId, counts)
    }
  }

  snapshot(providerProfileId = null) {
    const profileId = String(providerProfileId || '').trim()
    return {
      ...(profileId ? (this.byEnvironment.get(profileId) || this.#empty()) : this.total),
    }
  }

  #empty() {
    return Object.fromEntries(TASK_THROUGHPUT_COUNTERS.map((counter) => [counter, 0]))
  }
}

export function buildFailedBrowserObservationMetrics({
  context,
  previousMetrics,
  failedProbeDurationMs,
  consecutiveCdpFailures,
  errorCounts,
  taskVolume,
  helperUptimeSeconds,
  observationError,
  observedAt,
}) {
  const lastSuccessfulObservedAt = previousMetrics?.observationStatus === 'ok'
    ? previousMetrics.observedAt
    : previousMetrics?.lastSuccessfulObservedAt || null
  return {
    browserEnvironmentId: context.browserEnvironmentId || null,
    environmentKey: context.environmentKey || null,
    providerProfileId: context.providerProfileId,
    browserSessionEpoch: context.browserSessionEpoch,
    ownerType: context.ownerType || 'unknown',
    totalTargetCount: null,
    managedTargetCount: null,
    operatorTargetCount: null,
    unknownTargetCount: null,
    protectedTargetCount: null,
    cdpProbeLatencyMs: null,
    cdpStepLatencyMs: null,
    browserPageCount: null,
    browserVersion: null,
    processMetrics: null,
    failedProbeDurationMs: Math.max(0, Number(failedProbeDurationMs || 0)),
    consecutiveCdpFailures: Math.max(1, Number(consecutiveCdpFailures || 1)),
    errorCounts,
    taskVolume,
    helperUptimeSeconds,
    circuitState: 'not_implemented',
    lastCleanupAt: null,
    lastCleanupResult: 'observation_only',
    observationStatus: 'failed',
    observationError,
    lastSuccessfulObservedAt,
    observedAt,
  }
}

export function summarizeBrowserProcessMetrics({
  processInfo = [],
  processRows = [],
  previousSample = null,
  observedAtMs = Date.now(),
}) {
  const processTypes = new Map(
    processInfo.map((item) => [Number(item.id), String(item.type || 'unknown')]),
  )
  const normalizedRows = processRows
    .map((row) => ({
      pid: Number(row.Id ?? row.id),
      processName: String(row.ProcessName ?? row.processName ?? ''),
      cpuTimeSeconds: Number(row.CPU ?? row.cpuTimeSeconds ?? 0),
      rssBytes: Number(row.WorkingSet64 ?? row.rssBytes ?? 0),
      handleCount: Number(row.HandleCount ?? row.handleCount ?? 0),
    }))
    .filter((row) => Number.isFinite(row.pid) && row.pid > 0)
  const cpuTimeSeconds = normalizedRows.reduce(
    (sum, row) => sum + (Number.isFinite(row.cpuTimeSeconds) ? row.cpuTimeSeconds : 0),
    0,
  )
  const elapsedSeconds = previousSample
    ? Math.max(0, (observedAtMs - Number(previousSample.observedAtMs || 0)) / 1_000)
    : 0
  const cpuDeltaSeconds = previousSample
    ? Math.max(0, cpuTimeSeconds - Number(previousSample.cpuTimeSeconds || 0))
    : 0
  const cpuPercent = elapsedSeconds > 0
    ? Math.max(0, (cpuDeltaSeconds / elapsedSeconds) * 100)
    : null
  const byType = {}
  for (const row of normalizedRows) {
    const type = processTypes.get(row.pid) || 'unknown'
    const current = byType[type] || { processCount: 0, rssBytes: 0, handleCount: 0 }
    current.processCount += 1
    current.rssBytes += Number.isFinite(row.rssBytes) ? row.rssBytes : 0
    current.handleCount += Number.isFinite(row.handleCount) ? row.handleCount : 0
    byType[type] = current
  }
  return {
    status: normalizedRows.length ? 'ok' : 'unavailable',
    processCount: normalizedRows.length,
    processNames: [...new Set(normalizedRows.map((row) => row.processName).filter(Boolean))],
    rssBytes: normalizedRows.reduce(
      (sum, row) => sum + (Number.isFinite(row.rssBytes) ? row.rssBytes : 0),
      0,
    ),
    handleCount: normalizedRows.reduce(
      (sum, row) => sum + (Number.isFinite(row.handleCount) ? row.handleCount : 0),
      0,
    ),
    cpuTimeSeconds,
    cpuPercent,
    byType,
    sample: { observedAtMs, cpuTimeSeconds },
  }
}
