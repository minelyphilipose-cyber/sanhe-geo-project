<template>
  <div class="login-page">
    <!-- 背景 -->
    <div class="login-bg">
      <div class="login-bg__grid"></div>
      <div class="login-bg__glow login-bg__glow--blue"></div>
      <div class="login-bg__glow login-bg__glow--purple"></div>
    </div>

    <!-- 登录卡片 -->
    <div class="login-card glass-card">
      <div class="login-card__header">
        <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg" class="login-card__logo">
          <rect width="40" height="40" rx="10" fill="#2563EB"/>
          <path d="M10 20L17.5 12.5L25 20L17.5 27.5Z" fill="white" opacity="0.9"/>
          <path d="M17.5 20L25 12.5L32.5 20L25 27.5Z" fill="white" opacity="0.6"/>
        </svg>
        <h1 class="login-card__title">幻境AI · GEO 交付系统</h1>
        <p class="login-card__subtitle">内部运营平台</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            class="login-card__btn"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import type { FormInstance, FormRules } from 'element-plus'
import { resolvePostLoginPath } from '@/utils/navigation'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(form)

    // 登录成功后根据角色跳转
    const redirect = route.query.redirect as string
    if (redirect) {
      router.push(redirect)
    } else {
      const target = resolvePostLoginPath({
        isPartner: userStore.isPartner,
        hasPermission: userStore.hasPermission,
        hasRole: userStore.hasRole,
      })
      router.push(target || '/login')
    }
  } catch {
    // error 已在 axios 拦截器中处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #F8FAFC;
  position: relative;
  overflow: hidden;
}

/* 从旧代码提取的背景效果 */
.login-bg__grid {
  position: fixed;
  inset: 0;
  background-image: radial-gradient(#CBD5E1 1px, transparent 1px);
  background-size: 32px 32px;
  opacity: 0.4;
  mask-image: linear-gradient(to bottom, rgba(0,0,0,1) 0%, rgba(0,0,0,0) 80%);
  -webkit-mask-image: linear-gradient(to bottom, rgba(0,0,0,1) 0%, rgba(0,0,0,0) 80%);
  pointer-events: none;
}

.login-bg__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}

.login-bg__glow--blue {
  top: -15%;
  left: 20%;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(37,99,235,0.08) 0%, transparent 70%);
  animation: float 10s ease-in-out infinite alternate;
}

.login-bg__glow--purple {
  top: 20%;
  right: 10%;
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(139,92,246,0.06) 0%, transparent 70%);
  animation: float 12s ease-in-out infinite alternate-reverse;
}

@keyframes float {
  0% { transform: translate(0, 0); }
  100% { transform: translate(30px, 50px); }
}

/* 卡片 */
.login-card {
  width: 400px;
  padding: 40px 36px 32px;
  position: relative;
  z-index: 10;
}

.login-card__header {
  text-align: center;
  margin-bottom: 32px;
}

.login-card__logo {
  width: 48px;
  height: 48px;
  margin-bottom: 16px;
}

.login-card__title {
  font-size: 22px;
  font-weight: 800;
  color: #0F172A;
  margin: 0 0 4px;
  letter-spacing: -0.02em;
}

.login-card__subtitle {
  font-size: 14px;
  color: #64748B;
  margin: 0;
}

.login-card__btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 10px;
}
</style>
