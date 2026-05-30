const READY_REPORT_DELAYS_MS = [350, 1500, 3500, 7000]

for (const delayMs of READY_REPORT_DELAYS_MS) {
  setTimeout(() => {
    safeRuntimeSend({
      type: 'GEO_ENV_EDITOR_READY',
      href: location.href,
    })
  }, delayMs)
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  const run = async () => {
    if (message?.type === 'GEO_ENV_PING') {
      return { ok: true, href: location.href }
    }
    if (message?.type === 'GEO_ENV_CHECK_IDENTITY') {
      return checkIdentityPayload(message.payload || {})
    }
    if (message?.type === 'GEO_ENV_READ_IDENTITY') {
      const platform = message.payload?.platform || inferPlatformFromLocation()
      return {
        platform,
        identity: readPlatformIdentity(platform),
      }
    }
    if (message?.type === 'GEO_ENV_FILL_TASK') {
      return fillPayload(message.payload || {})
    }
    return null
  }

  run()
    .then((result) => sendResponse({ ok: true, result }))
    .catch((error) => {
      showStatus(`填充失败：${error.message}`, 'error')
      sendResponse({ ok: false, error: error.message })
    })
  return true
})

async function fillPayload(payload) {
  showStatus('正在等待编辑器...', 'info')
  const fillProfile = buildFillProfile(payload)
  await ensureEditorVisible(fillProfile)
  normalizeEditorViewport(fillProfile)
  assertPlatformLoggedIn(fillProfile)
  const identityCheck = resolveFillIdentityCheck(payload)
  const titleElement = findTitleElement(fillProfile)
  const expectedTitle = payload.title || payload.articleTitle || ''
  const rawHtml = payload.renderedHtml || payload.html || payload.content || ''
  const normalizedContent = removeDuplicateLeadingTitle(rawHtml, expectedTitle)
  const titleFilled = fillTitle(payload.title || payload.articleTitle || '', titleElement)
  const contentElement = findContentElement(titleElement, fillProfile)
  if (titleElement && contentElement && titleElement === contentElement) {
    throw new Error(`标题和正文命中同一元素：${describeElement(titleElement)}`)
  }
  const contentFilled = fillContent(normalizedContent.html, contentElement, fillProfile)
  const tagsFilled = fillTags(payload.tags || [], fillProfile)
  if (!titleFilled || !contentFilled) {
    const diagnostics = collectDiagnostics()
    throw new Error(`未找到${!titleFilled ? '标题' : ''}${!titleFilled && !contentFilled ? '和' : ''}${!contentFilled ? '正文' : ''}输入框；${diagnostics}`)
  }
  await delay(500)
  verifyFilled(titleElement, contentElement, expectedTitle, normalizedContent.html)
  normalizeEditorViewport(fillProfile)
  const draftState = detectDraftState()
  const identityText = identityCheck.message ? `${identityCheck.message}，` : ''
  const contentText = normalizedContent.removedTitle ? '已去除正文重复标题，' : ''
  const draftText = draftState.message ? `${draftState.message}，` : ''
  showStatus(`${identityText}${contentText}${draftText}标题和正文已填充：标题=${describeElement(titleElement)}，正文=${describeElement(contentElement)}，请人工核对后发布`, 'success')
  return {
    titleFilled,
    contentFilled,
    tagsFilled,
    identityCheck,
    draftState,
    removedDuplicateTitle: normalizedContent.removedTitle,
    titleElement: describeElement(titleElement),
    contentElement: describeElement(contentElement),
  }
}

function normalizeEditorViewport(fillProfile) {
  if (fillProfile.platform !== 'toutiao') return
  hideToutiaoAssistantPanel()
  window.scrollTo(0, 0)
  const root = document.scrollingElement || document.documentElement
  if (root) {
    root.scrollTop = 0
    root.scrollLeft = 0
  }
  document.body.scrollLeft = 0
  document.documentElement.scrollLeft = 0
  for (const el of Array.from(document.querySelectorAll('*'))) {
    if (el.scrollLeft > 0 && el.scrollWidth > el.clientWidth) {
      el.scrollLeft = 0
    }
  }
}

function hideToutiaoAssistantPanel() {
  const markers = ['头条创作助手', 'AI 创作', '内容建议']
  const markerNodes = Array.from(document.querySelectorAll('body *')).filter((el) => {
    const text = normalizeText(el.textContent || '')
    if (!text || text.length > 120) return false
    return markers.some((marker) => text.includes(marker))
  })

  for (const node of markerNodes) {
    const panel = findToutiaoAssistantPanel(node)
    if (!panel) continue
    panel.setAttribute('data-geo-hidden-toutiao-assistant', 'true')
    panel.style.setProperty('display', 'none', 'important')
    return true
  }
  return false
}

function findToutiaoAssistantPanel(node) {
  let current = node
  while (current && current !== document.body && current !== document.documentElement) {
    const rect = current.getBoundingClientRect()
    const style = window.getComputedStyle(current)
    const zIndex = Number.parseInt(style.zIndex, 10)
    const looksLikeFloatingPanel = rect.width >= 260
      && rect.width <= 560
      && rect.height >= 360
      && rect.left >= 240
      && ['fixed', 'absolute', 'sticky'].includes(style.position)
    const hasStacking = Number.isFinite(zIndex) && zIndex > 0
    if (looksLikeFloatingPanel || (hasStacking && rect.width >= 260 && rect.height >= 360)) {
      return current
    }
    current = current.parentElement
  }
  return null
}

function resolveFillIdentityCheck(payload) {
  if (payload.precheckedIdentity) return payload.precheckedIdentity
  if (requiresIdentityExpectation(payload.platform) && !payload.expectedPlatformAccountId && !payload.expectedAccountName) {
    throw new Error('IDENTITY_EXPECTATION_MISSING：多账号平台任务缺少 expectedPlatformAccountId/expectedAccountName，已拒绝填充')
  }
  if (payload.expectedPlatformAccountId || payload.expectedAccountName) {
    throw new Error('账号身份预检结果缺失，请先在平台账号身份页上报登录状态后重试')
  }
  return checkIdentityPayload(payload)
}

function requiresIdentityExpectation(platform) {
  return ['toutiao', 'zhihu', 'xiaohongshu'].includes(normalizePlatform(platform))
}

function checkIdentityPayload(payload) {
  const fillProfile = buildFillProfile(payload)
  return verifyExpectedPlatformIdentity(payload, fillProfile.platform)
}

