globalThis.__GEO_ENV_READY_REPORT_DELAYS_MS = globalThis.__GEO_ENV_READY_REPORT_DELAYS_MS || [350, 1500, 3500, 7000]
globalThis.__GEO_ENV_ACTIVE_FILL_TASK_CONTEXT = globalThis.__GEO_ENV_ACTIVE_FILL_TASK_CONTEXT || null

if (!globalThis.__GEO_ENV_FILL_CONTENT_SCRIPT_INSTALLED__) {
  globalThis.__GEO_ENV_FILL_CONTENT_SCRIPT_INSTALLED__ = true

  const reportEditorReady = () => {
    if (!isEditorReadyReportLocation()) return
    safeRuntimeSend({
      type: 'GEO_ENV_EDITOR_READY',
      href: location.href,
    })
  }

  for (const delayMs of globalThis.__GEO_ENV_READY_REPORT_DELAYS_MS) {
    setTimeout(() => {
      reportEditorReady()
    }, delayMs)
  }
  globalThis.__GEO_ENV_READY_REPORT_INTERVAL_ID__ = setInterval(reportEditorReady, 15_000)
  window.addEventListener('focus', reportEditorReady)
  window.addEventListener('pageshow', reportEditorReady)
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') reportEditorReady()
  })

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
      if (message?.type === 'GEO_ENV_FILL_TOUTIAO_SCHEDULE_FRAME') {
        return fillToutiaoScheduleDialogAndConfirm(message.value || {}, message.platform || 'toutiao')
      }
      return null
    }

    run()
      .then((result) => sendResponse({ ok: true, result }))
      .catch((error) => {
        const errorPrefix = message?.type === 'GEO_ENV_CHECK_IDENTITY' || message?.type === 'GEO_ENV_READ_IDENTITY'
          ? '登录状态读取失败'
          : '填充失败'
        const errorMessage = error?.message || String(error || '未知错误')
        const failureCode = classifyGeoFillFailureCode(errorMessage, message?.payload?.platform)
        showStatus(`${errorPrefix}：${errorMessage}`, 'error')
        sendResponse({ ok: false, error: errorMessage, failureCode })
      })
    return true
  })
}

function isEditorReadyReportLocation() {
  const href = String(location.href || '')
  if (location.hostname === 'mp.toutiao.com') return href.includes('/graphic/publish')
  if (location.hostname === 'zhuanlan.zhihu.com' || location.hostname === 'www.zhihu.com') {
    return location.pathname.startsWith('/write')
  }
  if (location.hostname === 'creator.xiaohongshu.com') return href.includes('/publish/publish')
  if (location.hostname === 'baijiahao.baidu.com') return href.includes('/builder/rc/edit')
  return false
}

function classifyGeoFillFailureCode(message, platform) {
  const text = String(message || '')
  const explicit = text.match(/^([A-Z0-9_]{3,80})[：:]/)?.[1]
  if (explicit) return explicit
  if (normalizePlatform(platform) === 'xiaohongshu' || text.includes('小红书')) {
    return globalThis.__GEO_XIAOHONGSHU_PLATFORM__?.classifyFailureCode?.(text, 'xiaohongshu')
      || 'XIAOHONGSHU_FILL_FAILED'
  }
  if (normalizePlatform(platform) === 'baijiahao' || text.includes('百家号')) {
    return globalThis.__GEO_BAIJIAHAO_PLATFORM__?.classifyFailureCode?.(text, 'baijiahao')
      || 'BAIJIAHAO_FILL_FAILED'
  }
  if (normalizePlatform(platform) === 'zhihu' || text.includes('知乎') || text.includes('草稿加载中')) {
    return globalThis.__GEO_ZHIHU_PLATFORM__?.classifyFailureCode?.(text, 'zhihu')
      || classifyZhihuFailureCode(text)
  }
  if (text.includes('账号一致性校验失败')) return 'ACCOUNT_MISMATCH'
  if (text.includes('填充令牌已使用') || text.includes('fill token used or expired')) return 'FILL_TOKEN_USED_OR_EXPIRED'
  return 'FILL_FAILED'
}

function classifyZhihuFailureCode(message) {
  const text = String(message || '')
  if (text.includes('知乎平台适配器未加载')) return 'ZHIHU_ADAPTER_NOT_LOADED'
  if (text.includes('草稿加载中') || text.includes('草稿加载未完成') || text.includes('发布被草稿加载阻塞')) return 'ZHIHU_DRAFT_LOADING'
  if (text.includes('发布后未检测到完成状态')) return 'ZHIHU_PUBLISH_NOT_SUBMITTED'
  if (text.includes('封面填充后未检测到封面图片')) return 'ZHIHU_COVER_UPLOAD_NOT_CONFIRMED'
  if (text.includes('封面上传入口未找到')) return 'ZHIHU_COVER_UPLOAD_ENTRY_NOT_FOUND'
  if (text.includes('封面图片上传完成超时')) return 'ZHIHU_COVER_UPLOAD_TIMEOUT'
  if (text.includes('封面文件选项未找到')) return 'ZHIHU_COVER_SELECTION_FAILED'
  if (text.includes('封面文件确认按钮未可用')) return 'ZHIHU_COVER_DIALOG_NOT_READY'
  if (text.includes('发布按钮未找到')) return 'ZHIHU_PUBLISH_BUTTON_NOT_FOUND'
  if (text.includes('账号一致性校验失败')) return 'ACCOUNT_MISMATCH'
  return 'ZHIHU_FILL_FAILED'
}

async function fillPayload(payload) {
  globalThis.__GEO_ENV_ACTIVE_FILL_TASK_CONTEXT = {
    taskId: payload.taskId || null,
    environmentKey: payload.environmentKey || null,
    platform: payload.platform || null,
  }
  showStatus('正在等待编辑器...', 'info')
  const fillProfile = buildFillProfile(payload)
  await ensureEditorVisible(fillProfile)
  normalizeEditorViewport(fillProfile)
  assertPlatformLoggedIn(fillProfile)
  const identityCheck = resolveFillIdentityCheck(payload)
  const titleElement = findTitleElement(fillProfile)
  const expectedTitle = payload.title || payload.articleTitle || ''
  const rawHtml = payload.renderedHtml || payload.html || payload.content || ''
  const coverImageCleanup = removeCoverImageFromContent(rawHtml, resolvePayloadCoverImageUrl(payload))
  const normalizedContent = removeDuplicateLeadingTitle(coverImageCleanup.html, expectedTitle)
  const titleFilled = fillTitle(payload.title || payload.articleTitle || '', titleElement)
  const contentElement = findContentElement(titleElement, fillProfile)
  if (titleElement && contentElement && titleElement === contentElement) {
    throw new Error(`标题和正文命中同一元素：${describeElement(titleElement)}`)
  }
  const contentFilled = await fillContent(normalizedContent.html, contentElement, fillProfile)
  const tagsFilled = fillTags(payload.tags || [], fillProfile)
  if (!titleFilled || !contentFilled) {
    const diagnostics = collectDiagnostics()
    throw new Error(`未找到${!titleFilled ? '标题' : ''}${!titleFilled && !contentFilled ? '和' : ''}${!contentFilled ? '正文' : ''}输入框；${diagnostics}`)
  }
  await delay(500)
  verifyFilled(titleElement, contentElement, expectedTitle, normalizedContent.html)
  const publishOptions = await fillPlatformPublishOptions(payload, fillProfile)
  normalizeEditorViewport(fillProfile)
  const draftState = detectDraftState()
  const identityText = identityCheck.message ? `${identityCheck.message}，` : ''
  const contentText = [
    normalizedContent.removedTitle ? '已去除正文重复标题' : '',
    coverImageCleanup.removed ? '已去除正文重复封面图' : '',
  ].filter(Boolean).join('，')
  const optionText = publishOptions.message ? `${publishOptions.message}，` : ''
  const draftText = draftState.message ? `${draftState.message}，` : ''
  showStatus(`${identityText}${contentText ? `${contentText}，` : ''}${optionText}${draftText}标题和正文已填充：标题=${describeElement(titleElement)}，正文=${describeElement(contentElement)}，请人工核对后发布`, 'success')
  return {
    titleFilled,
    contentFilled,
    tagsFilled,
    publishOptions,
    identityCheck,
    draftState,
    removedDuplicateTitle: normalizedContent.removedTitle,
    removedDuplicateCoverImage: coverImageCleanup.removed,
    titleElement: describeElement(titleElement),
    contentElement: describeElement(contentElement),
  }
}

async function fillPlatformPublishOptions(payload, fillProfile) {
  const adapter = resolvePublishOptionsAdapter(fillProfile.platform)
  if (!adapter?.fillPublishOptions) {
    return { filled: false, message: '' }
  }
  return adapter.fillPublishOptions(payload, fillProfile)
}

function resolvePublishOptionsAdapter(platform) {
  const normalized = normalizePlatform(platform)
  if (normalized === 'zhihu') return ZHIHU_PUBLISH_OPTIONS_ADAPTER
  if (normalized === 'xiaohongshu') return XIAOHONGSHU_PUBLISH_OPTIONS_ADAPTER
  if (normalized === 'baijiahao') return BAIJIAHAO_PUBLISH_OPTIONS_ADAPTER
  if (normalized === 'toutiao') return TOUTIAO_PUBLISH_OPTIONS_ADAPTER
  return null
}

var ZHIHU_PUBLISH_OPTIONS_ADAPTER = globalThis.__GEO_ZHIHU_PLATFORM__?.createPublishOptionsAdapter?.({
  fillCover: fillZhihuCover,
  hasCoverImage: hasZhihuCoverImage,
  publishArticle: publishZhihuArticle,
  describeSettings: describeZhihuPublishSettings,
}) || {
  platform: 'zhihu',
  fillPublishOptions: async () => {
    throw new Error('ZHIHU_ADAPTER_NOT_LOADED：知乎平台适配器未加载，请重新加载扩展')
  },
}

var TOUTIAO_PUBLISH_OPTIONS_ADAPTER = {
  platform: 'toutiao',
  fillPublishOptions: fillToutiaoPublishOptions,
}

var XIAOHONGSHU_PUBLISH_OPTIONS_ADAPTER = globalThis.__GEO_XIAOHONGSHU_PLATFORM__?.createPublishOptionsAdapter?.() || {
  platform: 'xiaohongshu',
  fillPublishOptions: async () => {
    throw new Error('XIAOHONGSHU_ADAPTER_NOT_LOADED：小红书平台适配器未加载，请重新加载扩展')
  },
}

var BAIJIAHAO_PUBLISH_OPTIONS_ADAPTER = globalThis.__GEO_BAIJIAHAO_PLATFORM__?.createPublishOptionsAdapter?.({
  waitForCondition,
  uploadCoverImageFromLocalHelper,
  delay,
  clickTrustedActionOnce,
  findVisibleTextElement,
  nearestLargeContainer,
  normalizeText,
  isVisibleElement,
  collectVisibleActionElements,
}) || {
  platform: 'baijiahao',
  fillPublishOptions: async () => {
    throw new Error('BAIJIAHAO_ADAPTER_NOT_LOADED：百家号平台适配器未加载，请重新加载扩展')
  },
}

async function fillToutiaoPublishOptions(payload, fillProfile) {
  const options = resolveToutiaoPublishOptions(payload)
  const actions = []
  let scheduled = false
  if (options.coverMode || options.coverImageUrl) {
    const cover = await fillToutiaoCover(options, fillProfile.platform)
    if (cover.filled) actions.push(cover.message)
  }
  if (options.locationName) {
    const locationResult = await fillToutiaoLocation(options.locationName, fillProfile.platform)
    if (locationResult.filled) actions.push(locationResult.message)
  }
  if (options.scheduledAt) {
    const scheduleResult = await fillToutiaoScheduledPublish(options.scheduledAt, fillProfile.platform, {
      title: payload.title || payload.articleTitle || '',
      locationName: options.locationName,
      coverImageUrl: options.coverImageUrl,
    })
    if (scheduleResult.filled) actions.push(scheduleResult.message)
    scheduled = Boolean(scheduleResult.scheduled)
    if (scheduleResult.publishVerification) {
      return {
        filled: actions.length > 0,
        scheduled,
        publishVerification: scheduleResult.publishVerification,
        message: actions.join('，'),
      }
    }
  }
  return {
    filled: actions.length > 0,
    scheduled,
    message: actions.join('，'),
  }
}

function resolveToutiaoPublishOptions(payload) {
  const profileOptions = payload.profile?.platformOptions || {}
  const platformOptions = payload.platformOptions || {}
  const toutiaoOptions = payload.toutiaoOptions || platformOptions.toutiao || profileOptions.toutiao || {}
  const coverImageUrl = firstText(
    payload.coverImageUrl,
    platformOptions.coverImageUrl,
    profileOptions.coverImageUrl,
    toutiaoOptions.coverImageUrl,
  )
  const explicitCoverMode = normalizeCoverMode(firstText(
    payload.coverMode,
    platformOptions.coverMode,
    profileOptions.coverMode,
    toutiaoOptions.coverMode,
  ))
  return {
    coverImageUrl,
    coverMode: coverImageUrl ? 'single' : (explicitCoverMode || 'none'),
    locationName: firstText(
      payload.locationName,
      payload.location,
      platformOptions.locationName,
      platformOptions.location,
      profileOptions.locationName,
      profileOptions.location,
      toutiaoOptions.locationName,
      toutiaoOptions.location,
    ),
    scheduledAt: firstText(
      payload.scheduledAt,
      payload.platformScheduledAt,
      platformOptions.scheduledAt,
      platformOptions.platformScheduledAt,
      profileOptions.scheduledAt,
      profileOptions.platformScheduledAt,
      toutiaoOptions.scheduledAt,
      toutiaoOptions.platformScheduledAt,
    ),
  }
}

function resolvePayloadCoverImageUrl(payload) {
  const profileOptions = payload.profile?.platformOptions || {}
  const platformOptions = payload.platformOptions || {}
  const platformSpecific = platformOptions[normalizePlatform(payload.platform)] || profileOptions[normalizePlatform(payload.platform)] || {}
  return firstText(
    payload.coverImageUrl,
    platformOptions.coverImageUrl,
    profileOptions.coverImageUrl,
    platformSpecific.coverImageUrl,
  )
}

