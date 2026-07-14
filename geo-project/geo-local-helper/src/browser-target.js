export function selectUploadTargetPage(pages, options = {}) {
  const candidates = Array.isArray(pages) ? pages.filter(Boolean) : []
  const platform = String(options.platform || '').trim().toLowerCase()
  const targetPageUrl = String(options.targetPageUrl || '').trim()
  const browserTargetId = String(options.browserTargetId || '').trim()

  if (browserTargetId) {
    const exactTarget = candidates.find((page) => puppeteerPageTargetId(page) === browserTargetId) || null
    if (exactTarget) return isPlatformUploadPage(exactTarget.url(), platform) ? exactTarget : null
  }

  if (targetPageUrl) {
    const exactUrlMatches = candidates.filter((page) => sameBrowserPageUrl(page.url(), targetPageUrl))
    if (exactUrlMatches.length === 1) return exactUrlMatches[0]
    if (platform === 'douyin' && exactUrlMatches.length > 1) return null

    const pathMatches = candidates.filter((page) => sameBrowserPagePath(page.url(), targetPageUrl))
    if (pathMatches.length === 1) return pathMatches[0]
    if (platform === 'douyin' && pathMatches.length > 1) return null
  }

  const platformMatches = candidates.filter((page) => isPlatformUploadPage(page.url(), platform))
  if (platform === 'douyin') return platformMatches.length === 1 ? platformMatches[0] : null
  return platformMatches[0] || candidates.find((page) => page.url().includes(platform)) || null
}

export function puppeteerPageTargetId(page) {
  const target = page?.target?.()
  return String(target?._targetId || '')
}

export function describeUploadPageCandidates(pages) {
  return (Array.isArray(pages) ? pages : []).map((page) => ({
    targetId: puppeteerPageTargetId(page),
    url: String(page?.url?.() || ''),
  }))
}

function isPlatformUploadPage(urlValue, platform) {
  const url = String(urlValue || '')
  if (platform === 'zhihu') return url.includes('zhihu.com') && (url.includes('/write') || url.includes('/edit'))
  if (platform === 'toutiao') return url.includes('mp.toutiao.com') && url.includes('/graphic/publish')
  if (platform === 'douyin') {
    return url.includes('creator.douyin.com')
      && (url.includes('/creator-micro/content/upload') || url.includes('/creator-micro/content/post/article'))
  }
  if (platform === 'baijiahao') return url.includes('baijiahao.baidu.com') && url.includes('/builder/rc/edit')
  return url.includes(platform)
}

function sameBrowserPageUrl(left, right) {
  try {
    const leftUrl = new URL(String(left || ''))
    const rightUrl = new URL(String(right || ''))
    leftUrl.hash = ''
    rightUrl.hash = ''
    return leftUrl.href === rightUrl.href
  } catch {
    return String(left || '') === String(right || '')
  }
}

function sameBrowserPagePath(left, right) {
  try {
    const leftUrl = new URL(String(left || ''))
    const rightUrl = new URL(String(right || ''))
    return leftUrl.origin === rightUrl.origin && leftUrl.pathname === rightUrl.pathname
  } catch {
    return false
  }
}
