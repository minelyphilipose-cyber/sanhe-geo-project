<template>
  <div class="space-y-4">
    <el-page-header content="项目报表" @back="$router.back()" />

    <el-card>
      <div class="flex items-center justify-between gap-3">
        <div class="text-sm text-gray-500">项目 ID: {{ projectId }}</div>
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </el-card>

    <el-card>
      <div class="space-y-4">
        <div class="flex items-center justify-between gap-3 flex-wrap">
          <div class="text-sm text-gray-500">
            同一项目同一时间仅保留一个有效看板分享链接，重新生成会自动使旧链接失效。
          </div>
          <el-button
            v-if="canManageDashboard"
            type="primary"
            :loading="shareSubmitting"
            @click="createOrRegenerateShare"
          >
            {{ activeShare ? '重新生成分享链接' : '生成分享链接' }}
          </el-button>
          <el-button
            v-if="canManageDashboard && activeShare"
            :disabled="refreshCooldown > 0"
            :loading="refreshSubmitting"
            @click="refreshDashboardSnapshot"
          >
            {{ refreshCooldown > 0 ? `刷新冷却 ${refreshCooldown}s` : '刷新看板数据' }}
          </el-button>
        </div>

        <el-alert
          v-if="activeShare"
          type="success"
          :closable="false"
          show-icon
          title="当前存在有效的客户访问链接"
        />

        <el-descriptions v-if="activeShare" :column="1" border>
          <el-descriptions-item label="访问链接">
            <div class="flex items-center gap-2 flex-wrap">
              <span>{{ dashboardUrl(activeShare.shareCode) }}</span>
              <el-button link type="primary" @click="copyDashboardUrl(activeShare.shareCode)">复制链接</el-button>
              <el-button
                v-if="canManageDashboard"
                link
                type="danger"
                :loading="shareSubmitting"
                @click="disableShare(activeShare.id)"
              >
                停用
              </el-button>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ activeShare.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="看板数据更新时间">{{ dashboardRefreshedAt || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-empty v-else description="当前还没有有效的统计看板分享链接" />

        <el-divider content-position="left">历史链接</el-divider>
        <DataState :loading="loading" :empty="!loading && shares.length === 0" empty-text="暂无历史链接">
          <el-table :data="shares" border>
            <el-table-column prop="shareCode" label="分享码" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag :type="scope.row.status === 'active' ? 'success' : 'info'">
                  {{ scope.row.status === 'active' ? '有效' : '已停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="180" />
            <el-table-column prop="disabledAt" label="停用时间" width="180" />
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="scope">
                <el-button
                  v-if="scope.row.status === 'active'"
                  link
                  type="primary"
                  @click="copyDashboardUrl(scope.row.shareCode)"
                >
                  复制链接
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </DataState>
      </div>
    </el-card>

    <el-card v-if="canManageDashboard">
      <div class="space-y-4">
        <div class="flex items-center justify-between gap-3 flex-wrap">
          <div>
            <div class="font-medium">服务观察与下阶段动作</div>
            <div class="text-sm text-gray-500">保存为草稿后仅内部可见，发布后展示到客户售后看板。</div>
          </div>
          <div class="flex items-center gap-2">
            <el-tag :type="adviceStatus === 'published' ? 'success' : 'info'">
              {{ adviceStatus === 'published' ? '已发布' : '草稿' }}
            </el-tag>
            <span class="text-xs text-gray-400">{{ adviceUpdatedAt || '-' }}</span>
          </div>
        </div>

        <el-form label-position="top">
          <el-form-item label="服务概述">
            <el-input
              v-model="adviceForm.summary"
              type="textarea"
              :rows="3"
              maxlength="2000"
              show-word-limit
              placeholder="概括当前售后服务进展、已产生的价值和整体判断。"
            />
          </el-form-item>
          <el-form-item label="服务亮点">
            <el-input
              v-model="adviceForm.highlightsText"
              type="textarea"
              :rows="4"
              placeholder="每行一条，例如：核心品牌词在豆包、通义等平台保持稳定命中。"
            />
            <div class="text-xs text-gray-400 mt-1">最多保留 8 条，超过部分保存时会自动忽略。</div>
          </el-form-item>
          <el-form-item label="待加强方向">
            <el-input
              v-model="adviceForm.improvementDirectionsText"
              type="textarea"
              :rows="4"
              placeholder="每行一条，使用客户可读的中性表达，不写内部风险判断。"
            />
            <div class="text-xs text-gray-400 mt-1">最多保留 8 条，超过部分保存时会自动忽略。</div>
          </el-form-item>
          <el-form-item label="下阶段动作">
            <el-input
              v-model="adviceForm.nextActionsText"
              type="textarea"
              :rows="4"
              placeholder="每行一条，例如：补充长尾问题覆盖，优先优化官网联系方式曝光。"
            />
            <div class="text-xs text-gray-400 mt-1">最多保留 8 条，超过部分保存时会自动忽略。</div>
          </el-form-item>
        </el-form>

        <div class="flex items-center justify-end gap-2">
          <el-button :loading="adviceSubmitting" @click="saveAdviceDraft">保存草稿</el-button>
          <el-button type="primary" :loading="adviceSubmitting" @click="publishAdvice">发布到客户看板</el-button>
        </div>
      </div>
    </el-card>

    <el-card>
      <DataState :loading="loading" :empty="!loading && reports.length === 0" empty-text="暂无报表记录">
        <el-table :data="reports" border>
          <el-table-column prop="id" label="报表 ID" width="90" />
          <el-table-column label="类型" width="160">
            <template #default="scope">{{ reportTypeLabel(scope.row.reportType) }}</template>
          </el-table-column>
          <el-table-column label="可见性" width="90">
            <template #default="scope">{{ scope.row.visibility === 'internal' ? '内部' : '客户' }}</template>
          </el-table-column>
          <el-table-column prop="versionNo" label="版本" width="80" />
          <el-table-column label="状态" width="120">
            <template #default="scope">{{ reportStatusLabel(scope.row.status) }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
          <el-table-column prop="publishedAt" label="发布时间" width="180" />
          <el-table-column label="分享链接" min-width="300">
            <template #default="scope">
              <span v-if="canShareReport(scope.row)">{{ reportShareUrl(scope.row.shareToken!) }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="previewReport(scope.row.id)">预览</el-button>
              <el-button
                v-if="canShareReport(scope.row)"
                link
                type="success"
                @click="copyReportUrl(scope.row.shareToken!)"
              >
                复制链接
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import {
  createProjectDashboardShare,
  disableProjectDashboardShare,
  getProjectDashboardAdvice,
  getProjectDashboardSnapshotStatus,
  getProjectDashboardShares,
  publishProjectDashboardAdvice,
  refreshProjectDashboardSnapshot,
  saveProjectDashboardAdvice,
} from '@/api/projectDashboard'
import { getReportList } from '@/api/report'
import { useUserStore } from '@/stores/user'
import type { ProjectDashboardAdvice, ProjectDashboardShare, Report } from '@/types'
import { REPORT_STATUS_MAP, REPORT_TYPE_MAP } from '@/utils/constants'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const projectId = Number(route.params.id)

const loading = ref(false)
const shareSubmitting = ref(false)
const refreshSubmitting = ref(false)
const adviceSubmitting = ref(false)
const refreshCooldown = ref(0)
const dashboardRefreshedAt = ref('')
const adviceStatus = ref('draft')
const adviceUpdatedAt = ref('')
const shares = ref<ProjectDashboardShare[]>([])
const reports = ref<Report[]>([])
let refreshCooldownTimer: ReturnType<typeof window.setInterval> | null = null
const MAX_ADVICE_ITEMS = 8

const adviceForm = ref({
  summary: '',
  highlightsText: '',
  improvementDirectionsText: '',
  nextActionsText: '',
})

const canManageDashboard = computed(() => userStore.hasPermission('project.report.export'))
const activeShare = computed(() => shares.value.find((item) => item.status === 'active') || null)

async function loadAll() {
  loading.value = true
  try {
    const [shareResp, reportResp] = await Promise.all([
      getProjectDashboardShares(projectId),
      getReportList({ current: 1, size: 100, projectId }),
    ])
    shares.value = shareResp.data.data || []
    reports.value = reportResp.data.data.records || []
    await loadDashboardSummary()
    if (canManageDashboard.value) {
      await loadDashboardAdvice()
    }
  } finally {
    loading.value = false
  }
}

async function loadDashboardSummary() {
  const active = activeShare.value
  if (!active) {
    dashboardRefreshedAt.value = ''
    return
  }
  try {
    const { data } = await getProjectDashboardSnapshotStatus(projectId)
    dashboardRefreshedAt.value = data.data?.refreshedAt || ''
  } catch {
    dashboardRefreshedAt.value = ''
  }
}

async function loadDashboardAdvice() {
  try {
    const { data } = await getProjectDashboardAdvice(projectId)
    fillAdviceForm(data.data || null)
  } catch {
    fillAdviceForm(null)
  }
}

function fillAdviceForm(advice: ProjectDashboardAdvice | null) {
  adviceForm.value = {
    summary: advice?.summary || '',
    highlightsText: joinLines(advice?.highlights),
    improvementDirectionsText: joinLines(advice?.improvementDirections),
    nextActionsText: joinLines(advice?.nextActions),
  }
  adviceStatus.value = advice?.status || 'draft'
  adviceUpdatedAt.value = advice?.updatedAt || ''
}

function buildAdvicePayload(): ProjectDashboardAdvice {
  return {
    summary: adviceForm.value.summary,
    highlights: splitLines(adviceForm.value.highlightsText),
    improvementDirections: splitLines(adviceForm.value.improvementDirectionsText),
    nextActions: splitLines(adviceForm.value.nextActionsText),
  }
}

async function saveAdviceDraft() {
  adviceSubmitting.value = true
  try {
    const { data } = await saveProjectDashboardAdvice(projectId, buildAdvicePayload())
    fillAdviceForm(data.data || null)
    ElMessage.success('服务观察草稿已保存')
  } finally {
    adviceSubmitting.value = false
  }
}

async function publishAdvice() {
  try {
    await ElMessageBox.confirm('发布后客户将在售后看板中看到这些内容，请确认文案均为客户可见表达。', '发布确认', {
      type: 'warning',
      confirmButtonText: '发布',
      cancelButtonText: '取消',
    })
    adviceSubmitting.value = true
    const { data } = await publishProjectDashboardAdvice(projectId, buildAdvicePayload())
    fillAdviceForm(data.data || null)
    ElMessage.success('服务观察已发布到客户看板')
  } catch (err: any) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err?.message || '发布失败')
    }
  } finally {
    adviceSubmitting.value = false
  }
}

async function createOrRegenerateShare() {
  shareSubmitting.value = true
  try {
    await createProjectDashboardShare(projectId)
    ElMessage.success(activeShare.value ? '分享链接已重新生成，旧链接已失效' : '分享链接已生成')
    await loadAll()
  } finally {
    shareSubmitting.value = false
  }
}

async function disableShare(id: number) {
  try {
    await ElMessageBox.confirm('停用后客户将无法继续访问该统计看板链接。', '停用链接确认', {
      type: 'warning',
      confirmButtonText: '停用',
      cancelButtonText: '取消',
    })
    shareSubmitting.value = true
    await disableProjectDashboardShare(id)
    ElMessage.success('分享链接已停用')
    await loadAll()
  } catch (err: any) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err?.message || '停用失败')
    }
  } finally {
    shareSubmitting.value = false
  }
}