function firstText(...values) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return ''
}

function normalizePlatform(value) {
  const text = String(value || '').trim().toLowerCase()
  const aliases = {
    '头条': 'toutiao',
    '今日头条': 'toutiao',
    'toutiao': 'toutiao',
    '知乎': 'zhihu',
    'zhihu': 'zhihu',
    '小红书': 'xiaohongshu',
    'xiaohongshu': 'xiaohongshu',
    'xhs': 'xiaohongshu',
    '百家号': 'baijiahao',
    'baijiahao': 'baijiahao',
  }
  return aliases[text] || text
}

function normalizeCoverMode(value) {
  const text = normalizeText(value).toLowerCase()
  if (!text) return ''
  if (['none', 'no', 'false', '无封面'].includes(text)) return 'none'
  if (['three', 'triple', '3', '三图'].includes(text)) return 'triple'
  if (['single', 'one', '1', '单图'].includes(text)) return 'single'
  return ''
}

function zhihuDomAdapter() {
  if (globalThis.__GEO_ZHIHU_DOM_ADAPTER__) return globalThis.__GEO_ZHIHU_DOM_ADAPTER__
  const factory = globalThis.__GEO_ZHIHU_PLATFORM__?.createDomAdapter
  if (typeof factory !== 'function') {
    throw new Error('ZHIHU_ADAPTER_NOT_LOADED：知乎平台 DOM 适配器未加载，请重新加载扩展')
  }
  globalThis.__GEO_ZHIHU_DOM_ADAPTER__ = factory({
    waitForCondition,
    uploadCoverImageFromLocalHelper,
    delay,
    clickTrustedActionOnce,
    requestTrustedClickAt,
    firePointerClick,
    requestTrustedClick,
    findVisibleTextElement,
    findTextElementInRoot,
    nearestLargeContainer,
    normalizeText,
    findLatestFileInput,
    isVisibleElement,
    findFirst,
    collectVisibleActionElements,
    clearEditableTextWithSelection,
    focusEditableElement,
    dispatchPasteIntoEditable,
    dispatchEditEvents,
    readIdentity: readZhihuIdentity,
    describeLastTrustedClick,
    describeSettings: describeZhihuPublishSettings,
  })
  return globalThis.__GEO_ZHIHU_DOM_ADAPTER__
}

async function fillZhihuCover(coverImageUrl, platform) {
  return zhihuDomAdapter().fillCover(coverImageUrl, platform)
}

async function waitForZhihuCoverUploadedOrChooser() {
  return zhihuDomAdapter().waitForCoverUploadedOrChooser()
}

async function closeZhihuCoverFileChooserIfOpen(platform) {
  return zhihuDomAdapter().closeCoverFileChooserIfOpen(platform)
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

function findZhihuCoverFileChooserDialog() {
  return zhihuDomAdapter().findCoverFileChooserDialog()
}

function findZhihuUploadedCoverFileRow(dialog) {
  return zhihuDomAdapter().findUploadedCoverFileRow(dialog)
}

function findZhihuCoverFileChooserConfirm(dialog) {
  return zhihuDomAdapter().findCoverFileChooserConfirm(dialog)
}

async function publishZhihuArticle(platform, context = {}) {
  return zhihuDomAdapter().publishArticle(platform, context)
}

async function waitForZhihuDraftReadyBeforePublish(platform) {
  return zhihuDomAdapter().waitForDraftReadyBeforePublish(platform)
}

async function waitForZhihuPublishAttemptOutcome(platform, context = {}) {
  return zhihuDomAdapter().waitForPublishAttemptOutcome(platform, context)
}

function findZhihuCoverUploadEntry() {
  return zhihuDomAdapter().findCoverUploadEntry()
}

function hasZhihuCoverImage() {
  return zhihuDomAdapter().hasCoverImage()
}

function findZhihuPublishButton() {
  return zhihuDomAdapter().findPublishButton()
}

async function clickZhihuPublishAction(el, platform) {
  return zhihuDomAdapter().clickPublishAction(el, platform)
}

function hasZhihuPublishProgressSignal() {
  return zhihuDomAdapter().hasPublishProgressSignal()
}

function findZhihuPublishConfirmButton(initialButton) {
  return zhihuDomAdapter().findPublishConfirmButton(initialButton)
}

function verifyZhihuPublishSubmitted(context = {}) {
  return zhihuDomAdapter().verifyPublishSubmitted(context)
}

function isZhihuEditorStillOpen(text = normalizeText(document.body?.innerText || '')) {
  return zhihuDomAdapter().isEditorStillOpen(text)
}

function findZhihuDraftLoadingDialog() {
  return zhihuDomAdapter().findDraftLoadingDialog()
}

async function closeZhihuDraftLoadingDialog(dialog, platform) {
  return zhihuDomAdapter().closeDraftLoadingDialog(dialog, platform)
}

async function setZhihuEditablePlainText(contentElement, text) {
  return zhihuDomAdapter().setEditablePlainText(contentElement, text)
}

function describeZhihuPublishSettings() {
  const text = normalizeText(document.body?.innerText || '').slice(0, 300)
  const chooser = findZhihuCoverFileChooserDialog()
  const chooserText = normalizeText(chooser?.textContent || '').slice(0, 220)
  const actions = collectVisibleActionElements()
    .map((item) => `${item.text}@${Math.round(item.rect.left)},${Math.round(item.rect.top)},${Math.round(item.rect.width)}x${Math.round(item.rect.height)}`)
    .slice(0, 30)
    .join('|')
  const fileInputs = Array.from(document.querySelectorAll('input[type="file"]')).map((input, index) => ({
    index,
    filesLength: input.files?.length || 0,
    fileName: input.files?.[0]?.name || '',
    accept: input.getAttribute('accept') || '',
  }))
  return `zhihuText=${text || '-'}; chooserText=${chooserText || '-'}; lastTrustedClick=${describeLastTrustedClick()}; actions=${actions || '-'}; fileInputs=${JSON.stringify(fileInputs).slice(0, 300)}`
}

async function fillToutiaoCover(options, platform) {
  const coverMode = options.coverMode || (options.coverImageUrl ? 'single' : '')
  if (!coverMode) return { filled: false, message: '' }

  const optionText = coverMode === 'none' ? '无封面' : coverMode === 'triple' ? '三图' : '单图'
  await scrollToToutiaoSection('展示封面')
  await clickToutiaoOptionNearLabel('展示封面', optionText, platform)

  if (coverMode === 'none') {
    return { filled: true, message: '已选择无封面' }
  }
  if (!options.coverImageUrl) {
    return { filled: true, message: `已选择${optionText}` }
  }

  const alreadyUploaded = hasToutiaoCoverThumbnail()
  if (!alreadyUploaded || options.coverImageUrl) {
    await openToutiaoCoverDrawer(platform)
    await uploadToutiaoCoverImage(options.coverImageUrl, platform)
    await confirmToutiaoCoverDrawer(platform)
    await waitForCondition(() => hasToutiaoCoverThumbnail(), 8000, '等待头条封面回填超时')
  }
  return { filled: true, message: '已上传封面' }
}

async function fillToutiaoLocation(locationName, platform) {
  const locationTerms = buildToutiaoLocationTerms(locationName)
  const searchName = locationTerms[0] || locationName
  await scrollToToutiaoSection('添加位置')
  const label = findVisibleTextElement('添加位置')
  const opener = findToutiaoLocationOpener(label)
  if (opener) {
    await clickClosestAction(opener, { platform })
    await delay(600)
  }

  const input = await waitForCondition(
    () => findToutiaoLocationInput(label),
    6000,
    `头条添加位置输入框未找到；${describeToutiaoLocationArea(label)}`,
  )
  if (!input) {
    throw new Error(`头条添加位置输入框未找到；${describeToutiaoLocationArea(label)}`)
  }
  setToutiaoLocationInputValue(input, searchName)
  input.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, key: searchName[0] || 'a' }))
  input.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: searchName[0] || 'a' }))
  await delay(900)
  const option = await waitForCondition(
    () => findToutiaoLocationOption(locationTerms),
    10000,
    `头条添加位置未匹配到选项：${locationName}；search=${searchName}；terms=${locationTerms.join('|')}；${describeToutiaoLocationArea(label)}`,
  )
  await clickClosestAction(option, { platform })
  await delay(800)
  return { filled: true, message: `已选择位置=${searchName}` }
}

async function fillToutiaoScheduledPublish(scheduledAt, platform, context = {}) {
  const value = normalizeToutiaoScheduleDateTime(scheduledAt)
  if (!value.full) throw new Error(`头条定时发布时间无效：${scheduledAt}`)
  if (isToutiaoScheduleTimeExpired(value)) {
    throw new Error(`头条定时发布时间已过期或过近：${value.full}，请重新创建未来时间的排期`)
  }
  await closeToutiaoPreviewDialogIfOpen(platform)
  window.scrollTo(0, document.body.scrollHeight)
  await delay(500)
  const button = await waitForCondition(
    () => findToutiaoPublishActionButton('定时发布'),
    8000,
    `头条定时发布按钮未找到；${describeToutiaoPublishActions()}`,
  )
  await clickTrustedActionOnce(button, { platform })
  await delay(800)
  if (isToutiaoPreviewLayerOpen() || findToutiaoPreviewBackButton() || findToutiaoPreviewConfirmButton()) {
    const confirmPreview = await waitForCondition(
      () => findToutiaoPreviewConfirmButton(),
      5000,
      `头条定时发布预览确认按钮未找到；target=${value.full}；${describeToutiaoScheduleDialog()}`,
    )
    await clickTrustedActionOnce(confirmPreview, { platform })
    await delay(800)
  }
  let filled = false
  try {
    filled = await waitForCondition(
      () => fillToutiaoScheduleInputs(value, platform),
      10000,
      `头条定时发布弹窗时间输入框未找到；target=${value.full}；${describeToutiaoScheduleDialog()}`,
    )
  } catch (error) {
    const frameResult = await fillToutiaoScheduleAcrossFrames(value, platform)
    if (frameResult?.scheduled) {
      const publishVerification = await verifyToutiaoScheduledWorkInList(value, context)
      return { filled: true, scheduled: true, publishVerification, message: `已设置定时发布=${value.full}` }
    }
    throw error
  }
  if (!filled) throw new Error(`头条定时发布时间未填写：${value.full}`)
  await delay(500)
  const confirm = await waitForCondition(
    () => findToutiaoScheduleConfirmButton(),
    8000,
    `头条定时发布确认按钮未找到；${describeToutiaoScheduleDialog()}`,
  )
  await clickToutiaoScheduleConfirmButton(confirm, value, platform)
  const publishVerification = await verifyToutiaoScheduledWorkInList(value, context)
  return { filled: true, scheduled: true, publishVerification, message: `已设置定时发布=${value.full}` }
}

async function fillToutiaoScheduleDialogAndConfirm(value, platform) {
  const filled = await waitForCondition(
    () => fillToutiaoScheduleInputs(value, platform),
    8000,
    `头条定时发布跨 frame 弹窗时间控件未找到；target=${value.full}；${describeToutiaoScheduleDialog()}`,
  )
  if (!filled) throw new Error(`头条定时发布时间未填写：${value.full}`)
  await delay(500)
  const confirm = await waitForCondition(
    () => findToutiaoScheduleConfirmButton(),
    8000,
    `头条定时发布跨 frame 确认按钮未找到；${describeToutiaoScheduleDialog()}`,
  )
  await clickToutiaoScheduleConfirmButton(confirm, value, platform)
  return { filled: true, scheduled: true, message: `已设置定时发布=${value.full}` }
}

async function fillToutiaoScheduleAcrossFrames(value, platform) {
  const response = await safeRuntimeRequest({
    type: 'GEO_ENV_FILL_TOUTIAO_SCHEDULE_ACROSS_FRAMES',
    value,
    platform,
  })
  return response?.ok ? response.result : null
}

async function closeToutiaoPreviewDialogIfOpen(platform) {
  const back = findToutiaoPreviewBackButton()
  if (!back) return false
  await clickSingleAction(back, { platform })
  await delay(800)
  return true
}

function normalizeToutiaoScheduleDateTime(value) {
  const text = String(value || '').trim().replace('T', ' ')
  const match = text.match(/(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{1,2})/)
  if (!match) return { full: '', date: '', time: '' }
  const yyyy = match[1]
  const mm = match[2].padStart(2, '0')
  const dd = match[3].padStart(2, '0')
  const hh = match[4].padStart(2, '0')
  const min = match[5].padStart(2, '0')
  return {
    full: `${yyyy}-${mm}-${dd} ${hh}:${min}`,
    date: `${yyyy}-${mm}-${dd}`,
    monthDay: `${mm}月${dd}日`,
    time: `${hh}:${min}`,
    hour: String(Number(hh)),
    minute: String(Number(min)),
  }
}

function isToutiaoScheduleTimeExpired(value) {
  const target = new Date(`${value.full.replace(' ', 'T')}:00`)
  if (!Number.isFinite(target.getTime())) return true
  return target.getTime() - Date.now() < 2 * 60 * 60 * 1000
}

async function fillToutiaoScheduleInputs(value, platform) {
  const dialog = findToutiaoScheduleDialog()
  if (!dialog) return false
  if (await fillToutiaoScheduleSelectControls(dialog, value, platform)) return true
  const root = dialog
  const inputs = Array.from(root.querySelectorAll('input, textarea, [contenteditable="true"]'))
    .filter(isVisibleElement)
    .filter((el) => !isLikelyArticleEditor(el))
    .filter(isLikelyToutiaoScheduleInput)
  if (!inputs.length) return false
  let count = 0
  for (const input of inputs) {
    const hint = toutiaoInputHint(input)
    if (/date|日期|年月日/.test(hint)) {
      setTextValue(input, value.date)
      count += 1
      continue
    }
    if (/time|时间|时分/.test(hint)) {
      setTextValue(input, value.time)
      count += 1
      continue
    }
    if (/发布|定时|选择|预约/.test(hint)) {
      setTextValue(input, value.full)
      count += 1
    }
  }
  return count > 0
}

