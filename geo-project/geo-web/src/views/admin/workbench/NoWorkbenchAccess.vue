<template>
  <div class="no-workbench">
    <el-result
      icon="info"
      title="暂无可用工作台"
      sub-title="当前角色暂未配置专属工作台，请从左侧可用菜单进入系统，或联系管理员确认账号权限。"
    >
      <template #extra>
        <el-button type="primary" @click="goFirstAvailable">查看可用功能</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

function goFirstAvailable() {
  if (userStore.hasPermission('presale.report.list')) {
    router.push('/admin/presale/report')
    return
  }
  if (userStore.hasPermission('company.read')) {
    router.push('/admin/customers')
    return
  }
  if (userStore.hasPermission('project.read')) {
    router.push('/admin/projects')
    return
  }
  router.push('/403')
}
</script>

<style scoped>
.no-workbench {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
