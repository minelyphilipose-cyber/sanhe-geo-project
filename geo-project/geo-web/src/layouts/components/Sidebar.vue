<template>
  <div class="sidebar" :class="{ 'sidebar--collapsed': collapsed }">
    <div class="sidebar__ambient sidebar__ambient--top"></div>
    <div class="sidebar__ambient sidebar__ambient--bottom"></div>

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
          <li v-if="group.title && !collapsed" class="sidebar__group-title">
            <span>{{ group.title }}</span>
          </li>
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
      <div class="sidebar__toggle" :class="{ 'sidebar__toggle--collapsed': collapsed }">
        <el-icon :size="18">
          <component :is="collapsed ? 'Expand' : 'Fold'" />
        </el-icon>
        <span v-if="!collapsed">收起菜单</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import logoHorizontalUrl from '@/assets/brand/logo-dark.svg'
import logoSymbolUrl from '@/assets/brand/logo-icon.svg'
import { useUserStore } from '@/stores/user'
import type { RoleType } from '@/types'

interface MenuItem {
  path: string
  name: string
  title: string
  icon?: string
  roles?: RoleType[]
  excludeRoles?: RoleType[]
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
        const excludePass = !m.excludeRoles || m.excludeRoles.length === 0 || !userStore.hasRole(m.excludeRoles)
        const permPass = !m.permissions || m.permissions.length === 0 || userStore.hasPermission(m.permissions)
        return rolePass && excludePass && permPass
      }),
    }))
    .filter((group) => group.menus.length > 0),
)
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  height: 100vh;
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.92) 0%, rgba(2, 6, 23, 0.98) 100%),
    radial-gradient(circle at 30% 0%, rgba(37, 99, 235, 0.32), transparent 34%),
    #020617;
  display: flex;
  flex-direction: column;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: fixed;
  left: 0;
  top: 0;
  z-index: 100;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  overflow: hidden;
  box-shadow: 18px 0 44px rgba(15, 23, 42, 0.08);
}

.sidebar--collapsed {
  width: var(--sidebar-collapsed-width);
}

.sidebar__ambient {
  position: absolute;
  pointer-events: none;
  border-radius: 999px;
  filter: blur(4px);
}

.sidebar__ambient--top {
  width: 180px;
  height: 180px;
  top: -96px;
  left: -54px;
  background: rgba(59, 130, 246, 0.22);
}

.sidebar__ambient--bottom {
  width: 160px;
  height: 160px;
  right: -86px;
  bottom: 64px;
  background: rgba(14, 165, 233, 0.13);
}

.sidebar__logo {
  min-height: 72px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}

.sidebar--collapsed .sidebar__logo {
  justify-content: center;
  padding: 0;
  min-height: var(--header-height);
}

.sidebar__brand {
  width: 178px;
  height: 39px;
  object-fit: contain;
  object-position: center;
  flex-shrink: 0;
  filter: drop-shadow(0 10px 24px rgba(15, 23, 42, 0.3));
}

.sidebar__brand--collapsed {
  width: 36px;
  height: 36px;
  object-position: center;
}

.sidebar__menu-wrap {
  flex: 1;
  overflow: hidden;
  padding: 12px 0 10px;
  position: relative;
  z-index: 1;
}

.sidebar__group-title {
  margin: 16px 12px 7px;
  padding: 0 9px;
  list-style: none;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
}

.sidebar__group-title span {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sidebar__group-title span::before {
  content: "";
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #2563eb;
  box-shadow: 0 0 12px rgba(37, 99, 235, 0.8);
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
  height: 19px;
  padding: 0 6px;
  border-radius: 999px;
  background: linear-gradient(135deg, #ef4444, #f97316);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 19px;
  text-align: center;
  display: inline-block;
  flex-shrink: 0;
  box-shadow: 0 8px 18px rgba(239, 68, 68, 0.22);
}

:deep(.el-menu) {
  border-right: none !important;
  padding: 0 10px;
  background: transparent !important;
}

:deep(.el-menu--collapse) {
  width: auto;
  padding: 0 8px;
}

:deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  margin-bottom: 4px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #94a3b8 !important;
  overflow: hidden;
  position: relative;
  transition:
    background 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

:deep(.el-menu-item::before) {
  content: "";
  position: absolute;
  left: 0;
  top: 10px;
  width: 3px;
  height: 22px;
  border-radius: 0 999px 999px 0;
  background: #60a5fa;
  opacity: 0;
  transform: translateX(-3px);
  transition: opacity 0.2s ease, transform 0.2s ease;
}

:deep(.el-menu-item .el-icon) {
  width: 18px;
  margin-right: 10px;
  color: #64748b;
  transition: color 0.2s ease, transform 0.2s ease;
}

:deep(.el-menu--collapse .el-menu-item) {
  justify-content: center;
  padding: 0 !important;
}

:deep(.el-menu--collapse .el-menu-item .el-icon) {
  margin-right: 0;
}

:deep(.el-menu-item:hover) {
  background: rgba(148, 163, 184, 0.1) !important;
  color: #e2e8f0 !important;
  transform: translateX(2px);
}

:deep(.el-menu-item:hover .el-icon) {
  color: #bfdbfe;
  transform: scale(1.04);
}

:deep(.el-menu-item.is-active) {
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.95), rgba(14, 165, 233, 0.72)) !important;
  color: #ffffff !important;
  box-shadow:
    0 12px 24px rgba(37, 99, 235, 0.22),
    inset 0 1px 0 rgba(255, 255, 255, 0.18);
}

:deep(.el-menu-item.is-active::before) {
  opacity: 1;
  transform: translateX(0);
  background: #ffffff;
}

:deep(.el-menu-item.is-active .el-icon) {
  color: #ffffff;
}

:deep(.el-menu-item.is-active:hover) {
  transform: none;
}

.sidebar__footer {
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 12px 14px;
  border-top: 1px solid rgba(148, 163, 184, 0.12);
  cursor: pointer;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}

.sidebar__toggle {
  width: 100%;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 10px;
  color: #94a3b8;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(148, 163, 184, 0.14);
  font-size: 13px;
  font-weight: 600;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.sidebar__toggle--collapsed {
  width: 38px;
}

.sidebar__footer:hover .sidebar__toggle {
  color: #e2e8f0;
  background: rgba(30, 41, 59, 0.72);
  border-color: rgba(148, 163, 184, 0.24);
}
</style>
