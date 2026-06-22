import { computed, reactive, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ArticleDraft, AuthorityMediaResource, PublishSite } from '@/types'
import {
  distributeContentArticleToAgentSite,
  distributeContentArticleToAuthorityMedia,
  distributeContentArticleToForumSite,
  distributeContentArticleToIndustrySite,
  getAuthorityMediaResources,
  getContentArticleDetail,
} from '@/api/content'
import { getBrandDetail } from '@/api/customer'
import { getPublishSites } from '@/api/publishSite'

export type DistributionChannel = 'official_site' | 'industry_site' | 'forum' | 'self_media' | 'authority_media'

type ForumBoardOption = {
  fid: number
  name: string
  enabled: boolean
  default: boolean
}

type UseArticleDistributionChannelsOptions = {
  load: () => Promise<void>
  submitting: Ref<boolean>
  openSelfMediaDistribute: (row: ArticleDraft) => Promise<void>
}

export function useArticleDistributionChannels(options: UseArticleDistributionChannelsOptions) {
  const distributionChannelVisible = ref(false)
  const distributionChannelArticle = ref<ArticleDraft | null>(null)
  const distributionChannels: Array<{
    value: DistributionChannel
    label: string
    description: string
    disabled?: boolean
  }> = [
    { value: 'official_site', label: 'Agent 官网', description: '发布到项目品牌的 Agent 官网站点。' },
    { value: 'industry_site', label: '行业资讯站', description: '选择已启用的行业资讯站并发布。' },
    { value: 'forum', label: '平台网站', description: '选择已启用的平台网站并发布讨论帖。' },
    { value: 'self_media', label: '自媒体平台', description: '分发到微信公众号、抖音、头条、知乎等账号。' },
    { value: 'authority_media', label: '权威媒体', description: '选择特价网新闻媒体资源并创建出稿订单。' },
  ]

  const industrySiteVisible = ref(false)
  const industrySiteLoading = ref(false)
  const industrySiteSubmitting = ref(false)
  const industrySiteArticle = ref<ArticleDraft | null>(null)
  const industrySites = ref<PublishSite[]>([])
  const selectedIndustrySiteId = ref<number | null>(null)

  const forumSiteVisible = ref(false)
  const forumSiteLoading = ref(false)
  const forumSiteSubmitting = ref(false)
  const forumSiteArticle = ref<ArticleDraft | null>(null)
  const forumSites = ref<PublishSite[]>([])
  const selectedForumSiteId = ref<number | null>(null)
  const selectedForumFid = ref<number | null>(null)
  const selectedForumSite = computed(() => forumSites.value.find((site) => site.id === selectedForumSiteId.value) || null)
  const selectedForumBoards = computed(() => enabledForumBoards(selectedForumSite.value))
  const canSubmitForumSite = computed(() => Boolean(selectedForumSiteId.value))

  const authorityMediaVisible = ref(false)
  const authorityLoading = ref(false)
  const authoritySubmitting = ref(false)
  const authorityResources = ref<AuthorityMediaResource[]>([])
  const selectedAuthorityResource = ref<AuthorityMediaResource | null>(null)
  const authorityPage = reactive({ current: 1, size: 10, total: 0 })
  const authorityQuery = reactive<{
    keyword: string
    industry: string
    province: string
    entranceLevel?: number
    newsResource?: number
    includeCondition?: number
  }>({
    keyword: '',
    industry: '',
    province: '',
    entranceLevel: undefined,
    newsResource: undefined,
    includeCondition: undefined,
  })
  const authorityForm = reactive({
    articleId: 0,
    resourceId: 0,
    salingPrice: 0,
    publishedAt: '',
    remark: '',
  })

  function openDistributionChannel(row: ArticleDraft) {
    distributionChannelArticle.value = row
    distributionChannelVisible.value = true
  }

  async function selectDistributionChannel(channel: DistributionChannel) {
    const row = distributionChannelArticle.value
    if (!row) return
    if (channel === 'industry_site') {
      distributionChannelVisible.value = false
      await openIndustrySiteDistribute(row)
      return
    }
    if (channel === 'forum') {
      distributionChannelVisible.value = false
      await openForumSiteDistribute(row)
      return
    }
    distributionChannelVisible.value = false
    if (channel === 'official_site') {
      await distributeToAgentSite(row)
      return
    }
    if (channel === 'self_media') {
      await options.openSelfMediaDistribute(row)
      return
    }
    await openAuthorityMedia(row)
  }

  async function openIndustrySiteDistribute(row: ArticleDraft) {
    industrySiteArticle.value = row
    selectedIndustrySiteId.value = null
    industrySites.value = []
    industrySiteVisible.value = true
    industrySiteLoading.value = true
    try {
      const { data } = await getPublishSites({ status: 'active' })
      industrySites.value = (data.data || []).filter(isIndustryPublishSite)
    } finally {
      industrySiteLoading.value = false
    }
  }

  async function openForumSiteDistribute(row: ArticleDraft) {
    forumSiteArticle.value = row
    selectedForumSiteId.value = null
    selectedForumFid.value = null
    forumSites.value = []
    forumSiteVisible.value = true
    forumSiteLoading.value = true
    try {
      const { data } = await getPublishSites({ status: 'active' })
      forumSites.value = (data.data || []).filter(isForumPublishSite)
    } finally {
      forumSiteLoading.value = false
    }
  }

  function selectIndustrySite(row?: PublishSite) {
    selectedIndustrySiteId.value = row?.id || null
  }

  function selectForumSite(row?: PublishSite) {
    selectedForumSiteId.value = row?.id || null
    selectedForumFid.value = null
  }

  async function submitIndustrySite() {
    const row = industrySiteArticle.value
    if (!row || !selectedIndustrySiteId.value) return
    industrySiteSubmitting.value = true
    try {
      const result = await distributeContentArticleToIndustrySite(row.id, selectedIndustrySiteId.value)
      const task = result.data.data
      if (isDistributionSuccessStatus(task.status)) {
        ElMessage.success(task.publishedUrl ? `行业资讯站分发成功：${task.publishedUrl}` : '行业资讯站分发成功')
        industrySiteVisible.value = false
        await options.load()
        return
      }
      if (isDistributionActiveStatus(task.status)) {
        ElMessage.info('行业资讯站已有发布任务正在处理中')
        industrySiteVisible.value = false
        await options.load()
        return
      }
      ElMessage.error(task.errorMessage || '行业资讯站分发失败')
    } finally {
      industrySiteSubmitting.value = false
    }
  }

  async function submitForumSite() {
    const row = forumSiteArticle.value
    if (!row || !selectedForumSiteId.value) return
    forumSiteSubmitting.value = true
    try {
      const result = await distributeContentArticleToForumSite(row.id, selectedForumSiteId.value, selectedForumFid.value)
      const task = result.data.data
      if (task.status === 'submitted') {
        ElMessage.success('平台网站分发成功')
        forumSiteVisible.value = false
        await options.load()
        return
      }
      ElMessage.error(task.errorMessage || '平台网站分发失败')
    } finally {
      forumSiteSubmitting.value = false
    }
  }

  async function openAuthorityMedia(row: ArticleDraft) {
    authorityForm.articleId = row.id
    authorityForm.resourceId = 0
    authorityForm.salingPrice = 0
    authorityForm.publishedAt = ''
    authorityForm.remark = ''
    selectedAuthorityResource.value = null
    authorityMediaVisible.value = true
    authorityPage.current = 1
    await loadAuthorityMediaResources()
  }

  async function loadAuthorityMediaResources() {
    authorityLoading.value = true
    try {
      const { data } = await getAuthorityMediaResources({
        current: authorityPage.current,
        size: authorityPage.size,
        keyword: authorityQuery.keyword.trim() || undefined,
        industry: authorityQuery.industry.trim() || undefined,
        province: authorityQuery.province.trim() || undefined,
        entranceLevel: authorityQuery.entranceLevel,
        newsResource: authorityQuery.newsResource,
        includeCondition: authorityQuery.includeCondition,
      })
      authorityResources.value = data.data.records || []
      authorityPage.total = data.data.total || 0
    } catch {
      authorityResources.value = []
      authorityPage.total = 0
      ElMessage.error('加载权威媒体资源失败')
    } finally {
      authorityLoading.value = false
    }
  }

  function searchAuthorityMedia() {
    authorityPage.current = 1
    void loadAuthorityMediaResources()
  }

  function resetAuthorityMediaQuery() {
    authorityQuery.keyword = ''
    authorityQuery.industry = ''
    authorityQuery.province = ''
    authorityQuery.entranceLevel = undefined
    authorityQuery.newsResource = undefined
    authorityQuery.includeCondition = undefined
    searchAuthorityMedia()
  }

  function onAuthorityPageChange(v: number) {
    authorityPage.current = v
    void loadAuthorityMediaResources()
  }

  function selectAuthorityResource(row?: AuthorityMediaResource) {
    if (!row) return
    selectedAuthorityResource.value = row
    authorityForm.resourceId = row.id
    authorityForm.salingPrice = Number(row.price || 0)
  }

  function authorityRowClass({ row }: { row: AuthorityMediaResource }) {
    return row.id === authorityForm.resourceId ? 'is-selected-authority' : ''
  }

  async function submitAuthorityMedia() {
    if (!authorityForm.articleId || !authorityForm.resourceId) return
    authoritySubmitting.value = true
    try {
      const result = await distributeContentArticleToAuthorityMedia(authorityForm.articleId, {
        resourceId: authorityForm.resourceId,
        salingPrice: authorityForm.salingPrice,
        publishedAt: authorityForm.publishedAt || undefined,
        remark: authorityForm.remark || undefined,
      })
      const task = result.data.data
      if (task.status === 'submitted') {
        ElMessage.success('权威媒体订单已提交，等待出稿')
        authorityMediaVisible.value = false
        await options.load()
        return
      }
      ElMessage.error(task.errorMessage || '权威媒体订单提交失败')
    } finally {
      authoritySubmitting.value = false
    }
  }

  async function distributeToAgentSite(row: ArticleDraft) {
    const detailRes = await getContentArticleDetail(row.id)
    const brandId = detailRes.data.data.project?.brandId
    if (!brandId) {
      ElMessage.error('当前文章未绑定品牌，无法分发到 Agent 官网')
      return
    }
    options.submitting.value = true
    try {
      const brandRes = await getBrandDetail(brandId)
      const brand = brandRes.data.data
      if (!brand.geoSiteDomain || brand.geoSiteStatus !== 'active') {
        ElMessage.error('当前品牌未配置可用 Agent 官网域名')
        return
      }
      const result = await distributeContentArticleToAgentSite(row.id, brandId)
      const task = result.data.data
      if (isDistributionSuccessStatus(task.status)) {
        ElMessage.success(task.publishedUrl
          ? `已分发到 ${brand.brandName || '品牌'} Agent 官网：${task.publishedUrl}`
          : `已分发到 ${brand.brandName || '品牌'} Agent 官网`)
      } else if (isDistributionActiveStatus(task.status)) {
        ElMessage.info(`${brand.brandName || '品牌'} Agent 官网已有发布任务正在处理中`)
      } else {
        ElMessage.error(task.errorMessage || 'Agent 官网分发失败')
      }
      await options.load()
    } finally {
      options.submitting.value = false
    }
  }

  return {
    distributionChannelVisible,
    distributionChannels,
    industrySiteVisible,
    industrySiteLoading,
    industrySiteSubmitting,
    industrySites,
    selectedIndustrySiteId,
    forumSiteVisible,
    forumSiteLoading,
    forumSiteSubmitting,
    forumSites,
    selectedForumSiteId,
    selectedForumFid,
    selectedForumBoards,
    canSubmitForumSite,
    authorityMediaVisible,
    authorityLoading,
    authoritySubmitting,
    authorityResources,
    selectedAuthorityResource,
    authorityPage,
    authorityQuery,
    authorityForm,
    openDistributionChannel,
    selectDistributionChannel,
    selectIndustrySite,
    selectForumSite,
    submitIndustrySite,
    submitForumSite,
    searchAuthorityMedia,
    resetAuthorityMediaQuery,
    onAuthorityPageChange,
    selectAuthorityResource,
    submitAuthorityMedia,
    distributionChannelInitial,
    distributionChannelClass,
    industrySiteInitial,
    industrySiteTagText,
    entranceLevelLabel,
    newsResourceLabel,
    includeConditionLabel,
    authorityMediaInitial,
    authorityWeightText,
    authorityRowClass,
    money,
    openExternalLink,
  }
}

