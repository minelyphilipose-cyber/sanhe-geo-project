<template>
  <div class="manual-article-page">
    <div class="page-toolbar">
      <div class="toolbar-left">
        <el-button class="back-button" :icon="Back" aria-label="返回" @click="goBack" />
        <div class="toolbar-title">
          <h1>手动生成文章</h1>
          <div class="breadcrumb">内容与执行 / 文章管理 / 新建</div>
        </div>
      </div>
      <div class="toolbar-right">
        <span class="ready-state">
          <span class="ready-dot" :class="{ pending: !canSubmit }" />
          {{ canSubmit ? '字段已就绪' : '字段待完善' }}
        </span>
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :icon="Check" :loading="submitting" @click="submitManualCreate">
          生成并提交审核
        </el-button>
      </div>
    </div>

    <div class="page-body">
      <section class="editor-pane">
        <section class="section-card">
          <header class="section-header">
            <div class="section-header-left">
              <span class="section-index">1</span>
              <span class="section-title">基础信息</span>
              <span class="section-desc">绑定项目用于后续分发到对应站点和自媒体</span>
            </div>
          </header>
          <div class="section-body">
            <div class="base-grid">
              <div class="form-item">
                <label class="form-label required">绑定项目</label>
                <el-select
                  v-model="manualForm.projectId"
                  filterable
                  remote
                  reserve-keyword
                  clearable
                  :remote-method="searchProjects"
                  :loading="projectSearching"
                  placeholder="搜索项目名称"
                  class="full-width"
                >
                  <el-option
                    v-for="project in projectOptions"
                    :key="project.id"
                    :label="project.projectName"
                    :value="project.id"
                  >
                    <span>{{ project.projectName }}</span>
                    <span class="project-option-meta">#{{ project.id }} {{ project.brandName || '' }}</span>
                  </el-option>
                </el-select>
                <div v-if="selectedProject" class="project-pill">
                  <span class="project-icon">{{ projectInitial }}</span>
                  <div class="project-meta">
                    <span class="project-name">{{ selectedProject.projectName }}</span>
                    <span class="project-desc">
                      #{{ selectedProject.id }} · 品牌：{{ selectedProject.brandName || '-' }}
                    </span>
                  </div>
                </div>
              </div>

              <div class="form-item">
                <label class="form-label required">文章类型</label>
                <div class="type-group">
                  <button
                    v-for="item in articleTypeOptions"
                    :key="item.value"
                    type="button"
                    class="type-tile"
                    :class="{ active: manualForm.articleType === item.value }"
                    @click="manualForm.articleType = item.value"
                  >
                    <el-icon class="tile-icon"><component :is="item.icon" /></el-icon>
                    <span class="tile-main">
                      <span class="tile-name">{{ item.label }}</span>
                      <span class="tile-desc">{{ item.desc }}</span>
                    </span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="section-card">
          <header class="section-header">
            <div class="section-header-left">
              <span class="section-index">2</span>
              <span class="section-title">文章结构</span>
              <span class="section-desc">大标题 + 多个小标题段落，自动转 Markdown</span>
            </div>
          </header>
          <div class="section-body">
            <div class="form-item title-field">
              <label class="form-label required">大标题</label>
              <el-input
                v-model="manualForm.title"
                maxlength="120"
                show-word-limit
                placeholder="一句话概括文章主题"
                class="title-input"
              />
              <div class="form-help">建议 15-40 字，会作为 Markdown 一级标题</div>
            </div>

            <div class="paragraph-header">
              <label class="form-label">小标题段落</label>
              <span class="form-help">共 {{ manualForm.sections.length }} 段 · 拖拽调整顺序</span>
            </div>
            <div class="paragraph-list">
              <article
                v-for="(section, index) in manualForm.sections"
                :key="section.id"
                class="paragraph-block"
                :class="{ focused: focusedSectionId === section.id }"
                @focusin="focusedSectionId = section.id"
              >
                <div class="paragraph-head">
                  <el-icon class="drag-handle"><Rank /></el-icon>
                  <span class="paragraph-no">{{ formatSectionNo(index) }}</span>
                  <input
                    v-model="section.heading"
                    class="heading-input"
                    placeholder="小标题"
                    maxlength="120"
                  />
                  <div class="paragraph-actions">
                    <el-button :icon="ArrowUp" text :disabled="index === 0" aria-label="上移" @click="moveSection(index, -1)" />
                    <el-button :icon="ArrowDown" text :disabled="index === manualForm.sections.length - 1" aria-label="下移" @click="moveSection(index, 1)" />
                    <el-button :icon="Delete" text :disabled="manualForm.sections.length === 1" aria-label="删除" @click="removeSection(index)" />
                  </div>
                </div>
                <div class="paragraph-body">
                  <el-input
                    v-model="section.content"
                    type="textarea"
                    :rows="5"
                    maxlength="10000"
                    placeholder="本段正文。匹配到的加粗字段会在 Markdown 中自动加粗。"
                  />
                </div>
              </article>
              <button type="button" class="add-paragraph-button" @click="addSection">
                <el-icon><Plus /></el-icon>
                添加小标题段落
              </button>
            </div>
          </div>
        </section>

        <section class="section-card">
          <header class="section-header">
            <div class="section-header-left">
              <span class="section-index">3</span>
              <span class="section-title">关键词加粗</span>
              <span class="section-desc">正文中匹配到的关键词会自动用 **词** 包裹</span>
            </div>
          </header>
          <div class="section-body">
            <div class="bold-tags" @click="focusBoldInput">
              <el-tag
                v-for="tag in manualForm.boldTags"
                :key="tag"
                closable
                type="primary"
                effect="light"
                @close="removeBoldTag(tag)"
              >
                {{ tag }}
              </el-tag>
              <input
                ref="boldInputRef"
                v-model="boldInput"
                class="bold-input"
                placeholder="输入关键词后回车 / 逗号"
                @keydown.enter.prevent="commitBoldInput"
                @input="handleBoldInput"
              />
            </div>
            <div class="form-help">仅作用于正文段落，不会修改大标题与小标题。</div>
          </div>
        </section>

        <section class="section-card">
          <header class="section-header">
            <div class="section-header-left">
              <span class="section-index">4</span>
              <span class="section-title">品牌图库插图</span>
              <span class="section-desc">从当前项目品牌图库选图，写入长期访问 URL</span>
            </div>
            <el-button :icon="Picture" :disabled="!selectedProject?.brandId" @click="openImagePicker">
              选择图片
            </el-button>
          </header>
          <div class="section-body">
            <div v-if="!selectedProject?.brandId" class="image-empty">选择绑定项目后可使用品牌图库插图。</div>
            <div v-else class="image-hint">
              图片将以 Markdown 语法插入正文，并使用素材库保存的完整访问路径。
            </div>
          </div>
        </section>

        <section class="source-card" :class="{ collapsed: !sourceExpanded, overridden: markdownOverridden }">
          <header class="source-card-head" @click="sourceExpanded = !sourceExpanded">
            <div class="source-title">
              <el-icon class="source-chevron"><ArrowDown /></el-icon>
              <span>Markdown 源码</span>
              <span class="source-desc">高级 · 直接编辑会脱离结构化字段</span>
            </div>
            <el-button size="small" @click.stop="copyMarkdown">复制</el-button>
          </header>
          <div v-show="sourceExpanded" class="source-card-body">
            <el-alert
              v-if="markdownOverridden"
              type="warning"
              :closable="false"
              show-icon
              class="override-alert"
            >
              <template #title>
                已直接编辑 Markdown，对上方字段的修改将不会自动同步。
                <el-button size="small" @click="restoreFieldSync">恢复字段同步</el-button>
              </template>
            </el-alert>
            <div class="source-card-toolbar">
              <span>格式：CommonMark · 提交时将传递给后端</span>
              <span>{{ markdownStats.characters }} 字符 / {{ markdownStats.lines }} 行</span>
            </div>
            <el-input
              v-model="manualMarkdown"
              type="textarea"
              maxlength="50000"
              class="source-textarea"
              :rows="12"
            />
          </div>
        </section>
      </section>

      <aside class="preview-pane">
        <div class="preview-head">
          <span class="preview-eyebrow">实时预览</span>
          <el-radio-group v-model="previewMode" size="small">
            <el-radio-button label="rendered">渲染效果</el-radio-button>
            <el-radio-button label="markdown">原始 Markdown</el-radio-button>
          </el-radio-group>
        </div>
        <article class="paper">
          <div class="paper-meta">
            <span>{{ selectedArticleTypeLabel }}</span>
            <span class="meta-dot" />
            <span>{{ selectedProject?.projectName || selectedProject?.brandName || '未绑定项目' }}</span>
            <span class="meta-dot" />
            <span>{{ todayText }}</span>
          </div>
          <div v-if="previewMode === 'rendered'" class="md-body" v-html="manualHtml"></div>
          <pre v-else class="raw-markdown">{{ manualMarkdown }}</pre>
        </article>
        <div class="preview-foot">
          <div class="preview-stats">
            <span><strong>{{ filledSectionCount }}</strong> 段落</span>
            <span><strong>{{ markdownStats.characters }}</strong> 字符</span>
            <span><strong>{{ strongCount }}</strong> 处加粗</span>
          </div>
          <div class="preview-status" :class="{ ok: canSubmit }">
            {{ canSubmit ? '✓ 结构完整，可提交' : '请完善项目、标题和正文' }}
          </div>
        </div>
      </aside>
    </div>

    <el-dialog v-model="imagePickerVisible" title="选择品牌图片" width="860px">
      <div class="image-picker">
        <div class="image-picker-toolbar">
          <el-select
            v-model="selectedImageFolderId"
            :loading="imageFoldersLoading"
            placeholder="选择文件夹"
            style="width: 220px"
          >
            <el-option
              v-for="folder in imageFolders"
              :key="folder.id"
              :label="`${folder.folderName}（${folder.materials?.length || folder.materialCount || 0}）`"
              :value="folder.id"
            />
          </el-select>
          <el-input
            v-model="imageAltText"
            maxlength="80"
            placeholder="图片说明"
            style="width: 260px"
          />
          <el-button :loading="imageFoldersLoading" @click="loadImageFolders">刷新</el-button>
        </div>

        <DataState :loading="imageFoldersLoading" :empty="!imageFoldersLoading && imageMaterials.length === 0" empty-text="当前项目品牌图库暂无可用图片">
          <div class="image-grid">
            <button
              v-for="material in imageMaterials"
              :key="material.id"
              type="button"
              class="image-tile"
              :class="{ selected: selectedImageMaterialId === material.id }"
              @click="selectImageMaterial(material)"
            >
              <img :src="material.fileUrl" :alt="material.fileName" loading="lazy" />
              <span>{{ material.fileName }}</span>
            </button>
          </div>
        </DataState>
      </div>
      <template #footer>
        <el-button @click="imagePickerVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedImageMaterial" @click="insertSelectedImage">
          插入文章
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  ArrowUp,
  Back,
  ChatLineRound,
  Check,
  CircleCheck,
  Delete,
  Document,
  Files,
  Picture,
  Plus,
  Rank,
} from '@element-plus/icons-vue'
import type { BrandImageFolder, BrandMaterial, Project } from '@/types'
import { createManualContentArticle } from '@/api/content'
import { getBrandImageFolders } from '@/api/customer'
import { getProjectDetail, getProjectList } from '@/api/project'
import { useDictStore } from '@/stores/dict'
import DataState from '@/components/ui/DataState.vue'
import { errorMessage } from '@/utils/error'

