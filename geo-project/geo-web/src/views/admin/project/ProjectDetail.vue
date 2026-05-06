<template>
  <div class="space-y-4">
    <el-page-header content="项目详情" @back="$router.back()" />

    <el-card v-loading="loading">
      <template #header>
        <div class="flex items-center justify-between">
          <span>基础信息</span>
          <div class="space-x-2">
            <el-button size="small" @click="goReports">项目报表</el-button>
            <el-tag>{{ dictStore.label('project_status', project?.status) }}</el-tag>
            <el-tag type="info">{{ dictStore.label('project_stage', project?.stage) }}</el-tag>
          </div>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="项目编码">{{ project?.projectCode }}</el-descriptions-item>
        <el-descriptions-item label="项目名称">{{ project?.projectName }}</el-descriptions-item>
        <el-descriptions-item label="项目别名">{{ project?.projectAliases || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ project?.companyName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="品牌名称">{{ project?.brandName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="拓词组">{{ `已选 ${project?.selectedKeywordGroupCount || 0} 个，已入库 ${project?.selectedKeywordSavedKeywords || 0} 条关键词` }}</el-descriptions-item>
        <el-descriptions-item label="归属类型">{{ dictStore.label('owner_type', project?.ownerType) }}</el-descriptions-item>
        <el-descriptions-item label="合伙人">{{ project?.ownerType === 'direct' ? '-' : '已绑定' }}</el-descriptions-item>
        <el-descriptions-item label="所在地区">{{ regionText(project) }}</el-descriptions-item>
        <el-descriptions-item label="交付模式">{{ project?.deliveryMode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="启动日期">{{ project?.activatedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="签约扣款(元)">{{ centsToYuan(project?.deductionAmount) }}</el-descriptions-item>
        <el-descriptions-item label="折扣快照">{{ project?.discountRateSnapshot != null ? (project.discountRateSnapshot * 100).toFixed(2) + '%' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="扣款流水号">{{ project?.deductionTxnNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主目标" :span="3">{{ project?.primaryGoal || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="project">
      <template #header><span>内容策略配置</span></template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="目标区域词">{{ joinArray(project.targetRegions) }}</el-descriptions-item>
        <el-descriptions-item label="目标受众">{{ project.targetAudience || '-' }}</el-descriptions-item>
        <el-descriptions-item label="内容调性">{{ project.contentTone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="优先写作角度">{{ joinArray(project.preferredAngles) }}</el-descriptions-item>
        <el-descriptions-item label="项目定制表述" :span="2">{{ project.customStatement || '-' }}</el-descriptions-item>
        <el-descriptions-item label="补充禁用词" :span="2">{{ joinArray(project.extraForbiddenPhrases) }}</el-descriptions-item>
        <el-descriptions-item label="内容备注" :span="2">{{ project.contentNote || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="showActivationGuide">
      <template #header><span>项目启动</span></template>
      <el-form label-width="120px" style="max-width: 540px">
        <el-form-item label="启动前确认">
          <el-checkbox v-model="activationConfirmed">我已阅读并确认项目基础信息</el-checkbox>
        </el-form-item>
        <el-form-item v-if="canActivateProject">
          <el-button type="primary" :loading="saving" :disabled="!activationConfirmed" @click="startProject">启动项目</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import { deleteProject, getProjectDetail, updateProjectStatus } from '@/api/project'
import type { Project } from '@/types'
import { regionDisplayFromPayload } from '@/constants/region'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const dictStore = useDictStore()
const canActivateProject = computed(() => userStore.hasPermission('project.start'))
const projectId = Number(route.params.id)
const hasValidId = Number.isFinite(projectId) && projectId > 0

const loading = ref(false)
const saving = ref(false)
const project = ref<Project | null>(null)

const activationConfirmed = ref(false)
const showActivationGuide = computed(() => route.query.activate === '1' && project.value?.status === 'paused')

function centsToYuan(v?: number | null) {
  if (v == null) return '-'
  return Number(v).toFixed(2)
}

function regionText(p?: Project | null) {
  if (!p) return '-'
  return regionDisplayFromPayload(p) || '-'
}

function joinArray(value?: string | string[] | null) {
  if (Array.isArray(value)) {
    return value.length ? value.join('、') : '-'
  }
  if (!value) {
    return '-'
  }
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) && parsed.length ? parsed.join('、') : '-'
  } catch {
    return value
  }
}

function goReports() {
  router.push(`/admin/projects/${projectId}/reports`)
}

async function load() {
  loading.value = true
  try {
    const { data } = await getProjectDetail(projectId)
    project.value = data.data
    activationConfirmed.value = false
  } catch {
    project.value = null
  } finally {
    loading.value = false
  }
}

async function startProject() {
  if (!canActivateProject.value) {
    ElMessage.warning('当前账号无项目启动权限')
    return
  }
  saving.value = true
  try {
    await ElMessageBox.confirm(
      '确认启动该项目？',
      '项目启动确认',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
    const current = project.value
    if (!current) {
      ElMessage.error('项目信息不存在')
      return
    }
    if (current.status !== 'paused') {
      ElMessage.info('当前项目已启动')
      return
    }
    if (!activationConfirmed.value) {
      ElMessage.warning('请先勾选“已阅读并确认项目基础信息”后再激活')
      return
    }
    await updateProjectStatus(projectId, 'active')
    ElMessage.success('项目已启动')
    await load()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  } finally {
    saving.value = false
  }
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
    ElMessage.error('项目参数无效')
    return
  }
  dictStore.ensureLoaded()
  load()
})
</script>