async function fillToutiaoScheduleSelectControls(dialog, value, platform) {
  if (setNativeToutiaoScheduleSelects(dialog, value)) return true
  const targets = [value.monthDay, value.hour, value.minute]
  for (let attempt = 0; attempt < 4; attempt += 1) {
    const initialControls = collectToutiaoScheduleDropdownControls(dialog)
    if (initialControls.length < 3) return false
    for (let index = 0; index < targets.length; index += 1) {
      const controls = collectToutiaoScheduleDropdownControls(dialog)
      const control = controls[index]
      const target = targets[index]
      if (!control || !target) return false
      const current = getToutiaoScheduleControlValue(control)
      if (sameToutiaoScheduleValue(current, target)) continue
      await clickTrustedActionOnce(control, { platform })
      await delay(300)
      const option = await waitForCondition(
        () => findOrRevealToutiaoScheduleOption(target, control),
        5000,
        `头条定时发布下拉选项未找到：${target}；${describeToutiaoScheduleDialog()}`,
      )
      await clickTrustedActionOnce(findToutiaoScheduleOptionClickTarget(option), { platform })
      await delay(index === 0 ? 700 : 450)
    }
    if (isToutiaoScheduleSelectValueMatched(dialog, value) || isToutiaoSchedulePreviewTimeMatched(dialog, value)) return true
  }
  throw new Error(`头条定时发布下拉控件选择后未保持目标时间：${value.full}；${describeToutiaoScheduleDialog()}`)
}

function setNativeToutiaoScheduleSelects(dialog, value) {
  const selects = Array.from(dialog.querySelectorAll('select')).filter(isVisibleElement)
  if (selects.length < 3) return false
  const targets = [value.monthDay, value.hour, value.minute]
  for (let index = 0; index < 3; index += 1) {
    const select = selects[index]
    const target = normalizeText(targets[index])
    const option = Array.from(select.options || []).find((item) => normalizeText(item.textContent || item.value) === target)
    if (!option) return false
    select.value = option.value
    select.dispatchEvent(new Event('input', { bubbles: true }))
    select.dispatchEvent(new Event('change', { bubbles: true }))
  }
  return true
}

function isToutiaoScheduleSelectValueMatched(dialog, value) {
  const controls = collectToutiaoScheduleDropdownControls(dialog)
  if (controls.length < 3) return false
  const targets = [value.monthDay, value.hour, value.minute]
  return targets.every((target, index) => sameToutiaoScheduleValue(getToutiaoScheduleControlValue(controls[index]), target))
}

function isToutiaoSchedulePreviewTimeMatched(dialog, value) {
  const text = normalizeText(dialog?.textContent || '')
  const target = normalizeText(value.full)
  if (!text || !target) return false
  const compactTarget = target.replace('-', '').replace('-', '').replace(':', '')
  const patterns = [
    target,
    value.full.replace(' ', ''),
    value.full.replace(' ', '').replace(':', ''),
    compactTarget,
  ].map(normalizeText)
  return patterns.some((pattern) => pattern && text.includes(pattern))
}

function getToutiaoScheduleControlValue(control) {
  return normalizeText(control?.textContent || control?.getAttribute?.('aria-label') || control?.getAttribute?.('title') || '')
}

function collectToutiaoScheduleDropdownControls(dialog) {
  const scope = dialog || document
  const candidates = Array.from(scope.querySelectorAll('button, [role="button"], [role="combobox"], [aria-haspopup], div, span'))
    .filter(isVisibleElement)
    .map((el) => {
      const clickable = el.closest('button, [role="button"], [role="combobox"], [aria-haspopup]') || el
      const clickableRect = clickable.getBoundingClientRect()
      const ownRect = el.getBoundingClientRect()
      const clickableText = normalizeText(clickable.textContent || clickable.getAttribute('aria-label') || clickable.getAttribute('title') || '')
      const ownText = normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '')
      const token = extractToutiaoScheduleControlToken(ownText) || extractToutiaoScheduleControlToken(clickableText)
      const rect = isLikelyToutiaoScheduleClickableRect(clickableRect) ? clickableRect : ownRect
      return { el: clickable, rect, text: clickableText || ownText, token }
    })
    .filter((item) => item.rect.width > 0 && item.rect.width <= 220 && item.rect.height >= 12 && item.rect.height <= 72)
    .filter((item) => item.token)
    .filter((item) => !/取消|发布|预览|定时/.test(item.text))
    .filter((item, index, items) => items.findIndex((other) => (
      other.token === item.token
      && sameScheduleControlRect(other.rect, item.rect)
    )) === index)
    .filter((item, index, items) => items.findIndex((other) => (
      other.el === item.el
      && other.token === item.token
      && Math.abs(verticalCenter(other.rect) - verticalCenter(item.rect)) <= 2
    )) === index)
  if (!candidates.length) return []
  const modalRect = dialog?.getBoundingClientRect?.()
  const firstRow = candidates
    .filter((item) => !modalRect || (
      item.rect.left >= modalRect.left - 4
      && item.rect.right <= modalRect.right + 4
      && item.rect.top >= modalRect.top
      && item.rect.bottom <= modalRect.bottom
    ))
    .sort((left, right) => left.rect.top - right.rect.top || left.rect.left - right.rect.left)
  for (const item of firstRow) {
    const row = firstRow
      .filter((candidate) => Math.abs(verticalCenter(candidate.rect) - verticalCenter(item.rect)) <= 18)
      .sort((left, right) => left.rect.left - right.rect.left)
    const controls = pickToutiaoScheduleControlRow(row)
    const texts = controls.map((candidate) => candidate.token)
    const rowWidth = controls.length >= 3 ? controls[2].rect.right - controls[0].rect.left : 0
    if (controls.length >= 3
        && texts.some((text) => /月\d{1,2}日/.test(text))
        && texts.filter((text) => /^\d{1,2}$/.test(text)).length >= 2
        && rowWidth >= 120
        && rowWidth <= 520) {
      return controls.slice(0, 3).map((candidate) => candidate.el)
    }
  }
  return []
}

function sameScheduleControlRect(left, right) {
  return Math.abs(left.left - right.left) <= 3
    && Math.abs(left.top - right.top) <= 3
    && Math.abs(left.width - right.width) <= 4
    && Math.abs(left.height - right.height) <= 4
}

function isLikelyToutiaoScheduleClickableRect(rect) {
  return rect
    && rect.width > 0
    && rect.width <= 220
    && rect.height >= 12
    && rect.height <= 72
}

function pickToutiaoScheduleControlRow(row) {
  const month = row.find((item) => /月\d{1,2}日/.test(item.token))
  if (!month) return []
  const numbers = row
    .filter((item) => /^\d{1,2}$/.test(item.token) && item.rect.left >= month.rect.right + 12)
    .sort((left, right) => left.rect.left - right.rect.left)
  const hour = numbers[0]
  if (!hour) return []
  const minute = numbers.find((item) => item.rect.left >= hour.rect.right + 12)
  if (!minute) return []
  return [month, hour, minute]
}

function extractToutiaoScheduleControlToken(text) {
  const value = normalizeText(text)
  if (!value || /取消|发布|预览|定时|北京时间|请选择/.test(value)) return ''
  const monthDay = value.match(/\d{1,2}月\d{1,2}日/)
  if (monthDay) return monthDay[0]
  const numericWithUnit = value.match(/^(\d{1,2})(?:时|分)$/)
  if (numericWithUnit) return numericWithUnit[1]
  const numeric = value.match(/^\d{1,2}$/)
  return numeric ? numeric[0] : ''
}

function findToutiaoScheduleOption(target, control) {
  const controlRect = control?.getBoundingClientRect?.()
  return Array.from(document.querySelectorAll('button, [role="button"], [role="option"], [role="menuitem"], li, div, span'))
    .filter(isVisibleElement)
    .map((el) => ({
      el,
      rect: el.getBoundingClientRect(),
      text: normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || ''),
    }))
    .filter((item) => sameToutiaoScheduleOptionValue(item.text, target))
    .filter((item) => !controlRect || item.rect.top >= controlRect.top - 20)
    .sort((left, right) => {
      const leftDistance = controlRect ? Math.hypot(left.rect.left - controlRect.left, left.rect.top - controlRect.top) : 0
      const rightDistance = controlRect ? Math.hypot(right.rect.left - controlRect.left, right.rect.top - controlRect.top) : 0
      return leftDistance - rightDistance || left.text.length - right.text.length
    })[0]?.el || null
}

function sameToutiaoScheduleOptionValue(left, right) {
  const leftToken = normalizeToutiaoScheduleOptionToken(left)
  const rightToken = normalizeToutiaoScheduleToken(right)
  return Boolean(leftToken && rightToken && leftToken === rightToken)
}

function normalizeToutiaoScheduleOptionToken(value) {
  const text = normalizeText(value)
  if (!text) return ''
  const monthDays = text.match(/\d{1,2}月\d{1,2}日/g) || []
  if (monthDays.length === 1 && text === monthDays[0]) return monthDays[0]
  if (monthDays.length > 1) return ''
  const numericWithUnit = text.match(/^(\d{1,2})(?:时|分)$/)
  if (numericWithUnit) return String(Number(numericWithUnit[1]))
  const numeric = text.match(/^\d{1,2}$/)
  if (numeric) return String(Number(numeric[0]))
  return ''
}

function findToutiaoScheduleOptionClickTarget(option) {
  if (!option) return null
  let current = option
  for (let depth = 0; current && depth < 4; depth += 1) {
    const text = normalizeText(current.textContent || current.getAttribute?.('aria-label') || current.getAttribute?.('title') || '')
    const rect = current.getBoundingClientRect()
    if (normalizeToutiaoScheduleOptionToken(text) && rect.width <= 220 && rect.height <= 72) {
      return current
    }
    current = current.parentElement
  }
  return option
}

function sameToutiaoScheduleValue(left, right) {
  const leftToken = normalizeToutiaoScheduleToken(left)
  const rightToken = normalizeToutiaoScheduleToken(right)
  return Boolean(leftToken && rightToken && leftToken === rightToken)
}

function normalizeToutiaoScheduleToken(value) {
  const token = extractToutiaoScheduleControlToken(value)
  if (!token) return ''
  if (/^\d{1,2}$/.test(token)) return String(Number(token))
  return token
}

function findOrRevealToutiaoScheduleOption(target, control) {
  const visible = findToutiaoScheduleOption(target, control)
  if (visible) return visible
  const dropdowns = collectScrollableToutiaoDropdowns(control)
  for (const dropdown of dropdowns) {
    const before = dropdown.scrollTop
    dropdown.scrollTop = 0
    const topVisible = findToutiaoScheduleOption(target, control)
    if (topVisible) return topVisible
    dropdown.scrollTop = Math.max(0, before - dropdown.clientHeight)
    const prevVisible = findToutiaoScheduleOption(target, control)
    if (prevVisible) return prevVisible
    dropdown.scrollTop = before + dropdown.clientHeight
    const nextVisible = findToutiaoScheduleOption(target, control)
    if (nextVisible) return nextVisible
  }
  return null
}

function collectScrollableToutiaoDropdowns(control) {
  const controlRect = control?.getBoundingClientRect?.()
  return Array.from(document.querySelectorAll('div, ul, ol'))
    .filter(isVisibleElement)
    .filter((el) => el.scrollHeight > el.clientHeight + 8)
    .map((el) => ({ el, rect: el.getBoundingClientRect() }))
    .filter((item) => !controlRect || (
      item.rect.top >= controlRect.top
      && item.rect.left >= controlRect.left - 80
      && item.rect.left <= controlRect.right + 80
      && item.rect.height <= 420
    ))
    .sort((left, right) => {
      const leftDistance = controlRect ? Math.hypot(left.rect.left - controlRect.left, left.rect.top - controlRect.top) : 0
      const rightDistance = controlRect ? Math.hypot(right.rect.left - controlRect.left, right.rect.top - controlRect.top) : 0
      return leftDistance - rightDistance
    })
    .map((item) => item.el)
    .slice(0, 3)
}

function isLikelyToutiaoScheduleInput(input) {
  const hint = toutiaoInputHint(input)
  if (/标题|正文|文章标题|请输入文章标题|创作|内容/.test(hint)) return false
  if (input instanceof HTMLTextAreaElement) return false
  if (input.isContentEditable || input.getAttribute?.('contenteditable') === 'true') return false
  return /datetime-local|date|time|日期|年月日|时间|时分|发布|定时|选择|预约/.test(hint)
}

function toutiaoInputHint(input) {
  return normalizeText([
    input.getAttribute('type') || '',
    input.getAttribute('placeholder') || '',
    input.getAttribute('aria-label') || '',
    input.getAttribute('title') || '',
    input.getAttribute('class') || '',
    input.getAttribute('role') || '',
    input.textContent || '',
  ].join(' '))
}

function findToutiaoPublishActionButton(text) {
  const target = normalizeText(text)
  if (target === '定时发布') {
    return findToutiaoBottomPublishActionButton(target)
  }
  const preview = findToutiaoPreviewDialog()
  return Array.from(document.querySelectorAll('button, [role="button"], a, div, span'))
    .filter(isVisibleElement)
    .filter((el) => !preview || !preview.contains(el))
    .map((el) => {
      const clickable = el.closest('button, a, [role="button"]') || el
      const rect = clickable.getBoundingClientRect()
      return {
        el: clickable,
        value: normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || ''),
        rect,
        interactive: isInteractiveElement(clickable) ? 0 : 1,
      }
    })
    .filter((item) => item.value === target)
    .filter((item, index, items) => items.findIndex((other) => other.el === item.el) === index)
    .sort((left, right) => {
      const leftBottomBar = isLikelyBottomPublishBarButton(left) ? 0 : 1
      const rightBottomBar = isLikelyBottomPublishBarButton(right) ? 0 : 1
      return leftBottomBar - rightBottomBar
        || left.interactive - right.interactive
        || right.rect.top - left.rect.top
        || left.value.length - right.value.length
    })[0]?.el || null
}

function findToutiaoBottomPublishActionButton(target) {
  const preview = findToutiaoPreviewDialog()
  const clickables = collectToutiaoBottomPublishButtons(preview)
  const grouped = findToutiaoPublishButtonGroup(clickables)
  const groupedTarget = grouped.find((item) => item.text === target)
  if (groupedTarget) return groupedTarget.el

  return null
}

