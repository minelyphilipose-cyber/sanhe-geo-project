<template>
  <div class="template-import-page">
    <header class="page-head">
      <div>
        <p class="eyebrow">公众号样式模板</p>
        <h1>{{ pageTitle }}</h1>
        <p>{{ pageDescription }}</p>
      </div>
      <el-button @click="$router.push('/admin/content/wechat-templates')">返回列表</el-button>
    </header>

    <section class="choice-section">
      <button
        class="choice-card"
        :class="{ active: importMode === 'generic' }"
        type="button"
        @click="selectImportMode('generic')"
      >
        <span class="choice-card__badge">方式一</span>
        <strong>通用 HTML 导入</strong>
        <span>适合从网页、编辑器或历史资料复制出来的普通 HTML。</span>
        <em>需要人工确认每类样式</em>
      </button>

      <button
        class="choice-card"
        :class="{ active: importMode === 'channel' }"
        type="button"
        @click="selectImportMode('channel')"
      >
        <span class="choice-card__badge">方式二</span>
        <strong>特定渠道导入</strong>
        <span>适合 135、秀米等编辑器导出的模板，系统会按渠道规则自动识别。</span>
        <em>{{ openChannelText }}</em>
      </button>
    </section>

    <section v-if="importMode === 'channel'" class="channel-section">
      <button
        v-for="channel in channels"
        :key="channel.value"
        class="channel-card"
        :class="{ active: selectedChannel === channel.value, disabled: channel.disabled }"
        type="button"
        :disabled="channel.disabled"
        @click="selectChannel(channel.value)"
      >
        <span class="channel-card__title">{{ channel.name }}</span>
        <span class="channel-card__desc">{{ channel.description }}</span>
        <el-tag :type="channel.disabled ? 'info' : 'success'" size="small">
          {{ channel.disabled ? '暂未开放' : '已开放' }}
        </el-tag>
      </button>
    </section>

    <section class="workspace">
      <aside class="panel source-panel">
        <div class="panel-head">
          <div>
            <h2>导入模板内容</h2>
            <p>{{ sourceHint }}</p>
          </div>
        </div>

        <el-form label-position="top">
          <el-form-item label="模板名称">
            <el-input v-model="form.name" placeholder="例如：红色编号长文模板" />
          </el-form-item>
          <el-form-item label="模板说明">
            <el-input v-model="form.description" placeholder="适用场景，可选" />
          </el-form-item>
          <el-form-item label="HTML 文件">
            <el-upload
              class="html-upload"
              drag
              action="#"
              accept=".html,.htm,text/html"
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleHtmlFileChange"
            >
              <div class="upload-copy">
                <strong>{{ uploadedFileName || '拖拽 HTML 文件到这里，或点击选择文件' }}</strong>
                <span>支持 .html / .htm 文件，读取后会自动填入下方内容。</span>
              </div>
            </el-upload>
          </el-form-item>
          <el-form-item v-if="form.sourceHtml" label="已读取内容">
            <div class="html-file-status">
              <strong>{{ uploadedFileName || '已读取手动输入的 HTML 内容' }}</strong>
              <span>HTML 内容已准备好，不会在页面上展开显示。</span>
            </div>
          </el-form-item>
          <el-collapse class="manual-html-collapse">
            <el-collapse-item title="手动粘贴 HTML（兜底）" name="manual-html">
              <el-form-item label="HTML 内容">
                <el-input
                  v-model="form.sourceHtml"
                  type="textarea"
                  :rows="10"
                  placeholder="如果无法上传文件，可在这里手动粘贴完整 HTML。"
                  @input="uploadedFileName = ''"
                />
              </el-form-item>
            </el-collapse-item>
          </el-collapse>
        </el-form>

        <div class="actions">
          <el-button type="primary" :loading="parsing" @click="parse">
            生成样式预览
          </el-button>
          <el-button :disabled="!roleDrafts.length" :loading="saving" @click="save">
            {{ saveButtonText }}
          </el-button>
        </div>

        <div class="tips">
          <strong>当前处理方式</strong>
          <span>{{ importModeLabel }}</span>
          <span v-if="importMode === 'channel'">系统会自动识别标题、段落、图片、分割线和引导关注模块。</span>
          <span v-else>系统会先拆成片段，你只需要选择每类样式的代表效果。</span>
        </div>
      </aside>

      <main class="review-area">
        <section class="panel result-panel">
          <div class="panel-head">
            <div>
              <h2>确认可用样式</h2>
            <p>{{ resultHint }}</p>
          </div>
            <div class="result-tools">
              <el-button-group v-if="roleDrafts.length">
                <el-button :type="previewMode === 'single' ? 'primary' : 'default'" @click="previewMode = 'single'">
                  单个样式
                </el-button>
                <el-button :type="previewMode === 'full' ? 'primary' : 'default'" @click="previewMode = 'full'">
                  整体预览
                </el-button>
              </el-button-group>
              <el-tag v-if="roleDrafts.length" type="success">{{ roleDrafts.length }} 类样式</el-tag>
            </div>
          </div>

          <el-alert
            v-if="warningSummary"
            :title="warningSummary"
            type="warning"
            show-icon
            :closable="false"
            class="warning-summary"
          />

          <el-empty v-if="!roleDrafts.length" description="点击“生成样式预览”后，这里会显示识别出的样式卡" />

          <template v-else>
            <div class="review-grid">
              <div class="style-list">
                <article
                  v-for="role in roleDrafts"
                  :key="role.role + role.sliceIds.join(',')"
                  class="style-card"
                  :class="{ active: previewMode === 'single' && selectedPreviewKey === role.role }"
                  @click="selectRolePreview(role.role)"
                >
                  <div class="style-card__top">
                    <el-select
                      v-model="role.role"
                      class="friendly-select"
                      @change="selectRolePreview(String(role.role))"
                    >
                      <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                    <el-tag :type="role.needsConfirmation ? 'warning' : 'success'" size="small">
                      {{ role.needsConfirmation ? '建议确认' : '可使用' }}
                    </el-tag>
                  </div>
                  <div class="style-card__meta">
                    <span>已合并 {{ role.reuseCount }} 个相似片段</span>
                    <span v-if="role.sliceIds.length">{{ previewMode === 'full' ? '整体预览会使用该样式' : '点击查看右侧效果' }}</span>
                  </div>
                </article>
              </div>

              <aside class="preview-panel">
                <div class="preview-size-bar">
                  <span>预览尺寸</span>
                  <el-segmented v-model="selectedPreviewSizeKey" :options="previewSizeOptions" size="small" />
                </div>
                <el-alert
                  v-if="previewLayoutWarning"
                  :title="previewLayoutWarning"
                  type="warning"
                  show-icon
                  :closable="false"
                  class="preview-layout-warning"
                />
                <div class="preview-shell" :style="{ width: `${selectedPreviewSize.width}px` }">
                  <div class="phone-bar">
                    <span></span>
                    <strong>{{ selectedPreviewSize.label }}预览</strong>
                    <span></span>
                  </div>
                  <iframe ref="previewFrameRef" :srcdoc="previewHtml" @load="checkPreviewOverflow"></iframe>
                </div>
                <div class="preview-note">
                  <strong>{{ previewTitle }}</strong>
                  <span>{{ previewDescription }}</span>
                </div>
              </aside>
            </div>
          </template>
        </section>

        <section v-if="roleDrafts.length && (manualSlices.length || outlierSlices.length)" class="panel confirm-panel">
          <div class="panel-head">
            <div>
              <h2>{{ confirmTitle }}</h2>
              <p>{{ confirmHint }}</p>
            </div>
            <el-tag type="warning">{{ manualSlices.length + outlierSlices.length }} 个建议确认</el-tag>
          </div>

          <div class="slice-list">
            <article v-for="slice in confirmSlices" :key="slice.id" class="slice-row" :class="{ outlier: slice.outlier }">
              <div>
                <strong>片段 {{ slice.order }}</strong>
                <p>{{ slice.previewText || '图片或装饰片段' }}</p>
              </div>
              <el-select v-model="slice.role" class="slice-role">
                <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-button @click="setRepresentative(slice)">
                {{ importMode === 'generic' ? '设为代表样式' : '采用这个样式' }}
              </el-button>
            </article>
          </div>
        </section>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import {
  createWechatRenderTemplateVersion,
  createWechatRenderTemplate,
  getWechatRenderTemplate,
  getWechatRenderTemplateCurrentVersion,
  parseWechatRenderTemplate,
  updateWechatRenderTemplate,
  type WechatBodyStyle,
  type WechatRenderRoleSchema,
  type WechatTemplateRoleDraft,
  type WechatTemplateSlice,
  type WechatRenderWarning,
  type PlatformRenderTemplate,
  type PlatformRenderTemplateVersion,
} from '@/api/content'
import { defaultImportChannel, findImportChannel, importChannels, type ImportChannelValue } from './importChannels'

