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
      <div class="toolbar-mode">
        <el-radio-group v-model="createMode" size="small" class="mode-switch">
          <el-radio-button label="manual">手动撰写</el-radio-button>
          <el-radio-button label="auto">AI 生成</el-radio-button>
        </el-radio-group>
      </div>
      <div class="toolbar-right">
        <span class="ready-state">
          <span class="ready-dot" :class="{ pending: !canSubmit }" />
          {{ submitStateText }}
        </span>
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :icon="Check" :loading="submitting" :disabled="!canSubmit" @click="submitManualCreate">
          提交审核
        </el-button>
      </div>
    </div>

    <div class="sub-toolbar">
      <div class="sub-toolbar-left">
        <label class="inline-field">
          <span class="inline-label required">绑定项目</span>
          <el-cascader
            v-model="manualForm.projectId"
            filterable
            clearable
            :options="projectCascadeOptions"
            :props="projectCascadeProps"
            :loading="projectLoading"
            placeholder="选择客户 / 品牌 / 项目"
            class="project-select"
          />
        </label>
        <label class="inline-field">
          <span class="inline-label required">文章类型</span>
          <el-select
            v-model="manualForm.articleType"
            class="article-type-select"
            popper-class="article-type-popper"
          >
            <el-option
              v-for="item in articleTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            >
              <div class="article-type-option">
                <span>{{ item.label }}</span>
                <small>{{ item.desc }}</small>
              </div>
            </el-option>
          </el-select>
        </label>
        <label v-if="createMode === 'manual'" class="inline-field">
          <span class="inline-label required">平台风格</span>
          <el-select v-model="manualForm.contentStyle" class="content-style-select">
            <el-option
              v-for="item in contentStyleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </label>
        <label v-if="createMode === 'manual'" class="inline-field">
          <span class="inline-label required">文章主题</span>
          <el-input
            v-model="manualForm.topic"
            class="topic-input"
            maxlength="1000"
            placeholder="输入本篇文章主题"
            clearable
          />
        </label>
      </div>
      <div class="sub-toolbar-spacer" />
    </div>

    <div class="page-body" :class="{ 'is-ai-mode': createMode === 'auto' }">
      <section class="editor-pane">
        <section v-if="createMode === 'auto'" class="section-card ai-card">
          <header class="section-header">
            <div class="section-header-left">
              <span class="section-index">1</span>
              <span class="section-title">AI 生成设置</span>
              <span class="section-desc">生成后回填为可编辑草稿，不直接进入审核</span>
            </div>
            <el-button class="generate-button" type="primary" :loading="generating" :disabled="!canGenerate" @click="generateAiPreview">
              {{ generating ? '生成中' : aiMetadata ? '重新生成' : '生成草稿' }}
            </el-button>
          </header>
          <div class="section-body">
            <div class="form-item topic-field">
              <div class="topic-label-row">
                <label class="form-label required">选题 / 主题</label>
                <el-button size="small" :icon="Search" :disabled="!manualForm.projectId" @click="openQuestionPicker">
                  选择问题词
                </el-button>
              </div>
              <el-input
                v-model="aiForm.topic"
                type="textarea"
                :rows="3"
                maxlength="1000"
                show-word-limit
                placeholder="如：2026 年 RAG 在法律行业的落地路径"
              />
              <div v-if="selectedQuestion" class="selected-question-line">
                已选问题词：{{ selectedQuestion.questionText }}
              </div>
            </div>

            <div class="ai-control-grid">
              <div class="form-item">
                <label class="form-label">语气</label>
                <el-segmented v-model="aiForm.tone" :options="toneOptions" block />
              </div>
              <div class="form-item">
                <label class="form-label">篇幅</label>
                <el-segmented v-model="aiForm.length" :options="lengthOptions" block />
              </div>
            </div>

            <div class="form-item style-field">
              <label class="form-label required">内容风格</label>
              <div class="style-grid">
                <button
                  v-for="item in contentStyleOptions"
                  :key="item.value"
                  type="button"
                  class="style-tile"
                  :class="{ active: aiForm.contentStyle === item.value }"
                  @click="aiForm.contentStyle = item.value"
                >
                  <span class="style-icon">{{ item.icon }}</span>
                  <span class="style-copy">
                    <span class="style-name">{{ item.label }}</span>
                    <span class="style-desc">{{ item.desc }}</span>
                  </span>
                  <el-icon v-if="aiForm.contentStyle === item.value" class="style-check"><Check /></el-icon>
                </button>
              </div>
            </div>

            <el-collapse class="ai-extra-collapse">
              <el-collapse-item title="补充提示词与参考资料（可选）" name="extra">
                <div class="ai-extra-stack">
                  <el-input
                    v-model="aiForm.extraPrompt"
                    type="textarea"
                    :rows="3"
                    maxlength="3000"
                    placeholder="如：避免空泛描述；多举具体案例；最后给出落地清单"
                  />
                  <el-input
                    v-model="aiForm.referenceMaterials"
                    type="textarea"
                    :rows="3"
                    maxlength="3000"
                    placeholder="参考资料或链接，每行一条"
                  />
                </div>
              </el-collapse-item>
            </el-collapse>

            <el-alert
              v-if="generationNotice"
              class="generation-alert"
              :type="generationNotice.type"
              :title="generationNotice.title"
              :description="generationNotice.description"
              show-icon
              :closable="false"
            />
          </div>
        </section>

        <section class="section-card">
          <header class="section-header">
            <div class="section-header-left">
              <span class="section-index">{{ createMode === 'auto' ? '2' : '1' }}</span>
              <span class="section-title">文章结构</span>
              <span class="section-desc">{{ createMode === 'auto' ? 'AI 回填后仍可继续编辑' : '大标题 + 多个小标题段落，自动转 Markdown' }}</span>
            </div>
            <button v-if="manualForm.sections.length > 1" type="button" class="fold-all-link" @click="toggleAllSections">
              {{ allSectionsCollapsed ? '全部展开' : '全部折叠' }}
            </button>
          </header>
          <div class="section-body">
            <el-alert
              v-if="parseNotice"
              class="generation-alert"
              :type="parseNotice.type"
              :title="parseNotice.title"
              :description="parseNotice.description"
              show-icon
              :closable="false"
            />
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
              <span class="form-help">共 {{ manualForm.sections.length }} 段 · 使用操作调整顺序</span>
            </div>
            <div class="paragraph-list">
              <article
                v-for="(section, index) in manualForm.sections"
                :key="section.id"
                class="paragraph-block"
                :class="{ focused: focusedSectionId === section.id, empty: !section.heading.trim() && !section.content.trim(), collapsed: section.collapsed }"
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
                    <el-button :icon="Document" text aria-label="复制" @click="duplicateSection(index)" />
                    <el-button :icon="ArrowDown" text aria-label="折叠" @click="toggleSection(section)" />
                    <el-button :icon="Delete" text :disabled="manualForm.sections.length === 1" aria-label="删除" @click="removeSection(index)" />
                  </div>
                </div>
                <div v-if="section.collapsed" class="paragraph-summary">
                  {{ section.content.trim() || '尚未填写正文' }}
                </div>
                <div v-else class="paragraph-body">
                  <el-input
                    v-model="section.content"
                    type="textarea"
                    :rows="5"
                    maxlength="10000"
                    placeholder="本段正文。"
                  />
                </div>
              </article>
              <button type="button" class="add-paragraph-button" @click="addSection">
                <el-icon><Plus /></el-icon>
                添加小标题段落
              </button>
              <div class="paragraph-summary-line">
                共 {{ filledSectionCount }} 段 · {{ markdownStats.characters }} 字 · 使用 ⋮⋮ / 上下按钮调整顺序
              </div>
            </div>
          </div>
        </section>
      </section>

      <aside class="preview-pane">
        <div class="preview-head">
          <div class="preview-title-wrap">
            <span class="preview-eyebrow">实时预览</span>
            <span class="preview-subtitle">{{ previewHeaderText }}</span>
          </div>
          <div class="preview-tools">
            <el-button size="small" :icon="Picture" :disabled="!selectedProject?.brandId" @click="openImagePicker">
              品牌图库
            </el-button>
            <el-button size="small" :icon="Document" @click="sourceExpanded = true">
              Markdown 源码
            </el-button>
            <el-radio-group v-model="previewMode" size="small">
              <el-radio-button label="rendered">渲染效果</el-radio-button>
              <el-radio-button label="markdown">原始 Markdown</el-radio-button>
            </el-radio-group>
          </div>
        </div>
        <article class="paper">
          <div class="paper-meta">
            <span>{{ selectedArticleTypeLabel }}</span>
            <span class="meta-dot" />
            <span v-if="createMode === 'auto'" class="ai-preview-badge">AI</span>
            <span v-if="createMode === 'auto'">{{ selectedContentStyleLabel }}</span>
            <span v-if="createMode === 'auto'" class="meta-dot" />
            <span>{{ selectedProject?.projectName || selectedProject?.brandName || '未绑定项目' }}</span>
            <span class="meta-dot" />
            <span>{{ todayText }}</span>
          </div>
          <div v-if="previewMode === 'rendered'" class="md-body" v-html="manualHtml"></div>
          <pre v-else class="raw-markdown">{{ manualMarkdown }}</pre>
        </article>
        <div class="preview-foot">
          <div class="preview-stats">
            <span>{{ filledSectionCount }} 段</span>
            <span>{{ markdownStats.characters }} 字</span>
          </div>
          <div class="preview-status" :class="{ ok: canSubmit }">
            {{ canSubmit ? '可提交' : '请完善必填项' }}
          </div>
        </div>
      </aside>
    </div>

    <el-drawer v-model="sourceExpanded" title="Markdown 源码" size="520px" append-to-body>
      <div class="source-drawer">
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
          <span>CommonMark · 提交时将传递给后端</span>
          <span>{{ markdownStats.characters }} 字符 / {{ markdownStats.lines }} 行</span>
        </div>
        <el-input
          v-model="manualMarkdown"
          type="textarea"
          maxlength="50000"
          class="source-textarea"
          :rows="18"
        />
        <div class="source-drawer-actions">
          <el-button @click="copyMarkdown">复制</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="questionPickerVisible" title="选择项目问题词" width="980px" append-to-body>
      <div class="question-picker">
        <div class="question-picker-toolbar">
          <el-select v-model="questionFilters.tier" placeholder="问题归属" style="width: 140px">
            <el-option label="全部归属" value="all" />
            <el-option label="A 类" value="A" />
            <el-option label="B 类" value="B" />
            <el-option label="C 类" value="C" />
          </el-select>
          <el-select v-model="questionFilters.scene" placeholder="问题场景" style="width: 180px">
            <el-option label="全部场景" value="all" />
            <el-option
              v-for="item in questionSceneOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-input
            v-model="questionFilters.keyword"
            clearable
            :prefix-icon="Search"
            placeholder="模糊查询问题内容"
            class="question-search-input"
          />
          <el-button :loading="questionPickerLoading" @click="reloadQuestionPicker">刷新</el-button>
        </div>

        <DataState :loading="questionPickerLoading" :empty="!questionPickerLoading && filteredQuestionRows.length === 0" empty-text="当前筛选条件下暂无问题词">
          <el-table
            :data="filteredQuestionRows"
            border
            height="420"
            highlight-current-row
            @row-click="selectQuestion"
          >
            <el-table-column width="52">
              <template #default="{ row }">
                <el-radio :model-value="selectedQuestionKey" :label="questionKey(row)" @change="selectQuestion(row)">
                  <span class="sr-only">选择</span>
                </el-radio>
              </template>
            </el-table-column>
            <el-table-column prop="questionText" label="问题内容" min-width="300" show-overflow-tooltip />
            <el-table-column label="问题归属" width="90">
              <template #default="{ row }">
                <el-tag :type="questionTierTagType(row.questionTier)" size="small">{{ row.questionTier || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="问题类型" width="120">
              <template #default="{ row }">{{ questionSourceTypeLabel(row) }}</template>
            </el-table-column>
            <el-table-column label="拓词组" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.groupName }}</template>
            </el-table-column>
            <el-table-column label="问题场景" width="110">
              <template #default="{ row }">{{ sceneLabel(row.sceneCode) }}</template>
            </el-table-column>
            <el-table-column prop="totalScore" label="总分" width="82" />
          </el-table>
        </DataState>
      </div>
      <template #footer>
        <el-button @click="questionPickerVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedQuestion" @click="confirmSelectedQuestion">
          确认选择
        </el-button>
      </template>
    </el-dialog>

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
              <img :src="materialThumbUrl(material)" :alt="material.fileName" loading="lazy" />
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
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  ArrowUp,
  Back,
  Check,
  Delete,
  Document,
  Picture,
  Plus,
  Rank,
  Search,
} from '@element-plus/icons-vue'
import type { BrandImageFolder, BrandMaterial, KeywordGroup, KeywordGroupQuestion, Project } from '@/types'
import {
  createManualContentArticle,
  previewAiContentArticleDraft,
  type ArticleAiDraftPreviewResponse,
} from '@/api/content'
import { getBrandImageFolders, getBrandMaterialStream } from '@/api/customer'
import { getKeywordGroupQuestions, getProjectDetail, getProjectList } from '@/api/project'
import { useDictStore } from '@/stores/dict'
import DataState from '@/components/ui/DataState.vue'
import { errorMessage } from '@/utils/error'

