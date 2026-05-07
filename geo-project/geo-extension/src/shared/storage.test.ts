import { describe, expect, it, beforeEach, vi } from 'vitest'
import { sessionStorage } from './storage'

const store = new Map<string, unknown>()

beforeEach(() => {
  store.clear()
  vi.stubGlobal('chrome', {
    storage: {
      local: {
        async get(key: string) {
          return { [key]: store.get(key) }
        },
        async set(value: Record<string, unknown>) {
          Object.entries(value).forEach(([key, stored]) => store.set(key, stored))
        },
        async remove(key: string) {
          store.delete(key)
        },
      },
    },
  })
})

describe('sessionStorage', () => {
  it('stores and clears extension session token', async () => {
    await sessionStorage.set({
      token: 'ext.secret',
      operatorId: 10,
      extensionVersion: '0.1.0',
      boundAt: '2026-05-07T00:00:00Z',
    })

    await expect(sessionStorage.get()).resolves.toMatchObject({ token: 'ext.secret' })

    await sessionStorage.clear()
    await expect(sessionStorage.get()).resolves.toBeNull()
  })
})
