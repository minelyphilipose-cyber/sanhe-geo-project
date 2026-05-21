<template>
  <div class="activity-logs-page admin-page">
    <div class="admin-page-header activity-header">
      <div>
        <div class="admin-page-kicker">监控中心</div>
        <h1 class="admin-page-title">操作日志</h1>
        <div class="admin-page-subtitle">追踪关键业务对象的操作记录、变更摘要和操作者信息，支撑审计回溯。</div>
      </div>
      <div class="admin-page-actions">
        <el-button type="primary" :loading="loading" @click="onSearch">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="admin-surface activity-filter-card">
      <div class="activity-filter-grid">
        <el-select v-model="query.action" class="filter-action" clearable placeholder="操作类型" @change="onSearch">
          <el-option
            v-for="item in dictStore.options('activity_action')"
            :key="item.dictKey"
            :label="item.dictValue"
            :value="item.dictKey"
          />
        </el-select>
        <el-select v-model="query.targetType" class="filter-target-type" clearable placeholder="目标类型" @change="onSearch">
          <el-option label="客户" value="company" />
          <el-option label="品牌" value="brand" />
          <el-option label="项目" value="project" />
        </el-select>
        <el-date-picker
          v-model="query.dateRange"
          class="filter-date-range"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DD HH:mm:ss"
          @change="onSearch"
        />
        <el-input v-model.number="query.targetId" class="filter-target-id" clearable placeholder="目标编号" @keyup.enter="onSearch" />
        <el-button class="filter-submit" type="primary" plain @click="onSearch">查询</el-button>
      </div>
    </el-card>

    <div class="admin-metric-grid activity-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">日志总数</span>
        <strong class="admin-metric-value">{{ page.total }}</strong>
        <span class="admin-metric-hint">当前筛选条件下记录数</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">操作者</span>
        <strong class="admin-metric-value">{{ operatorCount }}</strong>
        <span class="admin-metric-hint">当前页去重统计</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #7c3aed; --metric-tone: #f5f3ff">
        <span class="admin-metric-label">项目操作</span>
        <strong class="admin-metric-value">{{ projectLogCount }}</strong>
        <span class="admin-metric-hint">目标类型为项目的记录</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">最近操作</span>
        <strong class="admin-metric-value compact-value">{{ latestLogTime }}</strong>
        <span class="admin-metric-hint">按列表时间倒序展示</span>
      </div>
    </div>

    <div class="activity-insight-grid">
      <section class="activity-panel">
        <div class="panel-head">
          <div>
            <div class="panel-kicker">行为分布</div>
            <h3 class="panel-title">当前页操作类型</h3>
          </div>
        </div>
        <div class="action-distribution">
          <div v-for="item in actionInsights" :key="item.action" class="distribution-row">
            <span>{{ item.label }}</span>
            <div class="distribution-track">
              <i :style="{ width: `${item.percent}%` }"></i>
            </div>
            <strong>{{ item.count }}</strong>
          </div>
        </div>
      </section>

      <section class="activity-panel">
        <div class="panel-head">
          <div>
            <div class="panel-kicker">审计范围</div>
            <h3 class="panel-title">对象覆盖</h3>
          </div>
        </div>
        <div class="scope-grid">
          <div class="scope-item">
            <span>客户</span>
            <strong>{{ targetTypeCount.company }}</strong>
          </div>
          <div class="scope-item">
            <span>品牌</span>
            <strong>{{ targetTypeCount.brand }}</strong>
          </div>
          <div class="scope-item">
            <span>项目</span>
            <strong>{{ targetTypeCount.project }}</strong>
          </div>
        </div>
        <div class="scope-note">统计口径为当前页数据，用于快速判断审计记录集中在哪类业务对象。</div>
      </section>
    </div>

    <el-card shadow="never" class="admin-table-card activity-table-card">
      <div class="table-header">
        <div>
          <div class="table-title">日志明细</div>
          <div class="table-subtitle">记录操作人、动作、对象和结构化变更摘要。</div>
        </div>
        <div class="chips">
          <span class="chip chip-muted">总计 {{ page.total }}</span>
          <span class="chip chip-blue">当前页 {{ rows.length }}</span>
        </div>
      </div>

      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无操作日志">
        <el-table :data="rows" border table-layout="fixed">
          <el-table-column label="操作对象" min-width="230" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="admin-entity-cell">
                <div class="admin-entity-avatar log-avatar" :class="targetAvatarClass(row.targetType)">
                  {{ targetInitial(row.targetType) }}
                </div>
                <div class="min-w-0">
                  <div class="admin-entity-main">{{ targetTypeLabel(row.targetType) }} #{{ row.targetId || '-' }}</div>
                  <div class="admin-entity-sub">{{ formatDateTime(row.createdAt) }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="operatorName" label="操作人" width="140" show-overflow-tooltip />
          <el-table-column label="操作" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="admin-mini-pill is-blue">{{ dictStore.label('activity_action', row.action) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="目标类型" width="120">
            <template #default="{ row }">
              <span class="target-pill" :class="targetAvatarClass(row.targetType)">{{ targetTypeLabel(row.targetType) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="摘要" min-width="300" show-overflow-tooltip>
            <template #default="{ row }">{{ summaryText(row.detailJson) }}</template>
          </el-table-column>
          <el-table-column label="IP 地址" width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.ipAddress || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>

      <div class="admin-table-footer">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page="page.current"
          :page-size="page.size"
          :total="page.total"
          @current-change="onPageChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="日志详情" width="760px" class="admin-editor-dialog activity-detail-dialog">
      <pre class="detail-json">{{ selectedDetail }}</pre>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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

const operatorCount = computed(() => new Set(rows.value.map((row) => row.operatorName).filter(Boolean)).size)
const projectLogCount = computed(() => rows.value.filter((row) => row.targetType === 'project').length)
const latestLogTime = computed(() => rows.value[0]?.createdAt ? formatDateTime(rows.value[0].createdAt).slice(5, 16) : '-')
const targetTypeCount = computed(() => ({
  company: rows.value.filter((row) => row.targetType === 'company').length,
  brand: rows.value.filter((row) => row.targetType === 'brand').length,
  project: rows.value.filter((row) => row.targetType === 'project').length,
}))
const actionInsights = computed(() => {
  const counts = rows.value.reduce<Record<string, number>>((acc, row) => {
    acc[row.action] = (acc[row.action] || 0) + 1
    return acc
  }, {})
  const max = Math.max(...Object.values(counts), 1)
  const items = Object.entries(counts)
    .map(([action, count]) => ({
      action,
      count,
      label: dictStore.label('activity_action', action),
      percent: Math.max(8, Math.round((count / max) * 100)),
    }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 4)
  if (items.length) return items
  return [{ action: 'empty', count: 0, label: '暂无数据', percent: 8 }]
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

function targetInitial(type?: string) {
  if (type === 'company') return '客'
  if (type === 'brand') return '品'
  if (type === 'project') return '项'
  return '记'
}

function targetAvatarClass(type?: string) {
  if (type === 'company') return 'is-company'
  if (type === 'brand') return 'is-brand'
  if (type === 'project') return 'is-project'
  return 'is-muted'
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
.activity-header {
  align-items: center;
}

.activity-filter-card :deep(.el-card__body) {
  padding: 12px;
}

.activity-filter-grid {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
  flex-wrap: wrap;
  max-width: 980px;
}

.filter-action {
  width: 180px;
  flex: 0 0 180px;
}

.filter-target-type {
  width: 130px;
  flex: 0 0 130px;
}

.filter-date-range {
  width: 360px !important;
  max-width: 360px;
  flex: 0 0 360px;
}

.filter-target-id {
  width: 130px;
  flex: 0 0 130px;
}

.filter-submit {
  flex: 0 0 72px;
  min-width: 76px;
}

.activity-filter-grid :deep(.filter-date-range.el-date-editor) {
  width: 360px !important;
  max-width: 360px;
  flex: 0 0 360px;
}

.activity-filter-grid :deep(.filter-date-range .el-range-input) {
  width: 118px;
  flex: 0 0 118px;
}

.activity-metric-grid {
  margin-bottom: 0;
}

.compact-value {
  font-size: 24px;
}

.activity-insight-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.28fr) minmax(0, 0.72fr);
  gap: 12px;
}

.activity-panel {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--admin-panel-border);
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #ffffff 72%, #f8fafc 100%);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
}

.panel-head {
  min-height: 58px;
  padding: 16px 18px 13px;
  border-bottom: 1px solid var(--admin-panel-border-soft);
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 58%, #f0fdf4 100%);
}

.panel-kicker {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.panel-title {
  margin: 4px 0 0;
  color: var(--admin-text-strong);
  font-size: 16px;
  line-height: 1.35;
  font-weight: 800;
}

.action-distribution {
  display: grid;
  gap: 12px;
  padding: 16px;
}

.distribution-row {
  display: grid;
  grid-template-columns: minmax(90px, 0.55fr) minmax(0, 1fr) 34px;
  align-items: center;
  gap: 10px;
}

.distribution-row span {
  overflow: hidden;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.distribution-row strong {
  color: #0f172a;
  font-size: 14px;
  text-align: right;
}

.distribution-track {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #e2e8f0;
}

.distribution-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563eb, #10b981);
}

.scope-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 16px 16px 12px;
}

.scope-item {
  border: 1px solid #e7edf5;
  border-radius: 12px;
  background: #ffffff;
  padding: 13px;
}

.scope-item span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.scope-item strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 20px;
  font-weight: 800;
}

.scope-note {
  padding: 0 16px 16px;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.55;
}

.activity-table-card :deep(.el-card__body) {
  padding: 0;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--admin-panel-border-soft);
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 55%, #f0fdf4 100%);
}

.table-title {
  color: var(--admin-text-strong);
  font-size: 16px;
  font-weight: 800;
}

.table-subtitle {
  margin-top: 4px;
  color: var(--admin-text-muted);
  font-size: 12px;
}

.chips {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  border-radius: 14px;
  padding: 3px 9px;
  font-size: 12px;
  font-weight: 700;
}

.chip-muted {
  background: #f3f4f6;
  color: #6b7280;
}

.chip-blue {
  background: #eff6ff;
  color: #1d4ed8;
}

.log-avatar.is-company,
.target-pill.is-company {
  background: linear-gradient(135deg, #2563eb, #06b6d4);
}

.log-avatar.is-brand,
.target-pill.is-brand {
  background: linear-gradient(135deg, #7c3aed, #a855f7);
}

.log-avatar.is-project,
.target-pill.is-project {
  background: linear-gradient(135deg, #059669, #14b8a6);
}

.log-avatar.is-muted,
.target-pill.is-muted {
  background: linear-gradient(135deg, #64748b, #94a3b8);
}

.target-pill {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  color: #ffffff;
  font-size: 12px;
  font-weight: 800;
}

.detail-json {
  margin: 0;
  max-height: 420px;
  overflow: auto;
  padding: 14px;
  border: 1px solid #1e293b;
  border-radius: 10px;
  background: #0b1020;
  color: #dbe7ff;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .activity-insight-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .activity-filter-grid {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-action,
  .filter-target-type,
  .filter-date-range,
  .filter-target-id,
  .filter-submit {
    width: 100%;
  }

  .scope-grid {
    grid-template-columns: 1fr;
  }
}
</style>
