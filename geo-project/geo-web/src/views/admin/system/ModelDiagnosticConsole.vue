<template>
  <div class="diagnostic-page">
    <header class="page-header">
      <div class="title-group">
        <p class="eyebrow"><span />AI 模型能力验证</p>
        <h1>大模型诊断台</h1>
        <p class="subtitle">上线前验证模型配置、基础生成与联网引用链路，快速定位不可用环节。</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Clock" @click="openHistory">历史记录</el-button>
        <el-button type="primary" :icon="Plus" @click="newSession">新建会话</el-button>
      </div>
    </header>

    <section class="control-panel">
      <div class="section-heading">
        <div class="section-title">
          <span class="step-index">01</span>
          <div>
            <h2>配置诊断任务</h2>
            <p>选择要验证的模型、能力和测试方式。</p>
          </div>
        </div>
        <div class="readiness-pill" :class="selectedPlatform?.selectable ? 'is-ready' : 'is-blocked'">
          <span class="status-dot" />
          {{ platformStatus }}
        </div>
      </div>
      <div class="control-grid">
        <label class="field">
          <span>模型渠道</span>
          <el-select v-model="channelCode" placeholder="选择渠道" @change="onChannelChanged">
            <el-option v-for="channel in channels" :key="channel" :label="channelLabel(channel)" :value="channel" />
          </el-select>
        </label>
        <label class="field">
          <span>验证能力</span>
          <el-segmented v-model="diagnosticMode" :options="modeOptions" @change="onModeChanged" />
        </label>
        <label class="field field--wide">
          <span>模型配置</span>
          <el-select v-model="platformSelectionKey" placeholder="选择具体模型配置" @change="onPlatformChanged">
            <el-option
              v-for="item in availablePlatforms"
              :key="platformOptionKey(item)"
              :label="platformOptionLabel(item)"
              :value="platformOptionKey(item)"
              :disabled="!item.selectable"
            >
              <div class="platform-option">
                <span>{{ item.platformName }} · {{ item.modelId || '未配置模型' }} · {{ modelTierLabel(item.modelTier) }}</span>
                <small v-if="!item.selectable">{{ item.unavailableReason }}</small>
              </div>
            </el-option>
          </el-select>
        </label>
        <label class="field">
          <span>验证方式</span>
          <el-select v-model="testMode" @change="onTestModeChanged">
            <el-option label="自由对话" value="FREE_CHAT" />
            <el-option label="标准能力探针" value="STANDARD_PROBE" />
            <el-option label="生产轮询模板" value="PRODUCTION_POLL_TEMPLATE" />
          </el-select>
          <small class="field-help">{{ testModeHelp }}</small>
        </label>
        <label v-if="testMode !== 'FREE_CHAT'" class="field">
          <span>验证用例</span>
          <el-select v-model="probeCode" placeholder="选择探针">
            <el-option v-for="probe in availableProbes" :key="probe.code" :label="probe.label" :value="probe.code" />
          </el-select>
        </label>
      </div>
      <label v-if="testMode === 'FREE_CHAT'" class="system-prompt-field">
        <span class="field-copy">
          <b>系统提示词 <em>可选</em></b>
          <small>仅作用于当前会话，用于验证角色、语气或回答边界。</small>
        </span>
        <el-input
          v-model="systemPrompt"
          maxlength="8000"
          show-word-limit
          clearable
          placeholder="例如：请使用简洁中文回答，并在不确定时明确说明"
        />
      </label>
      <div class="context-strip">
        <div><span>当前模型</span><strong>{{ selectedPlatform ? `${selectedPlatform.modelId || '未配置模型'}（${modelTierLabel(selectedPlatform.modelTier)}）` : '待选择' }}</strong></div>
        <div><span>调用协议</span><strong>{{ selectedPlatform?.integrationType || '待确认' }}</strong></div>
        <div><span>响应方式：</span><strong>同步</strong></div>
        <span class="session-code" :title="sessionId">会话 {{ sessionId.slice(0, 8) }}</span>
      </div>
    </section>

    <main class="workspace">
      <section class="conversation-card">
        <div class="conversation-header">
          <div class="section-title section-title--compact">
            <span class="step-index">02</span>
            <div>
              <h2>发起验证对话</h2>
              <p>只需提交本轮问题，历史上下文由服务端维护。</p>
            </div>
          </div>
          <div class="conversation-context">
            <el-tag effect="plain">{{ modeLabel(diagnosticMode) }}</el-tag>
            <span>{{ testModeLabel(testMode) }}</span>
          </div>
        </div>
        <div ref="messageListRef" class="message-list">
          <div v-if="messages.length === 0" class="empty-state">
            <div class="empty-icon"><el-icon><ChatDotRound /></el-icon></div>
            <h2>准备好后，发送第一条测试消息</h2>
            <p>系统会保留完整诊断证据，但不会把失败轮次加入后续模型上下文。</p>
            <div class="empty-steps">
              <span><b>1</b>确认配置可调用</span>
              <span><b>2</b>输入真实问题</span>
              <span><b>3</b>查看能力结论</span>
            </div>
          </div>
          <article v-for="message in messages" :key="message.key" class="message" :class="`message--${message.role}`">
            <div class="message-meta">
              <span>{{ message.role === 'user' ? '你' : message.platformName || '模型' }}</span>
              <el-tag v-if="message.run" size="small" :type="conclusionType(message.run.conclusion)">
                {{ runBadgeLabel(message.run) }}
              </el-tag>
            </div>
            <div
              v-if="message.role === 'assistant'"
              class="answer-shell"
              :class="{
                'has-toggle': message.run && isLongDiagnosticAnswer(message.content),
                'is-collapsed': message.run && isLongDiagnosticAnswer(message.content) && !isAnswerExpanded(message.run.runId),
              }"
            >
              <div
                class="message-body message-body--markdown"
                v-html="renderDiagnosticAnswer(message.content, message.run)"
              />
              <button
                v-if="message.run && isLongDiagnosticAnswer(message.content)"
                type="button"
                class="answer-toggle"
                :aria-expanded="isAnswerExpanded(message.run.runId)"
                @click="toggleAnswer(message.run.runId)"
              >
                {{ isAnswerExpanded(message.run.runId) ? '收起回答' : '展开完整回答' }}
              </button>
            </div>
            <div v-else class="message-body">{{ message.content }}</div>
            <template v-if="message.run">
              <div v-if="message.run.conclusionReason" class="conclusion-note">
                <span>判定说明</span>{{ conclusionReasonLabel(message.run.conclusionReason) }}
              </div>
              <div class="run-summary">
                <span><b>耗时</b> {{ formatDuration(message.run.durationMs) }}</span>
                <span><b>Token</b> {{ message.run.promptTokens ?? '—' }} / {{ message.run.completionTokens ?? '—' }} / {{ message.run.totalTokens ?? '—' }}</span>
                <span v-if="message.run.diagnosticMode === 'WEB_SEARCH'"><b>搜索调用</b> {{ diagnosticSearchCallLabel(message.run) }}</span>
                <span v-if="message.run.diagnosticMode === 'WEB_SEARCH'"><b>搜索状态</b> {{ searchStatusLabel(message.run.searchStatus) }}</span>
                <span v-if="message.run.diagnosticMode === 'WEB_SEARCH'"><b>有效证据</b> {{ message.run.validSourceCount || 0 }} 来源 / {{ message.run.validCitationCount || 0 }} 引用</span>
              </div>
              <div v-if="message.run.sources.length || message.run.citations.length" class="evidence-stack">
                <details v-if="message.run.sources.length" class="evidence-panel" :open="message.run.sources.length <= 2">
                  <summary>
                    <span class="evidence-title"><b>来源证据</b><small>核查模型实际参考的网页</small></span>
                    <span class="evidence-count">
                      {{ message.run.validSourceCount || 0 }} / {{ message.run.sourceCount || message.run.sources.length }} 有效 · {{ diagnosticUniqueSourceDomainCount(message.run.sources) }} 域名
                    </span>
                  </summary>
                  <div class="source-list">
                    <article
                      v-for="(source, index) in message.run.sources"
                      :key="`${message.run.runId}-${index}`"
                      class="source-card"
                    >
                      <span>{{ index + 1 }}</span>
                      <div>
                        <a
                          v-if="safeSourceUrl(source)"
                          class="source-title-link"
                          :href="safeSourceUrl(source) || undefined"
                          target="_blank"
                          rel="noopener noreferrer"
                          :title="`打开来源：${sourceTitle(source, index)}`"
                        >{{ sourceTitle(source, index) }}</a>
                        <strong v-else class="source-title-link source-title-link--disabled">
                          {{ sourceTitle(source, index) }}
                        </strong>
                        <small>{{ sourceDomain(source) || '无有效链接' }}</small>
                      </div>
                    </article>
                  </div>
                </details>
                <details v-if="message.run.citations.length" class="evidence-panel">
                  <summary>
                    <span class="evidence-title"><b>引用映射</b><small>核对回答位置与来源关系</small></span>
                    <span class="evidence-count">{{ message.run.validCitationCount || 0 }} / {{ message.run.citationCount || message.run.citations.length }} 确认</span>
                  </summary>
                  <div class="citation-list">
                    <div v-for="(citation, index) in message.run.citations" :key="`citation-${message.run.runId}-${index}`">
                      <span>{{ citationText(citation, index) }}</span>
                      <p>{{ citationMapping(citation, message.run) }}</p>
                      <el-tag size="small" :type="citationType(citation)">{{ citationConfidenceLabel(citation) }}</el-tag>
                    </div>
                  </div>
                </details>
              </div>
              <el-alert v-if="message.run.error" type="error" :closable="false" show-icon>
                <template #title>{{ message.run.error.code || message.run.error.category || '诊断失败' }}</template>
                {{ diagnosticErrorMessage(message.run.error.message) }}
              </el-alert>
              <el-button class="raw-link" type="primary" link :icon="Document" @click="showRaw(message.run)">查看诊断详情与脱敏报文</el-button>
            </template>
          </article>
          <div v-if="sending" class="message message--assistant message--loading">
            <div class="pulse" /><span>供应商正在处理同步请求…</span>
          </div>
        </div>

        <div class="composer">
          <div class="composer-heading">
            <label for="diagnostic-message">测试消息</label>
            <span>{{ requiresUserMessage ? '必填' : '由服务端探针生成' }}</span>
          </div>
          <el-input
            id="diagnostic-message"
            v-model="userMessage"
            type="textarea"
            :rows="3"
            maxlength="8000"
            show-word-limit
            resize="none"
            :placeholder="composerPlaceholder"
            :disabled="sending || !requiresUserMessage"
            @keydown.ctrl.enter.prevent="send"
          />
          <div class="composer-footer">
            <span><kbd>Ctrl</kbd> + <kbd>Enter</kbd> 发送 · 最长 8000 字</span>
            <el-button type="primary" :icon="Promotion" :loading="sending" :disabled="!canSend" @click="send">发送诊断</el-button>
          </div>
        </div>
      </section>

      <aside class="inspector-card">
        <div class="section-title section-title--compact">
          <span class="step-index">03</span>
          <div>
            <h2>诊断结论</h2>
            <p>查看本轮能力判定与资源消耗。</p>
          </div>
        </div>

        <template v-if="latestRun">
          <div class="result-hero" :class="`result-hero--${(latestRun.conclusion || 'unknown').toLowerCase()}`">
            <span>{{ statusLabel(latestRun.status) }}</span>
            <strong>{{ conclusionLabel(latestRun.conclusion) }}</strong>
            <p>{{ resultDescription(latestRun) }}</p>
          </div>
          <div class="rollout-card" :class="`rollout-card--${latestRecommendation.tone}`">
            <span>本轮上线建议 <em>仅供灰度决策</em></span>
            <strong>{{ latestRecommendation.label }}</strong>
            <p>{{ latestRecommendation.description }}</p>
            <ul v-if="latestRecommendation.notices.length">
              <li v-for="notice in latestRecommendation.notices" :key="notice">{{ notice }}</li>
            </ul>
          </div>
          <h3 class="subsection-title">能力检查</h3>
          <div class="capability-grid">
            <div v-for="item in latestCapabilities" :key="item.key" class="capability-item">
              <span>{{ item.label }}</span>
              <b :class="capabilityClass(item.value)"><i />{{ capabilityValueLabel(item.value) }}</b>
            </div>
          </div>
          <h3 class="subsection-title">本轮用量</h3>
          <div class="usage-grid">
            <div><span>输入 Token</span><b>{{ latestRun.promptTokens ?? '—' }}</b></div>
            <div><span>输出 Token</span><b>{{ latestRun.completionTokens ?? '—' }}</b></div>
            <div><span>总 Token</span><b>{{ latestRun.totalTokens ?? '—' }}</b></div>
            <div><span>搜索调用</span><b>{{ diagnosticSearchCallLabel(latestRun) }}</b></div>
            <div v-if="diagnosticCachedInputTokens(latestRun) !== null"><span>其中缓存 Token</span><b>{{ diagnosticCachedInputTokens(latestRun) }}</b></div>
          </div>
        </template>
        <div v-else class="result-empty">
          <el-icon><Connection /></el-icon>
          <strong>等待诊断结果</strong>
          <p>发送请求后，这里会展示鉴权、生成、联网、来源和引用五项能力。</p>
        </div>

        <div class="contract-card">
          <h3>当前调用契约</h3>
          <dl>
            <div><dt>协议</dt><dd>{{ selectedPlatform?.integrationType || '—' }}</dd></div>
            <div><dt>模型</dt><dd>{{ selectedPlatform?.modelId || '—' }}</dd></div>
            <div><dt>主凭证</dt><dd>{{ selectedPlatform?.credentialAvailable ? '可解析' : '不可用' }}</dd></div>
            <div><dt>配置</dt><dd>{{ selectedPlatform?.enabled ? '已启用' : '未启用，可预检' }}</dd></div>
            <div v-if="selectedPlatform?.usageScene === 'QUESTION_POLL_WEB'"><dt>问题轮询</dt><dd>{{ selectedPlatform?.enabledForQuestionPoll ? '已加入候选' : '未启用' }}</dd></div>
          </dl>
        </div>
      </aside>
    </main>

    <el-drawer v-model="historyVisible" title="诊断历史" size="520px">
      <div v-loading="historyLoading" class="history-list">
        <button v-for="item in history.records" :key="item.id" type="button" @click="restoreSession(item.sessionId)">
          <div><strong>{{ item.platformName }}</strong><el-tag size="small" :type="conclusionType(item.conclusion)">{{ item.conclusion || item.status }}</el-tag></div>
          <p>{{ item.requestedModelId || '未知模型' }} · {{ item.diagnosticMode }}</p>
          <small>{{ formatTime(item.createdAt) }} · {{ formatDuration(item.durationMs) }}</small>
        </button>
      </div>
      <el-pagination
        v-model:current-page="historyPage"
        :page-size="20"
        layout="prev, pager, next"
        :total="history.total"
        @current-change="loadHistory"
      />
    </el-drawer>

    <el-drawer v-model="rawVisible" title="诊断详情" size="620px">
      <template v-if="rawRun">
        <h4>脱敏请求</h4>
        <pre class="raw-json">{{ rawRun.sanitizedRequest || '无脱敏请求报文' }}</pre>
        <h4>脱敏响应</h4>
        <pre class="raw-json">{{ rawRun.sanitizedResponse || '无脱敏响应报文' }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessageBox, type TagProps } from 'element-plus'
