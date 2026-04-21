/**
 * generationStatus 枚举到 UI 展示的映射。
 * 列表页、进度页、详情页共用,避免到处散落 switch。
 */

export type GenerationStatus = 'INIT' | 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED'

export interface StatusMeta {
  /** 中文显示名 */
  label: string
  /** el-tag 颜色类型 */
  tagType: 'info' | 'primary' | 'success' | 'warning' | 'danger'
  /** 是否显示 loading 图标(运行中) */
  loading: boolean
}

const META: Record<GenerationStatus, StatusMeta> = {
  INIT: { label: '等待中', tagType: 'info', loading: false },
  QUEUED: { label: '已入队', tagType: 'info', loading: true },
  RUNNING: { label: '生成中', tagType: 'primary', loading: true },
  DONE: { label: '已完成', tagType: 'success', loading: false },
  FAILED: { label: '失败', tagType: 'danger', loading: false }
}

const FALLBACK: StatusMeta = {
  label: '未知',
  tagType: 'info',
  loading: false
}

export function getStatusMeta(status: string | null | undefined): StatusMeta {
  if (!status) return FALLBACK
  return META[status as GenerationStatus] ?? FALLBACK
}

/**
 * 判断是否为"运行中"状态(RUNNING 或 QUEUED 或 INIT)。
 * 列表页点击此类行应跳转进度页,不跳详情页。
 */
export function isInProgress(status: string | null | undefined): boolean {
  return status === 'INIT' || status === 'QUEUED' || status === 'RUNNING'
}

export function isTerminal(status: string | null | undefined): boolean {
  return status === 'DONE' || status === 'FAILED'
}
