<template>
  <div class="partner-page partner-keyword-page">
    <div class="partner-page-header">
      <div>
        <div class="partner-page-kicker">核心问题</div>
        <h1 class="partner-page-title">拓词管理</h1>
        <div class="partner-page-subtitle">维护客户明确关注的问题，作为后续诊断和交付准备的问题资产。</div>
      </div>
      <div class="partner-page-actions">
        <el-button :loading="loadingProjects" @click="loadProjects">刷新项目</el-button>
      </div>
    </div>

    <section class="keyword-context-bar" :class="{ empty: !selectedProject }">
      <div class="context-main">
        <span class="context-avatar">{{ selectedProject ? entityInitial(selectedProject.projectName) : '项' }}</span>
        <div>
          <strong>{{ selectedProject?.projectName || '请先选择项目' }}</strong>
          <p>
            {{
              selectedProject
                ? `${selectedProject.companyName || '未归属客户'} · ${selectedProject.brandName || '未绑定品牌'} · ${projectStatusLabel(selectedProject.status)}`
                : '选定项目后会读取核心问题额度、当前任务和已维护问题。'
            }}
          </p>
        </div>
      </div>
      <div class="context-metrics">
        <span><b>{{ workorder ? `WO-${workorder.id}` : '-' }}</b><em>当前任务</em></span>
        <span><b>{{ coreQuotaLimit }}</b><em>核心问题额度</em></span>
        <span><b>{{ maintainedCount }}</b><em>已维护</em></span>
        <span><b>{{ partnerReviewStatusLabel }}</b><em>复核状态</em></span>
      </div>
    </section>

    <section class="keyword-workspace">
      <aside class="project-panel">
        <el-card shadow="never" class="partner-surface">
          <div class="panel-head">
            <div>
              <strong>选择项目</strong>
              <span>仅展示可维护的待启动或暂停项目。</span>
            </div>
          </div>
          <div class="project-search">
            <el-input
              v-model="keyword"
              clearable
              placeholder="搜索客户 / 品牌 / 项目"
              @keyup.enter="loadProjects"
            />
            <el-button type="primary" :loading="loadingProjects" @click="loadProjects">查询</el-button>
          </div>
          <DataState :loading="loadingProjects" :empty="!loadingProjects && projects.length === 0" empty-text="暂无可维护项目">
            <div class="project-list">
              <button
                v-for="project in projects"
                :key="project.id"
                class="project-card"
                :class="{ selected: selectedProject?.id === project.id }"
                @click="selectProject(project)"
              >
                <span class="project-avatar">{{ entityInitial(project.projectName) }}</span>
                <span class="project-card-main">
                  <b>{{ project.projectName }}</b>
                  <em>{{ project.companyName || '未归属客户' }} · {{ project.brandName || '未绑定品牌' }}</em>
                </span>
                <span class="project-quota">额度 {{ projectCoreQuestionLimit(project) }}</span>
              </button>
            </div>
          </DataState>
        </el-card>
      </aside>

      <main class="keyword-main">
        <el-card v-if="selectedProject" shadow="never" class="partner-surface selected-project-card">
          <div class="selected-info">
            <div><label>客户</label><strong>{{ selectedProject.companyName || '-' }}</strong></div>
            <div><label>品牌</label><strong>{{ selectedProject.brandName || '-' }}</strong></div>
            <div><label>项目</label><strong>{{ selectedProject.projectName }}</strong></div>
            <div><label>项目状态</label><strong>{{ projectStatusLabel(selectedProject.status) }}</strong></div>
            <div><label>当前任务</label><strong>{{ workorder ? `WO-${workorder.id}` : '待创建' }}</strong></div>
            <div><label>维护进度</label><strong>{{ maintainedCount }} / {{ coreQuotaLimit }}</strong></div>
          </div>
        </el-card>

        <el-card v-if="selectedProject" shadow="never" class="partner-surface customer-profile-card" v-loading="loadingProfile">
          <div class="section-head">
            <div>
              <h2>客户基本信息</h2>
              <p>可在生成前修正资料，保存后作为当前项目拓词任务的生成依据。</p>
            </div>
            <div class="profile-actions">
              <el-button :disabled="!selectedProject" @click="loadProjectProfile">刷新资料</el-button>
              <el-button type="primary" :loading="savingProfile" :disabled="!canEditWorkorder" @click="saveProfileDraft">保存资料</el-button>
            </div>
          </div>
          <div class="profile-grid editable">
            <label><span>公司全称</span><el-input v-model="profileForm.companyName" :disabled="!canEditWorkorder" /></label>
            <label><span>品牌名</span><el-input v-model="profileForm.brandName" :disabled="!canEditWorkorder" /></label>
            <label><span>品牌关系</span><el-input v-model="profileForm.brandRelation" :disabled="!canEditWorkorder" placeholder="自营 / 授权 / 加盟等" /></label>
            <label><span>所属行业</span><el-input v-model="profileForm.industry" :disabled="!canEditWorkorder" /></label>
            <label><span>目标区域</span><el-input v-model="profileForm.targetRegion" :disabled="!canEditWorkorder" /></label>
            <label><span>核心业务</span><el-input v-model="profileCoreBusinessText" :disabled="!canEditWorkorder" placeholder="多个业务用顿号或逗号分隔" /></label>
            <label class="profile-wide"><span>客户画像</span><el-input v-model="profileForm.targetCustomer" :disabled="!canEditWorkorder" type="textarea" :rows="3" /></label>
            <label class="profile-wide"><span>核心优势</span><el-input v-model="profileForm.coreAdvantage" :disabled="!canEditWorkorder" type="textarea" :rows="3" /></label>
            <label class="profile-wide"><span>核心需求</span><el-input v-model="profileCoreNeedsText" :disabled="!canEditWorkorder" type="textarea" :rows="3" placeholder="每行一条，或用分号分隔" /></label>
          </div>
        </el-card>

        <el-card v-if="selectedProject" shadow="never" class="partner-surface project-supplement-card">
          <div class="section-head">
            <div>
              <h2>项目资料补充</h2>
              <p>用于生成和核对核心问题的项目写作依据。</p>
            </div>
          </div>
          <div class="profile-grid">
            <div v-for="item in projectSupplementItems" :key="item.label" :class="{ 'profile-wide': item.wide }">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </el-card>

        <el-card v-if="selectedProject" shadow="never" class="partner-surface quota-card">
          <div class="quota-head">
            <div>
              <span class="quota-badge">核</span>
              <div>
                <strong>核心问题额度</strong>
                <p>合伙人侧只展示核心问题总量，不展示总部内部分类。</p>
              </div>
            </div>
            <el-progress
              class="quota-progress"
              :percentage="quotaPercentage"
              :stroke-width="9"
              :show-text="false"
              :status="quotaProgressStatus"
            />
          </div>
          <div class="quota-grid">
            <div>
              <span>项目分配额度</span>
              <strong>{{ coreQuotaLimit }}</strong>
            </div>
            <div>
              <span>已维护问题</span>
              <strong>{{ maintainedCount }}</strong>
            </div>
            <div>
              <span>剩余额度</span>
              <strong>{{ remainingCoreCount }}</strong>
            </div>
            <div>
              <span>本次待提交</span>
              <strong>{{ validManualRows.length }}</strong>
            </div>
          </div>
        </el-card>

        <el-card v-if="selectedProject" shadow="never" class="partner-surface entry-card">
          <div class="section-head">
            <div>
              <h2>录入核心问题</h2>
              <p>可手动补充现场确认的问题，也可按客户资料自动生成一批标准核心问题。</p>
            </div>
            <div class="entry-actions">
              <el-button :disabled="!canOpenManualEntry" @click="openManualEntry">
                <el-icon><Plus /></el-icon>
                手动录入
              </el-button>
              <el-button type="primary" :loading="generating" :disabled="!canGenerateCoreQuestions" @click="generateCoreQuestions">
                <el-icon><MagicStick /></el-icon>
                自动生成 {{ generationCount }} 条
              </el-button>
            </div>
          </div>
          <div class="generation-layout">
            <div class="generation-copy">
              <strong>{{ generationTitle }}</strong>
              <span>{{ generationTip }}</span>
            </div>
            <div class="generation-metrics">
              <span><b>{{ generationCount }}</b><em>本次上限</em></span>
              <span><b>{{ remainingCoreCount }}</b><em>剩余额度</em></span>
              <span><b>{{ runningReservedCount }}</b><em>生成中</em></span>
            </div>
          </div>
          <div class="entry-note" :class="{ warning: !canGenerateCoreQuestions && !generating }">
            <el-icon><InfoFilled /></el-icon>
            <span>{{ generationStatusTip }}</span>
          </div>
        </el-card>

        <el-card v-if="selectedProject" shadow="never" class="partner-surface">
          <div class="section-head">
            <div>
              <h2>待确认核心问题</h2>
              <p>生成或手动录入后先在这里审核，确认无误后再进入项目拓词组。</p>
            </div>
            <div class="review-actions">
              <el-button :loading="loadingQuestions" @click="reloadSelectedProject">刷新</el-button>
              <el-button v-if="duplicateQuestionTexts.length && canEditWorkorder" type="warning" plain @click="openDuplicateResolveDialog">
                处理重复 {{ duplicateQuestionTexts.length }} 项
              </el-button>
              <el-button
                v-if="canEditWorkorder"
                type="danger"
                plain
                :loading="batchDeleting"
                :disabled="selectedReviewQuestions.length === 0"
                @click="removeSelectedQuestions"
              >
                批量删除 {{ selectedReviewQuestions.length || '' }}
              </el-button>
              <el-button
                v-if="isPartnerStaff"
                type="primary"
                :loading="committing || submittingReview"
                :disabled="!canSubmitOwnerReview"
                @click="submitOwnerReview"
              >
                确认入库并提交负责人复核
              </el-button>
              <el-button
                v-if="isPartnerOwner && canShowOwnerReviewActions"
                type="warning"
                plain
                :loading="returningReview"
                @click="returnOwnerReview"
              >
                退回修改
              </el-button>
            </div>
          </div>
          <div class="review-note" :class="{ warning: !canCommitCoreQuestions }">
            <el-icon><InfoFilled /></el-icon>
            <span>{{ commitStatusTip }}</span>
          </div>
          <DataState :loading="loadingQuestions" :empty="!loadingQuestions && reviewQuestions.length === 0" empty-text="暂无核心问题">
            <el-table
              :data="pagedReviewQuestions"
              border
              table-layout="fixed"
              class="question-table"
              row-key="id"
              @selection-change="handleQuestionSelectionChange"
            >
              <el-table-column type="selection" width="48" align="center" :selectable="questionSelectable" />
              <el-table-column label="序号" width="72" align="center">
                <template #default="scope">{{ (questionPage - 1) * questionPageSize + scope.$index + 1 }}</template>
              </el-table-column>
              <el-table-column label="核心问题" min-width="420">
                <template #default="scope">
                  <div class="question-text">{{ scope.row.questionText }}</div>
                </template>
              </el-table-column>
              <el-table-column label="场景" width="140">
                <template #default="scope">{{ sceneLabel(scope.row.sceneCode) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="scope">
                  <span class="question-status">{{ questionStatusLabel(scope.row.status) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" align="center">
                <template #default="scope">
                  <div v-if="canEditWorkorder" class="question-actions">
                    <el-button link type="primary" @click="startEditQuestion(scope.row)">编辑</el-button>
                    <el-button link type="danger" @click="removeQuestion(scope.row)">删除</el-button>
                  </div>
                  <span v-else class="locked-text">已锁定</span>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="reviewQuestions.length > questionPageSize" class="question-pagination">
              <span>共 {{ reviewQuestions.length }} 条核心问题</span>
              <el-pagination
                v-model:current-page="questionPage"
                background
                layout="prev, pager, next"
                :page-size="questionPageSize"
                :total="reviewQuestions.length"
              />
            </div>
          </DataState>
        </el-card>

        <el-card v-if="selectedProject" shadow="never" class="partner-surface workorder-card">
          <div class="section-head">
            <div>
              <h2>项目任务记录</h2>
              <p>展示当前项目已产生的问题任务和维护数量。</p>
            </div>
            <el-button @click="loadWorkorderList">刷新</el-button>
          </div>
          <div class="workorder-list">
            <div class="workorder-row head">
              <div>任务</div>
              <div>状态</div>
              <div>核心问题数</div>
              <div>最近更新</div>
            </div>
            <div v-if="!workorderList.length" class="workorder-empty">暂无任务记录</div>
            <div v-for="item in workorderList" :key="item.id" class="workorder-row">
              <div>
                <strong>{{ item.workorderNo || `WO-${item.id}` }}</strong>
                <small>{{ item.packageName || '-' }}</small>
              </div>
              <div class="workorder-status-cell">
                <span class="question-status">{{ geoStatusLabel(item.status) }}</span>
                <small>{{ partnerReviewStatusText(item.partnerReviewStatus) }}</small>
              </div>
              <div>{{ item.countTotal || 0 }}</div>
              <div>{{ formatTime(item.updatedAt || item.createdAt) }}</div>
            </div>
          </div>
        </el-card>

        <el-card v-if="!selectedProject" shadow="never" class="partner-surface blank-state">
          <strong>请选择需要维护的项目</strong>
          <span>选择后可录入核心问题，并查看当前项目的问题维护进度。</span>
        </el-card>
      </main>
    </section>

    <el-dialog v-model="editVisible" title="编辑核心问题" width="720px" class="partner-question-dialog">
      <el-form label-position="top" class="question-edit-form">
        <el-form-item label="核心问题" required>
          <el-input v-model="editForm.questionText" type="textarea" :rows="3" maxlength="80" show-word-limit />
        </el-form-item>
        <div class="question-edit-grid">
          <el-form-item label="场景">
            <el-select v-model="editForm.sceneCode" style="width: 100%">
              <el-option v-for="scene in sceneOptions" :key="scene.code" :label="scene.label" :value="scene.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级">
            <el-select v-model="editForm.priority" style="width: 100%">
              <el-option label="高" value="high" />
              <el-option label="中" value="medium" />
              <el-option label="低" value="low" />
            </el-select>
          </el-form-item>
          <el-form-item label="监测频率">
            <el-select v-model="editForm.monitorFrequency" style="width: 100%">
              <el-option label="每日" value="daily" />
              <el-option label="每周" value="weekly" />
              <el-option label="每月" value="monthly" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingQuestion" @click="saveEditQuestion">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="duplicateResolveVisible" title="处理重复问题" width="920px" class="partner-duplicate-dialog">
      <div class="duplicate-note">以下问题文本重复，请修改为唯一内容后再入项目拓词组。</div>
      <div class="duplicate-list">
        <div v-for="group in duplicateQuestionGroups" :key="group.key" class="duplicate-group">
          <div class="duplicate-title">
            <strong>{{ group.text }}</strong>
            <span>{{ group.items.length }} 条重复</span>
          </div>
          <div v-for="item in group.items" :key="item.id" class="duplicate-edit-row">
            <span>{{ item.tier }}-{{ item.id }}</span>
            <el-input v-model="duplicateEditForms[item.id]" />
            <em>{{ sceneLabel(item.sceneCode) }}</em>
          </div>
        </div>
      </div>
      <div v-if="duplicateResolveError" class="manual-tip error">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ duplicateResolveError }}</span>
      </div>
      <template #footer>
        <el-button @click="duplicateResolveVisible = false">取消</el-button>
        <el-button type="primary" :loading="duplicateSaving" @click="saveDuplicateEdits">保存修改并重新检查</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="manualVisible" title="手动录入核心问题" width="920px" class="partner-manual-dialog">
      <div class="manual-form-grid">
        <label>
          <span>录入说明</span>
          <el-input v-model="manualReason" placeholder="例如：补充客户现场访谈问题" />
        </label>
        <label>
          <span>默认场景</span>
          <el-select v-model="manualDefaultScene" style="width: 100%">
            <el-option v-for="scene in sceneOptions" :key="scene.code" :label="scene.label" :value="scene.code" />
          </el-select>
        </label>
      </div>

      <div class="manual-paste">
        <div class="manual-label-row">
          <span>批量粘贴</span>
          <el-button @click="appendManualPaste">拆分为问题</el-button>
        </div>
        <el-input
          v-model="manualPasteText"
          type="textarea"
          :rows="3"
          placeholder="每行一个核心问题，点击“拆分为问题”后加入下方表格"
        />
      </div>

      <div class="manual-toolbar">
        <div>
          <strong>本次录入 {{ validManualRows.length }} 条</strong>
          <span>剩余额度 {{ remainingCoreCount }} 条</span>
        </div>
        <el-button @click="addManualRow">
          <el-icon><Plus /></el-icon>
          增加一行
        </el-button>
      </div>

      <div class="manual-table">
        <div class="manual-row head">
          <div>核心问题</div>
          <div>场景</div>
          <div>优先级</div>
          <div>频率</div>
          <div>对应需求</div>
          <div>操作</div>
        </div>
        <div v-for="(row, index) in manualRows" :key="row.key" class="manual-row">
          <el-input v-model="row.questionText" maxlength="500" show-word-limit placeholder="请输入核心问题" />
          <el-select v-model="row.sceneCode">
            <el-option v-for="scene in sceneOptions" :key="scene.code" :label="scene.label" :value="scene.code" />
          </el-select>
          <el-select v-model="row.priority">
            <el-option label="高" value="high" />
            <el-option label="中" value="medium" />
            <el-option label="低" value="low" />
          </el-select>
          <el-select v-model="row.monitorFrequency">
            <el-option label="每日" value="daily" />
            <el-option label="每周" value="weekly" />
            <el-option label="每月" value="monthly" />
          </el-select>
          <el-input v-model="row.relatedNeedText" placeholder="可选" />
          <el-button link type="danger" :disabled="manualRows.length <= 1" @click="removeManualRow(index)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>

      <div class="manual-tip" :class="{ error: Boolean(manualValidationMessage) }">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ manualValidationMessage || '提交后将创建手动批次，并回到当前项目的核心问题清单。' }}</span>
      </div>

      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="Boolean(manualValidationMessage)" @click="submitManualQuestions">
          提交核心问题
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { Delete, EditPen, InfoFilled, MagicStick, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchDeleteGeoQuestions,
  commitGeoWorkorder,
  getGeoBatch,
  getGeoProjectProfile,
  createManualGeoQuestions,
  createOrGetProjectWorkorder,
  deleteGeoQuestion,
  getGeoProjectWorkorders,
  getGeoQuestions,
  getGeoReview,
  saveGeoDraft,
  startPartnerCoreQuestionBatch,
  submitGeoPartnerReview,
  returnGeoPartnerReview,
  updateGeoQuestion,
  type BatchVO,
  type ManualQuestionInput,
  type ProfileVO,
  type QuestionVO,
  type QuotaSnapshot,
  type ReviewVO,
  type WorkorderListItem,
  type WorkorderVO,
} from '@/api/geoQuestion'
import { getProjectList } from '@/api/project'
import DataState from '@/components/ui/DataState.vue'
import type { Project } from '@/types'
import { useUserStore } from '@/stores/user'
import { errorMessage } from '@/utils/error'
import { formatDateTimeSeconds } from '@/utils/format'

