;(function installBaijiahaoPlatform(global) {
  const PUBLISH_URL = 'https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1'
  const WORKS_LIST_URL = 'https://baijiahao.baidu.com/builder/rc/content?type=news'

  const RETRYABLE_FAILURE_CODES = new Set([
    'BAIJIAHAO_COVER_UPLOAD_TIMEOUT',
    'BAIJIAHAO_SCHEDULE_DIALOG_NOT_READY',
    'BAIJIAHAO_PUBLISH_NOT_CONFIRMED',
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
    if (normalizePlatform(platform) !== 'baijiahao' && !text.includes('百家号')) return ''
    if (text.includes('封面上传入口')) return 'BAIJIAHAO_COVER_UPLOAD_ENTRY_NOT_FOUND'
    if (text.includes('封面本地上传')) return 'BAIJIAHAO_COVER_UPLOAD_INPUT_NOT_FOUND'
    if (text.includes('封面上传完成')) return 'BAIJIAHAO_COVER_UPLOAD_TIMEOUT'
    if (text.includes('封面确认按钮')) return 'BAIJIAHAO_COVER_CONFIRM_NOT_FOUND'
    if (text.includes('定时时间过近')) return 'BAIJIAHAO_SCHEDULE_TIME_TOO_SOON'
    if (text.includes('定时时间过远')) return 'BAIJIAHAO_SCHEDULE_TIME_TOO_LATE'
    if (text.includes('定时时间无效')) return 'BAIJIAHAO_SCHEDULE_TIME_INVALID'
    if (text.includes('定时发布按钮未找到')) return 'BAIJIAHAO_SCHEDULE_BUTTON_NOT_FOUND'
    if (text.includes('定时发布弹窗')) return 'BAIJIAHAO_SCHEDULE_DIALOG_NOT_READY'
    if (text.includes('定时发布下拉选项')) return 'BAIJIAHAO_SCHEDULE_OPTION_NOT_FOUND'
    if (text.includes('发布后未检测到成功状态')) return 'BAIJIAHAO_PUBLISH_NOT_CONFIRMED'
    if (text.includes('账号一致性校验失败')) return 'ACCOUNT_MISMATCH'
    return 'BAIJIAHAO_FILL_FAILED'
  }

  function isRetryableFailureCode(code) {
    return RETRYABLE_FAILURE_CODES.has(String(code || '').trim())
  }

  function createPublishOptionsAdapter(deps = {}) {
    return {
      platform: 'baijiahao',
      fillPublishOptions: (payload, fillProfile) => fillPublishOptions(payload, fillProfile, deps),
      describeState: () => describeBaijiahaoState(deps),
    }
  }

  async function fillPublishOptions(payload, fillProfile, deps) {
    const options = resolvePublishOptions(payload)
    const actions = []
    await dismissBlockingGuides(deps, fillProfile.platform)
    if (!options.coverImageUrl) {
      throw new Error('BAIJIAHAO_COVER_REQUIRED：百家号当前契约要求文章封面，请先为文章配置封面')
    }
    const cover = await fillCover(options.coverImageUrl, fillProfile.platform, deps)
    if (cover.filled) actions.push(cover.message)
    if (options.scheduledAt) {
      const schedule = await fillScheduledPublish(options.scheduledAt, fillProfile.platform, {
        title: payload.title || payload.articleTitle || '',
      }, deps)
      if (schedule.filled) actions.push(schedule.message)
      return {
        filled: true,
        scheduled: true,
        publishVerification: schedule.publishVerification,
        message: actions.join('，'),
      }
    }
    return {
      filled: true,
      scheduled: false,
      message: actions.join('，'),
    }
  }

  function resolvePublishOptions(payload = {}) {
    const profileOptions = payload.profile?.platformOptions || {}
    const platformOptions = payload.platformOptions || {}
    const options = payload.baijiahaoOptions || platformOptions.baijiahao || profileOptions.baijiahao || {}
    return {
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

  async function fillCover(coverImageUrl, platform, deps) {
    await dismissBlockingGuides(deps, platform)
    if (hasCoverSelected(deps)) return { filled: false, message: '百家号封面已存在' }
    const entry = await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => findCoverEntry(deps),
      8000,
      `百家号封面上传入口未找到；${describeCoverState(deps)}`,
    )
    await requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')(entry, { platform })
    await requireDependency(deps.delay, 'delay')(600)

    const localUpload = await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => findLocalUploadEntry(deps) || findCoverFileInput(),
      8000,
      `百家号封面本地上传入口未找到；${describeCoverState(deps)}`,
    )
    if (!isFileInput(localUpload)) {
      await requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')(localUpload, { platform })
      await requireDependency(deps.delay, 'delay')(500)
    }
    await requireDependency(deps.uploadCoverImageFromLocalHelper, 'uploadCoverImageFromLocalHelper')(coverImageUrl, platform, '百家号')
    await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => hasCoverPickerImageUploaded(deps) || hasCoverSelected(deps),
      20000,
      `百家号封面上传完成超时；${describeCoverState(deps)}`,
    )
    const confirm = findCoverConfirmButton(deps)
    if (confirm) {
      await requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')(confirm, { platform })
      await requireDependency(deps.waitForCondition, 'waitForCondition')(
        () => hasCoverSelected(deps),
        12000,
        `百家号封面确认后未回填；${describeCoverState(deps)}`,
      )
    } else if (!hasCoverSelected(deps)) {
      throw new Error(`百家号封面确认按钮未找到；${describeCoverState(deps)}`)
    }
    return { filled: true, message: '已上传百家号封面' }
  }

  async function fillScheduledPublish(scheduledAt, platform, context = {}, deps) {
    await dismissBlockingGuides(deps, platform)
    const value = normalizeScheduleDateTime(scheduledAt)
    if (!value.full) throw new Error(`百家号定时时间无效：${scheduledAt}`)
    assertScheduleRange(value)
    window.scrollTo(0, document.body.scrollHeight)
    await requireDependency(deps.delay, 'delay')(500)
    const button = await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => findBottomActionButton('定时发布', deps),
      8000,
      `百家号定时发布按钮未找到；${describeBaijiahaoState(deps)}`,
    )
    await requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')(button, { platform })
    const dialog = await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => findScheduleDialog(deps),
      8000,
      `百家号定时发布弹窗未就绪；target=${value.full}；${describeBaijiahaoState(deps)}`,
    )
    await fillScheduleDropdowns(dialog, value, platform, deps)
    const confirm = await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => findScheduleConfirmButton(deps),
      8000,
      `百家号定时发布弹窗确认按钮未找到；target=${value.full}；${describeBaijiahaoState(deps)}`,
    )
    await requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')(confirm, { platform })
    const verification = await waitForPublishSubmitted(value, context, deps)
    return {
      filled: true,
      scheduled: true,
      publishVerification: verification,
      message: `已设置百家号定时发布=${value.full}`,
    }
  }

  async function fillScheduleDropdowns(dialog, value, platform, deps) {
    const targets = [value.monthDay, `${Number(value.hour)}点`, `${Number(value.minute)}分`]
    for (let index = 0; index < targets.length; index += 1) {
      const controls = collectScheduleControls(dialog, deps)
      const control = controls[index]
      if (!control) {
        throw new Error(`百家号定时发布弹窗下拉控件未找到：${targets[index]}；${describeScheduleDialog(deps)}`)
      }
      const current = normalizeText(deps, control.textContent || control.getAttribute?.('aria-label') || '')
      if (sameScheduleToken(current, targets[index])) continue
      await requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')(control, { platform })
      await requireDependency(deps.delay, 'delay')(300)
      const option = await requireDependency(deps.waitForCondition, 'waitForCondition')(
        () => findScheduleOption(targets[index], deps),
        5000,
        `百家号定时发布下拉选项未找到：${targets[index]}；${describeScheduleDialog(deps)}`,
      )
      await requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')(option, { platform })
      await requireDependency(deps.delay, 'delay')(400)
    }
  }

  async function waitForPublishSubmitted(value, context, deps) {
    const deadline = Date.now() + 30000
    let latest = null
    while (Date.now() < deadline) {
      latest = verifySubmitted(value, context, deps)
      if (latest.verified) return latest
      await requireDependency(deps.delay, 'delay')(700)
    }
    throw new Error(`百家号发布后未检测到成功状态；target=${value.full}；${describeBaijiahaoState(deps)}`)
  }

  function verifySubmitted(value, context, deps) {
    const text = document.body?.innerText || document.body?.textContent || ''
    const normalizedText = normalizeText(deps, text)
    const success = /发布成功|定时发布成功|提交成功|审核中/.test(text)
      || /\/content|\/manage|\/success/.test(location.href)
      || (!findScheduleDialog(deps) && !location.pathname.includes('/edit'))
    return {
      verified: success,
      platformStatus: 'scheduled',
      pageUrl: location.href,
      pageTitle: document.title || '',
      expectedTitle: context.title || '',
      scheduledAtText: value.full,
      publishUi: {
        scheduleDialogVisible: Boolean(findScheduleDialog(deps)),
        bottomButtons: describeBottomButtons(deps),
      },
      successSignal: {
        successText: /发布成功|定时发布成功|提交成功|审核中/.test(text),
        leaveEditor: !location.pathname.includes('/edit'),
      },
      textSample: normalizedText.slice(0, 500),
    }
  }

  function normalizeScheduleDateTime(value) {
    const text = String(value || '').trim().replace('T', ' ')
    const match = text.match(/(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{1,2})/)
    if (!match) return { full: '', monthDay: '', hour: '', minute: '', timestamp: Number.NaN }
    const yyyy = match[1]
    const mm = match[2].padStart(2, '0')
    const dd = match[3].padStart(2, '0')
    const hh = match[4].padStart(2, '0')
    const min = match[5].padStart(2, '0')
    return {
      full: `${yyyy}-${mm}-${dd} ${hh}:${min}`,
      monthDay: `${Number(mm)}月${dd}日`,
      hour: hh,
      minute: min,
      timestamp: new Date(`${yyyy}-${mm}-${dd}T${hh}:${min}:00`).getTime(),
    }
  }

  function assertScheduleRange(value) {
    const timestamp = Number(value.timestamp)
    if (!Number.isFinite(timestamp)) throw new Error(`百家号定时时间无效：${value.full}`)
    const diff = timestamp - Date.now()
    if (diff < 60 * 60 * 1000) {
      throw new Error(`百家号定时时间过近：${value.full}，平台要求至少 1 小时后发布`)
    }
    if (diff > 7 * 24 * 60 * 60 * 1000) {
      throw new Error(`百家号定时时间过远：${value.full}，平台最多支持 7 天内定时发布`)
    }
  }

  function findCoverEntry(deps) {
    return findText(deps, '选择封面', { exact: false, maxLength: 20 })
      || findText(deps, '设置封面', { exact: false, maxLength: 20 })
  }

  async function dismissBlockingGuides(deps, platform) {
    for (let attempt = 0; attempt < 5; attempt += 1) {
      const guide = findBlockingGuide(deps)
      if (!guide) return
      const action = findGuideCloseAction(guide, deps) || findGuideNextAction(guide, deps)
      if (!action) return
      await requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')(action, { platform })
      await requireDependency(deps.delay, 'delay')(300)
    }
  }

  function findBlockingGuide(deps) {
    const markers = ['AI工具收起', '点击展开可立即体验', '下一步']
    for (const marker of markers) {
      const el = findText(deps, marker, { exact: false, maxLength: 80 })
      if (!el) continue
      const container = nearestLargeContainer(deps, el)
      const text = normalizeText(deps, container?.textContent || '')
      if (text.includes('AI工具收起') || text.includes('点击展开可立即体验') || /1\/4|2\/4|3\/4|4\/4/.test(text)) {
        return container
      }
    }
    return null
  }

  function findGuideCloseAction(guide, deps) {
    return collectActions(deps, guide)
      .filter((item) => /^(×|x|关闭|跳过|知道了|我知道了)$/i.test(item.text))
      .sort((left, right) => right.rect.top - left.rect.top || right.rect.left - left.rect.left)[0]?.el || null
  }

  function findGuideNextAction(guide, deps) {
    return collectActions(deps, guide)
      .filter((item) => item.text === '下一步' || item.text === '完成')
      .sort((left, right) => right.rect.top - left.rect.top || right.rect.left - left.rect.left)[0]?.el || null
  }

  function findLocalUploadEntry(deps) {
    return findText(deps, '点击本地上传', { exact: false, maxLength: 24 })
      || findText(deps, '本地上传', { exact: false, maxLength: 16 })
  }

  function findCoverFileInput() {
    const inputs = Array.from(document.querySelectorAll('input[type="file"]'))
    return inputs
      .filter((input) => {
        const descriptor = [
          input.getAttribute('accept') || '',
          input.id || '',
          input.name || '',
          String(input.className || ''),
          nearestText(input),
        ].join(' ').toLowerCase()
        return /image|jpg|jpeg|png|webp|upload|cover|file|封面|图片/.test(descriptor)
      })
      .pop() || inputs.at(-1) || null
  }

  function isFileInput(el) {
    return el?.tagName?.toLowerCase() === 'input' && String(el.getAttribute?.('type') || '').toLowerCase() === 'file'
  }

  function nearestText(el) {
    let current = el?.parentElement || null
    for (let depth = 0; current && depth < 5; depth += 1) {
      const text = String(current.textContent || '').replace(/\s+/g, '')
      if (text) return text.slice(0, 120)
      current = current.parentElement
    }
    return ''
  }

  function findCoverConfirmButton(deps) {
    return collectActions(deps)
      .filter((item) => /^确定(?:\(\d+\))?$/.test(item.text))
      .sort((left, right) => right.rect.top - left.rect.top || right.rect.left - left.rect.left)[0]?.el || null
  }

  function hasCoverPickerImageUploaded(deps) {
    const text = normalizeText(deps, document.body?.innerText || '')
    if (!/正文\/本地上传\(\d+\)|确定\(\d+\)/.test(text)) return false
    return Array.from(document.querySelectorAll('img')).some((img) => isVisible(deps, img))
  }

  function hasCoverSelected(deps) {
    const label = findText(deps, '设置封面', { exact: false, maxLength: 12 })
    const scope = label ? nearestLargeContainer(deps, label) : document.body
    const text = normalizeText(deps, scope?.textContent || '')
    if (text.includes('选择封面') && !Array.from((scope || document).querySelectorAll('img')).some((img) => isVisible(deps, img))) {
      return false
    }
    return Array.from((scope || document).querySelectorAll('img'))
      .some((img) => isVisible(deps, img) && img.getBoundingClientRect().width >= 40 && img.getBoundingClientRect().height >= 40)
  }

  function findBottomActionButton(label, deps) {
    const target = normalizeText(deps, label)
    return collectActions(deps)
      .filter((item) => item.text === target)
      .filter((item) => item.rect.top >= window.innerHeight * 0.55)
      .sort((left, right) => right.rect.top - left.rect.top || right.rect.left - left.rect.left)[0]?.el || null
  }

  function findScheduleDialog(deps) {
    const marker = findText(deps, '定时发文', { exact: false, maxLength: 30 })
      || findText(deps, '当前时间后1小时', { exact: false, maxLength: 80 })
    if (!marker) return null
    const container = nearestLargeContainer(deps, marker)
    if (!container) return null
    const text = normalizeText(deps, container.textContent || '')
    return text.includes('定时发文') && text.includes('定时发布') ? container : null
  }

  function findScheduleConfirmButton(deps) {
    const dialog = findScheduleDialog(deps)
    const root = dialog || document
    return collectActions(deps, root)
      .filter((item) => item.text === '定时发布')
      .filter((item) => item.rect.width >= 60 && item.rect.height >= 24)
      .sort((left, right) => right.rect.top - left.rect.top || right.rect.left - left.rect.left)[0]?.el || null
  }

  function collectScheduleControls(dialog, deps) {
    return Array.from(dialog.querySelectorAll('button, [role="button"], [role="combobox"], [aria-haspopup], div, span'))
      .filter((el) => isVisible(deps, el))
      .map((el) => {
        const clickable = el.closest?.('button, [role="button"], [role="combobox"], [aria-haspopup]') || el
        return {
          el: clickable,
          rect: clickable.getBoundingClientRect(),
          text: normalizeText(deps, clickable.textContent || clickable.getAttribute?.('aria-label') || ''),
        }
      })
      .filter((item) => extractScheduleToken(item.text))
      .filter((item) => item.rect.width >= 70 && item.rect.width <= 240 && item.rect.height >= 28 && item.rect.height <= 72)
      .filter((item, index, items) => items.findIndex((other) => other.el === item.el || sameRect(other.rect, item.rect)) === index)
      .sort((left, right) => left.rect.top - right.rect.top || left.rect.left - right.rect.left)
      .slice(0, 3)
      .map((item) => item.el)
  }

  function findScheduleOption(target, deps) {
    const expected = normalizeScheduleToken(target)
    return Array.from(document.querySelectorAll('button, [role="option"], [role="menuitem"], li, div, span'))
      .filter((el) => isVisible(deps, el))
      .map((el) => ({
        el,
        text: normalizeText(deps, el.textContent || el.getAttribute?.('aria-label') || ''),
        rect: el.getBoundingClientRect(),
      }))
      .filter((item) => normalizeScheduleToken(item.text) === expected)
      .filter((item) => item.rect.width >= 40 && item.rect.width <= 260 && item.rect.height >= 20 && item.rect.height <= 80)
      .sort((left, right) => left.text.length - right.text.length || right.rect.top - left.rect.top)[0]?.el || null
  }

  function sameScheduleToken(left, right) {
    return normalizeScheduleToken(left) === normalizeScheduleToken(right)
  }

  function normalizeScheduleToken(value) {
    const token = extractScheduleToken(value)
    if (!token) return ''
    const number = token.match(/^(\d{1,2})(?:点|分)$/)
    if (number) return `${Number(number[1])}${token.endsWith('点') ? '点' : '分'}`
    const monthDay = token.match(/^(\d{1,2})月(\d{1,2})日$/)
    if (monthDay) return `${Number(monthDay[1])}月${String(Number(monthDay[2])).padStart(2, '0')}日`
    return token
  }

  function extractScheduleToken(value) {
    const text = String(value || '').replace(/\s+/g, '')
    const monthDay = text.match(/\d{1,2}月\d{1,2}日/)
    if (monthDay) return monthDay[0]
    const hour = text.match(/\d{1,2}点/)
    if (hour) return hour[0]
    const minute = text.match(/\d{1,2}分/)
    if (minute) return minute[0]
    return ''
  }

  function editorSelectors() {
    return {
      title: [
        '[placeholder*="请输入标题"]',
        '[aria-label*="请输入标题"]',
        'textarea[placeholder*="标题"]',
        'input[placeholder*="标题"]',
        '[class*="title"] [contenteditable="true"]',
        '[class*="Title"] [contenteditable="true"]',
        '[class*="editor-title"] [contenteditable="true"]',
        '[contenteditable="true"][data-placeholder*="标题"]',
        '[contenteditable="true"][placeholder*="标题"]',
      ],
      content: [
        '[placeholder*="请输入正文"]',
        '[aria-label*="请输入正文"]',
        '[data-placeholder*="请输入正文"]',
        '.ProseMirror',
        '.DraftEditor-editorContainer [contenteditable="true"]',
        '.public-DraftEditor-content',
        '[contenteditable="true"]',
      ],
      tags: [
        'input[placeholder*="标签"]',
        'textarea[placeholder*="标签"]',
      ],
    }
  }

  function findText(deps, text, options) {
    return requireDependency(deps.findVisibleTextElement, 'findVisibleTextElement')(text, options)
  }

  function nearestLargeContainer(deps, el) {
    return requireDependency(deps.nearestLargeContainer, 'nearestLargeContainer')(el)
  }

  function collectActions(deps, root = document) {
    return requireDependency(deps.collectVisibleActionElements, 'collectVisibleActionElements')(root)
  }

  function isVisible(deps, el) {
    return requireDependency(deps.isVisibleElement, 'isVisibleElement')(el)
  }

  function normalizeText(deps, value) {
    const fn = deps.normalizeText
    return typeof fn === 'function' ? fn(value) : String(value || '').replace(/\s+/g, '')
  }

  function describeCoverState(deps) {
    const text = normalizeText(deps, document.body?.innerText || '').slice(0, 500)
    const fileInputs = Array.from(document.querySelectorAll('input[type="file"]')).length
    return `href=${location.href}; fileInputs=${fileInputs}; text=${text || '-'}`
  }

  function describeScheduleDialog(deps) {
    const dialog = findScheduleDialog(deps)
    const text = normalizeText(deps, dialog?.textContent || document.body?.innerText || '').slice(0, 500)
    return `href=${location.href}; dialog=${Boolean(dialog)}; text=${text || '-'}`
  }

  function describeBottomButtons(deps) {
    return collectActions(deps)
      .filter((item) => item.rect.top >= window.innerHeight * 0.55)
      .map((item) => `${item.text}@${Math.round(item.rect.left)},${Math.round(item.rect.top)},${Math.round(item.rect.width)}x${Math.round(item.rect.height)}`)
      .slice(0, 12)
      .join('|')
  }

  function describeBaijiahaoState(deps) {
    return `${describeCoverState(deps)}; ${describeScheduleDialog(deps)}; bottom=${describeBottomButtons(deps) || '-'}`
  }

  function sameRect(left, right) {
    return Math.abs(left.left - right.left) <= 3
      && Math.abs(left.top - right.top) <= 3
      && Math.abs(left.width - right.width) <= 4
      && Math.abs(left.height - right.height) <= 4
  }

  function firstText(...values) {
    for (const value of values) {
      if (typeof value === 'string' && value.trim()) return value.trim()
    }
    return ''
  }

  function requireDependency(fn, name) {
    if (typeof fn !== 'function') throw new Error(`BAIJIAHAO_ADAPTER_DEPENDENCY_MISSING：${name}`)
    return fn
  }

  global.__GEO_BAIJIAHAO_PLATFORM__ = {
    PUBLISH_URL,
    WORKS_LIST_URL,
    classifyFailureCode,
    isRetryableFailureCode,
    createPublishOptionsAdapter,
    editorSelectors,
    resolvePublishOptions,
  }
})(globalThis)
