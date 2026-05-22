<template>
  <div class="batch-article-page">
    <div class="page-toolbar">
      <div class="toolbar-left">
        <el-button class="back-button" :icon="Back" aria-label="返回" @click="goBack" />
        <div class="toolbar-title">
          <h1>批量生成文章</h1>
          <nav class="breadcrumb">
            <span>内容与执行</span>
            <span class="breadcrumb-sep">/</span>
            <span>文章管理</span>
            <span class="breadcrumb-sep">/</span>
            <span class="breadcrumb-current">批量生成</span>
          </nav>
        </div>
      </div>
      <div class="toolbar-right">
        <span class="ready-state" :class="{ 'is-ready': canSubmitBatchGeneration, 'is-warning': isBatchCountExceeded }">
          <span class="ready-dot" :class="{ pending: !canSubmitBatchGeneration }" />
          {{ batchStatusText }}
        </span>
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :icon="Check" :disabled="!canSubmitBatchGeneration" :loading="batchSubmitting" @click="submitBatchGeneration">
          生成文章
        </el-button>
      </div>
    </div>

    <main class="page-body">
      <div class="main-panel">
        <div class="page-alert">
          <span class="info-icon">i</span>
          <span>由运营手动发起批量生成，生成完成后自动进入可发布状态，不自动分发。</span>
        </div>

        <section class="section-block">
          <div class="section-head">
            <span class="section-index">1</span>
            <div class="section-head-text">
              <div class="section-title">选择归属项目</div>
              <div class="section-desc">沿用手动生成文章的客户 / 品牌 / 项目级联，只展示当前已激活项目。</div>
            </div>
          </div>
          <div class="section-body">
            <el-cascader
              v-model="batchForm.projectId"
              filterable
              clearable
              :options="projectCascadeOptions"
              :props="projectCascadeProps"
              :loading="projectLoading"
              placeholder="选择客户 / 品牌 / 项目"
              class="project-select"
              @change="handleBatchProjectChange"
            />
            <div v-if="selectedBatchProject" class="project-summary">
              <el-tag type="info">{{ selectedBatchProject.companyName || '未归属客户' }}</el-tag>
              <el-tag type="info">{{ selectedBatchProject.brandName || '未绑定品牌' }}</el-tag>
              <el-tag type="success">{{ selectedBatchProject.projectName }}</el-tag>
            </div>
          </div>
        </section>

        <section class="section-block">
          <div class="section-head">
            <span class="section-index">2</span>
            <div class="section-head-text">
              <div class="section-title">选择文章主题</div>
              <div class="section-desc">可以从当前项目拓词组中多选，也可以手动输入多个主题。</div>
            </div>
          </div>
          <div class="section-body topic-section">
            <div class="topic-actions">
              <el-input
                v-model="manualTopicInput"
                clearable
                placeholder="输入主题后按回车添加"
                class="manual-topic-input"
                @keyup.enter="addManualTopic"
              />
              <el-button type="primary" plain @click="addManualTopic">添加主题</el-button>
              <el-button :disabled="!batchForm.projectId" :loading="batchQuestionPickerLoading" @click="openBatchQuestionPicker">
                从拓词组选择
              </el-button>
            </div>
            <div v-if="selectedTopics.length" class="topic-chip-list">
              <el-tag
                v-for="topic in selectedTopics"
                :key="topic.id"
                closable
                size="large"
                :type="topic.source === 'keyword_group' ? 'success' : 'info'"
                @close="removeTopic(topic.id)"
              >
                {{ topic.topic }}
              </el-tag>
            </div>
            <el-empty v-else description="请先添加至少一个文章主题" :image-size="72" />
          </div>
        </section>

        <section class="section-block">
          <div class="section-head">
            <span class="section-index">3</span>
            <div class="section-head-text">
              <div class="section-title">配置平台生成数量</div>
              <div class="section-desc">每个主题下按平台填写生成数量，0 表示本批次不生成该平台风格文章。</div>
            </div>
          </div>
          <div v-loading="platformLoading" class="section-body matrix-body">
            <div v-if="selectedTopics.length" class="topic-matrix-list">
              <article v-for="topic in selectedTopics" :key="topic.id" class="topic-card">
                <div class="topic-card-head" :class="{ 'is-collapsed': isTopicCollapsed(topic) }">
                  <div class="topic-title-wrap">
                    <div class="topic-title-row">
                      <el-button
                        class="collapse-trigger"
                        link
                        :icon="isTopicCollapsed(topic) ? ArrowRight : ArrowDown"
                        :aria-label="isTopicCollapsed(topic) ? '展开主题' : '折叠主题'"
                        @click="toggleTopicCollapse(topic)"
                      />
                      <div class="topic-title">{{ topic.topic }}</div>
                    </div>
                    <div class="topic-meta">
                      {{ topic.source === 'keyword_group' ? `拓词组：${topic.keywordGroupName || '-'}` : '手动输入' }}
                    </div>
                    <div class="topic-summary-line">
                      <span>{{ topicGeneratedCount(topic) }} 篇</span>
                      <span>覆盖 {{ topicActivePlatformCount(topic) }} 个平台</span>
                      <span>{{ topicSceneSummary(topic) }}</span>
                      <span v-if="topicCustomPlatformCount(topic)">自定义 {{ topicCustomPlatformCount(topic) }} 项</span>
                    </div>
                  </div>
                  <div class="topic-card-actions">
                    <el-button link type="primary" @click="setTopicPreset(topic, 1)">每平台 1 篇</el-button>
                    <el-button link @click="clearTopicCounts(topic)">清空</el-button>
                    <el-button link type="danger" :icon="Delete" @click="removeTopic(topic.id)">移除</el-button>
                  </div>
                </div>
                <div v-show="!isTopicCollapsed(topic)" class="platform-group-list">
                  <section v-for="group in platformGroups" :key="group.key" class="platform-group">
                    <div class="platform-group-head" :class="{ 'is-collapsed': isPlatformGroupCollapsed(topic, group) }">
                      <div>
                        <div class="platform-group-title-row">
                          <el-button
                            class="collapse-trigger"
                            link
                            :icon="isPlatformGroupCollapsed(topic, group) ? ArrowRight : ArrowDown"
                            :aria-label="isPlatformGroupCollapsed(topic, group) ? '展开平台大类' : '折叠平台大类'"
                            @click="togglePlatformGroupCollapse(topic, group)"
                          />
                          <div class="platform-group-title">{{ platformGroupTitle(group) }}</div>
                        </div>
                        <div class="platform-group-desc">{{ group.desc }}</div>
                        <div class="platform-group-summary-line">
                          <span>{{ platformGroupGeneratedCount(topic, group) }} 篇</span>
                          <span>覆盖 {{ platformGroupActivePlatformCount(topic, group) }} 个平台</span>
                          <span>可用 {{ platformGroupTemplateCount(group) }} 个模板</span>
                          <span v-if="platformGroupCustomCount(topic, group)">自定义 {{ platformGroupCustomCount(topic, group) }} 项</span>
                        </div>
                      </div>
                      <div class="platform-group-actions">
                        <el-button link type="primary" @click="setPlatformGroupPreset(topic, group, 1)">本组每项 1 篇</el-button>
                        <el-button link @click="clearPlatformGroupCounts(topic, group)">清空本组</el-button>
                      </div>
                    </div>
                    <div v-show="!isPlatformGroupCollapsed(topic, group)" class="platform-grid">
                      <div v-for="platform in group.platforms" :key="platform.value" class="platform-cell" :class="{ 'is-disabled': platform.disabled }">
                        <div class="platform-label">
                          <img v-if="platform.iconUrl" class="platform-icon image-icon" :src="platform.iconUrl" :alt="platform.label" />
                          <span v-else class="platform-icon">{{ platform.icon }}</span>
                          <span>{{ platform.label }}</span>
                        </div>
                        <div class="platform-desc">{{ platform.desc }}</div>
                        <div v-if="platform.meta" class="platform-meta">{{ platform.meta }}</div>
                        <div v-if="!platform.disabled" class="platform-template-meta">
                          <el-tooltip
                            effect="dark"
                            :content="platform.templates?.map((template) => template.templateName).join('、') || '暂无启用模板'"
                            placement="top"
                          >
                            <button class="template-count-link" type="button" @click="openPlatformTemplates(platform)">
                              可用模板 {{ platform.templateCount || 0 }} 个
                            </button>
                          </el-tooltip>
                          <span class="platform-tags">
                            <el-tag v-if="isSuggestedPlatform(topic, platform)" size="small" type="success">推荐</el-tag>
                            <el-tag v-if="topic.platformAllocationModes[platform.value] === 'custom'" size="small" type="warning">自定义</el-tag>
                          </span>
                        </div>
                        <div v-if="dealNonSuggestedPlatformSelected(topic, platform)" class="platform-warning">
                          成交场景非主推平台，请确认发布目的
                        </div>
                        <div v-if="platform.disabledReason" class="platform-disabled-reason">{{ platform.disabledReason }}</div>
                        <el-input-number
                          v-model="topic.platformCounts[platform.value]"
                          :min="0"
                          :max="MAX_BATCH_ARTICLE_COUNT"
                          :disabled="platform.disabled"
                          controls-position="right"
                        />
                        <div class="platform-cell-actions">
                          <el-button
                            link
                            type="primary"
                            :disabled="platform.disabled || Number(topic.platformCounts[platform.value] || 0) <= 0"
                            @click="openAllocationDialog(topic, platform)"
                          >
                            展开配置
                          </el-button>
                        </div>
                      </div>
                    </div>
                  </section>
                </div>
              </article>
            </div>
            <el-empty v-else description="添加主题后配置平台生成数量" :image-size="72" />
          </div>
        </section>
      </div>

      <aside class="side-panel">
        <div class="summary-header">
          <div class="summary-title">生成配置摘要</div>
          <div class="summary-subtitle">单批次最多生成 {{ MAX_BATCH_ARTICLE_COUNT }} 篇</div>
        </div>
        <ul class="summary-list">
          <li class="summary-item">
            <span class="summary-label">归属项目</span>
            <strong class="summary-value" :class="{ muted: !selectedBatchProject }">{{ selectedBatchProject?.projectName || '未选择' }}</strong>
          </li>
          <li class="summary-item">
            <span class="summary-label">主题数量</span>
            <strong class="summary-value">{{ selectedTopics.length }} 个</strong>
          </li>
          <li class="summary-item">
            <span class="summary-label">平台覆盖</span>
            <strong class="summary-value">{{ selectedPlatformLabels || '未配置' }}</strong>
          </li>
          <li class="summary-item highlight" :class="{ warning: isBatchCountExceeded }">
            <span class="summary-label">生成总量</span>
            <strong class="summary-value">
              <span class="summary-number">{{ batchGeneratedTotal }}</span>
              <span class="summary-unit">/ {{ MAX_BATCH_ARTICLE_COUNT }} 篇</span>
            </strong>
            <span v-if="isBatchCountExceeded" class="summary-limit-warning">已超过单次批量生成上限</span>
          </li>
        </ul>
        <div v-if="batchForm.projectId && selectedTopics.length" v-loading="readinessLoading" class="readiness-card">
          <div class="readiness-card-head">
            <div>
              <div class="readiness-title">资料预检</div>
              <div class="readiness-subtitle">按当前问题场景提示缺失资料</div>
            </div>
            <el-tag :type="readinessTagType">{{ readinessStatusText }}</el-tag>
          </div>
          <div v-if="readinessReport" class="readiness-score-row">
            <span class="readiness-score">{{ readinessReport.score }}</span>
            <span class="readiness-score-label">完整度</span>
          </div>
          <div v-if="readinessMissingBaseItems.length" class="readiness-section">
            <div class="readiness-section-title">基础缺失</div>
            <div class="readiness-chip-list">
              <el-tag v-for="item in readinessMissingBaseItems" :key="item.code" size="small" type="warning">
                {{ item.label }}
              </el-tag>
            </div>
          </div>
          <div v-if="readinessSceneWarnings.length" class="readiness-section">
            <div class="readiness-section-title">场景提醒</div>
            <div v-for="scene in readinessSceneWarnings" :key="scene.questionSceneCode" class="readiness-scene">
              <span class="readiness-scene-name">{{ scene.questionSceneName }}</span>
              <span class="readiness-scene-message">{{ scene.items.map((item) => item.message).join('；') }}</span>
            </div>
          </div>
          <div v-if="readinessReport && !readinessMissingBaseItems.length && !readinessSceneWarnings.length" class="readiness-ok">
            当前资料可支撑所选场景生成。
          </div>
          <div v-if="!readinessReport && !readinessLoading" class="readiness-ok muted">
            暂无可预检的问题场景。
          </div>
        </div>
      </aside>
    </main>

    <el-dialog v-model="allocationDialogVisible" title="模板分配配置" width="760px">
      <div v-if="allocationTopic && allocationPlatform" class="allocation-dialog">
        <div class="allocation-summary">
          <div>
            <strong>{{ allocationTopic.topic }}</strong>
            <span>{{ allocationPlatform.label }}，共 {{ allocationTopic.platformCounts[allocationPlatform.value] || 0 }} 篇</span>
          </div>
          <el-segmented v-model="allocationMode" :options="allocationModeOptions" @change="handleAllocationModeChange" />
        </div>
        <div class="allocation-tip">
          {{ allocationTipText }}
        </div>
        <DataState :loading="allocationPreviewLoading" :empty="!allocationPreviewLoading && allocationRows.length === 0" empty-text="当前平台暂无可用模板">
          <el-table :data="allocationRows" border>
            <el-table-column prop="templateName" label="模板" min-width="220" show-overflow-tooltip />
            <el-table-column prop="articleTypeName" label="文章类型" width="120" />
            <el-table-column v-if="showAgentSiteModuleColumn" label="官网归属" width="110">
              <template #default="{ row }">{{ agentSiteModuleText(row.agentSiteModule) }}</template>
            </el-table-column>
            <el-table-column label="数量" width="180">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.count"
                  :min="0"
                  :max="MAX_BATCH_ARTICLE_COUNT"
                  size="small"
                  controls-position="right"
                  :disabled="allocationMode === 'auto'"
                />
              </template>
            </el-table-column>
          </el-table>
        </DataState>
      </div>
      <template #footer>
        <el-button @click="allocationDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAllocationDialog">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchQuestionPickerVisible" title="选择主题问题词" width="960px">
      <div class="question-picker-toolbar">
        <el-select v-model="batchQuestionFilters.tier" style="width: 140px">
          <el-option label="全部层级" value="all" />
          <el-option label="A 层" value="A" />
          <el-option label="B 层" value="B" />
          <el-option label="C 层" value="C" />
        </el-select>
        <el-select v-model="batchQuestionFilters.scene" style="width: 150px">
          <el-option label="全部场景" value="all" />
          <el-option label="品牌场景" value="brand" />
          <el-option label="决策场景" value="decision" />
          <el-option label="成交场景" value="deal" />
          <el-option label="对比场景" value="compare" />
          <el-option label="问答场景" value="qa" />
          <el-option label="功能场景" value="function" />
        </el-select>
        <el-input v-model="batchQuestionFilters.keyword" clearable placeholder="搜索问题词" style="width: 260px" />
        <el-button :loading="batchQuestionPickerLoading" @click="reloadBatchQuestionPicker">刷新</el-button>
      </div>
      <DataState :loading="batchQuestionPickerLoading" :empty="!batchQuestionPickerLoading && filteredBatchQuestionRows.length === 0" empty-text="暂无可选问题词">
        <el-table
          ref="questionTableRef"
          :data="filteredBatchQuestionRows"
          border
          height="420"
          row-key="selectionKey"
          @selection-change="handleQuestionSelectionChange"
        >
          <el-table-column type="selection" width="48" reserve-selection />
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
      <template #footer>
        <el-button @click="batchQuestionPickerVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedQuestionRows.length" @click="confirmSelectedBatchQuestions">
          添加 {{ selectedQuestionRows.length }} 个主题
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, ArrowRight, Back, Check, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import type { Brand, KeywordGroup, KeywordGroupQuestion, Project, PublishSite } from '@/types'
import {
  checkBatchArticleGenerationReadiness,
  createBatchContentArticles,
  getArticleGenerationOptions,
  previewArticleTemplateAllocation,
  type ArticleGenerationReadinessReport,
  type ArticleGenerationReadinessSceneImpact,
  type ArticleGenerationOptions,
  type ArticleGenerationTemplateOption,
  type BatchArticleGenerateNotice,
  type BatchArticleGenerateTemplateCount,
} from '@/api/content'
import { getBrandDetail } from '@/api/customer'
import { getPublishSites } from '@/api/publishSite'
import { getKeywordGroupQuestions, getProjectDetail, getProjectList } from '@/api/project'