function distributionChannelInitial(v: DistributionChannel) {
  const map: Record<DistributionChannel, string> = {
    official_site: '站',
    industry_site: '讯',
    forum: '坛',
    self_media: '媒',
    authority_media: '权',
  }
  return map[v]
}

function distributionChannelClass(v: DistributionChannel) {
  const map: Record<DistributionChannel, string> = {
    official_site: 'is-official',
    industry_site: 'is-industry',
    forum: 'is-forum',
    self_media: 'is-media',
    authority_media: 'is-authority',
  }
  return map[v]
}

function money(value: number | string | null | undefined) {
  const n = Number(value || 0)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

function parseIndustryTags(raw?: string | string[] | null) {
  if (Array.isArray(raw)) return raw
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function enabledForumBoards(site?: PublishSite | null): ForumBoardOption[] {
  if (!site?.contentConstraints || site.integrationMethod !== 'discuz_http') return []
  try {
    const parsed = JSON.parse(site.contentConstraints)
    const boards = Array.isArray(parsed?.boards) ? parsed.boards : []
    if (!Array.isArray(boards)) return []
    return boards
      .map((board) => {
        const value = isRecord(board) ? board : {}
        return {
          fid: Number(value.fid),
          name: String(value.name || value.fid || ''),
          enabled: value.enabled !== false,
          default: value.default === true,
        }
      })
      .filter((board: ForumBoardOption) => Number.isInteger(board.fid) && board.fid > 0 && board.enabled)
  } catch {
    return []
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isAgentPublishSite(site: PublishSite) {
  return site.integrationMethod === 'brand_geo_site' || site.siteCode === 'agent_official_site'
}

function isForumPublishSite(site: PublishSite) {
  return site.integrationMethod === 'forum_playwright' || site.integrationMethod === 'discuz_http'
}

function isIndustryPublishSite(site: PublishSite) {
  return !isAgentPublishSite(site) && !isForumPublishSite(site)
}

function industrySiteInitial(site: PublishSite) {
  const text = (site.siteName || site.domain || '站').trim()
  return Array.from(text)[0] || '站'
}

function industrySiteTagText(site: PublishSite) {
  const tags = parseIndustryTags(site.industryTags)
  return tags.length ? tags.join(' / ') : '未分类'
}

function entranceLevelLabel(value?: number | null) {
  const map: Record<number, string> = {
    0: '无入口',
    1: '首页入口',
    2: '频道入口',
    3: '上级入口',
  }
  return value == null ? '-' : (map[value] || String(value))
}

function newsResourceLabel(value?: number | null) {
  const map: Record<number, string> = {
    0: '非新闻源',
    1: '百度新闻源',
    2: '头条新闻源',
    3: '百度&头条',
  }
  return value == null ? '-' : (map[value] || String(value))
}

function includeConditionLabel(value?: number | null) {
  const map: Record<number, string> = {
    0: '不包收录',
    1: '百度包收录',
    2: '头条包收录',
  }
  return value == null ? '-' : (map[value] || String(value))
}

function authorityMediaInitial(resource: AuthorityMediaResource) {
  const text = (resource.name || resource.industry || '媒').trim()
  return Array.from(text)[0] || '媒'
}

function authorityWeightText(resource: AuthorityMediaResource) {
  return `PC ${resource.pcWeight ?? '-'} / M ${resource.mWeight ?? '-'}`
}

function openExternalLink(url?: string | null) {
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

function isDistributionSuccessStatus(status?: string | null) {
  return ['submitted', 'confirmed', 'published'].includes((status || '').toLowerCase())
}

function isDistributionActiveStatus(status?: string | null) {
  return ['pending', 'token_issued', 'filling', 'filled', 'submitting'].includes((status || '').toLowerCase())
}