interface ManualSection {
  id: number
  heading: string
  content: string
  collapsed?: boolean
}

interface ArticleTypeOption {
  value: string
  label: string
  desc: string
}

type CreateMode = 'manual' | 'auto'
type ParseStatus = 'success' | 'partial' | 'failed'
type NoticeType = 'success' | 'warning' | 'error' | 'info'

interface NoticeState {
  type: NoticeType
  title: string
  description?: string
}

interface ContentStyleOption {
  value: string
  label: string
  desc: string
  icon: string
}

interface ParsedArticle {
  status: ParseStatus
  title: string
  sections: ManualSection[]
}

interface ArticleQuestionOption extends KeywordGroupQuestion {
  groupName: string
  groupType?: string | null
  groupTypeLabel?: string | null
}

interface QuestionSceneOption {
  value: string
  label: string
}

const ARTICLE_TYPE_FALLBACKS: ArticleTypeOption[] = [
  { value: 'faq', label: 'FAQ', desc: '问答式短文' },
  { value: 'scenario_content', label: '场景内容', desc: '使用场景介绍' },
  { value: 'industry_article', label: '行业文章', desc: '行业深度解读' },
  { value: 'stage_advice', label: '阶段建议', desc: '分阶段方案建议' },
]

const CONTENT_STYLE_OPTIONS: ContentStyleOption[] = [
  { value: 'wechat', label: '公众号风格', desc: '深度长文，结构完整', icon: '公' },
  { value: 'toutiao', label: '头条风格', desc: '资讯密度高，结论前置', icon: '头' },
  { value: 'douyin_image_text', label: '抖音图文', desc: '钩子开头，适合卡片拆分', icon: '抖' },
  { value: 'zhihu', label: '知乎风格', desc: '问题导向，论据充分', icon: '知' },
  { value: 'xiaohongshu', label: '小红书风格', desc: '自然种草，清单友好', icon: '红' },
  { value: 'linkedin', label: '领英风格', desc: '商务专业，重视洞察', icon: '领' },
]

