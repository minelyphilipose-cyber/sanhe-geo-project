<template>
  <header class="topbar">
    <!-- 左侧: 面包屑 -->
    <div class="topbar__left">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item
          v-for="item in breadcrumbs"
          :key="item.path"
          :to="item.path"
        >
          {{ item.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 右侧: 异常提示 + 用户信息 -->
    <div class="topbar__right">
      <!-- 异常告警快捷入口 -->
      <el-badge :value="alertCount" :hidden="alertCount === 0" :max="99">
        <el-button
          text
          circle
          @click="$router.push('/admin/alerts')"
        >
          <el-icon :size="18"><Bell /></el-icon>
        </el-button>
      </el-badge>

      <!-- 用户下拉 -->
      <el-dropdown trigger="click" @command="handleCommand">
        <div class="topbar__user">
          <div class="topbar__avatar">
            {{ avatarLetter }}
          </div>
          <span class="topbar__name">{{ userStore.displayName }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>
              <span class="text-xs text-gray-400">
                {{ roleLabel }}
              </span>
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ROLE_MAP } from '@/utils/constants'
import { Bell, ArrowDown } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// TODO: 从后端轮询未解决告警数量
const alertCount = ref(0)

const avatarLetter = computed(() =>
  (userStore.displayName || 'U').charAt(0).toUpperCase()
)

const roleLabel = computed(() => {
  const r = userStore.role
  return r ? ROLE_MAP[r]?.label ?? r : ''
})

const breadcrumbs = computed(() => {
  return route.matched
    .filter((r) => r.meta?.title)
    .map((r) => ({
      path: r.path,
      title: r.meta.title as string,
    }))
})

function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.topbar {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid #E2E8F0;
  position: sticky;
  top: 0;
  z-index: 50;
}

.topbar__left {
  display: flex;
  align-items: center;
}

.topbar__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.topbar__user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s;
}

.topbar__user:hover {
  background: #F1F5F9;
}

.topbar__avatar {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: linear-gradient(135deg, #2563EB, #1D4ED8);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
}

.topbar__name {
  font-size: 14px;
  font-weight: 500;
  color: #1E293B;
}
</style>
