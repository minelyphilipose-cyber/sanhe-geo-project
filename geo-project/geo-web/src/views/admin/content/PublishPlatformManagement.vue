<template>
  <div class="publish-platform-page">
    <header class="page-header">
      <div class="page-title-row">
        <div class="page-title-block">
          <h1 class="page-title">
            发布平台管理
            <span class="page-title-tag">平台配置</span>
          </h1>
          <p class="page-desc">管理文章发布目标、平台分类、自动发布能力和启用状态。停用后不再进入新生成与发布流程，历史文章保留。</p>
        </div>
        <div class="page-actions">
          <div class="search">
            <el-icon><Search /></el-icon>
            <input v-model="keyword" placeholder="搜索平台名称..." />
          </div>
          <button class="btn btn-ghost" type="button" @click="refreshPage">
            <el-icon><Refresh /></el-icon>
            刷新
          </button>
          <button class="btn btn-primary" type="button" @click="openDrawer('new')">
            <el-icon><Plus /></el-icon>
            新增发布目标
          </button>
        </div>
      </div>

      <section class="stats">
        <div class="stat stat-blue">
          <div class="stat-content">
            <div class="stat-label">平台大类</div>
            <div class="stat-value">{{ categories.length }}<span class="unit">类</span></div>
            <div class="stat-trend">当前可配置的大类</div>
          </div>
          <div class="stat-icon blue"><el-icon><Grid /></el-icon></div>
        </div>
        <div class="stat stat-indigo">
          <div class="stat-content">
            <div class="stat-label">发布目标</div>
            <div class="stat-value">{{ targetStats.total }}<span class="unit">个</span></div>
            <div class="stat-trend">已配置目标数</div>
          </div>
          <div class="stat-icon gray"><el-icon><Link /></el-icon></div>
        </div>
        <div class="stat stat-green">
          <div class="stat-content">
            <div class="stat-label">自动发布</div>
            <div class="stat-value">{{ targetStats.autoPublish }}<span class="unit">个</span></div>
            <div class="stat-trend">已绑定执行器</div>
          </div>
          <div class="stat-icon green"><el-icon><Promotion /></el-icon></div>
        </div>
        <div class="stat stat-amber">
          <div class="stat-content">
            <div class="stat-label">已启用</div>
            <div class="stat-value">{{ targetStats.enabled }}<span class="unit">个</span></div>
            <div class="stat-trend">可进入业务流程</div>
          </div>
          <div class="stat-icon amber"><el-icon><SwitchButton /></el-icon></div>
        </div>
      </section>
    </header>

    <main class="content">
      <aside class="category-pane">
        <div class="pane-header">
          <div class="pane-title">平台大类</div>
          <div class="pane-count">{{ categories.length }} 类</div>
        </div>
        <div class="cat-list">
          <button
            v-for="category in categories"
            :key="category.code"
            class="cat-item"
            :class="{ active: activeCategoryCode === category.code }"
            type="button"
            @click="switchCategory(category.code)"
          >
            <span class="cat-icon"><el-icon><component :is="category.icon" /></el-icon></span>
            <span class="cat-meta">
              <span class="cat-name">{{ category.name }}</span>
              <span class="cat-desc">{{ category.shortDesc }}</span>
            </span>
            <span class="cat-badge">{{ targetsByCategory(category.code).length }}</span>
          </button>
        </div>
      </aside>

      <section ref="targetPaneRef" class="target-pane">
        <div class="target-header">
          <div class="target-header-text">
            <div class="target-h">
              <div class="target-title">{{ activeCategory?.name }}</div>
              <div class="target-meta">
                <span class="dot"></span>
                {{ activeCategoryMeta }}
              </div>
            </div>
            <div class="target-desc">{{ activeCategory?.desc }}</div>
          </div>
          <button
            v-if="activeCategory?.canCreate"
            class="btn btn-primary btn-sm"
            type="button"
            @click="openDrawer(activeCategory.code === 'industry_site' ? 'news-new' : 'new')"
          >
            <el-icon><Plus /></el-icon>
            {{ activeCategory.createText || '新增发布目标' }}
          </button>
        </div>

        <div v-if="activeCategory?.notice" class="notice" :class="{ info: activeCategory.noticeType === 'info' }">
          <el-icon><Warning /></el-icon>
          <div v-html="activeCategory.notice"></div>
        </div>

        <div class="target-grid">
          <article
            v-for="target in visibleTargets"
            :key="target.code"
            class="target-card fade-in"
            :class="{ disabled: !target.enabled }"
          >
            <div class="target-card-top">
              <div class="target-name-block">
                <div class="platform-logo" :class="target.logoClass">{{ target.logoText }}</div>
                <div>
                  <div class="target-name">{{ target.name }}</div>
                  <div class="target-id">{{ target.code }}</div>
                </div>
              </div>
              <label class="switch" :class="{ disabled: target.locked || target.updating }">
                <input
                  :checked="target.enabled"
                  type="checkbox"
                  :disabled="target.locked || target.updating"
                  @change="toggleTarget(target)"
                />
                <span class="slider"></span>
              </label>
            </div>

            <div class="target-fields">
              <div v-for="field in target.fields" :key="field.label" class="field">
                <div class="field-label">{{ field.label }}</div>
                <div class="field-value" :class="{ mono: field.mono, wrap: field.wrap }">
                  <span v-if="field.badge" class="badge" :class="field.badge">
                    <span class="dot"></span>{{ field.value }}
                  </span>
                  <span v-else>{{ field.value }}</span>
                </div>
              </div>
            </div>

            <div class="target-actions">
              <button class="btn btn-link" type="button" @click="openDrawer(target.drawerType || target.code, target)">编辑</button>
              <button v-if="target.source === 'publish_site'" class="btn btn-link" type="button" @click="testIndustrySite(target)">测试连通</button>
              <button v-if="!target.locked" class="btn btn-danger-link" type="button" @click="toggleTarget(target)">
                {{ target.enabled ? '停用' : '启用' }}
              </button>
              <button v-else-if="target.disabledActionText" class="btn btn-ghost btn-sm disabled-action" disabled type="button">
                <el-icon><Plus /></el-icon>
                {{ target.disabledActionText }}
              </button>
            </div>
          </article>
        </div>

        <el-empty v-if="visibleTargets.length === 0" description="没有匹配的发布目标" :image-size="96" />
      </section>
    </main>

    <div class="drawer-overlay" :class="{ open: drawerVisible }" @click="closeDrawer"></div>
    <aside class="drawer" :class="{ open: drawerVisible }">
      <div class="drawer-header">
        <div>
          <div class="drawer-title">{{ drawerTitle }}</div>
          <div class="drawer-subtitle">{{ drawerSubtitle }}</div>
        </div>
        <button class="drawer-close" type="button" @click="closeDrawer">
          <el-icon><Close /></el-icon>
        </button>
      </div>

      <div class="drawer-body">
        <section class="drawer-section">
          <div class="form-section-label">基础信息</div>
          <div class="form-row">
            <label>所属大类</label>
            <select v-model="drawerForm.categoryCode" class="select">
              <option v-for="category in categories" :key="category.code" :value="category.code">{{ category.name }}</option>
            </select>
          </div>
          <div class="form-grid-2">
            <div class="form-row">
              <label>平台名称</label>
              <input v-model="drawerForm.name" class="input" type="text" />
            </div>
            <div class="form-row">
              <label>{{ drawerForm.categoryCode === 'agent_site' ? '平台标识 / 站点唯一标识' : '平台标识' }}</label>
              <input v-model="drawerForm.code" class="input mono" type="text" />
              <div class="hint">
                {{ drawerForm.categoryCode === 'agent_site' ? 'Agent 官网使用该标识作为发布所需的站点唯一标识，提交后不可修改。' : '唯一标识，提交后不可修改' }}
              </div>
            </div>
          </div>
          <div class="form-row">
            <label>内容风格</label>
            <select v-model="drawerForm.contentStyle" class="select">
              <option value="toutiao">今日头条</option>
              <option value="wechat">公众号</option>
              <option value="zhihu">知乎</option>
              <option value="douyin">抖音图文</option>
              <option value="netease">网易</option>
              <option value="agent_site_article">Agent 官网文章</option>
              <option value="industry_site">行业资讯站</option>
              <option value="authority_media">权威媒体</option>
              <option value="forum">平台网站</option>
            </select>
            <div class="hint">影响“批量生成文章”页面的可选风格。停用后该风格不再用于新生成，历史文章保留。</div>
          </div>
        </section>

        <section v-if="drawerForm.categoryCode === 'industry_site' || drawerForm.categoryCode === 'forum'" class="drawer-section">
          <div class="form-section-label">{{ drawerForm.categoryCode === 'forum' ? '平台网站接入配置' : '行业站点接入配置' }}</div>
          <div class="form-grid-2">
            <div class="form-row">
              <label>站点域名</label>
              <input v-model="drawerForm.domain" class="input mono" type="text" placeholder="example.com" />
            </div>
            <div class="form-row">
              <label>网站图标</label>
              <input v-model="drawerForm.iconUrl" class="input" type="text" placeholder="https://example.com/favicon.ico" />
            </div>
          </div>
          <div class="form-row">
            <label>{{ drawerForm.categoryCode === 'forum' ? '发帖页面 URL' : '发布接口 URL' }}</label>
            <input v-model="drawerForm.apiEndpoint" class="input mono" type="text" :placeholder="drawerForm.categoryCode === 'forum' ? 'https://forum.example.com/post/new' : 'https://example.com/api/article/publish'" />
          </div>
          <div class="form-grid-2">
            <div class="form-row">
              <label>行业分类</label>
              <input v-model="drawerForm.industryTags" class="input" type="text" placeholder="多个行业用逗号分隔" />
            </div>
            <div class="form-row">
              <label>接入方式</label>
              <select v-model="drawerForm.integrationMethod" class="select">
                <option value="rest_api">REST API</option>
                <option value="discuz_http">Discuz HTTP 直发</option>
                <option value="forum_playwright">平台网站浏览器自动化</option>
                <option value="manual">手动发布</option>
              </select>
            </div>
          </div>
          <div class="form-row">
            <label>请求头信息</label>
            <textarea v-model="drawerForm.headers" class="input textarea mono" placeholder='{"X-Admin-Token":"token"}'></textarea>
            <div class="hint">由原“发布站点配置”迁入，后续保存时会写入行业资讯站点配置。</div>
          </div>
          <div v-if="drawerForm.categoryCode === 'forum'" class="form-row">
            <label>账号 / Cookie 配置</label>
            <textarea
              v-model="drawerForm.apiCredential"
              class="input textarea mono credential-textarea"
              placeholder='{"accounts":[{"username":"账号","password":"密码","cookie":"完整 Cookie","status":"active"}]}'
            ></textarea>
            <div class="hint">支持多个账号，发布时随机选择一个 active 账号；Cookie 为空时才会尝试账号密码登录。编辑时留空表示不修改已保存凭证。</div>
          </div>
          <div v-if="drawerForm.categoryCode === 'forum' && drawerForm.integrationMethod === 'discuz_http'" class="form-row">
            <div class="forum-board-header">
              <label>版块配置</label>
              <button class="btn btn-ghost btn-sm" type="button" @click="addForumBoard">添加版块</button>
            </div>
            <div class="forum-board-table">
              <div class="forum-board-row forum-board-row-head">
                <span>版块名称</span>
                <span>fid</span>
                <span>默认</span>
                <span>启用</span>
                <span></span>
              </div>
              <div v-for="(board, index) in drawerForm.forumBoards" :key="index" class="forum-board-row">
                <input v-model="board.name" class="input" type="text" placeholder="综合交流" />
                <input v-model.number="board.fid" class="input mono" type="number" min="1" placeholder="12" />
                <label class="checkbox-cell">
                  <input type="radio" :checked="board.default" name="forumDefaultBoard" @change="setDefaultForumBoard(index)" />
                </label>
                <label class="switch compact">
                  <input v-model="board.enabled" type="checkbox" />
                  <span class="slider"></span>
                </label>
                <button class="icon-action danger" type="button" title="删除版块" @click="removeForumBoard(index)">
                  <el-icon><Close /></el-icon>
                </button>
              </div>
            </div>
            <div class="hint">仅启用版块会出现在单篇和批量发布选择中；默认版块用于兼容旧调用。</div>
          </div>
        </section>

        <section class="drawer-section">
          <div class="form-section-label">发布能力</div>
          <div class="toggle-row">
            <div class="toggle-meta">
              <div class="toggle-title">是否启用</div>
              <div class="toggle-desc">关闭后该目标不出现在新生成与发布选项中，历史文章保留。</div>
            </div>
            <label class="switch">
              <input v-model="drawerForm.enabled" type="checkbox" />
              <span class="slider"></span>
            </label>
          </div>
          <div class="toggle-row">
            <div class="toggle-meta">
              <div class="toggle-title">允许自动发布</div>
              <div class="toggle-desc">需先在执行器配置中绑定执行器，否则该项保持关闭。</div>
            </div>
            <label class="switch" :class="{ disabled: !drawerForm.executor }">
              <input v-model="drawerForm.autoPublish" type="checkbox" :disabled="!drawerForm.executor" />
              <span class="slider"></span>
            </label>
          </div>
          <div class="form-row drawer-executor-row">
            <label>发布执行器</label>
            <select v-model="drawerForm.executor" class="select">
              <option value="">无 - 人工发布</option>
              <option value="industry_site">行业资讯站发布器</option>
              <option value="discuz_http">Discuz HTTP 发布器</option>
              <option value="forum_playwright">平台网站发布执行器</option>
              <option value="agent_site_publisher">Agent 官网发布器</option>
            </select>
          </div>
        </section>

        <section class="drawer-section">
          <div class="form-section-label">速率与并发</div>
          <div class="form-grid-2">
            <div class="form-row">
              <label>默认发布间隔</label>
              <div class="interval-input">
                <input v-model="drawerForm.interval" class="input mono" type="number" min="1" />
                <select v-model="drawerForm.intervalUnit" class="select unit-select">
                  <option value="minutes">分钟</option>
                  <option value="hours">小时</option>
                </select>
              </div>
            </div>
            <div class="form-row">
              <label>同平台并发上限</label>
              <input v-model="drawerForm.concurrency" class="input mono" type="number" min="1" />
              <div class="hint">同一时刻最多并发任务数</div>
            </div>
          </div>
        </section>

        <section class="drawer-section">
          <div class="form-section-label">备注</div>
          <div class="form-row">
            <textarea v-model="drawerForm.remark" class="input textarea" placeholder="备注信息（可选）"></textarea>
          </div>
        </section>
      </div>

      <div class="drawer-footer">
        <button class="btn btn-ghost" type="button" @click="closeDrawer">取消</button>
        <button class="btn btn-primary" type="button" :disabled="saving" @click="submitDrawer">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createPublishSite, getPublishSites, testPublishSite, updatePublishSite, updatePublishSiteStatus } from '@/api/publishSite'
