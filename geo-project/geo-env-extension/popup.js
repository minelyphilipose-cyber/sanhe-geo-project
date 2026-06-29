const fields = {
  apiBase: document.getElementById('apiBase'),
  helperBase: document.getElementById('helperBase'),
  environmentKey: document.getElementById('environmentKey'),
  environmentAccountId: document.getElementById('environmentAccountId'),
  selfMediaAccountId: document.getElementById('selfMediaAccountId'),
  platform: document.getElementById('platform'),
  brandId: document.getElementById('brandId'),
  bindCode: document.getElementById('bindCode'),
  autoRun: document.getElementById('autoRun'),
}

const statusEl = document.getElementById('status')
const profileHintEl = document.getElementById('profileHint')
const logEl = document.getElementById('log')
const saveBtn = document.getElementById('saveBtn')
const bindBtn = document.getElementById('bindBtn')
const reportLoginBtn = document.getElementById('reportLoginBtn')
const pollBtn = document.getElementById('pollBtn')
const selfTestBtn = document.getElementById('selfTestBtn')
const ACTIVE_PROFILE_KEY = 'geoEnvActiveProfile'
const PROFILES_KEY = 'geoEnvProfiles'
const SESSIONS_KEY = 'geoEnvSessions'
const LEGACY_CONFIG_KEY = 'geoEnvConfig'
const LEGACY_SESSION_KEY = 'geoEnvSession'
const DEFAULT_PROFILE_KEY = 'prod'
const DEFAULT_PROFILE_CONFIGS = {
  dev: {
    profileKey: 'dev',
    profileLabel: '本地开发',
    apiBase: 'http://127.0.0.1:8080',
    helperBase: 'http://127.0.0.1:17891',
  },
  prod: {
    profileKey: 'prod',
    profileLabel: '生产环境',
    apiBase: 'https://www.huanjingaigeo.com',
    helperBase: 'http://127.0.0.1:17891',
  },
}
const BUILD_PROFILE_CONFIG = globalThis.GEO_ENV_BUILD_CONFIG || DEFAULT_PROFILE_CONFIGS.prod
const BUILD_PROFILE_KEY = normalizeProfileKey(BUILD_PROFILE_CONFIG.profileKey || DEFAULT_PROFILE_KEY)
const BUILD_PROFILE_LABEL = BUILD_PROFILE_CONFIG.profileLabel || BUILD_PROFILE_CONFIG.label || DEFAULT_PROFILE_CONFIGS[BUILD_PROFILE_KEY]?.profileLabel || BUILD_PROFILE_KEY

function log(value) {
  const text = typeof value === 'string' ? value : JSON.stringify(value, null, 2)
  logEl.textContent = `${new Date().toLocaleTimeString()} ${text}\n${logEl.textContent}`.slice(0, 6000)
}

function normalizeBaseUrl(value) {
  return String(value || '').replace(/\/+$/, '')
}

function normalizeProfileKey(value) {
  const text = String(value || '').trim().toLowerCase()
  return text.replace(/[^a-z0-9_-]/g, '') || DEFAULT_PROFILE_KEY
}

function defaultProfileConfig(profileKey) {
  const key = normalizeProfileKey(profileKey)
  const buildDefaults = key === BUILD_PROFILE_KEY ? BUILD_PROFILE_CONFIG : {}
  return {
    apiBase: buildDefaults.apiBase || DEFAULT_PROFILE_CONFIGS[key]?.apiBase || DEFAULT_PROFILE_CONFIGS.prod.apiBase,
    helperBase: buildDefaults.helperBase || DEFAULT_PROFILE_CONFIGS[key]?.helperBase || DEFAULT_PROFILE_CONFIGS.prod.helperBase,
    environmentKey: '',
    environmentAccountId: '',
    selfMediaAccountId: '',
    platform: '',
    brandId: '',
    autoRun: true,
    profileKey: key,
    profileLabel: buildDefaults.profileLabel || buildDefaults.label || DEFAULT_PROFILE_CONFIGS[key]?.profileLabel || key,
  }
}

