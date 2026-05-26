<template>
  <div class="platform-health-page admin-page">
    <div class="admin-page-header platform-health-header">
      <div>
        <div class="admin-page-kicker">监控中心</div>
        <h1 class="admin-page-title">平台健康</h1>
        <div class="admin-page-subtitle">集中观察平台额度、异常次数与降级状态，辅助判断调度链路健康度。</div>
      </div>
      <div class="admin-page-actions platform-header-actions">
        <span class="refresh-state" :class="{ 'is-active': autoRefresh }">
          <span class="refresh-dot" />
          {{ autoRefresh ? '自动刷新中' : '手动刷新' }}
        </span>
        <el-button :type="autoRefresh ? 'primary' : 'default'" plain @click="toggleAutoRefresh">
          60秒自动刷新 {{ autoRefresh ? 'ON' : 'OFF' }}
        </el-button>
        <el-button type="primary" :loading="loading" @click="loadPlatforms">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="admin-surface platform-toolbar-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-select v-model="filters.rangeType" style="width: 130px" @change="loadPlatforms">
            <el-option label="今日" value="today" />
            <el-option label="7天" value="last7" />
            <el-option label="30天" value="last30" />
            <el-option label="自定义" value="custom" />
          </el-select>
          <el-date-picker
            v-if="filters.rangeType === 'custom'"
            v-model="filters.customRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            @change="loadPlatforms"
          />
        </div>
        <div class="toolbar-right">共 {{ platforms.length }} 个平台</div>
      </div>
    </el-card>

    <div class="admin-metric-grid platform-summary-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">总平台</span>
        <strong class="admin-metric-value">{{ platforms.length }}</strong>
        <span class="admin-metric-hint">当前筛选周期</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #7c3aed; --metric-tone: #f5f3ff">
        <span class="admin-metric-label">P0 / P1 / P2</span>
        <strong class="admin-metric-value">{{ p0Count }} / {{ p1Count }} / {{ p2Count }}</strong>
        <span class="admin-metric-hint">按平台优先级分布</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #ef4444; --metric-tone: #fef2f2">
        <span class="admin-metric-label">已降级</span>
        <strong class="admin-metric-value">{{ degradedCount }}</strong>
        <span class="admin-metric-hint">需要人工关注</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">接近阈值</span>
        <strong class="admin-metric-value">{{ nearLimitCount }}</strong>
        <span class="admin-metric-hint">异常占比达到 80%</span>
      </div>
    </div>

    <section class="llm-pool-panel">
      <div class="llm-pool-head">
        <div>
          <div class="panel-kicker">大模型执行池</div>
          <h3 class="panel-title">全局并发与异常信号</h3>
        </div>
        <span class="platform-status" :class="llmPoolStatusClass">
          <span class="health-dot"></span>
          {{ llmPoolStatusText }}
        </span>
      </div>
      <div class="llm-pool-grid">
        <div class="llm-pool-item">
          <span>全局占用</span>
          <strong>{{ llmPool?.activeGlobal || 0 }} / {{ llmPool?.globalConcurrency || 0 }}</strong>
        </div>
        <div class="llm-pool-item">
          <span>跟踪租约</span>
          <strong>{{ llmPool?.trackedLeases || 0 }}</strong>
        </div>
        <div class="llm-pool-item">
          <span>Permit Busy</span>
          <strong>{{ permitBusyTotal }}</strong>
        </div>
        <div class="llm-pool-item">
          <span>熔断/降级信号</span>
          <strong>{{ circuitSignalTotal }}</strong>
        </div>
      </div>
      <el-progress :percentage="llmPoolPercent" :status="llmPoolProgressStatus" />
      <div v-if="featureUsageItems.length" class="llm-feature-row">
        <span v-for="item in featureUsageItems" :key="item.key">
          {{ featureLabel(item.key) }} {{ item.active }} / {{ item.limit }}
        </span>
      </div>
    </section>

    <DataState :loading="loading" :empty="!loading && platforms.length === 0" empty-text="暂无平台健康数据">
      <div class="platform-grid">
        <article v-for="item in platforms" :key="item.id" class="platform-card" :class="platformCardClass(item)">
          <div class="platform-head">
            <div class="platform-title-row">
              <span class="platform-avatar" :class="platformCardClass(item)">{{ platformInitial(item.platformName) }}</span>
              <div class="min-w-0">
                <div class="platform-name">{{ item.platformName }}</div>
                <div class="platform-sub">{{ item.platformCode }} · {{ item.priorityLevel }}</div>
              </div>
            </div>
            <span class="platform-status" :class="platformStatusClass(item)">
              <span class="health-dot"></span>
              {{ platformStatusText(item) }}
            </span>
          </div>
          <div class="platform-limit-grid">
            <div class="platform-limit-item">
              <span>RPM 上限</span>
              <strong>{{ item.rpmLimit || 0 }}</strong>
            </div>
            <div class="platform-limit-item">
              <span>TPM 上限</span>
              <strong>{{ item.tpmLimit || 0 }}</strong>
            </div>
            <div class="platform-limit-item">
              <span>并发占用</span>
              <strong>{{ item.activePermitCount || 0 }} / {{ item.concurrencyLimit || 1 }}</strong>
            </div>
            <div class="platform-limit-item">
              <span>真实调用</span>
              <strong>{{ item.invocationCount || 0 }}</strong>
            </div>
            <div class="platform-limit-item">
              <span>异常信号</span>
              <strong>{{ item.exceptionCount || 0 }}</strong>
            </div>
          </div>
          <div class="platform-progress-row">
            <span>真实失败率</span>
            <strong>{{ platformPercent(item) }}%</strong>
          </div>
          <el-progress :percentage="platformPercent(item)" :status="platformProgressStatus(item)" />
          <div class="platform-observe-row">
            <span>成功 {{ item.successCount || 0 }}</span>
            <span>限流 {{ item.rateLimitedCount || 0 }}</span>
            <span>平均 {{ formatDuration(item.avgDurationMs) }}</span>
          </div>
          <div v-if="item.degradedReason" class="platform-risk">{{ item.degradedReason }}</div>
          <div v-if="item.lastSuccessAt" class="platform-fail-time">最近成功：{{ item.lastSuccessAt }}</div>
          <div v-if="item.lastFailureAt" class="platform-fail-time">最近失败：{{ item.lastFailureAt }}</div>
        </article>
      </div>
    </DataState>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import { getDispatchPlatforms, getLlmPoolSnapshot, type DispatchRangeParams } from '@/api/dispatch'
