import crypto from 'node:crypto'
import path from 'node:path'
import fs from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

export const BROWSER_RESOURCE_SCHEMA_VERSION = 2

const DEFAULT_MAX_RESOURCES = 2_000
const DEFAULT_MAX_BYTES = 4 * 1024 * 1024
const DEFAULT_AUDIT_MAX_BYTES = 2 * 1024 * 1024
const DEFAULT_AUDIT_ARCHIVES = 5
const ATOMIC_RENAME_RETRY_DELAYS_MS = [40, 120, 300]
const TERMINAL_LIFECYCLE_STATES = new Set([
  'closed',
  'create_failed',
  'stale',
  'target_missing',
])

function nowIso() {
  return new Date().toISOString()
}

function truncate(value, maxLength) {
  if (value === null || value === undefined) return null
  const text = String(value)
  return text.length <= maxLength ? text : text.slice(0, maxLength)
}

function sanitizePageUrl(value) {
  const text = String(value || '').trim()
  if (!text) return null
  try {
    const parsed = new URL(text)
    if (parsed.protocol === 'http:' || parsed.protocol === 'https:') {
      return truncate(`${parsed.origin}${parsed.pathname}`, 2_048)
    }
    if (parsed.protocol === 'about:' || parsed.protocol === 'chrome:') {
      return truncate(`${parsed.protocol}${parsed.pathname}`, 2_048)
    }
    if (parsed.protocol === 'chrome-extension:') {
      return 'chrome-extension:'
    }
    return truncate(parsed.protocol, 64)
  } catch {
    return null
  }
}

function clone(value) {
  return JSON.parse(JSON.stringify(value))
}

