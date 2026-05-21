<template>
  <div class="prompt-template-page">
    <header class="page-hero">
      <div>
        <p class="eyebrow">Prompt Template Center</p>
        <h1>文章提示词模板</h1>
        <p class="hero-desc">按分发渠道维护生成文章的提示词模板，批量生成时会按模板权重自动分配，也支持运营手动指定模板数量。权重范围为 0-100，0 表示不参与自动分配。</p>
      </div>
      <div class="hero-actions">
        <div class="hero-stat">
          <strong>{{ pagination.total }}</strong>
          <span>模板总数</span>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增模板</el-button>
      </div>
    </header>

    <section class="channel-panel">
      <div class="channel-tabs">
        <button
          class="channel-tab"
          :class="{ active: !filters.channelGroupCode }"
          type="button"
          @click="selectChannelGroup('')"
        >
          <span class="channel-icon">全</span>
          <span>
            <strong>全部渠道</strong>
            <small>按大类分组展示</small>
          </span>
        </button>
        <button
          v-for="group in channelTabGroups"
          :key="group.value"
          class="channel-tab"
          :class="{ active: filters.channelGroupCode === group.value }"
          type="button"
          @click="selectChannelGroup(group.value)"
        >
          <span class="channel-icon">{{ group.label.slice(0, 1) }}</span>
          <span>
            <strong>{{ group.label }}</strong>
            <small>{{ channelGroupDescription(group.value) }}</small>
          </span>
        </button>
      </div>
      <div v-if="currentSubOptions.length" class="sub-channel-row">
        <span>小类</span>
        <button
          class="sub-chip"
          :class="{ active: !filters.channelSubCode }"
          type="button"
          @click="selectSubChannel('')"
        >
          全部
        </button>
        <button
          v-for="item in currentSubOptions"
          :key="item.value"
          class="sub-chip"
          :class="{ active: filters.channelSubCode === item.value }"
          type="button"
          @click="selectSubChannel(item.value)"
        >
          {{ item.label }}
        </button>
      </div>
    </section>

    <section class="filter-bar">
      <div class="filter-main">
        <el-select v-model="filters.status" clearable placeholder="状态">
          <el-option label="草稿" value="draft" />
          <el-option label="启用" value="active" />
          <el-option label="停用" value="disabled" />
        </el-select>
      </div>
      <div class="filter-search">
        <el-input v-model="filters.keyword" clearable placeholder="搜索模板名称或说明" class="search-input" @keyup.enter="loadTemplates" />
        <el-button type="primary" plain :icon="Search" @click="loadTemplates">查询</el-button>
      </div>
    </section>

    <DataState :loading="loading" :empty="!loading && templates.length === 0" empty-text="暂无提示词模板">
      <div class="template-sections">
        <section v-for="section in groupedTemplateSections" :key="section.code" class="template-section">
          <div class="section-head">
            <div class="section-title">
              <span class="section-icon">{{ section.label.slice(0, 1) }}</span>
              <div>
                <h2>{{ section.label }}</h2>
                <p>{{ channelGroupDescription(section.code) }}</p>
              </div>
            </div>
            <span class="section-count">{{ section.items.length }} 个模板</span>
          </div>

          <div class="template-grid">
            <article
              v-for="item in section.items"
              :key="item.id"
              class="template-card"
              role="button"
              tabindex="0"
              @click="openDetail(item)"
              @keydown.enter.prevent="openDetail(item)"
            >
              <div class="card-top">
                <div class="template-title-wrap">
                  <div class="template-scope">{{ templateScopeLabel(item) }}</div>
                  <div class="template-title">{{ item.name }}</div>
                </div>
                <el-tag :type="statusTagType(item.status)" effect="light">{{ statusLabel(item.status) }}</el-tag>
              </div>
              <p class="template-desc">{{ item.description || '未填写模板说明' }}</p>
              <div class="template-facts">
                <span>{{ item.articleTypeName || articleTypeLabel(item.articleTypeCode) }}</span>
                <span>{{ contactModeLabel(item.contactDisclosureMode) }}</span>
                <span>版本 {{ item.currentVersionNo || '-' }}</span>
                <span>{{ formatDateTime(item.updatedAt) }}</span>
              </div>
              <div class="weight-row" @click.stop>
                <div>
                  <span>权重</span>
                  <small>范围 0-100。数字越大，自动生成时分配到的文章数越多；0 表示不参与自动分配</small>
                </div>
                <el-input-number
                  :model-value="item.weight"
                  :min="MIN_TEMPLATE_WEIGHT"
                  :max="MAX_TEMPLATE_WEIGHT"
                  :step="1"
                  step-strictly
                  size="small"
                  controls-position="right"
                  @change="(value: number | undefined) => confirmWeight(item, Number(value || 0))"
                />
              </div>
              <div class="card-actions">
                <el-button v-if="item.sampleOutputUrl" text type="success" @click.stop="openSample(item.sampleOutputUrl)">样文</el-button>
                <el-button text type="primary" @click.stop="openEdit(item)">编辑</el-button>
                <el-button text @click.stop="openVersion(item)">版本</el-button>
              </div>
            </article>
          </div>
        </section>
      </div>
    </DataState>

    <div class="pager-wrap">
      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        background
        layout="total, sizes, prev, pager, next"
        :total="pagination.total"
        @current-change="loadTemplates"
        @size-change="loadTemplates"
      />
    </div>

    <el-dialog
      v-model="editorVisible"
      :title="editingId ? '编辑提示词模板' : '新增提示词模板'"
      width="1080px"
      class="template-editor-dialog"
      destroy-on-close
    >
      <el-form label-position="top" class="template-form">
        <div class="form-grid">
          <el-form-item label="模板名称">
            <el-input v-model="form.name" maxlength="80" show-word-limit />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status">
              <el-option label="草稿" value="draft" />
              <el-option label="启用" value="active" />
              <el-option label="停用" value="disabled" />
            </el-select>
          </el-form-item>
          <el-form-item label="渠道大类">
            <el-select v-model="form.channelGroupCode" filterable allow-create default-first-option @change="handleFormGroupChange">
              <el-option v-for="group in formGroupOptions" :key="group.value" :label="group.label" :value="group.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="渠道小类">
            <el-select
              v-model="form.channelSubCode"
              clearable
              filterable
              allow-create
              default-first-option
              :disabled="form.channelGroupCode === 'agent_site' || form.channelGroupCode === 'industry_site' || form.channelGroupCode === 'forum'"
            >
              <el-option v-for="item in formSubOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.channelGroupCode === 'agent_site'" label="官网文章归属">
            <el-select v-model="form.agentSiteModule">
              <el-option label="FAQ" value="faq" />
              <el-option label="知识库" value="knowledge" />
              <el-option label="产品服务" value="product" />
            </el-select>
          </el-form-item>
          <el-form-item label="文章类型">
            <el-select v-model="form.articleTypeCode">
              <el-option v-for="item in articleTypes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="样文链接">
            <el-input v-model="form.sampleOutputUrl" clearable placeholder="可选，填写标杆样文 URL" />
          </el-form-item>
          <el-form-item label="联系方式露出">
            <el-select v-model="form.contactDisclosureMode">
              <el-option v-for="item in contactModes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <template #label>
              <span class="field-label">权重</span>
              <span class="field-help">取值 {{ MIN_TEMPLATE_WEIGHT }}-{{ MAX_TEMPLATE_WEIGHT }}，用于自动分配。数字越大分配越多，0 不参与。</span>
            </template>
            <el-input-number
              v-model="form.weight"
              :min="MIN_TEMPLATE_WEIGHT"
              :max="MAX_TEMPLATE_WEIGHT"
              :step="1"
              step-strictly
              controls-position="right"
            />
          </el-form-item>
        </div>
        <el-form-item label="模板说明">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="300" show-word-limit />
        </el-form-item>
        <div class="prompt-editor-grid">
          <el-form-item label="系统提示词">
            <el-input v-model="form.systemPrompt" class="prompt-textarea system-prompt" type="textarea" :rows="14" />
          </el-form-item>
          <el-form-item label="用户提示词模板">
            <el-input v-model="form.userPromptTemplate" class="prompt-textarea user-prompt" type="textarea" :rows="18" />
          </el-form-item>
        </div>
        <el-form-item label="变更说明">
          <el-input v-model="form.changeNote" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      title="模板详情"
      width="960px"
      class="template-detail-dialog"
      destroy-on-close
    >
      <DataState :loading="detailLoading" :empty="!detailLoading && !templateDetail">
        <div v-if="templateDetail" class="template-detail-panel">
          <div class="detail-summary">
            <div>
              <div class="detail-kicker">{{ templateScopeLabel(templateDetail) }}</div>
              <h3>{{ templateDetail.name }}</h3>
              <p>{{ templateDetail.description || '未填写模板说明' }}</p>
            </div>
            <el-tag :type="statusTagType(templateDetail.status)">{{ statusLabel(templateDetail.status) }}</el-tag>
          </div>
          <div class="detail-info-grid">
            <div><label>渠道大类</label><strong>{{ templateDetail.channelGroupName || channelGroupLabel(templateDetail.channelGroupCode) }}</strong></div>
            <div><label>渠道小类</label><strong>{{ templateDetail.channelSubName || templateDetail.channelSubCode || '-' }}</strong></div>
            <div><label>官网归属</label><strong>{{ templateDetail.agentSiteModule ? agentSiteModuleLabel(templateDetail.agentSiteModule) : '-' }}</strong></div>
            <div><label>文章类型</label><strong>{{ templateDetail.articleTypeName || articleTypeLabel(templateDetail.articleTypeCode) }}</strong></div>
            <div><label>联系方式露出</label><strong>{{ contactModeLabel(templateDetail.contactDisclosureMode) }}</strong></div>
            <div><label>权重</label><strong>{{ templateDetail.weight }}</strong></div>
            <div><label>当前版本</label><strong>{{ currentTemplateVersion(templateDetail)?.versionNo || '-' }}</strong></div>
            <div><label>更新时间</label><strong>{{ formatDateTime(templateDetail.updatedAt) }}</strong></div>
            <div class="detail-info-wide"><label>样文链接</label><strong>{{ templateDetail.sampleOutputUrl || '-' }}</strong></div>
          </div>
          <div class="detail-prompt-grid">
            <section>
              <h4>系统提示词</h4>
              <pre>{{ currentTemplateVersion(templateDetail)?.systemPrompt || '暂无系统提示词' }}</pre>
            </section>
            <section>
              <h4>用户提示词模板</h4>
              <pre>{{ currentTemplateVersion(templateDetail)?.userPromptTemplate || '暂无用户提示词模板' }}</pre>
            </section>
          </div>
        </div>
      </DataState>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="templateDetail?.sampleOutputUrl" type="success" plain @click="openSample(templateDetail.sampleOutputUrl)">查看样文</el-button>
        <el-button v-if="templateDetail" @click="openVersionFromDetail">查看版本</el-button>
        <el-button v-if="templateDetail" type="primary" @click="openEditFromDetail">编辑</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="versionVisible"
      title="模板版本"
      width="860px"
      class="version-dialog"
      destroy-on-close
    >
      <DataState :loading="detailLoading" :empty="!detailLoading && !templateDetail">
        <div v-if="templateDetail" class="version-panel">
          <div class="version-title">
            <div>
              <strong>{{ templateDetail.name }}</strong>
              <span>{{ templateDetail.channelGroupName || channelGroupLabel(templateDetail.channelGroupCode) }}</span>
            </div>
            <el-tag :type="statusTagType(templateDetail.status)">{{ statusLabel(templateDetail.status) }}</el-tag>
          </div>
          <div class="version-list">
            <article
              v-for="version in templateDetail.versions"
              :key="version.id"
              class="version-card"
            >
              <div class="version-card-head">
                <div>
                  <strong>版本 {{ version.versionNo }}</strong>
                  <span>{{ version.publishedAt || version.createdAt || '-' }}</span>
                </div>
                <el-tag size="small" :type="versionStatusTagType(version.status)">{{ versionStatusLabel(version.status) }}</el-tag>
              </div>
              <p>{{ version.changeNote || '未填写变更说明' }}</p>
              <el-button
                v-if="version.status === 'draft'"
                size="small"
                type="primary"
                plain
                :loading="publishing"
                @click="publishVersion(version.id)"
              >
                发布此版本
              </el-button>
            </article>
          </div>
        </div>
      </DataState>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import DataState from '@/components/ui/DataState.vue'
