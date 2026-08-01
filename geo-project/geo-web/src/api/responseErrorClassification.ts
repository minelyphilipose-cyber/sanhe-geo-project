export function isServerErrorCode(code: unknown): boolean {
  return typeof code === 'number' && code >= 500 && code < 600
}
