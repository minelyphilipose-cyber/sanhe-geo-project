import DOMPurify from 'dompurify'

export interface PlatformFillProfile {
  platform: string
  editorHosts: string[]
  cookieDomains: string[]
  titleSelectors: string[]
  contentSelectors: string[]
  coverSelectors: string[]
  tagsSelectors: string[]
  categorySelectors: string[]
  publishButtonSelectors: string[]
}

export const FILL_PROFILES: Record<string, PlatformFillProfile> = {
  toutiao: profile('toutiao', 'mp.toutiao.com', 'toutiao.com'),
  zhihu: profile('zhihu', 'zhuanlan.zhihu.com', 'zhihu.com'),
}

function profile(platform: string, host: string, domain: string): PlatformFillProfile {
  return {
    platform,
    editorHosts: [host],
    cookieDomains: [domain],
    titleSelectors: ['[data-geo-fill="title"]', 'textarea[placeholder*="标题"]', 'input[placeholder*="标题"]'],
    contentSelectors: ['[data-geo-fill="content"]', '[contenteditable="true"]'],
    coverSelectors: ['[data-geo-fill="cover"]', 'input[type="url"][name*="cover"]'],
    tagsSelectors: ['[data-geo-fill="tags"]', 'input[placeholder*="标签"]'],
    categorySelectors: ['[data-geo-fill="category"]', 'input[placeholder*="分类"]'],
    publishButtonSelectors: ['[data-geo-publish]'],
  }
}

export function profileForPlatform(platform: string): PlatformFillProfile {
  const profile = FILL_PROFILES[platform]
  if (!profile) throw new Error(`暂不支持平台 ${platform} 的自动填充`)
  return profile
}

export function profileForUrl(url: string): PlatformFillProfile | null {
  const host = new URL(url).hostname
  return Object.values(FILL_PROFILES).find(profile => profile.editorHosts.includes(host)) ?? null
}

export function isAllowedPublishUrl(platform: string, url: string): boolean {
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'https:' && profileForPlatform(platform).editorHosts.includes(parsed.hostname)
  } catch {
    return false
  }
}

export function sanitizeTitle(value: string | null | undefined): string {
  return (value ?? '').replace(/[<>]/g, '').replace(/\s+/g, ' ').trim().slice(0, 120)
}

export function sanitizeContentHtml(value: string | null | undefined): string {
  return DOMPurify.sanitize(value ?? '', {
    ALLOWED_TAGS: [
      'p', 'br', 'strong', 'em', 'b', 'i', 'ul', 'ol', 'li', 'a', 'img', 'h1', 'h2', 'h3', 'blockquote',
      'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td', 'caption',
      'pre', 'code',
      'details', 'summary',
      'h4', 'h5', 'h6', 'hr', 'span', 'div',
    ],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'target', 'rel', 'colspan', 'rowspan', 'class'],
    ALLOWED_URI_REGEXP: /^(?:(?:https?):|data:image\/(?:png|jpeg|jpg|gif|webp);base64,|[^a-z])/i,
  }).trim()
}
