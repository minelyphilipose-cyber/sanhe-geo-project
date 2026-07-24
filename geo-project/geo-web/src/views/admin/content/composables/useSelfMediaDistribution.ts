import { computed, onBeforeUnmount, reactive, ref, type Ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type {
  ArticleDraft,
  BrandImageFolder,
  BrandMaterial,
  CompanyDistributionQuotaItem,
  DistributionTask,
  DouyinCapability,
  SelfMediaAccount,
  WechatMpCapability,
} from '@/types'
import {
  abandonSemiAutoDistribution,
  checkSelfMediaAccountAuth,
  confirmSemiAutoDistribution,
  dispatchSelfMediaPlatformQuickSchedule,
  distributeContentArticleToSelfMediaAccount,
  getArticleDistribution,
  getContentArticleDetail,
  getDouyinCapability,
  getSelfMediaAccountsByBrand,
  getWechatMpAuthUrl,
  getWechatMpCapability,
  refreshDistributionTaskReviewStatus,
} from '@/api/content'
import { getBrandImageFolders, getBrandMaterialPreviewUrl, getCompanyDistributionQuotas } from '@/api/customer'
import {
  getBrowserEnvironmentAccountBySelfMedia,
  resetBrowserEnvironmentAccountLoginIdentity,
  type BrowserEnvironmentAccount,
} from '@/api/browserEnvironment'
import {
  listLocalAgentSessions,
  type LocalAgentSession,
} from '@/api/localAgent'
import { getLocalHelperHealth, openLocalHelperEnvironment, type LocalHelperHealthResponse } from '@/api/localHelper'
import {
  canonicalSelfMediaPlatform,
  selfMediaPlatformLabel,
  selfMediaQuotaChannel,
} from '@/constants/selfMediaPlatforms'

type MediaPlatform = 'wechat_mp' | 'douyin' | 'baijiahao' | 'toutiao' | 'zhihu' | 'xiaohongshu' | 'netease' | 'sohu'
type SemiAutoPlatform = 'toutiao' | 'baijiahao' | 'zhihu' | 'xiaohongshu' | 'douyin'
type PlatformQuickSchedulePlatform = SemiAutoPlatform | 'wechat_mp'

type UseSelfMediaDistributionOptions = {
  rows: Ref<ArticleDraft[]>
  load: () => Promise<void>
}

type SetupPromptTarget = 'localAgent' | 'brandEnvironment' | 'selfMediaAccounts'

interface SetupPromptOptions {
  title: string
  issue: string
  location: string
  action: string
  target?: SetupPromptTarget
}

function localAgentSessionTimeValue(session: LocalAgentSession) {
  const value = session.lastSeenAt || session.boundAt || session.expiresAt || ''
  const timestamp = value ? new Date(value).getTime() : 0
  return Number.isFinite(timestamp) ? timestamp : 0
}

function createRequestId(prefix = 'self_media') {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${prefix}_${Date.now()}_${Math.random().toString(16).slice(2)}`
}

function formatDateTimeText(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return value.replace('T', ' ').slice(0, 16)
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function isImageFileType(fileType?: string | null) {
  return ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes((fileType || '').toLowerCase())
}

function isPublishedLockedArticle(status?: string | null) {
  return ['published', 'distributed'].includes(String(status || '').toLowerCase())
}

export function useSelfMediaDistribution(options: UseSelfMediaDistributionOptions) {
  const userStore = useUserStore()
  const router = useRouter()

  const mediaDistributeVisible = ref(false)
  const mediaDistributeArticleId = ref<number | null>(null)
  const mediaDistributeBrandId = ref<number | null>(null)
  const lastLocalHelperHealth = ref<LocalHelperHealthResponse | null>(null)
  const wechatCapability = ref<WechatMpCapability | null>(null)
  const douyinCapability = ref<DouyinCapability | null>(null)
  const wechatAccounts = ref<SelfMediaAccount[]>([])
  const douyinAccounts = ref<SelfMediaAccount[]>([])
  const toutiaoAccounts = ref<SelfMediaAccount[]>([])
  const baijiahaoAccounts = ref<SelfMediaAccount[]>([])
  const zhihuAccounts = ref<SelfMediaAccount[]>([])
  const xiaohongshuAccounts = ref<SelfMediaAccount[]>([])
  const selfMediaQuotaItems = ref<CompanyDistributionQuotaItem[]>([])
  const checkingSelfMediaAccountId = ref<number | null>(null)
  const brandImageFolders = ref<BrandImageFolder[]>([])
  const materialThumbUrls = ref<Record<number, string | null>>({})
  const imageFolderScope = ref<'project' | 'all'>('project')
  const selectedImageFolderId = ref<number | null>(null)
  const selectedMediaPlatform = ref<MediaPlatform>('wechat_mp')
  const selectedSelfMediaAccountId = ref<number | null>(null)
  const selectedCoverMaterialId = ref<number | null>(null)
  const selectedDouyinImageMaterialIds = ref<number[]>([])
  const douyinText = ref('')
  const distributionAttempts = ref<DistributionTask[]>([])
  const refreshingReviewTaskId = ref<number | null>(null)
  const semiAutoConfirmingTaskId = ref<number | null>(null)
  const semiAutoAbandoningTaskId = ref<number | null>(null)
  const semiAutoLoginOpeningAccountId = ref<number | null>(null)
  const selfMediaSubmitting = ref(false)
  const browserEnvironmentAccounts = ref<Record<number, BrowserEnvironmentAccount | null>>({})
  const localAgentSessions = ref<LocalAgentSession[]>([])
  const environmentAccountResettingId = ref<number | null>(null)
  const localHelperConfig = reactive({
    helperBase: 'http://127.0.0.1:17891',
    localAgentSessionId: null as number | null,
  })

  let browserEnvironmentStatusPollTimer: number | null = null
  let browserEnvironmentStatusPollingInFlight = false

  const activeLocalAgentSessions = computed(() =>
    localAgentSessions.value
      .filter((session) => session.status === 'active')
      .slice()
      .sort((left, right) => {
        const timeDelta = localAgentSessionTimeValue(right) - localAgentSessionTimeValue(left)
        if (timeDelta !== 0) return timeDelta
        return right.id - left.id
      }),
  )
  const wechatActive = computed(() => wechatAccounts.value.some((account) => account.status === 'active'))
  const wechatDistributionAvailable = computed(() =>
    !!wechatCapability.value?.draftDistributionEnabled || !!wechatCapability.value?.autoPublishEnabled,
  )
  const wechatQuickScheduleAvailable = computed(() => !!wechatCapability.value?.autoPublishEnabled)
  const wechatStatusLabel = computed(() => {
    if (!wechatDistributionAvailable.value) return '审核中'
    if (wechatActive.value) return '已登录'
    return '未登录'
  })
  const wechatStatusTagType = computed<'success' | 'warning' | 'info'>(() => {
    if (!wechatDistributionAvailable.value) return 'warning'
    return wechatActive.value ? 'success' : 'info'
  })
  const douyinActive = computed(() => douyinAccounts.value.some((account) => account.status === 'active'))
  const douyinLiveVerificationBlocked = computed(() => false)
  const douyinDistributionAvailable = computed(() => true)
  const douyinStatusLabel = computed(() => {
    if (!douyinAccounts.value.length) return '未配置'
    return douyinActive.value ? '可打开环境' : '不可用'
  })
  const douyinStatusTagType = computed<'success' | 'warning' | 'info'>(() => {
    if (!douyinAccounts.value.length) return 'info'
    return douyinActive.value ? 'success' : 'warning'
  })
  const douyinSubmitButtonText = computed(() =>
    '发布抖音图文',
  )
  const currentPlatformAccounts = computed(() => {
    switch (selectedMediaPlatform.value) {
      case 'douyin':
        return douyinAccounts.value
      case 'toutiao':
        return toutiaoAccounts.value
      case 'baijiahao':
        return baijiahaoAccounts.value
      case 'zhihu':
        return zhihuAccounts.value
      case 'xiaohongshu':
        return xiaohongshuAccounts.value
      default:
        return wechatAccounts.value
    }
  })
  const selectedSelfMediaQuotaItem = computed(() => {
    const channelCode = selfMediaQuotaChannel(selectedMediaPlatform.value)
    return selfMediaQuotaItems.value.find((item) => item.channelCode === channelCode) || null
  })
  const selectedSelfMediaQuotaHint = computed(() => {
    const label = selfMediaPlatformLabel(selectedMediaPlatform.value)
    const item = selectedSelfMediaQuotaItem.value
    if (!item) {
      return `${label} 未配置套餐额度，发布时将无法扣减平台额度`
    }
    if (!item.enabled) {
      return `套餐未开通 ${label} 额度`
    }
    const period = distributionQuotaPeriodLabel(item.periodType)
    const limitText = item.limitMismatch && item.usageQuotaLimit != null
      ? `${item.quotaLimit}（本周期 ${item.usageQuotaLimit}）`
      : String(item.quotaLimit)
    if (item.status === 'exceeded') {
      return `${label} 已超额 ${Math.max(item.usedCount - item.quotaLimit, 0)}，已用 ${item.usedCount}/${limitText}（${period}）`
    }
    return `${label} 剩余 ${item.remainingCount}，已用 ${item.usedCount}/${limitText}（${period}）`
  })
  const projectImageFolders = computed(() => brandImageFolders.value.filter((folder) => folder.projectRelated))
  const displayImageFolders = computed(() => {
    if (imageFolderScope.value === 'project' && projectImageFolders.value.length) {
      return projectImageFolders.value
    }
    return brandImageFolders.value
  })
  const selectedImageFolder = computed(() => displayImageFolders.value.find((folder) => folder.id === selectedImageFolderId.value) || null)
  const currentFolderMaterials = computed(() => selectedImageFolder.value?.materials || [])
  const imageMaterials = computed(() => currentFolderMaterials.value.filter((item) => {
    const type = (item.fileType || '').toLowerCase()
    return ['jpg', 'jpeg', 'png', 'gif', 'bmp'].includes(type)
  }))
  const douyinImageMaterials = computed(() => currentFolderMaterials.value.filter((item) => {
    const type = (item.fileType || '').toLowerCase()
    return ['jpg', 'jpeg', 'png'].includes(type)
  }))
  const selectedDouyinMaterials = computed(() => selectedDouyinImageMaterialIds.value
    .map((id) => douyinImageMaterials.value.find((item) => item.id === id))
    .filter((item): item is BrandMaterial => !!item))

  async function openMediaDistribute(row: ArticleDraft) {
    if (isPublishedLockedArticle(row.status)) {
      ElMessage.warning('文章已发布或已分发，不能再次发起自媒体分发')
      return
    }
    stopBrowserEnvironmentStatusPolling()
    mediaDistributeArticleId.value = row.id
    mediaDistributeBrandId.value = null
    selectedMediaPlatform.value = 'wechat_mp'
    wechatAccounts.value = []
    douyinAccounts.value = []
    toutiaoAccounts.value = []
    baijiahaoAccounts.value = []
    zhihuAccounts.value = []
    xiaohongshuAccounts.value = []
    selfMediaQuotaItems.value = []
    brandImageFolders.value = []
    imageFolderScope.value = 'project'
    selectedImageFolderId.value = null
    selectedSelfMediaAccountId.value = null
    selectedCoverMaterialId.value = null
    selectedDouyinImageMaterialIds.value = []
    douyinText.value = row.title || ''
    distributionAttempts.value = []
    browserEnvironmentAccounts.value = {}
    localAgentSessions.value = []
    try {
      const [detailRes, wechatCapabilityRes, douyinCapabilityRes, distributionRes] = await Promise.all([
        getContentArticleDetail(row.id),
        getWechatMpCapability(),
        getDouyinCapability(),
        getArticleDistribution(row.id, { targetKind: 'mp_account' }),
      ])
      const brandId = detailRes.data.data.project?.brandId
      if (!brandId) {
        ElMessage.error('当前文章未绑定品牌，无法分发到自媒体')
        return
      }
      if (isPublishedLockedArticle(detailRes.data.data.article?.status)) {
        ElMessage.warning('文章已发布或已分发，不能再次发起自媒体分发')
        return
      }
      mediaDistributeBrandId.value = brandId
      wechatCapability.value = wechatCapabilityRes.data.data
      douyinCapability.value = douyinCapabilityRes.data.data
      distributionAttempts.value = distributionRes.data.data.attempts || []
      const companyId = detailRes.data.data.project?.companyId
      const [accountRes, folderRes] = await Promise.all([
        getSelfMediaAccountsByBrand(brandId),
        getBrandImageFolders(brandId, {
          projectId: row.projectId,
          activeOnly: true,
          includeMaterials: true,
        }),
      ])
      if (companyId) {
        await loadSelfMediaDistributionQuotas(companyId)
      }
      applySelfMediaAccounts(accountRes.data.data || [])
      await Promise.all([
        loadBrowserEnvironmentAccountStatuses(accountRes.data.data || []),
        refreshLocalAgentSessions(),
      ])
      brandImageFolders.value = folderRes.data.data || []
      ensureSelectedImageFolder()
      await loadMaterialThumbs()
      mediaDistributeVisible.value = true
      startBrowserEnvironmentStatusPolling()
    } catch {
      ElMessage.error('加载自媒体账号失败')
    }
  }

  function applySelfMediaAccounts(accounts: SelfMediaAccount[]) {
    wechatAccounts.value = accounts.filter((account) => canonicalSelfMediaPlatform(account.platform) === 'wechat')
    douyinAccounts.value = accounts.filter((account) => canonicalSelfMediaPlatform(account.platform) === 'douyin')
    toutiaoAccounts.value = accounts.filter((account) => canonicalSelfMediaPlatform(account.platform) === 'toutiao')
    baijiahaoAccounts.value = accounts.filter((account) => canonicalSelfMediaPlatform(account.platform) === 'baijiahao')
    zhihuAccounts.value = accounts.filter((account) => canonicalSelfMediaPlatform(account.platform) === 'zhihu')
    xiaohongshuAccounts.value = accounts.filter((account) => canonicalSelfMediaPlatform(account.platform) === 'xiaohongshu')
  }

  async function loadSelfMediaDistributionQuotas(companyId: number) {
    try {
      const { data } = await getCompanyDistributionQuotas(companyId)
      selfMediaQuotaItems.value = (data.data.items || [])
        .filter((item) => item.channelCode?.startsWith('self_media:'))
    } catch {
      selfMediaQuotaItems.value = []
    }
  }

  async function loadBrowserEnvironmentAccountStatuses(accounts: SelfMediaAccount[]) {
    const semiAutoAccounts = accounts.filter((account) => isSemiAutoPlatform(account.platform as MediaPlatform))
    if (!semiAutoAccounts.length) {
      browserEnvironmentAccounts.value = {}
      return
    }
    const entries = await Promise.all(semiAutoAccounts.map(async (account) => {
      try {
        const { data } = await getBrowserEnvironmentAccountBySelfMedia(account.id)
        return [account.id, data.data || null] as const
      } catch {
        return [account.id, null] as const
      }
    }))
    browserEnvironmentAccounts.value = Object.fromEntries(entries)
  }

  async function refreshBrowserEnvironmentAccountStatuses() {
    await loadBrowserEnvironmentAccountStatuses([
      ...toutiaoAccounts.value,
      ...baijiahaoAccounts.value,
      ...zhihuAccounts.value,
      ...xiaohongshuAccounts.value,
    ])
  }

  async function pollBrowserEnvironmentAccountStatusesOnce() {
    if (!mediaDistributeVisible.value || browserEnvironmentStatusPollingInFlight) return
    browserEnvironmentStatusPollingInFlight = true
    try {
      await refreshBrowserEnvironmentAccountStatuses()
    } finally {
      browserEnvironmentStatusPollingInFlight = false
    }
  }

  function startBrowserEnvironmentStatusPolling() {
    stopBrowserEnvironmentStatusPolling()
    void pollBrowserEnvironmentAccountStatusesOnce()
    browserEnvironmentStatusPollTimer = window.setInterval(() => {
      void pollBrowserEnvironmentAccountStatusesOnce()
    }, 3000)
  }

  function stopBrowserEnvironmentStatusPolling() {
    if (browserEnvironmentStatusPollTimer !== null) {
      window.clearInterval(browserEnvironmentStatusPollTimer)
      browserEnvironmentStatusPollTimer = null
    }
    browserEnvironmentStatusPollingInFlight = false
  }

  async function refreshLocalAgentSessions() {
    try {
      const { data } = await listLocalAgentSessions()
      localAgentSessions.value = data.data || []
      const activeIds = new Set(activeLocalAgentSessions.value.map((session) => session.id))
      if (!localHelperConfig.localAgentSessionId || !activeIds.has(localHelperConfig.localAgentSessionId)) {
        localHelperConfig.localAgentSessionId = activeLocalAgentSessions.value[0]?.id || null
      }
    } catch {
      localAgentSessions.value = []
      localHelperConfig.localAgentSessionId = null
    }
  }

  async function refreshSelfMediaAccounts() {
    if (!mediaDistributeBrandId.value) return
    const { data } = await getSelfMediaAccountsByBrand(mediaDistributeBrandId.value)
    const accounts = data.data || []
    applySelfMediaAccounts(accounts)
    await loadBrowserEnvironmentAccountStatuses(accounts)
  }

  async function handleWechatPlatformClick() {
    if (selfMediaSubmitting.value) {
      ElMessage.info('已有分发任务正在处理，请稍候')
      return
    }
    selectedMediaPlatform.value = 'wechat_mp'
    selectedSelfMediaAccountId.value = null
    selectedCoverMaterialId.value = null
    selectedDouyinImageMaterialIds.value = []
    if (!wechatDistributionAvailable.value) {
      ElMessage.info(wechatCapability.value?.description || '微信公众号能力审核中，暂未开放授权')
      return
    }
    if (!wechatActive.value) {
      if (!mediaDistributeBrandId.value) {
        ElMessage.error('当前文章未绑定品牌，无法授权公众号')
        return
      }
      const { data } = await getWechatMpAuthUrl({
        brandId: mediaDistributeBrandId.value,
        redirectArticleId: mediaDistributeArticleId.value || undefined,
      })
      window.location.href = data.data.authUrl
      return
    }
    if (!wechatQuickScheduleAvailable.value) {
      ElMessage.info(wechatCapability.value?.description || '微信公众号自动发布未开启，暂不可创建快速排期')
      return
    }
    await submitPlatformQuickSchedule('wechat_mp')
  }

  async function handleDouyinPlatformClick() {
    await handleSemiAutoPlatformClick('douyin')
  }

  function isSemiAutoPlatform(platform: MediaPlatform): platform is SemiAutoPlatform {
    return platform === 'toutiao' || platform === 'baijiahao' || platform === 'zhihu' || platform === 'xiaohongshu' || platform === 'douyin'
  }

  function distributionQuotaPeriodLabel(value?: string | null) {
    const labels: Record<string, string> = {
      day: '日',
      week: '周',
      month: '月',
      total: '总量',
    }
    return value ? (labels[value] || value) : '-'
  }

  function semiAutoPlatformLabel(platform: string) {
    if (platform === 'wechat_mp' || platform === 'wechat') return '微信公众号'
    if (platform === 'douyin') return '抖音图文'
    if (platform === 'toutiao') return '头条'
    if (platform === 'baijiahao') return '百家号'
    if (platform === 'zhihu') return '知乎'
    return '小红书'
  }

  function semiAutoStatusLabel(accounts: SelfMediaAccount[]) {
    if (!accounts.length) return '未配置'
    if (!accounts.some((account) => account.status === 'active')) return '不可用'
    return '可打开环境'
  }

  function semiAutoStatusTagType(accounts: SelfMediaAccount[]): 'success' | 'warning' | 'info' {
    if (!accounts.length) return 'info'
    return accounts.some((account) => account.status === 'active') ? 'success' : 'warning'
  }

  function environmentAccountOf(account: SelfMediaAccount) {
    return browserEnvironmentAccounts.value[account.id] || null
  }

  function semiAutoCredentialLabel(account: SelfMediaAccount) {
    return account.status === 'active' ? '环境内校验' : '不可用'
  }

  function semiAutoCredentialTagType(account: SelfMediaAccount): 'success' | 'warning' | 'info' {
    return account.status === 'active' ? 'info' : 'warning'
  }

  function environmentAccountLabel(account: SelfMediaAccount) {
    const binding = environmentAccountOf(account)
    if (!binding) return '未配置环境'
    if (binding.loginStatus === 'logged_in') return '环境已就绪'
    if (binding.loginStatus === 'unknown') return '需登录'
    if (binding.loginStatus === 'mismatch') return '账号不一致'
    if (binding.loginStatus === 'expired') return '需重新登录'
    if (binding.loginStatus === 'login_required') return '需重新登录'
    if (binding.loginStatus === 'error') return '环境异常'
    return '环境状态未知'
  }

  function browserEnvironmentProviderProfileIdOf(binding: BrowserEnvironmentAccount | null | undefined) {
    return String(binding?.providerProfileId || '').trim()
  }

  function environmentAccountTagType(account: SelfMediaAccount): 'success' | 'warning' | 'danger' | 'info' {
    const binding = environmentAccountOf(account)
    if (!binding) return 'info'
    if (binding.loginStatus === 'logged_in') return 'success'
    if (binding.loginStatus === 'mismatch' || binding.loginStatus === 'error') return 'danger'
    return 'warning'
  }

  function canSubmitSemiAutoEnvironmentTask(account: SelfMediaAccount) {
    if (selfMediaSubmitting.value) return false
    const binding = environmentAccountOf(account)
    return account.status === 'active' && !!binding && binding.loginStatus === 'logged_in'
  }

  function setupPromptPath(target?: SetupPromptTarget) {
    if (target === 'localAgent') return userStore.isPartner ? '/partner/profile' : '/admin/profile'
    if (target === 'brandEnvironment' || target === 'selfMediaAccounts') {
      return mediaDistributeBrandId.value ? `/admin/brands/${mediaDistributeBrandId.value}` : '/admin/brands'
    }
    return ''
  }

  async function showSetupPrompt(options: SetupPromptOptions) {
    const targetPath = setupPromptPath(options.target)
    const message = [
      options.issue,
      '',
      `设置位置：${options.location}`,
      `处理方式：${options.action}`,
    ].join('\n')

    try {
      await ElMessageBox.confirm(message, options.title, {
        confirmButtonText: targetPath ? '前往设置' : '我知道了',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }

    if (targetPath) {
      await router.push(targetPath)
    }
  }

  function showLocalAgentSetupPrompt(issue = '当前电脑尚未完成本地助手配对，系统无法打开 AdsPower 浏览器环境。') {
    const isAdspowerConfigIssue = /AdsPower|API Key/i.test(issue)
    return showSetupPrompt({
      title: isAdspowerConfigIssue ? 'AdsPower 连接未配置' : '本地助手未就绪',
      issue,
      location: '右上角头像 > 个人中心 > 本地助手',
      action: isAdspowerConfigIssue
        ? '先启动本地助手，在个人中心填写 AdsPower API 地址和 API Key，保存后回到当前分发页面继续操作。'
        : '先启动本地助手，在助手页面生成一次性配对码，再回到个人中心完成绑定。绑定成功后回到当前分发页面继续操作。',
      target: 'localAgent',
    })
  }

  function showBrandEnvironmentSetupPrompt(issue: string) {
    return showSetupPrompt({
      title: 'AdsPower 浏览器环境未配置',
      issue,
      location: '品牌详情 > 自媒体账号 > 指纹浏览器环境',
      action: '为该品牌配置 AdsPower 浏览器环境，并确认该平台账号已绑定到品牌环境。',
      target: 'brandEnvironment',
    })
  }

  function showSelfMediaAccountSetupPrompt(platform: PlatformQuickSchedulePlatform, issue: string) {
    const action = isSemiAutoPlatform(platform)
      ? `新增或启用${semiAutoPlatformLabel(platform)}账号后，确认该账号已绑定品牌的 AdsPower 浏览器环境。`
      : `新增或启用${semiAutoPlatformLabel(platform)}账号，并完成该平台官方授权后再创建排期。`
    return showSetupPrompt({
      title: `${semiAutoPlatformLabel(platform)}账号未就绪`,
      issue,
      location: '品牌详情 > 自媒体账号',
      action,
      target: 'selfMediaAccounts',
    })
  }

  function isLocalAgentSetupError(error: unknown) {
    if (!(error instanceof Error)) return false
    return /本地助手|local agent|helper/i.test(error.message)
  }

  async function openSemiAutoEnvironmentForLogin(account: SelfMediaAccount) {
    const binding = environmentAccountOf(account)
    if (!binding) {
      await showBrandEnvironmentSetupPrompt('当前自媒体账号未绑定 AdsPower 浏览器环境，无法打开对应环境进行登录。')
      return
    }
    const environmentKey = binding.environmentKey || ''
    if (!environmentKey) {
      await showBrandEnvironmentSetupPrompt('当前账号的浏览器环境配置不完整，请到品牌详情重新保存或重新绑定。')
      return
    }
    const providerProfileId = browserEnvironmentProviderProfileIdOf(binding)
    if (!providerProfileId) {
      await showBrandEnvironmentSetupPrompt('当前账号的 AdsPower 浏览器编号缺失，请到品牌详情补全环境配置。')
      return
    }
    semiAutoLoginOpeningAccountId.value = account.id
    try {
      await openLocalHelperEnvironment(
        await currentLocalHelperAuthConfig(),
        {
          environmentKey,
          providerProfileId,
          environmentName: binding.environmentKey || account.accountName || null,
          url: defaultSemiAutoLoginReportUrl(account.platform),
        },
      )
      ElMessage.success('已打开对应 AdsPower 浏览器环境。登录完成后，环境内扩展会自动上报登录状态')
    } catch (error) {
      if (isLocalAgentSetupError(error)) {
        await showLocalAgentSetupPrompt(error instanceof Error ? error.message : undefined)
      } else {
        ElMessage.error(error instanceof Error ? error.message : '打开指纹浏览器环境失败')
      }
    } finally {
      semiAutoLoginOpeningAccountId.value = null
    }
  }

  async function resetEnvironmentAccountIdentity(account: SelfMediaAccount) {
    const binding = environmentAccountOf(account)
    if (!binding) {
      ElMessage.warning('当前账号未绑定指纹浏览器环境')
      return
    }
    try {
      await ElMessageBox.confirm(
        `确认重置账号「${account.accountName}」的环境账号校验？这会清除当前已登记的平台身份，状态回到待首次登录。重置后请重新打开环境登录，扩展会自动上报登录状态。`,
        '重置账号校验',
        {
          confirmButtonText: '确认重置',
          cancelButtonText: '取消',
          type: 'warning',
        },
      )
    } catch {
      return
    }

    environmentAccountResettingId.value = account.id
    try {
      const { data } = await resetBrowserEnvironmentAccountLoginIdentity(binding.id)
      browserEnvironmentAccounts.value = {
        ...browserEnvironmentAccounts.value,
        [account.id]: data.data,
      }
      ElMessage.success('已重置账号校验，请重新打开环境登录，扩展会自动上报登录状态')
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '重置账号校验失败')
    } finally {
      environmentAccountResettingId.value = null
    }
  }

  async function handleSemiAutoPlatformClick(platform: SemiAutoPlatform) {
    if (selfMediaSubmitting.value) {
      ElMessage.info('已有分发任务正在处理，请稍候')
      return
    }
    selectedMediaPlatform.value = platform
    selectedSelfMediaAccountId.value = null
    selectedCoverMaterialId.value = null
    selectedDouyinImageMaterialIds.value = []
    await submitPlatformQuickSchedule(platform)
  }

  async function submitPlatformQuickSchedule(platform: PlatformQuickSchedulePlatform) {
    const articleId = mediaDistributeArticleId.value
    if (!articleId) {
      ElMessage.warning('请选择要分发的文章')
      return
    }
    if (!mediaDistributeBrandId.value) {
      await showSetupPrompt({
        title: '文章品牌缺失',
        issue: '当前文章未绑定品牌，系统无法判断要使用哪个品牌的自媒体账号。',
        location: '内容管理 > 文章详情',
        action: '先为文章选择所属品牌，再回到自媒体分发继续操作。',
      })
      return
    }
    const accounts = quickScheduleAccountsByPlatform(platform)
    if (!accounts.length) {
      await showSelfMediaAccountSetupPrompt(platform, `当前品牌暂无${semiAutoPlatformLabel(platform)}账号，无法创建分发任务。`)
      return
    }
    selfMediaSubmitting.value = true
    try {
      const dispatch = async (replaceNextScheduled = false) => (await dispatchSelfMediaPlatformQuickSchedule({
        articleId,
        platform,
        replaceNextScheduled,
      })).data.data
      let created = await dispatch()
      if (created.action === 'replace_required') {
        await ElMessageBox.confirm(
          created.message || `继续分发将替换已有${semiAutoPlatformLabel(platform)}排期，是否继续？`,
          `${semiAutoPlatformLabel(platform)}排期替换确认`,
          {
            confirmButtonText: '确认替换并分发',
            cancelButtonText: '取消',
            type: 'warning',
          },
        )
        created = await dispatch(true)
      }
      if (created.action !== 'created') {
        ElMessage.warning(created.message || `${semiAutoPlatformLabel(platform)}排期未创建`)
        return
      }
      const schedule = created.createResponse?.createdSchedules?.[0]
      const publishAt = schedule?.plannedPublishAt || created.plannedPublishAt
      const attemptAt = schedule?.nextAttemptAt || created.nextAttemptAt
      ElMessage.success(`${semiAutoPlatformLabel(platform)}排期已创建，预计发布时间 ${formatDateTimeText(publishAt)}，系统处理时间 ${formatDateTimeText(attemptAt)}`)
      mediaDistributeVisible.value = false
      await refreshDistributionHistory()
      await options.load()
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error(error instanceof Error ? error.message : `${semiAutoPlatformLabel(platform)}排期创建失败`)
      }
    } finally {
      selfMediaSubmitting.value = false
    }
  }

  function quickScheduleAccountsByPlatform(platform: PlatformQuickSchedulePlatform) {
    if (platform === 'wechat_mp') return wechatAccounts.value
    if (platform === 'douyin') return douyinAccounts.value
    if (platform === 'toutiao') return toutiaoAccounts.value
    if (platform === 'baijiahao') return baijiahaoAccounts.value
    if (platform === 'zhihu') return zhihuAccounts.value
    return xiaohongshuAccounts.value
  }

  function handleFolderScopeChange() {
    ensureSelectedImageFolder()
    selectedCoverMaterialId.value = selectedMediaPlatform.value === 'wechat_mp' ? imageMaterials.value[0]?.id || null : null
    selectedDouyinImageMaterialIds.value = selectedDouyinImageMaterialIds.value.filter((id) => douyinImageMaterials.value.some((item) => item.id === id))
  }

  function selectImageFolder(folderId: number) {
    selectedImageFolderId.value = folderId
    selectedCoverMaterialId.value = selectedMediaPlatform.value === 'wechat_mp' ? imageMaterials.value[0]?.id || null : null
    selectedDouyinImageMaterialIds.value = selectedDouyinImageMaterialIds.value.filter((id) => douyinImageMaterials.value.some((item) => item.id === id))
  }

  function ensureSelectedImageFolder() {
    const folders = displayImageFolders.value
    if (!folders.length) {
      selectedImageFolderId.value = null
      return
    }
    if (!folders.some((folder) => folder.id === selectedImageFolderId.value)) {
      selectedImageFolderId.value = folders[0].id
    }
  }

  function materialThumbUrl(material: BrandMaterial) {
    return materialThumbUrls.value[material.id] || material.publicUrl || ''
  }

  async function loadMaterialThumbs() {
    const brandId = mediaDistributeBrandId.value
    if (!brandId) {
      cleanupMaterialThumbs()
      return
    }
    cleanupMaterialThumbs()
    const seen = new Set<number>()
    const targets = brandImageFolders.value
      .flatMap((folder) => folder.materials || [])
      .filter((material) => {
        if (!isImageFileType(material.fileType) || seen.has(material.id)) return false
        seen.add(material.id)
        return true
      })
    const concurrency = Math.min(6, targets.length)
    let cursor = 0

    const worker = async () => {
      while (cursor < targets.length) {
        const material = targets[cursor++]
        try {
          const { data } = await getBrandMaterialPreviewUrl(brandId, material.id)
          const url = data.data.url
          materialThumbUrls.value = { ...materialThumbUrls.value, [material.id]: url }
        } catch {
          materialThumbUrls.value = { ...materialThumbUrls.value, [material.id]: null }
        }
      }
    }

    await Promise.all(Array.from({ length: concurrency }, () => worker()))
  }

  function cleanupMaterialThumbs() {
    materialThumbUrls.value = {}
  }

  function toggleDouyinImage(materialId: number) {
    const index = selectedDouyinImageMaterialIds.value.indexOf(materialId)
    if (index >= 0) {
      selectedDouyinImageMaterialIds.value.splice(index, 1)
      return
    }
    if (selectedDouyinImageMaterialIds.value.length >= 30) {
      ElMessage.warning('抖音图文最多选择 30 张图片')
      return
    }
    selectedDouyinImageMaterialIds.value.push(materialId)
  }

  function moveDouyinImage(index: number, offset: number) {
    const nextIndex = index + offset
    if (nextIndex < 0 || nextIndex >= selectedDouyinImageMaterialIds.value.length) {
      return
    }
    const next = [...selectedDouyinImageMaterialIds.value]
    const [item] = next.splice(index, 1)
    next.splice(nextIndex, 0, item)
    selectedDouyinImageMaterialIds.value = next
  }

  async function submitDouyinImageText() {
    if (!mediaDistributeArticleId.value || !selectedSelfMediaAccountId.value) {
      ElMessage.warning('请选择抖音账号')
      return
    }
    if (!selectedDouyinImageMaterialIds.value.length) {
      ElMessage.warning('请选择至少 1 张抖音图文图片')
      return
    }
    if (douyinText.value.length > 1000) {
      ElMessage.warning('抖音文案不能超过 1000 字')
      return
    }
    selfMediaSubmitting.value = true
    try {
      const result = await distributeContentArticleToSelfMediaAccount(mediaDistributeArticleId.value, {
        selfMediaAccountId: selectedSelfMediaAccountId.value,
        imageMaterialIds: selectedDouyinImageMaterialIds.value,
        platformOptions: {
          text: douyinText.value.trim() || undefined,
        },
        requestId: createRequestId('douyin'),
      })
      const task = result.data.data
      if (task.status === 'submitted') {
        ElMessage.success('抖音图文提交成功')
        await refreshDistributionHistory()
        await options.load()
        return
      }
      ElMessage.error(task.errorMessage || '抖音图文提交失败')
    } finally {
      selfMediaSubmitting.value = false
    }
  }

  function defaultSemiAutoPublishUrl(platform: string) {
    if (platform === 'toutiao') return 'https://mp.toutiao.com/profile_v4/graphic/publish'
    if (platform === 'baijiahao') return 'https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1'
    if (platform === 'zhihu') return 'https://zhuanlan.zhihu.com/write'
    if (platform === 'xiaohongshu') return 'https://creator.xiaohongshu.com/publish/publish?from=tab_switch&target=article'
    if (platform === 'douyin') return 'https://creator.douyin.com/creator-micro/content/post/article?media_type=article&type=new&enter_from=publish_page'
    return undefined
  }

  function defaultSemiAutoLoginReportUrl(platform: string) {
    if (platform === 'toutiao') return 'https://mp.toutiao.com/profile_v4'
    if (platform === 'baijiahao') return 'https://baijiahao.baidu.com/'
    if (platform === 'zhihu') return 'https://www.zhihu.com/'
    if (platform === 'xiaohongshu') return 'https://creator.xiaohongshu.com/'
    if (platform === 'douyin') return 'https://creator.douyin.com/creator-micro/content/manage'
    return defaultSemiAutoPublishUrl(platform)
  }

  async function syncLocalAgentSessionFromHelper() {
    const helperBase = localHelperConfig.helperBase.trim()
    if (!helperBase) return
    const health = await getLocalHelperHealth(helperBase)
    lastLocalHelperHealth.value = health
    const helperSessionId = Number(health.session?.sessionId)
    if (!health.paired || !Number.isFinite(helperSessionId) || helperSessionId <= 0) return

    if (localHelperConfig.localAgentSessionId !== helperSessionId) {
      localHelperConfig.localAgentSessionId = helperSessionId
    }

    const knownActiveIds = new Set(activeLocalAgentSessions.value.map((session) => session.id))
    if (!knownActiveIds.has(helperSessionId)) {
      await refreshLocalAgentSessions()
      if (localHelperConfig.localAgentSessionId !== helperSessionId) {
        localHelperConfig.localAgentSessionId = helperSessionId
      }
    }
    return health
  }

  async function currentLocalHelperAuthConfig() {
    const health = await syncLocalAgentSessionFromHelper()
    const helperBase = localHelperConfig.helperBase.trim()
    const localAgentSessionId = localHelperConfig.localAgentSessionId || activeLocalAgentSessions.value[0]?.id || null
    if (!helperBase) {
      throw new Error('请先到「个人中心 > 本地助手」完成本机配对')
    }
    if (!localAgentSessionId) {
      throw new Error('请先完成本地助手配对')
    }
    if (health && !health.adspower?.apiKeyConfigured) {
      throw new Error('请先到「个人中心 > 本地助手」配置 AdsPower API Key')
    }
    return {
      helperBase,
      localAgentSessionId,
    }
  }

  async function refreshDistributionHistory() {
    if (!mediaDistributeArticleId.value) return
    const { data } = await getArticleDistribution(mediaDistributeArticleId.value, { targetKind: 'mp_account' })
    distributionAttempts.value = data.data.attempts || []
  }

  async function refreshReviewStatus(task: DistributionTask) {
    refreshingReviewTaskId.value = task.id
    try {
      await refreshDistributionTaskReviewStatus(task.id)
      await refreshDistributionHistory()
      ElMessage.success('审核状态已刷新')
    } finally {
      refreshingReviewTaskId.value = null
    }
  }

  function canRefreshReviewStatus(task: DistributionTask) {
    return task.targetKind === 'mp_account'
      && (task.reviewStatus === 'under_review' || task.reviewStatus === 'unknown')
  }

  function canOperateSemiAutoDistributionTask(task: DistributionTask) {
    return task.targetKind === 'mp_account'
      && task.dispatchMode === 'SEMI_AUTO'
      && ['token_issued', 'filling', 'filled'].includes(task.status)
  }

  async function confirmSemiAutoPublished(task: DistributionTask) {
    try {
      const { value } = await ElMessageBox.prompt(
        '请填写平台发布后的文章链接；若暂时没有链接，可填写确认备注，系统会记录为“链接待补充”。',
        '确认半自动发布',
        {
          confirmButtonText: '确认发布',
          cancelButtonText: '取消',
          inputPlaceholder: '发布链接或确认备注',
          inputValidator: (input: string) => Boolean(input?.trim()) || '请填写发布链接或确认备注',
        },
      )
      const input = value.trim()
      const isUrl = /^https?:\/\/.+/i.test(input)
      semiAutoConfirmingTaskId.value = task.id
      await confirmSemiAutoDistribution(task.id, {
        publishedUrl: isUrl ? input : null,
        responsePayload: JSON.stringify({
          source: 'admin_console',
          confirmedAt: new Date().toISOString(),
          confirmMode: isUrl ? 'published_url' : 'operator_note',
          operatorNote: isUrl ? null : input,
        }),
      })
      await refreshDistributionHistory()
      await options.load()
      ElMessage.success('已确认半自动发布')
    } catch (error) {
      if (error instanceof Error) {
        ElMessage.error(error.message || '确认发布失败')
      }
    } finally {
      semiAutoConfirmingTaskId.value = null
    }
  }

  async function abandonSemiAutoPublished(task: DistributionTask) {
    try {
      const { value } = await ElMessageBox.prompt(
        '确认放弃本次半自动分发？系统会将该任务标记为失败并退回本次分发占用，文章可重新发起分发。',
        '放弃半自动发布',
        {
          confirmButtonText: '确认放弃',
          cancelButtonText: '取消',
          inputPlaceholder: '请填写放弃原因',
          inputValidator: (input: string) => Boolean(input?.trim()) || '请填写放弃原因',
          type: 'warning',
        },
      )
      semiAutoAbandoningTaskId.value = task.id
      await abandonSemiAutoDistribution(task.id, {
        reason: value.trim(),
      })
      await refreshDistributionHistory()
      await options.load()
      ElMessage.success('已放弃本次半自动分发')
    } catch (error) {
      if (error instanceof Error) {
        ElMessage.error(error.message || '放弃发布失败')
      }
    } finally {
      semiAutoAbandoningTaskId.value = null
    }
  }

  async function checkWechatAccount(id: number) {
    checkingSelfMediaAccountId.value = id
    try {
      const { data } = await checkSelfMediaAccountAuth(id)
      const next = data.data
      wechatAccounts.value = wechatAccounts.value.map((account) => account.id === id ? next : account)
      ElMessage.success(next.status === 'active' ? '登录状态有效' : '登录状态已更新')
    } finally {
      checkingSelfMediaAccountId.value = null
    }
  }

  function selfMediaAccountStatusLabel(account: SelfMediaAccount) {
    if (isSemiAutoPlatform(account.platform as MediaPlatform)) {
      return account.status === 'active' ? '启用' : '停用'
    }
    const map: Record<string, string> = {
      active: '已登录',
      expired: '已过期',
      revoked: '已取消',
      disabled: '不可用',
    }
    return map[account.status] || account.status
  }

  function selfMediaAccountStatusTag(account: SelfMediaAccount): 'success' | 'warning' | 'danger' | 'info' {
    if (isSemiAutoPlatform(account.platform as MediaPlatform)) {
      return account.status === 'active' ? 'info' : 'danger'
    }
    if (account.status === 'active') return 'success'
    if (account.status === 'expired') return 'warning'
    if (account.status === 'revoked' || account.status === 'disabled') return 'danger'
    return 'info'
  }

  function distributionPlatformLabel(v?: string | null) {
    const map: Record<string, string> = {
      wechat_mp: '微信公众号',
      douyin: '抖音图文',
      toutiao: '今日头条',
      baijiahao: '百家号',
      zhihu: '知乎',
      xiaohongshu: '小红书',
    }
    if (!v) return '自媒体平台'
    return map[v] || v
  }

  function distributionPlatformInitial(v?: string | null) {
    const label = distributionPlatformLabel(v)
    return label.slice(0, 1)
  }

  function distributionStatusLabel(v?: string | null) {
    const map: Record<string, string> = {
      pending: '待处理',
      submitted: '已提交',
      token_issued: '待扩展处理',
      filling: '填充中',
      filled: '已填充',
      published: '已发布',
      failed: '失败',
      review_rejected: '审核拒绝',
    }
    return v ? map[v] || v : '-'
  }

  function distributionTaskStatusLabel(task: DistributionTask) {
    return distributionStatusLabel(task.status)
  }

  function distributionStatusTag(v?: string | null): 'success' | 'warning' | 'danger' | 'info' {
    if (v === 'submitted' || v === 'published') return 'success'
    if (v === 'failed' || v === 'review_rejected') return 'danger'
    if (v === 'pending' || v === 'token_issued' || v === 'filling' || v === 'filled') return 'warning'
    return 'info'
  }

  function reviewStatusLabel(status?: string | null) {
    const map: Record<string, string> = {
      under_review: '审核中',
      published: '平台审核通过',
      rejected: '已拒审',
      offline: '已下线',
      unknown: '未知',
    }
    return status ? map[status] || status : '-'
  }

  function reviewStatusTag(status?: string | null): 'success' | 'warning' | 'danger' | 'info' {
    if (status === 'published') return 'success'
    if (status === 'rejected' || status === 'offline') return 'danger'
    if (status === 'under_review') return 'warning'
    return 'info'
  }

  watch(mediaDistributeVisible, (visible) => {
    if (!visible) {
      stopBrowserEnvironmentStatusPolling()
      cleanupMaterialThumbs()
    }
  })

  onBeforeUnmount(() => {
    stopBrowserEnvironmentStatusPolling()
    cleanupMaterialThumbs()
  })

  const selfMediaDistributeActions = {
    semiAutoStatusTagType,
    semiAutoStatusLabel,
    handleWechatPlatformClick,
    handleDouyinPlatformClick,
    handleSemiAutoPlatformClick,
    selfMediaAccountStatusTag,
    selfMediaAccountStatusLabel,
    isSemiAutoPlatform,
    semiAutoCredentialTagType,
    semiAutoCredentialLabel,
    environmentAccountTagType,
    environmentAccountLabel,
    checkWechatAccount,
    environmentAccountOf,
    canSubmitSemiAutoEnvironmentTask,
    openSemiAutoEnvironmentForLogin,
    resetEnvironmentAccountIdentity,
    semiAutoPlatformLabel,
    handleFolderScopeChange,
    selectImageFolder,
    materialThumbUrl,
    toggleDouyinImage,
    moveDouyinImage,
    distributionPlatformInitial,
    distributionPlatformLabel,
    distributionStatusTag,
    distributionTaskStatusLabel,
    reviewStatusTag,
    reviewStatusLabel,
    canRefreshReviewStatus,
    refreshReviewStatus,
    canOperateSemiAutoDistributionTask,
    confirmSemiAutoPublished,
    abandonSemiAutoPublished,
    submitDouyinImageText,
  }

  return {
    mediaDistributeVisible,
    mediaDistributeArticleId,
    mediaDistributeBrandId,
    lastLocalHelperHealth,
    wechatCapability,
    douyinCapability,
    toutiaoAccounts,
    baijiahaoAccounts,
    zhihuAccounts,
    xiaohongshuAccounts,
    checkingSelfMediaAccountId,
    imageFolderScope,
    selectedImageFolderId,
    selectedMediaPlatform,
    selectedSelfMediaAccountId,
    selectedCoverMaterialId,
    selectedDouyinImageMaterialIds,
    douyinText,
    distributionAttempts,
    refreshingReviewTaskId,
    semiAutoConfirmingTaskId,
    semiAutoAbandoningTaskId,
    semiAutoLoginOpeningAccountId,
    environmentAccountResettingId,
    selfMediaSubmitting,
    wechatDistributionAvailable,
    wechatQuickScheduleAvailable,
    wechatStatusLabel,
    wechatStatusTagType,
    douyinDistributionAvailable,
    douyinStatusLabel,
    douyinStatusTagType,
    douyinSubmitButtonText,
    currentPlatformAccounts,
    selectedSelfMediaQuotaItem,
    selectedSelfMediaQuotaHint,
    displayImageFolders,
    imageMaterials,
    douyinImageMaterials,
    selectedDouyinMaterials,
    selfMediaDistributeActions,
    openMediaDistribute,
  }
}
