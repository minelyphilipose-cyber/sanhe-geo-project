import type { ExtensionMessage } from '@/types/extension'
import type { FillCommandPayload } from '@/types/extension'
import { profileForUrl } from '@/shared/fillProfiles'
import { fillEditor } from './fillEditor'
import { activatePublishListener, handlePublishClick } from './publishListener'

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
  const result = fillEditor(message.payload as FillCommandPayload)
  if (result.ok) activatePublishListener((message.payload as FillCommandPayload).taskId)
  sendResponse(result)
  return true
})

// Compliance requirement: this is a passive listener for the operator's manual publish action.
// It must never simulate user interaction or invoke platform publish controls.
document.addEventListener('click', event => {
  handlePublishClick(event.target)
}, true)
