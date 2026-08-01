export interface AutomationExtensionProfile {
  profileKey: string
  workerBase: string
  hostPermissions: string[]
}

const profiles: Record<string, AutomationExtensionProfile> = {
  development: {
    profileKey: 'development',
    workerBase: 'http://127.0.0.1:17991',
    hostPermissions: [
      'http://127.0.0.1/*',
      'http://localhost/*',
      'https://mp.toutiao.com/*',
      'https://zhuanlan.zhihu.com/*',
      'https://www.zhihu.com/*',
      'https://creator.xiaohongshu.com/*',
      'https://baijiahao.baidu.com/*',
      'https://creator.douyin.com/*',
    ],
  },
  production: {
    profileKey: 'production',
    workerBase: 'http://127.0.0.1:17991',
    hostPermissions: [
      'http://127.0.0.1/*',
      'http://localhost/*',
      'https://mp.toutiao.com/*',
      'https://zhuanlan.zhihu.com/*',
      'https://www.zhihu.com/*',
      'https://creator.xiaohongshu.com/*',
      'https://baijiahao.baidu.com/*',
      'https://creator.douyin.com/*',
    ],
  },
}

export function resolveAutomationExtensionProfile(mode: string): AutomationExtensionProfile {
  return profiles[mode] || profiles.development
}
