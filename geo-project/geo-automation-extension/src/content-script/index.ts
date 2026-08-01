function inferPlatformFromLocation() {
  const host = window.location.hostname
  if (host === 'mp.toutiao.com') return 'toutiao'
  if (host === 'zhuanlan.zhihu.com' || host === 'www.zhihu.com') return 'zhihu'
  if (host === 'creator.xiaohongshu.com') return 'xiaohongshu'
  if (host === 'baijiahao.baidu.com') return 'baijiahao'
  if (host === 'creator.douyin.com') return 'douyin'
  return ''
}

function sendRuntimeStatus(stage: string, extra: Record<string, unknown> = {}) {
  chrome.runtime.sendMessage({
    type: 'GEO_AUTOMATION_RUNTIME_STATUS',
    payload: {
      runtimeStage: stage,
      detectedPlatform: inferPlatformFromLocation(),
      currentUrl: window.location.href,
      ...extra,
    },
  }).catch(() => null)
}

sendRuntimeStatus('extension_seen')

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type !== 'GEO_AUTOMATION_PING') return false
  sendResponse({
    ok: true,
    platform: inferPlatformFromLocation(),
    href: window.location.href,
  })
  return true
})