const TONE_OPTIONS = [
  { label: '专业严谨', value: 'professional' },
  { label: '亲切自然', value: 'friendly' },
  { label: '观点鲜明', value: 'sharp' },
  { label: '故事化表达', value: 'storytelling' },
]

const LENGTH_OPTIONS = [
  { label: '短 ~600 字', value: 'short' },
  { label: '中 ~1500 字', value: 'medium' },
  { label: '长 ~3000 字', value: 'long' },
]

const route = useRoute()
const router = useRouter()
const dictStore = useDictStore()

const createMode = ref<CreateMode>('manual')
const submitting = ref(false)
const generating = ref(false)
const projectLoading = ref(false)
const projectOptions = ref<Project[]>([])
const focusedSectionId = ref<number | null>(null)
const markdownOverride = ref('')
const markdownOverridden = ref(false)
const sourceExpanded = ref(false)
const previewMode = ref<'rendered' | 'markdown'>('rendered')
const imagePickerVisible = ref(false)
const questionPickerVisible = ref(false)
const questionPickerLoading = ref(false)
const imageFoldersLoading = ref(false)
const imageFolders = ref<BrandImageFolder[]>([])
const imageThumbUrls = ref<Record<number, string | null>>({})
const imageThumbObjectUrls = ref<string[]>([])
const imagePreviewUrls = ref<Record<string, string>>({})
const selectedImageFolderId = ref<number | null>(null)
const selectedImageMaterialId = ref<number | null>(null)
const imageAltText = ref('')
const generationNotice = ref<NoticeState | null>(null)
const parseNotice = ref<NoticeState | null>(null)
const aiMetadata = ref<Record<string, unknown> | null>(null)
const questionRows = ref<ArticleQuestionOption[]>([])
const selectedQuestionKey = ref('')
const questionLoadedProjectId = ref<number | null>(null)
let stillGeneratingTimer: ReturnType<typeof setTimeout> | null = null
let nextSectionId = 1

const manualForm = reactive({
  projectId: undefined as number | undefined,
  articleType: 'industry_article',
  contentStyle: 'wechat',
  topic: '',
  title: '',
  sections: [createSection()],
})

const aiForm = reactive({
  contentStyle: 'wechat',
  tone: 'professional',
  length: 'medium',
  topic: '',
  extraPrompt: '',
  referenceMaterials: '',
})

const questionFilters = reactive({
  tier: 'all',
  keyword: '',
  scene: 'all',
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

const contentStyleOptions = computed(() => CONTENT_STYLE_OPTIONS)
const toneOptions = computed(() => TONE_OPTIONS)
const lengthOptions = computed(() => LENGTH_OPTIONS)
const projectCascadeProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: false,
}
const projectCascadeOptions = computed(() => buildProjectCascadeOptions(projectOptions.value))
const selectedProject = computed(() => projectOptions.value.find((project) => project.id === manualForm.projectId) || null)
const selectedArticleTypeLabel = computed(() => articleTypeOptions.value.find((item) => item.value === manualForm.articleType)?.label || manualForm.articleType)
const selectedContentStyleLabel = computed(() => contentStyleOptions.value.find((item) => item.value === aiForm.contentStyle)?.label || 'AI 风格')
const draftContentStyle = computed(() => createMode.value === 'auto' ? aiForm.contentStyle : manualForm.contentStyle)
const draftTopic = computed(() => createMode.value === 'auto' ? aiForm.topic.trim() : manualForm.topic.trim())
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
  if (content) return renderPreviewMarkdown(content)
  if (createMode.value === 'auto') {
    return `
      <div class="preview-empty preview-empty-workflow">
        <strong>生成草稿后，将在这里预览 AI 内容</strong>
        <span>配置主题、语气和内容风格后点击生成草稿，确认内容后再提交审核。</span>
        <ol>
          <li>配置主题</li>
          <li>生成草稿</li>
          <li>编辑结构</li>
          <li>提交审核</li>
        </ol>
      </div>
    `
  }
  return `
    <div class="preview-empty preview-empty-workflow">
      <strong>完成标题和正文后，将在这里实时预览文章效果</strong>
      <span>左侧内容会自动转换为 Markdown，并保持与预览同步。</span>
    </div>
  `
})
const markdownStats = computed(() => {
  const content = manualMarkdown.value
  return {
    characters: content.replace(/\s/g, '').length,
    lines: content ? content.split(/\r?\n/).length : 0,
  }
})
const filledSectionCount = computed(() => manualForm.sections.filter((item) => item.heading.trim() || item.content.trim()).length)
const allSectionsCollapsed = computed(() => manualForm.sections.length > 0 && manualForm.sections.every((item) => item.collapsed))
const canSubmit = computed(() => Boolean(
  manualForm.projectId
  && draftContentStyle.value
  && draftTopic.value
  && manualForm.title.trim()
  && manualMarkdown.value.trim(),
) && !generating.value)
const canGenerate = computed(() => Boolean(manualForm.projectId && manualForm.articleType && aiForm.topic.trim()) && !generating.value)
const submitStateText = computed(() => {
  if (canSubmit.value) return '字段已就绪'
  if (createMode.value === 'auto' && aiMetadata.value && manualMarkdown.value.trim()) return '请确认 AI 草稿'
  return '字段待完善'
})
const previewHeaderText = computed(() => createMode.value === 'auto' ? 'AI 草稿生成后自动进入预览' : '编辑内容时自动同步 Markdown')
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
const questionSceneOptions: QuestionSceneOption[] = [
  { value: 'brand', label: '品牌场景' },
  { value: 'decision', label: '决策场景' },
  { value: 'deal', label: '成交场景' },
  { value: 'compare', label: '对比场景' },
  { value: 'qa', label: '问答场景' },
  { value: 'function', label: '功能场景' },
]
const filteredQuestionRows = computed(() => {
  const keyword = questionFilters.keyword.trim().toLowerCase()
  return questionRows.value.filter((row) => {
    if (questionFilters.tier !== 'all' && row.questionTier !== questionFilters.tier) return false
    if (questionFilters.scene !== 'all' && row.sceneCode !== questionFilters.scene) return false
    if (keyword && !row.questionText.toLowerCase().includes(keyword)) return false
    return true
  })
})
const selectedQuestion = computed(() => questionRows.value.find((row) => questionKey(row) === selectedQuestionKey.value) || null)

function createSection(): ManualSection {
  return {
    id: nextSectionId++,
    heading: '',
    content: '',
    collapsed: false,
  }
}

function restoreFieldSync() {
  markdownOverridden.value = false
  markdownOverride.value = ''
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
      parts.push(content)
    }
  }
  return parts.join('\n\n')
}

