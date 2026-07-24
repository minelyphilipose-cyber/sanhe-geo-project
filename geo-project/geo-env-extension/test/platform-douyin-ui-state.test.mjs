import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const source = fs.readFileSync(new URL('../platform-douyin.js', import.meta.url), 'utf8')
const serviceWorkerSource = fs.readFileSync(new URL('../service-worker.js', import.meta.url), 'utf8')
const contentScriptSource = fs.readFileSync(new URL('../content-script.js', import.meta.url), 'utf8')

function loadPlatformForTesting() {
  const context = {
    URL,
    document: {},
    location: new URL('https://creator.douyin.com/creator-micro/content/post/article?media_type=article'),
    getComputedStyle: (el) => el.style || {},
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

test('Douyin marks the final click as post-submission before waiting for works-list verification', () => {
  assert.match(contentScriptSource, /DOUYIN_PUBLISH_OPTIONS_ADAPTER[\s\S]+updateStage: updateActiveFillStage/)
  assert.match(
    source,
    /notifyStage\(deps, 'submitting_publish'\)[\s\S]+await click\(publishButton[\s\S]+notifyStage\(deps, 'verifying_publish_result'\)[\s\S]+verifyPublishSubmission/,
  )
  assert.match(serviceWorkerSource, /douyin: 'DOUYIN_PUBLISH_NOT_CONFIRMED'/)
})