interface ContentStyleOption {
  value: string
  label: string
  desc: string
  icon: string
  contentStyle: string
  channelGroupCode: string
  channelSubCode?: string | null
  templateCount?: number
  templates?: ArticleGenerationTemplateOption[]
  iconUrl?: string | null
  meta?: string
  disabled?: boolean
  disabledReason?: string
}

interface PlatformGroup {
  key: string
  label: string
  desc: string
  platforms: ContentStyleOption[]
}

interface ProjectCascadeNode {
  value: string | number
  label: string
  children?: ProjectCascadeNode[]
}

interface ArticleQuestionOption extends KeywordGroupQuestion {
  groupName: string
  groupType?: string | null
  groupTypeLabel?: string | null
  selectionKey: string
}

interface SelectedTopic {
  id: string
  source: 'manual' | 'keyword_group'
  topic: string
  topicAsQuestion?: string
  questionSceneCode?: string | null
  keywordGroupId?: number
  keywordGroupName?: string
  recommendationApplied?: boolean
  platformCounts: Record<string, number>
  platformAllocationModes: Record<string, 'auto' | 'custom'>
  platformTemplateCounts: Record<string, BatchArticleGenerateTemplateCount[]>
  platformPreviewCounts: Record<string, BatchArticleGenerateTemplateCount[]>
}

