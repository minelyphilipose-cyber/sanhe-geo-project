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
        async remove(key: string | string[]) {
          for (const item of Array.isArray(key) ? key : [key]) {
            store.delete(item)
          }
        },
      },
    },
  })
})

describe('sessionStorage', () => {
  it('stores and clears extension session token', async () => {
    await sessionStorage.set({
      token: 'ext.secret',
      sessionId: 7,
      operatorId: 10,
      extensionVersion: '0.1.0',
      expiresAt: '2026-05-14T00:00:00Z',
      boundAt: '2026-05-07T00:00:00Z',
    })

    await expect(sessionStorage.get()).resolves.toMatchObject({ token: 'ext.secret' })

    await sessionStorage.clear()
    await expect(sessionStorage.get()).resolves.toBeNull()
  })

  it('replaces session token on refresh and clears it on logout', async () => {
    await sessionStorage.set({
      token: 'ext.old',
      sessionId: 7,
      extensionVersion: '0.1.0',
      expiresAt: '2026-05-14T00:00:00Z',
      boundAt: '2026-05-07T00:00:00Z',
    })
    await sessionStorage.set({
      token: 'ext.new',
      sessionId: 8,
      extensionVersion: '0.1.1',
      expiresAt: '2026-05-15T00:00:00Z',
      boundAt: '2026-05-07T00:00:00Z',
    })

    await expect(sessionStorage.get()).resolves.toMatchObject({
      token: 'ext.new',
      sessionId: 8,
      expiresAt: '2026-05-15T00:00:00Z',
    })

    await sessionStorage.clear()
    await expect(sessionStorage.get()).resolves.toBeNull()
  })

  it('creates and reuses install id in chrome storage', async () => {
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('00000000-0000-4000-8000-000000000001')

    await expect(sessionStorage.getOrCreateInstallId()).resolves.toBe('00000000-0000-4000-8000-000000000001')
    await expect(sessionStorage.getOrCreateInstallId()).resolves.toBe('00000000-0000-4000-8000-000000000001')
  })

  it('keeps install id when clearing session token', async () => {
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('00000000-0000-4000-8000-000000000002')
    await expect(sessionStorage.getOrCreateInstallId()).resolves.toBe('00000000-0000-4000-8000-000000000002')
    await sessionStorage.set({
      token: 'ext.secret',
      sessionId: 7,
      extensionVersion: '0.1.0',
      expiresAt: '2026-05-14T00:00:00Z',
      boundAt: '2026-05-07T00:00:00Z',
    })

    await sessionStorage.clear()

    await expect(sessionStorage.get()).resolves.toBeNull()
    await expect(sessionStorage.getOrCreateInstallId()).resolves.toBe('00000000-0000-4000-8000-000000000002')
  })
})
