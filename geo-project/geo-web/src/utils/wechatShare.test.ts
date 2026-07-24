import { describe, expect, it } from 'vitest'
import {
  buildMobileDashboardDocumentTitle,
  classifyWechatSdkError,
  isWechatBrowser,
  resolveWechatSignatureUrl,
  stripUrlFragment,
} from './wechatShare'

describe('wechatShare utilities', () => {
  it('uses the project brand instead of the platform brand in the dashboard title', () => {
    expect(buildMobileDashboardDocumentTitle('移动数据看板', ' 华为鸿蒙智家 '))
      .toBe('移动数据看板 | 华为鸿蒙智家')
    expect(buildMobileDashboardDocumentTitle('移动数据看板', ''))
      .toBe('移动数据看板')
  })

  it('recognizes WeChat but excludes WeCom', () => {
    expect(isWechatBrowser('Mozilla MicroMessenger/8.0')).toBe(true)
    expect(isWechatBrowser('Mozilla MicroMessenger/8.0 wxwork/4.1')).toBe(false)
  })

  it('uses the iOS entry URL and the Android current URL', () => {
    expect(resolveWechatSignatureUrl(
      'https://www.huanjingaigeo.com/m/CODE/monitor',
      'https://www.huanjingaigeo.com/m/CODE',
      'iPhone MicroMessenger/8.0',
    )).toBe('https://www.huanjingaigeo.com/m/CODE')
    expect(resolveWechatSignatureUrl(
      'https://www.huanjingaigeo.com/m/CODE/monitor',
      'https://www.huanjingaigeo.com/m/CODE',
      'Android MicroMessenger/8.0',
    )).toBe('https://www.huanjingaigeo.com/m/CODE/monitor')
  })

  it('removes fragments without dropping query parameters', () => {
    expect(stripUrlFragment('https://example.test/page?a=1#part'))
      .toBe('https://example.test/page?a=1')
  })

  it('maps raw SDK messages to a sanitized code', () => {
    expect(classifyWechatSdkError('config:invalid signature')).toBe('invalid_signature')
    expect(classifyWechatSdkError('config:permission denied')).toBe('permission_denied')
    expect(classifyWechatSdkError('something unexpected')).toBe('sdk_error')
  })
})
