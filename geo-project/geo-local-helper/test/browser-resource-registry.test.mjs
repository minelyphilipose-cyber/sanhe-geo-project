import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import {
  BrowserResourceRegistry,
  browserWsBrowserId,
  observedBrowserSessionEpoch,
} from '../src/browser-resource-registry.js'

async function withTempRegistry(run) {
  const runtimeDir = await fs.mkdtemp(path.join(os.tmpdir(), 'geo-browser-registry-'))
  try {
    const registryPath = path.join(runtimeDir, 'browser-resources.json')
    const auditPath = path.join(runtimeDir, 'browser-resource-audit.jsonl')
    await run({ runtimeDir, registryPath, auditPath })
  } finally {
    await fs.rm(runtimeDir, { recursive: true, force: true })
  }
}

test('extracts a browser identity and scopes the observed epoch to the helper boot', () => {
  const endpoint = 'ws://127.0.0.1:50325/devtools/browser/browser-123'

  assert.equal(browserWsBrowserId(endpoint), 'browser-123')
  assert.equal(observedBrowserSessionEpoch('boot-a', endpoint).length, 32)
  assert.notEqual(
    observedBrowserSessionEpoch('boot-a', endpoint),
    observedBrowserSessionEpoch('boot-b', endpoint),
  )
})

test('persists explicit ownership and reconciles unknown targets atomically', async () => {
  await withTempRegistry(async ({ runtimeDir, registryPath, auditPath }) => {
    const registry = new BrowserResourceRegistry({
      runtimeDir,
      registryPath,
      auditPath,
      helperBootId: 'boot-a',
    })
    await registry.load()
    await registry.registerResource({
      resourceType: 'editor_tab',
      resourceOrigin: 'schedule_execution',
      ownership: 'automation',
      taskId: 10,
      scheduleId: 20,
      browserEnvironmentId: 30,
      environmentKey: 'env-a',
      providerProfileId: 'profile-a',
      browserSessionEpoch: 'epoch-a',
      browserWsBrowserId: 'browser-a',
      targetId: 'target-editor',
      pageUrl: 'https://creator.example/editor?registerToken=secret#draft',
    })
    await registry.reconcileEnvironment({
      browserEnvironmentId: 30,
      environmentKey: 'env-a',
      providerProfileId: 'profile-a',
      browserSessionEpoch: 'epoch-a',
      browserWsBrowserId: 'browser-a',
      targets: [
        {
          targetId: 'target-editor',
          resourceType: 'observed_tab',
          pageUrl: 'https://creator.example/editor?loaded=1',
        },
        {
          targetId: 'target-blank',
          resourceType: 'observed_tab',
          pageUrl: 'about:blank',
        },
      ],
    })

    const snapshot = registry.snapshot()
    assert.equal(snapshot.registryRevision, 2)
    assert.equal(snapshot.resources.length, 2)
    assert.equal(
      snapshot.resources.find((item) => item.targetId === 'target-editor').ownership,
      'automation',
    )
    assert.equal(
      snapshot.resources.find((item) => item.targetId === 'target-editor').pageUrl,
      'https://creator.example/editor',
    )
    assert.equal(
      snapshot.resources.find((item) => item.targetId === 'target-blank').ownership,
      'unknown',
    )
    assert.equal(
      snapshot.resources.find((item) => item.targetId === 'target-blank').taskId,
      null,
    )
    const persisted = JSON.parse(await fs.readFile(registryPath, 'utf8'))
    assert.equal(persisted.registryRevision, snapshot.registryRevision)
    assert.equal(persisted.resources.length, 2)
    const audit = await fs.readFile(auditPath, 'utf8')
    assert.match(audit, /resource_registered/)
  })
})

test('sanitizes page urls at register, update and reconcile write boundaries', async () => {
  await withTempRegistry(async ({ runtimeDir, registryPath, auditPath }) => {
    const registry = new BrowserResourceRegistry({
      runtimeDir,
      registryPath,
      auditPath,
      helperBootId: 'boot-a',
    })
    await registry.load()
    const identity = {
      providerProfileId: 'profile-a',
      browserSessionEpoch: 'epoch-a',
      targetId: 'target-a',
    }

    await registry.registerResource({
      ...identity,
      pageUrl: 'https://example.com/register?token=secret#draft',
    })
    assert.equal(registry.snapshot().resources[0].pageUrl, 'https://example.com/register')

    await registry.updateResource(identity, {
      pageUrl: 'https://example.com/update?token=secret#draft',
    })
    assert.equal(registry.snapshot().resources[0].pageUrl, 'https://example.com/update')

    await registry.reconcileEnvironment({
      providerProfileId: 'profile-a',
      browserSessionEpoch: 'epoch-a',
      targets: [{
        targetId: 'target-a',
        pageUrl: 'https://example.com/reconcile?token=secret#draft',
      }],
    })
    assert.equal(registry.snapshot().resources[0].pageUrl, 'https://example.com/reconcile')
    assert.doesNotMatch(await fs.readFile(registryPath, 'utf8'), /secret|token=|#draft/)
  })
})

test('treats resources from a previous helper boot as stale and unknown', async () => {
  await withTempRegistry(async ({ runtimeDir, registryPath, auditPath }) => {
    const first = new BrowserResourceRegistry({
      runtimeDir,
      registryPath,
      auditPath,
      helperBootId: 'boot-a',
    })
    await first.load()
    await first.registerResource({
      resourceType: 'editor_tab',
      resourceOrigin: 'schedule_execution',
      ownership: 'automation',
      providerProfileId: 'profile-a',
      browserSessionEpoch: 'epoch-a',
      targetId: 'target-a',
    })

    const restarted = new BrowserResourceRegistry({
      runtimeDir,
      registryPath,
      auditPath,
      helperBootId: 'boot-b',
    })
    await restarted.load()

    const [resource] = restarted.snapshot().resources
    assert.equal(resource.lifecycleState, 'stale')
    assert.equal(resource.ownership, 'unknown')
  })
})

test('quarantines a damaged registry instead of overwriting it in place', async () => {
  await withTempRegistry(async ({ runtimeDir, registryPath, auditPath }) => {
    await fs.writeFile(registryPath, '{not-json', 'utf8')
    const registry = new BrowserResourceRegistry({
      runtimeDir,
      registryPath,
      auditPath,
      helperBootId: 'boot-a',
    })

    await registry.load()

    const snapshot = registry.snapshot()
    assert.equal(snapshot.registryHealth.status, 'degraded')
    assert.match(snapshot.registryHealth.quarantinedPath, /\.corrupt-/)
    assert.equal(snapshot.resources.length, 0)
    assert.equal(await fs.readFile(snapshot.registryHealth.quarantinedPath, 'utf8'), '{not-json')
  })
})

test('serializes concurrent mutations with monotonic revisions', async () => {
  await withTempRegistry(async ({ runtimeDir, registryPath, auditPath }) => {
    const registry = new BrowserResourceRegistry({
      runtimeDir,
      registryPath,
      auditPath,
      helperBootId: 'boot-a',
    })
    await registry.load()

    await Promise.all(Array.from({ length: 12 }, (_, index) => registry.registerResource({
      providerProfileId: 'profile-a',
      browserSessionEpoch: 'epoch-a',
      targetId: `target-${index}`,
      pageUrl: `https://creator.example/${index}`,
    })))

    const snapshot = registry.snapshot()
    assert.equal(snapshot.registryRevision, 12)
    assert.equal(snapshot.resources.length, 12)
    assert.equal(JSON.parse(await fs.readFile(registryPath, 'utf8')).registryRevision, 12)
  })
})
