(function initGeoIdentityPolicy(global) {
  function evaluateExpectedIdentity(input = {}) {
    const platform = String(input.platform || '').trim().toLowerCase()
    const expectedName = normalizeName(input.expectedName)
    const currentNames = uniqueNormalizedNames(input.currentNames)

    if (!expectedName) {
      return {
        matched: false,
        code: 'IDENTITY_EXPECTATION_MISSING',
        message: `${platform || '平台'}任务缺少期望账号名称，已阻止填充`,
      }
    }
    if (currentNames.length !== 1) {
      return {
        matched: false,
        code: currentNames.length > 1 ? 'MULTIPLE_IDENTITIES' : 'TASK_ACCOUNT_IDENTITY_NOT_CONFIRMED',
        message: currentNames.length > 1
          ? `读取到多个候选账号名称：${currentNames.join(',')}，已阻止填充`
          : `账号名称未确认：当前名称=未读取到，期望名称=${expectedName}，已阻止填充`,
      }
    }
    if (currentNames[0] === expectedName) {
      return { matched: true, method: 'accountName' }
    }
    return {
      matched: false,
      code: 'TASK_ACCOUNT_IDENTITY_NOT_CONFIRMED',
      message: `账号名称未确认：当前名称=${currentNames.join(',') || '未读取到'}，期望名称=${expectedName}，已阻止填充`,
    }
  }

  function uniqueNormalizedNames(values) {
    return Array.from(new Set((Array.isArray(values) ? values : [])
      .map(normalizeName)
      .filter(Boolean)))
  }

  function normalizeName(value) {
    return String(value || '')
      .normalize('NFKC')
      .trim()
      .replace(/\s+/g, ' ')
      .toLowerCase()
  }

  global.__GEO_IDENTITY_POLICY__ = { evaluateExpectedIdentity }
})(globalThis)
