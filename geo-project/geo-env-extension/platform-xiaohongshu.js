;(function installXiaohongshuPlatform(global) {
  const WORKS_LIST_URL = 'https://creator.xiaohongshu.com/new/note-manager'
  const PUBLISH_URL = 'https://creator.xiaohongshu.com/publish/publish?from=tab_switch&target=article'

  const RETRYABLE_FAILURE_CODES = new Set([
    'XIAOHONGSHU_FORMAT_NOT_READY',
    'XIAOHONGSHU_PUBLISH_SETTINGS_NOT_READY',
    'XIAOHONGSHU_IMAGE_GENERATION_TIMEOUT',
    'XIAOHONGSHU_PUBLISH_NOT_CONFIRMED',
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
    if (normalizePlatform(platform) !== 'xiaohongshu' && !text.includes('小红书')) return ''
    if (text.includes('一键排版按钮未找到')) return 'XIAOHONGSHU_FORMAT_BUTTON_NOT_FOUND'
    if (text.includes('一键排版后未进入排版页') || text.includes('排版页未就绪')) return 'XIAOHONGSHU_FORMAT_NOT_READY'
    if (text.includes('下一步按钮未找到')) return 'XIAOHONGSHU_NEXT_BUTTON_NOT_FOUND'
    if (text.includes('点击下一步后未进入发布设置页') || text.includes('发布设置页未加载完成') || text.includes('发布设置页未就绪')) {
      return 'XIAOHONGSHU_PUBLISH_SETTINGS_NOT_READY'
    }
    if (text.includes('笔记图片生成') || text.includes('图片生成')) return 'XIAOHONGSHU_IMAGE_GENERATION_TIMEOUT'
    if (text.includes('定时发布时间过近')) return 'XIAOHONGSHU_SCHEDULE_TIME_TOO_SOON'
    if (text.includes('定时发布时间过远')) return 'XIAOHONGSHU_SCHEDULE_TIME_TOO_LATE'
    if (text.includes('定时发布时间无效')) return 'XIAOHONGSHU_SCHEDULE_TIME_INVALID'
    if (text.includes('定时发布开关')) return 'XIAOHONGSHU_SCHEDULE_SWITCH_NOT_FOUND'
    if (text.includes('定时发布时间输入框')) return 'XIAOHONGSHU_SCHEDULE_TIME_INPUT_NOT_FOUND'
    if (text.includes('定时发布时间未保持目标值')) return 'XIAOHONGSHU_SCHEDULE_TIME_NOT_APPLIED'
    if (text.includes('定时发布按钮未找到') || text.includes('发布按钮未找到')) return 'XIAOHONGSHU_PUBLISH_BUTTON_NOT_FOUND'
    if (text.includes('发布后未检测到成功状态')) return 'XIAOHONGSHU_PUBLISH_NOT_CONFIRMED'
    if (text.includes('账号一致性校验失败')) return 'ACCOUNT_MISMATCH'
    return 'XIAOHONGSHU_FILL_FAILED'
  }

  function isRetryableFailureCode(code) {
    return RETRYABLE_FAILURE_CODES.has(String(code || '').trim())
  }

  function createPublishOptionsAdapter(deps = {}) {
    const adapterDeps = {
      applyOneClickFormat: applyXiaohongshuOneClickFormat,
      advanceToPublishSettings: advanceXiaohongshuToPublishSettings,
      fillScheduledPublish: fillXiaohongshuScheduledPublish,
      publishNow: publishXiaohongshuNow,
      describeState: describeXiaohongshuPublishState,
      ...deps,
    }
    return {
      platform: 'xiaohongshu',
      fillPublishOptions: (payload, fillProfile) => fillPublishOptions(payload, fillProfile, adapterDeps),
      applyOneClickFormat: (platform) => requireDependency(adapterDeps.applyOneClickFormat, 'applyOneClickFormat')(platform),
      advanceToPublishSettings: (platform) => requireDependency(adapterDeps.advanceToPublishSettings, 'advanceToPublishSettings')(platform),
      fillScheduledPublish: (scheduledAt, platform, context) => requireDependency(adapterDeps.fillScheduledPublish, 'fillScheduledPublish')(scheduledAt, platform, context),
      publishNow: (platform, context) => requireDependency(adapterDeps.publishNow, 'publishNow')(platform, context),
      describeState: () => requireDependency(adapterDeps.describeState, 'describeState')(),
    }
  }

  async function fillPublishOptions(payload, fillProfile, deps) {
    const options = resolvePublishOptions(payload)
    const actions = []
    if (options.oneClickFormat) {
      const format = await requireDependency(deps.applyOneClickFormat, 'applyOneClickFormat')(fillProfile.platform)
      if (format.filled) actions.push(format.message)
    }
    const next = await requireDependency(deps.advanceToPublishSettings, 'advanceToPublishSettings')(fillProfile.platform)
    if (next.filled) actions.push(next.message)
    if (options.locationName) {
      actions.push(`小红书地点未处理=${options.locationName}`)
    }
    if (payload.scheduleRequired && !options.scheduledAt) {
      throw new Error(`XIAOHONGSHU_SCHEDULE_TIME_MISSING：小红书排期任务缺少定时时间，禁止降级为立即发布；${describeXiaohongshuPublishState()}`)
    }
    if (options.scheduledAt) {
      const schedule = await requireDependency(deps.fillScheduledPublish, 'fillScheduledPublish')(options.scheduledAt, fillProfile.platform, {
        title: payload.title || payload.articleTitle || '',
        locationName: options.locationName,
      })
      if (schedule.filled) actions.push(schedule.message)
      return {
        filled: true,
        scheduled: true,
        publishVerification: schedule.publishVerification,
        message: actions.join('，'),
      }
    }
    const publish = await requireDependency(deps.publishNow, 'publishNow')(fillProfile.platform, {
      title: payload.title || payload.articleTitle || '',
      locationName: options.locationName,
    })
    if (publish.message) actions.push(publish.message)
    return {
      filled: true,
      published: Boolean(publish.published),
      publishVerification: publish.publishVerification,
      message: actions.join('，'),
    }
  }

  function resolvePublishOptions(payload = {}) {
    const profileOptions = payload.profile?.platformOptions || {}
    const platformOptions = payload.platformOptions || {}
    const xhsOptions = payload.xiaohongshuOptions || platformOptions.xiaohongshu || profileOptions.xiaohongshu || {}
    const oneClickValue = firstText(
      payload.oneClickFormat,
      platformOptions.oneClickFormat,
      profileOptions.oneClickFormat,
      xhsOptions.oneClickFormat,
    )
    return {
      oneClickFormat: oneClickValue === '' ? true : !['false', '0', 'no', 'off'].includes(String(oneClickValue).trim().toLowerCase()),
      locationName: firstText(
        payload.locationName,
        payload.location,
        platformOptions.locationName,
        platformOptions.location,
        profileOptions.locationName,
        profileOptions.location,
        xhsOptions.locationName,
        xhsOptions.location,
      ),
      scheduledAt: firstText(
        payload.scheduledAt,
        payload.platformScheduledAt,
        platformOptions.scheduledAt,
        platformOptions.platformScheduledAt,
        profileOptions.scheduledAt,
        profileOptions.platformScheduledAt,
        xhsOptions.scheduledAt,
        xhsOptions.platformScheduledAt,
      ),
    }
  }

  function firstText(...values) {
    for (const value of values) {
      if (typeof value === 'string' && value.trim()) return value.trim()
    }
    return ''
  }

  function requireDependency(fn, name) {
    if (typeof fn !== 'function') {
      throw new Error(`XIAOHONGSHU_ADAPTER_DEPENDENCY_MISSING：${name}`)
    }
    return fn
  }

  async function applyXiaohongshuOneClickFormat(platform) {
    if (isXiaohongshuLayoutCanvasVisible()) {
      return { filled: false, message: '小红书已处于排版页' }
    }
    const button = await waitForCondition(
      () => findXiaohongshuActionButton(['一键排版']),
      10000,
      `小红书一键排版按钮未找到；${describeXiaohongshuPublishState()}`,
    )
    await clickTrustedActionOnce(button, { platform })
    await waitForCondition(
      () => isXiaohongshuLayoutCanvasVisible() || findXiaohongshuActionButton(['下一步']),
      20000,
      `小红书一键排版后未进入排版页；${describeXiaohongshuPublishState()}`,
    )
    return { filled: true, message: '已完成小红书一键排版' }
  }
  
  async function advanceXiaohongshuToPublishSettings(platform) {
    if (isXiaohongshuPublishSettingsVisible()) {
      await waitForXiaohongshuPublishSettingsReady()
      return { filled: false, message: '已在小红书发布设置页' }
    }
    const attempts = []
    for (let attempt = 1; attempt <= 3; attempt += 1) {
      const next = await waitForCondition(
        () => findXiaohongshuActionButton(['下一步']) || findXiaohongshuBottomTextButton('下一步'),
        15000,
        `小红书下一步按钮未找到；attempt=${attempt}；${describeXiaohongshuPublishState()}`,
      )
      attempts.push(describeXiaohongshuButton(next, `attempt=${attempt}`))
      await clickXiaohongshuNextButton(next, platform)
      const entered = await waitForOptionalCondition(
        () => isXiaohongshuPublishSettingsVisible(),
        attempt === 1 ? 15000 : 12000,
      )
      if (entered) {
        await waitForXiaohongshuPublishSettingsReady()
        return { filled: true, message: attempt > 1 ? `已进入小红书发布设置页，下一步重试=${attempt}` : '已进入小红书发布设置页' }
      }
      await delay(700)
    }
    throw new Error(`小红书点击下一步后未进入发布设置页；nextAttempts=${attempts.join('|') || '-'}；${describeXiaohongshuPublishState()}`)
  }

  async function clickXiaohongshuNextButton(button, platform) {
    if (!button?.getBoundingClientRect) {
      throw new Error(`小红书下一步按钮无效；${describeXiaohongshuPublishState()}`)
    }
    button.scrollIntoView?.({ block: 'center', inline: 'center' })
    await delay(180)
    if (typeof firePointerClick === 'function') {
      firePointerClick(button, { platform })
      button.click?.()
    }
    await delay(120)
    if (typeof requestTrustedClick === 'function') {
      await requestTrustedClick(button, { platform, label: '下一步' })
      return
    }
    await clickTrustedActionOnce(button, { platform, label: '下一步' })
  }

  async function waitForOptionalCondition(predicate, timeoutMs) {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      if (predicate()) return true
      await delay(400)
    }
    return Boolean(predicate())
  }

  function describeXiaohongshuButton(button, prefix = '') {
    if (!button?.getBoundingClientRect) return `${prefix}:missing`
    const rect = button.getBoundingClientRect()
    const text = normalizeText(button.textContent || button.getAttribute?.('aria-label') || button.getAttribute?.('title') || '')
    const className = String(button.className || '').slice(0, 80)
    return `${prefix}:${text || '-'}@${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)},class=${className || '-'}`
  }
  
  async function waitForXiaohongshuPublishSettingsReady() {
    await waitForCondition(
      () => isXiaohongshuPublishSettingsVisible() && isXiaohongshuGeneratedImageReady(),
      60000,
      `小红书发布设置页未加载完成；${describeXiaohongshuImageGenerationState()}；${describeXiaohongshuPublishState()}`,
    )
  }
  
  async function fillXiaohongshuScheduledPublish(scheduledAt, platform, context = {}) {
    const value = normalizeXiaohongshuScheduleDateTime(scheduledAt)
    if (!value.full) throw new Error(`小红书定时发布时间无效：${scheduledAt}`)
    assertXiaohongshuScheduleRange(value)
    await ensureXiaohongshuScheduleEnabled(platform)
    await fillXiaohongshuScheduleDateTime(value, platform)
    await closeXiaohongshuSchedulePicker(platform)
    if (!isXiaohongshuPublishSettingsVisible()) {
      throw new Error(`小红书关闭定时时间选择器后离开发布设置页；target=${value.full}；${describeXiaohongshuPublishState()}`)
    }
    const button = await waitForCondition(
      () => findXiaohongshuBottomPublishClickPoint('定时发布') || findXiaohongshuBottomPublishButton('定时发布'),
      15000,
      `小红书定时发布按钮未找到；target=${value.full}；${describeXiaohongshuPublishState()}`,
    )
    await clickXiaohongshuPublishTarget(button, platform)
    const verification = await waitForXiaohongshuPublishSuccess({
      ...context,
      scheduledAt: value.full,
      platformStatus: 'scheduled',
    })
    return {
      filled: true,
      scheduled: true,
      publishVerification: verification,
      message: `已设置小红书定时发布=${value.full}`,
    }
  }
  
  async function clickXiaohongshuPublishTarget(target, platform) {
    if (target?.getBoundingClientRect) {
      await clickTrustedActionOnce(target, { platform })
      return
    }
    if (Number.isFinite(target?.clientX) && Number.isFinite(target?.clientY)) {
      const el = document.elementFromPoint(target.clientX, target.clientY) || document.body
      firePointerClick(el, { absoluteClientX: target.clientX, absoluteClientY: target.clientY })
      el.click?.()
      if (requiresTrustedClick(platform)) {
        await requestTrustedClickAt(target, platform, target.label || '小红书定时发布按钮')
      }
      if (target.source === 'xhs-publish-btn') {
        const accepted = await waitForXiaohongshuPublishHostAccepted(target.label)
        if (!accepted) {
          const retryPoint = findXiaohongshuPublishHostClickPoint(target.label)
          if (retryPoint) {
            await requestTrustedClickAt(retryPoint, platform, `${target.label || '发布'}按钮补点`)
          }
        }
      }
    }
  }

  async function waitForXiaohongshuPublishHostAccepted(label) {
    const deadline = Date.now() + 2500
    while (Date.now() < deadline) {
      const host = findXiaohongshuPublishHost(label)
      if (!host || currentXiaohongshuStage() === 'publish_success' || !isXiaohongshuPublishSettingsVisible()) return true
      if (host.getAttribute('submit-loading') === 'true' || host.getAttribute('submit-disabled') === 'true') return true
      await delay(200)
    }
    return false
  }
  
  async function publishXiaohongshuNow(platform, context = {}) {
    const button = await waitForCondition(
      () => findXiaohongshuBottomPublishClickPoint('发布') || findXiaohongshuBottomPublishButton('发布'),
      10000,
      `小红书发布按钮未找到；${describeXiaohongshuPublishState()}`,
    )
    await clickXiaohongshuPublishTarget(button, platform)
    const verification = await waitForXiaohongshuPublishSuccess({
      ...context,
      platformStatus: 'published',
    })
    return {
      published: true,
      publishVerification: verification,
      message: '已点击小红书发布',
    }
  }
  
  async function ensureXiaohongshuScheduleEnabled(platform) {
    if (!isXiaohongshuPublishSettingsVisible()) {
      throw new Error(`小红书发布设置页未就绪；${describeXiaohongshuPublishState()}`)
    }
    await waitForXiaohongshuPublishSettingsReady()
    const input = findXiaohongshuScheduleInput()
    if (input && isVisibleElement(input)) return true
    const toggle = await waitForCondition(
      () => findXiaohongshuScheduleToggle(),
      15000,
      `小红书定时发布开关未找到；${describeXiaohongshuPublishState()}`,
    )
    if (!toggle) {
      throw new Error(`小红书定时发布开关未找到；${describeXiaohongshuPublishState()}`)
    }
    await clickXiaohongshuScheduleToggle(toggle, platform)
    await waitForCondition(
      () => findXiaohongshuScheduleInput(),
      10000,
      `小红书定时发布开关打开后未出现时间输入框；${describeXiaohongshuPublishState()}`,
    )
    return true
  }

  async function clickXiaohongshuScheduleToggle(toggle, platform) {
    const target = normalizeXiaohongshuScheduleToggleTarget(toggle)
    target.scrollIntoView?.({ block: 'center', inline: 'center' })
    await delay(300)
    await clickTrustedActionOnce(target, { platform, label: '小红书定时发布开关' })
    if (await waitForOptionalCondition(() => findXiaohongshuScheduleInput(), 2500)) return true

    const clickBox = findXiaohongshuScheduleToggleBox(target) || target
    const rect = clickBox.getBoundingClientRect?.()
    if (rect && rect.width > 0 && rect.height > 0) {
      const points = [
        { clientX: Math.round(rect.left + rect.width * 0.5), clientY: Math.round(rect.top + rect.height / 2) },
        { clientX: Math.round(rect.left + rect.width * 0.78), clientY: Math.round(rect.top + rect.height / 2) },
      ]
      for (const point of points) {
        await requestTrustedClickAt(point, platform, '小红书定时发布开关坐标', rect)
        if (await waitForOptionalCondition(() => findXiaohongshuScheduleInput(), 2000)) return true
      }
    }

    const row = findXiaohongshuScheduleRow()
    const rowRect = row?.getBoundingClientRect?.()
    if (rowRect && rowRect.width > 0 && rowRect.height > 0) {
      const point = {
        clientX: Math.round(rowRect.right - Math.min(42, Math.max(24, rowRect.width * 0.08))),
        clientY: Math.round(rowRect.top + rowRect.height / 2),
      }
      await requestTrustedClickAt(point, platform, '小红书定时发布行右侧开关', rowRect)
      if (await waitForOptionalCondition(() => findXiaohongshuScheduleInput(), 2500)) return true
    }

    return false
  }
  
  async function fillXiaohongshuScheduleDateTime(value, platform) {
    const input = await waitForCondition(
      () => findXiaohongshuScheduleInput(),
      8000,
      `小红书定时发布时间输入框未找到；target=${value.full}；${describeXiaohongshuPublishState()}`,
    )
    input.scrollIntoView?.({ block: 'center', inline: 'center' })
    await clickTrustedActionOnce(input, { platform })
    await delay(300)
    setXiaohongshuDateTimeValue(input, value.full)
    input.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, key: 'Enter' }))
    input.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: 'Enter' }))
    await delay(800)
    if (isXiaohongshuScheduleValueMatched(value)) {
      await closeXiaohongshuSchedulePicker(platform)
      return true
    }
  
    await selectXiaohongshuScheduleFromPicker(value, platform)
    await delay(500)
    if (!isXiaohongshuScheduleValueMatched(value)) {
      throw new Error(`小红书定时发布时间未保持目标值：target=${value.full}；${describeXiaohongshuPublishState()}`)
    }
    await closeXiaohongshuSchedulePicker(platform)
    return true
  }
  
  async function closeXiaohongshuSchedulePicker(platform) {
    if (!findXiaohongshuSchedulePicker()) return true
    const input = findXiaohongshuScheduleInput()
    const active = document.activeElement
    if (input) {
      input.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, key: 'Enter' }))
      input.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: 'Enter' }))
    }
    active?.blur?.()
    dispatchEscapeKey(document)
    dispatchEscapeKey(window)
    dispatchEscapeKey(document.body || document)
    await delay(500)
    if (!findXiaohongshuSchedulePicker()) return true
  
    const label = findXiaohongshuScheduleLabel()
    const labelRect = label?.getBoundingClientRect?.()
    const points = []
    if (labelRect) {
      points.push({
        clientX: Math.round(labelRect.left + Math.min(220, Math.max(120, labelRect.width + 90))),
        clientY: Math.round(labelRect.top + labelRect.height / 2),
      })
    }
    for (const point of points) {
      const target = document.elementFromPoint(point.clientX, point.clientY) || document.body
      firePointerClick(target, { absoluteClientX: point.clientX, absoluteClientY: point.clientY })
      target.click?.()
      await delay(350)
      if (!findXiaohongshuSchedulePicker()) return true
    }
    return !findXiaohongshuSchedulePicker()
  }
  
  async function selectXiaohongshuScheduleFromPicker(value, platform) {
    const input = findXiaohongshuScheduleInput()
    if (input) await clickTrustedActionOnce(input, { platform })
    await delay(300)
    const picker = findXiaohongshuSchedulePicker()
    if (!picker) return false
    const day = findXiaohongshuPickerDay(value.day, picker)
    if (day) {
      await clickTrustedActionOnce(day, { platform })
      await delay(300)
    }
    const hour = findXiaohongshuPickerTimeOption(value.hour, picker, 0)
    if (hour) {
      await clickTrustedActionOnce(hour, { platform })
      await delay(200)
    }
    const minute = findXiaohongshuPickerTimeOption(value.minute, picker, 1)
    if (minute) {
      await clickTrustedActionOnce(minute, { platform })
      await delay(200)
    }
    return Boolean(day || hour || minute)
  }
  
  async function waitForXiaohongshuPublishSuccess(context = {}) {
    const deadline = Date.now() + 45000
    let latest = null
    while (Date.now() < deadline) {
      latest = verifyXiaohongshuPublishSubmitted(context)
      if (latest?.verified) return latest
      await delay(500)
    }
    throw new Error(`小红书发布后未检测到成功状态；${describeXiaohongshuPublishState()}`)
  }
  
  function verifyXiaohongshuPublishSubmitted(context = {}) {
    const text = normalizeText(document.body?.innerText || document.body?.textContent || '')
    const successUrl = location.href.includes('/publish/success')
      || (location.pathname.includes('/publish/publish') && new URLSearchParams(location.search).get('published') === 'true')
    const success = text.includes('发布成功') || text.includes('秒后将返回发布页') || successUrl
    if (!success) return null
    const identity = readXiaohongshuIdentity()
    return {
      verified: true,
      platformStatus: context.platformStatus || '',
      pageUrl: location.href,
      pageTitle: document.title || '',
      expectedTitle: context.title || '',
      scheduledAtText: context.scheduledAt || readXiaohongshuScheduleInputText(),
      account: {
        expectedAccountName: context.expectedAccountName || '',
        accountNames: identity.accountNames,
        diagnostics: identity.diagnostics,
      },
      publishUi: {
        publishSettingsVisible: isXiaohongshuPublishSettingsVisible(),
        scheduleEnabled: Boolean(findXiaohongshuScheduleInput()),
        bottomButtons: describeXiaohongshuBottomButtons(),
        lastTrustedClick: describeLastTrustedClick(),
      },
      successSignal: {
        successText: text.includes('发布成功'),
        redirectText: text.includes('秒后将返回发布页'),
        successUrl,
        publishedQuery: new URLSearchParams(location.search).get('published') === 'true',
      },
      textSample: text.slice(0, 500),
    }
  }
  
  function normalizeXiaohongshuScheduleDateTime(value) {
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
      time: `${hh}:${min}`,
      day: String(Number(dd)),
      hour: String(Number(hh)),
      minute: String(Number(min)),
      timestamp: new Date(`${yyyy}-${mm}-${dd}T${hh}:${min}:00`).getTime(),
    }
  }
  
  function assertXiaohongshuScheduleRange(value) {
    const timestamp = Number(value.timestamp)
    if (!Number.isFinite(timestamp)) throw new Error(`小红书定时发布时间无效：${value.full}`)
    const diff = timestamp - Date.now()
    const min = 60 * 60 * 1000
    const max = 14 * 24 * 60 * 60 * 1000
    if (diff < min) {
      throw new Error(`小红书定时发布时间过近：${value.full}，平台要求至少 1 小时后发布`)
    }
    if (diff > max) {
      throw new Error(`小红书定时发布时间过远：${value.full}，平台最多支持 14 天内定时发布`)
    }
  }
  
  function findXiaohongshuActionButton(labels) {
    const targets = labels.map(normalizeText)
    return collectVisibleActionElements()
      .filter((item) => targets.includes(item.text))
      .filter((item) => !item.disabled)
      .sort((left, right) => {
        const leftBottom = left.rect.top >= window.innerHeight * 0.55 ? 0 : 1
        const rightBottom = right.rect.top >= window.innerHeight * 0.55 ? 0 : 1
        return leftBottom - rightBottom || right.rect.top - left.rect.top
      })[0]?.el || null
  }
  
  function findXiaohongshuBottomPublishButton(label) {
    const target = normalizeText(label)
    return findXiaohongshuBottomPrimaryButtonByStyle(target)
      || collectVisibleActionElements()
      .filter((item) => item.text === target)
      .filter((item) => !item.disabled)
      .filter((item) => isXiaohongshuBottomActionCandidate(item.rect))
      .sort((left, right) => right.rect.top - left.rect.top || right.rect.width - left.rect.width)[0]?.el
      || findXiaohongshuBottomTextButton(label)
  }
  
  function findXiaohongshuBottomPublishClickPoint(label) {
    if (!isXiaohongshuPublishSettingsVisible()) return null
    const target = normalizeText(label)
    if (target !== '发布') {
      const scheduleInput = findXiaohongshuScheduleInput()
      if (!scheduleInput || !normalizeText(scheduleInput.value || scheduleInput.textContent || '').includes('-')) return null
    }
    const hostPoint = findXiaohongshuPublishHostClickPoint(target)
    if (hostPoint) return hostPoint
    const redPoint = findXiaohongshuBottomRedButtonPoint()
    if (redPoint) return { ...redPoint, label: target }
  
    const draft = findXiaohongshuBottomTextButton('暂存离开', { actionBarOnly: true })
      || findXiaohongshuBottomTextPoint('暂存离开', { actionBarOnly: true })
    const draftRect = draft?.nodeType === 1 ? draft.getBoundingClientRect() : null
    if (draftRect) {
      return {
        clientX: Math.round(draftRect.right + Math.max(86, draftRect.width * 0.85)),
        clientY: Math.round(draftRect.top + draftRect.height / 2),
        label: target,
      }
    }
  
    const textPoint = findXiaohongshuBottomTextPoint(target, { actionBarOnly: true })
    if (textPoint) return textPoint
    return {
      clientX: Math.round(window.innerWidth * 0.49),
      clientY: Math.round(window.innerHeight - 46),
      label: target,
    }
  }

  function findXiaohongshuPublishHost(label) {
    const target = normalizeText(label)
    return Array.from(document.querySelectorAll('xhs-publish-btn'))
      .filter(isVisibleElement)
      .filter((host) => normalizeText(host.getAttribute('submit-text') || '') === target)
      .filter((host) => host.getAttribute('submit-disabled') !== 'true')
      .filter((host) => host.getAttribute('submit-loading') !== 'true')
      .sort((left, right) => right.getBoundingClientRect().top - left.getBoundingClientRect().top)[0] || null
  }

  function findXiaohongshuPublishHostClickPoint(label) {
    const host = findXiaohongshuPublishHost(label)
    if (!host) return null
    const shadowButton = Array.from(host.shadowRoot?.querySelectorAll?.('button') || [])
      .find((button) => normalizeText(button.textContent || '') === normalizeText(label)
        && !button.disabled
        && button.getAttribute('aria-disabled') !== 'true')
    if (shadowButton && isVisibleElement(shadowButton)) {
      const buttonRect = shadowButton.getBoundingClientRect()
      return {
        clientX: Math.round(buttonRect.left + buttonRect.width / 2),
        clientY: Math.round(buttonRect.top + buttonRect.height / 2),
        label: normalizeText(label),
        source: 'xhs-publish-btn-shadow',
      }
    }
    const rect = host.getBoundingClientRect()
    if (rect.width < 80 || rect.height < 24) return null
    const point = xiaohongshuPublishHostPrimaryButtonPoint(rect)
    return {
      ...point,
      label: normalizeText(label),
      source: 'xhs-publish-btn',
    }
  }

  function xiaohongshuPublishHostPrimaryButtonPoint(rect) {
    const width = Math.max(0, Number(rect?.width) || 0)
    const height = Math.max(0, Number(rect?.height) || 0)
    const left = Number(rect?.left) || 0
    const top = Number(rect?.top) || 0
    const actionWidth = Math.min(120, Math.max(88, width * 0.18))
    const actionGap = Math.min(24, Math.max(12, width * 0.035))
    return {
      clientX: Math.round(left + width / 2 + (actionWidth + actionGap) / 2),
      clientY: Math.round(top + height / 2),
    }
  }
  
  function findXiaohongshuBottomTextPoint(targetText, options = {}) {
    const { actionBarOnly = false } = options
    const target = normalizeText(targetText)
    const minTop = actionBarOnly ? xiaohongshuPublishActionMinTop() : window.innerHeight * 0.44
    const candidates = Array.from(document.querySelectorAll('button, a, [role="button"], label, div, span, p'))
      .filter(isVisibleElement)
      .map((el) => ({ el, text: normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || ''), rect: el.getBoundingClientRect() }))
      .filter((item) => item.text === target)
      .filter((item) => item.rect.top >= minTop)
      .filter((item) => isXiaohongshuBottomActionCandidate(item.rect))
      .filter((item) => item.rect.width >= 20 && item.rect.width <= 320 && item.rect.height >= 14 && item.rect.height <= 120)
      .sort((left, right) => right.rect.top - left.rect.top || right.rect.width - left.rect.width)
    const candidate = candidates[0]
    if (!candidate) return null
    return {
      clientX: Math.round(candidate.rect.left + candidate.rect.width / 2),
      clientY: Math.round(candidate.rect.top + candidate.rect.height / 2),
      label: target,
    }
  }
  
  function findXiaohongshuBottomRedButtonPoint() {
    const minTop = xiaohongshuPublishActionMinTop()
    const candidates = Array.from(document.querySelectorAll('button, a, [role="button"], div, span'))
      .filter(isVisibleElement)
      .map((el) => {
        const rect = el.getBoundingClientRect()
        const style = window.getComputedStyle(el)
        return { el, rect, background: style.backgroundColor || '', text: normalizeText(el.textContent || el.getAttribute('aria-label') || '') }
      })
      .filter((item) => item.rect.top >= minTop)
      .filter((item) => isXiaohongshuBottomActionCandidate(item.rect))
      .filter((item) => item.rect.width >= 70 && item.rect.width <= 260 && item.rect.height >= 28 && item.rect.height <= 90)
      .filter((item) => isLikelyXiaohongshuPrimaryButtonColor(item.background) && (!item.text || ['发布', '定时发布'].includes(item.text)))
      .sort((left, right) => {
        const leftExact = left.text === '定时发布' ? 0 : 1
        const rightExact = right.text === '定时发布' ? 0 : 1
        return leftExact - rightExact || right.rect.top - left.rect.top || right.rect.width - left.rect.width
      })
    const candidate = candidates[0]
    if (!candidate) return null
    return {
      clientX: Math.round(candidate.rect.left + candidate.rect.width / 2),
      clientY: Math.round(candidate.rect.top + candidate.rect.height / 2),
    }
  }
  
  function findXiaohongshuBottomTextButton(label, options = {}) {
    const target = normalizeText(label)
    const actionBarOnly = options.actionBarOnly === true || target === '定时发布' || target === '发布'
    const minTop = actionBarOnly ? xiaohongshuPublishActionMinTop() : window.innerHeight * 0.44
    const textMatch = Array.from(document.querySelectorAll('button, a, [role="button"], label, div, span, p'))
      .filter(isVisibleElement)
      .map((el) => ({
        el,
        text: normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || ''),
        rect: el.getBoundingClientRect(),
        className: String(el.className || ''),
      }))
      .filter((item) => item.text === target)
      .map((item) => {
        const targetEl = findXiaohongshuBottomButtonClickTarget(item.el, target) || item.el
        return {
          ...item,
          el: targetEl,
          rect: targetEl.getBoundingClientRect(),
          className: String(targetEl.className || item.className || ''),
        }
      })
      .filter((item) => item.rect.top >= minTop)
      .filter((item) => isXiaohongshuBottomActionCandidate(item.rect))
      .filter((item) => item.rect.width >= 44 && item.rect.width <= 280 && item.rect.height >= 20 && item.rect.height <= 100)
      .filter((item) => !/disabled|disable/i.test(item.className))
      .sort((left, right) => {
        const leftInteractive = isInteractiveElement(left.el) ? 0 : 1
        const rightInteractive = isInteractiveElement(right.el) ? 0 : 1
        return leftInteractive - rightInteractive || right.rect.top - left.rect.top || right.rect.width - left.rect.width
      })[0]?.el || null
    if (textMatch) return textMatch
  
    return findXiaohongshuBottomPrimaryButtonByStyle(target)
  }
  
  function xiaohongshuPublishActionMinTop() {
    const input = findXiaohongshuScheduleInput()
    const rect = input?.getBoundingClientRect?.()
    if (rect && rect.height > 0) {
      return Math.min(window.innerHeight - 140, rect.bottom + 48)
    }
    return window.innerHeight * 0.82
  }
  
  function isXiaohongshuBottomActionCandidate(rect) {
    if (!rect || rect.width <= 0 || rect.height <= 0) return false
    return rect.top >= xiaohongshuPublishActionMinTop()
      && rect.left >= window.innerWidth * 0.18
      && rect.left <= window.innerWidth * 0.72
  }
  
  function findXiaohongshuBottomButtonClickTarget(el, targetText = '定时发布') {
    const target = normalizeText(targetText)
    let current = el
    let best = null
    for (let depth = 0; current && depth < 6; depth += 1) {
      const rect = current.getBoundingClientRect()
      const text = normalizeText(current.textContent || current.getAttribute?.('aria-label') || '')
      if (text.includes('暂存离开') && (text.includes('定时发布') || text.includes('发布'))) break
      if (text === target
        && rect.width >= 44
        && rect.width <= 280
        && rect.height >= 20
        && rect.height <= 100) {
        best = current
      }
      if (isInteractiveElement(current)) return current
      current = current.parentElement
    }
    return best
  }
  
  function findXiaohongshuBottomPrimaryButtonByStyle(targetText) {
    return Array.from(document.querySelectorAll('button, a, [role="button"], div, span'))
      .filter(isVisibleElement)
      .map((el) => {
        const rect = el.getBoundingClientRect()
        const style = window.getComputedStyle(el)
        return {
          el,
          rect,
          text: normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || ''),
          className: String(el.className || ''),
          background: style.backgroundColor || '',
          color: style.color || '',
        }
      })
      .filter((item) => item.text === targetText)
      .filter((item) => isXiaohongshuBottomActionCandidate(item.rect))
      .filter((item) => item.rect.width >= 44 && item.rect.width <= 280 && item.rect.height >= 20 && item.rect.height <= 100)
      .filter((item) => isLikelyXiaohongshuPrimaryButtonColor(item.background) || /primary|submit|publish|red/i.test(item.className))
      .sort((left, right) => right.rect.top - left.rect.top || right.rect.width - left.rect.width)[0]?.el || null
  }
  
  function isLikelyXiaohongshuPrimaryButtonColor(value) {
    const match = String(value || '').match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/i)
    if (!match) return false
    const red = Number(match[1])
    const green = Number(match[2])
    const blue = Number(match[3])
    return red >= 210 && green <= 90 && blue <= 120
  }
  
  function isXiaohongshuLayoutCanvasVisible() {
    const text = normalizeText(document.body?.innerText || document.body?.textContent || '')
    return text.includes('选择模板')
      || text.includes('封面设置')
      || text.includes('共') && text.includes('张') && Boolean(findXiaohongshuActionButton(['下一步']))
  }
  
  function isXiaohongshuPublishSettingsVisible() {
    const text = normalizeText(document.body?.innerText || document.body?.textContent || '')
    if (!text.includes('图片编辑')) return false
    const markers = [
      '活动话题',
      '内容设置',
      '添加组件',
      '更多设置',
      '原创声明',
      '允许合拍',
      '允许正文复制',
      '公开可见',
      '定时发布',
    ]
    return markers.filter((marker) => text.includes(marker)).length >= 3
  }

  function currentXiaohongshuStage() {
    const href = String(location.href || '')
    const text = normalizeText(document.body?.innerText || document.body?.textContent || '')
    if (href.includes('/publish/success') || text.includes('发布成功') || text.includes('秒后将返回发布页')) return 'publish_success'
    if (isXiaohongshuPublishSettingsVisible()) return 'publish_settings'
    if (isXiaohongshuLayoutCanvasVisible()) return 'layout_canvas'
    if (text.includes('返回') && text.includes('一键排版') && text.includes('字数')) return 'article_editor'
    if (href.includes('/publish/publish') && (text.includes('写长文') || text.includes('新的创作') || text.includes('导入链接'))) return 'creator_home'
    if (href.includes('/new/note-manager') || text.includes('笔记管理')) return 'note_manager'
    return 'unknown'
  }
  
  function isXiaohongshuImageGenerating() {
    const text = normalizeText(document.body?.innerText || document.body?.textContent || '')
    return text.includes('笔记图片生成中') || text.includes('请稍后')
  }
  
  function isXiaohongshuGeneratedImageReady() {
    if (isXiaohongshuImageGenerating()) return false
    const text = normalizeText(document.body?.innerText || document.body?.textContent || '')
    if (text.includes('图片编辑') && text.includes('笔记预览')) return true
    return getXiaohongshuGeneratedImageSnapshot().thumbnailCount > 0
  }
  
  function getXiaohongshuGeneratedImageSnapshot() {
    const images = Array.from(document.querySelectorAll('img'))
      .filter(isVisibleElement)
      .map((img) => ({ img, rect: img.getBoundingClientRect(), src: img.currentSrc || img.src || '' }))
      .filter((item) => item.rect.width >= 24 && item.rect.height >= 24)
    const previewText = normalizeText(document.body?.innerText || document.body?.textContent || '')
    const pageMatches = previewText.match(/\d+\/\d+/g) || []
    return {
      thumbnailCount: images.filter((item) => item.rect.top < window.innerHeight * 0.55).length,
      visibleImageCount: images.length,
      previewPages: pageMatches.slice(0, 8),
      generating: isXiaohongshuImageGenerating(),
    }
  }
  
  function describeXiaohongshuImageGenerationState() {
    const snapshot = getXiaohongshuGeneratedImageSnapshot()
    return `xhsImageGenerating=${snapshot.generating}; xhsThumbnailCount=${snapshot.thumbnailCount}; xhsVisibleImageCount=${snapshot.visibleImageCount}; xhsPreviewPages=${snapshot.previewPages.join('|') || '-'}`
  }
  
  function findXiaohongshuScheduleToggle() {
    const label = findXiaohongshuScheduleLabel()
    if (label) label.scrollIntoView?.({ block: 'center', inline: 'nearest' })
    const labelRect = label?.getBoundingClientRect?.()
    const card = findXiaohongshuScheduleCard(label)
    const scope = card || document
    const minLeft = labelRect ? labelRect.left + Math.min(160, Math.max(80, labelRect.width * 0.45)) : 0
    const candidates = Array.from(scope.querySelectorAll(
      '.custom-switch-switch .d-switch.d-clickable, .custom-switch-switch [role="switch"], .custom-switch-switch input[type="checkbox"], .custom-switch-switch .d-switch-box, [role="switch"], input[type="checkbox"]',
    ))
      .filter(isVisibleElement)
      .map((el) => {
        const target = normalizeXiaohongshuScheduleToggleTarget(el)
        return {
          el: target,
          rect: target.getBoundingClientRect(),
          text: normalizeText(target.textContent || target.getAttribute('aria-label') || target.getAttribute('title') || ''),
          distance: label ? elementDistance(label, target) : 0,
          role: target.getAttribute?.('role') || '',
          className: String(target.className || ''),
        }
      })
      .filter((item, index, items) => items.findIndex((other) => other.el === item.el) === index)
      .filter((item) => !labelRect || Math.abs(verticalCenter(item.rect) - verticalCenter(labelRect)) <= 70)
      .filter((item) => !labelRect || item.rect.left >= minLeft)
      .filter((item) => item.distance <= 520)
      .filter((item) => isXiaohongshuScheduleToggleElement(item.el))
      .sort((left, right) => {
        const leftRole = left.role === 'switch' ? 0 : 1
        const rightRole = right.role === 'switch' ? 0 : 1
        return leftRole - rightRole || left.distance - right.distance || right.rect.width - left.rect.width
      })
    return candidates[0]?.el || null
  }
  
  function findXiaohongshuScheduleInput() {
    const label = findXiaohongshuScheduleLabel()
    const labelRect = label?.getBoundingClientRect?.()
    const inputs = Array.from(document.querySelectorAll('input, [contenteditable="true"]'))
      .filter(isVisibleElement)
      .map((input) => ({
        input,
        distance: label ? elementDistance(label, input) : 0,
        rect: input.getBoundingClientRect(),
        text: normalizeText([input.value || '', input.textContent || '', input.getAttribute('placeholder') || '', input.getAttribute('aria-label') || '', input.getAttribute('title') || ''].join(' ')),
      }))
      .filter((item) => !label || item.distance <= 620)
      .filter((item) => !labelRect || Math.abs(verticalCenter(item.rect) - verticalCenter(labelRect)) <= 90)
      .filter((item) => !labelRect || item.rect.left >= labelRect.right + 80)
      .filter((item) => /\d{4}-\d{1,2}-\d{1,2}|\d{1,2}:\d{1,2}|时间|日期|发布/.test(item.text)
        || item.input.getAttribute('type') === 'datetime-local'
        || item.input.getAttribute('type') === 'text')
      .filter((item) => !/群聊|地点|路线|文件|标题|话题|用户|表情/.test(item.text))
      .sort((left, right) => {
        const leftValue = /\d{4}-\d{1,2}-\d{1,2}/.test(left.text) ? 0 : 1
        const rightValue = /\d{4}-\d{1,2}-\d{1,2}/.test(right.text) ? 0 : 1
        return leftValue - rightValue || left.distance - right.distance
      })
    return inputs[0]?.input || null
  }
  
  function findXiaohongshuScheduleLabel() {
    const target = normalizeText('定时发布')
    const candidates = Array.from(document.querySelectorAll('button, a, [role="button"], label, div, span, p'))
      .filter(isVisibleElement)
      .map((el) => ({ el, rect: el.getBoundingClientRect(), text: normalizeText(el.textContent || el.getAttribute('aria-label') || '') }))
      .filter((item) => item.text === target || (item.text.includes(target) && item.text.length <= 12))
      .sort((left, right) => {
        const leftExact = left.text === target ? 0 : 1
        const rightExact = right.text === target ? 0 : 1
        return leftExact - rightExact
          || left.rect.width - right.rect.width
          || left.rect.height - right.rect.height
          || left.rect.top - right.rect.top
      })
    return candidates[0]?.el || null
  }
  
  function findXiaohongshuScheduleRow(label = findXiaohongshuScheduleLabel()) {
    const card = findXiaohongshuScheduleCard(label)
    if (card) return card
    let current = label
    let best = null
    for (let depth = 0; current && depth < 10; depth += 1) {
      const rect = current.getBoundingClientRect()
      const text = normalizeText(current.textContent || '')
      if (text.includes('定时发布')
        && rect.width >= 180
        && rect.width <= Math.max(760, window.innerWidth * 0.75)
        && rect.height >= 32
        && rect.height <= 110) {
        if (!best) best = current
      }
      current = current.parentElement
    }
    return best || label
  }
  
  function normalizeXiaohongshuScheduleToggleTarget(el) {
    const switchArea = el?.closest?.('.custom-switch-switch')
    const explicit = switchArea?.querySelector?.('.d-switch.d-clickable, [role="switch"]')
    if (explicit && isVisibleElement(explicit)) return explicit
    let current = el
    for (let depth = 0; current && depth < 5; depth += 1) {
      if (current.getAttribute?.('role') === 'switch'
        || current.getAttribute?.('type') === 'checkbox'
        || current.matches?.('.d-switch.d-clickable, .d-switch-box')) {
        return current
      }
      current = current.parentElement
    }
    return el
  }

  function findXiaohongshuScheduleCard(label = findXiaohongshuScheduleLabel()) {
    const card = label?.closest?.('.custom-switch-card') || null
    return card && isVisibleElement(card) ? card : null
  }

  function findXiaohongshuScheduleToggleBox(toggle) {
    const switchArea = toggle?.closest?.('.custom-switch-switch') || toggle
    const box = switchArea?.querySelector?.('.d-switch-box') || null
    return box && isVisibleElement(box) ? box : null
  }

  function isXiaohongshuScheduleToggleElement(el) {
    if (!el) return false
    if (el.getAttribute?.('role') === 'switch' || el.getAttribute?.('type') === 'checkbox') return true
    return Boolean(el.matches?.('.d-switch.d-clickable, .d-switch-box'))
  }
  
  function setXiaohongshuDateTimeValue(input, value) {
    if (!input) return
    if (input instanceof HTMLInputElement || input instanceof HTMLTextAreaElement) {
      setTextValue(input, value)
      return
    }
    setEditablePlainText(input, value)
    dispatchEditEvents(input)
  }
  
  function isXiaohongshuScheduleValueMatched(value) {
    const text = normalizeText(readXiaohongshuScheduleInputText() || document.body?.innerText || '')
    return text.includes(value.full.replace(/\s+/g, '')) || text.includes(`${value.date}${value.time}`)
  }
  
  function readXiaohongshuScheduleInputText() {
    const input = findXiaohongshuScheduleInput()
    return input ? (input.value || input.textContent || input.getAttribute('aria-label') || '') : ''
  }
  
  function findXiaohongshuSchedulePicker() {
    const marker = Array.from(document.querySelectorAll('button, [role="button"], div, span'))
      .filter(isVisibleElement)
      .find((el) => /\d{4}年/.test(normalizeText(el.textContent || el.getAttribute('aria-label') || '')))
      || findVisibleTextElement('时', { exact: false, maxLength: 4 })
      || findVisibleTextElement('分', { exact: false, maxLength: 4 })
    if (!marker) return null
    return nearestLargeContainer(marker)
  }
  
  function findXiaohongshuPickerDay(day, picker) {
    const target = String(Number(day))
    return Array.from((picker || document).querySelectorAll('button, [role="button"], td, div, span'))
      .filter(isVisibleElement)
      .map((el) => ({ el, text: normalizeText(el.textContent || el.getAttribute('aria-label') || ''), rect: el.getBoundingClientRect(), className: String(el.className || '') }))
      .filter((item) => item.text === target)
      .filter((item) => !/disabled|disable/i.test(item.className))
      .filter((item) => item.rect.width <= 80 && item.rect.height <= 80)
      .sort((left, right) => left.rect.top - right.rect.top || left.rect.left - right.rect.left)[0]?.el || null
  }
  
  function findXiaohongshuPickerTimeOption(value, picker, columnIndex) {
    const target = String(Number(value))
    const pickerRect = picker?.getBoundingClientRect?.()
    const candidates = Array.from((picker || document).querySelectorAll('button, [role="button"], li, div, span'))
      .filter(isVisibleElement)
      .map((el) => ({ el, text: normalizeText(el.textContent || el.getAttribute('aria-label') || ''), rect: el.getBoundingClientRect(), className: String(el.className || '') }))
      .filter((item) => item.text === target || item.text === `${target}${columnIndex === 0 ? '时' : '分'}`)
      .filter((item) => !/disabled|disable/i.test(item.className))
      .filter((item) => item.rect.width <= 100 && item.rect.height <= 80)
      .filter((item) => !pickerRect || item.rect.left >= pickerRect.left + pickerRect.width * 0.55)
      .sort((left, right) => {
        const leftColumn = pickerRect ? Math.abs(left.rect.left - (pickerRect.left + pickerRect.width * (columnIndex === 0 ? 0.70 : 0.86))) : 0
        const rightColumn = pickerRect ? Math.abs(right.rect.left - (pickerRect.left + pickerRect.width * (columnIndex === 0 ? 0.70 : 0.86))) : 0
        return leftColumn - rightColumn || left.rect.top - right.rect.top
      })
    return candidates[0]?.el || null
  }
  
  function describeXiaohongshuPublishState() {
    const fullText = normalizeText(document.body?.innerText || document.body?.textContent || '')
    const text = fullText.slice(0, 600)
    const settingsMarkers = ['图片编辑', '活动话题', '内容设置', '添加组件', '更多设置', '定时发布']
      .filter((marker) => fullText.includes(marker))
      .join(',')
    const scheduleControls = describeXiaohongshuScheduleControls()
    const bottomButtons = describeXiaohongshuBottomButtons()
    const actions = collectVisibleActionElements()
      .map((item) => `${item.text}@${Math.round(item.rect.left)},${Math.round(item.rect.top)},${Math.round(item.rect.width)}x${Math.round(item.rect.height)}`)
      .slice(-30)
      .join('|')
    const inputs = Array.from(document.querySelectorAll('input, textarea, [contenteditable="true"]'))
      .filter(isVisibleElement)
      .map((input, index) => ({
        index,
        tag: input.tagName,
        type: input.getAttribute('type') || '',
        rect: (() => {
          const rect = input.getBoundingClientRect()
          return `${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)}`
        })(),
        value: input.value || input.textContent || '',
        placeholder: input.getAttribute('placeholder') || '',
        aria: input.getAttribute('aria-label') || '',
      }))
      .slice(-12)
    return `xhsStage=${currentXiaohongshuStage()}; xhsSettingsVisible=${isXiaohongshuPublishSettingsVisible()}; xhsSettingsMarkers=${settingsMarkers || '-'}; ${scheduleControls}; xhsPublishHosts=${describeXiaohongshuPublishHosts()}; xhsBottomButtons=${bottomButtons || '-'}; xhsText=${text || '-'}; actions=${actions || '-'}; inputs=${JSON.stringify(inputs).slice(0, 520)}; lastTrustedClick=${describeLastTrustedClick()}`
  }

  function describeXiaohongshuPublishHosts() {
    return Array.from(document.querySelectorAll('xhs-publish-btn')).map((host) => {
      const rect = host.getBoundingClientRect()
      return `${host.getAttribute('submit-text') || '-'}@${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)},disabled=${host.getAttribute('submit-disabled') || '-'},loading=${host.getAttribute('submit-loading') || '-'}`
    }).join('|') || '-'
  }
  
  function describeXiaohongshuBottomButtons() {
    return Array.from(document.querySelectorAll('button, a, [role="button"], div, span, p'))
      .filter(isVisibleElement)
      .map((el) => {
        const rect = el.getBoundingClientRect()
        const style = window.getComputedStyle(el)
        return {
          text: normalizeText(el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || ''),
          rect,
          background: style.backgroundColor || '',
          className: String(el.className || ''),
        }
      })
      .filter((item) => item.text && item.rect.top >= window.innerHeight * 0.44)
      .filter((item) => /发布|暂存|定时/.test(item.text) || isLikelyXiaohongshuPrimaryButtonColor(item.background))
      .sort((left, right) => right.rect.top - left.rect.top || left.text.length - right.text.length)
      .slice(0, 12)
      .map((item) => `${item.text.slice(0, 16)}@${Math.round(item.rect.left)},${Math.round(item.rect.top)},${Math.round(item.rect.width)}x${Math.round(item.rect.height)},bg=${item.background}`)
      .join('|')
  }
  
  function describeXiaohongshuScheduleControls() {
    const label = findXiaohongshuScheduleLabel()
    const card = findXiaohongshuScheduleCard(label)
    const labelRect = label?.getBoundingClientRect?.()
    const labelInfo = labelRect
      ? `${Math.round(labelRect.left)},${Math.round(labelRect.top)},${Math.round(labelRect.width)}x${Math.round(labelRect.height)}`
      : '-'
    const candidates = Array.from((card || document).querySelectorAll(
      '.custom-switch-switch .d-switch.d-clickable, .custom-switch-switch [role="switch"], .custom-switch-switch input[type="checkbox"], .custom-switch-switch .d-switch-box, [role="switch"], input[type="checkbox"]',
    ))
      .filter(isVisibleElement)
      .map((el) => {
        const target = normalizeXiaohongshuScheduleToggleTarget(el)
        const rect = target.getBoundingClientRect()
        return {
          el: target,
          rect,
          text: normalizeText(target.textContent || target.getAttribute('aria-label') || target.getAttribute('title') || ''),
          role: target.getAttribute?.('role') || '',
          type: target.getAttribute?.('type') || '',
          className: String(target.className || ''),
          distance: label ? elementDistance(label, target) : 0,
        }
      })
      .filter((item, index, items) => items.findIndex((other) => other.el === item.el) === index)
      .filter((item) => isXiaohongshuScheduleToggleElement(item.el))
      .sort((left, right) => left.distance - right.distance)
      .slice(0, 8)
      .map((item) => `${item.role || item.type || 'node'}:${item.text || '-'}@${Math.round(item.rect.left)},${Math.round(item.rect.top)},${Math.round(item.rect.width)}x${Math.round(item.rect.height)},d=${Math.round(item.distance)}`)
      .join('|')
    return `xhsScheduleLabel=${labelInfo}; xhsScheduleToggle=${findXiaohongshuScheduleToggle() ? 'found' : 'missing'}; xhsSwitches=${candidates || '-'}`
  }

  function createIdentityReader(deps = {}) {
    return {
      readIdentity: () => readIdentity(deps),
      isLikelyAccountName: (value) => isLikelyAccountName(value, deps),
    }
  }

  function readIdentity(deps) {
    const accountIds = new Set()
    const accountNames = new Set()
    const preciseAccountNames = new Set()
    const normalizeText = requireDependency(deps.normalizeText, 'normalizeText')
    const rawVisibleText = document.body?.innerText || document.body?.textContent || ''
    const visibleText = normalizeText(rawVisibleText)
    collectCreatorHomeIdentity(accountIds, preciseAccountNames, deps)
    collectAccountIdsFromText(rawVisibleText, accountIds)
    if (preciseAccountNames.size) {
      for (const name of preciseAccountNames) accountNames.add(name)
    } else {
      collectAccountNamesFromAccountDom(accountNames, deps)
      collectAccountNamesFromScripts(accountNames, deps)
      collectAccountNamesFromStorage(accountNames, deps)
    }
    return {
      implemented: true,
      accountIds: Array.from(accountIds),
      accountNames: Array.from(accountNames),
      diagnostics: `href=${location.href}; nameSource=${preciseAccountNames.size ? 'creator_home_precise' : 'fallback'}; visibleTextLength=${visibleText.length}; accountIds=${Array.from(accountIds).join(',') || '-'}; accountNames=${Array.from(accountNames).join(',') || '-'}`,
    }
  }

  function collectCreatorHomeIdentity(accountIds, accountNames, deps) {
    if (location.hostname !== 'creator.xiaohongshu.com') return
    const root = document.querySelector('.personal, [class*="personal"], [class*="home-card"]')
    if (!root) return
    for (const el of Array.from(root.querySelectorAll('.account-name, [class*="account-name"], [class*="accountName"]'))) {
      const text = requireDependency(deps.normalizeAccountName, 'normalizeAccountName')(el.textContent || el.getAttribute('title') || el.getAttribute('aria-label') || '')
      if (isLikelyAccountName(text, deps)) accountNames.add(text)
    }
    collectAccountIdsFromText(root.innerText || root.textContent || '', accountIds)
  }

  function collectAccountIdsFromText(text, accountIds) {
    const patterns = [
      /小红书账号\s*[:：]?\s*(\d{5,})/g,
      /小红书号\s*[:：]?\s*(\d{5,})/g,
      /redId["']?\s*[:=]\s*["']?(\d{5,})/g,
      /userId["']?\s*[:=]\s*["']?(\d{5,})/g,
    ]
    for (const pattern of patterns) {
      for (const match of text.matchAll(pattern)) {
        const value = String(match[1] || '').trim()
        if (value) accountIds.add(value)
      }
    }
  }

  function collectAccountNamesFromAccountDom(accountNames, deps) {
    const preciseSelectors = [
      '.account-name',
      '[class*="account-name"]',
      '[class*="accountName"]',
      '.name-box',
      'span.name-box',
      '.d-topbar-default .user-info .name-box',
      '.user-info .name-box',
      '[class*="user"] [class*="name"]',
      '[class*="User"] [class*="Name"]',
      '[class*="avatar"] + *',
      '[class*="Avatar"] + *',
    ]
    for (const selector of preciseSelectors) {
      const elements = Array.from(document.querySelectorAll(selector))
      for (const el of elements) {
        if (!requireDependency(deps.isVisibleElement, 'isVisibleElement')(el)
          && !requireDependency(deps.hasVisibleAncestor, 'hasVisibleAncestor')(el)) continue
        if (!isCreatorAccountElement(el, deps)) continue
        const text = requireDependency(deps.normalizeAccountName, 'normalizeAccountName')(el.textContent || el.getAttribute('aria-label') || '')
        if (isLikelyAccountName(text, deps)) accountNames.add(text)
      }
    }
  }

  function isCreatorAccountElement(el, deps) {
    if (!el) return false
    if (el.closest?.('#geo-env-fill-status')) return false
    if (requireDependency(deps.isTopRightAccountElement, 'isTopRightAccountElement')(el)) return true
    if (location.hostname === 'creator.xiaohongshu.com') {
      const rect = el.getBoundingClientRect()
      if (rect.top <= 90 && rect.left >= 180) return true
      const className = String(el.className || '')
      if (className.includes('name-box') && rect.top <= 120) return true
      if (el.closest?.('.user-info, [class*="user"], [class*="User"], [class*="top"], [class*="Top"], [class*="header"], [class*="Header"]')) {
        return rect.top <= 140
      }
    }
    return false
  }

  function collectAccountNamesFromText(text, accountNames, deps) {
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
        const value = requireDependency(deps.normalizeAccountName, 'normalizeAccountName')(match[1])
        if (isLikelyAccountName(value, deps)) accountNames.add(value)
      }
    }
  }

  function collectAccountNamesFromStorage(accountNames, deps) {
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
        if (!/(current|login|session|profile|user|account|creator|author|self|me|mine)/i.test(key)) continue
        collectAccountNamesFromText(`${key}:${value}`, accountNames, deps)
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

  function collectAccountNamesFromScripts(accountNames, deps) {
    const scripts = Array.from(document.scripts).slice(0, 80)
    for (const script of scripts) {
      const text = script.textContent || ''
      if (!text || !/(currentUser|loginUser|userInfo|userProfile|creatorInfo|nickname)/i.test(text)) continue
      collectAccountNamesFromText(text.slice(0, 200_000), accountNames, deps)
    }
  }

  function isLikelyAccountName(value, deps) {
    const text = requireDependency(deps.normalizeAccountName, 'normalizeAccountName')(value)
    if (text.length < 2 || text.length > 40) return false
    if (/^(首页|发布笔记|笔记管理|数据看板|活动中心|笔记灵感|创作学院|创作百科|上传视频|上传图文|写长文|发播客|新的创作|导入链接|创建|保存|返回)$/.test(text)) return false
    if (/^[\w.-]{2,40}$/.test(text)) return true
    return /[\u4e00-\u9fa5]/.test(text) && !/[，。！？、]/.test(text)
  }

  function createEntryNavigator(deps = {}) {
    return {
      maybeSelectEditorMode: (fillProfile) => maybeSelectEditorMode(fillProfile, deps),
      isCreatorShellVisible: () => isCreatorShellVisible(deps),
      describeEntryState: () => describeEntryState(deps),
    }
  }

  function editorSelectors() {
    return {
      title: [
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
      ],
      content: [
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
      ],
      tags: [
        'input[placeholder*="话题"]',
        'input[placeholder*="标签"]',
        'textarea[placeholder*="话题"]',
        'textarea[placeholder*="标签"]',
      ],
    }
  }

  async function maybeSelectEditorMode(fillProfile, deps) {
    if (!location.hostname.endsWith('xiaohongshu.com')) return
    if (await navigateToArticleEditorIfNeeded(fillProfile, deps)) return
    await ensureEntryPageReady(deps)
    if (isDirectArticlePublishUrl()) {
      await maybeStartLongFormCreation(fillProfile, deps)
      if (hasEditor(fillProfile, deps)) return
    }
    await maybeLeaveVideoUploadTab(fillProfile, deps)
    const modes = ['写长文', '上传图文']
    for (const mode of modes) {
      if (await clickMode(mode, fillProfile, deps)) return
    }
    const menu = requireDependency(deps.findClickableByExactText, 'findClickableByExactText')(['发布笔记'])
      || requireDependency(deps.findClickableByShortText, 'findClickableByShortText')(['发布笔记'])
    if (menu) {
      await requireDependency(deps.clickClosestAction, 'clickClosestAction')(menu, { platform: fillProfile.platform })
      requireDependency(deps.showStatus, 'showStatus')('已展开小红书发布菜单', 'info')
      await requireDependency(deps.delay, 'delay')(800)
      for (const mode of modes) {
        if (await clickMode(mode, fillProfile, deps)) return
      }
    }
    await maybeStartLongFormCreation(fillProfile, deps)
    if (hasEditor(fillProfile, deps)) return
    const diagnostics = requireDependency(deps.collectDiagnostics, 'collectDiagnostics')()
    requireDependency(deps.showStatus, 'showStatus')(`小红书发布页未找到图文/长文编辑器；${describeEntryState(deps)}；${diagnostics}`, 'info')
  }

  async function navigateToArticleEditorIfNeeded(fillProfile, deps) {
    if (hasEditor(fillProfile, deps)) return true
    if (location.hostname !== 'creator.xiaohongshu.com') return false
    const directArticleUrl = PUBLISH_URL
    const currentTarget = new URLSearchParams(location.search).get('target')
    if (location.pathname.includes('/publish/publish') && currentTarget === 'article') return false
    requireDependency(deps.showStatus, 'showStatus')('小红书切换到长文编辑直达页', 'info')
    location.href = directArticleUrl
    await requireDependency(deps.delay, 'delay')(2600)
    return hasEditor(fillProfile, deps)
  }

  async function maybeLeaveVideoUploadTab(fillProfile, deps) {
    if (hasEditor(fillProfile, deps)) return
    if (!isVideoUploadTabVisible(deps)) return
    for (const mode of ['写长文', '上传图文']) {
      const tab = findModeTab(mode, deps)
      if (!tab) continue
      await requireDependency(deps.clickClosestAction, 'clickClosestAction')(tab, { platform: fillProfile.platform })
      requireDependency(deps.showStatus, 'showStatus')(`小红书当前停留上传视频页，已切换到：${mode}`, 'info')
      await requireDependency(deps.delay, 'delay')(1800)
      await maybeStartLongFormCreation(fillProfile, deps)
      if (hasEditor(fillProfile, deps)) return
    }
  }

  async function ensureEntryPageReady(deps) {
    if (!location.hostname.endsWith('xiaohongshu.com')) return
    const deadline = Date.now() + 6000
    while (Date.now() < deadline) {
      if (findEntryElement(deps)) return
      const fillProfile = requireDependency(deps.buildFillProfile, 'buildFillProfile')({ platform: 'xiaohongshu' })
      if (hasEditor(fillProfile, deps)) return
      await requireDependency(deps.delay, 'delay')(300)
    }
    if (isDirectArticlePublishUrl()) return
    if (location.pathname.includes('/publish/publish') && !findEntryElement(deps)) {
      requireDependency(deps.showStatus, 'showStatus')('小红书发布入口未渲染，切换到创作首页重试', 'info')
      location.href = 'https://creator.xiaohongshu.com/new/home'
      await requireDependency(deps.delay, 'delay')(2200)
    }
  }

  function isDirectArticlePublishUrl() {
    return location.hostname === 'creator.xiaohongshu.com'
      && location.pathname.includes('/publish/publish')
      && new URLSearchParams(location.search).get('target') === 'article'
  }

  function findEntryElement(deps) {
    return requireDependency(deps.findClickableByExactText, 'findClickableByExactText')(['发布笔记', '写长文', '上传图文', '新的创作', '新建长文', '新建创作', '发布图文笔记'])
      || requireDependency(deps.findClickableByShortText, 'findClickableByShortText')(['发布笔记', '写长文', '上传图文', '新的创作', '新建长文', '新建创作', '发布图文笔记'])
      || findCreateButton(deps)?.element
  }

  async function clickMode(mode, fillProfile, deps) {
    if (hasEditor(fillProfile, deps)) return true
    const tab = findModeTab(mode, deps)
    if (!tab) return false
    await requireDependency(deps.clickClosestAction, 'clickClosestAction')(resolveModeTabClickTarget(tab, deps), { platform: fillProfile.platform })
    requireDependency(deps.showStatus, 'showStatus')(`已切换小红书发布模式：${mode}`, 'info')
    await requireDependency(deps.delay, 'delay')(1800)
    await maybeStartLongFormCreation(fillProfile, deps)
    return hasEditor(fillProfile, deps)
  }

  function findModeTab(mode, deps) {
    const normalizeText = requireDependency(deps.normalizeText, 'normalizeText')
    const candidates = Array.from(document.querySelectorAll('button, a, [role="tab"], [role="button"], [class*="tab"], [class*="Tab"], li, div, span, p'))
      .map((el) => ({
        el,
        text: normalizeText(el.textContent || el.getAttribute('aria-label') || ''),
        rect: el.getBoundingClientRect(),
      }))
      .filter(({ el, text, rect }) => {
        if (!requireDependency(deps.isVisibleElement, 'isVisibleElement')(el) || rect.width <= 0 || rect.height <= 0) return false
        if (rect.top > 320 || rect.width > 260 || rect.height > 90) return false
        return text === mode || text.includes(mode)
      })
      .sort((left, right) => {
        const leftInteractive = requireDependency(deps.isInteractiveElement, 'isInteractiveElement')(left.el) ? 0 : 1
        const rightInteractive = requireDependency(deps.isInteractiveElement, 'isInteractiveElement')(right.el) ? 0 : 1
        return leftInteractive - rightInteractive
          || Math.abs(left.rect.top - 210) - Math.abs(right.rect.top - 210)
          || left.text.length - right.text.length
      })
    if (candidates[0]?.el) return candidates[0].el
    return requireDependency(deps.findClickableByExactText, 'findClickableByExactText')([mode])
      || requireDependency(deps.findClickableByShortText, 'findClickableByShortText')([mode])
  }

  function resolveModeTabClickTarget(el, deps) {
    if (!el) return el
    const normalizeText = requireDependency(deps.normalizeText, 'normalizeText')
    const currentText = normalizeText(el.textContent || el.getAttribute?.('aria-label') || '')
    let current = el
    let best = el
    for (let depth = 0; current && current !== document.body && depth < 5; depth += 1) {
      const rect = current.getBoundingClientRect?.()
      const text = normalizeText(current.textContent || current.getAttribute?.('aria-label') || '')
      if (rect && rect.width > 0 && rect.height > 0 && rect.width <= 320 && rect.height <= 110 && text.includes(currentText)) {
        best = current
      }
      if (current.matches?.('button, a, [role="tab"], [role="button"], [role="menuitem"], li, [class*="tab"], [class*="Tab"]')) {
        best = current
        break
      }
      current = current.parentElement
    }
    return best
  }

  function isVideoUploadTabVisible(deps) {
    const text = requireDependency(deps.normalizeText, 'normalizeText')(document.body?.innerText || document.body?.textContent || '')
    return location.hostname === 'creator.xiaohongshu.com'
      && location.pathname.includes('/publish/publish')
      && text.includes('上传视频')
      && (text.includes('拖拽视频到此') || text.includes('视频大小') || text.includes('视频格式'))
  }

  function describeEntryState(deps) {
    if (!location.hostname.endsWith('xiaohongshu.com')) return `xhsEntry=not_xhs; href=${location.href}`
    const normalizeText = requireDependency(deps.normalizeText, 'normalizeText')
    const tabTexts = Array.from(document.querySelectorAll('button, a, [role="tab"], [role="button"], div, span'))
      .map((el) => normalizeText(el.textContent || el.getAttribute('aria-label') || ''))
      .filter((text) => text && /上传视频|上传图文|写长文|发播客|发布笔记|新的创作|新建/.test(text))
      .slice(0, 24)
    return `xhsEntry=href:${location.href}; videoTab=${isVideoUploadTabVisible(deps)}; tabs=${tabTexts.join('|') || '-'}`
  }

  async function maybeStartLongFormCreation(fillProfile, deps) {
    if (hasEditor(fillProfile, deps)) return
    const createButton = findCreateButton(deps)
    if (createButton) {
      await requireDependency(deps.clickClosestAction, 'clickClosestAction')(createButton.element, { ...createButton.options, platform: fillProfile.platform })
      requireDependency(deps.showStatus, 'showStatus')(`已点击小红书入口：${createButton.label}`, 'info')
      await waitForEditorCandidate(fillProfile, 15000, deps)
    }
    if (hasEditor(fillProfile, deps)) return
    await maybeConfirmLongFormCreation(fillProfile, deps)
  }

  async function waitForEditorCandidate(fillProfile, timeoutMs, deps) {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      if (hasEditor(fillProfile, deps)) return true
      await requireDependency(deps.delay, 'delay')(300)
    }
    return false
  }

  async function maybeConfirmLongFormCreation(fillProfile, deps) {
    const confirmButton = requireDependency(deps.findClickableByExactText, 'findClickableByExactText')(['创建'])
      || requireDependency(deps.findClickableByShortText, 'findClickableByShortText')(['创建'])
    if (!confirmButton) return
    await requireDependency(deps.clickClosestAction, 'clickClosestAction')(confirmButton, { platform: fillProfile.platform })
    requireDependency(deps.showStatus, 'showStatus')('已点击小红书长文创建确认', 'info')
    await waitForEditorCandidate(fillProfile, 10_000, deps)
  }

  function findCreateButton(deps) {
    const normalizeText = requireDependency(deps.normalizeText, 'normalizeText')
    const exact = requireDependency(deps.findClickableByExactText, 'findClickableByExactText')(['新的创作', '新建长文', '新建创作', '发布图文笔记'])
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
        if (!requireDependency(deps.isVisibleElement, 'isVisibleElement')(el) || rect.width <= 0 || rect.height <= 0) return false
        if (rect.width * rect.height > 240_000) return false
        if (text.includes('新的创作')
          || text.includes('新建长文')
          || text.includes('新建创作')
          || text.includes('发布图文笔记')
          || text.includes('去写长文')) return true
        return false
      })
      .sort((left, right) => {
        const leftInteractive = requireDependency(deps.isInteractiveElement, 'isInteractiveElement')(left.el) ? 0 : 1
        const rightInteractive = requireDependency(deps.isInteractiveElement, 'isInteractiveElement')(right.el) ? 0 : 1
        return leftInteractive - rightInteractive
          || left.text.length - right.text.length
          || (left.rect.width * left.rect.height) - (right.rect.width * right.rect.height)
      })

    const candidate = candidates[0]
    if (!candidate) return null
    const options = candidate.text.includes('导入链接') ? { clickRatioX: 0.22 } : {}
    return { element: candidate.el, label: '新的创作', options }
  }

  function hasEditor(fillProfile, deps) {
    return Boolean(
      requireDependency(deps.findTitleElement, 'findTitleElement')(fillProfile)
      && requireDependency(deps.findContentElement, 'findContentElement')(null, fillProfile)
    )
  }

  function isCreatorShellVisible(deps) {
    const text = requireDependency(deps.normalizeText, 'normalizeText')(document.body?.innerText || document.body?.textContent || '')
    if (!text.includes('创作服务平台')) return false
    return [
      '发布笔记',
      '笔记管理',
      '数据看板',
      '草稿箱',
      '退出登录',
    ].some((marker) => text.includes(marker))
  }
  
    global.__GEO_XIAOHONGSHU_PLATFORM__ = {
    PUBLISH_URL,
    WORKS_LIST_URL,
    classifyFailureCode,
    isRetryableFailureCode,
    createPublishOptionsAdapter,
    createIdentityReader,
    createEntryNavigator,
    editorSelectors,
    resolvePublishOptions,
    testing: {
      xiaohongshuPublishHostPrimaryButtonPoint,
    },
  }
})(globalThis)
