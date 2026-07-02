<template>
  <div class="partner-list-page admin-page">
    <div class="admin-page-header partner-header">
      <div>
        <div class="admin-page-kicker">合伙人</div>
        <h1 class="admin-page-title">合伙人管理</h1>
        <div class="admin-page-subtitle">维护合伙人档案、账户折扣和合作状态，快速进入积分与充值审核视图。</div>
      </div>
      <div class="admin-page-actions">
        <el-button v-if="canCreatePartner" type="primary" @click="openCreate">新建合伙人</el-button>
      </div>
    </div>

    <el-card shadow="never" class="admin-surface partner-toolbar-card">
      <div class="partner-toolbar">
        <el-input
          v-model="query.keyword"
          class="filter-keyword"
          placeholder="搜索名称/编号"
          clearable
          @keyup.enter="load"
        />
        <el-select v-model="query.status" class="filter-status" placeholder="状态" clearable @change="load">
          <el-option
            v-for="item in dictStore.options('partner_status')"
            :key="item.dictKey"
            :label="item.dictValue"
            :value="item.dictKey"
          />
        </el-select>
        <el-button type="primary" plain @click="load">查询</el-button>
      </div>
    </el-card>

    <div class="admin-metric-grid partner-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">合伙人总数</span>
        <strong class="admin-metric-value">{{ page.total }}</strong>
        <span class="admin-metric-hint">当前筛选结果</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">启用中</span>
        <strong class="admin-metric-value">{{ activeCount }}</strong>
        <span class="admin-metric-hint">当前页 active 状态</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">暂停合作</span>
        <strong class="admin-metric-value">{{ pausedCount }}</strong>
        <span class="admin-metric-hint">需跟进合作节奏</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #7c3aed; --metric-tone: #f5f3ff">
        <span class="admin-metric-label">平均折扣</span>
        <strong class="admin-metric-value">{{ avgDiscountText }}</strong>
        <span class="admin-metric-hint">按当前页合伙人计算</span>
      </div>
    </div>

    <el-card shadow="never" class="admin-table-card partner-table-card">
      <div class="table-header">
        <div>
          <div class="table-title">合伙人列表</div>
          <div class="table-subtitle">查看基础信息、折扣策略、诊断报告权益和状态。</div>
        </div>
        <div class="chips">
          <span class="chip chip-muted">当前页 {{ rows.length }}</span>
          <span class="chip chip-success">启用 {{ activeCount }}</span>
          <span class="chip chip-warning">暂停 {{ pausedCount }}</span>
        </div>
      </div>

      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无合伙人数据">
        <el-table :data="rows" border table-layout="fixed">
          <el-table-column label="合伙人" min-width="240" show-overflow-tooltip>
            <template #default="scope">
              <div class="admin-entity-cell">
                <div class="admin-entity-avatar partner-avatar" :class="statusClass(scope.row.status)">
                  {{ partnerInitial(scope.row.partnerName) }}
                </div>
                <div class="min-w-0">
                  <div class="admin-entity-main">{{ scope.row.partnerName }}</div>
                  <div class="admin-entity-sub">{{ scope.row.partnerCode }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="折扣" width="100">
            <template #default="scope">{{ formatDiscount(scope.row.discountRate) }}</template>
          </el-table-column>
          <el-table-column label="诊断报告权益" width="210">
            <template #default="scope">
              <div class="quota-cell">
                <span>每月免费 {{ scope.row.presaleReportFreeQuotaLimit || 0 }} 次</span>
                <span>超额 {{ formatPoints(scope.row.presaleReportExtraPoints) }} 积分/次</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="scope">
              <span class="admin-status-tag" :class="statusClass(scope.row.status)">
                {{ dictStore.label('partner_status', scope.row.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="city" label="城市" width="130" show-overflow-tooltip />
          <el-table-column prop="contactName" label="联系人" width="120" show-overflow-tooltip />
          <el-table-column prop="contactPhone" label="电话" width="150" show-overflow-tooltip />
          <el-table-column label="操作" width="250" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="goDetail(scope.row.id)">详情</el-button>
              <el-button v-if="canUpdatePartner" link type="primary" @click="openEdit(scope.row)">编辑</el-button>
              <el-dropdown v-if="canUpdatePartnerStatus" @command="(v: string) => changeStatus(scope.row.id, v)">
                <el-button link type="primary" class="status-action">改状态</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="active">启用</el-dropdown-item>
                    <el-dropdown-item command="paused">暂停</el-dropdown-item>
                    <el-dropdown-item command="closed">关闭</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
      </DataState>

      <div class="admin-table-footer">
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

    <el-dialog
      v-model="formVisible"
      :title="formMode === 'create' ? '新建合伙人' : '编辑合伙人'"
      width="720px"
      class="admin-editor-dialog partner-editor-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="124px" class="admin-dialog-form partner-dialog-form">
        <div v-if="formMode === 'create'" class="account-delivery-alert">
          <span class="delivery-icon">i</span>
          <div>
            <strong>账号将在保存后自动生成</strong>
            <p>系统会生成合伙人登录账号和初始密码，请记录后通过线下安全方式交付给合伙人。</p>
          </div>
        </div>

        <section class="partner-form-section">
          <div class="section-caption">
            <span>01</span>
            <div>
              <strong>基础档案</strong>
              <p>用于识别合伙人主体和合作状态。</p>
            </div>
          </div>
          <div class="partner-section-grid">
            <el-form-item v-if="formMode === 'edit'" label="合伙人编号">
              <el-input v-model="form.partnerCode" disabled />
            </el-form-item>
            <el-form-item label="合伙人名称" prop="partnerName" :class="{ 'is-full': formMode === 'create' }" required>
              <el-input v-model="form.partnerName" placeholder="请输入合伙人名称" />
            </el-form-item>
            <el-form-item v-if="formMode === 'edit'" label="状态" prop="status" required>
              <el-select v-model="form.status" style="width: 100%">
                <el-option
                  v-for="item in dictStore.options('partner_status')"
                  :key="item.dictKey"
                  :label="item.dictValue"
                  :value="item.dictKey"
                />
              </el-select>
            </el-form-item>
          </div>
        </section>

        <section class="partner-form-section">
          <div class="section-caption">
            <span>02</span>
            <div>
              <strong>积分与诊断报告权益</strong>
              <p>审批项目和生成诊断报告时按这里配置消耗积分。</p>
            </div>
          </div>
          <div class="partner-section-grid">
            <el-form-item label="折扣率" prop="discountRate" required>
              <el-input-number
                v-model="form.discountRate"
                :precision="4"
                :step="0.0001"
                :min="0.0001"
                :max="1"
                :controls="false"
                style="width: 100%"
              />
              <div class="field-hint">1.0000 表示无折扣，0.8000 表示八折。</div>
            </el-form-item>
            <el-form-item label="初始积分" prop="initialAmount" required>
              <el-input-number
                v-model="form.initialAmount"
                :precision="2"
                :step="100"
                :min="0"
                :controls="false"
                style="width: 100%"
                :disabled="formMode === 'edit'"
              />
              <div class="field-hint">{{ formMode === 'edit' ? '初始积分仅在新建时录入。' : '默认 0，可创建后通过积分操作调整。' }}</div>
            </el-form-item>
            <el-form-item
              v-if="formMode === 'create' && form.initialAmount > 0"
              label="初始积分凭证"
              required
              class="is-full"
            >
              <div class="voucher-upload-field">
                <el-upload
                  drag
                  multiple
                  :show-file-list="false"
                  :before-upload="handleInitialVoucherUpload"
                  :disabled="voucherUploading"
                  accept="image/*,.pdf,.doc,.docx,.xls,.xlsx"
                >
                  <div class="voucher-upload-copy">
                    <strong>{{ voucherUploading ? '凭证上传中...' : '上传初始积分线下凭证' }}</strong>
                    <span>支持转账截图、审批单、PDF、Word、Excel，单个文件不超过 10MB。</span>
                  </div>
                </el-upload>
                <div v-if="initialVoucherFiles.length > 0" class="voucher-file-list">
                  <div v-for="file in initialVoucherFiles" :key="file.objectKey || file.fileName" class="voucher-file-item">
                    <button
                      v-if="isImageVoucher(file)"
                      type="button"
                      class="voucher-thumb"
                      @click="openVoucherFile(file)"
                    >
                      <img :src="voucherPreviewUrl(file)" :alt="file.fileName" />
                    </button>
                    <div v-else class="voucher-file-badge" :class="`is-${voucherFileKind(file)}`">
                      {{ voucherFileLabel(file) }}
                    </div>
                    <div class="voucher-file-main">
                      <strong :title="file.fileName">{{ file.fileName }}</strong>
                      <span>{{ formatFileSize(file.fileSize) }} · {{ voucherFileTypeText(file) }}</span>
                    </div>
                    <el-button link type="danger" @click="removeInitialVoucher(file)">移除</el-button>
                  </div>
                </div>
              </div>
            </el-form-item>
            <el-form-item label="每月免费额度" prop="presaleReportFreeQuotaLimit" required>
              <el-input-number
                v-model="form.presaleReportFreeQuotaLimit"
                :precision="0"
                :step="1"
                :min="0"
                :controls="false"
                style="width: 100%"
              />
              <div class="field-hint">合伙人每月可免费生成诊断报告的次数。</div>
            </el-form-item>
            <el-form-item label="单次超额积分" prop="presaleReportExtraPoints" required>
              <el-input-number
                v-model="form.presaleReportExtraPoints"
                :precision="2"
                :step="1"
                :min="0"
                :controls="false"
                style="width: 100%"
              />
              <div class="field-hint">当月免费额度用完后，单次生成诊断报告消耗的积分。</div>
            </el-form-item>
          </div>
        </section>

        <section class="partner-form-section">
          <div class="section-caption">
            <span>03</span>
            <div>
              <strong>联系信息</strong>
              <p>用于总部运营跟进和线下交付账号。</p>
            </div>
          </div>
          <div class="partner-section-grid">
            <el-form-item label="联系人">
              <el-input v-model="form.contactName" placeholder="请输入联系人" />
            </el-form-item>
            <el-form-item label="手机号" prop="contactPhone">
              <el-input v-model="form.contactPhone" placeholder="请输入手机号" />
            </el-form-item>
            <el-form-item label="城市" class="is-full">
              <RegionCascader v-model="form.cityCodes" />
              <div class="city-preview">{{ cityDisplayPreview || '未选择' }}</div>
            </el-form-item>
            <el-form-item label="备注" class="is-full">
              <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="记录合作背景、特殊说明或线下沟通信息" />
            </el-form-item>
          </div>
        </section>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import {
  createPartner,
  getPartnerList,
  updatePartner,
  updatePartnerStatus,
  uploadPartnerInitialAccountVoucher,
  type PartnerCreateResult,
  type PartnerItem,
  type PartnerVoucherFile,
} from '@/api/partner'
import DataState from '@/components/ui/DataState.vue'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { chinaRegionOptions, regionPayloadFromCodes } from '@/constants/region'
import { isValidMobile } from '@/utils/form'

const router = useRouter()
const userStore = useUserStore()
const dictStore = useDictStore()
const canCreatePartner = computed(() => userStore.hasPermission('partner.create'))
const canUpdatePartner = computed(() => userStore.hasPermission('partner.update'))
const canUpdatePartnerStatus = computed(() => userStore.hasPermission('partner.status.update'))

const loading = ref(false)
const saving = ref(false)
const rows = ref<PartnerItem[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive({ keyword: '', status: '' })

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const voucherUploading = ref(false)
const initialVoucherFiles = ref<PartnerVoucherFile[]>([])
const form = reactive({
  partnerCode: '',
  partnerName: '',
  discountRate: 1,
  initialAmount: 0,
  presaleReportFreeQuotaLimit: 0,
  presaleReportExtraPoints: 0,
  status: 'active',
  contactName: '',
  contactPhone: '',
  city: '',
  cityCodes: [] as string[],
  remark: '',
})

const activeCount = computed(() => rows.value.filter((item) => item.status === 'active').length)
const pausedCount = computed(() => rows.value.filter((item) => item.status === 'paused').length)
const avgDiscountText = computed(() => {
  if (!rows.value.length) return '-'
  const avg = rows.value.reduce((sum, item) => sum + Number(item.discountRate || 0), 0) / rows.value.length
  return formatDiscount(avg)
})

const rules: FormRules = {
  partnerName: [{ required: true, message: '请输入合伙人名称', trigger: 'blur' }],
  discountRate: [{ required: true, message: '请输入折扣率', trigger: 'change' }],
  initialAmount: [{ required: true, message: '请输入初始积分', trigger: 'change' }],
  presaleReportFreeQuotaLimit: [{ required: true, message: '请输入诊断报告每月免费额度', trigger: 'change' }],
  presaleReportExtraPoints: [{ required: true, message: '请输入诊断报告超额消耗积分', trigger: 'change' }],
  contactPhone: [{
    validator: (_rule, value: string, callback) => {
      callback(isValidMobile(value) ? undefined : new Error('请输入正确的手机号'))
    },
    trigger: 'blur',
  }],
}

function resetForm() {
  form.partnerCode = ''
  form.partnerName = ''
  form.discountRate = 1
  form.initialAmount = 0
  form.presaleReportFreeQuotaLimit = 0
  form.presaleReportExtraPoints = 0
  form.status = 'active'
  form.contactName = ''
  form.contactPhone = ''
  form.city = ''
  form.cityCodes = []
  form.remark = ''
  initialVoucherFiles.value.forEach(revokeVoucherPreviewUrl)
  initialVoucherFiles.value = []
}

function formatDiscount(value?: number | null) {
  if (value == null) return '-'
  return `${(Number(value) * 100).toFixed(1)}%`
}

function formatPoints(value?: number | null) {
  return Number(value || 0).toFixed(2)
}

function formatFileSize(size?: number | null) {
  if (!size || size <= 0) return '未知大小'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function partnerInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '合'
}

function statusClass(status?: string) {
  if (status === 'active') return 'is-success'
  if (status === 'paused') return 'is-warning'
  if (status === 'closed') return 'is-danger'
  return 'is-muted'
}

async function load() {
  loading.value = true
  try {
    const { data } = await getPartnerList({
      current: page.current,
      size: page.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
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

function openEdit(row: PartnerItem) {
  formMode.value = 'edit'
  editingId.value = row.id
  form.partnerCode = row.partnerCode
  form.partnerName = row.partnerName
  form.discountRate = row.discountRate
  form.initialAmount = 0
  form.presaleReportFreeQuotaLimit = row.presaleReportFreeQuotaLimit || 0
  form.presaleReportExtraPoints = Number(row.presaleReportExtraPoints || 0)
  form.status = row.status
  form.contactName = row.contactName || ''
  form.contactPhone = row.contactPhone || ''
  form.city = row.city || ''
  form.cityCodes = parseRegionCodesByDisplay(row.city || '')
  form.remark = row.remark || ''
  formVisible.value = true
}

function parseRegionCodesByDisplay(display: string): string[] {
  const target = (display || '').trim()
  if (!target) return []
  for (const province of chinaRegionOptions) {
    const p = province.label
    if (p === target) return [province.value]
    for (const city of province.children || []) {
      const pc = `${p} ${city.label}`
      if (pc === target || city.label === target) return [province.value, city.value]
      for (const district of city.children || []) {
        const pcd = `${p} ${city.label} ${district.label}`
        if (pcd === target || district.label === target) {
          return [province.value, city.value, district.value]
        }
      }
    }
  }
  return []
}

function resolveCityForSubmit(): string | undefined {
  const selected = regionPayloadFromCodes(form.cityCodes).displayName
  if (selected) return selected
  const fallback = (form.city || '').trim()
  return fallback || undefined
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (formMode.value === 'create' && form.initialAmount > 0 && initialVoucherFiles.value.length === 0) {
    ElMessage.warning('初始积分大于0时，请上传线下凭证')
    return
  }
  saving.value = true
  try {
    let createdId: number | null = null
    if (formMode.value === 'create') {
      const { data } = await createPartner({
        partnerName: form.partnerName,
        discountRate: form.discountRate,
        initialAmount: Number(form.initialAmount.toFixed(2)),
        initialOfflineReference: form.initialAmount > 0 ? buildVoucherReference(initialVoucherFiles.value) : undefined,
        presaleReportFreeQuotaLimit: form.presaleReportFreeQuotaLimit,
        presaleReportExtraPoints: Number(form.presaleReportExtraPoints.toFixed(2)),
        contactName: form.contactName || undefined,
        contactPhone: form.contactPhone || undefined,
        city: resolveCityForSubmit(),
        remark: form.remark || undefined,
      })
      const created = data.data as PartnerCreateResult
      createdId = created.partner.id
      await ElMessageBox.alert(
        `合伙人账号已创建\n账号：${created.username}\n初始密码：${created.initialPassword}\n\n请保存后线下发送给合伙人。`,
        '账号已生成',
        { confirmButtonText: '我已记录' },
      )
    } else if (editingId.value) {
      await updatePartner(editingId.value, {
        partnerName: form.partnerName,
        discountRate: form.discountRate,
        presaleReportFreeQuotaLimit: form.presaleReportFreeQuotaLimit,
        presaleReportExtraPoints: Number(form.presaleReportExtraPoints.toFixed(2)),
        status: form.status,
        contactName: form.contactName || undefined,
        contactPhone: form.contactPhone || undefined,
        city: resolveCityForSubmit(),
        remark: form.remark || undefined,
      })
    }
    formVisible.value = false
    ElMessage.success('保存成功')
    await load()
    if (createdId) {
      goDetail(createdId)
    }
  } finally {
    saving.value = false
  }
}

async function handleInitialVoucherUpload(file: File) {
  if (initialVoucherFiles.value.length >= 6) {
    ElMessage.warning('最多上传 6 个凭证文件')
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('单个凭证文件不能超过 10MB')
    return false
  }
  voucherUploading.value = true
  try {
    const { data } = await uploadPartnerInitialAccountVoucher(file)
    initialVoucherFiles.value.push(withLocalVoucherPreview(data.data, file))
    ElMessage.success('凭证上传成功')
  } finally {
    voucherUploading.value = false
  }
  return false
}

function removeInitialVoucher(file: PartnerVoucherFile) {
  revokeVoucherPreviewUrl(file)
  initialVoucherFiles.value = initialVoucherFiles.value.filter((item) => (
    (item.objectKey || item.fileName) !== (file.objectKey || file.fileName)
  ))
}

function openVoucherFile(file: PartnerVoucherFile) {
  const url = file.downloadUrl || file.previewUrl
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

function voucherFileKind(file: PartnerVoucherFile) {
  const name = file.fileName.toLowerCase()
  const contentType = (file.contentType || '').toLowerCase()
  if (contentType.startsWith('image/') || /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(name)) return 'image'
  if (contentType.includes('pdf') || name.endsWith('.pdf')) return 'pdf'
  if (contentType.includes('word') || name.endsWith('.doc') || name.endsWith('.docx')) return 'word'
  if (contentType.includes('excel') || contentType.includes('spreadsheet') || name.endsWith('.xls') || name.endsWith('.xlsx')) return 'excel'
  return 'file'
}

function voucherFileLabel(file: PartnerVoucherFile) {
  const labels: Record<string, string> = {
    image: 'IMG',
    pdf: 'PDF',
    word: 'Word',
    excel: 'Excel',
    file: 'File',
  }
  return labels[voucherFileKind(file)] || labels.file
}

function voucherFileTypeText(file: PartnerVoucherFile) {
  const labels: Record<string, string> = {
    image: '图片凭证',
    pdf: 'PDF 文件',
    word: 'Word 文档',
    excel: 'Excel 表格',
    file: '附件资料',
  }
  return isImageVoucher(file) ? '图片凭证' : labels[voucherFileKind(file)] || labels.file
}

function isImageVoucher(file: PartnerVoucherFile) {
  return Boolean(file.previewUrl) && voucherFileKind(file) === 'image'
}

function voucherPreviewUrl(file: PartnerVoucherFile) {
  return file.previewUrl || file.downloadUrl || ''
}

function withLocalVoucherPreview(voucher: PartnerVoucherFile, file: File) {
  if (isLocalImageFile(file)) {
    return { ...voucher, previewUrl: URL.createObjectURL(file) }
  }
  return voucher
}

function isLocalImageFile(file: File) {
  return file.type.toLowerCase().startsWith('image/')
    || /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(file.name.toLowerCase())
}

function revokeVoucherPreviewUrl(file: PartnerVoucherFile) {
  if (file.previewUrl?.startsWith('blob:')) {
    URL.revokeObjectURL(file.previewUrl)
  }
}

function buildVoucherReference(files: PartnerVoucherFile[]) {
  return JSON.stringify(files.map((file) => ({
    fileName: file.fileName,
    fileSize: file.fileSize,
    contentType: file.contentType,
    objectKey: file.objectKey,
    downloadUrl: file.downloadUrl,
  })))
}

async function changeStatus(id: number, status: string) {
  try {
    await ElMessageBox.confirm(`确认将合伙人状态更新为 "${dictStore.label('partner_status', status)}"？`, '状态变更确认', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    })
    await updatePartnerStatus(id, status)
    ElMessage.success('状态已更新')
    await load()
  } catch {
    // user canceled
  }
}

function goDetail(id: number) {
  router.push(`/admin/partners/${id}`)
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await load()
})

const cityDisplayPreview = computed(() => {
  return regionPayloadFromCodes(form.cityCodes).displayName || form.city
})
</script>

<style scoped>
.partner-header {
  align-items: center;
}

.partner-toolbar-card :deep(.el-card__body) {
  padding: 12px;
}

.partner-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-keyword {
  width: 240px;
}

.filter-status {
  width: 140px;
}

.partner-metric-grid {
  margin-bottom: 0;
}

.partner-table-card :deep(.el-card__body) {
  padding: 0;
}

.partner-editor-dialog :deep(.el-dialog__header) {
  padding: 24px 28px 18px;
  border-bottom: 1px solid #e5edf7;
  background: linear-gradient(90deg, #f7fbff 0%, #f2fff9 100%);
}

.partner-editor-dialog :deep(.el-dialog__title) {
  color: #0f2747;
  font-size: 22px;
  font-weight: 900;
}

.partner-editor-dialog :deep(.el-dialog__body) {
  padding: 20px 26px 18px;
}

.partner-editor-dialog :deep(.el-dialog__footer) {
  padding: 16px 28px 24px;
  border-top: 1px solid #e5edf7;
}

.partner-dialog-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.partner-dialog-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.partner-dialog-form :deep(.el-form-item__label) {
  align-items: center;
  color: #334155;
  font-weight: 800;
  line-height: 32px;
  white-space: nowrap;
}

.partner-dialog-form :deep(.el-form-item__content) {
  align-items: flex-start;
  min-width: 0;
}

.partner-dialog-form :deep(.el-input__wrapper),
.partner-dialog-form :deep(.el-input-number),
.partner-dialog-form :deep(.el-cascader) {
  width: 100%;
}

.partner-dialog-form :deep(.el-input__wrapper) {
  min-height: 40px;
  border-radius: 10px;
}

.partner-dialog-form :deep(.el-textarea__inner) {
  min-height: 84px !important;
  border-radius: 10px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--admin-panel-border-soft);
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 55%, #f0fdf4 100%);
}

.table-title {
  color: var(--admin-text-strong);
  font-size: 16px;
  font-weight: 800;
}

.table-subtitle {
  margin-top: 4px;
  color: var(--admin-text-muted);
  font-size: 12px;
}

.chips {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  border-radius: 14px;
  padding: 3px 9px;
  font-size: 12px;
  font-weight: 700;
}

.chip-muted {
  background: #f3f4f6;
  color: #6b7280;
}

.chip-success {
  background: #ecfdf5;
  color: #047857;
}

.chip-warning {
  background: #fffbeb;
  color: #b45309;
}

.partner-avatar.is-success {
  background: linear-gradient(135deg, #059669, #14b8a6);
}

.partner-avatar.is-warning {
  background: linear-gradient(135deg, #d97706, #f59e0b);
}

.partner-avatar.is-danger {
  background: linear-gradient(135deg, #dc2626, #ef4444);
}

.partner-avatar.is-muted {
  background: linear-gradient(135deg, #64748b, #94a3b8);
}

.status-action {
  margin-left: 10px;
}

.account-delivery-alert {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fbff 0%, #f0f9ff 100%);
  padding: 13px 15px;
}

.delivery-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  margin-top: 1px;
  border-radius: 999px;
  background: #2563eb;
  color: #fff;
  font-size: 13px;
  font-weight: 900;
  font-style: normal;
}

.account-delivery-alert strong {
  display: block;
  color: #1e3a8a;
  font-size: 13px;
  font-weight: 900;
  line-height: 1.35;
}

.account-delivery-alert p {
  margin: 3px 0 0;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.55;
}

.partner-form-section {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.88);
  padding: 15px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.section-caption {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.section-caption > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 900;
}

.section-caption strong {
  display: block;
  color: #0f172a;
  font-size: 15px;
  font-weight: 900;
  line-height: 1.25;
}

.section-caption p {
  margin: 3px 0 0;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
}

.partner-section-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 22px;
  row-gap: 16px;
}

.partner-section-grid .is-full,
.partner-section-grid :deep(.el-form-item.is-full),
.partner-section-grid :deep(.el-form-item:has(.el-textarea)),
.partner-section-grid :deep(.el-form-item:has(.region-cascader)) {
  grid-column: 1 / -1;
}

.quota-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.45;
}

.city-preview {
  width: 100%;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.field-hint {
  width: 100%;
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.45;
}

.voucher-upload-field {
  width: 100%;
}

.voucher-upload-field :deep(.el-upload),
.voucher-upload-field :deep(.el-upload-dragger) {
  width: 100%;
}

.voucher-upload-field :deep(.el-upload-dragger) {
  padding: 18px 16px;
  border-radius: 12px;
  background: #f8fbff;
}

.voucher-upload-copy {
  display: grid;
  gap: 5px;
  color: #64748b;
  line-height: 1.45;
}

.voucher-upload-copy strong {
  color: #0f172a;
  font-size: 14px;
}

.voucher-upload-copy span {
  font-size: 12px;
}

.voucher-file-list {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.voucher-file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
  padding: 10px 12px;
}

.voucher-file-item .el-button {
  margin-left: auto;
  flex-shrink: 0;
}

.voucher-thumb {
  overflow: hidden;
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  padding: 0;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f8fafc;
  cursor: pointer;
}

.voucher-thumb img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.voucher-file-badge {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: #eef2ff;
  color: #3730a3;
  font-size: 12px;
  font-weight: 800;
}

.voucher-file-badge.is-pdf {
  background: #fef2f2;
  color: #b91c1c;
}

.voucher-file-badge.is-word {
  background: #eff6ff;
  color: #1d4ed8;
}

.voucher-file-badge.is-excel {
  background: #ecfdf5;
  color: #047857;
}

.voucher-file-badge.is-file {
  background: #f1f5f9;
  color: #475569;
}

.voucher-file-main {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 3px;
}

.voucher-file-main strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.voucher-file-main span {
  color: #64748b;
  font-size: 12px;
}

@media (max-width: 768px) {
  .partner-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-keyword,
  .filter-status,
  .partner-toolbar .el-button {
    width: 100%;
  }

  .partner-editor-dialog :deep(.el-dialog) {
    width: calc(100vw - 24px) !important;
  }

  .partner-dialog-form {
    gap: 12px;
  }

  .partner-section-grid {
    grid-template-columns: 1fr;
  }
}
</style>
