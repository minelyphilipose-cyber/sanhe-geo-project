import type { ExtensionMessage } from '@/types/extension'

const CAPTURE_HOSTS = new Set(['mp.toutiao.com', 'www.zhihu.com'])

if (CAPTURE_HOSTS.has(window.location.hostname)) {
  chrome.runtime.sendMessage({
    type: 'GEO_COOKIE_DOMAIN_READY',
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
  sendResponse({ ok: true })
  return true
})