defineOptions({ name: 'PartnerKeywordManage' })

const userStore = useUserStore()

type ManualRow = {
  key: number
  questionText: string
  sceneCode: string
  priority: string
  monitorFrequency: string
  relatedNeedText: string
}

type ProjectSupplementDisplayItem = {
  label: string
  value: string
  wide?: boolean
}

const sceneOptions = [
  { code: 'brand', label: '品牌' },
  { code: 'decision', label: '决策' },
  { code: 'deal', label: '成交' },
  { code: 'compare', label: '对比' },
  { code: 'qa', label: '问答' },
  { code: 'function', label: '功能' },
]

function emptyProfile(): ProfileVO {
  return {
    companyId: 0,
    projectId: undefined,
    projectName: '',
    companyName: '',
    brandName: '',
    brandRelation: '自营',
    coreBusiness: [],
    targetRegion: '',
    industry: '',
    targetCustomer: '',
    coreAdvantage: '',
    benchmarkSpecs: '',
    competitors: [],
    coreNeeds: [],
  }
}

const keyword = ref('')
const loadingProjects = ref(false)
const loadingQuestions = ref(false)
const loadingProfile = ref(false)
const saving = ref(false)
const savingProfile = ref(false)
const savingQuestion = ref(false)
const committing = ref(false)
const submittingReview = ref(false)
const returningReview = ref(false)
const duplicateSaving = ref(false)
const generating = ref(false)
const batchDeleting = ref(false)
const projects = ref<Project[]>([])
const selectedProject = ref<Project | null>(null)
const workorder = ref<WorkorderVO | null>(null)
const quota = ref<QuotaSnapshot | null>(null)
const profile = ref<ProfileVO | null>(null)
const profileForm = ref<ProfileVO>(emptyProfile())
const profileCoreBusinessText = ref('')
const profileCoreNeedsText = ref('')
const activeBatch = ref<BatchVO | null>(null)
const review = ref<ReviewVO | null>(null)
const reviewQuestions = ref<QuestionVO[]>([])
const selectedReviewQuestions = ref<QuestionVO[]>([])
const workorderList = ref<WorkorderListItem[]>([])
const manualVisible = ref(false)
const editVisible = ref(false)
const duplicateResolveVisible = ref(false)
const manualReason = ref('')
const manualPasteText = ref('')
const manualDefaultScene = ref('brand')
const manualRows = ref<ManualRow[]>([])
const manualKey = ref(1)
const questionPage = ref(1)
const questionPageSize = 10
const editingQuestionId = ref<number | null>(null)
const duplicateResolveError = ref('')
const duplicateEditForms = ref<Record<number, string>>({})
const editForm = ref<Partial<QuestionVO>>({})
let generationTimer: number | undefined