interface ManualSection {
  id: number
  heading: string
  content: string
}

interface ArticleTypeOption {
  value: string
  label: string
  desc: string
  icon: unknown
}

const ARTICLE_TYPE_FALLBACKS: ArticleTypeOption[] = [
  { value: 'faq', label: 'FAQ', desc: '问答式短文', icon: ChatLineRound },
  { value: 'scenario_content', label: '场景内容', desc: '使用场景介绍', icon: Files },
  { value: 'industry_article', label: '行业文章', desc: '行业深度解读', icon: Document },
  { value: 'stage_advice', label: '阶段建议', desc: '分阶段方案建议', icon: CircleCheck },
]

const route = useRoute()
const router = useRouter()
const dictStore = useDictStore()

const submitting = ref(false)
const projectSearching = ref(false)
const projectOptions = ref<Project[]>([])
const focusedSectionId = ref<number | null>(null)
const boldInput = ref('')
const boldInputRef = ref<HTMLInputElement | null>(null)
const markdownOverride = ref('')
const markdownOverridden = ref(false)
const sourceExpanded = ref(false)
const previewMode = ref<'rendered' | 'markdown'>('rendered')
const imagePickerVisible = ref(false)
const imageFoldersLoading = ref(false)
const imageFolders = ref<BrandImageFolder[]>([])
const selectedImageFolderId = ref<number | null>(null)
const selectedImageMaterialId = ref<number | null>(null)
const imageAltText = ref('')
let nextSectionId = 1

