<template>
  <div class="space-y-4">
    <el-card>
      <div class="flex items-center gap-2">
        <el-input-number v-model="query.projectId" :min="1" :controls="false" placeholder="项目ID" style="width: 140px" />
        <el-select v-model="query.reportType" clearable placeholder="类型" style="width: 160px">
          <el-option label="售前诊断" value="presale" />
          <el-option label="售前诊断报告" value="presale_diagnosis" />
          <el-option label="管理汇总" value="management" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="状态" style="width: 140px">
          <el-option label="草稿" value="draft" />
          <el-option label="已发布" value="published" />
          <el-option label="已拦截" value="intercepted" />
          <el-option label="已替代" value="superseded" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </el-card>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无报告">
        <el-table :data="rows" border>
          <el-table-column prop="id" label="报告ID" width="90" />
          <el-table-column prop="projectId" label="项目ID" width="100" />
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
          <el-table-column label="分享" min-width="260">
            <template #default="scope">
              <span v-if="canShare(scope.row)">{{ `${origin}/r/${scope.row.shareToken}` }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
          <el-table-column prop="publishedAt" label="发布时间" width="180" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="preview(scope.row.id)">预览</el-button>
              <el-button v-if="canShare(scope.row)" link type="success" @click="copyShare(scope.row.shareToken)">复制链接</el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>

      <div class="mt-4 flex justify-end">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page="page.current"
          :page-size="page.size"
          :total="page.total"
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import { getReportList } from '@/api/report'
import type { Report } from '@/types'
import { REPORT_STATUS_MAP, REPORT_TYPE_MAP } from '@/utils/constants'

const router = useRouter()
const origin = window.location.origin
const loading = ref(false)
const rows = ref<Report[]>([])
const page = reactive({ current: 1, size: 20, total: 0 })
const query = reactive({
  projectId: undefined as number | undefined,
  reportType: '',
  status: '',
})

async function load() {
  loading.value = true
  try {
    const { data } = await getReportList({
      current: page.current,
      size: page.size,
      projectId: query.projectId,
      reportType: query.reportType || undefined,
      status: query.status || undefined,
    })
    rows.value = data.data.records || []
    page.total = data.data.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.current = 1
  load()
}

function onPageChange(v: number) {
  page.current = v
  load()
}

function preview(id: number) {
  router.push(`/admin/reports/${id}`)
}

function canShare(row: Report) {
  return row.visibility === 'client' && row.status === 'published' && !!row.shareToken
}

async function copyShare(token: string) {
  await navigator.clipboard.writeText(`${origin}/r/${token}`)
  ElMessage.success('链接已复制')
}

function reportTypeLabel(v?: string) {
  return REPORT_TYPE_MAP[v as keyof typeof REPORT_TYPE_MAP]?.label || v || '-'
}

function reportStatusLabel(v?: string) {
  return REPORT_STATUS_MAP[v as keyof typeof REPORT_STATUS_MAP]?.label || v || '-'
}

load()
</script>
