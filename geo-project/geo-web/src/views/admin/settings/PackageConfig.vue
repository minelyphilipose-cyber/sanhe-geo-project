<template>
  <div>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <el-input v-model="query.keyword" placeholder="搜索套餐类型/名称" clearable style="width: 260px" @keyup.enter="load" />
        <el-select v-model="query.enabled" placeholder="状态" clearable style="width: 140px" @change="load">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
      <el-button type="primary" @click="openCreate">新增套餐</el-button>
    </div>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无套餐配置">
        <el-table :data="rows" border>
          <el-table-column label="套餐类型" min-width="170">
            <template #default="scope">{{ dictStore.label('package_type', scope.row.packageType) }}</template>
          </el-table-column>
          <el-table-column prop="packageName" label="套餐名称" min-width="180" />
          <el-table-column label="标准价(元)" width="130">
            <template #default="scope">{{ centsToYuan(scope.row.standardPrice) }}</template>
          </el-table-column>
          <el-table-column prop="serviceMonths" label="服务月数" width="100" />
          <el-table-column label="问题池(总/核心)" width="140">
            <template #default="scope">{{ scope.row.questionPoolSize }}/{{ scope.row.coreQuestionCount }}</template>
          </el-table-column>
          <el-table-column label="平台监测(P0/P1/P2)" width="170">
            <template #default="scope">{{ scope.row.platformP0Count }}/{{ scope.row.platformP1Count }}/{{ scope.row.platformP2Count }}</template>
          </el-table-column>
          <el-table-column label="每问题调用(P0/P1/P2)" width="180">
            <template #default="scope">
              {{ scope.row.perQuestionCallsP0 ?? scope.row.perQuestionPlatformCalls }}/{{ scope.row.perQuestionCallsP1 ?? scope.row.perQuestionPlatformCalls }}/{{ scope.row.perQuestionCallsP2 ?? scope.row.perQuestionPlatformCalls }}
            </template>
          </el-table-column>
          <el-table-column label="双周简报" width="120">
            <template #default="scope">{{ biweeklyLabel(scope.row.biweeklyFrequency) }}</template>
          </el-table-column>
          <el-table-column label="月报/季报" width="140">
            <template #default="scope">{{ intensityLabel(scope.row.monthlyReportDepth) }}/{{ intensityLabel(scope.row.quarterlyReportDepth) }}</template>
          </el-table-column>
          <el-table-column label="目标指标" min-width="180">
            <template #default="scope">{{ targetMetricLabel(scope.row.targetMetricType) }}: {{ formatMetricValue(scope.row.targetMetricValue) }}</template>
          </el-table-column>
          <el-table-column label="发布站点(默认)" min-width="150">
            <template #default="scope">{{ contentPublishSummary(scope.row.contentConfigs) }}</template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
              <el-button
                link
                :type="scope.row.enabled ? 'warning' : 'success'"
                @click="changeStatus(scope.row)"
              >
                {{ scope.row.enabled ? '停用' : '启用' }}
              </el-button>
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

    <el-dialog
      v-model="formVisible"
      :title="formMode === 'create' ? '新增套餐' : '编辑套餐'"
      width="1080px"
      class="package-plan-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item label="套餐类型" prop="packageType" required>
              <el-input v-model="form.packageType" :disabled="formMode === 'edit'" placeholder="如: trial_6980" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="套餐名称" prop="packageName" required>
              <el-input v-model="form.packageName" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="标准价(元)" prop="standardPriceYuan" required>
              <el-input-number v-model="form.standardPriceYuan" :min="1" :precision="2" :step="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="服务月数" prop="serviceMonths" required>
              <el-input-number v-model="form.serviceMonths" :min="1" :max="36" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="排序" prop="sortOrder" required>
              <el-input-number v-model="form.sortOrder" :min="0" :step="10" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">问题池与平台监测</el-divider>
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="问题池规模(总)" prop="questionPoolSize" required>
              <el-input-number v-model="form.questionPoolSize" :min="1" :step="10" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="核心问题数量" prop="coreQuestionCount" required>
              <el-input-number v-model="form.coreQuestionCount" :min="1" :step="10" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="普通问题数量">
              <el-input :value="normalQuestionCount" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="P0平台数" prop="platformP0Count" required>
              <el-input-number v-model="form.platformP0Count" :min="0" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="P1平台数" prop="platformP1Count" required>
              <el-input-number v-model="form.platformP1Count" :min="0" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="P2平台数" prop="platformP2Count" required>
              <el-input-number v-model="form.platformP2Count" :min="0" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="每问题P0调用" prop="perQuestionCallsP0" required>
              <el-input-number v-model="form.perQuestionCallsP0" :min="1" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="每问题P1调用" prop="perQuestionCallsP1" required>
              <el-input-number v-model="form.perQuestionCallsP1" :min="1" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="每问题P2调用" prop="perQuestionCallsP2" required>
              <el-input-number v-model="form.perQuestionCallsP2" :min="1" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">报表与顾问强度</el-divider>
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="双周简报频率" prop="biweeklyFrequency" required>
              <el-select v-model="form.biweeklyFrequency" style="width: 100%">
                <el-option
                  v-for="item in biweeklyOptions"
                  :key="item.dictKey"
                  :label="item.dictValue"
                  :value="Number(item.dictKey)"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="月报深度" prop="monthlyReportDepth" required>
              <el-select v-model="form.monthlyReportDepth" style="width: 100%">
                <el-option v-for="item in intensityOptions" :key="item.dictKey" :label="item.dictValue" :value="item.dictKey" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="季报深度" prop="quarterlyReportDepth" required>
              <el-select v-model="form.quarterlyReportDepth" style="width: 100%">
                <el-option v-for="item in intensityOptions" :key="item.dictKey" :label="item.dictValue" :value="item.dictKey" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="顾问参与强度" prop="consultantIntensity" required>
              <el-select v-model="form.consultantIntensity" style="width: 100%">
                <el-option v-for="item in intensityOptions" :key="item.dictKey" :label="item.dictValue" :value="item.dictKey" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="竞品观察深度" prop="competitorInsightDepth" required>
              <el-select v-model="form.competitorInsightDepth" style="width: 100%">
                <el-option v-for="item in intensityOptions" :key="item.dictKey" :label="item.dictValue" :value="item.dictKey" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="媒体分发强度" prop="mediaDistributionIntensity" required>
              <el-select v-model="form.mediaDistributionIntensity" style="width: 100%">
                <el-option v-for="item in intensityOptions" :key="item.dictKey" :label="item.dictValue" :value="item.dictKey" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">承诺目标</el-divider>
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="目标强度" prop="commitmentTargetIntensity" required>
              <el-select v-model="form.commitmentTargetIntensity" style="width: 100%">
                <el-option v-for="item in intensityOptions" :key="item.dictKey" :label="item.dictValue" :value="item.dictKey" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="目标指标类型" prop="targetMetricType" required>
              <el-select v-model="form.targetMetricType" style="width: 100%">
                <el-option v-for="item in metricOptions" :key="item.dictKey" :label="item.dictValue" :value="item.dictKey" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="目标值" prop="targetMetricValue" required>
              <el-input-number v-model="form.targetMetricValue" :min="0.0001" :precision="4" :step="0.01" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="统计窗口(天)" prop="targetWindowDays" required>
              <el-input-number v-model="form.targetWindowDays" :min="1" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :lg="16">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">内容生成规则</el-divider>
        <el-row :gutter="12" class="mb-2">
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="发布站点等级（默认）" required>
              <el-select v-model="form.defaultPublishSiteTier" style="width: 100%">
                <el-option label="S0" value="S0" />
                <el-option label="S1" value="S1" />
                <el-option label="S2" value="S2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="发布站点数量（默认）" required>
              <el-input-number v-model="form.defaultPublishSiteCount" :min="1" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="24" :lg="8" class="action-col">
            <el-form-item label="批量操作">
              <el-button type="primary" class="apply-all-btn" @click="applyPublishSiteDefaults">应用到全部文章类型</el-button>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="文章类型规则">
          <el-table :data="form.contentConfigs" border>
            <el-table-column label="文章类型" min-width="180">
              <template #default="scope">
                <el-select v-model="scope.row.articleType" style="width: 100%">
                  <el-option v-for="item in articleTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="每批篇数" min-width="140">
              <template #default="scope">
                <el-input-number v-model="scope.row.articlesPerBatch" :min="1" :step="1" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="每篇问题数" min-width="140">
              <template #default="scope">
                <el-input-number v-model="scope.row.questionsPerArticle" :min="1" :step="1" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="发布站点等级" min-width="150">
              <template #default="scope">
                <el-select v-model="scope.row.publishSiteTier" style="width: 100%">
                  <el-option label="S0" value="S0" />
                  <el-option label="S1" value="S1" />
                  <el-option label="S2" value="S2" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="发布站点数量" min-width="150">
              <template #default="scope">
                <el-input-number v-model="scope.row.publishSiteCount" :min="1" :step="1" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="是否启用" width="120">
              <template #default="scope">
                <el-switch v-model="scope.row.isActive" />
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>

        <el-form-item v-if="formMode === 'create'" label="启用状态" required>
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import { useDictStore } from '@/stores/dict'
import {
  createPackagePlan,
  getAdminPackagePlans,
  getPackageContentConfigs,
  updatePackagePlan,
  updatePackageContentConfigs,
  updatePackagePlanStatus,
} from '@/api/packagePlan'
import type { PackageContentConfig, PackagePlan } from '@/types'

