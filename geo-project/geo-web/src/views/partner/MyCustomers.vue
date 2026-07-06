<template>
  <div class="partner-page">
    <div class="partner-page-header">
      <div>
        <div class="partner-page-kicker">客户资产</div>
        <h1 class="partner-page-title">客户管理</h1>
        <div class="partner-page-subtitle">{{ pageSubtitle }}</div>
      </div>
      <div class="partner-page-actions">
        <el-button v-if="canCreateCompany" type="primary" @click="openCreate">新增客户</el-button>
      </div>
    </div>

    <el-card shadow="never" class="partner-surface">
      <div class="partner-toolbar">
        <div>
          <div class="partner-toolbar-title">客户列表</div>
          <div class="partner-toolbar-subtitle">{{ toolbarSubtitle }}</div>
        </div>
        <div class="partner-toolbar-controls">
          <el-input v-model="keyword" class="partner-search" clearable placeholder="搜索客户名称" @keyup.enter="load" />
          <el-button @click="load">查询</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="partner-table-card">
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无客户">
        <el-table :data="rows" border table-layout="fixed">
          <el-table-column label="客户名称" min-width="260" show-overflow-tooltip>
            <template #default="scope">
              <div class="partner-entity-cell">
                <div class="partner-entity-avatar is-green">{{ entityInitial(scope.row.companyName) }}</div>
                <div class="min-w-0">
                  <div class="partner-entity-main">{{ scope.row.companyName }}</div>
                  <div class="partner-entity-sub">{{ scope.row.businessDirection || scope.row.officialWebsite || '未填写主营方向' }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="联系人" width="180">
            <template #default="scope">
              <div class="partner-cell-stack">
                <span class="partner-cell-main">{{ scope.row.contactName || '-' }}</span>
                <span class="partner-cell-sub">{{ scope.row.contactPhone || '未填写电话' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="industry" label="行业" width="140" />
          <el-table-column label="地区" min-width="220">
            <template #default="scope">{{ region(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="scope">
              <span class="partner-status-tag" :class="companyStatusClass(scope.row.status)">
                {{ dictStore.label('company_status', scope.row.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column v-if="isPartnerOwner" label="交付员工" width="150">
            <template #default="scope">{{ staffName(scope.row.partnerStaffOwnerId) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="270" fixed="right">
            <template #default="scope">
              <div class="partner-row-actions">
                <el-button link type="primary" @click="goDetail(scope.row)">管理</el-button>
                <el-button v-if="canEditCompany(scope.row)" link type="primary" @click="openEdit(scope.row)">编辑</el-button>
                <el-button
                  v-if="canRequestPackage(scope.row)"
                  link
                  type="primary"
                  :loading="workflowSavingId === scope.row.id"
                  @click="requestPackage(scope.row)"
                >
                  提交负责人添加套餐
                </el-button>
                <el-button
                  v-if="canNotifyEntry(scope.row)"
                  link
                  type="primary"
                  :loading="workflowSavingId === scope.row.id"
                  @click="notifyEntry(scope.row)"
                >
                  通知继续录入
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <el-dialog
      v-model="formVisible"
      :title="formMode === 'create' ? '新增客户' : '编辑客户'"
      width="760px"
      class="partner-form-dialog partner-customer-dialog"
    >
      <div class="partner-dialog-tip">
        <span class="partner-dialog-tip-icon">i</span>
        <span>客户资料保存后可继续维护品牌和项目；客户创建项目前，请先在客户详情中绑定合伙人套餐。</span>
      </div>
      <el-form ref="formRef" class="partner-dialog-form-grid" :model="form" :rules="rules" label-position="top">
        <el-form-item class="is-wide" label="公司名称" prop="companyName" required>
          <el-input v-model="form.companyName" placeholder="请输入客户公司名称" />
        </el-form-item>
        <el-form-item label="客户状态">
          <el-select v-model="form.status" placeholder="请选择客户状态" style="width: 100%">
            <el-option
              v-for="item in dictStore.options('company_status')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" placeholder="请输入联系人姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="contactPhone">
          <el-input v-model="form.contactPhone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="行业">
          <el-input v-model="form.industry" placeholder="例如：智能制造、教育培训" />
        </el-form-item>
        <el-form-item label="主营方向">
          <el-input v-model="form.businessDirection" placeholder="请输入客户主营业务方向" />
        </el-form-item>
        <el-form-item label="官网">
          <el-input v-model="form.officialWebsite" placeholder="https://example.com" />
        </el-form-item>
        <el-form-item class="is-wide" label="地区">
          <RegionCascader v-model="form.regionCodes" />
        </el-form-item>
        <el-form-item class="is-wide" label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="4" placeholder="补充客户背景、沟通情况或后续跟进事项" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="partner-dialog-footer">
          <el-button @click="formVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
        </div>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createCompany,
  getCompanyList,
  notifyCompanyProjectEntry,
  requestCompanyPackageReview,
  updateCompany,
} from '@/api/customer'
import { getMyPartnerStaff, type PartnerStaff } from '@/api/partner'
import type { Company } from '@/types'
import DataState from '@/components/ui/DataState.vue'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionCodesFromPayload, regionDisplayFromPayload, regionPayloadFromCodes } from '@/constants/region'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { isValidMobile, nullableText } from '@/utils/form'
import { errorMessage } from '@/utils/error'

const dictStore = useDictStore()
const userStore = useUserStore()
const router = useRouter()
const isPartnerOwner = computed(() => userStore.role === 'partner')
const isPartnerStaff = computed(() => userStore.role === 'partner_staff')
const canCreateCompany = computed(() => isPartnerStaff.value && userStore.hasPermission('company.create'))
const canUpdateCompany = computed(() => isPartnerStaff.value && userStore.hasPermission('company.update'))
const COMPANY_EDITABLE_WORKFLOW_STATUSES = new Set(['draft', 'package_requested', 'package_bound', 'project_entry'])

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const rows = ref<Company[]>([])
const staffList = ref<PartnerStaff[]>([])
const workflowSavingId = ref<number | null>(null)
const formVisible = ref(false)
const formRef = ref<FormInstance>()
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const form = reactive({
  companyName: '',
  contactName: '',
  contactPhone: '',
  industry: '',
  businessDirection: '',
  officialWebsite: '',
  status: 'potential',
  regionCodes: [] as string[],
  remark: '',
})
const rules: FormRules = {
  companyName: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
  contactPhone: [{
    validator: (_rule, value: string, callback) => {
      callback(isValidMobile(value) ? undefined : new Error('请输入正确的手机号'))
    },
    trigger: 'blur',
  }],
}

const assignedCount = computed(() => rows.value.filter((item) => Boolean(item.partnerStaffOwnerId)).length)
const pageSubtitle = computed(() => (
  isPartnerStaff.value
    ? '维护分配给自己的客户资料，并为后续品牌、项目和核心问题准备基础信息。'
    : '查看合伙人名下客户资料和交付员工归属，录入与维护由交付员工处理。'
))
const toolbarSubtitle = computed(() => (
  isPartnerOwner.value
    ? `共 ${rows.value.length} 个客户，${assignedCount.value} 个已归属交付员工`
    : `共 ${rows.value.length} 个客户，可维护分配给自己的客户资料`
))

function entityInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '客'
}

function companyStatusClass(status?: string | null) {
  if (status === 'active' || status === 'signed') return 'is-success'
  if (status === 'potential') return ''
  if (status === 'inactive' || status === 'lost') return 'is-muted'
  return ''
}

function normalizedWorkflowStatus(status?: string | null) {
  return status || 'draft'
}

function hasActivePackage(row: Company) {
  return Boolean(row.activePackageBindingId || row.activePackageName)
}

function effectiveWorkflowStatus(row: Company) {
  const status = normalizedWorkflowStatus(row.partnerWorkflowStatus)
  if (!hasActivePackage(row) && ['package_bound', 'project_entry', 'entry_completed', 'submitted_to_hq'].includes(status)) {
    return 'package_requested'
  }
  return status
}

function canRequestPackage(row: Company) {
  return isPartnerStaff.value && effectiveWorkflowStatus(row) === 'draft'
}

function canEditCompany(row: Company) {
  return canUpdateCompany.value && COMPANY_EDITABLE_WORKFLOW_STATUSES.has(effectiveWorkflowStatus(row))
}

function canNotifyEntry(row: Company) {
  return isPartnerOwner.value && effectiveWorkflowStatus(row) === 'package_bound'
}

function resetForm() {
  form.companyName = ''
  form.contactName = ''
  form.contactPhone = ''
  form.industry = ''
  form.businessDirection = ''
  form.officialWebsite = ''
  form.status = 'potential'
  form.regionCodes = []
  form.remark = ''
}

function openCreate() {
  resetForm()
  formMode.value = 'create'
  editingId.value = null
  formVisible.value = true
}

function openEdit(row: Company) {
  if (!canEditCompany(row)) {
    ElMessage.warning('当前客户已提交负责人确认，不能继续编辑客户资料')
    return
  }
  formMode.value = 'edit'
  editingId.value = row.id
  form.companyName = row.companyName
  form.contactName = row.contactName || ''
  form.contactPhone = row.contactPhone || ''
  form.industry = row.industry || ''
  form.businessDirection = row.businessDirection || ''
  form.officialWebsite = row.officialWebsite || ''
  form.status = row.status || 'potential'
  form.regionCodes = regionCodesFromPayload(row)
  form.remark = row.remark || ''
  formVisible.value = true
}

function goDetail(row: Company) {
  router.push(`/partner/customers/${row.id}`)
}

function region(company: Company) {
  return regionDisplayFromPayload(company) || company.city || '-'
}

function staffName(staffUserId?: number | null) {
  if (!staffUserId) return '未分配'
  const staff = staffList.value.find((item) => item.id === staffUserId)
  return staff ? staff.displayName || staff.username : `员工 ${staffUserId}`
}

function patchRow(next: Company) {
  const index = rows.value.findIndex((item) => item.id === next.id)
  if (index >= 0) {
    rows.value[index] = { ...rows.value[index], ...next }
  }
}

async function requestPackage(row: Company) {
  try {
    await ElMessageBox.confirm(
      `确认将客户「${row.companyName}」提交给负责人添加客户套餐？提交前请确认客户与品牌信息已录入完整。`,
      '提交负责人处理',
      { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  workflowSavingId.value = row.id
  try {
    const { data } = await requestCompanyPackageReview(row.id)
    patchRow(data.data)
    ElMessage.success('已提交负责人添加客户套餐')
  } catch (err) {
    ElMessage.error(errorMessage(err, '提交负责人失败'))
  } finally {
    workflowSavingId.value = null
  }
}

async function notifyEntry(row: Company) {
  workflowSavingId.value = row.id
  try {
    const { data } = await notifyCompanyProjectEntry(row.id)
    patchRow(data.data)
    ElMessage.success('已通知交付员工继续录入项目与核心问题')
  } catch (err) {
    ElMessage.error(errorMessage(err, '通知交付员工失败'))
  } finally {
    workflowSavingId.value = null
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const regionPayload = regionPayloadFromCodes(form.regionCodes)
    const payload = {
      companyName: form.companyName,
      contactName: nullableText(form.contactName),
      contactPhone: nullableText(form.contactPhone),
      industry: nullableText(form.industry),
      businessDirection: nullableText(form.businessDirection),
      officialWebsite: nullableText(form.officialWebsite),
      provinceCode: regionPayload.provinceCode,
      provinceName: regionPayload.provinceName,
      cityCode: regionPayload.cityCode,
      cityName: regionPayload.cityName,
      districtCode: regionPayload.districtCode,
      districtName: regionPayload.districtName,
      sourceType: 'partner',
      status: form.status,
      remark: nullableText(form.remark),
    }
    if (formMode.value === 'create') {
      await createCompany(payload)
    } else if (editingId.value) {
      await updateCompany(editingId.value, payload)
    }
    formVisible.value = false
    ElMessage.success('保存成功')
    await load()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const [companyRes, staffRes] = await Promise.all([
      getCompanyList({
      current: 1,
      size: 200,
      keyword: keyword.value || undefined,
      }),
      isPartnerOwner.value ? getMyPartnerStaff() : Promise.resolve(null),
    ])
    rows.value = companyRes.data.data.records || []
    if (staffRes) {
      staffList.value = staffRes.data.data || []
    }
  } catch {
    rows.value = []
    ElMessage.error('加载客户失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await load()
})
</script>

<style scoped>
.partner-status-tag.is-info {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
}
</style>
