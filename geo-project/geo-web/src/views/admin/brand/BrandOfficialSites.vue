<template>
  <div class="official-sites">
    <div class="official-sites__toolbar">
      <span class="official-sites__title">品牌官网发布配置</span>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新建</el-button>
    </div>

    <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无官网配置">
      <el-table :data="rows" border>
        <el-table-column prop="siteName" label="站点名称" min-width="150" />
        <el-table-column prop="cmsFrameworkCode" label="CMS 框架" min-width="180" show-overflow-tooltip />
        <el-table-column prop="apiEndpoint" label="API Endpoint" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="scope">
            <el-tag :type="isActive(scope.row.status) ? 'success' : 'info'">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上次探活" width="220">
          <template #default="scope">{{ checkText(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="scope">
            <el-button v-if="canWrite" link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            <el-button link type="success" @click="checkAuth(scope.row)">探活</el-button>
            <el-button v-if="canWrite" link type="danger" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </DataState>

    <el-dialog v-model="dialogVisible" :title="mode === 'create' ? '新建官网' : '编辑官网'" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="站点名称" prop="siteName"><el-input v-model="form.siteName" /></el-form-item>
        <el-form-item label="官网域名"><el-input v-model="form.siteDomain" placeholder="https://example.com" /></el-form-item>
        <el-form-item label="CMS 框架" prop="cmsFrameworkCode">
          <el-select v-model="form.cmsFrameworkCode" style="width: 100%">
            <el-option label="Official CMS Framework v1" value="Official CMS Framework v1" />
          </el-select>
        </el-form-item>
        <el-form-item label="Tenant Key" prop="tenantKey"><el-input v-model="form.tenantKey" /></el-form-item>
        <el-form-item label="API Endpoint" prop="apiEndpoint"><el-input v-model="form.apiEndpoint" /></el-form-item>
        <el-form-item label="鉴权方式">
          <el-select v-model="form.authType" clearable style="width: 100%">
            <el-option label="bearer_token" value="bearer_token" />
            <el-option label="api_key" value="api_key" />
          </el-select>
        </el-form-item>
        <el-form-item label="密钥" :prop="mode === 'create' ? 'credentials' : undefined">
          <el-input
            v-model="form.credentials"
            type="password"
            show-password
            :placeholder="mode === 'create' ? '请输入密钥' : '留空则不修改密钥'"
          />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import {
  checkAuthBrandOfficialSite,
  createBrandOfficialSite,
  deleteBrandOfficialSite,
  listBrandOfficialSites,
  updateBrandOfficialSite,
  type BrandOfficialSite,
  type BrandOfficialSiteCreateRequest,
  type BrandOfficialSiteUpdateRequest,
} from '@/api/brandOfficialSite'

const props = defineProps<{
  brandId: number
  canWrite?: boolean
}>()

const loading = ref(false)
const saving = ref(false)
const rows = ref<BrandOfficialSite[]>([])
const dialogVisible = ref(false)
const mode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  siteName: '',
  siteDomain: '',
  cmsFrameworkCode: 'Official CMS Framework v1',
  tenantKey: '',
  apiEndpoint: '',
  authType: 'bearer_token',
  credentials: '',
  remark: '',
})

const rules: FormRules = {
  siteName: [{ required: true, message: '请输入站点名称', trigger: 'blur' }],
  cmsFrameworkCode: [{ required: true, message: '请选择 CMS 框架', trigger: 'change' }],
  tenantKey: [{ required: true, message: '请输入 Tenant Key', trigger: 'blur' }],
  apiEndpoint: [{ required: true, message: '请输入 API Endpoint', trigger: 'blur' }],
  credentials: [{ required: true, message: '请输入密钥', trigger: 'blur' }],
}

function isActive(status?: string | null) {
  return status === 'active' || status === 'enabled'
}

function statusLabel(status?: string | null) {
  if (isActive(status)) return '启用'
  if (status === 'disabled') return '停用'
  return status || '-'
}

function checkText(row: BrandOfficialSite) {
  if (!row.lastCheckAt && !row.lastCheckResult) return '未探活'
  return `${row.lastCheckAt || '-'} · ${row.lastCheckResult || '-'}`
}

function resetForm() {
  form.siteName = ''
  form.siteDomain = ''
  form.cmsFrameworkCode = 'Official CMS Framework v1'
  form.tenantKey = ''
  form.apiEndpoint = ''
  form.authType = 'bearer_token'
  form.credentials = ''
  form.remark = ''
}

async function load() {
  loading.value = true
  try {
    rows.value = await listBrandOfficialSites(props.brandId)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  mode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: BrandOfficialSite) {
  mode.value = 'edit'
  editingId.value = row.id
  form.siteName = row.siteName || ''
  form.siteDomain = row.siteDomain || ''
  form.cmsFrameworkCode = row.cmsFrameworkCode || 'Official CMS Framework v1'
  form.tenantKey = row.tenantKey || ''
  form.apiEndpoint = row.apiEndpoint || ''
  form.authType = row.authType || 'bearer_token'
  form.credentials = ''
  form.remark = row.remark || ''
  dialogVisible.value = true
}

function createPayload(): BrandOfficialSiteCreateRequest {
  return {
    siteName: form.siteName.trim(),
    siteDomain: form.siteDomain.trim() || undefined,
    cmsFrameworkCode: form.cmsFrameworkCode.trim(),
    tenantKey: form.tenantKey.trim(),
    apiEndpoint: form.apiEndpoint.trim(),
    authType: form.authType.trim() || undefined,
    credentials: form.credentials.trim(),
    remark: form.remark.trim() || undefined,
  }
}

function updatePayload(): BrandOfficialSiteUpdateRequest {
  const payload: BrandOfficialSiteUpdateRequest = {
    siteName: form.siteName.trim(),
    siteDomain: form.siteDomain.trim() || undefined,
    cmsFrameworkCode: form.cmsFrameworkCode.trim(),
    tenantKey: form.tenantKey.trim(),
    apiEndpoint: form.apiEndpoint.trim(),
    authType: form.authType.trim() || undefined,
    remark: form.remark.trim() || undefined,
  }
  const credentials = form.credentials.trim()
  if (credentials) {
    payload.credentials = credentials
  }
  return payload
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (mode.value === 'create') {
      await createBrandOfficialSite(props.brandId, createPayload())
      ElMessage.success('官网配置已创建')
    } else if (editingId.value) {
      await updateBrandOfficialSite(editingId.value, updatePayload())
      ElMessage.success('官网配置已更新')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row: BrandOfficialSite) {
  try {
    await ElMessageBox.confirm(`确认删除官网「${row.siteName}」？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    await deleteBrandOfficialSite(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  }
}

async function checkAuth(row: BrandOfficialSite) {
  const result = await checkAuthBrandOfficialSite(row.id)
  if (result.success) {
    ElMessage.success('探活成功')
  } else {
    ElMessage.error(`探活失败: ${result.failureKind || result.message || 'UNKNOWN'}`)
  }
  await load()
}

onMounted(load)
</script>

<style scoped>
.official-sites {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.official-sites__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.official-sites__title {
  font-size: 14px;
  font-weight: 600;
}
</style>
