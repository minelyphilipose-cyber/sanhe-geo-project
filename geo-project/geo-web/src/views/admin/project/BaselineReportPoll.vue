<template>
  <div class="space-y-4">
    <el-page-header content="基线检测报告" @back="$router.back()" />

    <el-card v-loading="loading">
      <template #header>
        <div class="flex items-center justify-between">
          <span>轮询范围</span>
          <el-tag v-if="latestBatch" :type="batchTagType(latestBatch.status)">
            {{ batchStatusLabel(latestBatch.status) }}
          </el-tag>
        </div>
      </template>

      <el-form label-width="110px">
        <el-form-item label="轮询平台">
          <el-checkbox-group v-model="selectedPlatformCodes">
            <el-checkbox
              v-for="platform in platforms"
              :key="platform.code"
              :label="platform.code"
              border
            >
              {{ platform.name || platform.code }}
              <span class="option-meta">{{ platform.priorityLevel }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item label="问题分组">
          <el-checkbox-group v-model="selectedQuestionTiers">
            <el-checkbox
              v-for="tier in questionTiers"
              :key="tier.tier"
              :label="tier.tier"
              border
            >
              {{ tier.tier }} 类
              <span class="option-meta">{{ tier.questionCount }} 题</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item label="预计任务">
          <el-statistic :value="expectedTotal" suffix="次轮询" />
          <span class="ml-3 text-sm text-gray-500">
            {{ selectedPlatformCodes.length }} 个平台 x {{ selectedQuestionCount }} 个问题
          </span>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="polling"
            :disabled="expectedTotal <= 0"
            @click="startPoll"
          >
            开始轮询并保存结果
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <template #header>
        <div class="flex items-center justify-between">
          <span>轮询结果</span>
          <span v-if="latestBatch" class="text-sm text-gray-500">
            完成 {{ latestBatch.completedCount || 0 }} / {{ latestBatch.totalCount || 0 }}，失败 {{ latestBatch.failedCount || 0 }}
          </span>
        </div>
      </template>

      <el-table v-loading="resultLoading" :data="results" border empty-text="暂无轮询结果">
        <el-table-column prop="questionTier" label="分组" width="80" />
        <el-table-column prop="platformName" label="平台" width="150">
          <template #default="{ row }">{{ row.platformName || row.platformCode }}</template>
        </el-table-column>
        <el-table-column prop="questionText" label="问题" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'completed' ? 'success' : 'danger'">
              {{ row.status === 'completed' ? '完成' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="responseTimeMs" label="耗时(ms)" width="110" />
        <el-table-column label="结果" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">{{ row.status === 'completed' ? row.responseText : row.errorMessage }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        class="mt-4"
        layout="total, sizes, prev, pager, next"
        :total="page.total"
        :page-sizes="[20, 50, 100]"
        @current-change="loadResults"
        @size-change="() => loadResults(1)"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getBaselineReportOptions,
  getBaselineReportResults,
  startBaselineReportPoll,
  type BaselinePlatformOption,
  type BaselinePollBatch,
  type BaselinePollResult,
  type BaselineQuestionTierOption,
} from '@/api/baselineReport'

const route = useRoute()
const projectId = Number(route.params.id)

const loading = ref(false)
const polling = ref(false)
const resultLoading = ref(false)
const platforms = ref<BaselinePlatformOption[]>([])
const questionTiers = ref<BaselineQuestionTierOption[]>([])
const latestBatch = ref<BaselinePollBatch | null>(null)
const results = ref<BaselinePollResult[]>([])
const selectedPlatformCodes = ref<string[]>([])
const selectedQuestionTiers = ref<string[]>(['A', 'B', 'C'])
const page = reactive({ current: 1, size: 20, total: 0 })

const selectedQuestionCount = computed(() => questionTiers.value
  .filter((item) => selectedQuestionTiers.value.includes(item.tier))
  .reduce((sum, item) => sum + Number(item.questionCount || 0), 0))

const expectedTotal = computed(() => selectedPlatformCodes.value.length * selectedQuestionCount.value)

async function loadOptions() {
  loading.value = true
  try {
    const { data } = await getBaselineReportOptions(projectId)
    const payload = data.data
    platforms.value = payload.platforms || []
    questionTiers.value = payload.questionTiers || []
    latestBatch.value = payload.latestBatch || null
    selectedPlatformCodes.value = platforms.value.map((item) => item.code)
    selectedQuestionTiers.value = questionTiers.value
      .filter((item) => Number(item.questionCount || 0) > 0)
      .map((item) => item.tier)
    if (selectedQuestionTiers.value.length === 0) {
      selectedQuestionTiers.value = ['A', 'B', 'C']
    }
    await loadResults(1)
  } finally {
    loading.value = false
  }
}

async function loadResults(current = page.current) {
  resultLoading.value = true
  try {
    const { data } = await getBaselineReportResults(projectId, {
      batchId: latestBatch.value?.id,
      current,
      size: page.size,
    })
    const payload = data.data
    results.value = payload.records || []
    page.current = payload.current || current
    page.size = payload.size || page.size
    page.total = payload.total || 0
  } finally {
    resultLoading.value = false
  }
}

async function startPoll() {
  if (expectedTotal.value <= 0) {
    ElMessage.warning('请选择平台和问题分组')
    return
  }
  await ElMessageBox.confirm(
    `确认开始 ${expectedTotal.value} 次问题轮询？轮询完成后会保存结果。`,
    '基线检测报告',
    { type: 'warning', confirmButtonText: '开始', cancelButtonText: '取消' },
  )
  polling.value = true
  try {
    const { data } = await startBaselineReportPoll(projectId, {
      platformCodes: selectedPlatformCodes.value,
      questionTiers: selectedQuestionTiers.value,
    })
    latestBatch.value = data.data
    ElMessage.success('轮询结果已保存')
    await loadResults(1)
  } finally {
    polling.value = false
  }
}

function batchStatusLabel(status?: string) {
  if (status === 'completed') return '已完成'
  if (status === 'failed') return '失败'
  if (status === 'running') return '运行中'
  return status || '-'
}

function batchTagType(status?: string) {
  if (status === 'completed') return 'success'
  if (status === 'failed') return 'danger'
  return 'warning'
}

onMounted(loadOptions)
</script>

<style scoped>
.option-meta {
  margin-left: 6px;
  color: #909399;
  font-size: 12px;
}
</style>
