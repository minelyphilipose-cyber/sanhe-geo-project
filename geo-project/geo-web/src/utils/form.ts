export function nullableText(value?: string | null): string | null {
  const text = value?.trim() || ''
  return text || null
}

export function isValidMobile(value?: string | null): boolean {
  const text = value?.trim() || ''
  return !text || /^1[3-9]\d{9}$/.test(text)
}

export function isValidEmail(value?: string | null): boolean {
  const text = value?.trim() || ''
  return !text || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text)
}
