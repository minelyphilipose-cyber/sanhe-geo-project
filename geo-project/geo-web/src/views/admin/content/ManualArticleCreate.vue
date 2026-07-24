<template>
  <div class="article-import-page">
    <header class="page-toolbar">
      <div class="toolbar-title-wrap">
        <el-button class="back-button" :icon="Back" aria-label="返回" @click="goBack" />
        <div>
          <h1>导入文章</h1>
          <p>粘贴完整文章或上传 DOCX / MD，编辑后保存</p>
        </div>
      </div>
      <div class="toolbar-actions">
        <span class="ready-state" :class="submitStateTone">
          <i />
          {{ submitStateText }}
        </span>
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :icon="Check" :loading="submitting" :disabled="!canSubmit" @click="submitArticle">
          保存文章
        </el-button>
      </div>
    </header>

    <section class="metadata-bar">
      <label class="inline-field project-field" :title="selectedProjectPath">
        <span class="field-label required">绑定项目</span>
        <el-cascader
          v-model="form.projectId"
          :options="projectCascadeOptions"
          :props="projectCascadeProps"
          :loading="projectLoading"
          filterable
          clearable
          placeholder="选择客户 / 品牌 / 项目"
        />
      </label>
      <label class="inline-field channel-field">
        <span class="field-label required">目标发布渠道</span>
        <el-select v-model="form.targetChannelKey" filterable placeholder="选择发布渠道">
          <el-option-group
            v-for="group in targetChannelGroups"
            :key="group.label"
            :label="group.label"
          >
            <el-option
              v-for="item in group.options"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-option-group>
        </el-select>
      </label>
      <div class="import-policy">
        <span>通用文章</span>
        <i />
        <span>仅按目标渠道校验</span>
      </div>
    </section>

    <main class="workspace">
      <section class="editor-column">
        <article class="content-card import-card">
          <header class="card-header">
            <div>
              <strong>添加文章内容</strong>
              <p>可直接粘贴全文，或上传 DOCX / MD</p>
            </div>
            <el-button v-if="importSource" link type="primary" @click="replaceArticleContent">替换内容</el-button>
          </header>

          <div class="import-body">
            <template v-if="!importSource">
              <el-radio-group v-model="sourceMode" class="source-switch">
                <el-radio-button label="paste">粘贴全文</el-radio-button>
                <el-radio-button label="upload">上传文档</el-radio-button>
              </el-radio-group>

              <div v-if="sourceMode === 'paste'" class="paste-import-panel">
                <el-input
                  v-model="pasteDraft"
                  type="textarea"
                  :rows="6"
                  resize="vertical"
                  placeholder="将完整文章粘贴到这里，支持纯文本或 Markdown"
                  @paste="handleSourcePaste"
                />
                <div class="paste-import-actions">
                  <span>粘贴后会识别标题并转入下方编辑区</span>
                  <el-button type="primary" :disabled="!pasteDraft.trim()" @click="importPastedDraft">导入粘贴内容</el-button>
                </div>
              </div>

              <el-upload
                v-else
                ref="uploadRef"
                class="article-uploader"
                drag
                :auto-upload="false"
                :show-file-list="false"
                :disabled="importing"
                accept=".docx,.md"
                :on-change="handleImportFile"
              >
                <el-icon class="upload-icon"><UploadFilled /></el-icon>
                <div class="upload-copy">
                  <strong>{{ importing ? '正在解析文档…' : '拖入 DOCX / MD，或点击选择文件' }}</strong>
                  <span>最大 10MB；仅解析正文，原文件不会保存</span>
                </div>
              </el-upload>
            </template>

            <div v-else class="import-summary">
              <span class="summary-check"><el-icon><Check /></el-icon></span>
              <div class="summary-main">
                <strong>{{ importFileName || importSource }}</strong>
                <span>{{ importSource }} · 可直接编辑并保存</span>
              </div>
            </div>

            <div v-if="importSource && omittedImageCount > 0" class="image-import-notice">
              <el-icon><Picture /></el-icon>
              <span>原文有 <strong>{{ omittedImageCount }}</strong> 张图片未导入，请按需重新补充。</span>
              <el-button link type="primary" @click="openImagePicker('body')">补充图片</el-button>
            </div>
          </div>

          <div v-if="form.title || form.bodyMarkdown" class="editor-body">
            <label class="block-field">
              <span class="field-label required">文章标题</span>
              <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="输入文章标题" />
            </label>

            <div class="media-field cover-field">
              <div class="media-label-row">
                <div>
                  <span class="field-label">文章封面 <em>{{ requiresCover ? '当前渠道必填' : '可选' }}</em></span>
                  <small>{{ coverRequirementHint }}</small>
                </div>
                <el-button size="small" :icon="Picture" @click="openImagePicker('cover')">
                  {{ selectedCoverMaterial ? '更换封面' : '选择封面' }}
                </el-button>
              </div>
              <div v-if="selectedCoverMaterial" class="selected-media">
                <img :src="materialThumbUrl(selectedCoverMaterial)" :alt="selectedCoverMaterial.fileName" />
                <div>
                  <strong>{{ selectedCoverMaterial.fileName }}</strong>
                  <span>已指定为文章封面</span>
                </div>
                <el-button link type="danger" @click="clearSelectedCover">移除封面</el-button>
              </div>
              <div v-else class="cover-auto-hint">
                {{ requiresCover ? '当前渠道要求封面，请从品牌图库选择一张图片。' : '当前渠道不强制封面，可按内容需要补充。' }}
              </div>
            </div>

            <div class="body-editor-head">
              <div>
                <span class="field-label required">文章正文</span>
                <small>可直接粘贴完整纯文本或 Markdown</small>
              </div>
              <el-button size="small" :icon="Picture" @click="openImagePicker('body')">
                在光标处插入图片
              </el-button>
            </div>
            <el-input
              ref="bodyInputRef"
              v-model="form.bodyMarkdown"
              class="article-body-input"
              type="textarea"
              :rows="24"
              maxlength="50000"
              resize="vertical"
              placeholder="在这里粘贴完整文章。支持 Markdown 标题、列表、引用、链接和表格。"
              @paste="handleBodyPaste"
            />
            <div class="editor-foot">
              <span>当前 {{ bodyStats.blocks }} 个内容块 · 净字数 {{ bodyStats.characters }}</span>
              <span :class="{ danger: canonicalMarkdown.length > 50000 }">存储字符 {{ canonicalMarkdown.length }} / 50000</span>
            </div>
          </div>
        </article>
      </section>

      <aside class="preview-column">
        <header class="preview-header">
          <div>
            <strong>内容预览</strong>
            <span>展示最终保存的 Markdown 内容，不代表平台发布样式</span>
          </div>
          <el-radio-group v-model="previewMode" size="small">
            <el-radio-button label="rendered">内容预览</el-radio-button>
            <el-radio-button label="markdown">原始 Markdown</el-radio-button>
          </el-radio-group>
        </header>
        <article class="preview-paper">
          <div class="paper-meta">
            <span>手动导入</span>
            <i />
            <span>{{ selectedTargetChannel?.label || '未选择渠道' }}</span>
            <i />
            <span>{{ selectedProject?.projectName || selectedProject?.brandName || '未绑定项目' }}</span>
            <i />
            <span>{{ todayText }}</span>
          </div>
          <div v-if="canonicalMarkdown && previewMode === 'rendered'" class="markdown-body" v-html="previewHtml" />
          <pre v-else-if="canonicalMarkdown" class="raw-markdown">{{ canonicalMarkdown }}</pre>
          <div v-else class="preview-empty">
            <strong>导入或粘贴文章后在这里预览</strong>
            <span>标题和正文会组合为最终 Markdown。</span>
          </div>
        </article>
        <footer class="preview-footer">
          <span>{{ bodyStats.blocks }} 个内容块 · 净字数 {{ bodyStats.characters }}</span>
          <span :class="submitStateTone">{{ submitStateText }}</span>
        </footer>
      </aside>
    </main>

    <el-dialog v-model="imagePickerVisible" :title="imagePickerTitle" width="860px" append-to-body>
      <p class="image-picker-guide">{{ imagePickerGuide }}</p>
      <div class="image-picker-toolbar">
        <el-select v-model="selectedImageFolderId" :loading="imageFoldersLoading" placeholder="选择文件夹" style="width: 240px">
          <el-option
            v-for="folder in imageFolders"
            :key="folder.id"
            :label="`${folder.folderName}（${folder.materials?.length || folder.materialCount || 0}）`"
            :value="folder.id"
          />
        </el-select>
        <el-input v-model="imageAltText" maxlength="80" placeholder="图片说明" style="width: 260px" />
        <el-button v-if="canUploadMaterial" :icon="UploadFilled" :loading="uploadingImage" :disabled="!selectedImageFolderId" @click="triggerImageUpload">
          上传到当前图库
        </el-button>
        <input ref="imageUploadInput" class="visually-hidden" type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple @change="handleImageUpload" />
        <el-button :loading="imageFoldersLoading" @click="loadImageFolders">刷新</el-button>
      </div>
      <DataState :loading="imageFoldersLoading" :empty="!imageFoldersLoading && imageMaterials.length === 0" empty-text="当前项目品牌图库暂无可用图片">
        <div class="image-grid">
          <button
            v-for="material in imageMaterials"
            :key="material.id"
            type="button"
            class="image-tile"
            :class="{ selected: activePickerMaterialId === material.id }"
            @click="selectImageMaterial(material)"
          >
            <img :src="materialThumbUrl(material)" :alt="material.fileName" loading="lazy" />
            <span>{{ material.fileName }}</span>
          </button>
        </div>
      </DataState>
      <template #footer>
        <el-button @click="imagePickerVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!activePickerMaterial" @click="confirmImagePicker">
          {{ imagePickerConfirmText }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'
import { ElMessage, ElMessageBox, type UploadFile, type UploadInstance } from 'element-plus'
import { Back, Check, Picture, UploadFilled } from '@element-plus/icons-vue'
import type { BrandImageFolder, BrandMaterial, Project } from '@/types'
import {
  createManualContentArticle,
  parseManualArticleImport,
} from '@/api/content'
import { getBrandImageFolders, getBrandMaterialPreviewUrl, uploadBrandMaterial } from '@/api/customer'
import { getProjectDetail, getProjectList } from '@/api/project'
import { useUserStore } from '@/stores/user'
import DataState from '@/components/ui/DataState.vue'
import { errorMessage } from '@/utils/error'
import {
  calculateManualArticleStats,
  composeManualArticleMarkdown,
  evaluateManualArticleSubmission,
  normalizePastedArticle,
  removeSuggestedLeadingTitle,
} from '@/utils/manualArticleImport'

interface ProjectCascadeNode {
  value: string | number
  label: string
  children?: ProjectCascadeNode[]
}

interface TargetChannelOption {
  value: string
  label: string
  groupCode: string
  subCode?: string
}

const MANUAL_ARTICLE_TYPE = 'general_article'
const MAX_IMAGE_UPLOAD_SIZE = 10 * 1024 * 1024
const COVER_REQUIRED_SELF_MEDIA_PLATFORMS = new Set(['wechat', 'toutiao', 'baijiahao', 'netease', 'douyin'])
const targetChannelGroups: Array<{ label: string; options: TargetChannelOption[] }> = [
  {
    label: '内容渠道',
    options: [
      { value: 'agent_site', label: '官网', groupCode: 'agent_site' },
      { value: 'industry_site', label: '行业资讯站', groupCode: 'industry_site' },
      { value: 'forum', label: '平台网站', groupCode: 'forum' },
      { value: 'authority_media', label: '权重媒体平台', groupCode: 'authority_media' },
    ],
  },
  {
    label: '自媒体平台',
    options: [
      { value: 'self_media:wechat', label: '公众号', groupCode: 'self_media', subCode: 'wechat' },
      { value: 'self_media:douyin', label: '抖音图文', groupCode: 'self_media', subCode: 'douyin' },
      { value: 'self_media:baijiahao', label: '百家号', groupCode: 'self_media', subCode: 'baijiahao' },
      { value: 'self_media:zhihu', label: '知乎', groupCode: 'self_media', subCode: 'zhihu' },
      { value: 'self_media:xiaohongshu', label: '小红书', groupCode: 'self_media', subCode: 'xiaohongshu' },
      { value: 'self_media:toutiao', label: '今日头条', groupCode: 'self_media', subCode: 'toutiao' },
      { value: 'self_media:netease', label: '网易', groupCode: 'self_media', subCode: 'netease' },
      { value: 'self_media:sohu', label: '搜狐', groupCode: 'self_media', subCode: 'sohu' },
    ],
  },
]
const targetChannelOptions = targetChannelGroups.flatMap((group) => group.options)

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const markdown = new MarkdownIt({ html: false, linkify: true, breaks: true })

const form = reactive({
  projectId: undefined as number | undefined,
  targetChannelKey: '',
  topic: '',
  title: '',
  bodyMarkdown: '',
})

const submitting = ref(false)
const importing = ref(false)
const initialized = ref(false)
const dirty = ref(false)
const topicAutoSync = ref(true)
const previewMode = ref<'rendered' | 'markdown'>('rendered')
const sourceMode = ref<'paste' | 'upload'>('paste')
const pasteDraft = ref('')
const importSource = ref('')
const importFileName = ref('')
const omittedImageCount = ref(0)
const projectLoading = ref(false)
const projectOptions = ref<Project[]>([])
const uploadRef = ref<UploadInstance>()
const bodyInputRef = ref<{ textarea?: HTMLTextAreaElement } | null>(null)
const imageUploadInput = ref<HTMLInputElement | null>(null)

const imagePickerVisible = ref(false)
const imagePickerPurpose = ref<'body' | 'cover'>('body')
const imageFoldersLoading = ref(false)
const uploadingImage = ref(false)
const imageFolders = ref<BrandImageFolder[]>([])
const selectedImageFolderId = ref<number | null>(null)
const selectedImageMaterialId = ref<number | null>(null)
const selectedCoverMaterialId = ref<number | null>(null)
const pickerCoverMaterialId = ref<number | null>(null)
const imageAltText = ref('')
const imageThumbUrls = ref<Record<number, string | null>>({})
const imagePreviewUrls = ref<Record<string, string>>({})

const projectCascadeProps = { value: 'value', label: 'label', children: 'children', emitPath: false }
const canUploadMaterial = computed(() => userStore.hasPermission('brand.material.upload'))
const projectCascadeOptions = computed(() => buildProjectCascadeOptions(projectOptions.value))
const selectedProject = computed(() => projectOptions.value.find((project) => project.id === form.projectId) || null)
const selectedTargetChannel = computed(() => targetChannelOptions.find((item) => item.value === form.targetChannelKey) || null)
const requiresCover = computed(() => selectedTargetChannel.value?.groupCode === 'self_media'
  && COVER_REQUIRED_SELF_MEDIA_PLATFORMS.has(selectedTargetChannel.value.subCode || ''))
const coverRequirementHint = computed(() => requiresCover.value
  ? `${selectedTargetChannel.value?.label || '当前渠道'}发布要求设置文章封面。`
  : '封面为可选项，也可以在后续发布时按目标平台要求补充。')
const selectedProjectPath = computed(() => {
  const project = selectedProject.value
  return project ? [project.companyName, project.brandName, project.projectName].filter(Boolean).join(' / ') : ''
})
const canonicalMarkdown = computed(() => composeManualArticleMarkdown(form.title, form.bodyMarkdown))
const previewHtml = computed(() => renderPreviewMarkdown(canonicalMarkdown.value))
const bodyStats = computed(() => calculateManualArticleStats(form.bodyMarkdown))
const submissionState = computed(() => evaluateManualArticleSubmission({
  hasProject: Boolean(form.projectId),
  hasArticleType: true,
  hasTargetChannel: Boolean(selectedTargetChannel.value),
  hasTopic: Boolean(form.topic.trim()),
  hasTitle: Boolean(form.title.trim()),
  hasBody: Boolean(form.bodyMarkdown.trim()),
  withinContentLimit: canonicalMarkdown.value.length <= 50000,
  hasRequiredCover: !requiresCover.value || Boolean(selectedCoverMaterialId.value),
}))
const missingRequiredCount = computed(() => submissionState.value.missingRequiredCount)
const canSubmit = computed(() => (
  submissionState.value.canSubmit
  && !submitting.value
  && !importing.value
))
const submitStateText = computed(() => {
  if (importing.value) return '正在解析'
  if (submitting.value) return '正在保存'
  if (missingRequiredCount.value) return `待完善 ${missingRequiredCount.value} 项`
  return '可直接保存'
})
const submitStateTone = computed(() => canSubmit.value ? 'ready' : 'muted')
const todayText = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}.${String(now.getMonth() + 1).padStart(2, '0')}.${String(now.getDate()).padStart(2, '0')}`
})

const imageMaterials = computed(() => {
  const folder = imageFolders.value.find((item) => item.id === selectedImageFolderId.value)
  return (folder?.materials || []).filter((material) => material.category === 'brand_image' && isImageType(material.fileType) && Boolean(material.publicUrl))
})
const allImageMaterials = computed(() => imageFolders.value.flatMap((folder) => folder.materials || [])
  .filter((material) => material.category === 'brand_image' && isImageType(material.fileType) && Boolean(material.publicUrl)))
const selectedCoverMaterial = computed(() => allImageMaterials.value.find((item) => item.id === selectedCoverMaterialId.value) || null)
const activePickerMaterialId = computed(() => imagePickerPurpose.value === 'cover'
  ? pickerCoverMaterialId.value
  : selectedImageMaterialId.value)
const activePickerMaterial = computed(() => allImageMaterials.value.find((item) => item.id === activePickerMaterialId.value) || null)
const imagePickerTitle = computed(() => imagePickerPurpose.value === 'cover' ? '选择文章封面' : '补充正文图片')
const imagePickerGuide = computed(() => imagePickerPurpose.value === 'cover'
  ? '选择一张品牌图库图片作为文章封面。新上传图片会保存在当前品牌图库中。'
  : '选择图片后会插入到正文当前光标位置。新上传图片会保存到当前品牌图库，供后续文章继续使用。')
const imagePickerConfirmText = computed(() => imagePickerPurpose.value === 'cover' ? '设为文章封面' : '插入正文光标处')

async function loadProjectOptions() {
  projectLoading.value = true
  try {
    const { data } = await getProjectList({ current: 1, size: 500, status: 'active', excludeThirdPartySource: true })
    projectOptions.value = filterSelectableProjects(data.data.records || [])
  } catch (err) {
    console.error(err)
    ElMessage.error('加载项目失败')
  } finally {
    projectLoading.value = false
  }
}

async function loadInheritedProject(projectId: number) {
  try {
    const { data } = await getProjectDetail(projectId)
    if (data.data.thirdPartySource === true) {
      ElMessage.warning('第三方信源项目不支持导入文章，请重新选择客户项目')
      return
    }
    projectOptions.value = mergeProjects([data.data], projectOptions.value)
    form.projectId = data.data.id
  } catch (err) {
    console.error(err)
    ElMessage.warning('继承的项目加载失败，请重新选择项目')
  }
}

async function handleImportFile(uploadFile: UploadFile) {
  const file = uploadFile.raw
  if (!file) return
  try {
    if (!/\.(docx|md)$/i.test(file.name)) {
      ElMessage.warning('仅支持 DOCX 和 MD 文件')
      return
    }
    if (file.size > 10 * 1024 * 1024) {
      ElMessage.warning('导入文件不能超过 10MB')
      return
    }
    if (form.bodyMarkdown.trim() || form.title.trim()) {
      await ElMessageBox.confirm('重新导入会覆盖当前标题和正文，是否继续？', '覆盖当前内容', {
        type: 'warning',
        confirmButtonText: '继续导入',
        cancelButtonText: '取消',
      })
    }
    importing.value = true
    const { data } = await parseManualArticleImport(file)
    const result = data.data
    const resolvedTitle = result.title || result.suggestedTitle || ''
    form.title = resolvedTitle
    form.bodyMarkdown = result.title
      ? (result.contentMarkdown || '')
      : removeSuggestedLeadingTitle(result.contentMarkdown || '', resolvedTitle)
    importFileName.value = file.name
    importSource.value = `${result.format.toUpperCase()} 解析完成`
    omittedImageCount.value = result.stats?.omittedImages || imageOmissionCount(result.warnings)
    pasteDraft.value = ''
    if (topicAutoSync.value) form.topic = form.title
    dirty.value = true
    ElMessage.success('文档导入完成')
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
    console.error(err)
    ElMessage.error(errorMessage(err, '文档解析失败，请检查文件格式'))
  } finally {
    importing.value = false
    uploadRef.value?.clearFiles()
  }
}

function handleBodyPaste(event: ClipboardEvent) {
  if (form.bodyMarkdown.trim()) return
  const text = event.clipboardData?.getData('text/plain') || ''
  if (!text.trim()) return
  event.preventDefault()
  applyPastedArticle(text)
}

async function handleSourcePaste(event: ClipboardEvent) {
  const text = event.clipboardData?.getData('text/plain') || ''
  if (!text.trim()) return
  event.preventDefault()
  pasteDraft.value = text
  try {
    await confirmContentReplacement()
    applyPastedArticle(text)
    pasteDraft.value = ''
  } catch {
    // Keep the pasted content in the source box so the user can retry without pasting again.
  }
}

async function importPastedDraft() {
  const text = pasteDraft.value
  if (!text.trim()) return
  try {
    await confirmContentReplacement()
    applyPastedArticle(text)
    pasteDraft.value = ''
  } catch {
    // User cancelled replacement.
  }
}

async function confirmContentReplacement() {
  if (!form.bodyMarkdown.trim() && !form.title.trim()) return
  await ElMessageBox.confirm('导入新内容会覆盖当前标题和正文，是否继续？', '替换当前内容', {
    type: 'warning',
    confirmButtonText: '继续导入',
    cancelButtonText: '取消',
  })
}

function applyPastedArticle(text: string) {
  const result = normalizePastedArticle(text)
  const resolvedTitle = result.title || result.suggestedTitle
  form.title = resolvedTitle
  form.bodyMarkdown = result.title
    ? result.bodyMarkdown
    : removeSuggestedLeadingTitle(result.bodyMarkdown, resolvedTitle)
  importSource.value = '粘贴导入'
  importFileName.value = ''
  omittedImageCount.value = result.omittedImages
  if (topicAutoSync.value) form.topic = form.title
  dirty.value = true
}

function replaceArticleContent() {
  sourceMode.value = importFileName.value ? 'upload' : 'paste'
  importSource.value = ''
}

async function openImagePicker(purpose: 'body' | 'cover') {
  if (!selectedProject.value?.brandId) {
    ElMessage.warning(`请先在页面上方选择绑定项目，再${purpose === 'cover' ? '选择封面' : '补充图片'}`)
    return
  }
  imagePickerPurpose.value = purpose
  if (purpose === 'cover') pickerCoverMaterialId.value = selectedCoverMaterialId.value
  imagePickerVisible.value = true
  if (!imageFolders.value.length) await loadImageFolders()
  if (purpose === 'body' && !selectedImageMaterialId.value) {
    selectedImageMaterialId.value = imageMaterials.value[0]?.id || null
  }
}

async function loadImageFolders() {
  const project = selectedProject.value
  if (!project?.brandId) return
  imageFoldersLoading.value = true
  try {
    const { data } = await getBrandImageFolders(project.brandId, { projectId: project.id, activeOnly: true, includeMaterials: true })
    imageFolders.value = data.data || []
    if (!imageFolders.value.some((folder) => folder.id === selectedImageFolderId.value)) {
      selectedImageFolderId.value = imageFolders.value[0]?.id || null
    }
    if (!allImageMaterials.value.some((material) => material.id === selectedImageMaterialId.value)) {
      selectedImageMaterialId.value = imageMaterials.value[0]?.id || null
    }
    await loadImageThumbs(project.brandId)
  } catch (err) {
    console.error(err)
    ElMessage.error('加载品牌图库失败')
  } finally {
    imageFoldersLoading.value = false
  }
}

function selectImageMaterial(material: BrandMaterial) {
  if (imagePickerPurpose.value === 'cover') {
    pickerCoverMaterialId.value = material.id
  } else {
    selectedImageMaterialId.value = material.id
  }
  if (!imageAltText.value.trim()) imageAltText.value = filenameWithoutExt(material.fileName)
}

function confirmImagePicker() {
  const material = activePickerMaterial.value
  if (!material?.publicUrl) return
  if (imagePickerPurpose.value === 'cover') {
    selectedCoverMaterialId.value = material.id
    dirty.value = true
    ElMessage.success('文章封面已选择')
    imagePickerVisible.value = false
    return
  }
  insertMarkdownAtCursor(`![${escapeMarkdownAlt(imageAltText.value.trim() || filenameWithoutExt(material.fileName))}](${material.publicUrl})`)
  ElMessage.success('图片已插入正文光标位置')
  imagePickerVisible.value = false
}

function clearSelectedCover() {
  selectedCoverMaterialId.value = null
  dirty.value = true
}

function triggerImageUpload() {
  if (!selectedImageFolderId.value) {
    ElMessage.warning('请先选择一个品牌图库文件夹')
    return
  }
  imageUploadInput.value?.click()
}

async function handleImageUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  const project = selectedProject.value
  const folderId = selectedImageFolderId.value
  if (!files.length || !project?.brandId || !folderId) return
  const accepted = files.filter((file) => {
    if (!['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)) {
      ElMessage.warning(`文件“${file.name}”不是支持的图片格式`)
      return false
    }
    if (file.size > MAX_IMAGE_UPLOAD_SIZE) {
      ElMessage.warning(`图片“${file.name}”超过 10MB`)
      return false
    }
    return true
  })
  if (!accepted.length) return
  uploadingImage.value = true
  try {
    let lastMaterialId: number | null = null
    for (const file of accepted) {
      const { data } = await uploadBrandMaterial(project.brandId, 'brand_image', file, folderId)
      lastMaterialId = data.data.id
    }
    await loadImageFolders()
    if (lastMaterialId && allImageMaterials.value.some((material) => material.id === lastMaterialId)) {
      if (imagePickerPurpose.value === 'cover') {
        pickerCoverMaterialId.value = lastMaterialId
      } else {
        selectedImageMaterialId.value = lastMaterialId
      }
      const material = allImageMaterials.value.find((item) => item.id === lastMaterialId)
      imageAltText.value = filenameWithoutExt(material?.fileName)
    }
    ElMessage.success(`已上传 ${accepted.length} 张图片，请选择后${imagePickerPurpose.value === 'cover' ? '设为封面' : '插入正文'}`)
  } catch (err) {
    console.error(err)
    ElMessage.error(errorMessage(err, '图片上传失败'))
  } finally {
    uploadingImage.value = false
  }
}

function insertMarkdownAtCursor(markdownText: string) {
  const textarea = bodyInputRef.value?.textarea
  const start = textarea?.selectionStart ?? form.bodyMarkdown.length
  const end = textarea?.selectionEnd ?? start
  const before = form.bodyMarkdown.slice(0, start)
  const after = form.bodyMarkdown.slice(end)
  const prefix = before && !before.endsWith('\n\n') ? (before.endsWith('\n') ? '\n' : '\n\n') : ''
  const suffix = after && !after.startsWith('\n\n') ? (after.startsWith('\n') ? '\n' : '\n\n') : ''
  form.bodyMarkdown = `${before}${prefix}${markdownText}${suffix}${after}`
  const cursor = before.length + prefix.length + markdownText.length
  nextTick(() => {
    textarea?.focus()
    textarea?.setSelectionRange(cursor, cursor)
  })
}

async function loadImageThumbs(brandId: number) {
  imageThumbUrls.value = {}
  imagePreviewUrls.value = {}
  const materials = allImageMaterials.value
  let cursor = 0
  const worker = async () => {
    while (cursor < materials.length) {
      const material = materials[cursor++]
      try {
        const { data } = await getBrandMaterialPreviewUrl(brandId, material.id)
        imageThumbUrls.value = { ...imageThumbUrls.value, [material.id]: data.data.url }
        if (material.fileUrl) imagePreviewUrls.value = { ...imagePreviewUrls.value, [material.fileUrl]: data.data.url }
      } catch {
        imageThumbUrls.value = { ...imageThumbUrls.value, [material.id]: null }
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(6, materials.length) }, () => worker()))
}

function renderPreviewMarkdown(content: string) {
  const html = markdown.render(content)
  if (!Object.keys(imagePreviewUrls.value).length) return html
  const template = document.createElement('template')
  template.innerHTML = html
  template.content.querySelectorAll('img').forEach((image) => {
    const previewUrl = imagePreviewUrls.value[image.getAttribute('src') || '']
    if (previewUrl) image.setAttribute('src', previewUrl)
  })
  return template.innerHTML
}

async function submitArticle() {
  const targetChannel = selectedTargetChannel.value
  if (!canSubmit.value || !form.projectId || !targetChannel) return
  submitting.value = true
  try {
    const { data } = await createManualContentArticle({
      projectId: form.projectId,
      articleType: MANUAL_ARTICLE_TYPE,
      channelGroupCode: targetChannel.groupCode,
      channelSubCode: targetChannel.subCode,
      topic: form.topic.trim(),
      title: form.title.trim(),
      contentMarkdown: canonicalMarkdown.value,
      coverMaterialId: selectedCoverMaterialId.value || undefined,
      source: 'manual',
    })
    dirty.value = false
    ElMessage.success('文章已直接保存，可进入发布流程')
    await router.push({
      path: '/admin/content/execution',
      query: { projectId: String(form.projectId), articleType: MANUAL_ARTICLE_TYPE, articleId: String(data.data.id) },
    })
  } catch (err) {
    console.error(err)
    ElMessage.error(errorMessage(err, '保存失败，请重试'))
  } finally {
    submitting.value = false
  }
}

function buildProjectCascadeOptions(projects: Project[]): ProjectCascadeNode[] {
  const companies = new Map<string, ProjectCascadeNode>()
  const brands = new Map<string, ProjectCascadeNode>()
  const sorted = [...projects].sort((a, b) => compareText(a.companyName, b.companyName)
    || compareText(a.brandName, b.brandName)
    || compareText(a.projectName, b.projectName)
    || a.id - b.id)
  for (const project of sorted) {
    const companyKey = `company:${project.companyId ?? 'none'}:${project.companyName || '未归属客户'}`
    let company = companies.get(companyKey)
    if (!company) {
      company = { value: companyKey, label: project.companyName || '未归属客户', children: [] }
      companies.set(companyKey, company)
    }
    const brandKey = `${companyKey}:brand:${project.brandId ?? 'none'}:${project.brandName || '未绑定品牌'}`
    let brand = brands.get(brandKey)
    if (!brand) {
      brand = { value: brandKey, label: project.brandName || '未绑定品牌', children: [] }
      brands.set(brandKey, brand)
      company.children?.push(brand)
    }
    brand.children?.push({ value: project.id, label: project.projectName || `项目 #${project.id}` })
  }
  return Array.from(companies.values())
}

