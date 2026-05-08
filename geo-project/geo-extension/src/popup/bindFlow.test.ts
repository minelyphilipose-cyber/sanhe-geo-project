import { describe, expect, it, vi } from 'vitest'
import { bindExtension, normalizeBindCode, unbindExtension, validateBindInput } from './bindFlow'

describe('bind flow', () => {
  it('normalizes bind code before validation', () => {
    expect(normalizeBindCode(' abcd efgh ')).toBe('ABCDEFGH')
    expect(normalizeBindCode('abcd-efgh')).toBe('ABCDEFGH')
  })

  it('rejects invalid bind code and brand id', () => {
    expect(() => validateBindInput({ bindCode: 'abc' })).toThrow('绑定码')
  })

  it('binds extension and saves stored session', async () => {
    const set = vi.fn()
    const bind = vi.fn(async () => ({
      token: 'ext.new',
      sessionId: 12,
      expiresAt: '2026-05-14T00:00:00Z',
    }))

    const session = await bindExtension(
      { bindCode: 'abcd efgh' },
      {
        api: { bind, revoke: vi.fn() },
        storage: {
          set,
          clear: vi.fn(),
          getOrCreateInstallId: vi.fn(async () => 'install-1'),
        },
      },
    )

    expect(bind).toHaveBeenCalledWith('ABCDEFGH', 'install-1', '0.1.0')
    expect(set).toHaveBeenCalledWith(expect.objectContaining({
      token: 'ext.new',
      sessionId: 12,
      expiresAt: '2026-05-14T00:00:00Z',
    }))
    expect(session.token).toBe('ext.new')
  })

  it('revokes session and clears stored token', async () => {
    const revoke = vi.fn()
    const clear = vi.fn()

    await unbindExtension(
      {
        token: 'ext.old',
        sessionId: 9,
        extensionVersion: '0.1.0',
        expiresAt: '2026-05-14T00:00:00Z',
        boundAt: '2026-05-07T00:00:00Z',
      },
      {
        api: { bind: vi.fn(), revoke },
        storage: { set: vi.fn(), clear, getOrCreateInstallId: vi.fn() },
      },
    )

    expect(revoke).toHaveBeenCalledWith('ext.old', 9)
    expect(clear).toHaveBeenCalled()
  })

  it('clears stored token even when revoke fails', async () => {
    const revoke = vi.fn(async () => {
      throw new Error('network failed')
    })
    const clear = vi.fn()

    await expect(unbindExtension(
      {
        token: 'ext.old',
        sessionId: 9,
        extensionVersion: '0.1.0',
        expiresAt: '2026-05-14T00:00:00Z',
        boundAt: '2026-05-07T00:00:00Z',
      },
      {
        api: { bind: vi.fn(), revoke },
        storage: { set: vi.fn(), clear, getOrCreateInstallId: vi.fn() },
      },
    )).rejects.toThrow('network failed')

    expect(revoke).toHaveBeenCalledWith('ext.old', 9)
    expect(clear).toHaveBeenCalled()
  })
})
