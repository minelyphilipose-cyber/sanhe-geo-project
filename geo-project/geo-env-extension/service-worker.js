importScripts('fill-result.js', 'platform-baijiahao.js', 'platform-xiaohongshu.js', 'platform-zhihu.js')

const EXTENSION_VERSION = '0.1.0'
const INSTALL_ID_KEY = 'geoEnvInstallId'
const EVENT_LOG_KEY = 'geoEnvEventLog'
const IDENTITY_PRECHECK_PLATFORMS = new Set(['toutiao', 'zhihu', 'xiaohongshu', 'baijiahao'])
const autoLoginReportAtByKey = new Map()
const MAX_IMAGE_FETCH_BYTES = 20 * 1024 * 1024

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
    const error = new Error(body.message || `后台请求失败：${response.status}`)
    error.status = response.status
    error.code = body.code
    throw error
  }
  return body.data
}

function isExtensionUnauthorized(error) {
  const message = String(error?.message || '')
  return error?.status === 401
    || error?.code === 70002
    || message.toLowerCase() === 'unauthorized'
    || message.includes('扩展后台绑定已失效')
}

async function clearExtensionSession(expectedToken = null) {
  if (expectedToken) {
    const current = await storageGet(['geoEnvSession'])
    const currentToken = current.geoEnvSession?.extensionToken || ''
    if (currentToken && currentToken !== expectedToken) return
  }
  await storageSet({ geoEnvSession: null })
}

async function refreshExtensionSession(config, session, options = {}) {
  if (!session?.extensionToken) return session
  const refreshedAt = Date.parse(session.refreshedAt || '')
  if (!options.force && Number.isFinite(refreshedAt) && Date.now() - refreshedAt < 5 * 60 * 1000) {
    return session
  }

  try {
    const refreshed = await apiRequest(config, '/api/v1/extension/token/refresh', {
      method: 'POST',
      body: JSON.stringify({ extensionVersion: EXTENSION_VERSION }),
    }, session.extensionToken)
    const nextSession = {
      ...session,
      sessionId: refreshed?.sessionId || session.sessionId,
      extensionToken: refreshed?.token || session.extensionToken,
      expiresAt: refreshed?.expiresAt || session.expiresAt,
      refreshedAt: new Date().toISOString(),
    }
    await storageSet({ geoEnvSession: nextSession })
    return nextSession
  } catch (error) {
    if (isExtensionUnauthorized(error)) {
      await clearExtensionSession(session.extensionToken)
      throw new Error('扩展后台绑定已失效，请在扩展弹窗重新绑定后台')
    }
    throw error
  }
}

function requestBodyText(init = {}) {
  if (init.body == null) return ''
  if (typeof init.body === 'string') return init.body
  throw new Error('本地助手签名暂只支持字符串请求体')
}

async function signedHelperHeaders(config, path, init = {}, session = null) {
  if (!session?.extensionToken) return null
  const activeSession = await refreshExtensionSession(config, session)
  const method = String(init.method || 'GET').toUpperCase()
  const bodyHash = await sha256Hex(requestBodyText(init))
  const body = JSON.stringify({
    method,
    path,
    bodyHash,
  })

  try {
    const signed = await apiRequest(config, '/api/v1/extension/local-agent/sign', {
      method: 'POST',
      body,
    }, activeSession.extensionToken)
    return signed?.headers || null
  } catch (error) {
    if (!isExtensionUnauthorized(error)) throw error
    const refreshedSession = await refreshExtensionSession(config, activeSession, { force: true })
    const signed = await apiRequest(config, '/api/v1/extension/local-agent/sign', {
      method: 'POST',
      body,
    }, refreshedSession.extensionToken)
    return signed?.headers || null
  }
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
  await refreshRuntimeConfig({ reason: 'bind' }).catch((error) => appendEventLog({
    type: 'runtime_config',
    ok: false,
    reason: 'bind',
    error: error.message,
  }))
  return session
}

async function pollOnce(options = {}) {
  await refreshRuntimeConfig({
    reason: options.reason || 'poll',
    platform: options.platform || '',
  }).catch((error) => {
    if (isExtensionUnauthorized(error)) return null
    return appendEventLog({
      type: 'runtime_config',
      ok: false,
      reason: options.reason || 'poll',
      error: error.message,
    })
  })
  const { config, session } = await getConfig()
  if (!session?.extensionToken) throw new Error('请先绑定后台')
  const platform = options.platform || ''

  let next = null
  let activeConfig = config
  if (platform) {
    next = await helperRequest(
      config,
      `/v1/extension/tasks/next?platform=${encodeURIComponent(platform)}`,
      {},
      session,
    )
    if (next.task?.environmentKey) {
      activeConfig = { ...config, environmentKey: next.task.environmentKey }
    }
  }

  const candidateKeys = next?.task ? [] : await resolveCandidateEnvironmentKeys(config, session, platform)
  for (const environmentKey of candidateKeys) {
    const scopedConfig = { ...config, environmentKey }
    const candidate = await helperRequest(
      scopedConfig,
      `/v1/extension/tasks/next?environmentKey=${encodeURIComponent(environmentKey)}${platform ? `&platform=${encodeURIComponent(platform)}` : ''}`,
      {},
      session,
    )
    if (candidate.task || candidate.status) {
      next = candidate
      activeConfig = scopedConfig
      break
    }
    next = candidate
  }
  if (!next.task) {
    return {
      ok: true,
      message: next.status ? `暂无可领取任务，当前任务状态：${next.status}` : '暂无任务',
      helperStatus: next.status || null,
    }
  }

  try {
    const identityTabId = options.identityTabId || await findActivePlatformTabId(next.task.platform)
    const fillResult = await handleTask(activeConfig, session, next.task, { identityTabId })
    await helperRequest(activeConfig, `/v1/extension/tasks/${next.task.taskId}/complete`, {
      method: 'POST',
      body: JSON.stringify({ environmentKey: activeConfig.environmentKey, fillResult }),
    }, session)
    return {
      ok: true,
      message: '任务已填充',
      taskId: next.task.taskId,
      platform: next.task.platform,
      environmentKey: activeConfig.environmentKey,
    }
  } catch (error) {
    const failure = classifyTaskFailure(error)
    await apiRequest(activeConfig, `/api/v1/extension/tasks/${next.task.taskId}/fail`, {
      method: 'POST',
      body: JSON.stringify({
        error: failure,
      }),
    }, session.extensionToken).catch(() => {})
    await helperRequest(activeConfig, `/v1/extension/tasks/${next.task.taskId}/fail`, {
      method: 'POST',
      body: JSON.stringify({
        environmentKey: activeConfig.environmentKey,
        error: failure,
      }),
    }, session).catch(() => {})
    throw error
  }
}

async function refreshRuntimeConfig(options = {}) {
  const stored = await storageGet(['geoEnvConfig', 'geoEnvSession'])
  const rawConfig = stored.geoEnvConfig || {}
  const session = stored.geoEnvSession || null
  if (!session?.extensionToken) return { applied: false, reason: 'not_bound' }
  const { config } = await getConfig()
  const activeSession = await refreshExtensionSession(config, session)
  const query = new URLSearchParams()
  const environmentKeyFilter = rawConfig.environmentKey || options.environmentKey || ''
  const platformFilter = rawConfig.platform || options.platform || ''
  if (environmentKeyFilter) query.set('environmentKey', String(environmentKeyFilter))
  if (platformFilter) query.set('platform', normalizePlatform(platformFilter))
  const path = `/api/v1/extension/runtime-config${query.toString() ? `?${query}` : ''}`
  const runtime = await apiRequest(config, path, { method: 'GET' }, activeSession.extensionToken)
  await storageSet({ geoEnvRuntimeConfig: { ...runtime, refreshedAt: new Date().toISOString() } })

  const selected = runtime?.selected
  if (!selected) {
    await appendEventLog({
      type: 'runtime_config',
      ok: false,
      reason: options.reason || 'refresh',
      selectionStatus: runtime?.selectionStatus || 'unknown',
      candidateCount: Array.isArray(runtime?.candidates) ? runtime.candidates.length : 0,
      error: runtime?.selectionStatus === 'ambiguous'
        ? '匹配到多个环境账号绑定，已保留当前配置，需人工选择环境或平台'
        : '未匹配到环境账号绑定，已保留当前配置',
    })
    return { applied: false, runtime }
  }

  const decision = runtimeConfigAdoptionDecision(rawConfig, runtime, selected)
  if (!decision.apply) {
    await appendEventLog({
      type: 'runtime_config',
      ok: false,
      reason: options.reason || 'refresh',
      selectionStatus: 'conflict',
      error: decision.reason,
    })
    return { applied: false, runtime, reason: decision.reason }
  }

  const nextConfig = {
    ...rawConfig,
    apiBase: rawConfig.apiBase || config.apiBase,
    helperBase: rawConfig.helperBase || runtime.helperBase || config.helperBase,
    brandId: rawConfig.brandId || runtime.brandId || config.brandId || null,
    environmentKey: selected.environmentKey || rawConfig.environmentKey || config.environmentKey,
    environmentAccountId: selected.browserEnvironmentAccountId || rawConfig.environmentAccountId || null,
    selfMediaAccountId: selected.selfMediaAccountId || rawConfig.selfMediaAccountId || null,
    platform: selected.platform || rawConfig.platform || config.platform,
    autoRun: rawConfig.autoRun !== false,
  }
  await storageSet({ geoEnvConfig: nextConfig, geoEnvSession: activeSession })
  await appendEventLog({
    type: 'runtime_config',
    ok: true,
    reason: options.reason || 'refresh',
    environmentKey: nextConfig.environmentKey,
    platform: nextConfig.platform,
    status: selected.loginStatus || '-',
  })
  return { applied: true, config: nextConfig, session: activeSession, runtime }
}