function filterSelectableProjects(projects: Project[]) {
  return projects.filter((project) => project.thirdPartySource !== true)
}

function mergeProjects(primary: Project[], secondary: Project[]) {
  const map = new Map<number, Project>()
  filterSelectableProjects([...secondary, ...primary]).forEach((project) => map.set(project.id, project))
  return Array.from(map.values())
}

function compareText(a?: string | null, b?: string | null) {
  return (a || '').localeCompare(b || '', 'zh-Hans-CN')
}

function imageOmissionCount(warnings?: Array<{ code: string; count?: number | null }>) {
  return warnings?.find((warning) => ['EMBEDDED_IMAGES_OMITTED', 'IMAGES_OMITTED'].includes(warning.code))?.count || 0
}

function materialThumbUrl(material: BrandMaterial) {
  return imageThumbUrls.value[material.id] || material.publicUrl || ''
}

function isImageType(fileType?: string | null) {
  return Boolean(fileType && ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(fileType.toLowerCase()))
}

function filenameWithoutExt(fileName?: string | null) {
  return (fileName?.trim() || '品牌图片').replace(/\.[^.]+$/, '')
}

function escapeMarkdownAlt(value: string) {
  return value.replace(/[[\]]/g, '')
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/admin/content/execution')
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!dirty.value || submitting.value) return
  event.preventDefault()
  event.returnValue = ''
}

