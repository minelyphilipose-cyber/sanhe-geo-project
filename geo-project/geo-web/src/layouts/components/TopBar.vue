<template>
  <header class="topbar">
    <div class="topbar__left">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path" :to="item.path">
          {{ item.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="topbar__right">
      <el-tooltip v-if="canViewAlertCenter" content="告警中心" placement="bottom">
        <el-badge :value="alertCount" :hidden="alertCount === 0" :max="99">
          <el-button text circle aria-label="告警中心" @click="openAlertCenter">
            <el-icon :size="18"><Bell /></el-icon>
          </el-button>
        </el-badge>
      </el-tooltip>

      <el-dropdown trigger="click" @command="handleCommand">
        <div class="topbar__user">
          <div class="topbar__avatar">
            <img
              v-if="showAvatarImage"
              :key="userStore.avatarUrl"
              :src="userStore.avatarUrl"
              alt=""
              class="topbar__avatar-image"
              @load="avatarLoadFailed = false"
              @error="avatarLoadFailed = true"
            />
            <span v-else>{{ avatarLetter }}</span>
          </div>
          <span class="topbar__name">{{ userStore.displayName }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>
              <span class="text-xs text-gray-400">{{ roleLabel }}</span>
            </el-dropdown-item>
            <el-dropdown-item command="profile">个人中心</el-dropdown-item>
            <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { Bell, ArrowDown } from '@element-plus/icons-vue'
import type { RoleType } from '@/types'

const ROLE_LABELS: Record<RoleType, string> = {
  super_admin: '超级管理员',
  manager: '管理者',
  delivery_manager: '交付负责人',
  operator: '运营',
  sales: '销售',
  partner: '合伙人主账号',
  partner_staff: '合伙人员工',
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const alertCount = ref(0)
const avatarLoadFailed = ref(false)

const avatarLetter = computed(() =>
  (userStore.displayName || 'U').charAt(0).toUpperCase(),
)
const showAvatarImage = computed(() => !!userStore.avatarUrl && !avatarLoadFailed.value)
const canViewAlertCenter = computed(() =>
  userStore.hasPermission(['content.distribution.retry', 'dispatch.alert.resolve', 'system.alert.resolve']),
)

const roleLabel = computed(() => {
  const r = userStore.role
  return r ? ROLE_LABELS[r] ?? r : ''
})

const breadcrumbs = computed(() => {
  return route.matched
    .filter((r) => r.meta?.title)
    .map((r) => ({
      path: r.path,
      title: r.meta.title as string,
    }))
})

watch(() => userStore.avatarUrl, () => {
  avatarLoadFailed.value = false
})

async function openAlertCenter() {
  await router.push('/admin/alerts')
}

async function handleCommand(cmd: string) {
  if (cmd === 'profile') {
    await router.push(userStore.isPartner ? '/partner/profile' : '/admin/profile')
    return
  }
  if (cmd === 'logout') {
    await userStore.logout()
    await router.replace('/login')
    if (router.currentRoute.value.path !== '/login') {
      window.location.assign('/login')
    }
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
  border-bottom: 1px solid #e2e8f0;
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
  background: #f1f5f9;
}

.topbar__avatar {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  overflow: hidden;
}

.topbar__avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.topbar__name {
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
}
</style>
