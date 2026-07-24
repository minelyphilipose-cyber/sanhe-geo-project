import { onScopeDispose, ref } from 'vue'
import {
  getMobileDashboardWechatConfig,
  reportMobileDashboardWechatError,
} from '@/api/mobileDashboard'
import type {
  MobileDashboardWechatErrorCode,
  MobileDashboardWechatErrorStage,
  MobileDashboardWechatShareContent,
} from '@/types/mobileDashboard'
import {
  classifyWechatSdkError,
  isWechatBrowser,
  resolveWechatSignatureUrl,
  stripUrlFragment,
} from '@/utils/wechatShare'

interface WechatSdkResult {
  errMsg?: string
  checkResult?: Record<string, boolean | string>
}

interface WechatShareOptions {
  title: string
  desc?: string
  link: string
  imgUrl: string
  success?: () => void
  fail?: (result: WechatSdkResult) => void
}

interface WechatJsSdk {
  config(options: {
    debug: boolean
    appId: string
    timestamp: number
    nonceStr: string
    signature: string
    jsApiList: string[]
  }): void
  ready(callback: () => void): void
  error(callback: (result: WechatSdkResult) => void): void
  checkJsApi(options: {
    jsApiList: string[]
    success: (result: WechatSdkResult) => void
    fail?: (result: WechatSdkResult) => void
  }): void
  updateAppMessageShareData(options: WechatShareOptions): void
  updateTimelineShareData(options: WechatShareOptions): void
}

declare global {
  interface Window {
    wx?: WechatJsSdk
  }
}

const SDK_URLS = [
  'https://res.wx.qq.com/open/js/jweixin-1.6.0.js',
  'https://res2.wx.qq.com/open/js/jweixin-1.6.0.js',
]
const CONFIG_TIMEOUT_MS = 10_000
const API_TIMEOUT_MS = 5_000
const CONFIG_REQUEST_RETRY_DELAYS_MS = [500, 1_500]
let sdkPromise: Promise<WechatJsSdk> | null = null

function loadScript(url: string) {
  return new Promise<void>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${url}"]`)
    if (existing) {
      if (window.wx) {
        resolve()
        return
      }
      existing.addEventListener('load', () => resolve(), { once: true })
      existing.addEventListener('error', () => reject(new Error('script_load_failed')), { once: true })
      return
    }

    const script = document.createElement('script')
    script.src = url
    script.async = true
    script.referrerPolicy = 'no-referrer'
    script.onload = () => resolve()
    script.onerror = () => {
      script.remove()
      reject(new Error('script_load_failed'))
    }
    document.head.appendChild(script)
  })
}

async function loadWechatSdk() {
  if (window.wx) return window.wx
  if (sdkPromise) return sdkPromise

  sdkPromise = (async () => {
    let lastError: unknown
    for (const url of SDK_URLS) {
      try {
        await withTimeout(loadScript(url), API_TIMEOUT_MS)
        if (window.wx) return window.wx
      } catch (error) {
        lastError = error
      }
    }
    throw lastError instanceof Error ? lastError : new Error('script_load_failed')
  })().catch((error) => {
    sdkPromise = null
    throw error
  })
  return sdkPromise
}

function withTimeout<T>(promise: Promise<T>, timeoutMs: number) {
  return new Promise<T>((resolve, reject) => {
    const timer = window.setTimeout(() => reject(new Error('timeout')), timeoutMs)
    promise.then(
      (value) => {
        window.clearTimeout(timer)
        resolve(value)
      },
      (error) => {
        window.clearTimeout(timer)
        reject(error)
      },
    )
  })
}

function isApiAvailable(value: boolean | string | undefined) {
  return value === true || value === 'true'
}

function shouldRetryConfigRequest(error: unknown) {
  const status = typeof error === 'object' && error !== null && 'status' in error
    ? Number((error as { status?: number }).status)
    : undefined
  return status === undefined || [429, 502, 503, 504].includes(status)
}

function waitForRetry(delayMs: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException('Aborted', 'AbortError'))
      return
    }
    let timer = 0
    const onAbort = () => {
      window.clearTimeout(timer)
      reject(new DOMException('Aborted', 'AbortError'))
    }
    timer = window.setTimeout(() => {
      signal.removeEventListener('abort', onAbort)
      resolve()
    }, delayMs)
    signal.addEventListener('abort', onAbort, { once: true })
  })
}

async function fetchWechatConfigWithRetry(
  token: string,
  signatureUrl: string,
  signal: AbortSignal,
) {
  for (let attempt = 0; ; attempt += 1) {
    try {
      return await getMobileDashboardWechatConfig(token, signatureUrl, signal)
    } catch (error) {
      const retryDelay = CONFIG_REQUEST_RETRY_DELAYS_MS[attempt]
      if (signal.aborted || retryDelay === undefined || !shouldRetryConfigRequest(error)) {
        throw error
      }
      await waitForRetry(retryDelay, signal)
    }
  }
}

function configureSdk(
  sdk: WechatJsSdk,
  config: {
    appId: string
    timestamp: number
    nonceStr: string
    signature: string
  },
  generation: number,
  isCurrent: () => boolean,
) {
  return withTimeout(new Promise<void>((resolve, reject) => {
    sdk.ready(() => {
      if (isCurrent() && generation > 0) resolve()
    })
    sdk.error((result) => {
      if (isCurrent() && generation > 0) {
        reject(new Error(result.errMsg || 'sdk_error'))
      }
    })
    sdk.config({
      debug: false,
      ...config,
      jsApiList: [
        'checkJsApi',
        'updateAppMessageShareData',
        'updateTimelineShareData',
      ],
    })
  }), CONFIG_TIMEOUT_MS)
}

