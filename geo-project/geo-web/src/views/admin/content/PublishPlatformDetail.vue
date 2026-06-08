<template>
  <div class="platform-detail-page">
    <header class="detail-header">
      <button class="back-btn" type="button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </button>
      <div class="detail-title-row">
        <div class="platform-logo" :class="platform?.logoClass || 'media'">{{ platform?.logoText || '-' }}</div>
        <div class="min-w-0">
          <div class="detail-kicker">发布平台详情</div>
          <h1 class="detail-title">{{ platform?.name || '平台不存在' }}</h1>
          <div class="detail-subtitle">{{ platform?.code || routeCode }}</div>
        </div>
      </div>
      <div class="header-actions">
        <button v-if="isWechatPlatform" class="btn btn-ghost" type="button" @click="goWechatTemplates">样式模板</button>
        <button v-if="platform?.source === 'publish_site'" class="btn btn-ghost" type="button" :disabled="testing" @click="testSite">
          {{ testing ? '测试中...' : '测试连通' }}
        </button>
        <button class="btn btn-primary" type="button" @click="goManagement">平台管理</button>
      </div>
    </header>

    <DataState :loading="loading" :empty="!loading && !platform" empty-text="未找到该发布平台">
      <main v-if="platform" class="detail-grid">
        <section class="detail-card summary-card">
          <div class="card-title">基础信息</div>
          <div class="summary-row">
            <span>状态</span>
            <strong :class="platform.enabled ? 'ok' : 'muted'">{{ platform.enabled ? '启用' : '停用' }}</strong>
          </div>
          <div class="summary-row">
            <span>平台大类</span>
            <strong>{{ platform.categoryName }}</strong>
          </div>
          <div class="summary-row">
            <span>接入方式</span>
            <strong>{{ platform.integrationLabel }}</strong>
          </div>
          <div class="summary-row">
            <span>自动发布</span>
            <strong :class="platform.autoPublish ? 'ok' : 'muted'">{{ platform.autoPublish ? '已开启' : '未开启' }}</strong>
          </div>
        </section>

        <section v-if="isWechatPlatform" class="detail-card">
          <div class="card-title">公众号样式模板</div>
          <div class="template-entry">
            <div>
              <strong>管理公众号文章渲染样式</strong>
              <p>用于文章详情中的公众号样式渲染，维护标题、正文、强调块等样式片段。</p>
            </div>
            <button class="btn btn-primary" type="button" @click="goWechatTemplates">进入管理</button>
          </div>
        </section>

        <section class="detail-card">
          <div class="card-title">站点配置</div>
          <div class="info-list">
            <div class="info-item">
              <span>站点域名</span>
              <strong>{{ platform.domain || '-' }}</strong>
            </div>
            <div class="info-item">
              <span>接口 / 发帖 URL</span>
              <strong class="mono break-text">{{ platform.apiEndpoint || '-' }}</strong>
            </div>
            <div class="info-item">
              <span>行业分类</span>
              <strong>{{ platform.industryTags || '-' }}</strong>
            </div>
            <div class="info-item">
              <span>执行器</span>
              <strong>{{ platform.executor || '-' }}</strong>
            </div>
            <div class="info-item">
              <span>认证信息</span>
              <strong>{{ platform.credentialConfigured ? '已配置' : '未配置' }}</strong>
            </div>
          </div>
        </section>

        <section v-if="platform.boards.length" class="detail-card wide-card">
          <div class="card-title">论坛版块</div>
          <div class="board-table">
            <div class="board-row board-head">
              <span>版块名称</span>
              <span>fid</span>
              <span>默认</span>
              <span>状态</span>
            </div>
            <div v-for="board in platform.boards" :key="`${board.fid}-${board.name}`" class="board-row">
              <strong>{{ board.name }}</strong>
              <span class="mono">{{ board.fid }}</span>
              <span>{{ board.default ? '是' : '否' }}</span>
              <span :class="board.enabled ? 'ok' : 'muted'">{{ board.enabled ? '启用' : '停用' }}</span>
            </div>
          </div>
        </section>

        <section class="detail-card wide-card">
          <div class="card-title">备注</div>
          <p class="remark">{{ platform.remark || '暂无备注' }}</p>
        </section>
      </main>
    </DataState>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import DataState from '@/components/ui/DataState.vue'
import { getPublishSites, testPublishSite } from '@/api/publishSite'
import type { PublishSite } from '@/types'

interface BoardInfo {
  fid: number
  name: string
  enabled: boolean
  default: boolean
}

