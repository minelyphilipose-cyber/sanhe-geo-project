<template>
  <div>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <el-input v-model="query.keyword" placeholder="搜索用户名/姓名/手机号" clearable style="width: 280px" @keyup.enter="load" />
        <el-select v-model="query.roleKey" placeholder="角色" clearable style="width: 200px" @change="load">
          <el-option v-for="r in roles" :key="r.roleKey" :label="`${r.roleName} (${r.roleKey})`" :value="r.roleKey" />
        </el-select>
        <el-select v-model="query.isActive" placeholder="状态" clearable style="width: 130px" @change="load">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
      <el-button type="primary" @click="openCreate">新建账号</el-button>
    </div>

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无账号数据">
        <el-table :data="rows" border>
          <el-table-column prop="username" label="用户名" min-width="140" />
          <el-table-column prop="displayName" label="姓名" min-width="140" />
          <el-table-column label="角色" min-width="200">
            <template #default="scope">{{ scope.row.roleKeys?.join(', ') || scope.row.primaryRole }}</template>
          </el-table-column>
          <el-table-column prop="partnerId" label="合伙人ID" width="110" />
          <el-table-column prop="phone" label="手机" width="140" />
          <el-table-column prop="email" label="邮箱" min-width="180" />
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <el-tag :type="scope.row.isActive ? 'success' : 'danger'">{{ scope.row.isActive ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="360" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
              <el-button link type="primary" @click="openRole(scope.row)">分配角色</el-button>
              <el-button link type="warning" @click="openResetPassword(scope.row)">重置密码</el-button>
              <el-button link :type="scope.row.isActive ? 'danger' : 'success'" @click="toggleStatus(scope.row)">
                {{ scope.row.isActive ? '停用' : '启用' }}
              </el-button>
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

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新建账号' : '编辑账号'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="用户名" prop="username" required>
          <el-input v-model="form.username" :disabled="formMode === 'edit'" />
        </el-form-item>
        <el-form-item v-if="formMode === 'create'" label="初始密码" prop="password" required>
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="姓名" prop="displayName" required><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item v-if="formMode === 'create'" label="角色" prop="roleKey" required>
          <el-select v-model="form.roleKey" style="width: 100%">
            <el-option v-for="r in roles" :key="r.roleKey" :label="`${r.roleName} (${r.roleKey})`" :value="r.roleKey" />
          </el-select>
        </el-form-item>
        <el-form-item label="合伙人ID"><el-input-number v-model="form.partnerId" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="分配角色" width="520px">
      <el-form :model="roleForm" label-width="100px">
        <el-form-item label="角色" required>
          <el-select v-model="roleForm.roleKey" style="width: 100%">
            <el-option v-for="r in roles" :key="r.roleKey" :label="`${r.roleName} (${r.roleKey})`" :value="r.roleKey" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRole">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdVisible" title="重置密码" width="520px">
      <el-form :model="pwdForm" label-width="110px">
        <el-form-item label="新密码" required>
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPassword">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import {
  bindAdminUserRole,
  createAdminUser,
  getAdminUsers,
  getRoleOptions,
  resetAdminUserPassword,
  updateAdminUser,
  updateAdminUserStatus,
  type AdminUserItem,
  type RoleOption,
} from '@/api/system'

const loading = ref(false)
const saving = ref(false)
const rows = ref<AdminUserItem[]>([])
const roles = ref<RoleOption[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive<{ keyword: string; roleKey: string; isActive: boolean | undefined }>({
  keyword: '',
  roleKey: '',
  isActive: undefined,
})

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const formMode = ref<'create' | 'edit'>('create')
const currentUserId = ref<number | null>(null)

const form = reactive({
  username: '',
  password: '',
  displayName: '',
  roleKey: '',
  partnerId: null as number | null,
  phone: '',
  email: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  roleKey: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

const roleVisible = ref(false)
const roleForm = reactive({ roleKey: '' })

const pwdVisible = ref(false)
const pwdForm = reactive({ newPassword: '' })

function resetForm() {
  form.username = ''
  form.password = ''
  form.displayName = ''
  form.roleKey = ''
  form.partnerId = null
  form.phone = ''
  form.email = ''
}

async function loadRoles() {
  const { data } = await getRoleOptions()
  roles.value = data.data || []
}

async function load() {
  loading.value = true
  try {
    const { data } = await getAdminUsers({
      current: page.current,
      size: page.size,
      keyword: query.keyword || undefined,
      roleKey: query.roleKey || undefined,
      isActive: query.isActive,
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
  currentUserId.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(row: AdminUserItem) {
  formMode.value = 'edit'
  currentUserId.value = row.id
  form.username = row.username
  form.displayName = row.displayName
  form.roleKey = row.primaryRole
  form.partnerId = row.partnerId
  form.phone = row.phone || ''
  form.email = row.email || ''
  form.password = ''
  formVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (formMode.value === 'create') {
      await createAdminUser({
        username: form.username,
        password: form.password,
        displayName: form.displayName,
        roleKey: form.roleKey,
        partnerId: form.partnerId || undefined,
        phone: form.phone || undefined,
        email: form.email || undefined,
      })
      ElMessage.success('账号创建成功')
    } else if (currentUserId.value) {
      await updateAdminUser(currentUserId.value, {
        displayName: form.displayName,
        partnerId: form.partnerId || undefined,
        phone: form.phone || undefined,
        email: form.email || undefined,
      })
      ElMessage.success('账号更新成功')
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function openRole(row: AdminUserItem) {
  currentUserId.value = row.id
  roleForm.roleKey = row.primaryRole
  roleVisible.value = true
}

async function submitRole() {
  if (!currentUserId.value || !roleForm.roleKey) return
  saving.value = true
  try {
    await bindAdminUserRole(currentUserId.value, roleForm.roleKey)
    ElMessage.success('角色分配成功')
    roleVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function openResetPassword(row: AdminUserItem) {
  currentUserId.value = row.id
  pwdForm.newPassword = ''
  pwdVisible.value = true
}

async function submitPassword() {
  if (!currentUserId.value) return
  if (!pwdForm.newPassword || pwdForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少6位')
    return
  }
  saving.value = true
  try {
    await resetAdminUserPassword(currentUserId.value, pwdForm.newPassword)
    ElMessage.success('密码已重置')
    pwdVisible.value = false
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: AdminUserItem) {
  try {
    await ElMessageBox.confirm(
      `确认将账号“${row.username}”${row.isActive ? '停用' : '启用'}？`,
      '状态变更确认',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
    await updateAdminUserStatus(row.id, !row.isActive)
    ElMessage.success('状态已更新')
    await load()
  } catch {
    // canceled
  }
}

onMounted(async () => {
  await loadRoles()
  await load()
})
</script>
