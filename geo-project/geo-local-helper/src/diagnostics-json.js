export function stringifyBoundedDiagnostics(value, maxLength) {
  const limit = Math.max(Number(maxLength) || 0, 128)
  const raw = safeStringify(value)
  if (raw.length <= limit) return raw

  const envelope = {
    truncated: true,
    originalLength: raw.length,
    preview: '',
  }
  let low = 0
  let high = raw.length
  let best = JSON.stringify(envelope)
  while (low <= high) {
    const middle = Math.floor((low + high) / 2)
    const candidate = JSON.stringify({ ...envelope, preview: raw.slice(0, middle) })
    if (candidate.length <= limit) {
      best = candidate
      low = middle + 1
    } else {
      high = middle - 1
    }
  }
  return best
}

function safeStringify(value) {
  const seen = new WeakSet()
  try {
    return JSON.stringify(value, (_key, item) => {
      if (typeof item === 'bigint') return item.toString()
      if (!item || typeof item !== 'object') return item
      if (seen.has(item)) return '[Circular]'
      seen.add(item)
      return item
    }) || '{}'
  } catch (error) {
    return JSON.stringify({
      serializationFailed: true,
      error: error instanceof Error ? error.message : String(error || 'unknown error'),
    })
  }
}
