import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import vm from 'node:vm'

const requireFromWeb = createRequire(new URL('../../geo-web/package.json', import.meta.url))
const { JSDOM } = requireFromWeb('jsdom')
const source = fs.readFileSync(new URL('../platform-douyin.js', import.meta.url), 'utf8')
const serviceWorkerSource = fs.readFileSync(new URL('../service-worker.js', import.meta.url), 'utf8')
const contentScriptSource = fs.readFileSync(new URL('../content-script.js', import.meta.url), 'utf8')

function loadPlatformForTesting(overrides = {}) {
  const context = {
    URL,
    document: overrides.document || {},
    location: overrides.location
      || new URL('https://creator.douyin.com/creator-micro/content/post/article?media_type=article'),
    getComputedStyle: overrides.getComputedStyle || ((el) => el.style || {}),
  }
  context.globalThis = context
  vm.createContext(context)
  vm.runInContext(source, context)
  return context.__GEO_DOUYIN_PLATFORM__
}

function visibleElement(overrides = {}) {
  const attributes = overrides.attributes || {}
  return {
    hidden: false,
    inert: false,
    isConnected: true,
    parentElement: null,
    style: {
      display: 'block',
      visibility: 'visible',
      opacity: '1',
      contentVisibility: 'visible',
      ...overrides.style,
    },
    getBoundingClientRect: () => ({ width: 320, height: 180 }),
    getAttribute: (name) => attributes[name] ?? null,
    closest: () => null,
    ...overrides,
  }
}

test('Douyin treats opacity-zero and aria-hidden modal remnants as closed', () => {
  const platform = loadPlatformForTesting()
  const transparentPortal = visibleElement({ style: { opacity: '0' } })
  const actionInsideTransparentPortal = visibleElement({ parentElement: transparentPortal })
  const ariaHiddenPortal = visibleElement({ attributes: { 'aria-hidden': 'true' } })
  const actionInsideAriaHiddenPortal = visibleElement({ parentElement: ariaHiddenPortal })

  assert.equal(platform.testing.isVisible(actionInsideTransparentPortal), false)
  assert.equal(platform.testing.isVisible(actionInsideAriaHiddenPortal), false)
  assert.equal(platform.testing.isVisible(visibleElement()), true)
  assert.equal(platform.testing.isHiddenPresentationState({ dataState: 'closed' }), true)
})

test('Douyin recognizes blob and data image backgrounds used by upload thumbnails', () => {
  const platform = loadPlatformForTesting()
  const canvasThumbnail = visibleElement({
    tagName: 'CANVAS',
    width: 80,
    height: 80,
  })

  assert.equal(platform.testing.isSupportedBackgroundImage('url("blob:https://creator.douyin.com/abc")'), true)
  assert.equal(platform.testing.isSupportedBackgroundImage('url("data:image/png;base64,abc")'), true)
  assert.equal(platform.testing.isSupportedBackgroundImage('url("https://p3.douyinpic.com/example.webp")'), true)
  assert.equal(platform.testing.isSupportedBackgroundImage('none'), false)
  assert.equal(platform.testing.isSupportedBackgroundImage('linear-gradient(#fff, #000)'), false)
  assert.equal(platform.testing.isRenderedUploadMediaElement(canvasThumbnail), true)
})

test('Douyin image editor close timeout is explicitly retryable end to end', () => {
  const platform = loadPlatformForTesting()
  const code = platform.classifyFailureCode(
    'DOUYIN_IMAGE_EDITOR_CLOSE_TIMEOUT：抖音图片编辑确认后弹窗未关闭',
    'douyin',
  )

  assert.equal(code, 'DOUYIN_IMAGE_EDITOR_CLOSE_TIMEOUT')
  assert.equal(platform.testing.isRetryableFailureCode(code), true)
  assert.match(
    serviceWorkerSource,
    /__GEO_DOUYIN_PLATFORM__\?\.isRetryableFailureCode\?\.\(code\)/,
  )
})

test('Douyin waits while the expected image batch is still uploading', () => {
  const platform = loadPlatformForTesting()
  const state = platform.testing.parseImageUploadProgress(
    'geo-cover.jpg 0% 0/6 取消上传 添加作品标题 0/20 添加作品描述 0/1000',
    6,
  )

  assert.equal(state.current, 0)
  assert.equal(state.total, 6)
  assert.equal(state.percent, 0)
  assert.equal(state.pending, true)
  assert.equal(state.failed, false)
})

