<template>
  <div class="role-workbench">
    <header class="role-workbench__header">
      <div>
        <p class="role-workbench__eyebrow">Operator Workbench</p>
        <h1 class="role-workbench__title">运营工作台</h1>
        <p class="role-workbench__subtitle">今天需要关注的客户资产、项目进展和执行任务。</p>
      </div>
      <el-button type="primary" plain :loading="loading" @click="load">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </header>

    <section class="metric-grid">
      <article v-for="item in topMetrics" :key="item.key" class="metric-card">
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
            <h2>今日待办</h2>
            <p>按紧急程度聚合需要运营处理的客户事项。</p>
          </div>
          <el-tag :type="todoPressureType">{{ todoPressureLabel }}</el-tag>
        </div>

        <div v-if="priorityTodos.length === 0" class="empty-state">暂无需要优先处理的待办</div>
        <div v-else class="todo-list">
          <div v-for="todo in priorityTodos" :key="todoKey(todo)" class="todo-item">
            <span class="todo-item__dot" :class="todoDotClass(todo.severity)"></span>
            <div class="todo-item__body">
              <strong>{{ todo.message || todo.alertType || '待处理事项' }}</strong>
              <p>{{ todoMeta(todo) }}</p>
            </div>
            <el-button v-if="todo.route" link type="primary" @click="router.push(todo.route)">处理</el-button>
          </div>
        </div>
      </div>

      <div class="workbench-panel">
        <div class="panel-heading">
          <div>
            <h2>客户风险</h2>
            <p>按客户和品牌归并待办事项。</p>
          </div>
        </div>
        <div v-if="customerRiskGroups.length" class="risk-list">
          <div v-for="group in customerRiskGroups" :key="riskGroupKey(group)" class="risk-item">
            <strong>{{ riskGroupTitle(group) }}</strong>
            <p>{{ group.riskCount }} 项待处理 · 高优先级 {{ group.highSeverityCount }}</p>
            <small>{{ group.latestMessage }}</small>
          </div>
        </div>
        <div v-else class="empty-state empty-state--compact">暂无客户风险</div>
        <div class="quick-actions">
          <el-button @click="router.push('/admin/customers')">客户管理</el-button>
          <el-button @click="router.push('/admin/brands')">品牌管理</el-button>
          <el-button @click="router.push('/admin/projects')">项目管理</el-button>
          <el-button @click="router.push('/admin/content/execution')">内容执行</el-button>
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
import { getOperatorWorkbenchOverview, type OperatorWorkbenchOverview } from '@/api/workbench'

const router = useRouter()
const loading = ref(false)
const overview = ref<OperatorWorkbenchOverview | null>(null)

const topMetrics = computed(() => [
  { key: 'todos', label: '我的待办', value: overview.value?.openTodoCount, hint: `高优先级 ${displayNumber(overview.value?.highSeverityTodoCount)}`, icon: 'Bell', color: '#dc2626', bg: '#fee2e2' },
  { key: 'customers', label: '我的客户', value: overview.value?.customerCount, hint: `品牌 ${displayNumber(overview.value?.brandCount)}`, icon: 'User', color: '#2563eb', bg: '#dbeafe' },
  { key: 'projects', label: '活跃项目', value: overview.value?.activeProjectCount, hint: `全部 ${displayNumber(overview.value?.projectCount)}`, icon: 'Folder', color: '#059669', bg: '#d1fae5' },
  { key: 'reports', label: '本月报告', value: overview.value?.monthlyReportCount, hint: `文章 ${displayNumber(overview.value?.monthlyArticleCount)}`, icon: 'DataAnalysis', color: '#7c3aed', bg: '#ede9fe' },
])

const priorityTodos = computed(() => overview.value?.priorityTodos || [])
const customerRiskGroups = computed(() => overview.value?.customerRiskGroups || [])
const todoPressure = computed(() => Number(overview.value?.openTodoCount || 0))
const todoPressureType = computed(() => (todoPressure.value > 0 ? 'warning' : 'success'))
const todoPressureLabel = computed(() => (todoPressure.value > 0 ? `${todoPressure.value} 项待处理` : '今日平稳'))

async function load() {
  loading.value = true
  try {
    const res = await getOperatorWorkbenchOverview()
    overview.value = res.data.data
  } catch {
    ElMessage.error('运营工作台加载失败')
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

function todoMeta(todo: { customerName?: string | null; brandName?: string | null; severity?: string | null }) {
  const owner = [todo.customerName, todo.brandName].filter(Boolean).join(' / ')
  return `${owner || '未归属客户'} · ${severityLabel(todo.severity)}`
}

function todoKey(todo: { sourceType?: string | null; id?: number | null }) {
  return `${todo.sourceType || 'todo'}-${todo.id || 'new'}`
}

function severityLabel(value?: string | null) {
  const map: Record<string, string> = { critical: '严重', high: '高优先级', error: '错误', warn: '提醒', warning: '提醒', info: '信息' }
  return value ? map[value] || value : '信息'
}

function riskGroupKey(group: { customerName?: string | null; brandName?: string | null }) {
  return `${group.customerName || 'unknown'}-${group.brandName || 'unknown'}`
}

function riskGroupTitle(group: { customerName?: string | null; brandName?: string | null }) {
  return [group.customerName || '未知客户', group.brandName].filter(Boolean).join(' / ')
}

onMounted(load)
</script>

<style scoped>
@import './role-workbench.css';
</style>