async function loadProjectOptions() {
  projectLoading.value = true
  try {
    const { data } = await getProjectList({
      current: 1,
      size: 500,
      status: 'active',
    })
    projectOptions.value = mergeProjects(projectOptions.value, data.data.records || [])
  } catch (err) {
    console.error(err)
    projectOptions.value = []
    ElMessage.error('加载项目失败')
  } finally {
    projectLoading.value = false
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

interface ProjectCascadeNode {
  value: string | number
  label: string
  children?: ProjectCascadeNode[]
}

function buildProjectCascadeOptions(projects: Project[]): ProjectCascadeNode[] {
  const companyMap = new Map<string, ProjectCascadeNode>()
  const brandMap = new Map<string, ProjectCascadeNode>()
  const sortedProjects = [...projects].sort(compareProjectsForCascade)

  for (const project of sortedProjects) {
    const companyKey = `company:${project.companyId ?? 'none'}:${project.companyName || '未归属客户'}`
    let companyNode = companyMap.get(companyKey)
    if (!companyNode) {
      companyNode = {
        value: companyKey,
        label: project.companyName || '未归属客户',
        children: [],
      }
      companyMap.set(companyKey, companyNode)
    }

    const brandKey = `${companyKey}:brand:${project.brandId ?? 'none'}:${project.brandName || '未绑定品牌'}`
    let brandNode = brandMap.get(brandKey)
    if (!brandNode) {
      brandNode = {
        value: brandKey,
        label: project.brandName || '未绑定品牌',
        children: [],
      }
      brandMap.set(brandKey, brandNode)
      companyNode.children?.push(brandNode)
    }

    brandNode.children?.push({
      value: project.id,
      label: project.projectName || `项目 #${project.id}`,
    })
  }

  return Array.from(companyMap.values())
}

function compareProjectsForCascade(a: Project, b: Project) {
  return [
    compareText(a.companyName, b.companyName),
    compareText(a.brandName, b.brandName),
    compareText(a.projectName, b.projectName),
    a.id - b.id,
  ].find((result) => result !== 0) || 0
}

function compareText(a?: string | null, b?: string | null) {
  return (a || '').localeCompare(b || '', 'zh-Hans-CN')
}

function addSection() {
  manualForm.sections.push(createSection())
}

function duplicateSection(index: number) {
  const source = manualForm.sections[index]
  if (!source) return
  manualForm.sections.splice(index + 1, 0, {
    id: nextSectionId++,
    heading: source.heading,
    content: source.content,
    collapsed: false,
  })
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

function toggleSection(section: ManualSection) {
  section.collapsed = !section.collapsed
}

function toggleAllSections() {
  const nextCollapsed = !allSectionsCollapsed.value
  manualForm.sections.forEach((section) => {
    section.collapsed = nextCollapsed
  })
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
  } else if (!Object.keys(imageThumbUrls.value).length) {
    await loadImageThumbs(selectedProject.value.brandId)
  }
}

async function openQuestionPicker() {
  if (!manualForm.projectId) {
    ElMessage.warning('请先选择绑定项目')
    return
  }
  questionPickerVisible.value = true
  if (questionLoadedProjectId.value !== manualForm.projectId || questionRows.value.length === 0) {
    await loadQuestionPicker()
  }
}

async function reloadQuestionPicker() {
  if (!manualForm.projectId) return
  await loadQuestionPicker()
}

async function loadQuestionPicker() {
  const projectId = manualForm.projectId
  if (!projectId) return
  questionPickerLoading.value = true
  try {
    let project = selectedProject.value
    if (!project?.selectedKeywordGroups?.length) {
      const { data } = await getProjectDetail(projectId)
      projectOptions.value = mergeProjects([data.data], projectOptions.value)
      project = data.data
    }

    const groups = project?.selectedKeywordGroups || []
    questionRows.value = await loadProjectQuestions(groups)
    selectedQuestionKey.value = questionRows.value.some((row) => questionKey(row) === selectedQuestionKey.value)
      ? selectedQuestionKey.value
      : ''
    questionLoadedProjectId.value = projectId
  } catch (err) {
    console.error(err)
    questionRows.value = []
    selectedQuestionKey.value = ''
    ElMessage.error('加载项目问题词失败')
  } finally {
    questionPickerLoading.value = false
  }
}

async function loadProjectQuestions(groups: KeywordGroup[]) {
  const rows: ArticleQuestionOption[] = []
  for (const group of groups) {
    let current = 1
    let total = 0
    do {
      const { data } = await getKeywordGroupQuestions(group.id, {
        current,
        size: 100,
        tier: 'all',
      })
      const page = data.data
      total = page.total || 0
      rows.push(...(page.records || []).map((question) => ({
        ...question,
        groupName: group.name,
        groupType: group.type,
        groupTypeLabel: group.typeLabel,
      })))
      current += 1
    } while ((current - 1) * 100 < total)
  }
  return rows
}

function selectQuestion(row: ArticleQuestionOption) {
  selectedQuestionKey.value = questionKey(row)
}

async function confirmSelectedQuestion() {
  const question = selectedQuestion.value
  if (!question) return
  try {
    await ElMessageBox.confirm(
      '当前选定问题词将作为主题回显，是否确认？',
      '确认问题词',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
    aiForm.topic = question.questionText
    questionPickerVisible.value = false
    ElMessage.success('问题词已回显至选题 / 主题')
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  }
}

function questionKey(row: Pick<ArticleQuestionOption, 'groupId' | 'id'>) {
  return `${row.groupId}:${row.id}`
}

function sceneLabel(value?: string | null) {
  const labels: Record<string, string> = {
    brand: '品牌场景',
    decision: '决策场景',
    deal: '成交场景',
    compare: '对比场景',
    qa: '问答场景',
    function: '功能场景',
  }
  return value ? (labels[value] || value) : '-'
}

function questionTierTagType(value?: string | null) {
  if (value === 'A') return 'danger'
  if (value === 'B') return 'warning'
  if (value === 'C') return 'info'
  return 'info'
}

function questionSourceTypeLabel(row: ArticleQuestionOption) {
  return row.groupType === 'imported' ? (row.groupTypeLabel || '导入') : '录入'
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
    await loadImageThumbs(project.brandId)
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

function materialThumbUrl(material: BrandMaterial) {
  return imageThumbUrls.value[material.id] || previewImageUrl(material.fileUrl) || material.fileUrl
}

async function loadImageThumbs(brandId: number) {
  cleanupImageThumbs()
  const seen = new Set<number>()
  const targets = imageFolders.value
    .flatMap((folder) => folder.materials || [])
    .filter((material) => {
      if (!isImageType(material.fileType) || seen.has(material.id)) return false
      seen.add(material.id)
      return true
    })
  const concurrency = Math.min(6, targets.length)
  let cursor = 0

  const worker = async () => {
    while (cursor < targets.length) {
      const material = targets[cursor++]
      try {
        const { data: blob } = await getBrandMaterialStream(brandId, material.id, false)
        const url = URL.createObjectURL(blob)
        imageThumbObjectUrls.value.push(url)
        imageThumbUrls.value = { ...imageThumbUrls.value, [material.id]: url }
        if (material.fileUrl) {
          imagePreviewUrls.value = { ...imagePreviewUrls.value, [material.fileUrl]: url }
        }
      } catch {
        imageThumbUrls.value = { ...imageThumbUrls.value, [material.id]: null }
      }
    }
  }

  await Promise.all(Array.from({ length: concurrency }, () => worker()))
}

function cleanupImageThumbs() {
  imageThumbObjectUrls.value.forEach((url) => URL.revokeObjectURL(url))
  imageThumbObjectUrls.value = []
  imageThumbUrls.value = {}
  imagePreviewUrls.value = {}
}

function previewImageUrl(fileUrl: string) {
  return imagePreviewUrls.value[fileUrl] || ''
}

function renderPreviewMarkdown(content: string) {
  const html = markdown.render(content)
  const previewUrls = imagePreviewUrls.value
  if (!Object.keys(previewUrls).length) {
    return html
  }
  const template = document.createElement('template')
  template.innerHTML = html
  template.content.querySelectorAll('img').forEach((image) => {
    const fallbackUrl = previewUrls[image.getAttribute('src') || '']
    if (fallbackUrl) {
      image.setAttribute('data-source-src', image.getAttribute('src') || '')
      image.setAttribute('src', fallbackUrl)
    }
  })
  return template.innerHTML
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

function clearStillGeneratingTimer() {
  if (stillGeneratingTimer) {
    clearTimeout(stillGeneratingTimer)
    stillGeneratingTimer = null
  }
}

async function generateAiPreview() {
  if (!manualForm.projectId) {
    ElMessage.warning('请选择绑定项目')
    return
  }
  if (!aiForm.topic.trim()) {
    ElMessage.warning('请填写选题 / 主题')
    return
  }
  generating.value = true
  generationNotice.value = {
    type: 'info',
    title: '正在生成草稿',
    description: '模型生成可能需要几十秒，请保持当前页面打开。',
  }
  parseNotice.value = null
  clearStillGeneratingTimer()
  stillGeneratingTimer = setTimeout(() => {
    if (generating.value) {
      generationNotice.value = {
        type: 'info',
        title: '仍在生成中',
        description: '当前请求仍在等待模型返回，已保留你的全部输入，可继续等待。',
      }
    }
  }, 90000)

  try {
    const { data } = await previewAiContentArticleDraft({
      projectId: manualForm.projectId,
      articleType: manualForm.articleType,
      contentStyle: aiForm.contentStyle,
      tone: aiForm.tone,
      length: aiForm.length,
      topic: aiForm.topic.trim(),
      extraPrompt: aiForm.extraPrompt.trim() || undefined,
      referenceMaterials: aiForm.referenceMaterials.trim() || undefined,
    })
    applyAiPreview(data.data)
  } catch (err) {
    console.error(err)
    generationNotice.value = {
      type: 'error',
      title: aiGenerationFailureTitle(err),
      description: errorMessage(err, '生成失败，请稍后重试。已保留当前生成设置。'),
    }
  } finally {
    generating.value = false
    clearStillGeneratingTimer()
  }
}

function aiGenerationFailureTitle(err: unknown) {
  const status = (err as any)?.response?.status ?? (err as any)?.status
  const code = (err as any)?.code ?? (err as any)?.response?.data?.code
  if (status === 401 || code === 401) {
    return '登录状态已失效'
  }
  if (code === 80201) {
    return 'AI 模型配置异常'
  }
  return 'AI 生成失败'
}

function applyAiPreview(response: ArticleAiDraftPreviewResponse) {
  const contentMarkdown = response.contentMarkdown || ''
  const parsed = parseGeneratedMarkdown(contentMarkdown, response.title || '')
  aiMetadata.value = {
    inputSnapshot: response.inputSnapshot,
    promptSnapshot: response.promptSnapshot,
    modelResponseSnapshot: response.modelResponseSnapshot,
    modelPlatformCode: response.modelPlatformCode,
    modelId: response.modelId,
    modelName: response.modelName,
  }

  if (parsed.status === 'failed') {
    manualMarkdown.value = contentMarkdown
    sourceExpanded.value = true
    parseNotice.value = {
      type: 'error',
      title: '结构解析失败',
      description: '已保留完整 Markdown 源码，请在源码区编辑或手动整理为标题和段落。',
    }
  } else {
    manualForm.title = parsed.title || response.title || manualForm.title
    manualForm.sections = parsed.sections.length ? parsed.sections : [createSection()]
    restoreFieldSync()
    if (parsed.status === 'partial') {
      parseNotice.value = {
        type: 'warning',
        title: '结构解析部分成功',
        description: '已回填可识别内容，但标题或小标题段落不完整，请提交前检查文章结构。',
      }
    } else {
      parseNotice.value = null
    }
  }

  generationNotice.value = {
    type: 'success',
    title: 'AI 草稿已生成',
    description: '内容已回填到编辑区，可继续修改后提交审核。',
  }
}

function parseGeneratedMarkdown(markdownText: string, fallbackTitle = ''): ParsedArticle {
  const lines = markdownText.split(/\r?\n/)
  let title = ''
  const sections: ManualSection[] = []
  let current: ManualSection | null = null

  for (const line of lines) {
    const h1 = line.match(/^#\s+(.+)$/)
    if (h1 && !title) {
      title = h1[1].trim()
      continue
    }
    const h2 = line.match(/^##\s+(.+)$/)
    if (h2) {
      current = { id: nextSectionId++, heading: h2[1].trim(), content: '', collapsed: false }
      sections.push(current)
      continue
    }
    if (current) {
      current.content = appendMarkdownLine(current.content, line)
    }
  }

  title = title || fallbackTitle.trim()
  const normalizedSections = sections
    .map((section) => ({
      ...section,
      heading: section.heading.trim(),
      content: section.content.trim(),
    }))
    .filter((section) => section.heading || section.content)

  if (title && normalizedSections.length > 0) {
    return { status: 'success', title, sections: normalizedSections }
  }
  if (title || normalizedSections.length > 0) {
    return { status: 'partial', title, sections: normalizedSections }
  }
  return { status: 'failed', title: '', sections: [] }
}

function appendMarkdownLine(source: string, line: string) {
  if (!source) return line
  return `${source}\n${line}`
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
  if (!draftTopic.value) {
    ElMessage.warning('请填写文章主题')
    return
  }
  if (!draftContentStyle.value) {
    ElMessage.warning('请选择平台风格')
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
      contentStyle: draftContentStyle.value,
      topic: draftTopic.value,
      topicAsQuestion: createMode.value === 'auto' ? selectedQuestion.value?.questionText : undefined,
      title: manualForm.title.trim(),
      contentMarkdown,
      source: aiMetadata.value ? 'ai_preview' : 'manual',
      aiMetadata: aiMetadata.value || undefined,
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
  await loadProjectOptions()
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
  questionRows.value = []
  selectedQuestionKey.value = ''
  questionLoadedProjectId.value = null
  cleanupImageThumbs()
})

watch(createMode, (mode) => {
  if (mode === 'manual') {
    aiMetadata.value = null
    generationNotice.value = null
    parseNotice.value = null
  }
})

onBeforeUnmount(() => {
  clearStillGeneratingTimer()
  cleanupImageThumbs()
})
</script>

<style scoped>
.manual-article-page {
  height: 100vh;
  overflow: hidden;
  background: #f3f6fa;
  color: #1f2937;
}

.page-toolbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 68px;
  padding: 0 30px;
  border-bottom: 1px solid #e6ebf2;
  background: #ffffff;
  box-shadow: 0 1px 0 rgba(15, 23, 42, 0.02);
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
  font-size: 17px;
  font-weight: 700;
  line-height: 1.3;
  color: #111827;
}

.breadcrumb,
.section-desc,
.form-help,
.source-desc {
  color: #8b95a5;
  font-size: 12px;
}

.ready-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-right: 8px;
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

.mode-switch {
  flex-shrink: 0;
}

.manual-article-page :deep(.el-button) {
  border-radius: 7px;
}

.manual-article-page :deep(.el-input__wrapper),
.manual-article-page :deep(.el-textarea__inner) {
  border-radius: 7px;
  box-shadow: 0 0 0 1px #dfe6ef inset;
}

.manual-article-page :deep(.el-input__wrapper:hover),
.manual-article-page :deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 1px #cbd5e1 inset;
}

.manual-article-page :deep(.el-input__wrapper.is-focus),
.manual-article-page :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px #3b82f6 inset;
}

.manual-article-page :deep(.el-segmented) {
  --el-segmented-item-selected-bg-color: #2563eb;
  --el-segmented-item-selected-color: #ffffff;
  --el-segmented-border-radius: 7px;
}

.page-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  height: calc(100vh - 68px);
  overflow: hidden;
}

.editor-pane,
.preview-pane {
  min-width: 0;
  overflow: auto;
  padding: 26px 30px 42px;
}

.editor-pane {
  border-right: 1px solid #e5ebf3;
}

.preview-pane {
  background: #f4f7fb;
}

.section-card {
  margin-bottom: 18px;
  overflow: hidden;
  border: 1px solid #e4eaf2;
  border-radius: 7px;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.section-card:hover {
  border-color: #d8e1ee;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.05);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 60px;
  padding: 14px 18px;
  border-bottom: 1px solid #e8edf4;
}

.section-header-left {
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
  background: #eef5ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.section-title {
  color: #1f2937;
  font-size: 14px;
  font-weight: 700;
}

.section-body {
  padding: 20px 18px;
}

.ai-card {
  border-color: #bfdbfe;
  box-shadow: 0 0 0 1px rgba(37, 99, 235, 0.04), 0 12px 28px rgba(37, 99, 235, 0.07);
}

.ai-card .section-header {
  border-bottom-color: #dbeafe;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.generate-button {
  min-width: 96px;
  height: 34px;
  border-radius: 7px;
  font-weight: 700;
}

.generate-button.is-disabled,
.generate-button.is-disabled:hover,
.generate-button.is-disabled:focus {
  border-color: #dbe7f7;
  background: #dbe7f7;
  color: #ffffff;
  box-shadow: none;
  cursor: not-allowed;
}

.ai-grid,
.ai-extra-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(260px, 0.65fr);
  gap: 18px;
}

.topic-field {
  min-width: 0;
}

.topic-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.topic-label-row .form-label {
  margin-bottom: 0;
}

.selected-question-line {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.length-label {
  margin-top: 12px;
}

.style-field {
  margin-top: 16px;
}

.style-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.style-tile {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-height: 72px;
  padding: 11px 12px;
  border: 1px solid #dfe6ef;
  border-radius: 7px;
  background: #ffffff;
  color: #1f2937;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: border-color 0.15s ease, background 0.15s ease, box-shadow 0.15s ease;
}

.style-tile:hover,
.style-tile.active {
  border-color: #3b82f6;
}

.style-tile.active {
  background: #eff6ff;
  box-shadow: 0 0 0 1px #3b82f6 inset;
}

.style-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 5px;
  background: #f1f5f9;
  color: #8b95a5;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.style-tile.active .style-icon {
  background: #2563eb;
  color: #ffffff;
}

.style-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.style-name {
  color: #1f2937;
  font-size: 13px;
  font-weight: 700;
}

.style-desc {
  color: #8b95a5;
  font-size: 11px;
  line-height: 1.35;
}

.style-check {
  position: absolute;
  top: 9px;
  right: 9px;
  color: #2563eb;
  font-size: 14px;
}

.ai-extra-collapse {
  margin-top: 10px;
  border-top: 0;
  border-bottom: 0;
}

.ai-extra-collapse :deep(.el-collapse-item__header) {
  height: 34px;
  border-bottom: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.ai-extra-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
}

.generation-alert {
  margin: 0 0 14px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
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
  border: 1px solid #e4eaf2;
  border-radius: 7px;
  background: #fbfcfe;
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
  border: 1px dashed #d8e1ec;
  border-radius: 7px;
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
  background: color-mix(in srgb, #f4f7fb 92%, transparent);
  backdrop-filter: blur(8px);
}

.preview-eyebrow {
  color: #8b95a5;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0;
}

.paper {
  min-height: calc(100vh - 220px);
  padding: 58px 64px;
  border: 1px solid #e4eaf2;
  border-radius: 4px;
  background: #ffffff;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
}

.paper-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 28px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e8edf4;
  color: #8b95a5;
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
  color: #1f2937;
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

.question-picker {
  min-height: 490px;
}

.question-picker-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.question-search-input {
  flex: 1;
  min-width: 220px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
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
  color: #9aa4b2;
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
  border: 1px solid #e4eaf2;
  border-radius: 6px;
  background: #ffffff;
  color: #8b95a5;
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
  .manual-article-page {
    height: auto;
    overflow: visible;
  }

  .page-body {
    grid-template-columns: 1fr;
    height: auto;
    overflow: visible;
  }

  .editor-pane {
    border-right: 0;
    border-bottom: 1px solid #e5ebf3;
  }

  .editor-pane,
  .preview-pane {
    overflow: visible;
  }

  .ai-grid,
  .ai-extra-grid {
    grid-template-columns: 1fr;
  }

  .type-group {
    grid-template-columns: repeat(2, 1fr);
  }

  .style-grid {
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

  .style-grid {
    grid-template-columns: 1fr;
  }

  .preview-stats {
    flex-wrap: wrap;
  }
}

/* Manual article create UI repair pass: keep this block last so it overrides
   older section/card styles left from the previous implementation. */
.manual-article-page {
  height: 100vh;
  overflow: hidden;
  background: #f3f6fa;
}

.page-toolbar {
  height: 64px;
  padding: 0 28px;
}

.sub-toolbar {
  position: sticky;
  top: 64px;
  z-index: 19;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 48px;
  padding: 0 30px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}

.sub-toolbar-left,
.sub-toolbar-actions,
.inline-field {
  display: flex;
  align-items: center;
  min-width: 0;
}

.sub-toolbar-left {
  gap: 18px;
}

.sub-toolbar-spacer {
  flex: 1;
}

.sub-toolbar-actions {
  gap: 8px;
}

.inline-field {
  gap: 8px;
}

.inline-label {
  flex-shrink: 0;
  color: var(--el-text-color-regular);
  font-size: 12px;
  font-weight: 600;
}

.inline-label.required::before {
  content: "*";
  margin-right: 3px;
  color: var(--el-color-danger);
}

.project-select {
  width: min(34vw, 420px);
}

.article-type-select {
  width: 220px;
}

.content-style-select {
  width: 150px;
}

.topic-input {
  width: min(24vw, 320px);
}

.article-type-option {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 3px;
  min-width: 0;
  line-height: 1.35;
}

.article-type-option span {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.article-type-option small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.35;
}

.mode-switch {
  display: inline-flex;
  padding: 2px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.mode-switch :deep(.el-radio-button__inner) {
  border: 0 !important;
  border-radius: 5px !important;
  background: transparent;
  color: var(--el-text-color-secondary);
  box-shadow: none !important;
  font-weight: 600;
}

.mode-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: #ffffff;
  color: var(--el-text-color-primary);
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.12) !important;
}

.mode-switch :deep(.el-radio-button:last-child .el-radio-button__original-radio:checked + .el-radio-button__inner) {
  color: var(--el-color-primary);
}

.page-body {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  height: calc(100vh - 112px);
}

.editor-pane,
.preview-pane {
  padding: 18px 22px 28px;
}

.section-card {
  margin-bottom: 16px;
  border-radius: 8px;
}

.section-header {
  min-height: 50px;
  padding: 12px 16px;
}

.section-index {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  font-size: 11px;
}

.section-body {
  padding: 16px;
}

.ai-grid {
  display: block;
}

.topic-field {
  margin-bottom: 16px;
}

.ai-control-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 16px;
}

.ai-control-grid :deep(.el-segmented) {
  width: 100%;
}

.ai-control-grid :deep(.el-segmented__item) {
  min-width: 0;
}

.ai-control-grid :deep(.el-segmented__item-label) {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.style-field {
  margin-top: 0;
}

.style-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.style-tile {
  min-height: 58px;
  padding: 9px 10px;
}

.style-icon {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.style-tile.active .style-icon {
  background: var(--el-color-primary);
  color: #ffffff;
}

.style-name {
  font-size: 13px;
}

.style-desc {
  font-size: 11px;
}

.ai-extra-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.generation-alert {
  min-height: 38px;
  margin: 12px 0 0;
}

.title-field {
  margin-bottom: 14px;
}

.title-input :deep(.el-input__wrapper) {
  min-height: 44px;
  padding: 0;
  border-radius: 0;
  box-shadow: 0 2px 0 var(--el-border-color) !important;
  background: transparent;
}

.title-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 2px 0 var(--el-color-primary) !important;
}

.title-input :deep(.el-input__inner) {
  color: var(--el-text-color-primary);
  font-size: 17px;
  font-weight: 600;
}

.title-input :deep(.el-input__inner::placeholder) {
  color: var(--el-text-color-placeholder);
  font-weight: 400;
}

.fold-all-link {
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  cursor: pointer;
  font-family: inherit;
  font-size: 12px;
}

.paragraph-list {
  gap: 8px;
}

.paragraph-block {
  border-color: var(--el-border-color-lighter);
  border-radius: 8px;
  background: #ffffff;
}

.paragraph-block.empty {
  border-style: dashed;
  background: var(--el-fill-color-lighter);
}

.paragraph-block:hover {
  border-color: var(--el-border-color);
}

.paragraph-head {
  min-height: 34px;
  padding: 6px 10px;
}

.drag-handle,
.paragraph-actions {
  opacity: 0;
  transition: opacity 0.15s ease;
}

.paragraph-block:hover .drag-handle,
.paragraph-block:hover .paragraph-actions,
.paragraph-block.focused .drag-handle,
.paragraph-block.focused .paragraph-actions {
  opacity: 1;
}

.paragraph-actions {
  gap: 0;
}

.paragraph-actions :deep(.el-button) {
  width: 26px;
  height: 26px;
}

.paragraph-block.collapsed .paragraph-actions :deep(.el-button:nth-child(3) .el-icon) {
  transform: rotate(-90deg);
}

.paragraph-no {
  min-width: 24px;
  height: 22px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}

.heading-input {
  height: 28px;
  padding: 0 6px;
  border-radius: 5px;
  font-size: 13.5px;
}

.heading-input:focus {
  background: var(--el-fill-color-light);
}

.paragraph-body {
  padding: 0;
}

.paragraph-body :deep(.el-textarea__inner) {
  padding: 12px;
  border-radius: 0 0 8px 8px;
  font-size: 13.5px;
  line-height: 1.65;
}

.paragraph-summary {
  overflow: hidden;
  padding: 0 12px 10px 58px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.paragraph-summary-line {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.preview-head {
  top: 0;
  margin-bottom: 12px;
}

.paper {
  position: sticky;
  top: 16px;
  max-height: calc(100vh - 180px);
  min-height: 0;
  overflow: auto;
  padding: 46px 54px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.paper-meta {
  margin-bottom: 24px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
}

.ai-preview-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  border-radius: 999px;
  background: #f3e8ff;
  color: #7c3aed;
  font-size: 11px;
  font-weight: 700;
}

.md-body {
  font-size: 14px;
  line-height: 1.85;
}

.md-body :deep(h1) {
  margin: 0 0 22px;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: 0;
}

.md-body :deep(h2) {
  margin: 24px 0 8px;
  padding-left: 0;
  border-left: 0;
  font-size: 16px;
  font-weight: 600;
}

.md-body :deep(p) {
  margin: 0 0 14px;
  line-height: 1.85;
}

.preview-foot {
  height: 36px;
  margin-top: 12px;
  background: var(--el-fill-color-light);
}

.source-drawer {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.source-drawer-actions {
  display: flex;
  justify-content: flex-end;
}

/* Current admin visual polish pass. */
.manual-article-page {
  display: flex;
  flex-direction: column;
  background:
    linear-gradient(135deg, rgba(248, 251, 255, 0.98) 0%, rgba(241, 245, 249, 0.96) 52%, rgba(236, 253, 245, 0.84) 100%);
}

.page-toolbar {
  position: relative;
  flex-shrink: 0;
  height: 78px;
  margin: 0 0 10px;
  padding: 0 28px;
  overflow: hidden;
  border-bottom: 1px solid #dbeafe;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(239, 246, 255, 0.94) 58%, rgba(236, 253, 245, 0.86));
  box-shadow: 0 16px 36px rgba(37, 99, 235, 0.08);
}

.page-toolbar::before {
  content: "";
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 5px;
  background: linear-gradient(180deg, #2563eb, #06b6d4 48%, #10b981);
}

.page-toolbar::after {
  content: "";
  position: absolute;
  right: 28px;
  top: -34px;
  z-index: 0;
  width: 300px;
  height: 150px;
  opacity: 0.42;
  background-image: repeating-linear-gradient(135deg, rgba(37, 99, 235, 0.18) 0 1px, transparent 1px 12px);
  pointer-events: none;
}

.toolbar-left,
.toolbar-mode,
.toolbar-right {
  position: relative;
  z-index: 2;
}

.back-button {
  border-color: #bfdbfe;
  background: #ffffff;
  color: #2563eb;
}

.toolbar-title h1 {
  color: #0f172a;
  font-size: 20px;
  font-weight: 800;
}

.breadcrumb {
  color: #64748b;
  margin-top: 4px;
}

.ready-state {
  padding: 6px 11px;
  border: 1px solid rgba(191, 219, 254, 0.95);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  color: #475569;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.05);
}

.sub-toolbar {
  position: relative;
  top: auto;
  z-index: 10;
  flex-shrink: 0;
  height: auto;
  min-height: 64px;
  margin: 0 22px 14px;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 251, 255, 0.92));
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(10px);
}

.sub-toolbar-left {
  flex-wrap: wrap;
  gap: 12px;
}

.sub-toolbar-actions {
  flex-wrap: wrap;
}

.inline-label {
  color: #475569;
  font-weight: 800;
}

.inline-field {
  min-height: 38px;
  padding: 0 10px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.inline-field :deep(.el-input__wrapper),
.inline-field :deep(.el-select__wrapper),
.inline-field :deep(.el-cascader__wrapper) {
  box-shadow: none !important;
  background: transparent;
}

.project-select {
  width: min(32vw, 380px);
}

.article-type-select {
  width: 180px;
}

.content-style-select {
  width: 136px;
}

.topic-input {
  width: min(22vw, 280px);
}

.mode-switch {
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.74);
}

.mode-switch :deep(.el-radio-button__inner) {
  border-radius: 999px !important;
}

.mode-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: #2563eb;
  color: #ffffff;
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.22) !important;
}

.page-body {
  flex: 1;
  min-height: 0;
  height: auto;
  grid-template-columns: minmax(0, 1.08fr) minmax(420px, 0.92fr);
  gap: 18px;
  padding: 0 22px 22px;
  overflow: hidden;
}

.editor-pane {
  padding: 0 0 18px;
  border-right: 0;
  background:
    linear-gradient(180deg, rgba(248, 251, 255, 0.72), rgba(255, 255, 255, 0.36));
}

.preview-pane {
  padding: 0 0 18px;
  background:
    linear-gradient(180deg, rgba(248, 251, 255, 0.78), rgba(244, 247, 251, 0.58));
}

.section-card {
  border-color: #dbeafe;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.075);
  backdrop-filter: blur(12px);
}

.section-card:hover {
  border-color: #bfdbfe;
  box-shadow: 0 20px 42px rgba(37, 99, 235, 0.1);
}

.section-header {
  min-height: 56px;
  border-bottom-color: #e2e8f0;
  background: linear-gradient(135deg, #ffffff, #f8fbff 64%, #ecfdf5);
}

.section-index {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: #2563eb;
  color: #ffffff;
  font-weight: 800;
}

.section-title {
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.section-desc,
.form-help {
  color: #64748b;
}

.paragraph-block {
  border-color: #dbeafe;
  border-radius: 12px;
  background:
    linear-gradient(135deg, #ffffff 0%, #ffffff 68%, #f8fbff 100%);
  box-shadow: inset 3px 0 0 rgba(37, 99, 235, 0.18);
}

.paragraph-block.focused {
  border-color: #93c5fd;
  box-shadow:
    inset 3px 0 0 #2563eb,
    0 12px 28px rgba(37, 99, 235, 0.11);
}

.paragraph-block.empty {
  border-color: #cbd5e1;
  background:
    linear-gradient(135deg, rgba(248, 250, 252, 0.92), rgba(239, 246, 255, 0.62));
}

.paragraph-no {
  border-radius: 8px;
  background: #eff6ff;
  color: #2563eb;
  font-weight: 800;
}

.heading-input {
  color: #0f172a;
  font-weight: 700;
}

.add-paragraph-button {
  border-color: #bfdbfe;
  border-radius: 12px;
  background: linear-gradient(135deg, #ffffff, #eff6ff);
  color: #2563eb;
  font-weight: 800;
}

.preview-head {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 10px 12px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(10px);
}

.preview-eyebrow {
  color: #2563eb;
  font-weight: 800;
}

.paper {
  position: sticky;
  top: 64px;
  max-height: calc(100vh - 228px);
  border-color: #dbeafe;
  border-radius: 18px;
  background:
    linear-gradient(180deg, #ffffff 0%, #ffffff 74%, #f8fafc 100%);
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.12);
}

.paper-meta {
  color: #64748b;
}

.preview-foot {
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.82);
}

/* Creation workspace refinement pass. */
.page-toolbar {
  display: grid;
  grid-template-columns: minmax(300px, 1fr) auto minmax(300px, 1fr);
  gap: 18px;
}

.toolbar-mode {
  position: relative;
  z-index: 3;
  display: flex;
  justify-content: center;
}

.toolbar-right {
  justify-content: flex-end;
}

.mode-switch {
  padding: 3px;
  border-color: rgba(191, 219, 254, 0.9);
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.08);
}

.mode-switch :deep(.el-radio-button__inner) {
  min-width: 86px;
  padding: 6px 14px;
  position: relative;
  z-index: 1;
  color: #64748b;
  line-height: 18px;
}

.mode-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  color: #ffffff !important;
}

:global(.article-type-popper .el-select-dropdown__item) {
  height: auto;
  min-height: 58px;
  padding: 8px 24px;
  line-height: normal;
}

:global(.article-type-popper .el-select-dropdown__item.is-selected) {
  background: #f1f5f9;
}

:global(.article-type-popper .el-select-dropdown__item.hover),
:global(.article-type-popper .el-select-dropdown__item:hover) {
  background: #f8fbff;
}

.sub-toolbar {
  min-height: 58px;
  margin-bottom: 18px;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.045);
}

.sub-toolbar-actions {
  display: none;
}

.page-body {
  grid-template-columns: minmax(0, 1.14fr) minmax(420px, 0.86fr);
  gap: 20px;
}

.editor-pane {
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, 0.38) transparent;
}

.editor-pane::-webkit-scrollbar,
.paper::-webkit-scrollbar {
  width: 7px;
}

.editor-pane::-webkit-scrollbar-track,
.paper::-webkit-scrollbar-track {
  background: transparent;
}

.editor-pane::-webkit-scrollbar-thumb,
.paper::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.36);
}

.ai-card {
  border-color: #d7e7ff;
  box-shadow: 0 14px 30px rgba(37, 99, 235, 0.055);
}

.ai-card .section-header {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(248, 251, 255, 0.9));
}

