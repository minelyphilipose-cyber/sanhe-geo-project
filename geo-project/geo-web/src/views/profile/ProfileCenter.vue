<template>
  <div class="profile-page">
    <section class="profile-page__heading">
      <div>
        <h1>个人中心</h1>
        <p>管理个人资料、本机助手绑定与登录安全。</p>
      </div>
    </section>

    <div class="profile-stack">
      <el-card shadow="never" class="profile-card profile-card--primary">
        <template #header>
          <div class="profile-card__header">
            <div>
              <div class="profile-card__title">个人信息</div>
              <div class="profile-card__desc">维护当前登录账号的姓名、联系方式与头像。</div>
            </div>
          </div>
        </template>

        <div class="profile-split">
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
                <el-button size="small" plain :loading="uploadingAvatar">上传头像</el-button>
              </el-upload>
            </div>
          </div>

          <el-form
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            label-position="top"
            class="profile-form"
          >
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
        </div>
      </el-card>

      <div class="profile-grid">
        <el-card shadow="never" class="profile-card password-card">
          <template #header>
            <div class="profile-card__header">
              <div>
                <div class="profile-card__title">登录安全</div>
                <div class="profile-card__desc">定期更换登录密码，保障后台账号安全。</div>
              </div>
            </div>
          </template>

          <el-alert type="warning" :closable="false" show-icon title="密码修改成功后需重新登录" class="profile-alert" />
          <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top" class="password-form">
            <el-form-item label="旧密码" prop="oldPassword" class="is-wide">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="当前登录密码" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="新的登录密码" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
            </el-form-item>
            <el-form-item class="is-wide">
              <el-button type="primary" :loading="savingPassword" @click="submitPassword">确认修改</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="profile-card local-agent-card">
          <template #header>
            <div class="profile-card__header">
              <div>
                <div class="profile-card__title">本地助手</div>
                <div class="profile-card__desc">当前账号只需绑定一次本机助手，后续可用于打开有权限品牌的 AdsPower 环境。</div>
              </div>
              <el-button link type="primary" :loading="localAgentSessionsLoading" @click="refreshLocalAgentSessions">
                刷新
              </el-button>
            </div>
          </template>

          <div class="local-agent-summary" :class="{ 'is-bound': primaryLocalAgentSession }">
            <div class="local-agent-summary__icon">
              <el-icon><Monitor /></el-icon>
            </div>
            <div class="local-agent-summary__content">
              <div class="local-agent-summary__title">
                {{ primaryLocalAgentSession ? '本机助手已绑定' : '本机助手未绑定' }}
              </div>
              <div class="local-agent-summary__desc">
                <template v-if="primaryLocalAgentSession">
                  {{ primaryLocalAgentSession.helperName || '本地助手' }}，最近在线：
                  {{ primaryLocalAgentSession.lastSeenAt ? formatDateTime(primaryLocalAgentSession.lastSeenAt) : '待确认' }}
                </template>
                <template v-else>
                  启动本地助手，在助手页面生成配对码后填入下方完成绑定。
                </template>
              </div>
            </div>
            <el-tag :type="primaryLocalAgentSession ? 'success' : 'warning'" round>
              {{ primaryLocalAgentSession ? '可用' : '待绑定' }}
            </el-tag>
          </div>

          <div class="local-agent-settings-panel">
            <div class="local-agent-settings-panel__header">
              <div>
                <div class="local-agent-settings-panel__title">AdsPower 连接</div>
                <div class="local-agent-settings-panel__desc">
                  当前电脑的本地助手使用这组配置启动 AdsPower 环境。
                </div>
              </div>
              <el-tag :type="adspowerSettings.apiKeyConfigured ? 'success' : 'warning'" round>
                {{ adspowerSettings.apiKeyConfigured ? '已配置' : '未配置' }}
              </el-tag>
            </div>
            <el-form label-position="top" class="local-agent-settings-form">
              <el-form-item label="AdsPower API 地址">
                <el-input
                  v-model="adspowerSettingsForm.apiBase"
                  placeholder="http://localhost:50325"
                  :disabled="adspowerSettingsLoading || adspowerSettingsSaving"
                />
              </el-form-item>
              <el-form-item label="AdsPower API Key">
                <el-input
                  v-model="adspowerSettingsForm.apiKey"
                  type="password"
                  show-password
                  clearable
                  :placeholder="adspowerSettings.apiKeyConfigured ? `当前：${adspowerSettings.apiKeyPreview || '已配置'}` : '输入 AdsPower API Key'"
                  :disabled="adspowerSettingsLoading || adspowerSettingsSaving"
                />
              </el-form-item>
              <div class="local-agent-settings-actions">
                <span>
                  {{ adspowerSettings.apiKeyConfigured ? '留空保存时会保留当前 API Key。' : '未配置 API Key 时无法启动 AdsPower 环境。' }}
                </span>
                <el-button
                  type="primary"
                  plain
                  :loading="adspowerSettingsSaving"
                  @click="saveAdspowerSettingsFromProfile"
                >
                  保存连接
                </el-button>
              </div>
            </el-form>
          </div>

          <div class="local-agent-pairing-panel">
            <div class="local-agent-pairing-panel__label">一次性配对码</div>
            <div class="local-agent-pairing-row">
              <el-input
                v-model="localAgentPairingCode"
                clearable
                maxlength="32"
                placeholder="输入本地助手页面显示的配对码"
                @keyup.enter="approveLocalAgentPairingFromProfile"
              />
              <el-button type="primary" :loading="localAgentPairingSubmitting" @click="approveLocalAgentPairingFromProfile">
                绑定本机
              </el-button>
            </div>
            <div class="local-agent-pairing-panel__hint">
              重新绑定会自动替换当前账号原有的活跃助手会话。
            </div>
          </div>

          <div class="local-agent-session-title">
            <span>绑定记录</span>
            <span>{{ localAgentSessions.length }} 个</span>
          </div>

          <div v-if="localAgentSessionsLoading" class="local-agent-session-loading">
            <el-skeleton :rows="3" animated />
          </div>
          <div v-else-if="!localAgentSessions.length" class="local-agent-empty">
            <div class="local-agent-empty__icon">
              <el-icon><Monitor /></el-icon>
            </div>
            <div>
              <div class="local-agent-empty__title">暂无绑定记录</div>
              <div class="local-agent-empty__desc">完成本机绑定后，会在这里显示当前会话。</div>
            </div>
          </div>
          <div v-else class="local-agent-session-list">
            <div v-for="session in localAgentSessions" :key="session.id" class="local-agent-session-item">
              <div class="local-agent-session-main">
                <strong>{{ session.helperName || '本地助手' }}</strong>
                <span>绑定时间：{{ session.boundAt ? formatDateTime(session.boundAt) : '-' }}</span>
                <span>最近在线：{{ session.lastSeenAt ? formatDateTime(session.lastSeenAt) : '-' }}</span>
                <span>有效期至：{{ session.expiresAt ? formatDateTime(session.expiresAt) : '-' }}</span>
              </div>
              <el-tag size="small" :type="session.status === 'active' ? 'success' : 'info'" round>
                {{ session.status === 'active' ? '已绑定' : session.status }}
              </el-tag>
              <el-button
                size="small"
                type="danger"
                plain
                :loading="revokingLocalAgentSessionId === session.id"
                @click="revokeLocalAgentSessionFromProfile(session)"
              >
                吊销
              </el-button>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Monitor } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadRawFile } from 'element-plus'
