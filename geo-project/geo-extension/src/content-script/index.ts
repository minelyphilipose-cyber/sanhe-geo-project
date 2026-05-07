import type { ExtensionMessage } from '@/types/extension'

chrome.runtime.onMessage.addListener((message: ExtensionMessage, _sender, sendResponse) => {
  if (message.type !== 'GEO_FILL_TASK') return false

  // Compliance requirement: the extension must never click the platform "Publish" button.
  // The operator must publish manually; this script may only fill fields and report state.
  sendResponse({ ok: true })
  return true
})
