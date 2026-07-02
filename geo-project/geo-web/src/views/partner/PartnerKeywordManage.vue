<template>
  <div class="partner-page partner-keyword-page">
    <div class="partner-page-header">
      <div>
        <div class="partner-page-kicker">核心问题</div>
        <h1 class="partner-page-title">拓词管理</h1>
        <div class="partner-page-subtitle">维护项目可用于后续诊断与交付准备的核心问题，提交后由合伙人负责人核对并向总部发起工单。</div>
      </div>
      <div class="partner-page-actions">
        <el-button @click="loadProjects">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="partner-surface">
      <div class="partner-toolbar">
        <div>
          <div class="partner-toolbar-title">选择项目</div>
          <div class="partner-toolbar-subtitle">仅展示可维护的待启动或暂停项目。</div>
        </div>
        <div class="partner-toolbar-controls">
          <el-input v-model="keyword" class="partner-search" clearable placeholder="搜索客户 / 品牌 / 项目" @keyup.enter="loadProjects" />
          <el-button type="primary" @click="loadProjects">查询</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="partner-table-card">
      <DataState :loading="loadingProjects" :empty="!loadingProjects && projects.length === 0" empty-text="暂无可维护项目">
        <el-table :data="projects" border table-layout="fixed" highlight-current-row @row-click="selectProject">
          <el-table-column label="项目" min-width="260">
            <template #default="scope">
              <div class="partner-entity-cell">
                <div class="partner-entity-avatar is-blue">{{ entityInitial(scope.row.projectName) }}</div>
                <div class="min-w-0">
                  <div class="partner-entity-main">{{ scope.row.projectName }}</div>
                  <div class="partner-entity-sub">{{ scope.row.companyName || '未归属客户' }} · {{ scope.row.brandName || '未绑定品牌' }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="scope">
              <span class="partner-status-tag">{{ projectStatusLabel(scope.row.status) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="核心问题额度" width="150">
            <template #default="scope">{{ projectQuotaA(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click.stop="selectProject(scope.row)">维护</el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <template v-if="selectedProject">
      <div class="partner-keyword-summary">
        <div class="partner-keyword-summary__main">
          <span class="partner-keyword-summary__badge">核</span>
          <div>
            <strong>{{ selectedProject.projectName }}</strong>
            <p>{{ selectedProject.companyName || '-' }} · {{ selectedProject.brandName || '-' }}</p>
          </div>
        </div>
        <div class="partner-keyword-metrics">
          <div>
            <span>核心问题额度</span>
            <strong>{{ quota?.quotaA ?? projectQuotaA(selectedProject) }}</strong>
          </div>
          <div>
            <span>已维护</span>
            <strong>{{ quota?.workorderCountA ?? reviewQuestions.length }}</strong>
          </div>
          <div>
            <span>剩余额度</span>
            <strong>{{ quota?.remainingA ?? 0 }}</strong>
          </div>
        </div>
      </div>

      <el-card shadow="never" class="partner-table-card">
        <div class="partner-section-header">
          <div>
            <div class="partner-section-title">录入核心问题</div>
            <div class="partner-section-subtitle">只录入客户明确关注、后续需要重点诊断和交付跟进的问题。</div>
          </div>
          <el-button type="primary" :disabled="!canAddQuestion" @click="addQuestion">新增问题</el-button>
        </div>

        <div class="partner-question-editor">
          <div v-if="!draftQuestions.length" class="partner-question-empty">暂无待提交问题，可点击“新增问题”开始录入。</div>
          <div v-for="(item, index) in draftQuestions" :key="item.key" class="partner-question-row">
            <span class="partner-question-index">{{ index + 1 }}</span>
            <el-input v-model="item.questionText" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="请输入核心问题，例如：本地服务商怎么选、方案预算如何评估" />
            <el-button link type="danger" @click="removeDraftQuestion(index)">删除</el-button>
          </div>
        </div>

        <div class="partner-dialog-footer">
          <span class="partner-keyword-hint">本次提交 {{ validDraftQuestions.length }} 条，剩余额度 {{ quota?.remainingA ?? 0 }} 条。</span>
          <el-button type="primary" :loading="saving" :disabled="!canSubmitQuestions" @click="submitQuestions">提交给合伙人查看</el-button>
        </div>
      </el-card>

      <el-card shadow="never" class="partner-table-card">
        <div class="partner-section-header">
          <div>
            <div class="partner-section-title">已维护核心问题</div>
            <div class="partner-section-subtitle">这里只展示合伙人可见的核心问题，不展示内部 A/B/C 分类。</div>
          </div>
          <el-button @click="reloadSelectedProject">刷新</el-button>
        </div>
        <DataState :loading="loadingReview" :empty="!loadingReview && reviewQuestions.length === 0" empty-text="暂无核心问题">
          <el-table :data="reviewQuestions" border table-layout="fixed">
            <el-table-column label="核心问题" min-width="420">
              <template #default="scope">
                <div class="partner-question-text">{{ scope.row.questionText }}</div>
                <div class="partner-cell-sub">{{ scope.row.relatedNeedText || scope.row.designReason || '待合伙人负责人核对' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="场景" width="140">
              <template #default="scope">{{ sceneLabel(scope.row.sceneCode) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="scope">
                <span class="partner-status-tag is-success">{{ questionStatusLabel(scope.row.status) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </DataState>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createManualGeoQuestions,
  createOrGetProjectWorkorder,
  getGeoProjectWorkorders,
  getGeoQuestions,
  type ManualQuestionInput,
  type QuestionVO,
  type QuotaSnapshot,
  type WorkorderVO,
} from '@/api/geoQuestion'
import { getProjectList } from '@/api/project'
import DataState from '@/components/ui/DataState.vue'
import type { Project } from '@/types'

defineOptions({ name: 'PartnerKeywordManage' })

type DraftQuestion = { key: number; questionText: string }

const keyword = ref('')
const loadingProjects = ref(false)
const loadingReview = ref(false)
const saving = ref(false)
const projects = ref<Project[]>([])
const selectedProject = ref<Project | null>(null)
const workorder = ref<WorkorderVO | null>(null)
const quota = ref<QuotaSnapshot | null>(null)
const reviewQuestions = ref<QuestionVO[]>([])
const draftQuestions = ref<DraftQuestion[]>([])
const draftKey = ref(1)

const validDraftQuestions = computed(() => draftQuestions.value.filter((item) => item.questionText.trim()))
const canAddQuestion = computed(() => !!selectedProject.value && (quota.value?.remainingA ?? 0) > validDraftQuestions.value.length)
const canSubmitQuestions = computed(() => validDraftQuestions.value.length > 0 && validDraftQuestions.value.length <= (quota.value?.remainingA ?? 0))

function entityInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '项'
}

function projectStatusLabel(value?: string | null) {
  const mapping: Record<string, string> = {
    pending_start: '待启动',
    paused: '暂停',
    active: '进行中',
    setup_ready: '配置完成',
  }
  return value ? mapping[value] || value : '-'
}

function questionStatusLabel(value?: string | null) {
  const mapping: Record<string, string> = {
    active: '已提交',
    draft: '草稿',
    deleted: '已删除',
  }
  return value ? mapping[value] || '已提交' : '已提交'
}

function sceneLabel(value?: string | null) {
  const mapping: Record<string, string> = {
    brand: '品牌',
    decision: '决策',
    deal: '成交',
    compare: '对比',
    qa: '问答',
    function: '功能',
  }
  return value ? mapping[value] || value : '-'
}

function projectQuotaA(project: Project) {
  return Number((project as any).quotaA ?? (project as any).questionQuotaA ?? 0)
}

async function loadProjects() {
  loadingProjects.value = true
  try {
    const statuses = ['pending_start', 'paused']
    const responses = await Promise.all(statuses.map((status) => getProjectList({
      current: 1,
      size: 500,
      keyword: keyword.value || undefined,
      status,
    })))
    const projectMap = new Map<number, Project>()
    responses.forEach(({ data }) => {
      ;(data.data.records || []).forEach((project) => projectMap.set(project.id, project))
    })
    projects.value = Array.from(projectMap.values())
  } catch {
    projects.value = []
    ElMessage.error('加载项目失败')
  } finally {
    loadingProjects.value = false
  }
}

async function selectProject(project: Project) {
  selectedProject.value = project
  draftQuestions.value = []
  loadingReview.value = true
  try {
    const { data } = await createOrGetProjectWorkorder(project.id)
    workorder.value = data.data
    quota.value = data.data.quota
    await loadReviewQuestions()
  } catch {
    ElMessage.error('加载核心问题失败')
  } finally {
    loadingReview.value = false
  }
}

async function loadReviewQuestions() {
  if (!workorder.value) return
  const [{ data: questionRes }] = await Promise.all([
    getGeoQuestions(workorder.value.id, { tier: 'A', current: 1, size: 500 }),
    selectedProject.value ? getGeoProjectWorkorders(selectedProject.value.id) : Promise.resolve(null),
  ])
  reviewQuestions.value = questionRes.data.records || []
}

async function reloadSelectedProject() {
  if (!selectedProject.value) return
  await selectProject(selectedProject.value)
}

function addQuestion() {
  if (!canAddQuestion.value) {
    ElMessage.warning('核心问题剩余额度不足')
    return
  }
  draftQuestions.value.push({ key: draftKey.value++, questionText: '' })
}

function removeDraftQuestion(index: number) {
  draftQuestions.value.splice(index, 1)
}

async function submitQuestions() {
  if (!workorder.value || !canSubmitQuestions.value) return
  const duplicated = findDuplicated(validDraftQuestions.value.map((item) => item.questionText.trim()))
  if (duplicated) {
    ElMessage.warning(`存在重复核心问题：${duplicated}`)
    return
  }
  saving.value = true
  try {
    const items: ManualQuestionInput[] = validDraftQuestions.value.map((item) => ({
      questionText: item.questionText.trim(),
      tier: 'A',
      sceneCode: 'brand',
      priority: 'medium',
      monitorFrequency: 'weekly',
      designReason: '合伙人员工手动录入的核心问题',
    }))
    await createManualGeoQuestions(workorder.value.id, {
      items,
      manualReason: '合伙人员工提交给合伙人负责人查看',
    })
    draftQuestions.value = []
    ElMessage.success('核心问题已提交')
    await reloadSelectedProject()
  } catch {
    ElMessage.error('提交核心问题失败')
  } finally {
    saving.value = false
  }
}

function findDuplicated(values: string[]) {
  const seen = new Set<string>()
  for (const value of values) {
    const key = value.replace(/\s+/g, ' ').toLowerCase()
    if (seen.has(key)) return value
    seen.add(key)
  }
  return ''
}

onMounted(loadProjects)
</script>

<style scoped>
.partner-keyword-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 22px 24px;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  background: linear-gradient(135deg, #f8fbff 0%, #eef7ff 52%, #ecfdf5 100%);
  box-shadow: 0 16px 38px rgba(15, 23, 42, 0.06);
}

.partner-keyword-summary__main {
  display: flex;
  align-items: center;
  gap: 14px;
}

.partner-keyword-summary__main strong {
  display: block;
  color: #0f172a;
  font-size: 20px;
  font-weight: 800;
}

.partner-keyword-summary__main p {
  margin: 6px 0 0;
  color: #64748b;
}

.partner-keyword-summary__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #2563eb;
  color: #fff;
  font-weight: 800;
}

.partner-keyword-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr));
  gap: 12px;
  min-width: 420px;
}

.partner-keyword-metrics div {
  padding: 14px 16px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.84);
}

.partner-keyword-metrics span {
  display: block;
  color: #64748b;
  font-size: 13px;
}

.partner-keyword-metrics strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 24px;
  line-height: 1;
}

.partner-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.partner-section-title {
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.partner-section-subtitle {
  margin-top: 5px;
  color: #64748b;
  font-size: 13px;
}

.partner-question-editor {
  display: grid;
  gap: 12px;
}

.partner-question-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 48px;
  align-items: start;
  gap: 12px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}

.partner-question-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 10px;
  background: #eff6ff;
  color: #2563eb;
  font-weight: 800;
}

.partner-question-empty {
  padding: 26px;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  color: #64748b;
  text-align: center;
}

.partner-keyword-hint,
.partner-question-text {
  color: #0f172a;
  font-weight: 700;
}

@media (max-width: 1100px) {
  .partner-keyword-summary {
    align-items: stretch;
    flex-direction: column;
  }

  .partner-keyword-metrics {
    min-width: 0;
  }
}
</style>
