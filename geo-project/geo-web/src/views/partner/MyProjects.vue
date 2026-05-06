<template>
  <div class="space-y-4">
    <el-card>
      <template #header>
        <div class="flex items-center justify-between">
          <span>我的项目</span>
          <div class="flex items-center gap-2">
            <el-input v-model="keyword" clearable placeholder="搜索项目名称" style="width: 240px" @keyup.enter="load" />
            <el-button @click="load">查询</el-button>
            <el-button v-if="canCreateProject" type="primary" @click="openCreate">新增项目</el-button>
          </div>
        </div>
      </template>

      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无项目">
        <el-table :data="rows" border>
          <el-table-column prop="projectName" label="项目名称" min-width="220" />
          <el-table-column prop="companyName" label="客户" min-width="180" />
          <el-table-column prop="brandName" label="品牌" min-width="160" />
          <el-table-column label="项目状态" width="120">
            <template #default="scope">{{ dictStore.label('project_status', scope.row.status) || scope.row.status }}</template>
          </el-table-column>
          <el-table-column label="阶段" width="180">
            <template #default="scope">{{ dictStore.label('project_stage', scope.row.stage) || scope.row.stage }}</template>
          </el-table-column>
          <el-table-column v-if="canUpdateProject" label="操作" width="100" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新增项目' : '编辑项目'" width="760px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="项目名称" prop="projectName" required><el-input v-model="form.projectName" /></el-form-item>
        <el-form-item label="项目别名">
          <el-input v-model="form.projectAliases" placeholder="多个别名用英文逗号分隔" />
        </el-form-item>
        <el-form-item label="客户" prop="companyId" required>
          <el-select
            v-model="form.companyId"
            filterable
            style="width: 100%"
            placeholder="先选择客户"
            :disabled="formMode === 'edit'"
            @change="onCompanyChange"
          >
            <el-option v-for="c in companyOptions" :key="c.id" :label="c.companyName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌" prop="brandId" required>
          <el-select
            v-model="form.brandId"
            filterable
            style="width: 100%"
            placeholder="请选择品牌；选品牌前需先选客户"
            :disabled="!form.companyId || formMode === 'edit' || dependencyLoading"
            :loading="brandLoading"
          >
            <el-option v-for="b in brandOptions" :key="b.id" :label="b.brandName" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="拓词组" prop="keywordGroupIds" required>
          <div class="w-full">
            <el-select
              v-model="form.keywordGroupIds"
              style="width: 100%"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              :max-collapse-tags="3"
              placeholder="请选择当前客户下的拓词组"
              :disabled="!form.companyId || dependencyLoading"
              :loading="keywordGroupLoading"
            >
              <el-option
                v-for="kg in keywordGroupOptions"
                :key="kg.id"
                :label="`${kg.name}（已入库${kg.savedKeywordCount || 0}条）`"
                :value="kg.id"
              />
            </el-select>
            <div class="keyword-summary">{{ keywordGroupSummary }}</div>
          </div>
        </el-form-item>
        <el-form-item label="地区"><RegionCascader v-model="form.regionCodes" /></el-form-item>
        <el-form-item v-if="formMode === 'edit'" label="激活状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :label="dictStore.label('project_status', 'active')" value="active" />
            <el-option :label="dictStore.label('project_status', 'paused')" value="paused" />
          </el-select>
        </el-form-item>
        <el-form-item label="交付模式"><el-input v-model="form.deliveryMode" /></el-form-item>
        <el-form-item label="主目标"><el-input v-model="form.primaryGoal" type="textarea" :rows="3" /></el-form-item>
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
import { getBrandList, getCompanyList } from '@/api/customer'
import {
  createProject,
  getKeywordGroupPage,
  getProjectList,
  updateProject,
  updateProjectStatus,
} from '@/api/project'
import type { Brand, Company, KeywordGroup, Project } from '@/types'
import DataState from '@/components/ui/DataState.vue'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionCodesFromPayload, regionPayloadFromCodes } from '@/constants/region'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'

const dictStore = useDictStore()
const userStore = useUserStore()
const canCreateProject = computed(() => userStore.hasPermission('project.create'))
const canUpdateProject = computed(() => userStore.hasPermission('project.update'))

const loading = ref(false)
const saving = ref(false)
const brandLoading = ref(false)
const keywordGroupLoading = ref(false)
const keyword = ref('')
const rows = ref<Project[]>([])
const companyOptions = ref<Company[]>([])
const brandOptions = ref<Brand[]>([])
const keywordGroupOptions = ref<KeywordGroup[]>([])

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const originalStatus = ref<'active' | 'paused'>('paused')

const form = reactive({
  projectName: '',
  projectAliases: '',
  companyId: null as number | null,
  brandId: null as number | null,
  keywordGroupIds: [] as number[],
  status: 'paused' as 'active' | 'paused',
  regionCodes: [] as string[],
  deliveryMode: 'managed',
  primaryGoal: '',
  remark: '',
})

const rules: FormRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  companyId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  brandId: [{ required: true, message: '请选择品牌', trigger: 'change' }],
  keywordGroupIds: [{ required: true, type: 'array', min: 1, max: 10, message: '请选择 1-10 个拓词组', trigger: 'change' }],
}

