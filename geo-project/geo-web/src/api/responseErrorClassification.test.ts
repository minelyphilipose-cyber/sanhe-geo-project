import { describe, expect, it } from 'vitest'
import { isServerErrorCode } from './responseErrorClassification'

describe('response error classification', () => {
  it('only treats standard 5xx response codes as server errors', () => {
    expect(isServerErrorCode(500)).toBe(true)
    expect(isServerErrorCode(599)).toBe(true)
    expect(isServerErrorCode(400)).toBe(false)
  })

  it('keeps high-valued business codes visible to the user', () => {
    expect(isServerErrorCode(70040)).toBe(false)
    expect(isServerErrorCode(-1)).toBe(false)
    expect(isServerErrorCode('500')).toBe(false)
  })
})
