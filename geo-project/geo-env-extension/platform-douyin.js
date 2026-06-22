;(function installDouyinPlatform(global) {
  const PUBLISH_URL = 'https://creator.douyin.com/creator-micro/content/post/article?media_type=article&type=new&enter_from=publish_page'
  const MANAGE_URL = 'https://creator.douyin.com/creator-micro/content/manage'
  const MIN_SCHEDULE_LEAD_MINUTES = 120
  const MAX_SCHEDULE_LEAD_MINUTES = 14 * 24 * 60

  const RETRYABLE_FAILURE_CODES = new Set([
    'DOUYIN_ARTICLE_FORM_NOT_READY',
    'DOUYIN_COVER_UPLOAD_TIMEOUT',
    'DOUYIN_PUBLISH_NOT_CONFIRMED',
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
    if (normalizePlatform(platform) !== 'douyin' && !text.includes('抖音')) return ''
    if (text.includes('文章表单')) return 'DOUYIN_ARTICLE_FORM_NOT_READY'
    if (text.includes('头图上传')) return 'DOUYIN_HEAD_IMAGE_UPLOAD_FAILED'
    if (text.includes('封面上传')) return 'DOUYIN_COVER_UPLOAD_TIMEOUT'
    if (text.includes('封面图片')) return 'DOUYIN_COVER_REQUIRED'
    if (text.includes('定时时间过远')) return 'DOUYIN_SCHEDULE_TIME_TOO_LATE'
    if (text.includes('定时时间无效')) return 'DOUYIN_SCHEDULE_TIME_INVALID'
    if (text.includes('发布后未检测到成功状态')) return 'DOUYIN_PUBLISH_NOT_CONFIRMED'
    if (text.includes('账号一致性校验失败')) return 'ACCOUNT_MISMATCH'
    return 'DOUYIN_FILL_FAILED'
  }

  function isRetryableFailureCode(code) {
    return RETRYABLE_FAILURE_CODES.has(String(code || '').trim())
  }

  function editorSelectors() {
    return {
      title: [
        'input[placeholder*="文章标题"]',
        'textarea[placeholder*="文章标题"]',
        'input[placeholder*="标题"]',
        'textarea[placeholder*="标题"]',
      ],
      content: [
        '[contenteditable="true"]',
        '.ProseMirror[contenteditable="true"]',
        '[data-slate-editor="true"][contenteditable="true"]',
        'textarea[placeholder*="正文"]',
      ],
      tags: [],
    }
  }

  function createPublishOptionsAdapter(deps = {}) {
    return {
      platform: 'douyin',
      fillPublishOptions: (payload, fillProfile) => fillPublishOptions(payload, fillProfile, deps),
      describeState: () => describeState(deps),
    }
  }

  async function maybeSelectArticleEditor(deps = {}) {
    const waitForCondition = requireDependency(deps.waitForCondition, 'waitForCondition')
    if (isArticleFormVisible(deps)) return

    if (!isDouyinPublishPage()) {
      location.href = PUBLISH_URL
      return
    }

    if (new URLSearchParams(location.search).get('media_type') !== 'article') {
      location.href = PUBLISH_URL
      return
    }

    await clickIfVisible(['发布文章'], deps, { timeoutMs: 8000, required: false })
    await dismissExistingDraftPrompt(deps)
    if (isArticleFormVisible(deps)) return
    await clickIfVisible(['我要发文'], deps, { timeoutMs: 8000, required: false })
    await waitForCondition(
      () => isArticleFormVisible(deps),
      20000,
      `抖音文章表单未就绪；${describeState(deps)}`,
    )
  }

  async function fillPublishOptions(payload, fillProfile, deps) {
    await maybeSelectArticleEditor(deps)
    const options = resolvePublishOptions(payload)
    const actions = []
    const headImageUrl = options.headImageUrl || options.coverImageUrl
    if (!headImageUrl) {
      throw new Error('DOUYIN_COVER_REQUIRED：抖音文章必须配置头图/封面图片')
    }
    const headImage = await uploadImageInSection('文章头图', ['点击上传图片', '上传图片'], headImageUrl, fillProfile.platform, deps, {
      optional: false,
      helperClicksUploadEntry: true,
      success: () => hasSectionImage('文章头图', deps),
      afterUpload: () => confirmImageEditor(deps, fillProfile.platform),
    })
    if (headImage.filled) actions.push(headImage.message)
    if (!hasSectionImage('文章头图', deps)) {
      throw new Error(`DOUYIN_HEAD_IMAGE_UPLOAD_FAILED：抖音文章头图未填充；${describeState(deps)}`)
    }
    if (!hasSectionImage('封面设置', deps)) {
      actions.push('抖音封面已由文章头图同步生成')
    }

    await selectVisibilityPublic(deps, fillProfile.platform)
    if (options.scheduledAt) {
      const schedule = await fillScheduledPublish(options.scheduledAt, fillProfile.platform, payload, deps)
      actions.push(schedule.message)
      return {
        filled: true,
        scheduled: true,
        publishVerification: schedule.publishVerification,
        message: actions.join('，'),
      }
    }

    const publish = await publishNow(fillProfile.platform, payload, deps)
    actions.push(publish.message)
    return {
      filled: true,
      published: true,
      publishVerification: publish.publishVerification,
      message: actions.join('，'),
    }
  }

  function isDouyinPublishPage() {
    return location.hostname === 'creator.douyin.com'
      && (
        location.pathname.includes('/creator-micro/content/upload')
        || location.pathname.includes('/creator-micro/content/post/article')
      )
  }

  function resolvePublishOptions(payload = {}) {
    const profileOptions = payload.profile?.platformOptions || {}
    const platformOptions = payload.platformOptions || {}
    const options = payload.douyinOptions || platformOptions.douyin || profileOptions.douyin || {}
    return {
      headImageUrl: firstText(
        payload.headImageUrl,
        platformOptions.headImageUrl,
        profileOptions.headImageUrl,
        options.headImageUrl,
      ),
      coverImageUrl: firstText(
        payload.coverImageUrl,
        platformOptions.coverImageUrl,
        profileOptions.coverImageUrl,
        options.coverImageUrl,
      ),
      scheduledAt: firstText(
        payload.scheduledAt,
        payload.platformScheduledAt,
        platformOptions.scheduledAt,
        platformOptions.platformScheduledAt,
        profileOptions.scheduledAt,
        profileOptions.platformScheduledAt,
        options.scheduledAt,
        options.platformScheduledAt,
      ),
    }
  }

  function firstText(...values) {
    for (const value of values) {
      if (typeof value === 'string' && value.trim()) return value.trim()
    }
    return ''
  }

  async function dismissExistingDraftPrompt(deps) {
    const text = bodyText(deps)
    if (!text.includes('你还有上次未发布的文章')) return
    await clickIfVisible(['我要发文'], deps, { timeoutMs: 8000, required: true })
  }

  async function uploadImageInSection(sectionLabel, uploadTexts, imageUrl, platform, deps, options = {}) {
    const waitForCondition = requireDependency(deps.waitForCondition, 'waitForCondition')
    const uploadCoverImageFromLocalHelper = requireDependency(deps.uploadCoverImageFromLocalHelper, 'uploadCoverImageFromLocalHelper')
    const delay = requireDependency(deps.delay, 'delay')
    if (options.success?.()) return { filled: false, message: `抖音${sectionLabel}已存在` }

    const section = await waitForCondition(
      () => findSection(sectionLabel, deps),
      options.optional ? 5000 : 10000,
      `抖音${sectionLabel}区域未找到；${describeState(deps)}`,
    ).catch((error) => {
      if (options.optional) return null
      throw error
    })
    if (!section) return { filled: false, message: `抖音${sectionLabel}未填充：未找到区域` }
    await scrollToSection(sectionLabel, deps)

    const entry = findActionInRoot(section, uploadTexts, deps) || findActionInRoot(document, uploadTexts, deps)
    if (!entry) {
      if (options.optional) return { filled: false, message: `抖音${sectionLabel}未填充：未找到上传入口` }
      throw new Error(`抖音${sectionLabel}上传入口未找到；${describeState(deps)}`)
    }
    const target = uploadClickTarget(entry)
    const clickPoint = elementClickPoint(target)
    if (!options.helperClicksUploadEntry) {
      const target = uploadClickTarget(entry)
      deps.showStatus?.(`抖音${sectionLabel}点击上传入口：${bodyText({ ...deps, root: target }).slice(0, 40)}`, 'info')
      await click(target, platform, deps)
      await delay(500)
    } else {
      deps.showStatus?.(`抖音${sectionLabel}通过扩展调试通道选择图片`, 'info')
    }
    await uploadCoverImageFromLocalHelper(imageUrl, platform, `抖音${sectionLabel}`, {
      click: clickPoint
        ? {
          clientX: clickPoint.clientX,
          clientY: clickPoint.clientY,
          screenX: clickPoint.screenX,
          screenY: clickPoint.screenY,
          label: bodyText({ ...deps, root: target }).slice(0, 40),
        }
        : null,
    })
    if (options.afterUpload) await options.afterUpload()
    await scrollToSection(sectionLabel, deps)
    await waitForCondition(
      async () => {
        await scrollToSection(sectionLabel, deps)
        return options.success?.()
      },
      25000,
      `抖音${sectionLabel}上传完成超时；${describeState(deps)}`,
    )
    return { filled: true, message: `已上传抖音${sectionLabel}` }
  }

  function uploadClickTarget(entry) {
    return entry.closest?.('[class*="mycard"], [class*="content-upload"], [role="button"], button') || entry
  }

  function elementClickPoint(el) {
    if (!el?.getBoundingClientRect) return null
    const icon = el.querySelector?.('[class*="addIcon"], [class*="addInnerIcon"]')
    const rect = (icon && isVisible(icon) ? icon : el).getBoundingClientRect()
    if (rect.width <= 0 || rect.height <= 0) return null
    return {
      clientX: Math.round(rect.left + rect.width / 2),
      clientY: Math.round(rect.top + rect.height / 2),
      screenX: window.screenX + Math.round((window.outerWidth - window.innerWidth) / 2) + Math.round(rect.left + rect.width / 2),
      screenY: window.screenY + Math.round(window.outerHeight - window.innerHeight) + Math.round(rect.top + rect.height / 2),
    }
  }

  async function confirmImageEditor(deps, platform) {
    const waitForCondition = requireDependency(deps.waitForCondition, 'waitForCondition')
    const dialog = await waitForCondition(
      () => findDialogByText(['图片编辑', '确定'], deps) || findDialogByText(['编辑封面', '完成'], deps),
      12000,
      `抖音图片编辑弹窗未出现；${describeState(deps)}`,
    )
    const done = findActionInRoot(dialog, ['确定', '完成'], deps)
    if (!done) throw new Error(`抖音图片编辑确认按钮未找到；${describeState(deps)}`)
    await click(done, platform, deps)
    await waitForCondition(
      () => !findDialogByText(['图片编辑', '确定'], deps),
      12000,
      `抖音图片编辑确认后弹窗未关闭；${describeState(deps)}`,
    )
    await scrollToSection('文章头图', deps)
    await waitForCondition(
      async () => {
        await scrollToSection('文章头图', deps)
        return hasSectionImage('文章头图', deps) || hasSectionImage('封面设置', deps)
      },
      25000,
      `抖音图片编辑确认后未检测到头图上传成功；${describeState(deps)}`,
    )
  }

  async function scrollToSection(label, deps = {}) {
    const delay = deps.delay || ((ms) => new Promise((resolve) => setTimeout(resolve, ms)))
    const target = findSection(label, deps) || findSectionLabel(label, deps)
    if (!target?.scrollIntoView) return false
    target.scrollIntoView({ block: 'center', inline: 'nearest' })
    scrollClosestContainersIntoView(target)
    await delay(350)
    return true
  }

  function findSectionLabel(label, deps = {}) {
    const normalizeText = deps.normalizeText || defaultNormalizeText
    return Array.from(document.querySelectorAll('label, div, span, p'))
      .filter(isVisible)
      .filter((el) => isSectionLabel(el, label, normalizeText))
      .sort((left, right) => labelScore(right, label, normalizeText) - labelScore(left, label, normalizeText))[0] || null
  }

  function scrollClosestContainersIntoView(target) {
    const rect = target.getBoundingClientRect()
    const centerY = rect.top + rect.height / 2
    let current = target.parentElement
    for (let depth = 0; current && depth < 8; depth += 1) {
      if (current.scrollHeight > current.clientHeight + 20) {
        const containerRect = current.getBoundingClientRect()
        current.scrollTop += centerY - (containerRect.top + containerRect.height / 2)
      }
      current = current.parentElement
    }
  }

  async function selectVisibilityPublic(deps, platform) {
    const publicButton = findActionInRoot(document, ['公开'], deps)
    if (publicButton) await click(publicButton, platform, deps)
  }

  async function fillScheduledPublish(scheduledAt, platform, payload, deps) {
    const value = normalizeScheduleDateTime(scheduledAt)
    if (!value.full) throw new Error(`DOUYIN_SCHEDULE_TIME_INVALID：抖音定时时间无效：${scheduledAt}`)
    const adjusted = adjustScheduleRange(value)
    await clickIfVisible(['定时发布'], deps, { timeoutMs: 10000, required: true, platform })
    await setScheduleInputValue(adjusted.full, deps)
    assertPublishPreflight(payload, deps, { scheduledAt: adjusted.full, scheduled: true })
    const publishVerification = await submitPublish(platform, payload, deps, adjusted.full, 'scheduled')
    return {
      filled: true,
      scheduled: true,
      publishVerification,
      message: adjusted.adjustedFrom
        ? `已设置抖音定时发布=${adjusted.full}（原计划=${adjusted.adjustedFrom}，已调整到平台最早可选时间）`
        : `已设置抖音定时发布=${adjusted.full}`,
    }
  }

  async function publishNow(platform, payload, deps) {
    await clickIfVisible(['立即发布'], deps, { timeoutMs: 8000, required: false, platform })
    assertPublishPreflight(payload, deps, { scheduled: false })
    const publishVerification = await submitPublish(platform, payload, deps, '', 'published')
    return {
      publishVerification,
      message: '已提交抖音立即发布',
    }
  }

  async function setScheduleInputValue(full, deps) {
    const waitForCondition = requireDependency(deps.waitForCondition, 'waitForCondition')
    const input = await waitForCondition(
      () => findScheduleInput(),
      10000,
      `抖音定时时间输入框未找到；target=${full}；${describeState(deps)}`,
    )
    setNativeValue(input, full)
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
    input.dispatchEvent(new Event('blur', { bubbles: true }))
    await waitForCondition(
      () => (findScheduleInput()?.value || '').replace('T', ' ').slice(0, 16) === full,
      5000,
      `抖音定时时间未保持目标值；target=${full}；current=${findScheduleInput()?.value || '-'}；${describeState(deps)}`,
    )
  }

  function assertPublishPreflight(payload, deps = {}, options = {}) {
    const title = firstText(payload.title, payload.articleTitle).slice(0, 30)
    const titleInput = findTitleInput()
    const titleValue = titleInput ? String(titleInput.value || '').trim() : ''
    if (!title || !titleValue || !titleValue.includes(title)) {
      throw new Error(`DOUYIN_PREFLIGHT_TITLE_MISSING：抖音发布前标题校验失败；expected=${title || '-'}；actual=${titleValue || '-'}；${describeState(deps)}`)
    }
    const editorText = getArticleEditorText(deps)
    if (editorText.length < 10 || editorText.includes('请输入正文')) {
      throw new Error(`DOUYIN_PREFLIGHT_CONTENT_MISSING：抖音发布前正文校验失败；contentLength=${editorText.length}；${describeState(deps)}`)
    }
    if (!hasSectionImage('文章头图', deps) && !hasSectionImage('封面设置', deps)) {
      throw new Error(`DOUYIN_PREFLIGHT_IMAGE_MISSING：抖音发布前头图/封面校验失败；${describeState(deps)}`)
    }
    if (options.scheduled) {
      const scheduleValue = (findScheduleInput()?.value || '').replace('T', ' ').slice(0, 16)
      if (!scheduleValue || !scheduleTextVariants(options.scheduledAt).includes(scheduleValue)) {
        throw new Error(`DOUYIN_PREFLIGHT_SCHEDULE_MISMATCH：抖音发布前定时时间校验失败；expected=${options.scheduledAt || '-'}；actual=${scheduleValue || '-'}；${describeState(deps)}`)
      }
    }
  }

  function findTitleInput() {
    return Array.from(document.querySelectorAll('input[placeholder*="文章标题"], textarea[placeholder*="文章标题"], input[placeholder*="标题"], textarea[placeholder*="标题"]'))
      .filter(isVisible)[0] || null
  }

  function getArticleEditorText(deps = {}) {
    const normalizeText = deps.normalizeText || defaultNormalizeText
    const editors = Array.from(document.querySelectorAll('[contenteditable="true"], .ProseMirror[contenteditable="true"], [data-slate-editor="true"][contenteditable="true"], textarea[placeholder*="正文"]'))
      .filter(isVisible)
      .map((el) => normalizeText(el.innerText || el.value || el.textContent || ''))
      .filter(Boolean)
      .sort((left, right) => right.length - left.length)
    return editors[0] || ''
  }

  async function submitPublish(platform, payload, deps, scheduledAt, platformStatus) {
    const waitForCondition = requireDependency(deps.waitForCondition, 'waitForCondition')
    const publishAttemptKey = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    await clickIfVisible(['发布'], deps, { timeoutMs: 12000, required: true, platform })
    const verification = await waitForCondition(
      () => verifyManagePageAfterPublish(payload, scheduledAt, platformStatus, deps, publishAttemptKey),
      45000,
      `抖音发布后未检测到成功状态；${describeState(deps)}`,
    )
    return verification
  }

  function verifyManagePageAfterPublish(payload, scheduledAt, platformStatus, deps, publishAttemptKey = '') {
    const title = firstText(payload.title, payload.articleTitle).slice(0, 30)
    if (!location.href.includes('/creator-micro/content/manage')) return null
    const reloadKey = `geoDouyinManageReload:${publishAttemptKey || `${title || 'unknown'}:${scheduledAt || platformStatus || 'now'}`}`
    const record = findManageRecord({ title, scheduledAt, platformStatus, deps, reloadKey })
    if (!record) {
      maybeReloadManagePage(reloadKey)
      return null
    }
    return {
      verified: true,
      platformStatus,
      pageStatusCode: normalizeManageStatus(record.pageStatus, platformStatus),
      pageStatus: record.pageStatus,
      platformScheduledAt: scheduledAt || null,
      scheduledAtText: record.scheduledAtText || scheduledAt || null,
      platformPublishId: record.platformPublishId || null,
      platformPublishedUrl: record.platformPublishedUrl || null,
      coverImageUrl: record.coverImageUrl || null,
      recordLinks: record.links,
      title,
      manageUrl: location.href,
      matchedText: record.text.slice(0, 180),
      refreshed: record.reloadCount > 0,
      reloadCount: record.reloadCount,
    }
  }

  function findManageRecord({ title, scheduledAt, platformStatus, deps, reloadKey }) {
    if (!title) return null
    const reloadCount = getManageReloadState(reloadKey)?.count || 0
    const candidates = extractManageRecordCandidates(deps)
      .filter((item) => item.text.includes(title))
      .sort((left, right) => manageRecordScore(right, title, scheduledAt, platformStatus) - manageRecordScore(left, title, scheduledAt, platformStatus))
    const matched = candidates.find((item) => isExpectedManageRecord(item, title, scheduledAt, platformStatus))
    if (!matched) return null
    return {
      ...matched,
      reloadCount,
      pageStatus: extractManageStatus(matched.text),
      scheduledAtText: extractManageScheduleText(matched.text, scheduledAt),
      links: extractManageRecordLinks(matched.el),
      platformPublishedUrl: extractManageRecordPublishedUrl(matched.el),
      platformPublishId: extractManageRecordPublishId(matched.el),
      coverImageUrl: extractManageRecordImageUrl(matched.el),
    }
  }

  function extractManageRecordCandidates(deps = {}) {
    const normalizeText = deps.normalizeText || defaultNormalizeText
    return Array.from(document.querySelectorAll('section, article, li, tr, div'))
      .filter(isVisible)
      .map((el) => {
        const rect = el.getBoundingClientRect()
        return {
          el,
          rect,
          text: normalizeText(el.innerText || el.textContent || ''),
          hasImage: Array.from(el.querySelectorAll('img')).some(isVisible) || hasBackgroundImage(el),
        }
      })
      .filter((item) => item.text
        && item.rect.width >= 260
        && item.rect.height >= 60
        && item.rect.width <= 1600
        && item.rect.height <= 420
        && !looksLikeWholeManagePage(item.text))
  }

  function isExpectedManageRecord(item, title, scheduledAt, platformStatus) {
    if (!item.text.includes(title)) return false
    if (scheduledAt && !manageRecordContainsSchedule(item.text, scheduledAt)) return false
    if (platformStatus === 'scheduled' && !/定时发布中|定时|修改定时/.test(item.text)) return false
    if (platformStatus === 'published' && /草稿|未通过|删除作品/.test(item.text) && !/审核中|已发布|发布成功/.test(item.text)) return false
    return true
  }

  function manageRecordScore(item, title, scheduledAt, platformStatus) {
    let score = 0
    if (item.text.includes(title)) score += 1000
    if (item.hasImage) score += 80
    if (scheduledAt && manageRecordContainsSchedule(item.text, scheduledAt)) score += 500
    if (platformStatus === 'scheduled' && /定时发布中|修改定时/.test(item.text)) score += 260
    if (platformStatus === 'published' && /审核中|已发布|发布成功/.test(item.text)) score += 220
    if (/播放|点赞|评论|收藏|详情页进入率/.test(item.text)) score += 60
    score -= Math.min(item.text.length, 1200) / 12
    score -= Math.min(item.rect.width * item.rect.height, 1_000_000) / 120_000
    return score
  }

  function manageRecordContainsSchedule(text, scheduledAt) {
    return scheduleTextVariants(scheduledAt).some((item) => item && text.includes(item))
  }

  function scheduleTextVariants(value) {
    const normalized = normalizeScheduleDateTime(value)
    if (!normalized.full) return []
    const [date, time] = normalized.full.split(' ')
    const [year, month, day] = date.split('-')
    const looseMonth = String(Number(month))
    const looseDay = String(Number(day))
    return [
      normalized.full,
      `${year}年${month}月${day}日 ${time}`,
      `${year}年${looseMonth}月${looseDay}日 ${time}`,
      `${month}月${day}日 ${time}`,
      `${looseMonth}月${looseDay}日 ${time}`,
    ]
  }

  function extractManageStatus(text) {
    return firstMatchingText(text, ['定时发布中', '审核中', '已发布', '发布成功', '未通过', '草稿'])
  }

  function extractManageScheduleText(text, scheduledAt) {
    return scheduleTextVariants(scheduledAt).find((item) => item && text.includes(item)) || ''
  }

  function normalizeManageStatus(pageStatus, platformStatus) {
    if (/定时发布中|定时/.test(pageStatus)) return 'scheduled'
    if (/审核中/.test(pageStatus)) return 'reviewing'
    if (/已发布|发布成功/.test(pageStatus)) return 'published'
    if (/未通过/.test(pageStatus)) return 'rejected'
    if (/草稿/.test(pageStatus)) return 'draft'
    return platformStatus || ''
  }

  function extractManageRecordLinks(el) {
    return Array.from(el?.querySelectorAll?.('a[href]') || [])
      .map((link) => link.href || link.getAttribute('href') || '')
      .filter(Boolean)
      .slice(0, 8)
  }

  function extractManageRecordPublishedUrl(el) {
    return extractManageRecordLinks(el).find((href) => /item_id=|aweme_id=|\/video\/|\/note\//.test(href)) || ''
  }

  function extractManageRecordPublishId(el) {
    const source = [
      ...extractManageRecordLinks(el),
      ...Array.from(el?.attributes || []).map((attr) => `${attr.name}=${attr.value}`),
      el?.innerHTML || '',
    ].join(' ')
    const match = source.match(/(?:item_id|aweme_id|group_id|creation_id|itemId|awemeId)[=:]"?([A-Za-z0-9_-]{6,})/)
      || source.match(/\/(?:video|note)\/([A-Za-z0-9_-]{6,})/)
    return match?.[1] || ''
  }

  function extractManageRecordImageUrl(el) {
    const img = Array.from(el?.querySelectorAll?.('img') || []).find(isVisible)
    if (img?.currentSrc || img?.src) return img.currentSrc || img.src
    const withBg = Array.from(el?.querySelectorAll?.('[style*="background-image"]') || []).find((node) => {
      const bg = getComputedStyle(node).backgroundImage || ''
      return isVisible(node) && bg.includes('url(')
    })
    const bg = withBg ? getComputedStyle(withBg).backgroundImage || '' : ''
    return extractUrlFromCssBackground(bg)
  }

  function extractUrlFromCssBackground(value) {
    const match = String(value || '').match(/url\(["']?([^"')]+)["']?\)/)
    return match?.[1] || ''
  }

  function firstMatchingText(text, values) {
    return values.find((value) => text.includes(value)) || ''
  }

  function looksLikeWholeManagePage(text) {
    return text.includes('作品管理')
      && text.includes('全部作品')
      && text.includes('已发布')
      && text.includes('审核中')
      && text.length > 260
  }

  function maybeReloadManagePage(reloadKey) {
    const state = getManageReloadState(reloadKey)
    const now = Date.now()
    if (state.count >= 3) return
    if (state.lastReloadAt && now - state.lastReloadAt < 5000) return
    const nextState = { count: state.count + 1, lastReloadAt: now }
    sessionStorage.setItem(reloadKey, JSON.stringify(nextState))
    setTimeout(() => location.reload(), 500)
  }

  function getManageReloadState(reloadKey = '') {
    const raw = reloadKey ? sessionStorage.getItem(reloadKey) || '' : ''
    try {
      const parsed = JSON.parse(raw)
      return {
        count: Number(parsed.count) || 0,
        lastReloadAt: Number(parsed.lastReloadAt) || 0,
      }
    } catch (_) {
      return { count: 0, lastReloadAt: 0 }
    }
  }

  function normalizeScheduleDateTime(value) {
    const text = String(value || '').trim().replace('T', ' ').replace(/\//g, '-')
    const match = text.match(/^(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{1,2})/)
    if (!match) return { full: '' }
    const [, year, month, day, hour, minute] = match
    const full = `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}`
    return { full, date: new Date(`${year}-${pad(month)}-${pad(day)}T${pad(hour)}:${pad(minute)}:00`) }
  }

  function adjustScheduleRange(value) {
    const target = value.date
    if (!target || Number.isNaN(target.getTime())) {
      throw new Error(`DOUYIN_SCHEDULE_TIME_INVALID：抖音定时时间无效：${value.full || '-'}`)
    }
    const now = new Date()
    const earliest = new Date(now.getTime() + MIN_SCHEDULE_LEAD_MINUTES * 60 * 1000)
    const latest = new Date(now.getTime() + MAX_SCHEDULE_LEAD_MINUTES * 60 * 1000)
    if (target > latest) {
      throw new Error(`DOUYIN_SCHEDULE_TIME_TOO_LATE：抖音定时时间过远：${value.full}`)
    }
    if (target < earliest) {
      const adjusted = formatDateTime(earliest)
      return { full: adjusted, date: earliest, adjustedFrom: value.full }
    }
    return value
  }

  function formatDateTime(date) {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
  }

  function pad(value) {
    return String(value).padStart(2, '0')
  }

  function findScheduleInput() {
    const candidates = Array.from(document.querySelectorAll('input'))
      .filter((input) => /20\d{2}[-/]\d{1,2}[-/]\d{1,2}\s+\d{1,2}:\d{1,2}/.test(input.value || '')
        || /发布时间|定时/.test(input.placeholder || input.getAttribute('aria-label') || ''))
      .filter(isVisible)
    return candidates[candidates.length - 1] || null
  }

  function setNativeValue(input, value) {
    const descriptor = Object.getOwnPropertyDescriptor(input.constructor.prototype, 'value')
    if (descriptor?.set) descriptor.set.call(input, value)
    else input.value = value
  }

  function isArticleFormVisible(deps = {}) {
    const text = bodyText(deps)
    return text.includes('基础信息')
      && text.includes('文章标题')
      && text.includes('文章正文')
      && Boolean(document.querySelector('input[placeholder*="文章标题"], textarea[placeholder*="文章标题"], [contenteditable="true"]'))
  }

  function findSection(label, deps = {}) {
    const normalizeText = deps.normalizeText || defaultNormalizeText
    const labels = Array.from(document.querySelectorAll('label, div, span, p'))
      .filter(isVisible)
      .filter((el) => isSectionLabel(el, label, normalizeText))
      .sort((left, right) => labelScore(right, label, normalizeText) - labelScore(left, label, normalizeText))
    for (const labelEl of labels) {
      const section = findUploadSectionFromLabel(labelEl, label, normalizeText)
      if (section) return section
    }
    return null
  }

  function hasSectionImage(label, deps = {}) {
    if (hasUploadResultInCompactBlock(label, deps)) return true
    if (hasUploadResultNearLabel(label, deps)) return true
    const section = findSection(label, deps)
    if (!section) return false
    const text = bodyText({ ...deps, root: section })
    const hasImage = Array.from(section.querySelectorAll('img')).some(isVisible)
    return hasImage
      || hasBackgroundImage(section)
      || /点击替换图片|编辑封面|编辑头图|重新上传|更换图片|已上传/.test(text)
  }

  function hasUploadResultInCompactBlock(label, deps = {}) {
    const normalizeText = deps.normalizeText || defaultNormalizeText
    return Array.from(document.querySelectorAll('div, section'))
      .filter(isVisible)
      .some((el) => {
        const rect = el.getBoundingClientRect()
        if (rect.width > 1000 || rect.height > 260) return false
        const text = normalizeText(el.textContent || '')
        return text.includes(label)
          && /点击替换图片|编辑封面|编辑头图|重新上传|更换图片|已上传/.test(text)
          && hasBackgroundImage(el)
      })
  }

  function hasUploadResultNearLabel(label, deps = {}) {
    const normalizeText = deps.normalizeText || defaultNormalizeText
    const labels = Array.from(document.querySelectorAll('label, div, span, p'))
      .filter(isVisible)
      .filter((el) => isSectionLabel(el, label, normalizeText))
      .filter((el) => {
        const rect = el.getBoundingClientRect()
        return rect.width <= 180 && rect.height <= 80
      })
    const indicators = Array.from(document.querySelectorAll('img, button, a, [role="button"], div, span'))
      .filter(isVisible)
      .map((el) => ({
        el,
        rect: el.getBoundingClientRect(),
        text: normalizeText(el.textContent || ''),
        hasBackgroundImage: hasBackgroundImage(el),
      }))
      .filter((item) => item.el.tagName === 'IMG'
        || item.hasBackgroundImage
        || /点击替换图片|编辑封面|编辑头图|重新上传|更换图片|已上传/.test(item.text))

    return labels.some((labelEl) => {
      const labelRect = labelEl.getBoundingClientRect()
      const labelCenterY = labelRect.top + labelRect.height / 2
      return indicators.some((item) => {
        const centerY = item.rect.top + item.rect.height / 2
        const alignedVertically = Math.abs(centerY - labelCenterY) <= 80
        const onRightSide = item.rect.left >= labelRect.right - 10 && item.rect.left - labelRect.right <= 900
        const normalSize = item.rect.width <= 260 && item.rect.height <= 160
        return alignedVertically && onRightSide && normalSize
      })
    })
  }

  function hasBackgroundImage(root) {
    const nodes = root === document ? Array.from(document.querySelectorAll('*')) : [root, ...Array.from(root.querySelectorAll?.('*') || [])]
    return nodes.some((el) => {
      if (!isVisible(el)) return false
      const backgroundImage = getComputedStyle(el).backgroundImage || ''
      return /^url\(["']?https?:\/\//i.test(backgroundImage)
    })
  }

  function isSectionLabel(el, label, normalizeText) {
    const text = normalizeText(el.textContent || '')
    if (text === label || text === `${label}*` || text === `${label} *`) return true
    return text.includes(label) && text.length <= label.length + 3
  }

  function labelScore(el, label, normalizeText) {
    const text = normalizeText(el.textContent || '')
    let score = 0
    if (text === label) score += 100
    if (text.includes('*')) score += 5
    const rect = el.getBoundingClientRect()
    if (rect.width <= 160 && rect.height <= 80) score += 20
    return score
  }

  function findUploadSectionFromLabel(labelEl, label, normalizeText) {
    const structured = findStructuredUploadContent(labelEl, label, normalizeText)
    if (structured) return structured

    const uploadPattern = label === '文章头图'
      ? /点击上传图片|上传图片|AI配图|编辑头图|重新上传|更换图片|已上传/
      : /点击上传封面图|上传封面图|选择封面|编辑封面|重新上传|更换图片|已上传/
    let current = labelEl
    const candidates = []
    for (let depth = 0; current && depth < 8; depth += 1) {
      const text = normalizeText(current.textContent || '')
      if (text.includes(label) && uploadPattern.test(text) && !looksLikeWholeArticleForm(text)) {
        candidates.push(current)
      }
      current = current.parentElement
    }
    return candidates
      .sort((left, right) => sectionScore(right, label, uploadPattern, normalizeText) - sectionScore(left, label, uploadPattern, normalizeText))[0] || null
  }

  function findStructuredUploadContent(labelEl, label, normalizeText) {
    let row = labelEl
    for (let depth = 0; row && depth < 6; depth += 1) {
      const text = normalizeText(row.textContent || '')
      if (text.includes(label) && row.children?.length >= 2) {
        const content = Array.from(row.children).find((child) => {
          const childText = normalizeText(child.textContent || '')
          return child !== labelEl
            && !isSectionLabel(child, label, normalizeText)
            && isUploadContentForLabel(childText, label)
        })
        if (content) return content
      }
      row = row.parentElement
    }
    return null
  }

  function isUploadContentForLabel(text, label) {
    return label === '文章头图'
      ? /点击上传图片|上传图片|AI配图|编辑头图|重新上传|更换图片|已上传/.test(text)
      : /点击上传封面图|上传封面图|选择封面|编辑封面|重新上传|更换图片|已上传/.test(text)
  }

  function looksLikeWholeArticleForm(text) {
    return text.includes('文章标题') && text.includes('文章正文') && text.includes('发布设置')
  }

  function sectionScore(section, label, uploadPattern, normalizeText) {
    const text = normalizeText(section.textContent || '')
    const rect = section.getBoundingClientRect()
    let score = 0
    if (text.includes(label)) score += 100
    if (uploadPattern.test(text)) score += 180
    if (section.querySelector('input[type="file"]')) score += 30
    if (looksLikeWholeArticleForm(text)) score -= 500
    score -= Math.min(text.length, 1000) / 8
    score -= Math.min(rect.width * rect.height, 1_000_000) / 100_000
    return score
  }

  function findDialogByText(texts, deps = {}) {
    const roots = Array.from(document.querySelectorAll('[role="dialog"], .semi-modal, .semi-modal-content, .modal, .dialog'))
      .filter(isVisible)
    return roots.find((root) => texts.every((text) => bodyText({ ...deps, root }).includes(text))) || null
  }

  async function clickIfVisible(texts, deps, options = {}) {
    const waitForCondition = requireDependency(deps.waitForCondition, 'waitForCondition')
    const action = await waitForCondition(
      () => findActionInRoot(document, texts, deps),
      options.timeoutMs || 8000,
      `抖音操作按钮未找到：${texts.join('/')}；${describeState(deps)}`,
    ).catch((error) => {
      if (options.required) throw error
      return null
    })
    if (action) {
      deps.showStatus?.(`抖音点击：${normalizeText(action.textContent || '').slice(0, 40)}`, 'info')
      await click(action, options.platform || 'douyin', deps)
    }
    return action
  }

  function findActionInRoot(root, texts, deps = {}) {
    const normalizeText = deps.normalizeText || defaultNormalizeText
    const candidates = Array.from((root || document).querySelectorAll('button, a, [role="button"], [role="tab"], div, span'))
      .filter(isVisible)
      .map((el) => ({ el, text: normalizeText(el.textContent || '') }))
      .filter((item) => texts.some((text) => item.text === text || item.text.includes(text)))
    return candidates
      .sort((left, right) => actionScore(right, texts) - actionScore(left, texts))[0]?.el || null
  }

  function actionScore(item, texts) {
    const { el, text } = item
    const tag = el.tagName
    const rect = el.getBoundingClientRect()
    let score = 0
    if (texts.some((target) => text === target)) score += 1000
    else if (texts.some((target) => text.endsWith(target))) score += 120
    if (!hasVisibleChildMatchingText(el, texts)) score += 250
    if (tag === 'BUTTON') score += 50
    else if (el.getAttribute('role') === 'button') score += 40
    else if (el.getAttribute('role') === 'tab') score += 35
    else if (tag === 'A') score += 30
    else if (tag === 'DIV' || tag === 'SPAN') score += 10
    if (rect.width > 0 && rect.width <= 220 && rect.height > 0 && rect.height <= 80) score += 80
    if (/\b(active|current|selected)\b/i.test(el.className || '')) score -= 80
    score -= Math.min(text.length, 500) / 10
    return score
  }

  function hasVisibleChildMatchingText(el, texts) {
    return Array.from(el.children || []).some((child) => {
      const text = defaultNormalizeText(child.textContent || '')
      return isVisible(child) && texts.some((target) => text === target || text.includes(target))
    })
  }

  async function click(el, platform, deps = {}) {
    const clickTrustedActionOnce = requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')
    await clickTrustedActionOnce(el, { platform })
  }

  function bodyText(deps = {}) {
    const root = deps.root || document.body
    const normalizeText = deps.normalizeText || defaultNormalizeText
    return normalizeText(root?.innerText || root?.textContent || '')
  }

  function describeState(deps = {}) {
    const text = bodyText(deps).slice(0, 260)
    const inputs = Array.from(document.querySelectorAll('input[type="file"], input, textarea')).slice(0, 12).map((input, index) => ({
      index,
      type: input.type || input.tagName,
      accept: input.getAttribute('accept') || '',
      placeholder: input.getAttribute('placeholder') || '',
      value: input.value ? String(input.value).slice(0, 40) : '',
    }))
    const douyin = {
      uploadSections: collectUploadSectionDiagnostics(deps),
      manageRecords: location.href.includes('/creator-micro/content/manage')
        ? extractManageRecordCandidates(deps).slice(0, 5).map((item) => ({
          text: item.text.slice(0, 180),
          hasImage: item.hasImage,
          rect: compactRect(item.rect),
        }))
        : [],
    }
    return `url=${location.href}; text=${text || '-'}; inputs=${JSON.stringify(inputs).slice(0, 500)}; douyin=${JSON.stringify(douyin).slice(0, 1200)}`
  }

  function collectUploadSectionDiagnostics(deps = {}) {
    return ['文章头图', '封面设置'].map((label) => {
      const section = findSection(label, deps)
      const text = section ? bodyText({ ...deps, root: section }).slice(0, 120) : ''
      return {
        label,
        found: Boolean(section),
        hasResult: hasSectionImage(label, deps),
        hasBackgroundImage: section ? hasBackgroundImage(section) : false,
        imageCount: section ? Array.from(section.querySelectorAll('img')).filter(isVisible).length : 0,
        rect: section ? compactRect(section.getBoundingClientRect()) : null,
        text,
      }
    })
  }

  function compactRect(rect) {
    if (!rect) return null
    return {
      x: Math.round(rect.left),
      y: Math.round(rect.top),
      w: Math.round(rect.width),
      h: Math.round(rect.height),
    }
  }

  function requireDependency(fn, name) {
    if (typeof fn !== 'function') {
      throw new Error(`DOUYIN_ADAPTER_DEPENDENCY_MISSING：${name}`)
    }
    return fn
  }

  function isVisible(el) {
    if (!el || !el.getBoundingClientRect) return false
    const rect = el.getBoundingClientRect()
    const style = getComputedStyle(el)
    return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none'
  }

  function defaultNormalizeText(value) {
    return String(value || '').replace(/\s+/g, ' ').trim()
  }

  global.__GEO_DOUYIN_PLATFORM__ = {
    PUBLISH_URL,
    MANAGE_URL,
    classifyFailureCode,
    isRetryableFailureCode,
    createPublishOptionsAdapter,
    editorSelectors,
    maybeSelectArticleEditor,
  }
})(globalThis)
