import assert from 'node:assert/strict'
import test from 'node:test'
import { stringifyBoundedDiagnostics } from '../src/diagnostics-json.js'

test('keeps diagnostics valid when the original JSON exceeds the storage limit', () => {
  const result = stringifyBoundedDiagnostics({ message: 'x'.repeat(10_000) }, 6_000)
  const parsed = JSON.parse(result)

  assert.ok(result.length <= 6_000)
  assert.equal(parsed.truncated, true)
  assert.ok(parsed.originalLength > 6_000)
  assert.ok(parsed.preview.length > 0)
})

test('handles circular and bigint diagnostics safely', () => {
  const value = { taskId: 10n }
  value.self = value
  const parsed = JSON.parse(stringifyBoundedDiagnostics(value, 1_000))

  assert.equal(parsed.taskId, '10')
  assert.equal(parsed.self, '[Circular]')
})