import { changeMyPasswordApi, updateMyProfileApi, uploadMyAvatarApi } from '@/api/auth'
import {
  approveLocalAgentPairing,
  listLocalAgentSessions,
  revokeLocalAgentSession,
  type LocalAgentSession,
} from '@/api/localAgent'
import {
  getLocalHelperAdspowerSettings,
  updateLocalHelperAdspowerSettings,
  type LocalHelperAdspowerSettings,
} from '@/api/localHelper'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'
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
const localAgentPairingCode = ref('')
const localAgentPairingSubmitting = ref(false)
const localAgentSessions = ref<LocalAgentSession[]>([])
const localAgentSessionsLoading = ref(false)
const revokingLocalAgentSessionId = ref<number | null>(null)
const adspowerSettingsLoading = ref(false)
const adspowerSettingsSaving = ref(false)
const localHelperBase = 'http://127.0.0.1:17891'
const adspowerSettings = reactive<LocalHelperAdspowerSettings>({
  apiBase: 'http://localhost:50325',
  apiKeyConfigured: false,
  apiKeyPreview: '',
})
const adspowerSettingsForm = reactive({
  apiBase: 'http://localhost:50325',
  apiKey: '',
})

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
const primaryLocalAgentSession = computed(() =>
  localAgentSessions.value.find((session) => session.status === 'active') || null,
)

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

