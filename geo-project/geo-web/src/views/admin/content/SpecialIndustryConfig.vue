<template>
  <div class="special-industry-config-page">
    <div class="admin-page-header">
      <div>
        <h2>特殊行业文章配置</h2>
        <p>维护医疗/医美等特殊行业文章生成所需的选题角度、合规规则、合规内核和渠道文体模块。</p>
      </div>
      <div class="header-actions">
        <el-button v-if="activeTab === 'rules'" @click="openRuleTester">规则测试</el-button>
        <el-button type="primary" @click="openCreate">新增配置</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="loadCurrent">
      <el-tab-pane label="选题角度" name="angles" />
      <el-tab-pane label="合规规则" name="rules" />
      <el-tab-pane label="合规内核" name="kernels" />
      <el-tab-pane label="渠道模块" name="styles" />
      <el-tab-pane label="命中日志" name="logs" />
    </el-tabs>

    <el-card class="admin-table-card">
      <el-table v-loading="loading" :data="records" border>
        <el-table-column v-for="column in columns" :key="column.prop" :prop="column.prop" :label="column.label" :min-width="column.width || 120" show-overflow-tooltip />
        <el-table-column v-if="activeTab !== 'logs'" label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        class="table-pagination"
        layout="total, sizes, prev, pager, next"
        :total="pagination.total"
        @current-change="loadCurrent"
        @size-change="loadCurrent"
      />
    </el-card>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑配置' : '新增配置'" width="820px">
      <el-form class="special-industry-form" :model="form" label-position="top">
        <template v-if="activeTab === 'angles'">
          <el-form-item label="行业"><MedicalIndustrySelect v-model="form.industryCode" /></el-form-item>
          <el-form-item label="品类编码"><el-input v-model="form.categoryCode" /></el-form-item>
          <el-form-item label="品类名称"><el-input v-model="form.categoryName" /></el-form-item>
          <el-form-item label="选题角度"><el-input v-model="form.topicAngle" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="推荐侧重"><el-select v-model="form.recommendedFocus" clearable><el-option v-for="item in focuses" :key="item" :label="item" :value="item" /></el-select></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        </template>

        <template v-else-if="activeTab === 'rules'">
          <el-form-item label="规则类型"><el-input v-model="form.ruleType" /></el-form-item>
          <el-form-item label="行业"><MedicalIndustrySelect v-model="form.industryCode" clearable /></el-form-item>
          <el-form-item label="渠道档位"><MedicalTierSelect v-model="form.channelTier" clearable /></el-form-item>
          <el-form-item label="渠道组"><el-input v-model="form.channelGroupCode" placeholder="可留空表示通用" /></el-form-item>
          <el-form-item label="渠道子类"><el-input v-model="form.channelSubCode" placeholder="可留空表示通用" /></el-form-item>
          <el-form-item label="匹配方式"><el-select v-model="form.matchMode"><el-option label="包含" value="contains" /><el-option label="正则" value="regex" /></el-select></el-form-item>
          <el-form-item label="严重级别"><el-select v-model="form.severity"><el-option label="阻断" value="block" /><el-option label="提醒" value="warn" /></el-select></el-form-item>
          <el-form-item label="规则内容"><el-input v-model="form.pattern" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        </template>

        <template v-else-if="activeTab === 'kernels'">
          <el-form-item label="行业"><MedicalIndustrySelect v-model="form.industryCode" /></el-form-item>
          <el-form-item label="渠道档位"><MedicalTierSelect v-model="form.channelTier" /></el-form-item>
          <el-form-item label="内核名称"><el-input v-model="form.kernelName" /></el-form-item>
          <el-form-item label="版本号"><el-input-number v-model="form.versionNo" :min="1" /></el-form-item>
          <el-form-item label="品牌露出上限"><el-input-number v-model="form.brandExposureLimit" :min="0" /></el-form-item>
          <el-form-item label="发布前人工确认"><el-switch v-model="form.requireManualPublishReview" /></el-form-item>
          <el-form-item label="系统提示词"><el-input v-model="form.systemPrompt" type="textarea" :rows="8" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        </template>

        <template v-else-if="activeTab === 'styles'">
          <el-form-item label="渠道组"><el-input v-model="form.channelGroupCode" /></el-form-item>
          <el-form-item label="渠道子类"><el-input v-model="form.channelSubCode" placeholder="可留空" /></el-form-item>
          <el-form-item label="渠道档位"><MedicalTierSelect v-model="form.channelTier" /></el-form-item>
          <el-form-item label="高风险渠道"><el-switch v-model="form.highRisk" /></el-form-item>
          <el-form-item label="文体提示词"><el-input v-model="form.stylePrompt" type="textarea" :rows="8" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ruleTesterVisible" title="合规规则测试" width="760px">
      <el-form class="rule-test-form" :model="ruleTestForm" label-position="top">
        <div class="form-grid">
          <el-form-item label="行业"><MedicalIndustrySelect v-model="ruleTestForm.industryCode" /></el-form-item>
          <el-form-item label="渠道档位"><MedicalTierSelect v-model="ruleTestForm.channelTier" /></el-form-item>
          <el-form-item label="渠道组"><el-input v-model="ruleTestForm.channelGroupCode" placeholder="可留空" /></el-form-item>
          <el-form-item label="渠道子类"><el-input v-model="ruleTestForm.channelSubCode" placeholder="可留空" /></el-form-item>
          <el-form-item label="品牌名"><el-input v-model="ruleTestForm.brandName" placeholder="用于品牌露出测试" /></el-form-item>
          <el-form-item label="品牌露出上限"><el-input-number v-model="ruleTestForm.brandExposureLimit" :min="0" style="width: 100%" /></el-form-item>
        </div>
        <el-form-item label="高风险渠道"><el-switch v-model="ruleTestForm.highRiskChannel" /></el-form-item>
        <el-form-item label="标题"><el-input v-model="ruleTestForm.title" maxlength="200" /></el-form-item>
        <el-form-item label="测试正文">
          <el-input v-model="ruleTestForm.content" type="textarea" :rows="7" placeholder="请包含风险/个体差异提示，避免基础风险提示规则干扰规则测试" />
        </el-form-item>
      </el-form>
      <div v-if="ruleTestResult" class="rule-test-result">
        <el-alert
          :type="ruleTestResult.passed ? 'success' : 'error'"
          :closable="false"
          show-icon
          :title="ruleTestResult.passed ? '测试通过：未命中阻断规则' : '测试未通过：命中合规规则'"
        />
        <el-table v-if="ruleTestResult.issues.length" :data="ruleTestResult.issues" border>
          <el-table-column prop="ruleType" label="规则类型" min-width="140" />
          <el-table-column prop="severity" label="级别" width="100" />
          <el-table-column prop="matchedText" label="命中内容" min-width="180" show-overflow-tooltip />
          <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="ruleTesterVisible = false">关闭</el-button>
        <el-button type="primary" :loading="ruleTesting" @click="submitRuleTest">开始测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElOption, ElSelect } from 'element-plus'
