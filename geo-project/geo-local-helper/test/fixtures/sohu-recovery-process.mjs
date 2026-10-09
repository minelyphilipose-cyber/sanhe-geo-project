// Isolated process fixture: real helper persistence/recovery functions, fake backend and browser storage.
import fs from 'node:fs/promises'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import path from 'node:path'

const [mode, scenario, directory] = process.argv.slice(2)
const source = readFileSync(new URL('../../src/server.js', import.meta.url), 'utf8')
function extract(text, name) {
  const match = new RegExp(`^(?:async )?function ${name}\\(`, 'm').exec(text)
  if (!match) throw new Error(`Missing production function: ${name}`)
  return text.slice(match.index, text.indexOf('\n}', match.index) + 2)
}
const reports = []
const context = {
  fs, Date, console, TASKS_PATH: path.join(directory, 'tasks.json'), RUNTIME_DIR: directory,
  tasksById: new Map(), CLAIMABLE_STATUSES: new Set(['pending', 'requeued']),
  FAILED_SCHEDULE_REPORT_MAX_ATTEMPTS: 3, SCHEDULE_PROGRESS_STALL_TIMEOUT_MS: 15 * 60000,
  CLAIM_TIMEOUT_MS: 45000, nowIso: () => new Date().toISOString(), parseJsonText: JSON.parse,
  pruneRuntimeTasks: () => {}, recordTaskTerminalThroughput: () => {},
  publishedUrlFromPublishCheckResult: value => value.platformPublishedUrl || '',
  formatBackendError: error => ({ message: error.message }),
  terminateTaskForScheduleClaimError: () => false, isNonRetryableBackendReportError: () => false,
  reportPublishCheckFailed: async (_config, id, attempt, result) => {
    if (mode === 'seed') throw new Error('fixture backend unavailable')
    reports.push({ kind: 'failed', id, attempt, result }); return null
  },
  reportPublishCheckUnknown: async () => { throw new Error('Definite rejection must not become unknown') },
  reportScheduleExecutionSuccess: async (_config, task, result) => {
    reports.push({ kind: 'success', id: task.schedule.id, result }); return null
  },
}
vm.createContext(context)
for (const name of ['loadRuntimeTasks', 'saveRuntimeTasks', 'normalizePersistedTask', 'listTasks',
  'compactRuntimeTaskForStorage', 'hasPendingBackendReport', 'isTerminalStatus', 'scheduleIdOfTask',
  'claimAttemptOfTask', 'findNextClaimableTask', 'taskProgressAt', 'taskProgressAgeMs',
  'claimedTimeoutMsForTask', 'expireClaimedTask', 'requeueTimedOutClaims',
  'compactPublishCheckRuntimeResult', 'markPublishCheckRuntimeTaskStage', 'markPublishCheckRuntimeTaskFinished',
  'shouldFlushPublishCheckFailure', 'flushPendingPublishCheckFailureReports',
  'shouldFlushScheduleSuccess', 'flushPendingScheduleSuccessReports']) {
  vm.runInContext(extract(source, name), context)
}
const storagePath = path.join(directory, 'extension-storage.json')
const worker = readFileSync(new URL('../../../geo-env-extension/service-worker.js', import.meta.url), 'utf8')
const extension = {
  Date, chrome: { storage: { local: {
    get: async key => {
      try { const stored = JSON.parse(await fs.readFile(storagePath, 'utf8')); return { [key]: stored[key] } }
      catch (error) { if (error.code !== 'ENOENT') throw error; return {} }
    },
    set: async value => fs.writeFile(storagePath, JSON.stringify(value)),
  } } },
}
vm.createContext(extension)
vm.runInContext('let sohuSubmissionWriteChain = Promise.resolve();\n' + extract(worker, 'reserveSohuSubmissionAttempt'), extension)
const payload = { taskId: 9001, title: '隔离测试文章', expectedAccountName: '隔离测试账号' }
if (mode === 'seed') {
  const old = new Date(Date.now() - 20 * 60000).toISOString()
  const task = { taskId: 9001, platform: 'sohu', environmentKey: 'fixture-only', status: 'claimed',
    createdAt: old, claimedAt: old, lastStageAt: old, lastStage: scenario === 'uploading' ? 'uploading_images' : 'publish_submitting',
    schedule: { id: 900, attemptCount: 2 }, backendTask: { scheduleId: 900 } }
  if (scenario === 'submitting') await extension.reserveSohuSubmissionAttempt(payload)
  if (scenario === 'completed') {
    task.status = 'completed'
    task.fillResult = { publishOptions: { submitted: true, publishVerification: { platformStatus: 'reviewing', platformPublishId: 'fixture-id' } } }
  }
  if (scenario === 'rejected') {
    task.taskKind = 'publish_result_check'
    context.markPublishCheckRuntimeTaskFinished(task, 'failed', {
      failed: true, failureCode: 'SOHU_REVIEW_REJECTED', platformStatus: 'rejected',
      failureMessage: '文章被平台判定为广告软文', platformPublishId: 'fixture-id',
    })
  }
  context.tasksById.set(task.taskId, task)
  await context.saveRuntimeTasks()
  if (scenario === 'rejected') await context.flushPendingPublishCheckFailureReports({}, 'sohu')
  process.send({ persisted: true })
  setInterval(() => {}, 1000)
} else {
  await context.loadRuntimeTasks()
  const before = context.findNextClaimableTask('fixture-only', 'sohu')
  if (scenario === 'uploading' || scenario === 'submitting') await context.requeueTimedOutClaims('fixture-only')
  if (scenario === 'completed') await context.flushPendingScheduleSuccessReports({}, 'sohu')
  if (scenario === 'rejected') {
    await context.flushPendingPublishCheckFailureReports({}, 'sohu')
    await context.flushPendingPublishCheckFailureReports({}, 'sohu')
  }
  let reservationError = ''
  if (scenario === 'submitting') {
    try { await extension.reserveSohuSubmissionAttempt(payload) } catch (error) { reservationError = error.code || error.message }
  }
  process.send({ before, after: context.findNextClaimableTask('fixture-only', 'sohu'),
    tasks: context.listTasks(), reports, reservationError })
  process.disconnect()
}
