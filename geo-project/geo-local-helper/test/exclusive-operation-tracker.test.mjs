import assert from 'node:assert/strict'
import test from 'node:test'
import { ExclusiveOperationTracker } from '../src/exclusive-operation-tracker.js'

function deferred() {
  let resolve
  const promise = new Promise((next) => {
    resolve = next
  })
  return { promise, resolve }
}

test('keeps exclusivity after a waiter times out until the underlying operation settles', async () => {
  const tracker = new ExclusiveOperationTracker()
  const pending = deferred()
  let starts = 0
  const first = tracker.start('profile-a', () => {
    starts += 1
    return pending.promise
  })

  await assert.rejects(
    tracker.wait('profile-a', 5, 'browser observation'),
    /browser observation timed out/,
  )
  assert.equal(tracker.has('profile-a'), true)

  const second = tracker.start('profile-a', () => {
    starts += 1
    return Promise.resolve('overlap')
  })
  assert.equal(second, first)
  assert.equal(starts, 1)

  pending.resolve('finished')
  assert.equal(await first, 'finished')
  assert.equal(tracker.has('profile-a'), false)
})