function detectDraftState() {
  const visibleText = normalizeText(document.body?.innerText || document.body?.textContent || '')
  const markers = [
    '草稿已保存',
    '已保存草稿',
    '保存成功',
    '已保存',
    '草稿箱',
    '自动保存',
  ]
  const matched = markers.find((marker) => visibleText.includes(marker))
  if (!matched) return { detected: false, message: '' }
  return { detected: true, message: `草稿状态=${matched}` }
}

function removeDuplicateLeadingTitle(html, title) {
  const expectedTitle = normalizeArticleText(title)
  if (!html || !expectedTitle) return { html, removedTitle: false }

  const template = document.createElement('template')
  template.innerHTML = html
  const firstBlock = findFirstMeaningfulBlock(template.content)
  if (!firstBlock) return { html, removedTitle: false }

  const firstText = normalizeArticleText(firstBlock.textContent)
  if (firstText !== expectedTitle) return { html, removedTitle: false }

  firstBlock.remove()
  return { html: template.innerHTML, removedTitle: true }
}

function findFirstMeaningfulBlock(root) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT)
  let current = walker.nextNode()
  while (current) {
    const text = normalizeArticleText(current.textContent)
    if (text && isMeaningfulContentBlock(current)) return current
    current = walker.nextNode()
  }
  return null
}

function isMeaningfulContentBlock(el) {
  const tag = el.tagName
  if (['DIV', 'SECTION', 'ARTICLE'].includes(tag)) {
    const childBlocks = Array.from(el.children).filter((child) => [
      'H1',
      'H2',
      'H3',
      'H4',
      'H5',
      'H6',
      'P',
      'DIV',
      'SECTION',
      'ARTICLE',
    ].includes(child.tagName))
    if (childBlocks.length > 0) return false
  }
  return [
    'H1',
    'H2',
    'H3',
    'H4',
    'H5',
    'H6',
    'P',
    'DIV',
    'SECTION',
    'ARTICLE',
    'STRONG',
    'B',
  ].includes(el.tagName)
}

function buildFillProfile(payload) {
  const platform = payload.platform || inferPlatformFromLocation()
  const editorSelectors = payload.profile?.editorSelectors || {}
  return {
    platform,
    titleSelectors: selectorList(editorSelectors.title).concat(defaultTitleSelectors(platform)),
    contentSelectors: selectorList(editorSelectors.content).concat(defaultContentSelectors(platform)),
    tagSelectors: selectorList(editorSelectors.tags).concat(defaultTagSelectors(platform)),
  }
}

function selectorList(value) {
  if (!value) return []
  return String(value)
    .split(',')
    .map((selector) => selector.trim())
    .filter(Boolean)
}

function defaultTitleSelectors(platform) {
  const common = [
    '[data-geo-fill="title"]',
    'textarea[placeholder*="标题"]',
    'input[placeholder*="标题"]',
    'textarea[aria-label*="标题"]',
    'input[aria-label*="标题"]',
  ]
  if (platform === 'zhihu') {
    return [
      '.WriteIndex-titleInput textarea',
      'textarea[placeholder*="请输入标题"]',
      'textarea.Input',
    ].concat(common)
  }
  if (platform === 'xiaohongshu') {
    return [
      'input[placeholder*="请输入标题"]',
      'textarea[placeholder*="请输入标题"]',
      'input[placeholder*="输入标题"]',
      'textarea[placeholder*="输入标题"]',
      'input[placeholder*="添加标题"]',
      'textarea[placeholder*="添加标题"]',
      'input[placeholder*="填写标题"]',
      'textarea[placeholder*="填写标题"]',
      'input[placeholder*="标题"]',
      'textarea[placeholder*="标题"]',
    ].concat(common)
  }
  return [
    '.byte-editor-title textarea',
    '.byte-editor-title input',
    '.article-title textarea',
    '.article-title input',
    '.title textarea',
    '.title input',
  ].concat(common)
}

function defaultContentSelectors(platform) {
  const common = [
    '[data-geo-fill="content"]',
    '[contenteditable="true"]',
    'textarea[placeholder*="正文"]',
    'textarea[placeholder*="内容"]',
  ]
  if (platform === 'zhihu') {
    return [
      '.public-DraftEditor-content',
      '.DraftEditor-editorContainer [contenteditable="true"]',
      '[data-contents="true"]',
      'div[role="textbox"]',
    ].concat(common)
  }
  if (platform === 'xiaohongshu') {
    return [
      '.ql-editor',
      '.ProseMirror',
      '[data-placeholder*="正文"]',
      '[data-placeholder*="内容"]',
      'textarea[placeholder*="输入正文"]',
      'textarea[placeholder*="请输入正文"]',
      'textarea[placeholder*="添加正文"]',
      'textarea[placeholder*="分享"]',
      'div[contenteditable="true"]',
      'div[role="textbox"]',
    ].concat(common)
  }
  return [
    '.ProseMirror',
    '.DraftEditor-editorContainer [contenteditable="true"]',
    '.public-DraftEditor-content',
    '.notranslate[contenteditable="true"]',
    'div[role="textbox"]',
  ].concat(common)
}

function defaultTagSelectors(platform) {
  if (platform === 'xiaohongshu') {
    return [
      'input[placeholder*="话题"]',
      'input[placeholder*="标签"]',
      'textarea[placeholder*="话题"]',
      'textarea[placeholder*="标签"]',
    ]
  }
  return [
    '[data-geo-fill="tags"]',
    'input[placeholder*="标签"]',
    'textarea[placeholder*="标签"]',
  ]
}

