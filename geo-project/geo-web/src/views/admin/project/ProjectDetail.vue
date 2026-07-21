<template>
  <div class="admin-page">
    <el-page-header content="项目详情" @back="$router.back()" />

    <section v-if="project" class="admin-object-hero">
      <div class="admin-object-hero-main">
        <div>
          <h1 class="admin-object-title">{{ project.projectName }}</h1>
          <div class="admin-object-meta">
            {{ project.companyName || '-' }} · {{ project.brandName || '-' }}
          </div>
        </div>
        <span class="admin-status-tag" :class="projectStatusClass(project.status)">
          {{ projectStatusLabel(project.status) }}
        </span>
      </div>
      <div class="admin-object-kpis project-hero-kpis">
        <div class="admin-object-kpi project-hero-kpi project-hero-kpi--keyword">
          <span>拓词组</span>
          <strong>{{ project.selectedKeywordGroups?.length || 0 }}</strong>
        </div>
        <div class="admin-object-kpi project-hero-kpi project-hero-kpi--quota">
          <span>问题额度</span>
          <strong>{{ keywordAllocationSummary }}</strong>
        </div>
        <div class="admin-object-kpi project-hero-kpi project-hero-kpi--channel">
          <span>渠道额度</span>
          <strong>{{ project.channelAllocations?.length || 0 }}</strong>
        </div>
      </div>
    </section>

    <el-card v-loading="loading" class="admin-rich-card">
      <template #header>
        <div class="flex items-center justify-between">
          <span>基础信息</span>
          <div class="space-x-2">
            <el-button size="small" @click="goReports">实时看板</el-button>
            <el-button size="small" type="primary" plain @click="goMobileDashboardAdmin">移动数据看板</el-button>
            <el-button v-if="project?.status === 'active'" size="small" type="primary" plain @click="goBaselineReport">基线检测报告</el-button>
            <el-tag>{{ projectStatusLabel(project?.status) }}</el-tag>
          </div>
        </div>
      </template>
      <div class="admin-info-grid">
        <div
          v-for="item in projectBasicInfoItems"
          :key="item.label"
          class="admin-info-item"
          :class="{ 'is-wide': item.wide }"
        >
          <span class="admin-info-label">{{ item.label }}</span>
          <strong class="admin-info-value">{{ item.value }}</strong>
        </div>
      </div>
    </el-card>

    <el-card v-if="project" class="admin-rich-card">
      <template #header>
        <div class="section-header">
          <span>客户需求</span>
          <el-button v-if="canUpdateProject" size="small" type="primary" plain @click="openRequirementEdit">维护需求</el-button>
        </div>
      </template>
      <div v-if="project.customerRequirements?.length" class="requirement-view-list">
        <div v-for="(item, index) in project.customerRequirements" :key="`${index}-${item}`" class="requirement-view-item">
          <div class="requirement-view-index">{{ index + 1 }}</div>
          <div class="requirement-view-text">{{ item }}</div>
        </div>
      </div>
      <el-empty v-else description="暂无客户需求" :image-size="72" />
    </el-card>

    <MobileDashboardCompetitorPanel
      v-if="project"
      :project-id="projectId"
      :editable="canManageMobileCompetitors"
    />

    <el-card v-if="project" class="admin-rich-card">
      <template #header>
        <div class="flex items-center justify-between">
          <span>分发渠道额度</span>
          <el-button v-if="canUpdateProject" size="small" type="primary" plain @click="openChannelAllocationEdit">调整额度</el-button>
        </div>
      </template>
      <el-alert
        type="info"
        :closable="false"
        class="mb-3"
        title="仅官网、行业资讯站参与文章生成调度；额度为 0 时不会生成文章。"
      />
      <el-table :data="projectBaseChannelAllocations" border empty-text="暂无渠道额度">
        <el-table-column prop="channelName" label="渠道" min-width="140">
          <template #default="{ row }">
            <div class="channel-name">
              <span>{{ row.channelName || row.channelCode }}</span>
              <el-tag v-if="isArticleGenerationChannel(row.channelCode)" size="small" type="success">生成文章渠道</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="周期" width="100">
          <template #default="{ row }">{{ periodLabel(row.periodType) }}</template>
        </el-table-column>
        <el-table-column label="套餐总额" width="110">
          <template #default="{ row }">{{ row.quotaLimit || 0 }}</template>
        </el-table-column>
        <el-table-column label="已激活占用" width="120">
          <template #default="{ row }">{{ row.activeAllocatedCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="当前项目" width="120">
          <template #default="{ row }">{{ row.currentProjectAllocatedCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '可用' : '未启用' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-collapse v-if="projectSelfMediaChannelAllocations.length" class="quota-channel-groups" model-value="self_media">
        <el-collapse-item name="self_media">
          <template #title>
            <div class="quota-group-title">
              <span>自媒体平台</span>
              <el-tag size="small" type="info">{{ projectSelfMediaChannelAllocations.length }} 个平台</el-tag>
            </div>
          </template>
          <el-table :data="projectSelfMediaChannelAllocations" border empty-text="暂无自媒体平台额度">
            <el-table-column prop="channelName" label="平台" min-width="140">
              <template #default="{ row }">
                <div class="channel-name">
                  <span>{{ row.channelName || row.channelCode }}</span>
                  <el-tag v-if="isArticleGenerationChannel(row.channelCode)" size="small" type="success">生成文章渠道</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="周期" width="100">
              <template #default="{ row }">{{ periodLabel(row.periodType) }}</template>
            </el-table-column>
            <el-table-column label="套餐总额" width="110">
              <template #default="{ row }">{{ row.quotaLimit || 0 }}</template>
            </el-table-column>
            <el-table-column label="已激活占用" width="120">
              <template #default="{ row }">{{ row.activeAllocatedCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="当前项目" width="120">
              <template #default="{ row }">{{ row.currentProjectAllocatedCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '可用' : '未启用' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <el-card v-if="project && canViewSelfMediaSchedulePanel" v-loading="selfMediaScheduleLoading" class="admin-rich-card">
      <template #header>
        <div class="section-header">
          <div class="auto-schedule-title">
            <span>自媒体自动排期</span>
            <el-tag size="small" :type="selfMediaScheduleForm.autoScheduleEnabled ? 'success' : 'info'">
              {{ selfMediaScheduleForm.autoScheduleEnabled ? '已开启' : '已关闭' }}
            </el-tag>
          </div>
          <div class="auto-schedule-actions">
            <el-button size="small" :loading="selfMediaScheduleLoading" @click="loadSelfMediaSchedulePanel">刷新</el-button>
            <el-button v-if="canUpdateProject" size="small" type="primary" plain :loading="selfMediaScheduleSaving" @click="saveSelfMediaScheduleConfig">保存配置</el-button>
          </div>
        </div>
      </template>
      <div class="auto-schedule-shell">
        <div class="auto-schedule-overview">
          <div class="auto-schedule-overview-main">
            <span class="auto-schedule-kicker">排期方式</span>
            <strong>按平台规则</strong>
            <small>{{ selfMediaScheduleForm.includeAdjustedWorkdays ? '包含调休工作日' : '仅按标准工作日' }}</small>
          </div>
          <div class="auto-schedule-overview-meta">
            <span>{{ selfMediaScheduleMonth || '-' }}</span>
            <span>{{ selectedAccountCountText }}</span>
            <span>{{ selfMediaBatchStatusLabel(selfMediaScheduleBatch?.status) }}</span>
          </div>
        </div>

        <div class="auto-schedule-layout">
          <div class="auto-schedule-config">
            <div class="auto-schedule-panel-head">
              <span>排期配置</span>
              <small>项目级规则</small>
            </div>
            <el-form label-position="top" class="auto-schedule-form">
              <el-form-item label="自动创建排期">
                <div class="auto-schedule-switch-row">
                  <el-switch
                    v-model="selfMediaScheduleForm.autoScheduleEnabled"
                    :disabled="!canUpdateProject"
                  />
                  <span>{{ selfMediaScheduleForm.autoScheduleEnabled ? '开启' : '关闭' }}</span>
                </div>
              </el-form-item>
              <el-form-item label="平台规则">
                <div class="auto-schedule-rule-card">
                  <strong>平台自动匹配</strong>
                  <span>头条、百家号、小红书、知乎分别使用自身定时能力。</span>
                </div>
              </el-form-item>
              <el-form-item label="工作日规则">
                <el-checkbox v-model="selfMediaScheduleForm.includeAdjustedWorkdays" :disabled="!canUpdateProject">允许排入调休工作日</el-checkbox>
              </el-form-item>
              <el-form-item label="备注">
                <el-input
                  v-model="selfMediaScheduleForm.remark"
                  :disabled="!canUpdateProject"
                  maxlength="255"
                  show-word-limit
                  placeholder="可记录该项目的排期约束"
                />
              </el-form-item>
            </el-form>
          </div>

          <div class="auto-schedule-runner">
            <div class="auto-schedule-panel-head">
              <span>创建批次</span>
              <small>{{ selfMediaScheduleBatch ? `最近更新 ${compactDateTime(selfMediaScheduleBatch.updatedAt || selfMediaScheduleBatch.createdAt)}` : '尚无本月批次' }}</small>
            </div>
            <div class="auto-schedule-filters">
              <div class="auto-schedule-toolbar">
                <label class="auto-schedule-filter-field">
                  <span>目标月份</span>
                  <el-date-picker
                    v-model="selfMediaScheduleMonth"
                    type="month"
                    value-format="YYYY-MM"
                    format="YYYY-MM"
                    placeholder="选择月份"
                    class="auto-schedule-month"
                    @change="handleSelfMediaScheduleMonthChange"
                  />
                </label>
                <label class="auto-schedule-filter-field is-account">
                  <span>账号范围</span>
                  <el-select
                    v-model="selectedSelfMediaAccountIds"
                    multiple
                    collapse-tags
                    collapse-tags-tooltip
                    clearable
                    class="auto-schedule-accounts"
                    placeholder="默认全部启用账号"
                  >
                    <el-option
                      v-for="account in activeSelfMediaAccounts"
                      :key="account.id"
                      :label="selfMediaAccountLabel(account)"
                      :value="account.id"
                    />
                  </el-select>
                </label>
                <div class="auto-schedule-filter-note">
                  <strong>{{ selectedAccountCountText }}</strong>
                  <small>本次账号范围</small>
                </div>
              </div>

              <div class="auto-schedule-platforms">
                <span v-if="!activeSelfMediaPlatformPills.length" class="auto-schedule-platform-empty">暂无启用账号</span>
                <template v-else>
                  <span
                    v-for="item in activeSelfMediaPlatformPills"
                    :key="item.platform"
                    class="auto-schedule-platform-pill"
                  >
                    {{ item.label }} {{ item.count }}
                  </span>
                </template>
              </div>

              <el-alert
                :type="selfMediaCalendarMissing ? 'warning' : 'success'"
                :closable="false"
                class="auto-schedule-alert"
                :title="selfMediaCalendarStatusText"
              />
            </div>

            <div v-loading="selfMediaAutomationOverviewLoading" class="auto-schedule-health">
              <div class="auto-schedule-health-head">
                <div>
                  <strong>运行概况</strong>
                  <small>{{ selfMediaAutomationSummaryText }}</small>
                </div>
                <el-button link type="primary" :loading="selfMediaAutomationOverviewLoading" @click="loadSelfMediaAutomationOverview">
                  刷新
                </el-button>
              </div>
              <div class="auto-schedule-health-grid is-compact">
                <div v-for="item in selfMediaAutomationHealthItems" :key="item.label" class="auto-schedule-health-item">
                  <span>{{ item.label }}</span>
                  <strong>{{ item.value }}</strong>
                  <small>{{ item.hint }}</small>
                </div>
              </div>
              <el-alert
                v-if="selfMediaAutomationBlocked"
                type="warning"
                :closable="false"
                class="auto-schedule-health-alert"
                title="当前平台需要本地助手处理，但本地助手不足或未在线，自动排期创建后可能无法按时开始。"
              />
              <el-collapse class="auto-schedule-debug-collapse">
                <el-collapse-item title="排查信息" name="debug">
                  <div v-if="selfMediaAutomationCompensation" class="auto-schedule-health-note">
                    <strong>自动补救</strong>
                    <span>{{ selfMediaAutomationCompensation.message || '暂无自动补救信息' }}</span>
                    <small>
                      待补救 {{ selfMediaAutomationCompensation.candidateCount || 0 }} 条
                      <template v-if="selfMediaAutomationCompensation.lastTriedAt">
                        / 最近 {{ compactDateTime(selfMediaAutomationCompensation.lastTriedAt) }}
                      </template>
                    </small>
                  </div>
                  <div v-if="selfMediaLocalAgentSessions.length" class="auto-schedule-agent-list">
                    <span
                      v-for="session in selfMediaLocalAgentSessions"
                      :key="session.sessionId"
                      class="auto-schedule-agent-item"
                      :class="{ 'is-offline': !session.online }"
                    >
                      <strong>{{ session.helperName || '本地助手' }}</strong>
                      <small>{{ session.operatorName || '未绑定运营' }}</small>
                      <el-tag size="small" :type="session.online ? 'success' : 'warning'" effect="light">
                        {{ session.online ? '在线' : '未在线' }}
                      </el-tag>
                      <small>最近 {{ compactDateTime(session.lastSeenAt) }} / 处理中 {{ session.runningLoad || 0 }} / 等待 {{ session.waitingTasks || 0 }}</small>
                    </span>
                  </div>
                  <div v-if="selectedSelfMediaPlatformCapabilities.length" class="auto-schedule-capabilities">
                    <span
                      v-for="item in selectedSelfMediaPlatformCapabilities"
                      :key="item.platform"
                      class="auto-schedule-capability"
                      :class="{ 'is-warning': !item.scheduleReady }"
                    >
                      <strong>{{ item.displayName || selfMediaPlatformLabel(item.platform) }}</strong>
                      <small>{{ item.requiresLocalAgent ? '本地助手' : '官方能力' }}</small>
                      <el-tag size="small" :type="item.scheduleReady ? 'success' : 'warning'" effect="light">
                        {{ item.scheduleReady ? '可预约发布' : '需确认' }}
                      </el-tag>
                      <small class="auto-schedule-capability-rule">
                        处理提前 {{ selfMediaMinutesText(item.fillLeadMinutes) }} / 平台提前 {{ selfMediaMinutesText(item.minRemainingMinutes) }}
                      </small>
                      <em v-if="item.readinessMessage">{{ item.readinessMessage }}</em>
                    </span>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>

            <div class="auto-schedule-batch">
              <template v-if="selfMediaScheduleBatch">
                <el-tag :type="selfMediaBatchTagType(selfMediaScheduleBatch.status)" effect="light">
                  {{ selfMediaBatchStatusLabel(selfMediaScheduleBatch.status) }}
                </el-tag>
                <span>计划 {{ selfMediaScheduleBatch.plannedCount || 0 }}，已排 {{ selfMediaScheduleBatch.createdCount || 0 }}，失败 {{ selfMediaScheduleBatch.rejectedCount || 0 }}</span>
                <span v-if="selfMediaScheduleBatch.requestedCount != null">
                  请求 {{ selfMediaScheduleBatch.requestedCount || 0 }}，缺口 {{ selfMediaScheduleBatch.deficitCount || 0 }}，结转 {{ selfMediaScheduleBatch.carryOverCount || 0 }}
                </span>
                <el-button link type="primary" :loading="selfMediaDetailLoading" @click="openSelfMediaBatchDetail">
                  查看明细
                </el-button>
              </template>
              <span v-else>当前月份暂无批次</span>
            </div>

            <el-alert
              v-if="selfMediaScheduleBatch?.failureMessage"
              type="warning"
              :closable="false"
              class="auto-schedule-alert"
              :title="selfMediaScheduleBatch.failureMessage"
            />
            <el-alert
              v-if="selfMediaScheduleBatch?.decisionReason"
              type="info"
              :closable="false"
              class="auto-schedule-alert"
              :title="`结转原因：${selfMediaScheduleBatch.decisionReason}`"
            />
            <el-alert
              v-if="selfMediaPreview && selfMediaPreview.enough === false"
              type="warning"
              :closable="false"
              class="auto-schedule-alert"
              :title="selfMediaCapacityWarningText"
            />
            <el-alert
              v-for="warning in selfMediaPreview?.warnings || []"
              :key="warning"
              type="warning"
              :closable="false"
              class="auto-schedule-alert"
              :title="warning"
            />
            <div
              v-if="selfMediaPreview && ((selfMediaPreview.normalRequiredCount || 0) > 0 || (selfMediaPreview.pendingCarryOverCount || 0) > 0 || (selfMediaPreview.carryOverSources || []).length)"
              class="auto-schedule-demand-summary"
            >
              <span>正常额度 {{ selfMediaPreview.normalRequiredCount || 0 }}</span>
              <span>历史结转 {{ selfMediaPreview.pendingCarryOverCount || 0 }}</span>
              <span v-if="selfMediaPreview.unavailableCarryOverCount">不可用结转 {{ selfMediaPreview.unavailableCarryOverCount }}</span>
              <span
                v-for="source in selfMediaPreview.carryOverSources || []"
                :key="source.id || `${source.sourceMonth}-${source.targetMonth}`"
              >
                {{ source.sourceMonth || '-' }} 结转 {{ source.pendingCount || source.carryOverCount || 0 }}
              </span>
            </div>
            <div v-if="selfMediaPreview?.slotGroups?.length" class="auto-schedule-precheck">
              <div
                v-for="group in selfMediaPreview.slotGroups"
                :key="group.platform || group.platformLabel || 'unknown'"
                class="auto-schedule-precheck-row"
                :class="{ 'is-warning': !group.enough }"
              >
                <div class="auto-schedule-precheck-main">
                  <strong>{{ group.platformLabel || selfMediaPlatformLabel(group.platform) }}</strong>
                  <span>
                    需要 {{ group.requestedCount || 0 }} / 可排 {{ group.availableSlotCount || 0 }}
                    <template v-if="group.deficitCount"> / 缺口 {{ group.deficitCount }}</template>
                    <template v-if="group.remainingWorkdayCount != null"> / 剩余工作日 {{ group.remainingWorkdayCount }}</template>
                  </span>
                  <el-tag size="small" :type="group.enough ? 'success' : 'danger'" effect="light">
                    {{ group.enough ? '可自动排期' : '可用时间不足' }}
                  </el-tag>
                </div>
                <div class="auto-schedule-precheck-slots">
                  <span
                    v-for="slot in (group.selectedSlots || []).slice(0, 3)"
                    :key="`${slot.executionAt || ''}-${slot.plannedPublishAt || ''}`"
                  >
                    处理 {{ compactDateTime(slot.executionAt) }} / 发布 {{ compactDateTime(slot.plannedPublishAt) }}
                  </span>
                  <small v-if="(group.selectedSlots || []).length > 3">
                    另 {{ (group.selectedSlots || []).length - 3 }} 个可用时间
                  </small>
                  <small v-if="!group.enough">{{ group.message || '剩余可用时间不足' }}</small>
                </div>
              </div>
            </div>
            <div class="auto-schedule-submit">
              <el-button :loading="selfMediaPreviewLoading" @click="previewSelfMediaSchedule">预检排期</el-button>
              <el-button
                v-if="selfMediaPreviewHasInsufficientSlots && isSelfMediaScheduleCurrentMonth"
                plain
                type="primary"
                @click="switchSelfMediaScheduleToNextMonth"
              >
                切到 {{ nextSelfMediaScheduleMonth }}
              </el-button>
              <el-button
                type="primary"
                :disabled="!canCreateSelfMediaSchedule"
                :loading="selfMediaScheduleCreating"
                @click="createSelfMediaSchedule"
              >
                {{ selfMediaScheduleSubmitLabel }}
              </el-button>
            </div>
            <small v-if="selfMediaScheduleCreateDisabledReason" class="auto-schedule-submit-hint">
              {{ selfMediaScheduleCreateDisabledReason }}
            </small>
          </div>
        </div>
      </div>
    </el-card>

    <el-drawer
      v-model="selfMediaDetailVisible"
      size="88%"
      :title="`${selfMediaScheduleMonth || ''} 自媒体自动排期明细`"
    >
      <div class="auto-schedule-detail-overview">
        <div
          v-for="item in selfMediaDetailOverviewItems"
          :key="item.label"
          class="auto-schedule-detail-stat"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.hint }}</small>
        </div>
      </div>
      <div v-if="selfMediaFailureSummaries.length" class="auto-schedule-failure-summary">
        <div class="auto-schedule-failure-summary-head">
          <strong>失败原因归因</strong>
          <small>按批次明细自动聚合</small>
        </div>
        <div class="auto-schedule-failure-list">
          <div
            v-for="item in selfMediaFailureSummaries"
            :key="`${item.category || ''}-${item.code || ''}`"
            class="auto-schedule-failure-item"
          >
            <div>
              <strong>{{ item.groupLabel || item.label || item.code || '未知异常' }}</strong>
              <small>{{ failureCategoryLabel(item.category) }} · {{ item.operatorAction || item.actionHint || item.firstMessage || '查看单条异常信息' }}</small>
            </div>
            <div class="auto-schedule-failure-meta">
              <el-tag size="small" :type="item.retryable ? 'success' : 'warning'" effect="light">
                {{ item.retryable ? '可重试' : '需人工确认' }}
              </el-tag>
              <span>{{ item.count || 0 }} 条</span>
            </div>
          </div>
        </div>
      </div>
      <el-collapse
        v-if="selfMediaActionPreviewMessages.length || selfMediaStatusRules.length"
        class="auto-schedule-debug-collapse auto-schedule-detail-debug"
      >
        <el-collapse-item title="批量操作和状态说明" name="detail-debug">
          <div v-if="selfMediaActionPreviewMessages.length" class="auto-schedule-action-preview">
            <span v-for="message in selfMediaActionPreviewMessages" :key="message">{{ message }}</span>
          </div>
          <div v-if="selfMediaStatusRules.length" class="auto-schedule-status-rules">
            <span v-for="rule in selfMediaStatusRules" :key="rule.status">
              <strong>{{ rule.label || scheduleStatusLabel(rule.status) }}</strong>
              <small>{{ rule.meaning }}</small>
              <em>{{ rule.operatorHint }}</em>
            </span>
          </div>
        </el-collapse-item>
      </el-collapse>
      <div class="auto-schedule-detail-head">
        <div class="auto-schedule-detail-platforms">
          <span
            v-for="item in selfMediaDetailPlatformGroups"
            :key="item.platform"
            class="auto-schedule-detail-platform"
          >
            {{ item.label }} {{ item.total }}
            <small>已生成 {{ item.generated }} / 已排期 {{ item.scheduled }}</small>
          </span>
        </div>
        <div class="auto-schedule-detail-actions">
          <el-button
            v-if="selfMediaDetailRetryableCount > 0"
            size="small"
            type="primary"
            plain
            :loading="selfMediaRetryFailedLoading"
            @click="retrySelfMediaFailedItems"
          >
            补排期/重试 {{ selfMediaDetailRetryableCount }}
          </el-button>
          <el-button
            v-if="selfMediaDetailAbnormalScheduleCount > 0"
            size="small"
            type="warning"
            plain
            :loading="selfMediaRetryAbnormalLoading"
            @click="retrySelfMediaAbnormalSchedules"
          >
            批量重新处理 {{ selfMediaDetailAbnormalScheduleCount }}
          </el-button>
          <el-button
            v-if="selfMediaDetailManualMarkableCount > 0"
            size="small"
            type="danger"
            plain
            :loading="selfMediaMarkManualLoading"
            @click="markSelfMediaAbnormalSchedulesManualRequired"
          >
            批量转人工 {{ selfMediaDetailManualMarkableCount }}
          </el-button>
          <el-button
            v-if="selfMediaDetailAbnormalScheduleCount > 0"
            size="small"
            plain
            :loading="selfMediaRescheduleNextMonthLoading"
            @click="rescheduleSelfMediaAbnormalNextMonth"
          >
            改期到下月 {{ selfMediaDetailAbnormalScheduleCount }}
          </el-button>
          <el-button
            v-if="selfMediaDetailAbnormalScheduleCount > 0"
            size="small"
            plain
            :loading="selfMediaIgnoreAbnormalLoading"
            @click="ignoreSelfMediaAbnormalSchedules"
          >
            忽略异常 {{ selfMediaDetailAbnormalScheduleCount }}
          </el-button>
          <el-tag v-if="selfMediaBatchDetail?.batch" :type="selfMediaBatchTagType(selfMediaBatchDetail.batch.status)" effect="light">
            {{ selfMediaBatchStatusLabel(selfMediaBatchDetail.batch.status) }}
          </el-tag>
        </div>
      </div>
      <div class="auto-schedule-detail-filter">
        <el-radio-group v-model="selfMediaDetailFilter" size="small">
          <el-radio-button
            v-for="item in selfMediaDetailFilterOptions"
            :key="item.value"
            :label="item.value"
          >
            {{ item.label }} {{ item.count }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <el-table
        v-loading="selfMediaDetailLoading"
        :data="filteredSelfMediaDetailItems"
        empty-text="暂无排期明细"
        class="auto-schedule-detail-table"
      >
        <el-table-column type="expand" width="42">
          <template #default="{ row }">
            <div class="auto-schedule-row-debug">
              <span v-if="selfMediaDetailOperatorHint(row)">建议：{{ selfMediaDetailOperatorHint(row) }}</span>
              <span v-if="row.claimDiagnosticMessage">处理提示：{{ row.claimDiagnosticMessage }}</span>
              <span>处理次数：{{ scheduleAttemptText(row) }}</span>
              <span>下次处理：{{ compactDateTime(row.nextAttemptAt) }}</span>
              <span v-if="row.autoCompensationAvailable">系统还会自动补救 {{ row.autoCompensationRemaining || 0 }} 次</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="计划项" min-width="300">
          <template #default="{ row }">
            <div class="detail-plan-cell">
              <strong>{{ row.generationTopic || row.articleTitle || '等待生成文章' }}</strong>
              <small>{{ articleTypeLabel(row.generationArticleType) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="轮换主体" min-width="180">
          <template #default="{ row }">
            <div class="detail-task-cell">
              <span>{{ row.subjectBrandName || row.subjectBrandId || '-' }}</span>
              <small v-if="row.sourceBrandName && row.sourceBrandName !== row.subjectBrandName">信源：{{ row.sourceBrandName }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="平台账号" width="180">
          <template #default="{ row }">
            <div class="detail-task-cell">
              <span>{{ selfMediaPlatformLabel(row.platform) }}</span>
              <small>{{ row.selfMediaAccountName || row.selfMediaAccountId || '-' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="当前进度" min-width="260">
          <template #default="{ row }">
            <div class="detail-progress-cell">
              <div class="detail-tag-row">
                <el-tag :type="generationStatusTagType(row.generationStatus)" size="small">
                  {{ generationStatusLabel(row.generationStatus) }}
                </el-tag>
                <el-tag :type="scheduleStatusTagType(row.scheduleStatus)" size="small" effect="plain">
                  {{ scheduleStatusLabel(row.scheduleStatus) }}
                </el-tag>
              </div>
              <small>{{ selfMediaDetailProgressText(row) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="文章与发布时间" min-width="300">
          <template #default="{ row }">
            <div class="detail-plan-cell">
              <el-button
                v-if="row.articleId"
                link
                type="primary"
                class="detail-article-link"
                @click="openSelfMediaArticlePreview(row.articleId)"
              >
                {{ row.articleTitle || '查看文章内容' }}
              </el-button>
              <strong v-else>文章生成后自动创建排期</strong>
              <small>
                {{ compactDateTime(row.plannedPublishAt) }}
                <template v-if="row.queueKind"> / {{ scheduleQueueLabel(row.queueKind) }}</template>
              </small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="异常" min-width="220">
          <template #default="{ row }">
            <span class="detail-error-text">
              <template v-if="row.failureGroupLabel">{{ row.failureGroupLabel }}：</template>
              {{ row.scheduleFailureMessage || row.generationErrorMessage || row.scheduleFailureCode || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canRetrySelfMediaDetailRow(row)"
              link
              type="primary"
              :loading="selfMediaScheduleRetryingId === retryingRowKey(row)"
              @click="retrySelfMediaDetailRow(row)"
            >
              重新处理
            </el-button>
            <span v-else class="detail-action-placeholder">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-drawer
      v-model="selfMediaArticlePreviewVisible"
      size="60%"
      title="文章内容"
      class="self-media-article-preview"
    >
      <div v-loading="selfMediaArticlePreviewLoading" class="article-preview-wrap">
        <template v-if="selfMediaArticlePreview">
          <div class="article-preview-head">
            <span>{{ articleTypeLabel(selfMediaArticlePreview.article.articleTypeCode) }}</span>
            <h3>{{ selfMediaArticlePreview.article.title }}</h3>
          </div>
          <img
            v-if="selfMediaArticlePreview.article.coverImageUrl"
            class="article-preview-cover"
            :src="selfMediaArticlePreview.article.coverImageUrl"
            :alt="selfMediaArticlePreview.article.title"
          />
          <div class="article-preview-body" v-html="selfMediaArticlePreviewHtml"></div>
        </template>
        <el-empty v-else-if="!selfMediaArticlePreviewLoading" description="暂无文章内容" />
      </div>
    </el-drawer>

    <el-card v-if="project" class="admin-rich-card">
      <template #header>
        <div class="keyword-group-header">
          <span>绑定拓词组</span>
          <div class="keyword-group-actions">
            <el-button v-if="canCreateKeywordGroup" type="primary" plain size="small" @click="goCreateKeywordGroup">创建拓词组</el-button>
            <el-upload
              v-if="canImportKeywordGroup"
              class="keyword-import-upload"
              :auto-upload="false"
              :show-file-list="false"
              accept=".xlsx"
              :on-change="handleKeywordImport"
            >
              <el-button type="primary" size="small" :loading="importing">导入拓词组</el-button>
            </el-upload>
          </div>
        </div>
      </template>
      <el-alert
        v-if="canImportKeywordGroup"
        type="warning"
        :closable="false"
        class="mb-3"
        title="当前项目暂无拓词组，请添加或导入拓词组后再启动项目。导入 A/B/C 数量必须与项目额度一致。"
      />
      <el-table :data="project.selectedKeywordGroups || []" border empty-text="暂无绑定拓词组">
        <el-table-column prop="name" label="拓词组名称" min-width="220" />
        <el-table-column prop="typeLabel" label="类型" min-width="120">
          <template #default="{ row }">{{ keywordGroupTypeLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="savedKeywordCount" label="总问题数" width="110" />
        <el-table-column label="A/B/C" width="160">
          <template #default="{ row }">A {{ row.savedKeywordCountA || 0 }} / B {{ row.savedKeywordCountB || 0 }} / C {{ row.savedKeywordCountC || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openKeywordQuestions(row)">{{ canEditKeywordQuestion ? '查看编辑' : '查看' }}</el-button>
            <el-button v-if="canDeleteKeywordGroup" link type="danger" @click="removeKeywordGroup(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="project" class="admin-rich-card">
      <template #header><span>内容策略配置</span></template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="核心关键词">{{ contentStrategyDisplay.coreKeywords }}</el-descriptions-item>
        <el-descriptions-item label="目标区域词">{{ contentStrategyDisplay.targetRegions }}</el-descriptions-item>
        <el-descriptions-item label="目标受众">{{ contentStrategyDisplay.targetAudience }}</el-descriptions-item>
        <el-descriptions-item label="内容调性">{{ contentStrategyDisplay.contentTone }}</el-descriptions-item>
        <el-descriptions-item label="优先写作角度">{{ contentStrategyDisplay.preferredAngles }}</el-descriptions-item>
        <el-descriptions-item label="项目定制表述" :span="2">{{ contentStrategyDisplay.customStatement }}</el-descriptions-item>
        <el-descriptions-item label="补充禁用词" :span="2">{{ contentStrategyDisplay.extraForbiddenPhrases }}</el-descriptions-item>
        <el-descriptions-item label="内容备注" :span="2">{{ contentStrategyDisplay.contentNote }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="showActivationGuide" class="admin-rich-card">
      <template #header><span>项目启动</span></template>
      <el-form label-width="120px" style="max-width: 540px">
        <el-form-item label="启动前确认">
          <el-checkbox v-model="activationConfirmed">我已阅读并确认项目基础信息</el-checkbox>
        </el-form-item>
        <el-form-item v-if="canActivateProject">
          <el-button type="primary" :loading="saving" :disabled="!activationConfirmed" @click="startProject">启动项目</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-drawer v-model="questionDrawerVisible" size="88%">
      <template #header>
        <div class="drawer-header">
          <span>拓词组问题明细</span>
          <el-button type="primary" plain :icon="Download" :loading="questionExporting" @click="exportKeywordQuestions">
              问题词导出
          </el-button>
        </div>
      </template>
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <el-radio-group v-model="questionTier" @change="loadKeywordQuestions(1)">
            <el-radio-button label="all">全部</el-radio-button>
            <el-radio-button label="A">A 类</el-radio-button>
            <el-radio-button label="B">B 类</el-radio-button>
            <el-radio-button label="C">C 类</el-radio-button>
          </el-radio-group>
          <span class="text-sm text-gray-500">编辑不会改变 A/B/C 层级数量</span>
        </div>
        <el-table v-loading="questionLoading" :data="questionPage.records" border>
          <el-table-column prop="questionCode" label="ID" width="100" />
          <el-table-column prop="questionText" label="问题文本" min-width="260" />
          <el-table-column label="场景" width="110">
            <template #default="{ row }">{{ sceneLabel(row.sceneCode) }}</template>
          </el-table-column>
          <el-table-column prop="questionTier" label="分级" width="80" />
          <el-table-column label="轮询处理" width="130">
            <template #default="{ row }">
              <el-select
                v-if="row.questionTier === 'A'"
                :model-value="questionPollingEnabled(row)"
                :disabled="!canManageQuestionPolling || questionPollingSavingIds.has(row.id)"
                :loading="questionPollingSavingIds.has(row.id)"
                @change="changeQuestionPolling(row, $event === true)"
              >
                <el-option label="轮询" :value="true" />
                <el-option label="不轮询" :value="false" />
              </el-select>
              <span v-else class="text-gray-400">不适用</span>
            </template>
          </el-table-column>
          <el-table-column label="优先级" width="90">
            <template #default="{ row }">{{ priorityLabel(row.priority) }}</template>
          </el-table-column>
          <el-table-column prop="scoreRelevance" label="商业价值" width="95" />
          <el-table-column prop="scoreIntent" label="成交距离" width="95" />
          <el-table-column prop="scoreCompetition" label="品牌绑定" width="95" />
          <el-table-column prop="scoreConversion" label="地域行业" width="95" />
          <el-table-column prop="scoreCoverage" label="一期可达" width="95" />
          <el-table-column prop="totalScore" label="总分" width="80" />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="!canEditKeywordQuestion" @click="openQuestionEdit(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="questionPage.current"
          v-model:page-size="questionPage.size"
          layout="total, sizes, prev, pager, next"
          :total="questionPage.total"
          :page-sizes="[20, 50, 100]"
          @current-change="loadKeywordQuestions"
          @size-change="() => loadKeywordQuestions(1)"
        />
      </div>
    </el-drawer>

    <el-dialog v-model="questionEditVisible" title="编辑问题" width="820px" class="admin-editor-dialog">
      <el-form class="admin-dialog-form" label-width="130px">
        <el-form-item class="is-full" label="问题文本" required>
          <el-input v-model="questionForm.questionText" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="场景">
          <el-select v-model="questionForm.sceneCode" style="width: 220px">
            <el-option label="品牌场景" value="brand" />
            <el-option label="决策场景" value="decision" />
            <el-option label="成交场景" value="deal" />
            <el-option label="对比场景" value="compare" />
            <el-option label="问答场景" value="qa" />
            <el-option label="功能场景" value="function" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="questionForm.priority" style="width: 220px">
            <el-option label="高" value="high" />
            <el-option label="中" value="medium" />
            <el-option label="低" value="low" />
          </el-select>
        </el-form-item>
        <el-form-item label="商业价值评分">
          <el-input-number v-model="questionForm.scoreRelevance" class="score-input" :min="1" :max="5" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="成交距离评分">
          <el-input-number v-model="questionForm.scoreIntent" class="score-input" :min="1" :max="5" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="品牌绑定评分">
          <el-input-number v-model="questionForm.scoreCompetition" class="score-input" :min="1" :max="5" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="地域行业评分">
          <el-input-number v-model="questionForm.scoreConversion" class="score-input" :min="1" :max="5" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="一期可达评分">
          <el-input-number v-model="questionForm.scoreCoverage" class="score-input" :min="1" :max="5" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item class="is-full" label="生成文章备注">
          <el-input
            v-model="questionForm.articleGenerationNote"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="用于后续大模型根据该问题生成文章时补充 prompt"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="questionEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="questionSaving" @click="saveQuestion">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="channelEditVisible" title="调整分发渠道额度" width="720px" class="admin-editor-dialog">
      <div class="channel-edit-note">官网、行业资讯站额度会参与文章生成调度；可填范围为客户套餐总额度减去当前已激活项目占用，保存时后端会再次校验。</div>
      <div v-loading="channelQuotaLoading" class="channel-allocation-panel">
        <template v-for="group in channelQuotaGroups" :key="group.key">
          <div v-if="group.key === 'self_media'" class="channel-group-card">
            <div class="channel-group-header">
              <span>自媒体平台</span>
              <el-tag size="small" type="info">{{ group.items.length }} 个平台</el-tag>
            </div>
            <div v-for="item in group.items" :key="item.channelCode" class="channel-row">
              <div class="channel-meta">
                <div class="channel-name">
                  <span>{{ item.channelName }}</span>
                  <el-tag v-if="isArticleGenerationChannel(item.channelCode)" size="small" type="success">生成文章渠道</el-tag>
                </div>
                <small>{{ channelQuotaText(item) }}</small>
              </div>
              <el-input-number
                v-model="channelAllocationForm[item.channelCode]"
                :min="0"
                :max="channelInputMax(item)"
                :disabled="!item.enabled"
                controls-position="right"
              />
            </div>
          </div>
          <div v-else v-for="item in group.items" :key="item.channelCode" class="channel-row">
            <div class="channel-meta">
              <div class="channel-name">
                <span>{{ item.channelName }}</span>
                <el-tag v-if="isArticleGenerationChannel(item.channelCode)" size="small" type="success">生成文章渠道</el-tag>
              </div>
              <small>{{ channelQuotaText(item) }}</small>
            </div>
            <el-input-number
              v-model="channelAllocationForm[item.channelCode]"
              :min="0"
              :max="channelInputMax(item)"
              :disabled="!item.enabled"
              controls-position="right"
            />
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="channelEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="channelSaving" @click="saveChannelAllocations">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="requirementEditVisible" title="维护客户需求" width="760px" class="admin-editor-dialog">
      <div class="requirement-editor">
        <div v-for="(_, index) in requirementForm.items" :key="index" class="requirement-edit-item">
          <div class="requirement-row-head">
            <span>需求 {{ index + 1 }}</span>
            <el-button link type="danger" :disabled="requirementForm.items.length <= 1" @click="removeRequirementItem(index)">删除</el-button>
          </div>
          <el-input
            v-model="requirementForm.items[index]"
            type="textarea"
            :rows="3"
            maxlength="100"
            show-word-limit
            resize="none"
            placeholder="请输入 10-100 字客户需求"
          />
        </div>
        <el-button class="requirement-add" plain @click="addRequirementItem">新增需求</el-button>
      </div>
      <template #footer>
        <el-button @click="requirementEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="requirementSaving" @click="saveRequirements">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Download } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import {
  deleteProject,
  deleteKeywordGroup,
  createProjectSelfMediaAutoSchedule,
  getProjectChannelAllocationQuota,
  getProjectDetail,
  getProjectSelfMediaScheduleBatch,
  getProjectSelfMediaScheduleBatchDetail,
  getProjectSelfMediaScheduleCalendarStatus,
  getProjectSelfMediaScheduleConfig,
  ignoreProjectSelfMediaScheduleBatchAbnormalSchedules,
  getKeywordGroupQuestions,
  importProjectKeywordGroup,
  markProjectSelfMediaScheduleBatchAbnormalManualRequired,
  previewProjectSelfMediaAutoSchedule,
  rescheduleProjectSelfMediaScheduleBatchAbnormalNextMonth,
  retryProjectSelfMediaScheduleBatchAbnormalSchedules,
  retryProjectSelfMediaScheduleBatchFailedItems,
  updateKeywordGroupQuestion,
  updateProjectKeywordQuestionPolling,
  updateProject,
  updateProjectChannelAllocations,
  updateProjectSelfMediaScheduleConfig,
  updateProjectStatus,
} from '@/api/project'
import type {
  ProjectSelfMediaAutoSchedulePayload,
  ProjectSelfMediaAutoScheduleResponse,
  ProjectBusinessCalendarStatus,
  ProjectSelfMediaScheduleBatch,
  ProjectSelfMediaScheduleBatchDetail,
  ProjectSelfMediaScheduleBatchDetailItem,
  ProjectSelfMediaScheduleConfig,
} from '@/api/project'
import { getContentArticleDetail, getSelfMediaAccountsByBrand, getSelfMediaAutomationOverview, retrySelfMediaPublishScheduleNow } from '@/api/content'
import type {
  ArticleDetailResponse,
  KeywordGroup,
  KeywordGroupQuestion,
  PageResult,
  Project,
  ProjectChannelAllocationItem,
  SelfMediaAccount,
  SelfMediaAutomationOverview,
} from '@/types'
import { regionDisplayFromPayload } from '@/constants/region'
import { isSelfMediaQuotaChannel } from '@/constants/distributionChannels'
import { selfMediaPlatformLabel } from '@/constants/selfMediaPlatforms'
import { nullableText } from '@/utils/form'
import { canManageQuestionPolling as canManageQuestionPollingForProject, isQuestionPollingEnabled, questionPollingLabel } from '@/utils/keywordQuestionPolling'
import MobileDashboardCompetitorPanel from './MobileDashboardCompetitorPanel.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const dictStore = useDictStore()
const PROJECT_STATUS_LABELS: Record<string, string> = {
  pending_start: '待启动',
  active: '已启动',
  paused: '已暂停',
  expired: '已过期',
}
const KEYWORD_GROUP_TYPE_LABELS: Record<string, string> = {
  brand: '品牌词',
  decision: '决策词',
  transaction: '成交词',
  comparison: '对比词',
  qa: '问答词',
  function: '功能词',
  imported: '导入问题池',
  search: '搜索词(历史)',
  location: '地域词(历史)',
  industry: '行业词(历史)',
  competitor: '竞品词(历史)',
}
const CRC32_TABLE = Array.from({ length: 256 }, (_, index) => {
  let value = index
  for (let bit = 0; bit < 8; bit += 1) {
    value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1
  }
  return value >>> 0
})
const canActivateProject = computed(() => !userStore.isSales && userStore.hasPermission('project.start'))
const canUpdateProject = computed(() => !userStore.isSales && userStore.hasPermission('project.update'))
const canManageMobileCompetitors = computed(() => !userStore.isSales && userStore.hasPermission('project.competitor.manage'))
const canViewSelfMediaSchedulePanel = computed(() => !userStore.isSales)
const canPrepareProject = computed(() => project.value?.status === 'pending_start' || project.value?.status === 'paused')
const canCreateKeywordGroup = computed(() => !!project.value && !userStore.isSales && userStore.hasPermission('keyword_group.write'))
const canDeleteKeywordGroup = computed(() => !!project.value && !userStore.isSales && canPrepareProject.value && userStore.hasPermission('keyword_group.write'))
const canEditKeywordQuestion = computed(() => !!project.value && !userStore.isSales && canPrepareProject.value && userStore.hasPermission('keyword_group.write'))
const canManageQuestionPolling = computed(() => {
  return !!project.value && canManageQuestionPollingForProject(
    project.value.status,
    userStore.hasPermission('keyword_group.write'),
  )
})
const projectId = Number(route.params.id)
const hasValidId = Number.isFinite(projectId) && projectId > 0

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const project = ref<Project | null>(null)
const questionDrawerVisible = ref(false)
const questionEditVisible = ref(false)
const channelEditVisible = ref(false)
const requirementEditVisible = ref(false)
const questionLoading = ref(false)
const questionSaving = ref(false)
const questionExporting = ref(false)
const questionPollingSavingIds = ref<Set<number>>(new Set())
const channelQuotaLoading = ref(false)
const channelSaving = ref(false)
const requirementSaving = ref(false)
const selfMediaScheduleLoading = ref(false)
const selfMediaScheduleSaving = ref(false)
const selfMediaPreviewLoading = ref(false)
const selfMediaScheduleCreating = ref(false)
const selfMediaDetailVisible = ref(false)
const selfMediaDetailLoading = ref(false)
const selfMediaRetryFailedLoading = ref(false)
const selfMediaRetryAbnormalLoading = ref(false)
const selfMediaMarkManualLoading = ref(false)
const selfMediaRescheduleNextMonthLoading = ref(false)
const selfMediaIgnoreAbnormalLoading = ref(false)
const selfMediaAutomationOverviewLoading = ref(false)
const selfMediaArticlePreviewVisible = ref(false)
const selfMediaArticlePreviewLoading = ref(false)
const selfMediaArticlePreview = ref<ArticleDetailResponse | null>(null)
const selfMediaScheduleRetryingId = ref<string | null>(null)
const currentKeywordGroup = ref<KeywordGroup | null>(null)
const currentQuestionId = ref<number | null>(null)
const channelQuotaItems = ref<ProjectChannelAllocationItem[]>([])
const allocationVersion = ref<number | null>(null)
const selfMediaAccounts = ref<SelfMediaAccount[]>([])
const selfMediaScheduleConfig = ref<ProjectSelfMediaScheduleConfig | null>(null)
const selfMediaScheduleBatch = ref<ProjectSelfMediaScheduleBatch | null>(null)
const selfMediaBatchDetail = ref<ProjectSelfMediaScheduleBatchDetail | null>(null)
const selfMediaPreview = ref<ProjectSelfMediaAutoScheduleResponse | null>(null)
const selfMediaCalendarStatus = ref<ProjectBusinessCalendarStatus | null>(null)
const selfMediaAutomationOverview = ref<SelfMediaAutomationOverview | null>(null)
const selfMediaScheduleMonth = ref(currentMonthText())
const selfMediaDetailFilter = ref('all')
const selectedSelfMediaAccountIds = ref<number[]>([])
const channelAllocationForm = reactive<Record<string, number>>({})
const selfMediaScheduleForm = reactive({
  autoScheduleEnabled: false,
  includeAdjustedWorkdays: false,
  remark: '',
})
const questionTier = ref('all')
const questionPage = reactive<PageResult<KeywordGroupQuestion>>({ records: [], total: 0, current: 1, size: 20 })
const questionForm = reactive({
  questionText: '',
  sceneCode: 'brand',
  priority: 'medium',
  scoreRelevance: 4,
  scoreIntent: 4,
  scoreCompetition: 4,
  scoreConversion: 4,
  scoreCoverage: 4,
  articleGenerationNote: '',
})
const requirementForm = reactive({
  items: [''],
})
const markdown = new MarkdownIt({ html: false, linkify: true, breaks: true })

const activationConfirmed = ref(false)
const showActivationGuide = computed(() => route.query.activate === '1' && canPrepareProject.value && canActivateProject.value)
const canImportKeywordGroup = computed(() => {
  const current = project.value
  return !!current && canEditKeywordQuestion.value && (current.selectedKeywordGroupCount || 0) === 0
})
const keywordSummary = computed(() => {
  const current = project.value
  if (!current) return '-'
  return `已选 ${current.selectedKeywordGroupCount || 0} 个，已入库 ${current.selectedKeywordSavedKeywords || 0} 条关键词（A ${current.selectedKeywordSavedKeywordsA || 0} / B ${current.selectedKeywordSavedKeywordsB || 0} / C ${current.selectedKeywordSavedKeywordsC || 0}）`
})
const keywordAllocationSummary = computed(() => {
  const current = project.value
  if (!current) return '-'
  return `总 ${current.planKeywordGroupLimit || 0}，A ${current.planKeywordGroupLimitA || 0} / B ${current.planKeywordGroupLimitB || 0} / C ${current.planKeywordGroupLimitC || 0}`
})
const channelAllocationSummary = computed(() => {
  const rows = project.value?.channelAllocations || []
  const targets = rows.filter((row) => isArticleGenerationChannel(row.channelCode))
  if (!targets.length) return '-'
  const selfMediaTotal = targets
    .filter((row) => isSelfMediaQuotaChannel(row.channelCode))
    .reduce((sum, row) => sum + (row.currentProjectAllocatedCount || 0), 0)
  const baseParts = targets
    .filter((row) => !isSelfMediaQuotaChannel(row.channelCode))
    .map((row) => `${row.channelName || row.channelCode} ${row.currentProjectAllocatedCount || 0}`)
  if (selfMediaTotal > 0) {
    baseParts.push(`自媒体平台 ${selfMediaTotal}`)
  }
  return baseParts.length ? baseParts.join(' / ') : '-'
})
const projectBaseChannelAllocations = computed(() =>
  (project.value?.channelAllocations || []).filter((row) => !isSelfMediaQuotaChannel(row.channelCode)),
)
const projectSelfMediaChannelAllocations = computed(() =>
  (project.value?.channelAllocations || []).filter((row) => isSelfMediaQuotaChannel(row.channelCode)),
)
const activeSelfMediaAccounts = computed(() =>
  selfMediaAccounts.value.filter((account) => account.status === 'active'),
)
const selectedAccountCountText = computed(() => {
  const selected = selectedSelfMediaAccountIds.value.length
  if (selected > 0) return `${selected} 个`
  return activeSelfMediaAccounts.value.length ? `全部 ${activeSelfMediaAccounts.value.length} 个` : '暂无账号'
})
const selectedSelfMediaAccounts = computed(() => {
  const selected = new Set(selectedSelfMediaAccountIds.value)
  return selected.size
    ? activeSelfMediaAccounts.value.filter((account) => selected.has(account.id))
    : activeSelfMediaAccounts.value
})
const activeSelfMediaPlatformPills = computed(() => {
  const counts = new Map<string, number>()
  selectedSelfMediaAccounts.value.forEach((account) => {
    const platform = account.platform || 'unknown'
    counts.set(platform, (counts.get(platform) || 0) + 1)
  })
  return Array.from(counts.entries()).map(([platform, count]) => ({
    platform,
    count,
    label: selfMediaPlatformLabel(platform),
  }))
})
const selectedSelfMediaPlatforms = computed(() => new Set(activeSelfMediaPlatformPills.value.map((item) => item.platform)))
const selectedSelfMediaPlatformCapabilities = computed(() => {
  const selected = selectedSelfMediaPlatforms.value
  const capabilities = selfMediaAutomationOverview.value?.platformCapabilities || []
  if (!selected.size) return []
  return capabilities.filter((item) => selected.has(item.platform || 'unknown'))
})
const selfMediaAutomationSummaryText = computed(() => {
  const local = selfMediaAutomationOverview.value?.localExecution
  const queue = selfMediaAutomationOverview.value?.queue
  if (!local || !queue) return '暂无运行数据'
  if (selfMediaAutomationBlocked.value) return '本地助手不足，可能影响自动处理'
  const abnormal = (queue.failedTotal || 0) + (queue.manualRequired || 0) + (queue.publishUnknown || 0)
  if (abnormal > 0) return `有 ${abnormal} 条异常待处理`
  return '运行正常'
})
const selfMediaAutomationHealthItems = computed(() => {
  const queue = selfMediaAutomationOverview.value?.queue
  const local = selfMediaAutomationOverview.value?.localExecution
  return [
    {
      label: '本地助手',
      value: local ? `${local.onlineAgents || 0}/${local.activeSessions || 0}` : '-',
      hint: local?.message || capacityStatusLabel(local?.capacityStatus),
    },
    {
      label: '等待处理',
      value: queue ? `${(queue.dueScheduleExecution || 0) + (queue.duePublishCheck || 0)}` : '-',
      hint: local ? `等待本地助手处理 ${local.waitingForLocalAgent || 0}` : '暂无待处理数据',
    },
    {
      label: '异常待处理',
      value: queue ? `${(queue.failedTotal || 0) + (queue.manualRequired || 0) + (queue.publishUnknown || 0)}` : '-',
      hint: queue ? `失败 ${queue.failedTotal || 0} / 需人工处理 ${queue.manualRequired || 0}` : '暂无异常数据',
    },
  ]
})
const selfMediaAutomationBlocked = computed(() => {
  const local = selfMediaAutomationOverview.value?.localExecution
  const localRequired = selectedSelfMediaPlatformCapabilities.value.some((item) => item.requiresLocalAgent)
  if (!localRequired) return false
  if (!local) return true
  return (local.estimatedCapacity || 0) <= 0 || local.capacityStatus === 'blocked'
})
const selfMediaLocalAgentSessions = computed(() => selfMediaAutomationOverview.value?.localExecution?.sessions || [])
const selfMediaAutomationCompensation = computed(() => selfMediaAutomationOverview.value?.compensation || null)
const selfMediaDetailItems = computed(() => selfMediaBatchDetail.value?.items || [])
const selfMediaFailureSummaries = computed(() => selfMediaBatchDetail.value?.failureSummaries || [])
const selfMediaStatusRules = computed(() => (selfMediaBatchDetail.value?.statusRules || []).filter((rule) =>
  ['pending', 'schedule_failed', 'publish_failed', 'manual_required', 'cancelled', 'published_url_pending', 'published_confirmed'].includes(rule.status),
))
const selfMediaActionPreview = computed(() => selfMediaBatchDetail.value?.actionPreview || null)
const selfMediaActionPreviewMessages = computed(() => {
  const preview = selfMediaActionPreview.value
  if (!preview) return []
  const messages = [...(preview.messages || [])]
  if ((preview.rescheduleNextMonthCount || 0) > 0 && preview.nextMonth) {
    messages.push(`改期到下月会影响 ${preview.rescheduleNextMonthCount} 条，目标月份 ${preview.nextMonth}。`)
  }
  return messages
})
const selfMediaDetailGeneratedCount = computed(() =>
  selfMediaDetailItems.value.filter((item) => item.generationStatus === 'success' || !!item.articleId).length,
)
const selfMediaDetailScheduledCount = computed(() =>
  selfMediaDetailItems.value.filter((item) => !!item.scheduleId && item.scheduleStatus !== 'rejected').length,
)
const selfMediaDetailWaitingCount = computed(() =>
  selfMediaDetailItems.value.filter((item) => !item.articleId && item.generationStatus !== 'failed').length,
)
const selfMediaDetailFailedGenerationCount = computed(() =>
  selfMediaDetailItems.value.filter((item) => item.generationStatus === 'failed' && !item.scheduleId).length,
)
const selfMediaDetailRejectedScheduleCount = computed(() =>
  selfMediaDetailItems.value.filter((item) => item.scheduleStatus === 'rejected' && !!item.articleId && !item.scheduleId).length,
)
const selfMediaDetailUnscheduledGeneratedCount = computed(() =>
  selfMediaDetailItems.value.filter((item) => !!item.articleId && !item.scheduleId && item.scheduleStatus !== 'rejected').length,
)
const selfMediaDetailRetryableCount = computed(() =>
  selfMediaDetailFailedGenerationCount.value + selfMediaDetailRejectedScheduleCount.value + selfMediaDetailUnscheduledGeneratedCount.value,
)
const retryableScheduleStatuses = new Set(['manual_required', 'schedule_failed', 'publish_failed'])
const manualMarkableScheduleStatuses = new Set(['schedule_failed', 'publish_failed'])
const selfMediaDetailAbnormalScheduleCount = computed(() =>
  selfMediaDetailItems.value.filter((item) => !!item.scheduleId && retryableScheduleStatuses.has(item.scheduleStatus || '')).length,
)
const selfMediaDetailManualMarkableCount = computed(() =>
  selfMediaDetailItems.value.filter((item) => !!item.scheduleId && manualMarkableScheduleStatuses.has(item.scheduleStatus || '')).length,
)
const selfMediaPreviewHasInsufficientSlots = computed(() =>
  selfMediaPreview.value?.enough === false
    || (selfMediaPreview.value?.slotGroups || []).some((group) => group.enough === false),
)
const canDecideSelfMediaScheduleCarryOver = computed(() =>
  userStore.hasPermission('content.self_media_schedule.late_start_decide'),
)
const selfMediaCapacityWarningText = computed(() => {
  const preview = selfMediaPreview.value
  if (!preview) return ''
  const required = preview.requestedCount || 0
  const available = preview.availableSlotCount || 0
  const deficit = preview.deficitCount || Math.max(required - available, 0)
  const strategy = preview.recommendedStrategy === 'carry_over'
    ? (canDecideSelfMediaScheduleCarryOver.value ? '可由交付负责人确认结转补排。' : '请联系交付负责人结转补排。')
    : '请调整账号、减少数量或选择其他月份。'
  return `目标月份剩余自动排期容量不足：应排 ${required}，可排 ${available}，缺口 ${deficit}。${strategy}`
})
const selfMediaCalendarMissing = computed(() => selfMediaCalendarStatus.value?.exists === false)
const selfMediaCalendarStatusText = computed(() => {
  const status = selfMediaCalendarStatus.value
  if (!status) return '正在读取工作日历'
  if (!status.exists) return `${status.year} 年工作日历缺失`
  const source = status.activeSource === 'runtime' ? '运行目录' : status.activeSource === 'classpath' ? '内置资源' : status.activeSource
  return `${status.year} 年工作日历已可用（${source || '未知来源'}）`
})
const isSelfMediaScheduleCurrentMonth = computed(() => selfMediaScheduleMonth.value === currentMonthText())
const nextSelfMediaScheduleMonth = computed(() => addMonthsText(selfMediaScheduleMonth.value || currentMonthText(), 1))
const selfMediaDetailCompletedCount = computed(() =>
  selfMediaDetailItems.value.filter((item) => item.scheduleStatus === 'scheduled' || item.scheduleStatus === 'published_url_pending' || item.scheduleStatus === 'published_confirmed').length,
)
const selfMediaDetailFilterOptions = computed(() => [
  { value: 'all', label: '全部', count: selfMediaDetailItems.value.length },
  { value: 'waiting', label: '待处理', count: selfMediaDetailWaitingCount.value },
  { value: 'abnormal', label: '异常', count: selfMediaDetailRetryableCount.value + selfMediaDetailAbnormalScheduleCount.value },
  { value: 'completed', label: '已完成', count: selfMediaDetailCompletedCount.value },
])
const filteredSelfMediaDetailItems = computed(() => {
  if (selfMediaDetailFilter.value === 'waiting') {
    return selfMediaDetailItems.value.filter((item) => !item.articleId && item.generationStatus !== 'failed')
  }
  if (selfMediaDetailFilter.value === 'abnormal') {
    return selfMediaDetailItems.value.filter((item) => canRetrySelfMediaDetailRow(item))
  }
  if (selfMediaDetailFilter.value === 'completed') {
    return selfMediaDetailItems.value.filter((item) => item.scheduleStatus === 'scheduled' || item.scheduleStatus === 'published_url_pending' || item.scheduleStatus === 'published_confirmed')
  }
  return selfMediaDetailItems.value
})
const selfMediaArticlePreviewHtml = computed(() => {
  const versions = selfMediaArticlePreview.value?.versions || []
  const latest = [...versions].sort((a, b) => (b.versionNo || 0) - (a.versionNo || 0))[0]
  return markdown.render(latest?.contentMarkdown || '')
})
const selfMediaDetailOverviewItems = computed(() => {
  const batch = selfMediaBatchDetail.value?.batch
  return [
    {
      label: '计划总量',
      value: batch?.plannedCount ?? selfMediaDetailItems.value.length,
      hint: '本月准备生成并排期的文章',
    },
    {
      label: '已生成',
      value: selfMediaDetailGeneratedCount.value,
      hint: '文章已生成，等待或已经创建排期',
    },
    {
      label: '已排期',
      value: batch?.createdCount ?? selfMediaDetailScheduledCount.value,
      hint: '已安排发布时间，等待系统处理',
    },
    {
      label: '待处理',
      value: selfMediaDetailWaitingCount.value,
      hint: '后台生成链路仍在推进',
    },
    {
      label: '失败',
      value: batch?.rejectedCount ?? 0,
      hint: '单条失败不会中断整批',
    },
  ]
})
const selfMediaDetailPlatformGroups = computed(() => {
  const groups = new Map<string, { platform: string; label: string; total: number; generated: number; scheduled: number }>()
  selfMediaDetailItems.value.forEach((item) => {
    const platform = item.platform || 'unknown'
    const current = groups.get(platform) || {
      platform,
      label: selfMediaPlatformLabel(platform),
      total: 0,
      generated: 0,
      scheduled: 0,
    }
    current.total += 1
    if (item.generationStatus === 'success' || item.articleId) current.generated += 1
    if (item.scheduleId && item.scheduleStatus !== 'rejected') current.scheduled += 1
    groups.set(platform, current)
  })
  return Array.from(groups.values())
})
const canCreateSelfMediaSchedule = computed(() => {
  if (!canUpdateProject.value || !selfMediaScheduleForm.autoScheduleEnabled) return false
  if (!selfMediaScheduleMonth.value) return false
  if (!activeSelfMediaAccounts.value.length) return false
  if (selfMediaCalendarMissing.value) return false
  const status = selfMediaScheduleBatch.value?.status
  if (status === 'processing') return false
  if (selfMediaPreviewHasInsufficientSlots.value) return canDecideSelfMediaScheduleCarryOver.value
  return true
})
const isSelfMediaScheduleSupplementMode = computed(() => ['created', 'partial_failed'].includes(selfMediaScheduleBatch.value?.status || ''))
const selfMediaScheduleSubmitLabel = computed(() => isSelfMediaScheduleSupplementMode.value ? '补充排期' : '创建排期')
const selfMediaScheduleCreateDisabledReason = computed(() => {
  if (canCreateSelfMediaSchedule.value) {
    if (selfMediaScheduleForm.autoScheduleEnabled && !selfMediaScheduleConfig.value?.autoScheduleEnabled) {
      return '点击创建时会先保存当前自动排期配置。'
    }
    return ''
  }
  if (!canUpdateProject.value) return '当前账号缺少项目更新权限，无法创建自动排期。'
  if (!selfMediaScheduleForm.autoScheduleEnabled) return '请先开启自动创建排期。'
  if (!selfMediaScheduleMonth.value) return '请选择目标月份。'
  if (!activeSelfMediaAccounts.value.length) return '当前品牌暂无启用的自媒体账号。'
  if (selfMediaCalendarMissing.value) return '目标年份工作日历缺失，暂不能创建排期。'
  const status = selfMediaScheduleBatch.value?.status
  if (status === 'processing') return '当前月份批次正在处理中，请完成后再补充排期。'
  if (selfMediaPreviewHasInsufficientSlots.value && !canDecideSelfMediaScheduleCarryOver.value) {
    return '目标月份可用排期容量不足，请联系交付负责人确认结转补排。'
  }
  return ''
})
const channelQuotaGroups = computed(() => {
  const base = channelQuotaItems.value.filter((item) => !isSelfMediaQuotaChannel(item.channelCode))
  const selfMedia = channelQuotaItems.value.filter((item) => isSelfMediaQuotaChannel(item.channelCode))
  return [
    { key: 'base', items: base },
    ...(selfMedia.length ? [{ key: 'self_media', items: selfMedia }] : []),
  ]
})
const projectBasicInfoItems = computed(() => {
  const current = project.value
  return [
    { label: '项目名称', value: current?.projectName || '-' },
    { label: '项目别名', value: current?.projectAliases || '-' },
    { label: '客户名称', value: current?.companyName || '-' },
    { label: '品牌名称', value: current?.brandName || '-' },
    { label: '拓词组', value: keywordSummary.value },
    { label: '问题额度', value: keywordAllocationSummary.value },
    { label: '分发渠道额度', value: channelAllocationSummary.value },
    { label: '所在地区', value: regionText(current) },
    { label: '启动日期', value: current?.activatedAt || '-' },
    { label: '有效期至', value: current?.endDate || '-' },
    { label: '主目标', value: current?.primaryGoal || '-', wide: true },
  ]
})

const contentStrategyDisplay = computed(() => {
  const current = project.value
  const keywordGroupWords = collectKeywordGroupWords(current?.selectedKeywordGroups || [])
  const projectRegions = joinArray(current?.targetRegions)
  const fallbackRegions = keywordGroupWords.area.length ? keywordGroupWords.area.join('、') : regionText(current)
  const fallbackCoreKeywords = keywordGroupWords.core.length ? keywordGroupWords.core.join('、') : ''
  return {
    coreKeywords: displayText(current?.coreKeywords, fallbackCoreKeywords),
    targetRegions: projectRegions !== '-' ? projectRegions : fallbackRegions,
    targetAudience: displayText(current?.targetAudience),
    contentTone: displayText(current?.contentTone),
    preferredAngles: displayArray(current?.preferredAngles, keywordGroupWords.angles),
    customStatement: displayText(current?.customStatement),
    extraForbiddenPhrases: displayArray(current?.extraForbiddenPhrases),
    contentNote: displayText(current?.contentNote),
  }
})

function regionText(p?: Project | null) {
  if (!p) return '-'
  return regionDisplayFromPayload(p) || '-'
}

function projectStatusLabel(status?: string | null) {
  if (!status) return '-'
  return PROJECT_STATUS_LABELS[status] || dictStore.label('project_status', status) || status
}

function projectStatusClass(status?: string | null) {
  if (status === 'active') return 'is-success'
  if (status === 'paused' || status === 'pending_start') return 'is-warning'
  if (status === 'expired') return 'is-danger'
  return 'is-muted'
}

function capacityStatusLabel(status?: string | null) {
  if (status === 'healthy') return '本地助手可正常处理'
  if (status === 'pressure') return '本地助手较忙'
  if (status === 'saturated') return '本地助手已忙满'
  if (status === 'blocked') return '暂无可用本地助手'
  return '暂无运行数据'
}

function keywordExpectedCounts(current: Project) {
  return {
    a: current.planKeywordGroupLimitA ?? current.planKeywordGroupLimit ?? 0,
    b: current.planKeywordGroupLimitB ?? 0,
    c: current.planKeywordGroupLimitC ?? 0,
  }
}

function keywordActualCounts(current: Project) {
  return {
    a: current.selectedKeywordSavedKeywordsA || 0,
    b: current.selectedKeywordSavedKeywordsB || 0,
    c: current.selectedKeywordSavedKeywordsC || 0,
  }
}

function validateKeywordGroupCountsBeforeStart(current: Project) {
  const expected = keywordExpectedCounts(current)
  const actual = keywordActualCounts(current)
  if ((current.selectedKeywordGroupCount || 0) <= 0) {
    ElMessage.warning('项目启动前必须至少绑定一个拓词组')
    return false
  }
  if (actual.a !== expected.a || actual.b !== expected.b || actual.c !== expected.c) {
    ElMessage.warning(`拓词组问题数量需与项目额度一致：额度 A/B/C=${expected.a}/${expected.b}/${expected.c}，当前 A/B/C=${actual.a}/${actual.b}/${actual.c}`)
    return false
  }
  return true
}

function goCreateKeywordGroup() {
  router.push({ name: 'LayeredKeywordGroupManage', query: { projectId: String(projectId) } })
}

function joinArray(value?: string | string[] | null) {
  if (Array.isArray(value)) {
    return value.length ? value.join('、') : '-'
  }
  if (!value) {
    return '-'
  }
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) && parsed.length ? parsed.join('、') : '-'
  } catch {
    return value
  }
}

function displayText(value?: string | null, fallback = '') {
  const text = String(value || '').trim()
  if (text) return text
  const fallbackText = fallback.trim()
  return fallbackText || '-'
}

function displayArray(value?: string | string[] | null, fallback: string[] = []) {
  const text = joinArray(value)
  if (text !== '-') return text
  return fallback.length ? fallback.join('、') : '-'
}

function collectKeywordGroupWords(groups: KeywordGroup[]) {
  const area: string[] = []
  const core: string[] = []
  const angles: string[] = []
  for (const group of groups) {
    const columns = group.columns
    if (!columns) continue
    appendWordTexts(area, columns.areaWords)
    appendWordTexts(area, columns.regionWords)
    appendWordTexts(core, columns.coreWords)
    appendWordTexts(core, columns.coreWordsA)
    appendWordTexts(core, columns.coreWordsB)
    appendWordTexts(angles, columns.prefixWords)
    appendWordTexts(angles, columns.industryWords)
    appendWordTexts(angles, columns.suffixWords)
    appendWordTexts(angles, columns.compareWords)
  }
  return {
    area: uniqueWords(area),
    core: uniqueWords(core),
    angles: uniqueWords(angles),
  }
}

function appendWordTexts(target: string[], words?: Array<{ wordText?: string | null }> | null) {
  for (const word of words || []) {
    const text = String(word?.wordText || '').trim()
    if (text) target.push(text)
  }
}

function uniqueWords(words: string[]) {
  return words.filter((word, index, arr) => arr.indexOf(word) === index)
}

function isArticleGenerationChannel(channelCode?: string | null) {
  return channelCode === 'official_site' || channelCode === 'industry_site' || channelCode?.startsWith('self_media:')
}

function keywordGroupTypeLabel(row: KeywordGroup) {
  if (row.typeLabel) return row.typeLabel
  if (!row.type) return '-'
  return KEYWORD_GROUP_TYPE_LABELS[row.type] || row.type
}

function periodLabel(value?: string | null) {
  const labels: Record<string, string> = {
    day: '日',
    week: '周',
    month: '月',
    total: '总量',
    none: '-',
  }
  return value ? (labels[value] || value) : '-'
}

function currentMonthText() {
  const now = new Date()
  const month = `${now.getMonth() + 1}`.padStart(2, '0')
  return `${now.getFullYear()}-${month}`
}

function addMonthsText(value: string, offset: number) {
  const [yearText, monthText] = value.split('-')
  const year = Number(yearText)
  const month = Number(monthText)
  if (!year || !month) return currentMonthText()
  const date = new Date(year, month - 1 + offset, 1)
  return `${date.getFullYear()}-${`${date.getMonth() + 1}`.padStart(2, '0')}`
}

function createIdempotencyKey(prefix: string) {
  const random = typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${prefix}-${random}`
}

function selfMediaAccountLabel(account: SelfMediaAccount) {
  return `${selfMediaPlatformLabel(account.platform)} / ${account.accountName || account.platformAccountId || account.id}`
}

function articleTypeLabel(value?: string | null) {
  const labels: Record<string, string> = {
    faq: 'FAQ',
    scenario_content: '场景内容',
    industry_article: '行业文章',
    stage_advice: '阶段建议',
    buying_guide: '选择指南',
    comparison: '对比评测',
    cost_analysis: '费用解析',
    pitfall_guide: '避坑指南',
    social_note: '经验笔记',
    news_brief: '资讯简讯',
    forum_discussion: '讨论帖',
  }
  return value ? (labels[value] || value) : '-'
}

function selfMediaBatchStatusLabel(status?: string | null) {
  const labels: Record<string, string> = {
    processing: '处理中',
    created: '已创建',
    partial_failed: '部分失败',
    failed: '失败',
    skipped: '已跳过',
    cancelled: '已取消',
  }
  return status ? (labels[status] || status) : '未创建'
}

function selfMediaBatchTagType(status?: string | null) {
  if (status === 'created') return 'success'
  if (status === 'processing') return 'primary'
  if (status === 'partial_failed') return 'warning'
  if (status === 'failed') return 'danger'
  if (status === 'skipped') return 'info'
  return 'info'
}

function generationStatusLabel(status?: string | null) {
  const labels: Record<string, string> = {
    pending: '待生成',
    queued: '排队中',
    running: '生成中',
    success: '已生成',
    failed: '生成失败',
  }
  return status ? (labels[status] || status) : '未开始'
}

function generationStatusTagType(status?: string | null) {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'running') return 'primary'
  return 'info'
}

function scheduleStatusLabel(status?: string | null) {
  const labels: Record<string, string> = {
    rejected: '排期失败',
    pending: '等待处理',
    filling: '助手填充中',
    filled_verified: '填充已核验',
    scheduling: '正在预约平台发布时间',
    scheduled: '已定时',
    publish_due: '到点待核验',
    checking_publish_result: '发布结果核验中',
    publish_unknown: '发布待确认',
    published_url_pending: '已发布待补链接',
    published_confirmed: '已确认发布',
    schedule_failed: '定时失败',
    publish_failed: '发布失败',
    manual_required: '需人工处理',
    routed_to_semi_auto: '已转半自动',
    cancel_pending_platform: '取消待平台处理',
    cancelled: '已取消',
  }
  return status ? (labels[status] || status) : '未排期'
}

function scheduleStatusTagType(status?: string | null) {
  if (status === 'scheduled' || status === 'published_confirmed') return 'success'
  if (status === 'rejected' || status === 'schedule_failed' || status === 'publish_failed' || status === 'manual_required') return 'danger'
  if (status === 'filling' || status === 'filled_verified' || status === 'scheduling' || status === 'checking_publish_result') return 'primary'
  if (status === 'published_url_pending') return 'warning'
  if (status === 'cancelled' || status === 'routed_to_semi_auto') return 'info'
  return status ? 'warning' : 'info'
}

function scheduleQueueLabel(value?: string | null) {
  const labels: Record<string, string> = {
    schedule_execution: '按计划处理',
    publish_result_check: '发布回查',
  }
  return value ? (labels[value] || value) : '-'
}

function failureCategoryLabel(category?: string | null) {
  if (category === 'generation') return '文章生成'
  if (category === 'schedule_rejected') return '排期创建'
  if (category === 'schedule_abnormal') return '执行异常'
  return '异常'
}

function selfMediaMinutesText(value?: number | null) {
  if (value === null || value === undefined) return '-'
  return `${value} 分钟`
}

function scheduleAttemptText(row: ProjectSelfMediaScheduleBatchDetailItem) {
  const attempt = row.attemptCount ?? 0
  const max = row.maxAttempts ?? 0
  return max > 0 ? `${attempt}/${max}` : `${attempt}`
}

function selfMediaDetailProgressText(row: ProjectSelfMediaScheduleBatchDetailItem) {
  if (row.scheduleStatus === 'rejected') {
    return row.scheduleFailureMessage || row.scheduleFailureCode || '排期创建失败'
  }
  if (row.scheduleFailureMessage || row.scheduleFailureCode) {
    return row.scheduleFailureMessage || row.scheduleFailureCode || '排期执行失败'
  }
  if (row.generationErrorMessage) {
    return row.generationErrorMessage
  }
  if (row.scheduleStatus) {
    const next = compactDateTime(row.nextAttemptAt)
    return next === '-' ? '排期已创建，等待系统继续处理' : `排期已创建，下次处理 ${next}`
  }
  if (row.generationStatus === 'success' || row.articleId) {
    return '文章已生成，通常 1 分钟内会自动排期；超过 2 分钟仍未排期，可点击补排期/重试'
  }
  if (row.generationStatus === 'running') {
    return '文章正在生成，完成后会自动进入排期创建'
  }
  if (row.generationStatus === 'queued' || row.generationStatus === 'pending') {
    return '文章生成任务已入队，后台会自动继续处理'
  }
  return '等待后台生成文章并创建排期'
}

function selfMediaDetailOperatorHint(row: ProjectSelfMediaScheduleBatchDetailItem) {
  if (!row.scheduleId && row.articleId && row.scheduleStatus !== 'rejected') {
    return '文章已生成，通常 1 分钟内会自动排期；超过 2 分钟仍未排期，可点击补排期/重试。'
  }
  return row.operatorActionHint || ''
}

function retryingRowKey(row: ProjectSelfMediaScheduleBatchDetailItem) {
  return row.scheduleId ? `schedule-${row.scheduleId}` : `generation-${row.generationTaskId || row.articleId || row.selfMediaAccountId || 'unknown'}`
}

function canRetrySelfMediaDetailRow(row: ProjectSelfMediaScheduleBatchDetailItem) {
  if (row.scheduleId && retryableScheduleStatuses.has(row.scheduleStatus || '')) return true
  if (!row.scheduleId && row.scheduleStatus === 'rejected' && row.articleId) return true
  if (!row.scheduleId && row.articleId) return true
  if (!row.scheduleId && row.generationStatus === 'failed') return true
  return false
}

function compactDateTime(value?: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function applySelfMediaScheduleConfig(config?: ProjectSelfMediaScheduleConfig | null) {
  selfMediaScheduleConfig.value = config || null
  selfMediaScheduleForm.autoScheduleEnabled = !!config?.autoScheduleEnabled
  selfMediaScheduleForm.includeAdjustedWorkdays = !!config?.includeAdjustedWorkdays
  selfMediaScheduleForm.remark = config?.remark || ''
}

function selfMediaSchedulePayload(extra?: Partial<ProjectSelfMediaAutoSchedulePayload>): ProjectSelfMediaAutoSchedulePayload {
  return {
    targetMonth: selfMediaScheduleMonth.value,
    selfMediaAccountIds: selectedSelfMediaAccountIds.value.length ? selectedSelfMediaAccountIds.value : undefined,
    includeAdjustedWorkdays: selfMediaScheduleForm.includeAdjustedWorkdays,
    supplementExistingBatch: isSelfMediaScheduleSupplementMode.value || undefined,
    ...extra,
  }
}

async function loadSelfMediaScheduleBatch() {
  if (!project.value || !selfMediaScheduleMonth.value) return
  try {
    const { data } = await getProjectSelfMediaScheduleBatch(project.value.id, selfMediaScheduleMonth.value)
    selfMediaScheduleBatch.value = data.data || null
    selfMediaBatchDetail.value = null
  } catch {
    selfMediaScheduleBatch.value = null
    selfMediaBatchDetail.value = null
  }
}

async function loadSelfMediaScheduleCalendarStatus() {
  if (!project.value || !selfMediaScheduleMonth.value) {
    selfMediaCalendarStatus.value = null
    return
  }
  try {
    const { data } = await getProjectSelfMediaScheduleCalendarStatus(project.value.id, selfMediaScheduleMonth.value)
    selfMediaCalendarStatus.value = data.data || null
  } catch {
    selfMediaCalendarStatus.value = null
  }
}

async function handleSelfMediaScheduleMonthChange() {
  selfMediaPreview.value = null
  await Promise.all([loadSelfMediaScheduleBatch(), loadSelfMediaScheduleCalendarStatus()])
}

async function switchSelfMediaScheduleToNextMonth() {
  selfMediaScheduleMonth.value = nextSelfMediaScheduleMonth.value
  await handleSelfMediaScheduleMonthChange()
}

async function openSelfMediaBatchDetail() {
  const current = project.value
  if (!current || !selfMediaScheduleMonth.value || !selfMediaScheduleBatch.value) return
  selfMediaDetailVisible.value = true
  selfMediaDetailLoading.value = true
  try {
    const { data } = await getProjectSelfMediaScheduleBatchDetail(current.id, selfMediaScheduleMonth.value)
    selfMediaBatchDetail.value = data.data || null
  } finally {
    selfMediaDetailLoading.value = false
  }
}

async function retrySelfMediaFailedItems() {
  const current = project.value
  if (!current || !selfMediaScheduleMonth.value || selfMediaRetryFailedLoading.value) return
  const count = selfMediaActionPreview.value?.retryFailedCount ?? selfMediaDetailRetryableCount.value
  try {
    await ElMessageBox.confirm(
      `系统会处理 ${count} 条内容：已生成但未排期的会补上发布时间，生成失败的会重新生成；已经成功安排的内容不会重复处理。是否继续？`,
      '补排期/重试',
      { type: 'warning', confirmButtonText: '继续处理', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  selfMediaRetryFailedLoading.value = true
  try {
    const { data } = await retryProjectSelfMediaScheduleBatchFailedItems(current.id, selfMediaScheduleMonth.value)
    selfMediaBatchDetail.value = data.data || null
    selfMediaScheduleBatch.value = data.data?.batch || selfMediaScheduleBatch.value
    ElMessage.success('已提交处理，后台会补上发布时间或重新生成文章')
  } finally {
    selfMediaRetryFailedLoading.value = false
  }
}

async function retrySelfMediaAbnormalSchedules() {
  const current = project.value
  if (!current || !selfMediaScheduleMonth.value || selfMediaRetryAbnormalLoading.value) return
  const count = selfMediaActionPreview.value?.retryAbnormalCount ?? selfMediaDetailAbnormalScheduleCount.value
  try {
    await ElMessageBox.confirm(
      `确认批量重新处理 ${count} 条异常内容？系统会重新安排合适的处理时间。`,
      '批量重新处理',
      { type: 'warning', confirmButtonText: '重新处理', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  selfMediaRetryAbnormalLoading.value = true
  try {
    const { data } = await retryProjectSelfMediaScheduleBatchAbnormalSchedules(current.id, selfMediaScheduleMonth.value)
    selfMediaBatchDetail.value = data.data || null
    selfMediaScheduleBatch.value = data.data?.batch || selfMediaScheduleBatch.value
    ElMessage.success('异常内容已重新安排处理')
  } finally {
    selfMediaRetryAbnormalLoading.value = false
  }
}

async function markSelfMediaAbnormalSchedulesManualRequired() {
  const current = project.value
  if (!current || !selfMediaScheduleMonth.value || selfMediaMarkManualLoading.value) return
  const count = selfMediaActionPreview.value?.manualCount ?? selfMediaDetailManualMarkableCount.value
  try {
    await ElMessageBox.confirm(
      `确认将 ${count} 条异常内容转为人工处理？转人工后系统不会再自动处理这些内容。`,
      '批量转人工处理',
      { type: 'warning', confirmButtonText: '转人工', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  selfMediaMarkManualLoading.value = true
  try {
    const { data } = await markProjectSelfMediaScheduleBatchAbnormalManualRequired(current.id, selfMediaScheduleMonth.value)
    selfMediaBatchDetail.value = data.data || null
    selfMediaScheduleBatch.value = data.data?.batch || selfMediaScheduleBatch.value
    ElMessage.success('异常内容已转为人工处理')
  } finally {
    selfMediaMarkManualLoading.value = false
  }
}

async function rescheduleSelfMediaAbnormalNextMonth() {
  const current = project.value
  if (!current || !selfMediaScheduleMonth.value || selfMediaRescheduleNextMonthLoading.value) return
  const count = selfMediaActionPreview.value?.rescheduleNextMonthCount ?? selfMediaDetailAbnormalScheduleCount.value
  const nextMonth = selfMediaActionPreview.value?.nextMonth || '下月'
  try {
    await ElMessageBox.confirm(
      `确认将 ${count} 条异常内容改到 ${nextMonth}？系统会重新安排可用的处理时间。`,
      '批量改期到下月',
      { type: 'warning', confirmButtonText: '改期到下月', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  selfMediaRescheduleNextMonthLoading.value = true
  try {
    const { data } = await rescheduleProjectSelfMediaScheduleBatchAbnormalNextMonth(current.id, selfMediaScheduleMonth.value)
    selfMediaBatchDetail.value = data.data || null
    selfMediaScheduleBatch.value = data.data?.batch || selfMediaScheduleBatch.value
    ElMessage.success('异常内容已改到下月可用时间')
  } finally {
    selfMediaRescheduleNextMonthLoading.value = false
  }
}

async function ignoreSelfMediaAbnormalSchedules() {
  const current = project.value
  if (!current || !selfMediaScheduleMonth.value || selfMediaIgnoreAbnormalLoading.value) return
  const count = selfMediaActionPreview.value?.ignoreCount ?? selfMediaDetailAbnormalScheduleCount.value
  try {
    await ElMessageBox.confirm(
      `确认忽略 ${count} 条异常内容？忽略后系统不会继续自动处理这些内容。`,
      '批量忽略异常',
      { type: 'warning', confirmButtonText: '忽略异常', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  selfMediaIgnoreAbnormalLoading.value = true
  try {
    const { data } = await ignoreProjectSelfMediaScheduleBatchAbnormalSchedules(current.id, selfMediaScheduleMonth.value)
    selfMediaBatchDetail.value = data.data || null
    selfMediaScheduleBatch.value = data.data?.batch || selfMediaScheduleBatch.value
    ElMessage.success('异常内容已忽略')
  } finally {
    selfMediaIgnoreAbnormalLoading.value = false
  }
}

async function retrySelfMediaDetailRow(row: ProjectSelfMediaScheduleBatchDetailItem) {
  const current = project.value
  if (!current || !selfMediaScheduleMonth.value || !canRetrySelfMediaDetailRow(row)) return
  const key = retryingRowKey(row)
  selfMediaScheduleRetryingId.value = key
  try {
    if (row.scheduleId) {
      await ElMessageBox.confirm(
        `确认重新处理这条内容？系统会重新安排合适的处理时间。`,
        '重新处理',
        { type: 'warning', confirmButtonText: '重新处理', cancelButtonText: '返回' },
      )
      await retrySelfMediaPublishScheduleNow(row.scheduleId)
      ElMessage.success('已重新安排处理')
      await Promise.all([loadSelfMediaScheduleBatch(), openSelfMediaBatchDetail()])
      return
    }
    await retrySelfMediaFailedItems()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      throw error
    }
  } finally {
    selfMediaScheduleRetryingId.value = null
  }
}

async function openSelfMediaArticlePreview(articleId?: number | null) {
  if (!articleId) return
  selfMediaArticlePreviewVisible.value = true
  selfMediaArticlePreviewLoading.value = true
  selfMediaArticlePreview.value = null
  try {
    const { data } = await getContentArticleDetail(articleId)
    selfMediaArticlePreview.value = data.data
  } finally {
    selfMediaArticlePreviewLoading.value = false
  }
}

async function loadSelfMediaAutomationOverview() {
  selfMediaAutomationOverviewLoading.value = true
  try {
    const { data } = await getSelfMediaAutomationOverview()
    selfMediaAutomationOverview.value = data.data
  } catch (error) {
    selfMediaAutomationOverview.value = null
    console.warn('Failed to load self media automation overview', error)
  } finally {
    selfMediaAutomationOverviewLoading.value = false
  }
}

async function loadSelfMediaSchedulePanel() {
  const current = project.value
  if (!current || !canViewSelfMediaSchedulePanel.value) {
    resetSelfMediaSchedulePanel()
    return
  }
  selfMediaScheduleLoading.value = true
  try {
    const [configRes, accountsRes] = await Promise.all([
      getProjectSelfMediaScheduleConfig(current.id),
      current.brandId ? getSelfMediaAccountsByBrand(current.brandId) : Promise.resolve({ data: { data: [] as SelfMediaAccount[] } }),
    ])
    applySelfMediaScheduleConfig(configRes.data.data)
    selfMediaAccounts.value = accountsRes.data.data || []
    await Promise.all([loadSelfMediaScheduleBatch(), loadSelfMediaScheduleCalendarStatus(), loadSelfMediaAutomationOverview()])
    selfMediaPreview.value = null
  } finally {
    selfMediaScheduleLoading.value = false
  }
}

function resetSelfMediaSchedulePanel() {
  applySelfMediaScheduleConfig(null)
  selfMediaAccounts.value = []
  selfMediaScheduleBatch.value = null
  selfMediaBatchDetail.value = null
  selfMediaPreview.value = null
  selfMediaCalendarStatus.value = null
  selfMediaAutomationOverview.value = null
}

async function saveSelfMediaScheduleConfig() {
  await persistSelfMediaScheduleConfig(true)
}

async function persistSelfMediaScheduleConfig(showSuccess: boolean) {
  const current = project.value
  if (!current) return
  selfMediaScheduleSaving.value = true
  try {
    const { data } = await updateProjectSelfMediaScheduleConfig(current.id, {
      autoScheduleEnabled: selfMediaScheduleForm.autoScheduleEnabled,
      includeAdjustedWorkdays: selfMediaScheduleForm.includeAdjustedWorkdays,
      remark: selfMediaScheduleForm.remark || null,
    })
    applySelfMediaScheduleConfig(data.data)
    if (showSuccess) {
      ElMessage.success('自媒体自动排期配置已保存')
    }
  } finally {
    selfMediaScheduleSaving.value = false
  }
}

async function previewSelfMediaSchedule() {
  const current = project.value
  if (!current || !selfMediaScheduleMonth.value) return
  selfMediaPreviewLoading.value = true
  try {
    const { data } = await previewProjectSelfMediaAutoSchedule(current.id, selfMediaSchedulePayload())
    selfMediaPreview.value = data.data
  } finally {
    selfMediaPreviewLoading.value = false
  }
}

async function runSelfMediaSchedulePrecheck() {
  const current = project.value
  if (!current || !selfMediaScheduleMonth.value) return null
  selfMediaPreviewLoading.value = true
  try {
    const { data } = await previewProjectSelfMediaAutoSchedule(current.id, selfMediaSchedulePayload())
    selfMediaPreview.value = data.data
    return data.data
  } finally {
    selfMediaPreviewLoading.value = false
  }
}

async function createSelfMediaSchedule() {
  const current = project.value
  if (!current || !canCreateSelfMediaSchedule.value) return
  const supplementMode = isSelfMediaScheduleSupplementMode.value
  try {
    if (selfMediaScheduleForm.autoScheduleEnabled && !selfMediaScheduleConfig.value?.autoScheduleEnabled) {
      await persistSelfMediaScheduleConfig(false)
    }
    const preview = await runSelfMediaSchedulePrecheck()
    if (preview?.enough === false || (preview?.slotGroups || []).some((group) => group.enough === false)) {
      const required = preview?.requestedCount || 0
      const available = preview?.availableSlotCount || 0
      const deficit = preview?.deficitCount || Math.max(required - available, 0)
      if (!canDecideSelfMediaScheduleCarryOver.value) {
        ElMessage.warning('目标月份剩余自动排期容量不足，请联系交付负责人结转补排')
        return
      }
      const { value } = await ElMessageBox.prompt(
        `目标月份剩余自动排期容量不足：应排 ${required}，可排 ${available}，缺口 ${deficit}。是否由交付负责人确认本月先排 ${available} 条，剩余 ${deficit} 条结转到下月？`,
        '结转补排确认',
        {
          type: 'warning',
          confirmButtonText: '确认结转',
          cancelButtonText: '取消',
          inputType: 'textarea',
          inputPlaceholder: '请填写结转原因，例如：客户晚启动，本月有效工作日不足',
          inputValidator: (input: string) => input.trim().length > 0 || '请填写结转原因',
        },
      )
      const decisionReason = String(value || '').trim()
      selfMediaScheduleCreating.value = true
      const idempotencyPrefix = supplementMode
        ? `project-self-media-${current.id}-${selfMediaScheduleMonth.value}-supplement-carry-over`
        : `project-self-media-${current.id}-${selfMediaScheduleMonth.value}-carry-over`
      const idempotencyKey = createIdempotencyKey(idempotencyPrefix)
      const { data } = await createProjectSelfMediaAutoSchedule(
        current.id,
        selfMediaSchedulePayload({
          decisionStrategy: 'carry_over',
          decisionReason,
        }),
        idempotencyKey,
      )
      selfMediaPreview.value = data.data
      ElMessage.success(`自动排期已提交，已结转 ${data.data?.carryOverCount || deficit} 条到 ${data.data?.carryOverTargetMonth || '下月'}`)
      await loadSelfMediaScheduleBatch()
      return
    }
    await ElMessageBox.confirm(
      supplementMode
        ? `确认补充 ${selfMediaScheduleMonth.value} 的自媒体自动排期？系统只会按当前剩余额度追加生成文章和发布时间，不会重建已有排期。`
        : `确认创建 ${selfMediaScheduleMonth.value} 的自媒体自动排期？系统会按剩余额度生成文章，并安排发布时间。`,
      supplementMode ? '补充自动排期' : '创建自动排期',
      { type: 'warning', confirmButtonText: supplementMode ? '确认补排' : '确认创建', cancelButtonText: '取消' },
    )
    selfMediaScheduleCreating.value = true
    const idempotencyKey = createIdempotencyKey(
      supplementMode
        ? `project-self-media-${current.id}-${selfMediaScheduleMonth.value}-supplement`
        : `project-self-media-${current.id}-${selfMediaScheduleMonth.value}`,
    )
    const { data } = await createProjectSelfMediaAutoSchedule(current.id, selfMediaSchedulePayload(), idempotencyKey)
    selfMediaPreview.value = data.data
    const carryOverText = data.data?.carryOverCount ? `，已结转 ${data.data.carryOverCount} 条到 ${data.data.carryOverTargetMonth || '下月'}` : ''
    ElMessage.success(`${supplementMode ? '补充排期' : '自动排期'}已提交，后台正在生成文章并安排发布时间${carryOverText}`)
    await loadSelfMediaScheduleBatch()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  } finally {
    selfMediaScheduleCreating.value = false
  }
}

function channelInputMax(item: ProjectChannelAllocationItem) {
  return Math.max(item.inputMax ?? item.remainingCount ?? 0, 0)
}

function channelQuotaText(item: ProjectChannelAllocationItem) {
  if (!item.enabled) {
    return '套餐未启用'
  }
  return `可分配 ${channelInputMax(item)} / 套餐总额 ${item.quotaLimit || 0}（${periodLabel(item.periodType)}）`
}

function resetChannelAllocationForm() {
  for (const key of Object.keys(channelAllocationForm)) {
    delete channelAllocationForm[key]
  }
}

async function openChannelAllocationEdit() {
  const current = project.value
  if (!current?.companyId) {
    ElMessage.warning('项目信息缺少客户，无法调整渠道额度')
    return
  }
  channelEditVisible.value = true
  channelQuotaLoading.value = true
  resetChannelAllocationForm()
  try {
    const { data } = await getProjectChannelAllocationQuota({
      companyId: current.companyId,
      excludeProjectId: current.id,
    })
    channelQuotaItems.value = data.data.items || []
    allocationVersion.value = data.data.allocationVersion
    for (const item of channelQuotaItems.value) {
      channelAllocationForm[item.channelCode] = item.currentProjectAllocatedCount || 0
    }
  } finally {
    channelQuotaLoading.value = false
  }
}

function projectUpdatePayload(current: Project) {
  const allocationRows = channelQuotaItems.value.length
    ? channelQuotaItems.value.map((item) => ({
        channelCode: item.channelCode,
        allocatedCount: channelAllocationForm[item.channelCode] || 0,
      }))
    : (current.channelAllocations || []).map((item) => ({
        channelCode: item.channelCode,
        allocatedCount: item.currentProjectAllocatedCount || 0,
      }))
  return {
    provinceCode: current.provinceCode,
    provinceName: current.provinceName,
    cityCode: current.cityCode,
    cityName: current.cityName,
    districtCode: current.districtCode,
    districtName: current.districtName,
    projectName: current.projectName,
    projectAliases: nullableText(current.projectAliases),
    companyId: current.companyId,
    brandId: current.brandId,
    keywordGroupIds: current.selectedKeywordGroupIds || [],
    keywordGroupLimitA: current.planKeywordGroupLimitA ?? current.planKeywordGroupLimit ?? 0,
    keywordGroupLimitB: current.planKeywordGroupLimitB ?? 0,
    keywordGroupLimitC: current.planKeywordGroupLimitC ?? 0,
    allocationVersion: allocationVersion.value ?? current.allocationVersion,
    channelAllocations: allocationRows,
    deliveryMode: current.deliveryMode || 'managed',
    primaryGoal: nullableText(current.primaryGoal),
    customerRequirements: current.customerRequirements || [],
    targetRegions: parseStringArray(current.targetRegions),
    coreKeywords: current.coreKeywords,
    targetAudience: nullableText(current.targetAudience),
    customStatement: nullableText(current.customStatement),
    contentTone: nullableText(current.contentTone),
    preferredAngles: parseStringArray(current.preferredAngles),
    extraForbiddenPhrases: parseStringArray(current.extraForbiddenPhrases),
    contentNote: nullableText(current.contentNote),
    remark: nullableText(current.remark),
  }
}

function normalizeRequirementInputs(requirements?: string[] | null) {
  const normalized = (requirements || [])
    .map((item) => String(item || '').trim())
    .filter(Boolean)
  return normalized.length ? normalized : ['']
}

function openRequirementEdit() {
  requirementForm.items = normalizeRequirementInputs(project.value?.customerRequirements)
  requirementEditVisible.value = true
}

function addRequirementItem() {
  if (requirementForm.items.length >= 20) {
    ElMessage.warning('客户需求最多录入 20 条')
    return
  }
  requirementForm.items.push('')
}

function removeRequirementItem(index: number) {
  if (requirementForm.items.length <= 1) {
    return
  }
  requirementForm.items.splice(index, 1)
}

function buildRequirementPayload() {
  const items = requirementForm.items.map((item) => item.trim()).filter(Boolean)
  if (items.length > 20) {
    ElMessage.warning('客户需求最多录入 20 条')
    return null
  }
  for (const item of items) {
    const length = Array.from(item).length
    if (length < 10 || length > 100) {
      ElMessage.warning('每条客户需求字数需在 10-100 之间')
      return null
    }
  }
  return items
}

async function saveRequirements() {
  const current = project.value
  if (!current) return
  const customerRequirements = buildRequirementPayload()
  if (!customerRequirements) {
    return
  }
  requirementSaving.value = true
  try {
    await updateProject(current.id, {
      ...projectUpdatePayload(current),
      customerRequirements,
    })
    ElMessage.success('客户需求已保存')
    requirementEditVisible.value = false
    await load()
  } finally {
    requirementSaving.value = false
  }
}

function parseStringArray(value?: string | string[] | null) {
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter((item, index, arr) => item.length > 0 && arr.indexOf(item) === index)
  }
  if (!value) {
    return [] as string[]
  }
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed)
      ? parsed.map((item) => String(item).trim()).filter((item, index, arr) => item.length > 0 && arr.indexOf(item) === index)
      : []
  } catch {
    return String(value)
      .split(/[,，、;；\n\r]+/)
      .map((item) => item.trim())
      .filter((item, index, arr) => item.length > 0 && arr.indexOf(item) === index)
  }
}

async function saveChannelAllocations() {
  const current = project.value
  if (!current) return
  channelSaving.value = true
  try {
    await updateProjectChannelAllocations(current.id, {
      allocationVersion: allocationVersion.value ?? current.allocationVersion,
      channelAllocations: channelQuotaItems.value.map((item) => ({
        channelCode: item.channelCode,
        allocatedCount: channelAllocationForm[item.channelCode] || 0,
      })),
    })
    ElMessage.success('渠道额度已保存')
    channelEditVisible.value = false
    await load()
  } finally {
    channelSaving.value = false
  }
}

function goReports() {
  router.push(`/admin/projects/${projectId}/reports`)
}

function goMobileDashboardAdmin() {
  router.push(`/admin/projects/${projectId}/mobile-dashboard`)
}

function goBaselineReport() {
  router.push(`/admin/projects/${projectId}/baseline-report`)
}

async function load() {
  loading.value = true
  try {
    const { data } = await getProjectDetail(projectId)
    project.value = data.data
    activationConfirmed.value = false
    await loadSelfMediaSchedulePanel()
  } catch {
    project.value = null
  } finally {
    loading.value = false
  }
}

async function startProject() {
  if (!canActivateProject.value) {
    ElMessage.warning('当前账号无项目启动权限')
    return
  }
  saving.value = true
  try {
    await ElMessageBox.confirm(
      '确认启动该项目？',
      '项目启动确认',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
    const current = project.value
    if (!current) {
      ElMessage.error('项目信息不存在')
      return
    }
    if (current.status !== 'pending_start' && current.status !== 'paused') {
      ElMessage.info('当前项目不可启动')
      return
    }
    if (!activationConfirmed.value) {
      ElMessage.warning('请先勾选“已阅读并确认项目基础信息”后再激活')
      return
    }
    if (!validateKeywordGroupCountsBeforeStart(current)) {
      return
    }
    await updateProjectStatus(projectId, 'active')
    ElMessage.success('项目已启动')
    await load()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  } finally {
    saving.value = false
  }
}

async function handleKeywordImport(file: UploadFile) {
  if (!project.value || !file.raw) return
  importing.value = true
  try {
    await importProjectKeywordGroup(project.value.id, file.raw)
    ElMessage.success('拓词组导入成功')
    await load()
  } finally {
    importing.value = false
  }
}

async function removeKeywordGroup(row: KeywordGroup) {
  if (!project.value || !canDeleteKeywordGroup.value) {
    ElMessage.warning('当前项目状态不可删除拓词组')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除拓词组「${row.name}」？删除后可重新创建或导入拓词组。`,
      '删除拓词组确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
    await deleteKeywordGroup(row.id)
    ElMessage.success('拓词组已删除')
    if (currentKeywordGroup.value?.id === row.id) {
      questionDrawerVisible.value = false
      currentKeywordGroup.value = null
    }
    await load()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  }
}

async function openKeywordQuestions(row: KeywordGroup) {
  currentKeywordGroup.value = row
  questionTier.value = 'all'
  questionDrawerVisible.value = true
  await loadKeywordQuestions(1)
}

async function loadKeywordQuestions(page = questionPage.current) {
  if (!currentKeywordGroup.value) return
  questionLoading.value = true
  try {
    const { data } = await getKeywordGroupQuestions(currentKeywordGroup.value.id, {
      current: page,
      size: questionPage.size,
      tier: questionTier.value,
    })
    Object.assign(questionPage, data.data)
  } finally {
    questionLoading.value = false
  }
}

async function exportKeywordQuestions() {
  if (!currentKeywordGroup.value) return
  questionExporting.value = true
  try {
    const records = await fetchAllKeywordQuestionsForExport()
    if (!records.length) {
      ElMessage.warning('暂无可导出的问题词')
      return
    }
    const blob = toQuestionXlsx(records)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = questionExportFileName()
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${records.length} 条问题词`)
  } finally {
    questionExporting.value = false
  }
}

async function fetchAllKeywordQuestionsForExport() {
  if (!currentKeywordGroup.value) return []
  const pageSize = 1000
  const params = {
    current: 1,
    size: pageSize,
    tier: questionTier.value,
  }
  const firstResponse = await getKeywordGroupQuestions(currentKeywordGroup.value.id, params)
  const firstPage = firstResponse.data.data
  const records = [...(firstPage.records || [])]
  const total = Number(firstPage.total || records.length)
  const effectiveSize = Number(firstPage.size || pageSize)
  const pageCount = Math.ceil(total / Math.max(effectiveSize, 1))

  for (let current = 2; current <= pageCount; current += 1) {
    const { data } = await getKeywordGroupQuestions(currentKeywordGroup.value.id, {
      ...params,
      current,
      size: effectiveSize,
    })
    const pageRecords = data.data.records || []
    records.push(...pageRecords)
    if (!pageRecords.length) break
  }
  return records
}

function toQuestionXlsx(records: KeywordGroupQuestion[]) {
  const worksheetRows = questionExportRows(records)
  const sheetXml = buildWorksheetXml(worksheetRows)
  const files = [
    {
      name: '[Content_Types].xml',
      content: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        + '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        + '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        + '<Default Extension="xml" ContentType="application/xml"/>'
        + '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
        + '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
        + '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
        + '</Types>',
    },
    {
      name: '_rels/.rels',
      content: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        + '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
        + '</Relationships>',
    },
    {
      name: 'xl/workbook.xml',
      content: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        + '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
        + '<sheets><sheet name="问题词" sheetId="1" r:id="rId1"/></sheets>'
        + '</workbook>',
    },
    {
      name: 'xl/_rels/workbook.xml.rels',
      content: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        + '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>'
        + '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
        + '</Relationships>',
    },
    {
      name: 'xl/styles.xml',
      content: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        + '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
        + '<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>'
        + '<fills count="1"><fill><patternFill patternType="none"/></fill></fills>'
        + '<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>'
        + '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
        + '<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>'
        + '</styleSheet>',
    },
    {
      name: 'xl/worksheets/sheet1.xml',
      content: sheetXml,
    },
  ]
  const zipBytes = createZip(files)
  return new Blob([zipBytes], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
}

function questionExportRows(records: KeywordGroupQuestion[]) {
  const headers = ['ID', '问题文本', '场景', '分级', '轮询处理', '优先级', '商业价值', '成交距离', '品牌绑定', '地域行业', '一期可达', '总分', '生成文章备注']
  const rows = records.map((row) => [
    row.questionCode,
    row.questionText,
    sceneLabel(row.sceneCode),
    row.questionTier,
    questionPollingLabel(row),
    priorityLabel(row.priority),
    row.scoreRelevance,
    row.scoreIntent,
    row.scoreCompetition,
    row.scoreConversion,
    row.scoreCoverage,
    row.totalScore,
    row.articleGenerationNote,
  ])
  return [headers, ...rows]
}

function buildWorksheetXml(rows: unknown[][]) {
  const columnWidths = [14, 56, 14, 10, 12, 10, 12, 12, 12, 12, 12, 10, 44]
  const colsXml = columnWidths
    .map((width, index) => `<col min="${index + 1}" max="${index + 1}" width="${width}" customWidth="1"/>`)
    .join('')
  const rowsXml = rows
    .map((row, rowIndex) => {
      const rowNumber = rowIndex + 1
      const cells = row
        .map((value, colIndex) => {
          const ref = `${columnName(colIndex + 1)}${rowNumber}`
          const style = rowIndex === 0 ? ' s="1"' : ''
          return `<c r="${ref}" t="inlineStr"${style}><is><t xml:space="preserve">${escapeXml(cellText(value))}</t></is></c>`
        })
        .join('')
      return `<row r="${rowNumber}">${cells}</row>`
    })
    .join('')
  return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    + '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
    + `<cols>${colsXml}</cols>`
    + `<sheetData>${rowsXml}</sheetData>`
    + '</worksheet>'
}

function cellText(value: unknown) {
  return value === null || value === undefined ? '' : String(value)
}

function escapeXml(value: string) {
  return value
    .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F]/g, '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function columnName(index: number) {
  let value = index
  let name = ''
  while (value > 0) {
    value -= 1
    name = String.fromCharCode(65 + (value % 26)) + name
    value = Math.floor(value / 26)
  }
  return name
}

function createZip(files: Array<{ name: string; content: string }>) {
  const encoder = new TextEncoder()
  const chunks: Uint8Array[] = []
  const centralChunks: Uint8Array[] = []
  let offset = 0

  for (const file of files) {
    const nameBytes = encoder.encode(file.name)
    const contentBytes = encoder.encode(file.content)
    const crc = crc32(contentBytes)
    const localHeader = new Uint8Array(30 + nameBytes.length)
    writeUint32(localHeader, 0, 0x04034b50)
    writeUint16(localHeader, 4, 20)
    writeUint16(localHeader, 6, 0x0800)
    writeUint16(localHeader, 8, 0)
    writeUint32(localHeader, 14, crc)
    writeUint32(localHeader, 18, contentBytes.length)
    writeUint32(localHeader, 22, contentBytes.length)
    writeUint16(localHeader, 26, nameBytes.length)
    localHeader.set(nameBytes, 30)

    const centralHeader = new Uint8Array(46 + nameBytes.length)
    writeUint32(centralHeader, 0, 0x02014b50)
    writeUint16(centralHeader, 4, 20)
    writeUint16(centralHeader, 6, 20)
    writeUint16(centralHeader, 8, 0x0800)
    writeUint16(centralHeader, 10, 0)
    writeUint32(centralHeader, 16, crc)
    writeUint32(centralHeader, 20, contentBytes.length)
    writeUint32(centralHeader, 24, contentBytes.length)
    writeUint16(centralHeader, 28, nameBytes.length)
    writeUint32(centralHeader, 42, offset)
    centralHeader.set(nameBytes, 46)

    chunks.push(localHeader, contentBytes)
    centralChunks.push(centralHeader)
    offset += localHeader.length + contentBytes.length
  }

  const centralSize = centralChunks.reduce((sum, chunk) => sum + chunk.length, 0)
  const endHeader = new Uint8Array(22)
  writeUint32(endHeader, 0, 0x06054b50)
  writeUint16(endHeader, 8, files.length)
  writeUint16(endHeader, 10, files.length)
  writeUint32(endHeader, 12, centralSize)
  writeUint32(endHeader, 16, offset)
  return concatBytes([...chunks, ...centralChunks, endHeader])
}

function writeUint16(target: Uint8Array, offset: number, value: number) {
  target[offset] = value & 0xff
  target[offset + 1] = (value >>> 8) & 0xff
}

function writeUint32(target: Uint8Array, offset: number, value: number) {
  target[offset] = value & 0xff
  target[offset + 1] = (value >>> 8) & 0xff
  target[offset + 2] = (value >>> 16) & 0xff
  target[offset + 3] = (value >>> 24) & 0xff
}

function concatBytes(chunks: Uint8Array[]) {
  const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0)
  const result = new Uint8Array(total)
  let offset = 0
  for (const chunk of chunks) {
    result.set(chunk, offset)
    offset += chunk.length
  }
  return result
}

function crc32(bytes: Uint8Array) {
  let crc = 0xffffffff
  for (const byte of bytes) {
    crc = (crc >>> 8) ^ CRC32_TABLE[(crc ^ byte) & 0xff]
  }
  return (crc ^ 0xffffffff) >>> 0
}

function questionExportFileName() {
  const groupName = sanitizeFileName(currentKeywordGroup.value?.name || '拓词组')
  const tier = questionTier.value === 'all' ? '全部' : `${questionTier.value}类`
  return `${groupName}-问题词-${tier}.xlsx`
}

function sanitizeFileName(value: string) {
  return value.trim().replace(/[\\/:*?"<>|]/g, '_') || '拓词组'
}

function openQuestionEdit(row: KeywordGroupQuestion) {
  if (!canEditKeywordQuestion.value) return
  currentQuestionId.value = row.id
  questionForm.questionText = row.questionText
  questionForm.sceneCode = row.sceneCode || 'brand'
  questionForm.priority = row.priority || 'medium'
  questionForm.scoreRelevance = Number(row.scoreRelevance || 4)
  questionForm.scoreIntent = Number(row.scoreIntent || 4)
  questionForm.scoreCompetition = Number(row.scoreCompetition || 4)
  questionForm.scoreConversion = Number(row.scoreConversion || 4)
  questionForm.scoreCoverage = Number(row.scoreCoverage || 4)
  questionForm.articleGenerationNote = row.articleGenerationNote || ''
  questionEditVisible.value = true
}

function questionPollingEnabled(row: KeywordGroupQuestion) {
  return isQuestionPollingEnabled(row)
}

function setQuestionPollingSaving(questionId: number, saving: boolean) {
  const next = new Set(questionPollingSavingIds.value)
  if (saving) next.add(questionId)
  else next.delete(questionId)
  questionPollingSavingIds.value = next
}

async function changeQuestionPolling(row: KeywordGroupQuestion, pollingEnabled: boolean) {
  if (!canManageQuestionPolling.value || row.questionTier !== 'A' || questionPollingSavingIds.value.has(row.id)) return
  if (questionPollingEnabled(row) === pollingEnabled || !currentKeywordGroup.value) return

  if (!pollingEnabled) {
    try {
      await ElMessageBox.confirm(
        '关闭后，手机数据看板会立即移除该问题；已经规划的轮询批次仍会继续执行。是否确认关闭？',
        '确认关闭轮询处理',
        { type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '取消' },
      )
    } catch (error) {
      if (error === 'cancel' || error === 'close') return
      throw error
    }
  }

  setQuestionPollingSaving(row.id, true)
  try {
    const { data } = await updateProjectKeywordQuestionPolling(
      projectId,
      currentKeywordGroup.value.id,
      row.id,
      pollingEnabled,
    )
    Object.assign(row, data.data)
    ElMessage.success(pollingEnabled ? '已开启轮询处理' : '已关闭轮询处理')
  } catch (error) {
    ElMessage.error('轮询处理更新失败，请重试')
  } finally {
    setQuestionPollingSaving(row.id, false)
  }
}

async function saveQuestion() {
  if (!currentKeywordGroup.value || !currentQuestionId.value) return
  questionSaving.value = true
  try {
    await updateKeywordGroupQuestion(currentKeywordGroup.value.id, currentQuestionId.value, questionForm)
    ElMessage.success('问题已保存')
    questionEditVisible.value = false
    await loadKeywordQuestions()
  } finally {
    questionSaving.value = false
  }
}

function sceneLabel(value?: string | null) {
  const labels: Record<string, string> = {
    brand: '品牌场景',
    decision: '决策场景',
    deal: '成交场景',
    compare: '对比场景',
    qa: '问答场景',
    function: '功能场景',
  }
  return value ? (labels[value] || value) : '-'
}

function priorityLabel(value?: string | null) {
  const labels: Record<string, string> = { high: '高', medium: '中', low: '低' }
  return value ? (labels[value] || value) : '-'
}

async function removeCurrentProject() {
  if (!project.value) return
  try {
    await ElMessageBox.confirm(
      `确认删除项目「${project.value.projectName}」？该操作不可撤销。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
    await deleteProject(projectId)
    ElMessage.success('删除成功')
    router.push('/admin/projects')
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  }
}

onMounted(() => {
  if (!hasValidId) {
    ElMessage.error('项目参数无效')
    return
  }
  dictStore.ensureLoaded()
  load()
})
</script>

<style scoped>
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 32px;
}

.project-hero-kpis {
  gap: 12px;
}

.project-hero-kpi {
  position: relative;
  overflow: hidden;
  border-color: rgba(148, 163, 184, 0.22);
}

.project-hero-kpi::after {
  content: "";
  position: absolute;
  right: 14px;
  bottom: -18px;
  width: 74px;
  height: 74px;
  border-radius: 999px;
  opacity: 0.16;
}

.project-hero-kpi span,
.project-hero-kpi strong {
  position: relative;
  z-index: 1;
}

.project-hero-kpi--keyword {
  background: linear-gradient(135deg, rgba(239, 246, 255, 0.96), rgba(255, 255, 255, 0.9));
}

.project-hero-kpi--keyword::after {
  background: #2563eb;
}

.project-hero-kpi--keyword strong {
  color: #2563eb;
}

.project-hero-kpi--quota {
  background: linear-gradient(135deg, rgba(245, 243, 255, 0.96), rgba(255, 255, 255, 0.9));
}

.project-hero-kpi--quota::after {
  background: #8b5cf6;
}

.project-hero-kpi--quota strong {
  color: #6d28d9;
}

.project-hero-kpi--channel {
  background: linear-gradient(135deg, rgba(236, 253, 245, 0.96), rgba(255, 255, 255, 0.9));
}

.project-hero-kpi--channel::after {
  background: #10b981;
}

.project-hero-kpi--channel strong {
  color: #059669;
}

.score-input {
  width: 220px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.keyword-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.keyword-group-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}
.keyword-import-upload {
  display: inline-flex;
}
.keyword-import-upload :deep(.el-upload) {
  display: inline-flex;
}
.requirement-view-list {
  display: grid;
  gap: 10px;
}
.requirement-view-item {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 10px;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafafa;
}
.requirement-view-index {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #ecf5ff;
  color: #409eff;
  font-size: 12px;
  font-weight: 600;
}
.requirement-view-text {
  line-height: 1.6;
  color: #303133;
  word-break: break-word;
}
.requirement-editor {
  display: grid;
  gap: 12px;
}
.requirement-edit-item {
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fafafa;
}
.requirement-row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.requirement-add {
  width: 100%;
  border-style: dashed;
}
.channel-name {
  display: flex;
  align-items: center;
  gap: 8px;
}
.channel-edit-note {
  margin-bottom: 12px;
  font-size: 13px;
  color: #606266;
}
.channel-allocation-panel {
  display: grid;
  gap: 12px;
}
.quota-channel-groups {
  margin-top: 12px;
}
.quota-group-title,
.channel-group-header {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.auto-schedule-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.auto-schedule-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.auto-schedule-shell {
  display: grid;
  gap: 16px;
}
.auto-schedule-overview {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  overflow: hidden;
  background: linear-gradient(135deg, #f8fbff 0%, #f3f9f5 100%);
  border: 1px solid #dbeafe;
  border-radius: 12px;
}
.auto-schedule-overview-main {
  display: grid;
  gap: 4px;
  min-width: 0;
}
.auto-schedule-kicker {
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}
.auto-schedule-overview-main strong {
  color: #0f172a;
  font-size: 22px;
  line-height: 1.25;
}
.auto-schedule-overview-main small {
  color: #64748b;
  font-size: 13px;
}
.auto-schedule-overview-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  min-width: 260px;
}
.auto-schedule-overview-meta span {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  color: #1e293b;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
}
.auto-schedule-layout {
  display: grid;
  grid-template-columns: minmax(300px, 0.82fr) minmax(520px, 1.18fr);
  gap: 16px;
  align-items: start;
}
.auto-schedule-config,
.auto-schedule-runner {
  display: grid;
  gap: 14px;
  min-width: 0;
  padding: 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}
.auto-schedule-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}
.auto-schedule-panel-head span {
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
}
.auto-schedule-panel-head small {
  overflow: hidden;
  color: #94a3b8;
  font-size: 12px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.auto-schedule-form {
  display: grid;
  gap: 2px;
}
.auto-schedule-form :deep(.el-form-item) {
  margin-bottom: 12px;
}
.auto-schedule-form :deep(.el-form-item__label) {
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
}
.auto-schedule-switch-row {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #1e293b;
  font-weight: 600;
}
.auto-schedule-rule-card {
  display: grid;
  gap: 4px;
  padding: 12px;
  color: #475569;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 13px;
}
.auto-schedule-rule-card strong {
  color: #0f172a;
  font-size: 14px;
}
.auto-schedule-filters {
  display: grid;
  gap: 10px;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}
.auto-schedule-toolbar {
  display: grid;
  grid-template-columns: 172px minmax(260px, 420px) minmax(120px, 1fr);
  align-items: stretch;
  gap: 10px;
}
.auto-schedule-filter-field {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 8px 10px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}
.auto-schedule-filter-field > span {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}
.auto-schedule-month,
.auto-schedule-accounts {
  width: 100%;
}
.auto-schedule-filter-field :deep(.el-input__wrapper),
.auto-schedule-filter-field :deep(.el-select__wrapper) {
  min-height: 28px;
  padding: 0;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
}
.auto-schedule-filter-field :deep(.el-input__inner),
.auto-schedule-filter-field :deep(.el-select__placeholder),
.auto-schedule-filter-field :deep(.el-select__selected-item) {
  color: #0f172a;
  font-size: 14px;
  font-weight: 600;
}
.auto-schedule-filter-note {
  display: grid;
  align-content: center;
  justify-items: end;
  min-width: 0;
  padding: 8px 2px;
  color: #64748b;
}
.auto-schedule-filter-note strong {
  color: #0f172a;
  font-size: 15px;
  line-height: 1.2;
}
.auto-schedule-filter-note small {
  margin-top: 3px;
  color: #94a3b8;
  font-size: 12px;
}
.auto-schedule-platforms {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 28px;
}
.auto-schedule-platform-pill,
.auto-schedule-platform-empty {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
}
.auto-schedule-platform-pill {
  color: #1d4ed8;
  background: #fff;
  border: 1px solid #bfdbfe;
  font-weight: 600;
}
.auto-schedule-platform-empty {
  color: #94a3b8;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
}
.auto-schedule-health {
  display: grid;
  gap: 10px;
  padding: 12px;
  background: #fbfdff;
  border: 1px solid #dbeafe;
  border-radius: 10px;
}
.auto-schedule-health-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.auto-schedule-health-head div {
  display: grid;
  gap: 2px;
}
.auto-schedule-health-head strong {
  color: #0f172a;
  font-size: 14px;
}
.auto-schedule-health-head small {
  color: #94a3b8;
  font-size: 12px;
}
.auto-schedule-health-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}
.auto-schedule-health-grid.is-compact {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.auto-schedule-health-item {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 10px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}
.auto-schedule-health-item span,
.auto-schedule-health-item small {
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
}
.auto-schedule-health-item strong {
  color: #0f172a;
  font-size: 20px;
  line-height: 1.1;
  word-break: break-word;
}
.auto-schedule-health-alert {
  margin: 0;
}
.auto-schedule-debug-collapse {
  border: 0;
}
.auto-schedule-debug-collapse :deep(.el-collapse-item__header) {
  height: 34px;
  color: #2563eb;
  background: transparent;
  border: 0;
  font-size: 12px;
}
.auto-schedule-debug-collapse :deep(.el-collapse-item__wrap) {
  background: transparent;
  border: 0;
}
.auto-schedule-debug-collapse :deep(.el-collapse-item__content) {
  display: grid;
  gap: 8px;
  padding-bottom: 0;
}
.auto-schedule-health-note {
  display: grid;
  gap: 4px;
  padding: 10px;
  color: #475569;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}
.auto-schedule-health-note strong {
  color: #0f172a;
  font-size: 13px;
}
.auto-schedule-health-note span,
.auto-schedule-health-note small {
  font-size: 12px;
  line-height: 1.45;
}
.auto-schedule-agent-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.auto-schedule-agent-item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
  padding: 8px;
  background: #fff;
  border: 1px solid #dbeafe;
  border-radius: 8px;
}
.auto-schedule-agent-item.is-offline {
  border-color: #fed7aa;
  background: #fff7ed;
}
.auto-schedule-agent-item strong,
.auto-schedule-agent-item small {
  font-size: 12px;
}
.auto-schedule-agent-item strong {
  color: #0f172a;
}
.auto-schedule-agent-item small {
  color: #64748b;
}
.auto-schedule-capabilities {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.auto-schedule-capability {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
  padding: 6px 8px;
  color: #475569;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 12px;
  font-style: normal;
}
.auto-schedule-capability.is-warning {
  background: #fff7ed;
  border-color: #fed7aa;
}
.auto-schedule-capability strong {
  color: #0f172a;
  font-size: 12px;
}
.auto-schedule-capability small,
.auto-schedule-capability em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
}
.auto-schedule-mode-guide {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}
.auto-schedule-mode-guide div {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 8px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.auto-schedule-mode-guide strong {
  color: #0f172a;
  font-size: 13px;
}
.auto-schedule-mode-guide small {
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}
.auto-schedule-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.auto-schedule-stat {
  display: grid;
  gap: 6px;
  min-height: 78px;
  padding: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}
.auto-schedule-stat span {
  font-size: 12px;
  color: #64748b;
}
.auto-schedule-stat strong {
  min-width: 0;
  color: #0f172a;
  font-size: 24px;
  line-height: 1.2;
  word-break: break-word;
}
.auto-schedule-batch {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  min-height: 36px;
  padding: 10px 12px;
  color: #64748b;
  background: #fbfdff;
  border: 1px dashed #dbeafe;
  border-radius: 10px;
  font-size: 13px;
}
.auto-schedule-alert {
  margin-top: 0;
}
.auto-schedule-demand-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
}
.auto-schedule-demand-summary span {
  padding: 3px 7px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}
.auto-schedule-precheck {
  display: grid;
  gap: 8px;
}
.auto-schedule-precheck-row {
  display: grid;
  gap: 8px;
  padding: 10px 12px;
  background: #f7fdf9;
  border: 1px solid #cdebd8;
  border-radius: 10px;
}
.auto-schedule-precheck-row.is-warning {
  background: #fff8f6;
  border-color: #f3c7bd;
}
.auto-schedule-precheck-main,
.auto-schedule-precheck-slots {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}
.auto-schedule-precheck-main strong {
  color: #0f172a;
}
.auto-schedule-precheck-main span,
.auto-schedule-precheck-slots span,
.auto-schedule-precheck-slots small {
  color: #64748b;
  font-size: 12px;
}
.auto-schedule-precheck-slots span {
  padding: 3px 7px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
}
.auto-schedule-submit {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 2px;
}
.auto-schedule-submit-hint {
  display: block;
  text-align: right;
  color: #94a3b8;
  font-size: 12px;
  line-height: 18px;
}
.auto-schedule-detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px 14px;
  margin-bottom: 12px;
  color: #606266;
  font-size: 13px;
}
.auto-schedule-detail-overview {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}
.auto-schedule-detail-stat {
  display: grid;
  gap: 6px;
  min-height: 96px;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fbff 0%, #ffffff 100%);
}
.auto-schedule-detail-stat span {
  color: #64748b;
  font-size: 13px;
}
.auto-schedule-detail-stat strong {
  color: #0f172a;
  font-size: 26px;
  line-height: 1;
}
.auto-schedule-detail-stat small {
  color: #94a3b8;
  line-height: 1.5;
}
.auto-schedule-failure-summary {
  display: grid;
  gap: 10px;
  margin-bottom: 14px;
  padding: 12px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 12px;
}
.auto-schedule-action-preview {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
  padding: 12px;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 12px;
}
.auto-schedule-detail-debug {
  margin-bottom: 12px;
  padding: 0 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}
.auto-schedule-action-preview div {
  display: grid;
  gap: 3px;
  min-width: 160px;
}
.auto-schedule-action-preview strong {
  color: #075985;
  font-size: 14px;
}
.auto-schedule-action-preview small,
.auto-schedule-action-preview span {
  color: #0369a1;
  font-size: 12px;
  line-height: 1.5;
}
.auto-schedule-action-preview span {
  padding: 4px 8px;
  background: #fff;
  border: 1px solid #bae6fd;
  border-radius: 999px;
}
.auto-schedule-status-rules {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 14px;
}
.auto-schedule-status-rules span {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 9px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.auto-schedule-status-rules strong {
  color: #0f172a;
  font-size: 13px;
}
.auto-schedule-status-rules small,
.auto-schedule-status-rules em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  line-height: 1.45;
}
.auto-schedule-row-debug {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px;
  color: #64748b;
  background: #f8fafc;
  border-radius: 8px;
  font-size: 12px;
}
.auto-schedule-row-debug span {
  padding: 3px 7px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
}
.auto-schedule-failure-summary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.auto-schedule-failure-summary-head strong {
  color: #9a3412;
  font-size: 14px;
}
.auto-schedule-failure-summary-head small {
  color: #c2410c;
  font-size: 12px;
}
.auto-schedule-failure-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.auto-schedule-failure-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
  padding: 10px;
  background: #fff;
  border: 1px solid #fed7aa;
  border-radius: 8px;
}
.auto-schedule-failure-item div:first-child {
  display: grid;
  gap: 4px;
  min-width: 0;
}
.auto-schedule-failure-item strong {
  color: #0f172a;
  font-size: 13px;
}
.auto-schedule-failure-item small {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
}
.auto-schedule-failure-meta {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  gap: 6px;
  color: #9a3412;
  font-size: 12px;
  font-weight: 700;
}
.auto-schedule-detail-platforms {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}
.auto-schedule-detail-platform {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  color: #1d4ed8;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  font-weight: 600;
}
.auto-schedule-detail-platform small {
  color: #64748b;
  font-weight: 500;
}
.auto-schedule-detail-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.auto-schedule-detail-filter {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
.auto-schedule-detail-table {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
}
.detail-plan-cell,
.detail-progress-cell {
  display: grid;
  gap: 6px;
  min-width: 0;
}
.detail-plan-cell strong {
  min-width: 0;
  overflow: hidden;
  color: #111827;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.detail-article-link {
  justify-content: flex-start;
  height: auto;
  max-width: 100%;
  padding: 0;
  color: #111827;
  font-weight: 600;
  line-height: 1.4;
  text-align: left;
  white-space: normal;
}
.detail-plan-cell small,
.detail-progress-cell small {
  color: #64748b;
  line-height: 1.5;
}
.detail-tag-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.detail-task-cell {
  display: grid;
  gap: 3px;
  min-width: 0;
}
.detail-task-cell small {
  color: #909399;
}
.detail-article-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.detail-error-text {
  color: #c45656;
  word-break: break-word;
}
.detail-action-placeholder {
  color: #c0c4cc;
}
.article-preview-wrap {
  min-height: 320px;
}
.article-preview-head {
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid #e5e7eb;
}
.article-preview-head span {
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
}
.article-preview-head h3 {
  margin: 6px 0 0;
  color: #0f172a;
  font-size: 22px;
  line-height: 1.45;
}
.article-preview-cover {
  display: block;
  width: 100%;
  max-height: 280px;
  margin-bottom: 18px;
  object-fit: cover;
  border-radius: 10px;
}
.article-preview-body {
  color: #1f2937;
  font-size: 15px;
  line-height: 1.85;
}
.article-preview-body :deep(h1),
.article-preview-body :deep(h2),
.article-preview-body :deep(h3) {
  color: #0f172a;
  line-height: 1.45;
}
.article-preview-body :deep(p) {
  margin: 0 0 14px;
}
@media (max-width: 1180px) {
  .auto-schedule-detail-overview {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 780px) {
  .auto-schedule-detail-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .auto-schedule-failure-list {
    grid-template-columns: 1fr;
  }
  .auto-schedule-status-rules,
  .auto-schedule-agent-list {
    grid-template-columns: 1fr;
  }
}
.channel-group-card {
  display: grid;
  gap: 12px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafafa;
}
.channel-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.channel-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.channel-meta small {
  color: #909399;
}
@media (max-width: 640px) {
  .keyword-group-header {
    align-items: flex-start;
    flex-direction: column;
  }
  .keyword-group-actions {
    justify-content: flex-start;
    width: 100%;
  }
  .auto-schedule-layout,
  .auto-schedule-toolbar,
  .auto-schedule-summary,
  .auto-schedule-health-grid,
  .auto-schedule-mode-guide {
    grid-template-columns: 1fr;
  }
  .auto-schedule-overview {
    align-items: flex-start;
    flex-direction: column;
  }
  .auto-schedule-overview-meta {
    justify-content: flex-start;
    min-width: 0;
    width: 100%;
  }
  .auto-schedule-panel-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .auto-schedule-panel-head small {
    text-align: left;
    white-space: normal;
  }
  .auto-schedule-submit {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>