const validManualRows = computed(() => manualRows.value.filter((row) => row.questionText.trim()))
const pagedReviewQuestions = computed(() => {
  const start = (questionPage.value - 1) * questionPageSize
  return reviewQuestions.value.slice(start, start + questionPageSize)
})
const duplicateQuestionGroups = computed(() => findDuplicateQuestionGroups(reviewQuestions.value))
const duplicateQuestionTexts = computed(() => duplicateQuestionGroups.value.map((group) => group.text))
const isPartnerOwner = computed(() => userStore.role === 'partner')
const isPartnerStaff = computed(() => userStore.role === 'partner_staff')
const partnerReviewStatus = computed(() => workorder.value?.partnerReviewStatus || 'inputting')
const partnerReviewStatusLabel = computed(() => {
  const map: Record<string, string> = {
    inputting: '录入中',
    returned: '已退回',
    pending_owner_review: '待负责人复核',
    submitted_to_hq: '已提交总部',
  }
  return map[partnerReviewStatus.value] || partnerReviewStatus.value
})
const isSubmittedToHq = computed(() => partnerReviewStatus.value === 'submitted_to_hq')
const isPendingOwnerReview = computed(() => partnerReviewStatus.value === 'pending_owner_review')
const canEditWorkorder = computed(() => Boolean(
  workorder.value
    && !isSubmittedToHq.value
    && !(isPartnerStaff.value && isPendingOwnerReview.value),
))
const coreQuotaLimit = computed(() => quota.value?.quotaA ?? (selectedProject.value ? projectCoreQuestionLimit(selectedProject.value) : 0))
const committedCoreCount = computed(() => workorderList.value
  .filter((item) => item.status === 'committed')
  .reduce((max, item) => Math.max(max, Number(item.countA ?? item.countTotal ?? 0)), 0))