import {
  createArticlePromptTemplate,
  getArticlePromptTemplate,
  getArticlePromptTemplates,
  publishArticlePromptTemplateVersion,
  updateArticlePromptTemplate,
  updateArticlePromptTemplateWeight,
  type ArticlePromptTemplate,
  type ArticlePromptTemplateDetail,
  type ArticlePromptTemplateDetailResponse,
  type ArticlePromptTemplateSaveRequest,
} from '@/api/content'

const channelGroups = [
  { label: '官网', value: 'agent_site' },
  { label: '行业资讯站', value: 'industry_site' },
  { label: '自媒体平台', value: 'self_media' },
  { label: '权威媒体', value: 'authority_media' },
  { label: '论坛', value: 'forum' },
]
const channelGroupDescriptions: Record<string, string> = {
  agent_site: 'Agent 官网统一模板',
  industry_site: '垂直行业站通用模板',
  self_media: '公众号、知乎等平台风格',
  authority_media: '媒体稿件按类型区分',
  forum: '论坛讨论场景模板',
}
const subOptions: Record<string, Array<{ label: string; value: string }>> = {
  self_media: [
    { label: '今日头条', value: 'toutiao' },
    { label: '公众号', value: 'wechat' },
    { label: '知乎', value: 'zhihu' },
    { label: '抖音图文', value: 'douyin_image_text' },
    { label: '小红书', value: 'xiaohongshu' },
    { label: '百家号', value: 'baijiahao' },
  ],
  authority_media: [
    { label: '行业媒体', value: 'industry_media' },
    { label: '地方媒体', value: 'local_media' },
    { label: '财经媒体', value: 'finance_media' },
    { label: '科技媒体', value: 'tech_media' },
    { label: '新闻源媒体', value: 'news_source' },
    { label: '门户媒体', value: 'portal_media' },
  ],
}
const articleTypes = [
  { label: '问答文章', value: 'faq' },
  { label: '行业分析文', value: 'industry_article' },
  { label: '场景内容文', value: 'scenario_content' },
  { label: '阶段建议文', value: 'stage_advice' },
  { label: '选择指南', value: 'buying_guide' },
  { label: '对比评测', value: 'comparison' },
  { label: '费用解析', value: 'cost_analysis' },
  { label: '避坑指南', value: 'pitfall_guide' },
  { label: '经验笔记', value: 'social_note' },
  { label: '资讯简讯', value: 'news_brief' },
  { label: '讨论帖', value: 'forum_discussion' },
]
const contactModes = [
  { label: '完整露出（官网 / 电话 / 地址）', value: 'full' },
  { label: '软引导', value: 'soft_hint' },
  { label: '仅品牌名', value: 'brand_only' },
  { label: '不露出', value: 'none' },
]
const MIN_TEMPLATE_WEIGHT = 0
const MAX_TEMPLATE_WEIGHT = 100
const CHANNEL_CODE_PATTERN = /^[a-z][a-z0-9_]{1,63}$/

