<template>
  <div class="account-health-page admin-page">
    <div class="admin-page-header">
      <div>
        <div class="admin-page-kicker">账号授权健康</div>
        <h1 class="admin-page-title">登录复验与凭据风险</h1>
        <div class="admin-page-subtitle">时间风险来自凭据采集和最近验证时间，仅用于安排复验，不代表平台授权一定失效。</div>
      </div>
      <div class="page-actions">
        <span v-if="overview?.generatedAt" class="generated-at">更新于 {{ formatDateTime(overview.generatedAt) }}</span>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="RefreshRight" :loading="refreshing" @click="refreshScan">重新计算风险</el-button>
      </div>
    </div>

    <DataState :loading="loading" :empty="!overview" empty-text="暂无账号授权健康数据">
      <template v-if="overview">
        <section class="metric-grid">
          <div class="metric-panel is-danger">
            <span>复验超期</span>
            <strong>{{ overview.summary.expiredCount }}</strong>
            <small>建议确认当前登录状态</small>
          </div>
          <div class="metric-panel is-warning">
            <span>即将需要复验</span>
            <strong>{{ overview.summary.dueInSevenDays }}</strong>
            <small>可提前确认登录状态</small>
          </div>
          <div class="metric-panel">
            <span>开放待办</span>
            <strong>{{ overview.summary.openAlertCount }}</strong>
            <small>已按问题类型聚合降噪</small>
          </div>
          <div class="metric-panel is-success">
            <span>正常</span>
            <strong>{{ overview.summary.normalCount }}</strong>
            <small>总目标 {{ overview.summary.totalTargets }}</small>
          </div>
        </section>

        <section class="overview-grid">
          <div class="panel trend-panel">
            <div class="panel-head">
              <div>
                <strong>建议复验时间分布</strong>
                <span>未来 14 天账号与论坛凭据复验安排</span>
              </div>
              <el-tag size="small" type="warning">30 天内 {{ overview.summary.dueInThirtyDays }}</el-tag>
            </div>
            <div class="trend-list">
              <div v-for="bucket in overview.trendBuckets" :key="bucket.date" class="trend-row">
                <span class="trend-date">{{ shortDate(bucket.date) }}</span>
                <div class="trend-track">
                  <span
                    class="trend-bar self-media"
                    :style="{ width: trendWidth(bucket.selfMediaCount, trendMax) }"
                  />
                  <span
                    class="trend-bar forum"
                    :style="{ width: trendWidth(bucket.forumCount, trendMax) }"
                  />
                </div>
                <strong>{{ bucket.totalCount }}</strong>
              </div>
            </div>
            <div class="trend-legend">
              <span><i class="legend-dot self-media" />自媒体</span>
              <span><i class="legend-dot forum" />论坛</span>
            </div>
          </div>

          <div class="panel">
            <div class="panel-head">
              <div>
                <strong>聚合告警</strong>
                <span>同类问题合并展示，减少重复干扰</span>
              </div>
              <el-tag size="small" :type="overview.alertGroups.length ? 'warning' : 'success'">
                {{ overview.alertGroups.length ? `${overview.alertGroups.length} 类` : '无待办' }}
              </el-tag>
            </div>
            <el-empty v-if="!overview.alertGroups.length" description="当前没有账号授权类待办" />
            <div v-else class="alert-group-list">
              <button
                v-for="group in overview.alertGroups"
                :key="group.groupKey"
                class="alert-group-row"
                type="button"
                @click="openRoute(group.actionRoute)"
              >
                <span class="severity-dot" :class="severityClass(group.severity)" />
                <span class="alert-group-main">
                  <strong>{{ group.title }}</strong>
                  <small>{{ group.sampleMessage || group.issueCode }}</small>
                </span>
                <el-tag size="small" :type="severityTag(group.severity)">{{ group.count }} 条</el-tag>
              </button>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-head">
            <div>
              <strong>待处理清单</strong>
              <span>按复验超期、凭据缺失和建议复验时间排序。</span>
            </div>
            <div class="filter-tabs">
              <el-segmented v-model="riskFilter" :options="riskFilterOptions" />
            </div>
          </div>

          <el-table :data="filteredRiskItems" border table-layout="fixed">
            <el-table-column label="对象" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="entity-cell">
                  <div class="entity-avatar" :class="row.targetType === 'forum' ? 'is-forum' : 'is-self'">
                    {{ row.targetType === 'forum' ? '坛' : '媒' }}
                  </div>
                  <div class="min-w-0">
                    <div class="entity-main">{{ row.displayName || '-' }}</div>
                    <div class="entity-sub">{{ objectSubtitle(row) }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="130">
              <template #default="{ row }">
                <el-tag size="small" :type="riskTag(row.riskStatus)">
                  {{ riskLabel(row.riskStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="建议复验时间" min-width="180">
              <template #default="{ row }">
                <div>{{ row.expiresAt ? formatDateTime(row.expiresAt) : '-' }}</div>
                <div class="table-subtext">{{ daysText(row.daysUntilExpiry) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="来源" width="130">
              <template #default="{ row }">{{ row.expirySourceLabel || '-' }}</template>
            </el-table-column>
            <el-table-column label="负责人" width="130">
              <template #default="{ row }">{{ row.ownerName || '-' }}</template>
            </el-table-column>
            <el-table-column label="处理建议" min-width="280" show-overflow-tooltip>
              <template #default="{ row }">{{ row.actionHint || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRoute(row.actionRoute)">
                  {{ row.actionLabel || '查看详情' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </template>
    </DataState>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshRight } from '@element-plus/icons-vue'
import DataState from '@/components/ui/DataState.vue'
import { getAccountAuthHealthOverview, refreshAccountAuthHealthOverview } from '@/api/accountAuthHealth'
import type { AccountAuthHealthOverview, AccountAuthHealthRiskItem } from '@/types'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const refreshing = ref(false)
const overview = ref<AccountAuthHealthOverview | null>(null)
const riskFilter = ref('attention')

const riskFilterOptions = [
  { label: '需关注', value: 'attention' },
  { label: '复验超期', value: 'overdue' },
  { label: '即将复验', value: 'due_soon' },
  { label: '全部', value: 'all' },
]

const filteredRiskItems = computed(() => {
  const rows = overview.value?.riskItems || []
  if (riskFilter.value === 'all') return rows
  if (riskFilter.value === 'attention') {
    return rows.filter((item) => item.riskStatus !== 'normal')
  }
  if (riskFilter.value === 'overdue') {
    return rows.filter((item) => item.riskStatus === 'reverify_overdue' || item.riskStatus === 'expired')
  }
  if (riskFilter.value === 'due_soon') {
    return rows.filter((item) => item.riskStatus === 'reverify_due_soon' || item.riskStatus === 'expiring')
  }
  return rows.filter((item) => item.riskStatus === riskFilter.value)
})

const trendMax = computed(() => Math.max(1, ...(overview.value?.trendBuckets || []).map((item) => item.totalCount)))

async function load() {
  loading.value = true
  try {
    const { data } = await getAccountAuthHealthOverview()
    overview.value = data.data
  } finally {
    loading.value = false
  }
}

async function refreshScan() {
  refreshing.value = true
  try {
    const { data } = await refreshAccountAuthHealthOverview()
    overview.value = data.data
    ElMessage.success('账号授权时间风险已重新计算')
  } finally {
    refreshing.value = false
  }
}

function openRoute(route?: string | null) {
  if (!route) return
  router.push(route)
}

function objectSubtitle(row: AccountAuthHealthRiskItem) {
  if (row.targetType === 'forum') return row.companyName || row.platformLabel || '论坛站点'
  return [row.companyName, row.brandName, row.platformLabel].filter(Boolean).join(' / ') || '-'
}

function riskLabel(status?: string | null) {
  if (status === 'expired') return '复验超期'
  if (status === 'reverify_overdue') return '复验超期'
  if (status === 'missing') return '缺少凭据'
  if (status === 'expiring') return '即将复验'
  if (status === 'reverify_due_soon') return '即将复验'
  if (status === 'credential_missing') return '待验证登录'
  if (status === 'monitoring_disabled') return '未启用监控'
  if (status === 'unknown') return '到期未知'
  if (status === 'normal') return '正常'
  return status || '-'
}

function riskTag(status?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'expired' || status === 'missing' || status === 'credential_missing') return 'danger'
  if (status === 'expiring' || status === 'reverify_due_soon' || status === 'reverify_overdue') return 'warning'
  if (status === 'normal') return 'success'
  return 'info'
}

function severityTag(severity?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (severity === 'critical' || severity === 'high' || severity === 'error') return 'danger'
  if (severity === 'warn') return 'warning'
  return 'info'
}

function severityClass(severity?: string | null) {
  if (severity === 'critical' || severity === 'high' || severity === 'error') return 'is-danger'
  if (severity === 'warn') return 'is-warning'
  return 'is-info'
}

function daysText(days?: number | null) {
  if (days === null || days === undefined) return '未记录到期'
  if (days < 0) return `复验超期 ${Math.abs(days)} 天`
  if (days === 0) return '今天到期'
  return `还剩 ${days} 天`
}

function shortDate(value: string) {
  const parts = value.split('-')
  return parts.length === 3 ? `${parts[1]}/${parts[2]}` : value
}

function trendWidth(value: number, max: number) {
  if (!value) return '0%'
  return `${Math.max(8, Math.round((value / max) * 100))}%`
}

onMounted(load)
</script>

<style scoped>
.account-health-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.generated-at,
.table-subtext {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  padding: 16px;
}

.metric-panel span,
.metric-panel small {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.metric-panel strong {
  display: block;
  margin: 8px 0 4px;
  color: var(--el-text-color-primary);
  font-size: 28px;
  line-height: 1;
}

.metric-panel.is-danger {
  border-color: #fecaca;
  background: #fff7f7;
}

.metric-panel.is-warning {
  border-color: #fde68a;
  background: #fffaf0;
}

.metric-panel.is-success {
  border-color: #bbf7d0;
  background: #f6fef9;
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 0.9fr);
  gap: 12px;
}

.panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  padding: 16px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-head strong {
  display: block;
  font-size: 15px;
}

.panel-head span {
  display: block;
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.trend-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.trend-row {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) 36px;
  align-items: center;
  gap: 10px;
}

.trend-date {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.trend-track {
  display: flex;
  height: 12px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.trend-bar.self-media {
  background: #2563eb;
}

.trend-bar.forum {
  background: #f59e0b;
}

.trend-legend {
  display: flex;
  gap: 16px;
  margin-top: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.legend-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 2px;
}

.legend-dot.self-media {
  background: #2563eb;
}

.legend-dot.forum {
  background: #f59e0b;
}

.alert-group-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.alert-group-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  padding: 10px;
  text-align: left;
  cursor: pointer;
}

.alert-group-row:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.severity-dot {
  width: 8px;
  height: 34px;
  border-radius: 4px;
  background: var(--el-color-info);
}

.severity-dot.is-danger {
  background: var(--el-color-danger);
}

.severity-dot.is-warning {
  background: var(--el-color-warning);
}

.alert-group-main {
  min-width: 0;
}

.alert-group-main strong,
.alert-group-main small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-group-main small {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
}

.filter-tabs {
  min-width: 300px;
}

.entity-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.entity-avatar {
  display: grid;
  width: 34px;
  height: 34px;
  flex: none;
  place-items: center;
  border-radius: 8px;
  font-weight: 700;
}

.entity-avatar.is-self {
  background: #eff6ff;
  color: #1d4ed8;
}

.entity-avatar.is-forum {
  background: #fffbeb;
  color: #b45309;
}

.entity-main,
.entity-sub {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.entity-main {
  font-weight: 600;
}

.entity-sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.min-w-0 {
  min-width: 0;
}

@media (max-width: 1100px) {
  .metric-grid,
  .overview-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 760px) {
  .metric-grid,
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .page-actions,
  .panel-head {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