function collectToutiaoBottomPublishButtons(preview) {
  return Array.from(document.querySelectorAll('button, a, [role="button"]'))
    .filter(isVisibleElement)
    .filter((el) => !preview || !preview.contains(el))
    .map((el) => {
      const rect = el.getBoundingClientRect()
      const text = normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '')
      const parentText = normalizeText(el.parentElement?.textContent || '')
      return {
        el,
        text,
        parentText,
        rect,
        disabled: Boolean(el.disabled) || el.getAttribute('aria-disabled') === 'true',
      }
    })
    .filter((item) => !item.disabled)
    .filter((item) => ['预览', '定时发布', '预览并发布'].includes(item.text))
    .filter((item) => isLikelyBottomPublishBarButton(item))
    .filter((item, index, items) => items.findIndex((other) => other.el === item.el) === index)
}

function findToutiaoPublishButtonGroup(buttons) {
  const sorted = buttons.slice().sort((left, right) => left.rect.top - right.rect.top || left.rect.left - right.rect.left)
  for (const button of sorted) {
    const row = sorted
      .filter((item) => Math.abs(verticalCenter(item.rect) - verticalCenter(button.rect)) <= 32)
      .sort((left, right) => left.rect.left - right.rect.left)
    const texts = row.map((item) => item.text)
    if (texts.includes('预览') && texts.includes('定时发布')) {
      return row
    }
  }
  return []
}

function isLikelyBottomPublishBarButton(item) {
  const rect = item?.rect
  if (!rect) return false
  return rect.top >= window.innerHeight * 0.60
    && rect.width >= 50
    && rect.width <= 180
    && rect.height >= 24
    && rect.height <= 72
}

function verticalCenter(rect) {
  return rect.top + rect.height / 2
}

function collectVisibleActionElements(root = document) {
  return Array.from((root || document).querySelectorAll('button, a, [role="button"], label'))
    .filter(isVisibleElement)
    .map((el) => {
      const rect = el.getBoundingClientRect()
      return {
        el,
        text: normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || ''),
        rect,
        disabled: Boolean(el.disabled) || el.getAttribute('aria-disabled') === 'true',
      }
    })
    .filter((item) => item.text)
    .filter((item, index, items) => items.findIndex((other) => other.el === item.el) === index)
}

function findToutiaoScheduleDialog() {
  if (isToutiaoPreviewLayerOpen()) return null
  const markers = ['请选择当前时间后', '本文将于北京时间', '发布时间不能早于', '选择时间', '定时发布']
  const marker = markers.map((item) => findVisibleTextElement(item, { exact: false, maxLength: 80 })).find(Boolean)
  if (!marker) return null
  const dialog = findToutiaoScheduleModalContainer(marker) || nearestLargeContainer(marker)
  if (!dialog || dialog === document.body || dialog === document.documentElement) return null
  const text = normalizeText(dialog.textContent || '')
  if (!markers.some((item) => text.includes(item))) return null
  if (!text.includes('定时发布') && !text.includes('请选择当前时间后') && !text.includes('本文将于北京时间')) return null
  return dialog
}

function findToutiaoScheduleModalContainer(marker) {
  let current = marker
  let best = null
  for (let depth = 0; current && depth < 14; depth += 1) {
    if (current === document.body || current === document.documentElement) break
    const rect = current.getBoundingClientRect()
    const text = normalizeText(current.textContent || '')
    const looksLikeScheduleDialog = text.includes('定时发布')
      && (text.includes('请选择当前时间后') || text.includes('本文将于北京时间') || text.includes('预览并定时发布'))
    const looksLikeModalSize = rect.width >= 360
      && rect.width <= Math.max(760, window.innerWidth * 0.8)
      && rect.height >= 180
      && rect.height <= Math.max(720, window.innerHeight * 0.9)
    if (looksLikeScheduleDialog && looksLikeModalSize) {
      best = current
    }
    current = current.parentElement
  }
  return best
}

function findToutiaoScheduleConfirmButton() {
  const dialog = findToutiaoScheduleDialog()
  const root = dialog || findToutiaoPreviewDialog() || document
  const candidates = dialog ? ['预览并定时发布', '确认发布', '确定', '确认'] : ['预览并定时发布', '确认发布', '确定', '确认', '定时发布']
  const rootRect = root?.getBoundingClientRect?.()
  return Array.from(root.querySelectorAll('button, [role="button"], a, div, span'))
    .filter(isVisibleElement)
    .map((el) => {
      const clickable = el.closest?.('button, a, [role="button"]') || el
      const rect = clickable.getBoundingClientRect()
      const text = normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '')
      const interactive = clickable.matches?.('button, a, [role="button"]') ? 0 : 1
      const lowerInDialog = rootRect ? (rect.top >= rootRect.top + rootRect.height * 0.45 ? 0 : 1) : 0
      return { el: clickable, rect, text, interactive, lowerInDialog }
    })
    .filter((item) => candidates.includes(item.text))
    .filter((item) => item.rect.width >= 40 && item.rect.width <= 240 && item.rect.height >= 20 && item.rect.height <= 80)
    .filter((item, index, items) => items.findIndex((other) => other.el === item.el && other.text === item.text) === index)
    .sort((left, right) => {
      const leftPrimary = left.text === '预览并定时发布' ? 0 : 1
      const rightPrimary = right.text === '预览并定时发布' ? 0 : 1
      return leftPrimary - rightPrimary
        || left.lowerInDialog - right.lowerInDialog
        || left.interactive - right.interactive
        || right.rect.top - left.rect.top
    })[0]?.el || null
}

async function clickToutiaoScheduleConfirmButton(confirm, value, platform) {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    if (!findToutiaoScheduleDialog()) return confirmToutiaoPreviewPublishAfterSchedule(value, platform)
    const latestConfirm = attempt === 0 ? confirm : findToutiaoScheduleConfirmButton()
    const target = findToutiaoScheduleConfirmClickTarget(latestConfirm)
    if (!target) throw new Error(`头条定时发布确认按钮点击目标未找到；${describeToutiaoScheduleDialog()}`)
    target.scrollIntoView?.({ block: 'center', inline: 'center' })
    await delay(150)
    firePointerClick(target, { platform })
    target.click?.()
    await requestTrustedClick(target, { platform })
    await delay(1200)
    if (!findToutiaoScheduleDialog()) {
      return confirmToutiaoPreviewPublishAfterSchedule(value, platform)
    }
  }
  throw new Error(`头条定时发布确认按钮点击后页面未进入下一步；target=${value.full}；lastTrustedClick=${describeLastTrustedClick()}；${describeToutiaoScheduleDialog()}`)
}

async function confirmToutiaoPreviewPublishAfterSchedule(value, platform) {
  const previewConfirm = await waitForCondition(
    () => findToutiaoPreviewConfirmButton(),
    8000,
    `头条定时发布预览页最终发布按钮未找到；target=${value.full}；${describeToutiaoPreviewState()}`,
  )
  for (let attempt = 0; attempt < 3; attempt += 1) {
    if (isToutiaoPreviewPublishCompleted()) return true
    const current = attempt === 0 ? previewConfirm : findToutiaoPreviewConfirmButton()
    if (!current) {
      if (isToutiaoPreviewPublishCompleted()) return true
      await delay(500)
      continue
    }
    await clickTrustedActionOnce(current, { platform })
    const completed = await waitForCondition(
      () => isToutiaoPreviewPublishCompleted(),
      12000,
      null,
    ).catch(() => false)
    if (completed) return true
  }
  throw new Error(`头条定时发布预览页最终发布后未完成；target=${value.full}；lastTrustedClick=${describeLastTrustedClick()}；${describeToutiaoPreviewState()}`)
}

async function verifyToutiaoScheduledWorkInList(value, context = {}) {
  const entered = await waitForCondition(
    () => isToutiaoWorksManagePage(),
    18000,
    `WORKS_LIST_VERIFY_TIMEOUT：头条作品管理页未进入；target=${value.full}；href=${location.href}；${describeToutiaoPreviewState()}`,
  ).catch(() => false)
  if (!entered) {
    throw new Error(`WORKS_LIST_VERIFY_TIMEOUT：头条作品管理页未进入；target=${value.full}；href=${location.href}；${describeToutiaoPreviewState()}`)
  }

  const matched = await waitForCondition(
    () => findToutiaoScheduledWorkMatch(value, context),
    15000,
    `WORKS_LIST_VERIFY_TIMEOUT：头条作品列表未匹配到定时文章；target=${value.full}；${describeToutiaoWorksListState(context)}`,
  ).catch(() => null)
  if (!matched) {
    throw new Error(`WORKS_LIST_VERIFY_TIMEOUT：头条作品列表未匹配到定时文章；target=${value.full}；${describeToutiaoWorksListState(context)}`)
  }
  return {
    verified: true,
    platformStatus: 'scheduled',
    matchedTitle: matched.title,
    scheduledAtText: matched.scheduledAtText,
    locationText: matched.locationText,
    hasCover: matched.hasCover,
    pageUrl: location.href,
  }
}

function isToutiaoPreviewPublishCompleted() {
  if (isToutiaoWorksManagePage()) return true
  return !isToutiaoPreviewLayerOpen() && !findToutiaoPreviewConfirmButton()
}

function isToutiaoWorksManagePage() {
  if (location.hostname !== 'mp.toutiao.com') return false
  if (/\/profile_v\d+\/manage/.test(location.pathname) || location.pathname.includes('/profile_v4/content-manage')) return true
  const text = normalizeText(document.body?.innerText || document.body?.textContent || '')
  return text.includes('作品管理')
    || (text.includes('草稿箱') && (text.includes('已发布') || text.includes('审核中') || text.includes('定时发布中')))
}

function findToutiaoScheduledWorkMatch(value, context = {}) {
  const expectedTitle = normalizeArticleText(context.title || '')
  const titleNeedle = expectedTitle.length > 18 ? expectedTitle.slice(0, 18) : expectedTitle
  const locationName = normalizeText(context.locationName || '')
  for (const row of collectToutiaoWorksListRows()) {
    const text = normalizeText(row.textContent || '')
    if (!text.includes('定时发布中') && !text.includes('将于')) continue
    if (titleNeedle && !normalizeArticleText(row.textContent || '').includes(titleNeedle)) continue
    if (locationName && !text.includes(locationName)) continue
    if (!isToutiaoWorksRowScheduledAtMatched(text, value)) continue
    return {
      title: extractToutiaoWorksTitle(row, context.title),
      scheduledAtText: extractToutiaoWorksScheduleText(row, value),
      locationText: locationName && text.includes(locationName) ? context.locationName : '',
      hasCover: hasVisibleImage(row),
    }
  }
  return null
}

function collectToutiaoWorksListRows() {
  const candidates = Array.from(document.querySelectorAll('li, article, section, div'))
    .filter(isVisibleElement)
    .filter((el) => {
      const rect = el.getBoundingClientRect()
      if (rect.width < 300 || rect.height < 60 || rect.height > 360) return false
      const text = normalizeText(el.textContent || '')
      return text.includes('定时发布中') || text.includes('将于')
    })
  return candidates.filter((candidate) => !candidates.some((other) => other !== candidate && other.contains(candidate)))
}

function isToutiaoWorksRowScheduledAtMatched(text, value) {
  if (!text) return false
  const full = normalizeText(value.full)
  const compactFull = full.replace(/[-:]/g, '')
  const monthDay = normalizeText(value.monthDay)
  const hourMinute = `${Number(value.hour)}:${String(Number(value.minute)).padStart(2, '0')}`
  const compactHourMinute = `${Number(value.hour)}时${Number(value.minute)}分`
  return text.includes(full)
    || text.includes(compactFull)
    || (text.includes(monthDay) && (text.includes(hourMinute) || text.includes(compactHourMinute)))
    || text.includes(value.time)
}

function extractToutiaoWorksTitle(row, fallbackTitle) {
  const fallback = String(fallbackTitle || '').trim()
  const candidates = Array.from(row.querySelectorAll('a, span, div, h1, h2, h3'))
    .filter(isVisibleElement)
    .map((el) => String(el.textContent || '').trim())
    .filter((text) => text.length >= 8 && text.length <= 80)
    .filter((text) => !text.includes('定时发布中') && !text.includes('查看数据') && !text.includes('查看评论'))
  return candidates[0] || fallback
}

function extractToutiaoWorksScheduleText(row, value) {
  const text = normalizeText(row.textContent || '')
  const monthDay = normalizeText(value.monthDay)
  const index = text.indexOf('将于')
  if (index >= 0) return text.slice(index, Math.min(text.length, index + 32))
  const dateIndex = text.indexOf(monthDay)
  if (dateIndex >= 0) return text.slice(Math.max(0, dateIndex - 4), Math.min(text.length, dateIndex + 24))
  return value.full
}

function hasVisibleImage(root) {
  return Array.from(root.querySelectorAll('img, [style*="background-image"]')).some(isVisibleElement)
}

function describeToutiaoWorksListState(context = {}) {
  const expectedTitle = normalizeArticleText(context.title || '')
  const rows = collectToutiaoWorksListRows().slice(0, 6).map((row, index) => {
    const text = normalizeText(row.textContent || '').slice(0, 120)
    return `${index}:${text || '-'}`
  })
  const body = normalizeText(document.body?.innerText || document.body?.textContent || '').slice(0, 240)
  return `href=${location.href}; expectedTitle=${expectedTitle || '-'}; rows=${rows.join('|') || '-'}; body=${body || '-'}`
}

function describeToutiaoPreviewState() {
  const dialog = findToutiaoPreviewDialog()
  const root = dialog || document
  const text = normalizeText(root.textContent || document.body?.innerText || '').slice(0, 220)
  const actions = Array.from(root.querySelectorAll('button, [role="button"], a, div, span'))
    .filter(isVisibleElement)
    .map((el) => {
      const rect = el.getBoundingClientRect()
      const value = normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '')
      return value ? `${value}@${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)}` : ''
    })
    .filter(Boolean)
    .slice(-20)
  return `previewOpen=${isToutiaoPreviewLayerOpen()}; previewText=${text || '-'}; previewActions=${actions.join('|') || '-'}`
}

