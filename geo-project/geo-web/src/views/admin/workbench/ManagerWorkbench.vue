<template>
  <div class="role-workbench">
    <header class="role-workbench__header">
      <div>
        <p class="role-workbench__eyebrow">Manager Workbench</p>
        <h1 class="role-workbench__title">系统工作台</h1>
        <p class="role-workbench__subtitle">系统配置、权限治理和系统告警的集中入口。</p>
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
            <h2>系统告警</h2>
            <p>仅展示系统告警链路，不混入交付告警。</p>
          </div>
          <el-tag :type="systemAlertType">{{ systemAlertLabel }}</el-tag>
        </div>

        <div v-if="latestAlerts.length === 0" class="empty-state">暂无待处理系统告警</div>
        <div v-else class="alert-list">
          <div v-for="alert in latestAlerts" :key="alert.id" class="alert-item">
            <span class="alert-item__severity" :class="severityClass(alert.severity)"></span>
            <div>
              <strong>{{ alert.message || alert.alertType }}</strong>
              <p>{{ alert.source || 'system' }} · {{ severityLabel(alert.severity) }}</p>
            </div>
          </div>
        </div>
      </div>

      <div class="workbench-panel">
        <div class="panel-heading">
          <div>
            <h2>配置入口</h2>
            <p>系统管理员日常维护。</p>
          </div>
        </div>
        <div class="quick-actions">
          <el-button @click="router.push('/admin/settings/users')">用户与权限</el-button>
          <el-button @click="router.push('/admin/settings/platforms')">AI 平台配置</el-button>
          <el-button @click="router.push('/admin/settings/packages')">套餐配置</el-button>
          <el-button @click="router.push('/admin/settings/dicts')">字典中心</el-button>
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
import { getManagerWorkbenchOverview, type ManagerWorkbenchOverview } from '@/api/workbench'
import type { SystemAlertTodoItem } from '@/types'

const router = useRouter()
const loading = ref(false)
const overview = ref<ManagerWorkbenchOverview | null>(null)

const metrics = computed(() => [
  { key: 'users', label: '启用用户', value: overview.value?.activeUserCount, hint: `运营 ${displayNumber(overview.value?.activeOperatorCount)}`, icon: 'User', color: '#2563eb', bg: '#dbeafe' },
  { key: 'alerts', label: '系统告警', value: overview.value?.openSystemAlertCount, hint: `高优先级 ${displayNumber(overview.value?.highSeveritySystemAlertCount)}`, icon: 'Bell', color: '#dc2626', bg: '#fee2e2' },
  { key: 'platforms', label: 'AI 平台', value: overview.value?.aiPlatformConfigCount, hint: `站点 ${displayNumber(overview.value?.publishSiteCount)}`, icon: 'Setting', color: '#059669', bg: '#d1fae5' },
  { key: 'permissions', label: '有效权限', value: overview.value?.permissionCount, hint: 'active + deprecated', icon: 'Key', color: '#7c3aed', bg: '#ede9fe' },
])

const latestAlerts = computed<SystemAlertTodoItem[]>(() => overview.value?.latestSystemAlerts || [])
const systemAlertType = computed(() => Number(overview.value?.openSystemAlertCount || 0) > 0 ? 'warning' : 'success')
const systemAlertLabel = computed(() => Number(overview.value?.openSystemAlertCount || 0) > 0 ? `${overview.value?.openSystemAlertCount} 条待处理` : '运行平稳')

async function load() {
  loading.value = true
  try {
    const res = await getManagerWorkbenchOverview()
    overview.value = res.data.data
  } catch {
    ElMessage.error('系统工作台加载失败')
  } finally {
    loading.value = false
  }
}

function displayNumber(value?: number | null) {
  return value == null ? '--' : value.toLocaleString()
}

function severityClass(value?: string | null) {
  if (value === 'critical' || value === 'error') return 'is-danger'
  if (value === 'warn') return 'is-warning'
  return 'is-info'
}

function severityLabel(value?: string | null) {
  const map: Record<string, string> = { critical: '严重', error: '错误', warn: '警告', info: '信息' }
  return value ? map[value] || value : '信息'
}

onMounted(load)
</script>

<style scoped>
@import './role-workbench.css';
</style>
