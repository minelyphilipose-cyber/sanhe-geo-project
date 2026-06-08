<template>
  <div class="narrative-config">
    <div class="page-head">
      <div>
        <div class="breadcrumb">
          管理后台 <span class="breadcrumb-sep">/</span>
          AI可见度诊断报告 <span class="breadcrumb-sep">/</span>
          <span class="breadcrumb-current">诊断文案配置</span>
        </div>
        <h2 class="page-title">诊断文案配置</h2>
        <p class="page-subtitle">配置版本 {{ configVersion || '—' }}。修改只影响后续生成的 L3 文案，不回写历史报告。</p>
      </div>
      <div class="page-actions">
        <el-button class="btn-default" @click="goBack">返回列表</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" v-loading="loading" class="config-tabs">
      <el-tab-pane label="关键发现文案" name="finding">
        <el-table :data="findingCopies" border class="config-table">
          <el-table-column prop="code" label="Code" min-width="190" fixed />
          <el-table-column prop="tier" label="Tier" width="100" />
          <el-table-column label="覆盖条件" min-width="180">
            <template #default="{ row }">
              <div class="muted">{{ row.bandOverride || '任意档位' }}</div>
              <div class="muted">{{ row.archetypeOverride || '任意原型' }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="titleTemplate" label="标题模板" min-width="240" show-overflow-tooltip />
          <el-table-column prop="bodyTemplate" label="正文模板" min-width="360" show-overflow-tooltip />
          <el-table-column prop="evidenceTemplate" label="证据模板" min-width="260" show-overflow-tooltip />
          <el-table-column prop="priority" label="优先级" width="90" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="editFinding(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="热力图总览句" name="heatmap">
        <el-table :data="heatmapSummaries" border class="config-table">
          <el-table-column prop="heatmapPattern" label="Pattern" min-width="190" fixed />
          <el-table-column prop="bandOverride" label="档位覆盖" width="110">
            <template #default="{ row }">{{ row.bandOverride || '任意' }}</template>
          </el-table-column>
          <el-table-column prop="summaryTemplate" label="总览句" min-width="430" show-overflow-tooltip />
          <el-table-column prop="colorLegendTemplate" label="色彩说明" min-width="360" show-overflow-tooltip />
          <el-table-column prop="sortOrder" label="顺序" width="90" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="editHeatmap(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="行业词库待处理" name="lexicon">
        <div class="section-note">
          LLM 只负责从已审核 bucket 中生成分类草稿；正式映射必须人工审批后才会影响后续新报告。
        </div>
        <el-table :data="lexiconReviewTasks" border class="config-table">
          <el-table-column prop="industry" label="原始行业" min-width="160" fixed />
          <el-table-column prop="industryKey" label="Industry Key" min-width="150" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="taskTagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="fallbackHitCount" label="兜底次数" width="90">
            <template #default="{ row }">{{ row.fallbackHitCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="草稿 bucket" min-width="190">
            <template #default="{ row }">
              <div>{{ parseDraft(row.draftJson).bucketCode || '—' }}</div>
              <div class="muted">{{ parseDraft(row.draftJson).industryShort || '短名未填' }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="source" label="来源" min-width="150" />
          <el-table-column prop="updatedAt" label="更新时间" min-width="170">
            <template #default="{ row }">{{ row.updatedAt || row.createdAt || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :loading="draftingTaskId === row.id" :disabled="!isActiveTask(row.status)" @click="generateDraft(row)">
                生成草稿
              </el-button>
              <el-button link type="primary" :disabled="!isActiveTask(row.status)" @click="editLexiconTask(row)">编辑</el-button>
              <el-button link type="success" :disabled="row.status !== 'DRAFTED'" @click="approveTask(row)">通过</el-button>
              <el-button link type="danger" :disabled="!isActiveTask(row.status)" @click="rejectTask(row)">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="词汇 Bucket" name="bucket">
        <div class="section-toolbar">
          <div class="section-note inline-note">
            customer/conversion 只来自这里。新增或编辑 bucket 会影响后续映射到它的行业报告。
          </div>
          <el-button type="primary" @click="createBucket">新增 Bucket</el-button>
        </div>
        <el-table :data="lexiconBuckets" border class="config-table">
          <el-table-column prop="bucketCode" label="Bucket" min-width="150" fixed />
          <el-table-column prop="bucketName" label="名称" min-width="160" />
          <el-table-column prop="customerTerm" label="称谓" width="100" />
          <el-table-column prop="conversionTerm" label="动作" width="100" />
          <el-table-column prop="defaultIndustryShort" label="默认短名" width="120" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="240" show-overflow-tooltip />
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="editBucket(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="行业映射" name="mapping">
        <el-table :data="industryBucketMappings" border class="config-table">
          <el-table-column prop="industry" label="原始行业" min-width="160" fixed />
          <el-table-column prop="industryKey" label="Industry Key" min-width="150" />
          <el-table-column prop="bucketCode" label="Bucket" width="140" />
          <el-table-column prop="industryShort" label="短行业名" width="120" />
          <el-table-column prop="source" label="来源" width="140" />
          <el-table-column prop="approvedAt" label="审批时间" min-width="180">
            <template #default="{ row }">{{ row.approvedAt || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="editMapping(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="findingDialogVisible" title="编辑关键发现文案" width="720px">
      <el-form ref="findingFormRef" :model="findingForm" :rules="findingRules" label-position="top">
        <div class="readonly-row">
          <span>{{ editingFinding?.code }}</span>
          <span>{{ editingFinding?.tier }}</span>
        </div>
        <el-form-item label="标题模板" prop="titleTemplate">
          <el-input v-model="findingForm.titleTemplate" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="正文模板" prop="bodyTemplate">
          <el-input v-model="findingForm.bodyTemplate" type="textarea" :rows="5" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="证据模板" prop="evidenceTemplate">
          <el-input v-model="findingForm.evidenceTemplate" maxlength="300" show-word-limit />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="优先级" prop="priority">
            <el-input-number v-model="findingForm.priority" :min="0" :max="999" controls-position="right" />
          </el-form-item>
          <el-form-item label="启用" prop="enabled">
            <el-switch v-model="findingForm.enabled" />
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="findingForm.remark" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="findingDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveFinding">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="heatmapDialogVisible" title="编辑热力图总览句" width="720px">
      <el-form ref="heatmapFormRef" :model="heatmapForm" :rules="heatmapRules" label-position="top">
        <div class="readonly-row">
          <span>{{ editingHeatmap?.heatmapPattern }}</span>
          <span>{{ editingHeatmap?.bandOverride || '任意档位' }}</span>
        </div>
        <el-form-item label="总览句" prop="summaryTemplate">
          <el-input v-model="heatmapForm.summaryTemplate" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="色彩说明" prop="colorLegendTemplate">
          <el-input v-model="heatmapForm.colorLegendTemplate" type="textarea" :rows="3" maxlength="300" show-word-limit />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="顺序" prop="sortOrder">
            <el-input-number v-model="heatmapForm.sortOrder" :min="0" :max="999" controls-position="right" />
          </el-form-item>
          <el-form-item label="启用" prop="enabled">
            <el-switch v-model="heatmapForm.enabled" />
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="heatmapForm.remark" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="heatmapDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveHeatmap">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="lexiconDialogVisible" title="编辑行业 bucket 草稿" width="560px">
      <el-form ref="lexiconFormRef" :model="lexiconForm" :rules="lexiconRules" label-position="top">
        <div class="readonly-row">
          <span>{{ editingLexiconTask?.industry }}</span>
          <span>{{ editingLexiconTask?.industryKey }}</span>
        </div>
        <el-form-item label="Bucket" prop="bucketCode">
          <el-select v-model="lexiconForm.bucketCode" filterable class="full-width">
            <el-option
              v-for="bucket in enabledBuckets"
              :key="bucket.bucketCode"
              :label="`${bucket.bucketCode} · ${bucket.bucketName} (${bucket.customerTerm}/${bucket.conversionTerm})`"
              :value="bucket.bucketCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="短行业名">
          <el-input v-model="lexiconForm.industryShort" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="建议新增 bucket">
          <el-switch v-model="lexiconForm.suggestNewBucket" />
          <div class="muted">开启后不能直接审批，需先人工创建 bucket。</div>
        </el-form-item>
        <el-form-item label="理由">
          <el-input v-model="lexiconForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lexiconDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveLexiconDraft">保存草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bucketDialogVisible" :title="editingBucket ? '编辑词汇 Bucket' : '新增词汇 Bucket'" width="620px">
      <el-form ref="bucketFormRef" :model="bucketForm" :rules="bucketRules" label-position="top">
        <div class="readonly-row">
          <span>{{ editingBucket?.bucketCode || '新 Bucket' }}</span>
          <span>bucket 改动会影响后续所有映射行业</span>
        </div>
        <el-form-item v-if="!editingBucket" label="Bucket Code" prop="bucketCode">
          <el-input v-model="bucketForm.bucketCode" maxlength="50" show-word-limit placeholder="如 LEGAL_SERVICE" />
          <div class="muted">仅支持大写字母、数字和下划线；保存后不可修改。</div>
        </el-form-item>
        <el-form-item label="名称" prop="bucketName">
          <el-input v-model="bucketForm.bucketName" maxlength="100" show-word-limit />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="称谓" prop="customerTerm">
            <el-input v-model="bucketForm.customerTerm" maxlength="20" show-word-limit />
          </el-form-item>
          <el-form-item label="动作短语" prop="conversionTerm">
            <el-input v-model="bucketForm.conversionTerm" maxlength="20" show-word-limit />
          </el-form-item>
        </div>
        <el-form-item label="默认短行业名">
          <el-input v-model="bucketForm.defaultIndustryShort" maxlength="50" show-word-limit />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="启用" prop="enabled">
            <el-switch v-model="bucketForm.enabled" />
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="bucketForm.remark" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bucketDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveBucket">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mappingDialogVisible" title="编辑行业映射" width="560px">
      <el-form ref="mappingFormRef" :model="mappingForm" :rules="mappingRules" label-position="top">
        <div class="readonly-row">
          <span>{{ editingMapping?.industry }}</span>
          <span>{{ editingMapping?.industryKey }}</span>
        </div>
        <el-form-item label="Bucket" prop="bucketCode">
          <el-select v-model="mappingForm.bucketCode" filterable class="full-width">
            <el-option
              v-for="bucket in enabledBuckets"
              :key="bucket.bucketCode"
              :label="`${bucket.bucketCode} · ${bucket.bucketName} (${bucket.customerTerm}/${bucket.conversionTerm})`"
              :value="bucket.bucketCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="短行业名">
          <el-input v-model="mappingForm.industryShort" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input v-model="mappingForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mappingDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveMapping">保存映射</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  approvePresaleIndustryBucketTask,
  createPresaleLexiconBucket,
  draftPresaleIndustryBucket,
  getPresaleNarrativeConfig,
  rejectPresaleIndustryBucketTask,
  updatePresaleIndustryBucketDraft,
  updatePresaleIndustryBucketMapping,
  updatePresaleHeatmapSummary,
  updatePresaleLexiconBucket,
  updatePresaleNarrativeFindingCopy,
  type PresaleIndustryBucketDraftPayload,
  type PresaleIndustryBucketMappingPayload,
  type PresaleIndustryBucketMapping,
  type PresaleHeatmapSummaryConfig,
  type PresaleHeatmapSummaryPayload,
  type PresaleIndustryLexiconReviewTask,
  type PresaleLexiconBucket,
  type PresaleLexiconBucketCreatePayload,
  type PresaleLexiconBucketPayload,
  type PresaleNarrativeFindingCopy,
  type PresaleNarrativeFindingCopyPayload,
} from '@/api/presaleNarrativeConfig'

type BucketForm = PresaleLexiconBucketPayload & { bucketCode: string }
type MappingForm = PresaleIndustryBucketMappingPayload

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const draftingTaskId = ref<number | null>(null)
const activeTab = ref<'finding' | 'heatmap' | 'lexicon' | 'bucket' | 'mapping'>('finding')
const configVersion = ref('')
const findingCopies = ref<PresaleNarrativeFindingCopy[]>([])
const heatmapSummaries = ref<PresaleHeatmapSummaryConfig[]>([])
const lexiconBuckets = ref<PresaleLexiconBucket[]>([])
const industryBucketMappings = ref<PresaleIndustryBucketMapping[]>([])
const lexiconReviewTasks = ref<PresaleIndustryLexiconReviewTask[]>([])

const findingDialogVisible = ref(false)
const heatmapDialogVisible = ref(false)
const lexiconDialogVisible = ref(false)
const bucketDialogVisible = ref(false)
const mappingDialogVisible = ref(false)
const editingFinding = ref<PresaleNarrativeFindingCopy | null>(null)
const editingHeatmap = ref<PresaleHeatmapSummaryConfig | null>(null)
const editingLexiconTask = ref<PresaleIndustryLexiconReviewTask | null>(null)
const editingBucket = ref<PresaleLexiconBucket | null>(null)
const editingMapping = ref<PresaleIndustryBucketMapping | null>(null)
const findingFormRef = ref<FormInstance>()
const heatmapFormRef = ref<FormInstance>()
const lexiconFormRef = ref<FormInstance>()
const bucketFormRef = ref<FormInstance>()
const mappingFormRef = ref<FormInstance>()

const findingForm = reactive<PresaleNarrativeFindingCopyPayload>({
  titleTemplate: '',
  bodyTemplate: '',
  evidenceTemplate: '',
  priority: 0,
  enabled: true,
  remark: '',
})

const heatmapForm = reactive<PresaleHeatmapSummaryPayload>({
  summaryTemplate: '',
  colorLegendTemplate: '',
  sortOrder: 0,
  enabled: true,
  remark: '',
})

const lexiconForm = reactive<PresaleIndustryBucketDraftPayload>({
  bucketCode: '',
  industryShort: '',
  suggestNewBucket: false,
  reason: '',
})

const bucketForm = reactive<BucketForm>({
  bucketCode: '',
  bucketName: '',
  customerTerm: '',
  conversionTerm: '',
  defaultIndustryShort: '',
  enabled: true,
  remark: '',
})

const mappingForm = reactive<MappingForm>({
  bucketCode: '',
  industryShort: '',
  remark: '',
})

const required = [{ required: true, message: '必填', trigger: 'blur' }]
const findingRules: FormRules = {
  titleTemplate: required,
  bodyTemplate: required,
  evidenceTemplate: required,
  priority: [{ required: true, type: 'number', message: '必填', trigger: 'change' }],
  enabled: [{ required: true, type: 'boolean', message: '必填', trigger: 'change' }],
}
const heatmapRules: FormRules = {
  summaryTemplate: required,
  colorLegendTemplate: required,
  sortOrder: [{ required: true, type: 'number', message: '必填', trigger: 'change' }],
  enabled: [{ required: true, type: 'boolean', message: '必填', trigger: 'change' }],
}
const lexiconRules: FormRules = {
  bucketCode: required,
}
const bucketRules: FormRules = {
  bucketCode: [
    { required: true, message: '必填', trigger: 'blur' },
    { pattern: /^[A-Z0-9_]+$/, message: '仅支持大写字母、数字和下划线', trigger: 'blur' },
  ],
  bucketName: required,
  customerTerm: required,
  conversionTerm: required,
  enabled: [{ required: true, type: 'boolean', message: '必填', trigger: 'change' }],
}
const mappingRules: FormRules = {
  bucketCode: required,
}

const enabledBuckets = computed(() => lexiconBuckets.value.filter(item => item.enabled))

onMounted(load)

async function load() {
  loading.value = true
  try {
    const { data } = await getPresaleNarrativeConfig()
    configVersion.value = data.data.configVersion
    findingCopies.value = data.data.findingCopies
    heatmapSummaries.value = data.data.heatmapSummaries
    lexiconBuckets.value = data.data.lexiconBuckets ?? []
    industryBucketMappings.value = data.data.industryBucketMappings ?? []
    lexiconReviewTasks.value = data.data.lexiconReviewTasks ?? []
  } finally {
    loading.value = false
  }
}

function editFinding(row: PresaleNarrativeFindingCopy) {
  editingFinding.value = row
  Object.assign(findingForm, {
    titleTemplate: row.titleTemplate,
    bodyTemplate: row.bodyTemplate,
    evidenceTemplate: row.evidenceTemplate,
    priority: row.priority,
    enabled: row.enabled,
    remark: row.remark ?? '',
  })
  findingDialogVisible.value = true
}

function editHeatmap(row: PresaleHeatmapSummaryConfig) {
  editingHeatmap.value = row
  Object.assign(heatmapForm, {
    summaryTemplate: row.summaryTemplate,
    colorLegendTemplate: row.colorLegendTemplate,
    sortOrder: row.sortOrder,
    enabled: row.enabled,
    remark: row.remark ?? '',
  })
  heatmapDialogVisible.value = true
}

async function saveFinding() {
  const row = editingFinding.value
  if (!row) return
  const valid = await findingFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = trimPayload(findingForm) as PresaleNarrativeFindingCopyPayload
    const { data } = await updatePresaleNarrativeFindingCopy(row.id, payload)
    replaceRow(findingCopies.value, data.data)
    findingDialogVisible.value = false
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

async function saveHeatmap() {
  const row = editingHeatmap.value
  if (!row) return
  const valid = await heatmapFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = trimPayload(heatmapForm) as PresaleHeatmapSummaryPayload
    const { data } = await updatePresaleHeatmapSummary(row.id, payload)
    replaceRow(heatmapSummaries.value, data.data)
    heatmapDialogVisible.value = false
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

function editLexiconTask(row: PresaleIndustryLexiconReviewTask) {
  editingLexiconTask.value = row
  const draft = parseDraft(row.draftJson)
  Object.assign(lexiconForm, {
    bucketCode: draft.bucketCode || enabledBuckets.value[0]?.bucketCode || '',
    industryShort: draft.industryShort || '',
    suggestNewBucket: draft.suggestNewBucket,
    reason: draft.reason || '',
  })
  lexiconDialogVisible.value = true
}

async function generateDraft(row: PresaleIndustryLexiconReviewTask) {
  draftingTaskId.value = row.id
  try {
    const { data } = await draftPresaleIndustryBucket(row.id)
    replaceRow(lexiconReviewTasks.value, data.data)
    ElMessage.success('草稿已生成')
  } finally {
    draftingTaskId.value = null
  }
}

async function saveLexiconDraft() {
  const row = editingLexiconTask.value
  if (!row) return
  const valid = await lexiconFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = trimPayload(lexiconForm) as PresaleIndustryBucketDraftPayload
    const { data } = await updatePresaleIndustryBucketDraft(row.id, payload)
    replaceRow(lexiconReviewTasks.value, data.data)
    lexiconDialogVisible.value = false
    ElMessage.success('草稿已保存')
  } finally {
    saving.value = false
  }
}

async function approveTask(row: PresaleIndustryLexiconReviewTask) {
  const { data } = await approvePresaleIndustryBucketTask(row.id)
  replaceRow(lexiconReviewTasks.value, data.data)
  await load()
  ElMessage.success('审批通过，后续新报告将使用该映射')
}

async function rejectTask(row: PresaleIndustryLexiconReviewTask) {
  const reason = window.prompt('请输入驳回原因（可选）') || ''
  const { data } = await rejectPresaleIndustryBucketTask(row.id, { reason })
  replaceRow(lexiconReviewTasks.value, data.data)
  ElMessage.success('已驳回')
}

function createBucket() {
  editingBucket.value = null
  Object.assign(bucketForm, {
    bucketCode: '',
    bucketName: '',
    customerTerm: '',
    conversionTerm: '',
    defaultIndustryShort: '',
    enabled: true,
    remark: '',
  })
  bucketDialogVisible.value = true
}

function editBucket(row: PresaleLexiconBucket) {
  editingBucket.value = row
  Object.assign(bucketForm, {
    bucketCode: row.bucketCode,
    bucketName: row.bucketName,
    customerTerm: row.customerTerm,
    conversionTerm: row.conversionTerm,
    defaultIndustryShort: row.defaultIndustryShort ?? '',
    enabled: row.enabled,
    remark: row.remark ?? '',
  })
  bucketDialogVisible.value = true
}

async function saveBucket() {
  const row = editingBucket.value
  const valid = await bucketFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = trimPayload(bucketForm) as BucketForm
    if (row) {
      const updatePayload: PresaleLexiconBucketPayload = {
        bucketName: payload.bucketName,
        customerTerm: payload.customerTerm,
        conversionTerm: payload.conversionTerm,
        defaultIndustryShort: payload.defaultIndustryShort,
        enabled: payload.enabled,
        remark: payload.remark,
      }
      const { data } = await updatePresaleLexiconBucket(row.id, updatePayload)
      replaceRow(lexiconBuckets.value, data.data)
    } else {
      const { data } = await createPresaleLexiconBucket(payload as PresaleLexiconBucketCreatePayload)
      lexiconBuckets.value.push(data.data)
      sortBuckets()
    }
    bucketDialogVisible.value = false
    ElMessage.success('Bucket 已保存')
  } finally {
    saving.value = false
  }
}

function editMapping(row: PresaleIndustryBucketMapping) {
  editingMapping.value = row
  Object.assign(mappingForm, {
    bucketCode: row.bucketCode,
    industryShort: row.industryShort ?? '',
    remark: '',
  })
  mappingDialogVisible.value = true
}

async function saveMapping() {
  const row = editingMapping.value
  if (!row) return
  const valid = await mappingFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = trimPayload(mappingForm) as PresaleIndustryBucketMappingPayload
    const { data } = await updatePresaleIndustryBucketMapping(row.id, payload)
    replaceRow(industryBucketMappings.value, data.data)
    mappingDialogVisible.value = false
    ElMessage.success('行业映射已保存')
  } finally {
    saving.value = false
  }
}