const dictStore = useDictStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<PackagePlan[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive<{ keyword: string; enabled: boolean | undefined }>({ keyword: '', enabled: undefined })

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const form = reactive({
  packageType: '',
  packageName: '',
  standardPriceYuan: 6980,
  serviceMonths: 12,
  questionPoolSize: 100,
  coreQuestionCount: 20,
  platformP0Count: 2,
  platformP1Count: 2,
  platformP2Count: 1,
  perQuestionCallsP0: 1,
  perQuestionCallsP1: 1,
  perQuestionCallsP2: 1,
  perQuestionPlatformCalls: 1,
  biweeklyFrequency: 1,
  monthlyReportDepth: 'L2',
  quarterlyReportDepth: 'L2',
  consultantIntensity: 'L2',
  competitorInsightDepth: 'L2',
  mediaDistributionIntensity: 'L2',
  commitmentTargetIntensity: 'L2',
  targetMetricType: 'brand_mention_rate',
  targetMetricValue: 0.05,
  targetWindowDays: 90,
  defaultPublishSiteTier: 'S1',
  defaultPublishSiteCount: 1,
  enabled: true,
  sortOrder: 10,
  remark: '',
  contentConfigs: [] as PackageContentConfig[],
})

const articleTypeOptions = [
  { label: 'FAQ', value: 'faq' },
  { label: '问题场景内容', value: 'scenario_content' },
  { label: '行业文章', value: 'industry_article' },
  { label: '阶段建议', value: 'stage_advice' },
]

const intensityOptions = computed(() => dictStore.options('intensity_level'))
const biweeklyOptions = computed(() => dictStore.options('biweekly_frequency'))
const metricOptions = computed(() => dictStore.options('target_metric_type'))
const normalQuestionCount = computed(() => Math.max(form.questionPoolSize - form.coreQuestionCount, 0))

const rules: FormRules = {
  packageType: [
    { required: true, message: '请输入套餐类型', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]{2,31}$/, message: '格式: 小写字母开头, 仅字母数字下划线', trigger: 'blur' },
  ],
  packageName: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }],
  standardPriceYuan: [{ required: true, message: '请输入标准价', trigger: 'change' }],
  serviceMonths: [{ required: true, message: '请输入服务月数', trigger: 'change' }],
  questionPoolSize: [{ required: true, message: '请输入问题池总数', trigger: 'change' }],
  coreQuestionCount: [{ required: true, message: '请输入核心问题数', trigger: 'change' }],
  platformP0Count: [{ required: true, message: '请输入P0平台数', trigger: 'change' }],
  platformP1Count: [{ required: true, message: '请输入P1平台数', trigger: 'change' }],
  platformP2Count: [{ required: true, message: '请输入P2平台数', trigger: 'change' }],
  perQuestionCallsP0: [{ required: true, message: '请输入每问题P0调用数', trigger: 'change' }],
  perQuestionCallsP1: [{ required: true, message: '请输入每问题P1调用数', trigger: 'change' }],
  perQuestionCallsP2: [{ required: true, message: '请输入每问题P2调用数', trigger: 'change' }],
  biweeklyFrequency: [{ required: true, message: '请选择双周简报频率', trigger: 'change' }],
  monthlyReportDepth: [{ required: true, message: '请选择月报深度', trigger: 'change' }],
  quarterlyReportDepth: [{ required: true, message: '请选择季报深度', trigger: 'change' }],
  consultantIntensity: [{ required: true, message: '请选择顾问参与强度', trigger: 'change' }],
  competitorInsightDepth: [{ required: true, message: '请选择竞品观察深度', trigger: 'change' }],
  mediaDistributionIntensity: [{ required: true, message: '请选择媒体分发强度', trigger: 'change' }],
  commitmentTargetIntensity: [{ required: true, message: '请选择承诺目标强度', trigger: 'change' }],
  targetMetricType: [{ required: true, message: '请选择目标指标类型', trigger: 'change' }],
  targetMetricValue: [{ required: true, message: '请输入目标值', trigger: 'change' }],
  targetWindowDays: [{ required: true, message: '请输入统计窗口', trigger: 'change' }],
  sortOrder: [{ required: true, message: '请输入排序', trigger: 'change' }],
}

