<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { EXTENSION_VERSION } from '@/shared/env'
import { ExtensionApiError, extensionApi } from '@/shared/api'
import { friendlyErrorMessage } from '@/shared/errorMessages'
import { sessionStorage } from '@/shared/storage'
import type {
  CookieCaptureResponse,
  ExtensionSelfMediaAccount,
  ExtensionStatus,
  ExtensionTaskListItem,
  ExtensionTaskStateResponse,
  TaskLifecycleEvent,
  StoredSession,
} from '@/types/extension'
import { bindExtension, normalizeBindCode, unbindExtension, validateBindInput } from './bindFlow'
import { createTaskListState, formatCountdown, isExpired, mergeTasks, toggleTaskExpanded } from './taskListStore'

const status = ref<ExtensionStatus>('unbound')
const statusMessage = ref('未绑定')
const errorMessage = ref('')
const bindCode = ref('')
const loading = ref(false)
const tasksLoading = ref(false)
const accountsLoading = ref(false)
const captureLoading = ref(false)
const fillLoadingTaskId = ref<number | null>(null)
const session = ref<StoredSession | null>(null)
const taskState = reactive(createTaskListState())
const accounts = ref<ExtensionSelfMediaAccount[]>([])
const selectedAccountId = ref<number | null>(null)
const now = ref(new Date())
let refreshTimer: ReturnType<typeof setInterval> | undefined

const visibleTasks = computed(() => taskState.tasks.filter(task => !isExpired(task, now.value)))
const displayMessage = computed(() => errorMessage.value || statusMessage.value)

onMounted(async () => {
  session.value = await sessionStorage.get()
  status.value = session.value ? 'bound' : 'unbound'
  statusMessage.value = session.value ? `已绑定，版本 ${session.value.extensionVersion}` : '请输入绑定码完成绑定'
  chrome.runtime.onMessage.addListener(onRuntimeMessage)
  try {
    await extensionApi.versionCheck(EXTENSION_VERSION)
  } catch {
    errorMessage.value = '版本检查失败，请确认服务端可用'
  }
  if (session.value) {
    await refreshAccounts()
    await refreshTasks()
    startTaskRefresh()
  }
})

onBeforeUnmount(() => {
  stopTaskRefresh()
  chrome.runtime.onMessage.removeListener(onRuntimeMessage)
})

function onBindCodeInput(event: Event) {
  bindCode.value = normalizeBindCode((event.target as HTMLInputElement).value)
}

