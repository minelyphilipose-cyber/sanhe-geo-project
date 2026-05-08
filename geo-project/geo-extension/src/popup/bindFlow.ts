import { EXTENSION_VERSION } from '@/shared/env'
import { extensionApi } from '@/shared/api'
import { sessionStorage } from '@/shared/storage'
import type { BindResponse, StoredSession } from '@/types/extension'

const CROCKFORD_BASE32 = /^[0-9A-HJKMNP-TV-Z]{8}$/

export interface BindInput {
  bindCode: string
}

interface BindDependencies {
  api: Pick<typeof extensionApi, 'bind' | 'revoke'>
  storage: Pick<typeof sessionStorage, 'set' | 'clear' | 'getOrCreateInstallId'>
}

const defaultDeps: BindDependencies = {
  api: extensionApi,
  storage: sessionStorage,
}

export function normalizeBindCode(value: string): string {
  return value.replace(/[\s-]/g, '').toUpperCase()
}

export function validateBindInput(input: BindInput): { bindCode: string } {
  const bindCode = normalizeBindCode(input.bindCode)

  if (!CROCKFORD_BASE32.test(bindCode)) {
    throw new Error('绑定码应为 8 位 Crockford Base32 字符。')
  }
  return { bindCode }
}

export function toStoredSession(response: BindResponse, now = new Date()): StoredSession {
  return {
    token: response.token,
    sessionId: response.sessionId,
    extensionVersion: EXTENSION_VERSION,
    expiresAt: response.expiresAt,
    boundAt: now.toISOString(),
  }
}

export async function bindExtension(input: BindInput, deps: BindDependencies = defaultDeps): Promise<StoredSession> {
  const { bindCode } = validateBindInput(input)
  const installId = await deps.storage.getOrCreateInstallId()
  const response = await deps.api.bind(bindCode, installId, EXTENSION_VERSION)
  const session = toStoredSession(response)
  await deps.storage.set(session)
  return session
}

export async function unbindExtension(session: StoredSession, deps: BindDependencies = defaultDeps): Promise<void> {
  try {
    await deps.api.revoke(session.token, session.sessionId)
  } finally {
    await deps.storage.clear()
  }
}
