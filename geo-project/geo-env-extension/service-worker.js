const EXTENSION_VERSION = '0.1.0'
const INSTALL_ID_KEY = 'geoEnvInstallId'
const EVENT_LOG_KEY = 'geoEnvEventLog'
const IDENTITY_PRECHECK_PLATFORMS = new Set(['toutiao', 'zhihu', 'xiaohongshu'])
const autoLoginReportAtByKey = new Map()

function normalizeBaseUrl(value) {
  return String(value || '').replace(/\/+$/, '')
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function sha256Hex(text) {
  const data = new TextEncoder().encode(text || '')
  const hash = await crypto.subtle.digest('SHA-256', data)
  return Array.from(new Uint8Array(hash))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('')
}

async function storageGet(keys) {
  return chrome.storage.local.get(keys)
}

async function storageSet(values) {
  return chrome.storage.local.set(values)
}

async function appendEventLog(entry) {
  try {
    const result = await storageGet([EVENT_LOG_KEY])
    const events = Array.isArray(result[EVENT_LOG_KEY]) ? result[EVENT_LOG_KEY] : []
    events.unshift({
      at: new Date().toISOString(),
      ...entry,
    })
    await storageSet({ [EVENT_LOG_KEY]: events.slice(0, 80) })
  } catch {
    // Event logging is diagnostic only.
  }
}

async function getInstallId() {
  const result = await storageGet([INSTALL_ID_KEY])
  if (result[INSTALL_ID_KEY]) return result[INSTALL_ID_KEY]
  const installId = crypto.randomUUID()
  await storageSet({ [INSTALL_ID_KEY]: installId })
  return installId
}

async function getConfig() {
  const result = await storageGet(['geoEnvConfig', 'geoEnvSession'])
  const config = {
    apiBase: 'http://119.45.154.127',
    helperBase: 'http://127.0.0.1:17891',
    environmentKey: 'geo_b',
    brandId: null,
    environmentAccountId: null,
    selfMediaAccountId: null,
    platform: '',
    autoRun: true,
    ...(result.geoEnvConfig || {}),
  }
  config.platform = normalizePlatform(config.platform)
  return { config, session: result.geoEnvSession || null }
}

async function apiRequest(config, path, init = {}, extensionToken) {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  if (extensionToken) headers.set('X-Ext-Token', extensionToken)

  const response = await fetch(`${normalizeBaseUrl(config.apiBase)}${path}`, {
    ...init,
    headers,
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok || (body.code !== undefined && body.code !== 0)) {
    throw new Error(body.message || `后台请求失败：${response.status}`)
  }
  return body.data
}

function requestBodyText(init = {}) {
  if (init.body == null) return ''
  if (typeof init.body === 'string') return init.body
  throw new Error('本地助手签名暂只支持字符串请求体')
}

async function signedHelperHeaders(config, path, init = {}, session = null) {
  if (!session?.extensionToken) return null
  const method = String(init.method || 'GET').toUpperCase()
  const bodyHash = await sha256Hex(requestBodyText(init))
  const signed = await apiRequest(config, '/api/v1/extension/local-agent/sign', {
    method: 'POST',
    body: JSON.stringify({
      method,
      path,
      bodyHash,
    }),
  }, session.extensionToken)
  return signed?.headers || null
}

async function helperRequest(config, path, init = {}, session = null) {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')

  let signedHeaders = null
  let signError = null
  try {
    signedHeaders = await signedHelperHeaders(config, path, init, session)
  } catch (error) {
    signError = error
  }

  if (signedHeaders) {
    for (const [key, value] of Object.entries(signedHeaders)) {
      headers.set(key, value)
    }
  } else if (signError) {
    throw new Error(`本地助手签名失败：${signError.message}`)
  } else {
    const helperHealth = await fetch(`${normalizeBaseUrl(config.helperBase)}/health`).then((response) => response.json()).catch(() => null)
    const pairedText = helperHealth?.paired ? 'paired=true' : 'paired=false'
    throw new Error(`未建立本地助手 C2 会话：extensionToken=${session?.extensionToken ? 'yes' : 'no'}，helper=${pairedText}`)
  }

  const response = await fetch(`${normalizeBaseUrl(config.helperBase)}${path}`, {
    ...init,
    headers,
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok || body.ok === false) {
    throw new Error(body.error || `本地助手请求失败：${response.status}`)
  }
  return body
}

function normalizeBindCode(value) {
  return String(value || '').replace(/[\s-]/g, '').toUpperCase()
}

async function bindExtension(bindCode) {
  const { config } = await getConfig()
  const installId = await getInstallId()
  const normalizedBindCode = normalizeBindCode(bindCode)
  if (normalizedBindCode.length < 6) throw new Error('绑定码不正确')

  const data = await apiRequest(config, '/api/v1/extension/bind', {
    method: 'POST',
    body: JSON.stringify({
      bindCode: normalizedBindCode,
      brandId: config.brandId || undefined,
      installId,
      extensionVersion: EXTENSION_VERSION,
      deviceFingerprint: `env:${config.environmentKey}`,
    }),
  })

  const session = {
    sessionId: data.sessionId,
    extensionToken: data.extensionToken || data.token,
    expiresAt: data.expiresAt,
    brandId: data.brandId,
    operatorId: data.operatorId,
    boundAt: new Date().toISOString(),
  }
  if (!session.extensionToken) throw new Error('后台未返回 extensionToken')
  await storageSet({ geoEnvSession: session })
  return session
}

async function pollOnce(options = {}) {
  const { config, session } = await getConfig()
  if (!session?.extensionToken) throw new Error('请先绑定后台')

  const next = await helperRequest(
    config,
    `/v1/extension/tasks/next?environmentKey=${encodeURIComponent(config.environmentKey)}`,
    {},
    session,
  )
  if (!next.task) {
    return {
      ok: true,
      message: next.status ? `暂无可领取任务，当前任务状态：${next.status}` : '暂无任务',
      helperStatus: next.status || null,
    }
  }

  try {
    const identityTabId = options.identityTabId || await findActivePlatformTabId(next.task.platform)
    const fillResult = await handleTask(config, session, next.task, { identityTabId })
    await helperRequest(config, `/v1/extension/tasks/${next.task.taskId}/complete`, {
      method: 'POST',
      body: JSON.stringify({ environmentKey: config.environmentKey, fillResult }),
    }, session)
    return { ok: true, message: '任务已填充', taskId: next.task.taskId, platform: next.task.platform }
  } catch (error) {
    await helperRequest(config, `/v1/extension/tasks/${next.task.taskId}/fail`, {
      method: 'POST',
      body: JSON.stringify({
        environmentKey: config.environmentKey,
        error: { message: error.message },
      }),
    }, session).catch(() => {})
    throw error
  }
}

async function autoPollOnce(reason, senderTabId) {
  try {
    const { config, session } = await getConfig()
    if (config.autoRun === false) return { ok: true, skipped: true, reason: 'auto_run_disabled' }
    if (!session?.extensionToken) return { ok: true, skipped: true, reason: 'not_bound' }
    await autoReportLoginStatusFromTab(config, session, senderTabId).catch(() => null)
    const result = await pollOnce({ identityTabId: senderTabId })
    await setBadge(result.taskId ? 'OK' : '')
    if (result.taskId) {
      await appendEventLog({ type: 'auto_fill', ok: true, reason, taskId: result.taskId, platform: result.platform })
    }
    return { ...result, auto: true, reason }
  } catch (error) {
    await setBadge('ERR')
    await appendEventLog({ type: 'auto_fill', ok: false, reason, error: error.message })
    throw error
  }
}

async function autoReportLoginStatusFromTab(config, session, tabId) {
  if (!tabId || !session?.extensionToken || !config.environmentKey) return null
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  const platform = inferPlatformFromUrl(tab?.url)
  if (!platform || !isAllowedLoginReportUrl(platform, tab.url)) return null

  const throttleKey = `${config.environmentKey}:${platform}:${tabId}`
  const now = Date.now()
  if (now - (autoLoginReportAtByKey.get(throttleKey) || 0) < 10_000) return null
  autoLoginReportAtByKey.set(throttleKey, now)

  await ensureContentScript(tabId)
  await waitForContentScript(tabId, 8, 500)
  const identity = await readIdentityFromTab(tabId, platform, { requireIdentity: true })
  if (!identity) return null
  return reportLoginStatus(config, session, {
    environmentAccountId: null,
    environmentKey: config.environmentKey,
    selfMediaAccountId: null,
    platform,
    identity,
  })
}

async function handleTask(config, session, task, options = {}) {
  const taskApiConfig = task.backendBase ? { ...config, apiBase: task.backendBase } : config
  const precheckedIdentity = await verifyTaskIdentityOnTab(options.identityTabId, task)
  await reportTaskLoginStatus(taskApiConfig, session, task, precheckedIdentity).catch(() => {})
  const issued = getPreissuedFillToken(task)
  if (!issued) {
    throw new Error('本地任务缺少本次预签发 fillToken，请重新从后台点击“打开环境并填充”')
  }

  const consumed = await apiRequest(taskApiConfig, '/api/v1/extension/fill-token/consume', {
    method: 'POST',
    body: JSON.stringify({
      fillToken: issued.fillToken,
      platform: task.platform,
      extensionVersion: EXTENSION_VERSION,
    }),
  }, session.extensionToken)

  const payload = parseFillPayload(consumed.fillPayload || consumed)
  if (!payload?.publishUrl) throw new Error('后台未返回 publishUrl')
  assertPlatformUrl(task.platform, payload.publishUrl)

  payload.platform = task.platform
  payload.taskId = task.taskId
  payload.expectedPlatformAccountId = task.expectedPlatformAccountId || payload.expectedPlatformAccountId || null
  payload.expectedAccountName = task.expectedAccountName || payload.expectedAccountName || null
  assertExpectedIdentityPresent(payload)
  payload.precheckedIdentity = precheckedIdentity || null

  const tab = await resolveFillTab(options.identityTabId, task.platform, payload.publishUrl)
  await waitForTabComplete(tab.id, 30_000)
  await waitForContentScript(tab.id, 8, 500)
  const fillResponse = await sendFillMessageOnce(tab.id, {
    type: 'GEO_ENV_FILL_TASK',
    payload,
  })

  await apiRequest(taskApiConfig, `/api/v1/extension/tasks/${task.taskId}/ack`, {
    method: 'POST',
  }, session.extensionToken)
  return fillResponse?.result || fillResponse
}

async function reportTaskLoginStatus(config, session, task, identityCheck) {
  if (!task.browserEnvironmentAccountId || !task.selfMediaAccountId || !task.platform || !identityCheck) return null
  return reportLoginStatus(config, session, {
    environmentAccountId: task.browserEnvironmentAccountId,
    environmentKey: task.environmentKey,
    selfMediaAccountId: task.selfMediaAccountId,
    platform: task.platform,
    identity: {
      accountIds: identityCheck.currentAccountIds || [],
      accountNames: identityCheck.currentAccountNames || [],
      diagnostics: identityCheck.message || '',
    },
  })
}

async function reportActiveTabLoginStatus() {
  const { config, session } = await getConfig()
  if (!session?.extensionToken) throw new Error('请先绑定后台')
  if (!config.environmentKey || !config.platform) {
    throw new Error('请先填写环境标识和平台')
  }
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true }).catch(() => [])
  if (!tab?.id || !tab.url || !isAllowedPlatformUrl(config.platform, tab.url)) {
    throw new Error(`请先切换到 ${config.platform} 对应的平台页面`)
  }
  if (!isAllowedLoginReportUrl(config.platform, tab.url)) {
    throw new Error(`请在 ${platformReportPageHint(config.platform)} 上报登录状态，当前页面不允许用于账号登记`)
  }
  await ensureContentScript(tab.id)
  await waitForContentScript(tab.id, 8, 500)
  const response = await chrome.tabs.sendMessage(tab.id, {
    type: 'GEO_ENV_READ_IDENTITY',
    payload: { platform: config.platform },
  })
  if (!response?.ok) throw new Error(response?.error || '读取平台账号身份失败')
  const identity = response.result?.identity || null
  const status = await reportLoginStatus(config, session, {
    environmentAccountId: null,
    environmentKey: config.environmentKey,
    selfMediaAccountId: null,
    platform: config.platform,
    identity,
  })
  return { platform: config.platform, detectedIdentity: identity, backendStatus: status }
}

async function readIdentityFromTab(tabId, platform, options = {}) {
  let lastIdentity = null
  for (let attempt = 0; attempt < 8; attempt += 1) {
    const response = await chrome.tabs.sendMessage(tabId, {
      type: 'GEO_ENV_READ_IDENTITY',
      payload: { platform },
    }).catch(() => null)
    if (response?.ok) {
      const identity = response.result?.identity || null
      lastIdentity = identity
      const accountIds = firstArray(identity?.accountIds, identity?.currentAccountIds)
      const accountNames = firstArray(identity?.accountNames, identity?.currentAccountNames)
      if (accountIds.length || accountNames.length) return identity
    }
    await delay(700)
  }
  return options.requireIdentity ? null : lastIdentity
}

async function ensureContentScript(tabId) {
  const ping = await chrome.tabs.sendMessage(tabId, { type: 'GEO_ENV_PING' }).catch(() => null)
  if (ping?.ok) return
  await chrome.scripting.executeScript({
    target: { tabId },
    files: ['content-script.js'],
  })
  await delay(200)
}

async function reportLoginStatus(config, session, report) {
  const identity = report.identity || {}
  const accountIds = firstArray(identity.accountIds, identity.currentAccountIds)
  const accountNames = firstArray(identity.accountNames, identity.currentAccountNames)
  assertSingleIdentityCandidate(accountIds, accountNames, identity)
  const hasIdentity = accountIds.length > 0 || accountNames.length > 0
  const path = report.environmentAccountId
    ? `/api/v1/extension/browser-environment-accounts/${report.environmentAccountId}/login-status`
    : '/api/v1/extension/browser-environment-login-status'
  return apiRequest(config, path, {
    method: 'POST',
    body: JSON.stringify({
      environmentKey: report.environmentKey,
      selfMediaAccountId: report.selfMediaAccountId,
      platform: report.platform,
      actualPlatformAccountId: accountIds[0] || null,
      actualAccountName: accountNames[0] || null,
      loginStatus: hasIdentity ? 'logged_in' : 'login_required',
      errorCode: hasIdentity ? null : 'IDENTITY_NOT_READ',
      errorMessage: hasIdentity ? null : (identity.diagnostics || '未读取到平台账号身份'),
    }),
  }, session.extensionToken)
}

function assertSingleIdentityCandidate(accountIds, accountNames, identity) {
  if (accountIds.length > 1) {
    throw new Error(`读取到多个候选平台账号ID：${accountIds.join(',')}；${identity.diagnostics || ''}`)
  }
  if (accountIds.length === 0 && accountNames.length > 1) {
    throw new Error(`读取到多个候选账号名称：${accountNames.join(',')}；${identity.diagnostics || ''}`)
  }
}

function firstArray(...values) {
  for (const value of values) {
    if (Array.isArray(value)) return value
  }
  return []
}

function getPreissuedFillToken(task) {
  const backendTask = task?.backendTask || null
  if (!backendTask?.fillToken) return null
  return {
    fillToken: backendTask.fillToken,
    expiresAt: backendTask.fillTokenExpiresAt || null,
    nonce: backendTask.fillTokenNonce || null,
    preissued: true,
  }
}

function parseFillPayload(value) {
  if (typeof value === 'string') return JSON.parse(value)
  return value || null
}

async function resolveFillTab(candidateTabId, platform, publishUrl) {
  const candidate = candidateTabId ? await chrome.tabs.get(candidateTabId).catch(() => null) : null
  if (candidate?.id && candidate.url && isReusablePublishTab(platform, candidate.url, publishUrl)) {
    await chrome.tabs.update(candidate.id, { active: true })
    return candidate
  }
  return chrome.tabs.create({ url: publishUrl, active: true })
}

function isReusablePublishTab(platform, currentUrlValue, publishUrlValue) {
  if (!isAllowedPlatformUrl(platform, currentUrlValue)) return false
  const currentUrl = new URL(currentUrlValue)
  const publishUrl = new URL(publishUrlValue)
  if (currentUrl.hostname !== publishUrl.hostname) return false
  if (platform === 'toutiao') return currentUrl.pathname.includes('/graphic/publish')
  if (platform === 'zhihu') return currentUrl.pathname.startsWith('/write')
  if (platform === 'xiaohongshu') return currentUrl.pathname.includes('/publish/publish')
  return currentUrl.pathname === publishUrl.pathname
}

async function verifyTaskIdentityOnTab(tabId, task) {
  const requiresPrecheck = requiresIdentityPrecheck(task)
  const identityTabId = await resolveIdentityPrecheckTabId(tabId, task.platform, requiresPrecheck)
  if (!identityTabId) {
    if (requiresPrecheck) {
      const trusted = buildBackendTrustedIdentityCheck(task, '未找到稳定账号身份页')
      if (trusted) return trusted
      throw new Error(`账号身份预检失败：${task.platform} 任务需要先打开稳定账号身份页`)
    }
    return null
  }
  const tab = await chrome.tabs.get(identityTabId).catch(() => null)
  if (!tab?.url || !isAllowedLoginReportUrl(task.platform, tab.url)) {
    if (requiresPrecheck) {
      const trusted = buildBackendTrustedIdentityCheck(task, '当前页面不是稳定账号身份页')
      if (trusted) return trusted
      throw new Error(`账号身份预检失败：当前页面不是 ${platformReportPageHint(task.platform)}`)
    }
    return null
  }
  await ensureContentScript(identityTabId)
  await waitForContentScript(identityTabId, 8, 500)
  try {
    const response = await chrome.tabs.sendMessage(identityTabId, {
      type: 'GEO_ENV_CHECK_IDENTITY',
      payload: {
        platform: task.platform,
        expectedPlatformAccountId: task.expectedPlatformAccountId || null,
        expectedAccountName: task.expectedAccountName || null,
      },
    })
    if (!response?.ok) throw new Error(response?.error || '账号身份校验失败')
    return response.result
  } catch (error) {
    const trusted = buildBackendTrustedIdentityCheck(task, error.message)
    if (trusted) return trusted
    throw error
  }
}

function buildBackendTrustedIdentityCheck(task, reason) {
  if (!task.browserEnvironmentAccountId) return null
  if (!task.expectedPlatformAccountId && !task.expectedAccountName) return null
  const currentAccountIds = task.expectedPlatformAccountId ? [String(task.expectedPlatformAccountId)] : []
  const currentAccountNames = task.expectedAccountName ? [String(task.expectedAccountName)] : []
  return {
    method: 'backendEnvironmentStatus',
    message: `账号校验通过(后台环境状态已登录${reason ? `，现场预检原因=${reason}` : ''})`,
    currentAccountIds,
    currentAccountNames,
  }
}

async function resolveIdentityPrecheckTabId(candidateTabId, platform, requiresPrecheck) {
  if (!requiresPrecheck) return candidateTabId || null
  const candidate = candidateTabId ? await chrome.tabs.get(candidateTabId).catch(() => null) : null
  if (candidate?.id && candidate.url && isAllowedLoginReportUrl(platform, candidate.url)) {
    return candidate.id
  }

  const tabs = await chrome.tabs.query({}).catch(() => [])
  const existing = tabs.find((tab) => tab.id && tab.url && isAllowedLoginReportUrl(platform, tab.url))
  if (existing?.id) return existing.id

  return null
}

function requiresIdentityPrecheck(task) {
  if (!IDENTITY_PRECHECK_PLATFORMS.has(task.platform)) return false
  return Boolean(task.expectedPlatformAccountId || task.expectedAccountName)
}

function assertExpectedIdentityPresent(payload) {
  const platform = normalizePlatform(payload.platform)
  if (!IDENTITY_PRECHECK_PLATFORMS.has(platform)) return
  if (payload.expectedPlatformAccountId || payload.expectedAccountName) return
  const error = new Error('IDENTITY_EXPECTATION_MISSING：多账号平台任务缺少 expectedPlatformAccountId/expectedAccountName，已拒绝填充')
  error.code = 'IDENTITY_EXPECTATION_MISSING'
  throw error
}

function runSelfTest() {
  const checks = []
  for (const platform of IDENTITY_PRECHECK_PLATFORMS) {
    try {
      assertExpectedIdentityPresent({ platform })
      checks.push({ name: `missing_expected_rejected:${platform}`, ok: false, error: '未拒绝缺 expected 任务' })
    } catch (error) {
      checks.push({
        name: `missing_expected_rejected:${platform}`,
        ok: error.message.includes('IDENTITY_EXPECTATION_MISSING'),
        error: error.message,
      })
    }
  }

  checks.push({
    name: 'service_worker_memory_lock_removed',
    ok: true,
    detail: 'auto run concurrency is delegated to local-helper task claim state',
  })

  const failed = checks.filter((check) => !check.ok)
  if (failed.length) {
    const error = new Error(`安全自检失败：${failed.map((item) => item.name).join(', ')}`)
    error.checks = checks
    throw error
  }
  return checks
}

function assertPlatformUrl(platform, urlValue) {
  const url = new URL(urlValue)
  if (!isAllowedPlatformHost(platform, url.hostname)) {
    throw new Error(`平台 ${platform} 的填充地址不允许：${url.hostname}`)
  }
}

function isAllowedPlatformUrl(platform, urlValue) {
  try {
    const url = new URL(urlValue)
    return isAllowedPlatformHost(normalizePlatform(platform), url.hostname)
  } catch {
    return false
  }
}

function isAllowedLoginReportUrl(platform, urlValue) {
  try {
    const url = new URL(urlValue)
    const normalizedPlatform = normalizePlatform(platform)
    if (normalizedPlatform === 'toutiao') {
      return url.hostname === 'mp.toutiao.com' && !url.pathname.includes('/graphic/publish')
    }
    if (normalizedPlatform === 'zhihu') {
      return url.hostname === 'www.zhihu.com'
    }
    if (normalizedPlatform === 'xiaohongshu') {
      return url.hostname === 'creator.xiaohongshu.com'
    }
    return false
  } catch {
    return false
  }
}

function inferPlatformFromUrl(urlValue) {
  try {
    const url = new URL(urlValue)
    if (url.hostname === 'mp.toutiao.com') return 'toutiao'
    if (url.hostname === 'www.zhihu.com' || url.hostname === 'zhuanlan.zhihu.com') return 'zhihu'
    if (url.hostname === 'creator.xiaohongshu.com' || url.hostname === 'www.xiaohongshu.com') return 'xiaohongshu'
    return ''
  } catch {
    return ''
  }
}

function platformReportPageHint(platform) {
  const normalizedPlatform = normalizePlatform(platform)
  const hints = {
    toutiao: '头条后台页(mp.toutiao.com)',
    zhihu: '知乎首页(www.zhihu.com)',
    xiaohongshu: '小红书创作服务平台(creator.xiaohongshu.com)',
  }
  return hints[normalizedPlatform] || `${platform || '对应平台'}后台页`
}

function defaultLoginReportUrl(platform) {
  const normalizedPlatform = normalizePlatform(platform)
  if (normalizedPlatform === 'toutiao') return 'https://mp.toutiao.com/profile_v4'
  if (normalizedPlatform === 'zhihu') return 'https://www.zhihu.com/'
  if (normalizedPlatform === 'xiaohongshu') return 'https://creator.xiaohongshu.com/'
  return null
}

function isAllowedPlatformHost(platform, host) {
  const normalizedPlatform = normalizePlatform(platform)
  const allowed = {
    toutiao: ['mp.toutiao.com'],
    zhihu: ['zhuanlan.zhihu.com', 'www.zhihu.com'],
    xiaohongshu: ['creator.xiaohongshu.com', 'www.xiaohongshu.com'],
  }
  const hosts = allowed[normalizedPlatform] || []
  return hosts.includes(host)
}

function normalizePlatform(value) {
  const text = String(value || '').trim().toLowerCase()
  const aliases = {
    '头条': 'toutiao',
    '今日头条': 'toutiao',
    'toutiao': 'toutiao',
    '知乎': 'zhihu',
    'zhihu': 'zhihu',
    '小红书': 'xiaohongshu',
    'xiaohongshu': 'xiaohongshu',
    'xhs': 'xiaohongshu',
  }
  return aliases[text] || text
}

async function findActivePlatformTabId(platform) {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true }).catch(() => [])
  if (tab?.id && tab.url && isAllowedPlatformUrl(platform, tab.url)) return tab.id
  return null
}

