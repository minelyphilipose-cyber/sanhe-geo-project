<template>
  <div class="space-y-4">
    <el-page-header content="项目报告" @back="$router.back()" />

    <el-card>
      <div class="flex items-center justify-between">
        <div class="text-sm text-gray-500">项目ID：{{ projectId }}</div>
        <div class="flex items-center gap-2">
          <el-button :loading="loading" @click="load">刷新</el-button>
          <el-button v-if="canGenerate" type="primary" :loading="generating" @click="generatePresale">生成售前报告草稿</el-button>
        </div>
      </div>
    </el-card>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无报告记录">
        <el-table :data="rows" border>
          <el-table-column prop="id" label="报告ID" width="90" />
          <el-table-column label="类型" width="160">
            <template #default="scope">{{ reportTypeLabel(scope.row.reportType) }}</template>
          </el-table-column>
          <el-table-column label="可见性" width="90">
            <template #default="scope">{{ scope.row.visibility === 'internal' ? '内部' : '客户' }}</template>
          </el-table-column>
          <el-table-column prop="versionNo" label="版本" width="80" />
          <el-table-column label="状态" width="120">
            <template #default="scope">{{ reportStatusLabel(scope.row.status) }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
          <el-table-column prop="publishedAt" label="发布时间" width="180" />
          <el-table-column label="分享链接" min-width="300">
            <template #default="scope">
              <span v-if="canShare(scope.row)">{{ shareUrl(scope.row.shareToken) }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="preview(scope.row.id)">预览</el-button>
              <el-button v-if="canShare(scope.row)" link type="success" @click="copyShare(scope.row.shareToken)">复制链接</el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import type { Report } from '@/types'
import { generateReport, getReportList } from '@/api/report'
import { useUserStore } from '@/stores/user'
import { REPORT_STATUS_MAP, REPORT_TYPE_MAP } from '@/utils/constants'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const projectId = Number(route.params.id)

const canGenerate = computed(() => userStore.hasPermission('dispatch.presale.enqueue') || userStore.hasPermission('project.write'))
const loading = ref(false)
const generating = ref(false)
const rows = ref<Report[]>([])

async function load() {
  loading.value = true
  try {
    const { data } = await getReportList({ current: 1, size: 100, projectId })
    rows.value = data.data.records || []
  } finally {
    loading.value = false
  }
}

async function generatePresale() {
  generating.value = true
  try {
    await generateReport({ projectId, reportType: 'presale' })
    ElMessage.success('售前报告草稿已生成')
    await load()
  } finally {
    generating.value = false
  }
}

function preview(id: number) {
  router.push(`/admin/reports/${id}`)
}

function shareUrl(token: string) {
  return `${window.location.origin}/r/${token}`
}

function canShare(row: Report) {
  return row.visibility === 'client' && row.status === 'published' && !!row.shareToken
}

async function copyShare(token: string) {
  await navigator.clipboard.writeText(shareUrl(token))
  ElMessage.success('分享链接已复制')
}

function reportTypeLabel(v?: string) {
  return REPORT_TYPE_MAP[v as keyof typeof REPORT_TYPE_MAP]?.label || v || '-'
}

function reportStatusLabel(v?: string) {
  return REPORT_STATUS_MAP[v as keyof typeof REPORT_STATUS_MAP]?.label || v || '-'
}

load()
</script>
