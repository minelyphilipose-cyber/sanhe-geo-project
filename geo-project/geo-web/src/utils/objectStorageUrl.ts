const MINIO_BUCKET_PATH_PATTERN = /\/geo-files\//
const OSS_BUCKET_PATH_PATTERN = /\/oss\/geo-files\//

export function normalizeObjectStorageUrl(value?: string | null): string {
  const raw = String(value || '').trim()
  if (!raw) return ''
  if (raw.startsWith('/oss/oss/')) return raw.replace(/^\/oss\/oss\//, '/oss/')
  if (raw.startsWith('/oss/')) return raw
  if (raw.startsWith('/geo-files/')) return `/oss${raw}`
  if (raw.startsWith('geo-files/')) return `/oss/${raw}`

  try {
    const url = new URL(raw)
    if (OSS_BUCKET_PATH_PATTERN.test(url.pathname)) {
      return `${url.pathname}${url.search}${url.hash}`
    }
    if (MINIO_BUCKET_PATH_PATTERN.test(url.pathname)) {
      return raw
    }
  } catch {
    return raw
  }

  return raw
}
