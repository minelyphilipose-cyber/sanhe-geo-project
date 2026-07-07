import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import fs from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const RESTART_DELAY_MS = Number(process.env.GEO_HELPER_RESTART_DELAY_MS || 2_000)
const MAX_RESTARTS_PER_WINDOW = Number(process.env.GEO_HELPER_MAX_RESTARTS_PER_WINDOW || 6)
const RESTART_WINDOW_MS = Number(process.env.GEO_HELPER_RESTART_WINDOW_MS || 5 * 60_000)
const EXIT_CODE_PORT_IN_USE = 2
const CONFIG_PATH = new URL('../config.local.json', import.meta.url)
const EXAMPLE_CONFIG_PATH = new URL('../config.example.json', import.meta.url)
const RUNTIME_DIR = new URL('../runtime/', import.meta.url)
const LOG_PATH = new URL('supervisor.log', RUNTIME_DIR)
const MAX_LOG_BYTES = Number(process.env.GEO_HELPER_SUPERVISOR_LOG_MAX_BYTES || 2 * 1024 * 1024)
const EXIT_ON_RESTART_LIMIT = process.env.GEO_HELPER_EXIT_ON_RESTART_LIMIT === '1'

let child = null
let stopping = false
let restartTimes = []
let helperConfig = { host: '127.0.0.1', port: 17891 }
let childStartedAt = null

function parseJsonText(raw) {
  return JSON.parse(String(raw || '').replace(/^\uFEFF/, ''))
}

function nowIso() {
  return new Date().toISOString()
}

async function appendLog(value) {
  try {
    await fs.mkdir(RUNTIME_DIR, { recursive: true })
    await rotateLogIfNeeded()
    await fs.appendFile(LOG_PATH, value, 'utf8')
  } catch (_) {
    // Console logging must remain best-effort even if the runtime directory is not writable.
  }
}

async function rotateLogIfNeeded() {
  if (!Number.isFinite(MAX_LOG_BYTES) || MAX_LOG_BYTES <= 0) return
  const stat = await fs.stat(LOG_PATH).catch(() => null)
  if (!stat || stat.size < MAX_LOG_BYTES) return
  const rotatedPath = new URL('supervisor.log.1', RUNTIME_DIR)
  await fs.rm(rotatedPath, { force: true }).catch(() => null)
  await fs.rename(LOG_PATH, rotatedPath).catch(() => null)
}

function log(level, message, detail = null) {
  const suffix = detail ? ` ${JSON.stringify(detail)}` : ''
  const line = `[${nowIso()}] ${message}${suffix}`
  const writer = level === 'error' ? console.error : console.log
  writer(line)
  appendLog(`${line}\n`)
}

function pipeChildOutput(stream, target) {
  stream?.on('data', (chunk) => {
    target.write(chunk)
    appendLog(chunk)
  })
}

function pruneRestartWindow() {
  const threshold = Date.now() - RESTART_WINDOW_MS
  restartTimes = restartTimes.filter((time) => time >= threshold)
}

function restartDelayMs() {
  pruneRestartWindow()
  if (restartTimes.length < MAX_RESTARTS_PER_WINDOW) {
    return Math.max(RESTART_DELAY_MS, 500)
  }
  const oldestRestart = Math.min(...restartTimes)
  return Math.max(RESTART_DELAY_MS, oldestRestart + RESTART_WINDOW_MS - Date.now() + 1_000)
}

function restartWindowDetail(delayMs) {
  pruneRestartWindow()
  const nextRestartAt = new Date(Date.now() + Math.max(delayMs, 0)).toISOString()
  return {
    restartsInWindow: restartTimes.length,
    maxRestartsPerWindow: MAX_RESTARTS_PER_WINDOW,
    restartWindowMs: RESTART_WINDOW_MS,
    restartDelayMs: delayMs,
    nextRestartAt,
    exitOnRestartLimit: EXIT_ON_RESTART_LIMIT,
  }
}

async function loadHelperConfig() {
  const configPath = existsSync(CONFIG_PATH) ? CONFIG_PATH : EXAMPLE_CONFIG_PATH
  const raw = await fs.readFile(configPath, 'utf8')
  const config = parseJsonText(raw)
  return {
    host: config.host || '127.0.0.1',
    port: Number(config.port || 17891),
  }
}

