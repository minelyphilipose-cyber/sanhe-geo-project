import type { ExtensionMessage } from '@/types/extension'
import type { FillCommandPayload } from '@/types/extension'
import { profileForUrl } from './contentProfiles'
import { fillEditor } from './fillEditor'
import { activatePublishListener, handlePublishClick } from './publishListener'

const CAPTURE_HOSTS = new Set(['mp.toutiao.com', 'www.zhihu.com', 'zhuanlan.zhihu.com', 'creator.xiaohongshu.com'])
let captureNoticeShown = false
let identityReviewShown = false

if (CAPTURE_HOSTS.has(window.location.hostname)) {
  void notifyCookieDomainReady()
  let captureAttempts = 0
  const captureTimer = window.setInterval(() => {
    captureAttempts += 1
    if (captureNoticeShown || identityReviewShown || captureAttempts >= 120) {
      window.clearInterval(captureTimer)
      return
    }
    void notifyCookieDomainReady()
  }, 5_000)
}

if (profileForUrl(window.location.href)) {
  chrome.runtime.sendMessage({
    type: 'GEO_EDITOR_READY',
    payload: {
      host: window.location.hostname,
      href: window.location.href,
    },
  })
}

chrome.runtime.onMessage.addListener((message: ExtensionMessage, _sender, sendResponse) => {
  if (message.type !== 'GEO_FILL_TASK') return false

  // Compliance requirement: the extension must never click the platform "Publish" button.
  // The operator must publish manually; this script may only fill fields and report state.
  fillEditor(message.payload as FillCommandPayload)
    .then(result => {
      if (result.ok) activatePublishListener((message.payload as FillCommandPayload).taskId)
      sendResponse(result)
    })
    .catch(error => {
      sendResponse({
        ok: false,
        errorCode: 'FILL_FAILED',
        message: error instanceof Error ? error.message : 'fill failed',
      })
    })
  return true
})

// Compliance requirement: this is a passive listener for the operator's manual publish action.
// It must never simulate user interaction or invoke platform publish controls.
document.addEventListener('click', event => {
  handlePublishClick(event.target)
}, true)

async function notifyCookieDomainReady() {
  const platformIdentity = detectPlatformIdentity()
  const response = await chrome.runtime.sendMessage({
    type: 'GEO_COOKIE_DOMAIN_READY',
    payload: {
      host: window.location.hostname,
      href: window.location.href,
      platformIdentity,
    },
  }).catch(() => null) as { result?: { status?: string, message?: string } } | null
  if (response?.result?.status === 'captured') {
    const isWarning = response.result.message?.includes('不一致')
    showCaptureNotice(response.result.message || '登录状态已捕获，可以回到后台继续分发。', isWarning ? '#b45309' : '#0f766e')
  } else if (response?.result?.status === 'identity_review_required') {
    showIdentityReviewDialog(response.result, platformIdentity)
  }
}

function showIdentityReviewDialog(
  result: { message?: string, expectedAccountName?: string | null, actualDisplayName?: string | null },
  platformIdentity: ReturnType<typeof detectPlatformIdentity>,
) {
  if (identityReviewShown) return
  identityReviewShown = true
  const overlay = document.createElement('div')
  overlay.style.cssText = [
    'position:fixed',
    'inset:0',
    'z-index:2147483647',
    'display:flex',
    'align-items:flex-start',
    'justify-content:flex-end',
    'padding:24px',
    'background:rgba(15,23,42,.18)',
    'box-sizing:border-box',
  ].join(';')

  const panel = document.createElement('div')
  panel.style.cssText = [
    'width:420px',
    'max-width:calc(100vw - 48px)',
    'border-radius:10px',
    'background:#fff7ed',
    'border:1px solid #fed7aa',
    'box-shadow:0 18px 42px rgba(15,23,42,.22)',
    'font-size:14px',
    'line-height:1.6',
    'color:#7c2d12',
    'overflow:hidden',
  ].join(';')

  const body = document.createElement('div')
  body.style.cssText = 'padding:16px 18px 12px'
  const title = document.createElement('div')
  title.textContent = '平台账号可能不一致'
  title.style.cssText = 'font-size:16px;font-weight:700;margin-bottom:8px;color:#9a3412'
  const message = document.createElement('div')
  message.textContent = result.message || '当前捕获的登录账号与系统账号不一致，请确认后继续。'

  const info = document.createElement('div')
  info.style.cssText = 'margin-top:12px;padding:10px 12px;border-radius:8px;background:#ffedd5;color:#7c2d12'
  const actual = result.actualDisplayName || ('displayName' in platformIdentity ? platformIdentity.displayName : null) || '未识别'
  const expected = result.expectedAccountName || '未配置'
  info.appendChild(infoRow('当前捕获的登录账号', actual))
  info.appendChild(infoRow('当前配置的平台账号', expected))

  const actions = document.createElement('div')
  actions.style.cssText = 'display:flex;gap:10px;justify-content:flex-end;padding:12px 18px 16px;background:#fff'
  const continueButton = button('仍要保存', '#ea580c', '#fff')
  const stopButton = button('停止并重新登录', '#fff', '#9a3412')
  stopButton.style.border = '1px solid #fed7aa'

  continueButton.addEventListener('click', () => {
    void submitIdentityDecision('continue', platformIdentity, overlay)
  })
  stopButton.addEventListener('click', () => {
    void submitIdentityDecision('stop', platformIdentity, overlay)
  })

  body.append(title, message, info)
  actions.append(stopButton, continueButton)
  panel.append(body, actions)
  overlay.append(panel)
  document.documentElement.appendChild(overlay)
}

