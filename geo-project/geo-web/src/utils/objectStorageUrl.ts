const MINIO_BUCKET_PATH_PATTERN = /\/geo-files\//

export function normalizeObjectStorageUrl(value?: string | null): string {
  const raw = String(value || '').trim()
  if (!raw) return ''
  if (raw.startsWith('/oss/')) return raw
  if (raw.startsWith('/geo-files/')) return `/oss${raw}`
  if (raw.startsWith('geo-files/')) return `/oss/${raw}`

  try {
    const url = new URL(raw)
    if (MINIO_BUCKET_PATH_PATTERN.test(url.pathname)) {
      return `/oss${url.pathname}${url.search}${url.hash}`
    }
  } catch {
    return raw
  }

  return raw
}