.ai-control-grid {
  gap: 12px;
}

.ai-control-grid .form-item {
  padding: 10px;
  border: 1px solid #edf2f7;
  border-radius: 12px;
  background: #fbfdff;
}

.style-grid {
  gap: 8px;
}

.style-tile {
  min-height: 56px;
  border-color: #e4eaf2;
  background: #ffffff;
  box-shadow: none;
}

.style-tile:hover {
  border-color: #cbd5e1;
  background: #f8fbff;
}

.style-tile.active {
  border-color: #2563eb;
  background: #f6faff;
  box-shadow: 0 0 0 1px rgba(37, 99, 235, 0.18) inset;
}

.ai-extra-collapse {
  margin-top: 14px;
  overflow: hidden;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(248, 250, 252, 0.92), rgba(255, 255, 255, 0.96));
}

.ai-extra-collapse :deep(.el-collapse-item__header) {
  height: 42px;
  padding: 0 14px;
  background: transparent;
  color: #64748b;
  font-weight: 700;
}

.ai-extra-collapse :deep(.el-collapse-item__content) {
  padding: 0 14px 14px;
}

.section-card {
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.06);
}

.paragraph-block {
  box-shadow: none;
}

.paragraph-block.focused {
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.09);
}

.paragraph-head {
  min-height: 40px;
  border-bottom: 1px solid transparent;
}

