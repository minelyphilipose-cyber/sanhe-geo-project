<template>
  <div class="prompt-list-page">
    <div class="page-header">
      <div>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/admin/presale/report' }">售前报告</el-breadcrumb-item>
          <el-breadcrumb-item>Prompt 调用记录</el-breadcrumb-item>
        </el-breadcrumb>
        <h2 class="page-title">Prompt 调用记录</h2>
      </div>
      <div class="header-actions">
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

    <el-card shadow="never" class="filter-card">
      <el-form :model="filter" inline label-position="top">
        <el-form-item label="平台">
          <el-select v-model="filter.platformCode" placeholder="全部平台" clearable style="width: 160px">
            <el-option
              v-for="item in filterOptions.platforms"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Prompt 类型">
          <el-select v-model="filter.batchNo" placeholder="全部类型" clearable style="width: 160px">
            <el-option label="认知型 Prompt" :value="1" />
            <el-option label="对比型 Prompt" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务分类">
          <el-select v-model="filter.category" placeholder="全部分类" clearable style="width: 160px">
            <el-option
              v-for="item in filterOptions.categories"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部状态" clearable style="width: 160px">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="解析失败" value="ANALYZE_FAILED" />
            <el-option label="调用失败" value="QUERY_FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="filter.keyword"
            placeholder="搜索 Prompt / 响应"
            clearable
            style="width: 220px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label=" ">
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="records" v-loading="loading" stripe style="width: 100%">
        <el-table-column label="Prompt 内容" min-width="280">
          <template #default="{ row }">
            <div class="multi-line">{{ row.requestPromptContent }}</div>
          </template>
        </el-table-column>
        <el-table-column label="大模型响应" min-width="240">
          <template #default="{ row }">
            <div class="response-cell">
              <el-tag size="small" :type="statusTagType(row.traceStatus)">
                {{ row.traceStatusText }}
              </el-tag>
              <span class="brief">{{ row.queryAnswerBrief || '—' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="请求模型" min-width="140">
          <template #default="{ row }">
            <el-tooltip :disabled="!row.queryModelName" :content="row.queryModelName" placement="top">
              <span class="model-name">{{ row.queryModelName || '—' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="解析模型" min-width="140">
          <template #default="{ row }">
            <el-tooltip :disabled="!row.analyzeModelName" :content="row.analyzeModelName" placement="top">
              <span class="model-name">{{ row.analyzeModelName || '—' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="耗用时间" width="120">
          <template #default="{ row }">{{ formatDuration(row.totalDurationMs) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="goDetail(row.promptResultId)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="该版本暂无 Prompt 调用记录" />
        </template>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList"
          @current-change="loadList"
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
const filterOptions = ref<PresalePromptTraceFilterOptionsVO>({
  platforms: [],
  categories: []
})

const filter = reactive<PresalePromptTraceQueryRequest>({
  platformCode: '',
  batchNo: undefined,
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
      batchNo: filter.batchNo,
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
  loadList()
}

function onReset() {
  resetFilter()
  pagination.current = 1
  loadList()
}

function resetFilter() {
  filter.platformCode = ''
  filter.batchNo = undefined
  filter.category = ''
  filter.keyword = ''
  filter.status = undefined
}

function switchVersion(value: number | string) {
  const next = Number(value)
  if (!Number.isFinite(next) || next === versionNo.value) return
  router.push(`/admin/presale/report/${reportId.value}/versions/${next}/prompts`)
}

function goReportDetail() {
  router.push({
    path: `/admin/presale/report/${reportId.value}/detail`,
    query: { versionNo: String(versionNo.value) }
  })
}

function goDetail(promptResultId: number) {
  router.push(`/admin/presale/report/${reportId.value}/versions/${versionNo.value}/prompts/${promptResultId}`)
}

function versionLabel(item: ReportVersionOptionVO) {
  return `v${item.versionNo} · ${item.generationStatusText} · ${formatDateTime(item.createdAt)}`
}

function formatDuration(value: number | null | undefined) {
  if (value == null) return '—'
  if (value < 1000) return `${value}ms`
  return `${(value / 1000).toFixed(1)}s`
}

function statusTagType(status: PresalePromptTraceStatus) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'ANALYZE_FAILED') return 'warning'
  return 'danger'
}

onMounted(async () => {
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
.prompt-list-page {
  padding: 16px 24px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 16px;
}
.page-title {
  margin: 8px 0 0;
  font-size: 22px;
  font-weight: 600;
}
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
.filter-card {
  margin-bottom: 16px;
}
.multi-line {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.45;
}
.response-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.brief {
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
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
