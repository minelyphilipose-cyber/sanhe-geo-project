export function normalizeLocalAgentErrorMessage(message?: string | null, fallback = '本地助手请求失败') {
  const raw = (message || '').trim()
  if (!raw) return fallback

  const lower = raw.toLowerCase()
  if (lower.includes('local agent session not found')) {
    return '本地助手配对已失效，请重新配对本地助手'
  }
  if (lower.includes('local agent session revoked')) {
    return '本地助手配对已被吊销，请重新配对本地助手'
  }
  if (lower.includes('local agent session expired')) {
    return '本地助手配对已过期，请重新配对本地助手'
  }
  if (lower.includes('invalid helper access token')) {
    return '本地助手认证已失效，请重新配对本地助手'
  }
  if (lower.includes('require api-key') || lower.includes('api key')) {
    return '本地助手缺少 AdsPower API Key，请到「个人中心 > 本地助手」完成 AdsPower 连接配置'
  }
  if (lower.includes('helper request timestamp expired')) {
    return '本地助手请求时间已过期，请确认电脑时间正确后重试'
  }
  if (lower.includes('invalid helper request signature')) {
    return '本地助手请求签名无效，请重新配对本地助手'
  }
  if (lower.includes('replayed helper request nonce')) {
    return '本地助手请求已被拒绝：检测到重复请求，请重试'
  }
  if (lower.includes('failed to fetch') || lower.includes('networkerror')) {
    return '无法连接本地助手，请确认本地助手已启动，并允许当前后台地址访问'
  }
  if (lower.includes('load failed')) {
    return '本地助手请求加载失败，请确认本地助手已启动'
  }
  return raw
}