const manualForm = reactive({
  projectId: undefined as number | undefined,
  articleType: 'industry_article',
  title: '',
  boldTags: [] as string[],
  sections: [createSection()],
})

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const articleTypeOptions = computed<ArticleTypeOption[]>(() => {
  const dictOptions = dictStore.options('article_type')
  return ARTICLE_TYPE_FALLBACKS.map((item) => {
    const dictItem = dictOptions.find((option) => option.dictKey === item.value)
    return {
      ...item,
      label: dictItem?.dictValue || item.label,
      desc: dictItem?.remark || item.desc,
    }
  })
})

const selectedProject = computed(() => projectOptions.value.find((project) => project.id === manualForm.projectId) || null)
const projectInitial = computed(() => selectedProject.value?.projectName?.trim().slice(0, 1) || '项')
const selectedArticleTypeLabel = computed(() => articleTypeOptions.value.find((item) => item.value === manualForm.articleType)?.label || manualForm.articleType)
const generatedManualMarkdown = computed(() => buildManualMarkdown())
const manualMarkdown = computed({
  get: () => markdownOverridden.value ? markdownOverride.value : generatedManualMarkdown.value,
  set: (value: string) => {
    markdownOverride.value = value
    markdownOverridden.value = true
  },
})
const manualHtml = computed(() => {
  const content = manualMarkdown.value.trim()
  return content ? markdown.render(content) : '<div class="preview-empty">填写左侧内容后将显示预览</div>'
})
const markdownStats = computed(() => {
  const content = manualMarkdown.value
  return {
    characters: content.replace(/\s/g, '').length,
    lines: content ? content.split(/\r?\n/).length : 0,
  }
})
const filledSectionCount = computed(() => manualForm.sections.filter((item) => item.heading.trim() || item.content.trim()).length)
const strongCount = computed(() => (manualMarkdown.value.match(/\*\*[^*]+?\*\*/g) || []).length)
const canSubmit = computed(() => Boolean(manualForm.projectId && manualForm.title.trim() && manualMarkdown.value.trim()))
const todayText = computed(() => {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const date = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}.${month}.${date}`
})
const imageMaterials = computed(() => {
  const folder = imageFolders.value.find((item) => item.id === selectedImageFolderId.value)
  return (folder?.materials || []).filter((material) => material.category === 'brand_image' && isImageType(material.fileType) && Boolean(material.fileUrl))
})
const selectedImageMaterial = computed(() => imageMaterials.value.find((item) => item.id === selectedImageMaterialId.value) || null)

function createSection(): ManualSection {
  return {
    id: nextSectionId++,
    heading: '',
    content: '',
  }
}

function restoreFieldSync() {
  markdownOverridden.value = false
  markdownOverride.value = ''
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function applyBoldFields(content: string) {
  if (!manualForm.boldTags.length) return content
  const sortedTags = [...manualForm.boldTags].sort((a, b) => b.length - a.length)
  return content.split(/(\*\*[^*]+?\*\*)/g).map((segment) => {
    if (segment.startsWith('**') && segment.endsWith('**')) {
      return segment
    }
    return sortedTags.reduce((result, word) => {
      const pattern = new RegExp(escapeRegExp(word), 'g')
      return result.replace(pattern, `**${word}**`)
    }, segment)
  }).join('')
}

function buildManualMarkdown() {
  const parts: string[] = []
  const title = manualForm.title.trim()
  if (title) {
    parts.push(`# ${title}`)
  }
  for (const section of manualForm.sections) {
    const heading = section.heading.trim()
    const content = section.content.trim()
    if (!heading && !content) continue
    if (heading) {
      parts.push(`## ${heading}`)
    }
    if (content) {
      parts.push(applyBoldFields(content))
    }
  }
  return parts.join('\n\n')
}