const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const detailLoading = ref(false)
const editorVisible = ref(false)
const detailVisible = ref(false)
const versionVisible = ref(false)
const templates = ref<ArticlePromptTemplate[]>([])
const templateDetail = ref<ArticlePromptTemplateDetail | null>(null)
const editingDetail = ref<ArticlePromptTemplateDetail | null>(null)
const editingId = ref<number | null>(null)
const route = useRoute()
const pagination = reactive({ current: 1, size: 12, total: 0 })
const filters = reactive({ channelGroupCode: '', channelSubCode: '', status: '', keyword: '' })
const form = reactive<ArticlePromptTemplateSaveRequest>({
  name: '',
  description: '',
  channelGroupCode: 'agent_site',
  channelSubCode: null,
  agentSiteModule: 'knowledge',
  articleTypeCode: 'industry_article',
  status: 'draft',
  weight: 1,
  sortOrder: 0,
  sampleOutputUrl: '',
  contactDisclosureMode: 'none',
  systemPrompt: '',
  userPromptTemplate: '',
  changeNote: '',
})

const formGroupOptions = computed(() => {
  const map = new Map(channelGroups.map((item) => [item.value, item]))
  templates.value.forEach((item) => {
    if (item.channelGroupCode && !map.has(item.channelGroupCode)) {
      map.set(item.channelGroupCode, { label: item.channelGroupName || item.channelGroupCode, value: item.channelGroupCode })
    }
  })
  if (form.channelGroupCode && !map.has(form.channelGroupCode)) {
    map.set(form.channelGroupCode, { label: form.channelGroupCode, value: form.channelGroupCode })
  }
  if (editingDetail.value?.channelGroupCode && !map.has(editingDetail.value.channelGroupCode)) {
    map.set(editingDetail.value.channelGroupCode, {
      label: editingDetail.value.channelGroupName || editingDetail.value.channelGroupCode,
      value: editingDetail.value.channelGroupCode,
    })
  }
  return Array.from(map.values())
})
const channelTabGroups = computed(() => {
  const map = new Map(channelGroups.map((item) => [item.value, item]))
  templates.value.forEach((item) => {
    if (item.channelGroupCode && !map.has(item.channelGroupCode)) {
      map.set(item.channelGroupCode, { label: item.channelGroupName || item.channelGroupCode, value: item.channelGroupCode })
    }
  })
  if (filters.channelGroupCode && !map.has(filters.channelGroupCode)) {
    map.set(filters.channelGroupCode, { label: filters.channelGroupCode, value: filters.channelGroupCode })
  }
  return Array.from(map.values())
})
const currentSubOptions = computed(() => mergedSubOptions(filters.channelGroupCode))
const formSubOptions = computed(() => {
  const options = mergedSubOptions(form.channelGroupCode)
  const detail = editingDetail.value
  if (detail?.channelGroupCode === form.channelGroupCode && detail.channelSubCode && !options.some((item) => item.value === detail.channelSubCode)) {
    options.unshift({ label: detail.channelSubName || detail.channelSubCode, value: detail.channelSubCode })
  }
  return options
})
const groupedTemplateSections = computed(() => {
  const groups = new Map<string, ArticlePromptTemplate[]>()
  templates.value.forEach((item) => {
    const code = item.channelGroupCode || 'unknown'
    groups.set(code, [...(groups.get(code) || []), item])
  })
  const orderedCodes = [
    ...channelGroups.map((item) => item.value).filter((code) => groups.has(code)),
    ...Array.from(groups.keys()).filter((code) => !channelGroups.some((item) => item.value === code)),
  ]
  return orderedCodes.map((code) => ({
    code,
    label: channelGroupLabel(code),
    items: groups.get(code) || [],
  }))
})

