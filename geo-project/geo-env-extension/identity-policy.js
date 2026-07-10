(function initGeoIdentityPolicy(global) {
  function evaluateExpectedIdentity(input = {}) {
    const platform = String(input.platform || '').trim().toLowerCase()
    const expectedName = String(input.expectedName || '').trim()
    const currentNames = uniqueStrings(input.currentNames)

    if (!expectedName) {
      return {
        matched: false,
        code: 'IDENTITY_EXPECTATION_MISSING',
        message: `${platform || '平台'}任务缺少期望账号名称，已阻止填充`,
      }
    }
    if (currentNames.includes(expectedName)) {
      return { matched: true, method: 'accountName' }
    }
    return {
      matched: false,
      code: 'TASK_ACCOUNT_IDENTITY_NOT_CONFIRMED',
      message: `账号名称未确认：当前名称=${currentNames.join(',') || '未读取到'}，期望名称=${expectedName}，已阻止填充`,
    }
  }

  function uniqueStrings(values) {
    return Array.from(new Set((Array.isArray(values) ? values : [])
      .map((value) => String(value || '').trim())
      .filter(Boolean)))
  }

  global.__GEO_IDENTITY_POLICY__ = { evaluateExpectedIdentity }
})(globalThis)
