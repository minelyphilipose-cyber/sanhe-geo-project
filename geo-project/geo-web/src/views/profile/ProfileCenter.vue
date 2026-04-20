<template>
  <div class="profile-page">
    <el-row :gutter="20">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="profile-card">
          <template #header>
            <div class="profile-card__header">
              <div>
                <div class="profile-card__title">个人信息</div>
                <div class="profile-card__desc">维护当前登录账号的姓名、联系方式与头像。</div>
              </div>
            </div>
          </template>

          <div class="profile-hero">
            <div class="profile-hero__avatar">
              <img
                v-if="showAvatarImage"
                :key="userStore.avatarUrl"
                :src="userStore.avatarUrl"
                alt=""
                class="profile-hero__image"
                @load="avatarLoadFailed = false"
                @error="avatarLoadFailed = true"
              />
              <span v-else>{{ avatarLetter }}</span>
            </div>
            <div class="profile-hero__meta">
              <div class="profile-hero__name">{{ userStore.displayName || '未设置姓名' }}</div>
              <div class="profile-hero__sub">{{ roleLabel }}</div>
              <el-upload :show-file-list="false" :before-upload="handleAvatarUpload" accept="image/*">
                <el-button size="small" type="primary" plain :loading="uploadingAvatar">上传头像</el-button>
              </el-upload>
            </div>
          </div>

          <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="96px" class="profile-form">
            <el-form-item label="用户名">
              <el-input :model-value="userStore.userInfo?.username || '-'" disabled />
            </el-form-item>
            <el-form-item label="角色">
              <el-input :model-value="roleLabel" disabled />
            </el-form-item>
            <el-form-item label="所属主体">
              <el-input :model-value="partnerLabel" disabled />
            </el-form-item>
            <el-form-item label="姓名" prop="displayName">
              <el-input v-model="profileForm.displayName" maxlength="64" show-word-limit />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="profileForm.phone" maxlength="20" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="profileForm.email" maxlength="128" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingProfile" @click="submitProfile">保存资料</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="profile-card">
          <template #header>
            <div class="profile-card__header">
              <div>
                <div class="profile-card__title">修改密码</div>
              </div>
            </div>
          </template>

          <el-alert type="warning" :closable="false" show-icon title="密码修改成功后需重新登录" class="profile-alert" />
          <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="96px">
            <el-form-item label="旧密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingPassword" @click="submitPassword">确认修改</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules, type UploadRawFile } from 'element-plus'
import { changeMyPasswordApi, updateMyProfileApi, uploadMyAvatarApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import type { RoleType } from '@/types'

const ROLE_LABELS: Record<RoleType, string> = {
  super_admin: '超级管理员',
  manager: '管理者',
  delivery_manager: '交付负责人',
  operator: '运营',
  sales: '销售',
  partner: '合伙人主账号',
  partner_staff: '合伙人员工',
  partner_viewer: '合伙人只读',
}

const userStore = useUserStore()
const router = useRouter()

const profileFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const savingProfile = ref(false)
const savingPassword = ref(false)
const uploadingAvatar = ref(false)
const avatarLoadFailed = ref(false)

const profileForm = reactive({
  displayName: '',
  phone: '',
  email: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const roleLabel = computed(() => {
  const role = userStore.role
  return role ? ROLE_LABELS[role] ?? role : '-'
})

const partnerLabel = computed(() => {
  if (userStore.userInfo?.partnerId) {
    return `合伙人 #${userStore.userInfo.partnerId}`
  }
  return '内部账号'
})

const avatarLetter = computed(() => (userStore.displayName || 'U').charAt(0).toUpperCase())
const showAvatarImage = computed(() => !!userStore.avatarUrl && !avatarLoadFailed.value)

const profileRules: FormRules = {
  displayName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{
    validator: (_rule, value: string, callback) => {
      if (!value) {
        callback()
        return
      }
      const ok = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
      callback(ok ? undefined : new Error('请输入正确的邮箱'))
    },
    trigger: 'blur',
  }],
}

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [{
    validator: (_rule, value: string, callback) => {
      if (!value) {
        callback(new Error('请再次输入新密码'))
        return
      }
      callback(value === passwordForm.newPassword ? undefined : new Error('两次输入的新密码不一致'))
    },
    trigger: 'blur',
  }],
}

function fillProfileForm() {
  profileForm.displayName = userStore.userInfo?.displayName || ''
  profileForm.phone = userStore.userInfo?.phone || ''
  profileForm.email = userStore.userInfo?.email || ''
}

function resetPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate()
}

async function submitProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return

  savingProfile.value = true
  try {
    await updateMyProfileApi({
      displayName: profileForm.displayName.trim(),
      phone: profileForm.phone.trim() || undefined,
      email: profileForm.email.trim() || undefined,
    })
    await userStore.syncProfile()
    fillProfileForm()
    ElMessage.success('个人资料已更新')
  } finally {
    savingProfile.value = false
  }
}

async function handleAvatarUpload(file: UploadRawFile) {
  uploadingAvatar.value = true
  try {
    await uploadMyAvatarApi(file as File)
    await userStore.syncProfile()
    ElMessage.success('头像已更新')
  } finally {
    uploadingAvatar.value = false
  }
  return false
}

async function submitPassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  savingPassword.value = true
  try {
    await changeMyPasswordApi({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    ElMessage.success('密码修改成功，请重新登录')
    await userStore.logout()
    await router.replace('/login?reason=password_changed')
  } finally {
    savingPassword.value = false
  }
}

watch(() => userStore.avatarUrl, () => {
  avatarLoadFailed.value = false
})

onMounted(async () => {
  if (!userStore.userInfo?.email && !userStore.userInfo?.avatarUrl) {
    await userStore.syncProfile()
  }
  fillProfileForm()
  resetPasswordForm()
})
</script>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.profile-card {
  border-radius: 18px;
}

.profile-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.profile-card__title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.profile-card__desc {
  margin-top: 4px;
  font-size: 13px;
  color: #64748b;
}

.profile-hero {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  padding: 18px;
  border-radius: 16px;
  background: linear-gradient(135deg, #eff6ff, #f8fafc);
}

.profile-hero__avatar {
  width: 72px;
  height: 72px;
  border-radius: 20px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  overflow: hidden;
  flex-shrink: 0;
}

.profile-hero__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-hero__meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.profile-hero__name {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

.profile-hero__sub {
  font-size: 13px;
  color: #64748b;
}

.profile-form {
  margin-top: 4px;
}

.profile-alert {
  margin-bottom: 20px;
}


@media (max-width: 768px) {
  .profile-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
