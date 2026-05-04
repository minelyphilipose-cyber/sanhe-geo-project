<template>
  <div>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <el-input v-model="query.keyword" placeholder="搜索合伙人名称/编号" clearable style="width: 260px" @keyup.enter="load" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="load">
          <el-option
            v-for="item in dictStore.options('partner_status')"
            :key="item.dictKey"
            :label="item.dictValue"
            :value="item.dictKey"
          />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
      <el-button v-if="canCreatePartner" type="primary" @click="openCreate">新建合伙人</el-button>
    </div>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无合伙人数据">
        <el-table :data="rows" border>
        <el-table-column prop="partnerCode" label="编号" width="150" />
        <el-table-column prop="partnerName" label="名称" min-width="180" />
        <el-table-column label="等级" width="140">
          <template #default="scope">{{ dictStore.label('partner_level', scope.row.partnerLevel) }}</template>
        </el-table-column>
        <el-table-column label="折扣" width="100">
          <template #default="scope">{{ (scope.row.discountRate * 100).toFixed(1) }}%</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="scope">{{ dictStore.label('partner_status', scope.row.status) }}</template>
        </el-table-column>
        <el-table-column prop="city" label="城市" width="120" />
        <el-table-column prop="contactName" label="联系人" width="120" />
        <el-table-column prop="contactPhone" label="电话" width="140" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="goDetail(scope.row.id)">详情</el-button>
            <el-button v-if="canUpdatePartner" link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            <el-dropdown v-if="canUpdatePartnerStatus" @command="(v: string) => changeStatus(scope.row.id, v)">
              <el-button link type="primary" style="margin-left: 10px;margin-top: 3px;">
                改状态
              </el-button>
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

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新建合伙人' : '编辑合伙人'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="合伙人编号" required>
          <el-input v-model="form.partnerCode" :disabled="formMode === 'edit'" />
        </el-form-item>
        <el-form-item label="合伙人名称" required><el-input v-model="form.partnerName" /></el-form-item>
        <el-form-item label="等级" required>
          <el-select v-model="form.partnerLevel" style="width: 100%" @change="onPartnerLevelChange">
            <el-option
              v-for="item in dictStore.options('partner_level')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="折扣率" required>
          <el-input-number
            v-model="form.discountRate"
            :precision="4"
            :step="0.0001"
            :min="0.0001"
            :max="1"
            :controls="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="金额(元)" required>
          <el-input-number
            v-model="form.initialAmount"
            :precision="2"
            :step="100"
            :min="0"
            :controls="false"
            style="width: 100%"
            :disabled="formMode === 'edit'"
          />
        </el-form-item>
        <el-form-item v-if="formMode === 'edit'" label="状态" required>
          <el-select v-model="form.status" style="width: 100%">
            <el-option
              v-for="item in dictStore.options('partner_status')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="城市">
          <RegionCascader v-model="form.cityCodes" />
          <div class="mt-1 text-xs text-gray-500">{{ cityDisplayPreview || '未选择' }}</div>
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
  type PartnerCreateResult,
  type PartnerItem,
} from '@/api/partner'
import DataState from '@/components/ui/DataState.vue'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { chinaRegionOptions, regionPayloadFromCodes } from '@/constants/region'

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
const form = reactive({
  partnerCode: '',
  partnerName: '',
  partnerLevel: 'level_29800',
  discountRate: 0.3,
  initialAmount: 29800,
  status: 'active',
  contactName: '',
  contactPhone: '',
  city: '',
  cityCodes: [] as string[],
  remark: '',
})

const partnerLevelProfiles: Record<string, { discountRate: number; initialAmount: number }> = {
  level_29800: { discountRate: 0.3, initialAmount: 29800 },
  level_59800: { discountRate: 0.25, initialAmount: 59800 },
  level_99800: { discountRate: 0.2, initialAmount: 99800 },
}
const rules: FormRules = {
  partnerCode: [
    { required: true, message: '请输入合伙人编号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]{2,32}$/, message: '编号仅支持字母数字下划线中划线', trigger: 'blur' },
  ],
  partnerName: [{ required: true, message: '请输入合伙人名称', trigger: 'blur' }],
  partnerLevel: [{ required: true, message: '请选择等级', trigger: 'change' }],
  discountRate: [{ required: true, message: '请输入折扣率', trigger: 'change' }],
  initialAmount: [{ required: true, message: '请输入金额', trigger: 'change' }],
  contactPhone: [{ pattern: /^[0-9-+() ]{0,20}$/, message: '联系电话格式不正确', trigger: 'blur' }],
}

function resetForm() {
  form.partnerCode = ''
  form.partnerName = ''
  form.partnerLevel = 'level_29800'
  form.discountRate = 0.3
  form.initialAmount = 29800
  form.status = 'active'
  form.contactName = ''
  form.contactPhone = ''
  form.city = ''
  form.cityCodes = []
  form.remark = ''
}

function onPartnerLevelChange(level: string) {
  const profile = partnerLevelProfiles[level]
  if (!profile) return
  form.discountRate = profile.discountRate
  form.initialAmount = profile.initialAmount
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
  form.partnerLevel = row.partnerLevel
  form.discountRate = row.discountRate
  form.initialAmount = 0
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
  saving.value = true
  try {
    let createdId: number | null = null
    if (formMode.value === 'create') {
      const { data } = await createPartner({
        partnerCode: form.partnerCode,
        partnerName: form.partnerName,
        partnerLevel: form.partnerLevel,
        discountRate: form.discountRate,
        initialAmount: Number(form.initialAmount.toFixed(2)),
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
        partnerLevel: form.partnerLevel,
        discountRate: form.discountRate,
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
