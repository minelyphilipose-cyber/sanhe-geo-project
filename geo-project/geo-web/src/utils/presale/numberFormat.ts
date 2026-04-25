/**
 * 售前报表数值展示统一取整工具。
 *
 * 注意:JS Math.round 对负 .5 和 Java HALF_UP 不一致;
 * 本项目报表数值均为 0-100 正百分比,忽略该差异。
 */
export function toIntRounded(value: number | null | undefined): number {
  if (value == null || Number.isNaN(value)) return 0
  return Math.round(value)
}

export function toIntPercentRounded(value: number | null | undefined): string {
  return `${toIntRounded(value)}%`
}