function channelGroupLabel(value: string) {
  return channelGroups.find((item) => item.value === value)?.label || value
}

function channelGroupDescription(value: string) {
  return channelGroupDescriptions[value] || '提示词模板'
}

function mergedSubOptions(groupCode: string) {
  const map = new Map((subOptions[groupCode] || []).map((item) => [item.value, item]))
  templates.value
    .filter((item) => item.channelGroupCode === groupCode && item.channelSubCode)
    .forEach((item) => {
      const value = item.channelSubCode!
      if (!map.has(value)) {
        map.set(value, { label: item.channelSubName || value, value })
      }
    })
  return Array.from(map.values())
}

function agentSiteModuleLabel(value: string) {
  return ({ faq: 'FAQ', knowledge: '知识库', product: '产品服务' } as Record<string, string>)[value] || value
}

function articleTypeLabel(value: string) {
  return articleTypes.find((item) => item.value === value)?.label || value
}

function contactModeLabel(value?: string | null) {
  return contactModes.find((item) => item.value === value)?.label || '不露出'
}

function statusLabel(value: string) {
  return ({ draft: '草稿', active: '启用', disabled: '停用' } as Record<string, string>)[value] || value
}

function statusTagType(value: string) {
  if (value === 'active') return 'success'
  if (value === 'disabled') return 'info'
  return 'warning'
}

