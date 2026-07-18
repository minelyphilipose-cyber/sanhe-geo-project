export function createIdempotencyKey(prefix: string): string {
  const normalizedPrefix = prefix.trim()
  const runtimeCrypto = globalThis.crypto
  const randomPart = runtimeCrypto && typeof runtimeCrypto.randomUUID === 'function'
    ? runtimeCrypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`

  return normalizedPrefix ? `${normalizedPrefix}-${randomPart}` : randomPart
}