import type { PublishSite } from '@/types'
import {
  ChatDotRound,
  Close,
  Connection,
  Grid,
  Link,
  Monitor,
  OfficeBuilding,
  Plus,
  Promotion,
  Reading,
  Refresh,
  Search,
  SwitchButton,
  Warning,
} from '@element-plus/icons-vue'

interface CategoryConfig {
  code: string
  name: string
  shortDesc: string
  desc: string
  icon: object
  canCreate?: boolean
  createText?: string
  notice?: string
  noticeType?: 'info' | 'warning'
}

interface TargetField {
  label: string
  value: string
  badge?: 'success' | 'danger' | 'warning' | 'neutral' | 'accent'
  mono?: boolean
  wrap?: boolean
}

interface TargetConfig {
  categoryCode: string
  name: string
  code: string
  logoText: string
  logoClass: string
  enabled: boolean
  autoPublish: boolean
  locked?: boolean
  executor?: string
  drawerType?: string
  disabledActionText?: string
  source?: 'static' | 'publish_site'
  publishSite?: PublishSite
  updating?: boolean
  fields: TargetField[]
}

interface ForumBoardConfig {
  fid: number | null
  name: string
  enabled: boolean
  default: boolean
}

const categories: CategoryConfig[] = [
  {
    code: 'self_media',
    name: '自媒体平台',
    shortDesc: '头条 / 知乎 / 公众号 / 抖音',
    desc: '自媒体平台用于内容风格与人工发布归类。当前默认不支持自动发布，批量发布时需要提示运营转人工处理。',
    icon: ChatDotRound,
  },
  {
    code: 'agent_site',
    name: 'Agent 官网',
    shortDesc: '官网文章发布目标',
    desc: '领英风格在发布侧统一归并为 Agent 官网。新生成文章建议展示为 Agent 官网文章，历史 linkedin 风格兼容映射到该目标。',
    icon: Monitor,
    notice: 'Agent 官网支持自动发布。同平台多篇发布时仍需要按批量发布任务配置发布时间间隔。',
    noticeType: 'info',
  },
  {
    code: 'industry_site',
    name: '行业资讯站',
    shortDesc: '按行业配置具体站点',
    desc: '按行业配置的资讯站点。批量发布时根据品牌配置中的“资讯站名称 + 唯一标识”自动匹配。',
    icon: Reading,
    canCreate: true,
    createText: '新增资讯站',
  },
  {
    code: 'authority_media',
    name: '权威媒体',
    shortDesc: '固定大类，不展开',
    desc: '作为文章风格分类与人工发布归类使用。该类型文章仅用于人工投放或外部媒体合作，不进入自动发布流程。',
    icon: OfficeBuilding,
  },
  {
    code: 'forum',
    name: '平台网站',
    shortDesc: 'Discuz / 浏览器自动化',
    desc: '平台网站大类支持 Discuz HTTP 直发，必要时可保留浏览器自动化作为兜底执行器。',
    icon: Connection,
    canCreate: true,
    createText: '新增平台网站',
    notice: '优先使用 <strong>Discuz HTTP 直发</strong>，适合大批量发布；浏览器自动化仅作为页面强依赖脚本时的兜底方案。',
    noticeType: 'info',
  },
]