async function refreshLocalAgentSessions() {
  localAgentSessionsLoading.value = true
  try {
    const { data } = await listLocalAgentSessions()
    localAgentSessions.value = data.data || []
  } catch (error) {
    localAgentSessions.value = []
    ElMessage.error(error instanceof Error ? error.message : '本地助手会话加载失败')
  } finally {
    localAgentSessionsLoading.value = false
  }
}

async function refreshAdspowerSettings() {
  adspowerSettingsLoading.value = true
  try {
    const settings = await getLocalHelperAdspowerSettings(localHelperBase)
    adspowerSettings.apiBase = settings?.apiBase || 'http://localhost:50325'
    adspowerSettings.apiKeyConfigured = !!settings?.apiKeyConfigured
    adspowerSettings.apiKeyPreview = settings?.apiKeyPreview || ''
    adspowerSettingsForm.apiBase = adspowerSettings.apiBase || 'http://localhost:50325'
    adspowerSettingsForm.apiKey = ''
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AdsPower 连接配置读取失败')
  } finally {
    adspowerSettingsLoading.value = false
  }
}

async function saveAdspowerSettingsFromProfile() {
  const apiBase = adspowerSettingsForm.apiBase.trim() || 'http://localhost:50325'
  adspowerSettingsSaving.value = true
  try {
    const settings = await updateLocalHelperAdspowerSettings(localHelperBase, {
      apiBase,
      apiKey: adspowerSettingsForm.apiKey.trim() || null,
    })
    adspowerSettings.apiBase = settings?.apiBase || apiBase
    adspowerSettings.apiKeyConfigured = !!settings?.apiKeyConfigured
    adspowerSettings.apiKeyPreview = settings?.apiKeyPreview || ''
    adspowerSettingsForm.apiBase = adspowerSettings.apiBase || apiBase
    adspowerSettingsForm.apiKey = ''
    ElMessage.success('AdsPower 连接配置已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AdsPower 连接配置保存失败')
  } finally {
    adspowerSettingsSaving.value = false
  }
}

