/**
 * RFC3339 时间格式化工具。后端返回的 createdAt / updatedAt 等字段是
 * RFC3339 with offset(如 "2026-04-18T18:00:00+08:00"),前端统一格式化。
 */

/**
 * 格式化为 "YYYY-MM-DD HH:mm"。null/空串返回 "—"。
 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const d = new Date(value)
  if (isNaN(d.getTime())) return '—'
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day} ${hh}:${mm}`
}

/**
 * 格式化为 "YYYY-MM-DD"。
 */
export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const d = new Date(value)
  if (isNaN(d.getTime())) return '—'
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/**
 * 把 el-date-picker 选出的 Date[] 转成后端需要的 RFC3339 with +08:00。
 * daterange 模式下 start 默认 00:00:00, end 默认 23:59:59。
 */
export function toRfc3339Range(range: [Date, Date] | null): {
  startAt?: string
  endAt?: string
} {
  if (!range || range.length !== 2) return {}
  const [start, end] = range
  return {
    startAt: toRfc3339WithBeijing(start, '00:00:00'),
    endAt: toRfc3339WithBeijing(end, '23:59:59')
  }
}

function toRfc3339WithBeijing(d: Date, timeStr: string): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}T${timeStr}+08:00`
}