const STATIC_PLATFORM_GROUPS: PlatformGroup[] = [
  {
    key: 'self_media',
    label: '自媒体平台',
    desc: '面向账号内容分发，按平台阅读习惯生成不同表达。',
    platforms: [
      { value: 'self_media:toutiao', label: '今日头条', desc: '泛资讯阅读，结论前置', icon: '头', contentStyle: 'toutiao', channelGroupCode: 'self_media', channelSubCode: 'toutiao' },
      { value: 'self_media:wechat', label: '公众号', desc: '完整长文，结构稳', icon: '公', contentStyle: 'wechat', channelGroupCode: 'self_media', channelSubCode: 'wechat' },
      { value: 'self_media:zhihu', label: '知乎', desc: '问题回答，判断清晰', icon: '知', contentStyle: 'zhihu', channelGroupCode: 'self_media', channelSubCode: 'zhihu' },
      { value: 'self_media:douyin', label: '抖音图文', desc: '图文卡片式阅读', icon: '抖', contentStyle: 'douyin', channelGroupCode: 'self_media', channelSubCode: 'douyin' },
      { value: 'self_media:netease', label: '网易', desc: '门户资讯阅读，媒体感强', icon: '网', contentStyle: 'netease', channelGroupCode: 'self_media', channelSubCode: 'netease' },
    ],
  },
  {
    key: 'owned_site',
    label: '站点平台',
    desc: '用于官网或行业站点发布，强调可检索、可引用。',
    platforms: [],
  },
  {
    key: 'external_media',
    label: '外部媒体与社区',
    desc: '面向媒体投放、社区讨论或专业商务场景。',
    platforms: [
      { value: 'authority_media:industry_media', label: '权威媒体', desc: '正式审慎，事实边界', icon: '权', contentStyle: 'authority_media', channelGroupCode: 'authority_media', channelSubCode: 'industry_media' },
      { value: 'forum:', label: '平台网站', desc: '平台网站讨论感', icon: '坛', contentStyle: 'forum', channelGroupCode: 'forum', channelSubCode: null },
    ],
  },
]
const MAX_BATCH_ARTICLE_COUNT = 30

const router = useRouter()
const projectLoading = ref(false)
const platformLoading = ref(false)
const batchSubmitting = ref(false)
const readinessLoading = ref(false)
const readinessReport = ref<ArticleGenerationReadinessReport | null>(null)
const readinessLoadedKey = ref('')
const projectOptions = ref<Project[]>([])
const publishSites = ref<PublishSite[]>([])
const selectedBrand = ref<Brand | null>(null)
const generationOptions = ref<ArticleGenerationOptions | null>(null)
const batchQuestionPickerVisible = ref(false)
const batchQuestionPickerLoading = ref(false)
const allocationDialogVisible = ref(false)
const allocationPreviewLoading = ref(false)
const batchQuestionRows = ref<ArticleQuestionOption[]>([])
const selectedQuestionRows = ref<ArticleQuestionOption[]>([])
const questionTableRef = ref<TableInstance>()
const selectedTopics = ref<SelectedTopic[]>([])
const collapsedTopicMap = ref<Record<string, boolean>>({})
const collapsedPlatformGroupMap = ref<Record<string, boolean>>({})
const manualTopicInput = ref('')
const allocationMode = ref<'auto' | 'custom'>('auto')
const allocationRows = ref<Array<BatchArticleGenerateTemplateCount & {
  templateName?: string
  articleTypeName?: string | null
  agentSiteModule?: string | null
}>>([])
const allocationTarget = reactive({
  topicId: '',
  platformValue: '',
})

const batchForm = reactive({
  projectId: undefined as number | undefined,
})
const batchQuestionFilters = reactive({
  tier: 'all',
  keyword: '',
  scene: 'all',
})
const allocationModeOptions = [
  { label: '自动分配', value: 'auto' },
  { label: '自定义数量', value: 'custom' },
]

const platformGroups = computed(() => buildPlatformGroups())
const platformOptions = computed(() => platformGroups.value.flatMap((group) => group.platforms))
const activePlatformOptions = computed(() => platformOptions.value.filter((platform) => !platform.disabled))
const suggestionMap = computed(() => {
  const entries = generationOptions.value?.questionScenePlatformSuggestions || []
  return Object.fromEntries(entries.map((item) => [item.questionSceneCode, item.platformCodes || []])) as Record<string, string[]>
})
const selectedSceneCodes = computed(() => Array.from(new Set(
  selectedTopics.value
    .map((topic) => topic.questionSceneCode)
    .filter((code): code is string => Boolean(code)),
)))
const readinessRequestKey = computed(() => `${batchForm.projectId || ''}::${selectedSceneCodes.value.join('|')}`)
const readinessSceneMap = computed<Record<string, ArticleGenerationReadinessSceneImpact>>(() => Object.fromEntries(
  (readinessReport.value?.sceneImpacts || []).map((scene) => [scene.questionSceneCode, scene]),
) as Record<string, ArticleGenerationReadinessSceneImpact>)
const readinessMissingBaseItems = computed(() => (readinessReport.value?.baseItems || [])
  .filter((item) => item.status === 'missing'))
const readinessSceneWarnings = computed(() => (readinessReport.value?.sceneImpacts || [])
  .filter((scene) => scene.items?.length))
const readinessTagType = computed(() => {
  if (!readinessReport.value) return 'info'
  if (readinessReport.value.status === 'critical') return 'danger'
  if (readinessReport.value.status === 'warning') return 'warning'
  return 'success'
})
const readinessStatusText = computed(() => {
  if (readinessLoading.value) return '检查中'
  if (!readinessReport.value) return '未检查'
  if (readinessReport.value.status === 'critical') return '需确认'
  if (readinessReport.value.status === 'warning') return '有缺失'
  return '正常'
})
const projectCascadeProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: false,
}
const projectCascadeOptions = computed(() => buildProjectCascadeOptions(projectOptions.value))
const selectedBatchProject = computed(() => projectOptions.value.find((project) => project.id === batchForm.projectId) || null)
const allocationTopic = computed(() => selectedTopics.value.find((topic) => topic.id === allocationTarget.topicId) || null)
const allocationPlatform = computed(() => platformOptions.value.find((platform) => platform.value === allocationTarget.platformValue) || null)
const showAgentSiteModuleColumn = computed(() => allocationPlatform.value?.channelGroupCode === 'agent_site')
const allocationTipText = computed(() => (
  allocationTopic.value?.questionSceneCode
    ? '已展示当前平台全部启用模板，默认数量会分配到匹配当前问题类型的模板；其余模板为 0，可切换“自定义数量”调整。'
    : '已展示当前平台全部启用模板，未绑定问题类型时会按全部可用模板权重分配；可切换“自定义数量”调整。'
))
const filteredBatchQuestionRows = computed(() => {
  const keyword = batchQuestionFilters.keyword.trim()
  return batchQuestionRows.value.filter((row) => {
    const tierMatched = batchQuestionFilters.tier === 'all' || row.questionTier === batchQuestionFilters.tier
    const sceneMatched = batchQuestionFilters.scene === 'all' || row.sceneCode === batchQuestionFilters.scene
    const keywordMatched = !keyword || row.questionText.includes(keyword) || row.groupName.includes(keyword)
    return tierMatched && sceneMatched && keywordMatched
  })
})
const batchGeneratedTotal = computed(() => selectedTopics.value.reduce((total, topic) => (
  total + activePlatformOptions.value.reduce((sum, platform) => sum + Number(topic.platformCounts[platform.value] || 0), 0)
), 0))
const isBatchCountExceeded = computed(() => batchGeneratedTotal.value > MAX_BATCH_ARTICLE_COUNT)
const selectedPlatformLabels = computed(() => activePlatformOptions.value
  .filter((platform) => selectedTopics.value.some((topic) => Number(topic.platformCounts[platform.value] || 0) > 0))
  .map((platform) => platform.label)
  .join('、'))
