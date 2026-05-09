<template>
  <div class="sidebar" :class="{ 'sidebar--collapsed': collapsed }">
    <div class="sidebar__logo">
      <img
        class="sidebar__brand"
        :class="{ 'sidebar__brand--collapsed': collapsed }"
        :src="collapsed ? logoSymbolUrl : logoHorizontalUrl"
        alt="幻境AI FANTASY GEO"
      >
    </div>

    <el-scrollbar class="sidebar__menu-wrap">
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="transparent"
        text-color="#94A3B8"
        active-text-color="#FFFFFF"
        router
      >
        <template v-for="group in visibleGroups" :key="group.key">
          <li v-if="group.title && !collapsed" class="sidebar__group-title">{{ group.title }}</li>
          <template v-for="item in group.menus" :key="item.name">
            <el-menu-item :index="item.path">
              <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
              <template #title>
                <div class="sidebar__menu-title">
                  <span>{{ item.title }}</span>
                  <span
                    v-if="item.badgeCount && item.badgeCount > 0"
                    class="sidebar__badge"
                  >
                    {{ item.badgeCount > 99 ? '99+' : item.badgeCount }}
                  </span>
                </div>
              </template>
            </el-menu-item>
          </template>
        </template>
      </el-menu>
    </el-scrollbar>

    <div class="sidebar__footer" @click="$emit('toggle')">
      <el-icon :size="18">
        <component :is="collapsed ? 'Expand' : 'Fold'" />
      </el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import logoHorizontalUrl from '@/assets/brand/logo-horizontal.svg'
import logoSymbolUrl from '@/assets/brand/logo-symbol.svg'
import { useUserStore } from '@/stores/user'
import type { RoleType } from '@/types'

interface MenuItem {
  path: string
  name: string
  title: string
  icon?: string
  roles?: RoleType[]
  permissions?: string[]
  badgeCount?: number
}

interface MenuGroup {
  key: string
  title?: string
  menus: MenuItem[]
}

const props = defineProps<{
  collapsed: boolean
  groups: MenuGroup[]
}>()

defineEmits<{
  toggle: []
}>()

const route = useRoute()
const userStore = useUserStore()

const flattenMenus = computed(() => props.groups.flatMap((g) => g.menus))

const activeMenu = computed(() => {
  const matched = route.matched
  for (let i = matched.length - 1; i >= 0; i--) {
    const path = matched[i].path
    const found = flattenMenus.value.find((m) => {
      const purePath = m.path.split('?')[0]
      return path.startsWith(purePath)
    })
    if (found) return found.path
  }
  return route.path
})

const visibleGroups = computed(() =>
  props.groups
    .map((group) => ({
      ...group,
      menus: group.menus.filter((m) => {
        const rolePass = !m.roles || m.roles.length === 0 || userStore.hasRole(m.roles)
        const permPass = !m.permissions || m.permissions.length === 0 || userStore.hasPermission(m.permissions)
        return rolePass && permPass
      }),
    }))
    .filter((group) => group.menus.length > 0),
)
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  height: 100vh;
  background: linear-gradient(180deg, #0f172a 0%, #020617 100%);
  display: flex;
  flex-direction: column;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: fixed;
  left: 0;
  top: 0;
  z-index: 100;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
}

.sidebar--collapsed {
  width: var(--sidebar-collapsed-width);
}

.sidebar__logo {
  height: var(--header-height);
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.sidebar--collapsed .sidebar__logo {
  justify-content: center;
  padding: 0;
}

.sidebar__brand {
  width: 188px;
  height: 48px;
  object-fit: contain;
  object-position: left center;
  flex-shrink: 0;
}

.sidebar__brand--collapsed {
  width: 36px;
  height: 36px;
  object-position: center;
}

.sidebar__menu-wrap {
  flex: 1;
  overflow: hidden;
  padding-top: 8px;
}

.sidebar__group-title {
  margin: 10px 12px 6px;
  padding: 0 10px;
  list-style: none;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  letter-spacing: 0.02em;
  text-transform: uppercase;
}

.sidebar__menu-title {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.sidebar__badge {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: #f87171;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
  text-align: center;
  display: inline-block;
  flex-shrink: 0;
}

:deep(.el-menu) {
  border-right: none !important;
  padding: 0 8px;
}

:deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  margin-bottom: 2px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
}

:deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.06) !important;
}

:deep(.el-menu-item.is-active) {
  background: rgba(37, 99, 235, 0.25) !important;
  color: #ffffff !important;
}

.sidebar__footer {
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  cursor: pointer;
  color: #64748b;
  transition: color 0.2s;
  flex-shrink: 0;
}

.sidebar__footer:hover {
  color: #e2e8f0;
}

</style>