function verifyExpectedPlatformIdentity(payload, platform) {
  const expected = normalizeAccountId(payload.expectedPlatformAccountId)
  const expectedName = normalizeAccountName(payload.expectedAccountName)
  if (!expected && !expectedName) return { method: 'skipped', message: '' }

  const identity = readPlatformIdentity(platform)
  if (!identity.implemented) {
    return {
      method: 'notImplemented',
      message: `账号校验未启用(${platform || 'unknown'})`,
      currentAccountIds: [],
      currentAccountNames: [],
    }
  }
  if (expected && identity.accountIds.includes(expected)) {
    return {
      method: 'platformAccountId',
      message: `账号校验通过(ID=${expected})`,
      currentAccountIds: identity.accountIds,
      currentAccountNames: identity.accountNames,
    }
  }

  if (!expected && expectedName && identity.accountNames.length > 1) {
    throw new Error(`账号一致性校验失败：读取到多个候选账号名称=${identity.accountNames.join(',')}，期望名称=${payload.expectedAccountName}；${identity.diagnostics}`)
  }

  if (expectedName && identity.accountNames.includes(expectedName)) {
    const currentIdText = identity.accountIds.length ? `，当前ID=${identity.accountIds.join(',')}` : ''
    return {
      method: expected ? 'accountNameFallbackAfterIdMismatch' : 'accountNameFallback',
      message: `账号校验通过(名称=${payload.expectedAccountName}${currentIdText})`,
      currentAccountIds: identity.accountIds,
      currentAccountNames: identity.accountNames,
    }
  }

  if (platform === 'xiaohongshu' && expectedName) {
    throw new Error(`账号一致性校验失败：当前小红书账号名称=${identity.accountNames.join(',') || '未读取到'}，期望名称=${payload.expectedAccountName}`)
  }
  if (platform === 'zhihu' && expectedName) {
    const currentIds = identity.accountIds.length ? `，当前标识=${identity.accountIds.join(',')}` : ''
    throw new Error(`账号一致性校验失败：当前知乎账号名称=${identity.accountNames.join(',') || '未读取到'}${currentIds}，期望名称=${payload.expectedAccountName}`)
  }

  if (expected && !identity.accountIds.length) {
    throw new Error(`账号一致性校验失败：未读取到当前${platform || '平台'}账号 ID，期望=${expected}；${identity.diagnostics}`)
  }
  if (expected) {
    const nameText = expectedName ? `，期望名称=${payload.expectedAccountName}，当前名称=${identity.accountNames.join(',') || '未读取到'}` : ''
    throw new Error(`账号一致性校验失败：当前账号=${identity.accountIds.join(',')}，期望=${expected}${nameText}`)
  }

  throw new Error(`账号一致性校验失败：当前名称=${identity.accountNames.join(',') || '未读取到'}，期望名称=${payload.expectedAccountName}`)
}

function inferPlatformFromLocation() {
  if (location.hostname === 'mp.toutiao.com') return 'toutiao'
  if (location.hostname.endsWith('zhihu.com')) return 'zhihu'
  if (location.hostname.endsWith('xiaohongshu.com')) return 'xiaohongshu'
  return null
}

function readPlatformIdentity(platform) {
  if (platform === 'toutiao') return readToutiaoIdentity()
  if (platform === 'zhihu') return readZhihuIdentity()
  if (platform === 'xiaohongshu') return readXiaohongshuIdentity()
  return {
    implemented: false,
    accountIds: [],
    accountNames: [],
    diagnostics: `暂未实现 ${platform || 'unknown'} 账号读取`,
  }
}

function readZhihuIdentity() {
  const accountIds = new Set()
  const accountNames = new Set()
  const visibleText = normalizeText(document.body?.innerText || document.body?.textContent || '')
  collectZhihuIdentityFromVisibleDom(accountIds, accountNames)
  return {
    implemented: true,
    accountIds: Array.from(accountIds),
    accountNames: Array.from(accountNames),
    diagnostics: `href=${location.href}; visibleTextLength=${visibleText.length}; accountIds=${Array.from(accountIds).join(',') || '-'}; accountNames=${Array.from(accountNames).join(',') || '-'}`,
  }
}

function collectZhihuIdentityFromVisibleDom(accountIds, accountNames) {
  const avatarSelectors = [
    '.AppHeader-profile img[alt]',
    '.AppHeader [class*="profile"] img[alt]',
  ]
  for (const selector of avatarSelectors) {
    for (const el of Array.from(document.querySelectorAll(selector))) {
      if (!isVisibleElement(el)) continue
      if (!isTopRightAccountElement(el)) continue
      const alt = normalizeZhihuAccountName(el.getAttribute('alt') || '')
      if (isLikelyZhihuAccountName(alt)) accountNames.add(alt)
    }
  }

  const accountLinks = Array.from(document.querySelectorAll([
    '.AppHeader a[href*="/people/"]',
    '.AppHeader-profile a[href*="/people/"]',
    'a.AppHeader-profile[href*="/people/"]',
  ].join(',')))
  for (const el of accountLinks) {
    if (!isVisibleElement(el) && !hasVisibleAncestor(el)) continue
    if (!isTopRightAccountElement(el)) continue
    const href = el.getAttribute('href') || ''
    const token = href.match(/\/people\/([^/?#]+)/)?.[1]
    if (token) accountIds.add(decodeURIComponent(token))
    const text = normalizeZhihuAccountName(el.textContent || el.getAttribute('aria-label') || '')
    if (isLikelyZhihuAccountName(text)) accountNames.add(text)
  }
}

function collectZhihuIdentityFromText(text, accountIds, accountNames) {
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
    collectZhihuIdsFromText(block, accountIds)
    collectZhihuNamesFromText(block, accountNames)
  }
}

function collectZhihuIdsFromText(text, accountIds) {
  const patterns = [
    /"urlToken"\s*:\s*"([^"]{2,80})"/g,
    /"url_token"\s*:\s*"([^"]{2,80})"/g,
    /"username"\s*:\s*"([^"]{2,80})"/g,
    /"id"\s*:\s*"([^"]{6,120})"/g,
  ]
  for (const pattern of patterns) {
    for (const match of text.matchAll(pattern)) {
      const value = normalizeZhihuAccountId(match[1])
      if (value) accountIds.add(value)
    }
  }
}

function collectZhihuNamesFromText(text, accountNames) {
  const patterns = [
    /"name"\s*:\s*"([^"]{2,80})"/g,
    /"headline"\s*:\s*"([^"]{2,120})"/g,
  ]
  for (const pattern of patterns) {
    for (const match of text.matchAll(pattern)) {
      const value = normalizeZhihuAccountName(match[1])
      if (isLikelyZhihuAccountName(value)) accountNames.add(value)
    }
  }
}

function collectZhihuIdentityFromStorage(accountIds, accountNames) {
  for (const storage of [localStorage, sessionStorage]) {
    for (let index = 0; index < storage.length; index += 1) {
      const key = storage.key(index)
      if (!key) continue
      const value = storage.getItem(key)
      if (!value) continue
      if (!/(current|login|viewer|me|profile|user|account)/i.test(key)) continue
      collectZhihuIdentityFromText(`${key}:${value}`, accountIds, accountNames)
    }
  }
}