const canSubmitBatchGeneration = computed(() => Boolean(
  batchForm.projectId
  && selectedTopics.value.length
  && batchGeneratedTotal.value > 0
  && !isBatchCountExceeded.value,
))
const batchStatusText = computed(() => {
  if (isBatchCountExceeded.value) return `最多生成 ${MAX_BATCH_ARTICLE_COUNT} 篇`
  return canSubmitBatchGeneration.value ? `预计生成 ${batchGeneratedTotal.value} 篇` : '配置待完善'
})

function topicGeneratedCount(topic: SelectedTopic) {
  return activePlatformOptions.value.reduce((sum, platform) => sum + Number(topic.platformCounts[platform.value] || 0), 0)
}

function topicActivePlatformCount(topic: SelectedTopic) {
  return activePlatformOptions.value.filter((platform) => Number(topic.platformCounts[platform.value] || 0) > 0).length
}

function topicCustomPlatformCount(topic: SelectedTopic) {
  return activePlatformOptions.value.filter((platform) => (
    Number(topic.platformCounts[platform.value] || 0) > 0
    && topic.platformAllocationModes[platform.value] === 'custom'
  )).length
}

function topicSceneSummary(topic: SelectedTopic) {
  return topic.questionSceneCode ? `场景：${sceneLabel(topic.questionSceneCode)}` : '未绑定问题场景'
}

function platformCode(platform: ContentStyleOption) {
  return `${platform.channelGroupCode}:${platform.channelSubCode || ''}`
}

function suggestedPlatformCodes(topic: SelectedTopic) {
  return topic.questionSceneCode ? suggestionMap.value[topic.questionSceneCode] || [] : []
}

function isSuggestedPlatform(topic: SelectedTopic, platform: ContentStyleOption) {
  return suggestedPlatformCodes(topic).includes(platformCode(platform))
}

function isUniversalQuestionTypePlatform(platform: ContentStyleOption) {
  return platform.channelGroupCode === 'forum'
}

function dealNonSuggestedPlatformSelected(topic: SelectedTopic, platform: ContentStyleOption) {
  return topic.questionSceneCode === 'deal'
    && Number(topic.platformCounts[platform.value] || 0) > 0
    && !isUniversalQuestionTypePlatform(platform)
    && !isSuggestedPlatform(topic, platform)
}

function platformGroupGeneratedCount(topic: SelectedTopic, group: PlatformGroup) {
  return group.platforms
    .filter((platform) => !platform.disabled)
    .reduce((sum, platform) => sum + Number(topic.platformCounts[platform.value] || 0), 0)
}

function platformGroupActivePlatformCount(topic: SelectedTopic, group: PlatformGroup) {
  return group.platforms.filter((platform) => !platform.disabled && Number(topic.platformCounts[platform.value] || 0) > 0).length
}

function platformGroupCustomCount(topic: SelectedTopic, group: PlatformGroup) {
  return group.platforms.filter((platform) => (
    !platform.disabled
    && Number(topic.platformCounts[platform.value] || 0) > 0
    && topic.platformAllocationModes[platform.value] === 'custom'
  )).length
}

function platformGroupTemplateCount(group: PlatformGroup) {
  return group.platforms.reduce((sum, platform) => sum + Number(platform.templateCount || 0), 0)
}

function platformGroupCollapseKey(topic: SelectedTopic, group: PlatformGroup) {
  return `${topic.id}::${group.key}`
}

function isTopicCollapsed(topic: SelectedTopic) {
  return Boolean(collapsedTopicMap.value[topic.id])
}

function toggleTopicCollapse(topic: SelectedTopic) {
  collapsedTopicMap.value = {
    ...collapsedTopicMap.value,
    [topic.id]: !collapsedTopicMap.value[topic.id],
  }
}

function isPlatformGroupCollapsed(topic: SelectedTopic, group: PlatformGroup) {
  return Boolean(collapsedPlatformGroupMap.value[platformGroupCollapseKey(topic, group)])
}

function hasPlatformGroupCollapseState(topic: SelectedTopic, group: PlatformGroup) {
  return Object.prototype.hasOwnProperty.call(collapsedPlatformGroupMap.value, platformGroupCollapseKey(topic, group))
}

function collapseMissingPlatformGroups(topic: SelectedTopic) {
  const next = { ...collapsedPlatformGroupMap.value }
  for (const group of platformGroups.value) {
    if (!hasPlatformGroupCollapseState(topic, group)) {
      next[platformGroupCollapseKey(topic, group)] = true
    }
  }
  collapsedPlatformGroupMap.value = next
}

function togglePlatformGroupCollapse(topic: SelectedTopic, group: PlatformGroup) {
  const key = platformGroupCollapseKey(topic, group)
  collapsedPlatformGroupMap.value = {
    ...collapsedPlatformGroupMap.value,
    [key]: !collapsedPlatformGroupMap.value[key],
  }
}

function createPlatformCounts() {
  return Object.fromEntries(platformOptions.value.map((platform) => [platform.value, 0])) as Record<string, number>
}

function createPlatformAllocationModes() {
  return Object.fromEntries(platformOptions.value.map((platform) => [platform.value, 'auto'])) as Record<string, 'auto' | 'custom'>
}

function createPlatformTemplateCounts() {
  return Object.fromEntries(platformOptions.value.map((platform) => [platform.value, []])) as Record<string, BatchArticleGenerateTemplateCount[]>
}

function syncTopicPlatformKeys() {
  for (const topic of selectedTopics.value) {
    for (const platform of platformOptions.value) {
      if (topic.platformCounts[platform.value] == null) topic.platformCounts[platform.value] = 0
      if (!topic.platformAllocationModes[platform.value]) topic.platformAllocationModes[platform.value] = 'auto'
      if (!topic.platformTemplateCounts[platform.value]) topic.platformTemplateCounts[platform.value] = []
      if (!topic.platformPreviewCounts[platform.value]) topic.platformPreviewCounts[platform.value] = []
    }
    applySuggestedPlatformDefaults(topic)
    collapseMissingPlatformGroups(topic)
  }
}

function applySuggestedPlatformDefaults(topic: SelectedTopic) {
  if (!generationOptions.value) {
    return
  }
  if (topic.recommendationApplied || topicGeneratedCount(topic) > 0) {
    return
  }
  const suggested = new Set(suggestedPlatformCodes(topic))
  if (!suggested.size) {
    topic.recommendationApplied = true
    return
  }
  let applied = false
  for (const platform of activePlatformOptions.value) {
    if (suggested.has(platformCode(platform))) {
      topic.platformCounts[platform.value] = 1
      topic.platformAllocationModes[platform.value] = 'auto'
      applied = true
    }
  }
  if (applied) {
    topic.recommendationApplied = true
  }
}

async function handleBatchProjectChange() {
  selectedTopics.value = []
  selectedQuestionRows.value = []
  batchQuestionRows.value = []
  manualTopicInput.value = ''
  readinessReport.value = null
  readinessLoadedKey.value = ''
  await loadSelectedBrand()
  syncTopicPlatformKeys()
}

async function loadReadiness(force = false) {
  const key = readinessRequestKey.value
  if (!batchForm.projectId || !selectedSceneCodes.value.length) {
    readinessReport.value = null
    readinessLoadedKey.value = ''
    return null
  }
  if (!force && readinessReport.value && readinessLoadedKey.value === key) {
    return readinessReport.value
  }
  readinessLoading.value = true
  try {
    const { data } = await checkBatchArticleGenerationReadiness({
      projectId: batchForm.projectId,
      questionSceneCodes: selectedSceneCodes.value,
    })
    if (readinessRequestKey.value === key) {
      readinessReport.value = data.data
      readinessLoadedKey.value = key
    }
    return data.data
  } catch (err) {
    console.error(err)
    readinessReport.value = null
    readinessLoadedKey.value = ''
    ElMessage.warning('资料预检失败，请稍后重试')
    return null
  } finally {
    readinessLoading.value = false
  }
}