async function bind() {
  try {
    validateBindInput({ bindCode: bindCode.value })
    if (!window.confirm('确认使用该绑定码绑定 GEO 扩展？')) return

    loading.value = true
    session.value = await bindExtension({ bindCode: bindCode.value })
    status.value = 'bound'
    errorMessage.value = ''
    statusMessage.value = `绑定成功，sessionId ${session.value.sessionId}`
    bindCode.value = ''
    loading.value = false
    await refreshAccounts()
    await refreshTasks()
    startTaskRefresh()
  } catch (error) {
    status.value = 'unbound'
    errorMessage.value = friendlyErrorMessage(error)
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
    errorMessage.value = ''
    statusMessage.value = '已解绑，请重新绑定后使用'
    taskState.tasks = []
    accounts.value = []
    selectedAccountId.value = null
    taskState.expandedTaskId = null
    stopTaskRefresh()
  } catch (error) {
    errorMessage.value = friendlyErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function refreshAccounts() {
  if (!session.value) return
  accountsLoading.value = true
  try {
    accounts.value = await extensionApi.selfMediaAccounts(session.value.token)
    if (!selectedAccountId.value && accounts.value.length > 0) {
      selectedAccountId.value = accounts.value[0].accountId
    }
  } catch (error) {
    errorMessage.value = friendlyErrorMessage(error)
  } finally {
    accountsLoading.value = false
  }
}

async function captureCookies() {
  const account = accounts.value.find(item => item.accountId === selectedAccountId.value)
  if (!account) {
    errorMessage.value = '请选择要捕获凭证的账号'
    return
  }
  if (!window.confirm(`为账号 ${account.accountName || account.accountId} 捕获 ${account.platform} 凭证？`)) return

  captureLoading.value = true
  try {
    const result = await sendRuntimeMessage<CookieCaptureResponse>({
      type: 'GEO_CAPTURE_COOKIES',
      payload: account,
    })
    errorMessage.value = ''
    statusMessage.value = `凭证捕获成功，版本 ${result.version}`
  } catch (error) {
    errorMessage.value = friendlyErrorMessage(error)
  } finally {
    captureLoading.value = false
  }
}

async function startFill(task: ExtensionTaskListItem) {
  if (task.status !== 'token_issued') {
    errorMessage.value = task.status === 'filled' ? '该任务已完成填充' : '该任务正在填充中'
    return
  }
  fillLoadingTaskId.value = task.taskId
  try {
    await sendRuntimeMessage<ExtensionTaskStateResponse>({
      type: 'GEO_START_FILL_TASK',
      payload: task,
    })
    errorMessage.value = ''
    statusMessage.value = '已填充编辑器，请在平台页面人工确认并发布。'
    await refreshTasks()
  } catch (error) {
    errorMessage.value = friendlyErrorMessage(error)
  } finally {
    fillLoadingTaskId.value = null
  }
}

function sendRuntimeMessage<T>(payload: unknown): Promise<T> {
  return new Promise((resolve, reject) => {
    chrome.runtime.sendMessage(payload, (response?: { ok: boolean, result?: T, message?: string }) => {
      if (chrome.runtime.lastError) {
        reject(new Error(chrome.runtime.lastError.message))
        return
      }
      if (!response?.ok) {
        reject(new Error(response?.message || '请求失败，请稍后重试。'))
        return
      }
      resolve(response.result as T)
    })
  })
}

async function refreshTasks() {
  if (!session.value) return
  tasksLoading.value = true
  now.value = new Date()
  try {
    const tasks = await extensionApi.tasks(session.value.token)
    errorMessage.value = ''
    mergeTasks(taskState, tasks, now.value)
  } catch (error) {
    if (error instanceof ExtensionApiError && error.status === 401) {
      errorMessage.value = `${friendlyErrorMessage(error)} 请重新绑定后再试。`
      status.value = 'unbound'
      session.value = null
      taskState.tasks = []
      taskState.expandedTaskId = null
      accounts.value = []
      selectedAccountId.value = null
      stopTaskRefresh()
    } else {
      errorMessage.value = friendlyErrorMessage(error)
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

function onRuntimeMessage(message: { type: string, payload?: TaskLifecycleEvent }) {
  if (message.type !== 'GEO_TASK_LIFECYCLE_EVENT' || !message.payload) return false
  if (message.payload.kind === 'auth_required') {
    status.value = 'unbound'
    session.value = null
    taskState.tasks = []
    accounts.value = []
    selectedAccountId.value = null
    stopTaskRefresh()
  }
  if (message.payload.kind === 'published') {
    void refreshTasks()
  }
  errorMessage.value = message.payload.kind === 'published' ? '' : message.payload.message
  statusMessage.value = message.payload.message
  return false
}
</script>

<template>
  <main class="popup">
    <header>
      <strong>三合星链自媒体助手</strong>
      <span>{{ EXTENSION_VERSION }}</span>
    </header>
    <section :class="['status', status]">
      {{ displayMessage }}
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
      <section class="capture" aria-label="凭证捕获">
        <div class="tasks-header">
          <strong>捕获凭证</strong>
          <button class="secondary" :disabled="accountsLoading" type="button" @click="refreshAccounts">
            {{ accountsLoading ? '加载中...' : '刷新账号' }}
          </button>
        </div>
        <label>
          <span>账号</span>
          <select v-model.number="selectedAccountId">
            <option v-for="account in accounts" :key="account.accountId" :value="account.accountId">
              {{ account.platform }} / {{ account.accountName || account.accountId }} / brand {{ account.brandId }}
            </option>
          </select>
        </label>
        <p class="confirm">请先打开目标平台并完成登录，再手动确认捕获。</p>
        <button :disabled="captureLoading || accounts.length === 0" type="button" @click="captureCookies">
          {{ captureLoading ? '捕获中...' : '捕获凭证' }}
        </button>
      </section>
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
            <button class="task-button" :disabled="fillLoadingTaskId === task.taskId" type="button" @click="startFill(task)">
              <span class="task-title">{{ task.title || '未命名任务' }}</span>
              <span class="task-meta">
                <span>{{ task.platform }}</span>
                <span>{{ task.status }}</span>
                <span>{{ fillLoadingTaskId === task.taskId ? '填充中...' : formatCountdown(task.expiresAt, now) }}</span>
              </span>
            </button>
            <button class="secondary" type="button" @click="toggleTask(task.taskId)">详情</button>
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
