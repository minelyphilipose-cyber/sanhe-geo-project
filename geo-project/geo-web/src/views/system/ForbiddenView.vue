<template>
  <div class="forbidden-wrap">
    <el-result icon="warning" title="403 无权限访问" sub-title="当前账号没有访问该页面或操作该功能的权限。">
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

function goHome() {
  router.push(userStore.isPartner ? '/partner/home' : '/admin/overview')
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
