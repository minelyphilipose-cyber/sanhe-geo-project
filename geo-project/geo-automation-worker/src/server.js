import http from 'node:http'
import { existsSync } from 'node:fs'
import fs from 'node:fs/promises'
import { URL } from 'node:url'
import crypto from 'node:crypto'

const CONFIG_PATH = new URL('../config.local.json', import.meta.url)
const EXAMPLE_CONFIG_PATH = new URL('../config.example.json', import.meta.url)
const RUNTIME_DIR = new URL('../runtime/', import.meta.url)
const MACHINE_ID_PATH = new URL('machine-id', RUNTIME_DIR)
const STARTED_AT = new Date().toISOString()

function parseJsonText(raw) {
  return JSON.parse(String(raw || '').replace(/^\uFEFF/, ''))
}

async function loadConfig() {
  const path = existsSync(CONFIG_PATH) ? CONFIG_PATH : EXAMPLE_CONFIG_PATH
  const raw = await fs.readFile(path, 'utf8')
  const config = parseJsonText(raw)
  return {
    host: String(config.host || '127.0.0.1'),
    port: Number(config.port || 17991),
    backendBase: String(config.backendBase || '').replace(/\/+$/, ''),
    activeProfile: String(config.activeProfile || 'dev'),
    workerName: String(config.workerName || 'GEO Automation Worker'),
    adspower: {
      apiBase: String(config.adspower?.apiBase || 'http://127.0.0.1:50325').replace(/\/+$/, ''),
      apiKey: String(config.adspower?.apiKey || ''),
    },
  }
}

async function getMachineId() {
  try {
    const existing = String(await fs.readFile(MACHINE_ID_PATH, 'utf8')).trim()
    if (existing) return existing
  } catch {
    // First startup has no runtime identity yet.
  }
  const machineId = crypto.randomUUID()
  await fs.mkdir(RUNTIME_DIR, { recursive: true })
  await fs.writeFile(MACHINE_ID_PATH, `${machineId}\n`, 'utf8')
  return machineId
}

function sendJson(res, statusCode, body) {
  const payload = JSON.stringify(body)
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
  })
  res.end(payload)
}

async function readJson(req) {
  const chunks = []
  for await (const chunk of req) chunks.push(chunk)
  const raw = Buffer.concat(chunks).toString('utf8')
  return raw ? parseJsonText(raw) : {}
}

async function route(req, res, config) {
  const url = new URL(req.url || '/', `http://${req.headers.host || `${config.host}:${config.port}`}`)
  if (req.method === 'OPTIONS') {
    sendJson(res, 204, {})
    return
  }
  if (req.method === 'GET' && url.pathname === '/health') {
    sendJson(res, 200, {
      ok: true,
      service: 'geo-automation-worker',
      version: '0.1.0',
      machineId: await getMachineId(),
      activeProfile: config.activeProfile,
      startedAt: STARTED_AT,
      uptimeSeconds: Math.floor(process.uptime()),
      backendBase: config.backendBase || null,
      adspower: {
        apiBase: config.adspower.apiBase,
        apiKeyConfigured: Boolean(config.adspower.apiKey),
      },
    })
    return
  }
  if (req.method === 'GET' && url.pathname === '/v2/extension/current-task') {
    sendJson(res, 404, {
      ok: false,
      error: 'NO_ACTIVE_TASK',
      message: 'No v2 automation task has been claimed by this worker.',
    })
    return
  }
  if (req.method === 'POST' && url.pathname === '/v2/extension/runtime-status') {
    await readJson(req)
    sendJson(res, 202, {
      ok: true,
      accepted: true,
      message: 'Runtime status bridge is scaffolded; backend forwarding is not wired yet.',
    })
    return
  }
  sendJson(res, 404, { ok: false, error: 'not found' })
}

const config = await loadConfig()
const server = http.createServer((req, res) => {
  route(req, res, config).catch((error) => {
    sendJson(res, error.statusCode || 500, {
      ok: false,
      error: error.message || String(error),
    })
  })
})

server.listen(config.port, config.host, () => {
  console.log(`GEO automation worker listening on http://${config.host}:${config.port}`)
})
