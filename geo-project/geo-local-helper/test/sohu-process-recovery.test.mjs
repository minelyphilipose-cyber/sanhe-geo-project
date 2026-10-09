import assert from 'node:assert/strict'
import { fork } from 'node:child_process'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { once } from 'node:events'
import { fileURLToPath } from 'node:url'

const fixture = fileURLToPath(new URL('./fixtures/sohu-recovery-process.mjs', import.meta.url))
function run(mode, scenario, directory) {
  const child = fork(fixture, [mode, scenario, directory], { stdio: ['ignore', 'ignore', 'pipe', 'ipc'] })
  let stderr = ''
  child.stderr.on('data', value => { stderr += value })
  const result = new Promise((resolve, reject) => {
    child.once('message', resolve)
    child.once('error', reject)
    child.once('exit', code => { if (code) reject(new Error(stderr || `Child exited ${code}`)) })
  })
  return { child, result }
}
for (const scenario of ['uploading', 'submitting', 'completed', 'rejected']) {
  test(`Sohu isolated process kill/restart: ${scenario}`, { timeout: 15000 }, async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'geo-sohu-recovery-'))
    let seed, resumed
    try {
      seed = run('seed', scenario, directory)
      assert.equal((await seed.result).persisted, true)
      const stopped = once(seed.child, 'exit')
      seed.child.kill('SIGKILL')
      await stopped
      resumed = run('resume', scenario, directory)
      const result = await resumed.result
      assert.equal(result.before, null, 'Persisted work must not be filled again')
      assert.equal(result.after, null)
      if (scenario === 'uploading' || scenario === 'submitting') {
        assert.equal(result.tasks[0].status, 'failed')
        assert.equal(result.tasks[0].failureCode, 'LOCAL_HELPER_CLAIM_TIMEOUT')
        assert.equal(result.reports.length, 0)
      }
      if (scenario === 'submitting') assert.match(result.reservationError, /SOHU_ALREADY_SUBMITTED/)
      if (scenario === 'completed') {
        assert.equal(result.reports.length, 1)
        assert.equal(result.reports[0].kind, 'success')
        assert.equal(result.reports[0].result.publishOptions.publishVerification.platformPublishId, 'fixture-id')
      }
      if (scenario === 'rejected') {
        assert.equal(result.reports.length, 1, 'Acknowledgement must not be sent twice after success')
        assert.equal(result.reports[0].kind, 'failed')
        assert.equal(result.reports[0].attempt, 2)
        assert.equal(result.reports[0].result.failureMessage, '文章被平台判定为广告软文')
        assert.equal(result.reports[0].result.platformPublishId, 'fixture-id')
      }
    } finally {
      seed?.child.kill(); resumed?.child.kill()
      await rm(directory, { recursive: true, force: true })
    }
  })
}
