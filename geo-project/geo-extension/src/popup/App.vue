<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { EXTENSION_VERSION } from '@/shared/env'
import { ExtensionApiError, extensionApi } from '@/shared/api'
import { friendlyErrorMessage } from '@/shared/errorMessages'
import { sessionStorage } from '@/shared/storage'
import type { ExtensionStatus, StoredSession } from '@/types/extension'
import { bindExtension, normalizeBindCode, unbindExtension, validateBindInput } from './bindFlow'
import { createTaskListState, formatCountdown, isExpired, mergeTasks, toggleTaskExpanded } from './taskListStore'

const status = ref<ExtensionStatus>('unbound')
const message = ref('未绑定')
const bindCode = ref('')
const brandId = ref('')
const loading = ref(false)
const tasksLoading = ref(false)
const session = ref<StoredSession | null>(null)
const taskState = reactive(createTaskListState())
const now = ref(new Date())
let refreshTimer: ReturnType<typeof setInterval> | undefined

const visibleTasks = computed(() => taskState.tasks.filter(task => !isExpired(task, now.value)))

onMounted(async () => {
  session.value = await sessionStorage.get()
  status.value = session.value ? 'bound' : 'unbound'
  message.value = session.value ? `已绑定，版本 ${session.value.extensionVersion}` : '请输入后台生成的绑定码完成绑定'
  try {
    await extensionApi.versionCheck(EXTENSION_VERSION)
  } catch {
    message.value = '版本检查失败，请确认服务端可用'
  }
  if (session.value) {
    await refreshTasks()
    startTaskRefresh()
  }
})

onBeforeUnmount(() => {
  stopTaskRefresh()
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
    await refreshTasks()
    startTaskRefresh()
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
    taskState.tasks = []
    taskState.expandedTaskId = null
    stopTaskRefresh()
  } catch (error) {
    message.value = friendlyErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function refreshTasks() {
  if (!session.value) return
  tasksLoading.value = true
  now.value = new Date()
  try {
    const tasks = await extensionApi.tasks(session.value.token)
    mergeTasks(taskState, tasks, now.value)
  } catch (error) {
    message.value = `${friendlyErrorMessage(error)} 请重新绑定后再试。`
    if (error instanceof ExtensionApiError && error.status === 401) {
      status.value = 'unbound'
      session.value = null
      taskState.tasks = []
      taskState.expandedTaskId = null
      stopTaskRefresh()
    }
  } finally {
    tasksLoading.value = false
  }
}

function startTaskRefresh() {
  stopTaskRefresh()
  refreshTimer = setInterval(() => {
    void refreshTasks()
  }, 30_000)
}

function stopTaskRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = undefined
  }
}

function toggleTask(taskId: number) {
  toggleTaskExpanded(taskState, taskId)
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
      <section class="tasks" aria-label="任务列表">
        <div class="tasks-header">
          <strong>待处理任务</strong>
          <button class="secondary" :disabled="tasksLoading" type="button" @click="refreshTasks">
            {{ tasksLoading ? '刷新中...' : '刷新' }}
          </button>
        </div>
        <p v-if="!tasksLoading && visibleTasks.length === 0" class="empty">暂无可处理的半自动发布任务</p>
        <ul v-else class="task-list">
          <li v-for="task in visibleTasks" :key="task.taskId" class="task-item">
            <button class="task-button" type="button" @click="toggleTask(task.taskId)">
              <span class="task-title">{{ task.title || '未命名任务' }}</span>
              <span class="task-meta">
                <span>{{ task.platform }}</span>
                <span>{{ task.status }}</span>
                <span>{{ formatCountdown(task.expiresAt, now) }}</span>
              </span>
            </button>
            <div v-if="taskState.expandedTaskId === task.taskId" class="task-detail">
              <span>任务 {{ task.taskId }}</span>
              <span>{{ task.publishUrl || '等待填写发布结果' }}</span>
            </div>
          </li>
        </ul>
      </section>
      <button class="danger" :disabled="loading" type="button" @click="unbind">
        解绑
      </button>
    </section>
  </main>
</template>