function addManualTopic() {
  const raw = manualTopicInput.value.trim()
  if (!raw) {
    ElMessage.warning('请输入文章主题')
    return
  }
  const topics = raw.split(/[\n,，;；]/).map((item) => item.trim()).filter(Boolean)
  let added = 0
  for (const topic of topics) {
    if (appendTopic({
      id: `manual:${topic}`,
      source: 'manual',
      topic,
      questionSceneCode: null,
      platformCounts: createPlatformCounts(),
      platformAllocationModes: createPlatformAllocationModes(),
      platformTemplateCounts: createPlatformTemplateCounts(),
      platformPreviewCounts: createPlatformTemplateCounts(),
    })) {
      added += 1
    }
  }
  manualTopicInput.value = ''
  if (added > 0) {
    ElMessage.success(`已添加 ${added} 个主题`)
  }
}

function appendTopic(topic: SelectedTopic) {
  if (selectedTopics.value.some((item) => item.id === topic.id || item.topic === topic.topic)) {
    return false
  }
  selectedTopics.value.push(topic)
  applySuggestedPlatformDefaults(topic)
  collapseMissingPlatformGroups(topic)
  return true
}

function removeTopic(topicId: string) {
  selectedTopics.value = selectedTopics.value.filter((topic) => topic.id !== topicId)
  const remainingTopics = { ...collapsedTopicMap.value }
  delete remainingTopics[topicId]
  collapsedTopicMap.value = remainingTopics
  collapsedPlatformGroupMap.value = Object.fromEntries(
    Object.entries(collapsedPlatformGroupMap.value).filter(([key]) => !key.startsWith(`${topicId}::`)),
  )
}

function setTopicPreset(topic: SelectedTopic, count: number) {
  topic.recommendationApplied = true
  for (const platform of activePlatformOptions.value) {
    topic.platformCounts[platform.value] = count
    topic.platformAllocationModes[platform.value] = 'auto'
  }
}

function clearTopicCounts(topic: SelectedTopic) {
  topic.recommendationApplied = true
  for (const platform of platformOptions.value) {
    topic.platformCounts[platform.value] = 0
    topic.platformTemplateCounts[platform.value] = []
    topic.platformPreviewCounts[platform.value] = []
    topic.platformAllocationModes[platform.value] = 'auto'
  }
}

function setPlatformGroupPreset(topic: SelectedTopic, group: PlatformGroup, count: number) {
  topic.recommendationApplied = true
  for (const platform of group.platforms.filter((item) => !item.disabled)) {
    topic.platformCounts[platform.value] = count
    topic.platformAllocationModes[platform.value] = 'auto'
  }
}

function clearPlatformGroupCounts(topic: SelectedTopic, group: PlatformGroup) {
  topic.recommendationApplied = true
  for (const platform of group.platforms) {
    topic.platformCounts[platform.value] = 0
    topic.platformTemplateCounts[platform.value] = []
    topic.platformPreviewCounts[platform.value] = []
    topic.platformAllocationModes[platform.value] = 'auto'
  }
}

async function openAllocationDialog(topic: SelectedTopic, platform: ContentStyleOption) {
  const count = Number(topic.platformCounts[platform.value] || 0)
  if (count <= 0) {
    ElMessage.warning('请先填写该平台的生成数量')
    return
  }
  allocationTarget.topicId = topic.id
  allocationTarget.platformValue = platform.value
  allocationMode.value = topic.platformAllocationModes[platform.value] || 'auto'
  allocationDialogVisible.value = true
  if (allocationMode.value === 'custom' && topic.platformTemplateCounts[platform.value]?.length) {
    allocationRows.value = hydrateTemplateRows(platform, topic.platformTemplateCounts[platform.value], true)
    return
  }
  await loadAllocationPreview(topic, platform)
}

async function handleAllocationModeChange() {
  const topic = allocationTopic.value
  const platform = allocationPlatform.value
  if (!topic || !platform) return
  if (allocationMode.value === 'auto') {
    await loadAllocationPreview(topic, platform)
    return
  }
  if (!allocationRows.value.length) {
    allocationRows.value = hydrateTemplateRows(platform, [], true)
  }
}

async function loadAllocationPreview(topic: SelectedTopic, platform: ContentStyleOption) {
  allocationPreviewLoading.value = true
  try {
    const count = Number(topic.platformCounts[platform.value] || 0)
    const { data } = await previewArticleTemplateAllocation({
      channelGroupCode: platform.channelGroupCode,
      channelSubCode: platform.channelSubCode || null,
      questionSceneCode: topic.questionSceneCode || null,
      count,
    })
    allocationRows.value = hydrateTemplateRows(platform, data.data.items, true)
    topic.platformPreviewCounts[platform.value] = allocationRows.value.map(toTemplateCount).filter((item) => item.count > 0)
  } catch (err) {
    console.error(err)
    allocationRows.value = []
    ElMessage.error('预览模板分配失败')
  } finally {
    allocationPreviewLoading.value = false
  }
}

function hydrateTemplateRows(platform: ContentStyleOption, counts: BatchArticleGenerateTemplateCount[], includeAllTemplates = false) {
  const templateMap = new Map((platform.templates || []).map((template) => [template.templateId, template]))
  const countMap = new Map(counts.map((item) => [item.templateId, item]))
  const baseCounts = includeAllTemplates
    ? (platform.templates || []).map((template) => ({
        templateId: template.templateId,
        templateVersionId: template.templateVersionId,
        count: Number(countMap.get(template.templateId)?.count || 0),
        extraPrompt: countMap.get(template.templateId)?.extraPrompt,
      }))
    : counts
  return baseCounts.map((item) => {
    const template = templateMap.get(item.templateId)
    return {
      ...item,
      templateVersionId: item.templateVersionId || template?.templateVersionId,
      templateName: template?.templateName || `模板 #${item.templateId}`,
      articleTypeName: template?.articleTypeName,
      agentSiteModule: template?.agentSiteModule,
    }
  })
}

function confirmAllocationDialog() {
  const topic = allocationTopic.value
  const platform = allocationPlatform.value
  if (!topic || !platform) return
  const rows = allocationRows.value.map(toTemplateCount).filter((item) => item.count > 0)
  if (allocationMode.value === 'custom') {
    topic.recommendationApplied = true
    const total = rows.reduce((sum, item) => sum + item.count, 0)
    topic.platformCounts[platform.value] = total
    topic.platformTemplateCounts[platform.value] = rows
    topic.platformAllocationModes[platform.value] = 'custom'
  } else {
    topic.recommendationApplied = true
    topic.platformPreviewCounts[platform.value] = rows
    topic.platformTemplateCounts[platform.value] = []
    topic.platformAllocationModes[platform.value] = 'auto'
  }
  allocationDialogVisible.value = false
}

function toTemplateCount(row: BatchArticleGenerateTemplateCount): BatchArticleGenerateTemplateCount {
  return {
    templateId: row.templateId,
    templateVersionId: row.templateVersionId,
    count: Number(row.count || 0),
    extraPrompt: row.extraPrompt,
  }
}

function agentSiteModuleText(value?: string | null) {
  if (!value) return '-'
  return ({ faq: 'FAQ', knowledge: '知识库', product: '产品服务' } as Record<string, string>)[value] || value
}

async function loadProjectOptions() {
  projectLoading.value = true
  try {
    const { data } = await getProjectList({ current: 1, size: 500, status: 'active' })
    projectOptions.value = mergeProjects(projectOptions.value, data.data.records || [])
  } catch (err) {
    console.error(err)
    projectOptions.value = []
    ElMessage.error('加载项目失败')
  } finally {
    projectLoading.value = false
  }
}

async function loadPublishPlatformOptions() {
  platformLoading.value = true
  try {
    const [{ data }, optionResponse] = await Promise.all([
      getPublishSites({ status: 'active' }),
      getArticleGenerationOptions(),
    ])
    publishSites.value = data.data || []
    generationOptions.value = optionResponse.data.data
    syncTopicPlatformKeys()
  } catch (err) {
    console.error(err)
    publishSites.value = []
    generationOptions.value = null
    ElMessage.error('加载发布平台失败')
  } finally {
    platformLoading.value = false
  }
}

async function loadSelectedBrand() {
  const brandId = selectedBatchProject.value?.brandId
  selectedBrand.value = null
  if (!brandId) return
  try {
    const { data } = await getBrandDetail(brandId)
    selectedBrand.value = data.data
  } catch (err) {
    console.error(err)
    ElMessage.error('加载品牌绑定站点失败')
  }
}