async function searchProjects(keyword = '') {
  projectSearching.value = true
  try {
    const { data } = await getProjectList({
      current: 1,
      size: 20,
      keyword: keyword || undefined,
      status: 'active',
    })
    projectOptions.value = mergeProjects(projectOptions.value, data.data.records || [])
  } catch (err) {
    console.error(err)
    projectOptions.value = []
    ElMessage.error('加载项目失败')
  } finally {
    projectSearching.value = false
  }
}

async function loadInheritedProject(projectId: number) {
  try {
    const { data } = await getProjectDetail(projectId)
    projectOptions.value = mergeProjects([data.data], projectOptions.value)
    manualForm.projectId = data.data.id
  } catch (err) {
    console.error(err)
    ElMessage.warning('继承的项目加载失败，请重新选择项目')
  }
}

function mergeProjects(primary: Project[], secondary: Project[]) {
  const map = new Map<number, Project>()
  for (const item of [...primary, ...secondary]) {
    map.set(item.id, item)
  }
  return Array.from(map.values())
}

function addSection() {
  manualForm.sections.push(createSection())
}

function removeSection(index: number) {
  manualForm.sections.splice(index, 1)
  focusedSectionId.value = null
}

function moveSection(index: number, offset: number) {
  const nextIndex = index + offset
  if (nextIndex < 0 || nextIndex >= manualForm.sections.length) return
  const next = [...manualForm.sections]
  const [section] = next.splice(index, 1)
  next.splice(nextIndex, 0, section)
  manualForm.sections = next
}