type ImportMode = 'generic' | 'channel'

const router = useRouter()
const route = useRoute()
const parsing = ref(false)
const saving = ref(false)
const loadingTemplate = ref(false)
const importMode = ref<ImportMode>('channel')
const selectedChannel = ref<ImportChannelValue>(defaultImportChannel.value)
const selectedPreviewKey = ref('')
const previewMode = ref<'single' | 'full'>('single')
const selectedPreviewSizeKey = ref('standard')
const previewFrameRef = ref<HTMLIFrameElement | null>(null)
const previewLayoutWarning = ref('')
const uploadedFileName = ref('')
const slices = ref<WechatTemplateSlice[]>([])
const roleDrafts = ref<WechatTemplateRoleDraft[]>([])
const parseWarnings = ref<WechatRenderWarning[]>([])
const bodyStyle = ref<WechatBodyStyle | null>(null)
const editingTemplate = ref<PlatformRenderTemplate | null>(null)
const editingVersion = ref<PlatformRenderTemplateVersion | null>(null)

const form = reactive({
  name: '',
  description: '',
  sourceHtml: '',
})

const channels = importChannels
const editingTemplateId = computed(() => {
  const raw = route.params.templateId
  const value = Array.isArray(raw) ? raw[0] : raw
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
})
const isEditMode = computed(() => editingTemplateId.value != null)
const pageTitle = computed(() => (isEditMode.value ? '编辑模板' : '导入模板'))
const pageDescription = computed(() =>
  isEditMode.value
    ? '在原导入页面调整 HTML、样式角色和整体预览，保存后生成新的模板版本。'
    : '选择一种导入方式，粘贴模板 HTML，系统会帮你拆出可复用的公众号样式。',
)
const saveButtonText = computed(() => (isEditMode.value ? '保存为新版本' : '保存为模板'))

