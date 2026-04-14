<template>
  <div class="space-y-4">
    <el-page-header content="问题池版本" @back="$router.back()" />

    <el-card>
      <div class="mb-3 flex items-center justify-between">
        <div class="text-sm text-gray-500">支持批量生成 A 类问题内容建议（仅生成 strategy_status=none）。</div>
        <el-button type="primary" :loading="batchGenerating" @click="handleBatchGenerate">批量生成内容建议</el-button>
      </div>

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

    <el-dialog v-model="detailVisible" title="版本问题明细" width="1200px">
      <el-table v-if="detail?.items?.length" :data="detail.items" border>
        <el-table-column prop="questionText" label="问题内容" min-width="260" />
        <el-table-column label="分类" width="120">
          <template #default="scope">{{ dictStore.label('question_type', scope.row.questionType) }}</template>
        </el-table-column>
        <el-table-column label="等级" width="100">
          <template #default="scope">{{ dictStore.label('question_priority', scope.row.priority) }}</template>
        </el-table-column>
        <el-table-column label="核心" width="80">
          <template #default="scope">{{ scope.row.isCore ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="策略状态" width="110">
          <template #default="scope">{{ strategyStatusLabel(scope.row.strategyStatus) }}</template>
        </el-table-column>
        <el-table-column prop="strategySuggestedType" label="建议类型" width="140">
          <template #default="scope">{{ scope.row.strategySuggestedType || '-' }}</template>
        </el-table-column>
        <el-table-column label="策略建议" min-width="280" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.contentStrategy || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :loading="singleGeneratingId === scope.row.id" @click="handleSingleGenerate(scope.row.id)">生成/刷新</el-button>
            <el-button link type="success" @click="openEditStrategy(scope.row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="text-center text-gray-500">该版本无问题条目</div>
    </el-dialog>

    <el-dialog v-model="editStrategyVisible" title="编辑内容建议" width="760px">
      <el-form :model="strategyForm" label-width="110px">
        <el-form-item label="建议类型" required>
          <el-select v-model="strategyForm.strategySuggestedType" style="width: 260px">
            <el-option label="FAQ" value="faq" />
            <el-option label="场景文" value="scenario_content" />
            <el-option label="行业文" value="industry_article" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-select v-model="strategyForm.strategyKeywords" multiple filterable allow-create default-first-option style="width: 100%" placeholder="输入并回车，建议 3-5 个" />
        </el-form-item>
        <el-form-item label="策略建议" required>
          <el-input
            v-model="strategyForm.contentStrategy"
            type="textarea"
            :rows="6"
            maxlength="2000"
            show-word-limit
            placeholder="请输入问题场景内容建议"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editStrategyVisible = false">取消</el-button>
        <el-button type="primary" :loading="editingStrategy" @click="submitEditStrategy">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  generateProjectQuestionStrategies,
  generateSingleQuestionStrategy,
  getQuestionPoolVersionDetail,
  getQuestionPoolVersions,
  updateQuestionStrategy,
} from '@/api/project'
import { useDictStore } from '@/stores/dict'
import type { QuestionPoolItemVO, QuestionPoolVersionVO } from '@/types'
import DataState from '@/components/ui/DataState.vue'

const route = useRoute()
const dictStore = useDictStore()
const projectId = Number(route.params.id)

const loading = ref(false)
const rows = ref<QuestionPoolVersionVO[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })

const batchGenerating = ref(false)
const singleGeneratingId = ref<number | null>(null)

const detailVisible = ref(false)
const detail = ref<QuestionPoolVersionVO | null>(null)

const editStrategyVisible = ref(false)
const editingStrategy = ref(false)
const editingQuestionId = ref<number | null>(null)
const strategyForm = reactive({
  contentStrategy: '',
  strategyKeywords: [] as string[],
  strategySuggestedType: 'faq' as 'faq' | 'scenario_content' | 'industry_article',
})

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

function strategyStatusLabel(status?: string | null) {
  if (status === 'generated') return '已生成'
  if (status === 'edited') return '已人工编辑'
  return '未生成'
}

function parseKeywords(raw?: string | null) {
  if (!raw) return [] as string[]
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) {
      return parsed.map((it) => String(it)).filter((it) => it.trim().length > 0)
    }
    return []
  } catch {
    return []
  }
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

async function handleBatchGenerate() {
  if (!projectId) return
  batchGenerating.value = true
  try {
    await generateProjectQuestionStrategies(projectId)
    ElMessage.success('已投递批量生成任务')
  } catch {
    ElMessage.error('批量生成任务投递失败')
  } finally {
    batchGenerating.value = false
  }
}

async function handleSingleGenerate(questionId: number) {
  singleGeneratingId.value = questionId
  try {
    await generateSingleQuestionStrategy(questionId)
    ElMessage.success('已投递单题生成任务')
  } catch {
    ElMessage.error('单题生成任务投递失败')
  } finally {
    singleGeneratingId.value = null
  }
}

function openEditStrategy(row: QuestionPoolItemVO) {
  editingQuestionId.value = row.id
  strategyForm.contentStrategy = row.contentStrategy || ''
  strategyForm.strategyKeywords = parseKeywords(row.strategyKeywords)
  if (row.strategySuggestedType === 'scenario_content' || row.strategySuggestedType === 'industry_article' || row.strategySuggestedType === 'faq') {
    strategyForm.strategySuggestedType = row.strategySuggestedType
  } else {
    strategyForm.strategySuggestedType = 'faq'
  }
  editStrategyVisible.value = true
}

async function submitEditStrategy() {
  if (!editingQuestionId.value) return
  if (!strategyForm.contentStrategy.trim()) {
    ElMessage.warning('请填写策略建议')
    return
  }
  editingStrategy.value = true
  try {
    await updateQuestionStrategy(editingQuestionId.value, {
      contentStrategy: strategyForm.contentStrategy.trim(),
      strategyKeywords: strategyForm.strategyKeywords.filter((it) => it.trim().length > 0),
      strategySuggestedType: strategyForm.strategySuggestedType,
    })
    ElMessage.success('保存成功')
    editStrategyVisible.value = false
    if (detail.value?.versionNo) {
      await viewDetail(detail.value.versionNo)
    }
  } catch {
    ElMessage.error('保存失败')
  } finally {
    editingStrategy.value = false
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await load()
})
</script>