async function loadProfileStore() {
  const stored = await chrome.storage.local.get([ACTIVE_PROFILE_KEY, PROFILES_KEY, SESSIONS_KEY, LEGACY_CONFIG_KEY, LEGACY_SESSION_KEY, 'geoEnvEventLog'])
  const profiles = {
    dev: defaultProfileConfig('dev'),
    prod: defaultProfileConfig('prod'),
    ...(stored[PROFILES_KEY] || {}),
  }
  const sessions = { ...(stored[SESSIONS_KEY] || {}) }
  if (stored[LEGACY_CONFIG_KEY] && !stored[PROFILES_KEY]?.prod) {
    profiles.prod = {
      ...profiles.prod,
      ...stored[LEGACY_CONFIG_KEY],
      profileKey: 'prod',
      profileLabel: '生产环境',
    }
  }
  if (stored[LEGACY_SESSION_KEY] && !sessions.prod) {
    sessions.prod = stored[LEGACY_SESSION_KEY]
  }
  profiles[BUILD_PROFILE_KEY] = {
    ...defaultProfileConfig(BUILD_PROFILE_KEY),
    ...(profiles[BUILD_PROFILE_KEY] || {}),
    apiBase: BUILD_PROFILE_CONFIG.apiBase || profiles[BUILD_PROFILE_KEY]?.apiBase,
    helperBase: BUILD_PROFILE_CONFIG.helperBase || profiles[BUILD_PROFILE_KEY]?.helperBase,
    profileKey: BUILD_PROFILE_KEY,
    profileLabel: BUILD_PROFILE_LABEL,
  }
  return {
    activeProfile: BUILD_PROFILE_KEY,
    profiles,
    sessions,
    events: stored.geoEnvEventLog,
  }
}

async function saveProfileStore(store) {
  const activeProfile = BUILD_PROFILE_KEY
  const legacyConfig = store.profiles?.prod || defaultProfileConfig('prod')
  const legacySession = store.sessions?.prod || null
  await chrome.storage.local.set({
    [ACTIVE_PROFILE_KEY]: activeProfile,
    [PROFILES_KEY]: store.profiles || {},
    [SESSIONS_KEY]: store.sessions || {},
    [LEGACY_CONFIG_KEY]: legacyConfig,
    [LEGACY_SESSION_KEY]: legacySession,
  })
}

async function saveActiveConfig(config) {
  const store = await loadProfileStore()
  const activeProfile = BUILD_PROFILE_KEY
  store.activeProfile = activeProfile
  store.profiles[activeProfile] = {
    ...(store.profiles[activeProfile] || defaultProfileConfig(activeProfile)),
    ...config,
    profileKey: activeProfile,
    profileLabel: BUILD_PROFILE_LABEL,
  }
  await saveProfileStore(store)
}

async function sendMessage(message) {
  return chrome.runtime.sendMessage(message)
}

async function load() {
  const result = await loadProfileStore()
  const activeProfile = result.activeProfile
  const session = result.sessions[activeProfile] || null
  const sessionBrandId = session?.brandId || ''
  const config = {
    ...defaultProfileConfig(activeProfile),
    brandId: sessionBrandId,
    ...(result.profiles[activeProfile] || {}),
    profileKey: activeProfile,
  }
  profileHintEl.textContent = `运行环境：${config.profileLabel || activeProfile}（${activeProfile}），由扩展包固定。`
  if (!config.brandId && sessionBrandId) config.brandId = sessionBrandId
  for (const [key, el] of Object.entries(fields)) {
    if (!(key in config)) continue
    if (el.type === 'checkbox') {
      el.checked = config[key] !== false
    } else {
      el.value = config[key] ?? ''
    }
  }
  renderStatus(session, activeProfile)
  renderStoredLogs(result.events)
}

function renderStoredLogs(events) {
  if (!Array.isArray(events) || !events.length) return
  const text = events.slice(0, 20).map((event) => {
    const time = event.at ? new Date(event.at).toLocaleTimeString() : ''
    if (event.type === 'login_report') {
      if (event.ok) return `${time} 登录状态上报成功：environmentKey=${event.environmentKey || '-'}，platform=${event.platform || '-'}，status=${event.status || '-'}`
      return `${time} 登录状态上报失败：platform=${event.platform || '-'}，${event.error || 'unknown error'}`
    }
    if (event.type === 'runtime_config') {
      if (event.ok) return `${time} 配置发现成功：environmentKey=${event.environmentKey || '-'}，platform=${event.platform || '-'}，status=${event.status || '-'}`
      return `${time} 配置发现未采用：${event.error || event.selectionStatus || 'unknown'}`
    }
    if (event.type === 'bind_intent') {
      if (event.ok) return `${time} 自动绑定成功：environmentKey=${event.environmentKey || '-'}，brandId=${event.brandId || '-'}`
      return `${time} 自动绑定失败：${event.error || 'unknown error'}`
    }
    if (event.ok) return `${time} 自动处理成功：taskId=${event.taskId || '-'}，platform=${event.platform || '-'}`
    return `${time} 自动处理失败：${event.error || 'unknown error'}`
  }).join('\n')
  logEl.textContent = `${text}\n${logEl.textContent}`.slice(0, 6000)
}

