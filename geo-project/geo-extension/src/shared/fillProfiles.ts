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
  xiaohongshu: profile('xiaohongshu', 'creator.xiaohongshu.com', 'xiaohongshu.com'),
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