function collectZhihuIdentityFromScripts(accountIds, accountNames) {
  const scripts = Array.from(document.scripts).slice(0, 80)
  for (const script of scripts) {
    const text = script.textContent || ''
    if (!text || !/(currentUser|viewer|loginUser|urlToken)/i.test(text)) continue
    collectZhihuIdentityFromText(text.slice(0, 200_000), accountIds, accountNames)
  }
}

function normalizeZhihuAccountId(value) {
  const text = String(value || '').trim()
  if (!text) return ''
  try {
    return decodeURIComponent(text)
  } catch {
    return text
  }
}

function isLikelyZhihuAccountName(value) {
  const text = normalizeZhihuAccountName(value)
  if (text.length < 2 || text.length > 60) return false
  if (/^(写文章|发布文章|创作|开始写作|首页|会员|消息|私信|设置|退出|退出登录|知乎|知乎创作助手)$/.test(text)) return false
  if (/^[\w.-]{2,60}$/.test(text)) return true
  return /[\u4e00-\u9fa5]/.test(text) && !/[，。！？、]/.test(text)
}

function normalizeZhihuAccountName(value) {
  let text = normalizeAccountName(value)
  const homepageMatch = text.match(/^点击打开(.+?)的主页$/)
  if (homepageMatch?.[1]) text = homepageMatch[1]
  return text
}

function readXiaohongshuIdentity() {
  const accountNames = new Set()
  const visibleText = normalizeText(document.body?.innerText || document.body?.textContent || '')
  collectXiaohongshuAccountNamesFromAccountDom(accountNames)
  return {
    implemented: true,
    accountIds: [],
    accountNames: Array.from(accountNames),
    diagnostics: `href=${location.href}; visibleTextLength=${visibleText.length}; accountNames=${Array.from(accountNames).join(',') || '-'}`,
  }
}

function collectXiaohongshuAccountNamesFromAccountDom(accountNames) {
  const preciseSelectors = [
    '.d-topbar-default .user-info .name-box',
    '.user-info .name-box',
  ]
  for (const selector of preciseSelectors) {
    const elements = Array.from(document.querySelectorAll(selector))
    for (const el of elements) {
      if (!isVisibleElement(el) && !hasVisibleAncestor(el)) continue
      if (!isTopRightAccountElement(el)) continue
      const text = normalizeAccountName(el.textContent || el.getAttribute('aria-label') || '')
      if (isLikelyXiaohongshuAccountName(text)) accountNames.add(text)
    }
  }
}

function collectXiaohongshuAccountNamesFromText(text, accountNames) {
  const patterns = [
    /"nickname"\s*:\s*"([^"]{2,80})"/g,
    /"nickName"\s*:\s*"([^"]{2,80})"/g,
    /"userName"\s*:\s*"([^"]{2,80})"/g,
    /"user_name"\s*:\s*"([^"]{2,80})"/g,
    /"creatorName"\s*:\s*"([^"]{2,80})"/g,
    /"name"\s*:\s*"([^"]{2,80})"/g,
  ]
  for (const pattern of patterns) {
    for (const match of text.matchAll(pattern)) {
      const value = normalizeAccountName(match[1])
      if (isLikelyXiaohongshuAccountName(value)) accountNames.add(value)
    }
  }
}

function collectXiaohongshuAccountNamesFromStorage(accountNames) {
  for (const storage of [localStorage, sessionStorage]) {
    for (let index = 0; index < storage.length; index += 1) {
      const key = storage.key(index)
      if (!key) continue
      const value = storage.getItem(key)
      if (!value) continue
      if (!/(current|login|session|profile|user|account|creator|author|self|me|mine)/i.test(key)) continue
      collectXiaohongshuAccountNamesFromText(`${key}:${value}`, accountNames)
    }
  }
}

function collectXiaohongshuAccountNamesFromScripts(accountNames) {
  const scripts = Array.from(document.scripts).slice(0, 80)
  for (const script of scripts) {
    const text = script.textContent || ''
    if (!text || !/(currentUser|loginUser|userInfo|userProfile|creatorInfo|nickname)/i.test(text)) continue
    collectXiaohongshuAccountNamesFromText(text.slice(0, 200_000), accountNames)
  }
}

function isLikelyXiaohongshuAccountName(value) {
  const text = normalizeAccountName(value)
  if (text.length < 2 || text.length > 40) return false
  if (/^(首页|发布笔记|笔记管理|数据看板|活动中心|笔记灵感|创作学院|创作百科|上传视频|上传图文|写长文|发播客|新的创作|导入链接|创建|保存|返回)$/.test(text)) return false
  if (/^[\w.-]{2,40}$/.test(text)) return true
  return /[\u4e00-\u9fa5]/.test(text) && !/[，。！？、]/.test(text)
}

function readToutiaoIdentity() {
  const accountIds = new Set()
  const accountNames = new Set()
  const visibleText = normalizeText(document.body?.innerText || document.body?.textContent || '')
  collectToutiaoMediaIdsFromText(visibleText, accountIds)
  collectAccountIdsFromText(visibleText, accountIds)
  collectAccountIdentityFromStorage(accountIds, accountNames)
  collectAccountIdentityFromScripts(accountIds, accountNames)
  return {
    implemented: true,
    accountIds: Array.from(accountIds),
    accountNames: Array.from(accountNames),
    diagnostics: `href=${location.href}; visibleTextLength=${visibleText.length}; accountIds=${Array.from(accountIds).join(',') || '-'}; accountNames=${Array.from(accountNames).join(',') || '-'}`,
  }
}

function collectToutiaoMediaIdsFromText(text, candidates) {
  const patterns = [
    /"media"\s*:\s*\{[\s\S]{0,20000}?"id_str"\s*:\s*"(\d{6,})"/g,
    /"media"\s*:\s*\{[\s\S]{0,20000}?"id"\s*:\s*(\d{6,})/g,
  ]
  for (const pattern of patterns) {
    for (const match of text.matchAll(pattern)) {
      const value = normalizeAccountId(match[1])
      if (value) candidates.add(value)
    }
  }
}

function collectToutiaoAccountNamesFromText(text, accountNames) {
  const patterns = [
    /"media"\s*:\s*\{[\s\S]{0,20000}?"display_name"\s*:\s*"([^"]{2,80})"/g,
    /"user"\s*:\s*\{[\s\S]{0,8000}?"screen_name"\s*:\s*"([^"]{2,80})"/g,
    /"accountName"\s*:\s*"([^"]{2,80})"/g,
    /"account_name"\s*:\s*"([^"]{2,80})"/g,
  ]
  for (const pattern of patterns) {
    for (const match of text.matchAll(pattern)) {
      const value = normalizeAccountName(match[1])
      if (value) accountNames.add(value)
    }
  }
}