import type { DispatchPlatformHealthItem, LlmPoolSnapshot } from '@/types'

const loading = ref(false)
const autoRefresh = ref(true)
let timer: number | null = null

const filters = reactive({
  rangeType: 'today' as 'today' | 'last7' | 'last30' | 'custom',
  customRange: [] as string[],
})

const platforms = ref<DispatchPlatformHealthItem[]>([])
const llmPool = ref<LlmPoolSnapshot | null>(null)

const p0Count = computed(() => platforms.value.filter((x) => x.priorityLevel === 'P0').length)
const p1Count = computed(() => platforms.value.filter((x) => x.priorityLevel === 'P1').length)
const p2Count = computed(() => platforms.value.filter((x) => x.priorityLevel === 'P2').length)
const degradedCount = computed(() => platforms.value.filter((x) => x.degraded).length)
const nearLimitCount = computed(() => platforms.value.filter((x) => !x.degraded && platformPercent(x) >= 80).length)
const llmPoolPercent = computed(() => {
  const limit = llmPool.value?.globalConcurrency || 0
  if (limit <= 0) return 0
  return Math.min(100, Math.round(((llmPool.value?.activeGlobal || 0) / limit) * 100))
})
const permitBusyTotal = computed(() => counterTotal('llm_permit_acquire_busy_total'))
const circuitSignalTotal = computed(() => {
  const breakers = llmPool.value?.circuitBreakers || {}
  return Object.values(breakers).filter((item) => item?.open).length
})
const llmPoolStatusText = computed(() => {
  if (!llmPool.value?.enabled) return '未启用'
  if (permitBusyTotal.value > 0 || circuitSignalTotal.value > 0) return '存在告警'
  if (llmPoolPercent.value >= 80) return '接近上限'
  return '正常'
})
const llmPoolStatusClass = computed(() => {
  if (!llmPool.value?.enabled) return 'dot-gray'
  if (permitBusyTotal.value > 0 || circuitSignalTotal.value > 0) return 'dot-red'
  if (llmPoolPercent.value >= 80) return 'dot-yellow'
  return 'dot-green'
})
const llmPoolProgressStatus = computed<'' | 'success' | 'warning' | 'exception'>(() => {
  if (permitBusyTotal.value > 0 || circuitSignalTotal.value > 0) return 'exception'
  if (llmPoolPercent.value >= 80) return 'warning'
  return 'success'
})
const featureUsageItems = computed(() => {
  const limits = llmPool.value?.featureConcurrency || {}
  const active = llmPool.value?.activeFeatures || {}
  return Object.entries(limits).map(([key, limit]) => ({
    key,
    limit: Number(limit || 0),
    active: Number(active[key] || 0),
  }))
})