test('Douyin does not mistake assistant detection percentage for image upload progress', () => {
  const platform = loadPlatformForTesting()
  const state = platform.testing.parseImageUploadProgress(
    '已添加6张图片 继续添加 发文助手 检测中5%',
    6,
  )

  assert.equal(state.current, null)
  assert.equal(state.total, null)
  assert.equal(state.pending, false)
})

test('Douyin requires confirmed thumbnails or added-image text before filling details', () => {
  const platform = loadPlatformForTesting()
  const progressOnly = platform.testing.evaluateImageUploadState(
    '6/6 添加作品标题 添加作品描述',
    6,
    0,
  )
  const confirmed = platform.testing.evaluateImageUploadState(
    '已添加6张图片 继续添加',
    6,
    0,
  )

  assert.equal(progressOnly.uploadedCount, 0)
  assert.equal(progressOnly.ready, false)
  assert.equal(confirmed.uploadedCount, 6)
  assert.equal(confirmed.ready, true)
})

test('Douyin image upload timeout is retryable and the editor remains in waiting stage', () => {
  const platform = loadPlatformForTesting()
  const code = platform.classifyFailureCode(
    'DOUYIN_IMAGE_UPLOAD_TIMEOUT：等待抖音图片上传完成超时',
    'douyin',
  )

  assert.equal(platform.testing.isRetryableFailureCode(code), true)
  assert.match(source, /waiting_image_editor[\s\S]+waitForImageUploadCompletion[\s\S]+filling_title/)
  assert.match(source, /DOUYIN_IMAGE_UPLOAD_TIMEOUT[\s\S]+stage = [\s\S]+upload_timeout/)
  assert.match(
    serviceWorkerSource,
    /waitForContentScript[\s\S]+saveDouyinImageTextTaskState\(taskId, \{ stage: 'waiting_image_editor' \}\)/,
  )
  assert.match(
    serviceWorkerSource,
    /completion\?\.complete !== true[\s\S]+completion\?\.confirmedCount\) !== expectedImageCount/,
  )
})

