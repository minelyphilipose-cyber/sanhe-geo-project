<template>
  <el-card class="admin-rich-card mobile-share-panel" v-loading="loading">
    <template #header>
      <div class="section-header">
        <div class="panel-title">
          <span>移动看板分享链接</span>
          <el-tag size="small" type="info">{{ shares.length }} 条</el-tag>
        </div>
        <div class="panel-actions">
          <el-button size="small" :loading="loading" @click="loadData">刷新</el-button>
          <el-button v-if="editable" size="small" type="primary" :loading="creating" @click="createShare">
            生成新链接
          </el-button>
        </div>
      </div>
    </template>

    <el-alert
      v-if="createdShareUrl"
      type="success"
      :closable="true"
      class="mb-3"
      @close="createdShareUrl = ''"
    >
      <template #title>
        <div class="created-share">
          <span>新链接仅本次展示：</span>
          <el-input :model-value="createdShareUrl" readonly size="small" />
          <el-button size="small" type="success" plain @click="copyUrl(createdShareUrl)">复制</el-button>
        </div>
      </template>
    </el-alert>

    <el-alert
      type="warning"
      :closable="false"
      class="mb-3"
      title="生成新链接会自动停用同项目旧 active 链接；停用后客户侧立即失效。"
    />

    <el-table :data="shares" border empty-text="暂无分享链接">
      <el-table-column label="Token 前缀" width="128">
        <template #default="{ row }">
          <code>{{ row.tokenPrefix }}</code>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="96">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'">
            {{ row.status === 'active' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="过期时间" min-width="170">
        <template #default="{ row }">{{ formatTime(row.expiresAt) }}</template>
      </el-table-column>
      <el-table-column label="访问摘要" min-width="220">
        <template #default="{ row }">
          <div class="access-summary">
            <span>总 {{ summaryOf(row.id)?.totalAccess || 0 }}</span>
            <span>失败 {{ summaryOf(row.id)?.failedAccess || 0 }}</span>
            <span>IP {{ summaryOf(row.id)?.distinctIpCount || 0 }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="最后访问" min-width="160">
        <template #default="{ row }">{{ formatTime(summaryOf(row.id)?.lastAccessAt || row.lastAccessAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-popconfirm
            v-if="editable && row.status === 'active'"
            title="确认停用这条移动看板链接？"
            confirm-button-text="停用"
            cancel-button-text="取消"
            @confirm="disableShare(row.id)"
          >
            <template #reference>
              <el-button link type="danger" :loading="disablingId === row.id">停用</el-button>
            </template>
          </el-popconfirm>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createMobileDashboardShare,
  disableMobileDashboardShare,
  getMobileDashboardShareAccessSummary,
  getMobileDashboardShares,
} from '@/api/mobileDashboard'
import type { MobileDashboardShare, MobileDashboardShareAccessSummary } from '@/types/mobileDashboard'

const props = defineProps<{
  projectId: number
  editable: boolean
}>()

const loading = ref(false)
const creating = ref(false)
const disablingId = ref<number | null>(null)
const shares = ref<MobileDashboardShare[]>([])
const summaries = ref<MobileDashboardShareAccessSummary[]>([])
const createdShareUrl = ref('')

const summaryMap = computed(() => {
  const map = new Map<number, MobileDashboardShareAccessSummary>()
  for (const item of summaries.value) {
    map.set(item.shareId, item)
  }
  return map
})

function summaryOf(id: number) {
  return summaryMap.value.get(id)
}

function formatTime(value?: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

async function copyUrl(url: string) {
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

async function loadData() {
  loading.value = true
  try {
    const [shareRes, summaryRes] = await Promise.all([
      getMobileDashboardShares(props.projectId),
      getMobileDashboardShareAccessSummary(props.projectId),
    ])
    shares.value = shareRes.data.data || []
    summaries.value = summaryRes.data.data || []
  } catch (error: any) {
    ElMessage.error(error?.message || '分享链接加载失败')
  } finally {
    loading.value = false
  }
}

async function createShare() {
  creating.value = true
  try {
    const { data } = await createMobileDashboardShare(props.projectId)
    createdShareUrl.value = data.data?.shareUrl || ''
    ElMessage.success('分享链接已生成')
    await loadData()
  } catch (error: any) {
    ElMessage.error(error?.message || '生成失败')
  } finally {
    creating.value = false
  }
}

async function disableShare(id: number) {
  disablingId.value = id
  try {
    await disableMobileDashboardShare(id)
    ElMessage.success('链接已停用')
    await loadData()
  } catch (error: any) {
    ElMessage.error(error?.message || '停用失败')
  } finally {
    disablingId.value = null
  }
}

onMounted(loadData)
</script>

<style scoped>
.panel-title,
.panel-actions,
.created-share,
.access-summary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.panel-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.created-share {
  width: 100%;
}

.created-share .el-input {
  max-width: 560px;
}

.access-summary {
  flex-wrap: wrap;
  color: #606266;
  font-size: 12px;
}
</style>