.paragraph-block.focused .paragraph-head {
  border-bottom-color: #e8edf4;
}

.paragraph-actions :deep(.el-button) {
  color: #64748b;
}

.paragraph-actions :deep(.el-button:hover) {
  color: #2563eb;
  background: #eff6ff;
}

.paragraph-actions :deep(.el-button:last-child:hover) {
  color: #ef4444;
  background: #fff1f2;
}

.paragraph-block.collapsed .paragraph-actions :deep(.el-button:nth-child(4) .el-icon) {
  transform: rotate(-90deg);
}

.paragraph-no {
  background: #f1f5f9;
  color: #64748b;
}

.add-paragraph-button {
  width: auto;
  min-width: 190px;
  align-self: center;
  padding: 8px 18px;
  border-style: dashed;
  background: #ffffff;
  box-shadow: none;
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 12px;
}

.preview-title-wrap {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.preview-subtitle {
  overflow: hidden;
  color: #94a3b8;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
}

.preview-tools :deep(.el-button) {
  height: 28px;
  padding: 0 10px;
  border-color: #e2e8f0;
  background: #ffffff;
  color: #64748b;
  font-weight: 700;
}

.preview-tools .el-radio-group {
  margin-left: 2px;
}

.paper {
  top: 66px;
  max-height: calc(100vh - 236px);
  min-height: 330px;
  padding: 42px 50px;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.08);
}

