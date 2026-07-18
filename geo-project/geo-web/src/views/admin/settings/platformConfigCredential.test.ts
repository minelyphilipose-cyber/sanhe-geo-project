import { describe, expect, it } from 'vitest'
import {
  platformCredentialClearFlags,
  switchPlatformCredentialSource,
} from './platformConfigCredential'

describe('platform config credential source', () => {
  it('switches from environment reference to a page-managed key', () => {
    expect(switchPlatformCredentialSource(
      'apiKey', 'new-secret', 'env://ARK_API_KEY',
    )).toEqual({ apiKey: 'new-secret', primaryKeyRef: '' })
    expect(platformCredentialClearFlags('new-secret', '')).toEqual({
      clearApiKey: false,
      clearPrimaryKeyRef: true,
    })
  })

  it('switches from a page-managed key to an environment reference', () => {
    expect(switchPlatformCredentialSource(
      'primaryKeyRef', 'new-secret', 'env://ARK_API_KEY',
    )).toEqual({ apiKey: '', primaryKeyRef: 'env://ARK_API_KEY' })
    expect(platformCredentialClearFlags('', 'env://ARK_API_KEY')).toEqual({
      clearApiKey: true,
      clearPrimaryKeyRef: false,
    })
  })
})
