;(function installBaijiahaoPlatform(global) {
  const PUBLISH_URL = 'https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1'
  const WORKS_LIST_URL = 'https://baijiahao.baidu.com/builder/rc/content?type=news'

  const RETRYABLE_FAILURE_CODES = new Set([
    'BAIJIAHAO_COVER_UPLOAD_TIMEOUT',
    'BAIJIAHAO_SCHEDULE_DIALOG_NOT_READY',
    'BAIJIAHAO_SCHEDULE_OPTION_NOT_FOUND',
    'BAIJIAHAO_PUBLISH_NOT_CONFIRMED',
    'BAIJIAHAO_PLATFORM_RATE_LIMITED',
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
    if (text.includes('封面选择弹窗未打开')) return 'BAIJIAHAO_COVER_PICKER_NOT_OPEN'
    if (text.includes('封面本地上传')) return 'BAIJIAHAO_COVER_UPLOAD_INPUT_NOT_FOUND'
    if (text.includes('封面上传完成')) return 'BAIJIAHAO_COVER_UPLOAD_TIMEOUT'
    if (text.includes('封面确认按钮')) return 'BAIJIAHAO_COVER_CONFIRM_NOT_FOUND'
    if (text.includes('正文误入标题区域')) return 'BAIJIAHAO_CONTENT_WRITTEN_TO_TITLE'
    if (text.includes('正文填充后页面未显示正文')) return 'BAIJIAHAO_UEDITOR_FILL_NOT_VISIBLE'
    if (text.includes('定时时间过近')) return 'BAIJIAHAO_SCHEDULE_TIME_TOO_SOON'
    if (text.includes('定时时间过远')) return 'BAIJIAHAO_SCHEDULE_TIME_TOO_LATE'
    if (text.includes('定时时间无效')) return 'BAIJIAHAO_SCHEDULE_TIME_INVALID'
    if (text.includes('定时发布按钮未找到')) return 'BAIJIAHAO_SCHEDULE_BUTTON_NOT_FOUND'
    if (text.includes('定时发布弹窗')) return 'BAIJIAHAO_SCHEDULE_DIALOG_NOT_READY'
    if (text.includes('定时发布下拉选项')) return 'BAIJIAHAO_SCHEDULE_OPTION_NOT_FOUND'
    if (text.includes('触发过快') || text.includes('点击速度太快') || text.includes('操作频繁') || text.includes('稍后再试')) return 'BAIJIAHAO_PLATFORM_RATE_LIMITED'
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
    const timing = resolveAutomationTimingOptions(options)
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
      }, deps, timing)
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
      throttle: options.throttle || platformOptions.throttle || profileOptions.throttle || {},
    }
  }

  function resolveAutomationTimingOptions(options = {}) {
    const throttle = options.throttle || {}
    return {
      beforeConfirmDelayMs: boundedNumber(throttle.beforeConfirmDelayMs, 4500, 1000, 15000),
      confirmRetryDelayMs: boundedNumber(throttle.confirmRetryDelayMs, 8000, 2000, 30000),
      afterConfirmClickDelayMs: boundedNumber(throttle.afterConfirmClickDelayMs, 3000, 800, 10000),
      maxConfirmAttempts: boundedNumber(throttle.maxConfirmAttempts, 4, 1, 5),
    }
  }

  function boundedNumber(value, fallback, min, max) {
    const number = Number(value)
    if (!Number.isFinite(number)) return fallback
    return Math.min(max, Math.max(min, Math.round(number)))
  }

  async function fillCover(coverImageUrl, platform, deps) {
    await dismissBlockingGuides(deps, platform)
    if (hasCoverSelected(deps)) return { filled: false, message: '百家号封面已存在' }
    scrollToCoverSection(deps)
    await requireDependency(deps.delay, 'delay')(500)
    const entry = await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => findCoverEntry(deps),
      8000,
      `百家号封面上传入口未找到；${describeCoverState(deps)}`,
    )
    await openCoverPickerFromEntry(entry, platform, deps)
    const picker = await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => findCoverPickerDialog(deps),
      8000,
      `百家号封面选择弹窗未打开；${describeCoverState(deps)}`,
    )

    const localUpload = await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => findLocalUploadEntry(deps, picker) || findCoverFileInput(picker),
      8000,
      `百家号封面本地上传入口未找到；${describeCoverState(deps)}`,
    )
    if (!isFileInput(localUpload) && platform !== 'baijiahao') {
      await requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')(localUpload, { platform })
      await requireDependency(deps.waitForCondition, 'waitForCondition')(
        () => findCoverFileInput(findCoverPickerDialog(deps) || picker),
        5000,
        `百家号封面本地上传文件框未就绪；${describeCoverState(deps)}`,
      )
    }
    await requireDependency(deps.uploadCoverImageFromLocalHelper, 'uploadCoverImageFromLocalHelper')(coverImageUrl, platform, '百家号')
    await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => hasCoverPickerImageUploaded(deps, findCoverPickerDialog(deps) || picker),
      20000,
      `百家号封面上传完成超时；${describeCoverState(deps)}`,
    )
    const confirm = findCoverConfirmButton(deps, findCoverPickerDialog(deps) || picker)
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

  async function fillScheduledPublish(scheduledAt, platform, context = {}, deps, timing = resolveAutomationTimingOptions()) {
    await dismissBlockingGuides(deps, platform)
    let value = normalizeScheduleDateTime(scheduledAt)
    if (!value.full) throw new Error(`百家号定时时间无效：${scheduledAt}`)
    assertScheduleRange(value)
    window.scrollTo(0, document.body.scrollHeight)
    await waitForBaijiahaoDraftIdle(deps, '打开定时发布前')
    const button = await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => findBottomActionButton('定时发布', deps),
      8000,
      `百家号定时发布按钮未找到；${describeBaijiahaoState(deps)}`,
    )
    await clickBottomButtonUntilDialog(button, platform, deps)
    const dialog = await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => findScheduleDialog(deps),
      8000,
      `百家号定时发布弹窗未就绪；target=${value.full}；${describeBaijiahaoState(deps)}`,
    )
    value = resolveScheduleValueAgainstDialog(value, dialog, deps)
    await fillScheduleDropdowns(dialog, value, platform, deps)
    const confirm = await requireDependency(deps.waitForCondition, 'waitForCondition')(
      () => findScheduleConfirmButton(deps),
      8000,
      `百家号定时发布弹窗确认按钮未找到；target=${value.full}；${describeBaijiahaoState(deps)}`,
    )
    await clickScheduleConfirmWithThrottle(confirm, value, platform, deps, timing)
    const verification = await waitForPublishSubmitted(value, context, deps)
    return {
      filled: true,
      scheduled: true,
      publishVerification: verification,
      message: value.adjustedFrom
        ? `已设置百家号定时发布=${value.full}（平台最早可选时间，原计划=${value.adjustedFrom}）`
        : `已设置百家号定时发布=${value.full}`,
    }
  }

  async function clickScheduleConfirmWithThrottle(initialConfirm, value, platform, deps, timing = resolveAutomationTimingOptions()) {
    const delay = requireDependency(deps.delay, 'delay')
    const clickTrustedActionOnce = requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')
    let confirm = await waitForScheduleConfirmReady(initialConfirm, value, deps, timing)
    for (let attempt = 0; attempt < timing.maxConfirmAttempts; attempt += 1) {
      if (attempt > 0) {
        await delay(timing.confirmRetryDelayMs)
        confirm = await waitForScheduleConfirmReady(findScheduleConfirmButton(deps) || confirm, value, deps, timing)
      }
      await clickTrustedActionOnce(confirm, { platform })
      await delay(timing.afterConfirmClickDelayMs)
      if (!hasClickTooFastWarning(deps)) return
    }
    throw new Error(`BAIJIAHAO_PLATFORM_RATE_LIMITED：百家号定时发布触发过快被平台拦截；target=${value.full}；${describeBaijiahaoState(deps)}`)
  }

  async function waitForScheduleConfirmReady(initialConfirm, value, deps, timing = resolveAutomationTimingOptions()) {
    const delay = requireDependency(deps.delay, 'delay')
    const waitForCondition = requireDependency(deps.waitForCondition, 'waitForCondition')
    await delay(timing.beforeConfirmDelayMs)
    return waitForCondition(
      () => {
        const confirm = findScheduleConfirmButton(deps) || initialConfirm
        if (!confirm || !isVisible(deps, confirm) || isDisabled(confirm)) return null
        const text = normalizeText(deps, document.body?.innerText || document.body?.textContent || '')
        if (text.includes('保存中')) return null
        if (!findScheduleDialog(deps)) return null
        return confirm
      },
      12000,
      `百家号定时发布确认前页面未稳定；target=${value.full}；${describeBaijiahaoState(deps)}`,
    )
  }

  async function waitForBaijiahaoDraftIdle(deps, stage) {
    const delay = requireDependency(deps.delay, 'delay')
    const waitForCondition = requireDependency(deps.waitForCondition, 'waitForCondition')
    await delay(2500)
    return waitForCondition(
      () => {
        const text = normalizeText(deps, document.body?.innerText || document.body?.textContent || '')
        return text.includes('保存中') ? null : true
      },
      10000,
      `百家号${stage || '发布前'}草稿仍在保存中；${describeBaijiahaoState(deps)}`,
    )
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
      const option = await findScheduleOptionWithScroll(targets[index], control, deps, 8000)
      if (option) {
        await requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')(option, { platform })
        await requireDependency(deps.delay, 'delay')(400)
        if (sameScheduleToken(control.textContent || control.getAttribute?.('aria-label') || '', targets[index])) continue
      }
      const keyboardSelected = await selectScheduleOptionByKeyboard(targets[index], control, deps)
      if (!keyboardSelected) {
        throw new Error(`百家号定时发布下拉选项未找到：${targets[index]}；${describeScheduleDialog(deps)}`)
      }
      await requireDependency(deps.delay, 'delay')(400)
    }
  }

  function resolveScheduleValueAgainstDialog(value, dialog, deps) {
    const platformValue = readScheduleValueFromDialog(dialog, value, deps)
    if (!platformValue?.full || !Number.isFinite(platformValue.timestamp)) return value
    if (platformValue.timestamp <= value.timestamp) return value
    return { ...platformValue, adjustedFrom: value.full }
  }

  function readScheduleValueFromDialog(dialog, baseValue, deps) {
    const controls = collectScheduleControls(dialog, deps)
    if (controls.length < 3) return null
    const tokens = controls.slice(0, 3).map((control) => normalizeScheduleToken(
      control.textContent || control.getAttribute?.('aria-label') || '',
    ))
    const monthDay = tokens[0].match(/^(\d{1,2})月(\d{1,2})日$/)
    const hour = tokens[1].match(/^(\d{1,2})点$/)
    const minute = tokens[2].match(/^(\d{1,2})分$/)
    if (!monthDay || !hour || !minute) return null
    const baseDate = new Date(baseValue.timestamp)
    const yyyy = baseDate.getFullYear()
    const mm = String(Number(monthDay[1])).padStart(2, '0')
    const dd = String(Number(monthDay[2])).padStart(2, '0')
    const hh = String(Number(hour[1])).padStart(2, '0')
    const min = String(Number(minute[1])).padStart(2, '0')
    let timestamp = new Date(`${yyyy}-${mm}-${dd}T${hh}:${min}:00`).getTime()
    if (Number.isFinite(timestamp) && timestamp < Date.now() - 60 * 60 * 1000) {
      timestamp = new Date(`${yyyy + 1}-${mm}-${dd}T${hh}:${min}:00`).getTime()
    }
    if (!Number.isFinite(timestamp)) return null
    return {
      full: `${new Date(timestamp).getFullYear()}-${mm}-${dd} ${hh}:${min}`,
      monthDay: `${Number(mm)}月${dd}日`,
      hour: hh,
      minute: min,
      timestamp,
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
    const hasReviewSignal = /提交成功|审核中/.test(text)
    const hasSuccessSignal = /发布成功|定时发布成功/.test(text)
    const leftEditor = !location.pathname.includes('/edit')
    const success = /发布成功|定时发布成功|提交成功|审核中/.test(text)
      || /\/content|\/manage|\/success/.test(location.href)
      || (!findScheduleDialog(deps) && leftEditor)
    return {
      verified: success,
      platformStatus: hasReviewSignal ? 'reviewing' : (hasSuccessSignal ? 'submitted' : 'scheduled'),
      pageUrl: location.href,
      pageTitle: document.title || '',
      expectedTitle: context.title || '',
      plannedScheduledAt: value.adjustedFrom || value.full,
      platformScheduledAt: value.full,
      scheduledAtText: value.full,
      scheduleAdjusted: Boolean(value.adjustedFrom),
      platformTimeCorrectedFrom: value.adjustedFrom || '',
      publishUi: {
        scheduleDialogVisible: Boolean(findScheduleDialog(deps)),
        bottomButtons: describeBottomButtons(deps),
      },
      successSignal: {
        successText: /发布成功|定时发布成功|提交成功|审核中/.test(text),
        reviewText: hasReviewSignal,
        submittedText: hasSuccessSignal,
        leaveEditor: leftEditor,
      },
      textSample: normalizedText.slice(0, 500),
    }
  }

  function hasClickTooFastWarning(deps) {
    const text = normalizeText(deps, document.body?.innerText || document.body?.textContent || '')
    return /点击速度太快|操作太快|请稍后再试/.test(text)
  }

  function isDisabled(el) {
    if (!el) return true
    return Boolean(el.disabled)
      || el.getAttribute?.('disabled') !== null
      || el.getAttribute?.('aria-disabled') === 'true'
      || /\bdisabled\b/i.test(String(el.className || ''))
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
    const coverCard = findCoverCardEntry(deps)
    if (coverCard) return coverCard
    const choose = findText(deps, '选择封面', { exact: false, maxLength: 20 })
    if (choose) return findCoverEntryClickTarget(deps, choose) || choose
    const setting = findText(deps, '设置封面', { exact: false, maxLength: 20 })
    return setting ? findCoverEntryClickTarget(deps, setting) || setting : null
  }

  function findCoverCardEntry(deps) {
    const root = document.querySelector('#bjhNewsCover')
    if (!root || !isVisible(deps, root)) return null
    const textNode = findCoverTextNodeInCoverRoot(deps, root)
    if (!textNode) return null
    return findCoverCardClickTarget(deps, textNode) || textNode
  }

  function findCoverTextNodeInCoverRoot(deps, root) {
    return Array.from(root.querySelectorAll('button, a, [role="button"], label, div, span, p'))
      .filter((el) => isVisible(deps, el))
      .map((el) => {
        const rect = el.getBoundingClientRect()
        const text = normalizeText(deps, el.textContent || el.getAttribute?.('aria-label') || '')
        return { el, rect, text, area: rect.width * rect.height }
      })
      .filter((item) => item.text.includes('选择封面'))
      .filter((item) => item.text.length <= 24)
      .sort((left, right) => left.text.length - right.text.length || left.area - right.area)[0]?.el || null
  }

  function scrollToCoverSection(deps) {
    const existing = findCoverEntry(deps)
    if (existing) {
      existing.scrollIntoView?.({ block: 'center', inline: 'center' })
      return
    }
    window.scrollTo(0, Math.max(0, document.body.scrollHeight * 0.72))
  }

  async function openCoverPickerFromEntry(entry, platform, deps) {
    const delay = requireDependency(deps.delay, 'delay')
    const clickTrustedActionOnce = requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')
    for (let attempt = 0; attempt < 3; attempt += 1) {
      const candidates = getCoverOpenClickCandidates(deps, entry)
      for (const candidate of candidates) {
        if (!candidate || !isVisible(deps, candidate)) continue
        candidate.scrollIntoView?.({ block: 'center', inline: 'center' })
        await delay(120)
        candidate.focus?.()
        await clickCoverCandidate(candidate, platform, deps, clickTrustedActionOnce)
        await delay(500)
        if (findCoverPickerDialog(deps)) return
      }
      await delay(300)
    }
  }

  async function clickCoverCandidate(candidate, platform, deps, clickTrustedActionOnce) {
    const rect = candidate.getBoundingClientRect?.()
    if (!rect || rect.width <= 0 || rect.height <= 0) return
    const points = [
      { x: 0.5, y: 0.5 },
      { x: 0.42, y: 0.5 },
      { x: 0.58, y: 0.5 },
    ]
    if (typeof deps.requestTrustedClickAt === 'function') {
      const delay = requireDependency(deps.delay, 'delay')
      for (const point of points) {
        await deps.requestTrustedClickAt(
          {
            clientX: Math.round(rect.left + rect.width * point.x),
            clientY: Math.round(rect.top + rect.height * point.y),
          },
          platform,
          normalizeText(deps, candidate.textContent || candidate.getAttribute?.('aria-label') || '选择封面').slice(0, 30),
          rect,
        )
        await delay(180)
        if (findCoverPickerDialog(deps)) return
      }
    } else {
      await clickTrustedActionOnce(candidate, { platform })
    }
    fireDomClick(candidate)
    candidate.click?.()
  }

  function getCoverOpenClickCandidates(deps, entry) {
    const root = document.querySelector('#bjhNewsCover')
    const textNode = root ? findCoverTextNodeInCoverRoot(deps, root) : null
    const card = textNode ? findCoverCardClickTarget(deps, textNode) : null
    const entryTarget = entry ? findCoverEntryClickTarget(deps, entry) : null
    const rootTargets = root
      ? Array.from(root.querySelectorAll('button, a, [role="button"], label, div, span'))
        .filter((el) => isVisible(deps, el))
        .map((el) => {
          const rect = el.getBoundingClientRect()
          const text = normalizeText(deps, el.textContent || el.getAttribute?.('aria-label') || '')
          return { el, rect, text, area: rect.width * rect.height }
        })
        .filter((item) => item.text.includes('选择封面') && isCoverEntryTargetSize(item.el))
        .sort((left, right) => {
          const leftCardScore = isLikelyCoverCardRect(left.rect) ? 0 : 1
          const rightCardScore = isLikelyCoverCardRect(right.rect) ? 0 : 1
          return leftCardScore - rightCardScore || left.area - right.area
        })
        .map((item) => item.el)
      : []
    return uniqueElements([card, textNode, entryTarget, entry, ...rootTargets, root])
      .filter((el) => el && el !== document.body && el !== document.documentElement)
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

  function findCoverPickerDialog(deps) {
    const markers = ['正文/本地上传', '本地上传', 'AI封图', '免费正版图库', '封面预览']
    for (const marker of markers) {
      const el = findText(deps, marker, { exact: false, maxLength: 40 })
      if (!el) continue
      const container = nearestLargeContainer(deps, el)
      const text = normalizeText(deps, container?.textContent || '')
      if ((text.includes('正文/本地上传') || text.includes('本地上传'))
          && (text.includes('AI封图') || text.includes('免费正版图库') || text.includes('封面预览'))) {
        return container
      }
    }
    return null
  }

  function findCoverEntryClickTarget(deps, el) {
    const explicit = el.closest?.('button, a, label, [role="button"]')
    if (explicit && isVisible(deps, explicit) && isCoverEntryTargetSize(explicit)) return explicit

    let current = el
    let best = null
    let bestArea = Number.POSITIVE_INFINITY
    for (let depth = 0; current && current !== document.body && depth < 7; depth += 1) {
      const rect = current.getBoundingClientRect?.()
      const text = normalizeText(deps, current.textContent || current.getAttribute?.('aria-label') || '')
      if (rect && isVisible(deps, current) && /选择封面|设置封面/.test(text)) {
        const area = rect.width * rect.height
        if (isCoverEntryTargetSize(current) && area < bestArea) {
          best = current
          bestArea = area
        }
      }
      current = current.parentElement
    }
    return best || el
  }

  function findCoverCardClickTarget(deps, el) {
    let current = el
    let best = null
    let bestScore = Number.NEGATIVE_INFINITY
    for (let depth = 0; current && current !== document.body && depth < 8; depth += 1) {
      if (!isVisible(deps, current)) {
        current = current.parentElement
        continue
      }
      const rect = current.getBoundingClientRect?.()
      const text = normalizeText(deps, current.textContent || current.getAttribute?.('aria-label') || '')
      if (rect && text.includes('选择封面')) {
        const area = rect.width * rect.height
        let score = 0
        if (rect.width >= 120 && rect.width <= 260 && rect.height >= 90 && rect.height <= 180) score += 120
        if (/content|default|item|spin|cover/i.test(String(current.className || ''))) score += 20
        score -= Math.abs(area - 198 * 134) / 1000
        if (score > bestScore) {
          best = current
          bestScore = score
        }
      }
      if (current.id === 'bjhNewsCover') break
      current = current.parentElement
    }
    return bestScore > 40 ? best : null
  }

  function isCoverEntryTargetSize(el) {
    const rect = el?.getBoundingClientRect?.()
    if (!rect) return false
    const area = rect.width * rect.height
    return rect.width >= 80
      && rect.width <= 460
      && rect.height >= 40
      && rect.height <= 360
      && area >= 4800
  }

  function isLikelyCoverCardRect(rect) {
    return rect
      && rect.width >= 120
      && rect.width <= 260
      && rect.height >= 90
      && rect.height <= 180
  }

  function uniqueElements(items) {
    const seen = new Set()
    const result = []
    for (const item of items) {
      if (!item || seen.has(item)) continue
      seen.add(item)
      result.push(item)
    }
    return result
  }

  function findLocalUploadEntry(deps, root = document) {
    return findTextInRoot(deps, root, '点击本地上传', { exact: false, maxLength: 24 })
      || findTextInRoot(deps, root, '本地上传', { exact: false, maxLength: 16 })
  }

  function findCoverFileInput(root = document) {
    const inputs = Array.from((root || document).querySelectorAll('input[type="file"]'))
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

  function findCoverConfirmButton(deps, root = document) {
    return collectActions(deps, root)
      .filter((item) => /^确定(?:\(\d+\))?$/.test(item.text))
      .sort((left, right) => right.rect.top - left.rect.top || right.rect.left - left.rect.left)[0]?.el || null
  }

  function hasCoverPickerImageUploaded(deps, root = document) {
    const scope = root || document
    const text = normalizeText(deps, scope.textContent || '')
    if (!/正文\/本地上传\(\d+\)|确定\(\d+\)/.test(text)) return false
    return Array.from(scope.querySelectorAll('img')).some((img) => isVisible(deps, img))
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
    const exactButton = findExactBottomButton(target, deps)
    if (exactButton) return exactButton
    return collectActions(deps)
      .filter((item) => item.text === target)
      .filter((item) => item.rect.top >= window.innerHeight * 0.55)
      .sort((left, right) => right.rect.top - left.rect.top || right.rect.left - left.rect.left)[0]?.el || null
  }

  function findExactBottomButton(target, deps) {
    return Array.from(document.querySelectorAll('button'))
      .filter((el) => isVisible(deps, el))
      .map((el) => ({ el, text: normalizeText(deps, el.textContent || el.getAttribute?.('aria-label') || ''), rect: el.getBoundingClientRect() }))
      .filter((item) => item.text === target)
      .filter((item) => item.rect.top >= window.innerHeight * 0.45)
      .sort((left, right) => right.rect.top - left.rect.top || right.rect.left - left.rect.left)[0]?.el || null
  }

  async function clickBottomButtonUntilDialog(button, platform, deps) {
    const delay = requireDependency(deps.delay, 'delay')
    const clickTrustedActionOnce = requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')
    for (let attempt = 0; attempt < 3; attempt += 1) {
      button.scrollIntoView?.({ block: 'center', inline: 'center' })
      await delay(120)
      button.focus?.()
      await clickTrustedActionOnce(button, { platform })
      fireDomClick(button)
      await delay(600)
      if (findScheduleDialog(deps)) return
    }
  }

  function fireDomClick(el) {
    const rect = el.getBoundingClientRect()
    const init = {
      bubbles: true,
      cancelable: true,
      view: window,
      clientX: rect.left + rect.width / 2,
      clientY: rect.top + rect.height / 2,
    }
    el.dispatchEvent(new PointerEvent('pointerdown', init))
    el.dispatchEvent(new MouseEvent('mousedown', init))
    el.dispatchEvent(new PointerEvent('pointerup', init))
    el.dispatchEvent(new MouseEvent('mouseup', init))
    el.dispatchEvent(new MouseEvent('click', init))
    el.click?.()
  }

  function findScheduleDialog(deps) {
    const marker = findText(deps, '定时发文', { exact: false, maxLength: 30 })
      || findText(deps, '当前时间后1小时', { exact: false, maxLength: 80 })
    if (!marker) return null
    const container = findDialogContainer(marker) || nearestLargeContainer(deps, marker)
    if (!container) return null
    const text = normalizeText(deps, container.textContent || '')
    return text.includes('定时发文') && text.includes('定时发布') ? container : null
  }

  function findDialogContainer(marker) {
    return marker.closest?.('[role="dialog"], .cheetah-modal, .cheetah-modal-confirm, .cheetah-modal-content') || null
  }

  function findScheduleConfirmButton(deps) {
    const dialog = findScheduleDialog(deps)
    const root = dialog?.closest?.('[role="dialog"]') || dialog || document
    const direct = findDialogButtonByText(root, '定时发布', deps)
    if (direct) return direct
    return collectActions(deps, root)
      .filter((item) => item.text === '定时发布')
      .filter((item) => item.rect.width >= 60 && item.rect.height >= 24)
      .sort((left, right) => right.rect.top - left.rect.top || right.rect.left - left.rect.left)[0]?.el || null
  }

  function findDialogButtonByText(root, text, deps) {
    const target = normalizeText(deps, text)
    return Array.from((root || document).querySelectorAll('button, [role="button"]'))
      .filter((el) => isVisible(deps, el))
      .map((el) => {
        const rect = el.getBoundingClientRect()
        const value = normalizeText(deps, el.textContent || el.getAttribute?.('aria-label') || '')
        const style = window.getComputedStyle(el)
        return { el, rect, value, primary: /rgb\(.*65.*86.*217|#3f56d9|#4e6ef2/i.test(style.backgroundColor || '') }
      })
      .filter((item) => item.value === target)
      .filter((item) => item.rect.width >= 60 && item.rect.height >= 24)
      .sort((left, right) => Number(right.primary) - Number(left.primary)
        || right.rect.top - left.rect.top
        || right.rect.left - left.rect.left)[0]?.el || null
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

  async function findScheduleOptionWithScroll(target, control, deps, timeoutMs = 8000) {
    const expected = normalizeScheduleToken(target)
    const delay = requireDependency(deps.delay, 'delay')
    const deadline = Date.now() + timeoutMs
    let listbox = findScheduleDropdownList(control)
    let scrollbox = findScheduleScrollBox(listbox) || listbox
    let lastTop = -1
    while (Date.now() < deadline) {
      listbox = findScheduleDropdownList(control) || listbox
      scrollbox = findScheduleScrollBox(listbox) || listbox
      const option = findScheduleOption(target, deps, listbox || document)
      if (option) return option
      if (!scrollbox) {
        await delay(200)
        continue
      }
      const before = scrollbox.scrollTop
      if (before === lastTop && before + scrollbox.clientHeight >= scrollbox.scrollHeight - 2 && expected) return null
      lastTop = before
      const step = Math.max(36, Math.floor((scrollbox.clientHeight || 160) * 0.6))
      scrollbox.scrollTop = Math.min(scrollbox.scrollHeight, before + step)
      scrollbox.dispatchEvent(new Event('scroll', { bubbles: true }))
      await delay(180)
    }
    return null
  }

  async function selectScheduleOptionByKeyboard(target, control, deps) {
    const delay = requireDependency(deps.delay, 'delay')
    const clickTrustedActionOnce = requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')
    for (let attempt = 0; attempt < 3; attempt += 1) {
      if (sameScheduleToken(control.textContent || control.getAttribute?.('aria-label') || '', target)) return true
      if (attempt > 0) {
        await clickTrustedActionOnce(control, { platform: 'baijiahao' })
        await delay(180)
      }
      const current = extractScheduleToken(control?.textContent || control?.getAttribute?.('aria-label') || '')
      const delta = scheduleKeyboardDelta(current, target)
      if (!Number.isFinite(delta) || delta === 0 || Math.abs(delta) > 80) return false
      const key = delta > 0 ? 'ArrowDown' : 'ArrowUp'
      const steps = Math.abs(delta)
      for (let index = 0; index < steps; index += 1) {
        fireScheduleKeyboard(control, key, attempt)
        await delay(90)
        if (sameScheduleToken(control.textContent || control.getAttribute?.('aria-label') || '', target)) {
          fireScheduleKeyboard(control, 'Enter', attempt)
          await delay(250)
          return sameScheduleToken(control.textContent || control.getAttribute?.('aria-label') || '', target)
        }
      }
      fireScheduleKeyboard(control, 'Enter', attempt)
      await delay(350)
      if (sameScheduleToken(control.textContent || control.getAttribute?.('aria-label') || '', target)) return true
    }
    return false
  }

  function scheduleKeyboardDelta(current, target) {
    const currentToken = normalizeScheduleToken(current)
    const targetToken = normalizeScheduleToken(target)
    const currentNumber = Number(currentToken.match(/^(\d{1,2})(?:点|分)$/)?.[1])
    const targetNumber = Number(targetToken.match(/^(\d{1,2})(?:点|分)$/)?.[1])
    if (Number.isFinite(currentNumber) && Number.isFinite(targetNumber)) return targetNumber - currentNumber
    const currentDate = currentToken.match(/^(\d{1,2})月(\d{1,2})日$/)
    const targetDate = targetToken.match(/^(\d{1,2})月(\d{1,2})日$/)
    if (currentDate && targetDate) return Number(targetDate[2]) - Number(currentDate[2])
    return Number.NaN
  }

  function fireKeyboard(el, key) {
    const init = {
      bubbles: true,
      cancelable: true,
      key,
      code: key,
      keyCode: key === 'Enter' ? 13 : key === 'ArrowDown' ? 40 : 38,
      which: key === 'Enter' ? 13 : key === 'ArrowDown' ? 40 : 38,
    }
    el.dispatchEvent(new KeyboardEvent('keydown', init))
    el.dispatchEvent(new KeyboardEvent('keypress', init))
    el.dispatchEvent(new KeyboardEvent('keyup', init))
  }

  function fireScheduleKeyboard(control, key, attempt = 0) {
    const target = scheduleKeyboardTarget(control, attempt)
    if (!target) return
    target.focus?.()
    fireKeyboard(target, key)
  }

  function scheduleKeyboardTarget(control, attempt = 0) {
    const input = control.querySelector?.('input[role="combobox"]') || control.querySelector?.('input') || null
    const active = document.activeElement && document.activeElement !== document.body ? document.activeElement : null
    const targetGroups = [
      [input, control, active],
      [control, input, active],
      [active, control, input, document.body, document],
    ]
    return uniqueElements(targetGroups[Math.min(attempt, targetGroups.length - 1)].filter(Boolean))[0] || null
  }

  function findScheduleScrollBox(listbox) {
    if (!listbox) return null
    const candidates = [listbox].concat(Array.from(listbox.querySelectorAll('*')))
    return candidates
      .filter((el) => {
        const rect = el.getBoundingClientRect()
        return rect.width > 0 && rect.height > 0 && el.scrollHeight > el.clientHeight + 4
      })
      .sort((left, right) => (right.scrollHeight - right.clientHeight) - (left.scrollHeight - left.clientHeight))[0] || null
  }

  function findScheduleDropdownList(control) {
    const input = control?.querySelector?.('[aria-controls]')
      || control?.closest?.('[aria-controls]')
      || control?.querySelector?.('input[role="combobox"]')
    const listId = input?.getAttribute?.('aria-controls') || input?.getAttribute?.('aria-owns') || ''
    if (listId) {
      const byId = document.getElementById(listId)
      if (byId && isScheduleDropdownVisible(byId)) return byId
    }
    const expandedLists = Array.from(document.querySelectorAll('[role="listbox"], [id$="_list"], .cheetah-select-dropdown, .cheetah-select-dropdown-menu'))
      .filter((el) => {
        return isScheduleDropdownVisible(el)
      })
      .sort((left, right) => right.getBoundingClientRect().top - left.getBoundingClientRect().top)
    return expandedLists[0] || null
  }

  function isScheduleDropdownVisible(el) {
    if (!el) return false
    const rect = el.getBoundingClientRect()
    const style = window.getComputedStyle(el)
    return rect.width > 0
      && rect.height > 0
      && style.display !== 'none'
      && style.visibility !== 'hidden'
      && style.opacity !== '0'
  }

  function findScheduleOption(target, deps, root = document) {
    const expected = normalizeScheduleToken(target)
    return Array.from((root || document).querySelectorAll('button, [role="option"], [role="menuitem"], li, div, span'))
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
        '#bjhNewsTitle [contenteditable="true"]',
        '#bjhNewsTitle textarea',
        '#bjhNewsTitle input',
        '#bjhNewsTitle',
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

  function findTextInRoot(deps, root, text, options = {}) {
    const { exact = false, maxLength = 40 } = options
    const target = normalizeText(deps, text)
    return Array.from((root || document).querySelectorAll('button, a, [role="button"], [role="tab"], [role="option"], [role="menuitem"], label, div, span, p'))
      .filter((el) => isVisible(deps, el))
      .find((el) => {
        const value = normalizeText(deps, el.textContent || el.getAttribute?.('aria-label') || '')
        if (!value || value.length > Math.max(maxLength, target.length)) return false
        return exact ? value === target : value.includes(target)
      }) || null
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
    const picker = findCoverPickerDialog(deps)
    const fileInputs = Array.from(document.querySelectorAll('input[type="file"]')).map((input, index) => ({
      index,
      accept: input.getAttribute('accept') || '',
      id: input.id || '',
      name: input.name || '',
      inPicker: Boolean(picker && picker.contains(input)),
      filesLength: input.files?.length || 0,
    }))
    const pickerText = normalizeText(deps, picker?.textContent || '').slice(0, 260)
    const coverEntries = describeCoverEntries(deps)
    const coverNodeDetails = describeCoverNodeDetails(deps)
    return `href=${location.href}; picker=${Boolean(picker)}; coverEntries=${coverEntries || '-'}; coverNodes=${coverNodeDetails || '-'}; fileInputs=${JSON.stringify(fileInputs).slice(0, 500)}; pickerText=${pickerText || '-'}; text=${text || '-'}`
  }

  function describeCoverEntries(deps) {
    const card = findCoverCardEntry(deps)
    const cardRect = card?.getBoundingClientRect?.()
    const cardText = cardRect ? `coverCard@${Math.round(cardRect.left)},${Math.round(cardRect.top)},${Math.round(cardRect.width)}x${Math.round(cardRect.height)}` : ''
    return [cardText].concat(['选择封面', '设置封面']
      .map((label) => {
        const el = findText(deps, label, { exact: false, maxLength: 20 })
        if (!el) return ''
        const target = findCoverEntryClickTarget(deps, el) || el
        const rect = target.getBoundingClientRect?.()
        if (!rect) return `${label}@-`
        return `${label}@${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)}`
      })
      .filter(Boolean))
      .filter(Boolean)
      .join('|')
  }

  function describeCoverNodeDetails(deps) {
    const root = document.querySelector('#bjhNewsCover')
    if (!root) return ''
    return Array.from(root.querySelectorAll('button, a, [role="button"], label, div, span, p'))
      .filter((el) => isVisible(deps, el))
      .map((el) => {
        const text = normalizeText(deps, el.textContent || el.getAttribute?.('aria-label') || '')
        if (!text.includes('选择封面')) return ''
        const rect = el.getBoundingClientRect()
        return `${text.slice(0, 16)}@${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)}`
      })
      .filter(Boolean)
      .slice(0, 8)
      .join('|')
  }

  function describeScheduleDialog(deps) {
    const dialog = findScheduleDialog(deps)
    const text = normalizeText(deps, dialog?.textContent || document.body?.innerText || '').slice(0, 500)
    const buttons = dialog ? describeDialogButtons(dialog, deps) : ''
    const controls = dialog ? describeScheduleControls(dialog, deps) : ''
    const list = describeVisibleScheduleList(deps)
    return `href=${location.href}; dialog=${Boolean(dialog)}; buttons=${buttons || '-'}; controls=${controls || '-'}; list=${list || '-'}; text=${text || '-'}`
  }

  function describeDialogButtons(dialog, deps) {
    const root = dialog?.closest?.('[role="dialog"]') || dialog
    return Array.from((root || document).querySelectorAll('button, [role="button"]'))
      .filter((el) => isVisible(deps, el))
      .map((el) => {
        const rect = el.getBoundingClientRect()
        const text = normalizeText(deps, el.textContent || el.getAttribute?.('aria-label') || '')
        return `${text || '-'}@${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)}`
      })
      .slice(0, 8)
      .join('|')
  }

  function describeScheduleControls(dialog, deps) {
    return collectScheduleControls(dialog, deps)
      .map((control, index) => {
        const rect = control.getBoundingClientRect()
        const text = normalizeText(deps, control.textContent || control.getAttribute?.('aria-label') || '')
        return `${index}:${text || '-'}@${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)}`
      })
      .join('|')
  }

  function describeVisibleScheduleList(deps) {
    const lists = Array.from(document.querySelectorAll('[role="listbox"], [id$="_list"], .cheetah-select-dropdown, .cheetah-select-dropdown-menu'))
      .filter((el) => isScheduleDropdownVisible(el))
      .slice(0, 3)
    return lists.map((list, index) => {
      const rect = list.getBoundingClientRect()
      const options = Array.from(list.querySelectorAll('button, [role="option"], [role="menuitem"], li, div, span'))
        .filter((el) => isVisible(deps, el))
        .map((el) => normalizeScheduleToken(el.textContent || el.getAttribute?.('aria-label') || ''))
        .filter(Boolean)
        .filter((item, itemIndex, items) => items.indexOf(item) === itemIndex)
        .slice(0, 12)
        .join(',')
      return `${index}@${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)}:${options || '-'}`
    }).join('|')
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