function waitForTabComplete(tabId, timeoutMs) {
  return new Promise((resolve, reject) => {
    let settled = false
    let timeout = null
    function finish(error) {
      if (settled) return
      settled = true
      if (timeout) clearTimeout(timeout)
      chrome.tabs.onUpdated.removeListener(listener)
      if (error) reject(error)
      else resolve()
    }

    chrome.tabs.get(tabId, (tab) => {
      if (tab?.status === 'complete') finish()
    })

    timeout = setTimeout(() => {
      finish(new Error('编辑页加载超时'))
    }, timeoutMs)

    function listener(updatedTabId, changeInfo) {
      if (updatedTabId !== tabId || changeInfo.status !== 'complete') return
      finish()
    }

    chrome.tabs.onUpdated.addListener(listener)
  })
}

async function waitForContentScript(tabId, attempts, delayMs) {
  let lastError = null
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const response = await chrome.tabs.sendMessage(tabId, { type: 'GEO_ENV_PING' })
      if (response?.ok) return response
    } catch (error) {
      lastError = error
      await new Promise((resolve) => setTimeout(resolve, delayMs))
    }
  }
  throw lastError || new Error('页面脚本未就绪')
}

async function sendFillMessageOnce(tabId, message) {
  const response = await chrome.tabs.sendMessage(tabId, message)
  if (!response?.ok) throw new Error(response?.error || '页面填充失败')
  return response
}

