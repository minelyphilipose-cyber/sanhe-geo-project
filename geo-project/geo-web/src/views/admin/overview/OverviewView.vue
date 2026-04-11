<template>
  <div>
    <h1 class="page-title">工作台</h1>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-4 gap-4 mb-6">
      <div v-for="stat in stats" :key="stat.label" class="glass-card-sm p-5">
        <div class="flex items-center justify-between mb-2">
          <span class="text-sm text-gray-500">{{ stat.label }}</span>
          <el-icon :size="20" :color="stat.color"><component :is="stat.icon" /></el-icon>
        </div>
        <div class="text-2xl font-bold" :style="{ color: stat.color }">
          {{ stat.value }}
        </div>
        <div v-if="stat.sub" class="text-xs text-gray-400 mt-1">{{ stat.sub }}</div>
      </div>
    </div>

    <!-- 下半区 -->
    <div class="grid grid-cols-3 gap-4">
      <!-- 待处理事项 -->
      <div class="col-span-2 glass-card-sm p-5">
        <h3 class="section-title">待处理事项</h3>
        <el-empty description="暂无待处理事项" :image-size="80" />
      </div>

      <!-- 快捷入口 -->
      <div class="glass-card-sm p-5">
        <h3 class="section-title">快捷操作</h3>
        <div class="space-y-2">
          <el-button class="w-full" @click="$router.push('/admin/customers')">
            新建客户
          </el-button>
          <el-button class="w-full" @click="$router.push('/admin/projects')">
            新建项目
          </el-button>
          <el-button class="w-full" @click="$router.push('/admin/monitoring')">
            监测中心
          </el-button>
          <el-button class="w-full" @click="$router.push('/admin/alerts')">
            异常中心
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

// TODO: 从后端 API 获取实际统计数据
const stats = ref([
  { label: '活跃项目', value: '—', icon: 'Folder',       color: '#2563EB', sub: '' },
  { label: '本月报表', value: '—', icon: 'DataAnalysis',  color: '#059669', sub: '' },
  { label: '未解决告警', value: '—', icon: 'Bell',        color: '#EF4444', sub: '' },
  { label: '合伙人数',  value: '—', icon: 'Coordinate',   color: '#7C3AED', sub: '' },
])
</script>