function checkShareApi(sdk: WechatJsSdk) {
  return withTimeout(new Promise<boolean>((resolve, reject) => {
    sdk.checkJsApi({
      jsApiList: ['updateAppMessageShareData', 'updateTimelineShareData'],
      success: (result) => {
        resolve(isApiAvailable(result.checkResult?.updateAppMessageShareData))
      },
      fail: (result) => reject(new Error(result.errMsg || 'api_unavailable')),
    })
  }), API_TIMEOUT_MS)
}

function updateFriendShare(sdk: WechatJsSdk, content: MobileDashboardWechatShareContent) {
  return withTimeout(new Promise<void>((resolve, reject) => {
    sdk.updateAppMessageShareData({
      title: content.title,
      desc: content.description,
      link: content.link,
      imgUrl: content.imageUrl,
      success: resolve,
      fail: (result) => reject(new Error(result.errMsg || 'sdk_error')),
    })
  }), API_TIMEOUT_MS)
}

function updateTimelineShare(sdk: WechatJsSdk, content: MobileDashboardWechatShareContent) {
  return withTimeout(new Promise<void>((resolve, reject) => {
    sdk.updateTimelineShareData({
      title: content.title,
      link: content.link,
      imgUrl: content.imageUrl,
      success: resolve,
      fail: (result) => reject(new Error(result.errMsg || 'sdk_error')),
    })
  }), API_TIMEOUT_MS)
}

export function useWechatShare(options: {
  sessionToken: () => string
  shareCode: () => string
}) {
  const isReady = ref(false)
  const guideVisible = ref(false)
  const entryUrl = stripUrlFragment(window.location.href)
  let generation = 0
  let configuredSignatureUrl = ''
  let abortController: AbortController | null = null

  const reportError = async (
    stage: MobileDashboardWechatErrorStage,
    code: MobileDashboardWechatErrorCode,
  ) => {
    const token = options.sessionToken()
    if (!token) return
    try {
      await reportMobileDashboardWechatError(token, stage, code)
    } catch {
      // Error reporting is best-effort and must never affect dashboard access.
    }
  }

  const configure = async () => {
    if (!isWechatBrowser()) return false
    const token = options.sessionToken()
    if (!token) return false

    const signatureUrl = resolveWechatSignatureUrl(
      window.location.href,
      entryUrl,
    )
    if (configuredSignatureUrl === signatureUrl && isReady.value) return true

    generation += 1
    const currentGeneration = generation
    abortController?.abort()
    const controller = new AbortController()
    abortController = controller
    const isCurrent = () => currentGeneration === generation
    isReady.value = false

    try {
      const response = await fetchWechatConfigWithRetry(
        token,
        signatureUrl,
        controller.signal,
      )
      if (!isCurrent()) return false
      const config = response.data.data
      if (!config?.enabled) {
        configuredSignatureUrl = ''
        return false
      }
      if (!config.appId || !config.timestamp || !config.nonceStr || !config.signature || !config.share) {
        await reportError('config', 'sdk_error')
        return false
      }

      let sdk: WechatJsSdk
      try {
        sdk = await loadWechatSdk()
      } catch (error) {
        const code = error instanceof Error && error.message === 'timeout'
          ? 'timeout'
          : 'script_load_failed'
        await reportError('script_load', code)
        return false
      }
      if (!isCurrent()) return false

      try {
        await configureSdk(sdk, {
          appId: config.appId,
          timestamp: config.timestamp,
          nonceStr: config.nonceStr,
          signature: config.signature,
        }, currentGeneration, isCurrent)
      } catch (error) {
        await reportError(
          'config',
          classifyWechatSdkError(error instanceof Error ? error.message : undefined),
        )
        return false
      }
      if (!isCurrent()) return false

      let friendShareAvailable = false
      try {
        friendShareAvailable = await checkShareApi(sdk)
      } catch (error) {
        await reportError(
          'check_api',
          classifyWechatSdkError(error instanceof Error ? error.message : undefined),
        )
        return false
      }
      if (!friendShareAvailable || !isCurrent()) {
        await reportError('check_api', 'api_unavailable')
        return false
      }

      try {
        await updateFriendShare(sdk, config.share)
      } catch (error) {
        await reportError(
          'share_data',
          classifyWechatSdkError(error instanceof Error ? error.message : undefined),
        )
        return false
      }

      try {
        await updateTimelineShare(sdk, config.share)
      } catch {
        await reportError('share_data', 'api_unavailable')
      }
      if (!isCurrent()) return false

      configuredSignatureUrl = signatureUrl
      isReady.value = true
      const guideKey = `geo_wechat_share_guide_seen:${options.shareCode()}`
      guideVisible.value = sessionStorage.getItem(guideKey) !== '1'
      return true
    } catch (error) {
      if (controller.signal.aborted || !isCurrent()) return false
      await reportError(
        'config',
        shouldRetryConfigRequest(error) ? 'timeout' : 'sdk_error',
      )
      return false
    }
  }

  const dismissGuide = () => {
    guideVisible.value = false
    sessionStorage.setItem(
      `geo_wechat_share_guide_seen:${options.shareCode()}`,
      '1',
    )
  }

  onScopeDispose(() => {
    generation += 1
    abortController?.abort()
  })

  return {
    isWechat: isWechatBrowser(),
    isReady,
    guideVisible,
    configure,
    dismissGuide,
  }
}
