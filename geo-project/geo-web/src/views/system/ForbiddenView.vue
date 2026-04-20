<template>
  <div class="forbidden-wrap">
    <el-result
      icon="warning"
      title="403 无权限访问"
      sub-title="当前账号没有访问该页面或执行该操作的权限。"
    >
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { resolvePostLoginPath } from '@/utils/navigation'

const router = useRouter()
const userStore = useUserStore()

function goHome() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  const target = resolvePostLoginPath({
    isPartner: userStore.isPartner,
    hasPermission: userStore.hasPermission,
    hasRole: userStore.hasRole,
  })
  router.push(target || '/login')
}
</script>

<style scoped>
.forbidden-wrap {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