import { ChatDotRound, Clock, Connection, Document, Plus, Promotion } from '@element-plus/icons-vue'
import {
  executeDiagnostic,
  getDiagnosticHistory,
  getDiagnosticPlatforms,
  getDiagnosticProbes,
  getDiagnosticSessionRuns,
  type DiagnosticHistoryPage,
  type DiagnosticModelTier,
  type DiagnosticMode,
  type DiagnosticPlatformOption,
  type DiagnosticProbeOption,
  type DiagnosticRunView,
  type DiagnosticTestMode,
} from '@/api/modelDiagnostic'
import {
  buildDiagnosticConversation,
  diagnosticErrorMessage,
  diagnosticSourceUrl,
  diagnosticCachedInputTokens,
  diagnosticReleaseRecommendation,
  diagnosticSearchCallLabel,
  diagnosticUniqueSourceDomainCount,
  isLongDiagnosticAnswer,
  renderDiagnosticAnswer,
  safeDiagnosticSourceUrl,
  shouldRestoreDiagnosticInput,
  type DiagnosticConversationMessage,
} from './modelDiagnosticConsole'

const platforms = ref<DiagnosticPlatformOption[]>([])
const probes = ref<DiagnosticProbeOption[]>([])
const channelCode = ref('')
const diagnosticMode = ref<DiagnosticMode>('BASIC_CHAT')
const platformSelectionKey = ref('')
const testMode = ref<DiagnosticTestMode>('FREE_CHAT')
const probeCode = ref('')
const userMessage = ref('')
const systemPrompt = ref('')
const sessionId = ref<string>(crypto.randomUUID())
const messages = ref<DiagnosticConversationMessage[]>([])
const sending = ref(false)
const messageListRef = ref<HTMLElement | null>(null)
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyPage = ref(1)
const history = ref<DiagnosticHistoryPage>({ records: [], total: 0, page: 1, size: 20 })
const rawVisible = ref(false)
const rawRun = ref<DiagnosticRunView | null>(null)
const expandedRunIds = ref<number[]>([])
const modeOptions = [{ label: '基础对话', value: 'BASIC_CHAT' }, { label: '联网搜索', value: 'WEB_SEARCH' }]
const capabilityDefinitions = [
  { key: 'authentication', label: '鉴权' },
  { key: 'generation', label: '内容生成' },
  { key: 'webSearch', label: '联网搜索' },
  { key: 'sourceParsing', label: '来源解析' },
  { key: 'citationParsing', label: '引用解析' },
]
let acceptedChannel = ''
let acceptedMode: DiagnosticMode = 'BASIC_CHAT'
let acceptedPlatformSelectionKey = ''
let acceptedTestMode: DiagnosticTestMode = 'FREE_CHAT'