function formatSectionNo(index: number) {
  return String(index + 1).padStart(2, '0')
}

function focusBoldInput() {
  boldInputRef.value?.focus()
}

function handleBoldInput(event: Event) {
  const target = event.target as HTMLInputElement
  if (/[，,、]/.test(target.value)) {
    commitBoldInput()
  }
}

function commitBoldInput() {
  const values = boldInput.value
    .split(/[\n,，、]/)
    .map((item) => item.trim())
    .filter(Boolean)
  for (const value of values) {
    if (!manualForm.boldTags.includes(value)) {
      manualForm.boldTags.push(value)
    }
  }
  boldInput.value = ''
}

function removeBoldTag(tag: string) {
  manualForm.boldTags = manualForm.boldTags.filter((item) => item !== tag)
}

async function copyMarkdown() {
  await navigator.clipboard.writeText(manualMarkdown.value)
  ElMessage.success('Markdown 已复制')
}

async function openImagePicker() {
  if (!selectedProject.value?.brandId) {
    ElMessage.warning('请先选择绑定项目')
    return
  }
  imagePickerVisible.value = true
  if (!imageFolders.value.length) {
    await loadImageFolders()
  }
}

async function loadImageFolders() {
  const project = selectedProject.value
  if (!project?.brandId) {
    imageFolders.value = []
    selectedImageFolderId.value = null
    selectedImageMaterialId.value = null
    return
  }
  imageFoldersLoading.value = true
  try {
    const { data } = await getBrandImageFolders(project.brandId, {
      projectId: project.id,
      activeOnly: true,
      includeMaterials: true,
    })
    imageFolders.value = data.data || []
    if (!imageFolders.value.some((folder) => folder.id === selectedImageFolderId.value)) {
      selectedImageFolderId.value = imageFolders.value[0]?.id || null
    }
    if (!imageMaterials.value.some((material) => material.id === selectedImageMaterialId.value)) {
      selectedImageMaterialId.value = imageMaterials.value[0]?.id || null
    }
  } catch (err) {
    console.error(err)
    imageFolders.value = []
    selectedImageFolderId.value = null
    selectedImageMaterialId.value = null
    ElMessage.error('加载品牌图库失败')
  } finally {
    imageFoldersLoading.value = false
  }
}

