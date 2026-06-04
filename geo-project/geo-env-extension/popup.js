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
const logEl = document.getElementById('log')
const saveBtn = document.getElementById('saveBtn')
const bindBtn = document.getElementById('bindBtn')
const reportLoginBtn = document.getElementById('reportLoginBtn')
const pollBtn = document.getElementById('pollBtn')
const selfTestBtn = document.getElementById('selfTestBtn')

function log(value) {
  const text = typeof value === 'string' ? value : JSON.stringify(value, null, 2)
  logEl.textContent = `${new Date().toLocaleTimeString()} ${text}\n${logEl.textContent}`.slice(0, 6000)
}

function normalizeBaseUrl(value) {
  return String(value || '').replace(/\/+$/, '')
}

async function sendMessage(message) {
  return chrome.runtime.sendMessage(message)
}

async function load() {
  const result = await chrome.storage.local.get(['geoEnvConfig', 'geoEnvSession', 'geoEnvEventLog'])
  const sessionBrandId = result.geoEnvSession?.brandId || ''
  const config = {
    apiBase: 'http://119.45.154.127',
    helperBase: 'http://127.0.0.1:17891',
    environmentKey: 'geo_b',
    environmentAccountId: '',
    selfMediaAccountId: '',
    platform: '',
    brandId: sessionBrandId,
    autoRun: true,
    ...(result.geoEnvConfig || {}),
  }
  if (!config.brandId && sessionBrandId) config.brandId = sessionBrandId
  for (const [key, el] of Object.entries(fields)) {
    if (!(key in config)) continue
    if (el.type === 'checkbox') {
      el.checked = config[key] !== false
    } else {
      el.value = config[key] ?? ''
    }
  }
  renderStatus(result.geoEnvSession)
  renderStoredLogs(result.geoEnvEventLog)
}

function renderStoredLogs(events) {
  if (!Array.isArray(events) || !events.length) return
  const text = events.slice(0, 20).map((event) => {
    const time = event.at ? new Date(event.at).toLocaleTimeString() : ''
    if (event.type === 'login_report') {
      if (event.ok) return `${time} 登录状态上报成功：environmentKey=${event.environmentKey || '-'}，platform=${event.platform || '-'}，status=${event.status || '-'}`
      return `${time} 登录状态上报失败：platform=${event.platform || '-'}，${event.error || 'unknown error'}`
    }
    if (event.ok) return `${time} 自动处理成功：taskId=${event.taskId || '-'}，platform=${event.platform || '-'}`
    return `${time} 自动处理失败：${event.error || 'unknown error'}`
  }).join('\n')
  logEl.textContent = `${text}\n${logEl.textContent}`.slice(0, 6000)
}

function collectConfig() {
  return {
    apiBase: normalizeBaseUrl(fields.apiBase.value || 'http://119.45.154.127'),
    helperBase: normalizeBaseUrl(fields.helperBase.value || 'http://127.0.0.1:17891'),
    environmentKey: fields.environmentKey.value.trim() || 'geo_b',
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
  }
  return aliases[text] || text
}

function renderStatus(session) {
  if (!session?.extensionToken) {
    statusEl.textContent = '状态：未绑定后台'
    return
  }
  statusEl.textContent = `状态：已绑定，sessionId=${session.sessionId || '-'}`
}

saveBtn.addEventListener('click', async () => {
  try {
    const config = collectConfig()
    await chrome.storage.local.set({ geoEnvConfig: config })
    log(`配置已保存：environmentKey=${config.environmentKey}`)
  } catch (error) {
    log(`保存失败：${error.message}`)
  }
})

bindBtn.addEventListener('click', async () => {
  bindBtn.disabled = true
  try {
    const config = collectConfig()
    await chrome.storage.local.set({ geoEnvConfig: config })
    const response = await sendMessage({
      type: 'GEO_ENV_BIND',
      bindCode: fields.bindCode.value,
    })
    if (!response?.ok) throw new Error(response?.error || '绑定失败')
    renderStatus(response.session)
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
    await chrome.storage.local.set({ geoEnvConfig: config })
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
