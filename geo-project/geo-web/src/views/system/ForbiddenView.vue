<template>
  <div class="forbidden-wrap">
    <el-result :icon="isSessionExpired ? 'error' : 'warning'" :title="title" :sub-title="subTitle">
      <template #extra>
        <el-button v-if="isSessionExpired" type="primary" @click="goLogin">前往登录</el-button>
        <el-button v-else type="primary" @click="goHome">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { resolvePostLoginPath } from '@/utils/navigation'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isSessionExpired = computed(() => route.query.reason === 'session_expired')
const title = computed(() => (isSessionExpired.value ? '登录已过期' : '403 无权限访问'))
const subTitle = computed(() =>
  isSessionExpired.value
    ? '当前登录状态已失效，请重新登录后继续操作。'
    : '当前账号没有访问该页面或执行该操作的权限。',
)

function goLogin() {
  router.push('/login')
}

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
