import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import fs from 'node:fs/promises'

const RESTART_DELAY_MS = Number(process.env.GEO_HELPER_RESTART_DELAY_MS || 2_000)
const MAX_RESTARTS_PER_WINDOW = Number(process.env.GEO_HELPER_MAX_RESTARTS_PER_WINDOW || 6)
const RESTART_WINDOW_MS = Number(process.env.GEO_HELPER_RESTART_WINDOW_MS || 5 * 60_000)
const EXIT_CODE_PORT_IN_USE = 2
const CONFIG_PATH = new URL('../config.local.json', import.meta.url)
const EXAMPLE_CONFIG_PATH = new URL('../config.example.json', import.meta.url)

let child = null
let stopping = false
let restartTimes = []
let helperConfig = { host: '127.0.0.1', port: 17891 }

function nowIso() {
  return new Date().toISOString()
}

function pruneRestartWindow() {
  const threshold = Date.now() - RESTART_WINDOW_MS
  restartTimes = restartTimes.filter((time) => time >= threshold)
}

function canRestart() {
  pruneRestartWindow()
  return restartTimes.length < MAX_RESTARTS_PER_WINDOW
}

async function loadHelperConfig() {
  const configPath = existsSync(CONFIG_PATH) ? CONFIG_PATH : EXAMPLE_CONFIG_PATH
  const raw = await fs.readFile(configPath, 'utf8')
  const config = JSON.parse(raw)
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
    console.log(`[${nowIso()}] GEO local helper already running on http://${helperConfig.host}:${helperConfig.port}`)
    console.log(`[${nowIso()}] paired=${Boolean(runningHelper.paired)} platforms=${(runningHelper.schedulePoll?.platforms || []).join(',') || '-'}`)
    process.exitCode = 0
    return
  }
  restartTimes.push(Date.now())
  child = spawn(process.execPath, ['src/server.js'], {
    cwd: new URL('..', import.meta.url),
    env: {
      ...process.env,
      GEO_HELPER_SUPERVISED: '1',
    },
    stdio: 'inherit',
    windowsHide: false,
  })

  console.log(`[${nowIso()}] GEO local helper supervisor started child pid=${child.pid}`)

  child.once('exit', (code, signal) => {
    const summary = `code=${code ?? '-'} signal=${signal ?? '-'}`
    child = null
    if (stopping) {
      console.log(`[${nowIso()}] GEO local helper child stopped: ${summary}`)
      return
    }
    if (code === EXIT_CODE_PORT_IN_USE) {
      console.error(`[${nowIso()}] GEO local helper supervisor stopped: port already in use`)
      process.exitCode = EXIT_CODE_PORT_IN_USE
      return
    }
    console.error(`[${nowIso()}] GEO local helper child exited unexpectedly: ${summary}`)
    if (!canRestart()) {
      console.error(`[${nowIso()}] GEO local helper supervisor stopped: restart limit exceeded`)
      process.exitCode = 1
      return
    }
    setTimeout(startChild, Math.max(RESTART_DELAY_MS, 500))
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
await startChild()