test('Douyin marks the final click as post-submission before waiting for works-list verification', () => {
  assert.match(contentScriptSource, /DOUYIN_PUBLISH_OPTIONS_ADAPTER[\s\S]+updateStage: updateActiveFillStage/)
  assert.match(
    source,
    /notifyStage\(deps, 'submitting_publish'\)[\s\S]+await click\(publishButton[\s\S]+notifyStage\(deps, 'verifying_publish_result'\)[\s\S]+verifyPublishSubmission/,
  )
  assert.match(serviceWorkerSource, /douyin: 'DOUYIN_PUBLISH_NOT_CONFIRMED'/)
})

test('Douyin image-text flow persists the irreversible boundary before one publish click', () => {
  assert.match(contentScriptSource, /contentKind[\s\S]+image_text[\s\S]+fillImageText/)
  assert.match(
    source,
    /await persist\(taskId, \{[\s\S]+stage: 'submitting_publish'[\s\S]+publishClicked: true[\s\S]+await click\(publishButton, fillProfile\.platform, deps, \{[\s\S]+trustedOnly: true[\s\S]+抖音图文发布/,
  )
  assert.match(
    contentScriptSource,
    /if \(options\.trustedOnly\) \{[\s\S]+await requestTrustedClick\(target, options\)[\s\S]+return/,
  )
  assert.match(serviceWorkerSource, /DOUYIN_PUBLISH_ALREADY_SUBMITTED[\s\S]+禁止重复发布/)
})

test('Douyin image-text uses first semantic topic, collection-v2 location and first music card', () => {
  assert.match(source, /\.mention-suggest-mount-dom/)
  assert.match(source, /\[class\*="collection-v2-"\]/)
  assert.match(source, /#douyin_creator_pc_anchor_jump/)
  assert.match(source, /\[class\*="anchor-component-"\] \.semi-select-filterable/)
  assert.match(
    source,
    /await click\(control, platform, deps\)[\s\S]+findLocationInput\(control\)[\s\S]+typeTrustedText\(input, query/,
  )
  assert.match(source, /\.semi-select-input input\.semi-input\[type="text"\]/)
  assert.match(
    source,
    /await click\(collection, platform, deps\)[\s\S]+readLocationSelection\(control\)/,
  )
  assert.match(source, /findLocationListbox/)
  assert.match(source, /findLocationInput/)
  assert.match(source, /typeTrustedText\(input, query/)
  assert.match(source, /findMusicSelectionEntry\(\)/)
  assert.match(source, /#DCPF \[class\*="container-right-"\] span\[class\*="action-"\]/)
  assert.doesNotMatch(source, /const entry = findExactVisibleText\('选择音乐'\)/)
  assert.match(
    source,
    /const firstMusic = await waitForCondition\([\s\S]+findFirstRecommendedMusicCard\(drawer\)[\s\S]+12_000/,
  )
  assert.match(
    source,
    /await click\(entry, platform, deps, \{[\s\S]+trustedOnly: true[\s\S]+抖音选择音乐/,
  )
  assert.match(
    source,
    /await click\(useButton, platform, deps, \{[\s\S]+trustedOnly: true[\s\S]+抖音使用第一首音乐/,
  )
  assert.match(
    contentScriptSource,
    /normalizePlatform\(options\.platform\) === 'douyin'[\s\S]+if \(options\.trustedOnly\)[\s\S]+await requestTrustedClick\(target, options\)[\s\S]+return/,
  )
  assert.match(source, /drawer\.querySelectorAll\('\[class\*="card-container-left-"\]'\)/)
  assert.match(
    source,
    /await click\(left, platform, deps, \{[\s\S]+trustedOnly: true[\s\S]+抖音第一首推荐音乐/,
  )
  assert.match(source, /defaultNormalizeText\(button\.textContent \|\| ''\) === '使用'/)
})

test('Douyin only accepts the server-composed final description from task options', () => {
  assert.match(source, /firstText\(payload\.description, payload\.finalDescription\)/)
  assert.match(serviceWorkerSource, /'description',[\s\S]+'descriptionBase'/)
  assert.match(serviceWorkerSource, /payload\.content = payload\.description \|\| payload\.descriptionBase/)
  assert.doesNotMatch(source, /appendTopicOnce/)
  assert.doesNotMatch(source, /hasFinalTopicLine/)
})

test('Douyin fills the server-composed description once and waits for the automatic topic popup', () => {
  assert.match(
    source,
    /await replaceEditableText\(editor, finalDescription, delay\)[\s\S]+editorContainsText\(editor, finalDescription\)[\s\S]+stage: 'selecting_topic'[\s\S]+\.mention-suggest-mount-dom/,
  )
  assert.match(
    source,
    /async function replaceEditableText[\s\S]+dispatchPasteIntoEditable\(editor, text\)[\s\S]+execCommand\('insertHTML', false, plainTextToEditorHtml\(text\)\)/,
  )
  assert.doesNotMatch(source, /execCommand\('insertParagraph', false\)/)
  assert.match(
    source,
    /await click\(candidate[\s\S]+!popup\.isConnected \|\| !isVisible\(popup\) \|\| hasTopicNode\(editor\)/,
  )
  assert.doesNotMatch(source, /fillDescriptionWithTopic/)
  assert.doesNotMatch(source, /placeCaretAtLastEditableTextLeaf/)
  assert.doesNotMatch(source, /typeTrustedText\(editor/)
})

test('Douyin manage verification requires matching image count and does not trust success toast alone', () => {
  assert.match(serviceWorkerSource, /expectedImageCount[\s\S]+new RegExp\(`\$\{expectedImageCount\}\\\\s\*张`\)/)
  assert.match(
    serviceWorkerSource,
    /\[class\*="info-title-text-"\][\s\S]+closest\('\[class\*="video-card-content-"\]'\)[\s\S]+semanticCardSet/,
  )
  assert.match(serviceWorkerSource, /item\.semanticCard \|\| item\.rect\.width <= 1600/)
  assert.doesNotMatch(serviceWorkerSource, /if \(isManagePage && explicitSuccess\)/)
  assert.match(serviceWorkerSource, /审核中\|已发布\|未通过/)
  assert.match(serviceWorkerSource, /isRecordInTaskWindow\(item\.text, taskStartedAt\)/)
  assert.match(
    serviceWorkerSource,
    /const immediateImageText[\s\S]+publishMode[\s\S]+scheduledAt: immediateImageText[\s\S]+\? ''/,
  )
})

test('Douyin refreshes the redirected works list before matching the submitted image-text work', () => {
  assert.match(
    serviceWorkerSource,
    /recoverDouyinPublishAfterMessageChannelClosed[\s\S]+isDouyinWorksManageUrl\(current\.url\)[\s\S]+refreshAndInspectPlatformWorksList\([\s\S]+inspectDouyinManageTab/,
  )
  assert.match(
    serviceWorkerSource,
    /async function refreshAndInspectPlatformWorksList[\s\S]+await reloadPlatformWorksList\(tabId,[\s\S]+latest = await inspector\(tabId, context, refreshAttempt\)/,
  )
  assert.doesNotMatch(
    serviceWorkerSource,
    /recoverDouyinPublishAfterMessageChannelClosed[\s\S]+latest = await inspectDouyinManageTab\(recoveryTabId, context, attempt\)[\s\S]+chrome\.tabs\.reload/,
  )
})

test('Douyin image-text obtains a verified publish result before backend acknowledgement', () => {
  assert.match(
    serviceWorkerSource,
    /fillResult = await ensureVerifiedDouyinImageTextPublishResult\([\s\S]+await apiRequest\(taskApiConfig, `\/api\/v1\/extension\/tasks\/\$\{task\.taskId\}\/ack`/,
  )
  assert.match(
    serviceWorkerSource,
    /async function ensureVerifiedDouyinImageTextPublishResult[\s\S]+verification\.verified === true[\s\S]+publishOptions\.published !== true[\s\S]+recoverDouyinPublishAfterMessageChannelClosed/,
  )
  assert.match(
    serviceWorkerSource,
    /DOUYIN_PUBLISH_NOT_CONFIRMED：抖音图文已越过发布点击边界，但尚未取得作品管理页确认凭证/,
  )
})

test('Douyin image-text capability is advertised for runtime claim gating', () => {
  assert.match(serviceWorkerSource, /douyinImageText:\s*true/)
})

test('Douyin image-text state is bounded, serialized and retains submitted boundaries longer', () => {
  assert.match(serviceWorkerSource, /DOUYIN_IMAGE_TEXT_ACTIVE_STATE_TTL_MS = 7 \* 24 \* 60 \* 60 \* 1000/)
  assert.match(serviceWorkerSource, /DOUYIN_IMAGE_TEXT_SUBMITTED_STATE_TTL_MS = 90 \* 24 \* 60 \* 60 \* 1000/)
  assert.match(serviceWorkerSource, /DOUYIN_IMAGE_TEXT_STATE_MAX_ENTRIES = 200/)
  assert.match(
    serviceWorkerSource,
    /function pruneDouyinImageTextTaskStates[\s\S]+state\.publishClicked === true[\s\S]+slice\(0, DOUYIN_IMAGE_TEXT_STATE_MAX_ENTRIES\)/,
  )
  assert.match(
    serviceWorkerSource,
    /douyinImageTextStateWriteChain[\s\S]+stageTimingsMs[\s\S]+stageStartedAt/,
  )
})

test('Douyin captured DOM fixture resolves the exact first topic, location and music controls', () => {
  const html = fs.readFileSync(
    new URL('./fixtures/douyin-image-text-controls.html', import.meta.url),
    'utf8',
  )
  const dom = new JSDOM(html, {
    url: 'https://creator.douyin.com/creator-micro/content/post/image?media_type=image',
  })
  Object.defineProperty(dom.window.HTMLElement.prototype, 'getBoundingClientRect', {
    configurable: true,
    value() {
      if (this.hidden || this.getAttribute('aria-hidden') === 'true') {
        return { width: 0, height: 0, left: 0, top: 0, right: 0, bottom: 0 }
      }
      return { width: 320, height: 56, left: 0, top: 0, right: 320, bottom: 56 }
    },
  })
  const platform = loadPlatformForTesting({
    document: dom.window.document,
    location: dom.window.location,
    getComputedStyle: dom.window.getComputedStyle.bind(dom.window),
  })

  const popup = dom.window.document.querySelector('.mention-suggest-mount-dom')
  const topic = platform.testing.firstTopicCandidate(popup)
  assert.equal(
    topic.querySelector('[class*="tag-hash-view-name-"]').textContent.trim(),
    '阜阳全屋定制工厂',
  )

  const locationControl = platform.testing.findLocationControl()
  assert.ok(locationControl)
  assert.equal(platform.testing.findLocationInput(locationControl).tagName, 'INPUT')
  const listbox = platform.testing.findLocationListbox()
  assert.equal(
    listbox.querySelector('[role="option"] [class*="name-"]').textContent.trim(),
    '管仲老街',
  )

  const musicEntry = platform.testing.findMusicSelectionEntry()
  assert.equal(musicEntry.className, 'action-Q1y01k')
  const drawer = dom.window.document.querySelector('.semi-sidesheet')
  const firstMusic = platform.testing.findFirstRecommendedMusicCard(drawer)
  assert.equal(
    firstMusic.card.querySelector('[class*="song-name-"]').textContent.trim(),
    '如果你也刚好抬头看树（主歌）',
  )
  assert.equal(firstMusic.card.querySelector('button').textContent.trim(), '使用')
  assert.equal(
    dom.window.document.querySelector('[data-douyin-unpublished-draft]').textContent
      .replace(/\s+/g, ' ')
      .trim(),
    '你还有上次未发布的图文，是否继续编辑？ 继续编辑 放弃',
  )
})