function versionStatusLabel(value: string) {
  return ({ draft: '草稿', published: '已发布', archived: '已归档' } as Record<string, string>)[value] || value
}

function versionStatusTagType(value: string) {
  if (value === 'published') return 'success'
  if (value === 'archived') return 'info'
  return 'warning'
}

function formatDateTime(value?: string | null) {
  if (!value) return '未更新'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function isValidWeight(weight: number) {
  return Number.isInteger(weight) && weight >= MIN_TEMPLATE_WEIGHT && weight <= MAX_TEMPLATE_WEIGHT
}

function isValidChannelCode(value?: string | null) {
  return !!value && CHANNEL_CODE_PATTERN.test(value)
}

function queryStringValue(value: unknown) {
  if (Array.isArray(value)) return typeof value[0] === 'string' ? value[0] : ''
  return typeof value === 'string' ? value : ''
}

function applyRouteFilters() {
  const channelGroupCode = queryStringValue(route.query.channelGroupCode)
  const channelSubCode = queryStringValue(route.query.channelSubCode)
  const status = queryStringValue(route.query.status)
  const keyword = queryStringValue(route.query.keyword)
  if (channelGroupCode) filters.channelGroupCode = channelGroupCode
  if (channelSubCode) filters.channelSubCode = channelSubCode
  if (status) filters.status = status
  if (keyword) filters.keyword = keyword
  pagination.current = 1
}

function templateScopeLabel(item: ArticlePromptTemplate) {
  if (item.channelGroupCode === 'agent_site' && item.agentSiteModule) {
    return `官网模块 / ${agentSiteModuleLabel(item.agentSiteModule)}`
  }
  if (item.channelSubName) {
    return `小类 / ${item.channelSubName}`
  }
  if (item.channelSubCode) {
    return `小类 / ${item.channelSubCode}`
  }
  return '通用模板'
}

function selectChannelGroup(value: string) {
  filters.channelGroupCode = value
  filters.channelSubCode = ''
  pagination.current = 1
  loadTemplates()
}

function selectSubChannel(value: string) {
  filters.channelSubCode = value
  pagination.current = 1
  loadTemplates()
}

function openSample(url: string) {
  window.open(url, '_blank', 'noopener,noreferrer')
}

function currentTemplateVersion(detail: ArticlePromptTemplateDetail) {
  return detail.versions.find((version) => version.id === detail.currentVersionId) || detail.versions[0]
}

function normalizeTemplateDetail(response: ArticlePromptTemplateDetailResponse): ArticlePromptTemplateDetail {
  const versions = response.versions || []
  const currentVersion = response.currentVersion
  const allVersions = currentVersion && !versions.some((version) => version.id === currentVersion.id)
    ? [currentVersion, ...versions]
    : versions
  return {
    ...response.template,
    currentVersionId: response.template.currentVersionId || currentVersion?.id || null,
    currentVersionNo: response.template.currentVersionNo || currentVersion?.versionNo || null,
    versions: allVersions,
  }
}

function handleFormGroupChange() {
  form.channelSubCode = null
  form.agentSiteModule = form.channelGroupCode === 'agent_site' ? 'knowledge' : null
}

async function loadTemplates() {
  loading.value = true
  try {
    const { data } = await getArticlePromptTemplates({
      ...filters,
      current: pagination.current,
      size: pagination.size,
    })
    templates.value = data.data.records || []
    pagination.total = data.data.total || 0
  } catch (err) {
    console.error(err)
    ElMessage.error('加载提示词模板失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    name: '',
    description: '',
    channelGroupCode: 'agent_site',
    channelSubCode: null,
    agentSiteModule: 'knowledge',
    articleTypeCode: 'industry_article',
    status: 'draft',
    weight: 1,
    sortOrder: 0,
    sampleOutputUrl: '',
    contactDisclosureMode: 'none',
    systemPrompt: '你是专业 GEO 内容编辑。',
    userPromptTemplate: '请围绕“{{topicAsQuestion}}”生成一篇适合{{channelName}}发布的文章。',
    changeNote: '',
  })
}

function openCreate() {
  editingId.value = null
  editingDetail.value = null
  resetForm()
  editorVisible.value = true
}

function fillFormFromDetail(detail: ArticlePromptTemplateDetail) {
  const currentVersion = currentTemplateVersion(detail)
  Object.assign(form, {
    name: detail.name,
    description: detail.description || '',
    channelGroupCode: detail.channelGroupCode,
    channelSubCode: detail.channelSubCode || null,
    agentSiteModule: detail.channelGroupCode === 'agent_site' ? (detail.agentSiteModule || 'knowledge') : null,
    articleTypeCode: detail.articleTypeCode,
    status: detail.status,
    weight: detail.weight,
    sortOrder: detail.sortOrder,
    sampleOutputUrl: detail.sampleOutputUrl || '',
    contactDisclosureMode: detail.contactDisclosureMode || 'none',
    systemPrompt: currentVersion?.systemPrompt || '',
    userPromptTemplate: currentVersion?.userPromptTemplate || '',
    changeNote: '',
  })
}

async function loadTemplateDetail(templateId: number, failMessage: string) {
  detailLoading.value = true
  try {
    const { data } = await getArticlePromptTemplate(templateId)
    return normalizeTemplateDetail(data.data)
  } catch (err) {
    console.error(err)
    ElMessage.error(failMessage)
    return null
  } finally {
    detailLoading.value = false
  }
}

async function openDetail(item: ArticlePromptTemplate) {
  detailVisible.value = true
  templateDetail.value = null
  const detail = await loadTemplateDetail(item.id, '加载模板详情失败')
  if (detail) templateDetail.value = detail
}

async function openEdit(item: ArticlePromptTemplate) {
  editingId.value = item.id
  const detail = await loadTemplateDetail(item.id, '加载模板详情失败')
  if (!detail) return
  editingDetail.value = detail
  fillFormFromDetail(detail)
  editorVisible.value = true
}

function openEditFromDetail() {
  if (!templateDetail.value) return
  editingId.value = templateDetail.value.id
  editingDetail.value = templateDetail.value
  fillFormFromDetail(templateDetail.value)
  detailVisible.value = false
  editorVisible.value = true
}

async function saveTemplate() {
  if (!form.name.trim() || !form.systemPrompt.trim() || !form.userPromptTemplate.trim()) {
    ElMessage.warning('请填写模板名称和提示词内容')
    return
  }
  if (!isValidChannelCode(form.channelGroupCode)) {
    ElMessage.warning('渠道大类编码只能使用小写字母、数字、下划线，并以字母开头')
    return
  }
  if (form.channelSubCode && !isValidChannelCode(form.channelSubCode)) {
    ElMessage.warning('渠道小类编码只能使用小写字母、数字、下划线，并以字母开头')
    return
  }
  if (!isValidWeight(Number(form.weight))) {
    ElMessage.warning(`权重需为 ${MIN_TEMPLATE_WEIGHT}-${MAX_TEMPLATE_WEIGHT} 的整数`)
    return
  }
  if ((form.channelGroupCode === 'self_media' || form.channelGroupCode === 'authority_media') && !form.channelSubCode) {
    ElMessage.warning('请选择渠道小类')
    return
  }
  saving.value = true
  try {
    const payload = { ...form, channelSubCode: form.channelSubCode || null, agentSiteModule: form.channelGroupCode === 'agent_site' ? form.agentSiteModule : null }
    if (editingId.value) {
      await updateArticlePromptTemplate(editingId.value, payload)
    } else {
      await createArticlePromptTemplate(payload)
    }
    ElMessage.success('模板已保存')
    editorVisible.value = false
    loadTemplates()
  } catch (err) {
    console.error(err)
    ElMessage.error('保存模板失败')
  } finally {
    saving.value = false
  }
}

async function confirmWeight(item: ArticlePromptTemplate, weight: number) {
  if (!isValidWeight(weight)) {
    ElMessage.warning(`权重需为 ${MIN_TEMPLATE_WEIGHT}-${MAX_TEMPLATE_WEIGHT} 的整数`)
    loadTemplates()
    return
  }
  if (weight === item.weight) return
  try {
    await ElMessageBox.confirm(`确认将「${item.name}」权重调整为 ${weight}？`, '调整权重', {
      type: 'warning',
      confirmButtonText: '确认调整',
      cancelButtonText: '取消',
    })
    await updateArticlePromptTemplateWeight(item.id, { weight })
    ElMessage.success('权重已更新')
    loadTemplates()
  } catch {
    loadTemplates()
  }
}

async function openVersion(item: ArticlePromptTemplate) {
  await openVersionById(item.id)
}

async function openVersionFromDetail() {
  if (!templateDetail.value) return
  const templateId = templateDetail.value.id
  detailVisible.value = false
  await openVersionById(templateId)
}

async function openVersionById(templateId: number) {
  versionVisible.value = true
  templateDetail.value = null
  const detail = await loadTemplateDetail(templateId, '加载版本失败')
  if (detail) templateDetail.value = detail
}

async function publishVersion(versionId: number) {
  if (!templateDetail.value) return
  publishing.value = true
  try {
    const { data } = await publishArticlePromptTemplateVersion(templateDetail.value.id, versionId)
    templateDetail.value = normalizeTemplateDetail(data.data)
    ElMessage.success('版本已发布')
    loadTemplates()
  } catch (err) {
    console.error(err)
    ElMessage.error('发布版本失败')
  } finally {
    publishing.value = false
  }
}

onMounted(async () => {
  applyRouteFilters()
  await loadTemplates()
})
</script>

<style scoped>
.prompt-template-page {
  min-height: 100vh;
  padding: 30px;
  background:
    radial-gradient(circle at 10% 0%, rgba(59, 130, 246, 0.14), transparent 34%),
    radial-gradient(circle at 90% 4%, rgba(20, 184, 166, 0.12), transparent 28%),
    linear-gradient(180deg, #eef4ff 0%, rgba(238, 244, 255, 0) 300px),
    #f5f7fb;
  color: #111827;
}

.page-hero,
.channel-panel,
.filter-bar,
.template-section,
.template-card {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #ffffff;
}

.page-hero {
  position: relative;
  padding: 28px 30px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  overflow: hidden;
  border-color: rgba(191, 219, 254, 0.85);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(248, 251, 255, 0.92)),
    #ffffff;
  box-shadow: 0 18px 45px rgba(30, 64, 175, 0.1);
}

.page-hero::after {
  content: "";
  position: absolute;
  right: -90px;
  top: -110px;
  width: 280px;
  height: 280px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.08);
  pointer-events: none;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0;
  color: #2563eb;
}

