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
  const titleIndex = titleProbe ? normalizedText.indexOf(titleProbe) : -1
  const targetTextWindow = titleIndex >= 0 ? normalizedText.slice(titleIndex, titleIndex + 260) : ''
  const hasScheduledSignal = /定时发布|定时发布中|将于|发布时间/.test(targetTextWindow)
  const hasReviewSignal = /审核中|待审核|提交成功|已提交/.test(targetTextWindow)
  const hasRejectedSignal = /审核未通过|未通过|审核失败|发布失败|不通过/.test(targetTextWindow)
  const hasPublishedSignal = /发布成功|已发布/.test(targetTextWindow)
    || /\/explore\/|\/discovery\/item\//.test(url)

  const matchedUrl = Array.isArray(pageState.anchors)
    ? pageState.anchors.find((item) => normalizeCompact(item.text).includes(titleProbe))?.href || ''
    : ''
  const pendingScheduled = hasTitle && isBeforeScheduledAt && (hasScheduleTime || hasScheduledSignal || isNoteManager)
  const hasPublishedCard = hasTitle
    && isNoteManager
    && hasScheduleTime
    && !hasScheduledSignal
    && !hasReviewSignal
    && !hasRejectedSignal
  const found = hasTitle && !isBeforeScheduledAt && (hasPublishedSignal || hasPublishedCard)

  return {
    found,
    pendingScheduled,
    reason: pendingScheduled
      ? 'platform schedule time not due'
      : found
        ? (hasPublishedCard ? 'matched published note card' : 'matched title and platform status')
        : hasTitle && !hasPublishedSignal
        ? 'title matched but published signal missing'
        : 'title not matched',
    hasTitle,
    hasScheduleTime,
    hasScheduledSignal,
    hasReviewSignal,
    hasRejectedSignal,
    hasPublishedSignal,
    hasPublishedCard,
    isBeforeScheduledAt,
    isNoteManager,
    platformStatus: found ? 'published' : (pendingScheduled ? 'scheduled' : hasReviewSignal ? 'reviewing' : hasRejectedSignal ? 'rejected' : 'unknown'),
    targetTitle: target.title || '',
    platformScheduledAt: target.platformScheduledAt || '',
    scheduleProbe,
    url,
    platformPublishedUrl: '',
    pageTitle: pageState.pageTitle || '',
    matchedText: targetTextWindow.slice(0, 300),
    textSample: text.slice(0, 1200),
  }
}

