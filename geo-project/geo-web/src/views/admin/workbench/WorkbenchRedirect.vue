<template>
  <div class="workbench-redirect">
    <el-icon class="workbench-redirect__icon" :size="22">
      <Loading />
    </el-icon>
    <span>正在进入工作台...</span>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

function resolveWorkbenchPath(): string {
  if (userStore.hasRole(['super_admin'])) {
    return '/admin/workbench/super-admin'
  }
  if (userStore.hasPermission('delivery.overview.read')) {
    return '/admin/workbench/delivery'
  }
  if (userStore.hasPermission('workbench.manager.read')) {
    return '/admin/workbench/manager'
  }
  if (userStore.hasPermission('workbench.operator.read')) {
    return '/admin/workbench/operator'
  }
  if (userStore.hasPermission('workbench.sales.read')) {
    return '/admin/workbench/sales'
  }
  return '/admin/no-workbench'
}

onMounted(() => {
  router.replace(resolveWorkbenchPath())
})
</script>

<style scoped>
.workbench-redirect {
  min-height: 56vh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #64748b;
  font-size: 14px;
}

.workbench-redirect__icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
