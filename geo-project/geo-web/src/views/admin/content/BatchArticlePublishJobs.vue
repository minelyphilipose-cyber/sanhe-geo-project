<template>
  <div class="batch-publish-jobs-page">
    <el-card shadow="never" class="mb-3">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-select v-model="query.status" clearable placeholder="任务状态" style="width: 180px">
            <el-option label="待执行" value="pending" />
            <el-option label="执行中" value="running" />
            <el-option label="已完成" value="completed" />
            <el-option label="部分失败" value="partial_failed" />
            <el-option label="失败" value="failed" />
          </el-select>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </div>
        <div class="toolbar-right">
          <el-switch v-model="autoRefresh" active-text="自动刷新" />
          <el-button @click="load">刷新</el-button>
          <el-button @click="goBack">返回内容列表</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无批量发布任务">
        <el-table :data="rows" border>
          <el-table-column prop="jobId" label="任务ID" width="100">
            <template #default="{ row }">#{{ row.jobId }}</template>
          </el-table-column>
          <el-table-column label="发布方式" width="120">
            <template #default="{ row }">{{ publishModeLabel(row.publishMode) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ jobStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="进度" min-width="180">
            <template #default="{ row }">
              {{ row.successCount || 0 }} / {{ row.totalCount || 0 }} 成功
              <span v-if="row.failedCount">，{{ row.failedCount }} 失败</span>
            </template>
          </el-table-column>
          <el-table-column label="计划开始" width="170">
            <template #default="{ row }">{{ row.scheduledAt || '-' }}</template>
          </el-table-column>
          <el-table-column label="发布间隔" width="110">
            <template #default="{ row }">{{ row.intervalMinutes }} 分钟</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ row.createdAt || '-' }}</template>
          </el-table-column>
          <el-table-column label="完成时间" width="170">
            <template #default="{ row }">{{ row.finishedAt || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row.jobId)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="page.current"
            :page-size="page.size"
            :total="page.total"
            @current-change="onPageChange"
          />
        </div>
      </DataState>
    </el-card>

    <el-drawer v-model="detailVisible" title="批量发布详情" size="72%">
      <DataState :loading="detailLoading" :empty="!detailLoading && !detail">
        <template v-if="detail">
          <el-descriptions :column="3" border class="mb-3">
            <el-descriptions-item label="任务ID">#{{ detail.jobId }}</el-descriptions-item>
            <el-descriptions-item label="发布方式">{{ publishModeLabel(detail.publishMode) }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ jobStatusLabel(detail.status) }}</el-descriptions-item>
            <el-descriptions-item label="文章总数">{{ detail.totalCount }}</el-descriptions-item>
            <el-descriptions-item label="成功">{{ detail.successCount }}</el-descriptions-item>
            <el-descriptions-item label="失败">{{ detail.failedCount }}</el-descriptions-item>
            <el-descriptions-item label="计划开始">{{ detail.scheduledAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="发布间隔">{{ detail.intervalMinutes }} 分钟</el-descriptions-item>
          </el-descriptions>

          <el-table :data="detail.items" border>
            <el-table-column label="文章ID" width="90">
              <template #default="{ row }">#{{ row.articleId }}</template>
            </el-table-column>
            <el-table-column label="文章标题" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">{{ row.articleTitle || '-' }}</template>
            </el-table-column>
            <el-table-column label="项目" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.projectName || '-' }}</template>
            </el-table-column>
            <el-table-column label="平台" width="120">
              <template #default="{ row }">{{ platformLabel(row.platformKey) }}</template>
            </el-table-column>
            <el-table-column label="计划时间" width="170">
              <template #default="{ row }">{{ row.plannedAt || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="itemTagType(row.status)">{{ itemStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="分发任务" width="110">
              <template #default="{ row }">{{ row.distributionTaskId ? `#${row.distributionTaskId}` : '-' }}</template>
            </el-table-column>
            <el-table-column label="失败原因" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
            </el-table-column>
          </el-table>
        </template>
      </DataState>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import DataState from '@/components/ui/DataState.vue'
import {
  getBatchArticlePublish,
  getBatchArticlePublishJobs,
  type BatchArticlePublishJobSummary,
  type BatchArticlePublishResponse,
} from '@/api/content'

const router = useRouter()
const loading = ref(false)
const detailLoading = ref(false)
const autoRefresh = ref(true)
const rows = ref<BatchArticlePublishJobSummary[]>([])
const detail = ref<BatchArticlePublishResponse | null>(null)
const detailVisible = ref(false)
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive({ status: '' })
let refreshTimer: number | null = null

onMounted(() => {
  load()
  startTimer()
})
onBeforeUnmount(stopTimer)

watch(autoRefresh, (enabled) => {
  if (enabled) startTimer()
  else stopTimer()
})

async function load() {
  loading.value = true
  try {
    const { data } = await getBatchArticlePublishJobs({
      current: page.current,
      size: page.size,
      status: query.status || undefined,
    })
    rows.value = data.data.records || []
    page.total = data.data.total || 0
    if (detailVisible.value && detail.value?.jobId) {
      await loadDetail(detail.value.jobId, false)
    }
  } finally {
    loading.value = false
  }
}

function search() {
  page.current = 1
  load()
}

function resetQuery() {
  query.status = ''
  search()
}

function onPageChange(v: number) {
  page.current = v
  load()
}

async function openDetail(jobId: number) {
  detailVisible.value = true
  await loadDetail(jobId, true)
}

async function loadDetail(jobId: number, showLoading: boolean) {
  if (showLoading) detailLoading.value = true
  try {
    const { data } = await getBatchArticlePublish(jobId)
    detail.value = data.data
  } finally {
    if (showLoading) detailLoading.value = false
  }
}

function startTimer() {
  stopTimer()
  refreshTimer = window.setInterval(() => {
    if (autoRefresh.value) load()
  }, 15000)
}

function stopTimer() {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function publishModeLabel(v: string) {
  return v === 'scheduled' ? '定时发布' : '立刻发布'
}

function jobStatusLabel(v: string) {
  const map: Record<string, string> = {
    pending: '待执行',
    running: '执行中',
    completed: '已完成',
    partial_failed: '部分失败',
    failed: '失败',
  }
  return map[v] || v
}

function itemStatusLabel(v: string) {
  const map: Record<string, string> = {
    pending: '待执行',
    running: '执行中',
    success: '成功',
    failed: '失败',
  }
  return map[v] || v
}

function statusTagType(v: string): 'success' | 'warning' | 'danger' | 'info' {
  if (v === 'completed') return 'success'
  if (v === 'failed' || v === 'partial_failed') return 'danger'
  if (v === 'running') return 'warning'
  return 'info'
}

function itemTagType(v: string): 'success' | 'warning' | 'danger' | 'info' {
  if (v === 'success') return 'success'
  if (v === 'failed') return 'danger'
  if (v === 'running') return 'warning'
  return 'info'
}

function platformLabel(v: string) {
  if (v === 'agent_site') return 'Agent 官网'
  if (v === 'industry_site') return '行业资讯站'
  return v || '-'
}

function goBack() {
  router.push('/admin/content/execution')
}
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.mb-3 {
  margin-bottom: 16px;
}

@media (max-width: 768px) {
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-left,
  .toolbar-right {
    flex-wrap: wrap;
  }
}
</style>
