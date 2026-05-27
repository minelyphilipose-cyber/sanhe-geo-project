<template>
  <div class="role-workbench">
    <header class="role-workbench__header">
      <div>
        <p class="role-workbench__eyebrow">Super Admin Console</p>
        <h1 class="role-workbench__title">全局总控</h1>
        <p class="role-workbench__subtitle">最高权限兜底视角，集中查看系统级风险信号。</p>
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
            <h2>全局风险信号</h2>
            <p>用于发现迁移遗漏、脏数据和仍在生效的历史权限。</p>
          </div>
          <el-tag :type="riskType">{{ riskLabel }}</el-tag>
        </div>
        <div class="risk-list">
          <div class="risk-item">
            <strong>NULL owner 客户</strong>
            <p>{{ displayNumber(overview?.nullOwnerCompanyCount) }} 个客户需要确认负责人。</p>
          </div>
          <div class="risk-item">
            <strong>deprecated 仍生效权限</strong>
            <p>{{ displayNumber(overview?.deprecatedEffectivePermissionCount) }} 个历史权限仍绑定角色。</p>
          </div>
          <div class="risk-item">
            <strong>系统告警</strong>
            <p>{{ displayNumber(overview?.openSystemAlertCount) }} 条系统告警尚未处理。</p>
          </div>
        </div>
      </div>

      <div class="workbench-panel">
        <div class="panel-heading">
          <div>
            <h2>兜底入口</h2>
            <p>最高权限审计与维护。</p>
          </div>
        </div>
        <div class="quick-actions">
          <el-button @click="router.push('/admin/workbench/delivery')">交付视角</el-button>
          <el-button @click="router.push('/admin/workbench/manager')">系统视角</el-button>
          <el-button @click="router.push('/admin/activity-logs')">操作日志</el-button>
          <el-button @click="router.push('/admin/settings/users')">用户与权限</el-button>
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
import { getSuperAdminWorkbenchOverview, type SuperAdminWorkbenchOverview } from '@/api/workbench'

const router = useRouter()
const loading = ref(false)
const overview = ref<SuperAdminWorkbenchOverview | null>(null)

const metrics = computed(() => [
  { key: 'users', label: '全局用户', value: overview.value?.totalUserCount, hint: `启用 ${displayNumber(overview.value?.activeUserCount)}`, icon: 'User', color: '#2563eb', bg: '#dbeafe' },
  { key: 'companies', label: '全局客户', value: overview.value?.totalCompanyCount, hint: `项目 ${displayNumber(overview.value?.totalProjectCount)}`, icon: 'OfficeBuilding', color: '#059669', bg: '#d1fae5' },
  { key: 'nullOwner', label: 'NULL owner', value: overview.value?.nullOwnerCompanyCount, hint: '客户负责人缺失', icon: 'Warning', color: '#dc2626', bg: '#fee2e2' },
  { key: 'deprecated', label: '历史权限', value: overview.value?.deprecatedEffectivePermissionCount, hint: 'deprecated 仍绑定', icon: 'Key', color: '#7c3aed', bg: '#ede9fe' },
])

const riskTotal = computed(() =>
  Number(overview.value?.nullOwnerCompanyCount || 0)
  + Number(overview.value?.deprecatedEffectivePermissionCount || 0)
  + Number(overview.value?.openSystemAlertCount || 0),
)
const riskType = computed(() => riskTotal.value > 0 ? 'warning' : 'success')
const riskLabel = computed(() => riskTotal.value > 0 ? `${riskTotal.value} 项需关注` : '暂无风险')

async function load() {
  loading.value = true
  try {
    const res = await getSuperAdminWorkbenchOverview()
    overview.value = res.data.data
  } catch {
    ElMessage.error('全局总控加载失败')
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