function buildPlatformGroups(): PlatformGroup[] {
  if (generationOptions.value?.groups?.length) {
    return generationOptions.value.groups.map((group, index) => ({
      key: buildPlatformGroupUiKey(group, index),
      label: normalizePlatformGroupLabel(group.code, group.name),
      desc: group.description,
      platforms: group.channels.map((channel) => buildChannelOption(channel)),
    }))
  }
  const groups = STATIC_PLATFORM_GROUPS.map((group) => ({
    ...group,
    platforms: [...group.platforms],
  }))
  const ownedSiteGroup = groups.find((group) => group.key === 'owned_site')
  if (ownedSiteGroup) {
    ownedSiteGroup.platforms = [
      buildAgentSiteOption(),
      buildIndustrySiteOption(),
    ]
  }
  return groups
}

function buildPlatformGroupUiKey(group: ArticleGenerationOptions['groups'][number], index: number) {
  const groupCode = group.code?.trim()
  return `${groupCode || 'platform_group'}::${index}`
}

function normalizePlatformGroupLabel(groupCode?: string | null, label?: string | null) {
  const text = label?.trim()
  if (text) return text
  const labels: Record<string, string> = {
    agent_site: 'Agent 官网',
    industry_site: '行业资讯站',
    self_media: '自媒体平台',
    authority_media: '权威媒体',
    forum: '平台网站',
  }
  return labels[groupCode?.trim() || ''] || '分发平台'
}

function platformGroupTitle(group: PlatformGroup) {
  return group.label?.trim() || '分发平台'
}

function buildChannelOption(channel: ArticleGenerationOptions['groups'][number]['channels'][number]): ContentStyleOption {
  const value = `${channel.channelGroupCode}:${channel.channelSubCode || ''}`
  const option: ContentStyleOption = {
    value,
    label: channel.label,
    desc: channel.description,
    icon: channelIcon(channel.channelGroupCode, channel.channelSubCode),
    contentStyle: channel.contentStyle,
    channelGroupCode: channel.channelGroupCode,
    channelSubCode: channel.channelSubCode || null,
    templateCount: channel.templateCount,
    templates: channel.templates || [],
    disabled: !channel.enabled,
    disabledReason: channel.disabledReason || undefined,
  }
  if (channel.channelGroupCode === 'agent_site') {
    return { ...option, ...buildAgentSiteOption(option) }
  }
  if (channel.channelGroupCode === 'industry_site') {
    return { ...option, ...buildIndustrySiteOption(option) }
  }
  return option
}

function buildAgentSiteOption(base?: ContentStyleOption): ContentStyleOption {
  const agentSite = findAgentPublishSite()
  const geoSiteCode = selectedBrand.value?.geoSiteCode?.trim()
  const isActive = selectedBrand.value?.geoSiteStatus === 'active'
  const disabled = !geoSiteCode || !isActive
  return {
    value: base?.value || 'agent_site:',
    label: base?.label || 'Agent 官网',
    desc: base?.desc || '官网文章风格，适合品牌自有 GEO 站点发布',
    icon: base?.icon || 'A',
    contentStyle: base?.contentStyle || 'agent_site_article',
    channelGroupCode: 'agent_site',
    channelSubCode: null,
    templateCount: base?.templateCount,
    templates: base?.templates,
    iconUrl: agentSite?.iconUrl || null,
    meta: geoSiteCode ? `绑定站点：${geoSiteCode}` : undefined,
    disabled: disabled || base?.disabled,
    disabledReason: disabled ? '当前品牌未绑定可用 Agent 官网' : base?.disabledReason,
  }
}

function buildIndustrySiteOption(base?: ContentStyleOption): ContentStyleOption {
  const brand = selectedBrand.value
  const site = findIndustryPublishSite(brand?.industrySiteCode, brand?.industrySiteName)
  const label = brand?.industrySiteName || site?.siteName || base?.label || '行业资讯站'
  const disabled = !brand?.industrySiteCode && !brand?.industrySiteName
  return {
    value: base?.value || 'industry_site:',
    label,
    desc: base?.desc || '行业资讯站稿件，客观中立、可检索、可引用',
    icon: base?.icon || '讯',
    contentStyle: base?.contentStyle || 'industry_site',
    channelGroupCode: 'industry_site',
    channelSubCode: null,
    templateCount: base?.templateCount,
    templates: base?.templates,
    iconUrl: site?.iconUrl || null,
    meta: buildSiteMeta(site, brand?.industrySiteCode),
    disabled: disabled || base?.disabled,
    disabledReason: disabled ? '当前品牌未绑定行业资讯站' : base?.disabledReason,
  }
}

function channelIcon(group: string, sub?: string | null) {
  const icons: Record<string, string> = {
    agent_site: 'A',
    industry_site: '讯',
    forum: '坛',
    toutiao: '头',
    wechat: '公',
    zhihu: '知',
    douyin: '抖',
    xiaohongshu: '红',
    baijiahao: '百',
    netease: '网',
    industry_media: '行',
    local_media: '地',
    finance_media: '财',
    tech_media: '科',
    news_source: '新',
    portal_media: '门',
  }
  return icons[sub || group] || '文'
}

function findAgentPublishSite() {
  return publishSites.value.find((site) => (
    site.integrationMethod === 'brand_geo_site' || site.siteCode === 'agent_official_site'
  )) || null
}

function findIndustryPublishSite(siteCode?: string | null, siteName?: string | null) {
  const normalizedCode = siteCode?.trim().toLowerCase()
  const normalizedName = siteName?.trim()
  return publishSites.value.find((site) => (
    site.integrationMethod !== 'brand_geo_site'
    && site.siteCode !== 'agent_official_site'
    && ((normalizedCode && site.siteCode === normalizedCode) || (normalizedName && site.siteName === normalizedName))
  )) || null
}

function buildSiteMeta(site?: PublishSite | null, fallbackCode?: string | null) {
  if (site?.domain) return `站点：${site.siteName}（${site.domain}）`
  if (site?.siteName) return `站点：${site.siteName}`
  if (fallbackCode) return `绑定站点：${fallbackCode}`
  return undefined
}

function openPlatformTemplates(platform: ContentStyleOption) {
  const query: Record<string, string> = {
    channelGroupCode: platform.channelGroupCode,
    status: 'active',
  }
  if (platform.channelSubCode) {
    query.channelSubCode = platform.channelSubCode
  }
  router.push({ name: 'ArticlePromptTemplates', query })
}

function mergeProjects(primary: Project[], secondary: Project[]) {
  const map = new Map<number, Project>()
  for (const item of [...primary, ...secondary]) {
    map.set(item.id, item)
  }
  return Array.from(map.values())
}

function buildProjectCascadeOptions(projects: Project[]): ProjectCascadeNode[] {
  const companyMap = new Map<string, ProjectCascadeNode>()
  const brandMap = new Map<string, ProjectCascadeNode>()
  const sortedProjects = [...projects].sort(compareProjectsForCascade)

  for (const project of sortedProjects) {
    const companyKey = `company:${project.companyId ?? 'none'}:${project.companyName || '未归属客户'}`
    let companyNode = companyMap.get(companyKey)
    if (!companyNode) {
      companyNode = { value: companyKey, label: project.companyName || '未归属客户', children: [] }
      companyMap.set(companyKey, companyNode)
    }

    const brandKey = `${companyKey}:brand:${project.brandId ?? 'none'}:${project.brandName || '未绑定品牌'}`
    let brandNode = brandMap.get(brandKey)
    if (!brandNode) {
      brandNode = { value: brandKey, label: project.brandName || '未绑定品牌', children: [] }
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

async function openBatchQuestionPicker() {
  if (!batchForm.projectId) {
    ElMessage.warning('请先选择归属项目')
    return
  }
  batchQuestionPickerVisible.value = true
  if (!batchQuestionRows.value.length) {
    await loadBatchQuestionPicker()
  }
  await nextTick()
  syncQuestionSelection()
}

async function reloadBatchQuestionPicker() {
  if (!batchForm.projectId) return
  await loadBatchQuestionPicker()
  await nextTick()
  syncQuestionSelection()
}

async function loadBatchQuestionPicker() {
  const projectId = batchForm.projectId
  if (!projectId) return
  batchQuestionPickerLoading.value = true
  try {
    let project = selectedBatchProject.value
    if (!project?.selectedKeywordGroups?.length) {
      const { data } = await getProjectDetail(projectId)
      projectOptions.value = mergeProjects([data.data], projectOptions.value)
      project = data.data
    }

    const groups = project?.selectedKeywordGroups || []
    batchQuestionRows.value = await loadProjectQuestions(groups)
  } catch (err) {
    console.error(err)
    batchQuestionRows.value = []
    selectedQuestionRows.value = []
    ElMessage.error('加载项目问题词失败')
  } finally {
    batchQuestionPickerLoading.value = false
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
        selectionKey: questionKey(question),
      })))
      current += 1
    } while ((current - 1) * 100 < total)
  }
  return rows
}

