<template>
  <div class="user-manage-page admin-page">
    <div class="admin-page-header user-header">
      <div>
        <div class="admin-page-kicker">系统配置</div>
        <h1 class="admin-page-title">用户与权限</h1>
        <div class="admin-page-subtitle">维护后台账号、角色归属、合伙人绑定和账号启停状态。</div>
      </div>
      <div class="admin-page-actions">
        <el-button type="primary" @click="openCreate">新建账号</el-button>
      </div>
    </div>

    <el-card shadow="never" class="admin-surface user-toolbar-card">
      <div class="user-toolbar">
        <el-input v-model="query.keyword" class="filter-keyword" placeholder="搜索用户名/姓名/手机号" clearable @keyup.enter="load" />
        <el-select v-model="query.roleKey" class="filter-role" placeholder="角色" clearable @change="load">
          <el-option v-for="r in roles" :key="r.roleKey" :label="dictStore.label('role', r.roleKey)" :value="r.roleKey" />
        </el-select>
        <el-select v-model="query.isActive" class="filter-status" placeholder="状态" clearable @change="load">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-button type="primary" plain @click="load">查询</el-button>
      </div>
    </el-card>

    <div class="admin-metric-grid user-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">账号总数</span>
        <strong class="admin-metric-value">{{ page.total }}</strong>
        <span class="admin-metric-hint">当前筛选结果</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">启用账号</span>
        <strong class="admin-metric-value">{{ activeCount }}</strong>
        <span class="admin-metric-hint">当前页可登录账号</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">停用账号</span>
        <strong class="admin-metric-value">{{ inactiveCount }}</strong>
        <span class="admin-metric-hint">当前页已停用账号</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #7c3aed; --metric-tone: #f5f3ff">
        <span class="admin-metric-label">角色选项</span>
        <strong class="admin-metric-value">{{ roles.length }}</strong>
        <span class="admin-metric-hint">可分配角色数量</span>
      </div>
    </div>

    <el-card shadow="never" class="admin-table-card user-table-card">
      <div class="table-header">
        <div>
          <div class="table-title">账号列表</div>
          <div class="table-subtitle">按角色、状态和合伙人归属核对后台账号权限。</div>
        </div>
        <div class="chips">
          <span class="chip chip-muted">当前页 {{ rows.length }}</span>
          <span class="chip chip-success">启用 {{ activeCount }}</span>
          <span class="chip chip-warning">停用 {{ inactiveCount }}</span>
        </div>
      </div>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无账号数据">
        <el-table :data="rows" border table-layout="fixed">
          <el-table-column label="账号" min-width="240" show-overflow-tooltip>
            <template #default="scope">
              <div class="admin-entity-cell">
                <div class="admin-entity-avatar user-avatar" :class="scope.row.isActive ? 'is-success' : 'is-muted'">
                  {{ userInitial(scope.row.displayName || scope.row.username) }}
                </div>
                <div class="min-w-0">
                  <div class="admin-entity-main">{{ scope.row.displayName }}</div>
                  <div class="admin-entity-sub">{{ scope.row.username }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="角色" min-width="200">
            <template #default="scope">
              <div class="role-tags">
                <span v-for="item in scope.row.roleKeys || [scope.row.primaryRole]" :key="item" class="role-tag">
                  {{ dictStore.label('role', item) }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="所属合伙人" min-width="160" show-overflow-tooltip>
            <template #default="scope">{{ partnerName(scope.row.partnerId) }}</template>
          </el-table-column>
          <el-table-column prop="phone" label="手机" width="140" />
          <el-table-column prop="email" label="邮箱" min-width="190" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <span class="admin-status-tag" :class="scope.row.isActive ? 'is-success' : 'is-danger'">
                {{ scope.row.isActive ? '启用' : '停用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
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

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新建账号' : '编辑账号'" width="840px" class="admin-editor-dialog user-editor-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="user-form">
        <section class="form-section">
          <div class="form-section-head">
            <div>
              <span>账号信息</span>
              <strong>登录名、姓名与初始角色</strong>
            </div>
          </div>
          <div class="form-grid is-two">
            <el-form-item label="用户名" prop="username" required>
              <el-input v-model="form.username" :disabled="formMode === 'edit'" placeholder="用于后台登录" />
            </el-form-item>
            <el-form-item label="姓名" prop="displayName" required>
              <el-input v-model="form.displayName" placeholder="账号展示名称" />
            </el-form-item>
            <el-form-item v-if="formMode === 'create'" label="初始密码" prop="password" required>
              <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
            </el-form-item>
            <el-form-item v-if="formMode === 'create'" label="角色" prop="roleKey" required>
              <el-select v-model="form.roleKey">
                <el-option v-for="r in roles" :key="r.roleKey" :label="dictStore.label('role', r.roleKey)" :value="r.roleKey" />
              </el-select>
            </el-form-item>
          </div>
        </section>

        <section class="form-section">
          <div class="form-section-head">
            <div>
              <span>归属与联系方式</span>
              <strong>合伙人绑定、手机号与邮箱</strong>
            </div>
          </div>
          <div class="form-grid is-two">
            <el-form-item label="所属合伙人">
              <el-select v-model="form.partnerId" clearable filterable placeholder="可不选">
                <el-option v-for="p in partnerOptions" :key="p.id" :label="p.partnerName" :value="p.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="form.phone" placeholder="可选" />
            </el-form-item>
            <el-form-item label="邮箱" class="is-full">
              <el-input v-model="form.email" placeholder="可选" />
            </el-form-item>
          </div>
        </section>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="分配角色" width="560px" class="admin-editor-dialog user-editor-dialog">
      <el-form :model="roleForm" label-position="top" class="user-form">
        <section class="form-section">
          <div class="form-section-head">
            <div>
              <span>角色权限</span>
              <strong>调整账号当前角色</strong>
            </div>
          </div>
          <div class="form-grid is-one">
            <el-form-item label="角色" required>
              <el-select v-model="roleForm.roleKey">
                <el-option v-for="r in roles" :key="r.roleKey" :label="dictStore.label('role', r.roleKey)" :value="r.roleKey" />
              </el-select>
            </el-form-item>
          </div>
        </section>
      </el-form>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRole">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdVisible" title="重置密码" width="560px" class="admin-editor-dialog user-editor-dialog">
      <el-form :model="pwdForm" label-position="top" class="user-form">
        <section class="form-section">
          <div class="form-section-head">
            <div>
              <span>安全操作</span>
              <strong>为当前账号设置新的登录密码</strong>
            </div>
          </div>
          <div class="form-grid is-one">
            <el-form-item label="新密码" required>
              <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" />
            </el-form-item>
          </div>
        </section>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPassword">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import { useDictStore } from '@/stores/dict'
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
import { getPartnerList, type PartnerItem } from '@/api/partner'

const dictStore = useDictStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<AdminUserItem[]>([])
const roles = ref<RoleOption[]>([])
const partnerOptions = ref<PartnerItem[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive<{ keyword: string; roleKey: string; isActive: boolean | undefined }>({
  keyword: '',
  roleKey: '',
  isActive: undefined,
})
const activeCount = computed(() => rows.value.filter((item) => item.isActive).length)
const inactiveCount = computed(() => rows.value.filter((item) => !item.isActive).length)

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

function userInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0].toUpperCase() : 'U'
}

async function loadRoles() {
  const { data } = await getRoleOptions()
  roles.value = data.data || []
}

async function loadPartners() {
  try {
    const { data } = await getPartnerList({ current: 1, size: 500 })
    partnerOptions.value = data.data.records || []
  } catch {
    partnerOptions.value = []
  }
}

function partnerName(partnerId?: number | null) {
  if (!partnerId) return '-'
  return partnerOptions.value.find((x) => x.id === partnerId)?.partnerName || '-'
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
  await dictStore.ensureLoaded()
  await loadRoles()
  await loadPartners()
  await load()
})
</script>

<style scoped>
.user-header {
  align-items: center;
}

.user-toolbar-card :deep(.el-card__body) {
  padding: 12px;
}

.user-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-keyword {
  width: 260px;
}

.filter-role {
  width: 180px;
}

.filter-status {
  width: 128px;
}

.user-metric-grid {
  margin-bottom: 0;
}

.user-table-card :deep(.el-card__body) {
  padding: 0;
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

.chips,
.role-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.chip,
.role-tag {
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

.role-tag {
  background: #eff6ff;
  color: #1d4ed8;
}

.user-avatar.is-success {
  background: linear-gradient(135deg, #059669, #14b8a6);
}

.user-avatar.is-muted {
  background: linear-gradient(135deg, #64748b, #94a3b8);
}

.user-editor-dialog :deep(.el-dialog__body) {
  background: #f8fafc;
}

.user-form {
  display: grid;
  gap: 14px;
}

.form-section {
  overflow: hidden;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.04);
}

.form-section-head {
  padding: 13px 16px 11px;
  border-bottom: 1px solid #e7edf5;
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 62%, #f0fdf4 100%);
}

.form-section-head span {
  display: block;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.form-section-head strong {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.form-grid {
  display: grid;
  gap: 13px 14px;
  padding: 16px;
}

.form-grid.is-two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid.is-one {
  grid-template-columns: 1fr;
}

.form-grid .is-full {
  grid-column: 1 / -1;
}

.user-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.user-form :deep(.el-form-item__label) {
  padding-bottom: 7px;
  color: #475569;
  font-size: 12px;
  font-weight: 800;
  line-height: 1.2;
}

.user-form :deep(.el-select) {
  width: 100%;
}

@media (max-width: 768px) {
  .user-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-keyword,
  .filter-role,
  .filter-status,
  .user-toolbar .el-button {
    width: 100%;
  }

  .form-grid.is-two {
    grid-template-columns: 1fr;
  }
}
</style>