function collectConfig() {
  const profileKey = BUILD_PROFILE_KEY
  return {
    profileKey,
    profileLabel: BUILD_PROFILE_LABEL,
    apiBase: normalizeBaseUrl(fields.apiBase.value || DEFAULT_PROFILE_CONFIGS[profileKey]?.apiBase || DEFAULT_PROFILE_CONFIGS.prod.apiBase),
    helperBase: normalizeBaseUrl(fields.helperBase.value || 'http://127.0.0.1:17891'),
    environmentKey: fields.environmentKey.value.trim(),
    environmentAccountId: fields.environmentAccountId.value ? Number(fields.environmentAccountId.value) : null,
    selfMediaAccountId: fields.selfMediaAccountId.value ? Number(fields.selfMediaAccountId.value) : null,
    platform: normalizePlatform(fields.platform.value),
    brandId: fields.brandId.value ? Number(fields.brandId.value) : null,
    autoRun: fields.autoRun.checked,
  }
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
    '抖音': 'douyin',
    '抖音图文': 'douyin',
    'douyin': 'douyin',
  }
  return aliases[text] || text
}

function renderStatus(session, profileKey = BUILD_PROFILE_KEY) {
  const label = profileKey === BUILD_PROFILE_KEY ? BUILD_PROFILE_LABEL : DEFAULT_PROFILE_CONFIGS[normalizeProfileKey(profileKey)]?.profileLabel || profileKey
  if (!session?.extensionToken) {
    statusEl.textContent = `状态：${label}，未绑定后台`
    return
  }
  statusEl.textContent = `状态：${label}，已绑定，sessionId=${session.sessionId || '-'}`
}

saveBtn.addEventListener('click', async () => {
  try {
    const config = collectConfig()
    await saveActiveConfig(config)
    log(`配置已保存：profile=${config.profileKey}，environmentKey=${config.environmentKey}`)
  } catch (error) {
    log(`保存失败：${error.message}`)
  }
})

bindBtn.addEventListener('click', async () => {
  bindBtn.disabled = true
  try {
    const config = collectConfig()
    await saveActiveConfig(config)
    const response = await sendMessage({
      type: 'GEO_ENV_BIND',
      bindCode: fields.bindCode.value,
    })
    if (!response?.ok) throw new Error(response?.error || '绑定失败')
    renderStatus(response.session, config.profileKey)
    log({ bind: 'ok', session: response.session })
  } catch (error) {
    log(`绑定失败：${error.message}`)
  } finally {
    bindBtn.disabled = false
  }
})

reportLoginBtn.addEventListener('click', async () => {
  reportLoginBtn.disabled = true
  try {
    const config = collectConfig()
    await saveActiveConfig(config)
    const response = await sendMessage({ type: 'GEO_ENV_REPORT_LOGIN_STATUS' })
    if (!response?.ok) throw new Error(response?.error || '上报失败')
    const backend = response.status?.backendStatus
    if (backend?.loginStatus) {
      log(`上报成功：后台状态=${backend.loginStatus}，期望名称=${backend.expectedAccountName || '-'}，期望ID=${backend.expectedPlatformAccountId || '-'}，错误=${backend.lastErrorCode || '-'}`)
    }
    log({ reportLoginStatus: 'ok', status: response.status })
  } catch (error) {
    log(`上报失败：${error.message}`)
  } finally {
    reportLoginBtn.disabled = false
  }
})

pollBtn.addEventListener('click', async () => {
  pollBtn.disabled = true
  try {
    const response = await sendMessage({ type: 'GEO_ENV_POLL_ONCE' })
    if (!response?.ok) throw new Error(response?.error || '领取失败')
    log(response)
  } catch (error) {
    log(`领取失败：${error.message}`)
  } finally {
    pollBtn.disabled = false
  }
})

selfTestBtn.addEventListener('click', async () => {
  selfTestBtn.disabled = true
  try {
    const response = await sendMessage({ type: 'GEO_ENV_SELF_TEST' })
    if (!response?.ok) throw new Error(response?.error || '自检失败')
    log({ selfTest: 'ok', checks: response.checks })
  } catch (error) {
    log(`自检失败：${error.message}`)
  } finally {
    selfTestBtn.disabled = false
  }
})

load().catch((error) => log(`加载失败：${error.message}`))

sendMessage({ type: 'GEO_ENV_BIND_FROM_INTENT' })
  .then(async (response) => {
    if (!response?.ok || response.skipped) return
    renderStatus(response.session)
    log(`已通过本地助手自动绑定：environmentKey=${response.config?.environmentKey || '-'}`)
    await load()
  })
  .catch((error) => log(`自动绑定失败：${error.message}`))
