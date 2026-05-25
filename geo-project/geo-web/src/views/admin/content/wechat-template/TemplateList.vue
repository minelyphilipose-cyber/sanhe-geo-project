<template>
  <div class="wechat-template-page">
    <header class="page-head">
      <div>
        <div class="page-kicker">公众号样式模板</div>
        <h1>模板管理</h1>
        <p>管理由通用 HTML 或特定渠道导入的公众号排版模板。</p>
      </div>
      <el-button type="primary" @click="$router.push('/admin/content/wechat-templates/import')">导入模板</el-button>
    </header>

    <section class="template-toolbar">
      <el-input v-model="filters.keyword" clearable placeholder="搜索模板名称或说明" class="template-search" />
      <el-select v-model="filters.status" clearable placeholder="全部状态" class="template-status-filter">
        <el-option label="启用" value="enabled" />
        <el-option label="停用" value="disabled" />
      </el-select>
      <el-button @click="loadTemplates">刷新</el-button>
    </section>

    <el-table v-loading="loading" :data="filteredTemplates" border class="template-table">
      <el-table-column label="模板" min-width="260">
        <template #default="{ row }">
          <div class="template-name-cell">
            <strong>{{ row.name || '-' }}</strong>
            <span>{{ row.description || '暂无说明' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="平台" width="140">
        <template #default="{ row }">{{ platformLabel(row.platformCode) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === 'enabled' ? 'success' : 'info'">
            {{ row.status === 'enabled' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建人" width="110">
        <template #default="{ row }">{{ row.createdByName || row.createdBy || '-' }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <div class="template-actions">
            <el-button link type="primary" @click="openPreview(row)">预览</el-button>
            <el-button link @click="$router.push(`/admin/content/wechat-templates/${row.id}/edit`)">编辑</el-button>
            <el-button link :type="row.status === 'enabled' ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'enabled' ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="deleteTemplate(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && filteredTemplates.length === 0" description="暂无匹配模板" />

    <el-drawer v-model="previewVisible" title="模板预览" size="680px" class="template-preview-drawer">
      <div v-if="previewTemplate" class="preview-head">
        <strong>{{ previewTemplate.name }}</strong>
        <span>{{ previewTemplate.description || '暂无说明' }}</span>
      </div>
      <div v-if="previewVersion" class="preview-meta">
        <el-tag size="small">v{{ previewVersion.versionNo }}</el-tag>
        <el-tag size="small" type="info">{{ sourceTypeLabel(previewVersion.sourceType) }}</el-tag>
        <span>{{ formatDateTime(previewVersion.createdAt) }}</span>
      </div>
      <section v-if="previewVersion" class="preview-roles">
        <div>
          <strong>整套模板效果</strong>
          <span>按导入时的完整 HTML 展示，和导入页整体预览保持一致。</span>
        </div>
        <div class="role-tags">
          <el-tag v-for="role in previewRoles" :key="role" size="small" effect="plain">
            {{ roleLabel(role) }}
          </el-tag>
        </div>
      </section>
      <div v-loading="previewLoading" class="phone-frame">
        <div class="phone-top">
          <span />
          <strong>模板预览</strong>
        </div>
        <iframe v-if="previewVersion" class="phone-preview" sandbox="" referrerpolicy="no-referrer" :srcdoc="previewSrcdoc" />
        <div v-else class="empty-preview">当前模板暂无可预览版本</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteWechatRenderTemplate,
  getWechatRenderTemplateCurrentVersion,
  getWechatRenderTemplates,
  updateWechatRenderTemplateStatus,
  type PlatformRenderTemplate,
  type PlatformRenderTemplateVersion,
} from '@/api/content'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const previewLoading = ref(false)
const previewVisible = ref(false)
const templates = ref<PlatformRenderTemplate[]>([])
const previewTemplate = ref<PlatformRenderTemplate | null>(null)
const previewVersion = ref<PlatformRenderTemplateVersion | null>(null)
const filters = reactive({
  keyword: '',
  status: '',
})

const filteredTemplates = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return templates.value.filter((item) => {
    const matchesKeyword =
      !keyword ||
      item.name?.toLowerCase().includes(keyword) ||
      item.description?.toLowerCase().includes(keyword)
    const matchesStatus = !filters.status || item.status === filters.status
    return matchesKeyword && matchesStatus
  })
})

const previewSrcdoc = computed(() => {
  return buildPreviewDocument(buildVersionPreview(previewVersion.value))
})

const previewRoles = computed(() => Object.keys(parseTemplateRoles(previewVersion.value)))

const roleLabels: Record<string, string> = {
  article_title: '文章标题',
  image_block: '图片样式',
  heading: '段落标题',
  paragraph: '正文段落',
  highlight_block: '重点段落',
  golden_sentence_block: '整段金句',
  quote_block: '引用样式',
  ending_cta: '文末引导',
  divider: '分割线',
  native_html: '列表/表格',
}

function roleLabel(role: string) {
  return roleLabels[role] || role
}

function parseTemplateRoles(version: PlatformRenderTemplateVersion | null) {
  if (!version?.templateSchemaJson) return {}
  try {
    const schema = JSON.parse(version.templateSchemaJson) as {
      roles?: Record<string, { wrapperHtml?: string }>
      [key: string]: unknown
    }
    const roles = schema.roles && typeof schema.roles === 'object' ? schema.roles : schema
    return Object.fromEntries(
      Object.entries(roles)
        .filter(([, value]) => value && typeof value === 'object' && typeof (value as { wrapperHtml?: unknown }).wrapperHtml === 'string')
        .map(([role, value]) => [role, value as { wrapperHtml: string }]),
    ) as Record<string, { wrapperHtml: string }>
  } catch {
    return {}
  }
}

function buildVersionPreview(version: PlatformRenderTemplateVersion | null) {
  if (version?.sourceHtml?.trim()) {
    return version.sourceHtml
  }
  if (version?.sanitizedPreviewHtml?.trim()) {
    return version.sanitizedPreviewHtml
  }
  const roles = parseTemplateRoles(version)
  const roleOrder = ['image_block', 'quote_block', 'heading', 'paragraph', 'highlight_block', 'golden_sentence_block', 'ending_cta']
  const orderedRoles = roleOrder.filter((role) => roles[role]?.wrapperHtml)
  if (orderedRoles.length) {
    let headingIndex = 0
    return orderedRoles
      .map((role) => {
        const index = role === 'heading' ? ++headingIndex : headingIndex || 1
        return wrapPreviewSlice(buildSamplePreview(roles[role].wrapperHtml, role, index), role)
      })
      .join('')
  }
  return '<p style="color:#94a3b8;">暂无预览内容</p>'
}

function buildSamplePreview(html: string, role: string, index = 1) {
  const sampleContent: Record<string, string> = {
    heading: '这是第一段',
    paragraph: '这是一段公众号正文示例。保存模板后，系统会把文章内容套入这个样式。',
    highlight_block: '这个就是示例 这是第一点 这是第二点 这是第三点',
    golden_sentence_block: '用户的购物路径，正在从“搜索商品”转向“向 AI 描述需求”。',
    quote_block: '这是一段引用示例，用来展示重点观点或权威说明。',
    ending_cta: '联系我们',
  }
  const image = 'data:image/svg+xml;utf8,' + encodeURIComponent(`
    <svg xmlns="http://www.w3.org/2000/svg" width="640" height="360" viewBox="0 0 640 360">
      <rect width="640" height="360" rx="22" fill="#eef4ff"/>
      <rect x="44" y="44" width="552" height="272" rx="18" fill="#ffffff" stroke="#cbd9f4"/>
      <circle cx="168" cy="150" r="44" fill="#7da7ff"/>
      <path d="M92 276l124-98 78 64 74-54 180 88H92z" fill="#dbe7ff"/>
      <text x="320" y="180" text-anchor="middle" font-size="30" font-family="Arial" fill="#315178">图片样式预览</text>
    </svg>
  `)
  const content = sampleContent[role] || sampleContent.paragraph
  return replaceTokens(html, {
    '{{content}}': content,
    '{{text}}': content,
    '{{title}}': sampleContent.heading,
    '{{subtitle}}': '用于展示副标题或补充说明',
    '{{caption}}': '图片说明',
    '{{index}}': String(index).padStart(2, '0'),
    '{{imageUrl}}': image,
    '{{imageAlt}}': '图片样式预览',
  })
}

function wrapPreviewSlice(html: string, role: string) {
  const hasTopMargin = hasInlineEdgeMargin(html, 'top')
  const hasBottomMargin = hasInlineEdgeMargin(html, 'bottom')
  return `<section class="wechat-preview-slice" data-preview-role="${role}" data-has-top-margin="${hasTopMargin}" data-has-bottom-margin="${hasBottomMargin}">${html}</section>`
}

function hasInlineEdgeMargin(html: string, edge: 'top' | 'bottom') {
  const normalized = html.trim()
  if (!normalized) return false
  const document = new DOMParser().parseFromString(normalized, 'text/html')
  const element = (edge === 'top' ? document.body.firstElementChild : document.body.lastElementChild) as HTMLElement | null
  if (!element) return false
  const margin = edge === 'top' ? element.style.marginTop : element.style.marginBottom
  return hasPositiveCssLength(margin)
}

function hasPositiveCssLength(value: string | null | undefined) {
  if (!value || value === '0' || value === '0px') return false
  const numeric = Number.parseFloat(value)
  return Number.isNaN(numeric) || numeric > 0
}

function replaceTokens(html: string, tokens: Record<string, string>) {
  let result = html
  for (const [token, value] of Object.entries(tokens)) {
    result = result.split(token).join(value)
  }
  return result
}

function buildPreviewDocument(content: string) {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <style>
    html,
    body {
      margin: 0;
      padding: 0;
      background: #fff;
    }

    body {
      box-sizing: border-box;
      min-height: 100vh;
      color: #333;
      font-family: -apple-system, BlinkMacSystemFont, "Helvetica Neue", Helvetica, "PingFang SC", "Microsoft YaHei", Arial, sans-serif;
      font-size: 12px;
      font-weight: 400;
      line-height: 1.65;
      overflow-x: hidden;
      text-rendering: optimizeLegibility;
      -webkit-font-smoothing: antialiased;
      word-break: break-word;
    }

    .wechat-preview-body {
      box-sizing: border-box;
      width: 100%;
      min-height: 100vh;
      padding: 20px 28px 30px;
      background: #fff;
    }

    .wechat-preview-slice {
      display: block;
    }

    .wechat-preview-slice[data-preview-role="heading"][data-has-bottom-margin="false"] + .wechat-preview-slice[data-preview-role="paragraph"][data-has-top-margin="false"] {
      margin-top: 16px;
    }

    .wechat-preview-slice[data-preview-role="paragraph"][data-has-bottom-margin="false"] + .wechat-preview-slice[data-preview-role="highlight_block"][data-has-top-margin="false"] {
      margin-top: 18px;
    }

    .wechat-preview-slice[data-preview-role="highlight_block"][data-has-bottom-margin="false"] + .wechat-preview-slice[data-preview-role="heading"][data-has-top-margin="false"] {
      margin-top: 26px;
    }

    .wechat-preview-slice[data-preview-role="paragraph"][data-has-bottom-margin="false"] + .wechat-preview-slice[data-preview-role="paragraph"][data-has-top-margin="false"] {
      margin-top: 14px;
    }

    .wechat-preview-slice[data-preview-role="paragraph"][data-has-bottom-margin="false"] + .wechat-preview-slice[data-preview-role="ending_cta"][data-has-top-margin="false"] {
      margin-top: 24px;
    }

    *,
    *::before,
    *::after {
      box-sizing: border-box;
    }

    p {
      margin: 0;
    }

    ol,
    ul {
      margin-top: 0;
      margin-bottom: 0;
    }

    li p {
      margin: 0;
    }

    img {
      max-width: 100%;
      height: auto;
      vertical-align: middle;
    }
  </style>
</head>
<body><main class="wechat-preview-body">${content}</main></body>
</html>`
}

async function loadTemplates() {
  loading.value = true
  try {
    const { data } = await getWechatRenderTemplates({ current: 1, size: 100 })
    templates.value = data.data.records || []
  } catch (error) {
    ElMessage.error('加载公众号模板失败')
  } finally {
    loading.value = false
  }
}

async function openPreview(row: PlatformRenderTemplate) {
  previewTemplate.value = row
  previewVersion.value = null
  previewVisible.value = true
  previewLoading.value = true
  try {
    const { data } = await getWechatRenderTemplateCurrentVersion(row.id)
    previewVersion.value = data.data
  } catch (error) {
    ElMessage.error('加载模板预览失败')
  } finally {
    previewLoading.value = false
  }
}

async function toggleStatus(row: PlatformRenderTemplate) {
  const nextStatus = row.status === 'enabled' ? 'disabled' : 'enabled'
  try {
    await updateWechatRenderTemplateStatus(row.id, nextStatus)
    ElMessage.success(nextStatus === 'enabled' ? '模板已启用' : '模板已停用')
    await loadTemplates()
  } catch (error) {
    ElMessage.error(nextStatus === 'enabled' ? '启用失败' : '停用失败')
  }
}

async function deleteTemplate(row: PlatformRenderTemplate) {
  try {
    await ElMessageBox.confirm(
      `确定删除模板「${row.name || row.id}」吗？删除后不可恢复。已被文章使用的模板不能删除。`,
      '删除模板',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
    await deleteWechatRenderTemplate(row.id)
    ElMessage.success('模板已删除')
    await loadTemplates()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error('删除失败，请确认该模板没有被文章使用')
  }
}

function platformLabel(platformCode?: string | null) {
  if (platformCode === 'wechat_mp') return '微信公众号'
  return platformCode || '-'
}

function sourceTypeLabel(sourceType?: string | null) {
  if (!sourceType) return '未知来源'
  if (sourceType === 'article135' || sourceType === '135' || sourceType === 'source_135') return '135 编辑器'
  if (sourceType === 'generic' || sourceType === 'generic_html') return '通用 HTML'
  return sourceType
}

onMounted(loadTemplates)
</script>

<style scoped>
.wechat-template-page {
  min-height: 100%;
  padding: 28px;
  background: #f5f7fb;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.page-kicker {
  margin-bottom: 8px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

.page-head h1 {
  margin: 0;
  font-size: 26px;
  color: #0f172a;
}

.page-head p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 14px;
}

.template-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  margin-bottom: 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.template-search {
  width: 320px;
}

.template-status-filter {
  width: 150px;
}

.template-table {
  background: #fff;
}

.template-name-cell {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.template-name-cell strong {
  color: #0f172a;
  font-size: 14px;
}

.template-name-cell span {
  overflow: hidden;
  color: #64748b;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.template-preview-drawer :deep(.el-drawer__header) {
  padding: 20px 24px 16px;
  margin-bottom: 0;
  border-bottom: 1px solid #e5eaf3;
}

.template-preview-drawer :deep(.el-drawer__title) {
  color: #0f172a;
  font-size: 18px;
  font-weight: 700;
}

.template-preview-drawer :deep(.el-drawer__body) {
  padding: 20px 24px 28px;
  background: #f6f8fc;
}

.preview-head {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 18px;
  margin-bottom: 12px;
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 12px;
  box-shadow: 0 10px 24px rgb(15 23 42 / 4%);
}

.preview-head strong {
  color: #0f172a;
  font-size: 20px;
  line-height: 1.3;
}

.preview-head span {
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.preview-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 16px;
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  color: #64748b;
  font-size: 12px;
}

.preview-roles {
  padding: 14px 16px;
  margin-bottom: 18px;
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 12px;
  box-shadow: 0 10px 24px rgb(15 23 42 / 4%);
}

.preview-roles > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
}

.preview-roles strong {
  color: #0f172a;
  font-size: 14px;
}

.preview-roles span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.role-tags :deep(.el-tag) {
  padding: 0 10px;
  border-radius: 999px;
}

.phone-frame {
  width: 390px;
  min-height: 640px;
  padding: 14px 12px 18px;
  margin: 0 auto;
  background: #132033;
  border-radius: 30px;
  box-shadow: 0 22px 52px rgb(15 23 42 / 18%);
}

.phone-top {
  position: relative;
  height: 34px;
  color: #fff;
  text-align: center;
  font-size: 13px;
  line-height: 26px;
}

.phone-top span {
  position: absolute;
  top: 8px;
  left: 50%;
  width: 58px;
  height: 5px;
  background: #263a57;
  border-radius: 99px;
  transform: translateX(-50%);
}

.phone-preview,
.empty-preview {
  width: 100%;
  min-height: 560px;
  overflow: hidden;
  background: #fff;
  border: 0;
  border-radius: 18px;
}

.empty-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 14px;
}

@media (max-width: 720px) {
  .template-preview-drawer :deep(.el-drawer__body) {
    padding: 16px;
  }

  .phone-frame {
    width: min(390px, 100%);
  }
}

</style>