function runtimeConfigAdoptionDecision(rawConfig, runtime, selected) {
  const rawEnvironmentKey = String(rawConfig.environmentKey || '').trim()
  const rawPlatform = normalizePlatform(rawConfig.platform || '')
  const selectedEnvironmentKey = String(selected.environmentKey || '').trim()
  const selectedPlatform = normalizePlatform(selected.platform || '')
  if (rawEnvironmentKey && selectedEnvironmentKey && rawEnvironmentKey !== selectedEnvironmentKey) {
    return { apply: false, reason: `当前环境标识=${rawEnvironmentKey}，后台候选=${selectedEnvironmentKey}，不自动覆盖` }
  }
  if (rawPlatform && selectedPlatform && rawPlatform !== selectedPlatform) {
    return { apply: false, reason: `当前平台=${rawPlatform}，后台候选=${selectedPlatform}，不自动覆盖` }
  }
  if (rawConfig.brandId && runtime?.brandId && Number(rawConfig.brandId) !== Number(runtime.brandId)) {
    return { apply: false, reason: `当前品牌=${rawConfig.brandId}，后台绑定品牌=${runtime.brandId}，不自动覆盖` }
  }
  return { apply: true }
}

function classifyTaskFailure(errorOrMessage) {
  const text = String(errorOrMessage?.message || errorOrMessage || '')
  const code = errorOrMessage?.code
    || text.match(/^([A-Z0-9_]{3,80})[：:]/)?.[1]
    || classifyTaskFailureCode(text)
  return {
    code,
    message: text || '页面填充失败',
    retryable: isRetryableTaskFailureCode(code),
  }
}

function classifyTaskFailureCode(text) {
  if (text.includes('fill token used or expired')) return 'FILL_TOKEN_USED_OR_EXPIRED'
  if (text.includes('作品列表') || text.includes('作品管理页') || text.includes('WORKS_LIST_VERIFY_TIMEOUT')) return 'WORKS_LIST_VERIFY_TIMEOUT'
  if (text.includes('账号不一致') || text.includes('LOGIN_STATUS_MISMATCH') || text.includes('账号身份预检失败')) return 'ACCOUNT_MISMATCH'
  if (text.includes('IDENTITY_EXPECTATION_MISSING')) return 'IDENTITY_EXPECTATION_MISSING'
  if (text.includes('平台定时发布能力')) return 'PLATFORM_CAPABILITY_UNVERIFIED'
  if (text.includes('Material not found') || text.includes('素材不存在')) return 'COVER_MATERIAL_NOT_FOUND'
  if (text.includes('封面图片类型不支持') || text.includes('图片类型不支持')) return 'COVER_IMAGE_UNSUPPORTED'
  const zhihuCode = globalThis.__GEO_ZHIHU_PLATFORM__?.classifyFailureCode?.(text, 'zhihu')
  if (zhihuCode) return zhihuCode
  if (text.includes('知乎发布被草稿加载阻塞') || text.includes('知乎草稿加载未完成') || text.includes('草稿加载中')) return 'ZHIHU_DRAFT_LOADING'
  if (text.includes('知乎发布后未检测到完成状态')) return 'ZHIHU_PUBLISH_NOT_SUBMITTED'
  const xiaohongshuCode = globalThis.__GEO_XIAOHONGSHU_PLATFORM__?.classifyFailureCode?.(text, 'xiaohongshu')
  if (xiaohongshuCode) return xiaohongshuCode
  const baijiahaoCode = globalThis.__GEO_BAIJIAHAO_PLATFORM__?.classifyFailureCode?.(text, 'baijiahao')
  if (baijiahaoCode) return baijiahaoCode
  if (text.includes('小红书一键排版按钮未找到')) return 'XIAOHONGSHU_FORMAT_BUTTON_NOT_FOUND'
  if (text.includes('小红书一键排版后未进入排版页') || text.includes('小红书排版页未就绪')) return 'XIAOHONGSHU_FORMAT_NOT_READY'
  if (text.includes('小红书下一步按钮未找到')) return 'XIAOHONGSHU_NEXT_BUTTON_NOT_FOUND'
  if (text.includes('小红书点击下一步后未进入发布设置页') || text.includes('小红书发布设置页未加载完成') || text.includes('小红书发布设置页未就绪')) {
    return 'XIAOHONGSHU_PUBLISH_SETTINGS_NOT_READY'
  }
  if (text.includes('小红书笔记图片生成') || text.includes('小红书图片生成')) return 'XIAOHONGSHU_IMAGE_GENERATION_TIMEOUT'
  if (text.includes('小红书定时发布时间过近')) return 'XIAOHONGSHU_SCHEDULE_TIME_TOO_SOON'
  if (text.includes('小红书定时发布时间过远')) return 'XIAOHONGSHU_SCHEDULE_TIME_TOO_LATE'
  if (text.includes('小红书定时发布时间无效')) return 'XIAOHONGSHU_SCHEDULE_TIME_INVALID'
  if (text.includes('小红书定时发布开关')) return 'XIAOHONGSHU_SCHEDULE_SWITCH_NOT_FOUND'
  if (text.includes('小红书定时发布时间输入框')) return 'XIAOHONGSHU_SCHEDULE_TIME_INPUT_NOT_FOUND'
  if (text.includes('小红书定时发布时间未保持目标值')) return 'XIAOHONGSHU_SCHEDULE_TIME_NOT_APPLIED'
  if (text.includes('小红书定时发布按钮未找到') || text.includes('小红书发布按钮未找到')) return 'XIAOHONGSHU_PUBLISH_BUTTON_NOT_FOUND'
  if (text.includes('小红书发布后未检测到成功状态')) return 'XIAOHONGSHU_PUBLISH_NOT_CONFIRMED'
  if (text.includes('头条定时发布按钮点击后未打开弹窗或预览')) return 'TOUTIAO_SCHEDULE_DIALOG_NOT_OPENED'
  if (text.includes('头条定时发布弹窗时间输入框未找到')
      || text.includes('头条定时发布跨 frame 弹窗时间控件未找到')) {
    return 'TOUTIAO_SCHEDULE_TIME_INPUT_NOT_FOUND'
  }
  if (text.includes('头条定时发布按钮未找到')) return 'TOUTIAO_SCHEDULE_BUTTON_NOT_FOUND'
  if (text.includes('定时发布时间已过期') || text.includes('定时发布时间无效')) return 'SCHEDULE_TIME_INVALID'
  if (text.includes('页面填充执行超时') || text.includes('超时')) return 'PAGE_LOAD_TIMEOUT'
  if (text.includes('未找到') || text.includes('编辑器')) return 'EDITOR_NOT_READY'
  return 'FILL_FAILED'
}

function isRetryableTaskFailureCode(code) {
  if (globalThis.__GEO_ZHIHU_PLATFORM__?.isRetryableFailureCode?.(code)) return true
  if (globalThis.__GEO_XIAOHONGSHU_PLATFORM__?.isRetryableFailureCode?.(code)) return true
  if (globalThis.__GEO_BAIJIAHAO_PLATFORM__?.isRetryableFailureCode?.(code)) return true
  return [
    'PAGE_LOAD_TIMEOUT',
    'EDITOR_NOT_READY',
    'COVER_UPLOAD_TIMEOUT',
    'SCHEDULE_DIALOG_NOT_READY',
    'PREVIEW_PAGE_NOT_READY',
    'WORKS_LIST_VERIFY_TIMEOUT',
    'ZHIHU_DRAFT_LOADING',
    'ZHIHU_PUBLISH_NOT_SUBMITTED',
    'LOCAL_HELPER_TEMPORARY_ERROR',
    'FILL_TOKEN_USED_OR_EXPIRED',
  ].includes(code)
}

