(function attachToutiaoPlatform(global) {
  const WORKS_LIST_URL = 'https://mp.toutiao.com/profile_v4/graphic/articles'

  function isWorksManageUrl(value) {
    try {
      const url = new URL(String(value || ''), WORKS_LIST_URL)
      if (url.hostname !== 'mp.toutiao.com') return false
      return /\/profile_v\d+\/graphic\/articles(?:\/|$)/.test(url.pathname)
        || /\/profile_v\d+\/manage(?:\/|$)/.test(url.pathname)
        || url.pathname.includes('/profile_v4/content-manage')
    } catch (_) {
      return false
    }
  }

  global.__GEO_TOUTIAO_PLATFORM__ = {
    WORKS_LIST_URL,
    isWorksManageUrl,
  }
})(globalThis)
