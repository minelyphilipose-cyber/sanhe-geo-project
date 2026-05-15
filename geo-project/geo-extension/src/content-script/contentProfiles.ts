export interface ContentScriptFillProfile {
  platform: string
  editorHosts: string[]
  titleSelectors: string[]
  contentSelectors: string[]
  coverSelectors: string[]
  tagsSelectors: string[]
  categorySelectors: string[]
  publishButtonSelectors: string[]
  draftButtonSelectors: string[]
  completionButtonTextKeywords: string[]
}

const CONTENT_SCRIPT_PROFILES: ContentScriptFillProfile[] = [
  {
    platform: 'toutiao',
    editorHosts: ['mp.toutiao.com'],
    titleSelectors: ['[data-geo-fill="title"]', 'textarea[placeholder*="标题"]', 'input[placeholder*="标题"]'],
    contentSelectors: ['[data-geo-fill="content"]', '[contenteditable="true"]'],
    coverSelectors: ['[data-geo-fill="cover"]', 'input[type="url"][name*="cover"]'],
    tagsSelectors: ['[data-geo-fill="tags"]', 'input[placeholder*="标签"]'],
    categorySelectors: ['[data-geo-fill="category"]', 'input[placeholder*="分类"]'],
    publishButtonSelectors: ['[data-geo-publish]'],
    draftButtonSelectors: ['[data-geo-save-draft]'],
    completionButtonTextKeywords: ['发布', '保存草稿', '保存到草稿', '保存草稿箱', '存草稿'],
  },
  {
    platform: 'zhihu',
    editorHosts: ['zhuanlan.zhihu.com'],
    titleSelectors: ['[data-geo-fill="title"]', 'textarea[placeholder*="标题"]', 'input[placeholder*="标题"]'],
    contentSelectors: ['[data-geo-fill="content"]', '[contenteditable="true"]'],
    coverSelectors: ['[data-geo-fill="cover"]', 'input[type="url"][name*="cover"]'],
    tagsSelectors: ['[data-geo-fill="tags"]', 'input[placeholder*="标签"]'],
    categorySelectors: ['[data-geo-fill="category"]', 'input[placeholder*="分类"]'],
    publishButtonSelectors: ['[data-geo-publish]'],
    draftButtonSelectors: ['[data-geo-save-draft]'],
    completionButtonTextKeywords: ['发布', '保存草稿', '保存到草稿', '保存草稿箱', '存草稿'],
  },
]

export function profileForUrl(url: string): ContentScriptFillProfile | null {
  const host = new URL(url).hostname
  return CONTENT_SCRIPT_PROFILES.find(profile => profile.editorHosts.includes(host)) ?? null
}
