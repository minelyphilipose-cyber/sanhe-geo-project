export function evaluateXiaohongshuPublishSignals(target = {}, pageState = {}, options = {}) {
  const text = String(pageState.text || '')
  const normalizedText = normalizeCompact(text)
  const normalizedTitleText = normalizeTitleCompact(text)
  const titleProbe = titleProbeOf(target.title)
  const noteCards = xiaohongshuCardCandidates(
    pageState.xiaohongshuCards,
    target.title,
    target.platformScheduledAt,
  )
  const matchedCard = noteCards.find((item) => item.titleMatched) || null
  const hasTitle = Boolean(titleProbe && normalizedTitleText.includes(titleProbe)) || Boolean(matchedCard)
  const scheduleProbe = normalizeScheduleProbe(target.platformScheduledAt)
  const hasScheduleTime = !scheduleProbe
    || normalizedText.includes(scheduleProbe)
    || normalizeScheduleProbe(matchedCard?.publishedAt).includes(scheduleProbe)
  const scheduledAtMs = parseLocalDateTimeMs(target.platformScheduledAt)
  const nowMs = Number.isFinite(options.nowMs) ? options.nowMs : Date.now()
  const isBeforeScheduledAt = Number.isFinite(scheduledAtMs) && scheduledAtMs > nowMs
  const url = String(pageState.url || '')
  const isNoteManager = /\/new\/note-manager/.test(url) || normalizedText.includes('笔记管理')
  const titleIndex = titleProbe ? normalizedTitleText.indexOf(titleProbe) : -1
  const targetTextWindow = titleIndex >= 0 ? normalizedTitleText.slice(titleIndex, titleIndex + 260) : ''
  const hasScheduledSignal = /定时发布|定时发布中|将于|发布时间/.test(targetTextWindow)
  const hasReviewSignal = /审核中|待审核|提交成功|已提交/.test(targetTextWindow)
  const hasRejectedSignal = /审核未通过|未通过|审核失败|发布失败|不通过/.test(targetTextWindow)
  const hasPublishedSignal = /发布成功|已发布/.test(targetTextWindow)
    || /\/explore\/|\/discovery\/item\//.test(url)
  const currentPublishedUrl = /xiaohongshu\.com\/(explore|discovery\/item)\//.test(url) ? url : ''

  const anchorCandidates = Array.isArray(pageState.anchors)
    ? pageState.anchors
        .map((item) => ({
          text: String(item.text || '').trim(),
          href: String(item.href || '').trim(),
          titleMatched: Boolean(titleProbe && normalizeTitleCompact(item.text).includes(titleProbe)),
        }))
        .filter((item) => item.titleMatched || /xiaohongshu\.com\/(explore|discovery\/item)\//.test(item.href))
    : []
  const matchedUrl = anchorCandidates.find((item) => {
    return item.titleMatched && /xiaohongshu\.com\/(explore|discovery\/item)\//.test(item.href)
  })?.href || ''
  const matchedNoteId = firstText(matchedCard?.noteId)
  const hasPublishedCard = hasTitle
    && isNoteManager
    && !hasScheduledSignal
    && !hasReviewSignal
    && !hasRejectedSignal
  const hasConfirmedPublishedEvidence = hasPublishedSignal || hasPublishedCard
  const pendingScheduled = hasTitle
    && !hasConfirmedPublishedEvidence
    && isBeforeScheduledAt
    && (hasScheduleTime || hasScheduledSignal || isNoteManager)
  const found = hasTitle && hasConfirmedPublishedEvidence
  const matchStrategy = matchedUrl
    ? 'anchor_title_url'
    : currentPublishedUrl
      ? 'current_detail_url'
      : hasPublishedCard
        ? 'note_manager_card'
        : hasTitle
          ? 'title_status_window'
          : 'title_probe'

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
    platformPublishedUrl: found ? (matchedUrl || currentPublishedUrl) : '',
    platformPublishId: found ? matchedNoteId : '',
    matchStrategy,
    candidateCount: anchorCandidates.length,
    cardCandidateCount: noteCards.length,
    matchedCard: matchedCard ? {
      title: matchedCard.title,
      publishedAt: matchedCard.publishedAt,
      noteId: matchedNoteId,
      titleMatched: matchedCard.titleMatched,
      titleScore: matchedCard.titleScore,
    } : undefined,
    topCandidates: anchorCandidates.slice(0, 5).map((item) => ({
      text: item.text.slice(0, 120),
      href: item.href,
      titleMatched: item.titleMatched,
    })),
    pageTitle: pageState.pageTitle || '',
    matchedText: targetTextWindow.slice(0, 300),
    textSample: text.slice(0, 1200),
  }
}