interface PlatformDetail {
  id?: number
  categoryName: string
  name: string
  code: string
  logoText: string
  logoClass: string
  enabled: boolean
  autoPublish: boolean
  integrationLabel: string
  domain?: string | null
  apiEndpoint?: string | null
  industryTags?: string
  executor?: string
  credentialConfigured: boolean
  boards: BoardInfo[]
  remark?: string | null
  source: 'static' | 'publish_site'
}

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const testing = ref(false)
const publishSites = ref<PublishSite[]>([])
const routeCode = computed(() => String(route.params.code || ''))

const staticPlatforms: PlatformDetail[] = [
  staticPlatform('toutiao', '今日头条', '头', 'toutiao', '自媒体平台'),
  staticPlatform('zhihu', '知乎', '知', 'zhihu', '自媒体平台'),
  staticPlatform('wechat', '公众号', '微', 'wechat', '自媒体平台'),
  staticPlatform('douyin', '抖音图文', '抖', 'douyin', '自媒体平台'),
  staticPlatform('baijiahao', '百家号', '百', 'media', '自媒体平台'),
  staticPlatform('xiaohongshu', '小红书', '红', 'xiaohongshu', '自媒体平台'),
  staticPlatform('netease', '网易', '网', 'media', '自媒体平台'),
  staticPlatform('sohu', '搜狐', '狐', 'media', '自媒体平台'),
  {
    ...staticPlatform('agent_site', 'Agent 官网', 'A', 'agent', 'Agent 官网'),
    autoPublish: true,
    integrationLabel: 'Agent 官网发布器',
    executor: 'agent_site_publisher',
    remark: 'Agent 官网自动发布目标',
  },
  staticPlatform('authority_media', '权威媒体', '权', 'media', '权威媒体'),
]

const platform = computed<PlatformDetail | null>(() => {
  const site = publishSites.value.find((item) => item.siteCode === routeCode.value)
  if (site) return toPublishSiteDetail(site)
  return staticPlatforms.find((item) => item.code === routeCode.value) || null
})
const isWechatPlatform = computed(() => platform.value?.code === 'wechat')

function staticPlatform(code: string, name: string, logoText: string, logoClass: string, categoryName: string): PlatformDetail {
  return {
    categoryName,
    name,
    code,
    logoText,
    logoClass,
    enabled: true,
    autoPublish: false,
    integrationLabel: '人工发布',
    credentialConfigured: false,
    boards: [],
    source: 'static',
  }
}

function toPublishSiteDetail(site: PublishSite): PlatformDetail {
  const isForum = site.integrationMethod === 'discuz_http' || site.integrationMethod === 'forum_playwright'
  const isAgent = site.integrationMethod === 'brand_geo_site' || site.siteCode === 'agent_official_site'
  return {
    id: site.id,
    categoryName: isAgent ? 'Agent 官网' : isForum ? '平台网站' : '行业资讯站',
    name: site.siteName,
    code: site.siteCode,
    logoText: site.siteName.slice(0, 1),
    logoClass: isAgent ? 'agent' : isForum ? 'forum' : 'media',
    enabled: site.status === 'active',
    autoPublish: site.status === 'active' && site.integrationMethod !== 'manual',
    integrationLabel: integrationMethodLabel(site.integrationMethod),
    domain: site.domain,
    apiEndpoint: site.apiEndpoint,
    industryTags: parseIndustryTags(site.industryTags).join(' / '),
    executor: executorLabel(site.integrationMethod),
    credentialConfigured: Boolean(site.apiCredential || site.apiCredentialEncrypted || site.credentialRef),
    boards: parseBoards(site.contentConstraints),
    remark: site.remark,
    source: 'publish_site',
  }
}

function parseIndustryTags(raw?: string | string[] | null) {
  if (Array.isArray(raw)) return raw.map(String)
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    return raw.split(/[,，/]/).map((item) => item.trim()).filter(Boolean)
  }
}

function parseBoards(raw?: string | null): BoardInfo[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    const boards = Array.isArray(parsed?.boards) ? parsed.boards : []
    return boards.map((board: any) => ({
      fid: Number(board?.fid),
      name: String(board?.name || ''),
      enabled: board?.enabled !== false,
      default: board?.default === true,
    })).filter((board: BoardInfo) => Number.isFinite(board.fid) && board.name)
  } catch {
    return []
  }
}

function integrationMethodLabel(method?: string | null) {
  if (method === 'rest_api') return 'REST API'
  if (method === 'discuz_http') return 'Discuz HTTP 直发'
  if (method === 'forum_playwright') return '平台网站浏览器自动化'
  if (method === 'brand_geo_site') return 'Agent 官网'
  if (method === 'manual') return '手动发布'
  return method || '-'
}

