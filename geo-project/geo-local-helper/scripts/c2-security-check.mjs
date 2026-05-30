import crypto from 'node:crypto'
import fs from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const helperBase = (process.env.GEO_HELPER_BASE || 'http://127.0.0.1:17891').replace(/\/+$/, '')
const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sessionPath = path.join(rootDir, 'runtime', 'session.json')
const targetPath = '/v1/poc/tasks'
const jsonOutput = process.argv.includes('--json')

function sha256Hex(value) {
  return crypto.createHash('sha256').update(value, 'utf8').digest('hex')
}

function hmacSha256Base64Url(secret, value) {
  return crypto.createHmac('sha256', secret).update(value, 'utf8').digest('base64url')
}

function canonicalRequest(method, requestPath, bodyHash, timestamp, nonce, helperAccess) {
  return [
    String(method || '').toUpperCase(),
    requestPath,
    bodyHash,
    timestamp,
    nonce,
    helperAccess,
  ].join('\n')
}

async function loadSession() {
  const raw = await fs.readFile(sessionPath, 'utf8')
  const session = JSON.parse(raw)
  if (!session?.sessionId || !session?.hmacSecret) {
    throw new Error(`runtime session is missing sessionId/hmacSecret: ${sessionPath}`)
  }
  return session
}

function signedHeaders(session, options = {}) {
  const method = options.method || 'GET'
  const requestPath = options.path || targetPath
  const body = options.body || ''
  const timestamp = String(options.timestamp ?? Math.floor(Date.now() / 1000))
  const nonce = options.nonce || crypto.randomBytes(16).toString('hex')
  const helperAccess = options.helperAccess || `helper.session.${session.sessionId}`
  const bodyHash = sha256Hex(body)
  const canonical = canonicalRequest(method, requestPath, bodyHash, timestamp, nonce, helperAccess)
  const signature = options.signature || hmacSha256Base64Url(session.hmacSecret, canonical)
  return {
    'X-Geo-Helper-Access': helperAccess,
    'X-Geo-Helper-Timestamp': timestamp,
    'X-Geo-Helper-Nonce': nonce,
    'X-Geo-Helper-Signature': signature,
  }
}

async function helperRequest(headers) {
  const response = await fetch(`${helperBase}${targetPath}`, { headers })
  const text = await response.text()
  let body = null
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    body = text
  }
  return {
    status: response.status,
    body,
    message: typeof body?.error === 'string' ? body.error : text,
  }
}

function includesAny(value, fragments) {
  const text = String(value || '')
  return fragments.some((fragment) => text.includes(fragment))
}

async function runCheck(name, invoke, expect) {
  const result = await invoke()
  const ok = expect(result)
  return { name, ok, ...result }
}

async function main() {
  const session = await loadSession()
  const replayHeaders = signedHeaders(session, { nonce: crypto.randomBytes(16).toString('hex') })

  const checks = []
  checks.push(await runCheck(
    'valid_signed_request_accepts',
    () => helperRequest(signedHeaders(session)),
    (result) => result.status === 200 && result.body?.ok === true,
  ))
  checks.push(await runCheck(
    'expired_timestamp_rejected',
    () => helperRequest(signedHeaders(session, { timestamp: Math.floor(Date.now() / 1000) - 600 })),
    (result) => result.status === 401 && includesAny(result.message, ['timestamp expired']),
  ))
  checks.push(await runCheck(
    'wrong_signature_rejected',
    () => helperRequest(signedHeaders(session, { signature: 'bad-signature' })),
    (result) => result.status === 401 && includesAny(result.message, ['invalid helper request signature']),
  ))
  checks.push(await runCheck(
    'wrong_session_access_rejected',
    () => helperRequest(signedHeaders(session, { helperAccess: 'helper.session.0' })),
    (result) => result.status === 401 && includesAny(result.message, ['invalid helper access token']),
  ))

  const firstReplay = await helperRequest(replayHeaders)
  const secondReplay = await helperRequest(replayHeaders)
  checks.push({
    name: 'nonce_replay_rejected',
    ok: firstReplay.status === 200 && secondReplay.status === 409
      && includesAny(secondReplay.message, ['replayed helper request nonce']),
    first: firstReplay,
    second: secondReplay,
  })

  const ok = checks.every((check) => check.ok)
  const summary = {
    c2SecurityCheck: ok ? 'ok' : 'failed',
    helperBase,
    checks,
  }
  if (jsonOutput) {
    console.log(JSON.stringify(summary, null, 2))
  } else {
    printHumanSummary(summary)
  }
  if (!ok) process.exitCode = 1
}

function printHumanSummary(summary) {
  console.log(`C2_SECURITY_CHECK=${summary.c2SecurityCheck}`)
  console.log(`HELPER_BASE=${summary.helperBase}`)
  for (const check of summary.checks) {
    const status = statusText(check)
    const message = messageText(check)
    console.log(`${check.ok ? 'PASS' : 'FAIL'} ${check.name} ${status}${message ? ` ${message}` : ''}`)
  }
}

function statusText(check) {
  if (check.status) return `status=${check.status}`
  if (check.first || check.second) {
    return `first=${check.first?.status || '-'} second=${check.second?.status || '-'}`
  }
  return ''
}

function messageText(check) {
  if (check.name === 'valid_signed_request_accepts') {
    return check.body?.ok === true ? 'message="ok"' : ''
  }
  if (check.message) return `message="${check.message}"`
  if (check.second?.message) return `message="${check.second.message}"`
  return ''
}

main().catch((error) => {
  if (jsonOutput) {
    console.error(JSON.stringify({
      c2SecurityCheck: 'failed',
      helperBase,
      error: error.message,
    }, null, 2))
  } else {
    console.error('C2_SECURITY_CHECK=failed')
    console.error(`HELPER_BASE=${helperBase}`)
    console.error(`ERROR ${error.message}`)
  }
  process.exitCode = 1
})