watch(() => form.title, (title) => {
  if (topicAutoSync.value) form.topic = title
})

watch(() => form.projectId, () => {
  imageFolders.value = []
  selectedImageFolderId.value = null
  selectedImageMaterialId.value = null
  selectedCoverMaterialId.value = null
  pickerCoverMaterialId.value = null
  imageThumbUrls.value = {}
  imagePreviewUrls.value = {}
})

watch(selectedImageFolderId, () => {
  if (!imageMaterials.value.some((material) => material.id === selectedImageMaterialId.value)) {
    selectedImageMaterialId.value = imageMaterials.value[0]?.id || null
  }
})

watch(form, () => {
  if (initialized.value && !submitting.value) dirty.value = true
}, { deep: true })

onBeforeRouteLeave(async () => {
  if (!dirty.value || submitting.value) return true
  try {
    await ElMessageBox.confirm('当前文章尚未保存，确认离开此页面？', '未保存的内容', {
      type: 'warning', confirmButtonText: '确认离开', cancelButtonText: '继续编辑',
    })
    return true
  } catch {
    return false
  }
})

onMounted(async () => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  await loadProjectOptions()
  const projectId = Number(route.query.projectId || 0)
  if (projectId > 0) await loadInheritedProject(projectId)
  dirty.value = false
  initialized.value = true
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  imageThumbUrls.value = {}
  imagePreviewUrls.value = {}
})
</script>