const projectHasFullCommittedCore = computed(() => coreQuotaLimit.value > 0 && committedCoreCount.value >= coreQuotaLimit.value)
const maintainedCount = computed(() => Math.max(quota.value?.workorderCountA ?? reviewQuestions.value.length, committedCoreCount.value))
const runningReservedCount = computed(() => quota.value?.runningReservedA ?? 0)
const remainingCoreCount = computed(() => Math.max(coreQuotaLimit.value - maintainedCount.value - runningReservedCount.value, 0))
const generationCount = computed(() => Math.min(50, Math.max(remainingCoreCount.value, 0)))
const quotaPercentage = computed(() => {
  if (!coreQuotaLimit.value) return 0
  return Math.min(100, Math.round((maintainedCount.value / coreQuotaLimit.value) * 100))
})
const quotaProgressStatus = computed(() => maintainedCount.value >= coreQuotaLimit.value && coreQuotaLimit.value > 0 ? 'success' : undefined)
const canOpenManualEntry = computed(() => Boolean(selectedProject.value && canEditWorkorder.value && remainingCoreCount.value > 0))
const projectSupplementItems = computed<ProjectSupplementDisplayItem[]>(() => {
  const current = selectedProject.value
  if (!current) return []
  return [
    { label: '项目别名', value: current.projectAliases || '-' },
    { label: '目标区域词', value: joinArray(current.targetRegions) },
    { label: '核心关键词', value: current.coreKeywords || '-' },
    { label: '目标受众', value: current.targetAudience || '-' },
    { label: '主目标', value: current.primaryGoal || '-', wide: true },
    { label: '备注', value: current.remark || '-', wide: true },
  ]
})
const hasRunningBatch = computed(() => {
  if (activeBatch.value && ['pending', 'running'].includes(activeBatch.value.status)) return true
  return workorderList.value.some((item) => ['pending', 'running'].includes(item.latestBatchStatus || item.status))
})
const canGenerateCoreQuestions = computed(() => Boolean(
  selectedProject.value
    && canEditWorkorder.value
    && generationCount.value > 0
    && !hasRunningBatch.value
    && !projectHasFullCommittedCore.value,
))
const canCommitCoreQuestions = computed(() => Boolean(
  workorder.value
    && coreQuotaLimit.value > 0
    && maintainedCount.value === coreQuotaLimit.value
    && runningReservedCount.value === 0
    && !hasRunningBatch.value
    && duplicateQuestionTexts.value.length === 0
    && canEditWorkorder.value
    && ['draft', 'paused', 'committed'].includes(workorder.value.status),
))
const canSubmitOwnerReview = computed(() => Boolean(isPartnerStaff.value && canCommitCoreQuestions.value))
const canShowOwnerReviewActions = computed(() => Boolean(workorder.value && partnerReviewStatus.value === 'pending_owner_review'))
const manualEntryTip = computed(() => {
  if (!selectedProject.value) return '请先选择项目'
  if (!workorder.value) return '当前项目任务尚未创建'
  if (isSubmittedToHq.value) return '当前项目已提交总部，不能继续修改拓词组'
  if (isPartnerStaff.value && isPendingOwnerReview.value) return '当前拓词组已提交负责人复核，请等待负责人退回后再修改'
  if (remainingCoreCount.value <= 0) return '核心问题额度已用完，不能继续新增'
  return `当前还可录入 ${remainingCoreCount.value} 条核心问题`
})
const generationTitle = computed(() => {
  if (hasRunningBatch.value) return '核心问题正在生成'
  if (remainingCoreCount.value <= 0) return '核心问题额度已完成'
  return '按客户资料生成核心问题'
})
const generationTip = computed(() => {
  if (hasRunningBatch.value) return '当前任务已有生成批次，完成后会自动刷新清单。'
  if (remainingCoreCount.value <= 0) return '项目分配的核心问题额度已全部维护完成。'
  return `系统将按核心问题标准生成，单次最多 50 条，本次将生成 ${generationCount.value} 条。生成后先进入待确认池。`
})
const generationStatusTip = computed(() => {
  if (!selectedProject.value) return '请先选择项目'
  if (!workorder.value) return '当前项目任务尚未创建'
  if (isSubmittedToHq.value) return '当前项目已提交总部，不能继续生成问题'
  if (isPartnerStaff.value && isPendingOwnerReview.value) return '当前拓词组已提交负责人复核，请等待负责人退回后再修改'
  if (hasRunningBatch.value) return '生成任务运行中，请稍候查看结果'
  if (projectHasFullCommittedCore.value) return '当前项目已有满额入库核心问题，不能继续自动生成'
  if (remainingCoreCount.value <= 0) return '核心问题额度已用完，不能继续生成'
  return '生成后不会直接进入项目拓词组，请先检查和修改，确认无误后再正式入库。'
})
const commitStatusTip = computed(() => {
  if (!workorder.value) return '当前项目任务尚未创建'
  if (isSubmittedToHq.value) return '当前项目已提交总部，拓词组已锁定，不能继续修改。'
  if (isPartnerStaff.value && isPendingOwnerReview.value) return '已提交负责人复核，请等待负责人确认，启动工单将由负责人在项目管理中提交。'
  if (isPartnerOwner.value && isPendingOwnerReview.value) return '请核对客户资料与核心问题；如需员工调整，可退回修改。启动工单请在项目管理中提交。'
  if (workorder.value.status === 'committed') return '当前核心问题已入项目拓词组，启动工单提交前仍可修改并再次确认更新。'
  if (hasRunningBatch.value) return '生成任务运行中，请等待完成后再确认入库'
  if (duplicateQuestionTexts.value.length) return `存在重复问题，请先处理：${duplicateQuestionTexts.value.slice(0, 3).join('；')}`
  if (maintainedCount.value !== coreQuotaLimit.value) return `核心问题需补齐到项目分配额度：${maintainedCount.value} / ${coreQuotaLimit.value}`
  if (isPartnerStaff.value) return '问题已满足额度要求，确认入库后将提交负责人复核。'
  return '问题已满足额度要求，可以确认入库。'
})
const manualValidationMessage = computed(() => {
  if (!validManualRows.value.length) return '请至少录入 1 条核心问题'
  if (validManualRows.value.length > remainingCoreCount.value) return `本次最多可提交 ${remainingCoreCount.value} 条核心问题`
  const seen = new Set<string>()
  for (const row of validManualRows.value) {
    const text = row.questionText.trim()
    if (text.length > 500) return '单条核心问题最多 500 字'
    const key = text.replace(/\s+/g, ' ').toLowerCase()
    if (seen.has(key)) return `本次录入存在重复问题：${text}`
    seen.add(key)
  }
  return ''
})