function centsToYuan(v?: number | null) {
  if (v == null) return '-'
  return Number(v).toFixed(2)
}

function yuanToCents(v: number) {
  return Number(v.toFixed(2))
}

function intensityLabel(v?: string | null) {
  return dictStore.label('intensity_level', v)
}

function biweeklyLabel(v?: number | null) {
  if (v == null) return '-'
  return dictStore.label('biweekly_frequency', String(v))
}

function targetMetricLabel(v?: string | null) {
  return dictStore.label('target_metric_type', v)
}

function articleTypeLabel(v?: string | null) {
  const matched = articleTypeOptions.find((x) => x.value === v)
  return matched?.label || v || '-'
}

function contentPublishSummary(configs?: PackageContentConfig[] | null) {
  const first = (configs || [])[0]
  if (!first) return '-'
  return `${first.publishSiteTier || '-'} / ${first.publishSiteCount || '-'}`
}

function formatMetricValue(v?: number | null) {
  if (v == null) return '-'
  if (v <= 1) {
    return `${(v * 100).toFixed(2)}%`
  }
  return v.toFixed(4)
}

function resetForm() {
  form.packageType = ''
  form.packageName = ''
  form.standardPriceYuan = 6980
  form.serviceMonths = 12
  form.questionPoolSize = 100
  form.coreQuestionCount = 20
  form.platformP0Count = 2
  form.platformP1Count = 2
  form.platformP2Count = 1
  form.perQuestionCallsP0 = 1
  form.perQuestionCallsP1 = 1
  form.perQuestionCallsP2 = 1
  form.perQuestionPlatformCalls = 1
  form.biweeklyFrequency = 1
  form.monthlyReportDepth = 'L2'
  form.quarterlyReportDepth = 'L2'
  form.consultantIntensity = 'L2'
  form.competitorInsightDepth = 'L2'
  form.mediaDistributionIntensity = 'L2'
  form.commitmentTargetIntensity = 'L2'
  form.targetMetricType = 'brand_mention_rate'
  form.targetMetricValue = 0.05
  form.targetWindowDays = 90
  form.defaultPublishSiteTier = 'S1'
  form.defaultPublishSiteCount = 1
  form.enabled = true
  form.sortOrder = 10
  form.remark = ''
  form.contentConfigs = defaultContentConfigs()
}

