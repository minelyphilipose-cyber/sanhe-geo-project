export function nullableText(value?: string | null): string | null {
  const text = value?.trim() || ''
  return text || null
}
