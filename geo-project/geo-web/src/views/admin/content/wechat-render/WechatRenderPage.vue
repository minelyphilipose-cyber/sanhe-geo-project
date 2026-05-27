<template>
  <div class="wechat-render-page">
    <header class="page-head">
      <div>
        <div class="page-kicker">公众号排版</div>
        <h1>设置公众号展示效果</h1>
        <p>按顺序选模板、换图片、标重点，右侧会实时显示最终效果。</p>
      </div>
      <div class="head-actions">
        <el-button @click="router.back()">返回</el-button>
        <el-button :loading="previewing" @click="preview">刷新预览</el-button>
        <el-button :loading="finalPreviewing" @click="openFinalPreview">发布前预览</el-button>
        <el-button v-if="canArticleWrite" :loading="saving" type="primary" @click="save">保存样式</el-button>
      </div>
    </header>

    <el-alert
      v-for="item in warnings"
      :key="item.type + item.message + item.blockId"
      :title="item.message"
      type="warning"
      show-icon
      :closable="false"
      class="warning-alert"
    />

    <section class="workspace">
      <aside class="setup-panel">
        <section class="setup-card">
          <div class="card-title">
            <span class="step-badge">1</span>
            <div>
              <strong>选择模板</strong>
              <p>选一个公众号样式，系统会自动套到文章上。</p>
            </div>
          </div>
          <el-select
            v-model="selectedTemplateId"
            class="full-control"
            placeholder="选择公众号模板"
            filterable
            @change="handleTemplateChange"
          >
            <el-option v-for="item in templates" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </section>

        <section class="setup-card">
          <div class="card-title">
            <span class="step-badge">2</span>
            <div>
              <strong>替换图片</strong>
              <p>有图片位时，上传正式发布要用的图片。</p>
            </div>
          </div>
          <el-empty v-if="!imageBlocks.length" description="这篇文章暂无图片位" />
          <div v-for="(block, index) in imageBlocks" :key="blockKey(block)" class="image-slot">
            <div class="image-slot__preview">
              <img :src="imageValue(block) || block.imageUrl || ''" :alt="block.imageAlt || `图片 ${index + 1}`" />
            </div>
            <div class="image-slot__body">
              <strong>图片 {{ index + 1 }}</strong>
              <el-upload
                class="image-upload"
                drag
                action="#"
                accept="image/png,image/jpeg,image/jpg,image/webp,image/gif"
                :auto-upload="false"
                :show-file-list="false"
                :on-change="(file: UploadFile) => handleImageFileChange(block, file)"
              >
                <div class="image-upload__copy">
                  <strong>{{ imageValue(block) ? '已替换图片' : '拖拽或点击上传图片' }}</strong>
                  <span>{{ imageValue(block) ? '已保存到素材库，右侧预览会使用这张图片' : '支持 JPG、PNG、WebP、GIF，10MB 以内' }}</span>
                </div>
              </el-upload>
              <el-collapse class="image-url-fallback">
                <el-collapse-item title="也可以粘贴图片地址" :name="blockKey(block)">
                  <el-input
                    :model-value="imageValue(block)"
                    placeholder="粘贴图片 URL"
                    clearable
                    @input="(value: string) => setImageValue(block, value)"
                  />
                </el-collapse-item>
              </el-collapse>
              <div class="image-slot__actions">
                <el-button text type="primary" @click="setImageValue(block, block.imageUrl || '')">使用原图</el-button>
                <el-button text @click="setImageValue(block, '')">清空替换</el-button>
              </div>
            </div>
          </div>
        </section>

        <section class="setup-card">
          <div class="card-title">
            <span class="step-badge">3</span>
            <div>
              <strong>标出重点段落</strong>
              <p>给重要内容加浅色背景框，让读者更容易看到。</p>
            </div>
          </div>
          <el-alert
            v-if="!highlightRoleAvailable"
            title="当前模板没有重点背景框样式，可以跳过这一步"
            type="info"
            show-icon
            :closable="false"
            class="render-mode-alert"
          />
          <template v-else>
            <div class="quick-action-card">
              <div>
                <strong>智能推荐</strong>
                <p>{{ backgroundBoxSummary }}</p>
              </div>
              <div class="quick-action-card__actions">
                <el-button v-if="canArticleWrite" type="primary" @click="applySuggestedBackgroundBoxes">一键标重点</el-button>
                <el-button v-if="canArticleWrite && backgroundBoxAppliedCount" plain @click="clearBackgroundBoxes">全部取消</el-button>
              </div>
            </div>
            <el-collapse class="simple-collapse">
              <el-collapse-item title="手动选择重点段落" name="background-boxes">
                <div class="box-helper">
                  打开开关后，这段会套用模板里的背景框；关闭后恢复普通正文。
                </div>
                <div v-for="block in backgroundBoxBlocks" :key="blockKey(block)" class="box-row">
                  <div class="box-row__text">
                    <strong>{{ blockDisplayName(block) }}</strong>
                    <span>{{ blockPreviewText(block) }}</span>
                  </div>
                  <el-switch
                    :model-value="isBackgroundBoxApplied(block)"
                    active-text="突出显示"
                    inactive-text="普通"
                    @change="(enabled: string | number | boolean) => toggleBackgroundBox(block, Boolean(enabled))"
                  />
                </div>
                <el-empty v-if="!backgroundBoxBlocks.length" description="暂无可添加背景框的正文段落" />
              </el-collapse-item>
            </el-collapse>
          </template>
        </section>

        <section class="setup-card">
          <div class="card-title">
            <span class="step-badge">4</span>
            <div>
              <strong>正文排版</strong>
              <p>默认使用稳妥排版；模板正文干净时，可以让正文更像示例。</p>
            </div>
          </div>
          <div class="paragraph-wrapper-option">
            <el-switch
              v-model="paragraphWrapperEnabled"
              :disabled="!paragraphWrapperSafe"
              active-text="更像模板示例"
              inactive-text="稳妥排版"
            />
            <el-alert
              :title="paragraphRenderStatus.title"
              :description="paragraphRenderStatus.description"
              :type="paragraphRenderStatus.type"
              show-icon
              :closable="false"
              class="render-mode-alert"
            />
          </div>
        </section>

        <section class="setup-card">
          <div class="card-title">
            <span class="step-badge">5</span>
            <div>
              <strong>结尾引导</strong>
              <p>需要时加到文章最后，不需要可以关闭。</p>
            </div>
          </div>
          <el-switch v-model="endingCtaEnabled" active-text="添加结尾引导" inactive-text="不添加" />
          <el-input
            v-if="endingCtaEnabled"
            :model-value="endingCtaContent"
            class="cta-input"
            type="textarea"
            :rows="3"
            placeholder="例如：欢迎联系了解更多。"
            @input="setEndingCtaContent"
          />
        </section>

      </aside>

      <main class="preview-panel">
        <div class="preview-head">
          <div>
            <strong>实时预览</strong>
            <span>{{ previewing ? '正在更新预览...' : '左侧每一步修改都会自动更新' }}</span>
          </div>
          <div class="preview-actions">
            <el-tag v-if="selectedTemplateVersionId" type="success">已选择模板</el-tag>
            <el-tag v-else type="info">未选择模板</el-tag>
            <el-button size="small" :loading="finalPreviewing" @click="openFinalPreview">发布前预览</el-button>
          </div>
        </div>
        <div class="preview-status">
          <span>{{ previewSummary }}</span>
          <em>保存前可随时调整；发布前预览会使用同一套渲染结果。</em>
        </div>
        <div class="phone-frame" v-loading="loading">
          <div class="phone-top">
            <span />
            <strong>公众号预览</strong>
          </div>
          <iframe :srcdoc="previewSrcdoc"></iframe>
        </div>
      </main>
    </section>

    <el-dialog
      v-model="finalPreviewVisible"
      title="发布前预览"
      width="760px"
      class="final-preview-dialog"
      destroy-on-close
    >
      <el-alert
        v-for="item in finalPreviewWarnings"
        :key="item.type + item.message + item.blockId"
        :title="item.message"
        type="warning"
        show-icon
        :closable="false"
        class="warning-alert"
      />
      <div class="final-preview-body" v-loading="finalPreviewing">
        <div class="dialog-status">
          <span>{{ previewSummary }}</span>
          <em>这是发布前会使用的完整效果。</em>
        </div>
        <div class="phone-frame phone-frame--dialog">
          <div class="phone-top">
            <span />
            <strong>发布前预览</strong>
          </div>
          <iframe :srcdoc="finalPreviewSrcdoc"></iframe>
        </div>
      </div>
      <template #footer>
        <el-button @click="finalPreviewVisible = false">关闭</el-button>
        <el-button :loading="finalPreviewing" type="primary" @click="openFinalPreview">重新生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type UploadFile } from 'element-plus'