.preview-empty {
  min-height: 210px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  text-align: center;
}

.preview-empty-workflow {
  flex-direction: column;
  gap: 12px;
  padding: 34px 26px;
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
  background:
    linear-gradient(135deg, rgba(248, 250, 252, 0.92), rgba(255, 255, 255, 0.98)),
    repeating-linear-gradient(135deg, rgba(37, 99, 235, 0.06) 0 1px, transparent 1px 12px);
}

.preview-empty-workflow strong {
  color: #334155;
  font-size: 15px;
}

.preview-empty-workflow span {
  max-width: 420px;
  font-size: 13px;
  line-height: 1.6;
}

.preview-empty-workflow ol {
  display: grid;
  grid-template-columns: repeat(4, auto);
  gap: 8px;
  margin: 4px 0 0;
  padding: 0;
  list-style: none;
}

.preview-empty-workflow li {
  position: relative;
  padding: 5px 10px;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: #f8fbff;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.preview-foot {
  min-height: 38px;
  height: auto;
}

@media (max-width: 1280px) {
  .style-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1280px) {
  .manual-article-page {
    height: auto;
    overflow: visible;
  }

  .sub-toolbar {
    position: sticky;
    top: 64px;
    height: auto;
    min-height: 48px;
    flex-wrap: wrap;
    padding: 8px 18px;
  }
}