import {
  createSpecialIndustryChannelStyleModule,
  createSpecialIndustryComplianceKernel,
  createSpecialIndustryComplianceRule,
  createSpecialIndustryTopicAngle,
  getSpecialIndustryChannelStyleModules,
  getSpecialIndustryComplianceHitLogs,
  getSpecialIndustryComplianceKernels,
  getSpecialIndustryComplianceRules,
  getSpecialIndustryTopicAngles,
  testSpecialIndustryRules,
  updateSpecialIndustryChannelStyleModule,
  updateSpecialIndustryComplianceRule,
  updateSpecialIndustryTopicAngle,
  type SpecialIndustryRuleTestResult,
} from '@/api/content'

type TabName = 'angles' | 'rules' | 'kernels' | 'styles' | 'logs'

const activeTab = ref<TabName>('angles')
const records = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const ruleTesting = ref(false)
const editorVisible = ref(false)
const ruleTesterVisible = ref(false)
const editingId = ref<number | null>(null)
const ruleTestResult = ref<SpecialIndustryRuleTestResult | null>(null)
const pagination = reactive({ current: 1, size: 20, total: 0 })
const focuses = ['principle', 'misconception', 'risk', 'rational_decision']
const form = reactive<Record<string, any>>({})
const ruleTestForm = reactive({
  industryCode: 'medical_beauty',
  channelTier: 'education',
  channelGroupCode: '',
  channelSubCode: '',
  brandName: '',
  brandExposureLimit: 2,
  highRiskChannel: false,
  title: '',
  content: '第一步要做基础评估，需结合个体差异和风险判断。',
})

const columns = computed(() => {
  if (activeTab.value === 'angles') return [
    { prop: 'industryCode', label: '行业' },
    { prop: 'categoryName', label: '品类' },
    { prop: 'topicAngle', label: '选题角度', width: 260 },
    { prop: 'recommendedFocus', label: '推荐侧重' },
    { prop: 'enabled', label: '启用' },
  ]
  if (activeTab.value === 'rules') return [
    { prop: 'ruleType', label: '规则类型' },
    { prop: 'industryCode', label: '行业' },
    { prop: 'channelTier', label: '档位' },
    { prop: 'pattern', label: '规则内容', width: 260 },
    { prop: 'severity', label: '级别' },
    { prop: 'enabled', label: '启用' },
  ]
  if (activeTab.value === 'kernels') return [
    { prop: 'industryCode', label: '行业' },
    { prop: 'channelTier', label: '档位' },
    { prop: 'kernelName', label: '名称' },
    { prop: 'versionNo', label: '版本' },
    { prop: 'brandExposureLimit', label: '品牌上限' },
    { prop: 'enabled', label: '启用' },
  ]
  if (activeTab.value === 'styles') return [
    { prop: 'channelGroupCode', label: '渠道组' },
    { prop: 'channelSubCode', label: '子类' },
    { prop: 'channelTier', label: '档位' },
    { prop: 'highRisk', label: '高风险' },
    { prop: 'enabled', label: '启用' },
  ]
  return [
    { prop: 'createdAt', label: '时间', width: 170 },
    { prop: 'projectId', label: '项目' },
    { prop: 'taskId', label: '任务' },
    { prop: 'ruleType', label: '规则类型' },
    { prop: 'matchedText', label: '命中文本', width: 260 },
    { prop: 'action', label: '动作' },
  ]
})

