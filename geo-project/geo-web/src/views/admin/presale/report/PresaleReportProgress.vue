<template>
  <div class="presale-report-progress">
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/admin/presale/report' }">售前报告</el-breadcrumb-item>
        <el-breadcrumb-item>生成中</el-breadcrumb-item>
      </el-breadcrumb>
      <h2 class="page-title">报告生成进度</h2>
    </div>

    <!-- 降级提示 -->
    <el-alert
      v-if="version?.isDegraded && (version.degradedPlatforms?.length ?? 0) > 0"
      type="warning"
      :closable="false"
      show-icon
      class="degrade-alert"
    >
      <template #title>
        本次生成已降级,以下平台未能返回结果:{{ version.degradedPlatforms.join('、') }}
      </template>
    </el-alert>

    <!-- 主状态卡片 -->
    <el-card shadow="never" class="status-card">
      <div class="status-header">
        <el-icon v-if="isRunning" class="status-icon running"><Loading /></el-icon>
        <el-icon v-else-if="isDone" class="status-icon done"><CircleCheckFilled /></el-icon>
        <el-icon v-else-if="isFailed" class="status-icon failed"><CircleCloseFilled /></el-icon>
        <el-icon v-else class="status-icon"><Clock /></el-icon>

        <div class="status-text">
          <div class="status-main">{{ statusText }}</div>
          <div v-if="version" class="status-sub">
            已完成 {{ version.completedLlmCalls }} / {{ version.totalLlmCalls }} 次 LLM 调用
          </div>
        </div>
      </div>

      <el-progress
        v-if="version"
        :percentage="percentage"
        :status="isFailed ? 'exception' : isDone ? 'success' : undefined"
        :stroke-width="12"
        class="progress-bar"
      />

      <div v-if="isFailed && version?.failureReason" class="failure-reason">
        失败原因:{{ version.failureReason }}
      </div>
    </el-card>

    <!-- 阶段清单(v1 静态展示,P2 后端返回真实阶段) -->
    <el-card shadow="never" class="stages-card">
      <div class="stages-title">生成阶段</div>
      <ul class="stages-list">
        <li v-for="(stage, i) in stages" :key="i" :class="stage.state">
          <el-icon v-if="stage.state === 'done'" class="stage-icon done"><Check /></el-icon>
          <el-icon v-else-if="stage.state === 'running'" class="stage-icon running is-loading">
            <Loading />
          </el-icon>
          <el-icon v-else class="stage-icon pending"><Clock /></el-icon>
          <span class="stage-name">{{ stage.name }}</span>
          <span class="stage-desc">{{ stage.desc }}</span>
        </li>
      </ul>
    </el-card>

    <div class="action-bar">
      <el-button @click="goList">返回列表</el-button>
      <el-button v-if="isDone" type="primary" @click="goDetail">查看报告</el-button>
      <el-button v-if="isFailed" type="primary" @click="onRetry" disabled>
        重试(v1 暂未开放)
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Loading,
  Clock,
  Check,
  CircleCheckFilled,
  CircleCloseFilled
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getLatestVersionMeta, type ReportVersionMetaVO } from '@/api/presaleReport'
import { isTerminal } from '@/utils/presale/statusMeta'

const route = useRoute()
const router = useRouter()

const reportId = Number(route.params.id)
const version = ref<ReportVersionMetaVO | null>(null)
const pollTimer = ref<number | null>(null)

const POLL_INTERVAL_MS = 3000
const AUTO_JUMP_DELAY_MS = 2000

const isRunning = computed(
  () =>
    version.value?.generationStatus === 'RUNNING' ||
    version.value?.generationStatus === 'QUEUED' ||
    version.value?.generationStatus === 'INIT'
)
const isDone = computed(() => version.value?.generationStatus === 'DONE')
const isFailed = computed(() => version.value?.generationStatus === 'FAILED')

const statusText = computed(() => {
  if (!version.value) return '加载中...'
  if (isRunning.value) return '正在生成 AI 可见度诊断报告'
  if (isDone.value) return '生成完成,即将跳转详情页'
  if (isFailed.value) return '生成失败'
  return '未知状态'
})

