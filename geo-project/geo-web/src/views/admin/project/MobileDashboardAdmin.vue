<template>
  <div class="admin-page mobile-dashboard-admin-page">
    <el-page-header content="移动数据看板" @back="goBack" />

    <section class="mobile-dashboard-admin-hero">
      <div>
        <span>项目级移动 H5 看板</span>
        <h1>移动数据看板</h1>
        <p>集中管理客户侧分享链接与运行观测，项目详情页仅保留入口。</p>
        <ul>
          <li>分享链接</li>
          <li>访问风险</li>
          <li>裁判成本</li>
        </ul>
      </div>
      <el-button type="primary" plain @click="goProjectDetail">返回项目详情</el-button>
    </section>

    <MobileDashboardSharePanel
      :project-id="projectId"
      :editable="canManageMobileShare"
    />

    <MobileDashboardOpsPanel
      :project-id="projectId"
      :editable="canManageMobileShare"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import MobileDashboardSharePanel from './MobileDashboardSharePanel.vue'
import MobileDashboardOpsPanel from './MobileDashboardOpsPanel.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const projectId = Number(route.params.id)

const canManageMobileShare = computed(() => !userStore.isSales && userStore.hasPermission('project.report.export'))

function goBack() {
  router.back()
}

function goProjectDetail() {
  router.push(`/admin/projects/${projectId}`)
}
</script>

<style scoped>
.mobile-dashboard-admin-page {
  display: grid;
  gap: 20px;
  padding-bottom: 24px;
}

.mobile-dashboard-admin-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  position: relative;
  overflow: hidden;
  padding: 22px 24px;
  background:
    radial-gradient(circle at 92% 18%, rgba(7, 166, 107, 0.16), transparent 28%),
    linear-gradient(135deg, #f8fbff 0%, #f1fff8 100%);
  border: 1px solid #e5eef7;
  border-radius: 16px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.mobile-dashboard-admin-hero div {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.mobile-dashboard-admin-hero span {
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
}

.mobile-dashboard-admin-hero h1 {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
  line-height: 1.25;
}

.mobile-dashboard-admin-hero p {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.mobile-dashboard-admin-hero ul {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.mobile-dashboard-admin-hero li {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(7, 166, 107, 0.1);
  color: #047857;
  font-size: 12px;
  font-weight: 700;
}

@media (max-width: 720px) {
  .mobile-dashboard-admin-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
