<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { EXTENSION_VERSION } from '@/shared/env'
import { extensionApi } from '@/shared/api'
import { friendlyErrorMessage } from '@/shared/errorMessages'
import { sessionStorage } from '@/shared/storage'
import type { ExtensionStatus, StoredSession } from '@/types/extension'
import { bindExtension, normalizeBindCode, unbindExtension, validateBindInput } from './bindFlow'

const status = ref<ExtensionStatus>('unbound')
const message = ref('未绑定')
const bindCode = ref('')
const brandId = ref('')
const loading = ref(false)
const session = ref<StoredSession | null>(null)

onMounted(async () => {
  session.value = await sessionStorage.get()
  status.value = session.value ? 'bound' : 'unbound'
  message.value = session.value ? `已绑定，版本 ${session.value.extensionVersion}` : '请输入后台生成的绑定码完成绑定'
  try {
    await extensionApi.versionCheck(EXTENSION_VERSION)
  } catch {
    message.value = '版本检查失败，请确认服务端可用'
  }
})

function onBindCodeInput(event: Event) {
  bindCode.value = normalizeBindCode((event.target as HTMLInputElement).value)
}

async function bind() {
  try {
    const validated = validateBindInput({ bindCode: bindCode.value, brandId: brandId.value })
    if (!window.confirm(`确认将扩展绑定到 brandId ${validated.brandId}？`)) return

    loading.value = true
    session.value = await bindExtension({ bindCode: bindCode.value, brandId: brandId.value })
    status.value = 'bound'
    message.value = `绑定成功，sessionId ${session.value.sessionId}`
  } catch (error) {
    status.value = 'unbound'
    message.value = friendlyErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function unbind() {
  if (!session.value) return
  if (!window.confirm('确认解绑当前扩展？解绑后需要重新输入绑定码。')) return

  loading.value = true
  try {
    await unbindExtension(session.value)
    session.value = null
    status.value = 'unbound'
    message.value = '已解绑，请重新绑定后使用'
  } catch (error) {
    message.value = friendlyErrorMessage(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="popup">
    <header>
      <strong>GEO 半自动发布</strong>
      <span>{{ EXTENSION_VERSION }}</span>
    </header>
    <section :class="['status', status]">
      {{ message }}
    </section>
    <section v-if="status === 'unbound'" class="form">
      <label>
        <span>绑定码</span>
        <input
          :value="bindCode"
          maxlength="9"
          autocomplete="off"
          placeholder="ABCD-EFGH"
          @input="onBindCodeInput"
        >
      </label>
      <label>
        <span>brandId</span>
        <input v-model.trim="brandId" inputmode="numeric" pattern="[0-9]*" placeholder="例如 1001">
      </label>
      <p class="confirm">提交前请确认绑定到 brandId：{{ brandId || '未填写' }}</p>
      <button :disabled="loading" type="button" @click="bind">
        {{ loading ? '绑定中...' : '绑定' }}
      </button>
    </section>
    <section v-else class="form">
      <dl>
        <div>
          <dt>Session</dt>
          <dd>{{ session?.sessionId }}</dd>
        </div>
        <div>
          <dt>过期时间</dt>
          <dd>{{ session?.expiresAt }}</dd>
        </div>
      </dl>
      <button class="danger" :disabled="loading" type="button" @click="unbind">
        解绑
      </button>
    </section>
  </main>
</template>
