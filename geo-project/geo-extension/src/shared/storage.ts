import type { StoredSession } from '@/types/extension'

const SESSION_KEY = 'geo.extension.session'

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
}
