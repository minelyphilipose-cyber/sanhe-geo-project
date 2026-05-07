import { describe, expect, it } from 'vitest'
import { ExtensionApiError } from './api'
import { friendlyErrorMessage, messageForErrorCode } from './errorMessages'

describe('extension error messages', () => {
  it('maps extension error code segment to user friendly messages', () => {
    for (let code = 70001; code <= 70016; code += 1) {
      expect(messageForErrorCode(code)).not.toBe('请求失败，请稍后重试。')
    }
    expect(messageForErrorCode(70008)).toContain('绑定码无效')
    expect(messageForErrorCode(70009)).toContain('频繁')
    expect(messageForErrorCode(70015)).toContain('重新选择')
  })

  it('converts api errors to user friendly text', () => {
    expect(friendlyErrorMessage(new ExtensionApiError(400, 70008, 'raw backend text'))).toContain('绑定码无效')
    expect(friendlyErrorMessage(new ExtensionApiError(401, 70002, 'raw backend text'))).toContain('扩展弹窗')
    expect(friendlyErrorMessage(new ExtensionApiError(500, undefined, 'raw backend text'))).toBe('请求失败，请稍后重试。')
  })
})