const roleOptions = [
  { value: 'heading', label: '标题样式' },
  { value: 'paragraph', label: '普通段落' },
  { value: 'highlight_block', label: '重点段落' },
  { value: 'golden_sentence_block', label: '整段金句' },
  { value: 'quote_block', label: '引用样式' },
  { value: 'image_block', label: '图片样式' },
  { value: 'divider', label: '分割线' },
  { value: 'ending_cta', label: '结尾引导' },
]

const previewSizeOptions = [
  { label: '小屏', value: 'small' },
  { label: '标准', value: 'standard' },
  { label: '大屏', value: 'large' },
]

const previewSizes: Record<string, { label: string; width: number }> = {
  small: { label: '小屏', width: 320 },
  standard: { label: '标准', width: 375 },
  large: { label: '大屏', width: 414 },
}

const selectedChannelConfig = computed(() => findImportChannel(selectedChannel.value) || defaultImportChannel)
const openChannelText = computed(() => {
  const opened = channels.filter((channel) => !channel.disabled).map((channel) => channel.name)
  return opened.length ? `当前开放 ${opened.join('、')}` : '暂无开放渠道'
})
const sourceType = computed(() => (importMode.value === 'channel' ? selectedChannelConfig.value.sourceType : 'generic'))
const importModeLabel = computed(() =>
  importMode.value === 'channel' ? `特定渠道导入 / ${selectedChannelConfig.value.name}` : '通用 HTML 导入',
)
const sourceHint = computed(() =>
  importMode.value === 'channel'
    ? `从 ${selectedChannelConfig.value.name} 复制完整 HTML，系统会尽量自动识别样式。`
    : '粘贴任意来源的 HTML，解析后手动选择每类样式的代表效果。',
)
const resultHint = computed(() =>
  loadingTemplate.value
    ? '正在加载当前模板版本。'
    : isEditMode.value && !slices.value.length
    ? '当前展示的是已保存版本的样式。修改 HTML 后可重新生成识别结果，再保存为新版本。'
    : importMode.value === 'channel'
    ? '先看系统识别结果，同类样式有多个版本时再选一个代表。'
    : '把片段分成标题、段落、图片等样式，并指定每类样式的代表效果。',
)
const confirmTitle = computed(() => (importMode.value === 'generic' ? '选择每类代表样式' : '建议确认的样式版本'))
const confirmHint = computed(() =>
  importMode.value === 'generic'
    ? '普通 HTML 需要你选择每类样式的代表效果，同一类以最后一次设置为准。'
    : '系统已经识别出类型，但同一类里出现了不同视觉版本。选择你想保留的代表样式即可。',
)
const outlierSlices = computed(() => slices.value.filter((slice) => slice.outlier))
const manualSlices = computed(() => (importMode.value === 'generic' ? slices.value : []))
const confirmSlices = computed(() => (importMode.value === 'generic' ? manualSlices.value : outlierSlices.value))
const selectedRoleDraft = computed(() => roleDrafts.value.find((role) => role.role === selectedPreviewKey.value) || roleDrafts.value[0])
const selectedPreviewSize = computed(() => previewSizes[selectedPreviewSizeKey.value] || previewSizes.standard)
const warningSummary = computed(() => {
  if (!parseWarnings.value.length) return ''
  const outlierCount = parseWarnings.value.filter((item) => item.type === 'template_outlier').length
  const otherCount = parseWarnings.value.length - outlierCount
  if (outlierCount && otherCount) return `有 ${outlierCount} 个样式版本建议确认，另有 ${otherCount} 条提示。`
  if (outlierCount) return `有 ${outlierCount} 个样式版本建议确认，可在下方选择代表样式。`
  return parseWarnings.value[0]?.message || ''
})
const previewHtml = computed(() => {
  if (previewMode.value === 'full' && roleDrafts.value.length) {
    return buildPreviewDocument(buildFullPreview())
  }
  const role = selectedRoleDraft.value
  if (!role) {
    return buildPreviewDocument(`
      <main style="box-sizing:border-box;min-height:520px;padding:28px 22px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#7a8699;background:#fff;">
        <section style="height:100%;border:1px dashed #d8dee9;border-radius:16px;padding:48px 20px;text-align:center;">
          <strong style="display:block;color:#27364a;font-size:18px;margin-bottom:10px;">还没有预览内容</strong>
          <span style="font-size:14px;line-height:1.7;">粘贴 HTML 后点击“生成样式预览”。</span>
        </section>
      </main>
    `)
  }
  return buildPreviewDocument(buildRolePreview(role.wrapperHtml || '', role.role))
})
const previewTitle = computed(() =>
  previewMode.value === 'full' ? '整篇效果预览' : selectedRoleDraft.value ? roleLabel(selectedRoleDraft.value.role) : '等待生成预览',
)
const previewDescription = computed(() =>
  previewMode.value === 'full'
    ? '当前按原模板片段顺序展示整体效果，修改左侧样式、预览尺寸或下方片段确认后会实时更新。'
    : selectedRoleDraft.value
    ? `当前展示的是“${roleLabel(selectedRoleDraft.value.role)}”的实际样式，切换左侧样式卡或预览尺寸会实时更新。`
    : '解析后可以逐个查看每类样式的真实效果。',
)

