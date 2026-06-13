(function installGeoFillResultNormalizer(global) {
  function normalizeFillResult(fillResult, context) {
    const source = isPlainObject(fillResult) ? fillResult : {}
    const publishOptions = isPlainObject(source.publishOptions) ? source.publishOptions : {}
    const verification = normalizePublishVerification(publishOptions.publishVerification, publishOptions)
    const platformStatus = normalizePlatformStatus(firstText(
      verification.platformStatus,
      verification.status,
      publishOptions.platformStatus,
      publishOptions.status,
    ))
    const verified = verification.verified === true

    const scheduled = Boolean(publishOptions.scheduled === true || (verified && platformStatus === 'scheduled'))
    const published = Boolean(publishOptions.published === true || (verified && platformStatus === 'published'))

    return {
      ...source,
      platform: firstText(source.platform, context?.platform),
      taskId: source.taskId || context?.taskId || null,
      publishOptions: {
        ...publishOptions,
        filled: publishOptions.filled === true || source.titleFilled === true || source.contentFilled === true,
        scheduled,
        published,
        publishVerification: {
          ...verification,
          verified,
          platformStatus: platformStatus || verification.platformStatus || '',
        },
      },
    }
  }

  function normalizePublishVerification(value, publishOptions) {
    const verification = isPlainObject(value) ? value : {}
    const verified = verification.verified === true || verification.verified === 'true'
    return {
      ...verification,
      verified,
      platformStatus: firstText(verification.platformStatus, verification.status, publishOptions.platformStatus, publishOptions.status),
    }
  }

  function normalizePlatformStatus(value) {
    const text = String(value || '').trim().toLowerCase()
    if (['scheduled', 'schedule', 'platform_scheduled'].includes(text)) return 'scheduled'
    if (['published', 'publish', 'platform_published'].includes(text)) return 'published'
    return ''
  }

  function firstText() {
    for (const value of arguments) {
      if (typeof value === 'string' && value.trim()) return value.trim()
    }
    return ''
  }

  function isPlainObject(value) {
    return Boolean(value && typeof value === 'object' && !Array.isArray(value))
  }

  global.__GEO_FILL_RESULT__ = {
    normalizeFillResult,
  }
})(globalThis)