function handleQuestionSelectionChange(rows: ArticleQuestionOption[]) {
  selectedQuestionRows.value = rows
}

function syncQuestionSelection() {
  questionTableRef.value?.clearSelection()
  const selectedIds = new Set(selectedTopics.value.filter((topic) => topic.source === 'keyword_group').map((topic) => topic.id))
  for (const row of filteredBatchQuestionRows.value) {
    if (selectedIds.has(questionKey(row))) {
      questionTableRef.value?.toggleRowSelection(row, true)
    }
  }
}

function confirmSelectedBatchQuestions() {
  let added = 0
  for (const question of selectedQuestionRows.value) {
    if (appendTopic({
      id: questionKey(question),
      source: 'keyword_group',
      topic: question.questionText,
      topicAsQuestion: question.questionText,
      questionSceneCode: question.sceneCode || null,
      keywordGroupId: question.groupId,
      keywordGroupName: question.groupName,
      platformCounts: createPlatformCounts(),
      platformAllocationModes: createPlatformAllocationModes(),
      platformTemplateCounts: createPlatformTemplateCounts(),
      platformPreviewCounts: createPlatformTemplateCounts(),
    })) {
      added += 1
    }
  }
  batchQuestionPickerVisible.value = false
  if (added > 0) {
    ElMessage.success(`已添加 ${added} 个主题`)
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

async function submitBatchGeneration() {
  if (isBatchCountExceeded.value) {
    ElMessage.warning(`单次批量生成最多 ${MAX_BATCH_ARTICLE_COUNT} 篇文章，请减少生成条数`)
    return
  }
  if (!canSubmitBatchGeneration.value) {
    ElMessage.warning('请先选择项目、添加主题并配置生成数量')
    return
  }
  const confirmedReadinessWarnings = await confirmReadinessWarnings()
  if (!confirmedReadinessWarnings) {
    return
  }
  const confirmedPlatformWarnings = await confirmPlatformWarnings()
  if (!confirmedPlatformWarnings) {
    return
  }
  const payloadTopics = selectedTopics.value.map((topic) => ({
    topic: topic.topic,
    topicAsQuestion: topic.topicAsQuestion,
    questionSceneCode: topic.questionSceneCode || undefined,
    keywordGroupId: topic.keywordGroupId,
    keywordGroupName: topic.keywordGroupName,
    readinessWarningConfirmed: Boolean(confirmedReadinessWarnings[topic.id]?.length),
    readinessWarningCodes: confirmedReadinessWarnings[topic.id]?.length ? confirmedReadinessWarnings[topic.id] : undefined,
    platforms: activePlatformOptions.value.map((platform) => {
      const mode = topic.platformAllocationModes[platform.value] || 'auto'
      return {
        contentStyle: platform.contentStyle,
        channelGroupCode: platform.channelGroupCode,
        channelSubCode: platform.channelSubCode || undefined,
        allocationMode: mode,
        count: Number(topic.platformCounts[platform.value] || 0),
        templateCounts: mode === 'custom' ? topic.platformTemplateCounts[platform.value] || [] : undefined,
        previewTemplateCounts: mode === 'auto' ? topic.platformPreviewCounts[platform.value] || [] : undefined,
      }
    }),
  }))
  batchSubmitting.value = true
  try {
    const { data } = await createBatchContentArticles({
      projectId: batchForm.projectId!,
      topicSource: selectedTopics.value.some((topic) => topic.source === 'keyword_group') ? 'keyword_group' : 'manual',
      topics: payloadTopics,
    })
    const notices = data.data.notices || []
    if (notices.length) {
      await showGenerationNotices(data.data.totalCount, notices)
    } else {
      ElMessage.success(`已提交批量生成任务，预计生成 ${data.data.totalCount} 篇文章`)
    }
    router.push('/admin/content/execution')
  } catch (err) {
    console.error(err)
    ElMessage.error('批量生成任务提交失败')
  } finally {
    batchSubmitting.value = false
  }
}

async function confirmReadinessWarnings(): Promise<Record<string, string[]> | null> {
  const report = await loadReadiness(true)
  if (!report) {
    return {}
  }
  const topicWarnings = selectedTopics.value.flatMap((topic) => {
    const scene = topic.questionSceneCode ? readinessSceneMap.value[topic.questionSceneCode] : null
    const warningCodes = (scene?.items || [])
      .filter((item) => item.requiresConfirmation && item.warningCode)
      .map((item) => item.warningCode!)
    if (!warningCodes.length) {
      return []
    }
    return [{
      topicId: topic.id,
      topic: topic.topic,
      messages: (scene?.items || [])
        .filter((item) => item.requiresConfirmation && item.warningCode)
        .map((item) => item.message),
      warningCodes,
    }]
  })
  if (!topicWarnings.length) {
    return {}
  }
  const detailHtml = topicWarnings
    .map((item) => `<li>${escapeHtml(item.topic)}：${item.messages.map(escapeHtml).join('；')}</li>`)
    .join('')
  try {
    await ElMessageBox.confirm(
      `<div class="generation-notice-box"><p>以下主题资料存在关键缺失，请确认是否继续生成：</p><ul>${detailHtml}</ul></div>`,
      '确认资料预检风险',
      {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '仍要生成',
        cancelButtonText: '返回修改',
        type: 'warning',
      },
    )
    return Object.fromEntries(topicWarnings.map((item) => [item.topicId, item.warningCodes]))
  } catch {
    return null
  }
}

async function confirmPlatformWarnings(): Promise<boolean> {
  const nonSuggestedDealSelections = selectedTopics.value.flatMap((topic) => (
    topic.questionSceneCode === 'deal'
      ? activePlatformOptions.value
          .filter((platform) => dealNonSuggestedPlatformSelected(topic, platform))
          .map((platform) => `${topic.topic}｜${platform.label}`)
      : []
  ))

  if (!nonSuggestedDealSelections.length) {
    return true
  }

  const nonSuggestedList = nonSuggestedDealSelections.map((item) => `<li>${escapeHtml(item)}</li>`).join('')

  try {
    await ElMessageBox.confirm(
      `<div class="generation-notice-box"><p>以下成交场景选择了非推荐平台，请确认发布目的：</p><ul>${nonSuggestedList}</ul></div>`,
      '确认生成风险',
      {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '仍要生成',
        cancelButtonText: '返回修改',
        type: 'warning',
      },
    )
    return true
  } catch {
    return false
  }
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

async function showGenerationNotices(totalCount: number, notices: BatchArticleGenerateNotice[]) {
  const detailHtml = notices.flatMap((notice) => notice.items?.map((item) => {
    const channel = [item.channelGroupCode, item.channelSubCode].filter(Boolean).join(' / ')
    const after = item.after?.map((count) => `${count.templateName || `模板 #${count.templateId}`}：${count.count} 篇`).join('，')
    const reason = item.reason ? `，原因：${item.reason}` : ''
    return `<li>${item.topic || '-'}｜${channel || '-'}${item.templateId ? `｜模板 #${item.templateId}` : ''}${reason}${after ? `<br/>实际分配：${after}` : ''}</li>`
  }) || []).join('')
  await ElMessageBox.alert(
    `<div class="generation-notice-box">
      <p>生成任务已创建，共 ${totalCount} 篇。注意：因模板池有变化，实际分配与预览可能不一致。</p>
      <ul>${detailHtml || '<li>后端已按最新可用模板完成分配。</li>'}</ul>
    </div>`,
    '生成任务已创建',
    {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '知道了',
      type: 'warning',
    },
  )
}

function goBack() {
  router.push('/admin/content/execution')
}

watch(
  () => readinessRequestKey.value,
  () => {
    void loadReadiness()
  },
)

onMounted(() => {
  loadProjectOptions()
  loadPublishPlatformOptions()
})
</script>

<style scoped>
.batch-article-page {
  min-height: 100vh;
  --section-radius: 10px;
  --border-soft: #eef0f4;
  --text-primary: #111827;
  --text-secondary: #6b7280;
  --text-tertiary: #9ca3af;
  background: #f5f7fb;
  color: #1f2937;
}

.page-toolbar {
  position: sticky;
  top: 0;
  z-index: 10;
  min-height: 64px;
  padding: 12px 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: #ffffff;
  border-bottom: 1px solid var(--border-soft);
}

.toolbar-left,
.toolbar-right,
.topic-actions,
.topic-card-actions,
.platform-group-actions,
.question-picker-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.back-button {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  color: var(--text-secondary);
}

.toolbar-title h1 {
  margin: 0;
  font-size: 18px;
  line-height: 1.3;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: 0;
}

.breadcrumb {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}

.breadcrumb-sep {
  color: var(--text-tertiary);
}

.breadcrumb-current {
  color: var(--text-primary);
  font-weight: 500;
}

.ready-state {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  background: #fff7ed;
  font-size: 13px;
  font-weight: 500;
  color: #c2410c;
}

.ready-state.is-ready {
  background: #ecfdf5;
  border-color: #a7f3d0;
  color: #047857;
}

.ready-state.is-warning {
  background: #fef2f2;
  border-color: #fecaca;
  color: #b91c1c;
}

.ready-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #16a34a;
  box-shadow: 0 0 0 3px rgba(22, 163, 74, 0.15);
}

.ready-dot.pending {
  background: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.18);
}

.page-body {
  width: min(1320px, calc(100% - 48px));
  margin: 24px auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 20px;
  align-items: start;
}

.main-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-alert {
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid #dbeafe;
  border-radius: var(--section-radius);
  background: #eff6ff;
  color: #1e40af;
  font-size: 13px;
}

.info-icon {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  background: #3b82f6;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.section-block,
.side-panel {
  border: 1px solid var(--border-soft);
  border-radius: var(--section-radius);
  background: #ffffff;
}

.section-block {
  overflow: hidden;
}

.section-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--border-soft);
  background: linear-gradient(180deg, #fbfcfe 0%, #ffffff 100%);
}

.section-index {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  margin-top: 1px;
  background: #409eff;
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 2px 6px -1px rgba(64, 158, 255, 0.45);
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--text-primary);
}

.section-desc {
  margin-top: 4px;
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--text-secondary);
}