async function autoPollOnce(reason, senderTabId) {
  try {
    const { config, session } = await getConfig()
    if (config.autoRun === false) return { ok: true, skipped: true, reason: 'auto_run_disabled' }
    if (!session?.extensionToken) {
      await setBadge('ERR')
      await appendEventLog({ type: 'auto_fill', ok: false, reason, error: '扩展未绑定后台，无法向本地助手签名领取任务' })
      return { ok: true, skipped: true, reason: 'not_bound' }
    }
    const tab = senderTabId ? await chrome.tabs.get(senderTabId).catch(() => null) : null
    const platform = inferPlatformFromUrl(tab?.url)
    let identityTabId = senderTabId
    let result = await pollOnce({ identityTabId, platform })

    if (!result.taskId) {
      const fallbackTask = await findPendingHelperTaskForOtherPlatform(config, session, platform)
      if (fallbackTask?.platform) {
        const fallbackPlatform = normalizePlatform(fallbackTask.platform)
        identityTabId = await findPlatformTabId(fallbackPlatform)
        result = await pollOnce({ identityTabId, platform: fallbackPlatform })
      }
    }

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

async function findPendingHelperTaskForOtherPlatform(config, session, currentPlatform) {
  const normalizedCurrent = normalizePlatform(currentPlatform)
  const tasks = await listHelperTasks(config, session)
  return tasks.find((task) => {
    const platform = normalizePlatform(task.platform)
    if (!platform || platform === normalizedCurrent) return false
    return isClaimableHelperTaskStatus(task.status)
  }) || null
}

function isClaimableHelperTaskStatus(status) {
  return ['pending', 'requeued'].includes(String(status || '').toLowerCase())
}

async function autoReportLoginStatusFromTab(config, session, tabId, options = {}) {
  if (!tabId || !session?.extensionToken) {
    await appendEventLog({ type: 'login_report', ok: false, platform: options.platform || '-', error: '跳过登录上报：缺少 tab 或后台绑定' })
    return null
  }
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  const platform = options.platform || inferPlatformFromUrl(tab?.url)
  if (!platform || !isAllowedLoginReportUrl(platform, tab?.url)) {
    await appendEventLog({ type: 'login_report', ok: false, platform: platform || '-', error: `跳过登录上报：当前页不允许或无法识别平台，url=${tab?.url || '-'}` })
    return null
  }

  const environmentKey = options.environmentKey || (options.requireTaskEnvironment
    ? ''
    : await resolveLoginReportEnvironmentKey(config, session, platform))
  if (!environmentKey) {
    await appendEventLog({ type: 'login_report', ok: false, platform, error: '跳过登录上报：未解析到环境标识' })
    return null
  }
  const reportConfig = { ...config, environmentKey }
  const throttleKey = `${environmentKey}:${platform}:${tabId}`
  const now = Date.now()
  if (now - (autoLoginReportAtByKey.get(throttleKey) || 0) < 10_000) return null
  autoLoginReportAtByKey.set(throttleKey, now)

  await ensureContentScript(tabId)
  await waitForContentScript(tabId, 8, 500)
  const identity = await readIdentityFromTab(tabId, platform, { requireIdentity: false })
  if (!identity) {
    await appendEventLog({ type: 'login_report', ok: false, platform, environmentKey, error: '跳过登录上报：content-script 未返回身份诊断' })
    return null
  }
  const status = await reportLoginStatus(reportConfig, session, {
    environmentAccountId: options.environmentAccountId || null,
    environmentKey,
    selfMediaAccountId: options.selfMediaAccountId || null,
    platform,
    identity,
  })
  await appendEventLog({ type: 'login_report', ok: true, platform, environmentKey, status: status?.loginStatus || '-' })
  return status
}

async function resolveCandidateEnvironmentKeys(config, session, platform) {
  const keys = []
  const add = (value) => {
    const key = String(value || '').trim()
    if (key && !keys.includes(key)) keys.push(key)
  }
  for (const task of await listHelperTasks(config, session)) {
    if (task.status === 'completed' || task.status === 'cancelled') continue
    if (platform && task.platform && normalizePlatform(task.platform) !== normalizePlatform(platform)) continue
    add(task.environmentKey)
  }
  add(config.environmentKey)
  return keys
}

async function resolveLoginReportEnvironmentKey(config, session, platform) {
  for (const task of await listHelperTasks(config, session)) {
    if (task.status === 'completed' || task.status === 'cancelled') continue
    if (platform && task.platform && normalizePlatform(task.platform) !== normalizePlatform(platform)) continue
    if (task.environmentKey) return String(task.environmentKey).trim()
  }
  return config.environmentKey || ''
}

async function listHelperTasks(config, session) {
  try {
    const result = await helperRequest(config, '/v1/extension/tasks', {}, session)
    return Array.isArray(result?.tasks) ? result.tasks : []
  } catch {
    return []
  }
}

async function handleTask(config, session, task, options = {}) {
  const taskApiConfig = task.backendBase ? { ...config, apiBase: task.backendBase } : config
  const identityTabId = await ensureTaskIdentityTab(taskApiConfig, session, task, options.identityTabId)
  const precheckedIdentity = await verifyTaskIdentityOnTab(identityTabId, task)
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
  payload.environmentKey = task.environmentKey || config.environmentKey || null
  payload.expectedPlatformAccountId = task.expectedPlatformAccountId || payload.expectedPlatformAccountId || null
  payload.expectedAccountName = task.expectedAccountName || payload.expectedAccountName || null
  assertExpectedIdentityPresent(payload)
  payload.precheckedIdentity = precheckedIdentity || null

  const tab = await resolveFillTab(options.identityTabId, task.platform, payload.publishUrl)
  await waitForTabComplete(tab.id, 30_000)
  await waitForContentScript(tab.id, 8, 500)
  let fillResult
  try {
    const fillResponse = await sendFillMessageOnce(tab.id, {
      type: 'GEO_ENV_FILL_TASK',
      payload,
    })
    fillResult = normalizeFillResult(fillResponse?.result || fillResponse, task)
  } catch (error) {
    fillResult = normalizeFillResult(
      await recoverPublishAfterFillError(tab.id, task, payload, error),
      task,
    )
  }

  await apiRequest(taskApiConfig, `/api/v1/extension/tasks/${task.taskId}/ack`, {
    method: 'POST',
    body: JSON.stringify({ fillResult }),
  }, session.extensionToken)
  return fillResult
}

async function ensureTaskIdentityTab(config, session, task, candidateTabId) {
  if (!requiresIdentityPrecheck(task)) return candidateTabId || null
  const existingTabId = await resolveIdentityPrecheckTabId(candidateTabId, task.platform, true)
  if (existingTabId) {
    await autoReportLoginStatusFromTab(config, session, existingTabId, {
      platform: task.platform,
      environmentKey: task.environmentKey || config.environmentKey,
      environmentAccountId: task.browserEnvironmentAccountId || null,
      selfMediaAccountId: task.selfMediaAccountId || null,
      requireTaskEnvironment: true,
    })
    return existingTabId
  }

  const identityUrl = defaultLoginReportUrl(task.platform)
  if (!identityUrl) {
    throw new Error(`账号身份预检失败：${platformDisplayName(task.platform)}未配置稳定账号身份页`)
  }
  const tab = await chrome.tabs.create({ url: identityUrl, active: true })
  await waitForTabComplete(tab.id, 30_000).catch(() => null)
  await delay(1200)
  await autoReportLoginStatusFromTab(config, session, tab.id, {
    platform: task.platform,
    environmentKey: task.environmentKey || config.environmentKey,
    environmentAccountId: task.browserEnvironmentAccountId || null,
    selfMediaAccountId: task.selfMediaAccountId || null,
    requireTaskEnvironment: true,
  })
  return tab.id
}

function normalizeFillResult(fillResult, task = {}) {
  return globalThis.__GEO_FILL_RESULT__?.normalizeFillResult
    ? globalThis.__GEO_FILL_RESULT__.normalizeFillResult(fillResult, task)
    : fillResult
}

async function recoverPublishAfterFillError(tabId, task, payload, error) {
  const zhihuResult = await recoverZhihuPublishAfterMessageChannelClosed(tabId, task, payload, error).catch((zhihuError) => {
    if (zhihuError !== error) throw zhihuError
    return null
  })
  if (zhihuResult) return zhihuResult
  return recoverToutiaoScheduleAfterWorksListTimeout(tabId, task, payload, error)
}

async function recoverZhihuPublishAfterMessageChannelClosed(tabId, task, payload, error) {
  const message = error?.message || String(error || '')
  if (normalizePlatform(task?.platform || payload?.platform) !== 'zhihu' || !isMessageChannelClosedError(message)) {
    throw error
  }
  const verification = await waitForZhihuPublishedTab(tabId, 15000, {
    expectedTitle: payload?.title || payload?.articleTitle || '',
    expectedAccountName: payload?.expectedAccountName || '',
    expectedPlatformAccountId: payload?.expectedPlatformAccountId || '',
  })
  if (!verification?.verified) {
    throw error
  }
  return {
    titleFilled: true,
    contentFilled: true,
    tagsFilled: false,
    publishOptions: {
      filled: true,
      published: true,
      publishVerification: verification,
      message: '知乎发布后页面跳转，已通过标签页状态确认发布',
    },
    recoveredAfterMessageChannelClosed: true,
    messageChannelClosedError: message,
  }
}

function isMessageChannelClosedError(message) {
  const text = String(message || '')
  return text.includes('message channel closed')
    || text.includes('receiving end does not exist')
    || text.includes('Extension context invalidated')
}

async function recoverToutiaoScheduleAfterWorksListTimeout(tabId, task, payload, error) {
  const message = error?.message || String(error || '')
  const platform = normalizePlatform(task?.platform || payload?.platform)
  if (platform !== 'toutiao' || !isWorksListVerifyTimeout(error, message)) throw error

  const context = buildToutiaoWorksListVerifyContext(payload)
  if (!context.scheduledAt) throw error
  let latest = null
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await chrome.tabs.reload(tabId, { bypassCache: true })
    await delay(300)
    await waitForTabComplete(tabId, 30_000).catch(() => null)
    await delay(1200 + attempt * 800)
    latest = await inspectToutiaoWorksListTab(tabId, context, attempt + 1)
    if (latest?.verified) {
      return {
        titleFilled: true,
        contentFilled: true,
        tagsFilled: false,
        publishOptions: {
          filled: true,
          scheduled: true,
          published: false,
          publishVerification: latest,
          message: `头条作品列表首次未命中，刷新${attempt + 1}次后确认定时发布`,
        },
        recoveredAfterWorksListRefresh: true,
        worksListVerifyError: message,
      }
    }
  }
  throw new Error(`WORKS_LIST_VERIFY_TIMEOUT：头条作品列表刷新后仍未匹配到定时文章；target=${context.scheduledAt}；${latest?.diagnostics || message}`)
}

function isWorksListVerifyTimeout(error, message) {
  return error?.code === 'WORKS_LIST_VERIFY_TIMEOUT' || String(message || '').includes('WORKS_LIST_VERIFY_TIMEOUT')
}

function buildToutiaoWorksListVerifyContext(payload = {}) {
  const platformOptions = payload.platformOptions || {}
  const profileOptions = payload.profile?.platformOptions || {}
  const toutiaoOptions = payload.toutiaoOptions || platformOptions.toutiao || profileOptions.toutiao || {}
  return {
    title: firstText(payload.title, payload.articleTitle),
    locationName: firstText(
      payload.locationName,
      payload.location,
      platformOptions.locationName,
      platformOptions.location,
      profileOptions.locationName,
      profileOptions.location,
      toutiaoOptions.locationName,
      toutiaoOptions.location,
    ),
    scheduledAt: firstText(
      payload.scheduledAt,
      payload.platformScheduledAt,
      platformOptions.scheduledAt,
      platformOptions.platformScheduledAt,
      profileOptions.scheduledAt,
      profileOptions.platformScheduledAt,
      toutiaoOptions.scheduledAt,
      toutiaoOptions.platformScheduledAt,
    ),
  }
}

async function inspectToutiaoWorksListTab(tabId, context, refreshAttempt) {
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  if (!tab?.url || !isAllowedPlatformUrl('toutiao', tab.url)) {
    return {
      verified: false,
      pageUrl: tab?.url || '',
      diagnostics: `refreshAttempt=${refreshAttempt}; not_toutiao_tab`,
    }
  }
  const [state] = await chrome.scripting.executeScript({
    target: { tabId },
    args: [context, refreshAttempt],
    func: (context, refreshAttempt) => {
      const normalizeText = (value) => String(value || '').replace(/\s+/g, '')
      const normalizeArticleText = (value) => normalizeText(value)
        .replace(/[《》「」『』"'“”‘’]/g, '')
        .trim()
      const normalizeScheduleValue = (value) => {
        const text = String(value || '').trim()
        const match = text.match(/(\d{4})[-/年](\d{1,2})[-/月](\d{1,2})[日\sT]*(\d{1,2})[:时](\d{1,2})/)
        if (!match) return { full: normalizeText(text), monthDay: '', time: '' }
        const [, year, month, day, hour, minute] = match
        return {
          full: `${year}-${String(Number(month)).padStart(2, '0')}-${String(Number(day)).padStart(2, '0')} ${String(Number(hour)).padStart(2, '0')}:${String(Number(minute)).padStart(2, '0')}`,
          monthDay: `${Number(month)}月${Number(day)}日`,
          time: `${Number(hour)}:${String(Number(minute)).padStart(2, '0')}`,
          hour: Number(hour),
          minute: Number(minute),
        }
      }
      const isVisible = (el) => {
        if (!el || !el.getBoundingClientRect) return false
        const style = window.getComputedStyle(el)
        if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0) return false
        const rect = el.getBoundingClientRect()
        return rect.width > 0 && rect.height > 0
      }
      const isWorksManagePage = () => {
        if (location.hostname !== 'mp.toutiao.com') return false
        if (/\/profile_v\d+\/manage/.test(location.pathname) || location.pathname.includes('/profile_v4/content-manage')) return true
        const text = normalizeText(document.body?.innerText || document.body?.textContent || '')
        return text.includes('作品管理')
          || (text.includes('草稿箱') && (text.includes('已发布') || text.includes('审核中') || text.includes('定时发布中')))
      }
      const collectRows = () => {
        const candidates = Array.from(document.querySelectorAll('li, article, section, div'))
          .filter(isVisible)
          .filter((el) => {
            const rect = el.getBoundingClientRect()
            if (rect.width < 300 || rect.height < 60 || rect.height > 360) return false
            const text = normalizeText(el.textContent || '')
            return text.includes('定时发布中') || text.includes('将于')
          })
        return candidates.filter((candidate) => !candidates.some((other) => other !== candidate && other.contains(candidate)))
      }
      const schedule = normalizeScheduleValue(context.scheduledAt)
      const expectedTitle = normalizeArticleText(context.title || '')
      const titleNeedle = expectedTitle.length > 18 ? expectedTitle.slice(0, 18) : expectedTitle
      const locationName = normalizeText(context.locationName || '')
      const scheduleMatched = (text) => {
        const full = normalizeText(schedule.full)
        const compactFull = full.replace(/[-:]/g, '')
        const compactHourMinute = `${Number(schedule.hour)}时${Number(schedule.minute)}分`
        return text.includes(full)
          || text.includes(compactFull)
          || (schedule.monthDay && text.includes(schedule.monthDay) && (text.includes(schedule.time) || text.includes(compactHourMinute)))
          || (schedule.time && text.includes(schedule.time))
      }
      const extractTitle = (row) => {
        const fallback = String(context.title || '').trim()
        const candidates = Array.from(row.querySelectorAll('a, span, div, h1, h2, h3'))
          .filter(isVisible)
          .map((el) => String(el.textContent || '').trim())
          .filter((text) => text.length >= 8 && text.length <= 80)
          .filter((text) => !text.includes('定时发布中') && !text.includes('查看数据') && !text.includes('查看评论'))
        return candidates[0] || fallback
      }
      const extractScheduleText = (row) => {
        const text = normalizeText(row.textContent || '')
        const index = text.indexOf('将于')
        if (index >= 0) return text.slice(index, Math.min(text.length, index + 32))
        const dateIndex = schedule.monthDay ? text.indexOf(schedule.monthDay) : -1
        if (dateIndex >= 0) return text.slice(Math.max(0, dateIndex - 4), Math.min(text.length, dateIndex + 24))
        return schedule.full
      }
      const rows = collectRows()
      for (const row of rows) {
        const text = normalizeText(row.textContent || '')
        if (!text.includes('定时发布中') && !text.includes('将于')) continue
        if (titleNeedle && !normalizeArticleText(row.textContent || '').includes(titleNeedle)) continue
        if (locationName && !text.includes(locationName)) continue
        if (!scheduleMatched(text)) continue
        return {
          verified: true,
          platformStatus: 'scheduled',
          matchedTitle: extractTitle(row),
          scheduledAtText: extractScheduleText(row),
          locationText: locationName && text.includes(locationName) ? context.locationName : '',
          hasCover: Array.from(row.querySelectorAll('img, [style*="background-image"]')).some(isVisible),
          pageUrl: location.href,
          refreshAttempt,
        }
      }
      const rowText = rows.slice(0, 6).map((row, index) => `${index}:${normalizeText(row.textContent || '').slice(0, 120)}`)
      const body = normalizeText(document.body?.innerText || document.body?.textContent || '').slice(0, 240)
      return {
        verified: false,
        pageUrl: location.href,
        refreshAttempt,
        diagnostics: `refreshAttempt=${refreshAttempt}; worksPage=${isWorksManagePage()}; expectedTitle=${expectedTitle || '-'}; rows=${rowText.join('|') || '-'}; body=${body || '-'}`,
      }
    },
  }).catch(() => [])
  return state?.result || {
    verified: false,
    pageUrl: tab.url,
    diagnostics: `refreshAttempt=${refreshAttempt}; inspect_failed`,
  }
}

async function waitForZhihuPublishedTab(tabId, timeoutMs, context = {}) {
  const deadline = Date.now() + timeoutMs
  let latest = null
  while (Date.now() < deadline) {
    latest = await inspectZhihuPublishedTab(tabId, context)
    if (latest?.verified) return latest
    await delay(500)
  }
  return latest
}

async function inspectZhihuPublishedTab(tabId, context = {}) {
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  const url = tab?.url || ''
  const publishedUrl = normalizeZhihuPublishedUrl(url)
  if (isZhihuPublishedArticleUrl(url)) {
    const pageTitle = normalizeZhihuTitleText(tab?.title || '')
    return {
      verified: true,
      pageUrl: publishedUrl,
      pageTitle,
      expectedTitle: context.expectedTitle || '',
      titleMatch: matchZhihuPublishedTitle(context.expectedTitle || '', pageTitle, ''),
      account: {
        expectedAccountName: context.expectedAccountName || '',
        expectedPlatformAccountId: context.expectedPlatformAccountId || '',
        accountIds: [],
        accountNames: [],
        profileUrls: [],
        diagnostics: 'recovered from tab url; page script not inspected',
      },
      publishUi: {
        editorStillOpen: false,
        publishButtonVisible: false,
        draftLoadingDialogVisible: false,
      },
      successSignal: {
        publishedUrl: true,
        successText: false,
        reviewText: false,
        publishedAtText: false,
      },
      recoveredFrom: 'tab_url',
    }
  }
  if (!isAllowedPlatformUrl('zhihu', url)) {
    return { verified: false, pageUrl: url, reason: 'not_zhihu_tab' }
  }
  const [state] = await chrome.scripting.executeScript({
    target: { tabId },
    args: [context],
    func: (context) => {
      const normalize = (value) => String(value || '').replace(/\s+/g, '')
      const normalizeTitle = (value) => normalize(value)
        .replace(/[-_—|].*知乎.*/i, '')
        .replace(/^写文章/, '')
        .trim()
      const matchTitle = (expectedTitle, pageTitle, pageText) => {
        const expected = normalizeTitle(expectedTitle || '')
        const actual = normalizeTitle(pageTitle || '')
        if (!expected) return { expected: '', actual, matched: true, method: 'no_expected_title' }
        if (actual && (actual === expected || actual.includes(expected) || expected.includes(actual))) {
          return { expected, actual, matched: true, method: 'page_title' }
        }
        const text = normalizeTitle(pageText || '')
        return { expected, actual, matched: Boolean(text && text.includes(expected)), method: 'page_text' }
      }
      const extractPublishedAt = (value) => {
        const text = String(value || '')
        return text.match(/发布于\d{4}[-年]\d{1,2}[-月]\d{1,2}[^\s。；;，,]{0,16}/)?.[0]
          || text.match(/\d{4}[-年]\d{1,2}[-月]\d{1,2}[^\s。；;，,]{0,16}发布/)?.[0]
          || ''
      }
      const collectIdentity = () => {
        const ids = new Set()
        const names = new Set()
        const profileUrls = new Set()
        const collectProfileHref = (href) => {
          try {
            const url = new URL(href || '', location.href)
            const token = url.pathname.match(/^\/(?:people|org)\/([^/?#]+)/)?.[1]
            if (!token) return
            ids.add(decodeURIComponent(token))
            url.pathname = url.pathname.startsWith('/org/') ? `/org/${token}` : `/people/${token}`
            url.search = ''
            url.hash = ''
            profileUrls.add(url.toString())
          } catch (_) {
            const token = String(href || '').match(/\/(?:people|org)\/([^/?#]+)/)?.[1]
            if (token) ids.add(decodeURIComponent(token))
          }
        }
        for (const link of Array.from(document.querySelectorAll('a[href*="/people/"],a[href*="/org/"]')).slice(0, 80)) {
          collectProfileHref(link.getAttribute('href') || '')
          const imgAlt = link.querySelector('img[alt]')?.getAttribute('alt') || ''
          if (imgAlt && !/^(写文章|发布文章|创作|开始写作|首页|会员|消息|私信|设置|退出|退出登录|知乎|知乎创作助手)$/.test(imgAlt)) names.add(imgAlt)
        }
        const scriptText = Array.from(document.scripts)
          .slice(0, 80)
          .map((script) => script.textContent || '')
          .filter((text) => /(currentUser|viewer|loginUser|urlToken)/i.test(text))
          .join('\n')
          .slice(0, 200000)
        for (const match of scriptText.matchAll(/"urlToken"\s*:\s*"([^"]{2,80})"|"url_token"\s*:\s*"([^"]{2,80})"|"username"\s*:\s*"([^"]{2,80})"/g)) {
          const value = match[1] || match[2] || match[3]
          if (value) ids.add(value)
        }
        for (const match of scriptText.matchAll(/"name"\s*:\s*"([^"]{2,80})"|"nickname"\s*:\s*"([^"]{2,80})"/g)) {
          const value = match[1] || match[2]
          if (value && !/^(写文章|发布文章|创作|开始写作|首页|会员|消息|私信|设置|退出|退出登录|知乎|知乎创作助手)$/.test(value)) names.add(value)
        }
        return {
          expectedAccountName: context.expectedAccountName || '',
          expectedPlatformAccountId: context.expectedPlatformAccountId || '',
          accountIds: Array.from(ids),
          accountNames: Array.from(names),
          profileUrls: Array.from(profileUrls),
          diagnostics: `href=${location.href}; accountIds=${Array.from(ids).join(',') || '-'}; accountNames=${Array.from(names).join(',') || '-'}; profileUrls=${Array.from(profileUrls).join(',') || '-'}`,
        }
      }
      const text = normalize(document.body?.innerText || document.body?.textContent || '')
      const rawText = document.body?.innerText || document.body?.textContent || ''
      const pageTitle = normalizeTitle(document.querySelector('h1')?.textContent || document.title)
      const publishedAtText = extractPublishedAt(rawText)
      const editorStillOpen = location.pathname.startsWith('/write')
        || text.includes('发布设置')
        || text.includes('添加文章封面')
        || text.includes('Markdown语法输入中')
      return {
        href: location.href,
        title: pageTitle,
        expectedTitle: context.expectedTitle || '',
        titleMatch: matchTitle(context.expectedTitle || '', pageTitle, rawText),
        publishedAtText,
        account: collectIdentity(),
        publishUi: {
          editorStillOpen,
          publishButtonVisible: Boolean(Array.from(document.querySelectorAll('button,[role="button"]')).find((el) => normalize(el.textContent) === '发布')),
          draftLoadingDialogVisible: text.includes('草稿加载中'),
        },
        textSample: text.slice(0, 500),
        successText: text.includes('发布成功') || text.includes('审核中') || text.includes('已发布'),
        editorStillOpen,
      }
    },
  }).catch(() => [])
  const result = state?.result || {}
  if (result.successText && !result.editorStillOpen) {
    return {
      verified: true,
      pageUrl: normalizeZhihuPublishedUrl(result.href || url),
      pageTitle: result.title || tab?.title || '',
      expectedTitle: result.expectedTitle || context.expectedTitle || '',
      titleMatch: result.titleMatch || null,
      publishedAtText: result.publishedAtText || '',
      account: result.account || null,
      publishUi: result.publishUi || null,
      successSignal: {
        successText: Boolean(result.textSample?.includes?.('发布成功')),
        reviewText: Boolean(result.textSample?.includes?.('审核中')),
        publishedUrl: Boolean(normalizeZhihuPublishedUrl(result.href || url)),
        publishedAtText: Boolean(result.publishedAtText),
      },
      textSample: result.textSample || '',
      recoveredFrom: 'page_text',
    }
  }
  return {
    verified: false,
    pageUrl: result.href || url,
    pageTitle: result.title || tab?.title || '',
    textSample: result.textSample || '',
    reason: result.editorStillOpen ? 'editor_still_open' : 'publish_signal_not_found',
  }
}

function isZhihuPublishedArticleUrl(value) {
  const shared = globalThis.__GEO_ZHIHU_PLATFORM__?.isPublishedArticleUrl?.(value)
  if (typeof shared === 'boolean') return shared
  try {
    const url = new URL(value)
    return (url.hostname === 'zhuanlan.zhihu.com' || url.hostname === 'www.zhihu.com' || url.hostname === 'zhihu.com')
      && (/^\/p\/[^/]+/.test(url.pathname) || /^\/article\/[^/]+/.test(url.pathname))
      && !url.pathname.includes('/write')
      && !url.pathname.endsWith('/edit')
  } catch {
    return false
  }
}

function normalizeZhihuPublishedUrl(value) {
  const shared = globalThis.__GEO_ZHIHU_PLATFORM__?.normalizePublishedUrl?.(value)
  if (shared) return shared
  const raw = String(value || '').trim()
  if (!raw) return ''
  try {
    const url = new URL(raw)
    const match = url.pathname.match(/^\/p\/([^/]+)/)
    if (match) {
      url.pathname = `/p/${match[1]}`
      url.search = ''
      url.hash = ''
      return url.toString()
    }
    const articleMatch = url.pathname.match(/^\/article\/([^/]+)/)
    if (articleMatch) {
      url.pathname = `/article/${articleMatch[1]}`
      url.search = ''
      url.hash = ''
      return url.toString()
    }
    return url.toString()
  } catch {
    return raw.replace(/\/edit(?:[?#].*)?$/, '')
  }
}

function matchZhihuPublishedTitle(expectedTitle, pageTitle, pageText) {
  const shared = globalThis.__GEO_ZHIHU_PLATFORM__?.matchPublishedTitle?.(expectedTitle, pageTitle, pageText)
  if (shared) return shared
  const expected = normalizeZhihuTitleText(expectedTitle)
  const actual = normalizeZhihuTitleText(pageTitle)
  if (!expected) return { expected: '', actual, matched: true, method: 'no_expected_title' }
  if (actual && (actual === expected || actual.includes(expected) || expected.includes(actual))) {
    return { expected, actual, matched: true, method: 'page_title' }
  }
  const text = normalizeZhihuTitleText(pageText || '')
  return { expected, actual, matched: Boolean(text && text.includes(expected)), method: 'page_text' }
}

function normalizeZhihuTitleText(value) {
  const shared = globalThis.__GEO_ZHIHU_PLATFORM__?.normalizeTitleText?.(value)
  if (shared) return shared
  return String(value || '')
    .replace(/\s+/g, '')
    .replace(/[-_—|].*知乎.*/i, '')
    .replace(/^写文章/, '')
    .trim()
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
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true }).catch(() => [])
  const platform = inferPlatformFromUrl(tab?.url) || config.platform
  const environmentKey = await resolveLoginReportEnvironmentKey(config, session, platform)
  if (!environmentKey || !platform) {
    throw new Error('请先切换到平台页面，或填写环境标识和平台')
  }
  if (!tab?.id || !tab.url || !isAllowedPlatformUrl(platform, tab.url)) {
    throw new Error(`请先切换到 ${platform} 对应的平台页面`)
  }
  if (!isAllowedLoginReportUrl(platform, tab.url)) {
    throw new Error(`请在 ${platformReportPageHint(platform)} 上报登录状态，当前页面不允许用于账号登记`)
  }
  await ensureContentScript(tab.id)
  await waitForContentScript(tab.id, 8, 500)
  const response = await chrome.tabs.sendMessage(tab.id, {
    type: 'GEO_ENV_READ_IDENTITY',
    payload: { platform },
  })
  if (!response?.ok) throw new Error(response?.error || '读取平台账号身份失败')
  const identity = response.result?.identity || null
  const reportConfig = { ...config, environmentKey }
  const status = await reportLoginStatus(reportConfig, session, {
    environmentAccountId: null,
    environmentKey,
    selfMediaAccountId: null,
    platform,
    identity,
  })
  await appendEventLog({ type: 'login_report', ok: true, platform, environmentKey, status: status?.loginStatus || '-' })
  return { platform, environmentKey, detectedIdentity: identity, backendStatus: status }
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
    target: { tabId, allFrames: true },
    files: ['fill-result.js', 'platform-baijiahao.js', 'platform-xiaohongshu.js', 'platform-zhihu.js', 'content-script.js'],
  })
  await delay(200)
}

async function reportLoginStatus(config, session, report) {
  const identity = report.identity || {}
  const accountIds = firstArray(identity.accountIds, identity.currentAccountIds)
  const accountNames = firstArray(identity.accountNames, identity.currentAccountNames)
  assertSingleIdentityCandidate(accountIds, accountNames, identity)
  const hasIdentity = accountIds.length > 0 || accountNames.length > 0
  const brandId = session?.brandId || report.brandId || null
  const environmentAccountId = report.environmentAccountId || config.environmentAccountId || null
  const selfMediaAccountId = report.selfMediaAccountId || config.selfMediaAccountId || null
  const path = environmentAccountId
    ? `/api/v1/extension/browser-environment-accounts/${environmentAccountId}/login-status`
    : brandId
      ? `/api/v1/extension/brands/${brandId}/browser-environment-login-status`
      : '/api/v1/extension/browser-environment-login-status'
  const body = {
    selfMediaAccountId,
    platform: report.platform,
    actualPlatformAccountId: accountIds[0] || null,
    actualAccountName: accountNames[0] || null,
    loginStatus: hasIdentity ? 'logged_in' : 'login_required',
    errorCode: hasIdentity ? null : 'IDENTITY_NOT_READ',
    errorMessage: hasIdentity ? null : (identity.diagnostics || '未读取到平台账号身份'),
  }
  if (!brandId || environmentAccountId) {
    body.environmentKey = report.environmentKey
  }
  const backendStatus = await apiRequest(config, path, {
    method: 'POST',
    body: JSON.stringify(body),
  }, session.extensionToken)
  if (String(backendStatus?.loginStatus || '').toLowerCase() === 'mismatch') {
    throw new Error(`LOGIN_STATUS_MISMATCH：后台判定账号不一致；期望=${backendStatus.expectedAccountName || backendStatus.expectedPlatformAccountId || '-'}；实际=${accountNames[0] || accountIds[0] || '-'}`)
  }
  return backendStatus
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
      throw new Error(`账号身份预检失败：${task.platform} 任务需要先打开稳定账号身份页`)
    }
    return null
  }
  const tab = await chrome.tabs.get(identityTabId).catch(() => null)
  if (!tab?.url || !isAllowedLoginReportUrl(task.platform, tab.url)) {
    if (requiresPrecheck) {
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
    throw error
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

async function runSelfTest() {
  const checks = []
  const { config, session } = await getConfig()
  const runtimeRefresh = await refreshRuntimeConfig({ reason: 'self_test' }).catch((error) => ({ error }))

  checks.push({
    name: 'extension_bound',
    ok: Boolean(session?.extensionToken),
    detail: session?.extensionToken ? `sessionId=${session.sessionId || '-'}` : '未绑定后台',
  })

  try {
    const health = await fetch(`${normalizeBaseUrl(config.helperBase)}/health`)
      .then((response) => response.json())
    checks.push({
      name: 'local_helper_health',
      ok: Boolean(health?.ok),
      detail: `paired=${Boolean(health?.paired)}`,
    })
  } catch (error) {
    checks.push({
      name: 'local_helper_health',
      ok: false,
      error: `本地助手不可访问：${error.message}`,
    })
  }

  if (session?.extensionToken) {
    try {
      await signedHelperHeaders(
        config,
        `/v1/extension/tasks/next?environmentKey=${encodeURIComponent(config.environmentKey || '')}`,
        {},
        session,
      )
      checks.push({ name: 'local_agent_sign', ok: true })
    } catch (error) {
      checks.push({
        name: 'local_agent_sign',
        ok: false,
        error: error.message,
      })
    }
  }

  checks.push({
    name: 'runtime_config',
    ok: Boolean(runtimeRefresh?.applied || runtimeRefresh?.runtime?.selected),
    detail: runtimeRefresh?.runtime
      ? `selection=${runtimeRefresh.runtime.selectionStatus || '-'}, candidates=${runtimeRefresh.runtime.candidates?.length || 0}`
      : runtimeRefresh?.reason || '',
    error: runtimeRefresh?.error?.message,
  })

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

function isAutoPollReadyUrl(urlValue) {
  try {
    const url = new URL(urlValue)
    const platform = inferPlatformFromUrl(urlValue)
    if (platform === 'toutiao') {
      return url.hostname === 'mp.toutiao.com' && url.pathname.includes('/graphic/publish')
    }
    if (platform === 'zhihu') {
      return (url.hostname === 'www.zhihu.com' || url.hostname === 'zhihu.com' || url.hostname === 'zhuanlan.zhihu.com')
        && url.pathname.startsWith('/write')
    }
    if (platform === 'xiaohongshu') {
      return url.hostname === 'creator.xiaohongshu.com' && url.pathname.includes('/publish/publish')
    }
    if (platform === 'baijiahao') {
      return url.hostname === 'baijiahao.baidu.com' && url.pathname.includes('/builder/rc/edit')
    }
    return false
  } catch {
    return false
  }
}

function isAllowedLoginReportUrl(platform, urlValue) {
  try {
    const url = new URL(urlValue)
    const normalizedPlatform = normalizePlatform(platform)
    if (normalizedPlatform === 'toutiao') {
      return url.hostname === 'mp.toutiao.com'
        && !isToutiaoArticlePreviewUrl(url)
        && !url.pathname.includes('/graphic/publish')
    }
    if (normalizedPlatform === 'zhihu') {
      return isZhihuCreatorCenterUrl(url)
    }
    if (normalizedPlatform === 'xiaohongshu') {
      return url.hostname === 'creator.xiaohongshu.com'
    }
    if (normalizedPlatform === 'baijiahao') {
      return url.hostname === 'baijiahao.baidu.com'
    }
    return false
  } catch {
    return false
  }
}

function isToutiaoArticlePreviewUrl(url) {
  return url.hostname === 'mp.toutiao.com' && url.pathname.includes('/mp-article-preview/')
}

function isZhihuCreatorCenterUrl(url) {
  return (url.hostname === 'www.zhihu.com' || url.hostname === 'zhihu.com')
    && url.pathname.startsWith('/creator/')
}

function inferPlatformFromUrl(urlValue) {
  try {
    const url = new URL(urlValue)
    if (url.hostname === 'mp.toutiao.com') return 'toutiao'
    if (url.hostname === 'www.zhihu.com' || url.hostname === 'zhihu.com' || url.hostname === 'zhuanlan.zhihu.com') return 'zhihu'
    if (url.hostname === 'creator.xiaohongshu.com' || url.hostname === 'www.xiaohongshu.com') return 'xiaohongshu'
    if (url.hostname === 'baijiahao.baidu.com') return 'baijiahao'
    return ''
  } catch {
    return ''
  }
}

function platformReportPageHint(platform) {
  const normalizedPlatform = normalizePlatform(platform)
  const hints = {
    toutiao: '头条后台页(mp.toutiao.com)',
    zhihu: '知乎创作中心(www.zhihu.com/creator/manage/creation/article)',
    xiaohongshu: '小红书创作服务平台(creator.xiaohongshu.com)',
    baijiahao: '百家号后台页(baijiahao.baidu.com)',
  }
  return hints[normalizedPlatform] || `${platform || '对应平台'}后台页`
}

function platformDisplayName(platform) {
  const normalizedPlatform = normalizePlatform(platform)
  const names = {
    toutiao: '头条',
    zhihu: '知乎',
    xiaohongshu: '小红书',
    baijiahao: '百家号',
  }
  return names[normalizedPlatform] || '平台'
}

function defaultLoginReportUrl(platform) {
  const normalizedPlatform = normalizePlatform(platform)
  if (normalizedPlatform === 'toutiao') return 'https://mp.toutiao.com/profile_v4'
  if (normalizedPlatform === 'zhihu') return globalThis.__GEO_ZHIHU_PLATFORM__?.CREATOR_CENTER_URL || 'https://www.zhihu.com/creator/manage/creation/article'
  if (normalizedPlatform === 'xiaohongshu') return 'https://creator.xiaohongshu.com/'
  if (normalizedPlatform === 'baijiahao') return 'https://baijiahao.baidu.com/'
  return null
}

function isAllowedPlatformHost(platform, host) {
  const normalizedPlatform = normalizePlatform(platform)
  const allowed = {
    toutiao: ['mp.toutiao.com'],
    zhihu: ['zhuanlan.zhihu.com', 'www.zhihu.com', 'zhihu.com'],
    xiaohongshu: ['creator.xiaohongshu.com', 'www.xiaohongshu.com'],
    baijiahao: ['baijiahao.baidu.com'],
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
    '百家号': 'baijiahao',
    'baijiahao': 'baijiahao',
  }
  return aliases[text] || text
}

async function findActivePlatformTabId(platform) {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true }).catch(() => [])
  if (tab?.id && tab.url && isAllowedPlatformUrl(platform, tab.url)) return tab.id
  return null
}

async function findPlatformTabId(platform) {
  const activeTabId = await findActivePlatformTabId(platform)
  if (activeTabId) return activeTabId
  const tabs = await chrome.tabs.query({}).catch(() => [])
  const existing = tabs.find((tab) => tab.id && tab.url && isAllowedPlatformUrl(platform, tab.url))
  return existing?.id || null
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
  const response = await withTimeout(
    chrome.tabs.sendMessage(tabId, message),
    90_000,
    '页面填充执行超时，请检查定时发布弹窗或平台页面是否阻塞',
  )
  if (!response?.ok) {
    const error = new Error(response?.error || '页面填充失败')
    if (response?.failureCode) error.code = response.failureCode
    throw error
  }
  return response
}

async function fillToutiaoScheduleAcrossFrames(tabId, value, platform) {
  if (!tabId) throw new Error('跨 frame 设置定时发布缺少 tabId')
  await ensureContentScript(tabId)
  await chrome.scripting.executeScript({
    target: { tabId, allFrames: true },
    files: ['fill-result.js', 'platform-baijiahao.js', 'platform-xiaohongshu.js', 'platform-zhihu.js', 'content-script.js'],
  }).catch(() => {})
  await delay(200)

  const frames = await chrome.scripting.executeScript({
    target: { tabId, allFrames: true },
    func: () => {
      const text = String(document.body?.innerText || document.body?.textContent || '').replace(/\s+/g, '')
      const hasScheduleDialog = text.includes('定时发布')
        && (text.includes('请选择当前时间后') || text.includes('本文将于北京时间') || text.includes('预览并定时发布'))
      const hasScheduleControls = /月\d{1,2}日/.test(text) && text.includes('时') && text.includes('分')
      return {
        href: location.href,
        title: document.title,
        hasScheduleDialog,
        hasScheduleControls,
        text: text.slice(0, 160),
      }
    },
  })
  const frame = frames
    .filter((item) => item?.result?.hasScheduleDialog || item?.result?.hasScheduleControls)
    .sort((left, right) => Number(Boolean(right.result?.hasScheduleDialog)) - Number(Boolean(left.result?.hasScheduleDialog)))[0]
  if (!frame) {
    const diagnostic = frames.map((item) => `${item.frameId}:${item.result?.text || '-'}`).join('|').slice(0, 600)
    throw new Error(`头条定时发布弹窗 frame 未找到；frames=${diagnostic}`)
  }
  const response = await withTimeout(
    chrome.tabs.sendMessage(tabId, {
      type: 'GEO_ENV_FILL_TOUTIAO_SCHEDULE_FRAME',
      value,
      platform,
    }, { frameId: frame.frameId }),
    30_000,
    '头条定时发布跨 frame 设置超时',
  )
  if (!response?.ok) throw new Error(response?.error || '头条定时发布跨 frame 设置失败')
  return response.result || response
}

function withTimeout(promise, timeoutMs, message) {
  let timeout = null
  const timer = new Promise((_, reject) => {
    timeout = setTimeout(() => reject(new Error(message)), timeoutMs)
  })
  return Promise.race([promise, timer]).finally(() => {
    if (timeout) clearTimeout(timeout)
  })
}

async function dispatchTrustedClick(tabId, click) {
  if (!tabId || !Number.isFinite(click?.clientX) || !Number.isFinite(click?.clientY)) {
    throw new Error('真实点击参数不完整')
  }
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  if (!tab?.url
      || (!isAllowedPlatformUrl('xiaohongshu', tab.url)
        && !isAllowedPlatformUrl('toutiao', tab.url)
        && !isAllowedPlatformUrl('zhihu', tab.url)
        && !isAllowedPlatformUrl('baijiahao', tab.url))) {
    throw new Error('真实点击仅允许用于小红书、头条、知乎或百家号页面')
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

async function setFileInputFromUrl(tabId, urlValue, options = {}) {
  if (!tabId || !urlValue) throw new Error('文件上传参数不完整')
  const tab = await chrome.tabs.get(tabId).catch(() => null)
  const platform = normalizePlatform(options.platform || inferPlatformFromUrl(tab?.url || ''))
  if (!tab?.url || !isAllowedPlatformUrl(platform, tab.url)) {
    throw new Error(`本地文件上传仅允许用于${platformDisplayName(platform)}页面`)
  }
  const { config, session } = await getConfig()
  const uploaded = await helperRequest(config, '/v1/extension/files/upload-image-to-page', {
    method: 'POST',
    body: JSON.stringify({
      url: urlValue,
      backendBase: config.apiBase,
      taskId: options.taskId || null,
      environmentKey: options.environmentKey || config.environmentKey,
      platform,
    }),
  }, session)
  if (!uploaded?.ok) throw new Error(`本地助手未完成${platformDisplayName(platform)}文件上传`)
  return {
    ok: true,
    fileName: uploaded?.image?.fileName || '',
    contentType: uploaded?.image?.contentType || '',
    size: uploaded?.image?.size || 0,
    inputState: uploaded?.upload?.inputState || null,
    fileInputCount: uploaded?.upload?.fileInputCount || null,
    pageUrl: uploaded?.upload?.pageUrl || '',
  }
}

async function setLatestFileInputFiles(tabId, filePath) {
  const target = { tabId }
  await chrome.debugger.attach(target, '1.3')
  try {
    const documentResult = await chrome.debugger.sendCommand(target, 'DOM.getDocument', { depth: -1, pierce: true })
    const rootNodeId = documentResult?.root?.nodeId
    if (!rootNodeId) throw new Error('未获取到页面 DOM 根节点')
    const query = await chrome.debugger.sendCommand(target, 'DOM.querySelectorAll', {
      nodeId: rootNodeId,
      selector: 'input[type="file"]',
    })
    const nodeIds = Array.isArray(query?.nodeIds) ? query.nodeIds : []
    const chosen = await chooseImageFileInputNode(target, nodeIds)
    const nodeId = chosen?.nodeId || null
    if (!nodeId) throw new Error('头条封面本地上传文件框未找到')
    await chrome.debugger.sendCommand(target, 'DOM.setFileInputFiles', {
      nodeId,
      files: [filePath],
    })
    const state = await dispatchFileInputEvents(target, nodeId)
    return { ...state, chosenInput: chosen?.attrs || null, fileInputCount: nodeIds.length }
  } finally {
    await chrome.debugger.detach(target).catch(() => {})
  }
}

async function chooseImageFileInputNode(target, nodeIds) {
  let fallback = null
  const preferred = []
  for (const nodeId of nodeIds) {
    const described = await chrome.debugger.sendCommand(target, 'DOM.describeNode', { nodeId }).catch(() => null)
    const attrs = attributesToObject(described?.node?.attributes || [])
    const context = await describeFileInputContext(target, nodeId)
    const item = { nodeId, attrs, context, score: scoreImageFileInput(attrs, context) }
    if (!fallback) fallback = item
    const accept = String(attrs.accept || '').toLowerCase()
    const name = String(attrs.name || attrs.class || attrs.id || '').toLowerCase()
    if (accept.includes('image') || accept.includes('jpg') || accept.includes('png') || /image|upload|cover|file/.test(name)) {
      preferred.push(item)
    }
  }
  preferred.sort((left, right) => right.score - left.score)
  if (preferred.length) return preferred[0]
  return nodeIds.length ? { nodeId: nodeIds[nodeIds.length - 1], attrs: {} } : fallback
}

async function describeFileInputContext(target, nodeId) {
  const resolved = await chrome.debugger.sendCommand(target, 'DOM.resolveNode', { nodeId }).catch(() => null)
  const objectId = resolved?.object?.objectId
  if (!objectId) return {}
  const result = await chrome.debugger.sendCommand(target, 'Runtime.callFunctionOn', {
    objectId,
    returnByValue: true,
    functionDeclaration: `function() {
      function norm(value) { return String(value || '').replace(/\\s+/g, ' ').trim(); }
      const parts = [];
      let current = this;
      for (let depth = 0; current && depth < 7; depth += 1) {
        parts.push(current.id || '');
        parts.push(String(current.className || ''));
        parts.push(current.getAttribute && current.getAttribute('data-e2e') || '');
        const text = norm(current.textContent || '');
        if (text && text.length <= 180) parts.push(text);
        current = current.parentElement;
      }
      const rect = this.getBoundingClientRect();
      return {
        contextText: norm(parts.join(' ')),
        visible: rect.width > 0 && rect.height > 0,
        width: rect.width,
        height: rect.height,
      };
    }`,
  }).catch(() => null)
  return result?.result?.value || {}
}

function scoreImageFileInput(attrs, context) {
  const descriptor = `${attrs.accept || ''} ${attrs.id || ''} ${attrs.name || ''} ${attrs.class || ''} ${context?.contextText || ''}`.toLowerCase()
  let score = 0
  if (/(image|jpg|jpeg|png|webp)/.test(descriptor)) score += 20
  if (/btn-upload-handle|upload-handler|本地上传|上传图片|btn-upload|upload-btn/.test(descriptor)) score += 120
  if (/upload-drag-input/.test(descriptor)) score += 90
  if (/扫码上传/.test(descriptor)) score -= 30
  if (/头像|avatar|logo|账号|profile/.test(descriptor)) score -= 80
  if (context?.visible) score += 5
  return score
}

function attributesToObject(attributes) {
  const result = {}
  for (let index = 0; index < attributes.length; index += 2) {
    result[attributes[index]] = attributes[index + 1] || ''
  }
  return result
}

async function dispatchFileInputEvents(target, nodeId) {
  const resolved = await chrome.debugger.sendCommand(target, 'DOM.resolveNode', { nodeId })
  const objectId = resolved?.object?.objectId
  if (!objectId) return { filesLength: null }
  const result = await chrome.debugger.sendCommand(target, 'Runtime.callFunctionOn', {
    objectId,
    returnByValue: true,
    functionDeclaration: `function() {
      this.dispatchEvent(new Event('input', { bubbles: true }));
      this.dispatchEvent(new Event('change', { bubbles: true }));
      return {
        filesLength: this.files ? this.files.length : 0,
        fileName: this.files && this.files[0] ? this.files[0].name : '',
        accept: this.getAttribute('accept') || '',
        id: this.id || '',
        name: this.name || '',
      };
    }`,
  })
  return result?.result?.value || { filesLength: null }
}

async function fetchImageDataUrl(urlValue, depth = 0) {
  const url = new URL(urlValue)
  if (!['http:', 'https:'].includes(url.protocol)) {
    throw new Error('封面图片地址仅支持 http/https')
  }
  const response = await fetch(url.href)
  if (!response.ok) {
    throw new Error(`封面图片下载失败：HTTP ${response.status}`)
  }
  const type = response.headers.get('content-type') || 'image/jpeg'
  if (!type.startsWith('image/')) {
    const bodyText = await response.text().catch(() => '')
    const nestedUrl = extractImageUrlFromJsonText(bodyText)
    if (nestedUrl && depth < 2) {
      return fetchImageDataUrl(nestedUrl, depth + 1)
    }
    const backendUrl = await rewritePublicMaterialUrlToConfiguredBackend(url.href)
    if (backendUrl && depth < 2) {
      return fetchImageDataUrl(backendUrl, depth + 1)
    }
    throw new Error(`封面图片类型不支持：${type}；url=${url.href}；响应=${bodyText.slice(0, 240) || '-'}`)
  }
  const buffer = await response.arrayBuffer()
  if (buffer.byteLength > MAX_IMAGE_FETCH_BYTES) {
    throw new Error('封面图片超过 20MB，头条本地上传不支持')
  }
  return {
    dataUrl: `data:${type};base64,${arrayBufferToBase64(buffer)}`,
    type,
    size: buffer.byteLength,
  }
}

function extractImageUrlFromJsonText(text) {
  try {
    const json = JSON.parse(text)
    return firstText(
      json?.url,
      json?.data?.url,
      json?.data?.previewUrl,
      json?.data?.downloadUrl,
      json?.data?.fileUrl,
      json?.result?.url,
      json?.result?.previewUrl,
      json?.result?.downloadUrl,
    )
  } catch {
    const match = String(text || '').match(/https?:\/\/[^"'\\\s]+/i)
    return match?.[0] || ''
  }
}

async function rewritePublicMaterialUrlToConfiguredBackend(urlValue) {
  try {
    const url = new URL(urlValue)
    if (!url.pathname.startsWith('/api/public/brand-materials/')) return ''
    const { config } = await getConfig()
    if (!config?.apiBase) return ''
    const backend = new URL(config.apiBase)
    if (backend.origin === url.origin) return ''
    return `${backend.origin}${url.pathname}${url.search}`
  } catch {
    return ''
  }
}

function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer)
  const chunkSize = 0x8000
  let binary = ''
  for (let index = 0; index < bytes.length; index += chunkSize) {
    const chunk = bytes.subarray(index, index + chunkSize)
    binary += String.fromCharCode(...chunk)
  }
  return btoa(binary)
}

function firstText(...values) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return ''
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
      return { ok: true, checks: await runSelfTest() }
    }
    if (message?.type === 'GEO_ENV_EDITOR_READY') {
      const readyUrl = sender.url || message.href || sender.tab?.url || ''
      const frameId = Number.isInteger(sender.frameId) ? sender.frameId : 0
      if (frameId !== 0 || !isAutoPollReadyUrl(readyUrl)) {
        return { ok: true, skipped: true, reason: 'not_publish_editor_ready' }
      }
      return autoPollOnce(message.href || 'editor_ready', sender.tab?.id || null)
    }
    if (message?.type === 'GEO_ENV_TRUSTED_CLICK') {
      return dispatchTrustedClick(sender.tab?.id || null, message.click || {})
    }
    if (message?.type === 'GEO_ENV_SET_FILE_INPUT_FROM_URL') {
      return setFileInputFromUrl(sender.tab?.id || null, message.url, {
        platform: message.platform || null,
        taskId: message.taskId || null,
        environmentKey: message.environmentKey || null,
      })
    }
    if (message?.type === 'GEO_ENV_FETCH_IMAGE_DATA_URL') {
      const result = await fetchImageDataUrl(message.url)
      return { ok: true, result }
    }
    if (message?.type === 'GEO_ENV_FILL_TOUTIAO_SCHEDULE_ACROSS_FRAMES') {
      const result = await fillToutiaoScheduleAcrossFrames(sender.tab?.id || null, message.value || {}, message.platform || 'toutiao')
      return { ok: true, result }
    }
    return { ok: false, error: 'unknown message' }
  }

  run()
    .then((result) => sendResponse(result))
    .catch((error) => sendResponse({ ok: false, error: error.message }))
  return true
})