async function detectRunningHelper() {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 1_000)
  timer.unref?.()
  try {
    const response = await fetch(`http://${helperConfig.host}:${helperConfig.port}/health`, {
      signal: controller.signal,
    })
    if (!response.ok) return false
    const body = await response.json().catch(() => null)
    return body?.service === 'geo-local-helper' ? body : false
  } catch (_) {
    return false
  } finally {
    clearTimeout(timer)
  }
}

async function startChild() {
  const runningHelper = await detectRunningHelper()
  if (runningHelper) {
    log('info', `GEO local helper already running on http://${helperConfig.host}:${helperConfig.port}`, {
      supervised: runningHelper.runtime?.supervised,
      pid: runningHelper.runtime?.pid,
      paired: Boolean(runningHelper.paired),
      platforms: runningHelper.schedulePoll?.platforms || [],
      lastSchedulePoll: runningHelper.schedulePoll?.last || null,
      runtimeStatus: runningHelper.runtimeStatus?.last || null,
    })
    process.exitCode = 0
    return
  }
  restartTimes.push(Date.now())
  childStartedAt = Date.now()
  child = spawn(process.execPath, ['src/server.js'], {
    cwd: new URL('..', import.meta.url),
    env: {
      ...process.env,
      GEO_HELPER_SUPERVISED: '1',
    },
    stdio: ['inherit', 'pipe', 'pipe'],
    windowsHide: false,
  })
  pipeChildOutput(child.stdout, process.stdout)
  pipeChildOutput(child.stderr, process.stderr)

  log('info', 'GEO local helper supervisor started child', {
    pid: child.pid,
    host: helperConfig.host,
    port: helperConfig.port,
    restart: restartWindowDetail(0),
  })

  child.once('exit', (code, signal) => {
    const runtimeMs = childStartedAt ? Date.now() - childStartedAt : null
    const summary = `code=${code ?? '-'} signal=${signal ?? '-'}`
    child = null
    childStartedAt = null
    if (stopping) {
      log('info', `GEO local helper child stopped: ${summary}`, { runtimeMs })
      return
    }
    if (code === EXIT_CODE_PORT_IN_USE) {
      log('error', 'GEO local helper supervisor stopped: port already in use', {
        host: helperConfig.host,
        port: helperConfig.port,
        runtimeMs,
      })
      process.exitCode = EXIT_CODE_PORT_IN_USE
      return
    }
    log('error', `GEO local helper child exited unexpectedly: ${summary}`, { runtimeMs })
    const delayMs = restartDelayMs()
    const detail = restartWindowDetail(delayMs)
    if (delayMs > RESTART_DELAY_MS) {
      log('error', `GEO local helper restart rate limit reached; retrying after ${Math.ceil(delayMs / 1000)}s`, detail)
    }
    if (EXIT_ON_RESTART_LIMIT && delayMs > RESTART_DELAY_MS) {
      log('error', 'GEO local helper supervisor stopped: restart limit exceeded', detail)
      process.exitCode = 1
      return
    }
    log('info', 'GEO local helper supervisor scheduled restart', detail)
    setTimeout(startChild, delayMs)
  })
}

function stop(signal) {
  stopping = true
  if (!child || child.killed) {
    process.exit(0)
    return
  }
  child.once('exit', () => process.exit(0))
  child.kill(signal)
  setTimeout(() => {
    if (child && !child.killed) child.kill('SIGKILL')
    process.exit(0)
  }, 5_000).unref?.()
}

process.once('SIGINT', () => stop('SIGINT'))
process.once('SIGTERM', () => stop('SIGTERM'))

helperConfig = await loadHelperConfig()
log('info', 'GEO local helper supervisor booting', {
  host: helperConfig.host,
  port: helperConfig.port,
  restartDelayMs: RESTART_DELAY_MS,
  maxRestartsPerWindow: MAX_RESTARTS_PER_WINDOW,
  restartWindowMs: RESTART_WINDOW_MS,
  logPath: fileURLToPath(LOG_PATH),
  maxLogBytes: MAX_LOG_BYTES,
  exitOnRestartLimit: EXIT_ON_RESTART_LIMIT,
})
await startChild()
