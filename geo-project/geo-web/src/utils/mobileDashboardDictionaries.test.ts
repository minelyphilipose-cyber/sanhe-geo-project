import { describe, expect, it } from 'vitest'
import { isMobileDashboardAiPlatformVisible } from './mobileDashboardDictionaries'

describe('mobile dashboard AI platform visibility', () => {
  it('hides Yuanbao aliases while keeping supported platforms visible', () => {
    expect(isMobileDashboardAiPlatformVisible('yuanbao')).toBe(false)
    expect(isMobileDashboardAiPlatformVisible('hunyuan')).toBe(false)
    expect(isMobileDashboardAiPlatformVisible('tencent_search_web')).toBe(false)
    expect(isMobileDashboardAiPlatformVisible('doubao')).toBe(true)
    expect(isMobileDashboardAiPlatformVisible('deepseek')).toBe(true)
    expect(isMobileDashboardAiPlatformVisible('tongyi')).toBe(true)
  })
})
