<template>
  <el-card class="admin-rich-card mobile-ops-panel" v-loading="loading">
    <template #header>
      <div class="section-header">
        <div class="panel-title">
          <span>移动看板运行观测</span>
          <el-tag size="small" type="info">近 14 天</el-tag>
        </div>
        <div class="panel-actions">
          <el-button size="small" :loading="loading" @click="loadData">刷新</el-button>
          <el-button v-if="editable" size="small" type="primary" plain :loading="savingBudget" @click="saveBudget">
            保存预算
          </el-button>
        </div>
      </div>
    </template>

    <div class="ops-grid">
      <div class="ops-card">
        <span>裁判覆盖率</span>
        <strong>{{ ops?.judgeHealth.coveragePercent ?? 0 }}%</strong>
        <small>成功 {{ ops?.judgeHealth.successCount || 0 }} / 应判 {{ ops?.judgeHealth.expectedCount || 0 }}</small>
      </div>
      <div class="ops-card">
        <span>接口错误率</span>
        <strong>{{ ops?.apiErrorStats.errorRatePercent ?? 0 }}%</strong>
        <small>失败 {{ ops?.apiErrorStats.failed || 0 }} / 总 {{ ops?.apiErrorStats.total || 0 }}</small>
      </div>
      <div class="ops-card">
        <span>裁判调用量</span>
        <strong>{{ ops?.llmUsage.totalCalls || 0 }}</strong>
        <small>约 {{ ops?.llmUsage.estimatedCost || 0 }} {{ ops?.llmUsage.currency || 'CNY' }}</small>
      </div>
      <div class="ops-card">
        <span>异常分享</span>
        <strong>{{ suspiciousShareCount }}</strong>
        <small>多 IP 或失败访问偏高</small>
      </div>
    </div>

    <el-collapse class="ops-collapse">
      <el-collapse-item title="裁判预算封顶" name="budget">
        <el-form label-width="150px" class="budget-form">
          <el-form-item label="启用预算闸">
            <el-switch v-model="budgetForm.enabled" :disabled="!editable" />
          </el-form-item>
          <el-form-item label="每日调用上限">
            <el-input-number v-model="budgetForm.dailyCallLimit" :disabled="!editable" :min="0" :step="10" controls-position="right" />
          </el-form-item>
          <el-form-item label="每月调用上限">
            <el-input-number v-model="budgetForm.monthlyCallLimit" :disabled="!editable" :min="0" :step="100" controls-position="right" />
          </el-form-item>
          <el-form-item label="每日估算成本上限">
            <el-input-number v-model="budgetForm.dailyEstimatedCostLimit" :disabled="!editable" :min="0" :step="1" controls-position="right" />
          </el-form-item>
          <el-form-item label="每月估算成本上限">
            <el-input-number v-model="budgetForm.monthlyEstimatedCostLimit" :disabled="!editable" :min="0" :step="10" controls-position="right" />
          </el-form-item>
        </el-form>
        <el-alert
          type="info"
          :closable="false"
          title="触顶后暂停新裁判，不写失败结果；未判候选会在后续周期继续等待。成本为基于 tokens 的估算值。"
        />
      </el-collapse-item>
      <el-collapse-item title="接口错误明细" name="api">
        <el-table :data="ops?.apiErrorStats.endpoints || []" border empty-text="暂无接口访问">
          <el-table-column prop="eventType" label="接口" min-width="120" />
          <el-table-column prop="total" label="总数" width="90" />
          <el-table-column prop="failed" label="失败" width="90" />
          <el-table-column label="错误率" width="100">
            <template #default="{ row }">{{ row.errorRatePercent }}%</template>
          </el-table-column>
          <el-table-column prop="latestFailReason" label="最近失败原因" min-width="160" />
        </el-table>
      </el-collapse-item>
      <el-collapse-item title="分享访问风险" name="share">
        <el-table :data="ops?.shareRisks || []" border empty-text="暂无访问记录">
          <el-table-column prop="tokenPrefix" label="Token 前缀" width="128" />
          <el-table-column prop="totalAccess" label="访问" width="90" />
          <el-table-column prop="distinctIpCount" label="不同 IP" width="100" />
          <el-table-column prop="failedAccess" label="失败" width="90" />
          <el-table-column label="风险" width="90">
            <template #default="{ row }">
              <el-tag :type="row.suspicious ? 'danger' : 'success'">{{ row.suspicious ? '关注' : '正常' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后访问" min-width="160">
            <template #default="{ row }">{{ formatTime(row.lastAccessAt) }}</template>
          </el-table-column>
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getProjectMobileDashboardJudgeBudget,
  getProjectMobileDashboardOperations,
  updateProjectMobileDashboardJudgeBudget,
} from '@/api/mobileDashboard'
import type { MobileDashboardOperations } from '@/types/mobileDashboard'

const props = defineProps<{
  projectId: number
  editable: boolean
}>()

const loading = ref(false)
const savingBudget = ref(false)
const ops = ref<MobileDashboardOperations | null>(null)
const budgetForm = reactive({
  enabled: true,
  dailyCallLimit: null as number | null,
  monthlyCallLimit: null as number | null,
  dailyEstimatedCostLimit: null as number | null,
  monthlyEstimatedCostLimit: null as number | null,
})

const suspiciousShareCount = computed(() => (ops.value?.shareRisks || []).filter((item) => item.suspicious).length)

function formatTime(value?: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function nullablePositive(value: number | null) {
  return value && value > 0 ? value : null
}

async function loadData() {
  loading.value = true
  try {
    const [opsRes, budgetRes] = await Promise.all([
      getProjectMobileDashboardOperations(props.projectId),
      getProjectMobileDashboardJudgeBudget(props.projectId),
    ])
    ops.value = opsRes.data.data
    const budget = budgetRes.data.data
    budgetForm.enabled = budget?.enabled !== false
    budgetForm.dailyCallLimit = budget?.dailyCallLimit || null
    budgetForm.monthlyCallLimit = budget?.monthlyCallLimit || null
    budgetForm.dailyEstimatedCostLimit = budget?.dailyEstimatedCostLimit == null ? null : Number(budget.dailyEstimatedCostLimit)
    budgetForm.monthlyEstimatedCostLimit = budget?.monthlyEstimatedCostLimit == null ? null : Number(budget.monthlyEstimatedCostLimit)
  } catch (error: any) {
    ElMessage.error(error?.message || '运行观测加载失败')
  } finally {
    loading.value = false
  }
}

async function saveBudget() {
  savingBudget.value = true
  try {
    await updateProjectMobileDashboardJudgeBudget(props.projectId, {
      enabled: budgetForm.enabled,
      dailyCallLimit: nullablePositive(budgetForm.dailyCallLimit),
      monthlyCallLimit: nullablePositive(budgetForm.monthlyCallLimit),
      dailyEstimatedCostLimit: nullablePositive(budgetForm.dailyEstimatedCostLimit),
      monthlyEstimatedCostLimit: nullablePositive(budgetForm.monthlyEstimatedCostLimit),
    })
    ElMessage.success('预算配置已保存')
    await loadData()
  } catch (error: any) {
    ElMessage.error(error?.message || '预算保存失败')
  } finally {
    savingBudget.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.panel-title,
.panel-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.panel-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.ops-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.ops-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 14px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}

.ops-card span,
.ops-card small {
  color: #909399;
}

.ops-card strong {
  color: #303133;
  font-size: 24px;
  line-height: 1.2;
}

.ops-collapse {
  margin-top: 8px;
}

.budget-form {
  max-width: 620px;
}

@media (max-width: 1200px) {
  .ops-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