<style scoped>
.article-import-page {
  min-height: 100%;
  padding: 0 28px 48px;
  color: #1f2a44;
  background: #f6f8fb;
}

.page-toolbar {
  position: sticky;
  top: 0;
  z-index: 12;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 84px;
  margin: 0 -28px;
  padding: 0 36px;
  border-bottom: 1px solid #e4e9f0;
  background: rgba(250, 251, 253, 0.96);
  backdrop-filter: blur(16px);
}

.toolbar-title-wrap,
.toolbar-actions,
.card-header > div,
.ready-state,
.paper-meta,
.editor-foot,
.preview-footer,
.media-label-row,
.body-editor-head,
.image-picker-toolbar {
  display: flex;
  align-items: center;
}

.toolbar-title-wrap { gap: 14px; }
.toolbar-title-wrap h1 { margin: 0; font-size: 24px; line-height: 1.2; color: #17233c; }
.toolbar-title-wrap p { margin: 6px 0 0; color: #7a89a4; font-size: 13px; }
.back-button { width: 42px; height: 42px; border-color: #cfe0f5; color: #2563eb; }
.toolbar-actions { gap: 12px; }
.ready-state { gap: 7px; padding-right: 6px; color: #7b89a2; font-size: 13px; }
.ready-state i { width: 8px; height: 8px; border-radius: 50%; background: #f59e0b; }
.ready-state.ready { color: #047857; }
.ready-state.ready i { background: #10b981; }
.ready-state.warning { color: #a16207; }
.ready-state.warning i { background: #f59e0b; }
.ready-state.muted i { background: #94a3b8; }

.metadata-bar {
  display: grid;
  grid-template-columns: minmax(360px, 1.15fr) minmax(280px, .85fr) auto;
  align-items: center;
  gap: 24px;
  margin: 24px 0;
  padding: 14px 18px;
  border: 1px solid #e1e7ef;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 4px 16px rgba(39, 55, 80, 0.035);
}

.inline-field { display: flex; align-items: center; min-width: 0; }
.inline-field :deep(.el-cascader), .inline-field :deep(.el-select), .inline-field :deep(.el-input) { flex: 1; min-width: 0; }
.field-label { margin-right: 10px; color: #46546c; font-size: 13px; font-weight: 650; white-space: nowrap; }
.field-label.required::before { content: '*'; margin-right: 3px; color: #ef4444; }
.import-policy { display: flex; align-items: center; justify-content: flex-end; gap: 10px; color: #718096; font-size: 12px; }
.import-policy span:first-child { padding: 5px 9px; border-radius: 999px; background: #edf3fb; color: #526a88; font-weight: 650; }
.import-policy i { width: 4px; height: 4px; border-radius: 50%; background: #bdc7d5; }

.workspace {
  display: grid;
  grid-template-columns: minmax(640px, 1.08fr) minmax(480px, .92fr);
  gap: 28px;
  align-items: start;
}

.editor-column { display: grid; gap: 22px; }
.content-card,
.preview-column {
  overflow: hidden;
  border: 1px solid #e0e6ee;
  border-radius: 15px;
  background: #fff;
  box-shadow: 0 8px 28px rgba(39, 55, 80, 0.05);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 66px;
  padding: 0 22px;
  border-bottom: 1px solid #e8edf3;
  background: #fbfcfe;
}
.card-header > div { flex-wrap: wrap; gap: 10px; }
.card-header strong { font-size: 16px; color: #1d2941; }
.card-header p { margin: 0; color: #8190aa; font-size: 12px; }
.import-body { padding: 18px 22px 22px; }
.source-switch { margin-bottom: 14px; }
.paste-import-panel { display: grid; gap: 12px; }
.paste-import-panel :deep(.el-textarea__inner) { padding: 15px; line-height: 1.7; }
.paste-import-actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.paste-import-actions span { color: #7c8aa2; font-size: 12px; }
.article-uploader :deep(.el-upload) { width: 100%; }
.article-uploader :deep(.el-upload-dragger) {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  width: 100%;
  height: 112px;
  border: 1.5px dashed #9fc3f5;
  border-radius: 14px;
  background: #f8fbff;
}
.article-uploader :deep(.el-upload-dragger:hover) { border-color: #2563eb; background: #f3f8ff; }
.upload-icon { margin: 0; color: #3b82f6; font-size: 34px; }
.upload-copy { display: grid; gap: 7px; text-align: left; }
.upload-copy strong { color: #263650; font-size: 15px; }
.upload-copy span { color: #8491a7; font-size: 12px; }
.import-summary {
  display: grid;
  grid-template-columns: auto minmax(170px, 1fr);
  align-items: center;
  gap: 12px;
  padding: 13px 14px;
  border: 1px solid #cfe4dc;
  border-radius: 12px;
  background: #f6fcf9;
}
.summary-check { display: inline-grid; place-items: center; width: 34px; height: 34px; border-radius: 10px; background: #dff7ec; color: #059669; }
.summary-main { display: grid; gap: 4px; min-width: 0; }
.summary-main strong { overflow: hidden; color: #244238; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.summary-main span { color: #71877f; font-size: 12px; }
.image-import-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 9px 12px;
  border-radius: 9px;
  background: #fff8e8;
  color: #8a6117;
  font-size: 12px;
}
.image-import-notice > .el-icon { color: #d6941c; font-size: 16px; }
.image-import-notice strong { color: #8a4f0b; font-size: 13px; }
.image-import-notice .el-button { margin-left: auto; }

.editor-body { display: grid; gap: 24px; padding: 26px 28px 28px; border-top: 1px solid #edf1f5; }
.block-field { display: grid; gap: 9px; }

.media-field { display: grid; gap: 10px; padding: 14px; border: 1px solid #e0eaf5; border-radius: 13px; background: #fbfdff; }
.media-label-row, .body-editor-head { justify-content: space-between; }
.media-label-row > div { display: grid; gap: 4px; }
.media-label-row small { color: #8794a9; font-size: 12px; line-height: 1.5; }
.field-label em { margin-left: 6px; color: #8794a9; font-size: 12px; font-style: normal; font-weight: 400; }
.cover-auto-hint { padding: 10px 12px; border-radius: 8px; background: #f1f6ff; color: #60718b; font-size: 12px; line-height: 1.5; }
.selected-media { display: grid; grid-template-columns: 72px 1fr auto; gap: 12px; align-items: center; }
.selected-media img { width: 72px; height: 52px; border-radius: 8px; object-fit: cover; background: #eef3f8; }
.selected-media > div { display: grid; gap: 4px; min-width: 0; }
.selected-media strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.selected-media span { color: #8794a9; font-size: 12px; }
.body-editor-head > div { display: flex; align-items: center; gap: 8px; }
.body-editor-head small { color: #8a97ac; }
.article-body-input :deep(.el-textarea__inner) { min-height: 500px !important; padding: 18px; line-height: 1.75; font-family: 'JetBrains Mono', 'Noto Sans SC', monospace; font-size: 14px; }
.editor-foot, .preview-footer { justify-content: space-between; color: #8794aa; font-size: 12px; }
.editor-foot .danger { color: #dc2626; }

.preview-column { position: sticky; top: 106px; }
.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  padding: 0 16px;
  border-bottom: 1px solid #dfeaf6;
  background: #fbfdff;
}
.preview-header > div { display: grid; gap: 3px; }
.preview-header strong { color: #2563eb; font-size: 14px; }
.preview-header span { max-width: 360px; color: #8b98ad; font-size: 12px; line-height: 1.45; }
.preview-paper { min-height: 680px; max-height: calc(100vh - 260px); overflow: auto; padding: 46px 52px 60px; background: white; }
.paper-meta { gap: 10px; padding-bottom: 15px; border-bottom: 1px solid #ebeff5; color: #77869f; font-size: 12px; }
.paper-meta i { width: 4px; height: 4px; border-radius: 50%; background: #a5b2c5; }
.preview-footer { padding: 14px 16px; border-top: 1px solid #e3ecf6; background: #fbfdff; }
.preview-footer span:last-child { color: #8a97aa; }
.preview-footer span.ready { color: #059669; }
.preview-footer span.warning { color: #a16207; }
.raw-markdown { margin: 26px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; color: #334155; font: 13px/1.75 'JetBrains Mono', monospace; }
.preview-empty { display: grid; place-items: center; align-content: center; gap: 10px; min-height: 520px; text-align: center; color: #94a3b8; }
.preview-empty strong { color: #53647d; font-size: 16px; }

.markdown-body { margin-top: 28px; color: #24314a; font-size: 15px; line-height: 1.85; word-break: break-word; }
.markdown-body :deep(h1) { margin: 0 0 30px; color: #16233b; font-size: 30px; line-height: 1.3; }
.markdown-body :deep(h2) { margin: 30px 0 14px; color: #1b2941; font-size: 21px; line-height: 1.45; }
.markdown-body :deep(h3) { margin: 24px 0 12px; font-size: 18px; }
.markdown-body :deep(p) { margin: 0 0 16px; }
.markdown-body :deep(img) { display: block; max-width: 100%; margin: 22px auto; border-radius: 10px; }
.markdown-body :deep(blockquote) { margin: 18px 0; padding: 12px 16px; border-left: 4px solid #60a5fa; background: #f5f9ff; color: #51627a; }
.markdown-body :deep(table) { width: 100%; margin: 18px 0; border-collapse: collapse; font-size: 13px; }
.markdown-body :deep(th), .markdown-body :deep(td) { padding: 9px 10px; border: 1px solid #d9e3ef; text-align: left; }
.markdown-body :deep(th) { background: #f3f7fb; }
.markdown-body :deep(a) { color: #2563eb; }

.image-picker-guide { margin: -4px 0 16px; padding: 10px 12px; border-radius: 8px; background: #f3f6fa; color: #65758b; font-size: 12px; line-height: 1.6; }
.image-picker-toolbar { flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.image-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; max-height: 440px; overflow: auto; }
.image-tile { overflow: hidden; padding: 0; border: 2px solid transparent; border-radius: 11px; background: #f5f7fa; cursor: pointer; text-align: left; }
.image-tile.selected { border-color: #2563eb; box-shadow: 0 0 0 2px rgba(37, 99, 235, .12); }
.image-tile img { display: block; width: 100%; height: 120px; object-fit: cover; }
.image-tile span { display: block; overflow: hidden; padding: 9px; color: #526078; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }

.article-import-page :deep(.el-input__wrapper),
.article-import-page :deep(.el-textarea__inner) { box-shadow: 0 0 0 1px #dbe5f0 inset; }
.article-import-page :deep(.el-input__wrapper.is-focus),
.article-import-page :deep(.el-textarea__inner:focus) { box-shadow: 0 0 0 1px #3b82f6 inset, 0 0 0 3px rgba(59, 130, 246, .1); }
.visually-hidden { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }

@media (max-width: 1280px) {
  .metadata-bar { grid-template-columns: minmax(340px, 1fr) minmax(260px, .8fr); }
  .import-policy { grid-column: 1 / -1; justify-content: flex-start; }
  .workspace { grid-template-columns: minmax(540px, 1fr) minmax(400px, .8fr); }
  .preview-paper { padding: 34px 32px 48px; }
}

@media (max-width: 980px) {
  .article-import-page { padding-inline: 14px; }
  .page-toolbar { position: static; margin-inline: -14px; padding: 14px 18px; gap: 14px; flex-wrap: wrap; }
  .toolbar-actions { margin-left: auto; }
  .workspace { grid-template-columns: 1fr; }
  .preview-column { position: static; }
  .preview-paper { min-height: 520px; max-height: none; }
}

@media (max-width: 720px) {
  .metadata-bar { grid-template-columns: 1fr; }
  .import-policy { justify-content: flex-start; }
  .inline-field { display: grid; gap: 7px; }
  .field-label { margin-right: 0; }
  .ready-state { display: none; }
  .card-header { align-items: flex-start; padding-block: 14px; }
  .card-header p { flex-basis: 100%; }
  .paste-import-actions { align-items: flex-start; flex-direction: column; }
  .import-summary { grid-template-columns: auto minmax(0, 1fr); }
  .image-import-notice { align-items: flex-start; flex-wrap: wrap; }
  .image-import-notice .el-button { margin-left: 24px; }
  .selected-media { grid-template-columns: 58px 1fr auto; }
  .selected-media img { width: 58px; }
  .preview-paper { padding: 28px 20px 40px; }
  .image-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