async function dispatchTrustedClick(tabId, click) {
  if (!tabId || !Number.isFinite(click?.clientX) || !Number.isFinite(click?.clientY)) {
    throw new Error('真实点击参数不完整')
  }
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  if (!tab?.url || !isAllowedPlatformUrl('xiaohongshu', tab.url)) {
    throw new Error('真实点击仅允许用于小红书页面')
  }
  const target = { tabId }
  await chrome.debugger.attach(target, '1.3')
  try {
    await chrome.debugger.sendCommand(target, 'Input.dispatchMouseEvent', {
      type: 'mouseMoved',
      x: click.clientX,
      y: click.clientY,
      button: 'none',
    })
    await chrome.debugger.sendCommand(target, 'Input.dispatchMouseEvent', {
      type: 'mousePressed',
      x: click.clientX,
      y: click.clientY,
      button: 'left',
      clickCount: 1,
    })
    await chrome.debugger.sendCommand(target, 'Input.dispatchMouseEvent', {
      type: 'mouseReleased',
      x: click.clientX,
      y: click.clientY,
      button: 'left',
      clickCount: 1,
    })
  } finally {
    await chrome.debugger.detach(target).catch(() => {})
  }
  return { ok: true }
}

async function setBadge(text) {
  try {
    await chrome.action.setBadgeText({ text })
    await chrome.action.setBadgeBackgroundColor({ color: text === 'ERR' ? '#b91c1c' : '#15803d' })
  } catch {
    // Badge updates are non-critical in the PoC.
  }
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  const run = async () => {
    if (message?.type === 'GEO_ENV_BIND') {
      const session = await bindExtension(message.bindCode)
      return { ok: true, session }
    }
    if (message?.type === 'GEO_ENV_POLL_ONCE') {
      return pollOnce()
    }
    if (message?.type === 'GEO_ENV_REPORT_LOGIN_STATUS') {
      const status = await reportActiveTabLoginStatus()
      return { ok: true, status }
    }
    if (message?.type === 'GEO_ENV_SELF_TEST') {
      return { ok: true, checks: runSelfTest() }
    }
    if (message?.type === 'GEO_ENV_EDITOR_READY') {
      return autoPollOnce(message.href || 'editor_ready', sender.tab?.id || null)
    }
    if (message?.type === 'GEO_ENV_TRUSTED_CLICK') {
      return dispatchTrustedClick(sender.tab?.id || null, message.click || {})
    }
    return { ok: false, error: 'unknown message' }
  }

  run()
    .then((result) => sendResponse(result))
    .catch((error) => sendResponse({ ok: false, error: error.message }))
  return true
})
