<template>
  <div class="session-expired-wrap">
    <el-result
      icon="error"
      title="登录已过期"
      sub-title="当前登录状态已失效，请重新登录后继续操作。"
    >
      <template #extra>
        <el-button type="primary" @click="goLogin">前往登录</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

function goLogin() {
  const redirect = typeof route.query.redirect === 'string' && route.query.redirect
    ? route.query.redirect
    : '/'
  router.push(`/login?redirect=${encodeURIComponent(redirect)}`)
}
</script>

<style scoped>
.session-expired-wrap {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
