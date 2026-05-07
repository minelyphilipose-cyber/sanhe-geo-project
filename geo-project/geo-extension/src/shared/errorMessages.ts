import { ExtensionApiError } from './api'

const ERROR_MESSAGES: Record<number, string> = {
  70001: '请求参数不正确，请检查后重试。',
  70002: '扩展登录已失效，请重新绑定。',
  70003: '当前账号没有执行该操作的权限。',
  70004: '扩展会话不存在，请重新绑定。',
  70005: '扩展版本过低，请升级后再使用。',
  70006: '任务令牌无效，请刷新任务后重试。',
  70007: '任务令牌已使用或已过期，请重新领取任务。',
  70008: '绑定码无效或已过期，请在后台重新生成。',
  70009: '绑定尝试过于频繁，请稍后再试。',
  70010: '扩展服务暂时不可用，请稍后重试。',
  70011: '任务不存在或已被回收。',
  70012: '任务状态已变化，请刷新任务。',
  70013: '操作过于频繁，请稍后再试。',
  70014: '捕获凭证前需要确认授权。',
  70015: '账号与品牌不匹配，请刷新账号列表。',
  70016: '本次确认已使用，请重新确认后再试。',
}

export function messageForErrorCode(code: number | undefined): string {
  return code === undefined ? '请求失败，请稍后重试。' : (ERROR_MESSAGES[code] ?? '请求失败，请稍后重试。')
}

export function friendlyErrorMessage(error: unknown): string {
  if (error instanceof ExtensionApiError) {
    if (error.status === 401) return '扩展登录已失效，请重新绑定。'
    return messageForErrorCode(error.code)
  }
  return error instanceof Error ? error.message : '请求失败，请稍后重试。'
}