function selectImageMaterial(material: BrandMaterial) {
  selectedImageMaterialId.value = material.id
  if (!imageAltText.value.trim()) {
    imageAltText.value = filenameWithoutExt(material.fileName)
  }
}

function insertSelectedImage() {
  const material = selectedImageMaterial.value
  if (!material?.fileUrl) {
    ElMessage.warning('请选择可用图片')
    return
  }
  const markdownText = `![${escapeMarkdownAlt(imageAltText.value.trim() || filenameWithoutExt(material.fileName))}](${material.fileUrl})`
  if (markdownOverridden.value) {
    manualMarkdown.value = appendMarkdown(manualMarkdown.value, markdownText)
  } else {
    appendImageToFocusedSection(markdownText)
  }
  imagePickerVisible.value = false
  ElMessage.success('图片已插入文章')
}

function appendImageToFocusedSection(markdownText: string) {
  let target = manualForm.sections.find((section) => section.id === focusedSectionId.value)
  if (!target) {
    target = manualForm.sections[manualForm.sections.length - 1]
  }
  if (!target) {
    target = createSection()
    manualForm.sections.push(target)
  }
  target.content = appendMarkdown(target.content, markdownText)
}

function appendMarkdown(source: string, markdownText: string) {
  const current = source.trim()
  return current ? `${current}\n\n${markdownText}` : markdownText
}

function escapeMarkdownAlt(value: string) {
  return value.replace(/[[\]]/g, '')
}

function filenameWithoutExt(fileName?: string | null) {
  const name = fileName?.trim() || '品牌图片'
  return name.replace(/\.[^.]+$/, '')
}

function isImageType(fileType?: string | null) {
  if (!fileType) return false
  return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(fileType.toLowerCase())
}

async function submitManualCreate() {
  if (!manualForm.projectId) {
    ElMessage.warning('请选择绑定项目')
    return
  }
  if (!manualForm.title.trim()) {
    ElMessage.warning('请填写大标题')
    return
  }
  const contentMarkdown = manualMarkdown.value.trim()
  if (!contentMarkdown) {
    ElMessage.warning('正文不能为空')
    return
  }
  submitting.value = true
  try {
    const { data } = await createManualContentArticle({
      projectId: manualForm.projectId,
      articleType: manualForm.articleType,
      title: manualForm.title.trim(),
      contentMarkdown,
    })
    ElMessage.success('手动文章已生成，进入待审核')
    router.push({
      path: '/admin/content/execution',
      query: {
        projectId: String(manualForm.projectId),
        articleType: manualForm.articleType,
        articleId: String(data.data.id),
      },
    })
  } catch (err) {
    console.error(err)
    ElMessage.error(errorMessage(err, '提交失败，请重试'))
  } finally {
    submitting.value = false
  }
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/admin/content/execution')
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await searchProjects('')
  const projectId = Number(route.query.projectId || 0)
  if (projectId > 0) {
    await loadInheritedProject(projectId)
  }
  const articleType = String(route.query.articleType || '')
  if (articleType && articleTypeOptions.value.some((item) => item.value === articleType)) {
    manualForm.articleType = articleType
  }
})

