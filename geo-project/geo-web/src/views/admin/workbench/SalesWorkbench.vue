<template>
  <div class="role-workbench">
    <header class="role-workbench__header">
      <div>
        <p class="role-workbench__eyebrow">Sales Workbench</p>
        <h1 class="role-workbench__title">销售工作台</h1>
        <p class="role-workbench__subtitle">聚焦归属客户与本人创建的 AI 可见度诊断报告。</p>
      </div>
      <el-button type="primary" plain :loading="loading" @click="load">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </header>

    <section class="metric-grid">
      <article v-for="item in metrics" :key="item.key" class="metric-card">
        <div class="metric-card__icon" :style="{ color: item.color, backgroundColor: item.bg }">
          <el-icon><component :is="item.icon" /></el-icon>
        </div>
        <div>
          <p class="metric-card__label">{{ item.label }}</p>
          <p class="metric-card__value">{{ displayNumber(item.value) }}</p>
          <p class="metric-card__hint">{{ item.hint }}</p>
        </div>
      </article>
    </section>

    <section class="workbench-grid">
      <div class="workbench-panel workbench-panel--large">
        <div class="panel-heading">
          <div>
            <h2>报告待办</h2>
            <p>本人创建且需要处理的诊断报告事项。</p>
          </div>
          <el-tag :type="reportPressureType">{{ reportPressureLabel }}</el-tag>
        </div>

        <div v-if="priorityTodos.length === 0" class="empty-state">暂无需要优先处理的报告待办</div>
        <div v-else class="todo-list">
          <div v-for="todo in priorityTodos" :key="todoKey(todo)" class="todo-item">
            <span class="todo-item__dot" :class="todoDotClass(todo.severity)"></span>
            <div class="todo-item__body">
              <strong>{{ todo.message || '待处理事项' }}</strong>
              <p>{{ todoMeta(todo) }}</p>
            </div>
            <el-button v-if="todo.route" link type="primary" @click="router.push(todo.route)">处理</el-button>
          </div>
        </div>
      </div>

      <div class="workbench-panel">
        <div class="panel-heading">
          <div>
            <h2>诊断报告</h2>
            <p>本人创建的 AI 可见度诊断报告进展。</p>
          </div>
        </div>
        <div class="signal-grid signal-grid--compact">
          <div v-for="item in reportMetrics" :key="item.key" class="signal-card">
            <span class="signal-card__label">{{ item.label }}</span>
            <strong>{{ displayNumber(item.value) }}</strong>
            <small>{{ item.hint }}</small>
          </div>
        </div>
        <div class="quick-actions">
          <el-button @click="router.push('/admin/customers')">我的客户</el-button>
          <el-button type="primary" @click="router.push('/admin/presale/report')">诊断报告</el-button>
          <el-button @click="router.push('/admin/presale/report/create')">新建诊断报告</el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getSalesWorkbenchOverview, type SalesWorkbenchOverview } from '@/api/workbench'

const router = useRouter()
const loading = ref(false)
const overview = ref<SalesWorkbenchOverview | null>(null)

const metrics = computed(() => [
  { key: 'customers', label: '归属客户', value: overview.value?.customerCount, hint: `已签约 ${displayNumber(overview.value?.signedCustomerCount)}`, icon: 'User', color: '#2563eb', bg: '#dbeafe' },
  { key: 'potential', label: '潜在客户', value: overview.value?.potentialCustomerCount, hint: '待跟进客户', icon: 'Aim', color: '#f59e0b', bg: '#fffbeb' },
  { key: 'reports', label: '诊断报告', value: overview.value?.reportCount, hint: `本月新增 ${displayNumber(overview.value?.monthlyReportCount)}`, icon: 'Document', color: '#7c3aed', bg: '#ede9fe' },
  { key: 'failed', label: '异常报告', value: overview.value?.failedReportCount, hint: '需要重新处理', icon: 'Warning', color: '#dc2626', bg: '#fee2e2' },
])

const reportMetrics = computed(() => [
  { key: 'generating', label: '生成中', value: overview.value?.generatingReportCount, hint: '排队或运行中' },
  { key: 'done', label: '已完成', value: overview.value?.doneReportCount, hint: '可查看/编辑/导出' },
  { key: 'failed', label: '生成失败', value: overview.value?.failedReportCount, hint: '可重试或重新生成' },
])

const reportPressure = computed(() => Number(overview.value?.failedReportCount || 0))
const reportPressureType = computed(() => (reportPressure.value > 0 ? 'warning' : 'success'))
const reportPressureLabel = computed(() => (reportPressure.value > 0 ? `${reportPressure.value} 份异常` : '运行平稳'))
const priorityTodos = computed(() => overview.value?.priorityTodos || [])

async function load() {
  loading.value = true
  try {
    const res = await getSalesWorkbenchOverview()
    overview.value = res.data.data
  } catch {
    ElMessage.error('销售工作台加载失败')
  } finally {
    loading.value = false
  }
}

function displayNumber(value?: number | null) {
  return value == null ? '--' : value.toLocaleString()
}

function todoDotClass(severity?: string | null) {
  if (severity === 'critical' || severity === 'high' || severity === 'error') return 'todo-item__dot--high'
  if (severity === 'warn' || severity === 'warning') return 'todo-item__dot--medium'
  return 'todo-item__dot--normal'
}

function todoMeta(todo: { brandName?: string | null; severity?: string | null }) {
  return `${todo.brandName || '未归属品牌'} · ${severityLabel(todo.severity)}`
}

function severityLabel(value?: string | null) {
  const map: Record<string, string> = { critical: '严重', high: '高优先级', error: '错误', warn: '提醒', warning: '提醒', info: '信息' }
  return value ? map[value] || value : '信息'
}

function todoKey(todo: { sourceType?: string | null; id?: number | null }) {
  return `${todo.sourceType || 'todo'}-${todo.id || 'new'}`
}

onMounted(load)
</script>

<style scoped>
@import './role-workbench.css';
</style>