.page-hero h1 {
  margin: 0;
  font-size: 26px;
  line-height: 1.25;
}

.hero-desc {
  max-width: 760px;
  margin: 8px 0 0;
  color: #6b7280;
  line-height: 1.6;
}

.hero-actions {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 14px;
}

.hero-stat {
  min-width: 94px;
  padding: 10px 14px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
  text-align: right;
}

.hero-stat strong,
.hero-stat span {
  display: block;
}

.hero-stat strong {
  font-size: 22px;
  line-height: 1;
  color: #1d4ed8;
}

.hero-stat span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.channel-panel {
  margin-top: 16px;
  padding: 14px;
  border-color: #dbeafe;
  box-shadow: 0 10px 26px rgba(30, 64, 175, 0.055);
}

.channel-tabs {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

.channel-tab {
  min-height: 74px;
  padding: 12px;
  border: 1px solid #e5edf8;
  border-radius: 10px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  display: flex;
  align-items: center;
  gap: 10px;
  color: #334155;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, background 0.16s ease;
}

.channel-tab:hover,
.channel-tab.active {
  border-color: #93c5fd;
  background: linear-gradient(180deg, #eff6ff 0%, #ffffff 100%);
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.11);
}

.channel-icon,
.section-icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  background: #eaf2ff;
  color: #1d4ed8;
  font-weight: 700;
}