import {
  finalPreviewArticleWechatRender,
  getContentArticleDetail,
  getArticleWechatRender,
  getWechatRenderTemplateCurrentVersion,
  getWechatRenderTemplates,
  previewArticleWechatRender,
  saveArticleWechatRender,
  type PlatformRenderTemplate,
  type PlatformRenderTemplateVersion,
  type WechatArticleBlock,
  type WechatRenderAnnotations,
  type WechatRenderRoleSchema,
  type WechatRenderWarning,
} from '@/api/content'
import { useUserStore } from '@/stores/user'
import { getBrandMaterialPreviewUrl, uploadBrandMaterial } from '@/api/customer'

type ImageOverride = {
  imageUrl?: string
  imageAlt?: string
  brandId?: number
  materialId?: number
  materialFileName?: string
  source?: 'material' | 'url'
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const canArticleWrite = computed(() => userStore.hasPermission('content.article.write'))
const articleId = computed(() => Number(route.params.articleId))
const loading = ref(false)
const saving = ref(false)
const previewing = ref(false)
const finalPreviewing = ref(false)
const finalPreviewVisible = ref(false)
const initialized = ref(false)
const templates = ref<PlatformRenderTemplate[]>([])
const currentTemplateVersion = ref<PlatformRenderTemplateVersion | null>(null)
const blocks = ref<WechatArticleBlock[]>([])
const warnings = ref<WechatRenderWarning[]>([])
const articleBrandId = ref<number | null>(null)
const selectedTemplateId = ref<number | null>(null)
const selectedTemplateVersionId = ref<number | null>(null)
const paragraphWrapperEnabled = ref(false)
const previewHtml = ref('<main style="padding:32px;color:#64748b;">请选择模板，系统会在这里显示公众号效果。</main>')
const finalPreviewHtml = ref('<main style="padding:32px;color:#64748b;">请先生成发布前预览。</main>')
const finalPreviewWarnings = ref<WechatRenderWarning[]>([])
let previewTimer: number | undefined

const annotations = reactive<WechatRenderAnnotations>({
  marks: [],
  textMarks: [],
  inserts: [],
})

const imageOverrides = reactive<Record<string, ImageOverride>>({})

const duplicateBlockIds = computed(() => {
  const counts = new Map<string, number>()
  for (const block of blocks.value) {
    counts.set(block.id, (counts.get(block.id) || 0) + 1)
  }
  return new Set([...counts.entries()].filter(([, count]) => count > 1).map(([id]) => id))
})

const imageBlocks = computed(() => blocks.value.filter((block) => block.imageUrl || block.defaultRole === 'image_block' || block.type === 'image'))
const highlightRoleAvailable = computed(() => !!templateRoles.value.highlight_block?.wrapperHtml)
const backgroundBoxBlocks = computed(() =>
  blocks.value.filter((block) =>
    block.allowedRoles.includes('highlight_block')
    && ['paragraph', 'blockquote'].includes(block.defaultRole)
    && !imageBlocks.value.includes(block)
    && block.defaultRole !== 'native_html',
  ),
)
const suggestedBackgroundBoxKeys = computed(() => {
  const keys = new Set<string>()
  let waitingForFirstParagraph = false
  for (const block of blocks.value) {
    if (block.defaultRole === 'heading') {
      waitingForFirstParagraph = true
      continue
    }
    if (!waitingForFirstParagraph) {
      continue
    }
    if (backgroundBoxBlocks.value.includes(block)) {
      keys.add(blockKey(block))
      waitingForFirstParagraph = false
      continue
    }
    if (block.defaultRole !== 'native_html' && !imageBlocks.value.includes(block)) {
      waitingForFirstParagraph = false
    }
  }
  return keys
})
const backgroundBoxAppliedCount = computed(() => backgroundBoxBlocks.value.filter((block) => isBackgroundBoxApplied(block)).length)
const backgroundBoxSummary = computed(() => {
  const suggestedCount = suggestedBackgroundBoxKeys.value.size
  if (!suggestedCount) {
    return '这篇文章暂时没有识别到适合自动标重点的段落。'
  }
  if (backgroundBoxAppliedCount.value) {
    return `已标出 ${backgroundBoxAppliedCount.value} 段重点内容，可以继续手动调整。`
  }
  return `系统建议标出 ${suggestedCount} 段重点内容。`
})

const endingCta = computed(() => annotations.inserts.find((item) => item.role === 'ending_cta'))
const endingCtaEnabled = computed({
  get: () => !!endingCta.value,
  set: (enabled: boolean) => {
    if (enabled) {
      ensureEndingCta()
    } else {
      annotations.inserts = annotations.inserts.filter((item) => item.role !== 'ending_cta')
    }
  },
})
const endingCtaContent = computed(() => endingCta.value?.content || '')
const previewSrcdoc = computed(() => buildPreviewDocument(previewHtml.value))
const finalPreviewSrcdoc = computed(() => buildPreviewDocument(finalPreviewHtml.value))
const templateRoles = computed(() => readTemplateRoles(currentTemplateVersion.value))
const paragraphWrapperSafe = computed(() => templateRoles.value.paragraph?.wrapperSafe === true)
const paragraphRenderStatus = computed(() => {
  if (!selectedTemplateVersionId.value) {
    return {
      type: 'info' as const,
      title: '正文：未选择模板',
      description: '请选择公众号模板后再生成预览。',
    }
  }
  if (!paragraphWrapperSafe.value) {
    return {
      type: 'warning' as const,
      title: '正文：基础排版',
      description: '这个模板的正文样式包含装饰，系统不会整篇套用，避免正文变成卡片。',
    }
  }
  if (paragraphWrapperEnabled.value) {
    return {
      type: 'success' as const,
      title: '正文：已套用模板正文样式',
      description: '普通正文会使用模板的安全正文样式。',
    }
  }
  return {
    type: 'info' as const,
    title: '正文：基础排版',
    description: '模板正文样式可用，但当前未开启；普通正文只应用字号、行高、段距等基础排版。',
  }
})
const previewSummary = computed(() => {
  const items: string[] = []
  if (selectedTemplateVersionId.value) {
    items.push('模板已应用')
  } else {
    items.push('未选择模板')
  }
  const replacedImages = Object.values(imageOverrides).filter((item) => item.imageUrl?.trim()).length
  if (replacedImages) {
    items.push(`已替换 ${replacedImages} 张图片`)
  }
  if (backgroundBoxAppliedCount.value) {
    items.push(`${backgroundBoxAppliedCount.value} 段重点`)
  }
  if (endingCtaEnabled.value) {
    items.push('已添加结尾引导')
  }
  return items.join(' · ')
})
const roleLabels: Record<string, string> = {
  article_title: '文章标题',
  heading: '小标题',
  paragraph: '正文',
  highlight_block: '重点框',
  golden_sentence_block: '整段金句',
  golden_sentence_text: '句子金句',
  quote_block: '引用',
  image_block: '图片',
  native_html: '列表/表格',
  divider: '分割线',
  ending_cta: '结尾引导',
}

function roleLabel(role: string) {
  return roleLabels[role] || role
}

function roleFor(block: WechatArticleBlock) {
  return annotations.marks.find((item) => markMatchesBlock(item, block))?.role || block.defaultRole
}

function markMatchesBlock(mark: { blockId: string; order?: number | null }, block: WechatArticleBlock) {
  if (mark.blockId !== block.id) return false
  if (mark.order == null) return !duplicateBlockIds.value.has(block.id)
  return mark.order === block.order
}

function setRole(block: WechatArticleBlock, role: string) {
  if (role === block.defaultRole) {
    annotations.marks = annotations.marks.filter((item) => !markMatchesBlock(item, block))
    return
  }
  const existing = annotations.marks.find((item) => markMatchesBlock(item, block))
  if (existing) existing.role = role
  else annotations.marks.push({ blockId: block.id, order: block.order, role })
}

function isBackgroundBoxApplied(block: WechatArticleBlock) {
  return roleFor(block) === 'highlight_block'
}

function toggleBackgroundBox(block: WechatArticleBlock, enabled: boolean) {
  setRole(block, enabled ? 'highlight_block' : block.defaultRole)
  schedulePreview()
}

function applySuggestedBackgroundBoxes() {
  if (!highlightRoleAvailable.value) {
    ElMessage.info('当前模板没有重点背景框样式')
    return
  }
  let count = 0
  for (const block of backgroundBoxBlocks.value) {
    if (!suggestedBackgroundBoxKeys.value.has(blockKey(block))) {
      continue
    }
    if (!isBackgroundBoxApplied(block)) {
      setRole(block, 'highlight_block')
      count++
    }
  }
  schedulePreview()
  ElMessage.success(count ? `已为 ${count} 段正文添加重点背景框` : '推荐段落已是重点背景框')
}

function clearBackgroundBoxes() {
  const before = annotations.marks.length
  annotations.marks = annotations.marks.filter((mark) => mark.role !== 'highlight_block')
  if (before !== annotations.marks.length) {
    schedulePreview()
  }
}

function blockKey(block: WechatArticleBlock) {
  return `${block.id}#${block.order}`
}

function blockDisplayName(block: WechatArticleBlock) {
  const role = roleFor(block)
  return roleLabel(role)
}

function blockPreviewText(block: WechatArticleBlock) {
  const text = block.text || block.imageAlt || '图片或结构内容'
  return text.length > 52 ? `${text.slice(0, 52)}...` : text
}

function imageValue(block: WechatArticleBlock) {
  return imageOverrides[blockKey(block)]?.imageUrl || ''
}

function setImageValue(block: WechatArticleBlock, imageUrl: string) {
  const key = blockKey(block)
  if (!imageUrl.trim()) {
    delete imageOverrides[key]
    return
  }
  imageOverrides[key] = {
    imageUrl: imageUrl.trim(),
    imageAlt: block.imageAlt || `图片 ${block.order}`,
    source: 'url',
  }
}

async function handleImageFileChange(block: WechatArticleBlock, file: UploadFile) {
  const rawFile = file.raw
  if (!rawFile) return
  const brandId = articleBrandId.value
  if (!brandId) {
    ElMessage.warning('当前文章未绑定品牌，无法上传到素材库')
    return
  }
  if (!rawFile.type.startsWith('image/')) {
    ElMessage.warning('请上传图片文件')
    return
  }
  if (rawFile.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片请控制在 10MB 以内')
    return
  }
  try {
    const uploadRes = await uploadBrandMaterial(brandId, 'brand_image', rawFile)
    const material = uploadRes.data.data
    const previewRes = await getBrandMaterialPreviewUrl(brandId, material.id)
    imageOverrides[blockKey(block)] = {
      imageUrl: previewRes.data.data.url,
      imageAlt: block.imageAlt || material.fileName || rawFile.name || `图片 ${block.order}`,
      brandId,
      materialId: material.id,
      materialFileName: material.fileName,
      source: 'material',
    }
    schedulePreview()
    ElMessage.success('图片已上传素材库，右侧预览会自动更新')
  } catch {
    ElMessage.error('图片上传失败')
  }
}

function ensureEndingCta() {
  let insert = endingCta.value
  if (!insert) {
    insert = {
      afterBlockId: blocks.value.length ? blocks.value[blocks.value.length - 1].id : null,
      role: 'ending_cta',
      content: '欢迎联系了解更多。',
    }
    annotations.inserts.push(insert)
  }
  return insert
}

function setEndingCtaContent(value: string) {
  const insert = ensureEndingCta()
  insert.content = value
}

function buildRenderConfig(): Record<string, unknown> {
  const overrides: Record<string, ImageOverride> = {}
  for (const [key, value] of Object.entries(imageOverrides)) {
    if (value.imageUrl?.trim()) {
      overrides[key] = {
        imageUrl: value.imageUrl.trim(),
        imageAlt: value.imageAlt || '',
        brandId: value.brandId,
        materialId: value.materialId,
        materialFileName: value.materialFileName,
        source: value.source,
      }
    }
  }
  return {
    imageOverrides: overrides,
    useParagraphWrapper: paragraphWrapperEnabled.value && paragraphWrapperSafe.value,
  }
}

function applyRenderConfig(config?: Record<string, unknown> | null) {
  for (const key of Object.keys(imageOverrides)) {
    delete imageOverrides[key]
  }
  paragraphWrapperEnabled.value = Boolean(config?.useParagraphWrapper)
  const overrides = config?.imageOverrides
  if (!overrides || typeof overrides !== 'object' || Array.isArray(overrides)) return
  for (const [key, value] of Object.entries(overrides as Record<string, unknown>)) {
    if (typeof value === 'string') {
      imageOverrides[key] = { imageUrl: value }
    } else if (value && typeof value === 'object' && !Array.isArray(value)) {
      const data = value as Record<string, unknown>
      imageOverrides[key] = {
        imageUrl: typeof data.imageUrl === 'string' ? data.imageUrl : '',
        imageAlt: typeof data.imageAlt === 'string' ? data.imageAlt : '',
        brandId: typeof data.brandId === 'number' ? data.brandId : undefined,
        materialId: typeof data.materialId === 'number' ? data.materialId : undefined,
        materialFileName: typeof data.materialFileName === 'string' ? data.materialFileName : undefined,
        source: data.source === 'material' || data.source === 'url' ? data.source : undefined,
      }
    }
  }
}

async function refreshMaterialPreviewUrls() {
  const tasks = Object.entries(imageOverrides)
    .filter(([, value]) => value.source === 'material' && value.brandId && value.materialId)
    .map(async ([key, value]) => {
      try {
        const { data } = await getBrandMaterialPreviewUrl(value.brandId!, value.materialId!)
        imageOverrides[key] = {
          ...value,
          imageUrl: data.data.url,
        }
      } catch {
        // Keep the stored URL if refreshing fails; preview can still use it if it has not expired.
      }
    })
  await Promise.all(tasks)
}

async function loadTemplates() {
  const { data } = await getWechatRenderTemplates({ current: 1, size: 100 })
  templates.value = data.data.records || []
}

async function handleTemplateChange() {
  await loadTemplateVersion()
  schedulePreview()
}

async function loadTemplateVersion() {
  if (!selectedTemplateId.value) {
    selectedTemplateVersionId.value = null
    currentTemplateVersion.value = null
    paragraphWrapperEnabled.value = false
    return
  }
  const { data } = await getWechatRenderTemplateCurrentVersion(selectedTemplateId.value)
  currentTemplateVersion.value = data.data || null
  selectedTemplateVersionId.value = currentTemplateVersion.value?.id || null
  if (!paragraphWrapperSafe.value) {
    paragraphWrapperEnabled.value = false
  }
}

function readTemplateRoles(version?: PlatformRenderTemplateVersion | null): Record<string, WechatRenderRoleSchema> {
  if (!version?.templateSchemaJson) return {}
  try {
    const schema = JSON.parse(version.templateSchemaJson) as { roles?: Record<string, WechatRenderRoleSchema> }
    return schema.roles || {}
  } catch {
    return {}
  }
}

async function loadConfig() {
  loading.value = true
  try {
    const [renderRes, detailRes] = await Promise.all([
      getArticleWechatRender(articleId.value),
      getContentArticleDetail(articleId.value),
    ])
    articleBrandId.value = detailRes.data.data.project?.brandId || null
    blocks.value = renderRes.data.data.blocks || []
    warnings.value = renderRes.data.data.warnings || []
    selectedTemplateId.value = renderRes.data.data.templateId || null
    selectedTemplateVersionId.value = renderRes.data.data.templateVersionId || null
    annotations.marks = renderRes.data.data.annotations?.marks || []
    annotations.textMarks = renderRes.data.data.annotations?.textMarks || []
    annotations.inserts = renderRes.data.data.annotations?.inserts || []
    applyRenderConfig(renderRes.data.data.renderConfig)
    await refreshMaterialPreviewUrls()
  } catch {
    ElMessage.error('加载公众号渲染配置失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!canArticleWrite.value) {
    ElMessage.warning('当前账号没有保存排版权限')
    return
  }
  if (!selectedTemplateVersionId.value) {
    ElMessage.warning('请先选择模板')
    return
  }
  saving.value = true
  try {
    const { data } = await saveArticleWechatRender(articleId.value, {
      templateVersionId: selectedTemplateVersionId.value,
      annotations,
      renderConfig: buildRenderConfig(),
    })
    warnings.value = data.data.warnings || []
    applyRenderConfig(data.data.renderConfig)
    ElMessage.success('公众号样式已保存')
    await preview()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function preview() {
  if (!selectedTemplateVersionId.value) {
    previewHtml.value = '<main style="padding:32px;color:#64748b;">请选择模板，系统会在这里显示公众号效果。</main>'
    return
  }
  previewing.value = true
  try {
    const { data } = await previewArticleWechatRender(articleId.value, {
      templateVersionId: selectedTemplateVersionId.value,
      annotations,
      renderConfig: buildRenderConfig(),
    })
    previewHtml.value = data.data.html
    warnings.value = data.data.warnings || []
  } catch {
    ElMessage.error('预览失败')
  } finally {
    previewing.value = false
  }
}

async function openFinalPreview() {
  if (!selectedTemplateVersionId.value) {
    ElMessage.warning('请先选择模板')
    return
  }
  finalPreviewVisible.value = true
  finalPreviewing.value = true
  try {
    const { data } = await finalPreviewArticleWechatRender(articleId.value, {
      templateVersionId: selectedTemplateVersionId.value,
      annotations,
      renderConfig: buildRenderConfig(),
    })
    finalPreviewHtml.value = data.data.html
    finalPreviewWarnings.value = data.data.warnings || []
  } catch {
    ElMessage.error('发布前预览生成失败')
  } finally {
    finalPreviewing.value = false
  }
}

function schedulePreview() {
  if (!initialized.value) return
  window.clearTimeout(previewTimer)
  previewTimer = window.setTimeout(() => {
    void preview()
  }, 350)
}

function buildPreviewDocument(content: string) {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <style>
    html, body { margin: 0; padding: 0; background: #fff; }
    body {
      box-sizing: border-box;
      min-height: 100vh;
      color: #222;
      font-family: -apple-system, BlinkMacSystemFont, "Helvetica Neue", Helvetica, "PingFang SC", "Microsoft YaHei", Arial, sans-serif;
      font-size: 15px;
      line-height: 1.7;
      overflow-x: hidden;
      word-break: break-word;
    }
    main { box-sizing: border-box; width: 100%; padding: 20px 18px 32px; }
    img { max-width: 100%; height: auto; vertical-align: middle; }
    * { box-sizing: border-box; }
  </style>
</head>
<body><main>${content}</main></body>
</html>`
}

watch(
  () => [annotations.marks, annotations.textMarks, annotations.inserts, { ...imageOverrides }, paragraphWrapperEnabled.value],
  schedulePreview,
  { deep: true },
)

onMounted(async () => {
  await Promise.all([loadTemplates(), loadConfig()])
  initialized.value = true
  if (selectedTemplateId.value) {
    await loadTemplateVersion()
  }
  await preview()
})

onBeforeUnmount(() => {
  window.clearTimeout(previewTimer)
})
</script>

<style scoped>
.wechat-render-page {
  min-height: 100%;
  padding: 28px;
  background: #f5f7fb;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
}

.page-kicker {
  margin-bottom: 8px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

.page-head h1 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
}

.page-head p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 14px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.warning-alert {
  margin-bottom: 10px;
}

.workspace {
  display: grid;
  grid-template-columns: 420px minmax(420px, 1fr);
  gap: 18px;
  align-items: start;
}

.setup-panel {
  display: grid;
  gap: 14px;
}

.setup-card {
  padding: 18px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.card-title {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.card-title strong {
  display: block;
  color: #0f172a;
  font-size: 16px;
}

.card-title p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.step-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  color: #2563eb;
  font-weight: 700;
  background: #eff6ff;
  border-radius: 999px;
}

.full-control {
  width: 100%;
}

.image-slot {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 12px;
  padding: 12px 0;
  border-top: 1px solid #f1f5f9;
}

.image-slot:first-of-type {
  border-top: 0;
}

.image-slot__preview {
  width: 84px;
  height: 70px;
  overflow: hidden;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.image-slot__preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-slot__body {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.image-slot__body strong {
  color: #0f172a;
  font-size: 14px;
}

.image-upload {
  width: 100%;
}

.image-upload__copy {
  display: grid;
  gap: 4px;
  justify-items: center;
  padding: 4px;
  text-align: center;
}

.image-upload__copy strong {
  color: #2563eb;
  font-size: 13px;
}

.image-upload__copy span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
}

:deep(.image-upload .el-upload) {
  width: 100%;
}

:deep(.image-upload .el-upload-dragger) {
  width: 100%;
  padding: 12px;
  border-radius: 8px;
}

.image-url-fallback {
  border: 0;
}

:deep(.image-url-fallback .el-collapse-item__header) {
  height: 30px;
  color: #64748b;
  font-size: 12px;
  line-height: 30px;
}

:deep(.image-url-fallback .el-collapse-item__wrap) {
  border: 0;
}

:deep(.image-url-fallback .el-collapse-item__content) {
  padding-bottom: 0;
}

.image-slot__actions {
  display: flex;
  gap: 8px;
}

.cta-input {
  margin-top: 12px;
}

.paragraph-wrapper-option {
  display: grid;
  gap: 10px;
}

.render-mode-alert {
  margin-top: 2px;
}

.quick-action-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.quick-action-card strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
}

.quick-action-card p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.quick-action-card__actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.simple-collapse {
  margin-top: 10px;
  border: 0;
}

:deep(.simple-collapse .el-collapse-item__header) {
  height: 36px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
  line-height: 36px;
}

:deep(.simple-collapse .el-collapse-item__wrap) {
  border-bottom: 0;
}

:deep(.simple-collapse .el-collapse-item__content) {
  padding-bottom: 0;
}

.box-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.box-helper {
  padding: 10px 12px;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.box-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 150px;
  gap: 10px;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid #f1f5f9;
}

.box-row:first-of-type {
  border-top: 0;
}

.box-row__text {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.box-row__text strong {
  color: #0f172a;
  font-size: 13px;
}

.box-row__text span {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-panel {
  position: sticky;
  top: 18px;
  padding: 18px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.preview-head strong,
.preview-head span {
  display: block;
}

.preview-head strong {
  color: #0f172a;
  font-size: 18px;
}

.preview-head span {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.preview-status,
.dialog-status {
  display: grid;
  gap: 2px;
  padding: 10px 12px;
  margin-bottom: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.preview-status span,
.dialog-status span {
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.preview-status em,
.dialog-status em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  line-height: 1.5;
}

.phone-frame {
  width: 390px;
  min-height: 690px;
  padding: 14px 12px 18px;
  margin: 0 auto;
  background: #132033;
  border-radius: 30px;
}

.phone-frame--dialog {
  min-height: 740px;
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

.phone-frame iframe {
  width: 100%;
  height: 620px;
  background: #fff;
  border: 0;
  border-radius: 18px;
}

.phone-frame--dialog iframe {
  height: 670px;
}

.final-preview-body {
  display: grid;
  justify-items: center;
  min-height: 740px;
}

:deep(.final-preview-dialog .el-dialog__body) {
  padding-top: 8px;
}

@media (max-width: 1180px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .preview-panel {
    position: static;
  }
}
</style>