const channels = computed(() => [...new Set(platforms.value.map((item) => item.channelCode))])
const availablePlatforms = computed(() => platforms.value.filter((item) => item.channelCode === channelCode.value && item.supportedModes.includes(diagnosticMode.value)))
const selectedPlatform = computed(() => platforms.value.find((item) => platformOptionKey(item) === platformSelectionKey.value))
const availableProbes = computed(() => probes.value.filter((item) => item.diagnosticMode === diagnosticMode.value && item.testMode === testMode.value))
const selectedProbe = computed(() => probes.value.find((item) => item.code === probeCode.value))
const requiresUserMessage = computed(() => testMode.value === 'FREE_CHAT' || Boolean(selectedProbe.value?.userMessageRequired))
const canSend = computed(() => Boolean(selectedPlatform.value?.selectable && !sending.value && (testMode.value === 'FREE_CHAT' ? userMessage.value.trim() : probeCode.value) && (!requiresUserMessage.value || userMessage.value.trim())))
const testModeHelp = computed(() => ({
  FREE_CHAT: '手工输入问题；仅成功的自由对话会进入后续上下文。',
  STANDARD_PROBE: '运行服务端固定问题，用于重复验证生成、联网和引用能力。',
  PRODUCTION_POLL_TEMPLATE: '复用正式轮询提示词验证真实问题，不执行批次、分片和统计。',
} as Record<DiagnosticTestMode, string>)[testMode.value])
const latestRun = computed(() => [...messages.value].reverse().find((item) => item.run)?.run || null)
const latestRecommendation = computed(() => {
  if (!latestRun.value) return { tone: 'pending' as const, label: '等待诊断', description: '', notices: [] }
  const recommendation = diagnosticReleaseRecommendation(latestRun.value)
  const pollNotEnabled = selectedPlatform.value?.usageScene === 'QUESTION_POLL_WEB'
    && (!selectedPlatform.value.enabled || !selectedPlatform.value.enabledForQuestionPoll)
  if (!pollNotEnabled) return recommendation
  return {
    ...recommendation,
    tone: recommendation.tone === 'fail' ? 'fail' as const : 'warning' as const,
    notices: [
      ...recommendation.notices,
      '当前仅可诊断预检；需在平台配置中同时启用模型和问题轮询开关，才会进入正式任务候选。',
    ],
  }
})
const latestCapabilities = computed(() => capabilityDefinitions.map((item) => ({
  ...item,
  value: latestRun.value?.capabilities[item.key] ?? null,
})))
const composerPlaceholder = computed(() => requiresUserMessage.value ? '输入本轮问题，不需要提交历史消息' : '固定探针由服务端生成，无需输入')
const platformStatus = computed(() => {
  if (!selectedPlatform.value) return '请选择模型配置'
  if (!selectedPlatform.value.selectable) return selectedPlatform.value.unavailableReason || '配置不可用'
  if (selectedPlatform.value.usageScene === 'QUESTION_POLL_WEB'
    && (!selectedPlatform.value.enabled || !selectedPlatform.value.enabledForQuestionPoll)) {
    return '诊断可调用 · 轮询未启用'
  }
  return selectedPlatform.value.enabled ? '已启用，可调用' : '诊断可调用 · 业务未启用'
})