function collectAccountIdsFromText(text, candidates) {
  const patterns = [
    /头条号ID[:：]?(\d{6,})/g,
    /头条号[:：]?(\d{6,})/g,
    /user_id["']?[:=]["']?(\d{6,})/g,
    /userId["']?[:=]["']?(\d{6,})/g,
    /account_id["']?[:=]["']?(\d{6,})/g,
    /accountId["']?[:=]["']?(\d{6,})/g,
  ]
  for (const pattern of patterns) {
    for (const match of text.matchAll(pattern)) {
      const value = normalizeAccountId(match[1])
      if (value) candidates.add(value)
    }
  }
}

function collectAccountIdentityFromStorage(accountIds, accountNames) {
  for (const storage of [localStorage, sessionStorage]) {
    for (let index = 0; index < storage.length; index += 1) {
      const key = storage.key(index)
      if (!key) continue
      const value = storage.getItem(key)
      if (!value) continue
      if (!/(current|login|session|profile|user|account|creator|author|media|mp)/i.test(key)) continue
      collectToutiaoMediaIdsFromText(`${key}:${value}`, accountIds)
      collectToutiaoAccountNamesFromText(`${key}:${value}`, accountNames)
      collectAccountIdsFromText(`${key}:${value}`, accountIds)
    }
  }
}

function collectAccountIdentityFromScripts(accountIds, accountNames) {
  const scripts = Array.from(document.scripts).slice(0, 80)
  for (const script of scripts) {
    const text = script.textContent || ''
    if (!text || !/(currentUser|loginUser|media|id_str|头条号ID|头条号)/.test(text)) continue
    collectToutiaoMediaIdsFromText(text.slice(0, 200_000), accountIds)
    collectToutiaoAccountNamesFromText(text.slice(0, 200_000), accountNames)
    collectAccountIdsFromText(text.slice(0, 200_000), accountIds)
  }
}

function normalizeAccountId(value) {
  const id = String(value || '').trim().match(/\d{6,}/)?.[0]
  return id || ''
}

function normalizeAccountName(value) {
  return String(value || '')
    .replace(/\\u([0-9a-fA-F]{4})/g, (_, code) => String.fromCharCode(parseInt(code, 16)))
    .replace(/\s+/g, '')
    .trim()
}

function fillTitle(title, titleElement) {
  if (!title) return false
  const el = titleElement
  if (!el) return false
  setTextValue(el, title)
  return true
}

function fillContent(html, contentElement, fillProfile) {
  if (!html) return false
  const el = contentElement
  if (!el) return false
  if (fillProfile?.platform === 'zhihu') {
    setEditablePlainText(el, htmlToPlainText(html))
    return true
  }
  if (el.isContentEditable || el.getAttribute('contenteditable') === 'true') {
    setEditableHtml(el, html)
    dispatchEditEvents(el)
  } else {
    setTextValue(el, htmlToPlainText(html))
  }
  return true
}

function setEditablePlainText(el, text) {
  el.focus()
  const selection = window.getSelection()
  const range = document.createRange()
  range.selectNodeContents(el)
  selection?.removeAllRanges()
  selection?.addRange(range)
  const ok = document.execCommand?.('insertText', false, text)
  if (!ok) {
    el.textContent = text
    dispatchEditEvents(el)
  }
}

async function ensureEditorVisible(fillProfile) {
  if (findTitleElement(fillProfile) && findContentElement(null, fillProfile)) return
  await maybeOpenPlatformEditor(fillProfile)
  await waitUntilEditorReady(fillProfile, 30_000)
}

async function waitUntilEditorReady(fillProfile, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  let nextOpenAttemptAt = fillProfile.platform === 'xiaohongshu' ? 0 : Number.POSITIVE_INFINITY
  while (Date.now() < deadline) {
    const titleElement = findTitleElement(fillProfile)
    if (titleElement && findContentElement(titleElement, fillProfile)) return
    const loginState = detectLoginState(fillProfile)
    if (loginState.requiresLogin) {
      throw new Error(`平台账号未登录：${loginState.reason}`)
    }
    if (Date.now() >= nextOpenAttemptAt) {
      await maybeOpenPlatformEditor(fillProfile)
      nextOpenAttemptAt = Date.now() + 1800
    }
    await delay(300)
  }
  throw new Error(`等待编辑器超时；${collectDiagnostics()}`)
}

function assertPlatformLoggedIn(fillProfile) {
  const loginState = detectLoginState(fillProfile)
  if (loginState.requiresLogin) {
    throw new Error(`平台账号未登录：${loginState.reason}`)
  }
}

function detectLoginState(fillProfile) {
  const href = location.href
  if (/login|signin|passport|account\/login|sso/i.test(href)) {
    return { requiresLogin: true, reason: `当前地址疑似登录页 ${href}` }
  }
  if (fillProfile.platform === 'xiaohongshu' && isXiaohongshuCreatorShellVisible()) {
    return { requiresLogin: false, reason: 'xiaohongshu_creator_shell' }
  }
  if (findTitleElement(fillProfile) || findContentElement(null, fillProfile)) {
    return { requiresLogin: false, reason: 'editor_ready' }
  }
  const loginText = findVisibleLoginEntry([
    '登录',
    '扫码登录',
    '验证码登录',
    '手机号登录',
    '密码登录',
    '注册/登录',
    '请登录',
  ])
  if (loginText) {
    return { requiresLogin: true, reason: `页面出现登录入口：${loginText}` }
  }
  return { requiresLogin: false, reason: 'not_detected' }
}

async function maybeOpenPlatformEditor(fillProfile) {
  if (findTitleElement(fillProfile) && findContentElement(null, fillProfile)) return

  if (fillProfile.platform === 'xiaohongshu') {
    await maybeSelectXiaohongshuEditorMode(fillProfile)
    if (findTitleElement(fillProfile) && findContentElement(null, fillProfile)) return
    if (location.hostname.endsWith('xiaohongshu.com') && location.pathname.includes('/publish/publish')) return
  }

  const keywords = {
    toutiao: ['发布文章', '写文章', '发文章', '新建文章', '创作文章', '图文', '发头条'],
    zhihu: ['写文章', '发布文章', '创作', '开始写作'],
    xiaohongshu: ['发布笔记', '上传图文', '发布图文', '图文发布', '创建笔记'],
  }[fillProfile.platform] || []
  const clickable = findClickableByText(keywords)
  if (clickable) {
    await clickClosestAction(clickable, { platform: fillProfile.platform })
    showStatus(`已点击入口：${normalizeText(clickable.textContent).slice(0, 20)}`, 'info')
    await delay(1500)
    return
  }

  const publishLink = Array.from(document.querySelectorAll('a[href], button')).find((el) => {
    const href = el.getAttribute('href') || ''
    return href.includes('/graphic/publish')
      || href.includes('/article/publish')
      || href.includes('/write')
      || href.includes('/publish/publish')
  })
  if (publishLink) {
    await clickClosestAction(publishLink, { platform: fillProfile.platform })
    showStatus('已点击发布入口', 'info')
    await delay(1500)
  }
}

async function maybeSelectXiaohongshuEditorMode(fillProfile) {
  if (!location.hostname.endsWith('xiaohongshu.com')) return
  await ensureXiaohongshuEntryPageReady()
  const modes = ['写长文', '上传图文']
  for (const mode of modes) {
    if (await clickXiaohongshuMode(mode, fillProfile)) return
  }
  const menu = findClickableByExactText(['发布笔记']) || findClickableByShortText(['发布笔记'])
  if (menu) {
    await clickClosestAction(menu, { platform: fillProfile.platform })
    showStatus('已展开小红书发布菜单', 'info')
    await delay(800)
    for (const mode of modes) {
      if (await clickXiaohongshuMode(mode, fillProfile)) return
    }
  }
  await maybeStartXiaohongshuLongFormCreation(fillProfile)
  if (findTitleElement(fillProfile) && findContentElement(null, fillProfile)) return
  const diagnostics = collectDiagnostics()
  showStatus(`小红书发布页未找到图文/长文编辑器；${diagnostics}`, 'info')
}

async function ensureXiaohongshuEntryPageReady() {
  if (!location.hostname.endsWith('xiaohongshu.com')) return
  const deadline = Date.now() + 6000
  while (Date.now() < deadline) {
    if (findXiaohongshuEntryElement()) return
    if (findTitleElement(buildFillProfile({ platform: 'xiaohongshu' })) && findContentElement(null, buildFillProfile({ platform: 'xiaohongshu' }))) return
    await delay(300)
  }
  if (location.pathname.includes('/publish/publish') && !findXiaohongshuEntryElement()) {
    showStatus('小红书发布入口未渲染，切换到创作首页重试', 'info')
    location.href = 'https://creator.xiaohongshu.com/new/home'
    await delay(2200)
  }
}

function findXiaohongshuEntryElement() {
  return findClickableByExactText(['发布笔记', '写长文', '上传图文', '新的创作', '新建长文', '新建创作', '发布图文笔记'])
    || findClickableByShortText(['发布笔记', '写长文', '上传图文', '新的创作', '新建长文', '新建创作', '发布图文笔记'])
    || findXiaohongshuCreateButton()?.element
}

async function clickXiaohongshuMode(mode, fillProfile) {
  if (findTitleElement(fillProfile) && findContentElement(null, fillProfile)) return true
  const tab = findClickableByExactText([mode]) || findClickableByShortText([mode])
  if (!tab) return false
  await clickClosestAction(tab, { platform: fillProfile.platform })
  showStatus(`已切换小红书发布模式：${mode}`, 'info')
  await delay(1800)
  await maybeStartXiaohongshuLongFormCreation(fillProfile)
  return Boolean(findTitleElement(fillProfile) && findContentElement(null, fillProfile))
}

async function maybeStartXiaohongshuLongFormCreation(fillProfile) {
  if (findTitleElement(fillProfile) && findContentElement(null, fillProfile)) return
  const createButton = findXiaohongshuCreateButton()
  if (createButton) {
    await clickClosestAction(createButton.element, { ...createButton.options, platform: fillProfile.platform })
    showStatus(`已点击小红书入口：${createButton.label}`, 'info')
    await waitForEditorCandidate(fillProfile, 8000)
  }
  if (findTitleElement(fillProfile) && findContentElement(null, fillProfile)) return
  await maybeConfirmXiaohongshuLongFormCreation(fillProfile)
}

async function waitForEditorCandidate(fillProfile, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (findTitleElement(fillProfile) && findContentElement(null, fillProfile)) return true
    await delay(300)
  }
  return false
}

async function maybeConfirmXiaohongshuLongFormCreation(fillProfile) {
  const confirmButton = findClickableByExactText(['创建']) || findClickableByShortText(['创建'])
  if (!confirmButton) return
  await clickClosestAction(confirmButton, { platform: fillProfile.platform })
  showStatus('已点击小红书长文创建确认', 'info')
  await waitForEditorCandidate(fillProfile, 10_000)
}

function findXiaohongshuCreateButton() {
  const exact = findClickableByExactText(['新的创作', '新建长文', '新建创作', '发布图文笔记'])
  if (exact) {
    return { element: exact, label: normalizeText(exact.textContent || exact.getAttribute('aria-label') || '').slice(0, 20) }
  }

  const candidates = Array.from(document.querySelectorAll('button, a, [role="button"], [role="tab"], [role="menuitem"], div, span, p'))
    .map((el) => ({
      el,
      text: normalizeText(el.textContent || el.getAttribute('aria-label') || ''),
      rect: el.getBoundingClientRect(),
    }))
    .filter(({ el, text, rect }) => {
      if (!isVisibleElement(el) || rect.width <= 0 || rect.height <= 0) return false
      if (rect.width * rect.height > 240_000) return false
      if (text.includes('新的创作')
        || text.includes('新建长文')
        || text.includes('新建创作')
        || text.includes('发布图文笔记')
        || text.includes('去写长文')) return true
      return false
    })
    .sort((left, right) => {
      const leftInteractive = isInteractiveElement(left.el) ? 0 : 1
      const rightInteractive = isInteractiveElement(right.el) ? 0 : 1
      return leftInteractive - rightInteractive
        || left.text.length - right.text.length
        || (left.rect.width * left.rect.height) - (right.rect.width * right.rect.height)
    })

  const candidate = candidates[0]
  if (!candidate) return null
  const options = candidate.text.includes('导入链接') ? { clickRatioX: 0.22 } : {}
  return { element: candidate.el, label: '新的创作', options }
}

async function clickClosestAction(el, options = {}) {
  el.scrollIntoView?.({ block: 'center', inline: 'center' })
  await delay(100)
  const explicit = el.closest('button, a, [role="button"], [role="tab"], [role="menuitem"]')
  const candidates = [explicit, el]
  let parent = el.parentElement
  for (let depth = 0; parent && depth < 5; depth += 1) {
    candidates.push(parent)
    parent = parent.parentElement
  }

  for (const candidate of candidates.filter(Boolean)) {
    if (!isVisibleElement(candidate)) continue
    firePointerClick(candidate, candidate === el ? options : {})
    candidate.click?.()
  }
  if (options.platform === 'xiaohongshu') {
    await requestTrustedClick(el, options)
  }
}

function isInteractiveElement(el) {
  return Boolean(el?.closest?.('button, a, [role="button"], [role="tab"], [role="menuitem"]'))
}

function firePointerClick(el, options = {}) {
  const rect = el.getBoundingClientRect()
  const clientX = rect.left + rect.width * (options.clickRatioX || 0.5)
  const clientY = rect.top + rect.height * (options.clickRatioY || 0.5)
  const eventInit = {
    bubbles: true,
    cancelable: true,
    view: window,
    clientX,
    clientY,
  }
  el.dispatchEvent(new PointerEvent('pointerdown', eventInit))
  el.dispatchEvent(new MouseEvent('mousedown', eventInit))
  el.dispatchEvent(new PointerEvent('pointerup', eventInit))
  el.dispatchEvent(new MouseEvent('mouseup', eventInit))
  el.dispatchEvent(new MouseEvent('click', eventInit))
}

async function requestTrustedClick(el, options = {}) {
  const rect = el.getBoundingClientRect()
  if (rect.width <= 0 || rect.height <= 0) return
  await safeRuntimeRequest({
    type: 'GEO_ENV_TRUSTED_CLICK',
    click: {
      clientX: Math.round(rect.left + rect.width * (options.clickRatioX || 0.5)),
      clientY: Math.round(rect.top + rect.height * (options.clickRatioY || 0.5)),
      label: normalizeText(el.textContent || el.getAttribute('aria-label') || '').slice(0, 30),
    },
  })
}

function findTitleElement(fillProfile) {
  return findFirst(fillProfile.titleSelectors, { rejectTitleLike: false })
}

function findContentElement(titleElement, fillProfile) {
  return findFirst(fillProfile.contentSelectors, { excludeElement: titleElement, rejectTitleLike: true })
}

async function waitUntil(predicate, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (predicate()) return
    await delay(300)
  }
  throw new Error('等待编辑器超时')
}

function findClickableByText(keywords) {
  const elements = Array.from(document.querySelectorAll('button, a, [role="button"], div, span'))
  return elements.find((el) => {
    const text = normalizeText(el.textContent)
    if (!text) return false
    if (!keywords.some((keyword) => text.includes(keyword))) return false
    return isVisibleElement(el)
  })
}

function findClickableByExactText(keywords) {
  const elements = Array.from(document.querySelectorAll('button, a, [role="button"], [role="tab"], [role="menuitem"], div, span'))
  return elements.find((el) => {
    const text = normalizeText(el.textContent || el.getAttribute('aria-label') || '')
    if (!keywords.includes(text)) return false
    return isVisibleElement(el)
  })
}

function findClickableByShortText(keywords) {
  const elements = Array.from(document.querySelectorAll('button, a, [role="button"], [role="tab"], [role="menuitem"], div, span'))
  return elements.find((el) => {
    const text = normalizeText(el.textContent || el.getAttribute('aria-label') || '')
    if (!text || text.length > 16) return false
    if (!keywords.some((keyword) => text.includes(keyword))) return false
    return isVisibleElement(el)
  })
}

function findVisibleLoginEntry(keywords) {
  const elements = Array.from(document.querySelectorAll('button, a, [role="button"], [role="link"], input, div, span'))
  const matched = elements.find((el) => {
    const text = normalizeText(el.value || el.textContent || el.getAttribute('aria-label') || '')
    if (!text) return false
    if (!keywords.some((keyword) => text.includes(keyword))) return false
    if (!isLoginEntryText(text)) return false
    return isVisibleElement(el)
  })
  return matched ? normalizeText(matched.value || matched.textContent || matched.getAttribute('aria-label') || '').slice(0, 30) : ''
}

function isLoginEntryText(text) {
  if (!text || text.includes('退出登录')) return false
  const explicitLoginTexts = [
    '扫码登录',
    '验证码登录',
    '手机号登录',
    '密码登录',
    '注册/登录',
    '请登录',
  ]
  if (explicitLoginTexts.some((item) => text.includes(item))) return true
  if (text === '登录' || text === '注册登录') return true
  if (text.length > 20) return false
  return false
}

function isXiaohongshuCreatorShellVisible() {
  const text = normalizeText(document.body?.innerText || document.body?.textContent || '')
  if (!text.includes('创作服务平台')) return false
  return [
    '发布笔记',
    '笔记管理',
    '数据看板',
    '草稿箱',
    '退出登录',
  ].some((marker) => text.includes(marker))
}

function fillTags(tags, fillProfile) {
  const normalized = Array.isArray(tags) ? tags.filter(Boolean) : []
  if (normalized.length === 0) return false
  const tagInput = findFirst(fillProfile.tagSelectors)
  if (!tagInput) return false
  setTextValue(tagInput, normalized.join(','))
  return true
}

function findFirst(selectors, options = {}) {
  const { excludeElement, rejectTitleLike = false } = options
  for (const selector of selectors) {
    const elements = Array.from(document.querySelectorAll(selector))
    const el = elements.find((candidate) => {
      if (!candidate) return false
      if (excludeElement && candidate === excludeElement) return false
      if (excludeElement && candidate.contains(excludeElement)) return false
      if (excludeElement && excludeElement.contains(candidate)) return false
      if (!isVisibleElement(candidate)) return false
      const placeholder = candidate.getAttribute('placeholder') || ''
      const ariaLabel = candidate.getAttribute('aria-label') || ''
      if (rejectTitleLike && (placeholder + ariaLabel).includes('标题')) return false
      return true
    })
    if (el) return el
  }
  return null
}

function isVisibleElement(el) {
  if (!el) return false
  const rect = el.getBoundingClientRect()
  if (rect.width <= 0 || rect.height <= 0) return false
  const style = window.getComputedStyle(el)
  return style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0'
}

function hasVisibleAncestor(el) {
  let parent = el?.parentElement
  for (let depth = 0; parent && depth < 4; depth += 1) {
    if (isVisibleElement(parent)) return true
    parent = parent.parentElement
  }
  return false
}

function isTopRightAccountElement(el) {
  const rect = el?.getBoundingClientRect?.()
  if (!rect || rect.width <= 0 || rect.height <= 0) return false
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 2
  return centerY <= 180 && centerX >= window.innerWidth * 0.45
}

function normalizeText(value) {
  return String(value || '').replace(/\s+/g, '')
}

function normalizeArticleText(value) {
  return String(value || '')
    .replace(/\u00a0/g, ' ')
    .replace(/\s+/g, '')
    .replace(/[，。！？、,.!?;；:："'“”‘’（）()[\]【】《》<>]/g, '')
    .trim()
}

function setTextValue(el, value) {
  el.focus()
  const descriptor = Object.getOwnPropertyDescriptor(el.__proto__, 'value')
  if (descriptor?.set) {
    descriptor.set.call(el, value)
  } else {
    el.value = value
  }
  dispatchEditEvents(el)
}

function setEditableHtml(el, html) {
  el.focus()
  const selection = window.getSelection()
  const range = document.createRange()
  range.selectNodeContents(el)
  selection?.removeAllRanges()
  selection?.addRange(range)

  const ok = document.execCommand?.('insertHTML', false, html)
  if (!ok) {
    el.innerHTML = html
  }
}

function dispatchEditEvents(el) {
  el.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText' }))
  el.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, inputType: 'insertText' }))
  el.dispatchEvent(new Event('change', { bubbles: true }))
  el.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }))
}

