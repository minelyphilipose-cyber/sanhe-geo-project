const KNOWN_ERROR_CODES = [
  'BLACKLIST_HIT',
  'QUOTA_EXCEEDED',
  'INVALID_RESULT_KEYWORDS',
  'COMPARE_CORE_A_REQUIRED',
  'COMPARE_CORE_B_REQUIRED',
  'COMPARE_WORD_REQUIRED',
  'FUNCTION_INDUSTRY_REQUIRED',
] as const

export type ErrorCode = typeof KNOWN_ERROR_CODES[number]

export function parseErrorCode(res: { errorCode?: string; message?: string }) {
  const message = res.message || ''
  if (res.errorCode && KNOWN_ERROR_CODES.includes(res.errorCode as ErrorCode)) {
    return { code: res.errorCode as ErrorCode, text: message }
  }
  for (const code of KNOWN_ERROR_CODES) {
    if (message.startsWith(`${code}:`)) {
      return { code, text: message.slice(code.length + 1).trim() }
    }
  }
  return { code: null as ErrorCode | null, text: message }
}

export const ERROR_CODE_HINTS: Record<ErrorCode, string> = {
  BLACKLIST_HIT: '词条命中黑名单，请修改后重试',
  QUOTA_EXCEEDED: '已达配额上限，如需更多请升级档位',
  INVALID_RESULT_KEYWORDS: '保存的关键词与预览结果不一致，请重新预览',
  COMPARE_CORE_A_REQUIRED: '请填写核心词 A',
  COMPARE_CORE_B_REQUIRED: '请填写核心词 B',
  COMPARE_WORD_REQUIRED: '请选择对比连接词',
  FUNCTION_INDUSTRY_REQUIRED: '请选择行业',
}
