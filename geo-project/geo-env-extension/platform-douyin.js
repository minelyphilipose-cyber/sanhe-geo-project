;(function installDouyinPlatform(global) {
  const PUBLISH_URL = 'https://creator.douyin.com/creator-micro/content/upload'
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

    if (location.hostname !== 'creator.douyin.com' || !location.pathname.includes('/creator-micro/content/upload')) {
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
    if (options.headImageUrl) {
      const headImage = await uploadImageInSection('文章头图', ['点击上传图片', '上传图片'], options.headImageUrl, fillProfile.platform, deps, {
        optional: true,
        success: () => hasSectionImage('文章头图', deps),
      })
      if (headImage.filled) actions.push(headImage.message)
    }
    if (!options.coverImageUrl) {
      throw new Error('DOUYIN_COVER_REQUIRED：抖音文章必须配置封面图片')
    }
    const cover = await uploadImageInSection('封面设置', ['点击上传封面图', '上传封面图'], options.coverImageUrl, fillProfile.platform, deps, {
      optional: false,
      success: () => hasSectionImage('封面设置', deps),
      afterUpload: () => confirmCoverEditor(deps, fillProfile.platform),
    })
    if (cover.filled) actions.push(cover.message)

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

    const entry = findActionInRoot(section, uploadTexts, deps) || findActionInRoot(document, uploadTexts, deps)
    if (!entry) {
      if (options.optional) return { filled: false, message: `抖音${sectionLabel}未填充：未找到上传入口` }
      throw new Error(`抖音${sectionLabel}上传入口未找到；${describeState(deps)}`)
    }
    await click(entry, platform, deps)
    await delay(500)
    await uploadCoverImageFromLocalHelper(imageUrl, platform, `抖音${sectionLabel}`)
    if (options.afterUpload) await options.afterUpload()
    await waitForCondition(
      () => options.success?.(),
      25000,
      `抖音${sectionLabel}上传完成超时；${describeState(deps)}`,
    )
    return { filled: true, message: `已上传抖音${sectionLabel}` }
  }

  async function confirmCoverEditor(deps, platform) {
    const waitForCondition = requireDependency(deps.waitForCondition, 'waitForCondition')
    const dialog = await waitForCondition(
      () => findDialogByText(['编辑封面', '替换封面', '完成'], deps),
      12000,
      `抖音封面编辑弹窗未出现；${describeState(deps)}`,
    )
    const done = findActionInRoot(dialog, ['完成'], deps)
    if (!done) throw new Error(`抖音封面编辑完成按钮未找到；${describeState(deps)}`)
    await click(done, platform, deps)
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

  async function submitPublish(platform, payload, deps, scheduledAt, platformStatus) {
    const waitForCondition = requireDependency(deps.waitForCondition, 'waitForCondition')
    await clickIfVisible(['发布'], deps, { timeoutMs: 12000, required: true, platform })
    const verification = await waitForCondition(
      () => verifyManagePageAfterPublish(payload, scheduledAt, platformStatus, deps),
      45000,
      `抖音发布后未检测到成功状态；${describeState(deps)}`,
    )
    return verification
  }

  function verifyManagePageAfterPublish(payload, scheduledAt, platformStatus, deps) {
    const title = firstText(payload.title, payload.articleTitle).slice(0, 30)
    if (!location.href.includes('/creator-micro/content/manage')) return null
    const reloadKey = `geoDouyinManageReloaded:${title || 'unknown'}:${scheduledAt || platformStatus || 'now'}`
    if (sessionStorage.getItem(reloadKey) !== '1') {
      sessionStorage.setItem(reloadKey, '1')
      setTimeout(() => location.reload(), 500)
      return null
    }
    const text = bodyText(deps)
    if (title && !text.includes(title)) return null
    return {
      verified: true,
      platformStatus,
      platformScheduledAt: scheduledAt || null,
      scheduledAtText: scheduledAt || null,
      title,
      manageUrl: location.href,
      refreshed: true,
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
      .filter((el) => normalizeText(el.textContent || '').includes(label))
    for (const labelEl of labels) {
      const root = labelEl.closest?.('section, form, .semi-design-form-field, .form-item, .semi-row, .semi-col, div') || labelEl.parentElement
      if (root && normalizeText(root.textContent || '').includes(label)) return root
    }
    return null
  }

  function hasSectionImage(label, deps = {}) {
    const section = findSection(label, deps)
    if (!section) return false
    if (section.querySelector('img')) return true
    return /编辑封面|重新上传|更换图片|已上传/.test(bodyText({ ...deps, root: section }))
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
    if (action) await click(action, options.platform || 'douyin', deps)
    return action
  }

  function findActionInRoot(root, texts, deps = {}) {
    const normalizeText = deps.normalizeText || defaultNormalizeText
    const candidates = Array.from((root || document).querySelectorAll('button, a, [role="button"], [role="tab"], div, span'))
      .filter(isVisible)
      .filter((el) => {
        const text = normalizeText(el.textContent || '')
        return texts.some((item) => text === item || text.includes(item))
      })
    return candidates
      .sort((left, right) => actionScore(right) - actionScore(left))[0] || null
  }

  function actionScore(el) {
    const tag = el.tagName
    if (tag === 'BUTTON') return 5
    if (el.getAttribute('role') === 'button') return 4
    if (tag === 'A') return 3
    return 1
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
    return `url=${location.href}; text=${text || '-'}; inputs=${JSON.stringify(inputs).slice(0, 500)}`
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
