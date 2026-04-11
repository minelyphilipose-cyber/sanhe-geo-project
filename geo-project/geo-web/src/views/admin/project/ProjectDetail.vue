<template>
  <div class="space-y-4">
    <el-page-header content="项目详情" @back="$router.back()" />

    <el-card v-loading="loading">
      <template #header>
        <div class="flex items-center justify-between">
          <span>基础信息</span>
          <div class="space-x-2">
            <el-tag>{{ project?.status || '-' }}</el-tag>
            <el-tag type="info">{{ project?.stage || '-' }}</el-tag>
            <el-button v-if="canWriteProject" type="danger" link @click="removeCurrentProject">删除项目</el-button>
          </div>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="项目编码">{{ project?.projectCode }}</el-descriptions-item>
        <el-descriptions-item label="项目名称">{{ project?.projectName }}</el-descriptions-item>
        <el-descriptions-item label="品牌ID">{{ project?.brandId }}</el-descriptions-item>
        <el-descriptions-item label="套餐">{{ project?.packageType }}</el-descriptions-item>
        <el-descriptions-item label="签约价(元)">{{ centsToYuan(project?.packagePrice) }}</el-descriptions-item>
        <el-descriptions-item label="服务月数">{{ project?.serviceMonths }}</el-descriptions-item>
        <el-descriptions-item label="归属类型">{{ project?.ownerType }}</el-descriptions-item>
        <el-descriptions-item label="合伙人ID">{{ project?.partnerId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="交付模式">{{ project?.deliveryMode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主目标" :span="3">{{ project?.primaryGoal || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card>
      <template #header><span>状态推进</span></template>
      <el-form :model="progressForm" label-width="120px" style="max-width: 540px">
        <el-form-item label="项目状态">
          <el-select v-model="progressForm.status" style="width: 100%">
            <el-option v-for="v in statusOptions" :key="v" :label="v" :value="v" :disabled="isStatusDisabled(v)" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目阶段">
          <el-select v-model="progressForm.stage" style="width: 100%" :disabled="isArchivedStatus">
            <el-option v-for="v in availableStageOptions" :key="v" :label="v" :value="v" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="canWriteProject">
          <el-button type="primary" :loading="saving" @click="submitProgress">保存推进状态</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { deleteProject, getProjectDetail, updateProjectStage, updateProjectStatus } from '@/api/project'
import type { Project } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const canWriteProject = computed(() => userStore.hasPermission('project.write'))
const projectId = Number(route.params.id)
const hasValidId = Number.isFinite(projectId) && projectId > 0

const loading = ref(false)
const saving = ref(false)
const project = ref<Project | null>(null)

const statusOptions = ['draft', 'active', 'paused', 'dispute', 'completed', 'archived']
const stageOptions = [
  'pending_start',
  'collecting_materials',
  'baseline_diagnosis',
  'building_questions',
  'executing',
  'biweekly_feedback',
  'monthly_report',
  'quarterly_report',
  'needs_renewal',
  'high_risk',
  'dispute_handling',
  'completed',
]
const draftAllowedStages = ['pending_start', 'collecting_materials']
const statusTransitionMap: Record<string, string[]> = {
  draft: ['active', 'archived'],
  active: ['paused', 'dispute', 'completed', 'archived'],
  paused: ['active', 'dispute', 'archived'],
  dispute: ['active', 'paused', 'archived'],
  completed: ['archived'],
  archived: [],
}

const progressForm = reactive({ status: 'draft', stage: 'pending_start' })
const isArchivedStatus = computed(() => progressForm.status === 'archived')
const availableStageOptions = computed(() => {
  if (progressForm.status === 'draft') {
    return draftAllowedStages
  }
  return stageOptions
})

watch(
  () => progressForm.status,
  (value) => {
    if (value === 'draft' && !draftAllowedStages.includes(progressForm.stage)) {
      progressForm.stage = 'pending_start'
    }
  },
)

function centsToYuan(v?: number | null) {
  if (v == null) return '-'
  return (v / 100).toFixed(2)
}

async function load() {
  loading.value = true
  try {
    const { data } = await getProjectDetail(projectId)
    project.value = data.data
    progressForm.status = data.data.status
    progressForm.stage = data.data.stage
  } catch {
    project.value = null
  } finally {
    loading.value = false
  }
}

async function submitProgress() {
  if (!progressForm.status || !progressForm.stage) {
    ElMessage.warning('请选择状态和阶段')
    return
  }
  saving.value = true
  try {
    await ElMessageBox.confirm(
      `确认更新项目状态为 "${progressForm.status}"、阶段为 "${progressForm.stage}"？`,
      '状态变更确认',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
    const current = project.value
    if (!current) {
      ElMessage.error('项目信息不存在')
      return
    }
    if (progressForm.status !== current.status) {
      await updateProjectStatus(projectId, progressForm.status)
    }
    if (progressForm.stage !== current.stage) {
      await updateProjectStage(projectId, progressForm.stage)
    }
    ElMessage.success('状态已更新')
    await load()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  } finally {
    saving.value = false
  }
}

function isStatusDisabled(target: string) {
  const currentStatus = project.value?.status
  if (!currentStatus || target === currentStatus) return false
  return !(statusTransitionMap[currentStatus] || []).includes(target)
}

async function removeCurrentProject() {
  if (!project.value) return
  try {
    await ElMessageBox.confirm(
      `确认删除项目「${project.value.projectName}」？该操作不可撤销。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
    await deleteProject(projectId)
    ElMessage.success('删除成功')
    router.push('/admin/projects')
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  }
}

onMounted(() => {
  if (!hasValidId) {
    ElMessage.error('无效的项目ID')
    return
  }
  load()
})
</script>

