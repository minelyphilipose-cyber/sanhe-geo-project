import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const source = readFileSync(new URL('../src/server.js', import.meta.url), 'utf8')
function load(name, context) {
  const match = new RegExp(`^(?:async )?function ${name}\\(`, 'm').exec(source)
  assert.ok(match, `Missing production function: ${name}`)
  vm.runInContext(source.slice(match.index, source.indexOf('\n}', match.index) + 2), context)
  return context[name]
}

for (const platform of ['toutiao', 'baijiahao', 'zhihu', 'xiaohongshu', 'douyin', 'sohu']) {
  test(`${platform}: execution acknowledgement retains shared task identity`, async () => {
    const acknowledged = { id: 1, status: 'scheduled' }
    const context = vm.createContext({
      scheduleIdOfTask: task => task.schedule.id, claimAttemptOfTask: () => 1,
      extractPublishedUrl: () => '', shortDiagnosticsJson: JSON.stringify,
      signedTrustedBackendRequest: async () => acknowledged,
    })
    load('resolveScheduleExecutionOutcome', context)
    const report = load('reportScheduleExecutionSuccess', context)
    const task = { platform, taskId: 10, schedule: { id: 1, expectedAccountName: 'legacy account' } }
    const result = await report({}, task, {})
    assert.equal(result.id, 1)
    assert.equal(result.status, 'scheduled')
    assert.equal(result.expectedAccountName, 'legacy account')
    assert.equal(context.resolveScheduleExecutionOutcome({ platform, publishOptions: {
      submitted: true, publishVerification: { submissionVerified: true, platformStatus: 'reviewing' },
    } }), platform === 'sohu' ? 'scheduled' : 'filled', 'Only platform status parsing differs')
    assert.equal(context.resolveScheduleExecutionOutcome({ platform, publishOptions: {
      scheduled: true, publishVerification: { verified: true },
    } }), 'scheduled')
    assert.equal(context.resolveScheduleExecutionOutcome({ platform, publishOptions: {
      published: true, publishVerification: { verified: true },
    } }), 'published')
  })

  test(`${platform}: definite failure acknowledgement recovery keeps its evidence`, async () => {
    const reports = []
    const task = { platform, taskId: 10, schedule: { id: 1 },
      lastError: { code: 'LEGACY_CHECK_ERROR', message: 'legacy check error' },
      lastResult: { failed: true, failureCode: 'CONTENT_REJECTED', failureMessage: 'platform evidence' },
    }
    const context = vm.createContext({
      tasksById: new Map([[10, task]]), shouldFlushPublishCheckFailure: () => true,
      scheduleIdOfTask: () => 1, claimAttemptOfTask: () => 1,
      nowIso: () => '2026-10-09T03:00:00Z', saveRuntimeTasks: async () => {},
      reportPublishCheckFailed: async (_config, _id, _attempt, result) => { reports.push(result) },
      reportPublishCheckUnknown: async () => { throw new Error('Definite rejection must not become an unknown result') },
      formatBackendError: error => error.message, terminateTaskForScheduleClaimError: () => false,
      isNonRetryableBackendReportError: () => false, console,
    })
    await load('flushPendingPublishCheckFailureReports', context)({})
    assert.equal(reports.length, 1)
    assert.equal(reports[0].failureCode, 'CONTENT_REJECTED')
    assert.equal(reports[0].failureMessage, 'platform evidence')
    assert.ok(task.backendFailureReportedAt)
  })
}
