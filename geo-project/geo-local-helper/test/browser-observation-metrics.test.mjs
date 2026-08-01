import assert from 'node:assert/strict'
import test from 'node:test'
import {
  BrowserRuntimeErrorCounter,
  HelperTaskThroughputCounter,
  buildFailedBrowserObservationMetrics,
  classifyBrowserRuntimeError,
  summarizeBrowserProcessMetrics,
} from '../src/browser-observation-metrics.js'

test('classifies required browser infrastructure errors without double counting an error object', () => {
  assert.equal(classifyBrowserRuntimeError(new Error('Network.enable timed out')), 'networkEnableTimeout')
  assert.equal(classifyBrowserRuntimeError(new Error('Protocol error: Target closed')), 'cdpDisconnected')
  assert.equal(classifyBrowserRuntimeError(new Error('chrome.scripting injection failed')), 'extensionInjectionError')
  assert.equal(classifyBrowserRuntimeError(new Error('Navigation timeout of 75000 ms exceeded')), 'pageTimeout')
  assert.equal(classifyBrowserRuntimeError(new Error('CDP command timed out')), 'cdpProtocolTimeout')

  const counter = new BrowserRuntimeErrorCounter()
  const error = new Error('Network.enable timed out')
  counter.record(error, 'profile-a')
  counter.record(error, 'profile-a')
  assert.equal(counter.snapshot().networkEnableTimeout, 1)
  assert.equal(counter.snapshot('profile-a').networkEnableTimeout, 1)
})

test('failed observations do not carry forward successful measurement fields', () => {
  const failed = buildFailedBrowserObservationMetrics({
    context: {
      browserEnvironmentId: 12,
      environmentKey: 'env-a',
      providerProfileId: 'profile-a',
      browserSessionEpoch: 'epoch-a',
    },
    previousMetrics: {
      observationStatus: 'ok',
      observedAt: '2026-07-30T01:00:00.000Z',
      totalTargetCount: 9,
      cdpStepLatencyMs: { connectMs: 10 },
      processMetrics: { rssBytes: 4096 },
    },
    failedProbeDurationMs: 20_000,
    consecutiveCdpFailures: 1,
    errorCounts: { pageTimeout: 1 },
    taskVolume: { retainedTaskCount: 2 },
    helperUptimeSeconds: 60,
    observationError: 'page timed out',
    observedAt: '2026-07-30T01:01:00.000Z',
  })

  assert.equal(failed.observationStatus, 'failed')
  assert.equal(failed.totalTargetCount, null)
  assert.equal(failed.cdpStepLatencyMs, null)
  assert.equal(failed.processMetrics, null)
  assert.equal(failed.lastSuccessfulObservedAt, '2026-07-30T01:00:00.000Z')
})

test('tracks helper-boot task throughput independently from retained task inventory', () => {
  const counter = new HelperTaskThroughputCounter()
  counter.increment('claimedTotal', 'profile-a')
  counter.increment('executionClaimedTotal', 'profile-a')
  counter.increment('completedTotal', 'profile-a')

  assert.equal(counter.snapshot().claimedTotal, 1)
  assert.equal(counter.snapshot('profile-a').executionClaimedTotal, 1)
  assert.equal(counter.snapshot('profile-a').completedTotal, 1)
  assert.equal(counter.snapshot('profile-b').claimedTotal, 0)
})

test('aggregates browser process rss, handles and cpu delta', () => {
  const result = summarizeBrowserProcessMetrics({
    processInfo: [
      { id: 10, type: 'browser' },
      { id: 11, type: 'renderer' },
    ],
    processRows: [
      { Id: 10, CPU: 12, WorkingSet64: 1000, HandleCount: 20 },
      { Id: 11, CPU: 8, WorkingSet64: 500, HandleCount: 10 },
    ],
    previousSample: { observedAtMs: 1_000, cpuTimeSeconds: 15 },
    observedAtMs: 6_000,
  })

  assert.equal(result.processCount, 2)
  assert.equal(result.rssBytes, 1500)
  assert.equal(result.handleCount, 30)
  assert.equal(result.cpuTimeSeconds, 20)
  assert.equal(result.cpuPercent, 100)
  assert.equal(result.byType.renderer.rssBytes, 500)
})