function findToutiaoScheduleConfirmClickTarget(confirm) {
  const candidates = []
  let current = confirm
  for (let depth = 0; current && depth < 5; depth += 1) {
    candidates.push(current)
    current = current.parentElement
  }
  return candidates
    .filter(isVisibleElement)
    .map((el) => {
      const rect = el.getBoundingClientRect()
      const text = normalizeText(el.textContent || el.getAttribute?.('aria-label') || el.getAttribute?.('title') || '')
      const interactive = el.matches?.('button, a, [role="button"]') ? 0 : 1
      return { el, rect, text, interactive }
    })
    .filter((item) => item.text === '预览并定时发布' || item.text === '确认发布' || item.text === '定时发布')
    .filter((item) => item.rect.width >= 60 && item.rect.width <= 220 && item.rect.height >= 24 && item.rect.height <= 72)
    .sort((left, right) => left.interactive - right.interactive || (right.rect.width * right.rect.height) - (left.rect.width * left.rect.height))[0]?.el
    || confirm.closest?.('button, a, [role="button"]')
    || confirm
}

function isToutiaoScheduleConfirmEffectVisible(value) {
  const text = normalizeText(document.body?.innerText || document.body?.textContent || '')
  if (!text) return false
  const stillScheduleDialog = findToutiaoScheduleDialog()
  if (stillScheduleDialog) return false
  return text.includes('返回编辑')
    || text.includes('确认发布')
    || text.includes('手机预览')
    || text.includes('草稿已保存')
    || isToutiaoSchedulePreviewTimeMatched(document.body, value)
}

function findToutiaoPreviewConfirmButton() {
  const dialog = findToutiaoPreviewDialog()
  const root = dialog || document
  const previewVisible = isToutiaoPreviewLayerOpen() || Boolean(dialog)
  if (!previewVisible) return null
  return Array.from(root.querySelectorAll('button, [role="button"], a, div, span'))
    .filter(isVisibleElement)
    .map((el) => {
      const clickable = el.closest?.('button, a, [role="button"]') || el
      const rect = clickable.getBoundingClientRect()
      const text = normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '')
      const interactive = clickable.matches?.('button, a, [role="button"]') ? 0 : 1
      return { el: clickable, rect, text, interactive, area: rect.width * rect.height }
    })
    .filter((item) => item.text === '确认发布' || item.text === '定时发布')
    .filter((item) => item.rect.width >= 40 && item.rect.width <= 220 && item.rect.height >= 20 && item.rect.height <= 80)
    .filter((item, index, items) => items.findIndex((other) => other.el === item.el) === index)
    .sort((left, right) => {
      const leftConfirm = left.text === '确认发布' ? 0 : 1
      const rightConfirm = right.text === '确认发布' ? 0 : 1
      return leftConfirm - rightConfirm
        || left.interactive - right.interactive
        || right.rect.top - left.rect.top
        || right.area - left.area
    })[0]?.el || null
}

function findToutiaoPreviewBackButton() {
  const root = findToutiaoPreviewDialog() || document
  return Array.from(root.querySelectorAll('button, [role="button"], a, div, span'))
    .filter(isVisibleElement)
    .find((el) => normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '') === '返回编辑') || null
}

function findToutiaoPreviewDialog() {
  const markers = ['确认发布', '定时发布', '返回编辑', '仅支持预览', '手机预览']
  const marker = markers.map((item) => findVisibleTextElement(item, { exact: false, maxLength: 80 })).find(Boolean)
  if (!marker && !isToutiaoPreviewLayerOpen()) return null
  if (!marker) return document.body
  const dialog = nearestLargeContainer(marker)
  if (!dialog || dialog === document.body || dialog === document.documentElement) return isToutiaoPreviewLayerOpen() ? document.body : null
  const text = normalizeText(dialog.textContent || '')
  const hasPreviewMarker = text.includes('返回编辑') || text.includes('仅支持预览') || text.includes('手机预览') || text.includes('屏幕尺寸')
  const hasPublishAction = text.includes('确认发布') || text.includes('定时发布')
  return hasPreviewMarker && hasPublishAction ? dialog : null
}

function isToutiaoPreviewLayerOpen() {
  if (findVisibleTextElement('返回编辑', { exact: true, maxLength: 8 })
      && (findVisibleTextElement('确认发布', { exact: true, maxLength: 8 }) || findVisibleTextElement('定时发布', { exact: true, maxLength: 8 }))) {
    return true
  }
  if (findVisibleTextElement('仅支持预览', { exact: false, maxLength: 12 })
      || findVisibleTextElement('手机预览', { exact: false, maxLength: 12 })) {
    return true
  }
  const text = normalizeText(document.body?.innerText || document.body?.textContent || '')
  if (!text) return false
  const hasPublishConfirm = text.includes('确认发布') || text.includes('定时发布')
  const hasBackEdit = text.includes('返回编辑')
  const hasPreviewOnly = text.includes('仅支持预览') || text.includes('手机预览')
  const hasScreenSize = text.includes('屏幕尺寸') && /4\.7寸|5\.5寸|5\.8寸/.test(text)
  return (hasPublishConfirm && hasBackEdit) || (hasPreviewOnly && hasBackEdit) || (hasPreviewOnly && hasScreenSize)
}

function describeToutiaoPublishActions() {
  const bottomButtons = collectToutiaoBottomPublishButtons(findToutiaoPreviewDialog())
    .map((item) => `${item.text}@${Math.round(item.rect.left)},${Math.round(item.rect.top)},${Math.round(item.rect.width)}x${Math.round(item.rect.height)}`)
  const grouped = findToutiaoPublishButtonGroup(bottomButtons.length ? collectToutiaoBottomPublishButtons(findToutiaoPreviewDialog()) : [])
    .map((item) => item.text)
  const actions = Array.from(document.querySelectorAll('button, [role="button"], a'))
    .filter(isVisibleElement)
    .map((el) => {
      const rect = el.getBoundingClientRect()
      const text = normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '')
      return text ? `${text}@${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)}` : ''
    })
    .filter(Boolean)
    .slice(-20)
  return `bottomButtons=${bottomButtons.join('|') || '-'}; publishGroup=${grouped.join('|') || '-'}; actions=${actions.join('|') || '-'}`
}

function describeToutiaoScheduleDialog() {
  const dialog = findToutiaoScheduleDialog()
  const root = dialog || document
  const text = normalizeText(root.textContent || document.body?.innerText || '').slice(0, 220)
  const scheduleControls = dialog
    ? collectToutiaoScheduleDropdownControls(dialog).map((el) => {
        const rect = el.getBoundingClientRect()
        const value = normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '')
        return `${value || '-'}@${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)}`
      })
    : []
  const inputs = Array.from(root.querySelectorAll('input, textarea, [contenteditable="true"]'))
    .filter(isVisibleElement)
    .map((input, index) => ({
      index,
      tag: input.tagName,
      type: input.getAttribute('type') || '',
      value: input.value || input.textContent || '',
      placeholder: input.getAttribute('placeholder') || '',
      aria: input.getAttribute('aria-label') || '',
    }))
  return `previewOpen=${isToutiaoPreviewLayerOpen()}; lastTrustedClick=${describeLastTrustedClick()}; ${describeToutiaoPublishActions()}; scheduleControls=${scheduleControls.join('|') || '-'}; scheduleText=${text || '-'}; inputs=${JSON.stringify(inputs).slice(0, 420)}`
}

function describeLastTrustedClick() {
  const click = globalThis.__GEO_ENV_ACTIVE_FILL_TASK_CONTEXT?.lastTrustedClick
  if (!click) return '-'
  return `${click.label || '-'}@${click.clientX},${click.clientY},${click.rect || '-'}`
}

async function scrollToToutiaoSection(labelText) {
  const label = findVisibleTextElement(labelText)
  if (!label) {
    throw new Error(`头条发布设置未找到：${labelText}`)
  }
  label.scrollIntoView?.({ block: 'center', inline: 'nearest' })
  await delay(250)
}

async function clickToutiaoOptionNearLabel(labelText, optionText, platform) {
  const label = findVisibleTextElement(labelText)
  const scoped = label ? findTextElementNear(label, optionText, 420) : null
  const option = scoped || findVisibleTextElement(optionText, { exact: true, maxLength: 8 })
  if (!option) {
    throw new Error(`头条${labelText}选项未找到：${optionText}`)
  }
  await clickToutiaoRadioText(option, platform, optionText)
  await delay(300)
}

async function clickToutiaoRadioText(option, platform, optionText) {
  option.scrollIntoView?.({ block: 'center', inline: 'nearest' })
  await delay(100)
  const rect = option.getBoundingClientRect()
  firePointerClick(option)
  option.click?.()
  if (requiresTrustedClick(platform)) {
    const radioClientX = rect.width <= 42 ? rect.left - 18 : rect.left + 14
    await requestTrustedClickAt(
      {
        clientX: Math.round(Math.max(0, radioClientX)),
        clientY: Math.round(rect.top + rect.height / 2),
      },
      platform,
      optionText,
      rect,
    )
  }
}

function findToutiaoInputNearLabel(labelText) {
  const label = findVisibleTextElement(labelText)
  const candidates = Array.from(document.querySelectorAll('input, textarea, [contenteditable="true"]'))
    .filter((el) => isVisibleElement(el))
    .map((el) => ({ el, distance: label ? elementDistance(label, el) : 0 }))
    .filter((item) => !label || item.distance <= 620)
    .sort((left, right) => left.distance - right.distance)
  return candidates[0]?.el || null
}

function findToutiaoLocationOpener(label) {
  const keywords = ['添加位置', '选择位置', '位置', '+']
  for (const keyword of keywords) {
    const near = label ? findTextElementNear(label, keyword, 520) : null
    if (near && isLikelyLocationAction(near)) return near
  }
  const box = findLocationActionBoxNear(label)
  if (box) return box
  return label && isLikelyLocationAction(label) ? label : null
}

function isLikelyLocationAction(el) {
  const text = normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '')
  const className = String(el.className || '')
  return text.includes('位置') || text === '+' || /location|poi|position|address/i.test(className)
}

function findLocationActionBoxNear(label) {
  if (!label) return null
  return Array.from(document.querySelectorAll('button, [role="button"], div, span'))
    .filter(isVisibleElement)
    .map((el) => ({
      el,
      text: normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || ''),
      className: String(el.className || ''),
      rect: el.getBoundingClientRect(),
      distance: elementDistance(label, el),
    }))
    .filter((item) => item.distance <= 560)
    .filter((item) => (
      item.text.includes('位置')
      || /location|poi|position|address/i.test(item.className)
      || (item.rect.width >= 80 && item.rect.height >= 24 && item.text.length <= 12)
    ))
    .sort((left, right) => left.distance - right.distance || left.text.length - right.text.length)[0]?.el || null
}

function findToutiaoLocationInput(label) {
  const selectors = [
    'input[placeholder*="位置"]',
    'input[placeholder*="地点"]',
    'input[placeholder*="地址"]',
    'input[placeholder*="搜索"]',
    'input[placeholder*="城市"]',
    'input[placeholder*="同城"]',
    'input[placeholder*="标记"]',
    'textarea[placeholder*="位置"]',
    'textarea[placeholder*="地点"]',
    'textarea[placeholder*="城市"]',
    '[contenteditable="true"][placeholder*="位置"]',
    '[contenteditable="true"][aria-label*="位置"]',
  ]
  for (const selector of selectors) {
    const input = rankToutiaoLocationInputs(Array.from(document.querySelectorAll(selector)), label)[0]?.input
    if (input) return input
  }

  const roots = [findToutiaoLocationPanel(), label ? nearestLargeContainer(label) : null].filter(Boolean)
  const inputs = []
  for (const root of roots) {
    inputs.push(...rankToutiaoLocationInputs(Array.from(root.querySelectorAll('input, textarea, [contenteditable="true"]')), label))
  }
  return inputs[0]?.input || null
}

function rankToutiaoLocationInputs(elements, label) {
  const labelRect = label?.getBoundingClientRect?.() || null
  return elements
    .filter(isVisibleElement)
    .map((input) => {
      const rect = input.getBoundingClientRect()
      const text = normalizeText([
        input.getAttribute('placeholder') || '',
        input.getAttribute('aria-label') || '',
        input.getAttribute('title') || '',
        input.getAttribute('role') || '',
        input.textContent || '',
      ].join(' '))
      const distance = label ? elementDistance(label, input) : 0
      const sameRow = labelRect ? Math.abs((rect.top + rect.height / 2) - (labelRect.top + labelRect.height / 2)) : 0
      return { input, text, rect, distance, sameRow }
    })
    .filter((item) => isSafeToutiaoLocationInput(item, labelRect))
    .sort((left, right) => {
      const leftHint = /位置|地点|地址|搜索|城市|同城|标记|combobox/.test(left.text) ? 0 : 1
      const rightHint = /位置|地点|地址|搜索|城市|同城|标记|combobox/.test(right.text) ? 0 : 1
      const leftNativeInput = left.input instanceof HTMLInputElement ? 0 : 1
      const rightNativeInput = right.input instanceof HTMLInputElement ? 0 : 1
      return leftHint - rightHint
        || leftNativeInput - rightNativeInput
        || left.sameRow - right.sameRow
        || left.distance - right.distance
        || right.rect.width - left.rect.width
    })
}

function isSafeToutiaoLocationInput(item, labelRect) {
  const { input, text, rect, distance } = item
  if (isLikelyArticleEditor(input)) return false
  if (labelRect && rect.top < labelRect.top - 120) return false
  const hasLocationHint = /位置|地点|地址|搜索|城市|同城|标记|combobox/.test(text)
  const isNativeInput = input instanceof HTMLInputElement
  if (hasLocationHint) return distance <= 900
  if (!labelRect || !isNativeInput) return false
  const centerY = rect.top + rect.height / 2
  const labelCenterY = labelRect.top + labelRect.height / 2
  return distance <= 760
    && Math.abs(centerY - labelCenterY) <= 180
    && rect.width >= 160
    && rect.width <= 760
    && rect.height <= 80
}