export function evaluateBaijiahaoPublishSignals(target = {}, pageState = {}, options = {}) {
  const text = String(pageState.text || '')
  const normalizedText = normalizeCompact(text)
  const titleProbe = titleProbeOf(target.title, 20)
  const hasTitle = Boolean(titleProbe && normalizedText.includes(titleProbe))
  const scheduleProbe = normalizeScheduleProbe(target.platformScheduledAt)
  const hasScheduleTime = !scheduleProbe || normalizedText.includes(scheduleProbe)
  const scheduledAtMs = parseLocalDateTimeMs(target.platformScheduledAt)
  const nowMs = Number.isFinite(options.nowMs) ? options.nowMs : Date.now()
  const isBeforeScheduledAt = Number.isFinite(scheduledAtMs) && scheduledAtMs > nowMs
  const url = String(pageState.url || '')
  const matchedUrl = Array.isArray(pageState.anchors)
    ? pageState.anchors.find((item) => {
        const href = String(item.href || '')
        return normalizeCompact(item.text).includes(titleProbe) && /baijiahao\.baidu\.com\/s\?id=/.test(href)
      })?.href || ''
    : ''
  const hasRejectedSignal = /审核未通过|未通过|审核失败|发布失败|不通过/.test(text)
  const hasWithdrawnSignal = /已撤回|已删除|已下线|已撤销/.test(text)
  const hasReviewSignal = /审核中|待审核|提交成功|已提交/.test(text)
  const hasScheduledSignal = /预计\d{4}[-年]\d{1,2}[-月]\d{1,2}|预计\s*\d{4}|定时发布|发布时间|待发布|将于/.test(text)
  const titleIndex = titleProbe ? normalizedText.indexOf(titleProbe) : -1
  const targetTextWindow = titleIndex >= 0 ? normalizedText.slice(titleIndex, titleIndex + 240) : ''
  const hasPublishedNearTitle = /已发布|已推荐|发布成功/.test(targetTextWindow)
  const hasPublishedSignal = hasPublishedNearTitle || /已发布|已推荐|发布成功/.test(text)
  const platformScheduledText = extractBaijiahaoScheduledText(text)

  const pendingScheduled = hasTitle && isBeforeScheduledAt && (hasScheduleTime || hasScheduledSignal || hasReviewSignal)
  const failed = hasTitle && (hasRejectedSignal || hasWithdrawnSignal)
  const reviewing = hasTitle && hasReviewSignal && !hasPublishedSignal && !failed
  const scheduled = hasTitle && hasScheduledSignal && !hasPublishedSignal && !failed
  const found = hasTitle && !isBeforeScheduledAt && hasPublishedNearTitle
  const platformStatus = failed
    ? (hasRejectedSignal ? 'rejected' : 'withdrawn')
    : hasPublishedSignal
      ? 'published'
      : reviewing
        ? 'reviewing'
        : scheduled
          ? 'scheduled'
          : 'unknown'
  const reason = baijiahaoPublishCheckReason({
    pendingScheduled,
    failed,
    found,
    reviewing,
    scheduled,
    hasTitle,
    hasPublishedSignal,
    platformStatus,
  })

  return {
    found,
    failed,
    pendingScheduled,
    platformStatus,
    failureCode: failed
      ? (hasRejectedSignal ? 'BAIJIAHAO_REVIEW_REJECTED' : 'BAIJIAHAO_WORK_WITHDRAWN')
      : undefined,
    failureMessage: failed
      ? (hasRejectedSignal ? '百家号作品审核未通过或发布失败' : '百家号作品已撤回、删除或下线')
      : undefined,
    reason,
    hasTitle,
    hasScheduleTime,
    hasScheduledSignal,
    hasPublishedSignal,
    hasPublishedNearTitle,
    hasReviewSignal,
    hasRejectedSignal,
    hasWithdrawnSignal,
    isBeforeScheduledAt,
    targetTitle: target.title || '',
    platformScheduledAt: target.platformScheduledAt || '',
    platformScheduledText,
    scheduleProbe,
    url,
    platformPublishedUrl: found ? matchedUrl : '',
    pageTitle: pageState.pageTitle || '',
    textSample: text.slice(0, 1200),
  }
}

function extractBaijiahaoScheduledText(text) {
  const value = String(text || '')
  const patterns = [
    /预计\s*\d{4}[-年]\d{1,2}[-月]\d{1,2}[日\s]+\d{1,2}:\d{2}\s*发布/,
    /预计\s*\d{4}[-年]\d{1,2}[-月]\d{1,2}[日\s]+\d{1,2}点\d{1,2}分?\s*发布/,
    /将于\s*\d{4}[-年]\d{1,2}[-月]\d{1,2}[日\s]+\d{1,2}:\d{2}\s*发布/,
    /发布时间[:：]?\s*\d{4}[-年]\d{1,2}[-月]\d{1,2}[日\s]+\d{1,2}:\d{2}/,
  ]
  for (const pattern of patterns) {
    const match = value.match(pattern)
    if (match) return match[0].replace(/\s+/g, ' ').trim()
  }
  return ''
}

function baijiahaoPublishCheckReason(state) {
  if (state.pendingScheduled) return 'platform schedule time not due'
  if (state.failed) return state.platformStatus
  if (state.found) return 'matched title and platform status'
  if (state.reviewing) return 'title matched and platform is still reviewing'
  if (state.scheduled) return 'title matched and platform schedule is still pending'
  if (state.hasTitle && !state.hasPublishedSignal) return 'title matched but final published signal missing'
  return 'title not matched'
}

function titleProbeOf(value, maxLength = 18) {
  const normalizedTitle = normalizeCompact(value)
  return normalizedTitle.length > maxLength ? normalizedTitle.slice(0, maxLength) : normalizedTitle
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