const staticTargets = reactive<TargetConfig[]>([
  {
    categoryCode: 'self_media',
    name: '今日头条',
    code: 'toutiao',
    logoText: '头',
    logoClass: 'toutiao',
    enabled: true,
    autoPublish: false,
    executor: '',
    source: 'static',
    fields: [
      { label: '内容风格', value: '今日头条' },
      { label: '状态', value: '启用', badge: 'success' },
      { label: '自动发布', value: '不支持', badge: 'neutral' },
      { label: '执行器', value: '-', mono: true },
    ],
  },
  {
    categoryCode: 'self_media',
    name: '知乎',
    code: 'zhihu',
    logoText: '知',
    logoClass: 'zhihu',
    enabled: true,
    autoPublish: false,
    executor: '',
    source: 'static',
    fields: [
      { label: '内容风格', value: '知乎' },
      { label: '状态', value: '启用', badge: 'success' },
      { label: '自动发布', value: '不支持', badge: 'neutral' },
      { label: '执行器', value: '-', mono: true },
    ],
  },
  {
    categoryCode: 'self_media',
    name: '公众号',
    code: 'wechat',
    logoText: '微',
    logoClass: 'wechat',
    enabled: true,
    autoPublish: false,
    executor: '',
    source: 'static',
    fields: [
      { label: '内容风格', value: '公众号' },
      { label: '状态', value: '启用', badge: 'success' },
      { label: '自动发布', value: '不支持', badge: 'neutral' },
      { label: '执行器', value: '-', mono: true },
    ],
  },
  {
    categoryCode: 'self_media',
    name: '抖音图文',
    code: 'douyin',
    logoText: '抖',
    logoClass: 'douyin',
    enabled: true,
    autoPublish: false,
    executor: '',
    source: 'static',
    fields: [
      { label: '内容风格', value: '抖音图文' },
      { label: '状态', value: '启用', badge: 'success' },
      { label: '自动发布', value: '不支持', badge: 'neutral' },
      { label: '执行器', value: '-', mono: true },
    ],
  },
  {
    categoryCode: 'self_media',
    name: '网易',
    code: 'netease',
    logoText: '网',
    logoClass: 'media',
    enabled: true,
    autoPublish: false,
    executor: '',
    source: 'static',
    fields: [
      { label: '内容风格', value: '网易' },
      { label: '状态', value: '启用', badge: 'success' },
      { label: '自动发布', value: '不支持', badge: 'neutral' },
      { label: '执行器', value: '-', mono: true },
    ],
  },
  {
    categoryCode: 'agent_site',
    name: 'Agent 官网',
    code: 'agent_site',
    logoText: 'A',
    logoClass: 'agent',
    enabled: true,
    autoPublish: true,
    executor: 'agent_site_publisher',
    drawerType: 'agent',
    source: 'static',
    fields: [
      { label: '内容风格', value: 'Agent 官网文章' },
      { label: '站点唯一标识', value: 'agent_official_site', mono: true },
      { label: '状态', value: '启用', badge: 'success' },
      { label: '自动发布', value: '已开启', badge: 'accent' },
      { label: '执行器', value: 'Agent 官网发布器' },
      { label: '默认间隔', value: '20 分钟', mono: true },
      { label: '并发上限', value: '2', mono: true },
    ],
  },
  {
    categoryCode: 'authority_media',
    name: '权威媒体',
    code: 'authority_media',
    logoText: '权',
    logoClass: 'media',
    enabled: true,
    autoPublish: false,
    executor: '',
    drawerType: 'authority',
    source: 'static',
    fields: [
      { label: '内容风格', value: '权威媒体' },
      { label: '状态', value: '启用', badge: 'success' },
      { label: '自动发布', value: '不支持', badge: 'neutral' },
      { label: '用途', value: '人工投放 / 媒体合作' },
    ],
  },
])

const keyword = ref('')
const activeCategoryCode = ref('self_media')
const targetPaneRef = ref<HTMLElement>()
const drawerVisible = ref(false)
const drawerTitle = ref('编辑发布目标')
const drawerSubtitle = ref('编辑发布配置')
const publishSites = ref<PublishSite[]>([])
const publishSiteLoading = ref(false)
const saving = ref(false)
const editingPublishSiteId = ref<number | null>(null)

const drawerForm = reactive({
  categoryCode: 'self_media',
  name: '知乎',
  code: 'zhihu',
  contentStyle: 'zhihu',
  enabled: true,
  autoPublish: false,
  executor: '',
  interval: 30,
  intervalUnit: 'minutes',
  concurrency: 1,
  domain: '',
  iconUrl: '',
  apiEndpoint: '',
  industryTags: '',
  integrationMethod: 'rest_api',
  headers: '{\n  "X-Admin-Token": ""\n}',
  apiCredential: '',
  forumBoards: [] as ForumBoardConfig[],
  remark: '该平台当前仅支持人工发布',
})

const activeCategory = computed(() => categories.find((item) => item.code === activeCategoryCode.value))