.channel-tab strong,
.channel-tab small {
  display: block;
}

.channel-tab strong {
  font-size: 14px;
  color: #0f172a;
}

.channel-tab small {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.35;
}

.sub-channel-row {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #dbeafe;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.sub-channel-row > span {
  margin-right: 2px;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.sub-chip {
  height: 30px;
  padding: 0 12px;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: #ffffff;
  color: #475569;
  font-size: 13px;
  cursor: pointer;
}

.sub-chip.active,
.sub-chip:hover {
  border-color: #2563eb;
  background: #eff6ff;
  color: #1d4ed8;
}

.filter-bar {
  margin-top: 16px;
  padding: 16px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
  border-color: #e2e8f0;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.filter-main,
.filter-search {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-main :deep(.el-select) {
  width: 130px;
}

.search-input {
  width: 280px;
}

.template-sections {
  margin-top: 18px;
  display: grid;
  gap: 18px;
}

.template-section {
  padding: 16px;
  border-color: #dfe8f5;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.045);
}

.section-head {
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #edf2f7;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-title h2 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  line-height: 1.25;
}

.section-title p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.section-count {
  padding: 6px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  white-space: nowrap;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.template-card {
  position: relative;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-color: #e2e8f0;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.045);
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.template-card:focus-visible,
.template-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 16px 34px rgba(37, 99, 235, 0.1);
  transform: translateY(-1px);
  outline: none;
}

.card-top,
.weight-row,
.card-actions,
.version-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.template-title {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
  line-height: 1.45;
}

.template-scope,
.template-desc,
.template-facts {
  color: #6b7280;
  font-size: 12px;
  line-height: 1.5;
}

.template-scope {
  width: fit-content;
  margin-bottom: 6px;
  padding: 4px 8px;
  border-radius: 999px;
  background: #eef6ff;
  color: #2563eb;
  font-weight: 600;
}

.template-desc {
  min-height: 36px;
  margin: 0;
  color: #475569;
}

.template-facts {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.template-facts span {
  padding: 5px 9px;
  border-radius: 6px;
  background: #f3f6fb;
  color: #475569;
  border: 1px solid #edf2f7;
}

.weight-row {
  padding: 12px;
  border-radius: 10px;
  border: 1px solid #eef2f7;
  background: linear-gradient(135deg, #f8fafc 0%, #eff6ff 100%);
  color: #374151;
  font-size: 13px;
}

.weight-row > div {
  min-width: 0;
}

.weight-row span,
.weight-row small {
  display: block;
}

.weight-row span {
  font-weight: 600;
}

.weight-row small {
  max-width: 260px;
  margin-top: 3px;
  color: #64748b;
  font-size: 11px;
  line-height: 1.45;
}

.card-actions {
  justify-content: flex-end;
}

.pager-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

.template-form {
  max-height: 70vh;
  padding: 2px 6px 0 0;
  overflow-y: auto;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 14px;
}

.form-grid .el-form-item:first-child {
  grid-column: span 2;
}

.field-label,
.field-help {
  display: block;
}

.field-help {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.prompt-editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  gap: 14px;
}

.prompt-textarea :deep(.el-textarea__inner) {
  min-height: 360px !important;
  padding: 14px 15px;
  border-radius: 8px;
  font-family: "JetBrains Mono", Consolas, "Courier New", monospace;
  font-size: 13px;
  line-height: 1.7;
  background: #fbfdff;
}

.system-prompt :deep(.el-textarea__inner) {
  min-height: 300px !important;
}

.user-prompt :deep(.el-textarea__inner) {
  min-height: 420px !important;
}

.template-detail-panel {
  display: grid;
  gap: 16px;
}

.detail-summary {
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  background: linear-gradient(135deg, #f8fbff 0%, #ffffff 100%);
}

.detail-summary h3 {
  margin: 4px 0 6px;
  font-size: 20px;
  line-height: 1.35;
  color: #0f172a;
}

.detail-summary p {
  margin: 0;
  color: #64748b;
  line-height: 1.6;
}

.detail-kicker {
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
}

.detail-info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.detail-info-grid > div {
  min-width: 0;
  padding: 12px;
  border: 1px solid #edf2f7;
  border-radius: 10px;
  background: #fbfdff;
}

.detail-info-grid label,
.detail-info-grid strong {
  display: block;
}

.detail-info-grid label {
  margin-bottom: 5px;
  color: #94a3b8;
  font-size: 12px;
}

.detail-info-grid strong {
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-info-wide {
  grid-column: span 2;
}

.detail-prompt-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  gap: 12px;
}

.detail-prompt-grid section {
  min-width: 0;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
  background: #ffffff;
}

.detail-prompt-grid h4 {
  margin: 0;
  padding: 12px 14px;
  border-bottom: 1px solid #edf2f7;
  color: #334155;
  font-size: 14px;
}

.detail-prompt-grid pre {
  min-height: 260px;
  max-height: 360px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  color: #334155;
  font-family: "JetBrains Mono", Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  background: #fbfdff;
}

.version-title {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  background: #f8fafc;
}

.version-title strong,
.version-title span {
  display: block;
}

.version-title strong {
  font-size: 18px;
}

.version-title span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.version-list {
  max-height: 62vh;
  padding-right: 4px;
  display: grid;
  gap: 12px;
  overflow-y: auto;
}

.version-card {
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #ffffff;
}

.version-card p {
  margin: 8px 0 10px;
  color: #6b7280;
}

.version-card-head strong,
.version-card-head span {
  display: block;
}

.version-card-head span {
  margin-top: 4px;
  color: #94a3b8;
  font-size: 12px;
}

:deep(.template-editor-dialog .el-dialog__body),
:deep(.template-detail-dialog .el-dialog__body),
:deep(.version-dialog .el-dialog__body) {
  padding-top: 12px;
}

:deep(.template-editor-dialog .el-dialog),
:deep(.template-detail-dialog .el-dialog),
:deep(.version-dialog .el-dialog) {
  border-radius: 14px;
}

:deep(.template-editor-dialog .el-dialog__header),
:deep(.template-detail-dialog .el-dialog__header),
:deep(.version-dialog .el-dialog__header) {
  padding: 20px 24px 8px;
}

:deep(.template-editor-dialog .el-dialog__footer),
:deep(.template-detail-dialog .el-dialog__footer),
:deep(.version-dialog .el-dialog__footer) {
  padding: 10px 24px 20px;
}

@media (max-width: 1180px) {
  .channel-tabs {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .template-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-info-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-prompt-grid {
    grid-template-columns: 1fr;
  }

  .prompt-editor-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .prompt-template-page {
    padding: 16px;
  }

  .page-hero,
  .section-head,
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .hero-actions,
  .filter-main,
  .filter-search {
    align-items: stretch;
  }

  .template-grid,
  .channel-tabs,
  .form-grid,
  .detail-info-grid {
    grid-template-columns: 1fr;
  }

  .form-grid .el-form-item:first-child {
    grid-column: span 1;
  }

  .filter-bar :deep(.el-select),
  .search-input {
    width: 100%;
  }
}
</style>
