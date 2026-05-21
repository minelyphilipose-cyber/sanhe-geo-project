import dayjs from 'dayjs'

/**
 * 金额: 分 → 元 (带千分位)
 */
export function formatMoney(cents: number | null | undefined): string {
  if (cents == null) return '—'
  return (cents / 100).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/**
 * 日期格式化
 */
export function formatDate(date: string | null | undefined, fmt = 'YYYY-MM-DD'): string {
  if (!date) return '—'
  return dayjs(date).format(fmt)
}

export function formatDateTime(date: string | null | undefined): string {
  return formatDate(date, 'YYYY-MM-DD HH:mm')
}

export function formatDateTimeSeconds(date: string | null | undefined): string {
  return formatDate(date, 'YYYY-MM-DD HH:mm:ss')
}

/**
 * 百分比
 * // 非 presale 使用:presale 报表统一走 @/utils/presale/numberFormat
 */
export function formatPercent(value: number | null | undefined, digits = 1): string {
  if (value == null) return '—'
  return `${(value * 100).toFixed(digits)}%`
}

/**
 * 截断文本
 */
export function truncate(text: string, maxLen: number): string {
  if (text.length <= maxLen) return text
  return text.slice(0, maxLen) + '…'
}