function ensureCustomRange() {
  if (filters.rangeType !== 'custom') return true
  if (!filters.customRange?.[0] || !filters.customRange?.[1]) {
    ElMessage.warning('请选择完整的自定义日期范围')
    return false
  }
  return true
}

function buildRangeParams(): DispatchRangeParams {
  const params: DispatchRangeParams = { rangeType: filters.rangeType }
  if (filters.rangeType === 'custom') {
    params.startDate = filters.customRange?.[0]
    params.endDate = filters.customRange?.[1]
  }
  return params
}

function platformPercent(item: DispatchPlatformHealthItem) {
  if ((item.invocationCount || 0) > 0) {
    return Math.min(100, Math.round(Number(item.failureRate || 0)))
  }
  const limit = item.rpmLimit || 0
  if (limit <= 0) return 0
  return Math.min(100, Math.round(((item.exceptionCount || 0) / limit) * 100))
}

function platformStatusText(item: DispatchPlatformHealthItem) {
  if (item.degraded) return '已降级'
  const status = item.currentHealthStatus || 'normal'
  if (status === 'maintenance') return '维护中'
  if (status === 'manual_takeover') return '人工接管'
  if (status === 'degraded') return '熔断降级'
  if (status === 'high_failure') return '高失败率'
  if (status === 'slow_response') return '响应慢'
  const p = platformPercent(item)
  if (p >= 80) return '接近阈值'
  return '正常'
}

function platformProgressStatus(item: DispatchPlatformHealthItem): '' | 'success' | 'warning' | 'exception' {
  if (item.degraded || item.currentHealthStatus === 'high_failure' || item.currentHealthStatus === 'degraded') return 'exception'
  if (item.currentHealthStatus === 'slow_response') return 'warning'
  const p = platformPercent(item)
  if (p >= 80) return 'warning'
  return 'success'
}

function platformCardClass(item: DispatchPlatformHealthItem) {
  if (item.degraded || item.currentHealthStatus === 'high_failure' || item.currentHealthStatus === 'degraded') return 'platform-card-danger'
  if (item.currentHealthStatus === 'slow_response') return 'platform-card-warning'
  const p = platformPercent(item)
  if (p >= 80) return 'platform-card-warning'
  return 'platform-card-success'
}

function platformStatusClass(item: DispatchPlatformHealthItem) {
  if (item.degraded || item.currentHealthStatus === 'high_failure' || item.currentHealthStatus === 'degraded') return 'dot-red'
  if (item.currentHealthStatus === 'slow_response') return 'dot-yellow'
  if (item.currentHealthStatus === 'manual_takeover' || item.currentHealthStatus === 'maintenance') return 'dot-gray'
  const p = platformPercent(item)
  if (p >= 80) return 'dot-yellow'
  return 'dot-green'
}

function formatDuration(value?: number | null) {
  const ms = Number(value || 0)
  if (ms <= 0) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function platformInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '平'
}

async function loadPlatforms() {
  if (!ensureCustomRange()) return
  loading.value = true
  try {
    const [{ data }, poolResp] = await Promise.all([
      getDispatchPlatforms(buildRangeParams()),
      getLlmPoolSnapshot(),
    ])
    platforms.value = data.data || []
    llmPool.value = poolResp.data.data || null
  } finally {
    loading.value = false
  }
}

function counterTotal(pattern: string) {
  const counters = llmPool.value?.counters || {}
  return Object.entries(counters)
    .filter(([key]) => key.includes(pattern))
    .reduce((sum, [, value]) => sum + Number(value || 0), 0)
}

function featureLabel(feature: string) {
  const labels: Record<string, string> = {
    monitoring: '问题池',
    article: '文章',
    presale: '售前',
    draft: '草稿',
    generic: '通用',
  }
  return labels[feature] || feature
}

function startTimer() {
  stopTimer()
  if (!autoRefresh.value) return
  timer = window.setInterval(() => {
    if (document.hidden) return
    loadPlatforms()
  }, 60000)
}

function stopTimer() {
  if (!timer) return
  window.clearInterval(timer)
  timer = null
}

function toggleAutoRefresh() {
  autoRefresh.value = !autoRefresh.value
  startTimer()
}

onMounted(async () => {
  await loadPlatforms()
  startTimer()
})

onBeforeUnmount(() => {
  stopTimer()
})
</script>

<style scoped>
.platform-health-header {
  align-items: center;
}

.platform-header-actions,
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.platform-toolbar-card :deep(.el-card__body) {
  padding: 12px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.toolbar-right {
  font-size: 13px;
  color: var(--admin-text-muted);
  font-weight: 700;
}

.refresh-state {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.refresh-state.is-active {
  border-color: #a7f3d0;
  background: #ecfdf5;
  color: #047857;
}

.refresh-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #94a3b8;
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.16);
}

