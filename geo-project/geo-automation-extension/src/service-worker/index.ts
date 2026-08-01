const INSTALL_ID_KEY = 'geoAutomationInstallId'

async function storageGet<T>(key: string): Promise<T | undefined> {
  const result = await chrome.storage.local.get([key])
  return result[key] as T | undefined
}

async function storageSet(values: Record<string, unknown>) {
  await chrome.storage.local.set(values)
}

async function getInstallId() {
  const existing = await storageGet<string>(INSTALL_ID_KEY)
  if (existing) return existing
  const installId = crypto.randomUUID()
  await storageSet({ [INSTALL_ID_KEY]: installId })
  return installId
}

function workerBase() {
  return __AUTOMATION_EXTENSION_PROFILE__.workerBase.replace(/\/+$/, '')
}

async function fetchCurrentTask() {
  const response = await fetch(`${workerBase()}/v2/extension/current-task`)
  return response.json().catch(() => ({ ok: false, error: `HTTP_${response.status}` }))
}

async function reportRuntimeStatus(payload: Record<string, unknown>) {
  const body = {
    installId: await getInstallId(),
    extensionVersion: __AUTOMATION_EXTENSION_VERSION__,
    protocolVersion: '2',
    ...payload,
  }
  const response = await fetch(`${workerBase()}/v2/extension/runtime-status`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  return response.json().catch(() => ({ ok: false, error: `HTTP_${response.status}` }))
}

chrome.runtime.onInstalled.addListener(() => {
  void reportRuntimeStatus({ runtimeStage: 'installed' })
})

chrome.runtime.onStartup.addListener(() => {
  void reportRuntimeStatus({ runtimeStage: 'startup' })
})

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  const run = async () => {
    if (message?.type === 'GEO_AUTOMATION_GET_INSTALL_ID') {
      return { ok: true, installId: await getInstallId() }
    }
    if (message?.type === 'GEO_AUTOMATION_CURRENT_TASK') {
      return fetchCurrentTask()
    }
    if (message?.type === 'GEO_AUTOMATION_RUNTIME_STATUS') {
      return reportRuntimeStatus({
        ...(message.payload || {}),
        currentUrl: sender.tab?.url || message.payload?.currentUrl || null,
      })
    }
    return { ok: false, error: 'UNKNOWN_MESSAGE' }
  }

  run()
    .then(sendResponse)
    .catch(error => sendResponse({ ok: false, error: error instanceof Error ? error.message : String(error) }))
  return true
})