function isLikelyArticleEditor(el) {
  if (!el) return false
  const className = String(el.className || '')
  const role = el.getAttribute?.('role') || ''
  const placeholder = el.getAttribute?.('placeholder') || ''
  const aria = el.getAttribute?.('aria-label') || ''
  const text = normalizeText(el.textContent || '')
  const hint = normalizeText(`${placeholder} ${aria} ${el.getAttribute?.('title') || ''}`)
  if (/文章标题|请输入文章标题|标题（?2/.test(hint)) return true
  if (el instanceof HTMLTextAreaElement && !/位置|地点|地址|搜索|城市|同城|标记|日期|时间|发布|定时|预约/.test(hint)) {
    return true
  }
  if (/ProseMirror|DraftEditor|public-DraftEditor|notranslate|editor/i.test(className)) return true
  if ((el.isContentEditable || el.getAttribute?.('contenteditable') === 'true') && !/位置|地点|地址|搜索|城市|同城|标记/.test(placeholder + aria)) {
    return true
  }
  if (role === 'textbox' && text.length > 30 && !/位置|地点|地址|搜索|城市|同城|标记/.test(placeholder + aria)) {
    return true
  }
  return false
}

function findToutiaoLocationOption(locationTerms) {
  const normalizedTargets = (Array.isArray(locationTerms) ? locationTerms : [locationTerms])
    .map((item) => normalizeText(item))
    .filter(Boolean)
  const primaryTarget = normalizedTargets[0] || ''
  const searchInput = findToutiaoLocationInput(null)
  return collectToutiaoLocationOptionCandidates()
    .filter(isVisibleElement)
    .filter((el) => !el.querySelector?.('input, textarea, [contenteditable="true"]'))
    .map((el) => {
      const text = normalizeText(el.textContent || el.getAttribute('aria-label') || '')
      const rect = el.getBoundingClientRect()
      return {
        el,
        text,
        rect,
        distance: searchInput ? elementDistance(searchInput, el) : 0,
        interactive: isInteractiveElement(el) ? 0 : 1,
      }
    })
    .filter((item) => item.text && item.text.length <= Math.max(80, primaryTarget.length + 40))
    .filter((item) => !/^(添加位置|选择位置|搜索地点|搜索位置|不显示位置|位置服务)$/.test(item.text))
    .map((item) => ({ ...item, matchRank: rankToutiaoLocationOption(item.text, normalizedTargets) }))
    .filter((item) => item.matchRank < 99)
    .sort((left, right) => {
      return left.matchRank - right.matchRank
        || left.interactive - right.interactive
        || left.distance - right.distance
        || left.rect.top - right.rect.top
        || left.text.length - right.text.length
    })[0]?.el || null
}

function collectToutiaoLocationOptionCandidates() {
  const roots = [findToutiaoLocationFloatingLayer(), findToutiaoLocationPanel(), document].filter(Boolean)
  const elements = []
  for (const root of roots) {
    for (const el of Array.from(root.querySelectorAll('div, span, li, button, [role="option"], [role="menuitem"]'))) {
      if (!elements.includes(el)) elements.push(el)
    }
  }
  return elements
}

function findToutiaoLocationFloatingLayer() {
  const input = findToutiaoLocationInput(null)
  if (!input) return null
  const inputRect = input.getBoundingClientRect()
  const candidates = Array.from(document.querySelectorAll('div, ul, li, [role="listbox"], [role="menu"]'))
    .filter(isVisibleElement)
    .map((el) => ({ el, rect: el.getBoundingClientRect(), text: normalizeText(el.textContent || '') }))
    .filter((item) => item.text && item.text.length <= 120)
    .filter((item) => item.rect.top >= inputRect.bottom - 12 || Math.abs(item.rect.top - inputRect.top) <= 80)
    .filter((item) => Math.abs((item.rect.left + item.rect.width / 2) - (inputRect.left + inputRect.width / 2)) <= Math.max(520, inputRect.width))
    .sort((left, right) => {
      const leftVertical = Math.abs(left.rect.top - inputRect.bottom)
      const rightVertical = Math.abs(right.rect.top - inputRect.bottom)
      return leftVertical - rightVertical || left.text.length - right.text.length
    })
  return candidates[0]?.el || null
}

function rankToutiaoLocationOption(optionText, targets) {
  for (let index = 0; index < targets.length; index += 1) {
    const target = targets[index]
    if (!target) continue
    const base = index * 10
    if (optionText === target) return base
    if (optionText.replace(/[市县区]$/u, '') === target.replace(/[市县区]$/u, '')) return base + 1
    if (optionText.includes(target)) return base + 2
    if (target.includes(optionText) && optionText.length >= 2) return base + 3
  }
  return 99
}

function buildToutiaoLocationTerms(locationName) {
  const raw = normalizeText(locationName)
  if (!raw) return []
  const terms = []
  const add = (value) => {
    const text = normalizeText(value)
      .replace(/^(北京|上海|天津|重庆)市/u, '$1')
      .replace(/^(河北|山西|辽宁|吉林|黑龙江|江苏|浙江|安徽|福建|江西|山东|河南|湖北|湖南|广东|海南|四川|贵州|云南|陕西|甘肃|青海|台湾)省?/u, '')
      .replace(/^(内蒙古|广西壮族|西藏|宁夏回族|新疆维吾尔)(自治区)?/u, '')
      .replace(/^(香港|澳门)特别行政区/u, '$1')
      .replace(/[省市县区]$/u, '')
    if (text && text.length >= 2 && !terms.includes(text)) terms.push(text)
  }

  const firstSegment = raw.split(/[\/,，、;；|｜\s]+/u).find((item) => item && item.length >= 2)
  if (firstSegment && firstSegment !== raw) add(firstSegment)

  const cityMatch = raw.match(/(?:^|省|自治区|特别行政区)([^省自治区特别行政区]{2,12}?市)/u)
    || raw.match(/([\u4e00-\u9fa5]{2,12}?市)/u)
  if (cityMatch?.[1]) add(cityMatch[1])

  const districtMatch = raw.match(/([\u4e00-\u9fa5]{2,12}?[县区])/u)
  if (districtMatch?.[1]) add(districtMatch[1])

  add(raw)
  return terms
}

function setToutiaoLocationInputValue(input, value) {
  if (isLikelyArticleEditor(input)) {
    throw new Error(`头条添加位置输入框命中了正文编辑器，已阻止写入；input=${describeElement(input)}`)
  }
  if (input.isContentEditable) {
    input.focus()
    input.textContent = value
    dispatchEditEvents(input)
    return
  }
  setTextValue(input, value)
  if (document.activeElement !== input) {
    input.focus()
  }
  if (document.activeElement !== input && input instanceof HTMLInputElement) {
    throw new Error(`头条添加位置输入框无法聚焦，已阻止写入正文；input=${describeElement(input)}`)
  }
}

function findToutiaoLocationPanel() {
  const markers = ['搜索地点', '搜索位置', '添加位置', '不显示位置', '位置服务', '标记城市']
  const marker = markers.map((item) => findVisibleTextElement(item, { exact: false, maxLength: 24 })).find(Boolean)
  if (marker) return nearestLargeContainer(marker)
  const input = Array.from(document.querySelectorAll('input[placeholder*="地点"], input[placeholder*="位置"], input[placeholder*="搜索"], input[placeholder*="城市"], input[placeholder*="同城"], input[placeholder*="标记"]'))
    .filter(isVisibleElement).at(-1)
  return input ? nearestLargeContainer(input) : null
}

function describeToutiaoLocationArea(label) {
  const panel = findToutiaoLocationPanel()
  const root = panel || (label ? nearestLargeContainer(label) : document.body)
  const text = normalizeText(root?.textContent || document.body?.innerText || '').slice(0, 220)
  const visibleOptions = collectToutiaoLocationOptionCandidates()
    .filter(isVisibleElement)
    .map((el) => normalizeText(el.textContent || el.getAttribute('aria-label') || ''))
    .filter((text) => text && text.length <= 20)
    .slice(0, 20)
  const inputs = Array.from(document.querySelectorAll('input, textarea, [contenteditable="true"]'))
    .filter(isVisibleElement)
    .slice(-8)
    .map((input, index) => ({
      index,
      tag: input.tagName,
      value: input.value || input.textContent || '',
      placeholder: input.getAttribute('placeholder') || '',
      aria: input.getAttribute('aria-label') || '',
    }))
  return `locationText=${text || '-'}; locationOptions=${visibleOptions.join('|') || '-'}; inputs=${JSON.stringify(inputs).slice(0, 420)}`
}

function hasToutiaoCoverThumbnail() {
  const label = findVisibleTextElement('展示封面')
  const scope = label ? nearestLargeContainer(label) : document.body
  const text = normalizeText(scope?.textContent || '')
  if (text.includes('编辑') && text.includes('替换')) return true
  return Array.from((scope || document).querySelectorAll('img'))
    .some((img) => isVisibleElement(img) && img.getBoundingClientRect().width >= 40 && img.getBoundingClientRect().height >= 40)
}

async function openToutiaoCoverDrawer(platform) {
  if (findLatestFileInput()) return
  const label = findVisibleTextElement('展示封面')
  const entries = findToutiaoCoverUploadEntries(label)
  if (!entries.length) {
    throw new Error(`头条封面上传入口未找到；${describeToutiaoCoverArea(label)}`)
  }
  for (const entry of entries) {
    await clickClosestAction(entry, { platform })
    await delay(600)
    if (findLatestFileInput() || findVisibleTextElement('本地上传') || findVisibleTextElement('上传图片') || findToutiaoCoverDrawer()) {
      break
    }
  }
  if (!findLatestFileInput() && !findVisibleTextElement('本地上传') && !findVisibleTextElement('上传图片') && !findToutiaoCoverDrawer()) {
    await waitForCondition(
      () => findLatestFileInput() || findVisibleTextElement('本地上传') || findVisibleTextElement('上传图片') || findToutiaoCoverDrawer(),
      5000,
      `等待头条封面上传抽屉超时；${describeToutiaoCoverArea(label)}`,
    )
  }
  const uploadTab = findVisibleTextElement('上传图片', { exact: true, maxLength: 8 })
  if (uploadTab) await clickClosestAction(uploadTab, { platform })
}

function findToutiaoCoverUploadEntries(label) {
  const keywords = ['替换', '编辑', '添加封面', '上传封面', '上传图片', '本地上传', '选择图片', '重新上传']
  const entries = []
  const add = (el) => {
    if (el && !entries.includes(el)) entries.push(el)
  }
  for (const keyword of keywords) {
    add(label ? findTextElementNear(label, keyword, 720) : null)
    add(findVisibleTextElement(keyword, { exact: false, maxLength: 16 }))
  }
  add(findTextElementNear(label, '+', 720))
  for (const box of findCoverUploadBoxesNear(label)) add(box)
  return entries
}

function findCoverUploadBoxNear(label) {
  return findCoverUploadBoxesNear(label)[0] || null
}

function findCoverUploadBoxesNear(label) {
  const candidates = Array.from(document.querySelectorAll('div, button, [role="button"]'))
    .filter(isVisibleElement)
    .map((el) => ({
      el,
      text: normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || ''),
      className: String(el.className || ''),
      rect: el.getBoundingClientRect(),
      distance: label ? elementDistance(label, el) : 0,
    }))
    .filter((item) => item.distance <= 760)
    .filter((item) => (
      item.text === '+'
      || /上传|封面|图片|添加/.test(item.text)
      || /upload|cover|image|picture/i.test(item.className)
      || (item.rect.width >= 70 && item.rect.height >= 50 && item.text.length <= 10)
    ))
    .sort((left, right) => left.distance - right.distance || (left.rect.width * left.rect.height) - (right.rect.width * right.rect.height))
  return candidates.map((item) => item.el)
}

function describeToutiaoCoverArea(label) {
  const scope = label ? nearestLargeContainer(label) : null
  const text = normalizeText(scope?.textContent || document.body?.innerText || '').slice(0, 160)
  const fileInputs = Array.from(document.querySelectorAll('input[type="file"]')).length
  return `coverText=${text || '-'}; fileInputs=${fileInputs}`
}

async function uploadToutiaoCoverImage(imageUrl, platform) {
  let fileInput = findLatestFileInput()
  if (!fileInput) {
    const localUpload = findVisibleTextElement('本地上传', { exact: false, maxLength: 12 })
      || findVisibleTextElement('上传图片', { exact: false, maxLength: 12 })
      || findVisibleTextElement('选择图片', { exact: false, maxLength: 12 })
    if (localUpload) {
      await clickClosestAction(localUpload, { platform })
      await delay(500)
    }
    fileInput = await waitForCondition(
      () => findLatestFileInput(),
      8000,
      '头条封面本地上传文件框未找到',
    )
  } else {
    await delay(200)
  }
  const localUpload = await setFileInputFromLocalHelper(imageUrl, platform)
  if (localUpload?.ok) {
    await waitForCondition(
      () => isToutiaoCoverDrawerImageUploaded(),
      20000,
      `等待头条封面图片上传完成超时；${describeToutiaoUploadState(localUpload)}`,
    )
    return
  }
  const file = await fetchImageAsFile(imageUrl)
  const transfer = new DataTransfer()
  transfer.items.add(file)
  fileInput.files = transfer.files
  fileInput.dispatchEvent(new Event('input', { bubbles: true }))
  fileInput.dispatchEvent(new Event('change', { bubbles: true }))
  await waitForCondition(
    () => isToutiaoCoverDrawerImageUploaded(),
    20000,
    `等待头条封面图片上传完成超时；${describeToutiaoUploadState(null)}`,
  )
}

async function setFileInputFromLocalHelper(imageUrl, platform) {
  if (!supportsLocalHelperUploadPlatform(platform)) return null
  const response = await safeRuntimeRequest({
    type: 'GEO_ENV_SET_FILE_INPUT_FROM_URL',
    url: imageUrl,
    platform: normalizePlatform(platform),
    taskId: globalThis.__GEO_ENV_ACTIVE_FILL_TASK_CONTEXT?.taskId || null,
    environmentKey: globalThis.__GEO_ENV_ACTIVE_FILL_TASK_CONTEXT?.environmentKey || null,
  })
  if (!response?.ok) {
    throw new Error(`${platformDisplayName(platform)}本地文件上传通道失败：${response?.error || '扩展后台未响应'}`)
  }
  await delay(500)
  const fileInput = findLatestFileInput()
  fileInput?.dispatchEvent(new Event('input', { bubbles: true }))
  fileInput?.dispatchEvent(new Event('change', { bubbles: true }))
  return response
}

