<template>
  <div class="space-y-4">
    <el-page-header content="问题池版本" @back="$router.back()" />

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无问题池版本记录">
        <el-table :data="rows" border>
          <el-table-column prop="versionNo" label="版本号" width="90" />
          <el-table-column prop="totalQuestions" label="总问题数" width="100" />
          <el-table-column prop="coreQuestions" label="核心问题数" width="110" />
          <el-table-column prop="changeReason" label="调整原因" min-width="220" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.changeReason || '-' }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="viewDetail(scope.row.versionNo)">查看详情</el-button>
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

    <el-dialog v-model="detailVisible" title="版本问题明细" width="1000px">
      <el-table v-if="detail?.items?.length" :data="detail.items" border>
        <el-table-column prop="questionText" label="问题内容" min-width="300" />
        <el-table-column label="分类" width="120">
          <template #default="scope">{{ dictStore.label('question_type', scope.row.questionType) }}</template>
        </el-table-column>
        <el-table-column label="等级" width="100">
          <template #default="scope">{{ dictStore.label('question_priority', scope.row.priority) }}</template>
        </el-table-column>
        <el-table-column label="核心问题" width="100">
          <template #default="scope">{{ scope.row.isCore ? '是' : '否' }}</template>
        </el-table-column>
      </el-table>
      <div v-else class="text-center text-gray-500">该版本无问题条目</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getQuestionPoolVersionDetail, getQuestionPoolVersions } from '@/api/project'
import { useDictStore } from '@/stores/dict'
import type { QuestionPoolVersionVO } from '@/types'
import DataState from '@/components/ui/DataState.vue'

const route = useRoute()
const dictStore = useDictStore()
const projectId = Number(route.params.id)

const loading = ref(false)
const rows = ref<QuestionPoolVersionVO[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })

const detailVisible = ref(false)
const detail = ref<QuestionPoolVersionVO | null>(null)

async function load() {
  if (!projectId) return
  loading.value = true
  try {
    const { data } = await getQuestionPoolVersions(projectId, { current: page.current, size: page.size })
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

async function viewDetail(versionNo: number) {
  try {
    const { data } = await getQuestionPoolVersionDetail(projectId, versionNo)
    detail.value = data.data
    detailVisible.value = true
  } catch {
    ElMessage.error('加载版本详情失败')
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await load()
})
</script>