watch(reviewQuestions, (questions) => {
  const pageCount = Math.max(1, Math.ceil(questions.length / questionPageSize))
  if (questionPage.value > pageCount) {
    questionPage.value = pageCount
  }
  const questionIds = new Set(questions.map((item) => item.id))
  selectedReviewQuestions.value = selectedReviewQuestions.value.filter((item) => questionIds.has(item.id))
})

watch(questionPage, () => {
  selectedReviewQuestions.value = []
})

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
    pending_review: '待确认',
    draft: '草稿',
    deleted: '已删除',
  }
  return value ? mapping[value] || '已提交' : '已提交'
}

function geoStatusLabel(value?: string | null) {
  const mapping: Record<string, string> = {
    draft: '草稿',
    paused: '暂停',
    committed: '已入库',
    completed: '已完成',
    active: '已提交',
  }
  return value ? mapping[value] || value : '-'
}

function partnerReviewStatusText(value?: string | null) {
  const mapping: Record<string, string> = {
    inputting: '录入中',
    returned: '负责人已退回',
    pending_owner_review: '待负责人复核',
    submitted_to_hq: '已提交总部',
  }
  return mapping[String(value || 'inputting')] || String(value || '-')
}

function sceneLabel(value?: string | null) {
  return sceneOptions.find((item) => item.code === value)?.label || value || '-'
}

function projectCoreQuestionLimit(project: Project) {
  return Number(
    project.planCoreQuestionLimit
      ?? project.planKeywordGroupLimitA
      ?? project.planKeywordGroupLimit
      ?? (project as any).quotaA
      ?? (project as any).questionQuotaA
      ?? 0,
  )
}

function formatTime(value?: string | null) {
  return formatDateTimeSeconds(value) || '-'
}

function parseStringArray(value?: string | string[] | null) {
  if (Array.isArray(value)) return value.map((item) => String(item).trim()).filter(Boolean)
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) return parsed.map((item) => String(item).trim()).filter(Boolean)
  } catch {
    return String(value).split(/[,，、;；\n\r]+/).map((item) => item.trim()).filter(Boolean)
  }
  return []
}

function joinArray(value?: string | string[] | null) {
  return parseStringArray(value).join('、') || '-'
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
  stopGenerationPolling()
  selectedProject.value = project
  workorder.value = null
  quota.value = null
  profile.value = null
  profileForm.value = emptyProfile()
  profileCoreBusinessText.value = ''
  profileCoreNeedsText.value = ''
  activeBatch.value = null
  review.value = null
  reviewQuestions.value = []
  questionPage.value = 1
  workorderList.value = []
  loadingQuestions.value = true
  try {
    const { data } = await createOrGetProjectWorkorder(project.id)
    workorder.value = data.data
    quota.value = data.data.quota
    await Promise.all([loadProjectProfile(), loadReviewQuestions(), loadWorkorderList()])
  } catch {
    ElMessage.error('加载核心问题失败')
  } finally {
    loadingQuestions.value = false
  }
}

async function loadProjectProfile() {
  if (!selectedProject.value) return
  loadingProfile.value = true
  try {
    const { data } = await getGeoProjectProfile(selectedProject.value.id)
    profile.value = data.data
    applyProfileToForm(data.data)
  } catch {
    profile.value = null
    applyProfileToForm(null)
    ElMessage.error('加载客户资料失败')
  } finally {
    loadingProfile.value = false
  }
}

function applyProfileToForm(source: ProfileVO | null) {
  const base = source || emptyProfile()
  profileForm.value = {
    ...emptyProfile(),
    ...base,
    companyName: base.companyName || selectedProject.value?.companyName || '',
    brandName: base.brandName || selectedProject.value?.brandName || '',
    projectName: base.projectName || selectedProject.value?.projectName || '',
  }
  profileCoreBusinessText.value = (base.coreBusiness || []).join('、')
  profileCoreNeedsText.value = (base.coreNeeds || [])
    .map((item) => String(item?.text || item?.requirementText || item?.name || '').trim())
    .filter(Boolean)
    .join('\n')
}

function buildProfileDraft() {
  const business = splitTextList(profileCoreBusinessText.value)
  const needs = splitTextList(profileCoreNeedsText.value)
  return {
    ...profileForm.value,
    companyId: profileForm.value.companyId || selectedProject.value?.companyId || 0,
    projectId: profileForm.value.projectId || selectedProject.value?.id,
    projectName: profileForm.value.projectName || selectedProject.value?.projectName || '',
    coreBusiness: business,
    coreNeeds: needs.map((text) => ({ text, scene: 'brand', urgent: false })),
  }
}

