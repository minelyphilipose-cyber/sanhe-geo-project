import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

test('signed schedule poll hashes the received body before authentication and rejects before claiming', async () => {
  const source = readFileSync(new URL('../src/server.js', import.meta.url), 'utf8')
  const handler = source.match(/async function handleSchedulePollOnce[\s\S]*?\n}\n/)[0]
  let claims = 0
  const run = new Function('readJson', 'requireHelperAccess', 'claimAndCheckPublishResult', 'claimAndLaunchScheduledTask', 'sendJson', `return (${handler})`)(
    async req => { req.rawBody = '{"platform":"sohu"}'; return { platform: 'sohu' } },
    async req => { assert.equal(req.rawBody, '{"platform":"sohu"}'); if (!req.authorized) throw new Error('invalid signature') },
    async () => { claims++; return { claimed: false } },
    async () => { claims++; return { claimed: true } },
    () => {},
  )
  await assert.rejects(run({ method: 'POST', url: '/v1/poc/schedule-poll-once' }, {}, {}), /invalid signature/)
  assert.equal(claims, 0)
  await run({ method: 'POST', url: '/v1/poc/schedule-poll-once', authorized: true }, {}, {})
  assert.equal(claims, 2)
})

test('execution acknowledgement preserves schedule identity and strategy for recovery', async () => {
  const source = readFileSync(new URL('../src/server.js', import.meta.url), 'utf8')
  const handler = source.match(/async function reportScheduleExecutionSuccess[\s\S]*?\n}\n/)[0]
  const run = new Function('scheduleIdOfTask', 'resolveScheduleExecutionOutcome', 'extractPublishedUrl', 'claimAttemptOfTask', 'shortDiagnosticsJson', 'signedTrustedBackendRequest', `return (${handler})`)(
    task => task.schedule.id, () => 'scheduled', () => '', task => task.schedule.attemptCount,
    JSON.stringify, async () => ({ ok: true, scheduleId: 483 }),
  )
  const schedule = { id: 483, scheduleStrategy: 'backend_delayed_publish', attemptCount: 2, platform: 'sohu' }
  const result = await run({}, { schedule }, {})
  assert.equal(result.id, 483)
  assert.equal(result.scheduleStrategy, 'backend_delayed_publish')
  assert.equal(result.attemptCount, 2)
})
