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
            <h2>执行队列</h2>
            <p>需要优先处理的分发、半自动和失败任务。</p>
          </div>
          <el-tag :type="taskPressureType">{{ taskPressureLabel }}</el-tag>
        </div>

        <div class="signal-grid">
          <div v-for="item in taskMetrics" :key="item.key" class="signal-card">
            <span class="signal-card__label">{{ item.label }}</span>
            <strong>{{ displayNumber(item.value) }}</strong>
            <small>{{ item.hint }}</small>
          </div>
        </div>
      </div>

      <div class="workbench-panel">
        <div class="panel-heading">
          <div>
            <h2>快捷入口</h2>
            <p>运营日常处理路径。</p>
          </div>
        </div>
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
  { key: 'customers', label: '我的客户', value: overview.value?.customerCount, hint: `品牌 ${displayNumber(overview.value?.brandCount)}`, icon: 'User', color: '#2563eb', bg: '#dbeafe' },
  { key: 'projects', label: '活跃项目', value: overview.value?.activeProjectCount, hint: `全部 ${displayNumber(overview.value?.projectCount)}`, icon: 'Folder', color: '#059669', bg: '#d1fae5' },
  { key: 'reports', label: '本月报告', value: overview.value?.monthlyReportCount, hint: `文章 ${displayNumber(overview.value?.monthlyArticleCount)}`, icon: 'DataAnalysis', color: '#7c3aed', bg: '#ede9fe' },
  { key: 'risk', label: '高风险项目', value: overview.value?.highRiskProjectCount, hint: '需优先跟进', icon: 'Warning', color: '#dc2626', bg: '#fee2e2' },
])

const taskMetrics = computed(() => [
  { key: 'failed', label: '失败分发', value: overview.value?.failedDistributionTaskCount, hint: '等待排查' },
  { key: 'retry', label: '待重试', value: overview.value?.retryDistributionTaskCount, hint: '已进入重试队列' },
  { key: 'semiAuto', label: '半自动任务', value: overview.value?.semiAutoTaskCount, hint: '需要扩展处理' },
  { key: 'inFlight', label: '进行中扩展任务', value: overview.value?.inFlightExtensionTaskCount, hint: '填表/发布中' },
  { key: 'completed', label: '已完成分发', value: overview.value?.completedDistributionTaskCount, hint: '历史完成量' },
])

const taskPressure = computed(() => Number(overview.value?.failedDistributionTaskCount || 0) + Number(overview.value?.retryDistributionTaskCount || 0))
const taskPressureType = computed(() => (taskPressure.value > 0 ? 'warning' : 'success'))
const taskPressureLabel = computed(() => (taskPressure.value > 0 ? `${taskPressure.value} 项待处理` : '运行平稳'))

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

onMounted(load)
</script>

<style scoped>
@import './role-workbench.css';
</style>
