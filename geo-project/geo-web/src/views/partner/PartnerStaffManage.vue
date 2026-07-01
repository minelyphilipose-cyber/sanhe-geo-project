<template>
  <div class="partner-staff-page">
    <el-card shadow="never">
      <template #header>
        <div class="page-head">
          <div>
            <div class="page-title">交付员工</div>
            <div class="page-subtitle">每个合伙人当前仅允许创建一个交付员工账号。</div>
          </div>
          <el-button v-if="staffList.length === 0" type="primary" @click="openCreate">创建员工</el-button>
        </div>
      </template>

      <DataState :loading="loading" :empty="!loading && staffList.length === 0" empty-text="暂无交付员工">
        <el-table :data="staffList" border table-layout="fixed">
          <el-table-column prop="displayName" label="姓名" min-width="160" />
          <el-table-column prop="username" label="账号" min-width="180" />
          <el-table-column prop="phone" label="电话" width="160" show-overflow-tooltip />
          <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />
          <el-table-column label="状态" width="110">
            <template #default="scope">
              <span class="staff-status" :class="{ 'is-active': scope.row.isActive }">
                {{ scope.row.isActive ? '启用' : '停用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="toggleStatus(scope.row)">
                {{ scope.row.isActive ? '停用' : '启用' }}
              </el-button>
              <el-button link type="primary" @click="resetPassword(scope.row)">重置密码</el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <el-dialog v-model="createVisible" title="创建交付员工" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="账号" prop="username" required>
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="姓名" prop="displayName" required>
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createMyPartnerStaff,
  getMyPartnerStaff,
  resetMyPartnerStaffPassword,
  updateMyPartnerStaffStatus,
  type PartnerStaff,
} from '@/api/partner'
import DataState from '@/components/ui/DataState.vue'

const loading = ref(false)
const saving = ref(false)
const createVisible = ref(false)
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
}

function resetForm() {
  form.username = ''
  form.displayName = ''
  form.phone = ''
  form.email = ''
}

function openCreate() {
  resetForm()
  createVisible.value = true
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

async function submitCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const { data } = await createMyPartnerStaff({
      username: form.username,
      displayName: form.displayName,
      phone: form.phone || undefined,
      email: form.email || undefined,
    })
    createVisible.value = false
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

onMounted(load)
</script>

<style scoped>
.partner-staff-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-title {
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.page-subtitle {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.staff-status {
  color: #b45309;
  font-weight: 700;
}

.staff-status.is-active {
  color: #047857;
}
</style>