const dependencyLoading = computed(() => brandLoading.value || keywordGroupLoading.value)

const keywordGroupSummary = computed(() => {
  const selected = new Set(form.keywordGroupIds)
  let saved = 0
  for (const group of keywordGroupOptions.value) {
    if (selected.has(group.id)) {
      saved += group.savedKeywordCount || 0
    }
  }
  return `已选 ${form.keywordGroupIds.length} 个拓词组，已入库 ${saved} 条关键词`
})

function resetForm() {
  form.projectName = ''
  form.projectAliases = ''
  form.companyId = null
  form.brandId = null
  form.keywordGroupIds = []
  form.status = 'paused'
  form.regionCodes = []
  form.deliveryMode = 'managed'
  form.primaryGoal = ''
  form.remark = ''
}

async function onCompanyChange() {
  if (!form.companyId) {
    form.brandId = null
    brandOptions.value = []
    keywordGroupOptions.value = []
    form.keywordGroupIds = []
    return
  }
  if (formMode.value === 'create') {
    form.brandId = null
  }
  form.keywordGroupIds = []
  await Promise.all([loadBrands(form.companyId), loadKeywordGroups(form.companyId)])
}

function openCreate() {
  formMode.value = 'create'
  editingId.value = null
  resetForm()
  formVisible.value = true
}

async function openEdit(row: Project) {
  formMode.value = 'edit'
  editingId.value = row.id
  form.projectName = row.projectName
  form.projectAliases = row.projectAliases || ''
  form.companyId = row.companyId || null
  form.brandId = row.brandId
  form.keywordGroupIds = [...(row.selectedKeywordGroupIds || [])]
  form.status = row.status === 'active' ? 'active' : 'paused'
  originalStatus.value = form.status
  form.regionCodes = regionCodesFromPayload(row)
  form.deliveryMode = row.deliveryMode || 'managed'
  form.primaryGoal = row.primaryGoal || ''
  form.remark = row.remark || ''
  await Promise.all([loadBrands(form.companyId), loadKeywordGroups(form.companyId)])
  formVisible.value = true
}

async function submit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  if (!form.companyId || !form.brandId) {
    return
  }

  saving.value = true
  try {
    const region = regionPayloadFromCodes(form.regionCodes)
    const payload = {
      projectName: form.projectName,
      projectAliases: form.projectAliases || undefined,
      companyId: form.companyId,
      brandId: form.brandId,
      keywordGroupIds: form.keywordGroupIds,
      deliveryMode: form.deliveryMode || 'managed',
      provinceCode: region.provinceCode,
      provinceName: region.provinceName,
      cityCode: region.cityCode,
      cityName: region.cityName,
      districtCode: region.districtCode,
      districtName: region.districtName,
      primaryGoal: form.primaryGoal || undefined,
      remark: form.remark || undefined,
    }

    if (formMode.value === 'create') {
      await createProject(payload)
    } else if (editingId.value) {
      await updateProject(editingId.value, payload)
      if (form.status !== originalStatus.value) {
        await updateProjectStatus(editingId.value, form.status)
      }
    }

    formVisible.value = false
    ElMessage.success('保存成功')
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function loadCompanies() {
  try {
    const { data } = await getCompanyList({ current: 1, size: 500 })
    companyOptions.value = data.data.records || []
  } catch {
    companyOptions.value = []
  }
}

async function loadBrands(companyId?: number | null) {
  brandLoading.value = true
  try {
    const { data } = await getBrandList({ current: 1, size: 500, companyId: companyId || undefined })
    brandOptions.value = data.data.records || []
  } catch (err) {
    brandOptions.value = []
    ElMessage.error(errorMessage(err, '加载品牌失败'))
  } finally {
    brandLoading.value = false
  }
}

async function loadKeywordGroups(companyId?: number | null) {
  if (!companyId) {
    keywordGroupOptions.value = []
    form.keywordGroupIds = []
    return
  }
  keywordGroupLoading.value = true
  try {
    const { data } = await getKeywordGroupPage({ current: 1, size: 500, companyId })
    keywordGroupOptions.value = data.data.records || []
    const validIds = new Set(keywordGroupOptions.value.map((item) => item.id))
    form.keywordGroupIds = form.keywordGroupIds.filter((id) => validIds.has(id))
  } catch (err) {
    keywordGroupOptions.value = []
    form.keywordGroupIds = []
    ElMessage.error(errorMessage(err, '加载拓词组失败'))
  } finally {
    keywordGroupLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await getProjectList({
      current: 1,
      size: 200,
      keyword: keyword.value || undefined,
    })
    rows.value = data.data.records || []
  } catch {
    rows.value = []
    ElMessage.error('加载项目失败')
  } finally {
    loading.value = false
  }
}

function errorMessage(err: unknown, fallback: string) {
  const data = (err as any)?.response?.data
  return data?.message || data?.msg || (err as any)?.message || fallback
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await loadCompanies()
  await load()
})
</script>

<style scoped>
.keyword-summary {
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
}
</style>