const agentFallbackTarget = computed(() => staticTargets.find((item) => item.categoryCode === 'agent_site'))

const agentTargets = computed<TargetConfig[]>(() => {
  const sites = publishSites.value.filter((item) => item.integrationMethod === 'brand_geo_site' || item.siteCode === 'agent_official_site')
  if (sites.length) return sites.map((site) => toAgentTarget(site))
  return agentFallbackTarget.value ? [agentFallbackTarget.value] : []
})

const industryTargets = computed<TargetConfig[]>(() => publishSites.value
  .filter((site) => site.integrationMethod !== 'brand_geo_site' && site.integrationMethod !== 'forum_playwright' && site.integrationMethod !== 'discuz_http' && site.siteCode !== 'agent_official_site')
  .map((site) => toIndustryTarget(site)))

const forumTargets = computed<TargetConfig[]>(() => publishSites.value
  .filter((site) => site.integrationMethod === 'forum_playwright' || site.integrationMethod === 'discuz_http')
  .map((site) => toForumTarget(site)))

const targets = computed<TargetConfig[]>(() => [
  ...staticTargets.filter((item) => item.categoryCode !== 'agent_site'),
  ...agentTargets.value,
  ...industryTargets.value,
  ...forumTargets.value,
])

const targetStats = computed(() => ({
  total: targets.value.length,
  enabled: targets.value.filter((item) => item.enabled).length,
  autoPublish: targets.value.filter((item) => item.enabled && item.autoPublish).length,
}))

const visibleTargets = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return targetsByCategory(activeCategoryCode.value).filter((item) => {
    if (!query) return true
    return item.name.toLowerCase().includes(query) || item.code.toLowerCase().includes(query)
  })
})

const activeCategoryMeta = computed(() => {
  const items = targetsByCategory(activeCategoryCode.value)
  const enabledCount = items.filter((item) => item.enabled).length
  const autoCount = items.filter((item) => item.enabled && item.autoPublish).length
  if (activeCategoryCode.value === 'forum') return `${items.length} 个平台网站 · ${autoCount} 个自动发布`
  if (activeCategoryCode.value === 'authority_media') return '固定大类 · 不展开'
  if (activeCategoryCode.value === 'industry_site') return `${items.length} 个站点 · ${autoCount} 个自动发布`
  return `${items.length} 个目标 · ${enabledCount} 个启用`
})

function targetsByCategory(categoryCode: string) {
  return targets.value.filter((item) => item.categoryCode === categoryCode)
}

function switchCategory(categoryCode: string) {
  activeCategoryCode.value = categoryCode
  targetPaneRef.value?.scrollTo({ top: 0, behavior: 'smooth' })
}

function refreshPage() {
  keyword.value = ''
  if (activeCategoryCode.value === 'industry_site' || activeCategoryCode.value === 'agent_site' || activeCategoryCode.value === 'forum') {
    loadPublishSites()
  }
}

function openDrawer(type: string, selectedTarget?: TargetConfig) {
  editingPublishSiteId.value = null
  if (type === 'new') {
    drawerTitle.value = '新增发布目标'
    drawerSubtitle.value = '创建新的发布目标'
    drawerForm.name = ''
    drawerForm.code = ''
    drawerForm.categoryCode = activeCategoryCode.value
    drawerForm.contentStyle = activeCategoryCode.value === 'forum' ? 'forum' : 'industry_site'
    drawerForm.enabled = true
    drawerForm.executor = activeCategoryCode.value === 'forum' ? 'discuz_http' : ''
    drawerForm.autoPublish = activeCategoryCode.value === 'forum'
    drawerForm.remark = ''
    resetSiteConnectionForm()
  } else if (type === 'news-new') {
    drawerTitle.value = '新增行业资讯站'
    drawerSubtitle.value = '配置行业资讯站发布目标'
    drawerForm.categoryCode = 'industry_site'
    drawerForm.name = ''
    drawerForm.code = ''
    drawerForm.contentStyle = 'industry_site'
    drawerForm.executor = 'industry_site'
    drawerForm.autoPublish = true
    drawerForm.remark = ''
    resetSiteConnectionForm()
  } else {
    const target = selectedTarget || targets.value.find((item) => item.code === type || item.drawerType === type)
    drawerTitle.value = type === 'agent' ? '编辑：Agent 官网' : type === 'news' ? '编辑：行业资讯站' : type === 'authority' ? '编辑：权威媒体' : '编辑发布目标'
    drawerSubtitle.value = target?.name ? `${target.name} 发布配置` : '编辑发布目标'
    editingPublishSiteId.value = target?.publishSite?.id || null
    drawerForm.categoryCode = target?.categoryCode || activeCategoryCode.value
    drawerForm.name = target?.name || '知乎'
    drawerForm.code = target?.code || 'zhihu'
    drawerForm.contentStyle = resolveContentStyle(target)
    drawerForm.enabled = target?.enabled ?? true
    drawerForm.executor = target?.executor || ''
    drawerForm.autoPublish = target?.autoPublish ?? false
    drawerForm.remark = target?.autoPublish ? '该平台支持自动发布' : '该平台当前仅支持人工发布'
    fillSiteConnectionForm(target)
  }
  drawerVisible.value = true
}

function resetSiteConnectionForm() {
  drawerForm.domain = ''
  drawerForm.iconUrl = ''
  drawerForm.apiEndpoint = ''
  drawerForm.industryTags = ''
  drawerForm.integrationMethod = drawerForm.categoryCode === 'forum' ? 'discuz_http' : 'rest_api'
  drawerForm.headers = '{\n  "X-Admin-Token": ""\n}'
  drawerForm.apiCredential = ''
  drawerForm.forumBoards = drawerForm.categoryCode === 'forum' ? [emptyForumBoard(true)] : []
}

function fillSiteConnectionForm(target?: TargetConfig) {
  if ((target?.categoryCode !== 'industry_site' && target?.categoryCode !== 'agent_site' && target?.categoryCode !== 'forum') || !target.publishSite) {
    resetSiteConnectionForm()
    return
  }
  const site = target.publishSite
  drawerForm.code = site.siteCode || normalizeSiteCode(site)
  drawerForm.domain = site.domain || ''
  drawerForm.iconUrl = site.iconUrl || ''
  drawerForm.apiEndpoint = site.apiEndpoint || ''
  drawerForm.industryTags = parseIndustryTags(site.industryTags).join(', ')
  drawerForm.integrationMethod = site.integrationMethod || 'rest_api'
  drawerForm.headers = formatHeaders(site.requestHeaderTemplate)
  drawerForm.apiCredential = ''
  drawerForm.forumBoards = target.categoryCode === 'forum'
    ? parseForumBoards(site.contentConstraints)
    : []
}

function resolveContentStyle(target?: TargetConfig) {
  if (!target) return 'zhihu'
  if (target.categoryCode === 'agent_site') return 'agent_site_article'
  if (target.categoryCode === 'industry_site') return 'industry_site'
  if (target.categoryCode === 'authority_media') return 'authority_media'
  if (target.categoryCode === 'forum') return 'forum'
  return target.code
}

function toIndustryTarget(site: PublishSite): TargetConfig {
  const enabled = site.status === 'active'
  const autoPublish = enabled && site.integrationMethod === 'rest_api'
  const industryLabel = parseIndustryTags(site.industryTags).join(' / ') || '未配置'
  return {
    categoryCode: 'industry_site',
    name: site.siteName,
    code: site.siteCode || normalizeSiteCode(site),
    logoText: site.siteName?.trim()?.charAt(0) || '资',
    logoClass: 'news',
    enabled,
    autoPublish,
    executor: autoPublish ? 'industry_site' : '',
    drawerType: 'news',
    source: 'publish_site',
    publishSite: site,
    fields: [
      { label: '所属行业', value: industryLabel },
      { label: '状态', value: enabled ? '启用' : '已停用', badge: enabled ? 'success' : 'danger' },
      { label: '自动发布', value: autoPublish ? '已开启' : '已关闭', badge: autoPublish ? 'accent' : 'warning' },
      { label: '执行器', value: autoPublish ? '行业资讯站发布器' : '-', mono: !autoPublish },
      { label: '域名', value: site.domain || '-', mono: true },
      { label: '接入方式', value: integrationMethodLabel(site.integrationMethod) },
    ],
  }
}

