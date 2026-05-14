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
          <span>由运营手动发起批量生成，生成完成后进入待审核文章池，不自动分发。</span>
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
          <div class="section-body matrix-body">
            <div v-if="selectedTopics.length" class="topic-matrix-list">
              <article v-for="topic in selectedTopics" :key="topic.id" class="topic-card">
                <div class="topic-card-head">
                  <div class="topic-title-wrap">
                    <div class="topic-title">{{ topic.topic }}</div>
                    <div class="topic-meta">
                      {{ topic.source === 'keyword_group' ? `拓词组：${topic.keywordGroupName || '-'}` : '手动输入' }}
                    </div>
                  </div>
                  <div class="topic-card-actions">
                    <el-button link type="primary" @click="setTopicPreset(topic, 1)">每平台 1 篇</el-button>
                    <el-button link @click="clearTopicCounts(topic)">清空</el-button>
                    <el-button link type="danger" :icon="Delete" @click="removeTopic(topic.id)">移除</el-button>
                  </div>
                </div>
                <div class="platform-grid">
                  <div v-for="platform in platformOptions" :key="platform.value" class="platform-cell">
                    <div class="platform-label">
                      <span class="platform-icon">{{ platform.icon }}</span>
                      <span>{{ platform.label }}</span>
                    </div>
                    <el-input-number
                      v-model="topic.platformCounts[platform.value]"
                      :min="0"
                      :max="MAX_BATCH_ARTICLE_COUNT"
                      controls-position="right"
                    />
                  </div>
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
      </aside>
    </main>

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
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Back, Check, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { TableInstance } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import type { KeywordGroup, KeywordGroupQuestion, Project } from '@/types'
import { createBatchContentArticles } from '@/api/content'
import { getKeywordGroupQuestions, getProjectDetail, getProjectList } from '@/api/project'

interface ContentStyleOption {
  value: string
  label: string
  desc: string
  icon: string
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
  keywordGroupId?: number
  keywordGroupName?: string
  platformCounts: Record<string, number>
}

const PLATFORM_OPTIONS: ContentStyleOption[] = [
  { value: 'toutiao', label: '今日头条', desc: '泛资讯阅读，结论前置', icon: '头' },
  { value: 'wechat', label: '公众号', desc: '完整长文，结构稳', icon: '公' },
  { value: 'zhihu', label: '知乎', desc: '问题回答，判断清晰', icon: '知' },
  { value: 'douyin_image_text', label: '抖音图文', desc: '图文卡片式阅读', icon: '抖' },
  { value: 'linkedin', label: '领英', desc: '商业观察，专业克制', icon: '领' },
  { value: 'industry_site', label: '行业资讯站', desc: '客观中立，可引用', icon: '讯' },
  { value: 'authority_media', label: '权威媒体', desc: '正式审慎，事实边界', icon: '权' },
  { value: 'forum', label: '论坛', desc: '垂直社区讨论感', icon: '坛' },
]
const MAX_BATCH_ARTICLE_COUNT = 30

const router = useRouter()
const projectLoading = ref(false)
const batchSubmitting = ref(false)
const projectOptions = ref<Project[]>([])
const batchQuestionPickerVisible = ref(false)
const batchQuestionPickerLoading = ref(false)
const batchQuestionRows = ref<ArticleQuestionOption[]>([])
const selectedQuestionRows = ref<ArticleQuestionOption[]>([])
const questionTableRef = ref<TableInstance>()
const selectedTopics = ref<SelectedTopic[]>([])
const manualTopicInput = ref('')

const batchForm = reactive({
  projectId: undefined as number | undefined,
})
const batchQuestionFilters = reactive({
  tier: 'all',
  keyword: '',
  scene: 'all',
})

const platformOptions = computed(() => PLATFORM_OPTIONS)
const projectCascadeProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: false,
}
const projectCascadeOptions = computed(() => buildProjectCascadeOptions(projectOptions.value))
const selectedBatchProject = computed(() => projectOptions.value.find((project) => project.id === batchForm.projectId) || null)
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
  total + platformOptions.value.reduce((sum, platform) => sum + Number(topic.platformCounts[platform.value] || 0), 0)
), 0))
const isBatchCountExceeded = computed(() => batchGeneratedTotal.value > MAX_BATCH_ARTICLE_COUNT)
const selectedPlatformLabels = computed(() => platformOptions.value
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

function createPlatformCounts() {
  return Object.fromEntries(platformOptions.value.map((platform) => [platform.value, 0])) as Record<string, number>
}

function handleBatchProjectChange() {
  selectedTopics.value = []
  selectedQuestionRows.value = []
  batchQuestionRows.value = []
  manualTopicInput.value = ''
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
      platformCounts: createPlatformCounts(),
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
  return true
}

function removeTopic(topicId: string) {
  selectedTopics.value = selectedTopics.value.filter((topic) => topic.id !== topicId)
}

function setTopicPreset(topic: SelectedTopic, count: number) {
  for (const platform of platformOptions.value) {
    topic.platformCounts[platform.value] = count
  }
}

function clearTopicCounts(topic: SelectedTopic) {
  for (const platform of platformOptions.value) {
    topic.platformCounts[platform.value] = 0
  }
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
      keywordGroupId: question.groupId,
      keywordGroupName: question.groupName,
      platformCounts: createPlatformCounts(),
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
  const payloadTopics = selectedTopics.value.map((topic) => ({
    topic: topic.topic,
    topicAsQuestion: topic.topicAsQuestion,
    keywordGroupId: topic.keywordGroupId,
    keywordGroupName: topic.keywordGroupName,
    platforms: platformOptions.value.map((platform) => ({
      contentStyle: platform.value,
      count: Number(topic.platformCounts[platform.value] || 0),
    })),
  }))
  batchSubmitting.value = true
  try {
    const { data } = await createBatchContentArticles({
      projectId: batchForm.projectId!,
      topicSource: selectedTopics.value.some((topic) => topic.source === 'keyword_group') ? 'keyword_group' : 'manual',
      topics: payloadTopics,
    })
    ElMessage.success(`已提交批量生成任务，预计生成 ${data.data.totalCount} 篇文章`)
    router.push('/admin/content/execution')
  } catch (err) {
    console.error(err)
    ElMessage.error('批量生成任务提交失败')
  } finally {
    batchSubmitting.value = false
  }
}

function goBack() {
  router.push('/admin/content/execution')
}

onMounted(() => {
  loadProjectOptions()
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

.topic-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.45;
}

.topic-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.platform-grid {
  padding: 16px;
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

.platform-label {
  margin-bottom: 8px;
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

.platform-cell :deep(.el-input-number) {
  width: 100%;
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
}
</style>