onMounted(async () => {
  const [platformRes, probeRes] = await Promise.all([getDiagnosticPlatforms(), getDiagnosticProbes()])
  platforms.value = platformRes.data.data
  probes.value = probeRes.data.data
  channelCode.value = channels.value[0] || ''
  selectFirstPlatform()
  rememberSelections()
})

function selectFirstPlatform() {
  const option = availablePlatforms.value.find((item) => item.selectable) || availablePlatforms.value[0]
  platformSelectionKey.value = option ? platformOptionKey(option) : ''
}
async function onChannelChanged() {
  if (!await allowConversationReset()) { channelCode.value = acceptedChannel; return }
  resetConversation(); selectFirstPlatform(); rememberSelections()
}
async function onModeChanged() {
  if (!await allowConversationReset()) { diagnosticMode.value = acceptedMode; return }
  resetConversation(); selectFirstPlatform(); selectFirstProbe(); rememberSelections()
}
async function onPlatformChanged() {
  if (!await allowConversationReset()) { platformSelectionKey.value = acceptedPlatformSelectionKey; return }
  resetConversation(); rememberSelections()
}
async function onTestModeChanged() {
  if (!await allowConversationReset()) { testMode.value = acceptedTestMode; return }
  resetConversation(); selectFirstProbe(); rememberSelections()
}
async function newSession() {
  if (!await allowConversationReset()) return
  resetConversation(); rememberSelections()
}
function selectFirstProbe() { probeCode.value = availableProbes.value[0]?.code || '' }
function resetConversation() { sessionId.value = crypto.randomUUID(); messages.value = []; userMessage.value = ''; systemPrompt.value = ''; expandedRunIds.value = [] }
function rememberSelections() {
  acceptedChannel = channelCode.value
  acceptedMode = diagnosticMode.value
  acceptedPlatformSelectionKey = platformSelectionKey.value
  acceptedTestMode = testMode.value
}
async function allowConversationReset() {
  if (!messages.value.length) return true
  try {
    await ElMessageBox.confirm('切换配置会新建会话，当前对话仍可从诊断历史恢复。', '新建诊断会话', {
      confirmButtonText: '继续切换', cancelButtonText: '保留当前会话', type: 'warning',
    })
    return true
  } catch { return false }
}

