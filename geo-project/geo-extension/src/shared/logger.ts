const PREFIX = '[geo-extension]'
const TOKEN_PATTERN = /\b(ext|ft)\.[A-Za-z0-9_\-=]+/g
const SENSITIVE_KEYS = new Set(['cookiesJson', 'cookies', 'token', 'fillToken', 'extensionToken'])
const MAX_DEPTH = 5

export function sanitizeForLog(value: unknown, depth = 0): unknown {
  if (depth > MAX_DEPTH) return '[redacted-depth]'
  if (typeof value === 'string') return value.replace(TOKEN_PATTERN, '[redacted-token]')
  if (Array.isArray(value)) return value.map((item) => sanitizeForLog(item, depth + 1))
  if (!value || typeof value !== 'object') return value

  const sanitized: Record<string, unknown> = {}
  for (const [key, nested] of Object.entries(value)) {
    sanitized[key] = SENSITIVE_KEYS.has(key) ? '[redacted]' : sanitizeForLog(nested, depth + 1)
  }
  return sanitized
}

export const logger = {
  info(message: string, ...args: unknown[]) {
    console.info(PREFIX, message, ...args.map((arg) => sanitizeForLog(arg)))
  },
  warn(message: string, ...args: unknown[]) {
    console.warn(PREFIX, message, ...args.map((arg) => sanitizeForLog(arg)))
  },
  error(message: string, ...args: unknown[]) {
    console.error(PREFIX, message, ...args.map((arg) => sanitizeForLog(arg)))
  },
}