function infoRow(label: string, value: string) {
  const row = document.createElement('div')
  row.style.cssText = 'display:flex;gap:8px;justify-content:space-between;margin:2px 0'
  const labelNode = document.createElement('span')
  labelNode.textContent = label
  labelNode.style.cssText = 'color:#9a3412'
  const valueNode = document.createElement('strong')
  valueNode.textContent = value
  valueNode.style.cssText = 'max-width:220px;text-align:right;word-break:break-all;color:#7c2d12'
  row.append(labelNode, valueNode)
  return row
}

function button(text: string, background: string, color: string) {
  const node = document.createElement('button')
  node.type = 'button'
  node.textContent = text
  node.style.cssText = [
    'height:34px',
    'padding:0 14px',
    'border-radius:6px',
    'border:0',
    `background:${background}`,
    `color:${color}`,
    'font-size:14px',
    'font-weight:600',
    'cursor:pointer',
  ].join(';')
  return node
}

async function submitIdentityDecision(
  decision: 'continue' | 'stop',
  platformIdentity: ReturnType<typeof detectPlatformIdentity>,
  overlay: HTMLElement,
) {
  const response = await chrome.runtime.sendMessage({
    type: 'GEO_COOKIE_IDENTITY_DECISION',
    payload: {
      decision,
      host: window.location.hostname,
      platformIdentity,
    },
  }).catch(() => null) as { ok?: boolean, result?: { status?: string, message?: string } } | null
  overlay.remove()
  if (response?.ok && response.result?.status === 'captured') {
    showCaptureNotice(response.result.message || '登录状态已捕获，可以回到后台继续分发。', '#b45309')
    return
  }
  showCaptureNotice(response?.result?.message || '已停止捕获流程，请处理账号后重新发起捕获。', '#b45309')
}

function detectPlatformIdentity() {
  const fromStorage = detectIdentityFromStorage()
  if (fromStorage) return fromStorage
  const fromDom = detectIdentityFromDom()
  if (fromDom) return fromDom
  return {
    status: 'unknown',
    host: window.location.hostname,
    href: window.location.href,
  }
}

function detectIdentityFromStorage() {
  const reliableIdentityKeyPattern = /(^|[_-])(user|account|profile|author|creator)(info|profile|account)?($|[_-])|^(userInfo|accountInfo|profileInfo|creatorInfo|authorInfo)$/i
  for (const storageName of ['localStorage', 'sessionStorage'] as const) {
    try {
      const storage = window[storageName]
      for (let i = 0; i < storage.length; i++) {
        const key = storage.key(i) || ''
        if (!reliableIdentityKeyPattern.test(key)) continue
        const raw = storage.getItem(key)
        if (!raw || raw.length > 20_000) continue
        const displayName = findIdentityName(parseMaybeJson(raw))
        if (displayName) {
          return identity(displayName, `storage:${key}`)
        }
      }
    } catch {
      // Some platform pages deny storage access; fall back to DOM detection.
    }
  }
  return null
}

function detectIdentityFromDom() {
  const selectors = [
    '[data-testid*="nickname"]',
    '[data-testid*="username"]',
    '[data-testid*="account-name"]',
    '[class*="user-name"]',
    '[class*="username"]',
    '[class*="account-name"]',
    '[class*="nickname"]',
    '[class*="creator"] [class*="name"]',
  ]
  for (const selector of selectors) {
    const element = document.querySelector(selector)
    const value = element instanceof HTMLImageElement ? element.alt : element?.textContent
    const displayName = normalizeDisplayName(value || '')
    if (displayName) return identity(displayName, `dom:${selector}`)
  }
  return null
}

function identity(displayName: string, source: string) {
  return {
    status: 'detected',
    displayName,
    source,
    host: window.location.hostname,
    href: window.location.href,
  }
}

function parseMaybeJson(raw: string): unknown {
  try {
    return JSON.parse(raw)
  } catch {
    return raw
  }
}

function findIdentityName(value: unknown): string | null {
  if (!value) return null
  if (typeof value === 'string') return normalizeDisplayName(value)
  if (Array.isArray(value)) {
    for (const item of value.slice(0, 20)) {
      const found = findIdentityName(item)
      if (found) return found
    }
    return null
  }
  if (typeof value !== 'object') return null
  const record = value as Record<string, unknown>
  for (const key of ['nickname', 'nickName', 'displayName', 'screenName', 'userName', 'username', 'name']) {
    const found = normalizeDisplayName(String(record[key] || ''))
    if (found) return found
  }
  for (const key of Object.keys(record).slice(0, 40)) {
    if (!/(user|account|profile|author|creator)/i.test(key)) continue
    const found = findIdentityName(record[key])
    if (found) return found
  }
  return null
}

function normalizeDisplayName(value: string) {
  const text = value.replace(/\s+/g, ' ').trim()
  if (!text || text.length > 80) return null
  if (/^(登录|注册|发布|创作中心|消息|设置|首页)$/i.test(text)) return null
  return text
}

function showCaptureNotice(message: string, background = '#0f766e') {
  if (captureNoticeShown) return
  captureNoticeShown = true
  const notice = document.createElement('div')
  notice.textContent = message
  notice.style.cssText = [
    'position:fixed',
    'right:24px',
    'top:24px',
    'z-index:2147483647',
    'max-width:360px',
    'padding:12px 16px',
    'border-radius:8px',
    `background:${background}`,
    'color:#fff',
    'font-size:14px',
    'line-height:1.5',
    'box-shadow:0 12px 32px rgba(15,23,42,.18)',
  ].join(';')
  document.documentElement.appendChild(notice)
  window.setTimeout(() => notice.remove(), 5_000)
}
