;(function installDouyinPlatform(global) {
  const PUBLISH_URL = 'https://creator.douyin.com/creator-micro/content/upload?default-tab=3'
  const MANAGE_URL = 'https://creator.douyin.com/creator-micro/content/manage'
  const MIN_SCHEDULE_LEAD_MINUTES = 120
  const MAX_SCHEDULE_LEAD_MINUTES = 14 * 24 * 60
  const SCHEDULE_STEP_MINUTES = 5

  const RETRYABLE_FAILURE_CODES = new Set([
    'DOUYIN_ARTICLE_FORM_NOT_READY',
    'DOUYIN_COVER_UPLOAD_TIMEOUT',
    'DOUYIN_IMAGE_EDITOR_CLOSE_TIMEOUT',
    'DOUYIN_IMAGE_UPLOAD_TIMEOUT',
    'DOUYIN_PUBLISH_NOT_CONFIRMED',
    'PAGE_LOAD_TIMEOUT',
    'EDITOR_NOT_READY',
  ])
  const IMAGE_UPLOAD_WAIT_TIMEOUT_MS = 180_000
  const MUSIC_USE_MAX_ATTEMPTS = 3
  const MUSIC_USE_CONFIRM_TIMEOUT_MS = 3_500

  function normalizePlatform(value) {
    return String(value || '').trim().toLowerCase()
  }

  function classifyFailureCode(message, platform) {
    const text = String(message || '')
    const explicit = text.match(/^([A-Z0-9_]{3,80})[：:]/)?.[1]
    if (explicit) return explicit
    if (normalizePlatform(platform) !== 'douyin' && !text.includes('抖音')) return ''
    if (text.includes('文章表单')) return 'DOUYIN_ARTICLE_FORM_NOT_READY'
    if (text.includes('正文不允许包含图片')) return 'DOUYIN_BODY_IMAGE_NOT_ALLOWED'
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
      fillImageText: (payload, fillProfile) => fillImageText(payload, fillProfile, deps),
      describeState: () => describeState(deps),
    }
  }

  async function fillImageText(payload, fillProfile, deps) {
    const waitForCondition = requireDependency(deps.waitForCondition, 'waitForCondition')
    const delay = requireDependency(deps.delay, 'delay')
    const persist = requireDependency(deps.persistImageTextState, 'persistImageTextState')
    const taskId = Number(payload.taskId)
    const expectedImageCount = Number(payload.expectedImageCount || payload.imageUrls?.length || 0)
    const title = firstText(payload.title, payload.articleTitle)
    const descriptionBase = firstText(payload.descriptionBase)
    const topicQuery = firstText(payload.topicQuery)
    const finalDescription = firstText(payload.description, payload.finalDescription)
    if (!location.pathname.includes('/creator-micro/content/post/image')) {
      throw new Error(`DOUYIN_IMAGE_EDITOR_NOT_READY：当前不是抖音图文详情页；${describeState(deps)}`)
    }
    if (!taskId || expectedImageCount < 4 || expectedImageCount > 6
        || !title || !descriptionBase || !finalDescription || !topicQuery) {
      throw new Error('DOUYIN_IMAGE_TEXT_PAYLOAD_INVALID：服务端下发的抖音图文最终载荷不完整')
    }
    if (title.length > 20 || finalDescription.length > 1000) {
      throw new Error('DOUYIN_IMAGE_TEXT_LENGTH_INVALID：抖音图文标题或描述超过平台限制')
    }
    deps.updateStage?.('waiting_image_editor')
    await persist(taskId, { stage: 'waiting_image_editor', expectedImageCount })
    try {
      await waitForImageUploadCompletion(expectedImageCount, waitForCondition, deps)
    } catch (error) {
      const message = String(error?.message || error)
      const stage = message.startsWith('DOUYIN_IMAGE_UPLOAD_TIMEOUT') ? 'upload_timeout' : 'upload_failed'
      await persist(taskId, {
        stage,
        expectedImageCount,
        uploadFailure: message.slice(0, 1000),
      }).catch(() => {})
      throw error
    }

    deps.updateStage?.('filling_title')
    await persist(taskId, { stage: 'filling_title', expectedImageCount })
    const titleInput = await waitForCondition(
      () => visibleQuery('input[placeholder="添加作品标题"]'),
      15_000,
      `DOUYIN_TITLE_INPUT_NOT_FOUND：未找到作品标题输入框；${describeState(deps)}`,
    )
    setNativeInputValue(titleInput, title)
    if (String(titleInput.value || '').trim() !== title) {
      throw new Error('DOUYIN_TITLE_FILL_FAILED：作品标题回读不一致')
    }

    deps.updateStage?.('filling_description')
    await persist(taskId, { stage: 'filling_description' })
    const editor = await waitForCondition(
      () => visibleQuery('[data-slate-editor="true"][contenteditable="true"]')
        || visibleQuery('[contenteditable="true"]'),
      15_000,
      `DOUYIN_DESCRIPTION_EDITOR_NOT_FOUND：未找到作品描述编辑器；${describeState(deps)}`,
    )
    await replaceEditableText(editor, finalDescription, delay)
    await delay(250)
    if (!editorContainsText(editor, finalDescription)) {
      throw new Error('DOUYIN_DESCRIPTION_FILL_FAILED：作品描述与服务端最终内容回读不一致')
    }

    deps.updateStage?.('selecting_topic')
    await persist(taskId, { stage: 'selecting_topic', topicQuery })
    let topicSelected = false
    let selectedTopic = ''
    let topicSkippedReason = ''
    try {
      const popup = await waitForCondition(
        () => visibleQuery('.mention-suggest-mount-dom'),
        6_000,
        '抖音话题候选未出现',
      )
      const candidate = firstTopicCandidate(popup)
      if (!candidate) throw new Error('抖音话题首个候选不存在')
      selectedTopic = defaultNormalizeText(
        candidate.querySelector?.('[class*="tag-hash-view-name-"]')?.textContent
        || candidate.textContent
        || '',
      )
      if (!selectedTopic) throw new Error('抖音话题首个候选名称为空')
      await click(candidate, fillProfile.platform, deps, {
        trustedOnly: true,
        label: '抖音选择首个话题',
      })
      await waitForCondition(
        () => !popup.isConnected || !isVisible(popup) || hasTopicNode(editor),
        5_000,
        '抖音话题候选点击后候选层未关闭',
      )
      if (!editorContainsText(editor, selectedTopic)) {
        throw new Error('抖音话题候选点击后作品描述未保留选中话题')
      }
      topicSelected = true
    } catch (error) {
      topicSkippedReason = error?.message || String(error)
      selectedTopic = ''
      dismissTopicSuggestion(editor)
    }
    if (!topicSelected) {
      deps.showStatus?.(`抖音话题候选未确认，已保留系统话题：${topicSkippedReason || '候选未出现'}`, 'error')
    }
    if (!normalizeForCompare(editor.innerText || editor.textContent).includes(normalizeForCompare(descriptionBase))) {
      throw new Error('DOUYIN_DESCRIPTION_CHANGED：选择话题后作品描述发生异常变化')
    }

    deps.updateStage?.('selecting_location')
    await persist(taskId, {
      stage: 'selecting_location',
      topicSelected,
      selectedTopic,
      topicSkipped: !topicSelected,
      topicSkippedReason,
    })
    const locationResult = await selectFirstLocation(payload.locationQuery, fillProfile.platform, deps).catch((error) => ({
      selected: false,
      value: '',
      skippedReason: error?.message || String(error),
    }))
    if (!locationResult.selected) {
      deps.showStatus?.(`抖音位置未确认：${locationResult.skippedReason || '未知原因'}`, 'error')
    }

    deps.updateStage?.('selecting_music')
    await persist(taskId, {
      stage: 'selecting_music',
      locationSelected: locationResult.selected,
      selectedLocation: locationResult.value,
      locationSkipped: !locationResult.selected,
      locationSkippedReason: locationResult.skippedReason || '',
    })
    const musicResult = await selectFirstRecommendedMusic(fillProfile.platform, deps).catch(async (error) => {
      await closeMusicDrawer(fillProfile.platform, deps).catch(() => {})
      return { selected: false, value: '', skippedReason: error?.message || String(error) }
    })
    if (!musicResult.selected) {
      deps.showStatus?.(`抖音音乐未确认：${musicResult.skippedReason || '未知原因'}`, 'error')
    }

    deps.updateStage?.('ready_to_publish')
    await persist(taskId, {
      stage: 'ready_to_publish',
      topicSelected,
      selectedTopic,
      topicSkipped: !topicSelected,
      topicSkippedReason,
      locationSelected: locationResult.selected,
      selectedLocation: locationResult.value,
      locationSkipped: !locationResult.selected,
      locationSkippedReason: locationResult.skippedReason || '',
      musicSelected: musicResult.selected,
      selectedMusic: musicResult.value,
      musicSkipped: !musicResult.selected,
      musicSkippedReason: musicResult.skippedReason || '',
    })
    assertImageTextReadyToPublish({
      titleInput,
      editor,
      title,
      descriptionBase,
      topicQuery,
      topicSelected,
      selectedTopic,
      expectedImageCount,
    })
    const publishButton = findExactVisibleButton('发布')
    if (!publishButton || publishButton.disabled || publishButton.getAttribute('aria-disabled') === 'true') {
      throw new Error(`DOUYIN_PUBLISH_BUTTON_DISABLED：发布按钮不可用；${describeState(deps)}`)
    }

    // Irreversible boundary: persist before the single permitted click.
    deps.updateStage?.('submitting_publish')
    await persist(taskId, {
      stage: 'submitting_publish',
      publishClicked: true,
      publishClickedAt: new Date().toISOString(),
    })
    await click(publishButton, fillProfile.platform, deps, {
      trustedOnly: true,
      label: '抖音图文发布',
    })
    await delay(500)
    await waitForCondition(
      () => location.pathname.includes('/creator-micro/content/manage'),
      30_000,
      `DOUYIN_PUBLISH_NOT_CONFIRMED：点击发布后暂未进入作品管理页；${describeState(deps)}`,
    )
    await persist(taskId, { stage: 'verifying_manage_page' }).catch(() => {})
    return {
      filled: true,
      published: true,
      topicSelected,
      selectedTopic,
      topicSkippedReason,
      locationSelected: locationResult.selected,
      selectedLocation: locationResult.value,
      locationSkippedReason: locationResult.skippedReason || '',
      musicSelected: musicResult.selected,
      selectedMusic: musicResult.value,
      musicSkippedReason: musicResult.skippedReason || '',
      message: '抖音图文已提交，正在通过作品管理页确认结果',
    }
  }

  function visibleQuery(selector, root = document) {
    return Array.from(root.querySelectorAll(selector)).find(isVisible) || null
  }

  async function waitForImageUploadCompletion(expectedImageCount, waitForCondition, deps = {}) {
    let latestState = readImageUploadState(expectedImageCount)
    try {
      return await waitForCondition(
        () => {
          latestState = readImageUploadState(expectedImageCount)
          if (latestState.failed) {
            throw new Error(
              `DOUYIN_IMAGE_UPLOAD_FAILED：抖音页面报告图片上传失败，预期=${expectedImageCount}，`
              + `页面=${latestState.uploadedCount}`,
            )
          }
          return latestState.ready ? latestState : null
        },
        IMAGE_UPLOAD_WAIT_TIMEOUT_MS,
        'DOUYIN_IMAGE_UPLOAD_TIMEOUT：等待抖音图片上传完成超时',
      )
    } catch (error) {
      if (String(error?.message || error).startsWith('DOUYIN_IMAGE_UPLOAD_FAILED')) throw error
      latestState = readImageUploadState(expectedImageCount)
      throw new Error(
        `DOUYIN_IMAGE_UPLOAD_TIMEOUT：等待抖音图片上传完成超时，预期=${expectedImageCount}，`
        + `页面=${latestState.uploadedCount}，进度=${formatImageUploadProgress(latestState)}；${describeState(deps)}`,
      )
    }
  }

  function readImageUploadState(expectedImageCount, text = document.body?.innerText || document.body?.textContent || '') {
    return evaluateImageUploadState(text, expectedImageCount, readRenderedImageTextThumbnailCount())
  }

  function evaluateImageUploadState(text, expectedImageCount, renderedThumbnailCount = 0) {
    const progress = parseImageUploadProgress(text, expectedImageCount)
    const explicitCount = readExplicitUploadedImageCount(text)
    const thumbnailCount = Number(renderedThumbnailCount) || 0
    const uploadedCount = explicitCount ?? thumbnailCount
    return {
      ...progress,
      uploadedCount,
      explicitCount,
      thumbnailCount,
      ready: uploadedCount === expectedImageCount && !progress.pending && !progress.failed,
    }
  }

  function parseImageUploadProgress(text, expectedImageCount) {
    const normalized = defaultNormalizeText(text)
    const fractions = Array.from(normalized.matchAll(/(\d{1,2})\s*\/\s*(\d{1,2})/g))
      .map((match) => ({ current: Number(match[1]), total: Number(match[2]) }))
    const progress = fractions.find((item) => item.total === Number(expectedImageCount)) || null
    const percentMatch = normalized.match(/(?:^|\s)(\d{1,3})\s*%(?:\s|$)/)
    const percent = percentMatch ? Number(percentMatch[1]) : null
    const failed = /图片上传失败|上传失败|重新上传失败/.test(normalized)
    const hasUploadOperation = /取消上传|正在上传|上传中/.test(normalized)
    const pending = !failed && (
      hasUploadOperation
      || Boolean(progress && progress.current < progress.total)
    )
    return {
      current: progress?.current ?? null,
      total: progress?.total ?? null,
      percent,
      pending,
      failed,
    }
  }

  function readExplicitUploadedImageCount(text) {
    const matched = text.match(/已添加\s*(\d+)\s*张图片/)
    return matched ? Number(matched[1]) : null
  }

  function readRenderedImageTextThumbnailCount() {
    const editImageLabel = Array.from(document.querySelectorAll('span, div'))
      .filter(isVisible)
      .find((item) => defaultNormalizeText(item.textContent || '') === '编辑图片')
    let section = editImageLabel?.parentElement || null
    while (section && section !== document.body) {
      const thumbnails = Array.from(section.querySelectorAll('[class*="img-"], [draggable="true"]'))
        .filter(isVisible)
        .filter((item) => isSupportedBackgroundImage(getComputedStyle(item).backgroundImage))
      if (thumbnails.length > 0) return thumbnails.length
      section = section.parentElement
    }
    return 0
  }

  function readUploadedImageCount() {
    return readImageUploadState(Number.POSITIVE_INFINITY).uploadedCount
  }

  function formatImageUploadProgress(state) {
    if (state.current !== null && state.total !== null) return `${state.current}/${state.total}`
    if (state.percent !== null) return `${state.percent}%`
    return state.pending ? '上传中' : '未知'
  }

  function setNativeInputValue(input, value, options = {}) {
    input.focus()
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set
    setter?.call(input, value)
    input.dispatchEvent(new Event('input', { bubbles: true, composed: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
    if (options.blur !== false) input.blur()
  }

  async function replaceEditableText(editor, value, delay) {
    const text = String(value || '').replace(/\r\n?/g, '\n')
    editor.focus()
    selectEditableContents(editor)
    document.execCommand('delete', false)
    dispatchPasteIntoEditable(editor, text)
    await delay(150)
    if (editorContainsText(editor, text)) return

    editor.focus()
    selectEditableContents(editor)
    document.execCommand('delete', false)
    document.execCommand('insertHTML', false, plainTextToEditorHtml(text))
    editor.dispatchEvent(new InputEvent('input', {
      bubbles: true,
      composed: true,
      inputType: 'insertFromPaste',
      data: text,
    }))
    await delay(150)
  }

  function selectEditableContents(editor) {
    const selection = window.getSelection()
    const range = document.createRange()
    range.selectNodeContents(editor)
    selection.removeAllRanges()
    selection.addRange(range)
  }

  function dispatchPasteIntoEditable(editor, text) {
    try {
      const data = new DataTransfer()
      data.setData('text/plain', text)
      data.setData('text/html', plainTextToEditorHtml(text))
      const event = new ClipboardEvent('paste', {
        bubbles: true,
        cancelable: true,
        clipboardData: data,
      })
      editor.dispatchEvent(event)
    } catch (_) {
      const event = new Event('paste', { bubbles: true, cancelable: true })
      Object.defineProperty(event, 'clipboardData', {
        value: {
          getData: (type) => (type === 'text/html' ? plainTextToEditorHtml(text) : text),
          types: ['text/plain', 'text/html'],
        },
      })
      editor.dispatchEvent(event)
    }
  }

  function plainTextToEditorHtml(text) {
    return String(text || '')
      .replace(/\r\n?/g, '\n')
      .split('\n')
      .map((line) => `<div>${escapeEditorHtml(line) || '<br>'}</div>`)
      .join('')
  }

  function escapeEditorHtml(value) {
    return String(value || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;')
  }

  function dismissTopicSuggestion(editor) {
    editor.dispatchEvent(new KeyboardEvent('keydown', {
      key: 'Escape',
      code: 'Escape',
      bubbles: true,
      cancelable: true,
    }))
    editor.dispatchEvent(new KeyboardEvent('keyup', {
      key: 'Escape',
      code: 'Escape',
      bubbles: true,
    }))
  }

  function editorContainsText(editor, expected) {
    return normalizeForCompare(editor?.innerText || editor?.textContent)
      .includes(normalizeForCompare(expected))
  }

  function firstTopicCandidate(popup) {
    const candidates = Array.from(popup.querySelectorAll('[class*="tag-hash-"]')).filter(isVisible)
    return candidates.find((candidate) => !candidate.parentElement?.closest?.('[class*="tag-hash-"]'))
      || candidates.find((candidate) => candidate.querySelector?.('[class*="tag-hash-view-name-"]'))
      || candidates[0]
      || null
  }

  function hasTopicNode(editor) {
    return Boolean(editor.querySelector('[data-mention], [class*="mention"], [class*="topic"]'))
  }

  async function selectFirstLocation(locationQuery, platform, deps) {
    if (!firstText(locationQuery)) {
      return { selected: false, value: '', skippedReason: '品牌公开地址为空' }
    }
    const waitForCondition = requireDependency(deps.waitForCondition, 'waitForCondition')
    const typeTrustedText = requireDependency(deps.typeTrustedText, 'typeTrustedText')
    const query = String(locationQuery).trim()
    const control = findLocationControl()
    if (!control) throw new Error('抖音位置选择器未找到')
    const existingValue = readLocationSelection(control)
    if (existingValue) {
      return { selected: true, value: existingValue, skippedReason: '' }
    }
    await click(control, platform, deps, {
      trustedOnly: true,
      label: '抖音打开位置选择器',
    })
    const input = await waitForCondition(
      () => findLocationInput(control),
      5_000,
      '抖音位置选择器展开后未出现输入框',
    )
    try {
      clearLocationInput(input, false)
      let trustedInputError = ''
      try {
        input.focus()
        await typeTrustedText(input, query, {
          platform: 'douyin',
          label: '抖音图文位置',
        })
      } catch (error) {
        trustedInputError = error?.message || String(error)
      }
      let listbox = await waitForCondition(
        findLocationListbox,
        8_000,
        '抖音位置候选列表未出现',
      ).catch(() => null)
      if (!listbox) {
        setNativeInputValue(input, '', { blur: false })
        setNativeInputValue(input, query, { blur: false })
        listbox = await waitForCondition(
          findLocationListbox,
          8_000,
          `抖音位置候选列表未出现；trusted=${trustedInputError || '-'}`,
        )
      }
      const option = Array.from(listbox.querySelectorAll('[role="option"]')).find(isVisible)
      const collection = option?.querySelector('[class*="collection-v2-"]')
      if (!option || !collection || !isVisible(collection)) {
        throw new Error('抖音位置首项 collection-v2 节点未找到')
      }
      const selectedText = defaultNormalizeText(
        collection.querySelector('[class*="name-"]')?.textContent
        || collection.textContent
        || '',
      )
      await click(collection, platform, deps, {
        trustedOnly: true,
        label: '抖音选择首个位置',
      })
      let confirmedValue = ''
      await waitForCondition(
        () => {
          confirmedValue = readLocationSelection(control)
          return confirmedValue
            && !Array.from(document.querySelectorAll('[role="listbox"]')).some(isVisible)
        },
        5_000,
        '抖音位置点击后未回填地点名称',
      )
      return { selected: true, value: confirmedValue || selectedText, skippedReason: '' }
    } catch (error) {
      clearLocationInput(input, true)
      throw error
    }
  }

  function findLocationControl() {
    const root = document.querySelector('#douyin_creator_pc_anchor_jump')
    if (!root) return null
    const candidates = Array.from(root.querySelectorAll(
      '[class*="anchor-component-"] .semi-select-filterable, .semi-select-filterable',
    )).filter(isVisible)
    return candidates.find((control) => /输入相关位置|相关位置/.test(defaultNormalizeText(control.textContent || '')))
      || candidates.find((control) => control.closest('[class*="anchor-component-"]'))
      || null
  }

  function readLocationSelection(control) {
    const selection = Array.from(control?.querySelectorAll?.('.semi-select-selection-text') || [])
      .filter(isVisible)
      .find((element) => !element.classList.contains('semi-select-selection-placeholder'))
    return defaultNormalizeText(selection?.textContent || '')
  }

  function findLocationInput(control) {
    const scoped = Array.from(control?.querySelectorAll?.(
      '.semi-select-input input.semi-input[type="text"], input.semi-input[type="text"], input[type="text"]',
    ) || []).find(isVisible)
    if (scoped) return scoped
    if (document.activeElement?.tagName === 'INPUT'
        && isVisible(document.activeElement)
        && locationInputScore(document.activeElement) > 0) {
      return document.activeElement
    }
    return Array.from(document.querySelectorAll('input'))
      .filter(isVisible)
      .map((input) => ({ input, score: locationInputScore(input) }))
      .filter((item) => item.score > 0)
      .sort((left, right) => right.score - left.score)[0]?.input || null
  }

  function locationInputScore(input) {
    const descriptor = defaultNormalizeText(
      `${input.placeholder || ''} ${input.getAttribute('aria-label') || ''}`,
    )
    let context = ''
    let current = input.parentElement
    for (let depth = 0; current && depth < 5; depth += 1) {
      context += ` ${defaultNormalizeText(current.textContent || '')}`
      current = current.parentElement
    }
    let score = 0
    if (/输入相关位置|相关位置/.test(descriptor)) score += 500
    if (/位置|地点|地址/.test(descriptor)) score += 200
    if (/添加标签/.test(context)) score += 150
    if (/位置/.test(context)) score += 80
    if (/搜索音乐|作品标题/.test(descriptor)) score -= 600
    return score
  }

  function findLocationListbox() {
    return Array.from(document.querySelectorAll('[role="listbox"]'))
      .filter(isVisible)
      .find((listbox) => Array.from(listbox.querySelectorAll('[role="option"]'))
        .some((option) => isVisible(option) && option.querySelector('[class*="collection-v2-"]')))
      || null
  }

  function clearLocationInput(input, dismiss) {
    if (!input?.isConnected) return
    setNativeInputValue(input, '', { blur: false })
    if (dismiss) {
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }))
      input.blur()
    }
  }

  async function selectFirstRecommendedMusic(platform, deps) {
    const waitForCondition = requireDependency(deps.waitForCondition, 'waitForCondition')
    const delay = requireDependency(deps.delay, 'delay')
    const entry = findMusicSelectionEntry()
    if (!entry) throw new Error('抖音选择音乐入口未找到')
    await click(entry, platform, deps, {
      trustedOnly: true,
      label: '抖音选择音乐',
    })
    const drawer = await waitForCondition(
      () => visibleQuery('.semi-sidesheet'),
      8_000,
      '抖音音乐抽屉未打开',
    )
    const firstMusic = await waitForCondition(
      () => findFirstRecommendedMusicCard(drawer),
      12_000,
      `抖音第一首推荐音乐加载超时；抽屉=${defaultNormalizeText(drawer.textContent || '').slice(0, 180) || '-'}`,
    )
    const musicName = readMusicName(firstMusic.card)
    const failures = []
    await activateRecommendedMusic(firstMusic, platform, deps)

    for (let attempt = 1; attempt <= MUSIC_USE_MAX_ATTEMPTS; attempt += 1) {
      let target
      try {
        target = await waitForStableMusicUseTarget(
          drawer,
          musicName,
          waitForCondition,
        )
        await click(target.useButton, platform, deps, {
          trustedOnly: true,
          label: `抖音使用第一首音乐（第${attempt}次）`,
        })
      } catch (error) {
        failures.push(`第${attempt}次点击失败：${error?.message || String(error)}`)
        if (!visibleQuery('.semi-sidesheet')) break
        const current = findRecommendedMusicCard(drawer, musicName)
        if (current) await activateRecommendedMusic(current, platform, deps).catch(() => {})
        continue
      }

      if (await waitForMusicApplied(deps, delay, MUSIC_USE_CONFIRM_TIMEOUT_MS)) {
        return { selected: true, value: musicName, skippedReason: '' }
      }
      failures.push(`第${attempt}次点击后未出现“修改音乐”`)
      if (!visibleQuery('.semi-sidesheet')) break
      const current = findRecommendedMusicCard(drawer, musicName)
      if (current) await activateRecommendedMusic(current, platform, deps).catch(() => {})
    }

    throw new Error(`抖音第一首音乐“使用”按钮未生效；${failures.join('；') || '未获得有效点击结果'}`)
  }

  function findFirstRecommendedMusicCard(drawer) {
    return findRecommendedMusicCard(drawer)
  }

  function findRecommendedMusicCard(drawer, expectedMusicName = '') {
    if (!drawer?.isConnected || !isVisible(drawer)) return null
    const candidates = Array.from(drawer.querySelectorAll('[class*="card-container-left-"]'))
      .filter(isVisible)
      .map((left) => ({
        left,
        card: left.closest('[class*="card-wrapper-"]') || left.parentElement,
      }))
      .filter(({ card }) => card && isVisible(card))
    if (!expectedMusicName) return candidates[0] || null
    const normalizedExpected = defaultNormalizeText(expectedMusicName)
    return candidates.find(({ card }) => readMusicName(card) === normalizedExpected) || null
  }

  function readMusicName(card) {
    return defaultNormalizeText(
      card?.querySelector?.('[class*="song-name-"]')?.textContent
      || card?.textContent
      || '',
    )
  }

  async function activateRecommendedMusic(target, platform, deps) {
    const { left } = target || {}
    if (!left?.isConnected || !isVisible(left)) {
      throw new Error('抖音第一首推荐音乐卡片已失效')
    }
    left.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }))
    left.dispatchEvent(new MouseEvent('mouseenter', { bubbles: false }))
    left.dispatchEvent(new PointerEvent('pointerover', { bubbles: true }))
    left.dispatchEvent(new PointerEvent('pointerenter', { bubbles: false }))
    await click(left, platform, deps, {
      trustedOnly: true,
      label: '抖音第一首推荐音乐',
    })
  }

  async function waitForStableMusicUseTarget(drawer, musicName, waitForCondition) {
    let previousButton = null
    let previousRect = ''
    let stableObservations = 0
    return waitForCondition(
      () => {
        const current = findRecommendedMusicCard(drawer, musicName)
        const useButton = Array.from(current?.card?.querySelectorAll?.('button') || [])
          .find((button) => button.isConnected
            && isVisible(button)
            && defaultNormalizeText(button.textContent || '') === '使用')
        if (!current || !useButton) {
          previousButton = null
          previousRect = ''
          stableObservations = 0
          return null
        }
        const rect = useButton.getBoundingClientRect()
        const rectSignature = `${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)}`
        if (previousButton === useButton && previousRect === rectSignature) {
          stableObservations += 1
        } else {
          previousButton = useButton
          previousRect = rectSignature
          stableObservations = 1
        }
        return stableObservations >= 2 ? { ...current, useButton } : null
      },
      5_000,
      '抖音第一首音乐未出现稳定的使用按钮',
    )
  }

  async function waitForMusicApplied(deps, delay, timeoutMs) {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      if (!visibleQuery('.semi-sidesheet') && /修改音乐/.test(bodyText(deps))) return true
      await delay(200)
    }
    return false
  }

  function findMusicSelectionEntry() {
    const scopedSelectors = [
      '#DCPF [class*="container-right-"] span[class*="action-"]',
      '[class*="container-right-"] span[class*="action-"]',
      '[class*="container-right-"] span',
    ]
    for (const selector of scopedSelectors) {
      const entry = Array.from(document.querySelectorAll(selector))
        .filter(isVisible)
        .find((element) => defaultNormalizeText(element.textContent || '') === '选择音乐')
      if (entry) return entry
    }
    return Array.from(document.querySelectorAll('span[class*="action-"]'))
      .filter(isVisible)
      .find((element) => defaultNormalizeText(element.textContent || '') === '选择音乐')
      || null
  }

  async function closeMusicDrawer(platform, deps) {
    const drawer = visibleQuery('.semi-sidesheet')
    if (!drawer) return
    const close = Array.from(drawer.querySelectorAll('button, [role="button"], svg'))
      .find((item) => isVisible(item) && /关闭|close/i.test(`${item.getAttribute?.('aria-label') || ''} ${item.textContent || ''}`))
    if (close) await click(close, platform, deps)
    else document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }))
  }

  function findExactVisibleText(text) {
    return Array.from(document.querySelectorAll('button, [role="button"], span, div'))
      .filter(isVisible)
      .find((item) => defaultNormalizeText(item.textContent || '') === text)
  }

  function findExactVisibleButton(text) {
    return Array.from(document.querySelectorAll('button'))
      .filter(isVisible)
      .find((button) => defaultNormalizeText(button.textContent || '') === text)
  }

  function assertImageTextReadyToPublish(context) {
    if (readUploadedImageCount() !== context.expectedImageCount) {
      throw new Error('DOUYIN_IMAGE_COUNT_CHANGED：发布前图片数量发生变化')
    }
    if (String(context.titleInput.value || '').trim() !== context.title) {
      throw new Error('DOUYIN_TITLE_CHANGED：发布前标题回读不一致')
    }
    if (!normalizeForCompare(context.editor.innerText || context.editor.textContent)
      .includes(normalizeForCompare(context.descriptionBase))) {
      throw new Error('DOUYIN_DESCRIPTION_CHANGED：发布前作品描述回读不一致')
    }
    const expectedTopicText = context.topicSelected ? context.selectedTopic : context.topicQuery
    if (!normalizeForCompare(context.editor.innerText || context.editor.textContent)
      .includes(normalizeForCompare(expectedTopicText).replace(/^#/, ''))) {
      throw new Error('DOUYIN_TOPIC_CHANGED：发布前中文话题文本缺失')
    }
    const text = bodyText()
    if (/上传失败|图片上传失败|字数超限|超过\d+字/.test(text)) {
      throw new Error('DOUYIN_PAGE_VALIDATION_FAILED：页面存在上传错误或字数超限')
    }
    const scheduled = Array.from(document.querySelectorAll('input[type="radio"]:checked, [role="radio"][aria-checked="true"]'))
      .some((item) => /定时发布/.test(item.closest?.('label, div')?.textContent || ''))
    if (scheduled) throw new Error('DOUYIN_SCHEDULE_MODE_INVALID：抖音图文必须立即发布')
  }

  function normalizeForCompare(value) {
    return String(value || '').replace(/[\s\u200B\uFEFF]+/g, '').trim()
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
      uploadTarget: 'douyin_article_head_image',
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
      uploadTarget: options.uploadTarget || '',
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
      () => !isActiveImageEditorDialog(dialog, deps),
      12000,
      `DOUYIN_IMAGE_EDITOR_CLOSE_TIMEOUT：抖音图片编辑确认后弹窗未关闭；${describeState(deps)}`,
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
        ? `已设置抖音定时发布=${adjusted.full}（原计划=${adjusted.adjustedFrom}，已调整到平台可选时间）`
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
    if (hasEditorBodyImage()) {
      throw new Error(`DOUYIN_BODY_IMAGE_NOT_ALLOWED：抖音文章正文不允许包含图片，图片只能上传至文章头图/封面区域；${describeState(deps)}`)
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
    const publishButton = await waitForCondition(
      () => findDouyinSubmitButton(deps),
      12000,
      `抖音最终发布按钮未找到；${describeState(deps)}`,
    )
    notifyStage(deps, 'submitting_publish')
    await click(publishButton, platform, deps)
    notifyStage(deps, 'verifying_publish_result')
    const verification = await waitForCondition(
      () => verifyPublishSubmission(payload, scheduledAt, platformStatus, deps, publishAttemptKey),
      45000,
      `抖音发布后未检测到成功状态；${describeState(deps)}`,
    )
    return verification
  }

  function notifyStage(deps, stage) {
    if (typeof deps?.updateStage === 'function') deps.updateStage(stage)
  }

  function findDouyinSubmitButton(deps = {}) {
    const normalizeText = deps.normalizeText || defaultNormalizeText
    return Array.from(document.querySelectorAll('button'))
      .filter(isVisible)
      .filter((button) => normalizeText(button.textContent || '') === '发布')
      .filter((button) => !button.disabled && button.getAttribute('aria-disabled') !== 'true')
      .sort((left, right) => right.getBoundingClientRect().top - left.getBoundingClientRect().top)[0] || null
  }

  function verifyPublishSubmission(payload, scheduledAt, platformStatus, deps, publishAttemptKey) {
    return verifyManagePageAfterPublish(payload, scheduledAt, platformStatus, deps, publishAttemptKey)
      || verifyManagePageSuccessNotice(payload, scheduledAt, platformStatus, deps)
  }

  function verifyManagePageSuccessNotice(payload, scheduledAt, platformStatus, deps = {}) {
    if (!location.href.includes('/creator-micro/content/manage')) return null
    const normalizeText = deps.normalizeText || defaultNormalizeText
    const notice = Array.from(document.querySelectorAll('[role="alert"], [class*="toast"], [class*="Toast"], [class*="message"], [class*="Message"], [class*="notification"], [class*="Notification"]'))
      .filter(isVisible)
      .map((el) => normalizeText(el.textContent || ''))
      .find((text) => /(发布成功|定时发布成功|提交成功|已提交审核)/.test(text))
    if (!notice) return null
    return {
      verified: true,
      platformStatus,
      pageStatusCode: platformStatus === 'scheduled' ? 'scheduled' : 'submitted',
      pageStatus: notice,
      platformScheduledAt: scheduledAt || null,
      scheduledAtText: scheduledAt || null,
      platformPublishId: null,
      platformPublishedUrl: null,
      coverImageUrl: null,
      recordLinks: [],
      title: firstText(payload.title, payload.articleTitle).slice(0, 30),
      manageUrl: location.href,
      matchedText: notice.slice(0, 180),
      refreshed: false,
      reloadCount: 0,
    }
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
      platformPublishedUrl: null,
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
      platformPublishedUrl: null,
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
    const boundedTarget = target < earliest ? earliest : target
    const steppedTarget = roundDateUpToMinuteStep(boundedTarget, SCHEDULE_STEP_MINUTES)
    if (steppedTarget > latest) {
      throw new Error(`DOUYIN_SCHEDULE_TIME_TOO_LATE：抖音定时时间过远：${value.full}`)
    }
    const adjusted = formatDateTime(steppedTarget)
    if (adjusted !== value.full) {
      return { full: adjusted, date: steppedTarget, adjustedFrom: value.full }
    }
    return { ...value, date: steppedTarget }
  }

  function roundDateUpToMinuteStep(date, stepMinutes) {
    const rounded = new Date(date.getTime())
    rounded.setSeconds(0, 0)
    const remainder = rounded.getMinutes() % stepMinutes
    if (remainder > 0) {
      rounded.setMinutes(rounded.getMinutes() + (stepMinutes - remainder))
    }
    return rounded
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
    const section = findSection(label, deps)
    if (!section) return false
    const text = bodyText({ ...deps, root: section })
    return hasRenderedUploadMedia(section)
      || /点击替换图片|编辑封面|编辑头图|重新上传|更换图片|已上传/.test(text)
  }

  function hasEditorBodyImage() {
    const editor = document.querySelector(
      '.ProseMirror[contenteditable="true"], [data-slate-editor="true"][contenteditable="true"], [contenteditable="true"][role="textbox"]',
    )
    return Boolean(editor?.querySelector('img, picture, [style*="background-image"]'))
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
          && (hasRenderedUploadMedia(el) || /点击替换图片|编辑头图|重新上传|更换图片|已上传/.test(text))
      })
  }

  function hasRenderedUploadMedia(root) {
    const nodes = root === document
      ? Array.from(document.querySelectorAll('*'))
      : [root, ...Array.from(root.querySelectorAll?.('*') || [])]
    return nodes.some(isRenderedUploadMediaElement)
  }

  function isRenderedUploadMediaElement(el) {
    if (!isVisible(el)) return false
    if (el.closest('[contenteditable="true"], .ProseMirror, [class*="editor"], [class*="Editor"]')) return false
    const rect = el.getBoundingClientRect()
    if (rect.width < 24 || rect.height < 24) return false
    const tagName = String(el.tagName || '').toUpperCase()
    if (tagName === 'IMG') {
      return Boolean(el.currentSrc || el.src || el.getAttribute?.('src'))
    }
    if (tagName === 'CANVAS') {
      return Number(el.width || rect.width) >= 24 && Number(el.height || rect.height) >= 24
    }
    return isSupportedBackgroundImage(getComputedStyle(el).backgroundImage)
  }

  function hasBackgroundImage(root) {
    const nodes = root === document
      ? Array.from(document.querySelectorAll('*'))
      : [root, ...Array.from(root.querySelectorAll?.('*') || [])]
    return nodes.some((el) => {
      if (!isVisible(el)) return false
      if (el.closest('[contenteditable="true"], .ProseMirror, [class*="editor"], [class*="Editor"]')) return false
      return isSupportedBackgroundImage(getComputedStyle(el).backgroundImage)
    })
  }

  function isSupportedBackgroundImage(value) {
    return /(?:^|,)\s*url\(\s*(['"]?)(?!\s*['"]?\s*\))[\s\S]+?\1\s*\)/i.test(String(value || ''))
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

  function isActiveImageEditorDialog(dialog, deps = {}) {
    if (!dialog || dialog.isConnected === false || !isVisible(dialog)) return false
    return Boolean(findActionInRoot(dialog, ['确定', '完成'], deps))
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

  async function click(el, platform, deps = {}, options = {}) {
    const clickTrustedActionOnce = requireDependency(deps.clickTrustedActionOnce, 'clickTrustedActionOnce')
    await clickTrustedActionOnce(el, { ...options, platform })
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
      submitButtons: collectSubmitButtonDiagnostics(deps),
      lastTrustedClick: global.__GEO_ENV_ACTIVE_FILL_TASK_CONTEXT?.lastTrustedClick || null,
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

  function collectSubmitButtonDiagnostics(deps = {}) {
    const normalizeText = deps.normalizeText || defaultNormalizeText
    return Array.from(document.querySelectorAll('button'))
      .filter(isVisible)
      .map((button) => ({
        text: normalizeText(button.textContent || ''),
        disabled: Boolean(button.disabled),
        ariaDisabled: button.getAttribute('aria-disabled') || '',
        ariaBusy: button.getAttribute('aria-busy') || '',
        rect: compactRect(button.getBoundingClientRect()),
      }))
      .filter((item) => item.text === '发布' || item.text === '暂存离开')
  }

  function collectUploadSectionDiagnostics(deps = {}) {
    return ['文章头图', '封面设置'].map((label) => {
      const section = findSection(label, deps)
      const text = section ? bodyText({ ...deps, root: section }).slice(0, 120) : ''
      const visibleMedia = section
        ? [section, ...Array.from(section.querySelectorAll?.('*') || [])].filter(isRenderedUploadMediaElement)
        : []
      return {
        label,
        found: Boolean(section),
        hasResult: hasSectionImage(label, deps),
        hasBackgroundImage: section ? hasBackgroundImage(section) : false,
        imageCount: section ? Array.from(section.querySelectorAll('img')).filter(isVisible).length : 0,
        canvasCount: visibleMedia.filter((el) => String(el.tagName || '').toUpperCase() === 'CANVAS').length,
        renderedMediaCount: visibleMedia.length,
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
    if (rect.width <= 0 || rect.height <= 0) return false
    let current = el
    for (let depth = 0; current && depth < 16; depth += 1) {
      const style = getComputedStyle(current)
      if (isHiddenPresentationState({
        hidden: Boolean(current.hidden),
        inert: Boolean(current.inert),
        ariaHidden: current.getAttribute?.('aria-hidden'),
        dataState: current.getAttribute?.('data-state'),
        dataVisible: current.getAttribute?.('data-visible'),
        dataShow: current.getAttribute?.('data-show'),
        display: style.display,
        visibility: style.visibility,
        contentVisibility: style.contentVisibility,
        opacity: style.opacity,
      })) {
        return false
      }
      current = current.parentElement
    }
    return true
  }

  function isHiddenPresentationState(state = {}) {
    if (state.hidden || state.inert) return true
    if (String(state.ariaHidden || '').toLowerCase() === 'true') return true
    if (/^(closed|hidden|exited)$/i.test(String(state.dataState || ''))) return true
    if (/^false$/i.test(String(state.dataVisible || ''))) return true
    if (/^false$/i.test(String(state.dataShow || ''))) return true
    if (state.display === 'none') return true
    if (state.visibility === 'hidden' || state.visibility === 'collapse') return true
    if (state.contentVisibility === 'hidden') return true
    const opacity = Number.parseFloat(state.opacity)
    return Number.isFinite(opacity) && opacity <= 0.01
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
    testing: {
      parseImageUploadProgress,
      evaluateImageUploadState,
      findFirstRecommendedMusicCard,
      findRecommendedMusicCard,
      findLocationControl,
      findLocationInput,
      findLocationListbox,
      findMusicSelectionEntry,
      firstTopicCandidate,
      readImageUploadState,
      isHiddenPresentationState,
      isRenderedUploadMediaElement,
      isRetryableFailureCode,
      isSupportedBackgroundImage,
      isVisible,
      waitForStableMusicUseTarget,
    },
  }
})(globalThis)