watch([previewHtml, selectedPreviewSize], () => {
  previewLayoutWarning.value = ''
  nextTick(() => window.setTimeout(checkPreviewOverflow, 80))
})

function roleLabel(role: string) {
  return roleOptions.find((item) => item.value === role)?.label || role
}

function selectImportMode(mode: ImportMode) {
  importMode.value = mode
  if (mode === 'channel') {
    selectedChannel.value = defaultImportChannel.value
  }
  clearParsedResult()
}

function selectChannel(value: ImportChannelValue) {
  selectedChannel.value = value
  clearParsedResult()
}

function clearParsedResult() {
  slices.value = []
  roleDrafts.value = []
  parseWarnings.value = []
  bodyStyle.value = null
  selectedPreviewKey.value = ''
  previewMode.value = 'single'
}

async function handleHtmlFileChange(file: UploadFile) {
  const rawFile = file.raw
  if (!rawFile) return
  const fileName = rawFile.name || file.name || ''
  const lowerName = fileName.toLowerCase()
  const isHtmlFile = lowerName.endsWith('.html') || lowerName.endsWith('.htm') || rawFile.type === 'text/html'
  if (!isHtmlFile) {
    ElMessage.warning('请上传 .html 或 .htm 文件')
    return
  }
  if (rawFile.size > 8 * 1024 * 1024) {
    ElMessage.warning('HTML 文件过大，请控制在 8MB 以内')
    return
  }
  try {
    const text = await rawFile.text()
    form.sourceHtml = text
    uploadedFileName.value = fileName
    clearParsedResult()
    ElMessage.success('HTML 文件已读取，请点击生成样式预览')
  } catch {
    ElMessage.error('读取 HTML 文件失败，请检查文件后重试')
  }
}

async function parse() {
  if (!form.sourceHtml.trim()) {
    ElMessage.warning('请先上传或粘贴 HTML 内容')
    return
  }
  parsing.value = true
  try {
    const { data } = await parseWechatRenderTemplate({
      sourceHtml: form.sourceHtml,
      sourceType: sourceType.value,
    })
    slices.value = data.data.slices || []
    roleDrafts.value = data.data.roles || []
    parseWarnings.value = data.data.warnings || []
    bodyStyle.value = data.data.bodyStyle || null
    selectedPreviewKey.value = preferredPreviewRole(roleDrafts.value)
    previewMode.value = 'full'
    ElMessage.success('已生成样式预览')
  } catch {
    ElMessage.error('模板解析失败')
  } finally {
    parsing.value = false
  }
}

function selectRolePreview(role: string) {
  selectedPreviewKey.value = role
  previewMode.value = 'single'
}

function preferredPreviewRole(roles: WechatTemplateRoleDraft[]) {
  const priority = ['heading', 'highlight_block', 'paragraph', 'quote_block', 'golden_sentence_block', 'ending_cta', 'image_block']
  for (const role of priority) {
    if (roles.some((item) => item.role === role)) return role
  }
  return roles[0]?.role || ''
}