const MedicalIndustrySelect = defineComponent({
  props: { modelValue: String, clearable: Boolean },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () => h(ElSelect, {
      modelValue: props.modelValue,
      clearable: props.clearable,
      style: 'width: 100%',
      'onUpdate:modelValue': (value: string) => emit('update:modelValue', value),
    }, () => [
      h(ElOption, { label: '医美', value: 'medical_beauty' }),
      h(ElOption, { label: '口腔', value: 'oral' }),
    ])
  },
})

const MedicalTierSelect = defineComponent({
  props: { modelValue: String, clearable: Boolean },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () => h(ElSelect, {
      modelValue: props.modelValue,
      clearable: props.clearable,
      style: 'width: 100%',
      'onUpdate:modelValue': (value: string) => emit('update:modelValue', value),
    }, () => [
      h(ElOption, { label: '科普', value: 'education' }),
      h(ElOption, { label: '信源站', value: 'source_site' }),
      h(ElOption, { label: '官网', value: 'official_site' }),
    ])
  },
})

function resetForm() {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, { enabled: true, sortOrder: 100, matchMode: 'contains', severity: 'block', versionNo: 1, requireManualPublishReview: false, highRisk: false })
}

async function loadCurrent() {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    const api = {
      angles: getSpecialIndustryTopicAngles,
      rules: getSpecialIndustryComplianceRules,
      kernels: getSpecialIndustryComplianceKernels,
      styles: getSpecialIndustryChannelStyleModules,
      logs: getSpecialIndustryComplianceHitLogs,
    }[activeTab.value]
    const { data } = await api(params)
    records.value = data.data.records || []
    pagination.total = data.data.total || 0
  } finally {
    loading.value = false
  }
}

function openCreate() {
  if (activeTab.value === 'logs') return
  editingId.value = null
  resetForm()
  editorVisible.value = true
}

function openEdit(row: any) {
  editingId.value = row.id
  resetForm()
  Object.assign(form, row)
  editorVisible.value = true
}

function openRuleTester() {
  ruleTestResult.value = null
  ruleTesterVisible.value = true
}

async function submit() {
  saving.value = true
  try {
    if (activeTab.value === 'angles') {
      editingId.value ? await updateSpecialIndustryTopicAngle(editingId.value, form) : await createSpecialIndustryTopicAngle(form)
    } else if (activeTab.value === 'rules') {
      editingId.value ? await updateSpecialIndustryComplianceRule(editingId.value, form) : await createSpecialIndustryComplianceRule(form)
    } else if (activeTab.value === 'kernels') {
      await createSpecialIndustryComplianceKernel(form)
    } else if (activeTab.value === 'styles') {
      editingId.value ? await updateSpecialIndustryChannelStyleModule(editingId.value, form) : await createSpecialIndustryChannelStyleModule(form)
    }
    ElMessage.success('配置已保存')
    editorVisible.value = false
    await loadCurrent()
  } finally {
    saving.value = false
  }
}

async function submitRuleTest() {
  if (!ruleTestForm.content.trim()) {
    ElMessage.warning('请先填写测试正文')
    return
  }
  ruleTesting.value = true
  try {
    const { data } = await testSpecialIndustryRules({
      ...ruleTestForm,
      channelGroupCode: ruleTestForm.channelGroupCode.trim() || null,
      channelSubCode: ruleTestForm.channelSubCode.trim() || null,
      brandName: ruleTestForm.brandName.trim() || null,
      title: ruleTestForm.title.trim() || null,
    })
    ruleTestResult.value = data.data
  } finally {
    ruleTesting.value = false
  }
}

onMounted(loadCurrent)
</script>

<style scoped>
.special-industry-config-page {
  padding: 18px;
}

.admin-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.admin-page-header h2 {
  margin: 0 0 6px;
  font-size: 20px;
}

.admin-page-header p {
  margin: 0;
  color: #667085;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.table-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.special-industry-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
}

.special-industry-form :deep(.el-form-item:has(textarea)) {
  grid-column: 1 / -1;
}

.rule-test-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.rule-test-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}

@media (max-width: 760px) {
  .special-industry-form,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