async function uploadCoverImageFromLocalHelper(imageUrl, platform, platformName) {
  const localUpload = await setFileInputFromLocalHelper(imageUrl, platform)
  if (localUpload?.ok) return localUpload

  const fileInput = await waitForCondition(
    () => findLatestFileInput(),
    8000,
    `${platformName || platformDisplayName(platform)}封面本地上传文件框未找到`,
  )
  const file = await fetchImageAsFile(imageUrl)
  const transfer = new DataTransfer()
  transfer.items.add(file)
  fileInput.files = transfer.files
  fileInput.dispatchEvent(new Event('input', { bubbles: true }))
  fileInput.dispatchEvent(new Event('change', { bubbles: true }))
  return { ok: true, fallback: true }
}

function supportsLocalHelperUploadPlatform(platform) {
  return ['toutiao', 'zhihu', 'baijiahao'].includes(normalizePlatform(platform))
}

function platformDisplayName(platform) {
  const normalized = normalizePlatform(platform)
  if (normalized === 'zhihu') return '知乎'
  if (normalized === 'toutiao') return '头条'
  if (normalized === 'baijiahao') return '百家号'
  return normalized || '平台'
}

function describeToutiaoUploadState(localUpload) {
  const fileInputs = Array.from(document.querySelectorAll('input[type="file"]')).map((input, index) => ({
    index,
    filesLength: input.files?.length || 0,
    fileName: input.files?.[0]?.name || '',
    accept: input.getAttribute('accept') || '',
  }))
  const drawer = findToutiaoCoverDrawer()
  const drawerText = normalizeText(drawer?.textContent || document.body?.innerText || '').slice(0, 180)
  const imageCount = Array.from((drawer || document).querySelectorAll('img')).length
  const helperState = localUpload?.inputState ? JSON.stringify(localUpload.inputState) : '-'
  return `helperInput=${helperState}; fileInputs=${JSON.stringify(fileInputs).slice(0, 300)}; imageCount=${imageCount}; drawerText=${drawerText || '-'}`
}

function findLatestFileInput() {
  const inputs = Array.from(document.querySelectorAll('input[type="file"]'))
  return inputs[inputs.length - 1] || null
}

async function fetchImageAsFile(imageUrl) {
  const directFile = await fetchImageAsFileDirect(imageUrl, 0).catch(() => null)
  if (directFile) return directFile

  const response = await safeRuntimeRequest({
    type: 'GEO_ENV_FETCH_IMAGE_DATA_URL',
    url: imageUrl,
  })
  if (!response?.ok || !response.result?.dataUrl) {
    throw new Error(`封面图片下载失败：${response?.error || '页面与扩展后台均无法下载图片'}`)
  }
  return dataUrlToFile(response.result.dataUrl, response.result.type)
}

async function fetchImageAsFileDirect(imageUrl, depth = 0) {
  let response
  try {
    response = await fetch(imageUrl)
  } catch (error) {
    throw new Error(`封面图片下载失败：${error.message}`)
  }
  if (!response.ok) {
    throw new Error(`封面图片下载失败：HTTP ${response.status}`)
  }
  const blob = await response.blob()
  const type = blob.type || 'image/jpeg'
  if (type.includes('json') || type.includes('text')) {
    const bodyText = await blob.text().catch(() => '')
    const nestedUrl = extractImageUrlFromJsonText(bodyText)
    if (nestedUrl && depth < 2) {
      return fetchImageAsFileDirect(nestedUrl, depth + 1)
    }
    throw new Error(`封面图片类型不支持：${type}；url=${imageUrl}；响应=${bodyText.slice(0, 240) || '-'}`)
  }
  const ext = type.includes('png') ? 'png' : type.includes('webp') ? 'webp' : 'jpg'
  return new File([blob], `geo-cover.${ext}`, { type })
}

function extractImageUrlFromJsonText(text) {
  try {
    const json = JSON.parse(text)
    return firstText(
      json?.url,
      json?.data?.url,
      json?.data?.previewUrl,
      json?.data?.downloadUrl,
      json?.data?.fileUrl,
      json?.result?.url,
      json?.result?.previewUrl,
      json?.result?.downloadUrl,
    )
  } catch {
    const match = String(text || '').match(/https?:\/\/[^"'\\\s]+/i)
    return match?.[0] || ''
  }
}

function dataUrlToFile(dataUrl, preferredType) {
  const match = String(dataUrl || '').match(/^data:([^;,]+)?;base64,(.+)$/)
  if (!match) throw new Error('封面图片下载失败：扩展后台返回的图片格式无效')
  const type = preferredType || match[1] || 'image/jpeg'
  const binary = atob(match[2])
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index)
  }
  const ext = type.includes('png') ? 'png' : type.includes('webp') ? 'webp' : 'jpg'
  return new File([bytes], `geo-cover.${ext}`, { type })
}

function isToutiaoCoverDrawerImageUploaded() {
  const text = normalizeText(document.body?.innerText || '')
  if (text.includes('已上传') || text.includes('支持拖拽调整图片顺序')) return true
  const drawer = findToutiaoCoverDrawer()
  return Array.from((drawer || document).querySelectorAll('img'))
    .some((img) => isVisibleElement(img) && img.getBoundingClientRect().width >= 50 && img.getBoundingClientRect().height >= 50)
}

async function confirmToutiaoCoverDrawer(platform) {
  const confirm = await waitForCondition(
    () => findDrawerActionButton('确定'),
    8000,
    '头条封面上传抽屉确定按钮未找到',
  )
  await clickClosestAction(confirm, { platform })
  await delay(800)
}

function findDrawerActionButton(text) {
  const drawer = findToutiaoCoverDrawer()
  const root = drawer || document
  return Array.from(root.querySelectorAll('button, [role="button"], div, span'))
    .filter(isVisibleElement)
    .find((el) => normalizeText(el.textContent || el.getAttribute('aria-label') || '') === text) || null
}

function findToutiaoCoverDrawer() {
  const markers = ['上传图片', '本地上传', '扫码上传', '免费正版图片', '我的素材']
  const marker = markers.map((item) => findVisibleTextElement(item)).find(Boolean)
  return marker ? nearestLargeContainer(marker) : null
}

function findVisibleTextElement(text, options = {}) {
  const { exact = false, maxLength = 40 } = options
  const target = normalizeText(text)
  return Array.from(document.querySelectorAll('button, a, [role="button"], [role="tab"], [role="option"], [role="menuitem"], label, div, span, p'))
    .filter(isVisibleElement)
    .find((el) => {
      const value = normalizeText(el.textContent || el.getAttribute('aria-label') || '')
      if (!value || value.length > Math.max(maxLength, target.length)) return false
      return exact ? value === target : value.includes(target)
    }) || null
}

function findTextElementInRoot(root, text, options = {}) {
  const { exact = false, maxLength = 40 } = options
  const target = normalizeText(text)
  return Array.from((root || document).querySelectorAll('button, a, [role="button"], [role="tab"], [role="option"], [role="menuitem"], label, div, span, p'))
    .filter(isVisibleElement)
    .find((el) => {
      const value = normalizeText(el.textContent || el.getAttribute('aria-label') || '')
      if (!value || value.length > Math.max(maxLength, target.length)) return false
      return exact ? value === target : value.includes(target)
    }) || null
}

function findTextElementNear(anchor, text, maxDistance) {
  if (!anchor) return null
  const target = normalizeText(text)
  return Array.from(document.querySelectorAll('button, a, [role="button"], [role="tab"], [role="option"], [role="menuitem"], label, div, span, p'))
    .filter(isVisibleElement)
    .map((el) => ({ el, value: normalizeText(el.textContent || el.getAttribute('aria-label') || ''), distance: elementDistance(anchor, el) }))
    .filter((item) => item.value && item.value.includes(target) && item.distance <= maxDistance)
    .sort((left, right) => left.distance - right.distance || left.value.length - right.value.length)[0]?.el || null
}

function nearestLargeContainer(el) {
  let current = el
  for (let depth = 0; current && depth < 8; depth += 1) {
    const rect = current.getBoundingClientRect()
    if (rect.width >= 220 && rect.height >= 90) return current
    current = current.parentElement
  }
  return el
}

function elementDistance(left, right) {
  const a = left.getBoundingClientRect()
  const b = right.getBoundingClientRect()
  const ax = a.left + a.width / 2
  const ay = a.top + a.height / 2
  const bx = b.left + b.width / 2
  const by = b.top + b.height / 2
  return Math.hypot(ax - bx, ay - by)
}

async function waitForCondition(predicate, timeoutMs, message) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const result = await predicate()
    if (result) return result
    await delay(300)
  }
  throw new Error(message)
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

function removeCoverImageFromContent(html, coverImageUrl) {
  if (!html || !coverImageUrl) return { html, removed: false }
  const coverKey = normalizeImageUrlForComparison(coverImageUrl)
  if (!coverKey) return { html, removed: false }

  const template = document.createElement('template')
  template.innerHTML = html
  let removed = false
  const images = Array.from(template.content.querySelectorAll('img[src]'))
  for (const image of images) {
    const src = image.getAttribute('src') || ''
    if (!sameImageResource(src, coverKey)) continue
    const removable = removableImageBlock(image)
    removable.remove()
    removed = true
  }
  return { html: template.innerHTML, removed }
}

function removableImageBlock(image) {
  let current = image
  for (let depth = 0; current?.parentElement && depth < 3; depth += 1) {
    const parent = current.parentElement
    const text = normalizeText(parent.textContent || '')
    const images = parent.querySelectorAll?.('img')?.length || 0
    const children = parent.children?.length || 0
    if (!text && images === 1 && children <= 2 && ['P', 'DIV', 'FIGURE'].includes(parent.tagName)) {
      current = parent
      continue
    }
    break
  }
  return current
}

function sameImageResource(src, normalizedCoverKey) {
  const imageKey = normalizeImageUrlForComparison(src)
  return Boolean(imageKey && normalizedCoverKey && imageKey === normalizedCoverKey)
}

function normalizeImageUrlForComparison(value) {
  const raw = String(value || '').replace(/&amp;/g, '&').trim()
  if (!raw) return ''
  try {
    const url = new URL(raw, location.href)
    const materialMatch = url.pathname.match(/\/api\/public\/brand-materials\/(\d+)\/stream/i)
    if (materialMatch) return `brand-material:${materialMatch[1]}`
    url.search = ''
    url.hash = ''
    return `${url.origin}${url.pathname}`
  } catch (_) {
    return raw.split('?')[0].split('#')[0]
  }
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
    return xiaohongshuEditorSelectors().title.concat(common)
  }
  if (platform === 'baijiahao') {
    return baijiahaoEditorSelectors().title.concat(common)
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
      'div[role="textbox"]',
    ].concat(common)
  }
  if (platform === 'xiaohongshu') {
    return xiaohongshuEditorSelectors().content.concat(common)
  }
  if (platform === 'baijiahao') {
    return baijiahaoEditorSelectors().content.concat(common)
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
    return xiaohongshuEditorSelectors().tags
  }
  if (platform === 'baijiahao') {
    return baijiahaoEditorSelectors().tags
  }
  return [
    '[data-geo-fill="tags"]',
    'input[placeholder*="标签"]',
    'textarea[placeholder*="标签"]',
  ]
}

function xiaohongshuEditorSelectors() {
  const selectors = globalThis.__GEO_XIAOHONGSHU_PLATFORM__?.editorSelectors?.()
  if (!selectors) {
    throw new Error('XIAOHONGSHU_ADAPTER_NOT_LOADED：小红书平台选择器适配器未加载，请重新加载扩展')
  }
  return selectors
}

function baijiahaoEditorSelectors() {
  const selectors = globalThis.__GEO_BAIJIAHAO_PLATFORM__?.editorSelectors?.()
  if (!selectors) {
    throw new Error('BAIJIAHAO_ADAPTER_NOT_LOADED：百家号平台选择器适配器未加载，请重新加载扩展')
  }
  return selectors
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
  if (location.hostname === 'baijiahao.baidu.com') return 'baijiahao'
  return null
}

function readPlatformIdentity(platform) {
  if (platform === 'toutiao') return readToutiaoIdentity()
  if (platform === 'zhihu') return readZhihuIdentity()
  if (platform === 'xiaohongshu') return readXiaohongshuIdentity()
  if (platform === 'baijiahao') return readBaijiahaoIdentity()
  return {
    implemented: false,
    accountIds: [],
    accountNames: [],
    diagnostics: `暂未实现 ${platform || 'unknown'} 账号读取`,
  }
}

function readBaijiahaoIdentity() {
  const accountNames = new Set()
  const rawVisibleText = document.body?.innerText || document.body?.textContent || ''
  const visibleText = normalizeText(rawVisibleText)
  collectBaijiahaoAccountNamesFromDom(accountNames)
  collectBaijiahaoAccountNamesFromText(collectStorageAndScriptIdentityText(), accountNames)
  return {
    implemented: true,
    accountIds: [],
    accountNames: Array.from(accountNames),
    diagnostics: `href=${location.href}; visibleTextLength=${visibleText.length}; accountNames=${Array.from(accountNames).join(',') || '-'}`,
  }
}

function collectBaijiahaoAccountNamesFromDom(accountNames) {
  const selectors = [
    '[class*="user"] [class*="name"]',
    '[class*="User"] [class*="Name"]',
    '[class*="account"] [class*="name"]',
    '[class*="Account"] [class*="Name"]',
    '[class*="avatar"] + *',
    '[class*="Avatar"] + *',
  ]
  for (const selector of selectors) {
    for (const el of Array.from(document.querySelectorAll(selector))) {
      if (!isVisibleElement(el) && !hasVisibleAncestor(el)) continue
      if (!isTopRightAccountElement(el)) continue
      const text = normalizeAccountName(el.textContent || el.getAttribute('aria-label') || '')
      if (isLikelyBaijiahaoAccountName(text)) accountNames.add(text)
    }
  }
}

function collectBaijiahaoAccountNamesFromText(text, accountNames) {
  const patterns = [
    /"nickname"\s*:\s*"([^"]{2,80})"/g,
    /"nickName"\s*:\s*"([^"]{2,80})"/g,
    /"userName"\s*:\s*"([^"]{2,80})"/g,
    /"accountName"\s*:\s*"([^"]{2,80})"/g,
    /"name"\s*:\s*"([^"]{2,80})"/g,
  ]
  for (const pattern of patterns) {
    for (const match of text.matchAll(pattern)) {
      const value = normalizeAccountName(match[1])
      if (isLikelyBaijiahaoAccountName(value)) accountNames.add(value)
    }
  }
}