function splitTextList(value: string) {
  return value
    .split(/[\n;；,，、]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

async function saveProfileDraft(showMessage = true) {
  if (!workorder.value) {
    ElMessage.warning('当前项目任务尚未创建')
    return false
  }
  if (!canEditWorkorder.value) {
    ElMessage.warning(commitStatusTip.value)
    return false
  }
  savingProfile.value = true
  try {
    const draft = buildProfileDraft()
    await saveGeoDraft({
      workorderId: workorder.value.id,
      profileJson: JSON.stringify(draft),
      syncToCustomerProfile: true,
      validationStatus: 'valid',
    })
    profile.value = draft
    profileForm.value = draft
    if (showMessage) ElMessage.success('客户资料已保存')
    return true
  } catch {
    ElMessage.error('保存客户资料失败')
    return false
  } finally {
    savingProfile.value = false
  }
}

async function loadReviewQuestions() {
  if (!workorder.value) return
  const { data } = await getGeoQuestions(workorder.value.id, { tier: 'A', current: 1, size: 500 })
  reviewQuestions.value = data.data.records || []
  await refreshReview()
}

async function refreshReview() {
  if (!workorder.value) return
  const { data } = await getGeoReview(workorder.value.id)
  review.value = data.data
  quota.value = data.data.workorder.quota
  workorder.value = data.data.workorder
  reviewQuestions.value = (data.data.questions || []).filter((item) => item.tier === 'A')
}

async function loadWorkorderList() {
  if (!selectedProject.value) return
  const { data } = await getGeoProjectWorkorders(selectedProject.value.id)
  workorderList.value = data.data || []
}

async function reloadSelectedProject() {
  if (!selectedProject.value) return
  await selectProject(selectedProject.value)
}

async function generateCoreQuestions() {
  if (!workorder.value) {
    ElMessage.warning(generationStatusTip.value)
    return
  }
  if (!canGenerateCoreQuestions.value) {
    ElMessage.warning(generationStatusTip.value)
    return
  }
  generating.value = true
  try {
    if (!(await saveProfileDraft(false))) return
    const { data } = await startPartnerCoreQuestionBatch(workorder.value.id, { count: generationCount.value })
    activeBatch.value = data.data
    ElMessage.success('核心问题生成任务已创建')
    await Promise.all([loadWorkorderList(), loadReviewQuestions()])
    startGenerationPolling(data.data.id)
  } catch {
    ElMessage.error('创建生成任务失败')
  } finally {
    generating.value = false
  }
}

function startEditQuestion(question: QuestionVO) {
  if (!canEditWorkorder.value) {
    ElMessage.warning(commitStatusTip.value)
    return
  }
  editingQuestionId.value = question.id
  editForm.value = { ...question }
  editVisible.value = true
}

async function saveEditQuestion() {
  if (!editingQuestionId.value) return
  const text = String(editForm.value.questionText || '').trim()
  if (!text) {
    ElMessage.warning('核心问题不能为空')
    return
  }
  savingQuestion.value = true
  try {
    await updateGeoQuestion(editingQuestionId.value, {
      ...editForm.value,
      questionText: text,
      tier: 'A',
    })
    ElMessage.success('核心问题已保存')
    editVisible.value = false
    await refreshReview()
  } catch {
    ElMessage.error('保存核心问题失败')
  } finally {
    savingQuestion.value = false
  }
}

async function removeQuestion(question: QuestionVO) {
  if (!canEditWorkorder.value) {
    ElMessage.warning(commitStatusTip.value)
    return
  }
  await ElMessageBox.confirm(`确认删除核心问题「${question.questionText}」？删除后会释放 1 条项目额度。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
  })
  await deleteGeoQuestion(question.id)
  ElMessage.success('核心问题已删除')
  await refreshReview()
}

function handleQuestionSelectionChange(selection: QuestionVO[]) {
  selectedReviewQuestions.value = selection
}

function questionSelectable() {
  return canEditWorkorder.value
}

async function removeSelectedQuestions() {
  if (!canEditWorkorder.value) {
    ElMessage.warning(commitStatusTip.value)
    return
  }
  const questions = [...selectedReviewQuestions.value]
  if (!questions.length) return
  try {
    await ElMessageBox.confirm(
      `确认删除已选 ${questions.length} 条核心问题？删除后会释放对应项目额度。`,
      '批量删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  batchDeleting.value = true
  try {
    const { data } = await batchDeleteGeoQuestions(questions.map((question) => question.id))
    const successCount = data.data || 0
    selectedReviewQuestions.value = []
    await refreshReview()
    ElMessage.success(`已删除 ${successCount} 条核心问题`)
  } finally {
    batchDeleting.value = false
  }
}

async function commitCoreQuestions() {
  if (!workorder.value) return
  await refreshReview()
  if (duplicateQuestionTexts.value.length) {
    ElMessage.warning(`存在重复问题，请修改后再入库：${duplicateQuestionTexts.value.slice(0, 5).join('；')}`)
    openDuplicateResolveDialog()
    return
  }
  if (!canCommitCoreQuestions.value) {
    ElMessage.warning(commitStatusTip.value)
    return
  }
  try {
    await ElMessageBox.confirm(
      '确认将当前核心问题入项目拓词组？入库后负责人和总部将按这批问题继续后续流程。',
      '确认入库',
      { type: 'warning', confirmButtonText: '确认入库', cancelButtonText: '取消' },
    )
    committing.value = true
    await commitGeoWorkorder(workorder.value.id, 'v1.0')
    ElMessage.success('核心问题已进入项目拓词组')
    await Promise.all([refreshReview(), loadWorkorderList()])
  } catch (error) {
    if (isDuplicateCommitError(error)) {
      await refreshReview()
      openDuplicateResolveDialog()
      return
    }
    if (error !== 'cancel') {
      ElMessage.error('确认入库失败')
    }
  } finally {
    committing.value = false
  }
}

async function submitOwnerReview() {
  if (!workorder.value) return
  if (!canSubmitOwnerReview.value) {
    ElMessage.warning(commitStatusTip.value)
    return
  }
  try {
    await ElMessageBox.confirm(
      '确认将当前核心问题入项目拓词组，并提交给负责人复核？提交后需负责人退回才可继续修改。',
      '提交负责人复核',
      { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  committing.value = true
  submittingReview.value = true
  try {
    await commitGeoWorkorder(workorder.value.id, 'v1.0')
    await submitGeoPartnerReview(workorder.value.id)
    ElMessage.success('已提交负责人复核')
    await Promise.all([refreshReview(), loadWorkorderList()])
  } catch (err) {
    if (isDuplicateCommitError(err)) {
      await refreshReview()
      openDuplicateResolveDialog()
      return
    }
    ElMessage.error(errorMessage(err, '提交负责人复核失败'))
  } finally {
    committing.value = false
    submittingReview.value = false
  }
}

async function returnOwnerReview() {
  if (!workorder.value) return
  try {
    const { value } = await ElMessageBox.prompt(
      '请填写退回原因，交付员工会根据原因修改客户资料或核心问题。',
      '退回修改',
      {
        confirmButtonText: '确认退回',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '例如：请补充更贴近成交转化的问题词',
        inputValidator: (value) => Boolean(String(value || '').trim()) || '请填写退回原因',
      },
    )
    returningReview.value = true
    await returnGeoPartnerReview(workorder.value.id, String(value || '').trim())
    ElMessage.success('已退回交付员工修改')
    await Promise.all([refreshReview(), loadWorkorderList()])
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(errorMessage(err, '退回修改失败'))
    }
  } finally {
    returningReview.value = false
  }
}

function openDuplicateResolveDialog() {
  duplicateResolveError.value = ''
  const forms: Record<number, string> = {}
  duplicateQuestionGroups.value.forEach((group) => {
    group.items.forEach((item) => {
      forms[item.id] = item.questionText
    })
  })
  duplicateEditForms.value = forms
  duplicateResolveVisible.value = true
}

async function saveDuplicateEdits() {
  if (!canEditWorkorder.value) {
    ElMessage.warning(commitStatusTip.value)
    return
  }
  duplicateResolveError.value = ''
  const nextQuestions = reviewQuestions.value.map((question) => ({
    ...question,
    questionText: duplicateEditForms.value[question.id] ?? question.questionText,
  }))
  const stillDuplicate = findDuplicateQuestionGroups(nextQuestions)
  if (stillDuplicate.length) {
    duplicateResolveError.value = `仍存在重复问题：${stillDuplicate.map((group) => group.text).slice(0, 5).join('；')}`
    return
  }
  duplicateSaving.value = true
  try {
    const changed = reviewQuestions.value.filter((question) => {
      const nextText = String(duplicateEditForms.value[question.id] || '').trim()
      return nextText && nextText !== question.questionText
    })
    for (const question of changed) {
      await updateGeoQuestion(question.id, {
        ...question,
        questionText: String(duplicateEditForms.value[question.id]).trim(),
        tier: 'A',
      })
    }
    await refreshReview()
    if (duplicateQuestionTexts.value.length) {
      duplicateResolveError.value = `仍存在重复问题：${duplicateQuestionTexts.value.slice(0, 5).join('；')}`
      openDuplicateResolveDialog()
      return
    }
    duplicateResolveVisible.value = false
    ElMessage.success('重复问题已处理')
  } catch {
    ElMessage.error('保存重复问题修改失败')
  } finally {
    duplicateSaving.value = false
  }
}

function questionDedupeKey(value?: string | null) {
  return String(value || '').trim().replace(/\s+/g, ' ').toLowerCase()
}

function findDuplicateQuestionGroups(questions: QuestionVO[]) {
  const groups = new Map<string, QuestionVO[]>()
  questions.forEach((question) => {
    const key = questionDedupeKey(question.questionText)
    if (!key) return
    const items = groups.get(key) || []
    items.push(question)
    groups.set(key, items)
  })
  return Array.from(groups.entries())
    .filter(([, items]) => items.length > 1)
    .map(([key, items]) => ({ key, text: items[0].questionText, items }))
}

function isDuplicateCommitError(error: unknown) {
  const message = String((error as any)?.response?.data?.message || (error as any)?.message || '')
  return message.includes('重复问题') || message.includes('请替换后再入库')
}

function startGenerationPolling(batchId: number) {
  stopGenerationPolling()
  generationTimer = window.setInterval(async () => {
    try {
      const { data } = await getGeoBatch(batchId)
      activeBatch.value = data.data
      if (!['pending', 'running'].includes(data.data.status)) {
        stopGenerationPolling()
        await reloadSelectedProject()
        if (data.data.status === 'success') {
          ElMessage.success('核心问题已生成')
        } else if (data.data.status === 'failed') {
          ElMessage.error(data.data.errorMessage || '核心问题生成失败')
        }
      }
    } catch {
      stopGenerationPolling()
    }
  }, 3000)
}

function stopGenerationPolling() {
  if (generationTimer) {
    window.clearInterval(generationTimer)
    generationTimer = undefined
  }
}

function blankManualRow(): ManualRow {
  return {
    key: manualKey.value++,
    questionText: '',
    sceneCode: manualDefaultScene.value,
    priority: 'medium',
    monitorFrequency: 'weekly',
    relatedNeedText: '',
  }
}

function openManualEntry() {
  if (!canOpenManualEntry.value) {
    ElMessage.warning(manualEntryTip.value)
    return
  }
  manualReason.value = ''
  manualPasteText.value = ''
  manualDefaultScene.value = 'brand'
  manualRows.value = [blankManualRow()]
  manualVisible.value = true
}

function addManualRow(questionText = '') {
  const row = blankManualRow()
  row.questionText = questionText
  manualRows.value.push(row)
}

function removeManualRow(index: number) {
  manualRows.value.splice(index, 1)
}

function appendManualPaste() {
  const lines = manualPasteText.value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
  if (!lines.length) {
    ElMessage.warning('请先粘贴核心问题')
    return
  }
  const existing = new Set(manualRows.value.map((row) => row.questionText.trim().replace(/\s+/g, ' ').toLowerCase()).filter(Boolean))
  lines.forEach((line) => {
    const key = line.replace(/\s+/g, ' ').toLowerCase()
    if (existing.has(key)) return
    const emptyRow = manualRows.value.find((row) => !row.questionText.trim())
    if (emptyRow) {
      emptyRow.questionText = line
    } else {
      addManualRow(line)
    }
    existing.add(key)
  })
  manualPasteText.value = ''
}

async function submitManualQuestions() {
  if (!workorder.value || manualValidationMessage.value) return
  saving.value = true
  try {
    const items: ManualQuestionInput[] = validManualRows.value.map((row) => ({
      questionText: row.questionText.trim(),
      tier: 'A',
      sceneCode: row.sceneCode,
      priority: row.priority,
      monitorFrequency: row.monitorFrequency,
      relatedNeedText: row.relatedNeedText.trim() || undefined,
      designReason: '合伙人员工手动录入的核心问题',
    }))
    await createManualGeoQuestions(workorder.value.id, {
      items,
      manualReason: manualReason.value.trim() || '合伙人员工提交核心问题',
    })
    manualVisible.value = false
    ElMessage.success('核心问题已提交')
    await reloadSelectedProject()
  } catch {
    ElMessage.error('提交核心问题失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadProjects)
onUnmounted(stopGenerationPolling)
</script>

<style scoped>
.partner-keyword-page {
  display: grid;
  gap: 16px;
}

.keyword-context-bar,
.partner-surface {
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
}

.keyword-context-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 20px;
  background: linear-gradient(135deg, #fbfdff 0%, #eff6ff 56%, #ecfdf5 100%);
}

.keyword-context-bar.empty {
  background: #fff;
}

.context-main {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 14px;
}

.context-avatar,
.project-avatar,
.quota-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  background: #2563eb;
  color: #fff;
  font-weight: 900;
}

.context-avatar {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  font-size: 20px;
}

.context-main strong {
  display: block;
  color: #0f172a;
  font-size: 18px;
  font-weight: 900;
}

.context-main p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.context-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(112px, 1fr));
  gap: 10px;
  min-width: 520px;
}

.context-metrics span,
.quota-grid div {
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.88);
}

.context-metrics b,
.context-metrics em,
.quota-grid span,
.quota-grid strong {
  display: block;
}

.context-metrics b {
  color: #0f172a;
  font-size: 18px;
  font-weight: 900;
}

.context-metrics em,
.quota-grid span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}

.keyword-workspace {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.project-panel {
  position: sticky;
  top: 12px;
}

.panel-head {
  margin-bottom: 14px;
}

.panel-head strong,
.panel-head span {
  display: block;
}

.panel-head strong {
  color: #0f172a;
  font-size: 16px;
  font-weight: 900;
}

.panel-head span {
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.project-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  margin-bottom: 14px;
}

.project-list {
  display: grid;
  gap: 10px;
  max-height: 620px;
  overflow: auto;
  padding-right: 2px;
}

.project-card {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px;
  background: #f8fafc;
  cursor: pointer;
  text-align: left;
}

.project-card:hover,
.project-card.selected {
  border-color: #93c5fd;
  background: #eff6ff;
}

.project-avatar {
  width: 38px;
  height: 38px;
  border-radius: 10px;
}

.project-card-main {
  min-width: 0;
}

.project-card-main b,
.project-card-main em {
  display: block;
}

.project-card-main b {
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-card-main em {
  overflow: hidden;
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-quota,
.question-status {
  border-radius: 999px;
  padding: 4px 9px;
  background: #ecfdf5;
  color: #047857;
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
}

.keyword-main {
  display: grid;
  gap: 16px;
}

.selected-info {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.selected-info div {
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}

.selected-info label,
.selected-info strong {
  display: block;
}

.selected-info label {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.selected-info strong {
  overflow: hidden;
  margin-top: 6px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quota-card {
  background: linear-gradient(135deg, #fff 0%, #f8fbff 100%);
}

.customer-profile-card {
  background: #fff;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.profile-grid > div,
.profile-grid > label {
  min-width: 0;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px 14px;
  background: #f8fafc;
}

.profile-grid label span,
.profile-grid strong,
.profile-grid p {
  display: block;
}

.profile-grid label span {
  margin-bottom: 8px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.profile-grid.editable > label {
  background: #fff;
}

.profile-grid strong {
  overflow: hidden;
  margin-top: 6px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-grid p {
  margin: 6px 0 0;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.65;
}

.profile-grid .profile-wide {
  grid-column: 1 / -1;
  background: #fff;
}

.profile-actions,
.review-actions,
.question-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.quota-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 20px;
  align-items: center;
  margin-bottom: 14px;
}

.quota-head > div {
  display: flex;
  align-items: center;
  gap: 12px;
}

.quota-badge {
  width: 40px;
  height: 40px;
  border-radius: 12px;
}

.quota-head strong {
  color: #0f172a;
  font-size: 16px;
  font-weight: 900;
}

.quota-head p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.quota-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.quota-grid strong {
  margin-top: 6px;
  color: #0f172a;
  font-size: 24px;
  font-weight: 900;
  line-height: 1;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-head h2 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 900;
}

.section-head p {
  margin: 5px 0 0;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.entry-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.generation-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.55fr);
  gap: 14px;
  align-items: stretch;
  margin-bottom: 12px;
}

.generation-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  padding: 16px;
  background: linear-gradient(135deg, #f8fbff 0%, #ecfdf5 100%);
}

.generation-copy strong,
.generation-copy span {
  display: block;
}

.generation-copy strong {
  color: #0f172a;
  font-size: 17px;
  font-weight: 900;
}

.generation-copy span {
  margin-top: 6px;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.6;
}

.generation-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.generation-metrics span {
  min-width: 0;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px;
  background: #fff;
}

.generation-metrics b,
.generation-metrics em {
  display: block;
}

.generation-metrics b {
  color: #0f172a;
  font-size: 24px;
  font-weight: 900;
  line-height: 1;
}

.generation-metrics em {
  margin-top: 7px;
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}

.entry-note,
.review-note,
.manual-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #bfdbfe;
  border-radius: 12px;
  padding: 12px 14px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 700;
}

.entry-note.warning,
.review-note.warning,
.manual-tip.error {
  border-color: #fde68a;
  background: #fffbeb;
  color: #92400e;
}

.review-note {
  margin-bottom: 12px;
}

.question-text {
  color: #0f172a;
  font-weight: 800;
  line-height: 1.5;
}

.locked-text {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 800;
}

.question-edit-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.duplicate-note {
  margin-bottom: 14px;
  border: 1px solid #fde68a;
  border-radius: 10px;
  padding: 10px 12px;
  background: #fffbeb;
  color: #92400e;
  font-size: 13px;
  font-weight: 700;
}

.duplicate-list {
  display: grid;
  gap: 12px;
  max-height: 520px;
  overflow: auto;
}

.duplicate-group {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
}

.duplicate-title,
.duplicate-edit-row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 120px;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
}

.duplicate-title {
  grid-template-columns: minmax(0, 1fr) auto;
  background: #f8fbff;
  color: #0f172a;
  font-size: 13px;
}

.duplicate-title span,
.duplicate-edit-row span,
.duplicate-edit-row em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}

.duplicate-edit-row {
  border-top: 1px solid #edf2f7;
}

.question-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid #e2e8f0;
  border-top: 0;
  border-radius: 0 0 12px 12px;
  padding: 12px 14px;
  background: #fff;
}

.question-pagination span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.workorder-list {
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.workorder-row {
  display: grid;
  grid-template-columns:
    minmax(220px, 1.25fr)
    minmax(120px, 0.7fr)
    minmax(120px, 0.7fr)
    minmax(180px, 1fr);
  column-gap: 24px;
  align-items: center;
  padding: 13px 20px;
  border-bottom: 1px solid #edf2f7;
}

.workorder-row > div {
  justify-self: start;
}

.workorder-row:last-child {
  border-bottom: 0;
}

.workorder-row.head {
  background: #f8fbff;
  color: #334155;
  font-size: 13px;
  font-weight: 900;
}

.workorder-row strong,
.workorder-row small {
  display: block;
}

.workorder-row strong {
  color: #0f172a;
}

.workorder-status-cell {
  display: inline-flex;
  min-width: 72px;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  line-height: 1;
}

.workorder-status-cell .question-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-left: -9px;
}

.workorder-row small,
.workorder-empty {
  color: #64748b;
  font-size: 12px;
}

.workorder-empty,
.blank-state {
  padding: 42px 20px;
  text-align: center;
}

.blank-state strong,
.blank-state span {
  display: block;
}

.blank-state strong {
  color: #0f172a;
  font-size: 18px;
  font-weight: 900;
}

.blank-state span {
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.manual-form-grid {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 16px;
  margin-bottom: 18px;
}

.manual-form-grid label,
.manual-label-row {
  display: grid;
  gap: 8px;
}

.manual-form-grid span,
.manual-label-row span {
  color: #475569;
  font-size: 13px;
  font-weight: 800;
}

.manual-paste {
  margin-bottom: 18px;
}

.manual-label-row {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  margin-bottom: 8px;
}

.manual-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  border-radius: 12px;
  padding: 12px 14px;
  background: #f8fafc;
}

.manual-toolbar strong,
.manual-toolbar span {
  display: inline-block;
  margin-right: 14px;
}

.manual-toolbar strong {
  color: #0f172a;
}

.manual-toolbar span {
  color: #64748b;
  font-size: 13px;
}

.manual-table {
  overflow-x: auto;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.manual-row {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) 110px 90px 100px 150px 60px;
  gap: 8px;
  min-width: 780px;
  align-items: center;
  padding: 9px 12px;
  border-top: 1px solid #edf2f7;
}

.manual-row:first-child {
  border-top: 0;
}

.manual-row.head {
  background: #f8fbff;
  color: #334155;
  font-size: 12px;
  font-weight: 900;
}

.manual-tip {
  margin-top: 14px;
}

@media (max-width: 1180px) {
  .keyword-context-bar,
  .keyword-workspace {
    grid-template-columns: 1fr;
  }

  .keyword-context-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .context-metrics {
    min-width: 0;
  }

  .project-panel {
    position: static;
  }
}

@media (max-width: 768px) {
  .context-metrics,
  .selected-info,
  .profile-grid,
  .quota-grid,
  .generation-layout,
  .generation-metrics,
  .question-edit-grid,
  .duplicate-title,
  .duplicate-edit-row,
  .manual-form-grid {
    grid-template-columns: 1fr;
  }

  .quota-head,
  .workorder-row,
  .workorder-row.head {
    grid-template-columns: 1fr;
  }

  .section-head,
  .entry-actions,
  .profile-actions,
  .review-actions,
  .manual-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
