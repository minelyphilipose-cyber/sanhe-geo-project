import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const source = fs.readFileSync(new URL('../platform-baijiahao.js', import.meta.url), 'utf8')

function loadPlatform() {
  const context = {
    URL,
  }
  context.globalThis = context
  vm.createContext(context)
  vm.runInContext(source, context)
  return context.__GEO_BAIJIAHAO_PLATFORM__
}

test('marks Baijiahao final submission and verification as post-submission stages', () => {
  assert.match(source, /notifyStage\(deps, 'submitting_publish'\)[\s\S]*clickTrustedActionOnce\(confirm/)
  assert.match(source, /clickScheduleConfirmWithThrottle[\s\S]*notifyStage\(deps, 'verifying_publish_result'\)[\s\S]*waitForPublishSubmitted/)

  const stages = []
  loadPlatform().notifyStage({ updateStage: (stage) => stages.push(stage) }, 'submitting_publish')
  assert.deepEqual(stages, ['submitting_publish'])
})