function buildSamplePreview(html: string, role: string, sourceText?: string, index = 1, sourceHtml?: string) {
  const previewText = normalizePreviewText(sourceText, role)
  const previewContentHtml = normalizePreviewHtml(sourceHtml, role)
  const sampleContent: Record<string, string> = {
    heading: '一、过去，品牌争夺的是“结果搜索页”',
    paragraph: '这是一段公众号正文示例。保存模板后，系统会把文章内容套入这个样式。',
    highlight_block: '我解决什么问题；我和其他品牌有什么不同；我在哪些场景下更值得被推荐。',
    golden_sentence_block: '用户的购物路径，正在从“搜索商品”转向“向 AI 描述需求”。',
    quote_block: '这是一段引用示例，用来展示重点观点或权威说明。',
    ending_cta: '欢迎关注我们，获取更多品牌增长与 AI 可见度案例。',
  }
  const content = previewContentHtml || previewText || sampleContent[role] || sampleContent.paragraph
  const image = 'data:image/svg+xml;utf8,' + encodeURIComponent(`
    <svg xmlns="http://www.w3.org/2000/svg" width="640" height="360" viewBox="0 0 640 360">
      <rect width="640" height="360" rx="22" fill="#eef4ff"/>
      <rect x="44" y="44" width="552" height="272" rx="18" fill="#ffffff" stroke="#cbd9f4"/>
      <circle cx="168" cy="150" r="44" fill="#7da7ff"/>
      <path d="M92 276l124-98 78 64 74-54 180 88H92z" fill="#dbe7ff"/>
      <text x="320" y="180" text-anchor="middle" font-size="30" font-family="Arial" fill="#315178">图片样式预览</text>
    </svg>
  `)
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

function buildRolePreview(html: string, role: string, sourceText?: string, index = 1, sourceHtml?: string) {
  return applyPreviewBodyStyle(buildSamplePreview(html, role, sourceText, index, sourceHtml), role)
}

function buildFullPreview() {
  if (!slices.value.length && roleDrafts.value.length) {
    const roleOrder = ['image_block', 'quote_block', 'heading', 'paragraph', 'highlight_block', 'golden_sentence_block', 'ending_cta']
    let headingIndex = 0
    return roleOrder
      .map((role) => roleDrafts.value.find((item) => item.role === role))
      .filter((role): role is WechatTemplateRoleDraft => Boolean(role))
      .map((role) => {
        const previewIndex = role.role === 'heading' ? ++headingIndex : headingIndex || 1
        return wrapPreviewSlice(buildRolePreview(role.wrapperHtml, role.role, undefined, previewIndex), role.role)
      })
      .join('')
  }
  const sortedSlices = [...slices.value].sort((a, b) => Number(a.order || 0) - Number(b.order || 0))
  let headingIndex = 0
  const html = sortedSlices
    .map((slice) => {
      const role = slice.role || slice.suggestedRole || 'paragraph'
      const draft = roleDrafts.value.find((item) => item.role === role)
      const wrapper = draft?.wrapperHtml || slice.html || ''
      const previewIndex = role === 'heading' ? ++headingIndex : headingIndex || 1
      return wrapPreviewSlice(buildRolePreview(wrapper, role, slice.previewText, previewIndex, slice.previewHtml), role)
    })
    .join('')
  return html || buildSamplePreview('', 'paragraph')
}

function wrapPreviewSlice(html: string, role: string) {
  const hasTopMargin = hasInlineEdgeMargin(html, 'top')
  const hasBottomMargin = hasInlineEdgeMargin(html, 'bottom')
  return `<section class="wechat-preview-slice" data-preview-role="${role}" data-has-top-margin="${hasTopMargin}" data-has-bottom-margin="${hasBottomMargin}">${html}</section>`
}

function applyPreviewBodyStyle(html: string, role: string) {
  if (role !== 'paragraph' || !bodyStyle.value || !html.trim()) return html
  const document = new DOMParser().parseFromString(html, 'text/html')
  const outer = document.body.firstElementChild as HTMLElement | null
  if (!outer) return html
  putStyleIfMissing(outer, 'font-size', bodyStyle.value.fontSize, ['font'])
  putStyleIfMissing(outer, 'line-height', bodyStyle.value.lineHeight, ['font'])
  putStyleIfMissing(outer, 'letter-spacing', bodyStyle.value.letterSpacing)
  putStyleIfMissing(outer, 'color', bodyStyle.value.color)
  putStyleIfMissing(outer, 'text-align', bodyStyle.value.textAlign)
  putStyleIfMissing(outer, 'margin', bodyStyle.value.paragraphMargin, ['margin-bottom'])
  return document.body.innerHTML
}

function putStyleIfMissing(element: HTMLElement, property: string, value?: string | null, relatedProperties: string[] = []) {
  if (!value?.trim()) return
  const style = parseInlineStyle(element.getAttribute('style') || '')
  if (style.has(property) || relatedProperties.some((item) => style.has(item))) return
  style.set(property, value.trim())
  element.setAttribute(
    'style',
    Array.from(style.entries())
      .map(([key, styleValue]) => `${key}:${styleValue}`)
      .join(';'),
  )
}

function parseInlineStyle(rawStyle: string) {
  const style = new Map<string, string>()
  rawStyle.split(';').forEach((declaration) => {
    const index = declaration.indexOf(':')
    if (index <= 0) return
    const property = declaration.slice(0, index).trim().toLowerCase()
    const value = declaration.slice(index + 1).trim()
    if (property && value) {
      style.set(property, value)
    }
  })
  return style
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

function normalizePreviewText(text: string | undefined, role?: string) {
  const normalized = (text || '').replace(/\s+/g, ' ').trim()
  if (role === 'heading') {
    return normalized
      .replace(/^(?:\d{1,3}|[一二三四五六七八九十百]{1,4})[\s、.．-]*/, '')
      .trim()
  }
  return normalized
}

function normalizePreviewHtml(html: string | undefined, role?: string) {
  const normalized = (html || '').trim()
  if (!normalized) return ''
  if (role === 'heading') {
    return normalizePreviewText(normalized.replace(/<[^>]+>/g, ' '), role)
  }
  return normalized
}

function replaceTokens(html: string, tokens: Record<string, string>) {
  let result = html
  for (const [token, value] of Object.entries(tokens)) {
    result = result.split(token).join(value)
  }
  return result
}

function checkPreviewOverflow() {
  const frame = previewFrameRef.value
  const doc = frame?.contentDocument
  if (!frame || !doc) return
  const body = doc.body
  const root = doc.documentElement
  const contentWidth = Math.max(body?.scrollWidth || 0, root?.scrollWidth || 0)
  const viewportWidth = frame.clientWidth
  previewLayoutWarning.value =
    contentWidth > viewportWidth + 2
      ? `当前样式在${selectedPreviewSize.value.label}宽度下可能横向溢出，建议切换片段检查固定宽度或过大的图片。`
      : ''
}

function setRepresentative(slice: WechatTemplateSlice) {
  const existing = roleDrafts.value.find((item) => item.role === slice.role)
  if (existing) {
    existing.wrapperHtml = slice.html
    existing.sliceIds = [slice.id]
    existing.reuseCount = slices.value.filter((item) => item.role === slice.role).length || 1
    existing.needsConfirmation = false
  } else {
    roleDrafts.value.push({
      role: slice.role,
      wrapperHtml: slice.html,
      sliceIds: [slice.id],
      reuseCount: slices.value.filter((item) => item.role === slice.role).length || 1,
      needsConfirmation: false,
    })
  }
  selectedPreviewKey.value = slice.role
  ElMessage.success(`已将片段 ${slice.order} 设为“${roleLabel(slice.role)}”`)
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }
  if (!roleDrafts.value.length) {
    ElMessage.warning('请先生成样式预览')
    return
  }
  const roles: Record<string, WechatRenderRoleSchema> = {}
  for (const item of roleDrafts.value) {
    if (item.role && item.wrapperHtml) {
      roles[item.role] = {
        wrapperHtml: item.wrapperHtml,
        wrapperSafe: item.wrapperSafe,
      }
    }
  }
  if (!Object.keys(roles).length) {
    ElMessage.warning('至少需要保留一个可用样式')
    return
  }
  saving.value = true
  try {
    if (isEditMode.value && editingTemplateId.value) {
      await updateWechatRenderTemplate(editingTemplateId.value, {
        name: form.name.trim(),
        description: form.description.trim(),
        status: editingTemplate.value?.status === 'disabled' ? 'disabled' : 'enabled',
      })
      await createWechatRenderTemplateVersion(editingTemplateId.value, {
        sourceType: sourceType.value,
        sourceHtml: form.sourceHtml,
        roles,
        bodyStyle: bodyStyle.value,
      })
      ElMessage.success('模板新版本已保存')
    } else {
      await createWechatRenderTemplate({
        name: form.name,
        description: form.description,
        sourceType: sourceType.value,
        sourceHtml: form.sourceHtml,
        roles,
        bodyStyle: bodyStyle.value,
      })
      ElMessage.success('模板已保存')
    }
    router.push('/admin/content/wechat-templates')
  } catch {
    ElMessage.error('保存模板失败')
  } finally {
    saving.value = false
  }
}

async function loadTemplateForEdit() {
  if (!editingTemplateId.value) return
  loadingTemplate.value = true
  try {
    const [{ data: templateData }, { data: versionData }] = await Promise.all([
      getWechatRenderTemplate(editingTemplateId.value),
      getWechatRenderTemplateCurrentVersion(editingTemplateId.value),
    ])
    editingTemplate.value = templateData.data
    editingVersion.value = versionData.data
    form.name = editingTemplate.value?.name || ''
    form.description = editingTemplate.value?.description || ''
    form.sourceHtml = editingVersion.value?.sourceHtml || ''
    applySourceType(editingVersion.value?.sourceType || 'generic')
    const schema = readTemplateSchema(editingVersion.value)
    roleDrafts.value = schemaToRoleDrafts(schema.roles)
    bodyStyle.value = schema.bodyStyle
    selectedPreviewKey.value = preferredPreviewRole(roleDrafts.value)
    previewMode.value = 'full'
  } catch {
    ElMessage.error('加载模板失败')
    router.push('/admin/content/wechat-templates')
  } finally {
    loadingTemplate.value = false
  }
}

function applySourceType(type: string) {
  const channel = channels.find((item) => item.sourceType === type)
  if (channel) {
    importMode.value = 'channel'
    selectedChannel.value = channel.value
    return
  }
  importMode.value = 'generic'
}

function readTemplateSchema(version: PlatformRenderTemplateVersion | null) {
  if (!version?.templateSchemaJson) {
    return { roles: {}, bodyStyle: null as WechatBodyStyle | null }
  }
  try {
    const parsed = JSON.parse(version.templateSchemaJson) as {
      roles?: Record<string, WechatRenderRoleSchema>
      bodyStyle?: WechatBodyStyle | null
      [key: string]: unknown
    }
    const roles = parsed.roles && typeof parsed.roles === 'object' ? parsed.roles : {}
    return {
      roles,
      bodyStyle: parsed.bodyStyle || null,
    }
  } catch {
    return { roles: {}, bodyStyle: null as WechatBodyStyle | null }
  }
}

function schemaToRoleDrafts(roles: Record<string, WechatRenderRoleSchema>) {
  return Object.entries(roles)
    .filter(([, value]) => value?.wrapperHtml)
    .map(([role, value]) => ({
      role,
      wrapperHtml: value.wrapperHtml,
      wrapperSafe: value.wrapperSafe,
      reuseCount: 1,
      sliceIds: [],
      needsConfirmation: false,
    }))
}

onMounted(loadTemplateForEdit)
</script>

<style scoped>
.template-import-page {
  min-height: 100%;
  padding: 24px;
  background: #f6f8fb;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.eyebrow {
  margin: 0 0 6px;
  color: #2f6fed;
  font-size: 13px;
  font-weight: 700;
}

.page-head h1 {
  margin: 0;
  color: #102033;
  font-size: 28px;
  line-height: 1.25;
}

.page-head p:not(.eyebrow) {
  margin: 8px 0 0;
  color: #66758a;
  font-size: 14px;
}

.choice-section {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 14px;
}

.choice-card,
.channel-card {
  border: 1px solid #dce4ef;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.choice-card {
  min-height: 150px;
  padding: 22px;
  border-radius: 10px;
}

.choice-card:hover,
.channel-card:hover:not(.disabled) {
  transform: translateY(-1px);
  border-color: #8db4ff;
  box-shadow: 0 12px 28px rgba(36, 87, 158, 0.08);
}

.choice-card.active,
.channel-card.active {
  border-color: #2f6fed;
  box-shadow: 0 14px 32px rgba(47, 111, 237, 0.14);
}

.choice-card__badge {
  display: inline-flex;
  margin-bottom: 14px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #edf4ff;
  color: #2f6fed;
  font-size: 12px;
  font-weight: 700;
}

.choice-card strong,
.choice-card span,
.choice-card em {
  display: block;
}

.choice-card strong {
  color: #102033;
  font-size: 19px;
  line-height: 1.35;
}

.choice-card span:not(.choice-card__badge) {
  margin-top: 8px;
  color: #66758a;
  font-size: 14px;
  line-height: 1.7;
}

.choice-card em {
  margin-top: 14px;
  color: #32465f;
  font-size: 13px;
  font-style: normal;
  font-weight: 700;
}

.channel-section {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.channel-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px 12px;
  min-height: 92px;
  padding: 16px;
  border-radius: 8px;
}

.channel-card.disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.channel-card__title {
  color: #132235;
  font-size: 16px;
  font-weight: 700;
}

.channel-card__desc {
  grid-column: 1 / -1;
  color: #718096;
  font-size: 13px;
  line-height: 1.6;
}

.workspace {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.panel {
  min-height: 680px;
  padding: 18px;
  border: 1px solid #e1e8f2;
  border-radius: 10px;
  background: #fff;
}

.panel-head,
.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-head h2,
.section-head h3 {
  margin: 0;
  color: #102033;
  font-size: 18px;
}

.panel-head p,
.section-head p {
  margin: 6px 0 0;
  color: #718096;
  font-size: 13px;
  line-height: 1.6;
}

.html-upload {
  width: 100%;
}

:deep(.html-upload .el-upload) {
  width: 100%;
}

:deep(.html-upload .el-upload-dragger) {
  width: 100%;
  padding: 22px 16px;
  background: #f8fbff;
  border-color: #c9d8f2;
}

:deep(.html-upload .el-upload-dragger:hover) {
  border-color: #2563eb;
}

.upload-copy {
  display: grid;
  justify-items: center;
  gap: 8px;
  color: #607086;
  text-align: center;
}

.upload-copy strong {
  color: #102033;
  font-size: 15px;
}

.upload-copy span {
  font-size: 12px;
  line-height: 1.5;
}

.html-file-status {
  display: grid;
  gap: 6px;
  width: 100%;
  padding: 12px 14px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
}

.html-file-status strong {
  color: #166534;
  font-size: 14px;
}

.html-file-status span {
  color: #4d7c0f;
  font-size: 12px;
  line-height: 1.5;
}

.manual-html-collapse {
  margin-bottom: 16px;
  border-top: 0;
  border-bottom: 0;
}

:deep(.manual-html-collapse .el-collapse-item__header) {
  height: 36px;
  color: #64748b;
  font-size: 13px;
  border-bottom: 0;
}

:deep(.manual-html-collapse .el-collapse-item__wrap) {
  border-bottom: 0;
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 2px;
}

.tips {
  display: grid;
  gap: 8px;
  margin-top: 18px;
  padding: 14px;
  border-radius: 8px;
  background: #f2f6fb;
  color: #607086;
  font-size: 13px;
  line-height: 1.6;
}

.tips strong {
  color: #24354a;
}

.review-area {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.warning-summary {
  margin-bottom: 14px;
}

.result-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.review-grid {
  display: grid;
  grid-template-columns: minmax(260px, 360px) minmax(320px, 1fr);
  gap: 18px;
  align-items: start;
}

.style-list {
  display: grid;
  gap: 10px;
}

.style-card {
  padding: 12px;
  border: 1px solid #e4ebf4;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.style-card.active {
  border-color: #2f6fed;
  box-shadow: inset 0 0 0 1px rgba(47, 111, 237, 0.18);
}

.style-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.friendly-select {
  width: 150px;
}

.style-card__meta {
  display: grid;
  gap: 4px;
  margin: 10px 0;
  color: #7b8798;
  font-size: 12px;
}

.slice-list {
  display: grid;
  gap: 10px;
}

.slice-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 150px auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e8eef6;
  border-radius: 8px;
  background: #fbfdff;
}

.slice-row.outlier {
  border-color: #f5c56b;
  background: #fffbeb;
}

.slice-row strong {
  color: #26364a;
  font-size: 14px;
}

.slice-row p {
  overflow: hidden;
  margin: 6px 0 0;
  color: #718096;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slice-role {
  width: 150px;
}

.preview-panel {
  min-width: 0;
}

.preview-size-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 12px;
  color: #607086;
  font-size: 13px;
}

.preview-size-bar span {
  font-weight: 700;
}

.preview-layout-warning {
  max-width: 520px;
  margin: 0 auto 12px;
}

.preview-shell {
  margin: 0 auto;
  padding: 14px;
  border-radius: 30px;
  background: #142033;
  box-shadow: 0 22px 42px rgba(20, 32, 51, 0.18);
  box-sizing: content-box;
  max-width: calc(100% - 28px);
  transition: width 0.2s ease;
}

.phone-bar {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 4px 10px 12px;
  color: #dce6f4;
  font-size: 12px;
}

.phone-bar span:first-child {
  width: 54px;
  height: 5px;
  justify-self: center;
  border-radius: 999px;
  background: #26364f;
}

.preview-shell iframe {
  width: 100%;
  height: 560px;
  border: 0;
  border-radius: 20px;
  background: #fff;
}

.preview-note {
  display: grid;
  gap: 6px;
  margin-top: 14px;
  padding: 14px 16px;
  border: 1px solid #e1e8f2;
  border-radius: 10px;
  background: #fff;
}

.preview-note strong {
  color: #102033;
  font-size: 15px;
}

.preview-note span {
  color: #718096;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 1280px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .source-panel {
    min-height: auto;
  }
}

@media (max-width: 960px) {
  .choice-section,
  .channel-section,
  .workspace,
  .review-grid {
    grid-template-columns: 1fr;
  }

  .slice-row {
    grid-template-columns: 1fr;
  }

  .preview-shell iframe {
    height: 540px;
  }
}
</style>
