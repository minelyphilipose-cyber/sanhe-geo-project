<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { EXTENSION_VERSION } from '@/shared/env'
import { extensionApi } from '@/shared/api'
import { sessionStorage } from '@/shared/storage'
import type { ExtensionStatus } from '@/types/extension'

const status = ref<ExtensionStatus>('unbound')
const message = ref('未绑定')

onMounted(async () => {
  const session = await sessionStorage.get()
  status.value = session ? 'bound' : 'unbound'
  message.value = session ? `已绑定，版本 ${session.extensionVersion}` : '请输入后台生成的绑定码完成绑定'
  try {
    await extensionApi.versionCheck(EXTENSION_VERSION)
  } catch {
    message.value = '版本检查失败，请确认服务端可用'
  }
})
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
  </main>
</template>
