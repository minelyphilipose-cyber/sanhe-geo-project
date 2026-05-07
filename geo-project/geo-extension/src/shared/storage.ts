import type { StoredSession } from '@/types/extension'

const SESSION_KEY = 'geo.extension.session'
const INSTALL_ID_KEY = 'geo.extension.installId'

// SECURITY NOTE: chrome.storage.local is isolated by the browser process and is the
// only approved token store for this extension. A local malware process can still read
// the browser profile LevelDB files; this is an accepted host-compromise limitation.

function storageArea(): chrome.storage.StorageArea | null {
  return globalThis.chrome?.storage?.local ?? null
}

export const sessionStorage = {
  async get(): Promise<StoredSession | null> {
    const area = storageArea()
    if (!area) return null
    const result = await area.get(SESSION_KEY)
    return (result[SESSION_KEY] as StoredSession | undefined) ?? null
  },

  async set(session: StoredSession): Promise<void> {
    const area = storageArea()
    if (!area) throw new Error('chrome.storage.local is unavailable')
    await area.set({ [SESSION_KEY]: session })
  },

  async clear(): Promise<void> {
    const area = storageArea()
    if (area) await area.remove(SESSION_KEY)
  },

  async getOrCreateInstallId(): Promise<string> {
    const area = storageArea()
    if (!area) throw new Error('chrome.storage.local is unavailable')

    const result = await area.get(INSTALL_ID_KEY)
    const existing = result[INSTALL_ID_KEY] as string | undefined
    if (existing) return existing

    const installId = globalThis.crypto?.randomUUID?.() ?? randomFallbackId()
    await area.set({ [INSTALL_ID_KEY]: installId })
    return installId
  },
}

function randomFallbackId(): string {
  return `install-${Date.now()}-${Math.random().toString(36).slice(2)}`
}
