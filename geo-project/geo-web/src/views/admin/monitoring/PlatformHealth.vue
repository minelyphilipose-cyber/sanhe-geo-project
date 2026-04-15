<template>
  <div class="platform-health-page">
    <el-card shadow="never" class="toolbar-card">
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
          <el-button :type="autoRefresh ? 'primary' : 'default'" plain @click="toggleAutoRefresh">
            60秒自动刷新 {{ autoRefresh ? 'ON' : 'OFF' }}
          </el-button>
          <el-button :loading="loading" @click="loadPlatforms">刷新</el-button>
        </div>
        <div class="toolbar-right">共 {{ platforms.length }} 个平台</div>
      </div>
    </el-card>

    <el-card shadow="never" class="summary-card">
      <div class="summary-wrap">
        <span class="summary-pill summary-pill-all">总平台 {{ platforms.length }}</span>
        <span class="summary-pill summary-pill-p0">P0 {{ p0Count }}</span>
        <span class="summary-pill summary-pill-p1">P1 {{ p1Count }}</span>
        <span class="summary-pill summary-pill-p2">P2 {{ p2Count }}</span>
        <span class="summary-pill summary-pill-danger">已降级 {{ degradedCount }}</span>
        <span class="summary-pill summary-pill-warn">接近阈值 {{ nearLimitCount }}</span>
      </div>
    </el-card>

    <DataState :loading="loading" :empty="!loading && platforms.length === 0" empty-text="暂无平台健康数据">
      <el-row :gutter="12">
        <el-col v-for="item in platforms" :key="item.id" :xs="24" :sm="12" :md="8" :lg="6" class="mb-3">
          <el-card shadow="never" class="platform-card" :class="platformCardClass(item)">
            <div class="platform-head">
              <div>
                <div class="platform-name">
                  <span class="health-dot" :class="platformDotClass(item)"></span>
                  {{ item.platformName }}
                </div>
                <div class="platform-sub">{{ item.platformCode }} · {{ item.priorityLevel }}</div>
              </div>
              <el-tag :type="platformTagType(item)">{{ platformStatusText(item) }}</el-tag>
            </div>
            <div class="platform-line">RPM上限：{{ item.rpmLimit || 0 }}</div>
            <div class="platform-line">TPM上限：{{ item.tpmLimit || 0 }}</div>
            <div class="platform-line">异常次数：{{ item.exceptionCount || 0 }}</div>
            <el-progress :percentage="platformPercent(item)" :status="platformProgressStatus(item)" />
            <div v-if="item.degradedReason" class="platform-risk">{{ item.degradedReason }}</div>
            <div v-if="item.lastFailureAt" class="platform-fail-time">最近失败：{{ item.lastFailureAt }}</div>
          </el-card>
        </el-col>
      </el-row>
    </DataState>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import { getDispatchPlatforms, type DispatchRangeParams } from '@/api/dispatch'
import type { DispatchPlatformHealthItem } from '@/types'

const loading = ref(false)
const autoRefresh = ref(true)
let timer: number | null = null

const filters = reactive({
  rangeType: 'today' as 'today' | 'last7' | 'last30' | 'custom',
  customRange: [] as string[],
})

const platforms = ref<DispatchPlatformHealthItem[]>([])

const p0Count = computed(() => platforms.value.filter((x) => x.priorityLevel === 'P0').length)
const p1Count = computed(() => platforms.value.filter((x) => x.priorityLevel === 'P1').length)
const p2Count = computed(() => platforms.value.filter((x) => x.priorityLevel === 'P2').length)
const degradedCount = computed(() => platforms.value.filter((x) => x.degraded).length)
const nearLimitCount = computed(() => platforms.value.filter((x) => !x.degraded && platformPercent(x) >= 80).length)

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
  const limit = item.rpmLimit || 0
  if (limit <= 0) return 0
  return Math.min(100, Math.round(((item.exceptionCount || 0) / limit) * 100))
}

function platformStatusText(item: DispatchPlatformHealthItem) {
  if (item.degraded) return '已降级'
  const p = platformPercent(item)
  if (p >= 80) return '接近阈值'
  return '正常'
}

function platformTagType(item: DispatchPlatformHealthItem): 'success' | 'warning' | 'danger' | 'info' {
  if (item.degraded) return 'danger'
  const p = platformPercent(item)
  if (p >= 80) return 'warning'
  return 'success'
}

function platformProgressStatus(item: DispatchPlatformHealthItem): '' | 'success' | 'warning' | 'exception' {
  if (item.degraded) return 'exception'
  const p = platformPercent(item)
  if (p >= 80) return 'warning'
  return 'success'
}

function platformCardClass(item: DispatchPlatformHealthItem) {
  if (item.degraded) return 'platform-card-danger'
  const p = platformPercent(item)
  if (p >= 80) return 'platform-card-warning'
  return 'platform-card-success'
}

function platformDotClass(item: DispatchPlatformHealthItem) {
  if (item.degraded) return 'dot-red'
  const p = platformPercent(item)
  if (p >= 80) return 'dot-yellow'
  return 'dot-green'
}

async function loadPlatforms() {
  if (!ensureCustomRange()) return
  loading.value = true
  try {
    const { data } = await getDispatchPlatforms(buildRangeParams())
    platforms.value = data.data || []
  } finally {
    loading.value = false
  }
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
.platform-health-page {
  padding: 6px 0;
}

.toolbar-card,
.summary-card {
  margin-bottom: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.toolbar-right {
  font-size: 13px;
  color: #6b7280;
}

.summary-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.summary-pill {
  display: inline-flex;
  align-items: center;
  border-radius: 14px;
  padding: 2px 10px;
  font-size: 12px;
}

.summary-pill-all {
  background: #f3f4f6;
  color: #374151;
}

.summary-pill-p0 {
  background: #e0f2fe;
  color: #0369a1;
}

.summary-pill-p1 {
  background: #eef2ff;
  color: #4338ca;
}

.summary-pill-p2 {
  background: #ecfdf5;
  color: #047857;
}

.summary-pill-danger {
  background: #fef2f2;
  color: #b91c1c;
}

.summary-pill-warn {
  background: #fffbeb;
  color: #b45309;
}

.platform-card {
  min-height: 178px;
  border: 1px solid var(--el-border-color-lighter);
}

.platform-card-success {
  border-color: #b7ebc6;
}

.platform-card-warning {
  border-color: #f8d08a;
}

.platform-card-danger {
  border-color: #f2b1b1;
}

.platform-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.platform-name {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.platform-sub {
  margin-top: 2px;
  color: #6b7280;
  font-size: 12px;
}

.platform-line {
  color: #374151;
  font-size: 13px;
  margin-bottom: 6px;
}

.platform-risk {
  margin-top: 6px;
  color: #dc2626;
  font-size: 12px;
}

.platform-fail-time {
  margin-top: 4px;
  color: #9ca3af;
  font-size: 12px;
}

.health-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.dot-green {
  background: #65a30d;
}

.dot-yellow {
  background: #d97706;
}

.dot-red {
  background: #ef4444;
}
</style>
