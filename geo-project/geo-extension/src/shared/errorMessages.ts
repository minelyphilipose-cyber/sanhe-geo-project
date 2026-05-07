import { ExtensionApiError } from './api'

const ERROR_MESSAGES: Record<number, string> = {
  70001: '操作信息不完整。请刷新扩展弹窗后重试。',
  70002: '扩展登录已失效。请打开扩展弹窗重新绑定。',
  70003: '你没有这个品牌或账号的操作权限，请联系管理员开通权限。',
  70004: '扩展登录记录已失效。请打开扩展弹窗重新绑定。',
  70005: '当前扩展版本过旧，请更新扩展后再继续。',
  70006: '任务授权已失效。请刷新任务列表后重新点击任务。',
  70007: '这个任务授权已用过或已过期。请刷新任务列表后重新领取。',
  70008: '绑定码无效或已过期，请在后台重新生成绑定码。',
  70009: '绑定尝试太频繁，请稍后再试。',
  70010: '扩展服务暂时不可用，请稍后重试；若持续出现请联系管理员。',
  70011: '任务不存在或已被系统回收，请刷新任务列表。',
  70012: '任务状态已变化，请刷新任务列表后确认最新状态。',
  70013: '操作太频繁，系统正在保护任务状态，请稍后再试。',
  70014: '捕获凭证前需要你先确认授权。',
  70015: '所选账号不属于当前品牌，请刷新账号列表后重新选择。',
  70016: '本次确认已使用，请重新点击确认后再试。',
}

export function messageForErrorCode(code: number | undefined): string {
  return code === undefined ? '请求失败，请稍后重试。' : (ERROR_MESSAGES[code] ?? '请求失败，请稍后重试。')
}

export function friendlyErrorMessage(error: unknown): string {
  if (error instanceof ExtensionApiError) {
    if (error.status === 401) return '扩展登录已失效。请打开扩展弹窗重新绑定。'
    return messageForErrorCode(error.code)
  }
  return error instanceof Error ? error.message : '请求失败，请稍后重试。'
}
