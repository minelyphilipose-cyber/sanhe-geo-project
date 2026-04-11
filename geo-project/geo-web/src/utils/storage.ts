/**
 * localStorage 封装
 * ⚠️ 仅用于 UI 偏好设置（侧边栏状态、主题色等）
 * ⚠️ 绝不用于存储业务数据、用户信息、token
 */

const PREFIX = 'geo_ui_'

export function getUIPreference<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(PREFIX + key)
    if (raw === null) return fallback
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

export function setUIPreference(key: string, value: any): void {
  try {
    localStorage.setItem(PREFIX + key, JSON.stringify(value))
  } catch {
    // localStorage 满了或被禁用，静默忽略
  }
}
