import { describe, expect, it } from 'vitest'
import { FILL_PROFILES, sanitizeContentHtml } from './fillProfiles'

describe('sanitizeContentHtml', () => {
  it.each([
    '<scr<script>ipt>alert(1)</scr</script>ipt>',
    '<svg onload=alert(1)><circle></circle></svg>',
    '<iframe src="javascript:alert(1)"></iframe>',
    '<img src=x onerror=alert(1)>',
    '<form action="javascript:alert(1)"><button>go</button></form>',
    '<table><tbody><tr><td><script>alert(1)</script>safe</td></tr></tbody></table>',
  ])('removes executable payload %s', (payload) => {
    const sanitized = sanitizeContentHtml(payload).toLowerCase()

    expect(sanitized).not.toContain('script')
    expect(sanitized).not.toContain('onload')
    expect(sanitized).not.toContain('onerror')
    expect(sanitized).not.toContain('javascript:')
    expect(sanitized).not.toContain('<iframe')
    expect(sanitized).not.toContain('<form')
  })

  it('preserves markdown table structure', () => {
    const sanitized = sanitizeContentHtml('<table><thead><tr><th>渠道</th></tr></thead><tbody><tr><td>知乎</td></tr><tr><td>头条</td></tr></tbody></table>')

    expect(sanitized).toContain('<table>')
    expect(sanitized.match(/<tr>/g)).toHaveLength(3)
    expect(sanitized).toContain('<th>渠道</th>')
    expect(sanitized).toContain('<td>知乎</td>')
  })

  it('preserves code block structure and language class', () => {
    const sanitized = sanitizeContentHtml('<pre><code class="language-js">const x = 1;</code></pre>')

    expect(sanitized).toContain('<pre>')
    expect(sanitized).toContain('<code class="language-js">')
    expect(sanitized).toContain('const x = 1;')
  })

  it('preserves details summary structure', () => {
    const sanitized = sanitizeContentHtml('<details><summary>展开</summary><p>内容</p></details>')

    expect(sanitized).toContain('<details>')
    expect(sanitized).toContain('<summary>展开</summary>')
    expect(sanitized).toContain('<p>内容</p>')
  })

  it('preserves common rich markdown structures while stripping image style', () => {
    const sanitized = sanitizeContentHtml(`
      <h4>标题</h4>
      <blockquote><ul><li><strong>A</strong><ol><li>B</li></ol></li></ul></blockquote>
      <p><img src="https://example.com/a.png" alt="a" style="width:100px"></p>
      <hr>
    `)

    expect(sanitized).toContain('<h4>标题</h4>')
    expect(sanitized).toContain('<blockquote>')
    expect(sanitized).toContain('<ol>')
    expect(sanitized).toContain('<img src="https://example.com/a.png" alt="a">')
    expect(sanitized).toContain('<hr>')
    expect(sanitized).not.toContain('style=')
  })

  it('keeps publish selectors explicit without generic submit fallback', () => {
    for (const profile of Object.values(FILL_PROFILES)) {
      expect(profile.publishButtonSelectors).toEqual(['[data-geo-publish]'])
      expect(profile.publishButtonSelectors).not.toContain('button[type="submit"]')
      expect(profile.publishButtonSelectors).not.toContain('button.publish')
      expect(profile.publishButtonSelectors).not.toContain('.publish-button')
    }
  })
})
