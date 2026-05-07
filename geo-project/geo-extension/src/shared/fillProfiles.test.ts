import { describe, expect, it } from 'vitest'
import { sanitizeContentHtml } from './fillProfiles'

describe('sanitizeContentHtml', () => {
  it.each([
    '<scr<script>ipt>alert(1)</scr</script>ipt>',
    '<svg onload=alert(1)><circle></circle></svg>',
    '<iframe src="javascript:alert(1)"></iframe>',
    '<img src=x onerror=alert(1)>',
    '<form action="javascript:alert(1)"><button>go</button></form>',
  ])('removes executable payload %s', (payload) => {
    const sanitized = sanitizeContentHtml(payload).toLowerCase()

    expect(sanitized).not.toContain('script')
    expect(sanitized).not.toContain('onload')
    expect(sanitized).not.toContain('onerror')
    expect(sanitized).not.toContain('javascript:')
    expect(sanitized).not.toContain('<iframe')
    expect(sanitized).not.toContain('<form')
  })
})
