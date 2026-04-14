<template>
  <div class="space-y-4">
    <el-card>
      <template #header>
        <div class="flex items-center justify-between">
          <span>我的客户</span>
          <div class="flex items-center gap-2">
            <el-input v-model="keyword" clearable placeholder="搜索客户名称" style="width: 240px" @keyup.enter="load" />
            <el-button @click="load">查询</el-button>
            <el-button v-if="canWriteCompany" type="primary" @click="openCreate">新增客户</el-button>
          </div>
        </div>
      </template>

      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无客户">
        <el-table :data="rows" border>
          <el-table-column prop="companyName" label="客户名称" min-width="220" />
          <el-table-column prop="contactName" label="联系人" width="140" />
          <el-table-column prop="contactPhone" label="联系电话" width="160" />
          <el-table-column prop="industry" label="行业" width="140" />
          <el-table-column label="地区" min-width="220">
            <template #default="scope">{{ region(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="scope">{{ dictStore.label('company_status', scope.row.status) }}</template>
          </el-table-column>
          <el-table-column v-if="canWriteCompany" label="操作" width="100" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新增客户' : '编辑客户'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="公司名称" prop="companyName" required><el-input v-model="form.companyName" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="行业"><el-input v-model="form.industry" /></el-form-item>
        <el-form-item label="主营方向"><el-input v-model="form.businessDirection" /></el-form-item>
        <el-form-item label="官网"><el-input v-model="form.officialWebsite" /></el-form-item>
        <el-form-item label="地区"><RegionCascader v-model="form.regionCodes" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option
              v-for="item in dictStore.options('company_status')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createCompany, getCompanyList, updateCompany } from '@/api/customer'
import type { Company } from '@/types'
import DataState from '@/components/ui/DataState.vue'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionCodesFromPayload, regionDisplayFromPayload, regionPayloadFromCodes } from '@/constants/region'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'

const dictStore = useDictStore()
const userStore = useUserStore()
const canWriteCompany = computed(() => userStore.hasPermission('company.write'))

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const rows = ref<Company[]>([])
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

function region(company: Company) {
  return regionDisplayFromPayload(company) || company.city || '-'
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const regionPayload = regionPayloadFromCodes(form.regionCodes)
    const payload = {
      companyName: form.companyName,
      contactName: form.contactName || undefined,
      contactPhone: form.contactPhone || undefined,
      industry: form.industry || undefined,
      businessDirection: form.businessDirection || undefined,
      officialWebsite: form.officialWebsite || undefined,
      provinceCode: regionPayload.provinceCode,
      provinceName: regionPayload.provinceName,
      cityCode: regionPayload.cityCode,
      cityName: regionPayload.cityName,
      districtCode: regionPayload.districtCode,
      districtName: regionPayload.districtName,
      sourceType: 'partner',
      status: form.status,
      remark: form.remark || undefined,
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
    const { data } = await getCompanyList({
      current: 1,
      size: 200,
      keyword: keyword.value || undefined,
    })
    rows.value = data.data.records || []
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