export function evaluateDouyinPublishSignals(target = {}, pageState = {}) {
  const normalizedTargetTitle = normalizeTitleCompact(target.title)
  const scheduleProbe = normalizeScheduleProbe(target.platformScheduledAt)
  const cards = (Array.isArray(pageState.douyinCards) ? pageState.douyinCards : [])
    .map((item) => {
      const title = String(item?.title || '').trim()
      const titleScore = partialTitleScore(normalizedTargetTitle, title)
      const status = firstText(item?.status, String(item?.text || '').match(/(定时发布中|已发布|审核中|发布成功|未通过|草稿)/)?.[1])
      const publishedAt = String(item?.publishedAt || '').trim()
      const scheduleMatched = !scheduleProbe || normalizeScheduleProbe(publishedAt).includes(scheduleProbe)
      let score = Math.round(titleScore * 1000)
      if (scheduleMatched) score += 500
      if (/已发布|发布成功/.test(status)) score += 340
      else if (/审核中/.test(status)) score += 260
      else if (/定时发布中/.test(status)) score += 180
      return {
        ...item,
        title,
        status,
        publishedAt,
        titleScore,
        titleMatched: titleScore >= 0.55,
        scheduleMatched,
        score,
      }
    })
    .filter((item) => item.titleMatched)
    .sort((left, right) => right.score - left.score)
  const matchedCard = cards[0] || null
  const status = matchedCard?.status || ''
  const pageStatusCode = /已发布|发布成功/.test(status)
    ? 'published'
    : /审核中/.test(status)
      ? 'reviewing'
      : /定时发布中/.test(status)
        ? 'scheduled'
        : ''
  const publishedLink = (matchedCard?.links || []).find((link) => /\/video\/|\/note\/|modal_id=|item_id=/.test(link.href || ''))
    || (matchedCard?.links || [])[0]
  const platformPublishId = douyinPublishIdFromUrl(publishedLink?.href)

  return {
    found: ['published', 'reviewing'].includes(pageStatusCode),
    pendingScheduled: pageStatusCode === 'scheduled',
    reason: matchedCard
      ? (pageStatusCode === 'scheduled' ? 'platform schedule time not due' : '')
      : 'title not matched in structured work cards',
    hasTitle: Boolean(matchedCard),
    hasPublishedSignal: Boolean(pageStatusCode),
    candidateCount: cards.length,
    topCandidates: cards.slice(0, 5).map((item) => ({
      text: String(item.text || '').slice(0, 180),
      title: item.title,
      publishedAt: item.publishedAt,
      status: item.status,
      titleMatched: item.titleMatched,
      titleScore: item.titleScore,
      width: item.width,
      height: item.height,
      score: item.score,
    })),
    matchedCard: matchedCard ? {
      title: matchedCard.title,
      publishedAt: matchedCard.publishedAt,
      status: matchedCard.status,
      titleScore: matchedCard.titleScore,
      width: matchedCard.width,
      height: matchedCard.height,
    } : undefined,
    platformStatus: pageStatusCode || 'unknown',
    pageStatusCode,
    pageStatus: status,
    targetTitle: target.title || '',
    platformScheduledAt: target.platformScheduledAt || '',
    scheduledAtText: matchedCard?.scheduleMatched ? matchedCard.publishedAt : '',
    url: publishedLink?.href || pageState.url || '',
    platformPublishedUrl: '',
    platformPublishId,
    coverImageUrl: matchedCard?.coverImageUrl || '',
    pageTitle: pageState.pageTitle || '',
    matchedText: String(matchedCard?.text || '').slice(0, 300),
    textSample: String(pageState.text || '').slice(0, 1200),
  }
}