function toForumTarget(site: PublishSite): TargetConfig {
  const enabled = site.status === 'active'
  return {
    categoryCode: 'forum',
    name: site.siteName,
    code: site.siteCode || normalizeSiteCode(site),
    logoText: site.siteName?.trim()?.charAt(0) || '坛',
    logoClass: 'forum',
    enabled,
    autoPublish: enabled && (site.integrationMethod === 'forum_playwright' || site.integrationMethod === 'discuz_http'),
    executor: site.integrationMethod || '',
    drawerType: site.siteCode || normalizeSiteCode(site),
    publishSite: site,
    fields: [
      { label: '接入方式', value: integrationMethodLabel(site.integrationMethod) },
      { label: '站点域名', value: site.domain || '-' },
      { label: '健康状态', value: forumHealthLabel(site.currentHealthStatus), badge: site.currentHealthStatus === 'degraded' ? 'warning' : 'success' },
      { label: '账号状态', value: site.currentHealthStatus === 'degraded' ? '登录信息可能过期' : '正常', badge: site.currentHealthStatus === 'degraded' ? 'warning' : 'success' },
    ],
  }
}

function toAgentTarget(site: PublishSite): TargetConfig {
  const enabled = site.status === 'active'
  return {
    categoryCode: 'agent_site',
    name: site.siteName,
    code: site.siteCode || 'agent_official_site',
    logoText: 'A',
    logoClass: 'agent',
    enabled,
    autoPublish: enabled,
    executor: 'agent_site_publisher',
    drawerType: 'agent',
    source: 'publish_site',
    publishSite: site,
    fields: [
      { label: '内容风格', value: 'Agent 官网文章' },
      { label: '站点唯一标识', value: site.siteCode || '-', mono: true },
      { label: '状态', value: enabled ? '启用' : '已停用', badge: enabled ? 'success' : 'danger' },
      { label: '自动发布', value: enabled ? '已开启' : '已关闭', badge: enabled ? 'accent' : 'warning' },
      { label: '执行器', value: 'Agent 官网发布器' },
      { label: '默认间隔', value: '20 分钟', mono: true },
      { label: '并发上限', value: '2', mono: true },
    ],
  }
}

function normalizeSiteCode(site: PublishSite) {
  return (site.domain || site.siteName || `publish_site_${site.id}`)
    .trim()
    .toLowerCase()
    .replace(/^https?:\/\//, '')
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '') || `publish_site_${site.id}`
}

function parseIndustryTags(raw?: string | string[] | null) {
  if (Array.isArray(raw)) return raw
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.map((item) => String(item)) : []
  } catch {
    return raw.split(/[,，/]/).map((item) => item.trim()).filter(Boolean)
  }
}

function parseIndustryTagsInput(raw: string) {
  return raw.split(/[,，/]/).map((item) => item.trim()).filter(Boolean)
}

