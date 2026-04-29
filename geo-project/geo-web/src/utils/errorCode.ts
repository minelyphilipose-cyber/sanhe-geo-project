const KNOWN_ERROR_CODES = [
  'BLACKLIST_HIT',
  'QUOTA_EXCEEDED',
  'INVALID_RESULT_KEYWORDS',
  'COMPARE_CORE_A_REQUIRED',
  'COMPARE_CORE_B_REQUIRED',
  'COMPARE_WORD_REQUIRED',
  'FUNCTION_INDUSTRY_REQUIRED',
  'LLM_GENERATE_FAILED',
  'LLM_GENERATE_INSUFFICIENT',
  'LLM_QUESTION_TAMPERED',
  'COUNT_LESS_THAN_LLM',
  'LLM_SEED_INVALID_COUNT',
  'LLM_SEED_TOO_LONG',
  'LLM_EXCEED_COUNT',
  'LLM_TARGET_COUNT_INVALID',
  'KEYWORD_GROUP_NAME_DUPLICATE',
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
  LLM_GENERATE_FAILED: 'AI 扩写失败，请稍后重试',
  LLM_GENERATE_INSUFFICIENT: 'AI 生成问题数量不足，请调整种子词或重试',
  LLM_QUESTION_TAMPERED: '检测到大模型问题异常，请重新生成',
  COUNT_LESS_THAN_LLM: '入库数小于已生成 LLM 问题数，请调整',
  LLM_SEED_INVALID_COUNT: '种子词不能为空',
  LLM_SEED_TOO_LONG: '种子词长度不能超过 10 字',
  LLM_EXCEED_COUNT: '累积 AI 问题数将超过预览总数，请调整',
  LLM_TARGET_COUNT_INVALID: '单次生成数量必须在 5-50 条之间',
  KEYWORD_GROUP_NAME_DUPLICATE: '该客户下已存在同名词组，请换个名字',
}
