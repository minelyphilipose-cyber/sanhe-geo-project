<template>
  <div class="prompt-list-page admin-page">
    <div class="page-header admin-page-header">
      <div>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/admin/presale/report' }">AI可见度诊断报告</el-breadcrumb-item>
          <el-breadcrumb-item>Prompt 调用记录</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="admin-page-kicker">调用链路</div>
        <h2 class="page-title admin-page-title">Prompt 调用记录</h2>
        <div class="admin-page-subtitle">按平台、业务分类和执行状态追踪每次模型调用。</div>
      </div>
      <div class="header-actions admin-page-actions">
        <el-select
          :model-value="versionNo"
          class="version-select"
          size="default"
          @change="switchVersion"
        >
          <el-option
            v-for="item in versions"
            :key="item.versionId"
            :label="versionLabel(item)"
            :value="item.versionNo"
            :disabled="item.disabled"
          >
            <el-tooltip
              :disabled="!item.disabled"
              :content="item.disabledReason || '该版本无 Prompt 数据'"
              placement="right"
            >
              <div class="version-option" :class="{ selected: item.versionNo === versionNo }">
                <span>{{ item.versionNo === versionNo ? '✓ ' : '' }}{{ versionLabel(item) }}</span>
              </div>
            </el-tooltip>
          </el-option>
        </el-select>
        <el-button @click="goReportDetail">返回报告详情</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card admin-surface">
      <el-form :model="filter" class="filter-form" label-position="top">
        <el-form-item label="平台">
          <el-select v-model="filter.platformCode" placeholder="全部平台" clearable>
            <el-option
              v-for="item in filterOptions.platforms"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="业务分类">
          <el-select v-model="filter.category" placeholder="全部分类" clearable>
            <el-option
              v-for="item in filterOptions.categories"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部状态" clearable>
            <el-option label="成功" value="SUCCESS" />
            <el-option label="解析失败" value="ANALYZE_FAILED" />
            <el-option label="调用失败" value="QUERY_FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题内容">
          <el-input
            v-model="filter.keyword"
            placeholder="搜索问题内容"
            clearable
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label=" ">
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="admin-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">调用总数</span>
        <strong class="admin-metric-value">{{ pagination.total }}</strong>
        <span class="admin-metric-hint">当前筛选结果</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">成功</span>
        <strong class="admin-metric-value">{{ successCount }}</strong>
        <span class="admin-metric-hint">本页可见</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">解析失败</span>
        <strong class="admin-metric-value">{{ analyzeFailedCount }}</strong>
        <span class="admin-metric-hint">需关注</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #ef4444; --metric-tone: #fef2f2">
        <span class="admin-metric-label">调用失败</span>
        <strong class="admin-metric-value">{{ queryFailedCount }}</strong>
        <span class="admin-metric-hint">需排查</span>
      </div>
    </div>

    <el-card shadow="never" class="table-card admin-table-card">
      <el-table
        class="prompt-record-table"
        :data="records"
        v-loading="loading"
        border
        table-layout="fixed"
        highlight-current-row
        style="width: 100%"
      >
        <el-table-column label="Prompt 对象" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="admin-entity-cell">
              <div class="admin-entity-avatar is-violet">{{ promptInitial(row.category) }}</div>
              <div class="min-w-0">
                <div class="admin-entity-main">{{ row.category || '未分类 Prompt' }}</div>
                <div class="admin-entity-sub">{{ row.platformName || '未知平台' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="内容摘要" min-width="360" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="admin-cell-stack">
              <span class="admin-cell-main">{{ row.requestPromptContent || '—' }}</span>
              <span class="admin-cell-sub">响应：{{ row.queryAnswerBrief || '—' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="模型/耗时" width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="admin-cell-stack">
              <span class="admin-cell-main">{{ row.queryModelName || '—' }}</span>
              <span class="admin-cell-sub">解析 {{ row.analyzeModelName || '—' }} · {{ formatDuration(row.totalDurationMs) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="admin-status-tag" :class="promptStatusClass(row.traceStatus)">
              {{ row.traceStatusText }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <div class="admin-row-actions">
              <el-button link type="primary" size="small" @click="goDetail(row.promptResultId)">
                查看详情
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="该版本暂无 Prompt 调用记录" />
        </template>
      </el-table>

      <div class="pagination-wrapper admin-table-footer">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="onPageSizeChange"
          @current-change="onCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import {
  listReportPromptTraces,
  listReportVersions,
  type PresalePromptTraceFilterOptionsVO,
  type PresalePromptTraceListItemVO,
  type PresalePromptTraceQueryRequest,
  type PresalePromptTraceStatus,
  type ReportVersionOptionVO
} from '@/api/presaleReport'
import { formatDateTime } from '@/utils/presale/formatDateTime'

const route = useRoute()
const router = useRouter()
const reportId = computed(() => Number(route.params.id))
const versionNo = computed(() => Number(route.params.versionNo))

const loading = ref(false)
const versions = ref<ReportVersionOptionVO[]>([])
const records = ref<PresalePromptTraceListItemVO[]>([])
const successCount = computed(() => records.value.filter((row) => row.traceStatus === 'SUCCESS').length)
const analyzeFailedCount = computed(() => records.value.filter((row) => row.traceStatus === 'ANALYZE_FAILED').length)
const queryFailedCount = computed(() => records.value.filter((row) => row.traceStatus === 'QUERY_FAILED').length)
const filterOptions = ref<PresalePromptTraceFilterOptionsVO>({
  platforms: [],
  categories: []
})

const filter = reactive<PresalePromptTraceQueryRequest>({
  platformCode: '',
  category: '',
  keyword: '',
  status: undefined
})

const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

async function loadVersions() {
  versions.value = await listReportVersions(reportId.value)
}

async function loadList() {
  loading.value = true
  try {
    const res = await listReportPromptTraces(reportId.value, versionNo.value, {
      current: pagination.current,
      size: pagination.size,
      platformCode: filter.platformCode || undefined,
      category: filter.category || undefined,
      keyword: filter.keyword || undefined,
      status: filter.status || undefined
    })
    records.value = res.page.records
    pagination.total = res.page.total
    filterOptions.value = res.filterOptions
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pagination.current = 1
  syncQueryAndLoad()
}

function onReset() {
  resetFilter()
  pagination.current = 1
  syncQueryAndLoad()
}

function onPageSizeChange() {
  pagination.current = 1
  syncQueryAndLoad()
}

function onCurrentChange() {
  syncQueryAndLoad()
}

function resetFilter() {
  filter.platformCode = ''
  filter.category = ''
  filter.keyword = ''
  filter.status = undefined
}

function switchVersion(value: number | string) {
  const next = Number(value)
  if (!Number.isFinite(next) || next === versionNo.value) return
  router.push({
    path: `/admin/presale/report/${reportId.value}/versions/${next}/prompts`,
    query: buildRouteQuery()
  })
}

function goReportDetail() {
  router.push({
    path: `/admin/presale/report/${reportId.value}/detail`,
    query: { versionNo: String(versionNo.value) }
  })
}

function goDetail(promptResultId: number) {
  router.push({
    path: `/admin/presale/report/${reportId.value}/versions/${versionNo.value}/prompts/${promptResultId}`,
    query: buildRouteQuery()
  })
}

function versionLabel(item: ReportVersionOptionVO) {
  return `v${item.versionNo} · ${item.generationStatusText} · ${formatDateTime(item.createdAt)}`
}

function formatDuration(value: number | null | undefined) {
  if (value == null) return '—'
  if (value < 1000) return `${value}ms`
  return `${(value / 1000).toFixed(1)}s`
}

function promptInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : 'P'
}

function promptStatusClass(status: PresalePromptTraceStatus) {
  if (status === 'SUCCESS') return 'is-success'
  if (status === 'ANALYZE_FAILED') return 'is-warning'
  return 'is-danger'
}

async function syncQueryAndLoad() {
  await router.replace({
    path: route.path,
    query: buildRouteQuery()
  })
  await loadList()
}

function buildRouteQuery() {
  return {
    platformCode: filter.platformCode || undefined,
    category: filter.category || undefined,
    keyword: filter.keyword || undefined,
    status: filter.status || undefined,
    current: pagination.current > 1 ? String(pagination.current) : undefined,
    size: pagination.size !== 20 ? String(pagination.size) : undefined
  }
}

function restoreStateFromQuery() {
  filter.platformCode = queryString('platformCode')
  filter.category = queryString('category')
  filter.keyword = queryString('keyword')
  filter.status = queryStatus()
  pagination.current = queryPositiveInt('current', 1)
  pagination.size = queryPageSize()
}

function queryString(key: string) {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

function queryStatus(): PresalePromptTraceStatus | undefined {
  const value = queryString('status')
  return value === 'SUCCESS' || value === 'ANALYZE_FAILED' || value === 'QUERY_FAILED'
    ? value
    : undefined
}

function queryPositiveInt(key: string, fallback: number) {
  const value = Number(queryString(key))
  return Number.isInteger(value) && value > 0 ? value : fallback
}

function queryPageSize() {
  const value = queryPositiveInt('size', 20)
  return [20, 50, 100].includes(value) ? value : 20
}

onMounted(async () => {
  restoreStateFromQuery()
  await loadVersions()
  await loadList()
})

watch([reportId, versionNo], async ([newReportId, newVersionNo], [oldReportId, oldVersionNo]) => {
  const changed = newReportId !== oldReportId || newVersionNo !== oldVersionNo
  if (changed) {
    resetFilter()
    pagination.current = 1
  }
  if (newReportId !== oldReportId) {
    await loadVersions()
  }
  if (changed) {
    await loadList()
  }
})
</script>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.version-select {
  width: 300px;
}
.version-option {
  width: 100%;
}
.version-option.selected {
  font-weight: 600;
}
.filter-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(150px, 1fr)) minmax(220px, 1.35fr) auto;
  gap: 20px 24px;
  align-items: end;
}
.filter-form :deep(.el-form-item) {
  min-width: 0;
  margin-right: 0;
  margin-bottom: 0;
}
.filter-form :deep(.el-form-item__label) {
  padding-bottom: 8px;
  color: #606266;
  line-height: 1.2;
}
.filter-form :deep(.el-input),
.filter-form :deep(.el-select) {
  width: 100%;
}
.multi-line {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.45;
}
.brief {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.model-name {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1440px) {
  .filter-form {
    grid-template-columns: repeat(3, minmax(150px, 1fr));
  }
}

@media (max-width: 960px) {
  .header-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .version-select {
    width: 100%;
  }

  .filter-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .filter-form {
    grid-template-columns: 1fr;
  }
}
</style>