export function evaluateBaijiahaoPublishSignals(target = {}, pageState = {}, options = {}) {
  const text = String(pageState.text || '')
  const normalizedText = normalizeCompact(text)
  const normalizedTitleText = normalizeTitleCompact(text)
  const titleProbe = titleProbeOf(target.title, 20)
  const normalizedTargetTitle = normalizeTitleCompact(target.title)
  const pageHasTitle = Boolean(titleProbe && normalizedTitleText.includes(titleProbe))
  const scheduleProbe = normalizeScheduleProbe(target.platformScheduledAt)
  const hasScheduleTime = !scheduleProbe || normalizedText.includes(scheduleProbe)
  const scheduledAtMs = parseLocalDateTimeMs(target.platformScheduledAt)
  const nowMs = Number.isFinite(options.nowMs) ? options.nowMs : Date.now()
  const isBeforeScheduledAt = Number.isFinite(scheduledAtMs) && scheduledAtMs > nowMs
  const url = String(pageState.url || '')
  const anchorCandidates = Array.isArray(pageState.anchors)
    ? pageState.anchors
        .map((item) => ({
          text: String(item.text || '').trim(),
          href: String(item.href || '').trim(),
          titleScore: baijiahaoTitleMatchScore(normalizedTargetTitle, item.text),
          titleMatched: baijiahaoTitleMatched(normalizedTargetTitle, item.text),
          isPublicUrl: /baijiahao\.baidu\.com\/s\?id=/.test(String(item.href || '')),
        }))
        .filter((item) => item.titleMatched || item.isPublicUrl)
    : []
  const matchedUrl = anchorCandidates.find((item) => {
    return item.titleMatched && item.isPublicUrl
  })?.href || ''
  const cardCandidates = baijiahaoCardCandidates(pageState.baijiahaoCards, normalizedTargetTitle)
  const matchedCard = cardCandidates.find((item) => item.titleMatched) || null
  const hasTitle = pageHasTitle || Boolean(matchedCard) || anchorCandidates.some((item) => item.titleMatched)
  const matchedCardText = String(matchedCard?.text || '')
  const matchedCardStatusText = firstText(matchedCard?.status, matchedCardText)
  const matchedCardUrl = firstText(matchedCard?.publishedUrl, matchedCard?.publicUrl)
  const titleIndex = titleProbe ? normalizedTitleText.indexOf(titleProbe) : -1
  const targetTextWindow = titleIndex >= 0 ? normalizedTitleText.slice(titleIndex, titleIndex + 240) : ''
  const cardWindow = normalizeTitleCompact(matchedCardText).slice(0, 320)
  const hasRejectedSignal = /审核未通过|未通过|审核失败|发布失败|不通过/.test(targetTextWindow)
    || /审核未通过|未通过|审核失败|发布失败|不通过/.test(cardWindow)
  const hasWithdrawnSignal = /已撤回|已删除|已下线|已撤销/.test(targetTextWindow)
    || /已撤回|已删除|已下线|已撤销/.test(cardWindow)
  const hasReviewSignal = /审核中|待审核|提交成功|已提交/.test(targetTextWindow)
    || /审核中|待审核|提交成功|已提交/.test(cardWindow)
  const hasScheduledSignal = /预计\d{4}[-年]\d{1,2}[-月]\d{1,2}|预计\s*\d{4}|定时发布|发布时间|待发布|将于/.test(targetTextWindow)
    || /预计\d{4}[-年]\d{1,2}[-月]\d{1,2}|预计\s*\d{4}|定时发布|发布时间|待发布|将于/.test(cardWindow)
  const hasPublishedNearTitle = /已发布|已推荐|发布成功/.test(targetTextWindow)
    || /已发布|已推荐|发布成功/.test(matchedCardStatusText)
  const hasPagePublishedSignal = /已发布|已推荐|发布成功/.test(text)
  const hasPublishedSignal = matchedCard ? hasPublishedNearTitle : (hasPublishedNearTitle || hasPagePublishedSignal)
  const platformScheduledText = extractBaijiahaoScheduledText(text)
  const evidence = baijiahaoCheckEvidence({
    pageState,
    cardCandidates,
    anchorCandidates,
    matchedCard,
    matchedUrl,
    normalizedTargetTitle,
  })

  const pendingScheduled = hasTitle
    && !hasPublishedNearTitle
    && isBeforeScheduledAt
    && (hasScheduleTime || hasScheduledSignal || hasReviewSignal)
  const failed = hasTitle && (hasRejectedSignal || hasWithdrawnSignal)
  const reviewing = hasTitle && hasReviewSignal && !hasPublishedSignal && !failed
  const scheduled = hasTitle && hasScheduledSignal && !hasPublishedSignal && !failed
  const found = hasTitle && hasPublishedNearTitle
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
  const resolvedPublishedUrl = matchedCardUrl || matchedUrl
  const matchStrategy = matchedCard && matchedCardUrl
      ? 'article_card_public_url'
    : matchedUrl
      ? 'anchor_title_public_url'
      : matchedCard && hasPublishedNearTitle
        ? 'article_card_status_window'
        : hasPublishedNearTitle
          ? 'title_status_window'
          : hasTitle
            ? 'title_only'
            : 'title_probe'

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
    platformPublishedUrl: found ? resolvedPublishedUrl : '',
    matchStrategy,
    checkStages: {
      pageOpened: Boolean(url),
      listLoaded: evidence.listLoaded,
      listItemCount: evidence.listItemCount,
      candidateCount: anchorCandidates.length,
      cardCandidateCount: cardCandidates.length,
      titleMatched: hasTitle,
      statusResolved: platformStatus,
      publicUrlMatched: Boolean(resolvedPublishedUrl),
      backendReportReady: found || failed || pendingScheduled || reviewing || scheduled,
    },
    evidence,
    candidateCount: anchorCandidates.length,
    cardCandidateCount: cardCandidates.length,
    matchedCard: matchedCard ? {
      title: matchedCard.title,
      status: matchedCard.status,
      publishedAt: matchedCard.publishedAt,
      publishedUrl: matchedCardUrl,
      titleMatched: matchedCard.titleMatched,
      text: matchedCard.text.slice(0, 240),
    } : undefined,
    topCandidates: anchorCandidates.slice(0, 5).map((item) => ({
      text: item.text.slice(0, 120),
      href: item.href,
      titleMatched: item.titleMatched,
      titleScore: item.titleScore,
      isPublicUrl: item.isPublicUrl,
    })),
    pageTitle: pageState.pageTitle || '',
    matchedText: (matchedCardText || targetTextWindow).slice(0, 300),
    textSample: text.slice(0, 1200),
  }
}