async function send() {
  if (!canSend.value || !selectedPlatform.value) return
  const input = userMessage.value.trim()
  if (requiresUserMessage.value) messages.value.push({ key: crypto.randomUUID(), role: 'user', content: input })
  sending.value = true
  userMessage.value = ''
  await scrollToBottom()
  try {
    const response = await executeDiagnostic({
      sessionId: sessionId.value,
      clientRequestId: crypto.randomUUID(),
      platformConfigId: selectedPlatform.value.platformConfigId,
      modelTier: selectedPlatform.value.modelTier,
      mode: diagnosticMode.value,
      testMode: testMode.value,
      probeCode: testMode.value === 'FREE_CHAT' ? undefined : probeCode.value,
      systemPrompt: testMode.value === 'FREE_CHAT' && systemPrompt.value.trim() ? systemPrompt.value.trim() : undefined,
      userMessage: requiresUserMessage.value ? input : undefined,
    })
    const run = response.data.data
    if (!requiresUserMessage.value) messages.value.push({ key: `user-${run.runId}`, role: 'user', content: run.userMessage })
    messages.value.push({ key: `assistant-${run.runId}`, role: 'assistant', content: run.assistantMessage || run.error?.message || '未生成回答', platformName: run.platformName, run })
    if (shouldRestoreDiagnosticInput(run.status) && requiresUserMessage.value) userMessage.value = input
  } catch {
    if (requiresUserMessage.value) userMessage.value = input
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

async function openHistory() { historyVisible.value = true; historyPage.value = 1; await loadHistory() }
async function loadHistory() { historyLoading.value = true; try { history.value = (await getDiagnosticHistory({ page: historyPage.value, size: 20 })).data.data } finally { historyLoading.value = false } }
async function restoreSession(id: string) {
  const runs = (await getDiagnosticSessionRuns(id)).data.data
  sessionId.value = id
  messages.value = buildDiagnosticConversation(runs)
  expandedRunIds.value = []
  const last = runs.length ? runs[runs.length - 1] : undefined
  if (last) {
    diagnosticMode.value = last.diagnosticMode
    const restored = platforms.value.find((item) => item.platformConfigId === last.platformConfigId && item.modelId === last.requestedModelId)
      || platforms.value.find((item) => item.platformConfigId === last.platformConfigId && item.modelTier === 'PRIMARY')
    platformSelectionKey.value = restored ? platformOptionKey(restored) : ''
    channelCode.value = restored?.channelCode || channelCode.value
    testMode.value = last.testMode
    rememberSelections()
  }
  historyVisible.value = false
  await scrollToBottom()
}

function showRaw(run: DiagnosticRunView) { rawRun.value = run; rawVisible.value = true }
function isAnswerExpanded(runId: number) { return expandedRunIds.value.includes(runId) }
function toggleAnswer(runId: number) {
  expandedRunIds.value = isAnswerExpanded(runId)
    ? expandedRunIds.value.filter((id) => id !== runId)
    : [...expandedRunIds.value, runId]
}
function sourceUrl(source: Record<string, unknown>) { return diagnosticSourceUrl(source) }
function safeSourceUrl(source: Record<string, unknown>) { return safeDiagnosticSourceUrl(source) }
function sourceTitle(source: Record<string, unknown>, index: number) { return typeof source.title === 'string' && source.title ? source.title : `来源 ${index + 1}` }
function sourceDomain(source: Record<string, unknown>) { if (typeof source.domain === 'string') return source.domain; try { return new URL(safeSourceUrl(source)).hostname } catch { return '无有效链接' } }
function citationConfidence(citation: Record<string, unknown>) { return typeof citation.confidence === 'string' ? citation.confidence : '未确认' }
function citationConfidenceLabel(citation: Record<string, unknown>) {
  return ({
    CONFIRMED: '已确认',
    PROBABLE: '可能匹配',
    UNCONFIRMED: '未确认',
  } as Record<string, string>)[citationConfidence(citation)] || '未确认'
}
function citationText(citation: Record<string, unknown>, index: number) { return typeof citation.citationText === 'string' && citation.citationText ? citation.citationText : `引用 ${index + 1}` }
function citationType(citation: Record<string, unknown>): TagProps['type'] { return citationConfidence(citation) === 'CONFIRMED' ? 'success' : citationConfidence(citation) === 'PROBABLE' ? 'warning' : 'info' }
function citationMapping(citation: Record<string, unknown>, run: DiagnosticRunView) {
  const occurrence = typeof citation.sourceOccurrenceIndex === 'number' ? citation.sourceOccurrenceIndex : -1
  const source = occurrence >= 0 ? run.sources[occurrence] : undefined
  const answerStart = typeof citation.answerStart === 'number' ? citation.answerStart : null
  const answerEnd = typeof citation.answerEnd === 'number' ? citation.answerEnd : null
  const position = answerStart == null || answerEnd == null ? '回答位置未确认' : `回答位置 ${answerStart}–${answerEnd}`
  return `${position} → ${source ? sourceTitle(source, occurrence) : '未匹配来源'}`
}
function channelLabel(value: string) {
  return ({
    deepseek: 'DeepSeek',
    doubao: '豆包',
    qwen: '通义千问',
    wenxin: '文心一言',
    yuanbao: '腾讯元宝',
  } as Record<string, string>)[value.toLowerCase()] || value
}
function platformOptionKey(item: DiagnosticPlatformOption) { return `${item.platformConfigId}:${item.modelTier}` }
function modelTierLabel(value: DiagnosticModelTier) { return value === 'LOW' ? '低性能模型' : '主模型' }
function platformOptionLabel(item: DiagnosticPlatformOption) { return `${item.platformName} · ${item.modelId || '未配置模型'} · ${modelTierLabel(item.modelTier)}` }
function modeLabel(value: DiagnosticMode) { return value === 'WEB_SEARCH' ? '联网搜索' : '基础对话' }
function testModeLabel(value: DiagnosticTestMode) {
  return ({ FREE_CHAT: '自由对话', STANDARD_PROBE: '标准能力探针', PRODUCTION_POLL_TEMPLATE: '生产轮询模板' } as Record<DiagnosticTestMode, string>)[value]
}
function statusLabel(value: DiagnosticRunView['status']) {
  return ({ RUNNING: '执行中', SUCCEEDED: '执行完成', FAILED: '执行失败', REJECTED: '并发拒绝', ABANDONED: '已超时废弃' } as Record<DiagnosticRunView['status'], string>)[value]
}
function conclusionLabel(value: DiagnosticRunView['conclusion']) {
  return value === 'PASS' ? '诊断通过' : value === 'WARNING' ? '需要关注' : value === 'FAIL' ? '诊断未通过' : '等待判定'
}
function runBadgeLabel(run: DiagnosticRunView) { return run.conclusion ? conclusionLabel(run.conclusion) : statusLabel(run.status) }
function searchStatusLabel(value: string | null) {
  return ({ NOT_CONFIRMED: '未确认联网', TRIGGERED: '已触发搜索', EMPTY: '搜索无结果', NO_VALID_SOURCE: '无有效来源', FAILED: '搜索失败' } as Record<string, string>)[value || ''] || '不适用'
}
function capabilityValueLabel(value: string | null) {
  return ({ PASS: '通过', WARNING: '需关注', FAIL: '失败', NOT_APPLICABLE: '不适用' } as Record<string, string>)[value || ''] || '未确认'
}
function conclusionReasonLabel(value: string) {
  return ({
    'All applicable diagnostic capabilities passed': '本轮所有适用能力均已通过。',
    'Provider completed the request but web search was not confirmed': '供应商已完成请求，但未确认实际发生联网搜索。',
    'Web search ran but no valid source could be parsed': '已触发联网搜索，但没有解析到有效来源。',
    'The request completed with capability warnings': '请求已完成，但部分能力仍需人工确认。',
    'At least one required diagnostic capability failed': '至少一项必要诊断能力未通过。',
  } as Record<string, string>)[value] || value
}
function resultDescription(run: DiagnosticRunView) {
  if (run.conclusionReason) return conclusionReasonLabel(run.conclusionReason)
  if (run.error?.message) return diagnosticErrorMessage(run.error.message)
  if (run.conclusion === 'PASS') return '当前配置已完成本轮能力验证。'
  if (run.conclusion === 'WARNING') return '调用已完成，但部分能力仍需人工确认。'
  if (run.conclusion === 'FAIL') return '本轮存在未通过项，请查看能力检查和错误详情。'
  return '本轮尚未形成完整诊断结论。'
}
function conclusionType(value: string | null): TagProps['type'] { return value === 'PASS' ? 'success' : value === 'WARNING' ? 'warning' : value === 'FAIL' ? 'danger' : 'info' }
function capabilityClass(value: string | null) { return value === 'PASS' ? 'is-pass' : value === 'WARNING' ? 'is-warning' : value === 'FAIL' ? 'is-fail' : value === 'NOT_APPLICABLE' ? 'is-muted' : '' }
function formatDuration(value: number | null) { return value == null ? '—' : value < 1000 ? `${value}ms` : `${(value / 1000).toFixed(1)}s` }
function formatTime(value: string) { return dayjs(value).format('YYYY-MM-DD HH:mm') }
async function scrollToBottom() { await nextTick(); if (messageListRef.value) messageListRef.value.scrollTop = messageListRef.value.scrollHeight }
</script>

<style scoped>
.diagnostic-page {
  --ink: #17233a;
  --muted: #687791;
  --line: #e3e9f2;
  --primary: #315ee8;
  --primary-soft: #eef3ff;
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: calc(100vh - 104px);
  color: var(--ink);
}

.page-header,
.section-heading,
.conversation-header,
.composer-heading,
.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-header { padding: 2px 2px 4px; }
.title-group { min-width: 0; }
.eyebrow {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 7px;
  color: #4163ca;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .06em;
}
.eyebrow span { width: 18px; height: 2px; border-radius: 2px; background: var(--primary); }
.page-header h1 { margin: 0; font-size: clamp(28px, 2vw, 34px); line-height: 1.15; letter-spacing: -.035em; }
.subtitle { margin: 9px 0 0; color: var(--muted); font-size: 14px; line-height: 1.6; }
.header-actions { display: flex; gap: 10px; }

.control-panel,
.conversation-card,
.inspector-card {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(27, 39, 67, .055);
}

.control-panel { padding: 20px 22px 18px; }
.section-heading { gap: 18px; margin-bottom: 18px; }
.section-title { display: flex; align-items: center; gap: 11px; min-width: 0; }
.section-title h2 { margin: 0; font-size: 15px; line-height: 1.35; }
.section-title p { margin: 3px 0 0; color: var(--muted); font-size: 12px; line-height: 1.5; }
.section-title--compact { align-items: flex-start; }
.step-index {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--primary-soft);
  color: var(--primary);
  font: 700 11px/1 'JetBrains Mono', monospace;
}
.readiness-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  padding: 0 11px;
  border-radius: 999px;
  background: #fff8e6;
  color: #9a6700;
  font-size: 12px;
  font-weight: 650;
}
.status-dot { width: 8px; height: 8px; border-radius: 50%; background: #f59f00; }
.readiness-pill.is-ready { background: #eafaf4; color: #087c5b; }
.readiness-pill.is-ready .status-dot { background: #12b886; box-shadow: 0 0 0 4px rgba(18, 184, 134, .12); }
.readiness-pill.is-blocked { background: #fff0f1; color: #c92a3b; }
.readiness-pill.is-blocked .status-dot { background: #fa5252; }

.control-grid {
  display: grid;
  grid-template-columns: minmax(140px, .85fr) minmax(220px, 1fr) minmax(260px, 1.35fr) minmax(190px, 1fr);
  gap: 14px;
}
.field { display: flex; min-width: 0; flex-direction: column; gap: 8px; }
.field > span,
.field-copy b { color: #46536a; font-size: 12px; font-weight: 700; }
.field :deep(.el-select),
.field :deep(.el-segmented) { width: 100%; }
.field :deep(.el-segmented) { --el-segmented-item-selected-bg-color: var(--primary); --el-segmented-item-selected-color: #fff; }
.field-help { min-height: 32px; color: var(--muted); font-size: 10px; font-weight: 400; line-height: 1.55; }
.platform-option { display: flex; justify-content: space-between; gap: 16px; }
.platform-option small { color: #d9485f; }
.system-prompt-field {
  display: grid;
  grid-template-columns: minmax(190px, .55fr) minmax(0, 3fr);
  align-items: center;
  gap: 18px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #eef1f6;
}
.field-copy { display: flex; flex-direction: column; gap: 3px; }
.field-copy em { margin-left: 5px; color: #8995a9; font-size: 11px; font-style: normal; font-weight: 500; }
.field-copy small { color: var(--muted); font-size: 11px; line-height: 1.5; }
.context-strip {
  display: flex;
  align-items: center;
  gap: 22px;
  margin-top: 16px;
  padding: 11px 13px;
  border-radius: 11px;
  background: #f7f9fc;
}
.context-strip div { display: flex; align-items: center; gap: 7px; min-width: 0; font-size: 11px; }
.context-strip div span { color: var(--muted); }
.context-strip div strong { max-width: 240px; overflow: hidden; color: #3d4a61; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.session-code { margin-left: auto; color: #77849a; font: 11px/1.2 'JetBrains Mono', monospace; white-space: nowrap; }

.workspace { display: grid; grid-template-columns: minmax(0, 1fr) 320px; align-items: start; gap: 18px; }
.conversation-card { display: grid; min-height: 610px; grid-template-rows: auto minmax(0, 1fr) auto; overflow: hidden; }
.conversation-header { gap: 14px; padding: 17px 20px; border-bottom: 1px solid var(--line); }
.conversation-context { display: flex; align-items: center; gap: 9px; color: var(--muted); font-size: 12px; }
.message-list {
  height: clamp(420px, calc(100vh - 330px), 680px);
  min-height: 0;
  overflow: auto;
  padding: 24px;
  scroll-behavior: smooth;
  scrollbar-gutter: stable;
}
.empty-state { display: grid; place-items: center; max-width: 620px; min-height: 330px; margin: 18px auto; text-align: center; color: var(--muted); }
.empty-icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border: 1px solid #dce5fb;
  border-radius: 20px;
  background: linear-gradient(145deg, #f8faff, #eaf0ff);
  color: var(--primary);
  font-size: 26px;
  box-shadow: 0 14px 34px rgba(49, 94, 232, .12);
}
.empty-state h2 { margin: 18px 0 7px; color: var(--ink); font-size: 18px; }
.empty-state > p { max-width: 520px; margin: 0; font-size: 13px; line-height: 1.7; }
.empty-steps { display: flex; gap: 8px; margin-top: 22px; }
.empty-steps span { display: flex; align-items: center; gap: 7px; padding: 8px 10px; border: 1px solid #e7ebf3; border-radius: 10px; background: #fff; color: #536078; font-size: 11px; }
.empty-steps b { display: grid; place-items: center; width: 18px; height: 18px; border-radius: 6px; background: var(--primary-soft); color: var(--primary); }

.message { width: min(92%, 980px); margin-bottom: 24px; }
.message--assistant { width: 100%; }
.message--user { width: fit-content; max-width: min(76%, 720px); margin-left: auto; }
.message-meta { display: flex; align-items: center; gap: 8px; margin: 0 6px 7px; color: #637089; font-size: 12px; font-weight: 700; }
.message--user .message-meta { justify-content: flex-end; }
.message-body { padding: 14px 16px; border-radius: 15px; background: #f3f6fa; color: #26344c; line-height: 1.75; overflow-wrap: anywhere; white-space: pre-wrap; }
.message--user .message-body { border-bottom-right-radius: 5px; background: linear-gradient(135deg, #315ee8, #3c58c9); color: #fff; }
.message--assistant .message-body { max-width: 880px; border: 1px solid #e9edf4; border-bottom-left-radius: 5px; background: #f7f9fc; box-shadow: 0 8px 24px rgba(31, 45, 76, .045); }
.answer-shell { position: relative; max-width: 880px; }
.answer-shell.has-toggle { padding-bottom: 42px; }
.answer-shell.is-collapsed .message-body { max-height: 480px; overflow: hidden; }
.answer-shell.is-collapsed::after {
  content: '';
  position: absolute;
  right: 1px;
  bottom: 42px;
  left: 1px;
  height: 96px;
  border-radius: 0 0 14px 5px;
  background: linear-gradient(180deg, rgba(247, 249, 252, 0), #f7f9fc 82%);
  pointer-events: none;
}
.answer-toggle {
  position: absolute;
  z-index: 1;
  bottom: 0;
  left: 50%;
  min-height: 32px;
  padding: 0 14px;
  border: 1px solid #ccd7f5;
  border-radius: 999px;
  background: #fff;
  color: #3157c8;
  font: 650 11px/1 sans-serif;
  cursor: pointer;
  transform: translateX(-50%);
}
.answer-toggle:hover { border-color: #8da6eb; background: #f3f6ff; }
.answer-toggle:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.message-body--markdown { overflow-x: auto; padding: 18px 20px; white-space: normal; }
.message-body--markdown :deep(> :first-child) { margin-top: 0; }
.message-body--markdown :deep(> :last-child) { margin-bottom: 0; }
.message-body--markdown :deep(h1),
.message-body--markdown :deep(h2),
.message-body--markdown :deep(h3),
.message-body--markdown :deep(h4) { margin: 1.25em 0 .55em; color: #17233a; line-height: 1.35; letter-spacing: -.015em; }
.message-body--markdown :deep(h1) { font-size: 20px; }
.message-body--markdown :deep(h2) { padding-bottom: 7px; border-bottom: 1px solid #e3e8f1; font-size: 17px; }
.message-body--markdown :deep(h3) { font-size: 15px; }
.message-body--markdown :deep(p),
.message-body--markdown :deep(ul),
.message-body--markdown :deep(ol),
.message-body--markdown :deep(blockquote) { margin: .75em 0; }
.message-body--markdown :deep(ul),
.message-body--markdown :deep(ol) { padding-left: 1.45em; }
.message-body--markdown :deep(li + li) { margin-top: .35em; }
.message-body--markdown :deep(strong) { color: #16243c; font-weight: 700; }
.message-body--markdown :deep(a) { color: #2856d8; text-decoration: none; }
.message-body--markdown :deep(a:hover) { text-decoration: underline; }
.message-body--markdown :deep(.inline-citation) { display: inline-flex; align-items: center; max-width: 180px; margin: 0 3px; padding: 1px 7px; overflow: hidden; border: 1px solid #e1e6ef; border-radius: 999px; background: #f2f4f8; color: #6b7890; font-size: 10px; font-weight: 500; line-height: 1.55; text-decoration: none; text-overflow: ellipsis; vertical-align: .08em; white-space: nowrap; }
.message-body--markdown :deep(.inline-citation:hover) { border-color: #b9c7eb; background: #eaf0ff; color: #3157c8; text-decoration: none; }
.message-body--markdown :deep(.inline-citation:focus-visible) { outline: 2px solid var(--primary); outline-offset: 2px; }
.message-body--markdown :deep(.inline-citation--disabled) { cursor: default; opacity: .72; }
.message-body--markdown :deep(blockquote) { padding: 8px 12px; border-left: 3px solid #8ea7ef; background: #eef3ff; color: #53627a; }
.message-body--markdown :deep(code) { padding: 2px 5px; border-radius: 5px; background: #e9eef8; color: #344c8c; font: .88em 'JetBrains Mono', monospace; }
.message-body--markdown :deep(pre) { overflow-x: auto; padding: 13px; border-radius: 10px; background: #172033; color: #e8eefc; }
.message-body--markdown :deep(pre code) { padding: 0; background: transparent; color: inherit; }
.message-body--markdown :deep(table) { width: 100%; min-width: 580px; margin: 13px 0; border-collapse: separate; border-spacing: 0; overflow: hidden; border: 1px solid #dfe5ef; border-radius: 10px; font-size: 12px; }
.message-body--markdown :deep(th),
.message-body--markdown :deep(td) { padding: 9px 11px; border-right: 1px solid #e7ebf2; border-bottom: 1px solid #e7ebf2; text-align: left; vertical-align: top; }
.message-body--markdown :deep(th) { background: #edf2fb; color: #34435e; font-weight: 700; }
.message-body--markdown :deep(tr:last-child td) { border-bottom: 0; }
.message-body--markdown :deep(th:last-child),
.message-body--markdown :deep(td:last-child) { border-right: 0; }
.conclusion-note { margin-top: 10px; padding: 9px 11px; border-left: 3px solid #9bb1ef; border-radius: 0 9px 9px 0; background: #f7f9fe; color: #59677e; font-size: 12px; line-height: 1.55; }
.conclusion-note span { margin-right: 8px; color: #35435b; font-weight: 700; }
.run-summary { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 10px; }
.run-summary > span { display: inline-flex; gap: 5px; padding: 5px 8px; border: 1px solid #e8ecf3; border-radius: 8px; background: #fff; color: #66748b; font-size: 11px; }
.run-summary b { color: #3f4d64; font-weight: 700; }
.evidence-stack { display: grid; max-width: 980px; gap: 9px; margin-top: 12px; }
.evidence-panel { overflow: hidden; border: 1px solid #e2e7f0; border-radius: 12px; background: #fff; }
.evidence-panel summary { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 12px 14px; background: #f8faff; cursor: pointer; list-style: none; user-select: none; }
.evidence-panel summary::-webkit-details-marker { display: none; }
.evidence-panel summary::after { content: '⌄'; flex: 0 0 auto; color: #7586aa; font-size: 16px; transition: transform .16s ease; }
.evidence-panel[open] summary::after { transform: rotate(180deg); }
.evidence-title { display: flex; align-items: baseline; gap: 9px; min-width: 0; }
.evidence-title b { color: #32415c; font-size: 12px; }
.evidence-title small { color: var(--muted); font-size: 11px; }
.evidence-count { margin-left: auto; padding: 4px 8px; border-radius: 999px; background: #edf3ff; color: #3157c8; font-size: 10px; font-weight: 700; white-space: nowrap; }
.source-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; padding: 10px; border-top: 1px solid #edf0f5; }
.source-card { display: flex; gap: 11px; padding: 11px; border: 1px solid var(--line); border-radius: 11px; color: var(--ink); transition: border-color .16s ease, background .16s ease; }
.source-card:hover { border-color: #aebff5; background: #f8faff; }
.source-card > span { display: grid; place-items: center; flex: 0 0 auto; width: 25px; height: 25px; border-radius: 7px; background: #e9edff; color: var(--primary); font-weight: 700; }
.source-card > div { min-width: 0; }
.source-title-link,
.source-list small { display: block; }
.source-list small { margin-top: 3px; color: var(--muted); }
.source-title-link { display: -webkit-box; overflow: hidden; color: var(--ink); font-weight: 700; line-height: 1.45; text-decoration: none; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.source-title-link[href]::after { content: ' ↗'; color: #7186bd; font-size: .86em; }
.source-title-link:hover { color: var(--primary); text-decoration: underline; text-underline-offset: 3px; }
.source-title-link:focus-visible { border-radius: 4px; outline: 2px solid var(--primary); outline-offset: 3px; }
.source-title-link--disabled { opacity: .62; }
.citation-list { display: grid; gap: 7px; padding: 10px; border-top: 1px solid #edf0f5; }
.citation-list > div { display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 10px; padding: 9px 11px; border-radius: 10px; background: #f8f9fc; font-size: 12px; }
.citation-list p { margin: 0; color: var(--muted); }
.raw-link { margin-top: 9px; padding-left: 0; }
.message--loading { display: flex; align-items: center; gap: 10px; color: var(--muted); }
.pulse { width: 9px; height: 9px; border-radius: 50%; background: var(--primary); animation: pulse 1s infinite; }

.composer { padding: 15px 18px 16px; border-top: 1px solid var(--line); background: #fbfcfe; }
.composer-heading { margin-bottom: 8px; }
.composer-heading label { color: #3f4d64; font-size: 12px; font-weight: 700; }
.composer-heading span { color: var(--muted); font-size: 11px; }
.composer-footer { margin-top: 10px; color: var(--muted); font-size: 11px; }
.composer-footer kbd { padding: 2px 5px; border: 1px solid #d8deea; border-bottom-width: 2px; border-radius: 5px; background: #fff; color: #4d5a70; font: 10px 'JetBrains Mono', monospace; }
.composer :deep(.el-textarea__inner) { min-height: 86px !important; border-radius: 11px; box-shadow: 0 0 0 1px #dfe5ee inset; }
.composer :deep(.el-textarea__inner:focus) { box-shadow: 0 0 0 1px var(--primary) inset, 0 0 0 3px rgba(49, 94, 232, .1); }
.composer-footer :deep(.el-button) { min-width: 122px; min-height: 40px; }

.inspector-card { padding: 20px; }
.result-hero { margin-top: 18px; padding: 16px; border-radius: 13px; background: #f1f4f8; color: #536078; }
.result-hero > span { display: block; margin-bottom: 5px; font-size: 11px; font-weight: 700; }
.result-hero strong { display: block; color: #344054; font-size: 22px; line-height: 1.3; }
.result-hero p { margin: 7px 0 0; font-size: 11px; line-height: 1.6; }
.result-hero--pass { background: #eaf9f3; color: #35826b; }
.result-hero--pass strong { color: #087c5b; }
.result-hero--warning { background: #fff8e1; color: #9a6b09; }
.result-hero--warning strong { color: #c16b00; }
.result-hero--fail { background: #fff0f1; color: #a6505d; }
.result-hero--fail strong { color: #c92a3b; }
.rollout-card { margin-top: 10px; padding: 13px 14px; border: 1px solid #e2e7f0; border-radius: 12px; background: #f8fafc; }
.rollout-card > span { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: #667085; font-size: 10px; font-weight: 700; }
.rollout-card em { color: #98a2b3; font-size: 9px; font-style: normal; font-weight: 500; }
.rollout-card strong { display: block; margin-top: 5px; color: #344054; font-size: 14px; }
.rollout-card p { margin: 5px 0 0; color: #667085; font-size: 10px; line-height: 1.55; }
.rollout-card ul { display: grid; gap: 5px; margin: 9px 0 0; padding: 8px 9px 8px 24px; border-radius: 8px; background: rgba(255, 255, 255, .72); color: #7a5a13; font-size: 10px; line-height: 1.45; }
.rollout-card--pass { border-color: #cdece1; background: #f0faf6; }
.rollout-card--pass strong { color: #087c5b; }
.rollout-card--warning { border-color: #f2dfad; background: #fffaf0; }
.rollout-card--warning strong { color: #a86300; }
.rollout-card--fail { border-color: #f2cbd1; background: #fff5f6; }
.rollout-card--fail strong { color: #c92a3b; }
.subsection-title { margin: 20px 0 10px; color: #344054; font-size: 12px; }
.capability-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 7px; }
.capability-item { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 9px; border: 1px solid #edf0f5; border-radius: 9px; font-size: 11px; }
.capability-item > span { color: var(--muted); }
.capability-item b { display: inline-flex; align-items: center; gap: 5px; color: #758197; font-size: 11px; white-space: nowrap; }
.capability-item i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.capability-item .is-pass { color: #0b8f68; }
.capability-item .is-warning { color: #d47b00; }
.capability-item .is-fail { color: #d6384c; }
.capability-item .is-muted { color: #98a2b3; }
.usage-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.usage-grid div { padding: 10px; border-radius: 10px; background: #f5f7fb; }
.usage-grid span,
.usage-grid b { display: block; font-size: 11px; }
.usage-grid span { color: var(--muted); }
.usage-grid b { margin-top: 4px; color: #344054; font: 700 13px 'JetBrains Mono', monospace; }
.usage-grid div:nth-child(4) b { font-family: inherit; font-size: 11px; line-height: 1.35; }
.result-empty { display: grid; place-items: center; margin-top: 18px; padding: 28px 14px; border: 1px dashed #dce3ee; border-radius: 13px; text-align: center; }
.result-empty .el-icon { color: #8095c9; font-size: 25px; }
.result-empty strong { margin-top: 10px; font-size: 13px; }
.result-empty p { margin: 6px 0 0; color: var(--muted); font-size: 11px; line-height: 1.6; }
.contract-card { margin-top: 20px; padding-top: 18px; border-top: 1px solid var(--line); }
.contract-card h3 { margin: 0 0 8px; color: #344054; font-size: 12px; }
.contract-card dl { margin: 0; }
.contract-card dl div { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px solid #f0f2f6; font-size: 11px; }
.contract-card dt { color: var(--muted); }
.contract-card dd { margin: 0; max-width: 190px; text-align: right; overflow-wrap: anywhere; }

.history-list { display: grid; gap: 9px; min-height: 200px; }
.history-list button { padding: 13px; border: 1px solid var(--line); border-radius: 12px; background: #fff; text-align: left; cursor: pointer; }
.history-list button:hover { border-color: #91a7ff; background: #f8f9ff; }
.history-list button div { display: flex; justify-content: space-between; }
.history-list p { margin: 7px 0; color: #526078; }
.history-list small { color: var(--muted); }
.raw-json { margin: 0 0 20px; padding: 16px; border-radius: 12px; background: #111827; color: #dbeafe; font: 12px/1.65 'JetBrains Mono', monospace; overflow-wrap: anywhere; white-space: pre-wrap; }

@keyframes pulse { 50% { opacity: .25; transform: scale(.75); } }

@media (max-width: 1280px) {
  .control-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 1100px) {
  .workspace { grid-template-columns: 1fr; }
  .capability-grid { grid-template-columns: repeat(5, minmax(0, 1fr)); }
  .usage-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .contract-card dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 22px; }
}

@media (max-width: 720px) {
  .diagnostic-page { gap: 14px; }
  .page-header { align-items: flex-start; gap: 16px; }
  .header-actions { flex-direction: column-reverse; }
  .header-actions :deep(.el-button) { width: 100%; margin-left: 0; }
  .section-heading { align-items: flex-start; }
  .readiness-pill { max-width: 46%; }
  .control-panel { padding: 17px; }
  .control-grid { grid-template-columns: 1fr; }
  .system-prompt-field { grid-template-columns: 1fr; gap: 8px; }
  .context-strip { align-items: flex-start; flex-direction: column; gap: 8px; }
  .session-code { margin-left: 0; }
  .conversation-header { align-items: flex-start; }
  .conversation-context { align-items: flex-end; flex-direction: column; }
  .message-list { padding: 18px 15px; }
  .message { width: 94%; }
  .message--assistant { width: 100%; }
  .message--user { width: fit-content; max-width: 88%; }
  .message-body--markdown { padding: 15px; }
  .empty-steps { align-items: stretch; flex-direction: column; width: 100%; max-width: 240px; }
  .source-list { grid-template-columns: 1fr; }
  .evidence-title small { display: none; }
  .citation-list > div { grid-template-columns: 1fr auto; }
  .citation-list p { grid-column: 1 / -1; }
  .composer-footer > span { display: none; }
  .capability-grid { grid-template-columns: 1fr 1fr; }
  .usage-grid { grid-template-columns: 1fr 1fr; }
  .contract-card dl { display: block; }
}

@media (prefers-reduced-motion: reduce) {
  .pulse { animation: none; }
  .message-list { scroll-behavior: auto; }
}
</style>