async function approveLocalAgentPairingFromProfile() {
  const pairingCode = localAgentPairingCode.value.trim()
  if (!pairingCode) {
    ElMessage.warning('请输入本地助手页面显示的一次性配对码')
    return
  }
  localAgentPairingSubmitting.value = true
  try {
    await approveLocalAgentPairing({ pairingCode })
    localAgentPairingCode.value = ''
    await refreshLocalAgentSessions()
    ElMessage.success('本地助手绑定成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '本地助手绑定失败')
  } finally {
    localAgentPairingSubmitting.value = false
  }
}

async function revokeLocalAgentSessionFromProfile(session: LocalAgentSession) {
  try {
    await ElMessageBox.confirm(
      `确认吊销「${session.helperName || '本地助手'}」？吊销后当前电脑需要重新配对才能继续打开 AdsPower 环境。`,
      '吊销本地助手',
      {
        confirmButtonText: '确认吊销',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }
  revokingLocalAgentSessionId.value = session.id
  try {
    await revokeLocalAgentSession(session.id)
    await refreshLocalAgentSessions()
    ElMessage.success('本地助手已吊销')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '本地助手吊销失败')
  } finally {
    revokingLocalAgentSessionId.value = null
  }
}

watch(() => userStore.avatarUrl, () => {
  avatarLoadFailed.value = false
})

onMounted(async () => {
  if (!userStore.userInfo?.email && !userStore.userInfo?.avatarUrl) {
    await userStore.syncProfile()
  }
  await refreshLocalAgentSessions()
  await refreshAdspowerSettings()
  fillProfileForm()
  resetPasswordForm()
})
</script>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 1200px;
  margin: 0 auto;
  padding: 12px 0 40px;
}

.profile-page__heading {
  padding: 0 2px;
  background: transparent;
  border: 0;
  box-shadow: none;
}

.profile-page__heading h1 {
  margin: 0;
  font-size: 30px;
  font-weight: 800;
  line-height: 1.25;
  color: #111827;
}

.profile-page__heading p {
  margin: 8px 0 0;
  font-size: 14px;
  color: #64748b;
}

.profile-stack {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  align-items: stretch;
}

.profile-card {
  overflow: hidden;
  border: 1px solid #e6ebf3;
  border-radius: 18px;
  box-shadow: 0 12px 34px -18px rgb(26 34 54 / 18%), 0 2px 6px -2px rgb(26 34 54 / 5%);
}

.profile-card :deep(.el-card__header) {
  padding: 24px 28px 0;
  background: #ffffff;
  border-bottom: 0;
}

.profile-card :deep(.el-card__body) {
  padding: 24px 28px 28px;
}

.profile-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.profile-card__title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
}

.profile-card__title::before {
  display: block;
  width: 4px;
  height: 17px;
  border-radius: 3px;
  background: linear-gradient(180deg, #2563eb, #6f8bff);
  content: '';
}

.profile-card__desc {
  margin-top: 8px;
  font-size: 13px;
  color: #64748b;
}

.profile-split {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 36px;
}

.profile-hero {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 0;
  margin-bottom: 0;
  padding: 0 36px 0 0;
  background: transparent;
  border-right: 1px solid #eef2f7;
  border-bottom: 0;
}

.profile-hero__avatar {
  width: 84px;
  height: 84px;
  border-radius: 20px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  font-weight: 800;
  overflow: hidden;
  flex-shrink: 0;
  box-shadow: 0 18px 44px -16px rgb(47 92 255 / 34%);
}

.profile-hero__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-hero__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  margin-top: 18px;
}

.profile-hero__name {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
  color: #0f172a;
}

.profile-hero__sub {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 999px;
  background: #eef2ff;
  font-size: 13px;
  font-weight: 500;
  color: #2563eb;
}

.profile-hero__sub::before {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2563eb;
  content: '';
}

.profile-form {
  display: grid;
  column-gap: 22px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 0;
}

.profile-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.profile-form :deep(.el-form-item:nth-child(3)),
.profile-form :deep(.el-form-item:last-child) {
  grid-column: 1 / -1;
}

.profile-form :deep(.el-form-item__label),
.password-card :deep(.el-form-item__label) {
  margin-bottom: 7px;
  color: #414b63;
  font-size: 13px;
  font-weight: 500;
}

.profile-form :deep(.el-input__wrapper),
.password-card :deep(.el-input__wrapper),
.profile-card :deep(.el-textarea__inner) {
  min-height: 44px;
  border-radius: 11px;
  background: #f7f9fc;
  box-shadow: 0 0 0 1px #e9edf5 inset;
}