watch(() => manualForm.projectId, () => {
  imageFolders.value = []
  selectedImageFolderId.value = null
  selectedImageMaterialId.value = null
  imageAltText.value = ''
})
</script>

<style scoped>
.manual-article-page {
  min-height: 100vh;
  background: var(--el-bg-color-page);
}

.page-toolbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 24px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: color-mix(in srgb, var(--el-bg-color) 92%, transparent);
  backdrop-filter: saturate(180%) blur(10px);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.toolbar-title h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--el-text-color-primary);
}

.breadcrumb,
.section-desc,
.form-help,
.source-desc {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.ready-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-right: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.ready-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--el-color-success);
}

.ready-dot.pending {
  background: var(--el-color-warning);
}

.page-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  min-height: calc(100vh - 61px);
}

.editor-pane,
.preview-pane {
  padding: 24px;
}

.editor-pane {
  border-right: 1px solid var(--el-border-color-lighter);
}

.preview-pane {
  background: var(--el-fill-color-light);
}

.section-card,
.source-card {
  margin-bottom: 16px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  transition: box-shadow 0.15s ease;
}

.section-card:hover,
.source-card:hover {
  box-shadow: var(--el-box-shadow-light);
}

.section-header,
.source-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.section-header-left,
.source-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.section-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.section-title,
.source-title span:first-of-type {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.section-body,
.source-card-body {
  padding: 18px;
}

.base-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 500;
}

.form-label.required::before {
  content: "*";
  margin-right: 3px;
  color: var(--el-color-danger);
}

.full-width {
  width: 100%;
}

.project-option-meta {
  float: right;
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.project-pill {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: var(--el-border-radius-base);
  background: var(--el-color-primary-light-9);
}

.project-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--el-color-primary);
  color: var(--el-color-white);
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.project-meta {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.project-name {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
}

.project-desc {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-group {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.type-tile {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-height: 70px;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: all 0.15s ease;
}

.type-tile:hover,
.type-tile.active {
  border-color: var(--el-color-primary);
}

.type-tile.active {
  background: var(--el-color-primary-light-9);
}

.tile-icon {
  margin-top: 1px;
  color: var(--el-text-color-secondary);
}

.type-tile.active .tile-icon {
  color: var(--el-color-primary);
}

.tile-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.tile-name {
  font-size: 13px;
  font-weight: 600;
}

.tile-desc {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1.4;
}

.title-field {
  margin-bottom: 18px;
}

.title-input :deep(.el-input__wrapper) {
  min-height: 40px;
  font-size: 15px;
}

.paragraph-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.paragraph-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.paragraph-block {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  transition: all 0.15s ease;
}

.paragraph-block:hover,
.paragraph-block.focused {
  border-color: var(--el-color-primary-light-7);
  background: var(--el-bg-color);
}

.paragraph-block.focused {
  box-shadow: 0 0 0 3px var(--el-color-primary-light-9);
}

.paragraph-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
}

.drag-handle {
  color: var(--el-text-color-placeholder);
  cursor: grab;
}

.paragraph-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 11px;
  font-weight: 600;
}

.paragraph-block.focused .paragraph-no {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.heading-input {
  min-width: 0;
  flex: 1;
  height: 28px;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--el-text-color-primary);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
}

.heading-input::placeholder {
  color: var(--el-text-color-placeholder);
  font-weight: 400;
}

.paragraph-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.paragraph-body {
  padding: 0 12px 12px;
}

.paragraph-body :deep(.el-textarea__inner) {
  border: 0;
  box-shadow: none;
  background: transparent;
  line-height: 1.6;
}

.add-paragraph-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 10px;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  font-family: inherit;
  font-size: 13px;
  transition: all 0.15s ease;
}