function htmlToPlainText(html) {
  const template = document.createElement('template')
  template.innerHTML = html
  return template.content.textContent || ''
}

function verifyFilled(titleElement, contentElement, expectedTitle, expectedHtml) {
  const titleText = readElementText(titleElement)
  const contentText = readElementText(contentElement)
  const expectedContent = htmlToPlainText(expectedHtml).replace(/\s+/g, '')
  const contentProbe = expectedContent.slice(0, Math.min(24, expectedContent.length))

  if (expectedTitle && !titleText.includes(expectedTitle.trim())) {
    throw new Error(`标题填充后校验失败：命中=${describeElement(titleElement)}，当前="${titleText.slice(0, 60)}"`)
  }
  if (contentProbe && !contentText.replace(/\s+/g, '').includes(contentProbe)) {
    throw new Error(`正文填充后校验失败：命中=${describeElement(contentElement)}，当前="${contentText.slice(0, 80)}"`)
  }
}

function readElementText(el) {
  if (!el) return ''
  if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement) return el.value || ''
  return el.textContent || ''
}

function collectDiagnostics() {
  const inputElements = Array.from(document.querySelectorAll('input, textarea, [contenteditable="true"], div[role="textbox"]'))
  const inputs = inputElements.length
  const inputDetails = inputElements
    .filter(isVisibleElement)
    .map((el) => {
      const placeholder = el.getAttribute('placeholder') || ''
      const ariaLabel = el.getAttribute('aria-label') || ''
      const role = el.getAttribute('role') || ''
      const text = normalizeText(el.textContent || '').slice(0, 12)
      return describeElement(el) + (placeholder || ariaLabel || role || text ? `:${placeholder || ariaLabel || role || text}` : '')
    })
    .slice(0, 8)
  const buttons = Array.from(document.querySelectorAll('button, a, [role="button"]'))
    .map((el) => normalizeText(el.textContent).slice(0, 20))
    .filter(Boolean)
    .slice(0, 12)
  const actionTexts = Array.from(document.querySelectorAll('button, a, [role="button"], [role="tab"], [role="menuitem"], div, span, p'))
    .filter(isVisibleElement)
    .map((el) => normalizeText(el.textContent || el.getAttribute('aria-label') || '').slice(0, 30))
    .filter((text) => text && /新的创作|新建|创建|写长文|上传图文|导入链接|发布笔记/.test(text))
    .slice(0, 12)
  return `href=${location.href}; inputs=${inputs}; inputDetails=${inputDetails.join('|')}; buttons=${buttons.join('|')}; actions=${actionTexts.join('|')}`
}