function executorLabel(method?: string | null) {
  if (method === 'discuz_http') return 'Discuz HTTP 发布器'
  if (method === 'forum_playwright') return '平台网站发布执行器'
  if (method === 'brand_geo_site') return 'Agent 官网发布器'
  if (method === 'rest_api') return '行业资讯站发布器'
  return method || '-'
}

async function loadDetail() {
  loading.value = true
  try {
    const { data } = await getPublishSites()
    publishSites.value = data.data || []
  } catch {
    publishSites.value = []
    ElMessage.error('加载发布平台详情失败')
  } finally {
    loading.value = false
  }
}

async function testSite() {
  if (!platform.value?.id) return
  testing.value = true
  try {
    const { data } = await testPublishSite(platform.value.id)
    if (data.data?.success) {
      ElMessage.success(`连通测试成功，耗时 ${data.data.elapsedMs ?? '-'}ms`)
    } else {
      ElMessage.warning(`连通测试失败：${data.data?.message || '无法连接'}`)
    }
  } finally {
    testing.value = false
  }
}

function goBack() {
  router.back()
}

function goManagement() {
  router.push({ name: 'PublishPlatformManagement' })
}

function goWechatTemplates() {
  router.push({ path: '/admin/content/wechat-templates' })
}

onMounted(loadDetail)
</script>

<style scoped>
.platform-detail-page {
  min-height: calc(100vh - var(--header-height) - 40px);
  padding: 22px;
  background: #f7f8fb;
  color: #1f2937;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 20px;
  margin-bottom: 18px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 1px 2px rgb(15 23 42 / 0.04);
}

.detail-title-row {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.detail-kicker {
  margin-bottom: 2px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.detail-title {
  margin: 0;
  font-size: 22px;
  line-height: 1.25;
}

.detail-subtitle {
  margin-top: 4px;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.platform-logo {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 44px;
  height: 44px;
  color: #fff;
  font-weight: 800;
  border-radius: 8px;
}

.platform-logo.toutiao { background: #f04438; }
.platform-logo.zhihu { background: #056de8; }
.platform-logo.wechat { background: #07c160; }
.platform-logo.douyin { background: #111827; }
.platform-logo.agent { background: #2563eb; }
.platform-logo.forum { background: #0f766e; }
.platform-logo.media { background: #7c3aed; }

.header-actions,
.back-btn,
.btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.header-actions {
  flex-shrink: 0;
}

.back-btn,
.btn {
  height: 36px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  border-radius: 8px;
}

.back-btn,
.btn-ghost {
  color: #334155;
  background: #fff;
  border: 1px solid #cbd5e1;
}

.btn-primary {
  color: #fff;
  background: #2563eb;
  border: 1px solid #2563eb;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.8fr) minmax(0, 1.2fr);
  gap: 16px;
}

.detail-card {
  padding: 18px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 1px 2px rgb(15 23 42 / 0.04);
}

.wide-card {
  grid-column: 1 / -1;
}

.card-title {
  padding-bottom: 12px;
  margin-bottom: 14px;
  color: #111827;
  font-size: 14px;
  font-weight: 800;
  border-bottom: 1px solid #eef2f7;
}

.summary-row,
.info-item,
.board-row {
  display: grid;
  gap: 12px;
  align-items: center;
}

.summary-row {
  grid-template-columns: 120px 1fr;
  padding: 10px 0;
}

.summary-row + .summary-row,
.info-item + .info-item {
  border-top: 1px solid #f1f5f9;
}

.summary-row span,
.info-item span,
.board-head {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.info-item {
  grid-template-columns: 140px minmax(0, 1fr);
  padding: 12px 0;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.break-text {
  overflow-wrap: anywhere;
}

.ok {
  color: #047857;
}

.muted {
  color: #64748b;
}

.board-table {
  display: grid;
  gap: 8px;
}

.board-row {
  grid-template-columns: minmax(0, 1fr) 110px 80px 80px;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 8px;
}

.board-head {
  background: transparent;
  border: 0;
}

.remark {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

.template-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.template-entry strong {
  display: block;
  margin-bottom: 6px;
  color: #111827;
  font-size: 14px;
}

.template-entry p {
  max-width: 520px;
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.min-w-0 {
  min-width: 0;
}

@media (max-width: 820px) {
  .platform-detail-page {
    padding: 14px;
  }

  .detail-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
  }

  .template-entry {
    align-items: stretch;
    flex-direction: column;
  }

  .detail-grid,
  .summary-row,
  .info-item,
  .board-row {
    grid-template-columns: 1fr;
  }
}
</style>
