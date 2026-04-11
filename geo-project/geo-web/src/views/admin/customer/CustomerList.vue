<template>
  <div>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <el-input v-model="query.keyword" placeholder="搜索公司名称" clearable style="width: 260px" @keyup.enter="load" />
        <el-select v-model="query.ownerType" placeholder="归属类型" clearable style="width: 140px" @change="load">
          <el-option label="direct" value="direct" />
          <el-option label="partner" value="partner" />
          <el-option label="joint" value="joint" />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
      <el-button type="primary" @click="openCreate">新建客户</el-button>
    </div>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无客户数据">
        <el-table :data="rows" border>
        <el-table-column prop="companyName" label="公司名称" min-width="220" />
        <el-table-column prop="industry" label="行业" width="120" />
        <el-table-column prop="city" label="城市" width="120" />
        <el-table-column prop="ownerType" label="归属" width="100" />
        <el-table-column prop="partnerId" label="合伙人ID" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="goDetail(scope.row.id)">详情</el-button>
            <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
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

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新建客户' : '编辑客户'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="公司名称" required><el-input v-model="form.companyName" /></el-form-item>
        <el-form-item label="行业"><el-input v-model="form.industry" /></el-form-item>
        <el-form-item label="城市"><el-input v-model="form.city" /></el-form-item>
        <el-form-item label="归属类型" required>
          <el-select v-model="form.ownerType" style="width: 100%">
            <el-option label="direct" value="direct" />
            <el-option label="partner" value="partner" />
            <el-option label="joint" value="joint" />
          </el-select>
        </el-form-item>
        <el-form-item label="合伙人ID"><el-input-number v-model="form.partnerId" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="销售ID"><el-input-number v-model="form.salesOwnerId" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="来源"><el-input v-model="form.referralSource" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="potential" value="potential" />
            <el-option label="signed" value="signed" />
            <el-option label="inactive" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createCompany, getCompanyList, updateCompany } from '@/api/customer'
import type { Company } from '@/types'
import DataState from '@/components/ui/DataState.vue'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const rows = ref<Company[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive({ keyword: '', ownerType: '' })

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)

const form = reactive({
  companyName: '',
  industry: '',
  city: '',
  ownerType: 'direct',
  partnerId: null as number | null,
  salesOwnerId: null as number | null,
  referralSource: '',
  status: 'potential',
  remark: '',
})
const rules: FormRules = {
  companyName: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
  ownerType: [{ required: true, message: '请选择归属类型', trigger: 'change' }],
}

function resetForm() {
  form.companyName = ''
  form.industry = ''
  form.city = ''
  form.ownerType = 'direct'
  form.partnerId = null
  form.salesOwnerId = null
  form.referralSource = ''
  form.status = 'potential'
  form.remark = ''
}

async function load() {
  loading.value = true
  try {
    const { data } = await getCompanyList({
      current: page.current,
      size: page.size,
      keyword: query.keyword || undefined,
      ownerType: query.ownerType || undefined,
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

function openEdit(row: Company) {
  formMode.value = 'edit'
  editingId.value = row.id
  form.companyName = row.companyName
  form.industry = row.industry || ''
  form.city = row.city || ''
  form.ownerType = row.ownerType
  form.partnerId = row.partnerId
  form.salesOwnerId = row.salesOwnerId
  form.referralSource = row.referralSource || ''
  form.status = (row as any).status || 'potential'
  form.remark = (row as any).remark || ''
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if ((form.ownerType === 'partner' || form.ownerType === 'joint') && !form.partnerId) {
    ElMessage.warning('partner/joint 客户需填写合伙人ID')
    return
  }
  saving.value = true
  try {
    const payload = {
      companyName: form.companyName,
      industry: form.industry || undefined,
      city: form.city || undefined,
      ownerType: form.ownerType,
      partnerId: form.partnerId || undefined,
      salesOwnerId: form.salesOwnerId || undefined,
      referralSource: form.referralSource || undefined,
      status: form.status,
      remark: form.remark || undefined,
    }
    if (formMode.value === 'create') {
      const { data } = await createCompany(payload)
      formVisible.value = false
      ElMessage.success('保存成功')
      await load()
      goDetail(data.data.id)
    } else if (editingId.value) {
      await updateCompany(editingId.value, payload)
      formVisible.value = false
      ElMessage.success('保存成功')
      load()
    }
  } finally {
    saving.value = false
  }
}

function goDetail(id: number) {
  router.push(`/admin/customers/${id}`)
}

onMounted(load)
</script>