function defaultContentConfigs(): PackageContentConfig[] {
  return articleTypeOptions.map((item) => ({
    articleType: item.value,
    articlesPerBatch: 1,
    questionsPerArticle: 3,
    publishSiteTier: 'S1',
    publishSiteCount: 1,
    isActive: true,
  }))
}

async function load() {
  loading.value = true
  try {
    const { data } = await getAdminPackagePlans({
      current: page.current,
      size: page.size,
      keyword: query.keyword || undefined,
      enabled: query.enabled,
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

function openCreate() {
  formMode.value = 'create'
  editingId.value = null
  resetForm()
  formVisible.value = true
}

async function openEdit(row: PackagePlan) {
  formMode.value = 'edit'
  editingId.value = row.id
  form.packageType = row.packageType
  form.packageName = row.packageName
  form.standardPriceYuan = Number(row.standardPrice)
  form.serviceMonths = row.serviceMonths
  form.questionPoolSize = row.questionPoolSize
  form.coreQuestionCount = row.coreQuestionCount
  form.platformP0Count = row.platformP0Count
  form.platformP1Count = row.platformP1Count
  form.platformP2Count = row.platformP2Count
  form.perQuestionCallsP0 = row.perQuestionCallsP0 ?? row.perQuestionPlatformCalls
  form.perQuestionCallsP1 = row.perQuestionCallsP1 ?? row.perQuestionPlatformCalls
  form.perQuestionCallsP2 = row.perQuestionCallsP2 ?? row.perQuestionPlatformCalls
  form.perQuestionPlatformCalls = row.perQuestionPlatformCalls
  form.biweeklyFrequency = row.biweeklyFrequency
  form.monthlyReportDepth = row.monthlyReportDepth
  form.quarterlyReportDepth = row.quarterlyReportDepth
  form.consultantIntensity = row.consultantIntensity
  form.competitorInsightDepth = row.competitorInsightDepth
  form.mediaDistributionIntensity = row.mediaDistributionIntensity
  form.commitmentTargetIntensity = row.commitmentTargetIntensity
  form.targetMetricType = row.targetMetricType
  form.targetMetricValue = row.targetMetricValue
  form.targetWindowDays = row.targetWindowDays
  form.defaultPublishSiteTier = 'S1'
  form.defaultPublishSiteCount = 1
  form.enabled = row.enabled
  form.sortOrder = row.sortOrder
  form.remark = row.remark || ''
  form.contentConfigs = normalizeContentConfigs(row.contentConfigs || [])
  if (!form.contentConfigs.length) {
    try {
      const { data } = await getPackageContentConfigs(row.id)
      form.contentConfigs = normalizeContentConfigs(data.data || [])
    } catch {
      form.contentConfigs = defaultContentConfigs()
    }
  }
  if (form.contentConfigs.length > 0) {
    form.defaultPublishSiteTier = form.contentConfigs[0].publishSiteTier || 'S1'
    form.defaultPublishSiteCount = Number(form.contentConfigs[0].publishSiteCount || 1)
  }
  formVisible.value = true
}

function normalizeContentConfigs(input: PackageContentConfig[]) {
  const map = new Map<string, PackageContentConfig>()
  for (const item of input || []) {
    if (!item?.articleType) continue
    map.set(item.articleType, {
      articleType: item.articleType,
      articlesPerBatch: Number(item.articlesPerBatch || 1),
      questionsPerArticle: Number(item.questionsPerArticle || 3),
      publishSiteTier: item.publishSiteTier || 'S1',
      publishSiteCount: Number(item.publishSiteCount || 1),
      isActive: !!item.isActive,
    })
  }
  return articleTypeOptions.map((opt) => map.get(opt.value) || {
    articleType: opt.value,
    articlesPerBatch: 1,
    questionsPerArticle: 3,
    publishSiteTier: 'S1',
    publishSiteCount: 1,
    isActive: true,
  })
}

function validateContentConfigs() {
  if (!form.contentConfigs.length) {
    ElMessage.warning('请配置内容生成规则')
    return false
  }
  const uniqueTypes = new Set(form.contentConfigs.map((x) => x.articleType))
  if (uniqueTypes.size !== form.contentConfigs.length) {
    ElMessage.warning('文章类型规则存在重复，请检查')
    return false
  }
  for (const item of form.contentConfigs) {
    if (!item.articleType) {
      ElMessage.warning('文章类型不能为空')
      return false
    }
    if (!Number.isFinite(item.articlesPerBatch) || item.articlesPerBatch <= 0) {
      ElMessage.warning('每批篇数必须大于0')
      return false
    }
    if (!Number.isFinite(item.questionsPerArticle) || item.questionsPerArticle <= 0) {
      ElMessage.warning('每篇问题数必须大于0')
      return false
    }
    if (!item.publishSiteTier) {
      ElMessage.warning('请选择发布站点等级')
      return false
    }
    if (!Number.isFinite(item.publishSiteCount) || item.publishSiteCount <= 0) {
      ElMessage.warning('发布站点数量必须大于0')
      return false
    }
  }
  return true
}

function applyPublishSiteDefaults() {
  if (!form.defaultPublishSiteTier) {
    ElMessage.warning('请选择发布站点等级')
    return
  }
  if (!Number.isFinite(form.defaultPublishSiteCount) || form.defaultPublishSiteCount <= 0) {
    ElMessage.warning('发布站点数量必须大于0')
    return
  }
  form.contentConfigs = form.contentConfigs.map((item) => ({
    ...item,
    publishSiteTier: form.defaultPublishSiteTier,
    publishSiteCount: form.defaultPublishSiteCount,
  }))
  ElMessage.success('已应用到全部文章类型')
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (form.coreQuestionCount > form.questionPoolSize) {
    ElMessage.warning('核心问题数量不能大于问题池总规模')
    return
  }
  if (!validateContentConfigs()) {
    return
  }

  saving.value = true
  try {
    const payload = {
      packageType: form.packageType,
      packageName: form.packageName,
      standardPrice: yuanToCents(form.standardPriceYuan),
      serviceMonths: form.serviceMonths,
      questionPoolSize: form.questionPoolSize,
      coreQuestionCount: form.coreQuestionCount,
      platformP0Count: form.platformP0Count,
      platformP1Count: form.platformP1Count,
      platformP2Count: form.platformP2Count,
      perQuestionCallsP0: form.perQuestionCallsP0,
      perQuestionCallsP1: form.perQuestionCallsP1,
      perQuestionCallsP2: form.perQuestionCallsP2,
      perQuestionPlatformCalls: Math.max(form.perQuestionCallsP0, form.perQuestionCallsP1, form.perQuestionCallsP2),
      biweeklyFrequency: form.biweeklyFrequency,
      monthlyReportDepth: form.monthlyReportDepth,
      quarterlyReportDepth: form.quarterlyReportDepth,
      consultantIntensity: form.consultantIntensity,
      competitorInsightDepth: form.competitorInsightDepth,
      mediaDistributionIntensity: form.mediaDistributionIntensity,
      commitmentTargetIntensity: form.commitmentTargetIntensity,
      targetMetricType: form.targetMetricType,
      targetMetricValue: form.targetMetricValue,
      targetWindowDays: form.targetWindowDays,
      sortOrder: form.sortOrder,
      remark: form.remark || undefined,
      contentConfigs: form.contentConfigs.map((item) => ({
        articleType: item.articleType,
        articlesPerBatch: item.articlesPerBatch,
        questionsPerArticle: item.questionsPerArticle,
        publishSiteTier: item.publishSiteTier,
        publishSiteCount: item.publishSiteCount,
        isActive: item.isActive,
      })),
    }

    if (formMode.value === 'create') {
      await createPackagePlan({
        ...payload,
        enabled: form.enabled,
      })
    } else if (editingId.value) {
      await updatePackagePlan(editingId.value, payload)
      await updatePackageContentConfigs(editingId.value, payload.contentConfigs)
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function changeStatus(row: PackagePlan) {
  try {
    const target = !row.enabled
    await ElMessageBox.confirm(
      `确认${target ? '启用' : '停用'}套餐「${row.packageName}」？`,
      '状态变更确认',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
    await updatePackagePlanStatus(row.id, target)
    ElMessage.success('状态已更新')
    await load()
  } catch {
    // canceled
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  if (intensityOptions.value.length > 0) {
    form.monthlyReportDepth = intensityOptions.value[1]?.dictKey || intensityOptions.value[0].dictKey
    form.quarterlyReportDepth = intensityOptions.value[1]?.dictKey || intensityOptions.value[0].dictKey
    form.consultantIntensity = intensityOptions.value[1]?.dictKey || intensityOptions.value[0].dictKey
    form.competitorInsightDepth = intensityOptions.value[1]?.dictKey || intensityOptions.value[0].dictKey
    form.mediaDistributionIntensity = intensityOptions.value[1]?.dictKey || intensityOptions.value[0].dictKey
    form.commitmentTargetIntensity = intensityOptions.value[1]?.dictKey || intensityOptions.value[0].dictKey
  }
  if (metricOptions.value.length > 0) {
    form.targetMetricType = metricOptions.value[0].dictKey
  }
  if (biweeklyOptions.value.length > 0) {
    form.biweeklyFrequency = Number(biweeklyOptions.value[0].dictKey)
  }
  form.contentConfigs = defaultContentConfigs()
  await load()
})
</script>

<style scoped>
:deep(.package-plan-dialog .el-dialog) {
  max-width: calc(100vw - 24px);
}

:deep(.package-plan-dialog .apply-all-btn.el-button) {
  min-width: 180px;
  height: 32px;
  border-radius: 8px;
  font-weight: 500;
  padding: 0 16px;
}

:deep(.package-plan-dialog .action-col .el-form-item__content) {
  justify-content: flex-start;
}

</style>
