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
  successFeedbackKeywords: string[]
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
    successFeedbackKeywords: ['发布成功', '保存成功', '草稿已保存', '已保存至草稿箱', '已保存到草稿箱', '提交成功', '审核中'],
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
    successFeedbackKeywords: ['发布成功', '保存成功', '草稿已保存', '已保存至草稿箱', '已保存到草稿箱', '提交成功', '审核中'],
  },
  {
    platform: 'xiaohongshu',
    editorHosts: ['creator.xiaohongshu.com'],
    titleSelectors: ['[data-geo-fill="title"]', 'input[placeholder*="标题"]', 'textarea[placeholder*="标题"]'],
    contentSelectors: ['[data-geo-fill="content"]', '[contenteditable="true"]', 'textarea[placeholder*="正文"]', 'textarea[placeholder*="内容"]'],
    coverSelectors: ['[data-geo-fill="cover"]', 'input[type="file"]'],
    tagsSelectors: ['[data-geo-fill="tags"]', 'input[placeholder*="标签"]', 'input[placeholder*="话题"]'],
    categorySelectors: ['[data-geo-fill="category"]'],
    publishButtonSelectors: ['[data-geo-publish]'],
    draftButtonSelectors: ['[data-geo-save-draft]'],
    completionButtonTextKeywords: ['发布', '保存草稿', '存草稿'],
    successFeedbackKeywords: ['发布成功', '保存成功', '草稿已保存', '提交成功', '审核中'],
  },
]

export function profileForUrl(url: string): ContentScriptFillProfile | null {
  const host = new URL(url).hostname
  return CONTENT_SCRIPT_PROFILES.find(profile => profile.editorHosts.includes(host)) ?? null
}