.add-paragraph-button:hover {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.bold-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  min-height: 38px;
  padding: 6px 8px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
}

.bold-tags:focus-within {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-3);
}

.bold-input {
  min-width: 120px;
  flex: 1;
  height: 24px;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--el-text-color-primary);
  font-family: inherit;
  font-size: 13px;
}

.source-card-head {
  cursor: pointer;
  user-select: none;
}

.source-chevron {
  transition: transform 0.2s ease;
}

.source-card.collapsed .source-chevron {
  transform: rotate(-90deg);
}

.override-alert {
  margin-bottom: 8px;
}

.override-alert :deep(.el-alert__title) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.source-card-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.source-textarea :deep(.el-textarea__inner),
.raw-markdown {
  border-color: var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-primary);
  font-family: "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
}

.preview-head {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 8px;
  background: color-mix(in srgb, var(--el-fill-color-light) 92%, transparent);
  backdrop-filter: blur(8px);
}

.preview-eyebrow {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.paper {
  min-height: 600px;
  padding: 56px 64px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-light);
}

.paper-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 28px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 11px;
  font-weight: 500;
}

.meta-dot {
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: var(--el-text-color-placeholder);
}

.md-body {
  color: var(--el-text-color-primary);
  font-size: 15px;
  line-height: 1.7;
}

.md-body :deep(h1) {
  margin: 0 0 24px;
  color: var(--el-text-color-primary);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
}

.md-body :deep(h2) {
  margin: 32px 0 14px;
  padding-left: 12px;
  border-left: 3px solid var(--el-color-primary);
  color: var(--el-text-color-primary);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.4;
}

.md-body :deep(p),
.md-body :deep(li) {
  color: var(--el-text-color-regular);
}

.md-body :deep(p) {
  margin: 0 0 14px;
}

.md-body :deep(strong) {
  padding: 0 2px;
  background: linear-gradient(to top, var(--el-color-primary-light-9) 55%, transparent 55%);
  color: var(--el-text-color-primary);
  font-weight: 700;
}

.md-body :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 18px auto;
  border-radius: 6px;
}

.image-empty,
.image-hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.image-picker {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.image-picker-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
  max-height: 460px;
  overflow: auto;
  padding-right: 4px;
}

.image-tile {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 8px;
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.image-tile:hover,
.image-tile.selected {
  border-color: var(--el-color-primary);
}

.image-tile.selected {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}

.image-tile img {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 10;
  object-fit: cover;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.image-tile span {
  display: block;
  margin-top: 7px;
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-empty {
  padding: 80px 24px;
  text-align: center;
  color: var(--el-text-color-placeholder);
}

.raw-markdown {
  min-height: 420px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
}

.preview-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding: 10px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-bg-color);
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.preview-stats {
  display: flex;
  gap: 16px;
}

.preview-stats strong {
  color: var(--el-text-color-primary);
}

.preview-status.ok {
  color: var(--el-color-success);
}

@media (max-width: 1100px) {
  .page-body {
    grid-template-columns: 1fr;
  }

  .editor-pane {
    border-right: 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .base-grid {
    grid-template-columns: 1fr;
  }

  .type-group {
    grid-template-columns: repeat(2, 1fr);
  }

  .paper {
    padding: 32px 28px;
  }
}

@media (max-width: 720px) {
  .page-toolbar,
  .toolbar-right,
  .paragraph-header,
  .preview-foot {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-left {
    width: 100%;
  }

  .type-group {
    grid-template-columns: 1fr;
  }

  .preview-stats {
    flex-wrap: wrap;
  }
}
</style>