async function refreshDashboardSnapshot() {
  if (refreshCooldown.value > 0) return
  refreshSubmitting.value = true
  try {
    const { data } = await refreshProjectDashboardSnapshot(projectId)
    const result = data.data
    if (result?.status === 'RUNNING') {
      ElMessage.info(result.startedAt ? `看板数据刷新进行中，开始时间：${result.startedAt}` : '看板数据刷新进行中，请稍候')
    } else {
      dashboardRefreshedAt.value = result?.refreshedAt || ''
      ElMessage.success('看板数据已刷新')
      await loadAll()
    }
    startRefreshCooldown()
  } catch {
    ElMessage.error('看板数据刷新失败，请稍后重试')
    startRefreshCooldown()
  } finally {
    refreshSubmitting.value = false
  }
}

function startRefreshCooldown() {
  refreshCooldown.value = 30
  if (refreshCooldownTimer) {
    window.clearInterval(refreshCooldownTimer)
  }
  refreshCooldownTimer = window.setInterval(() => {
    refreshCooldown.value -= 1
    if (refreshCooldown.value <= 0 && refreshCooldownTimer) {
      window.clearInterval(refreshCooldownTimer)
      refreshCooldownTimer = null
    }
  }, 1000)
}

function previewReport(id: number) {
  router.push(`/admin/reports/${id}`)
}

