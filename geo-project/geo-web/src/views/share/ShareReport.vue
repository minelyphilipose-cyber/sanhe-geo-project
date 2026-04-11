<template>
  <div class="share-page">
    <div class="share-page__bg">
      <div class="share-page__grid"></div>
    </div>

    <!-- 加载中 -->
    <div v-if="state === 'loading'" class="share-page__center">
      <el-icon :size="32" class="is-loading" color="#2563EB"><Loading /></el-icon>
      <p class="mt-4 text-gray-500">加载报表中…</p>
    </div>

    <!-- 需要密码 -->
    <div v-else-if="state === 'need_password'" class="share-page__center">
      <div class="glass-card p-8 w-[360px] text-center">
        <el-icon :size="40" color="#2563EB"><Lock /></el-icon>
        <h2 class="text-lg font-bold text-gray-800 mt-4 mb-2">访问需要密码</h2>
        <p class="text-sm text-gray-500 mb-4">此报表已设置访问密码</p>
        <el-input
          v-model="password"
          type="password"
          placeholder="请输入访问密码"
          size="large"
          show-password
          @keyup.enter="verifyPassword"
        />
        <el-button
          type="primary"
          class="w-full mt-4"
          size="large"
          :loading="verifying"
          @click="verifyPassword"
        >
          确认
        </el-button>
      </div>
    </div>

    <!-- 已过期 -->
    <div v-else-if="state === 'expired'" class="share-page__center">
      <div class="glass-card p-8 w-[360px] text-center">
        <el-icon :size="40" color="#EF4444"><CircleClose /></el-icon>
        <h2 class="text-lg font-bold text-gray-800 mt-4 mb-2">链接已过期</h2>
        <p class="text-sm text-gray-500">此报表分享链接已过有效期，请联系服务方获取新链接</p>
      </div>
    </div>

    <!-- 报表内容 -->
    <div v-else-if="state === 'loaded'" class="share-page__report">
      <!-- TODO: 根据 reportData.reportType 渲染不同的报表模板组件 -->
      <div class="glass-card p-8 max-w-[1000px] mx-auto">
        <h1 class="text-xl font-bold mb-4">报表预览</h1>
        <p class="text-gray-500">报表模板将在 Phase 3 实现</p>
        <pre class="mt-4 p-4 bg-gray-50 rounded-lg text-xs overflow-auto max-h-[400px]">{{ JSON.stringify(reportData, null, 2) }}</pre>
      </div>
    </div>

    <!-- 不存在 -->
    <div v-else-if="state === 'not_found'" class="share-page__center">
      <div class="glass-card p-8 w-[360px] text-center">
        <el-icon :size="40" color="#94A3B8"><Warning /></el-icon>
        <h2 class="text-lg font-bold text-gray-800 mt-4 mb-2">报表不存在</h2>
        <p class="text-sm text-gray-500">请确认链接是否正确</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getShareReport, verifySharePassword } from '@/api/report'
import { Loading, Lock, CircleClose, Warning } from '@element-plus/icons-vue'

const route = useRoute()
const token = route.params.token as string

type PageState = 'loading' | 'need_password' | 'expired' | 'loaded' | 'not_found'
const state = ref<PageState>('loading')
const reportData = ref<any>(null)
const password = ref('')
const verifying = ref(false)

onMounted(async () => {
  try {
    const { data } = await getShareReport(token)
    const res = data.data

    if (res.status === 'need_password') {
      state.value = 'need_password'
    } else if (res.status === 'expired') {
      state.value = 'expired'
    } else {
      reportData.value = res
      state.value = 'loaded'
    }
  } catch {
    state.value = 'not_found'
  }
})

async function verifyPassword() {
  verifying.value = true
  try {
    const { data } = await verifySharePassword(token, password.value)
    reportData.value = data.data
    state.value = 'loaded'
  } catch {
    // 错误信息已由拦截器处理
  } finally {
    verifying.value = false
  }
}
</script>

<style scoped>
.share-page {
  min-height: 100vh;
  background: #F8FAFC;
  position: relative;
}

.share-page__bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
}

.share-page__grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(#CBD5E1 1px, transparent 1px);
  background-size: 32px 32px;
  opacity: 0.3;
  mask-image: linear-gradient(to bottom, rgba(0,0,0,1) 0%, rgba(0,0,0,0) 60%);
}

.share-page__center {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  z-index: 10;
}

.share-page__report {
  padding: 40px 24px;
  position: relative;
  z-index: 10;
}
</style>
