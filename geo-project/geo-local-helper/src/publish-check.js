export function evaluateXiaohongshuPublishSignals(target = {}, pageState = {}, options = {}) {
  const text = String(pageState.text || '')
  const normalizedText = normalizeCompact(text)
  const titleProbe = titleProbeOf(target.title)
  const hasTitle = Boolean(titleProbe && normalizedText.includes(titleProbe))
  const scheduleProbe = normalizeScheduleProbe(target.platformScheduledAt)
  const hasScheduleTime = !scheduleProbe || normalizedText.includes(scheduleProbe)
  const scheduledAtMs = parseLocalDateTimeMs(target.platformScheduledAt)
  const nowMs = Number.isFinite(options.nowMs) ? options.nowMs : Date.now()
  const isBeforeScheduledAt = Number.isFinite(scheduledAtMs) && scheduledAtMs > nowMs
  const url = String(pageState.url || '')
  const isNoteManager = /\/new\/note-manager/.test(url) || normalizedText.includes('笔记管理')
  const hasScheduledSignal = /定时发布|定时发布中|将于|发布时间/.test(text)
  const hasPublishedSignal = /发布成功|已发布|审核中/.test(text)
    || /\/explore\/|\/discovery\/item\//.test(url)

  const matchedUrl = Array.isArray(pageState.anchors)
    ? pageState.anchors.find((item) => normalizeCompact(item.text).includes(titleProbe))?.href || ''
    : ''
  const pendingScheduled = hasTitle && isBeforeScheduledAt && (hasScheduleTime || hasScheduledSignal || isNoteManager)

  return {
    found: hasTitle && !isBeforeScheduledAt && hasPublishedSignal,
    pendingScheduled,
    reason: pendingScheduled
      ? 'platform schedule time not due'
      : hasTitle && !hasPublishedSignal
        ? 'title matched but published signal missing'
        : 'title not matched',
    hasTitle,
    hasScheduleTime,
    hasScheduledSignal,
    hasPublishedSignal,
    isBeforeScheduledAt,
    isNoteManager,
    targetTitle: target.title || '',
    platformScheduledAt: target.platformScheduledAt || '',
    scheduleProbe,
    url: matchedUrl || url,
    pageTitle: pageState.pageTitle || '',
    textSample: text.slice(0, 1200),
  }
}

function titleProbeOf(value) {
  const normalizedTitle = normalizeCompact(value)
  return normalizedTitle.length > 18 ? normalizedTitle.slice(0, 18) : normalizedTitle
}

function normalizeScheduleProbe(value) {
  return normalizeCompact(String(value || '')
    .replace('T', ' ')
    .replace(/[年月/]/g, '-')
    .replace(/日/g, ' ')
    .replace(/:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?$/, ''))
}

function parseLocalDateTimeMs(value) {
  const text = String(value || '').trim()
  const match = text.match(/(\d{4})-(\d{1,2})-(\d{1,2})[T\s](\d{1,2}):(\d{1,2})(?::(\d{1,2}))?/)
  if (!match) return Number.NaN
  const year = Number(match[1])
  const month = Number(match[2]) - 1
  const day = Number(match[3])
  const hour = Number(match[4])
  const minute = Number(match[5])
  const second = Number(match[6] || 0)
  return new Date(year, month, day, hour, minute, second).getTime()
}

function normalizeCompact(value) {
  return String(value || '').replace(/\s+/g, '').trim()
}