function dashboardUrl(shareCode: string) {
  return `${window.location.origin}/dashboard/${shareCode}`
}

function reportShareUrl(token: string) {
  return `${window.location.origin}/r/${token}`
}

function splitLines(value: string) {
  return value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, MAX_ADVICE_ITEMS)
}

function joinLines(values?: string[]) {
  return (values || []).join('\n')
}

async function copyDashboardUrl(shareCode: string) {
  await navigator.clipboard.writeText(dashboardUrl(shareCode))
  ElMessage.success('看板链接已复制')
}

async function copyReportUrl(token: string) {
  await navigator.clipboard.writeText(reportShareUrl(token))
  ElMessage.success('报告链接已复制')
}

function canShareReport(row: Report) {
  return row.visibility === 'client' && row.status === 'published' && !!row.shareToken
}

function reportTypeLabel(v?: string) {
  return REPORT_TYPE_MAP[v as keyof typeof REPORT_TYPE_MAP]?.label || v || '-'
}

function reportStatusLabel(v?: string) {
  return REPORT_STATUS_MAP[v as keyof typeof REPORT_STATUS_MAP]?.label || v || '-'
}

onUnmounted(() => {
  if (refreshCooldownTimer) {
    window.clearInterval(refreshCooldownTimer)
  }
})

void loadAll()
</script>
