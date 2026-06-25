<template>
  <section class="mobile-share-panel" v-loading="loading">
    <div class="section-header">
      <div class="panel-title">
        <div class="panel-title-icon">
          <el-icon><Link /></el-icon>
        </div>
        <div>
          <h2>移动看板分享链接</h2>
          <p>用于客户在微信内访问移动 H5 数据看板，泄露时可立即停用或删除无效链接。</p>
        </div>
      </div>
      <el-tag round type="info">{{ shares.length }} 条</el-tag>
    </div>

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

    <div class="table-toolbar">
      <div>
        <h3>链接列表</h3>
        <p>仅 active 链接可访问；停用或过期链接可删除。</p>
      </div>
      <div class="panel-actions">
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button v-if="editable" type="primary" :loading="creating" @click="createShare">
          <el-icon><Plus /></el-icon>
          生成新链接
        </el-button>
      </div>
    </div>

    <el-table :data="shares" class="share-table" border empty-text="暂无分享链接">
      <el-table-column label="Token 前缀" width="128">
        <template #default="{ row }">
          <code class="token-prefix">{{ row.tokenPrefix }}</code>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="96">
        <template #default="{ row }">
          <el-tag round :type="row.status === 'active' ? 'success' : 'info'">
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
          <div class="row-actions">
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
            <el-popconfirm
              v-else-if="editable"
              title="确认删除这条无效链接？删除后不可恢复。"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="deleteShare(row.id)"
            >
              <template #reference>
                <el-button link type="danger" :loading="deletingId === row.id">删除</el-button>
              </template>
            </el-popconfirm>
            <span v-else>-</span>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Link, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  createMobileDashboardShare,
  deleteMobileDashboardShare,
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
const deletingId = ref<number | null>(null)
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

async function deleteShare(id: number) {
  deletingId.value = id
  try {
    await deleteMobileDashboardShare(id)
    ElMessage.success('无效链接已删除')
    await loadData()
  } catch (error: any) {
    ElMessage.error(error?.message || '删除失败')
  } finally {
    deletingId.value = null
  }
}

onMounted(loadData)
</script>

<style scoped>
.mobile-share-panel {
  padding: 18px 20px 20px;
  border: 1px solid #e5eef7;
  border-radius: 18px;
  background:
    linear-gradient(120deg, rgba(7, 166, 107, 0.08), rgba(255, 255, 255, 0) 32%),
    #fff;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.07);
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.panel-title,
.panel-actions,
.created-share,
.access-summary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.panel-title {
  align-items: flex-start;
}

.panel-title-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: #e6f7ef;
  color: #07a66b;
  font-size: 18px;
}

.panel-title h2 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
  line-height: 1.3;
}

.panel-title p,
.table-toolbar p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.panel-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 14px 0 12px;
}

.table-toolbar h3 {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
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

.token-prefix {
  padding: 3px 7px;
  border-radius: 8px;
  background: #f8fafc;
  color: #334155;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}

.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.share-table {
  border-radius: 12px;
  overflow: hidden;
}

.share-table :deep(.el-table__header th) {
  background: #f8fbff;
  color: #334155;
  font-weight: 800;
}

.share-table :deep(.el-table__row) {
  height: 58px;
}

@media (max-width: 720px) {
  .section-header,
  .table-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .panel-actions {
    justify-content: flex-start;
  }
}
</style>