function baijiahaoCardCandidates(cards, normalizedTargetTitle) {
  if (!Array.isArray(cards)) return []
  return cards
    .map((card) => {
      const anchors = Array.isArray(card?.anchors) ? card.anchors : []
      const publicAnchor = anchors.find((item) => /baijiahao\.baidu\.com\/s\?id=/.test(String(item.href || ''))) || null
      const title = firstText(card?.title, publicAnchor?.text)
      const text = firstText(card?.text, title)
      return {
        title,
        text,
        status: firstText(card?.status),
        publishedAt: firstText(card?.publishedAt, card?.time),
        publishedUrl: firstText(card?.publishedUrl, publicAnchor?.href),
        publicUrl: firstText(publicAnchor?.href),
        titleScore: baijiahaoTitleMatchScore(normalizedTargetTitle, firstText(title, text)),
        titleMatched: baijiahaoTitleMatched(normalizedTargetTitle, firstText(title, text)),
      }
    })
    .filter((item) => item.title || item.text || item.publishedUrl || item.publicUrl)
}

function baijiahaoTitleMatched(normalizedTargetTitle, candidateTitle) {
  return baijiahaoTitleMatchScore(normalizedTargetTitle, candidateTitle) >= 0.62
}

function baijiahaoTitleMatchScore(normalizedTargetTitle, candidateTitle) {
  const target = String(normalizedTargetTitle || '')
  const candidate = normalizeTitleCompact(candidateTitle)
  if (!target || !candidate) return 0
  const probe = target.length > 20 ? target.slice(0, 20) : target
  if (probe && candidate.includes(probe)) return 1
  if (target.length >= 12 && (candidate.includes(target) || target.includes(candidate))) return 0.92
  return titleBigramsSimilarity(target, candidate)
}