const percentage = computed(() => {
  if (!version.value) return 0
  if (version.value.totalLlmCalls === 0) return 0
  return Math.min(
    100,
    Math.round((version.value.completedLlmCalls / version.value.totalLlmCalls) * 100)
  )
})

// v1 静态阶段列表,真实推进靠 completedLlmCalls 推算
const stages = computed(() => {
  const pct = percentage.value
  function state(start: number, end: number): 'done' | 'running' | 'pending' {
    if (pct >= end) return 'done'
    if (pct > start) return 'running'
    return 'pending'
  }
  return [
    { name: '初始化 prompt 库', desc: '< 1 秒', state: state(0, 1) },
    { name: '第一轮测试', desc: '30×11 = 330 次调用', state: state(1, 50) },
    { name: '第二轮测试', desc: '30×11 = 330 次调用', state: state(50, 83) },
    { name: '竞品识别与分析', desc: '识别 Top3 竞品', state: state(83, 92) },
    { name: '评分计算与规则引擎', desc: '5 维度评分 + 10 规则命中', state: state(92, 100) }
  ]
})

async function fetchOnce() {
  try {
    const meta = await getLatestVersionMeta(reportId)
    version.value = meta
    if (isTerminal(meta.generationStatus)) {
      stopPolling()
      if (isDone.value) {
        setTimeout(() => {
          router.push(`/admin/presale/report/${reportId}/detail`)
        }, AUTO_JUMP_DELAY_MS)
      }
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '获取生成进度失败')
    stopPolling()
  }
}

function startPolling() {
  if (pollTimer.value !== null) return
  pollTimer.value = window.setInterval(fetchOnce, POLL_INTERVAL_MS)
}

function stopPolling() {
  if (pollTimer.value !== null) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
}

function goList() {
  router.push('/admin/presale/report')
}

function goDetail() {
  router.push(`/admin/presale/report/${reportId}/detail`)
}

function onRetry() {
  ElMessage.info('重试功能 v1 暂未开放,将在 P1·F·1·b 中实现')
}

onMounted(async () => {
  await fetchOnce()
  if (!isTerminal(version.value?.generationStatus ?? '')) {
    startPolling()
  }
})

onBeforeUnmount(stopPolling)
</script>

<style scoped>
.presale-report-progress {
  padding: 16px 24px;
  max-width: 920px;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  margin: 8px 0 0 0;
  font-size: 22px;
  font-weight: 600;
}
.degrade-alert {
  margin-bottom: 16px;
}
.status-card,
.stages-card {
  margin-bottom: 16px;
}
.status-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}
.status-icon {
  font-size: 36px;
}
.status-icon.running {
  color: #409eff;
  animation: spin 2s linear infinite;
}
.status-icon.done {
  color: #67c23a;
}
.status-icon.failed {
  color: #f56c6c;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.status-main {
  font-size: 18px;
  font-weight: 500;
}
.status-sub {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
  font-family: 'JetBrains Mono', Consolas, monospace;
}
.progress-bar {
  margin-top: 12px;
}
.failure-reason {
  margin-top: 16px;
  padding: 12px 16px;
  background: #fef0f0;
  color: #f56c6c;
  border-radius: 4px;
  font-size: 13px;
}
.stages-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 12px;
}
.stages-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.stages-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
.stages-list li:last-child {
  border-bottom: none;
}
.stages-list li.pending {
  color: #909399;
}
.stage-icon {
  font-size: 16px;
}
.stage-icon.done {
  color: #67c23a;
}
.stage-icon.running {
  color: #409eff;
}
.stage-icon.pending {
  color: #c0c4cc;
}
.stage-name {
  flex: 1;
  font-size: 14px;
}
.stage-desc {
  font-size: 12px;
  color: #909399;
  font-family: 'JetBrains Mono', Consolas, monospace;
}
.action-bar {
  text-align: right;
  margin-top: 16px;
}
.action-bar .el-button + .el-button {
  margin-left: 12px;
}
.is-loading {
  animation: spin 2s linear infinite;
}
</style>
