import type { ExtensionMessage } from '@/types/extension'
import type { FillCommandPayload } from '@/types/extension'
import { profileForUrl } from './contentProfiles'
import { fillEditor } from './fillEditor'
import { activatePublishListener, handlePublishClick } from './publishListener'

const CAPTURE_HOSTS = new Set(['mp.toutiao.com', 'www.zhihu.com', 'zhuanlan.zhihu.com', 'creator.xiaohongshu.com'])
let captureNoticeShown = false

if (CAPTURE_HOSTS.has(window.location.hostname)) {
  void notifyCookieDomainReady()
  let captureAttempts = 0
  const captureTimer = window.setInterval(() => {
    captureAttempts += 1
    if (captureNoticeShown || captureAttempts >= 120) {
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
  const response = await chrome.runtime.sendMessage({
    type: 'GEO_COOKIE_DOMAIN_READY',
    payload: {
      host: window.location.hostname,
      href: window.location.href,
    },
  }).catch(() => null) as { result?: { status?: string, message?: string } } | null
  if (response?.result?.status === 'captured') {
    showCaptureNotice(response.result.message || '登录状态已捕获，可以回到后台继续分发。')
  }
}

function showCaptureNotice(message: string) {
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
    'background:#0f766e',
    'color:#fff',
    'font-size:14px',
    'line-height:1.5',
    'box-shadow:0 12px 32px rgba(15,23,42,.18)',
  ].join(';')
  document.documentElement.appendChild(notice)
  window.setTimeout(() => notice.remove(), 5_000)
}
