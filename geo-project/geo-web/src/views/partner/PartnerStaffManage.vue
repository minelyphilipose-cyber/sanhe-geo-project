<template>
  <div class="partner-page">
    <div class="partner-page-header">
      <div>
        <div class="partner-page-kicker">团队协作</div>
        <h1 class="partner-page-title">交付员工</h1>
        <div class="partner-page-subtitle">合伙人可以创建交付员工账号；交付员工只能查看归属自己的客户和项目。</div>
      </div>
      <div class="partner-page-actions">
        <el-button v-if="staffList.length === 0" type="primary" @click="openCreate">创建员工</el-button>
      </div>
    </div>

    <el-card shadow="never" class="partner-table-card">
      <DataState :loading="loading" :empty="!loading && staffList.length === 0" empty-text="暂无交付员工">
        <el-table :data="staffList" border table-layout="fixed">
          <el-table-column label="员工" min-width="240">
            <template #default="scope">
              <div class="partner-entity-cell">
                <div class="partner-entity-avatar is-violet">{{ entityInitial(scope.row.displayName || scope.row.username) }}</div>
                <div class="min-w-0">
                  <div class="partner-entity-main">{{ scope.row.displayName || scope.row.username }}</div>
                  <div class="partner-entity-sub">{{ scope.row.username }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="联系方式" min-width="260">
            <template #default="scope">
              <div class="partner-cell-stack">
                <span class="partner-cell-main">{{ scope.row.phone || '未填写电话' }}</span>
                <span class="partner-cell-sub">{{ scope.row.email || '未填写邮箱' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="scope">
              <span class="partner-status-tag" :class="scope.row.isActive ? 'is-success' : 'is-muted'">
                {{ scope.row.isActive ? '启用' : '停用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="320" fixed="right">
            <template #default="scope">
              <div class="partner-row-actions">
                <el-button link type="primary" @click="toggleStatus(scope.row)">
                  {{ scope.row.isActive ? '停用' : '启用' }}
                </el-button>
                <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
                <el-button link type="primary" @click="resetPassword(scope.row)">重置密码</el-button>
                <el-button link type="danger" @click="removeStaff(scope.row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <el-dialog
      v-model="formVisible"
      :title="formMode === 'create' ? '创建交付员工' : '编辑交付员工'"
      width="640px"
      class="partner-form-dialog"
    >
      <div class="partner-dialog-tip">
        <span class="partner-dialog-tip-icon">i</span>
        <span>{{ formMode === 'create' ? '交付员工创建后仅可查看分配给自己的客户和项目；当前每个合伙人只允许保留一个启用的交付员工账号。' : '登录账号不可修改，可维护员工姓名、手机号和邮箱。' }}</span>
      </div>
      <el-form ref="formRef" class="partner-dialog-form-grid" :model="form" :rules="rules" label-position="top">
        <el-form-item v-if="formMode === 'create'" label="账号" prop="username" required>
          <el-input v-model="form.username" placeholder="请输入登录账号" />
        </el-form-item>
        <el-form-item v-else label="账号">
          <el-input v-model="form.username" disabled />
        </el-form-item>
        <el-form-item label="姓名" prop="displayName" required>
          <el-input v-model="form.displayName" placeholder="请输入员工姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱地址" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="partner-dialog-footer">
          <el-button @click="formVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">{{ formMode === 'create' ? '创建' : '保存' }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createMyPartnerStaff,
  deleteMyPartnerStaff,
  getMyPartnerStaff,
  resetMyPartnerStaffPassword,
  updateMyPartnerStaff,
  updateMyPartnerStaffStatus,
  type PartnerStaff,
} from '@/api/partner'
import DataState from '@/components/ui/DataState.vue'
import { isValidEmail, isValidMobile } from '@/utils/form'

const loading = ref(false)
const saving = ref(false)
const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const staffList = ref<PartnerStaff[]>([])
const form = reactive({
  username: '',
  displayName: '',
  phone: '',
  email: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]{2,32}$/, message: '账号仅支持字母数字下划线中划线', trigger: 'blur' },
  ],
  displayName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{
    validator: (_rule, value: string, callback) => {
      callback(isValidMobile(value) ? undefined : new Error('请输入正确的手机号'))
    },
    trigger: 'blur',
  }],
  email: [{
    validator: (_rule, value: string, callback) => {
      callback(isValidEmail(value) ? undefined : new Error('请输入正确的邮箱地址'))
    },
    trigger: 'blur',
  }],
}

function entityInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '员'
}

function resetForm() {
  editingId.value = null
  form.username = ''
  form.displayName = ''
  form.phone = ''
  form.email = ''
  formRef.value?.clearValidate()
}

function openCreate() {
  formMode.value = 'create'
  resetForm()
  formVisible.value = true
}

function openEdit(staff: PartnerStaff) {
  formMode.value = 'edit'
  editingId.value = staff.id
  form.username = staff.username
  form.displayName = staff.displayName || ''
  form.phone = staff.phone || ''
  form.email = staff.email || ''
  formVisible.value = true
}

async function load() {
  loading.value = true
  try {
    const { data } = await getMyPartnerStaff()
    staffList.value = data.data || []
  } catch {
    staffList.value = []
    ElMessage.error('加载交付员工失败')
  } finally {
    loading.value = false
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (formMode.value === 'edit' && editingId.value) {
      await updateMyPartnerStaff(editingId.value, {
        displayName: form.displayName,
        phone: form.phone || undefined,
        email: form.email || undefined,
      })
      formVisible.value = false
      ElMessage.success('员工信息已保存')
      await load()
      return
    }
    const { data } = await createMyPartnerStaff({
      username: form.username,
      displayName: form.displayName,
      phone: form.phone || undefined,
      email: form.email || undefined,
    })
    formVisible.value = false
    await load()
    await ElMessageBox.alert(
      `员工账号已创建\n账号：${data.data.staff.username}\n初始密码：${data.data.initialPassword}`,
      '账号已生成',
      { confirmButtonText: '我已记录' },
    )
  } finally {
    saving.value = false
  }
}

async function toggleStatus(staff: PartnerStaff) {
  const nextActive = !staff.isActive
  await ElMessageBox.confirm(`确认${nextActive ? '启用' : '停用'}该员工账号？`, '状态确认', {
    type: 'warning',
  })
  await updateMyPartnerStaffStatus(staff.id, nextActive)
  ElMessage.success('状态已更新')
  await load()
}

async function resetPassword(staff: PartnerStaff) {
  await ElMessageBox.confirm(`确认重置「${staff.displayName || staff.username}」的登录密码？`, '重置密码', {
    type: 'warning',
  })
  const { data } = await resetMyPartnerStaffPassword(staff.id)
  await ElMessageBox.alert(
    `新密码：${data.data.newPassword}`,
    '密码已重置',
    { confirmButtonText: '我已记录' },
  )
}

async function removeStaff(staff: PartnerStaff) {
  await ElMessageBox.confirm(
    `确认删除「${staff.displayName || staff.username}」？删除后该员工账号无法登录，已分配给该员工的客户会变为未分配。`,
    '删除交付员工',
    {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    },
  )
  await deleteMyPartnerStaff(staff.id)
  ElMessage.success('员工已删除')
  await load()
}

onMounted(load)
</script>
