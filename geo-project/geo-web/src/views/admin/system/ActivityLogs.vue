<template>
  <div>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <el-select v-model="query.action" clearable placeholder="操作类型" style="width: 220px" @change="onSearch">
          <el-option
            v-for="item in dictStore.options('activity_action')"
            :key="item.dictKey"
            :label="item.dictValue"
            :value="item.dictKey"
          />
        </el-select>
        <el-select v-model="query.targetType" clearable placeholder="目标类型" style="width: 140px" @change="onSearch">
          <el-option label="客户" value="company" />
          <el-option label="品牌" value="brand" />
          <el-option label="项目" value="project" />
        </el-select>
        <el-date-picker
          v-model="query.dateRange"
          type="datetimerange"
          range-separator="to"
          start-placeholder="Start Time"
          end-placeholder="End Time"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 360px"
          @change="onSearch"
        />
        <el-input v-model.number="query.targetId" clearable placeholder="目标编号" style="width: 120px" @keyup.enter="onSearch" />
        <el-button @click="onSearch">查询</el-button>
      </div>
    </div>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无操作日志">
        <el-table :data="rows" border>
          <el-table-column label="时间" min-width="160">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="operatorName" label="操作人" width="140" />
          <el-table-column label="操作" min-width="180">
            <template #default="{ row }">{{ dictStore.label('activity_action', row.action) }}</template>
          </el-table-column>
          <el-table-column label="目标类型" width="120">
            <template #default="{ row }">{{ targetTypeLabel(row.targetType) }}</template>
          </el-table-column>
          <el-table-column label="摘要" min-width="260">
            <template #default="{ row }">{{ summaryText(row.detailJson) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="mt-4 flex justify-end">
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

    <el-dialog v-model="detailVisible" title="日志详情" width="760px">
      <pre class="detail-json">{{ selectedDetail }}</pre>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { ActivityLog } from '@/types'
import { getActivityLogs } from '@/api/system'
import { useDictStore } from '@/stores/dict'
import DataState from '@/components/ui/DataState.vue'
import { formatDateTime } from '@/utils/format'

const dictStore = useDictStore()
const loading = ref(false)
const detailVisible = ref(false)
const selectedDetail = ref('{}')
const rows = ref<ActivityLog[]>([])
const page = reactive({ current: 1, size: 20, total: 0 })
const query = reactive<{
  action: string
  targetType: string
  targetId: number | undefined
  dateRange: [string, string] | []
}>({
  action: '',
  targetType: '',
  targetId: undefined,
  dateRange: [],
})

async function load() {
  loading.value = true
  try {
    const [dateFrom, dateTo] = query.dateRange || []
    const { data } = await getActivityLogs({
      current: page.current,
      size: page.size,
      action: query.action || undefined,
      targetType: query.targetType || undefined,
      targetId: query.targetId || undefined,
      dateFrom,
      dateTo,
    })
    rows.value = data.data.records || []
    page.total = data.data.total || 0
  } catch {
    rows.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

function targetTypeLabel(type?: string) {
  if (type === 'company') return '客户'
  if (type === 'brand') return '品牌'
  if (type === 'project') return '项目'
  return type || '-'
}

function onSearch() {
  page.current = 1
  load()
}

function onPageChange(v: number) {
  page.current = v
  load()
}

function openDetail(row: ActivityLog) {
  selectedDetail.value = prettyJson(row.detailJson)
  detailVisible.value = true
}

function prettyJson(raw: string | null | undefined) {
  if (!raw) return '{}'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function summaryText(raw: string | null | undefined) {
  if (!raw) return '-'
  try {
    const parsed = JSON.parse(raw)
    const from = parsed?.extra?.from
    const to = parsed?.extra?.to
    if (from && to) {
      return `由 ${from} 变更为 ${to}`
    }
    const after = parsed?.after
    if (after && typeof after === 'object') {
      return Object.keys(after)
        .slice(0, 3)
        .map((k) => `${k}:${String(after[k])}`)
        .join(', ')
    }
  } catch {
    return raw.slice(0, 80)
  }
  return '-'
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await load()
})
</script>

<style scoped>
.detail-json {
  background: #0b1020;
  color: #dbe7ff;
  border-radius: 8px;
  padding: 14px;
  max-height: 420px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.5;
}
</style>