.section-body {
  padding: 18px 22px 22px;
}

.project-select {
  width: min(100%, 480px);
}

.project-summary,
.topic-chip-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.manual-topic-input {
  width: min(100%, 420px);
}

.topic-section :deep(.el-empty) {
  padding: 16px 0 4px;
}

.matrix-body :deep(.el-empty) {
  padding: 28px 0;
}

.topic-matrix-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.topic-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  background: #ffffff;
}

.topic-card-head {
  padding: 14px 16px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid #eef2f7;
  background: #f9fafb;
}

.topic-card-head.is-collapsed {
  border-bottom: 0;
}

.topic-title-wrap {
  min-width: 0;
  flex: 1 1 auto;
}

.topic-title-row,
.platform-group-title-row {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.collapse-trigger {
  width: 24px;
  height: 24px;
  min-height: 24px;
  flex: 0 0 24px;
  border-radius: 6px;
  color: #64748b;
}

.collapse-trigger:hover {
  background: #eef2ff;
  color: #2563eb;
}

.topic-title {
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.topic-meta {
  margin-top: 6px;
  padding-left: 32px;
  font-size: 12px;
  color: var(--text-secondary);
}

.topic-summary-line,
.platform-group-summary-line {
  margin-top: 8px;
  padding-left: 32px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.topic-summary-line span,
.platform-group-summary-line span {
  height: 24px;
  padding: 0 9px;
  border: 1px solid #dbe4f0;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  background: #ffffff;
  color: #475569;
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
}

.platform-group-list {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.platform-group {
  border: 1px solid #e8edf5;
  border-radius: 8px;
  background: #fbfcff;
  overflow: hidden;
}

.platform-group-head {
  padding: 12px 14px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid #edf2f7;
  background: #f8fafc;
}

.platform-group-head.is-collapsed {
  border-bottom: 0;
}

.platform-group-title {
  min-width: 0;
  font-size: 14px;
  line-height: 1.4;
  font-weight: 600;
  color: var(--text-primary);
  overflow-wrap: anywhere;
}

.platform-group-desc {
  margin-top: 5px;
  padding-left: 32px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--text-secondary);
}

.platform-group-actions {
  justify-content: flex-end;
  flex: 0 0 auto;
}

.platform-grid {
  padding: 14px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.platform-cell {
  min-width: 0;
  padding: 12px;
  border: 1px solid #edf0f5;
  border-radius: 8px;
  background: #ffffff;
}

.platform-cell.is-disabled {
  background: #f9fafb;
  color: var(--text-tertiary);
}

.platform-label {
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}

.platform-icon {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  background: #ecf5ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.image-icon {
  object-fit: cover;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.platform-desc {
  min-height: 34px;
  margin-bottom: 10px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--text-secondary);
}

.platform-meta,
.platform-template-meta,
.platform-disabled-reason {
  margin: -4px 0 10px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--text-tertiary);
}

.platform-template-meta {
  margin-top: -2px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: #2563eb;
}

.platform-tags {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.template-count-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: #2563eb;
  font: inherit;
  cursor: pointer;
}

.template-count-link:hover {
  color: #1d4ed8;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.platform-disabled-reason {
  color: #b45309;
}

.platform-warning {
  margin: -4px 0 10px;
  font-size: 12px;
  line-height: 1.45;
  color: #b45309;
}

.platform-cell :deep(.el-input-number) {
  width: 100%;
}

.platform-cell-actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}

.allocation-dialog {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.allocation-summary {
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: #f8fafc;
}

.allocation-summary strong,
.allocation-summary span {
  display: block;
}

.allocation-summary span {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 12px;
}

.allocation-tip {
  padding: 10px 12px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  color: #1d4ed8;
  background: #eff6ff;
  font-size: 13px;
  line-height: 1.5;
}

.side-panel {
  position: sticky;
  top: 88px;
  padding: 20px 22px;
}

.summary-header {
  padding-bottom: 14px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--border-soft);
}

.summary-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--text-primary);
}

.summary-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.summary-list {
  padding: 0;
  margin: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
}

.summary-item {
  padding: 14px 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  border-bottom: 1px dashed #eef2f7;
}

.summary-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.summary-value {
  font-size: 14px;
  line-height: 1.4;
  font-weight: 600;
  color: var(--text-primary);
  word-break: break-word;
}

.summary-value.muted {
  color: var(--text-tertiary);
  font-weight: 500;
}

.summary-item.highlight {
  margin-top: 4px;
  padding: 14px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: linear-gradient(135deg, #eff6ff 0%, #f5f3ff 100%);
}

.summary-item.highlight.warning {
  border-color: #fed7aa;
  background: #fff7ed;
}

.summary-number {
  font-size: 22px;
  line-height: 1;
  font-weight: 700;
  color: #409eff;
}

.summary-item.highlight.warning .summary-number,
.summary-limit-warning {
  color: #c2410c;
}

.summary-unit {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.summary-limit-warning {
  font-size: 12px;
}

.readiness-card {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.readiness-card-head,
.readiness-score-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.readiness-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.readiness-subtitle {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--text-tertiary);
}

.readiness-score-row {
  margin-top: 12px;
  justify-content: flex-start;
}

.readiness-score {
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
  color: #2563eb;
}

.readiness-score-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.readiness-section {
  margin-top: 12px;
}

.readiness-section-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.readiness-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.readiness-scene {
  padding: 8px 0;
  border-top: 1px dashed #eef2f7;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.readiness-scene-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.readiness-scene-message,
.readiness-ok {
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary);
}

.readiness-ok {
  margin-top: 12px;
}

.readiness-ok.muted {
  color: var(--text-tertiary);
}

.question-picker-toolbar {
  margin-bottom: 12px;
}

@media (max-width: 1100px) {
  .page-body {
    width: calc(100% - 32px);
    grid-template-columns: 1fr;
  }

  .side-panel {
    position: static;
  }

  .platform-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .page-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .platform-grid {
    grid-template-columns: 1fr;
  }

  .topic-card-head {
    flex-direction: column;
  }

  .topic-card-actions,
  .platform-group-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .topic-summary-line,
  .platform-group-summary-line {
    padding-left: 0;
  }

  .topic-meta,
  .platform-group-desc {
    padding-left: 0;
  }

  .platform-group-head {
    flex-direction: column;
  }

  .platform-group-actions {
    justify-content: flex-start;
  }
}
</style>