.refresh-state.is-active .refresh-dot {
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.16);
}

.platform-summary-grid {
  margin-bottom: 0;
}

.llm-pool-panel {
  margin: 14px 0;
  padding: 16px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
}

.llm-pool-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-kicker {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.panel-title {
  margin: 3px 0 0;
  color: var(--admin-text-strong);
  font-size: 16px;
  font-weight: 800;
}

.llm-pool-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.llm-pool-item {
  padding: 12px;
  border: 1px solid #e7edf5;
  border-radius: 10px;
  background: #f8fafc;
}

.llm-pool-item span {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.llm-pool-item strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.llm-feature-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.llm-feature-row span {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 10px;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: #eff6ff;
  color: #1e40af;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.platform-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.platform-card {
  min-height: 228px;
  position: relative;
  overflow: hidden;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background:
    linear-gradient(135deg, #ffffff 0%, #ffffff 62%, #f8fbff 100%);
  padding: 16px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
}

.platform-card::after {
  content: "";
  position: absolute;
  right: -36px;
  bottom: -44px;
  width: 112px;
  height: 112px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.06);
  pointer-events: none;
}

.platform-card-success {
  border-color: #bfdbfe;
}

.platform-card-warning {
  border-color: #fde68a;
  background:
    linear-gradient(135deg, #ffffff 0%, #ffffff 58%, #fffbeb 100%);
}

.platform-card-danger {
  border-color: #fecaca;
  background:
    linear-gradient(135deg, #ffffff 0%, #ffffff 58%, #fef2f2 100%);
}

.platform-head {
  display: flex;
  position: relative;
  z-index: 1;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.platform-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.platform-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 11px;
  background: linear-gradient(135deg, #2563eb, #06b6d4);
  color: #ffffff;
  font-size: 16px;
  font-weight: 800;
  box-shadow: 0 10px 18px rgba(37, 99, 235, 0.18);
}

.platform-avatar.platform-card-warning {
  background: linear-gradient(135deg, #d97706, #f59e0b);
  box-shadow: 0 10px 18px rgba(245, 158, 11, 0.18);
}

.platform-avatar.platform-card-danger {
  background: linear-gradient(135deg, #dc2626, #ef4444);
  box-shadow: 0 10px 18px rgba(239, 68, 68, 0.18);
}

.platform-name {
  overflow: hidden;
  color: var(--admin-text-strong);
  font-size: 15px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.platform-sub {
  margin-top: 4px;
  color: var(--admin-text-muted);
  font-size: 12px;
}

.platform-status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.platform-status.dot-yellow {
  background: #fffbeb;
  color: #b45309;
}

.platform-status.dot-red {
  background: #fef2f2;
  color: #b91c1c;
}

.platform-status.dot-gray {
  background: #f8fafc;
  color: #64748b;
}

.platform-limit-grid {
  display: grid;
  position: relative;
  z-index: 1;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 14px;
}

.platform-limit-item {
  min-width: 0;
  padding: 10px;
  border: 1px solid #e7edf5;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.82);
}

.platform-limit-item span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.platform-limit-item strong {
  display: block;
  margin-top: 5px;
  color: #0f172a;
  font-size: 16px;
  font-weight: 800;
}

.platform-progress-row {
  display: flex;
  position: relative;
  z-index: 1;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 7px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.platform-progress-row strong {
  color: #0f172a;
}

.platform-observe-row {
  display: flex;
  position: relative;
  z-index: 1;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.platform-observe-row span {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border: 1px solid #e7edf5;
  border-radius: 999px;
  background: rgba(248, 250, 252, 0.86);
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.platform-card :deep(.el-progress) {
  position: relative;
  z-index: 1;
}

.platform-risk {
  position: relative;
  z-index: 1;
  margin-top: 10px;
  padding: 9px 10px;
  border: 1px solid #fecaca;
  border-radius: 10px;
  background: rgba(254, 242, 242, 0.82);
  color: #dc2626;
  font-size: 12px;
  line-height: 1.5;
}

.platform-fail-time {
  position: relative;
  z-index: 1;
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.health-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.platform-status.dot-green .health-dot {
  background: #65a30d;
}

.platform-status.dot-yellow .health-dot {
  background: #d97706;
}

.platform-status.dot-red .health-dot {
  background: #ef4444;
}

.platform-status.dot-gray .health-dot {
  background: #94a3b8;
}

@media (max-width: 1280px) {
  .platform-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .platform-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .platform-health-header,
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .platform-grid {
    grid-template-columns: 1fr;
  }

  .llm-pool-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .toolbar-left {
    align-items: stretch;
  }
}
</style>
