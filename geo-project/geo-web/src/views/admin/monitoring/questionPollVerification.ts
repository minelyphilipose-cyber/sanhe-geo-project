import type { ManualQuestionPollBatchView } from '@/api/dispatch'

const TERMINAL_BATCH_STATUSES = new Set(['finished', 'finished_with_failures', 'failed'])

export function isTerminalBatchStatus(status?: string | null): boolean {
  return TERMINAL_BATCH_STATUSES.has(String(status || '').toLowerCase())
}

export function estimateLogicalResults(platformCount: number, questionLimit: number): number {
  return Math.max(0, platformCount) * Math.max(0, questionLimit)
}

export function estimateMaxProviderCalls(platformCount: number, questionLimit: number): number {
  return estimateLogicalResults(platformCount, questionLimit) * 2
}

export function batchProgress(batch?: ManualQuestionPollBatchView | null): number {
  if (!batch?.shardCount) return 0
  return Math.min(100, Math.round((batch.terminalShardCount / batch.shardCount) * 100))
}

export function createClientRequestId(): string {
  const runtimeCrypto = globalThis.crypto
  if (runtimeCrypto && typeof runtimeCrypto.randomUUID === 'function') {
    return runtimeCrypto.randomUUID()
  }
  const bytes = new Uint8Array(16)
  if (runtimeCrypto?.getRandomValues) {
    runtimeCrypto.getRandomValues(bytes)
  } else {
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Math.floor(Math.random() * 256)
    }
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export function safeVerificationSourceUrl(value?: string | null): string {
  if (!value) return ''
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : ''
  } catch {
    return ''
  }
}