function baijiahaoCheckEvidence({ pageState, cardCandidates, anchorCandidates, matchedCard, matchedUrl, normalizedTargetTitle }) {
  const listItemCount = Array.isArray(pageState?.baijiahaoCards) ? pageState.baijiahaoCards.length : 0
  const topCardCandidates = cardCandidates
    .slice()
    .sort((left, right) => Number(right.titleScore || 0) - Number(left.titleScore || 0))
    .slice(0, 5)
    .map((item) => ({
      title: item.title,
      status: item.status,
      publishedAt: item.publishedAt,
      publishedUrl: item.publishedUrl || item.publicUrl,
      titleScore: Number(item.titleScore || 0).toFixed(2),
      titleMatched: item.titleMatched,
    }))
  const topAnchorCandidates = anchorCandidates
    .slice()
    .sort((left, right) => Number(right.titleScore || 0) - Number(left.titleScore || 0))
    .slice(0, 5)
    .map((item) => ({
      text: item.text.slice(0, 120),
      href: item.href,
      titleScore: Number(item.titleScore || 0).toFixed(2),
      titleMatched: item.titleMatched,
      isPublicUrl: item.isPublicUrl,
    }))
  return {
    listLoaded: listItemCount > 0 || /作品管理|全部|已发布|待发布/.test(String(pageState?.text || '')),
    listItemCount,
    targetTitleNormalized: String(normalizedTargetTitle || '').slice(0, 120),
    bestTitleScore: Number(Math.max(
      0,
      ...cardCandidates.map((item) => Number(item.titleScore || 0)),
      ...anchorCandidates.map((item) => Number(item.titleScore || 0)),
    ).toFixed(2)),
    matchedTitle: firstText(matchedCard?.title),
    matchedStatus: firstText(matchedCard?.status),
    matchedPublishedAt: firstText(matchedCard?.publishedAt),
    matchedPublishedUrl: firstText(matchedCard?.publishedUrl, matchedCard?.publicUrl, matchedUrl),
    topCardCandidates,
    topAnchorCandidates,
  }
}

function titleBigramsSimilarity(left, right) {
  const leftSet = titleBigrams(left)
  const rightSet = titleBigrams(right)
  if (!leftSet.size || !rightSet.size) return 0
  let hit = 0
  for (const item of leftSet) {
    if (rightSet.has(item)) hit += 1
  }
  return hit / Math.min(leftSet.size, rightSet.size)
}

function titleBigrams(value) {
  const text = String(value || '')
  const result = new Set()
  for (let index = 0; index < text.length - 1; index += 1) {
    result.add(text.slice(index, index + 2))
  }
  return result
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
  const normalizedTitle = normalizeTitleCompact(value)
  return normalizedTitle.length > maxLength ? normalizedTitle.slice(0, maxLength) : normalizedTitle
}

function xiaohongshuCardCandidates(values, targetTitle, platformScheduledAt) {
  const normalizedTarget = normalizeTitleCompact(targetTitle)
  const scheduleProbe = normalizeScheduleProbe(platformScheduledAt)
  if (!normalizedTarget) return []
  return (Array.isArray(values) ? values : [])
    .map((item) => {
      const title = String(item?.title || '').trim()
      const titleScore = partialTitleScore(normalizedTarget, title)
      const scheduleMatched = !scheduleProbe
        || normalizeScheduleProbe(item?.publishedAt).includes(scheduleProbe)
      return {
        ...item,
        title,
        titleScore,
        titleMatched: titleScore >= 0.55,
        scheduleMatched,
      }
    })
    .filter((item) => item.titleMatched)
    .sort((left, right) => Number(right.scheduleMatched) - Number(left.scheduleMatched)
      || right.titleScore - left.titleScore)
}

function partialTitleScore(normalizedTarget, candidateTitle) {
  const candidate = normalizeTitleCompact(candidateTitle)
  if (!normalizedTarget || !candidate) return 0
  if (normalizedTarget === candidate) return 1
  const shorterLength = Math.min(normalizedTarget.length, candidate.length)
  let commonPrefixLength = 0
  while (commonPrefixLength < shorterLength
    && normalizedTarget[commonPrefixLength] === candidate[commonPrefixLength]) {
    commonPrefixLength += 1
  }
  const containmentLength = normalizedTarget.includes(candidate)
    ? candidate.length
    : candidate.includes(normalizedTarget)
      ? normalizedTarget.length
      : 0
  const matchedLength = Math.max(commonPrefixLength, containmentLength)
  if (matchedLength < Math.min(12, shorterLength)) return 0
  return matchedLength / shorterLength
}

function douyinPublishIdFromUrl(value) {
  const href = String(value || '')
  for (const pattern of [/\/video\/(\d+)/, /modal_id=(\d+)/, /item_id=(\d+)/, /\/note\/([^/?#]+)/]) {
    const match = href.match(pattern)
    if (match?.[1]) return match[1]
  }
  return ''
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

function firstText(...values) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return ''
}

function normalizeCompact(value) {
  return String(value || '').replace(/\s+/g, '').trim()
}

function normalizeTitleCompact(value) {
  return normalizeCompact(value)
    .replace(/[「」『』【】\[\]（）()《》<>“”"‘’'`,，。！？!?、:：；;·.\-—_]/g, '')
}
