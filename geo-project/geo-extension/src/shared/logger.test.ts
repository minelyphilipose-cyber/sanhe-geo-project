import { describe, expect, it } from 'vitest'
import { sanitizeForLog } from './logger'

describe('sanitizeForLog', () => {
  it('redacts tokens and sensitive nested fields', () => {
    const result = sanitizeForLog({
      nested: {
        message: 'use ext.abc_123 and ft.payload-signature now',
        cookiesJson: '[{"name":"sid","value":"secret"}]',
        values: [
          {
            fillToken: 'ft.hidden',
            extensionToken: 'ext.hidden',
          },
        ],
      },
    })

    expect(JSON.stringify(result)).not.toContain('ext.abc_123')
    expect(JSON.stringify(result)).not.toContain('ft.payload-signature')
    expect(JSON.stringify(result)).not.toContain('secret')
    expect(result).toEqual({
      nested: {
        message: 'use [redacted-token] and [redacted-token] now',
        cookiesJson: '[redacted]',
        values: [
          {
            fillToken: '[redacted]',
            extensionToken: '[redacted]',
          },
        ],
      },
    })
  })
})