function formatHeaders(raw?: string | null) {
  if (!raw) return '{\n  "X-Admin-Token": ""\n}'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function normalizeHeaders(raw: string) {
  const text = raw.trim()
  if (!text) return undefined
  try {
    return JSON.stringify(JSON.parse(text))
  } catch {
    return text
  }
}

function normalizeCredential(raw: string) {
  const text = raw.trim()
  if (!text) return undefined
  try {
    return JSON.stringify(JSON.parse(text))
  } catch {
    return text
  }
}

function emptyForumBoard(defaultBoard = false): ForumBoardConfig {
  return {
    fid: null,
    name: '',
    enabled: true,
    default: defaultBoard,
  }
}

function parseForumBoards(raw?: string | null): ForumBoardConfig[] {
  if (!raw) return [emptyForumBoard(true)]
  try {
    const parsed = JSON.parse(raw)
    const boards = Array.isArray(parsed?.boards) ? parsed.boards : []
    const normalized = boards.map((board: any) => ({
      fid: Number.isFinite(Number(board?.fid)) ? Number(board.fid) : null,
      name: String(board?.name || ''),
      enabled: board?.enabled !== false,
      default: board?.default === true,
    }))
    return normalized.length ? ensureSingleDefaultBoard(normalized) : [emptyForumBoard(true)]
  } catch {
    return [emptyForumBoard(true)]
  }
}

function ensureSingleDefaultBoard(boards: ForumBoardConfig[]) {
  let defaultSeen = false
  const normalized = boards.map((board) => {
    const isDefault = board.default && !defaultSeen
    if (isDefault) defaultSeen = true
    return {
      ...board,
      default: isDefault,
    }
  })
  if (!defaultSeen && normalized.length) {
    const firstEnabled = normalized.find((board) => board.enabled)
    const defaultBoard = firstEnabled || normalized[0]
    defaultBoard.default = true
  }
  return normalized
}

function buildForumContentConstraints() {
  const boards = drawerForm.forumBoards
    .map((board) => ({
      fid: Number(board.fid),
      name: board.name.trim(),
      enabled: board.enabled,
      default: board.default,
    }))
  return JSON.stringify({
    baseUrl: normalizeForumBaseUrl(drawerForm.domain || drawerForm.apiEndpoint),
    boards,
    requestTimeoutMs: 30000,
    successUrlRegex: '(thread|forum)-\\d+',
  })
}

function normalizeForumBaseUrl(raw: string) {
  const value = raw.trim()
  if (!value) return ''
  const normalized = value.startsWith('http://') || value.startsWith('https://') ? value : `https://${value}`
  const forumIndex = normalized.indexOf('/forum.php')
  const base = forumIndex > 0 ? normalized.slice(0, forumIndex + 1) : normalized
  return base.endsWith('/') ? base : `${base}/`
}

function addForumBoard() {
  drawerForm.forumBoards.push(emptyForumBoard(!drawerForm.forumBoards.some((board) => board.default)))
}

function removeForumBoard(index: number) {
  drawerForm.forumBoards.splice(index, 1)
  if (!drawerForm.forumBoards.length) {
    drawerForm.forumBoards.push(emptyForumBoard(true))
    return
  }
  if (!drawerForm.forumBoards.some((board) => board.default)) {
    drawerForm.forumBoards[0].default = true
  }
}

function setDefaultForumBoard(index: number) {
  drawerForm.forumBoards.forEach((board, boardIndex) => {
    board.default = boardIndex === index
  })
}

function integrationMethodLabel(v?: string | null) {
  if (v === 'rest_api') return 'REST API'
  if (v === 'discuz_http') return 'Discuz HTTP 直发'
  if (v === 'forum_playwright') return '平台网站浏览器自动化'
  if (v === 'manual') return '手动发布'
  if (v === 'ftp') return 'FTP'
  if (v === 'email') return '邮件'
  return v || '-'
}

function forumHealthLabel(status?: string | null) {
  if (status === 'degraded') return '登录信息异常'
  if (status === 'slow') return '响应慢'
  if (status === 'high_failure') return '失败率高'
  return '正常'
}

async function loadPublishSites() {
  publishSiteLoading.value = true
  try {
    const { data } = await getPublishSites()
    publishSites.value = data.data || []
  } catch {
    publishSites.value = []
    ElMessage.error('加载行业资讯站失败')
  } finally {
    publishSiteLoading.value = false
  }
}

async function toggleTarget(target: TargetConfig) {
  if (target.locked || target.updating) return
  if (target.source !== 'publish_site') {
    target.enabled = !target.enabled
    return
  }
  const siteId = target.publishSite?.id
  if (!siteId) return
  target.updating = true
  try {
    await updatePublishSiteStatus(siteId, target.enabled ? 'suspended' : 'active')
    ElMessage.success('状态已更新')
    await loadPublishSites()
  } finally {
    target.updating = false
  }
}

async function testIndustrySite(target: TargetConfig) {
  const siteId = target.publishSite?.id
  if (!siteId) return
  const { data } = await testPublishSite(siteId)
  if (data.data?.success) {
    ElMessage.success(`连通测试成功，耗时 ${data.data.elapsedMs ?? '-'}ms`)
  } else {
    ElMessage.warning(`连通测试失败：${data.data?.message || '无法连接'}`)
  }
}

function validateIndustrySiteForm() {
  if (!drawerForm.name.trim()) {
    ElMessage.warning('请输入平台名称')
    return false
  }
  if (!drawerForm.code.trim()) {
    ElMessage.warning('请输入平台标识')
    return false
  }
  if (!/^[a-z0-9][a-z0-9_-]{1,127}$/.test(drawerForm.code.trim())) {
    ElMessage.warning('平台标识需为 2-128 位小写字母、数字、下划线或短横线，并以字母或数字开头')
    return false
  }
  if (!drawerForm.domain.trim()) {
    ElMessage.warning('请输入站点域名')
    return false
  }
  if ((drawerForm.integrationMethod === 'rest_api' || drawerForm.integrationMethod === 'forum_playwright' || drawerForm.integrationMethod === 'discuz_http') && !drawerForm.apiEndpoint.trim()) {
    ElMessage.warning(drawerForm.categoryCode === 'forum' ? '请输入发帖页面 URL' : '请输入发布接口 URL')
    return false
  }
  if (drawerForm.categoryCode === 'forum' && drawerForm.apiCredential.trim()) {
    try {
      JSON.parse(drawerForm.apiCredential)
    } catch {
      ElMessage.warning('账号 / Cookie 配置必须是合法 JSON')
      return false
    }
  }
  if (drawerForm.categoryCode === 'forum' && drawerForm.integrationMethod === 'discuz_http') {
    const seen = new Set<number>()
    let enabledCount = 0
    let defaultCount = 0
    for (const board of drawerForm.forumBoards) {
      if (!board.name.trim()) {
        ElMessage.warning('请输入版块名称')
        return false
      }
      const fid = Number(board.fid)
      if (!Number.isInteger(fid) || fid <= 0) {
        ElMessage.warning('版块 fid 必须是正整数')
        return false
      }
      if (seen.has(fid)) {
        ElMessage.warning(`版块 fid 重复：${fid}`)
        return false
      }
      seen.add(fid)
      if (board.enabled) enabledCount += 1
      if (board.default) defaultCount += 1
    }
    if (enabledCount === 0) {
      ElMessage.warning('至少启用一个版块')
      return false
    }
    if (defaultCount > 1) {
      ElMessage.warning('只能设置一个默认版块')
      return false
    }
  }
  if (drawerForm.categoryCode !== 'forum' && !parseIndustryTagsInput(drawerForm.industryTags).length) {
    ElMessage.warning('请输入至少一个行业分类')
    return false
  }
  return true
}

function validateAgentSiteForm() {
  if (!drawerForm.name.trim()) {
    ElMessage.warning('请输入平台名称')
    return false
  }
  if (!drawerForm.code.trim()) {
    ElMessage.warning('请输入站点唯一标识')
    return false
  }
  if (!/^[a-z0-9][a-z0-9_-]{1,127}$/.test(drawerForm.code.trim())) {
    ElMessage.warning('站点唯一标识需为 2-128 位小写字母、数字、下划线或短横线，并以字母或数字开头')
    return false
  }
  return true
}

function buildPublishSitePayload() {
  return {
    siteName: drawerForm.name.trim(),
    siteCode: drawerForm.code.trim(),
    domain: drawerForm.domain.trim(),
    iconUrl: drawerForm.iconUrl.trim() || undefined,
    industryTags: parseIndustryTagsInput(drawerForm.industryTags),
    tier: 'S2',
    status: drawerForm.enabled ? 'active' : 'suspended',
    integrationMethod: drawerForm.integrationMethod,
    currentHealthStatus: 'normal',
    apiEndpoint: drawerForm.apiEndpoint.trim(),
    httpMethod: 'POST',
    requestHeaderTemplate: normalizeHeaders(drawerForm.headers),
    requestBodyTemplate: '{"title":"{{title}}","content":"{{content}}","contentMarkdown":"{{contentMarkdown}}","contentHtml":"{{contentHtml}}","author":"{{author}}"}',
    apiCredential: drawerForm.categoryCode === 'forum' ? normalizeCredential(drawerForm.apiCredential) : undefined,
    authType: drawerForm.categoryCode === 'forum' ? 'account_cookie' : undefined,
    contentConstraints: drawerForm.categoryCode === 'forum' && drawerForm.integrationMethod === 'discuz_http'
      ? buildForumContentConstraints()
      : undefined,
    remark: drawerForm.remark.trim() || undefined,
  }
}

function buildAgentSitePayload() {
  return {
    siteName: drawerForm.name.trim() || 'Agent 官网',
    siteCode: drawerForm.code.trim(),
    domain: drawerForm.domain.trim() || 'agent-site.local',
    industryTags: ['general'],
    tier: 'S0',
    status: drawerForm.enabled ? 'active' : 'suspended',
    integrationMethod: 'brand_geo_site',
    currentHealthStatus: 'normal',
    remark: drawerForm.remark.trim() || 'Agent 官网自动发布目标',
  }
}

async function submitDrawer() {
  if (drawerForm.categoryCode === 'agent_site') {
    if (!validateAgentSiteForm()) return
    saving.value = true
    try {
      const payload = buildAgentSitePayload()
      if (editingPublishSiteId.value) {
        await updatePublishSite(editingPublishSiteId.value, payload)
      } else {
        await createPublishSite(payload)
      }
      ElMessage.success('保存成功')
      closeDrawer()
      await loadPublishSites()
    } finally {
      saving.value = false
    }
    return
  }
  if (drawerForm.categoryCode !== 'industry_site' && drawerForm.categoryCode !== 'forum') {
    closeDrawer()
    return
  }
  if (!validateIndustrySiteForm()) return
  saving.value = true
  try {
    const payload = buildPublishSitePayload()
    if (editingPublishSiteId.value) {
      await updatePublishSite(editingPublishSiteId.value, payload)
    } else {
      await createPublishSite(payload)
    }
    ElMessage.success('保存成功')
    closeDrawer()
    await loadPublishSites()
  } finally {
    saving.value = false
  }
}

function closeDrawer() {
  drawerVisible.value = false
}

onMounted(() => {
  loadPublishSites()
})
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&family=Noto+Sans+SC:wght@400;500;600;700&display=swap');

.publish-platform-page {
  --bg: oklch(98.5% 0.003 240);
  --bg-elev: oklch(100% 0 0);
  --bg-subtle: oklch(96% 0.004 240);
  --border: oklch(92% 0.006 240);
  --border-strong: oklch(86% 0.008 240);
  --border-subtle: oklch(95% 0.004 240);
  --text: oklch(22% 0.015 250);
  --text-muted: oklch(48% 0.012 245);
  --text-faint: oklch(65% 0.01 245);
  --accent: oklch(45% 0.14 255);
  --accent-hover: oklch(40% 0.16 255);
  --accent-soft: oklch(95% 0.025 255);
  --accent-text: oklch(35% 0.16 255);
  --success: oklch(55% 0.13 155);
  --success-soft: oklch(95% 0.03 155);
  --warning-soft: oklch(96% 0.04 80);
  --danger: oklch(58% 0.18 25);
  --danger-soft: oklch(96% 0.025 25);
  --neutral-soft: oklch(94% 0.005 240);
  --font-sans: 'Manrope', 'Noto Sans SC', system-ui, sans-serif;
  --font-mono: 'JetBrains Mono', ui-monospace, monospace;
  --shadow-xs: 0 1px 2px 0 oklch(20% 0.02 250 / 0.04);
  --shadow-md: 0 4px 12px -2px oklch(20% 0.02 250 / 0.08), 0 2px 4px -2px oklch(20% 0.02 250 / 0.05);
  --r-md: 8px;
  --r-lg: 10px;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: calc(100vh - var(--header-height) - 40px);
  min-height: 0;
  margin: 0;
  overflow: hidden;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  color: var(--text);
  font-family: var(--font-sans);
  font-size: 14px;
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
  font-feature-settings: 'cv11', 'ss01';
}

.publish-platform-page *,
.publish-platform-page *::before,
.publish-platform-page *::after {
  box-sizing: border-box;
}

button,
input,
select,
textarea {
  font: inherit;
}

.page-header {
  flex-shrink: 0;
  padding: 24px 28px 20px;
  background: var(--bg-elev);
  border-bottom: 1px solid var(--border);
}

.page-title-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.page-title-block {
  min-width: 0;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0;
}

.page-title-tag {
  padding: 3px 7px;
  border-radius: 4px;
  background: var(--neutral-soft);
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.page-desc {
  max-width: 720px;
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.page-actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  max-width: 540px;
}

.search {
  position: relative;
  width: 240px;
}

.search :deep(.el-icon) {
  position: absolute;
  top: 50%;
  left: 11px;
  z-index: 1;
  width: 14px;
  height: 14px;
  color: var(--text-faint);
  transform: translateY(-50%);
}

.search input {
  width: 100%;
  height: 34px;
  padding: 0 12px 0 34px;
  color: var(--text);
  background: var(--bg-elev);
  border: 1px solid var(--border-strong);
  border-radius: var(--r-md);
  outline: none;
  transition: all 0.15s;
}

.search input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: var(--r-md);
  transition: all 0.15s;
}

.btn :deep(.el-icon) {
  width: 14px;
  height: 14px;
}

.btn-primary {
  color: #fff;
  background: var(--accent);
  box-shadow: var(--shadow-xs);
}

.btn-primary:hover {
  background: var(--accent-hover);
}

.btn-ghost {
  background: var(--bg-elev);
  border-color: var(--border-strong);
}

.btn-ghost:hover {
  background: var(--bg-subtle);
}

.btn-sm {
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
}

.btn-link,
.btn-danger-link {
  height: auto;
  padding: 0 6px;
  background: transparent;
}

.btn-link {
  color: var(--accent-text);
}

.btn-danger-link {
  color: var(--danger);
}

.btn-link:hover,
.btn-danger-link:hover {
  text-decoration: underline;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  overflow: hidden;
  background: var(--border-subtle);
  border: 1px solid var(--border-subtle);
  border-radius: var(--r-lg);
}

.stat {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  background: var(--bg-elev);
}

.stat-label {
  margin-bottom: 6px;
  color: var(--text-muted);
  font-size: 11.5px;
  font-weight: 500;
  letter-spacing: 0.04em;
}

.stat-value {
  display: flex;
  align-items: baseline;
  gap: 6px;
  color: var(--stat-accent, var(--text));
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0;
}

.stat-value .unit {
  color: color-mix(in oklch, var(--stat-accent, var(--text-faint)) 62%, var(--text-faint));
  font-size: 12px;
  font-weight: 650;
}

.stat-blue {
  --stat-accent: var(--accent-text);
}

.stat-indigo {
  --stat-accent: oklch(52% 0.16 275);
}

.stat-green {
  --stat-accent: var(--success);
}

.stat-amber {
  --stat-accent: oklch(50% 0.14 75);
}

.stat-trend {
  margin-top: 6px;
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 11px;
}

.stat-icon {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--r-md);
}

