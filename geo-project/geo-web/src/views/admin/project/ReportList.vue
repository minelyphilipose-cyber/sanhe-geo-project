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
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import { createProjectDashboardShare, disableProjectDashboardShare, getProjectDashboardShares } from '@/api/projectDashboard'
import { getReportList } from '@/api/report'
import { useUserStore } from '@/stores/user'
import type { ProjectDashboardShare, Report } from '@/types'
import { REPORT_STATUS_MAP, REPORT_TYPE_MAP } from '@/utils/constants'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const projectId = Number(route.params.id)

const loading = ref(false)
const shareSubmitting = ref(false)
const shares = ref<ProjectDashboardShare[]>([])
const reports = ref<Report[]>([])

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
  } finally {
    loading.value = false
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

function previewReport(id: number) {
  router.push(`/admin/reports/${id}`)
}

function dashboardUrl(shareCode: string) {
  return `${window.location.origin}/dashboard/${shareCode}`
}

function reportShareUrl(token: string) {
  return `${window.location.origin}/r/${token}`
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

void loadAll()
</script>