.profile-form :deep(.el-input__wrapper.is-focus),
.password-card :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow: 0 0 0 1px #2f5cff inset, 0 0 0 4px rgb(47 92 255 / 12%);
}

.profile-form :deep(.is-disabled .el-input__wrapper) {
  background: #f1f4f9;
  box-shadow: 0 0 0 1px #e5eaf2 inset;
}

.profile-alert {
  margin-bottom: 18px;
}

.local-agent-card,
.password-card {
  min-height: 0;
  background: #ffffff;
}

.password-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 18px;
}

.password-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.password-form :deep(.el-form-item.is-wide) {
  grid-column: 1 / -1;
}

.password-form :deep(.el-button) {
  min-width: 104px;
}

.local-agent-summary {
  display: grid;
  align-items: center;
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid #fde6c8;
  border-radius: 12px;
  background: #fffaf3;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
}

.local-agent-summary.is-bound {
  border-color: #c8f3da;
  background: #f6fef9;
}

.local-agent-summary__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: #fff;
  color: #2563eb;
  font-size: 20px;
}

.local-agent-summary__content {
  min-width: 0;
}

.local-agent-summary__title {
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
}

.local-agent-summary__desc {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.local-agent-pairing-panel {
  margin-bottom: 18px;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fbfcff;
}

.local-agent-settings-panel {
  margin-bottom: 18px;
  padding: 16px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.local-agent-settings-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.local-agent-settings-panel__title {
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.local-agent-settings-panel__desc {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.local-agent-settings-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 12px;
}

.local-agent-settings-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.local-agent-settings-form :deep(.el-form-item__label) {
  margin-bottom: 7px;
  color: #414b63;
  font-size: 13px;
  font-weight: 500;
}

.local-agent-settings-form :deep(.el-input__wrapper) {
  min-height: 42px;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
}

.local-agent-settings-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  grid-column: 1 / -1;
  gap: 12px;
  color: #94a3b8;
  font-size: 12px;
}

.local-agent-pairing-panel__label {
  margin-bottom: 10px;
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.local-agent-pairing-panel__hint {
  margin-top: 8px;
  color: #94a3b8;
  font-size: 12px;
}

.local-agent-pairing-row {
  display: grid;
  width: 100%;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.local-agent-pairing-row .el-button {
  min-width: 96px;
}

.local-agent-session-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.local-agent-session-title span:last-child {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
}

.local-agent-session-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.local-agent-session-loading {
  padding: 10px 0 2px;
}

.local-agent-empty {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
}

.local-agent-empty__icon {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  color: #2563eb;
  background: #eff6ff;
  border-radius: 10px;
}

.local-agent-empty__title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.local-agent-empty__desc {
  margin-top: 3px;
  font-size: 12px;
  color: #64748b;
}

.local-agent-session-item {
  display: grid;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 10px;
}

.local-agent-session-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.local-agent-session-main strong {
  color: #0f172a;
  font-size: 14px;
}

.local-agent-session-main span {
  color: #64748b;
  font-size: 12px;
}

@media (max-width: 768px) {
  .profile-page {
    padding-bottom: 20px;
  }

  .profile-page__heading {
    padding: 0 0 8px;
  }

  .profile-card__header {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .profile-grid,
  .profile-split {
    grid-template-columns: 1fr;
  }

  .profile-hero {
    align-items: center;
    flex-direction: row;
    gap: 18px;
    padding: 0 0 22px;
    border-right: 0;
    border-bottom: 1px solid #eef2f7;
  }

  .profile-form {
    grid-template-columns: 1fr;
  }

  .password-form {
    grid-template-columns: 1fr;
  }

  .profile-form :deep(.el-form-item) {
    grid-column: 1 / -1;
  }

  .local-agent-summary,
  .local-agent-settings-form,
  .local-agent-pairing-row,
  .local-agent-session-item {
    grid-template-columns: 1fr;
  }

  .local-agent-settings-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