.stat-icon.blue {
  color: var(--accent-text);
  background: var(--accent-soft);
}

.stat-icon.green {
  color: var(--success);
  background: var(--success-soft);
}

.stat-icon.amber {
  color: oklch(50% 0.14 75);
  background: var(--warning-soft);
}

.stat-icon.gray {
  color: var(--text-muted);
  background: var(--neutral-soft);
}

.content {
  display: grid;
  grid-template-columns: 280px 1fr;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.category-pane {
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  background: var(--bg-elev);
  border-right: 1px solid var(--border);
}

.pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 12px;
  border-bottom: 1px solid var(--border-subtle);
}

.pane-title {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.pane-count {
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 11px;
}

.cat-list {
  flex: 1;
  padding: 10px;
}

.cat-item {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  gap: 12px;
  margin-bottom: 2px;
  padding: 12px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--r-md);
  transition: all 0.15s;
}

.cat-item:hover {
  background: var(--bg-subtle);
}

.cat-item.active {
  background: var(--accent-soft);
}

.cat-item.active::before {
  position: absolute;
  top: 14px;
  bottom: 14px;
  left: 0;
  width: 3px;
  content: '';
  background: var(--accent);
  border-radius: 0 3px 3px 0;
}

.cat-icon {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 36px;
  height: 36px;
  color: var(--text-muted);
  background: var(--bg-subtle);
  border-radius: 8px;
}

.cat-item.active .cat-icon {
  color: #fff;
  background: var(--accent);
}

.cat-meta {
  flex: 1;
  min-width: 0;
}

.cat-name,
.cat-desc {
  display: block;
}

.cat-name {
  margin-bottom: 2px;
  color: var(--text);
  font-size: 13.5px;
  font-weight: 600;
  line-height: 1.3;
}

.cat-item.active .cat-name {
  color: var(--accent-text);
}

.cat-desc {
  overflow: hidden;
  color: var(--text-faint);
  font-size: 11.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cat-badge {
  flex-shrink: 0;
  padding: 2px 7px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  background: var(--bg-subtle);
  border-radius: 10px;
}

.cat-item.active .cat-badge {
  color: #fff;
  background: var(--accent);
}

.target-pane {
  overflow-y: auto;
  padding: 20px 28px 32px;
}

.target-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
  margin-bottom: 20px;
  border-bottom: 1px dashed var(--border-strong);
}

.target-header-text {
  flex: 1;
  min-width: 0;
}

.target-h {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.target-title {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0;
}

.target-meta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  background: var(--bg-subtle);
  border-radius: 4px;
}

.target-meta .dot {
  width: 5px;
  height: 5px;
  background: var(--text-muted);
  border-radius: 50%;
}

.target-desc {
  max-width: 640px;
  color: var(--text-muted);
  font-size: 12.5px;
}

.notice {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  margin-bottom: 18px;
  color: oklch(35% 0.1 60);
  font-size: 12.5px;
  background: var(--warning-soft);
  border: 1px solid oklch(88% 0.06 80);
  border-radius: var(--r-md);
}

.notice.info {
  color: var(--accent-text);
  background: var(--accent-soft);
  border-color: oklch(86% 0.04 255);
}

.notice :deep(.el-icon) {
  flex-shrink: 0;
  margin-top: 2px;
}

.target-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 14px;
}

.target-card {
  position: relative;
  padding: 16px 18px;
  overflow: hidden;
  background: var(--bg-elev);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  transition: all 0.2s ease;
}

.target-card:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.target-card.disabled {
  background: var(--bg-subtle);
  opacity: 0.85;
}

.target-card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