function describeElement(el) {
  if (!el) return '未命中'
  const tag = el.tagName.toLowerCase()
  const className = String(el.className || '').trim().replace(/\s+/g, '.')
  const placeholder = el.getAttribute('placeholder')
  const contenteditable = el.getAttribute('contenteditable')
  const role = el.getAttribute('role')
  return [
    tag,
    className ? `.${className}` : '',
    placeholder ? `[placeholder="${placeholder}"]` : '',
    role ? `[role="${role}"]` : '',
    contenteditable ? `[contenteditable="${contenteditable}"]` : '',
  ].join('')
}


function showStatus(message, type) {
  const id = 'geo-env-fill-status'
  let el = document.getElementById(id)
  if (!el) {
    el = document.createElement('div')
    el.id = id
    Object.assign(el.style, {
      position: 'fixed',
      right: '18px',
      bottom: '18px',
      zIndex: '2147483647',
      maxWidth: '300px',
      padding: '8px 10px',
      borderRadius: '6px',
      color: '#fff',
      fontSize: '12px',
      lineHeight: '1.35',
      boxShadow: '0 8px 24px rgba(15, 23, 42, .18)',
      whiteSpace: 'pre-wrap',
      wordBreak: 'break-word',
    })
    document.documentElement.appendChild(el)
  }
  const colors = {
    info: '#245bff',
    success: '#15803d',
    error: '#b91c1c',
  }
  el.style.background = colors[type] || colors.info
  el.title = message
  el.textContent = type === 'success'
    ? 'GEO：填充完成，请人工核对后发布'
    : `GEO：${message}`
  if (type === 'success') {
    window.clearTimeout(el._geoHideTimer)
    el._geoHideTimer = window.setTimeout(() => {
      el.style.display = 'none'
    }, 3000)
  } else {
    el.style.display = 'block'
  }
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function safeRuntimeSend(message) {
  try {
    const promise = chrome.runtime?.sendMessage?.(message)
    if (promise?.catch) promise.catch(() => {})
  } catch {
    // The page can keep an old content script after the extension is reloaded.
    // A page refresh injects the current script again.
  }
}

async function safeRuntimeRequest(message) {
  try {
    return await chrome.runtime?.sendMessage?.(message)
  } catch {
    return null
  }
}
