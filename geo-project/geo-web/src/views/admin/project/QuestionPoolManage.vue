<template>
  <div>
    <div class="mb-4 flex items-center gap-2">
      <el-input v-model="query.keyword" placeholder="按项目名称搜索" clearable style="width: 260px" @keyup.enter="load" />
      <el-button @click="load">查询</el-button>
    </div>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无问题池记录">
        <el-table :data="rows" border>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
          <el-table-column prop="projectName" label="所属项目" min-width="180" />
          <el-table-column prop="versionNo" label="版本号" width="90" />
          <el-table-column prop="totalQuestions" label="总问题数" width="100" />
          <el-table-column prop="coreQuestions" label="核心问题数" width="110" />
          <el-table-column prop="changeReason" label="调整原因" min-width="220" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.changeReason || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="toProjectQuestionPool(scope.row.projectId)">查看版本</el-button>
            </template>
          </el-table-column>
        </el-table>

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
      </DataState>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import DataState from '@/components/ui/DataState.vue'
import { getQuestionPoolManagePage } from '@/api/project'
import type { QuestionPoolManageItemVO } from '@/types'

const router = useRouter()
const loading = ref(false)
const rows = ref<QuestionPoolManageItemVO[]>([])
const page = reactive({ current: 1, size: 20, total: 0 })
const query = reactive({ keyword: '' })

async function load() {
  loading.value = true
  try {
    const { data } = await getQuestionPoolManagePage({
      current: page.current,
      size: page.size,
      keyword: query.keyword || undefined,
    })
    rows.value = data.data.records || []
    page.total = data.data.total || 0
  } catch {
    rows.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

function onPageChange(v: number) {
  page.current = v
  load()
}

function toProjectQuestionPool(projectId: number) {
  router.push(`/admin/projects/${projectId}/questions`)
}

onMounted(load)
</script>