@media (max-width: 1024px) {
  .manual-article-page {
    height: auto;
    overflow: visible;
  }

  .page-body {
    grid-template-columns: 1fr;
    height: auto;
    overflow: visible;
  }

  .editor-pane,
  .preview-pane {
    overflow: visible;
  }
}

@media (max-width: 768px) {
  .page-toolbar {
    height: auto;
    min-height: 64px;
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
    padding: 10px 16px;
  }

  .toolbar-left,
  .toolbar-right {
    width: 100%;
    min-width: 0;
  }

  .toolbar-right {
    align-items: center;
    flex-direction: row;
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .toolbar-right :deep(.el-button) {
    min-width: 76px;
  }

  .sub-toolbar {
    position: static;
    align-items: stretch;
    flex-direction: column;
    gap: 8px;
  }

  .sub-toolbar-left,
  .inline-field,
  .ai-control-grid {
    align-items: stretch;
    flex-direction: column;
  }

  .sub-toolbar-actions {
    flex-direction: row;
  }

  .ai-control-grid {
    display: flex;
  }

  .project-select,
  .article-type-select,
  .content-style-select,
  .topic-input {
    width: 100%;
  }

  .style-grid {
    grid-template-columns: 1fr;
  }

  .paper {
    position: static;
    padding: 30px 24px;
  }
}
</style>
