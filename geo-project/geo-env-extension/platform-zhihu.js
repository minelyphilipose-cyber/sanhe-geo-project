;(function installZhihuPlatform(global) {
  const HOME_URL = 'https://www.zhihu.com/'
  const CREATOR_CENTER_URL = 'https://www.zhihu.com/creator/manage/creation/article'
  const ORGANIZATION_VERIFY_URL = 'https://www.zhihu.com/organization/verify/levelup'
  const WRITE_URL = 'https://zhuanlan.zhihu.com/write'

  const RETRYABLE_FAILURE_CODES = new Set([
    'ZHIHU_DRAFT_LOADING',
    'ZHIHU_PUBLISH_NOT_SUBMITTED',
    'ZHIHU_PUBLISH_NOT_CONFIRMED',
    'ZHIHU_COVER_UPLOAD_TIMEOUT',
    'PAGE_LOAD_TIMEOUT',
    'EDITOR_NOT_READY',
  ])

  function normalizePlatform(value) {
    return String(value || '').trim().toLowerCase()
  }

  function classifyFailureCode(message, platform) {
    const text = String(message || '')
    const explicit = text.match(/^([A-Z0-9_]{3,80})[：:]/)?.[1]
    if (explicit) return explicit
    if (text.includes('知乎平台适配器未加载')) return 'ZHIHU_ADAPTER_NOT_LOADED'
    if (normalizePlatform(platform) !== 'zhihu' && !text.includes('知乎') && !text.includes('草稿加载中')) return ''
    if (text.includes('草稿加载中') || text.includes('草稿加载未完成') || text.includes('发布被草稿加载阻塞')) return 'ZHIHU_DRAFT_LOADING'
    if (text.includes('发布后未检测到完成状态')) return 'ZHIHU_PUBLISH_NOT_CONFIRMED'
    if (text.includes('封面填充后未检测到封面图片')) return 'ZHIHU_COVER_UPLOAD_NOT_CONFIRMED'
    if (text.includes('封面上传入口未找到')) return 'ZHIHU_COVER_UPLOAD_ENTRY_NOT_FOUND'
    if (text.includes('等待知乎封面图片上传完成超时') || text.includes('封面图片上传完成超时')) return 'ZHIHU_COVER_UPLOAD_TIMEOUT'
    if (text.includes('封面文件选项未找到')) return 'ZHIHU_COVER_SELECTION_FAILED'
    if (text.includes('封面文件确认按钮未可用')) return 'ZHIHU_COVER_DIALOG_NOT_READY'
    if (text.includes('发布按钮未找到')) return 'ZHIHU_PUBLISH_BUTTON_NOT_FOUND'
    if (text.includes('账号一致性校验失败')) return 'ACCOUNT_MISMATCH'
    return 'ZHIHU_FILL_FAILED'
  }

  function isPublishedArticleUrl(value) {
    try {
      const url = new URL(value)
      if (url.pathname.includes('/write') || url.pathname.endsWith('/edit')) return false
      if (url.hostname === 'zhuanlan.zhihu.com') return /^\/p\/[^/]+/.test(url.pathname)
      if (url.hostname !== 'www.zhihu.com' && url.hostname !== 'zhihu.com') return false
      return /^\/p\/[^/]+/.test(url.pathname) || /^\/article\/[^/]+/.test(url.pathname) || /^\/question\/[^/]+\/answer\/[^/]+/.test(url.pathname)
    } catch {
      return false
    }
  }

  function normalizePublishedUrl(value, baseUrl) {
    const raw = String(value || '').trim()
    if (!raw) return ''
    try {
      const url = baseUrl ? new URL(raw, baseUrl) : new URL(raw)
      const path = normalizedPublishedPath(url.pathname)
      if (!path) return url.toString()
      url.pathname = path
      url.search = ''
      url.hash = ''
      return url.toString()
    } catch {
      return raw.replace(/\/edit(?:[?#].*)?$/, '')
    }
  }

  function normalizedPublishedPath(pathname) {
    const zhuanlanMatch = String(pathname || '').match(/^\/p\/([^/]+)/)
    if (zhuanlanMatch) return `/p/${zhuanlanMatch[1]}`
    const articleMatch = String(pathname || '').match(/^\/article\/([^/]+)/)
    if (articleMatch) return `/article/${articleMatch[1]}`
    const answerMatch = String(pathname || '').match(/^\/question\/([^/]+)\/answer\/([^/]+)/)
    if (answerMatch) return `/question/${answerMatch[1]}/answer/${answerMatch[2]}`
    return ''
  }

  function matchPublishedTitle(expectedTitle, pageTitle, pageText) {
    const expected = normalizeTitleText(expectedTitle)
    const actual = normalizeTitleText(pageTitle)
    if (!expected) return { expected: '', actual, matched: true, method: 'no_expected_title' }
    if (actual && (actual === expected || actual.includes(expected) || expected.includes(actual))) {
      return { expected, actual, matched: true, method: 'page_title' }
    }
    const text = normalizeTitleText(pageText || '')
    return {
      expected,
      actual,
      matched: Boolean(text && text.includes(expected)),
      method: 'page_text',
    }
  }

  function normalizeTitleText(value) {
    return String(value || '')
      .replace(/\s+/g, '')
      .replace(/[-_—|].*知乎.*/i, '')
      .replace(/^写文章/, '')
      .trim()
  }

  function collectProfileHref(href, accountIds, profileUrls, baseUrl) {
    const raw = String(href || '').trim()
    if (!raw) return false
    try {
      const url = baseUrl ? new URL(raw, baseUrl) : new URL(raw)
      const token = url.pathname.match(/^\/(?:people|org)\/([^/?#]+)/)?.[1]
      if (!token) return false
      const normalizedToken = normalizeAccountId(token)
      if (normalizedToken && accountIds?.add) accountIds.add(normalizedToken)
      url.pathname = url.pathname.startsWith('/org/') ? `/org/${token}` : `/people/${token}`
      url.search = ''
      url.hash = ''
      if (profileUrls?.add) profileUrls.add(url.toString())
      return true
    } catch {
      const token = raw.match(/\/(?:people|org)\/([^/?#]+)/)?.[1]
      const normalizedToken = normalizeAccountId(token)
      if (normalizedToken && accountIds?.add) {
        accountIds.add(normalizedToken)
        return true
      }
      return false
    }
  }

  function normalizeAccountId(value) {
    const text = String(value || '').trim()
    if (!text) return ''
    try {
      return decodeURIComponent(text)
    } catch {
      return text
    }
  }

  function isLikelyAccountName(value) {
    const text = normalizeAccountName(value)
    if (text.length < 2 || text.length > 60) return false
    if (/^(写文章|发布文章|创作|开始写作|首页|会员|消息|私信|设置|退出|退出登录|知乎|知乎创作助手)$/.test(text)) return false
    if (/^[\w.-]{2,60}$/.test(text)) return true
    return /[\u4e00-\u9fa5]/.test(text) && !/[，。！？、]/.test(text)
  }

  function normalizeAccountName(value) {
    let text = String(value || '')
      .replace(/\s+/g, ' ')
      .replace(/[\u200b-\u200f\uFEFF]/g, '')
      .trim()
    const homepageMatch = text.match(/^点击打开(.+?)的主页$/)
    if (homepageMatch?.[1]) text = homepageMatch[1].trim()
    return text
  }

  function isRetryableFailureCode(code) {
    return RETRYABLE_FAILURE_CODES.has(String(code || '').trim())
  }

  function createPublishOptionsAdapter(deps = {}) {
    return {
      platform: 'zhihu',
      fillPublishOptions: (payload, fillProfile) => fillPublishOptions(payload, fillProfile, deps),
    }
  }

  function createDomAdapter(deps = {}) {
    return {
      fillCover: (coverImageUrl, platform) => fillCover(coverImageUrl, platform, deps),
      hasCoverImage: () => hasCoverImage(deps),
      waitForCoverUploadedOrChooser: () => waitForCoverUploadedOrChooser(deps),
      closeCoverFileChooserIfOpen: (platform) => closeCoverFileChooserIfOpen(platform, deps),
      findCoverUploadEntry: () => findCoverUploadEntry(deps),
      findCoverFileChooserDialog: () => findCoverFileChooserDialog(deps),
      findCoverFileChooserConfirm: (dialog) => findCoverFileChooserConfirm(dialog, deps),
      findUploadedCoverFileRow: (dialog) => findUploadedCoverFileRow(dialog, deps),
      publishArticle: (platform, context) => publishArticle(platform, context, deps),
      waitForDraftReadyBeforePublish: (platform) => waitForDraftReadyBeforePublish(platform, deps),
      waitForPublishAttemptOutcome: (platform, context) => waitForPublishAttemptOutcome(platform, context, deps),
      findPublishButton: () => findPublishButton(deps),
      clickPublishAction: (el, platform) => clickPublishAction(el, platform, deps),
      hasPublishProgressSignal: () => hasPublishProgressSignal(deps),
      findPublishConfirmButton: (initialButton) => findPublishConfirmButton(initialButton, deps),
      detectPublishSuccessModal: () => detectPublishSuccessModal(deps),
      verifyPublishSubmitted: (context) => verifyPublishSubmitted(context, deps),
      setEditablePlainText: (contentElement, text) => setEditablePlainText(contentElement, text, deps),
      resolveEditableContentElement: (contentElement) => resolveEditableContentElement(contentElement, deps),
      waitForEditorAcceptedContent: (el, text, before, timeoutMs) => waitForEditorAcceptedContent(el, text, before, timeoutMs, deps),
      readEditorWordCount,
      isEditorStillOpen: (text) => isEditorStillOpen(text, deps),
      findDraftLoadingDialog: () => findDraftLoadingDialog(deps),
      closeDraftLoadingDialog: (dialog, platform) => closeDraftLoadingDialog(dialog, platform, deps),
    }
  }

  function createIdentityReader(deps = {}) {
    return {
      readIdentity: () => readIdentity(deps),
    }
  }

  function readIdentity(deps = {}) {
    const organizationIdentity = readOrganizationVerifyIdentity()
    if (organizationIdentity) return organizationIdentity

    const creatorCenterIdentity = readCreatorCenterIdentity(deps)
    if (creatorCenterIdentity) return creatorCenterIdentity

    const accountIds = new Set()
    const accountNames = new Set()
    const profileUrls = new Set()
    const visibleText = normalizeDomText(document.body?.innerText || document.body?.textContent || '', deps)
    collectIdentityFromVisibleDom(accountIds, accountNames, profileUrls, deps)
    collectIdentityFromMeta(accountIds, accountNames, profileUrls, deps)
    collectIdentityFromScripts(accountIds, accountNames)
    collectIdentityFromStorage(accountIds, accountNames)
    return {
      implemented: true,
      accountIds: Array.from(accountIds),
      accountNames: Array.from(accountNames),
      profileUrls: Array.from(profileUrls),
      diagnostics: `href=${location.href}; visibleTextLength=${visibleText.length}; accountIds=${Array.from(accountIds).join(',') || '-'}; accountNames=${Array.from(accountNames).join(',') || '-'}; profileUrls=${Array.from(profileUrls).join(',') || '-'}; cookieKeys=${document.cookie.split(';').map((item) => item.split('=')[0]?.trim()).filter(Boolean).slice(0, 20).join(',') || '-'}`,
    }
  }

  function readOrganizationVerifyIdentity() {
    if (!isOrganizationVerifyLocation()) return null
    const root = document.querySelector('.OrgVerifyDesc')
    if (!root) return null

    const accountIds = new Set()
    const accountNames = new Set()
    const profileUrls = new Set()
    const profileLink = root.querySelector('a[href*="/org/"]')
    collectProfileHref(profileLink?.getAttribute('href') || '', accountIds, profileUrls, location.href)

    const nameElement = root.querySelector('.OrgVerifyDesc-name a, .OrgVerifyDesc-name, a.UserLink-link')
    const name = normalizeAccountName(nameElement?.textContent || '')
    if (isLikelyAccountName(name)) accountNames.add(name)
    if (!accountIds.size && !accountNames.size) return null

    return {
      implemented: true,
      accountIds: Array.from(accountIds),
      accountNames: Array.from(accountNames),
      profileUrls: Array.from(profileUrls),
      diagnostics: `href=${location.href}; source=organization_verify; accountIds=${Array.from(accountIds).join(',') || '-'}; accountNames=${Array.from(accountNames).join(',') || '-'}; profileUrls=${Array.from(profileUrls).join(',') || '-'}`,
    }
  }

  function isOrganizationVerifyLocation() {
    return (location.hostname === 'www.zhihu.com' || location.hostname === 'zhihu.com')
      && location.pathname.startsWith('/organization/verify/')
  }

  function readCreatorCenterIdentity(deps = {}) {
    if (!isCreatorCenterLocation()) return null
    const accountIds = new Set()
    const accountNames = new Set()
    const profileUrls = new Set()
    const name = findCreatorCenterAccountName(deps)
    if (!name) return null
    accountNames.add(name)
    collectCreatorCenterProfileUrls(accountIds, profileUrls)
    return {
      implemented: true,
      accountIds: Array.from(accountIds),
      accountNames: Array.from(accountNames),
      profileUrls: Array.from(profileUrls),
      diagnostics: `href=${location.href}; source=creator_center; accountIds=${Array.from(accountIds).join(',') || '-'}; accountNames=${Array.from(accountNames).join(',') || '-'}; profileUrls=${Array.from(profileUrls).join(',') || '-'}`,
    }
  }

  function isCreatorCenterLocation() {
    return (location.hostname === 'www.zhihu.com' || location.hostname === 'zhihu.com')
      && location.pathname.startsWith('/creator/')
  }

  function findCreatorCenterAccountName(deps = {}) {
    const direct = findCreatorCenterAccountNameByClass(deps)
    if (direct) return direct

    const candidates = []
    for (const el of Array.from(document.querySelectorAll('main [role="navigation"] *'))) {
      if (!isVisibleWithDeps(el, deps)) continue
      const rect = el.getBoundingClientRect?.()
      if (!rect || rect.left > 620 || rect.top < 60 || rect.top > 380) continue
      const text = normalizeAccountName(el.textContent || '')
      if (!isLikelyCreatorCenterAccountName(text)) continue
      candidates.push({
        text,
        score: (rect.top >= 120 && rect.top <= 300 ? 20 : 0)
          + (rect.left >= 240 && rect.left <= 560 ? 10 : 0)
          - Math.abs(text.length - 8),
      })
    }
    candidates.sort((a, b) => b.score - a.score)
    return candidates[0]?.text || ''
  }

  function findCreatorCenterAccountNameByClass(deps = {}) {
    const selectors = [
      '[class*="LevelInfoV2-creatorInfo"]',
      '[class*="creatorInfo"]',
    ]
    for (const selector of selectors) {
      for (const el of Array.from(document.querySelectorAll(selector))) {
        if (!isVisibleWithDeps(el, deps)) continue
        const text = normalizeAccountName(el.textContent || '')
        if (isLikelyCreatorCenterAccountName(text)) return text
      }
    }
    return ''
  }

  function isLikelyCreatorCenterAccountName(value) {
    const text = normalizeAccountName(value)
    if (!isLikelyAccountName(text)) return false
    if (/^(创作中心|内容管理|评论管理|数据分析|收益变现|活动中心|创作成长|个人中心|创作主页|等你来答|发布内容)$/.test(text)) return false
    if (/^Lv\s*\d+$/i.test(text)) return false
    if (/^创作分\s*\d+$/i.test(text)) return false
    return true
  }

  function collectCreatorCenterProfileUrls(accountIds, profileUrls) {
    for (const el of Array.from(document.querySelectorAll('a[href*="/people/"], a[href*="/org/"]'))) {
      collectProfileHref(el.getAttribute('href') || '', accountIds, profileUrls, location.href)
    }
  }

  function collectIdentityFromVisibleDom(accountIds, accountNames, profileUrls, deps) {
    const avatarSelectors = [
      '.AppHeader-profile img[alt]',
      '.AppHeader [class*="profile"] img[alt]',
      'a[href*="/people/"] img[alt]',
      'a[href*="/org/"] img[alt]',
    ]
    for (const selector of avatarSelectors) {
      for (const el of Array.from(document.querySelectorAll(selector))) {
        if (!isVisibleWithDeps(el, deps)) continue
        const link = el.closest?.('a[href*="/people/"], a[href*="/org/"]')
        if (!link && !isTopRightWithDeps(el, deps)) continue
        const alt = normalizeAccountName(el.getAttribute('alt') || '')
        if (isLikelyAccountName(alt)) accountNames.add(alt)
        collectProfileHref(link?.getAttribute('href') || '', accountIds, profileUrls, location.href)
      }
    }

    const accountLinks = Array.from(document.querySelectorAll([
      '.AppHeader a[href*="/people/"]',
      '.AppHeader a[href*="/org/"]',
      '.AppHeader-profile a[href*="/people/"]',
      '.AppHeader-profile a[href*="/org/"]',
      'a.AppHeader-profile[href*="/people/"]',
      'a.AppHeader-profile[href*="/org/"]',
      'a[href*="/people/"][aria-label]',
      'a[href*="/org/"][aria-label]',
    ].join(',')))
    for (const el of accountLinks) {
      if (!isVisibleWithDeps(el, deps) && !hasVisibleAncestorWithDeps(el, deps)) continue
      if (!isTopRightWithDeps(el, deps) && !el.querySelector?.('img[alt]') && !el.getAttribute('aria-label')) continue
      collectProfileHref(el.getAttribute('href') || '', accountIds, profileUrls, location.href)
      const text = normalizeAccountName(el.textContent || el.getAttribute('aria-label') || '')
      if (isLikelyAccountName(text)) accountNames.add(text)
    }
  }

  function collectIdentityFromMeta(accountIds, accountNames, profileUrls, deps) {
    const metaSelectors = [
      'meta[property="article:author"]',
      'meta[name="author"]',
      'meta[property="og:author"]',
    ]
    for (const selector of metaSelectors) {
      for (const el of Array.from(document.querySelectorAll(selector))) {
        const content = el.getAttribute('content') || ''
        collectProfileHref(content, accountIds, profileUrls, location.href)
        const name = normalizeAccountName(content)
        if (isLikelyAccountName(name)) accountNames.add(name)
      }
    }
    for (const script of Array.from(document.querySelectorAll('script[type="application/ld+json"]')).slice(0, 20)) {
      const text = script.textContent || ''
      if (!text || !/(author|url|name)/i.test(text)) continue
      collectIdentityFromText(text.slice(0, 100_000), accountIds, accountNames)
      for (const match of text.matchAll(/https?:\/\/(?:www\.)?zhihu\.com\/(?:people|org)\/[^"\\\s]+/g)) {
        collectProfileHref(match[0], accountIds, profileUrls, location.href)
      }
    }
  }

  function collectIdentityFromScripts(accountIds, accountNames) {
    const scripts = Array.from(document.scripts).slice(0, 80)
    for (const script of scripts) {
      const text = script.textContent || ''
      if (!text || !/(currentUser|viewer|loginUser|urlToken)/i.test(text)) continue
      collectIdentityFromText(text.slice(0, 200_000), accountIds, accountNames)
    }
  }

  function collectIdentityFromStorage(accountIds, accountNames) {
    for (const storage of safeBrowserStorages()) {
      for (let index = 0; index < storage.length; index += 1) {
        const key = storage.key(index)
        if (!key) continue
        let value = ''
        try {
          value = storage.getItem(key) || ''
        } catch {
          continue
        }
        if (!value) continue
        if (!/(current|login|viewer|me|profile|user|account)/i.test(key)) continue
        collectIdentityFromText(`${key}:${value}`, accountIds, accountNames)
      }
    }
  }

  function collectIdentityFromText(text, accountIds, accountNames) {
    const currentUserBlocks = []
    const blockPatterns = [
      /"currentUser"\s*:\s*\{[\s\S]{0,12000}?\}/g,
      /"viewer"\s*:\s*\{[\s\S]{0,12000}?\}/g,
      /"me"\s*:\s*\{[\s\S]{0,12000}?\}/g,
    ]
    for (const pattern of blockPatterns) {
      for (const match of text.matchAll(pattern)) currentUserBlocks.push(match[0])
    }
    for (const block of currentUserBlocks) {
      collectIdsFromText(block, accountIds)
      collectNamesFromText(block, accountNames)
    }
  }

  function collectIdsFromText(text, accountIds) {
    const patterns = [
      /"urlToken"\s*:\s*"([^"]{2,80})"/g,
      /"url_token"\s*:\s*"([^"]{2,80})"/g,
      /"username"\s*:\s*"([^"]{2,80})"/g,
      /"id"\s*:\s*"([^"]{6,120})"/g,
    ]
    for (const pattern of patterns) {
      for (const match of text.matchAll(pattern)) {
        const value = normalizeAccountId(match[1])
        if (value) accountIds.add(value)
      }
    }
  }

  function collectNamesFromText(text, accountNames) {
    const patterns = [
      /"name"\s*:\s*"([^"]{2,80})"/g,
      /"nickname"\s*:\s*"([^"]{2,80})"/g,
    ]
    for (const pattern of patterns) {
      for (const match of text.matchAll(pattern)) {
        const value = normalizeAccountName(match[1])
        if (isLikelyAccountName(value)) accountNames.add(value)
      }
    }
  }

  function safeBrowserStorages() {
    const storages = []
    try {
      if (window.localStorage) storages.push(window.localStorage)
    } catch {
      // ignore storage access failures on restricted pages
    }
    try {
      if (window.sessionStorage) storages.push(window.sessionStorage)
    } catch {
      // ignore storage access failures on restricted pages
    }
    return storages
  }

  function isVisibleWithDeps(el, deps) {
    if (typeof deps.isVisibleElement === 'function') return deps.isVisibleElement(el)
    const rect = el?.getBoundingClientRect?.()
    if (!rect || rect.width <= 0 || rect.height <= 0) return false
    const style = window.getComputedStyle(el)
    return style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0'
  }

  function hasVisibleAncestorWithDeps(el, deps) {
    if (typeof deps.hasVisibleAncestor === 'function') return deps.hasVisibleAncestor(el)
    let parent = el?.parentElement
    for (let depth = 0; parent && depth < 4; depth += 1) {
      if (isVisibleWithDeps(parent, deps)) return true
      parent = parent.parentElement
    }
    return false
  }

  function isTopRightWithDeps(el, deps) {
    if (typeof deps.isTopRightAccountElement === 'function') return deps.isTopRightAccountElement(el)
    const rect = el?.getBoundingClientRect?.()
    if (!rect || rect.width <= 0 || rect.height <= 0) return false
    const centerX = rect.left + rect.width / 2
    const centerY = rect.top + rect.height / 2
    return centerY <= 180 && centerX >= window.innerWidth * 0.45
  }

  async function fillPublishOptions(payload, fillProfile, deps) {
    const options = resolvePublishOptions(payload, deps)
    const actions = []
    if (options.coverImageUrl) {
      updateStage(deps, 'filling_cover')
      const cover = await requireDependency(deps.fillCover, 'fillCover')(options.coverImageUrl, fillProfile.platform)
      if (cover?.filled) actions.push(cover.message)
      if (!requireDependency(deps.hasCoverImage, 'hasCoverImage')()) {
        throw new Error(`知乎封面填充后未检测到封面图片；${describeWith(deps)}`)
      }
    }

    updateStage(deps, 'preparing_publish')
    const publish = await requireDependency(deps.publishArticle, 'publishArticle')(fillProfile.platform, {
      expectedTitle: payload.title || payload.articleTitle || '',
      expectedAccountName: payload.expectedAccountName || '',
      expectedPlatformAccountId: payload.expectedPlatformAccountId || '',
    })
    if (publish?.message) actions.push(publish.message)
    return {
      filled: actions.length > 0,
      published: Boolean(publish?.published),
      publishVerification: publish?.publishVerification,
      message: actions.join('，'),
    }
  }

  function resolvePublishOptions(payload, deps = {}) {
    const profileOptions = payload?.profile?.platformOptions || {}
    const platformOptions = payload?.platformOptions || {}
    const zhihuOptions = payload?.zhihuOptions || platformOptions.zhihu || profileOptions.zhihu || {}
    return {
      coverImageUrl: firstText(
        payload?.coverImageUrl,
        platformOptions.coverImageUrl,
        profileOptions.coverImageUrl,
        zhihuOptions.coverImageUrl,
      ),
    }
  }

  function firstText(...values) {
    for (const value of values) {
      if (typeof value === 'string' && value.trim()) return value.trim()
    }
    return ''
  }

  function describeWith(deps) {
    try {
      return deps.describeSettings?.() || 'zhihuDiagnostics=-'
    } catch (error) {
      return `zhihuDiagnostics=unavailable; error=${error?.message || error}`
    }
  }

  function requireDependency(fn, name) {
    if (typeof fn === 'function') return fn
    throw new Error(`ZHIHU_ADAPTER_DEPENDENCY_MISSING：${name}`)
  }

  async function fillCover(coverImageUrl, platform, deps) {
    scrollToSection('添加封面', deps)
    if (!hasCoverImage(deps)) {
      await requireDependency(deps.waitForCondition, 'waitForCondition')(
        () => findCoverUploadEntry(deps),
        8000,
        `知乎封面上传入口未找到；${describeWith(deps)}`,
      )
    }
    await requireDependency(deps.uploadCoverImageFromLocalHelper, 'uploadCoverImageFromLocalHelper')(coverImageUrl, platform, '知乎')
    const uploaded = await waitForCoverUploadedOrChooser(deps)
    if (uploaded?.image) {
      await closeCoverFileChooserIfOpen(platform, deps)
      return { filled: true, message: '已上传知乎封面' }
    }
    if (uploaded?.dialog) {
      if (hasCoverImage(deps)) {
        await closeCoverFileChooserIfOpen(platform, deps)
        return { filled: true, message: '已上传知乎封面' }
      }
      await confirmCoverFileChooser(uploaded.dialog, platform, deps)
    }
    await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => hasCoverImage(deps),
      20000,
      `等待知乎封面图片上传完成超时；${describeWith(deps)}`,
    )
    await closeCoverFileChooserIfOpen(platform, deps)
    return { filled: true, message: '已上传知乎封面' }
  }

  async function waitForCoverUploadedOrChooser(deps) {
    const deadline = Date.now() + 20000
    let latestDialog = null
    while (Date.now() < deadline) {
      if (hasCoverImage(deps)) return { image: true }
      const dialog = findCoverFileChooserDialog(deps)
      if (dialog) latestDialog = dialog
      await requireDependency(deps.delay, 'delay')(300)
    }
    return latestDialog ? { dialog: latestDialog } : null
  }

  async function closeCoverFileChooserIfOpen(platform, deps) {
    const dialog = findCoverFileChooserDialog(deps)
    if (!dialog) return false
    const closeTarget = findCoverFileChooserCloseTarget(dialog, deps)
    if (closeTarget) {
      await requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')(closeTarget, { platform })
      await requireDependency(deps.delay, 'delay')(500)
    }
    if (findCoverFileChooserDialog(deps)) {
      await clickCoverFileChooserClosePoint(dialog, platform, deps)
      await requireDependency(deps.delay, 'delay')(500)
    }
    if (findCoverFileChooserDialog(deps)) {
      dispatchEscapeKey(document)
      dispatchEscapeKey(window)
      document.body?.focus?.()
      dispatchEscapeKey(document.body || document)
      await requireDependency(deps.delay, 'delay')(500)
    }
    return !findCoverFileChooserDialog(deps)
  }

  async function clickCoverFileChooserClosePoint(dialog, platform, deps) {
    const rect = dialog?.getBoundingClientRect?.()
    if (!rect) return
    const points = [
      { clientX: Math.round(rect.right + 64), clientY: Math.round(rect.top + 42) },
      { clientX: Math.round(rect.right + 32), clientY: Math.round(rect.top + 32) },
      { clientX: Math.round(rect.right - 18), clientY: Math.round(rect.top + 18) },
    ]
    for (const point of points) {
      const target = document.elementFromPoint(point.clientX, point.clientY)
      if (target) {
        requireDependency(deps.firePointerClick, 'firePointerClick')(target, { absoluteClientX: point.clientX, absoluteClientY: point.clientY })
        target.click?.()
        await requireDependency(deps.delay, 'delay')(120)
      }
      if (!findCoverFileChooserDialog(deps)) return
      await requireDependency(deps.requestTrustedClickAt, 'requestTrustedClickAt')(point, platform, '关闭知乎文件弹窗')
      await requireDependency(deps.delay, 'delay')(250)
      if (!findCoverFileChooserDialog(deps)) return
    }
  }

  function dispatchEscapeKey(target) {
    if (!target?.dispatchEvent) return
    for (const type of ['keydown', 'keypress', 'keyup']) {
      target.dispatchEvent(new KeyboardEvent(type, {
        key: 'Escape',
        code: 'Escape',
        keyCode: 27,
        which: 27,
        bubbles: true,
        cancelable: true,
      }))
    }
  }

  function findCoverFileChooserCloseTarget(dialog, deps) {
    const rect = dialog?.getBoundingClientRect?.()
    if (!rect) return null
    const candidates = Array.from(document.querySelectorAll('button, [role="button"], div, span, svg'))
      .filter((el) => requireDependency(deps.isVisibleElement, 'isVisibleElement')(el))
      .map((el) => {
        const itemRect = el.getBoundingClientRect()
        const text = normalizeDomText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '', deps)
        return { el, rect: itemRect, text }
      })
      .filter((item) => {
        const centerX = item.rect.left + item.rect.width / 2
        const centerY = item.rect.top + item.rect.height / 2
        return centerX >= rect.right - 8
          && centerX <= rect.right + 120
          && centerY >= rect.top - 30
          && centerY <= rect.top + 80
          && item.rect.width >= 12
          && item.rect.width <= 80
          && item.rect.height >= 12
          && item.rect.height <= 80
      })
      .sort((left, right) => {
        const leftDistance = Math.abs((left.rect.left + left.rect.width / 2) - (rect.right + 24))
          + Math.abs((left.rect.top + left.rect.height / 2) - (rect.top + 30))
        const rightDistance = Math.abs((right.rect.left + right.rect.width / 2) - (rect.right + 24))
          + Math.abs((right.rect.top + right.rect.height / 2) - (rect.top + 30))
        return leftDistance - rightDistance
      })
    return candidates[0]?.el || null
  }

  async function confirmCoverFileChooser(dialog, platform, deps) {
    const row = await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => findUploadedCoverFileRow(dialog, deps),
      45000,
      `知乎封面文件选项未找到；${describeWith(deps)}`,
    )
    await clickCoverFileRow(row, platform, deps)
    await requireDependency(deps.delay, 'delay')(500)

    const confirm = await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => findCoverFileChooserConfirm(dialog, deps),
      45000,
      `知乎封面文件确认按钮未可用；${describeWith(deps)}`,
    )
    await requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')(confirm, { platform })
    await requireDependency(deps.delay, 'delay')(1000)
  }

  function findCoverFileChooserDialog(deps) {
    const findVisibleTextElement = requireDependency(deps.findVisibleTextElement, 'findVisibleTextElement')
    const title = findVisibleTextElement('选择文件', { exact: true, maxLength: 8 })
    const confirm = findVisibleTextElement('请选择文件', { exact: true, maxLength: 8 })
    const storageHint = findVisibleTextElement('默认储存在', { exact: false, maxLength: 80 })
    const marker = title || confirm || storageHint
    if (!marker) return null
    const dialog = requireDependency(deps.nearestLargeContainer, 'nearestLargeContainer')(marker)
    const text = normalizeDomText(dialog?.textContent || '', deps)
    return text.includes('选择文件') && text.includes('请选择文件') ? dialog : null
  }

  function findUploadedCoverFileRow(dialog, deps) {
    const root = dialog || findCoverFileChooserDialog(deps)
    if (!root) return null
    const candidates = Array.from(root.querySelectorAll('label, button, [role="button"], [role="option"], div, li'))
      .filter((el) => requireDependency(deps.isVisibleElement, 'isVisibleElement')(el))
      .map((el) => {
        const rect = el.getBoundingClientRect()
        const text = normalizeDomText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '', deps)
        return { el, rect, text, area: rect.width * rect.height }
      })
      .filter((item) => /geo-cover|\.png|\.jpg|\.jpeg|PNG|JPG|JPEG/i.test(item.text))
      .filter((item) => item.rect.width >= 120 && item.rect.height >= 24)
      .sort((left, right) => right.area - left.area)
    for (const candidate of candidates) {
      const row = findFileCardContainer(candidate.el, root, deps)
      if (row) return row
    }
    return null
  }

  function findFileCardContainer(el, root, deps) {
    const rootRect = root?.getBoundingClientRect?.() || { width: window.innerWidth, height: window.innerHeight }
    let current = el
    let best = null
    while (current && current !== root && current !== document.body) {
      if (requireDependency(deps.isVisibleElement, 'isVisibleElement')(current)) {
        const rect = current.getBoundingClientRect()
        const text = normalizeDomText(current.textContent || '', deps)
        if (/geo-cover|\.png|\.jpg|\.jpeg/i.test(text)
          && rect.width >= 280
          && rect.height >= 50
          && rect.height <= 140
          && rect.width <= rootRect.width + 20) {
          best = current
        }
      }
      current = current.parentElement
    }
    return best
  }

  async function clickCoverFileRow(row, platform, deps) {
    const target = findFileRowSelectionTarget(row, deps) || row
    await clickCoverSelectionTarget(target, row, platform, deps)
    await requireDependency(deps.delay, 'delay')(300)
    if (!isCoverFileRowSelected(row, deps)) {
      await clickCoverSelectionAtRowRight(row, platform, deps)
      await requireDependency(deps.delay, 'delay')(300)
    }
    if (!isCoverFileRowSelected(row, deps)) {
      await requireDependency(deps.requestTrustedClickAt, 'requestTrustedClickAt')(coverRowRightClickPoint(row), platform, '知乎封面文件')
      await requireDependency(deps.delay, 'delay')(300)
    }
  }

  async function clickCoverSelectionTarget(target, row, platform, deps) {
    requireDependency(deps.firePointerClick, 'firePointerClick')(target, { clickRatioX: target === row ? 0.94 : 0.5, clickRatioY: 0.5 })
    target.click?.()
    await requireDependency(deps.delay, 'delay')(120)
    if (!isCoverFileRowSelected(row, deps)) {
      await requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')(target, { platform, clickRatioX: target === row ? 0.94 : 0.5, clickRatioY: 0.5 })
    }
  }

  async function clickCoverSelectionAtRowRight(row, platform, deps) {
    const point = coverRowRightClickPoint(row)
    const target = document.elementFromPoint(point.clientX, point.clientY) || row
    requireDependency(deps.firePointerClick, 'firePointerClick')(target, { absoluteClientX: point.clientX, absoluteClientY: point.clientY })
    target.click?.()
    await requireDependency(deps.delay, 'delay')(120)
    if (!isCoverFileRowSelected(row, deps)) {
      await requireDependency(deps.requestTrustedClickAt, 'requestTrustedClickAt')(point, platform, '知乎封面文件')
    }
  }

  function coverRowRightClickPoint(row) {
    const rect = row.getBoundingClientRect()
    return {
      clientX: Math.round(rect.right - Math.min(Math.max(rect.width * 0.08, 18), 32)),
      clientY: Math.round(rect.top + rect.height / 2),
    }
  }

  function findFileRowSelectionTarget(row, deps) {
    const rowRect = row.getBoundingClientRect()
    const controls = Array.from(row.querySelectorAll('input, button, [role="radio"], [role="checkbox"], [aria-checked], span, div'))
      .filter((el) => requireDependency(deps.isVisibleElement, 'isVisibleElement')(el))
      .map((el) => ({ el, rect: el.getBoundingClientRect() }))
      .filter((item) => item.rect.left >= rowRect.left + rowRect.width * 0.72)
      .filter((item) => item.rect.width >= 10 && item.rect.width <= 36 && item.rect.height >= 10 && item.rect.height <= 36)
      .sort((left, right) => {
        const leftCenter = left.rect.left + left.rect.width / 2
        const rightCenter = right.rect.left + right.rect.width / 2
        return rightCenter - leftCenter
      })
    return controls[0]?.el || row
  }

  function isCoverFileRowSelected(row, deps) {
    if (!row) return false
    if (row.querySelector?.('input:checked')) return true
    if (row.querySelector?.('[aria-checked="true"], [data-checked="true"]')) return true
    const text = normalizeDomText(row.textContent || '', deps)
    return /已选择|选中/.test(text)
  }

  function findCoverFileChooserConfirm(dialog, deps) {
    const root = dialog || findCoverFileChooserDialog(deps)
    if (!root) return null
    const candidates = Array.from(root.querySelectorAll('button, [role="button"], div, span'))
      .filter((el) => requireDependency(deps.isVisibleElement, 'isVisibleElement')(el))
      .map((el) => {
        const clickable = el.closest?.('button, [role="button"]') || el
        const rect = clickable.getBoundingClientRect()
        const text = normalizeDomText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '', deps)
        const style = window.getComputedStyle(clickable)
        const disabled = clickable.disabled
          || clickable.getAttribute?.('aria-disabled') === 'true'
          || /disabled/i.test(String(clickable.className || ''))
          || style.pointerEvents === 'none'
        return { el: clickable, rect, text, disabled, area: rect.width * rect.height }
      })
      .filter((item) => item.text === '请选择文件')
      .filter((item) => !item.disabled)
      .filter((item) => item.rect.width >= 80 && item.rect.height >= 28)
      .sort((left, right) => right.area - left.area)
    return candidates[0]?.el || null
  }

  function scrollToSection(labelText, deps) {
    const label = requireDependency(deps.findVisibleTextElement, 'findVisibleTextElement')(labelText, { exact: false, maxLength: 20 })
    if (label) {
      label.scrollIntoView({ block: 'center', inline: 'nearest' })
    } else {
      window.scrollTo(0, document.body.scrollHeight)
    }
  }

  function findCoverUploadEntry(deps) {
    const labels = ['添加文章封面', '添加封面', '上传封面']
    const findVisibleTextElement = requireDependency(deps.findVisibleTextElement, 'findVisibleTextElement')
    for (const text of labels) {
      const el = findVisibleTextElement(text, { exact: false, maxLength: 30 })
      const target = el?.closest?.('button, label, [role="button"], div, span') || el
      if (target && requireDependency(deps.isVisibleElement, 'isVisibleElement')(target)) return target
    }
    const fileInput = requireDependency(deps.findLatestFileInput, 'findLatestFileInput')()
    if (fileInput) return fileInput
    return null
  }

  function hasCoverImage(deps) {
    const label = requireDependency(deps.findVisibleTextElement, 'findVisibleTextElement')('添加封面', { exact: false, maxLength: 20 })
    const scope = label ? requireDependency(deps.nearestLargeContainer, 'nearestLargeContainer')(label) : document.body
    const images = Array.from((scope || document).querySelectorAll('img'))
      .filter((img) => requireDependency(deps.isVisibleElement, 'isVisibleElement')(img))
      .filter((img) => {
        const rect = img.getBoundingClientRect()
        const src = img.getAttribute('src') || ''
        return rect.width >= 60
          && rect.height >= 40
          && !/avatar|icon|logo|emoji|data:image\/svg/i.test(src)
      })
    return images.length > 0
  }

  function normalizeDomText(value, deps) {
    return typeof deps.normalizeText === 'function'
      ? deps.normalizeText(value)
      : String(value || '').replace(/\s+/g, '').trim()
  }

  async function setEditablePlainText(contentElement, text, deps) {
    const el = resolveEditableContentElement(contentElement, deps)
    if (!el || !text) return false
    const before = normalizeDomText(el.textContent || '', deps)
    requireDependency(deps.clearEditableTextWithSelection, 'clearEditableTextWithSelection')(el)
    requireDependency(deps.focusEditableElement, 'focusEditableElement')(el)

    requireDependency(deps.dispatchPasteIntoEditable, 'dispatchPasteIntoEditable')(el, text)
    if (await waitForEditorAcceptedContent(el, text, before, 1800, deps)) return true

    requireDependency(deps.clearEditableTextWithSelection, 'clearEditableTextWithSelection')(el)
    requireDependency(deps.focusEditableElement, 'focusEditableElement')(el)
    document.execCommand?.('insertText', false, text)
    if (await waitForEditorAcceptedContent(el, text, before, 1800, deps)) return true

    return false
  }

  function resolveEditableContentElement(contentElement, deps) {
    const isVisibleElement = requireDependency(deps.isVisibleElement, 'isVisibleElement')
    const direct = contentElement?.matches?.('[contenteditable="true"]')
      ? contentElement
      : contentElement?.querySelector?.('[contenteditable="true"]')
    if (direct && isVisibleElement(direct)) return direct

    const candidates = [
      '.DraftEditor-editorContainer [contenteditable="true"]',
      '.public-DraftEditor-content[contenteditable="true"]',
      '[role="textbox"][contenteditable="true"]',
      'div[contenteditable="true"]',
    ]
    return requireDependency(deps.findFirst, 'findFirst')(candidates, { rejectTitleLike: true })
  }

  async function waitForEditorAcceptedContent(el, text, before, timeoutMs, deps) {
    const expected = normalizeDomText(text, deps).slice(0, 40)
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      requireDependency(deps.dispatchEditEvents, 'dispatchEditEvents')(el)
      const current = normalizeDomText(el.textContent || '', deps)
      const wordCount = readEditorWordCount()
      const hasExpectedText = expected && current.includes(expected)
      const changed = current && current !== before && current.length >= Math.min(20, normalizeDomText(text, deps).length)
      if ((hasExpectedText || changed) && (wordCount === null || wordCount > 0)) return true
      await requireDependency(deps.delay, 'delay')(120)
    }
    return false
  }

  function readEditorWordCount() {
    const text = document.body?.innerText || document.body?.textContent || ''
    const match = text.match(/字数\s*[:：]\s*(\d+)/)
    if (!match) return null
    const value = Number(match[1])
    return Number.isFinite(value) ? value : null
  }

  async function publishArticle(platform, context = {}, deps) {
    await closeCoverFileChooserIfOpen(platform, deps)
    await waitForDraftReadyBeforePublish(platform, deps)
    let lastBlocked = null
    for (let attempt = 1; attempt <= 3; attempt += 1) {
      window.scrollTo(0, document.body.scrollHeight)
      await requireDependency(deps.delay, 'delay')(600)
      const button = await requireDependency(deps.waitForCondition, 'waitForCondition')(
        () => findPublishButton(deps),
        10000,
        `知乎发布按钮未找到；${describeWith(deps)}`,
      )
      updateStage(deps, 'submitting_publish')
      await clickPublishAction(button, platform, deps)
      await requireDependency(deps.delay, 'delay')(1200)

      const blockedAfterClick = findDraftLoadingDialog(deps)
      if (blockedAfterClick) {
        lastBlocked = await closeDraftLoadingDialog(blockedAfterClick, platform, deps)
        await requireDependency(deps.delay, 'delay')(5000)
        await waitForDraftReadyBeforePublish(platform, deps)
        continue
      }

      const confirm = findPublishConfirmButton(button, deps)
      if (confirm) {
        updateStage(deps, 'submitting_publish')
        await clickPublishAction(confirm, platform, deps)
        await requireDependency(deps.delay, 'delay')(1200)
      }

      updateStage(deps, 'verifying_publish_result')
      const outcome = await waitForPublishAttemptOutcome(platform, context, deps)
      if (outcome?.verified) {
        return {
          published: true,
          publishVerification: outcome,
          message: '已点击知乎发布',
        }
      }
      if (outcome?.blocked) {
        lastBlocked = outcome
        await requireDependency(deps.delay, 'delay')(5000)
        await waitForDraftReadyBeforePublish(platform, deps)
        continue
      }
    }
    throw new Error(`知乎发布被草稿加载阻塞：${lastBlocked?.message || '多次点击发布后仍提示草稿加载中'}；${describeWith(deps)}`)
  }

  async function waitForDraftReadyBeforePublish(platform, deps) {
    const deadline = Date.now() + 30000
    let latest = null
    while (Date.now() < deadline) {
      const dialog = findDraftLoadingDialog(deps)
      if (!dialog) return true
      latest = await closeDraftLoadingDialog(dialog, platform, deps)
      await requireDependency(deps.delay, 'delay')(5000)
    }
    throw new Error(`知乎草稿加载未完成，暂不发布：${latest?.message || '仍检测到草稿加载弹窗'}；${describeWith(deps)}`)
  }

  async function waitForPublishAttemptOutcome(platform, context = {}, deps) {
    const deadline = Date.now() + 20000
    while (Date.now() < deadline) {
      const verified = verifyPublishSubmitted(context, deps)
      if (verified) return verified
      const dialog = findDraftLoadingDialog(deps)
      if (dialog) {
        return closeDraftLoadingDialog(dialog, platform, deps)
      }
      await requireDependency(deps.delay, 'delay')(300)
    }
    throw new Error(`知乎发布后未检测到完成状态；${describeWith(deps)}`)
  }

  function findPublishButton(deps) {
    return requireDependency(deps.collectVisibleActionElements, 'collectVisibleActionElements')()
      .filter((item) => item.text === '发布')
      .filter((item) => !item.disabled)
      .sort((left, right) => right.rect.top - left.rect.top || right.rect.left - left.rect.left)[0]?.el || null
  }

  async function clickPublishAction(el, platform, deps) {
    const target = el?.closest?.('button, [role="button"]') || el
    if (!target) return
    await requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')(target, { platform })
    await requireDependency(deps.delay, 'delay')(600)
  }

  function updateStage(deps, stage) {
    if (typeof deps?.updateStage === 'function') deps.updateStage(stage)
  }

  function hasPublishProgressSignal(deps) {
    const findVisibleTextElement = requireDependency(deps.findVisibleTextElement, 'findVisibleTextElement')
    return Boolean(
      findDraftLoadingDialog(deps)
      || verifyPublishSubmitted({}, deps)
      || findVisibleTextElement('审核中', { exact: false, maxLength: 40 })
    )
  }

  function findPublishConfirmButton(initialButton, deps) {
    const buttons = requireDependency(deps.collectVisibleActionElements, 'collectVisibleActionElements')()
      .filter((item) => !initialButton || item.el !== initialButton)
      .filter((item) => item.text === '发布' || item.text === '确认发布' || item.text === '继续发布')
      .sort((left, right) => right.rect.top - left.rect.top)
    return buttons[0]?.el || null
  }

  function detectPublishSuccessModal(deps = {}) {
    const closeButtons = Array.from(document.querySelectorAll?.('button[aria-label="关闭"]') || [])
      .filter((button) => isVisibleWithDeps(button, deps))
    for (const closeButton of closeButtons) {
      let root = closeButton.parentElement
      for (let depth = 0; root && depth < 8; depth += 1, root = root.parentElement) {
        if (root === document.body || root === document.documentElement) break
        if (!isVisibleWithDeps(root, deps)) continue
        const text = normalizeDomText(root.textContent || '', deps)
        const creationMatch = text.match(/感谢你的第(\d+)篇创作[！!]?/)
        const hasShareSections = text.includes('转发到想法') && text.includes('更多分享')
        if (!creationMatch && !hasShareSections) continue
        const title = Array.from(root.querySelectorAll?.('*') || [])
          .find((element) => isVisibleWithDeps(element, deps)
            && normalizeDomText(element.textContent || '', deps) === '发布成功')
        if (!title) continue
        return {
          detected: true,
          title: '发布成功',
          creationCount: creationMatch ? Number(creationMatch[1]) : null,
          confirmationText: creationMatch?.[0] || '',
          closeButtonPresent: true,
          forwardToIdeaPresent: text.includes('转发到想法'),
          moreSharePresent: text.includes('更多分享'),
          signature: 'title+close_button+creation_or_share',
        }
      }
    }
    return null
  }

  function verifyPublishSubmitted(context = {}, deps) {
    const text = normalizeDomText(document.body?.innerText || '', deps)
    const pageUrl = String(location.href || '')
    const publishedUrl = isPublishedArticleUrl(pageUrl)
      ? normalizePublishedUrl(pageUrl, pageUrl)
      : ''
    if (findDraftLoadingDialog(deps)) return null
    const successModal = detectPublishSuccessModal(deps)
    const editorStillOpen = isEditorStillOpen(text, deps)
    const pathname = location.pathname || ''
    const success = Boolean(successModal)
      || (!editorStillOpen && (/^\/p\/[^/]+/.test(pathname) || /^\/article\/[^/]+/.test(pathname) || /^\/question\/[^/]+\/answer\/[^/]+/.test(pathname)))
      || (!editorStillOpen && /发布于\d{4}[-年]\d{1,2}[-月]\d{1,2}/.test(text))
    if (!success) return null
    const publishedAtText = extractPublishedAtText(text)
    const pageTitle = extractPageTitle(deps)
    const expectedTitle = String(context.expectedTitle || '').trim()
    const titleMatch = matchPublishedTitle(expectedTitle, pageTitle, text)
    const identity = requireDependency(deps.readIdentity, 'readIdentity')()
    const button = findPublishButton(deps)
    const draftDialog = findDraftLoadingDialog(deps)
    return {
      verified: true,
      verificationSource: successModal ? 'zhihu_publish_success_modal' : 'zhihu_published_page',
      pageUrl,
      platformPublishedUrl: publishedUrl,
      publishedUrl,
      pageTitle,
      expectedTitle,
      titleMatch,
      publishedAtText,
      account: {
        expectedAccountName: context.expectedAccountName || '',
        expectedPlatformAccountId: context.expectedPlatformAccountId || '',
        accountIds: identity.accountIds,
        accountNames: identity.accountNames,
        profileUrls: identity.profileUrls || [],
        diagnostics: identity.diagnostics,
      },
      publishUi: {
        editorStillOpen,
        publishButtonVisible: Boolean(button),
        draftLoadingDialogVisible: Boolean(draftDialog),
        lastTrustedClick: requireDependency(deps.describeLastTrustedClick, 'describeLastTrustedClick')(),
      },
      successSignal: {
        successText: Boolean(successModal),
        successModal,
        reviewText: text.includes('审核中'),
        publishedUrl: Boolean(publishedUrl),
        publishedAtText: Boolean(publishedAtText),
      },
      textSample: text.slice(0, 500),
    }
  }

  function extractPageTitle(deps) {
    const candidates = [
      document.querySelector('h1')?.textContent,
      document.querySelector('[class*="Post-Title"]')?.textContent,
      document.querySelector('[class*="ContentItem-title"]')?.textContent,
      document.title,
    ]
    return candidates
      .map((item) => normalizeTitleText(item || ''))
      .find(Boolean) || ''
  }

  function extractPublishedAtText(text) {
    const normalized = String(text || '')
    return normalized.match(/发布于\d{4}[-年]\d{1,2}[-月]\d{1,2}[^\s。；;，,]{0,16}/)?.[0]
      || normalized.match(/\d{4}[-年]\d{1,2}[-月]\d{1,2}[^\s。；;，,]{0,16}发布/)?.[0]
      || ''
  }

  function isEditorStillOpen(text = normalizeDomText(document.body?.innerText || '', {}), deps) {
    return Boolean(
      location.pathname.startsWith('/write')
      || text.includes('发布设置')
      || text.includes('添加文章封面')
      || text.includes('Markdown语法输入中')
      || findPublishButton(deps)
    )
  }

  function findDraftLoadingDialog(deps) {
    const marker = requireDependency(deps.findVisibleTextElement, 'findVisibleTextElement')('草稿加载中', { exact: false, maxLength: 80 })
    if (!marker) return null
    const dialog = requireDependency(deps.nearestLargeContainer, 'nearestLargeContainer')(marker)
    const text = normalizeDomText(dialog?.textContent || marker.textContent || '', deps)
    if (!text.includes('草稿加载中') || !text.includes('等待加载完成')) return null
    return dialog
  }

  async function closeDraftLoadingDialog(dialog, platform, deps) {
    const message = normalizeDomText(dialog?.textContent || '', deps).slice(0, 160)
    const confirm = requireDependency(deps.findTextElementInRoot, 'findTextElementInRoot')(dialog, '确定', { exact: true, maxLength: 4 })
      || requireDependency(deps.collectVisibleActionElements, 'collectVisibleActionElements')(dialog).find((item) => item.text === '确定')?.el
    if (confirm) {
      await requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')(confirm, { platform })
      await requireDependency(deps.delay, 'delay')(1000)
    }
    return {
      blocked: true,
      message: message || '草稿加载中，请等待加载完成后再次修改',
    }
  }

  global.__GEO_ZHIHU_PLATFORM__ = {
    HOME_URL,
    CREATOR_CENTER_URL,
    ORGANIZATION_VERIFY_URL,
    WRITE_URL,
    classifyFailureCode,
    isPublishedArticleUrl,
    normalizePublishedUrl,
    matchPublishedTitle,
    normalizeTitleText,
    collectProfileHref,
    normalizeAccountId,
    normalizeAccountName,
    isLikelyAccountName,
    isRetryableFailureCode,
    detectPublishSuccessModal,
    createPublishOptionsAdapter,
    createDomAdapter,
    createIdentityReader,
    resolvePublishOptions,
  }
})(globalThis)