function collectStorageAndScriptIdentityText() {
  const parts = []
  for (const storage of safeBrowserStorages()) {
    for (let index = 0; index < storage.length; index += 1) {
      const key = storage.key(index)
      if (!key || !/(current|login|session|profile|user|account|author|self|me)/i.test(key)) continue
      try {
        parts.push(`${key}:${storage.getItem(key) || ''}`)
      } catch {
        // ignore storage access failures
      }
    }
  }
  for (const script of Array.from(document.scripts).slice(0, 80)) {
    const text = script.textContent || ''
    if (/(currentUser|loginUser|userInfo|account|nickname|userName)/i.test(text)) {
      parts.push(text.slice(0, 120_000))
    }
  }
  return parts.join('\n')
}

function safeBrowserStorages() {
  const storages = []
  try {
    if (window.localStorage) storages.push(window.localStorage)
  } catch {
    // ignore storage access failures
  }
  try {
    if (window.sessionStorage) storages.push(window.sessionStorage)
  } catch {
    // ignore storage access failures
  }
  return storages
}

function isLikelyBaijiahaoAccountName(value) {
  const text = normalizeAccountName(value)
  if (text.length < 2 || text.length > 40) return false
  if (/^(首页|图文|视频|动态|直播|合集|图集|AI成片|基础信息|活动投稿|智能创作|创作声明|发布|预览|存草稿|定时发布|取消|确定|登录)$/.test(text)) return false
  if (/^[\w.-]{2,40}$/.test(text)) return true
  return /[\u4e00-\u9fa5]/.test(text) && !/[，。！？、]/.test(text)
}

function readZhihuIdentity() {
  return zhihuIdentityReader().readIdentity()
}

function zhihuIdentityReader() {
  if (globalThis.__GEO_ZHIHU_IDENTITY_READER__) return globalThis.__GEO_ZHIHU_IDENTITY_READER__
  const factory = globalThis.__GEO_ZHIHU_PLATFORM__?.createIdentityReader
  if (typeof factory !== 'function') {
    throw new Error('ZHIHU_ADAPTER_NOT_LOADED：知乎平台身份读取适配器未加载，请重新加载扩展')
  }
  globalThis.__GEO_ZHIHU_IDENTITY_READER__ = factory({
    normalizeText,
    isVisibleElement,
    hasVisibleAncestor,
    isTopRightAccountElement,
  })
  return globalThis.__GEO_ZHIHU_IDENTITY_READER__
}

function readXiaohongshuIdentity() {
  return xiaohongshuIdentityReader().readIdentity()
}

function xiaohongshuIdentityReader() {
  if (globalThis.__GEO_XIAOHONGSHU_IDENTITY_READER__) return globalThis.__GEO_XIAOHONGSHU_IDENTITY_READER__
  const factory = globalThis.__GEO_XIAOHONGSHU_PLATFORM__?.createIdentityReader
  if (typeof factory !== 'function') {
    throw new Error('XIAOHONGSHU_ADAPTER_NOT_LOADED：小红书平台身份读取适配器未加载，请重新加载扩展')
  }
  globalThis.__GEO_XIAOHONGSHU_IDENTITY_READER__ = factory({
    normalizeText,
    normalizeAccountName,
    isVisibleElement,
    hasVisibleAncestor,
    isTopRightAccountElement,
  })
  return globalThis.__GEO_XIAOHONGSHU_IDENTITY_READER__
}

function xiaohongshuEntryNavigator() {
  if (globalThis.__GEO_XIAOHONGSHU_ENTRY_NAVIGATOR__) return globalThis.__GEO_XIAOHONGSHU_ENTRY_NAVIGATOR__
  const factory = globalThis.__GEO_XIAOHONGSHU_PLATFORM__?.createEntryNavigator
  if (typeof factory !== 'function') {
    throw new Error('XIAOHONGSHU_ADAPTER_NOT_LOADED：小红书平台入口导航适配器未加载，请重新加载扩展')
  }
  globalThis.__GEO_XIAOHONGSHU_ENTRY_NAVIGATOR__ = factory({
    normalizeText,
    findTitleElement,
    findContentElement,
    buildFillProfile,
    findClickableByExactText,
    findClickableByShortText,
    clickClosestAction,
    showStatus,
    delay,
    collectDiagnostics,
    isVisibleElement,
    isInteractiveElement,
  })
  return globalThis.__GEO_XIAOHONGSHU_ENTRY_NAVIGATOR__
}

function readToutiaoIdentity() {
  const accountIds = new Set()
  const accountNames = new Set()
  const rawVisibleText = document.body?.innerText || document.body?.textContent || ''
  const visibleText = normalizeText(rawVisibleText)
  collectToutiaoMediaIdsFromText(visibleText, accountIds)
  collectToutiaoAccountNamesFromText(rawVisibleText, accountNames)
  collectAccountIdsFromText(visibleText, accountIds)
  collectAccountIdentityFromDom(accountIds, accountNames)
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
    /"media"\s*:\s*\{[\s\S]{0,20000}?"name"\s*:\s*"([^"]{2,80})"/g,
    /"user"\s*:\s*\{[\s\S]{0,8000}?"screen_name"\s*:\s*"([^"]{2,80})"/g,
    /"user"\s*:\s*\{[\s\S]{0,8000}?"name"\s*:\s*"([^"]{2,80})"/g,
    /"screen_name"\s*:\s*"([^"]{2,80})"/g,
    /"screenName"\s*:\s*"([^"]{2,80})"/g,
    /"display_name"\s*:\s*"([^"]{2,80})"/g,
    /"displayName"\s*:\s*"([^"]{2,80})"/g,
    /"nick_name"\s*:\s*"([^"]{2,80})"/g,
    /"nickname"\s*:\s*"([^"]{2,80})"/g,
    /"accountName"\s*:\s*"([^"]{2,80})"/g,
    /"account_name"\s*:\s*"([^"]{2,80})"/g,
    /头条号名称[:：]?([^\s，。！？、]{2,60})/g,
    /账号名称[:：]?([^\s，。！？、]{2,60})/g,
    /昵称[:：]?([^\s，。！？、]{2,60})/g,
  ]
  for (const pattern of patterns) {
    for (const match of text.matchAll(pattern)) {
      const value = normalizeAccountName(match[1])
      if (isLikelyToutiaoAccountName(value)) accountNames.add(value)
    }
  }
}

function collectAccountIdsFromText(text, candidates) {
  const patterns = [
    /头条号ID[:：]?\s*(\d{6,})/g,
    /头条号[:：]?\s*(\d{6,})/g,
    /media_id["']?\s*[:=]\s*["']?(\d{6,})/g,
    /mediaId["']?\s*[:=]\s*["']?(\d{6,})/g,
    /mediaIdStr["']?\s*[:=]\s*["']?(\d{6,})/g,
    /user_id["']?\s*[:=]\s*["']?(\d{6,})/g,
    /userId["']?\s*[:=]\s*["']?(\d{6,})/g,
    /account_id["']?\s*[:=]\s*["']?(\d{6,})/g,
    /accountId["']?\s*[:=]\s*["']?(\d{6,})/g,
  ]
  for (const pattern of patterns) {
    for (const match of text.matchAll(pattern)) {
      const value = normalizeAccountId(match[1])
      if (value) candidates.add(value)
    }
  }
}

function collectAccountIdentityFromDom(accountIds, accountNames) {
  const selectors = [
    '[class*="account"]',
    '[class*="user"]',
    '[class*="profile"]',
    '[class*="avatar"]',
    '[class*="name"]',
    '[aria-label]',
    '[title]',
    'img[alt]',
  ]
  const elements = Array.from(document.querySelectorAll(selectors.join(','))).slice(0, 250)
  for (const element of elements) {
    const text = [
      element.getAttribute('title'),
      element.getAttribute('aria-label'),
      element.getAttribute('alt'),
      element.textContent,
    ].filter(Boolean).join(' ')
    if (!text) continue
    collectToutiaoMediaIdsFromText(text, accountIds)
    collectToutiaoAccountNamesFromText(text, accountNames)
    collectAccountIdsFromText(text, accountIds)
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

function isLikelyToutiaoAccountName(value) {
  const text = normalizeAccountName(value)
  if (text.length < 2 || text.length > 60) return false
  if (/^\d+$/.test(text)) return false
  if (/^https?:\/\//i.test(text)) return false
  if (/^(首页|发布|发文|发视频|文章管理|作品管理|数据|数据助手|创作中心|头条号|今日头条|西瓜视频|抖音|登录|退出|设置|消息|通知|保存|取消|发布文章|图文|视频|问答)$/.test(text)) return false
  if (/[，。！？、；;<>]/.test(text)) return false
  return true
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
  if (el.isContentEditable || el.getAttribute?.('contenteditable') === 'true') {
    setEditablePlainText(el, title)
    return true
  }
  setTextValue(el, title)
  return true
}

async function fillContent(html, contentElement, fillProfile) {
  if (!html) return false
  const el = contentElement
  if (!el) return false
  if (fillProfile?.platform === 'zhihu') {
    return setZhihuEditablePlainText(el, htmlToPlainText(html))
  }
  if (el.isContentEditable || el.getAttribute('contenteditable') === 'true') {
    setEditableHtml(el, html)
    dispatchEditEvents(el)
  } else {
    setTextValue(el, htmlToPlainText(html))
  }
  return true
}

function clearEditableTextWithSelection(el) {
  focusEditableElement(el)
  selectEditableContents(el)
  document.execCommand?.('delete', false)
}

function focusEditableElement(el) {
  el.scrollIntoView?.({ block: 'center', inline: 'nearest' })
  el.focus()
}

function selectEditableContents(el) {
  const selection = window.getSelection()
  const range = document.createRange()
  range.selectNodeContents(el)
  selection?.removeAllRanges()
  selection?.addRange(range)
}

function dispatchPasteIntoEditable(el, text) {
  try {
    const data = new DataTransfer()
    data.setData('text/plain', text)
    data.setData('text/html', plainTextToHtml(text))
    const event = new ClipboardEvent('paste', {
      bubbles: true,
      cancelable: true,
      clipboardData: data,
    })
    return el.dispatchEvent(event)
  } catch (_) {
    try {
      const data = new DataTransfer()
      data.setData('text/plain', text)
      data.setData('text/html', plainTextToHtml(text))
      const event = new Event('paste', { bubbles: true, cancelable: true })
      Object.defineProperty(event, 'clipboardData', { value: data })
      return el.dispatchEvent(event)
    } catch (__) {
      return false
    }
  }
}

function plainTextToHtml(text) {
  return String(text || '')
    .split(/\n{2,}/)
    .map((paragraph) => `<p>${escapeHtml(paragraph).replace(/\n/g, '<br>')}</p>`)
    .join('')
}

function escapeHtml(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
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
  if (fillProfile.platform === 'xiaohongshu' && xiaohongshuEntryNavigator().isCreatorShellVisible()) {
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
    if (location.hostname === 'www.xiaohongshu.com') {
      showStatus('小红书当前处于用户端页面，切换到创作服务平台', 'info')
      location.href = 'https://creator.xiaohongshu.com/publish/publish'
      await delay(2200)
      return
    }
    await xiaohongshuEntryNavigator().maybeSelectEditorMode(fillProfile)
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

async function waitForEditorCandidate(fillProfile, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (findTitleElement(fillProfile) && findContentElement(null, fillProfile)) return true
    await delay(300)
  }
  return false
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
  if (requiresTrustedClick(options.platform)) {
    await requestTrustedClick(el, options)
  }
}

async function clickSingleAction(el, options = {}) {
  const target = el.closest?.('button, a, [role="button"], [role="tab"], [role="menuitem"]') || el
  target.scrollIntoView?.({ block: 'center', inline: 'center' })
  await delay(100)
  firePointerClick(target, options)
  target.click?.()
  if (requiresTrustedClick(options.platform)) {
    await requestTrustedClick(target, options)
  }
}

async function clickTrustedActionOnce(el, options = {}) {
  const target = el.closest?.('button, a, [role="button"], [role="tab"], [role="menuitem"]') || el
  target.scrollIntoView?.({ block: 'center', inline: 'center' })
  await delay(100)
  if (requiresTrustedClick(options.platform)) {
    await requestTrustedClick(target, options)
    return
  }
  firePointerClick(target, options)
  target.click?.()
}

function isInteractiveElement(el) {
  return Boolean(el?.closest?.('button, a, [role="button"], [role="tab"], [role="menuitem"]'))
}

function firePointerClick(el, options = {}) {
  const rect = el.getBoundingClientRect()
  const clientX = Number.isFinite(options.absoluteClientX)
    ? options.absoluteClientX
    : rect.left + rect.width * (options.clickRatioX || 0.5)
  const clientY = Number.isFinite(options.absoluteClientY)
    ? options.absoluteClientY
    : rect.top + rect.height * (options.clickRatioY || 0.5)
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

function requiresTrustedClick(platform) {
  return ['xiaohongshu', 'toutiao', 'zhihu', 'baijiahao'].includes(normalizePlatform(platform))
}

async function requestTrustedClick(el, options = {}) {
  const rect = el.getBoundingClientRect()
  if (rect.width <= 0 || rect.height <= 0) return
  const clientX = Math.round(rect.left + rect.width * (options.clickRatioX || 0.5))
  const clientY = Math.round(rect.top + rect.height * (options.clickRatioY || 0.5))
  await requestTrustedClickAt({ clientX, clientY }, options.platform, normalizeText(el.textContent || el.getAttribute('aria-label') || '').slice(0, 30), rect)
}

async function requestTrustedClickAt(point, platform, label = '', rect = null) {
  if (!Number.isFinite(point?.clientX) || !Number.isFinite(point?.clientY)) return
  globalThis.__GEO_ENV_ACTIVE_FILL_TASK_CONTEXT = {
    ...(globalThis.__GEO_ENV_ACTIVE_FILL_TASK_CONTEXT || {}),
    lastTrustedClick: {
      label,
      clientX: point.clientX,
      clientY: point.clientY,
      rect: rect
        ? `${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)}`
        : '-',
    },
  }
  await safeRuntimeRequest({
    type: 'GEO_ENV_TRUSTED_CLICK',
    click: {
      clientX: point.clientX,
      clientY: point.clientY,
      label,
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