function nullableNumber(value) {
  if (value === null || value === undefined || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function filesystemPath(value) {
  return value instanceof URL ? fileURLToPath(value) : path.resolve(String(value))
}

function resourceIdentity(resource) {
  return [
    String(resource?.providerProfileId || '').trim(),
    String(resource?.browserSessionEpoch || '').trim(),
    String(resource?.targetId || '').trim(),
  ].join('\u0000')
}

function normalizeResource(input, helperBootId, timestamp = nowIso()) {
  return {
    resourceId: truncate(input.resourceId || crypto.randomUUID(), 64),
    resourceType: truncate(input.resourceType || 'unknown_target', 64),
    resourceOrigin: truncate(input.resourceOrigin || 'unknown', 64),
    ownership: truncate(input.ownership || 'unknown', 32),
    helperBootId: truncate(input.helperBootId || helperBootId, 64),
    taskId: nullableNumber(input.taskId),
    scheduleId: nullableNumber(input.scheduleId),
    claimAttempt: nullableNumber(input.claimAttempt),
    browserEnvironmentId: nullableNumber(input.browserEnvironmentId),
    environmentKey: truncate(input.environmentKey, 128),
    providerProfileId: truncate(input.providerProfileId, 128),
    browserSessionEpoch: truncate(input.browserSessionEpoch, 64),
    browserWsBrowserId: truncate(input.browserWsBrowserId, 128),
    targetId: truncate(input.targetId, 128),
    parentTargetId: truncate(input.parentTargetId, 128),
    platform: truncate(input.platform, 32),
    pageUrl: sanitizePageUrl(input.pageUrl),
    lifecycleState: truncate(input.lifecycleState || 'active', 32),
    irreversibleBoundary: truncate(input.irreversibleBoundary || 'no_mutation', 32),
    backendReportState: truncate(input.backendReportState || 'unknown', 32),
    openedAt: input.openedAt || timestamp,
    lastObservedAt: input.lastObservedAt || timestamp,
    lastTaskActivityAt: input.lastTaskActivityAt || null,
    manualHoldRequired: input.manualHoldRequired === true,
    protectedUntil: input.protectedUntil || null,
    closedAt: input.closedAt || null,
    closeReason: truncate(input.closeReason, 256),
  }
}

function normalizeTarget(target) {
  return {
    targetId: truncate(target?.targetId, 128),
    parentTargetId: truncate(target?.parentTargetId, 128),
    resourceType: truncate(target?.resourceType || 'unknown_target', 64),
    pageUrl: sanitizePageUrl(target?.pageUrl),
    ownership: truncate(target?.ownership || 'unknown', 32),
    resourceOrigin: truncate(target?.resourceOrigin || 'startup_discovered', 64),
  }
}

function emptyState(helperBootId) {
  return {
    schemaVersion: BROWSER_RESOURCE_SCHEMA_VERSION,
    registryRevision: 0,
    helperBootId,
    resources: [],
  }
}

export function browserWsBrowserId(wsEndpoint) {
  const text = String(wsEndpoint || '').trim()
  if (!text) return null
  const match = text.match(/\/devtools\/browser\/([^/?#]+)/i)
  return match ? decodeURIComponent(match[1]) : null
}

export function observedBrowserSessionEpoch(helperBootId, wsEndpoint) {
  const browserId = browserWsBrowserId(wsEndpoint)
  if (!browserId) return null
  return crypto
    .createHash('sha256')
    .update(`${helperBootId}\u0000${browserId}`)
    .digest('hex')
    .slice(0, 32)
}

export class BrowserResourceRegistry {
  constructor(options) {
    this.registryPath = filesystemPath(options.registryPath)
    this.runtimeDir = filesystemPath(options.runtimeDir || path.dirname(this.registryPath))
    this.auditPath = filesystemPath(
      options.auditPath || path.join(this.runtimeDir, 'browser-resource-audit.jsonl'),
    )
    this.helperBootId = String(options.helperBootId || crypto.randomUUID())
    this.maxResources = Math.max(100, Number(options.maxResources || DEFAULT_MAX_RESOURCES))
    this.maxBytes = Math.max(256 * 1024, Number(options.maxBytes || DEFAULT_MAX_BYTES))
    this.auditMaxBytes = Math.max(128 * 1024, Number(options.auditMaxBytes || DEFAULT_AUDIT_MAX_BYTES))
    this.auditArchives = Math.max(1, Number(options.auditArchives || DEFAULT_AUDIT_ARCHIVES))
    this.state = emptyState(this.helperBootId)
    this.health = {
      status: 'not_loaded',
      loadedAt: null,
      lastPersistedAt: null,
      lastError: null,
      quarantinedPath: null,
      capacityExceeded: false,
    }
    this.operationQueue = Promise.resolve()
  }

  async load() {
    await fs.mkdir(this.runtimeDir, { recursive: true })
    try {
      const raw = await fs.readFile(this.registryPath, 'utf8')
      if (Buffer.byteLength(raw, 'utf8') > this.maxBytes) {
        throw new Error(`browser resource registry exceeds ${this.maxBytes} bytes`)
      }
      const parsed = JSON.parse(raw)
      if (parsed?.schemaVersion !== BROWSER_RESOURCE_SCHEMA_VERSION || !Array.isArray(parsed.resources)) {
        throw new Error('browser resource registry schema is invalid')
      }
      if (parsed.resources.length > this.maxResources) {
        throw new Error(`browser resource registry exceeds ${this.maxResources} resources`)
      }
      const loadedAt = nowIso()
      this.state = {
        schemaVersion: BROWSER_RESOURCE_SCHEMA_VERSION,
        registryRevision: Math.max(0, Number(parsed.registryRevision || 0)),
        helperBootId: this.helperBootId,
        resources: parsed.resources.map((resource) => {
          const normalized = normalizeResource(resource, resource.helperBootId || parsed.helperBootId, loadedAt)
          if (!TERMINAL_LIFECYCLE_STATES.has(normalized.lifecycleState)) {
            normalized.lifecycleState = 'stale'
            normalized.ownership = 'unknown'
            normalized.lastObservedAt = loadedAt
          }
          return normalized
        }),
      }
      this.health = {
        ...this.health,
        status: 'healthy',
        loadedAt,
        lastError: null,
      }
      await this.#persistLoadedState()
    } catch (error) {
      if (error?.code !== 'ENOENT') {
        await this.#quarantineDamagedRegistry(error)
      } else {
        this.health = {
          ...this.health,
          status: 'healthy',
          loadedAt: nowIso(),
          lastError: null,
        }
      }
      this.state = emptyState(this.helperBootId)
    }
    return this.snapshot()
  }

  snapshot() {
    return {
      ...clone(this.state),
      registryHealth: clone(this.health),
    }
  }

  async registerResource(input) {
    const timestamp = nowIso()
    return this.#mutate((draft, events) => {
      const normalized = normalizeResource(input, this.helperBootId, timestamp)
      if (!normalized.providerProfileId || !normalized.browserSessionEpoch || !normalized.targetId) {
        throw new Error('browser resource identity requires providerProfileId, browserSessionEpoch and targetId')
      }
      const identity = resourceIdentity(normalized)
      const existing = draft.resources.find((resource) => resourceIdentity(resource) === identity)
      if (existing) {
        Object.assign(existing, normalized, {
          resourceId: existing.resourceId,
          openedAt: existing.openedAt || normalized.openedAt,
          lastObservedAt: timestamp,
        })
        return existing
      }
      if (draft.resources.length >= this.maxResources) {
        this.health.capacityExceeded = true
        throw new Error(`browser resource registry capacity ${this.maxResources} reached`)
      }
      draft.resources.push(normalized)
      events.push({
        eventType: 'resource_registered',
        resource: normalized,
        actorType: normalized.ownership === 'operator' ? 'operator' : 'local_helper',
        reason: normalized.resourceOrigin,
      })
      return normalized
    })
  }

  async updateResource(identity, changes = {}) {
    const timestamp = nowIso()
    return this.#mutate((draft) => {
      const key = resourceIdentity(identity)
      const existing = draft.resources.find((resource) => resourceIdentity(resource) === key)
      if (!existing) return null
      const normalizedChanges = {
        ...changes,
        ...(Object.hasOwn(changes, 'pageUrl') ? { pageUrl: sanitizePageUrl(changes.pageUrl) } : {}),
      }
      Object.assign(existing, normalizedChanges, {
        resourceId: existing.resourceId,
        lastObservedAt: timestamp,
      })
      return existing
    })
  }

  async markResourceClosed(identity, reason) {
    const timestamp = nowIso()
    return this.#mutate((draft, events) => {
      const key = resourceIdentity(identity)
      const existing = draft.resources.find((resource) => resourceIdentity(resource) === key)
      if (!existing) return null
      existing.lifecycleState = 'closed'
      existing.closedAt = timestamp
      existing.closeReason = truncate(reason || 'observed_closed', 256)
      existing.lastObservedAt = timestamp
      events.push({
        eventType: 'target_closed',
        resource: existing,
        actorType: 'existing_runtime_behavior',
        reason: existing.closeReason,
        dryRun: true,
        result: 'observed',
      })
      return existing
    })
  }

  async reconcileEnvironment(observation) {
    const timestamp = observation.observedAt || nowIso()
    const profileId = String(observation.providerProfileId || '').trim()
    const sessionEpoch = String(observation.browserSessionEpoch || '').trim()
    if (!profileId || !sessionEpoch) {
      throw new Error('browser observation requires providerProfileId and browserSessionEpoch')
    }
    const targets = (Array.isArray(observation.targets) ? observation.targets : [])
      .map(normalizeTarget)
      .filter((target) => target.targetId)
    return this.#mutate((draft, events) => {
      for (const resource of draft.resources) {
        if (resource.providerProfileId !== profileId) continue
        if (resource.browserSessionEpoch === sessionEpoch) continue
        if (TERMINAL_LIFECYCLE_STATES.has(resource.lifecycleState)) continue
        resource.lifecycleState = 'stale'
        resource.ownership = 'unknown'
        resource.lastObservedAt = timestamp
        events.push({
          eventType: 'resource_session_stale',
          resource,
          actorType: 'local_helper',
          reason: 'browser_session_changed_or_unprovable',
          dryRun: true,
          result: 'marked_stale',
        })
      }

      const observedTargetIds = new Set()
      for (const target of targets) {
        observedTargetIds.add(target.targetId)
        const key = resourceIdentity({
          providerProfileId: profileId,
          browserSessionEpoch: sessionEpoch,
          targetId: target.targetId,
        })
        const existing = draft.resources.find((resource) => resourceIdentity(resource) === key)
        if (existing) {
          existing.pageUrl = target.pageUrl
          existing.parentTargetId = target.parentTargetId
          existing.resourceType = existing.resourceType === 'unknown_target'
            ? target.resourceType
            : existing.resourceType
          existing.lastObservedAt = timestamp
          if (existing.lifecycleState === 'target_missing') existing.lifecycleState = 'active'
          continue
        }
        if (draft.resources.length >= this.maxResources) {
          this.health.capacityExceeded = true
          continue
        }
        const discovered = normalizeResource({
          ...target,
          browserEnvironmentId: observation.browserEnvironmentId,
          environmentKey: observation.environmentKey,
          providerProfileId: profileId,
          browserSessionEpoch: sessionEpoch,
          browserWsBrowserId: observation.browserWsBrowserId,
          platform: observation.platform,
          lifecycleState: 'active',
          backendReportState: 'unknown',
          openedAt: timestamp,
          lastObservedAt: timestamp,
        }, this.helperBootId, timestamp)
        draft.resources.push(discovered)
        events.push({
          eventType: 'resource_registered',
          resource: discovered,
          actorType: 'local_helper',
          reason: 'startup_discovered',
          dryRun: true,
          result: 'observed_unknown',
        })
      }

      for (const resource of draft.resources) {
        if (resource.providerProfileId !== profileId
          || resource.browserSessionEpoch !== sessionEpoch
          || TERMINAL_LIFECYCLE_STATES.has(resource.lifecycleState)
          || observedTargetIds.has(resource.targetId)) {
          continue
        }
        resource.lifecycleState = 'target_missing'
        resource.lastObservedAt = timestamp
        events.push({
          eventType: 'target_close_observed',
          resource,
          actorType: 'local_helper',
          reason: 'target_missing_during_reconciliation',
          dryRun: true,
          result: 'observed_missing',
        })
      }
      return {
        providerProfileId: profileId,
        browserSessionEpoch: sessionEpoch,
        observedTargetCount: targets.length,
      }
    })
  }

  async #persistLoadedState() {
    if (!this.state.resources.length) return
    const draft = clone(this.state)
    draft.registryRevision += 1
    await this.#persist(draft)
    this.state = draft
  }

  async #mutate(mutator) {
    const operation = this.operationQueue.then(async () => {
      const draft = clone(this.state)
      const events = []
      const result = mutator(draft, events)
      draft.registryRevision = Math.max(0, Number(draft.registryRevision || 0)) + 1
      draft.schemaVersion = BROWSER_RESOURCE_SCHEMA_VERSION
      draft.helperBootId = this.helperBootId
      await this.#persist(draft)
      this.state = draft
      await this.#appendAuditEvents(events, draft.registryRevision)
      return clone(result)
    })
    this.operationQueue = operation.catch(() => null)
    return operation
  }

  async #persist(draft) {
    const payload = `${JSON.stringify(draft, null, 2)}\n`
    const bytes = Buffer.byteLength(payload, 'utf8')
    if (bytes > this.maxBytes) {
      const error = new Error(`browser resource registry payload exceeds ${this.maxBytes} bytes`)
      this.health = { ...this.health, status: 'degraded', lastError: error.message }
      throw error
    }
    await fs.mkdir(this.runtimeDir, { recursive: true })
    const tempPath = `${this.registryPath}.tmp-${process.pid}-${crypto.randomUUID()}`
    let handle
    try {
      handle = await fs.open(tempPath, 'wx')
      await handle.writeFile(payload, 'utf8')
      await handle.sync()
      await handle.close()
      handle = null
      let renamed = false
      let lastError
      for (let attempt = 0; attempt <= ATOMIC_RENAME_RETRY_DELAYS_MS.length; attempt += 1) {
        try {
          await fs.rename(tempPath, this.registryPath)
          renamed = true
          break
        } catch (error) {
          lastError = error
          if (!['EPERM', 'EACCES', 'EBUSY'].includes(error?.code)
            || attempt >= ATOMIC_RENAME_RETRY_DELAYS_MS.length) {
            break
          }
          await delay(ATOMIC_RENAME_RETRY_DELAYS_MS[attempt])
        }
      }
      if (!renamed) throw lastError || new Error('browser resource registry atomic rename failed')
      await this.#syncRuntimeDirectory()
      this.health = {
        ...this.health,
        status: this.health.capacityExceeded ? 'degraded' : 'healthy',
        lastPersistedAt: nowIso(),
        lastError: null,
      }
    } catch (error) {
      this.health = {
        ...this.health,
        status: 'degraded',
        lastError: truncate(error?.message || error, 500),
      }
      throw error
    } finally {
      await handle?.close().catch(() => null)
      await fs.unlink(tempPath).catch(() => null)
    }
  }

  async #syncRuntimeDirectory() {
    let handle
    try {
      handle = await fs.open(this.runtimeDir, 'r')
      await handle.sync()
    } catch {
      // Directory fsync is not supported on every Windows filesystem.
    } finally {
      await handle?.close().catch(() => null)
    }
  }

  async #quarantineDamagedRegistry(error) {
    const suffix = new Date().toISOString().replace(/[:.]/g, '-')
    const quarantinedPath = `${this.registryPath}.corrupt-${suffix}`
    await fs.rename(this.registryPath, quarantinedPath).catch(() => null)
    this.health = {
      ...this.health,
      status: 'degraded',
      loadedAt: nowIso(),
      lastError: truncate(error?.message || error, 500),
      quarantinedPath,
    }
  }

  async #appendAuditEvents(events, registryRevision) {
    if (!events.length) return
    const lines = events.map((event) => JSON.stringify({
      eventId: crypto.randomUUID(),
      eventType: event.eventType,
      resourceId: event.resource?.resourceId || null,
      browserEnvironmentId: event.resource?.browserEnvironmentId || null,
      targetId: event.resource?.targetId || null,
      browserSessionEpoch: event.resource?.browserSessionEpoch || null,
      actorType: event.actorType || 'local_helper',
      operatorId: null,
      reason: truncate(event.reason, 256),
      policyVersion: null,
      registryRevision,
      dryRun: event.dryRun !== false,
      result: event.result || 'observed',
      occurredAt: nowIso(),
    })).join('\n') + '\n'
    try {
      await this.#rotateAuditIfNeeded(Buffer.byteLength(lines, 'utf8'))
      await fs.appendFile(this.auditPath, lines, 'utf8')
    } catch (error) {
      this.health = {
        ...this.health,
        status: 'degraded',
        lastError: truncate(`browser resource audit append failed: ${error?.message || error}`, 500),
      }
    }
  }

  async #rotateAuditIfNeeded(incomingBytes) {
    const stat = await fs.stat(this.auditPath).catch(() => null)
    if (!stat || stat.size + incomingBytes <= this.auditMaxBytes) return
    const archivePath = `${this.auditPath}.${new Date().toISOString().replace(/[:.]/g, '-')}`
    await fs.rename(this.auditPath, archivePath)
    const directory = path.dirname(this.auditPath)
    const prefix = `${path.basename(this.auditPath)}.`
    const archives = (await fs.readdir(directory, { withFileTypes: true }))
      .filter((entry) => entry.isFile() && entry.name.startsWith(prefix))
      .map((entry) => path.join(directory, entry.name))
      .sort()
      .reverse()
    for (const stalePath of archives.slice(this.auditArchives)) {
      await fs.unlink(stalePath).catch(() => null)
    }
  }
}
