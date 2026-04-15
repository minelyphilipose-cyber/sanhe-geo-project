<template>
  <div class="space-y-4">
    <el-page-header content="售前诊断" @back="$router.back()" />

    <el-card>
      <div class="toolbar">
        <div class="text-sm text-gray-500">
          项目ID：{{ projectId }}，流程：生成问题集 -> 锁定 -> 开始诊断 -> 自动生成草稿 -> 跳转报表页
        </div>
        <div class="flex items-center gap-2">
          <el-button :loading="loading" @click="loadSets">刷新</el-button>
          <el-button v-if="canWrite" type="primary" :loading="generating" @click="generate(false)">生成问题集</el-button>
          <el-button v-if="canWrite && sets.length > 0" :loading="generating" @click="generate(true)">重新生成</el-button>
        </div>
      </div>
    </el-card>

    <el-card v-if="diagnosis.visible" class="progress-card">
      <div class="progress-header">
        <div>
          <div class="font-medium">售前诊断执行中</div>
          <div class="text-xs text-gray-500">任务ID：{{ diagnosis.taskId }}，状态：{{ diagnosisStatusText }}</div>
        </div>
        <el-tag :type="diagnosisTagType">{{ diagnosisStatusText }}</el-tag>
      </div>

      <el-progress :percentage="diagnosis.percent" :status="diagnosisProgressStatus" :stroke-width="14" />

      <el-alert
        v-if="diagnosis.error"
        title="诊断任务执行失败"
        type="error"
        :description="diagnosis.error"
        show-icon
        class="mt-3"
      />

      <div class="mt-3 flex items-center gap-2">
        <el-button type="primary" :disabled="!diagnosisCanJump" @click="goReportList">前往项目报表</el-button>
        <el-button @click="loadSets">刷新问题集状态</el-button>
      </div>
    </el-card>

    <el-card>
      <DataState :loading="loading" :empty="!loading && sets.length === 0" empty-text="暂无售前问题集">
        <el-table :data="sets" border>
          <el-table-column prop="versionNo" label="版本" width="80" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="questionCount" label="题目数" width="100" />
          <el-table-column prop="generatedAt" label="生成时间" width="180" />
          <el-table-column prop="lockedAt" label="锁定时间" width="180" />
          <el-table-column label="操作" width="340" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openSet(scope.row.id)">查看/编辑</el-button>
              <el-button
                v-if="canWrite && scope.row.status === 'draft'"
                link
                type="success"
                :loading="lockingSetId === scope.row.id"
                @click="lock(scope.row.id)"
              >
                锁定
              </el-button>
              <el-button
                v-if="canStartDiagnosis && scope.row.status === 'locked'"
                link
                type="warning"
                :loading="startingSetId === scope.row.id"
                @click="start(scope.row.id)"
              >
                开始诊断
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <el-dialog v-model="detailVisible" title="问题集编辑" width="1000px">
      <div class="mb-3 flex items-center justify-between">
        <div class="text-sm text-gray-500">
          状态：{{ currentSet?.status || '-' }}，仅 draft 可编辑
        </div>
        <div class="flex items-center gap-2">
          <el-button v-if="editable" @click="addQuestion">新增题目</el-button>
          <el-button v-if="editable" type="primary" :loading="saving" @click="save">保存</el-button>
        </div>
      </div>
      <el-table :data="editingItems" border max-height="520">
        <el-table-column prop="sortOrder" label="排序" width="80">
          <template #default="scope">
            <el-input-number v-if="editable" v-model="scope.row.sortOrder" :min="1" :controls="false" style="width: 66px" />
            <span v-else>{{ scope.row.sortOrder }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="问题内容" min-width="360">
          <template #default="scope">
            <el-input v-if="editable" v-model="scope.row.content" />
            <span v-else>{{ scope.row.content }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="questionType" label="分类" width="140">
          <template #default="scope">
            <el-select v-if="editable" v-model="scope.row.questionType" style="width: 120px">
              <el-option label="品牌词" value="brand" />
              <el-option label="行业词" value="industry" />
              <el-option label="决策词" value="decision" />
              <el-option label="竞品词" value="competitor" />
              <el-option label="问答词" value="qa" />
            </el-select>
            <span v-else>{{ scope.row.questionType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="source" label="来源" width="100" />
        <el-table-column prop="isActive" label="启用" width="100">
          <template #default="scope">
            <el-switch v-if="editable" v-model="scope.row.isActive" />
            <span v-else>{{ scope.row.isActive ? '是' : '否' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="editable" label="操作" width="90" fixed="right">
          <template #default="scope">
            <el-button link type="danger" @click="removeQuestion(scope.$index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import { useUserStore } from '@/stores/user'
import type { DispatchTaskItem, PresaleQuestionItem, PresaleQuestionSet } from '@/types'
import {
  generatePresaleQuestionSet,
  getPresaleQuestionSetDetail,
  getPresaleQuestionSets,
  lockPresaleQuestionSet,
  savePresaleQuestionSetItems,
  startPresaleDiagnosis,
} from '@/api/presale'
import { getDispatchTask } from '@/api/dispatch'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const projectId = Number(route.params.id)

const canWrite = computed(() => userStore.hasPermission('project.write'))
const canStartDiagnosis = computed(() => userStore.hasPermission('dispatch.presale.enqueue') || canWrite.value)

const loading = ref(false)
const generating = ref(false)
const saving = ref(false)
const sets = ref<PresaleQuestionSet[]>([])
const lockingSetId = ref<number | null>(null)
const startingSetId = ref<number | null>(null)

const detailVisible = ref(false)
const currentSet = ref<PresaleQuestionSet | null>(null)
const editingItems = ref<PresaleQuestionItem[]>([])
const editable = computed(() => !!currentSet.value && currentSet.value.status === 'draft' && canWrite.value)

const diagnosis = reactive({
  visible: false,
  taskId: 0,
  status: '',
  percent: 0,
  error: '',
})

let pollTimer: number | null = null

const diagnosisStatusText = computed(() => {
  const map: Record<string, string> = {
    pending: '排队中',
    running: '执行中',
    retry_pending: '重试中',
    completed: '已完成',
    failed: '失败',
    dead_letter: '失败（死信）',
  }
  return map[diagnosis.status] || '处理中'
})

const diagnosisTagType = computed(() => {
  if (diagnosis.status === 'completed') return 'success'
  if (diagnosis.status === 'failed' || diagnosis.status === 'dead_letter') return 'danger'
  if (diagnosis.status === 'retry_pending') return 'warning'
  return 'info'
})

const diagnosisProgressStatus = computed(() => {
  if (diagnosis.status === 'completed') return 'success'
  if (diagnosis.status === 'failed' || diagnosis.status === 'dead_letter') return 'exception'
  return undefined
})

const diagnosisCanJump = computed(() => diagnosis.status === 'completed')

function goReportList() {
  router.push(`/admin/projects/${projectId}/reports`)
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function calcProgressByStatus(status: string): number {
  if (status === 'pending') return 15
  if (status === 'running') return 65
  if (status === 'retry_pending') return 45
  if (status === 'completed') return 100
  if (status === 'failed' || status === 'dead_letter') return 100
  return 10
}

async function pullDiagnosisTask(taskId: number) {
  const { data } = await getDispatchTask(taskId)
  const task = data.data as DispatchTaskItem
  diagnosis.status = task.status || 'pending'
  diagnosis.percent = calcProgressByStatus(diagnosis.status)
  if (diagnosis.status === 'completed') {
    stopPolling()
    diagnosis.error = ''
    ElMessage.success('售前诊断已完成，正在跳转项目报表页')
    setTimeout(() => {
      goReportList()
    }, 500)
    return
  }
  if (diagnosis.status === 'failed' || diagnosis.status === 'dead_letter') {
    stopPolling()
    diagnosis.error = task.lastError || '诊断任务执行失败，请稍后重试'
    ElMessage.error('售前诊断执行失败，请查看失败原因')
  }
}

function startPolling(taskId: number) {
  stopPolling()
  pullDiagnosisTask(taskId).catch(() => {
    diagnosis.error = '获取诊断任务状态失败'
    stopPolling()
  })
  pollTimer = window.setInterval(() => {
    pullDiagnosisTask(taskId).catch(() => {
      diagnosis.error = '获取诊断任务状态失败'
      stopPolling()
    })
  }, 2500)
}

async function loadSets() {
  loading.value = true
  try {
    const { data } = await getPresaleQuestionSets(projectId)
    sets.value = data.data || []
  } finally {
    loading.value = false
  }
}

async function generate(regenerate: boolean) {
  generating.value = true
  try {
    await generatePresaleQuestionSet(projectId, regenerate)
    ElMessage.success(regenerate ? '已创建新版本问题集' : '问题集已生成')
    await loadSets()
  } finally {
    generating.value = false
  }
}

async function openSet(setId: number) {
  const { data } = await getPresaleQuestionSetDetail(setId)
  currentSet.value = data.data.set
  editingItems.value = (data.data.items || []).map((it) => ({ ...it }))
  detailVisible.value = true
}

function addQuestion() {
  editingItems.value.push({
    id: 0,
    setId: currentSet.value?.id || 0,
    projectId,
    content: '',
    questionType: 'qa',
    source: 'manual',
    sortOrder: editingItems.value.length + 1,
    isActive: true,
  })
}

function removeQuestion(index: number) {
  editingItems.value.splice(index, 1)
}

async function save() {
  if (!currentSet.value) return
  for (const item of editingItems.value) {
    if (!item.content?.trim()) {
      ElMessage.warning('问题内容不能为空')
      return
    }
  }
  saving.value = true
  try {
    await savePresaleQuestionSetItems(
      currentSet.value.id,
      editingItems.value.map((it, idx) => ({
        id: it.id > 0 ? it.id : undefined,
        content: it.content,
        questionType: it.questionType,
        source: it.source || 'manual',
        sortOrder: it.sortOrder || (idx + 1),
        isActive: it.isActive,
      })),
    )
    ElMessage.success('保存成功')
    await openSet(currentSet.value.id)
    await loadSets()
  } finally {
    saving.value = false
  }
}

async function lock(setId: number) {
  lockingSetId.value = setId
  try {
    await lockPresaleQuestionSet(setId)
    ElMessage.success('问题集已锁定')
    await loadSets()
    if (currentSet.value?.id === setId) {
      await openSet(setId)
    }
  } finally {
    lockingSetId.value = null
  }
}

async function start(setId: number) {
  if (diagnosis.visible && ['pending', 'running', 'retry_pending'].includes(diagnosis.status)) {
    ElMessage.warning('已有诊断任务正在执行，请稍候')
    return
  }

  startingSetId.value = setId
  try {
    const { data } = await startPresaleDiagnosis(projectId, setId)
    const taskId = data.data?.id
    if (!taskId) {
      ElMessage.warning('任务已投递，但未返回任务ID，请在报表页查看结果')
      goReportList()
      return
    }

    diagnosis.visible = true
    diagnosis.taskId = taskId
    diagnosis.status = 'pending'
    diagnosis.percent = 10
    diagnosis.error = ''

    ElMessage.success('诊断任务已投递，正在执行')
    startPolling(taskId)
  } finally {
    startingSetId.value = null
  }
}

onBeforeUnmount(() => {
  stopPolling()
})

loadSets()
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.progress-card {
  border-left: 4px solid var(--el-color-primary);
}

.progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
</style>