function parseDraft(raw?: string | null): PresaleIndustryBucketDraftPayload {
  if (!raw) {
    return { bucketCode: '', industryShort: '', suggestNewBucket: false, reason: '' }
  }
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>
    return {
      bucketCode: String(parsed.bucket_code || parsed.bucketCode || ''),
      industryShort: String(parsed.industry_short || parsed.industryShort || ''),
      suggestNewBucket: Boolean(parsed.suggest_new_bucket || parsed.suggestNewBucket),
      reason: String(parsed.reason || ''),
    }
  } catch {
    return { bucketCode: '', industryShort: '', suggestNewBucket: false, reason: '' }
  }
}

function taskTagType(status: string) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'info'
  if (status === 'DRAFTED') return 'primary'
  return 'warning'
}

function isActiveTask(status: string) {
  return status === 'PENDING' || status === 'DRAFTED'
}

function trimPayload<T extends Record<string, unknown>>(source: T): T {
  return Object.fromEntries(
    Object.entries(source).map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value]),
  ) as T
}

function replaceRow<T extends { id: number }>(rows: T[], row: T) {
  const index = rows.findIndex(item => item.id === row.id)
  if (index >= 0) {
    rows.splice(index, 1, row)
  }
}

function sortBuckets() {
  lexiconBuckets.value.sort((a, b) => {
    if (a.enabled !== b.enabled) return a.enabled ? -1 : 1
    return a.bucketCode.localeCompare(b.bucketCode)
  })
}

function goBack() {
  router.push('/admin/presale/report')
}
</script>

<style scoped>
.narrative-config {
  min-height: 100%;
  padding: 24px;
  background: #f6f7fb;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.breadcrumb {
  margin-bottom: 8px;
  color: #7b8794;
  font-size: 13px;
}

.breadcrumb-sep {
  margin: 0 6px;
  color: #c0c4cc;
}

.breadcrumb-current {
  color: #303133;
}

.page-title {
  margin: 0;
  color: #1f2937;
  font-size: 24px;
  font-weight: 700;
}

.page-subtitle {
  margin: 8px 0 0;
  color: #667085;
  font-size: 14px;
}

.config-tabs {
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.config-table {
  width: 100%;
}

.section-note {
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f9fafb;
  color: #475467;
  font-size: 13px;
}

.section-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.inline-note {
  flex: 1;
  margin-bottom: 0;
}

.full-width {
  width: 100%;
}

.muted {
  color: #667085;
  font-size: 12px;
  line-height: 20px;
}

.readonly-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.readonly-row span {
  padding: 5px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f9fafb;
  color: #344054;
  font-size: 13px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
</style>