.target-name-block {
  display: flex;
  flex: 1;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.platform-logo {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 36px;
  height: 36px;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  border-radius: 8px;
  box-shadow: var(--shadow-xs);
}

.platform-logo.toutiao {
  background: linear-gradient(135deg, #ff4d4f, #c41d1f);
}

.platform-logo.zhihu {
  background: linear-gradient(135deg, #1772f6, #0d5cd0);
}

.platform-logo.wechat {
  background: linear-gradient(135deg, #2dba4e, #1a8a3a);
}

.platform-logo.douyin {
  background: linear-gradient(135deg, #25f4ee 0%, #fe2c55 100%);
}

.platform-logo.agent {
  background: linear-gradient(135deg, oklch(45% 0.14 255), oklch(35% 0.18 280));
}

.platform-logo.news {
  background: linear-gradient(135deg, oklch(55% 0.13 30), oklch(45% 0.16 20));
}

.platform-logo.media {
  background: linear-gradient(135deg, oklch(35% 0.05 250), oklch(25% 0.04 250));
}

.platform-logo.forum {
  background: linear-gradient(135deg, oklch(60% 0.12 180), oklch(50% 0.14 200));
}

.target-name {
  margin-bottom: 2px;
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.target-id {
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 11px;
}

.switch {
  position: relative;
  display: inline-block;
  flex-shrink: 0;
  width: 32px;
  height: 18px;
}

.switch input {
  width: 0;
  height: 0;
  opacity: 0;
}

.slider {
  position: absolute;
  inset: 0;
  cursor: pointer;
  background: var(--border-strong);
  border-radius: 18px;
  transition: 0.2s;
}

.slider::before {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  content: '';
  background: #fff;
  border-radius: 50%;
  box-shadow: var(--shadow-xs);
  transition: 0.2s;
}

.switch input:checked + .slider {
  background: var(--accent);
}

.switch input:checked + .slider::before {
  transform: translateX(14px);
}

.switch.disabled .slider {
  cursor: not-allowed;
  opacity: 0.5;
}

.target-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
  padding: 12px 0;
  margin-bottom: 12px;
  border-top: 1px solid var(--border-subtle);
  border-bottom: 1px solid var(--border-subtle);
}

.field {
  min-width: 0;
}

.field-label {
  margin-bottom: 3px;
  color: var(--text-faint);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.field-value {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  color: var(--text);
  font-size: 12.5px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.field-value.wrap {
  white-space: normal;
}

.field-value.mono {
  font-family: var(--font-mono);
  font-size: 12px;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 7px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
  border-radius: 10px;
}

.badge .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.badge.success {
  color: oklch(38% 0.13 155);
  background: var(--success-soft);
}

.badge.success .dot {
  background: var(--success);
}

.badge.danger {
  color: var(--danger);
  background: var(--danger-soft);
}

.badge.danger .dot {
  background: var(--danger);
}

.badge.warning {
  color: oklch(45% 0.13 60);
  background: var(--warning-soft);
}

.badge.warning .dot {
  background: oklch(60% 0.15 70);
}

.badge.neutral {
  color: var(--text-muted);
  background: var(--neutral-soft);
}

.badge.neutral .dot {
  background: var(--text-faint);
}

.badge.accent {
  color: var(--accent-text);
  background: var(--accent-soft);
}

.badge.accent .dot {
  background: var(--accent);
}

.target-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}

.disabled-action {
  cursor: not-allowed;
  opacity: 0.5;
}

.drawer-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  pointer-events: none;
  background: oklch(15% 0.02 250 / 0.35);
  opacity: 0;
  backdrop-filter: blur(2px);
  transition: opacity 0.2s;
}

.drawer-overlay.open {
  pointer-events: auto;
  opacity: 1;
}

.drawer {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 60;
  display: flex;
  flex-direction: column;
  width: 480px;
  height: 100vh;
  background: var(--bg-elev);
  box-shadow: -8px 0 32px -8px oklch(20% 0.02 250 / 0.2);
  transform: translateX(100%);
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.drawer.open {
  transform: translateX(0);
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid var(--border);
}

.drawer-title {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0;
}

.drawer-subtitle {
  margin-top: 2px;
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 11.5px;
}

.drawer-close {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  color: var(--text-muted);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--r-md);
}

.drawer-close:hover {
  background: var(--bg-subtle);
}

.drawer-body {
  flex: 1;
  padding: 20px 24px;
  overflow-y: auto;
}

.drawer-section + .drawer-section {
  margin-top: 22px;
}

.form-section-label {
  padding-bottom: 8px;
  margin-bottom: 10px;
  color: var(--text-faint);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  border-bottom: 1px dashed var(--border);
}

.form-row {
  min-width: 0;
  margin-bottom: 14px;
}

.form-row label {
  display: block;
  margin-bottom: 6px;
  color: var(--text);
  font-size: 12.5px;
  font-weight: 600;
}

.hint {
  margin-top: 4px;
  color: var(--text-faint);
  font-size: 11.5px;
  line-height: 1.5;
}

.input,
.select,
.textarea {
  box-sizing: border-box;
  width: 100%;
  height: 34px;
  padding: 0 11px;
  color: var(--text);
  background: var(--bg-elev);
  border: 1px solid var(--border-strong);
  border-radius: var(--r-md);
  outline: none;
  transition: all 0.15s;
}

.textarea {
  height: auto;
  min-height: 72px;
  padding: 9px 11px;
  line-height: 1.5;
  resize: vertical;
}

.input:focus,
.select:focus,
.textarea:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.input.mono {
  font-family: var(--font-mono);
  font-size: 12.5px;
}

.forum-board-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.forum-board-header label {
  margin-bottom: 0;
}

.forum-board-table {
  display: grid;
  gap: 6px;
}

.forum-board-row {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(96px, 0.7fr) 56px 56px 36px;
  gap: 8px;
  align-items: center;
}

.forum-board-row-head {
  color: var(--text-faint);
  font-size: 11.5px;
  font-weight: 700;
}

.checkbox-cell {
  display: grid !important;
  place-items: center;
  margin: 0 !important;
}

.switch.compact {
  margin: 0 auto;
}

.icon-action {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  color: var(--text-muted);
  cursor: pointer;
  background: var(--bg-elev);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
}

.icon-action.danger:hover {
  color: var(--danger);
  border-color: color-mix(in srgb, var(--danger) 40%, var(--border));
}

.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  margin-bottom: 8px;
  background: var(--bg-elev);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
}

.toggle-meta {
  min-width: 0;
}

.toggle-title {
  margin-bottom: 2px;
  font-size: 13px;
  font-weight: 600;
}

.toggle-desc {
  max-width: 320px;
  color: var(--text-muted);
  font-size: 11.5px;
  line-height: 1.5;
}

.drawer-executor-row {
  margin-top: 12px;
}

.form-grid-2 {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
}

.interval-input {
  display: flex;
  gap: 6px;
}

.interval-input .input {
  flex: 1;
}

.unit-select {
  width: 96px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 24px;
  background: var(--bg-subtle);
  border-top: 1px solid var(--border);
}

.fade-in {
  animation: fade-up 0.4s ease both;
}

@keyframes fade-up {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1100px) {
  .page-title-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .page-actions {
    width: 100%;
  }

  .search {
    flex: 1;
  }

  .stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .content {
    grid-template-columns: 240px 1fr;
  }
}

@media (max-width: 820px) {
  .publish-platform-page {
    height: auto;
    min-height: 100vh;
    overflow: auto;
  }

  .content {
    display: block;
    overflow: visible;
  }

  .category-pane {
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }

  .cat-list {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
  }

  .target-pane {
    overflow: visible;
  }

  .stats,
  .target-grid,
  .form-grid-2 {
    grid-template-columns: 1fr;
  }

  .drawer {
    width: min(100vw, 480px);
  }
}
</style>
