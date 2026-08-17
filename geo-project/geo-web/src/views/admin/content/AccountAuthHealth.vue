<template>
  <div class="account-health-page admin-page">
    <div class="admin-page-header">
      <div>
        <div class="admin-page-kicker">账号运营</div>
        <h1 class="admin-page-title">账号登录管理</h1>
        <div class="admin-page-subtitle">账号需要登录后，由运营人员统一处理；时间风险只用于安排复验，不代表平台登录已经失效。</div>
      </div>
      <div class="page-actions">
        <span v-if="overview?.generatedAt" class="generated-at">更新于 {{ formatDateTime(overview.generatedAt) }}</span>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button
          v-if="canRecalculateRisk"
          type="primary"
          :icon="RefreshRight"
          :loading="refreshing"
          @click="refreshScan"
        >
          重新计算风险
        </el-button>
      </div>
    </div>

    <DataState :loading="loading" :empty="!overview" empty-text="暂无账号授权健康数据">
      <template v-if="overview">
        <section class="panel work-order-panel is-primary-workspace">
          <div class="panel-head">
            <div>
              <strong>账号登录待办</strong>
              <span>账号需要登录后，由运营人员开始处理，系统确认登录恢复后结束。</span>
            </div>
            <div class="work-order-actions">
              <el-button @click="loadWorkOrders">刷新待办</el-button>
              <el-button @click="exportRiskItems">导出当前清单</el-button>
            </div>
          </div>
          <div class="work-order-metrics">
            <button v-for="item in workOrderMetricItems" :key="item.value" type="button" :class="{ active: workOrderFilter === item.value }" @click="selectWorkOrderFilter(item.value)">
              <span>{{ item.label }}</span>
              <strong>{{ item.count }}</strong>
              <small>{{ item.hint }}</small>
            </button>
          </div>
          <div class="work-order-toolbar">
            <el-input v-model="workOrderKeyword" clearable placeholder="筛选本页品牌、公司、负责人或账号" />
            <div class="work-order-toolbar-options">
              <el-switch v-model="onlyMyWorkOrders" :disabled="workOrderFilter === 'unclaimed'" active-text="只看我负责" />
              <span>逾期待办自动优先</span>
            </div>
          </div>
          <el-empty v-if="!filteredWorkOrders.length" :image-size="52" description="当前分类暂无账号登录待办" />
          <el-table v-else :data="filteredWorkOrders" border table-layout="fixed" :row-class-name="workOrderRowClassName">
            <el-table-column label="品牌" min-width="170">
              <template #default="{ row }">{{ workOrderBrandName(row.brandId) }}</template>
            </el-table-column>
            <el-table-column label="阶段" width="130">
              <template #default="{ row }">
                <el-tag :type="workOrderStatusTag(row.status, row.ownerUserId)">{{ workOrderStatusLabel(row.status, row.ownerUserId) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="当前登录情况" min-width="300">
              <template #default="{ row }">
                <div v-if="row.status === 'resolved'" class="work-order-summary is-resolved">
                  <strong>处理记录已归档</strong>
                  <small>{{ row.resolvedAt ? formatDateTime(row.resolvedAt) : formatDateTime(row.updatedAt) }}</small>
                </div>
                <div v-else class="work-order-summary">
                  <template v-if="workOrderIssueItems(row).length">
                    <div v-for="item in workOrderIssueItems(row).slice(0, 2)" :key="item.targetId" class="work-order-summary-row">
                      <span>{{ item.platformLabel || item.platform }} · {{ item.displayName || '-' }}</span>
                      <el-tag size="small" type="danger">需要登录</el-tag>
                    </div>
                    <small>{{ workOrderAccountProgressText(row) }}</small>
                  </template>
                  <template v-else>
                    <div class="work-order-summary-row">
                      <span>当前目标账号登录状态已恢复</span>
                      <el-tag size="small" type="success">可结束</el-tag>
                    </div>
                    <small>打开待办确认后即可处理结束</small>
                  </template>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="负责人" width="130">
              <template #default="{ row }">
                <el-tag :type="row.ownerUserId ? 'success' : 'info'" effect="plain">{{ workOrderOwnerLabel(row) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="下次跟进" width="170">
              <template #default="{ row }">
                <div :class="{ 'is-follow-up-overdue': isFollowUpOverdue(row) }">{{ row.nextFollowUpAt ? formatDateTime(row.nextFollowUpAt) : '-' }}</div>
                <small v-if="isFollowUpOverdue(row)" class="table-subtext">已逾期 {{ followUpOverdueText(row) }}</small>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button v-if="!row.ownerUserId" link type="success" @click="claimAndOpenWorkOrder(row)">开始处理</el-button>
                <el-button v-else-if="isMyWorkOrder(row)" link type="primary" @click="openWorkOrder(row)">继续处理</el-button>
                <el-button v-else link type="info" @click="openWorkOrder(row)">查看进度</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="workOrderTotal > workOrderPageSize" class="work-order-pagination">
            <el-pagination v-model:current-page="workOrderPage" :page-size="workOrderPageSize" :total="workOrderTotal" layout="prev, pager, next, total" @current-change="loadWorkOrders" />
          </div>
        </section>

        <el-collapse v-model="systemDetailsOpen" class="system-details-collapse">
          <el-collapse-item name="system">
            <template #title>
              <div class="system-details-title">
                <strong>系统运行情况与辅助查询</strong>
                <span>登录检测 {{ overview.summary.explicitLoginIssueCount }} 项 · 复验超期 {{ overview.summary.reverifyOverdueCount }} 项</span>
              </div>
            </template>
        <section class="metric-grid">
          <div class="metric-panel is-danger">
            <span>登录检测待核对</span>
            <strong>{{ overview.summary.explicitLoginIssueCount }}</strong>
            <small>由运营人员统一核对并处理</small>
          </div>
          <div class="metric-panel is-warning">
            <span>建议复验超期</span>
            <strong>{{ overview.summary.reverifyOverdueCount }}</strong>
            <small>参考周期已到，不代表已经失效</small>
          </div>
          <div class="metric-panel is-warning">
            <span>即将需要复验</span>
            <strong>{{ reverifyDueSoonCount }}</strong>
            <small>未来 7 天建议确认</small>
          </div>
          <div class="metric-panel is-success">
            <span>参考周期内</span>
            <strong>{{ overview.summary.referenceWindowNormalCount }}</strong>
            <small>仅表示尚未到建议复验时间</small>
          </div>
        </section>

        <section class="panel scan-panel">
          <div class="panel-head">
            <div>
              <strong>数据扫描状态</strong>
              <span>风险数据来自定时扫描与人工重新计算；部分失败时不会把未扫描账号误判为正常。</span>
            </div>
          </div>
          <div v-if="overview.latestScans?.length" class="scan-grid">
            <div v-for="scan in overview.latestScans" :key="scan.id" class="scan-item">
              <div class="scan-item-head">
                <strong>{{ scan.scanType === 'self_media' ? '自媒体账号' : '论坛凭据' }}</strong>
                <el-tag size="small" :type="scanStatusTag(scan.status)">{{ scanStatusLabel(scan.status) }}</el-tag>
              </div>
              <span>{{ scan.finishedAt ? formatDateTime(scan.finishedAt) : formatDateTime(scan.startedAt) }}</span>
              <small>成功 {{ scan.successCount || 0 }} / {{ scan.totalCount || 0 }}，失败 {{ scan.failureCount || 0 }}</small>
              <small v-if="scan.errorSummary" class="scan-error">{{ scan.errorSummary }}</small>
            </div>
          </div>
          <el-empty v-else :image-size="52" description="尚无扫描批次记录" />
        </section>

        <section class="panel version-panel">
          <div class="panel-head">
            <div>
              <strong>运行组件版本覆盖</strong>
              <span>统计近 7 天在线版本，用于灰度升级；新旧兼容版本可并存，不要求客户重新登录。</span>
            </div>
          </div>
          <div class="version-coverage-grid">
            <div>
              <strong>浏览器扩展（{{ versionCoverage?.recentExtensionInstallCount || 0 }}）</strong>
              <div class="version-tags">
                <el-tag v-for="item in versionCoverage?.extensionVersions || []" :key="item.version" type="info">
                  {{ item.version }} · {{ item.count }}
                </el-tag>
              </div>
            </div>
            <div>
              <strong>本地助手（{{ versionCoverage?.recentHelperCount || 0 }}）</strong>
              <div class="version-tags">
                <el-tag v-for="item in versionCoverage?.helperVersions || []" :key="item.version" type="info">
                  {{ item.version }} · {{ item.count }}
                </el-tag>
              </div>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-head">
            <div>
              <strong>品牌发布环境</strong>
              <span>同一品牌的账号共享一个指纹浏览器环境，优先按环境处理异常。</span>
            </div>
            <el-tag size="small" type="info">{{ filteredBrandEnvironments.length }} 个品牌</el-tag>
          </div>
          <div class="environment-toolbar">
            <el-input v-model="keyword" clearable placeholder="搜索公司、品牌、环境或账号" />
            <el-select v-model="platformFilter" clearable placeholder="全部平台">
              <el-option v-for="option in platformOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <el-switch v-model="issuesOnly" active-text="只看有问题" />
          </div>
          <div v-if="filteredBrandEnvironments.length" class="environment-grid">
            <button
              v-for="environment in filteredBrandEnvironments"
              :key="environment.brandId"
              class="environment-card"
              type="button"
            >
              <div class="environment-card-head">
                <div>
                  <strong>{{ environment.brandName || `品牌 ${environment.brandId}` }}</strong>
                  <small>{{ environment.companyName || '-' }}</small>
                </div>
                <el-tag size="small" :type="environment.loginIssueCount ? 'danger' : environment.unverifiedCount ? 'warning' : 'success'">
                  {{ environment.loginIssueCount ? `${environment.loginIssueCount} 项异常` : environment.unverifiedCount ? `${environment.unverifiedCount} 项待验证` : '登录状态可用' }}
                </el-tag>
              </div>
              <div class="environment-name">{{ environment.environmentKey || '尚未配置品牌环境' }}</div>
              <div class="environment-stats">
                <span>账号 <strong>{{ environment.accountCount }}</strong></span>
                <span>已确认 <strong>{{ environment.confirmedLoginCount }}</strong></span>
                <span>复验超期 <strong>{{ environment.reverifyOverdueCount }}</strong></span>
                <span>负责人 <strong>{{ environment.ownerName || '-' }}</strong></span>
              </div>
              <div class="environment-card-actions">
                <el-button link type="primary" @click.stop="openRoute(environment.actionRoute)">查看品牌</el-button>
                <el-button link type="warning" :disabled="!canOperateEnvironmentWorkOrder(environment)" @click.stop="handleEnvironmentWorkOrder(environment)">
                  {{ environmentWorkOrderActionLabel(environment.brandId) }}
                </el-button>
              </div>
            </button>
          </div>
          <el-empty v-else :image-size="56" description="没有符合当前条件的品牌环境" />
        </section>

        <section class="overview-grid">
          <div class="panel trend-panel">
            <div class="panel-head">
              <div>
                <strong>建议复验时间分布</strong>
                <span>未来 14 天账号与论坛凭据复验安排</span>
              </div>
              <el-tag size="small" type="warning">30 天内 {{ overview.summary.dueInThirtyDays }}</el-tag>
            </div>
            <div class="trend-list">
              <div v-for="bucket in overview.trendBuckets" :key="bucket.date" class="trend-row">
                <span class="trend-date">{{ shortDate(bucket.date) }}</span>
                <div class="trend-track">
                  <span
                    class="trend-bar self-media"
                    :style="{ width: trendWidth(bucket.selfMediaCount, trendMax) }"
                  />
                  <span
                    class="trend-bar forum"
                    :style="{ width: trendWidth(bucket.forumCount, trendMax) }"
                  />
                </div>
                <strong>{{ bucket.totalCount }}</strong>
              </div>
            </div>
            <div class="trend-legend">
              <span><i class="legend-dot self-media" />自媒体</span>
              <span><i class="legend-dot forum" />论坛</span>
            </div>
          </div>

          <div class="panel">
            <div class="panel-head">
              <div>
                <strong>聚合告警</strong>
                <span>同类问题合并展示，减少重复干扰</span>
              </div>
              <el-tag size="small" :type="overview.alertGroups.length ? 'warning' : 'success'">
                {{ overview.alertGroups.length ? `${overview.alertGroups.length} 类` : '无待办' }}
              </el-tag>
            </div>
            <el-empty v-if="!overview.alertGroups.length" description="当前没有账号授权类待办" />
            <div v-else class="alert-group-list">
              <button
                v-for="group in overview.alertGroups"
                :key="group.groupKey"
                class="alert-group-row"
                type="button"
                @click="openRoute(group.actionRoute)"
              >
                <span class="severity-dot" :class="severityClass(group.severity)" />
                <span class="alert-group-main">
                  <strong>{{ group.title }}</strong>
                  <small>{{ group.sampleMessage || group.issueCode }}</small>
                </span>
                <el-tag size="small" :type="severityTag(group.severity)">{{ group.count }} 条</el-tag>
              </button>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-head">
            <div>
              <strong>待处理清单</strong>
              <span>成功检测可作为登录事实；失败检测需先内部核对，建议复验时间仅用于运营安排。</span>
            </div>
            <div class="filter-tabs">
              <el-segmented v-model="riskFilter" :options="riskFilterOptions" />
            </div>
          </div>

          <el-table :data="filteredRiskItems" border table-layout="fixed">
            <el-table-column label="对象" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="entity-cell">
                  <div class="entity-avatar" :class="row.targetType === 'forum' ? 'is-forum' : 'is-self'">
                    {{ row.targetType === 'forum' ? '坛' : '媒' }}
                  </div>
                  <div class="min-w-0">
                    <div class="entity-main">{{ row.displayName || '-' }}</div>
                    <div class="entity-sub">{{ objectSubtitle(row) }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="登录事实" width="140">
              <template #default="{ row }">
                <el-tag size="small" :type="loginFactTag(row.loginFactStatus)">
                  {{ loginFactLabel(row.loginFactStatus, row.targetType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="复验计划" width="140">
              <template #default="{ row }">
                <el-tag size="small" :type="reverifyTag(row.reverifyStatus)">
                  {{ reverifyLabel(row.reverifyStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="建议复验时间" min-width="180">
              <template #default="{ row }">
                <div>{{ row.expiresAt ? formatDateTime(row.expiresAt) : '-' }}</div>
                <div class="table-subtext">{{ daysText(row.daysUntilExpiry) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="最近确认登录" min-width="180">
              <template #default="{ row }">
                <div>{{ row.lastLoginVerifiedAt ? formatDateTime(row.lastLoginVerifiedAt) : '-' }}</div>
                <div v-if="row.targetType === 'self_media'" class="table-subtext">
                  {{ loginCheckText(row) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="来源" width="130">
              <template #default="{ row }">{{ row.expirySourceLabel || '-' }}</template>
            </el-table-column>
            <el-table-column label="负责人" width="130">
              <template #default="{ row }">{{ row.ownerName || '-' }}</template>
            </el-table-column>
            <el-table-column label="处理建议" min-width="280" show-overflow-tooltip>
              <template #default="{ row }">{{ row.actionHint || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleRiskItemAction(row)">
                    {{ row.targetType === 'self_media' ? '进入登录待办' : (row.actionLabel || '查看详情') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
          </el-collapse-item>
        </el-collapse>
      </template>
    </DataState>

    <el-dialog v-model="workOrderDialogVisible" :title="`账号登录处理 · ${activeWorkOrder ? workOrderBrandName(activeWorkOrder.brandId) : ''}`" width="680px">
      <el-alert
        :title="workOrderOwnershipTitle"
        :type="canEditActiveWorkOrder ? 'success' : activeWorkOrder?.ownerUserId ? 'warning' : 'info'"
        :closable="false"
        show-icon
        class="work-order-owner-alert"
      />
      <section v-if="activeWorkOrder" class="work-order-live-context">
        <div class="work-order-live-head">
          <div>
            <strong>当前登录情况 · 已恢复 {{ activeWorkOrderRestoredCount }}/{{ activeWorkOrderRiskItems.length }}</strong>
            <span>系统每 10 秒自动更新，最近刷新 {{ lastWorkOrderRefreshAt ? formatDateTime(lastWorkOrderRefreshAt) : '等待刷新' }}</span>
          </div>
          <div>
            <el-button size="small" :loading="loading" @click="refreshActiveWorkOrderContext">刷新数据</el-button>
            <el-button size="small" type="primary" :loading="environmentOpening" @click="openActiveWorkOrderEnvironment()">打开账号环境</el-button>
          </div>
        </div>
        <div v-if="activeWorkOrderRiskItems.length" class="work-order-account-list">
          <div v-for="item in visibleActiveWorkOrderRiskItems" :key="`${item.targetType}-${item.targetId}`" class="work-order-account-row" :class="{ 'is-login-required': requiresLoginHandling(item) }">
            <div>
              <strong>{{ item.platformLabel || item.platform }} · {{ item.displayName || '-' }}</strong>
              <span>最近确认 {{ item.lastLoginVerifiedAt ? formatDateTime(item.lastLoginVerifiedAt) : '尚无记录' }}</span>
            </div>
            <div class="work-order-account-actions">
              <el-tag size="small" :type="loginFactTag(item.loginFactStatus)">{{ loginFactLabel(item.loginFactStatus, item.targetType) }}</el-tag>
              <el-button v-if="requiresLoginHandling(item)" link type="primary" :loading="environmentOpening" @click="openActiveWorkOrderEnvironment(item.platform)">打开平台</el-button>
            </div>
          </div>
          <el-button v-if="activeWorkOrderNormalItems.length" link type="info" class="normal-account-toggle" @click="showNormalAccounts = !showNormalAccounts">
            {{ showNormalAccounts ? '收起正常账号' : `查看其余 ${activeWorkOrderNormalItems.length} 个正常账号` }}
          </el-button>
        </div>
        <el-empty v-else :image-size="42" description="当前品牌暂无账号风险数据" />
      </section>
      <div v-if="environmentOpenIssue" class="environment-open-issue">
        <div>
          <strong>{{ environmentOpenIssue.title }}</strong>
          <span>{{ environmentOpenIssue.message }}</span>
        </div>
        <el-button size="small" type="primary" plain @click="handleEnvironmentRecovery">{{ environmentOpenIssue.actionLabel }}</el-button>
      </div>
      <el-alert
        v-if="activeWorkOrder && activeWorkOrder.status !== 'resolved'"
        :title="activeWorkOrderCanResolve ? '当前账号登录状态已恢复，可以处理结束' : '系统每 10 秒自动刷新登录状态，完成登录后无需手动刷新页面'"
        :type="activeWorkOrderCanResolve ? 'success' : 'info'"
        :closable="false"
        show-icon
        class="work-order-ready-alert"
      />
      <el-form label-width="110px">
        <el-form-item label="当前阶段">
          <el-tag v-if="activeWorkOrder" :type="workOrderStatusTag(activeWorkOrder.status, activeWorkOrder.ownerUserId)">
            {{ workOrderStatusLabel(activeWorkOrder.status, activeWorkOrder.ownerUserId) }}
          </el-tag>
        </el-form-item>
        <el-form-item v-if="activeWorkOrder?.latestNote" label="最近处置备注">
          <div class="latest-note">{{ activeWorkOrder.latestNote }}</div>
        </el-form-item>
        <el-form-item label="处理记录">
          <el-input
            v-model="workOrderForm.note"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            :disabled="!canEditActiveWorkOrder"
            placeholder="可填写本次处理进展；登录恢复后可直接处理结束"
          />
          <div class="credential-safety-hint" :class="{ 'is-danger': workOrderNoteHasSensitiveCredential }">
            {{ workOrderNoteHasSensitiveCredential ? '检测到疑似密码、验证码、Cookie 或 Token，请删除后再保存。' : '请勿填写密码、验证码、Cookie 或 Token。' }}
          </div>
        </el-form-item>
        <el-form-item v-if="canEditActiveWorkOrder" label="下次跟进（选填）">
          <div class="follow-up-editor">
            <div class="follow-up-shortcuts">
              <el-button size="small" plain @click="setFollowUpShortcut('one_hour')">1小时后</el-button>
              <el-button size="small" plain @click="setFollowUpShortcut('end_of_day')">今天下班前</el-button>
              <el-button size="small" plain @click="setFollowUpShortcut('tomorrow_morning')">明天上午</el-button>
            </div>
            <el-date-picker v-model="workOrderForm.nextFollowUpAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" clearable placeholder="自定义跟进时间" />
          </div>
        </el-form-item>
        <el-collapse class="work-order-more-settings">
          <el-collapse-item title="更多设置" name="more">
            <el-form-item label="优先级">
              <el-select v-model="workOrderForm.priority" :disabled="!canEditActiveWorkOrder" style="width: 100%">
                <el-option label="低" value="low" />
                <el-option label="普通" value="normal" />
                <el-option label="高" value="high" />
                <el-option label="紧急" value="urgent" />
              </el-select>
            </el-form-item>
          </el-collapse-item>
        </el-collapse>
        <template v-if="specialResolutionVisible">
          <el-alert title="仅在账号已停止运营时使用。确认后，所选账号会停用并退出后续登录监测；误报请先保存进展并继续核验。" type="warning" :closable="false" show-icon class="special-resolution-alert" />
          <el-form-item label="特殊结束原因">
            <el-select v-model="specialResolutionReason" style="width: 100%" placeholder="请选择原因">
              <el-option v-for="reason in specialResolutionReasons" :key="reason" :label="reason" :value="reason" />
            </el-select>
          </el-form-item>
          <el-form-item label="停止运营账号">
            <el-checkbox-group v-model="specialResolutionAccountIds" class="special-resolution-accounts">
              <el-checkbox v-for="item in activeWorkOrderIssueItems" :key="item.targetId" :value="item.targetId">
                {{ item.platformLabel || item.platform }} · {{ item.displayName || '-' }}
              </el-checkbox>
            </el-checkbox-group>
            <div class="form-hint special-resolution-hint">所选账号会同步设为停用；未选择或仍有其他异常账号时无法结束。</div>
          </el-form-item>
        </template>
        <el-form-item label="处置时间线">
          <div v-if="workOrderEvents.length" class="work-order-timeline">
            <div v-for="event in visibleWorkOrderEvents" :key="event.id" class="work-order-event">
              <strong>{{ event.toStatus ? workOrderStatusLabel(event.toStatus) : '创建工单' }}</strong>
              <span>{{ formatDateTime(event.createdAt) }} · {{ event.operatorName || (event.operatorId ? `用户 ${event.operatorId}` : '系统') }}</span>
              <small v-if="event.note">{{ event.note }}</small>
            </div>
            <el-button v-if="workOrderEvents.length > 3" link type="primary" @click="showAllWorkOrderEvents = !showAllWorkOrderEvents">
              {{ showAllWorkOrderEvents ? '收起历史记录' : `展开全部 ${workOrderEvents.length} 条记录` }}
            </el-button>
          </div>
          <span v-else class="form-hint">暂无处置记录</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="workOrderDialogVisible = false">关闭</el-button>
        <el-button v-if="activeWorkOrder && !activeWorkOrder.ownerUserId" type="success" :loading="workOrderSaving" @click="claimAndOpenWorkOrder(activeWorkOrder)">开始处理</el-button>
        <template v-else-if="canEditActiveWorkOrder">
          <el-button type="primary" plain :loading="workOrderSaving" @click="saveWorkOrder('operator_verification')">保存处理进展</el-button>
          <el-button v-if="!activeWorkOrderCanResolve && !specialResolutionVisible" link type="warning" @click="specialResolutionVisible = true">特殊结束</el-button>
          <el-button v-if="specialResolutionVisible" type="warning" plain :loading="workOrderSaving" @click="saveWorkOrder('resolved', true)">确认特殊结束</el-button>
          <el-button type="success" :disabled="!activeWorkOrderCanResolve" :loading="workOrderSaving" @click="saveWorkOrder('resolved')">处理结束</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshRight } from '@element-plus/icons-vue'
import DataState from '@/components/ui/DataState.vue'
import { getAccountAuthHealthOverview, getAccountAuthRuntimeVersionCoverage, refreshAccountAuthHealthOverview } from '@/api/accountAuthHealth'
import {
  claimAccountAuthWorkOrder,
  getOrCreateAccountAuthWorkOrder,
  listAccountAuthWorkOrderEvents,
  listAccountAuthWorkOrders,
  updateAccountAuthWorkOrder,
  type AccountAuthWorkOrder,
  type AccountAuthWorkOrderEvent,
} from '@/api/accountAuthWorkOrder'
import type { AccountAuthHealthBrandEnvironment, AccountAuthHealthOverview, AccountAuthHealthRiskItem } from '@/types'
import { formatDateTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import { getSelfMediaAutomationReadiness, listBrowserEnvironments } from '@/api/browserEnvironment'
import { openLocalHelperEnvironment } from '@/api/localHelper'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)
const refreshing = ref(false)
const overview = ref<AccountAuthHealthOverview | null>(null)
const versionCoverage = ref<Awaited<ReturnType<typeof getAccountAuthRuntimeVersionCoverage>>['data']['data'] | null>(null)
const workOrders = ref<AccountAuthWorkOrder[]>([])
const workOrderTotal = ref(0)
const workOrderPage = ref(1)
const workOrderPageSize = 20
const workOrderCounts = ref({ unclaimed: 0, processing: 0, resolved: 0 })
const workOrderDialogVisible = ref(false)
const workOrderSaving = ref(false)
const environmentOpening = ref(false)
const workOrderAutoRefreshing = ref(false)
const systemDetailsOpen = ref<string[]>([])
const activeWorkOrder = ref<AccountAuthWorkOrder | null>(null)
const workOrderEvents = ref<AccountAuthWorkOrderEvent[]>([])
const activeWorkOrderTargetAccountIds = ref<number[]>([])
const workOrderForm = ref({ status: 'operator_verification', priority: 'normal', note: '', nextFollowUpAt: null as string | null })
const WORK_ORDER_PREFERENCES_KEY = 'account-auth-health-work-order-preferences'
const savedWorkOrderPreferences = readWorkOrderPreferences()
const workOrderFilter = ref(savedWorkOrderPreferences.filter)
const onlyMyWorkOrders = ref(savedWorkOrderPreferences.onlyMine)
const workOrderKeyword = ref(savedWorkOrderPreferences.keyword)
const showNormalAccounts = ref(false)
const showAllWorkOrderEvents = ref(false)
const specialResolutionVisible = ref(false)
const specialResolutionReason = ref('')
const specialResolutionAccountIds = ref<number[]>([])
const lastWorkOrderRefreshAt = ref<string | null>(null)
const environmentOpenIssue = ref<EnvironmentOpenIssue | null>(null)
const riskFilter = ref('attention')
const keyword = ref('')
const platformFilter = ref('')
const issuesOnly = ref(true)
const LOCAL_HELPER_BASE = 'http://127.0.0.1:17891'
let workOrderRefreshTimer: number | null = null
type EnvironmentOpenIssue = {
  kind: 'helper' | 'configuration' | 'environment' | 'extension' | 'retry'
  title: string
  message: string
  actionLabel: string
}
const specialResolutionReasons = ['账号已停用', '账号停止运营', '品牌不再运营该平台']
const canRecalculateRisk = computed(() => userStore.hasPermission([
  'self-media.auth-health.recalculate',
  'company.update',
]))
const currentUserId = computed(() => Number(userStore.userInfo?.id || 0))
const canEditActiveWorkOrder = computed(() => activeWorkOrder.value?.status !== 'resolved' && Boolean(activeWorkOrder.value?.ownerUserId)
  && Number(activeWorkOrder.value?.ownerUserId) === currentUserId.value)
const workOrderOwnershipTitle = computed(() => {
  const row = activeWorkOrder.value
  if (!row?.ownerUserId) return '该待办尚未开始处理，开始后才能更新进度。'
  if (isMyWorkOrder(row)) return '该待办由我负责，可继续更新处理进度。'
  return `该待办已由${row.ownerName || '其他运营人员'}处理，当前仅可查看。`
})
const activeWorkOrderRiskItems = computed(() => {
  if (!activeWorkOrder.value) return []
  return (overview.value?.riskItems || []).filter((item) => item.targetType === 'self_media' && item.brandId === activeWorkOrder.value?.brandId)
})
const activeWorkOrderIssueItems = computed(() => activeWorkOrderRiskItems.value.filter(requiresLoginHandling))
const activeWorkOrderNormalItems = computed(() => activeWorkOrderRiskItems.value.filter((item) => !requiresLoginHandling(item)))
const visibleActiveWorkOrderRiskItems = computed(() => [
  ...activeWorkOrderIssueItems.value,
  ...(showNormalAccounts.value ? activeWorkOrderNormalItems.value : []),
])
const activeWorkOrderRestoredCount = computed(() => activeWorkOrderRiskItems.value.filter(isConfirmedLoggedIn).length)
const visibleWorkOrderEvents = computed(() => showAllWorkOrderEvents.value ? workOrderEvents.value : workOrderEvents.value.slice(0, 3))
const workOrderNoteHasSensitiveCredential = computed(() => containsSensitiveCredential(workOrderForm.value.note))
const activeWorkOrderCanResolve = computed(() => activeWorkOrderTargetAccountIds.value.length > 0
  ? activeWorkOrderTargetAccountIds.value.every((accountId) => {
    const item = activeWorkOrderRiskItems.value.find((row) => row.targetId === accountId)
    return item?.loginFactStatus === 'confirmed_logged_in' || item?.loginFactStatus === 'official_authorized'
  }) && !activeWorkOrderRiskItems.value.some(requiresLoginHandling)
  : activeWorkOrderRiskItems.value.some((item) => item.loginFactStatus === 'confirmed_logged_in' || item.loginFactStatus === 'official_authorized')
    && !activeWorkOrderRiskItems.value.some(requiresLoginHandling))
const workOrderMetricItems = computed(() => [
  { label: '账号需要登录', value: 'unclaimed', count: workOrderCounts.value.unclaimed, hint: '等待运营处理' },
  { label: '运营人员处理', value: 'processing', count: workOrderCounts.value.processing, hint: `${workOrders.value.filter(isFollowUpOverdue).length} 项跟进逾期` },
  { label: '处理结束', value: 'resolved', count: workOrderCounts.value.resolved, hint: '查看处理历史' },
])
const filteredWorkOrders = computed(() => {
  const keyword = workOrderKeyword.value.trim().toLowerCase()
  return workOrders.value.filter((row) => {
    if (!keyword) return true
    const environment = overview.value?.brandEnvironments.find((item) => item.brandId === row.brandId)
    const accounts = workOrderRiskItems(row).flatMap((item) => [item.displayName, item.platformLabel, item.platform])
    return [environment?.brandName, environment?.companyName, row.ownerName, row.issueSummary, row.latestNote, ...accounts]
      .some((value) => String(value || '').toLowerCase().includes(keyword))
  }).sort(compareWorkOrders)
})

const riskFilterOptions = [
  { label: '需关注', value: 'attention' },
  { label: '复验超期', value: 'overdue' },
  { label: '即将复验', value: 'due_soon' },
  { label: '全部', value: 'all' },
]

const filteredRiskItems = computed(() => {
  const rows = filteredRiskItemsBySearch.value
  if (riskFilter.value === 'all') return rows
  if (riskFilter.value === 'attention') {
    return rows.filter((item) => item.riskStatus !== 'normal')
  }
  if (riskFilter.value === 'overdue') {
    return rows.filter((item) => ['reverify_overdue', 'expired', 'login_verification_failed'].includes(item.riskStatus))
  }
  if (riskFilter.value === 'due_soon') {
    return rows.filter((item) => item.riskStatus === 'reverify_due_soon' || item.riskStatus === 'expiring')
  }
  return rows.filter((item) => item.riskStatus === riskFilter.value)
})

const filteredRiskItemsBySearch = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()
  return (overview.value?.riskItems || []).filter((item) => {
    if (platformFilter.value && item.platform !== platformFilter.value) return false
    if (!normalizedKeyword) return true
    return [item.displayName, item.brandName, item.companyName, item.environmentKey, item.ownerName]
      .some((value) => String(value || '').toLowerCase().includes(normalizedKeyword))
  })
})

const platformOptions = computed(() => {
  const seen = new Map<string, string>()
  ;(overview.value?.riskItems || []).forEach((item) => {
    if (item.platform) seen.set(item.platform, item.platformLabel || item.platform)
  })
  return [...seen.entries()].map(([value, label]) => ({ value, label }))
})

const filteredBrandEnvironments = computed(() => {
  const visibleBrandIds = new Set(filteredRiskItemsBySearch.value.map((item) => item.brandId).filter(Boolean))
  return (overview.value?.brandEnvironments || []).filter((item) => {
    if (!visibleBrandIds.has(item.brandId)) return false
    return !issuesOnly.value || item.loginIssueCount > 0 || item.unverifiedCount > 0 || item.reverifyOverdueCount > 0
  })
})

const trendMax = computed(() => Math.max(1, ...(overview.value?.trendBuckets || []).map((item) => item.totalCount)))
const reverifyDueSoonCount = computed(() => (overview.value?.riskItems || [])
  .filter((item) => item.targetType === 'self_media' && item.riskStatus === 'reverify_due_soon')
  .length)

async function load() {
  loading.value = true
  try {
    const { data } = await getAccountAuthHealthOverview()
    overview.value = data.data
    await Promise.all([loadWorkOrders(), loadVersionCoverage()])
    await openWorkOrderFromRoute()
  } finally {
    loading.value = false
  }
}

async function loadVersionCoverage() {
  const { data } = await getAccountAuthRuntimeVersionCoverage()
  versionCoverage.value = data.data
}

async function loadWorkOrders() {
  const { data } = await listAccountAuthWorkOrders({
    stage: workOrderFilter.value,
    ownerUserId: onlyMyWorkOrders.value ? currentUserId.value : undefined,
    current: workOrderPage.value,
    size: workOrderPageSize,
  })
  const page = data.data
  workOrders.value = page?.records || []
  workOrderTotal.value = page?.total || 0
  workOrderCounts.value = {
    unclaimed: page?.unclaimedCount || 0,
    processing: page?.processingCount || 0,
    resolved: page?.resolvedCount || 0,
  }
}

function readWorkOrderPreferences() {
  const fallback = { filter: 'unclaimed', onlyMine: false, keyword: '' }
  try {
    const parsed = JSON.parse(window.sessionStorage.getItem(WORK_ORDER_PREFERENCES_KEY) || '{}') as Partial<typeof fallback>
    const filter = ['unclaimed', 'processing', 'resolved'].includes(parsed.filter || '') ? parsed.filter! : fallback.filter
    return { filter, onlyMine: Boolean(parsed.onlyMine), keyword: String(parsed.keyword || '') }
  } catch {
    return fallback
  }
}

function workOrderRiskItems(row: AccountAuthWorkOrder) {
  return (overview.value?.riskItems || []).filter((item) => item.targetType === 'self_media' && item.brandId === row.brandId)
}

function workOrderIssueItems(row: AccountAuthWorkOrder) {
  return workOrderRiskItems(row).filter(requiresLoginHandling)
}

function workOrderAccountProgressText(row: AccountAuthWorkOrder) {
  const items = workOrderRiskItems(row)
  const issueCount = items.filter(requiresLoginHandling).length
  const restoredCount = items.filter(isConfirmedLoggedIn).length
  const hiddenCount = Math.max(0, issueCount - 2)
  return `已恢复 ${restoredCount}/${items.length}${hiddenCount ? `，另有 ${hiddenCount} 个账号需要登录` : ''}`
}

function isConfirmedLoggedIn(item: AccountAuthHealthRiskItem) {
  return item.loginFactStatus === 'confirmed_logged_in' || item.loginFactStatus === 'official_authorized'
}

function workOrderByBrand(brandId?: number | null) {
  return workOrders.value.find((item) => item.brandId === brandId)
}

function isMyWorkOrder(row: AccountAuthWorkOrder) {
  return Boolean(row.ownerUserId) && Number(row.ownerUserId) === currentUserId.value
}

function workOrderOwnerLabel(row: AccountAuthWorkOrder) {
  if (!row.ownerUserId) return '等待处理'
  if (isMyWorkOrder(row)) return '我处理中'
  return row.ownerName ? `${row.ownerName}处理中` : '其他运营处理中'
}

function environmentWorkOrderActionLabel(brandId: number) {
  const row = workOrderByBrand(brandId)
  const environment = overview.value?.brandEnvironments.find((item) => item.brandId === brandId)
  if (!row) return environment && hasEnvironmentLoginRequirement(environment) ? '开始处理' : '查看历史'
  if (row.status === 'resolved') return environment && hasEnvironmentLoginRequirement(environment) ? '重新开始处理' : '查看历史'
  if (!row.ownerUserId) return '开始处理'
  if (isMyWorkOrder(row)) return '继续处理'
  return '查看进度'
}

function requiresLoginHandling(item: AccountAuthHealthRiskItem) {
  return item.targetType === 'self_media'
    && (item.loginFactStatus === 'login_required'
      || item.loginFactStatus === 'verification_failed'
      || item.riskStatus === 'expired')
}

function hasEnvironmentLoginRequirement(environment?: AccountAuthHealthBrandEnvironment) {
  return Boolean(environment && (overview.value?.riskItems || [])
    .some((item) => item.brandId === environment.brandId && requiresLoginHandling(item)))
}

function canOperateEnvironmentWorkOrder(environment: AccountAuthHealthBrandEnvironment) {
  return Boolean(workOrderByBrand(environment.brandId) || hasEnvironmentLoginRequirement(environment))
}

async function handleEnvironmentWorkOrder(environment: AccountAuthHealthBrandEnvironment) {
  let row = workOrderByBrand(environment.brandId)
  if (!row && !hasEnvironmentLoginRequirement(environment)) {
    ElMessage.info('当前没有明确的账号登录失效事实，请先完成内部核验')
    return
  }
  if (!row || hasEnvironmentLoginRequirement(environment)) {
    const { data } = await getOrCreateAccountAuthWorkOrder({
      brandId: environment.brandId,
      browserEnvironmentId: environment.browserEnvironmentId,
      issueSummary: `${environment.loginIssueCount} 项登录异常，${environment.unverifiedCount} 项待验证，${environment.reverifyOverdueCount} 项复验超期`,
    })
    row = data.data
    await loadWorkOrders()
  }
  if (!row) return
  if (!row.ownerUserId) {
    await claimAndOpenWorkOrder(row)
    return
  }
  await openWorkOrder(row)
}

async function openWorkOrder(row: AccountAuthWorkOrder) {
  activeWorkOrder.value = row
  activeWorkOrderTargetAccountIds.value = activeWorkOrderRiskItems.value
    .filter(requiresLoginHandling)
    .map((item) => item.targetId)
  workOrderForm.value = {
    status: workOrderDefaultAction(row.status),
    priority: row.priority || 'normal',
    note: '',
    nextFollowUpAt: row.nextFollowUpAt || null,
  }
  workOrderEvents.value = []
  showNormalAccounts.value = false
  showAllWorkOrderEvents.value = false
  specialResolutionVisible.value = false
  specialResolutionReason.value = ''
  specialResolutionAccountIds.value = []
  environmentOpenIssue.value = null
  lastWorkOrderRefreshAt.value = overview.value?.generatedAt || null
  workOrderDialogVisible.value = true
  const { data } = await listAccountAuthWorkOrderEvents(row.id)
  workOrderEvents.value = data.data || []
}

function selectWorkOrderFilter(value: string) {
  workOrderFilter.value = value
  workOrderPage.value = 1
  if (value === 'unclaimed') onlyMyWorkOrders.value = false
  void loadWorkOrders()
}

async function claimWorkOrder(row: AccountAuthWorkOrder) {
  const { data } = await claimAccountAuthWorkOrder(row.id)
  ElMessage.success('已开始处理')
  await loadWorkOrders()
  return data.data || workOrders.value.find((item) => item.id === row.id) || row
}

async function claimAndOpenWorkOrder(row: AccountAuthWorkOrder) {
  workOrderSaving.value = true
  try {
    const claimed = await claimWorkOrder(row)
    await openWorkOrder(claimed)
  } finally {
    workOrderSaving.value = false
  }
}

async function saveWorkOrder(status: 'operator_verification' | 'resolved', specialResolution = false) {
  if (!activeWorkOrder.value) return
  if (!canEditActiveWorkOrder.value) {
    ElMessage.warning(workOrderOwnershipTitle.value)
    return
  }
  if (status === 'resolved' && !activeWorkOrderCanResolve.value && !specialResolution) {
    ElMessage.warning('目标账号登录状态尚未恢复；仅停止运营的账号可使用特殊结束，误报请保存进展并继续核验')
    return
  }
  if (specialResolution && !specialResolutionReason.value) {
    ElMessage.warning('请选择特殊结束原因')
    return
  }
  if (specialResolution && !specialResolutionAccountIds.value.length) {
    ElMessage.warning('请选择需要停止运营的账号')
    return
  }
  if (workOrderNoteHasSensitiveCredential.value) {
    ElMessage.warning('工单备注禁止填写账号密码、验证码、Cookie 或 Token')
    return
  }
  const note = status === 'resolved'
    ? specialResolution
      ? `特殊结束：${specialResolutionReason.value}${workOrderForm.value.note.trim() ? `；${workOrderForm.value.note.trim()}` : ''}`
      : workOrderForm.value.note.trim() || '系统已确认目标账号登录状态恢复'
    : workOrderForm.value.note
  workOrderSaving.value = true
  try {
    await updateAccountAuthWorkOrder(activeWorkOrder.value.id, {
      ...workOrderForm.value,
      note,
      status,
      resolutionMode: status === 'resolved' ? (specialResolution ? 'special' : 'login_restored') : undefined,
      resolutionReason: specialResolution ? specialResolutionReason.value : undefined,
      affectedAccountIds: specialResolution ? specialResolutionAccountIds.value : undefined,
      nextFollowUpAt: status === 'operator_verification' ? workOrderForm.value.nextFollowUpAt : null,
    })
    ElMessage.success(status === 'resolved' ? '处理已结束' : '处理进展已保存')
    workOrderDialogVisible.value = false
    await loadWorkOrders()
  } finally {
    workOrderSaving.value = false
  }
}

function workOrderDefaultAction(status: string) {
  return status === 'resolved' ? 'resolved' : 'operator_verification'
}

function containsSensitiveCredential(note: string) {
  return /(密码|验证码|cookie|token|pwd|password)\s*[:：=]\s*\S+/i.test(note)
    || /sessionid=|sid_tt=/i.test(note)
}

function isFollowUpOverdue(row: AccountAuthWorkOrder) {
  if (row.status === 'resolved' || !row.nextFollowUpAt) return false
  return Date.parse(row.nextFollowUpAt) <= Date.now()
}

function followUpOverdueText(row: AccountAuthWorkOrder) {
  if (!row.nextFollowUpAt) return ''
  const hours = Math.max(1, Math.floor((Date.now() - Date.parse(row.nextFollowUpAt)) / 3_600_000))
  return hours < 24 ? `${hours} 小时` : `${Math.floor(hours / 24)} 天`
}

function compareWorkOrders(left: AccountAuthWorkOrder, right: AccountAuthWorkOrder) {
  if (isFollowUpOverdue(left) !== isFollowUpOverdue(right)) return isFollowUpOverdue(left) ? -1 : 1
  const priorityWeight: Record<string, number> = { urgent: 4, high: 3, normal: 2, low: 1 }
  const priorityDiff = (priorityWeight[right.priority] || 0) - (priorityWeight[left.priority] || 0)
  if (priorityDiff) return priorityDiff
  const leftTime = left.nextFollowUpAt ? Date.parse(left.nextFollowUpAt) : Number.MAX_SAFE_INTEGER
  const rightTime = right.nextFollowUpAt ? Date.parse(right.nextFollowUpAt) : Number.MAX_SAFE_INTEGER
  return leftTime - rightTime
}

function workOrderRowClassName({ row }: { row: AccountAuthWorkOrder }) {
  return isFollowUpOverdue(row) ? 'is-work-order-overdue' : ''
}

async function refreshActiveWorkOrderContext() {
  await refreshActiveWorkOrderContextSilently(false)
}

async function refreshActiveWorkOrderContextSilently(silent: boolean) {
  if (workOrderAutoRefreshing.value) return
  workOrderAutoRefreshing.value = true
  try {
    const wasResolvable = activeWorkOrderCanResolve.value
    const { data } = await getAccountAuthHealthOverview()
    overview.value = data.data
    lastWorkOrderRefreshAt.value = data.data?.generatedAt || new Date().toISOString()
    if (silent && !wasResolvable && activeWorkOrderCanResolve.value) {
      ElMessage.success('目标账号登录状态已恢复，可以处理结束')
    }
    if (!silent) ElMessage.success('当前账号风险已刷新')
  } finally {
    workOrderAutoRefreshing.value = false
  }
}

async function openActiveWorkOrderEnvironment(platform?: string | null) {
  if (!activeWorkOrder.value) return
  environmentOpening.value = true
  environmentOpenIssue.value = null
  try {
    const brandId = activeWorkOrder.value.brandId
    const [{ data: environmentResponse }, { data: readinessResponse }] = await Promise.all([
      listBrowserEnvironments(brandId),
      getSelfMediaAutomationReadiness(brandId),
    ])
    const environment = (environmentResponse.data || []).find((item) => item.status === 'active')
    if (!environment?.environmentKey || !environment.providerProfileId) {
      environmentOpenIssue.value = {
        kind: 'environment',
        title: '品牌环境尚未配置',
        message: '请先在品牌详情中绑定可用的 AdsPower 环境。',
        actionLabel: '前往品牌配置',
      }
      return
    }
    const openResult = await openLocalHelperEnvironment({
      helperBase: LOCAL_HELPER_BASE,
      localAgentSessionId: readinessResponse.data?.localAgent.sessionId || null,
    }, {
      environmentKey: environment.environmentKey,
      providerProfileId: environment.providerProfileId,
      environmentName: environment.name || environment.environmentKey,
      url: platformLoginUrl(platform),
    })
    if (openResult?.extensionStatus && !openResult.extensionStatus.detected) {
      environmentOpenIssue.value = {
        kind: 'extension',
        title: '账号环境已打开，但扩展尚未就绪',
        message: '请确认该环境中的 GEO 自媒体助手扩展已启用并完成绑定，然后重新检测登录状态。',
        actionLabel: '重新检测',
      }
    }
    ElMessage.success('已打开账号环境，完成登录后系统会自动刷新状态')
  } catch (error) {
    const message = error instanceof Error ? error.message : '打开账号环境失败'
    environmentOpenIssue.value = environmentOpenRecoveryFor(message)
    ElMessage.error(message)
  } finally {
    environmentOpening.value = false
  }
}

function platformLoginUrl(platform?: string | null) {
  const normalized = String(platform || '').trim().toLowerCase()
  const urls: Record<string, string> = {
    douyin: 'https://creator.douyin.com/creator-micro/home',
    xiaohongshu: 'https://creator.xiaohongshu.com/',
    zhihu: 'https://www.zhihu.com/creator',
    toutiao: 'https://mp.toutiao.com/profile_v4',
    baijiahao: 'https://baijiahao.baidu.com/',
  }
  return urls[normalized] || null
}

function environmentOpenRecoveryFor(message: string): EnvironmentOpenIssue {
  if (/配对|认证已失效/.test(message)) return { kind: 'configuration', title: '本地助手需要重新配对', message, actionLabel: '前往本地助手配置' }
  if (/API Key|AdsPower 连接配置/.test(message)) return { kind: 'configuration', title: 'AdsPower 连接尚未配置', message, actionLabel: '前往本地助手配置' }
  if (/扩展/.test(message)) return { kind: 'extension', title: '浏览器扩展未就绪', message, actionLabel: '重新检测' }
  if (/无法连接本地助手|本地助手请求加载失败|本地助手已启动/.test(message)) return { kind: 'helper', title: '本地助手未连接', message, actionLabel: '重新检测' }
  return { kind: 'retry', title: '账号环境打开失败', message, actionLabel: '重新尝试' }
}

async function handleEnvironmentRecovery() {
  const issue = environmentOpenIssue.value
  if (!issue) return
  if (issue.kind === 'configuration') {
    await router.push(userStore.isPartner ? '/partner/profile' : '/admin/profile')
    return
  }
  if (issue.kind === 'environment' && activeWorkOrder.value) {
    await router.push(`/admin/brands/${activeWorkOrder.value.brandId}`)
    return
  }
  if (issue.kind === 'extension') {
    await refreshActiveWorkOrderContext()
    return
  }
  await openActiveWorkOrderEnvironment()
}

function setFollowUpShortcut(kind: 'one_hour' | 'end_of_day' | 'tomorrow_morning') {
  const target = new Date()
  if (kind === 'one_hour') target.setHours(target.getHours() + 1)
  if (kind === 'end_of_day') target.setHours(18, 0, 0, 0)
  if (kind === 'tomorrow_morning') {
    target.setDate(target.getDate() + 1)
    target.setHours(9, 30, 0, 0)
  }
  if (kind === 'end_of_day' && target.getTime() <= Date.now()) target.setDate(target.getDate() + 1)
  workOrderForm.value.nextFollowUpAt = formatLocalDateTimeValue(target)
}

function formatLocalDateTimeValue(value: Date) {
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
}

async function handleRiskItemAction(row: AccountAuthHealthRiskItem) {
  if (row.targetType !== 'self_media' || !row.brandId || !requiresLoginHandling(row)) {
    openRoute(row.actionRoute)
    return
  }
  const environment = overview.value?.brandEnvironments.find((item) => item.brandId === row.brandId)
  if (environment) await handleEnvironmentWorkOrder(environment)
}

async function openWorkOrderFromRoute() {
  const brandId = Number(route.query.brandId || 0)
  if (!brandId) return
  const environment = overview.value?.brandEnvironments.find((item) => item.brandId === brandId)
  if (!environment) return
  let row = workOrderByBrand(brandId)
  if (!row && !hasEnvironmentLoginRequirement(environment)) {
    ElMessage.info('当前问题不需要创建账号登录待办，请按页面建议完成内部核验')
    const nextQuery = { ...route.query }
    delete nextQuery.brandId
    await router.replace({ path: route.path, query: nextQuery })
    return
  }
  if (!row || (row.status === 'resolved' && hasEnvironmentLoginRequirement(environment))) {
    const { data } = await getOrCreateAccountAuthWorkOrder({
      brandId,
      browserEnvironmentId: environment.browserEnvironmentId,
      issueSummary: `${environment.loginIssueCount} 项登录异常，${environment.unverifiedCount} 项待验证，${environment.reverifyOverdueCount} 项复验超期`,
    })
    row = data.data
    await loadWorkOrders()
  }
  if (row) await openWorkOrder(row)
  const nextQuery = { ...route.query }
  delete nextQuery.brandId
  await router.replace({ path: route.path, query: nextQuery })
}

function workOrderBrandName(brandId: number) {
  return overview.value?.brandEnvironments.find((item) => item.brandId === brandId)?.brandName || `品牌 ${brandId}`
}

function workOrderStatusLabel(status: string, ownerUserId?: number | null) {
  if (status === 'resolved') return '处理结束'
  if (status === 'system_diagnosis' || ownerUserId === null) return '账号需要登录'
  return '运营人员处理'
}

function workOrderStatusTag(status: string, ownerUserId?: number | null): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'resolved') return 'success'
  if (status === 'system_diagnosis' || ownerUserId === null) return 'danger'
  return 'warning'
}

function exportRiskItems() {
  const rows = filteredRiskItems.value
  const cells = [
    ['公司', '品牌', '平台', '账号', '登录事实', '复验计划', '建议复验时间', '负责人', '处理建议'],
    ...rows.map((row) => [row.companyName, row.brandName, row.platformLabel, row.displayName,
      loginFactLabel(row.loginFactStatus, row.targetType), reverifyLabel(row.reverifyStatus),
      row.expiresAt ? formatDateTime(row.expiresAt) : '', row.ownerName, row.actionHint]),
  ]
  const csv = `\uFEFF${cells.map((row) => row.map((cell) => `"${String(cell || '').replace(/"/g, '""')}"`).join(',')).join('\r\n')}`
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `账号授权健康-${new Date().toISOString().slice(0, 10)}.csv`
  anchor.click()
  URL.revokeObjectURL(url)
}

async function refreshScan() {
  refreshing.value = true
  try {
    const { data } = await refreshAccountAuthHealthOverview()
    ElMessage.success(data.data?.message || '扫描已在后台启动')
    window.setTimeout(() => load(), 1200)
  } finally {
    refreshing.value = false
  }
}

function openRoute(route?: string | null) {
  if (!route) return
  router.push(route)
}

function objectSubtitle(row: AccountAuthHealthRiskItem) {
  if (row.targetType === 'forum') return row.companyName || row.platformLabel || '论坛站点'
  return [row.companyName, row.brandName, row.platformLabel].filter(Boolean).join(' / ') || '-'
}

function loginCheckText(row: AccountAuthHealthRiskItem) {
  if (row.lastLoginCheckResult === 'failed') {
    return row.lastLoginCheckedAt ? `最近检测失败：${formatDateTime(row.lastLoginCheckedAt)}` : '最近检测失败'
  }
  if (row.lastLoginCheckResult === 'success') return '最近检测成功'
  return '尚无登录检测记录'
}

function riskLabel(status?: string | null) {
  if (status === 'expired') return '复验超期'
  if (status === 'reverify_overdue') return '复验超期'
  if (status === 'missing') return '缺少凭据'
  if (status === 'expiring') return '即将复验'
  if (status === 'reverify_due_soon') return '即将复验'
  if (status === 'credential_missing') return '待验证登录'
  if (status === 'login_verification_failed') return '最近检测失败'
  if (status === 'monitoring_disabled') return '未启用监控'
  if (status === 'unknown') return '到期未知'
  if (status === 'normal') return '正常'
  return status || '-'
}

function loginFactLabel(status?: string | null, targetType?: string | null) {
  if (targetType === 'forum' || status === 'not_applicable') return '凭据管理'
  if (status === 'confirmed_logged_in') return '已确认登录'
  if (status === 'official_authorized') return '官方授权有效'
  if (status === 'authorization_missing') return '缺少官方授权'
  if (status === 'verification_failed') return '检测待内部核对'
  if (status === 'account_mismatch') return '账号名称待核对'
  if (status === 'login_required') return '需要重新登录'
  if (status === 'environment_error') return '环境异常'
  if (status === 'unbound') return '未绑定环境'
  return '尚未验证'
}

function loginFactTag(status?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'confirmed_logged_in' || status === 'official_authorized') return 'success'
  if (['verification_failed', 'account_mismatch', 'login_required', 'environment_error', 'authorization_missing'].includes(String(status))) return 'danger'
  if (status === 'unverified' || status === 'unbound') return 'warning'
  return 'info'
}

function reverifyLabel(status?: string | null) {
  if (status === 'within_reference_window') return '参考周期内'
  if (status === 'due_soon') return '即将需要复验'
  if (status === 'overdue') return '复验超期'
  if (status === 'monitoring_disabled') return '未启用监控'
  return '时间未知'
}

function reverifyTag(status?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'within_reference_window') return 'success'
  if (status === 'due_soon' || status === 'overdue') return 'warning'
  return 'info'
}

function scanStatusLabel(status?: string | null) {
  if (status === 'succeeded') return '完整成功'
  if (status === 'partial_success') return '部分成功'
  if (status === 'failed') return '扫描失败'
  if (status === 'running') return '扫描中'
  return status || '未知'
}

function scanStatusTag(status?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'succeeded') return 'success'
  if (status === 'partial_success' || status === 'running') return 'warning'
  if (status === 'failed') return 'danger'
  return 'info'
}

function riskTag(status?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'expired' || status === 'missing' || status === 'credential_missing' || status === 'login_verification_failed') return 'danger'
  if (status === 'expiring' || status === 'reverify_due_soon' || status === 'reverify_overdue') return 'warning'
  if (status === 'normal') return 'success'
  return 'info'
}

function severityTag(severity?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (severity === 'critical' || severity === 'high' || severity === 'error') return 'danger'
  if (severity === 'warn') return 'warning'
  return 'info'
}

function severityClass(severity?: string | null) {
  if (severity === 'critical' || severity === 'high' || severity === 'error') return 'is-danger'
  if (severity === 'warn') return 'is-warning'
  return 'is-info'
}

function daysText(days?: number | null) {
  if (days === null || days === undefined) return '未记录到期'
  if (days < 0) return `复验超期 ${Math.abs(days)} 天`
  if (days === 0) return '今天到期'
  return `还剩 ${days} 天`
}

function shortDate(value: string) {
  const parts = value.split('-')
  return parts.length === 3 ? `${parts[1]}/${parts[2]}` : value
}

function trendWidth(value: number, max: number) {
  if (!value) return '0%'
  return `${Math.max(8, Math.round((value / max) * 100))}%`
}

onMounted(load)

watch(workOrderDialogVisible, (visible) => {
  if (workOrderRefreshTimer !== null) {
    window.clearInterval(workOrderRefreshTimer)
    workOrderRefreshTimer = null
  }
  if (visible && activeWorkOrder.value?.status !== 'resolved') {
    workOrderRefreshTimer = window.setInterval(() => refreshActiveWorkOrderContextSilently(true), 10_000)
  }
})

watch([workOrderFilter, onlyMyWorkOrders, workOrderKeyword], ([filter, onlyMine, keyword]) => {
  window.sessionStorage.setItem(WORK_ORDER_PREFERENCES_KEY, JSON.stringify({ filter, onlyMine, keyword }))
})

watch(onlyMyWorkOrders, () => {
  workOrderPage.value = 1
  void loadWorkOrders()
})

onBeforeUnmount(() => {
  if (workOrderRefreshTimer !== null) window.clearInterval(workOrderRefreshTimer)
})
</script>

<style scoped>
.account-health-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.generated-at,
.table-subtext {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.is-primary-workspace {
  border-color: var(--el-color-primary-light-7);
  box-shadow: 0 8px 24px rgb(37 99 235 / 8%);
}

.system-details-collapse {
  border: 0;
}

.system-details-title {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.system-details-title span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.system-details-collapse :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 54px;
  padding: 0 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.system-details-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
}

.system-details-collapse :deep(.el-collapse-item__content) {
  display: grid;
  gap: 16px;
  padding: 16px 0 0;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  padding: 16px;
}

.metric-panel span,
.metric-panel small {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.metric-panel strong {
  display: block;
  margin: 8px 0 4px;
  color: var(--el-text-color-primary);
  font-size: 28px;
  line-height: 1;
}

.metric-panel.is-danger {
  border-color: #fecaca;
  background: #fff7f7;
}

.metric-panel.is-warning {
  border-color: #fde68a;
  background: #fffaf0;
}

.metric-panel.is-success {
  border-color: #bbf7d0;
  background: #f6fef9;
}

.scan-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
}

.scan-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-extra-light);
}

.scan-item-head,
.environment-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.scan-item span,
.scan-item small,
.environment-card small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.scan-error {
  color: var(--el-color-danger) !important;
}

.version-coverage-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.version-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.environment-toolbar {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) 180px auto;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.environment-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}

.environment-card {
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-bg-color);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.environment-card:hover,
.environment-card:focus-visible {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 5px 18px rgb(37 99 235 / 10%);
  outline: none;
}

.environment-card-head strong,
.environment-card-head small {
  display: block;
}

.environment-name {
  margin: 14px 0;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.environment-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.environment-stats strong {
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.environment-card-actions,
.work-order-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.work-order-owner-alert,
.work-order-ready-alert,
.credential-safety-alert {
  margin-bottom: 18px;
}

.work-order-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.work-order-metrics button {
  display: grid;
  gap: 4px;
  padding: 12px;
  color: var(--el-text-color-regular);
  text-align: left;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  cursor: pointer;
}

.work-order-metrics button.active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.work-order-metrics strong {
  font-size: 22px;
}

.work-order-metrics small,
.work-order-toolbar span,
.work-order-live-head span,
.work-order-account-row span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.work-order-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.work-order-toolbar-options {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
}

.work-order-toolbar .el-input {
  max-width: 360px;
}

.work-order-summary,
.work-order-summary-row {
  display: grid;
  gap: 5px;
}

.work-order-summary-row {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.work-order-summary small,
.work-order-summary.is-resolved small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.work-order-summary.is-resolved strong {
  color: var(--el-color-success);
}

.work-order-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.is-follow-up-overdue {
  color: var(--el-color-danger);
  font-weight: 600;
}

.work-order-live-context {
  margin-bottom: 20px;
  padding: 14px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.work-order-live-head,
.work-order-account-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.work-order-account-row.is-login-required {
  border: 1px solid var(--el-color-danger-light-7);
  background: var(--el-color-danger-light-9);
}

.work-order-live-head > div:first-child,
.work-order-account-row > div:first-child {
  display: grid;
  gap: 4px;
}

.work-order-account-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.work-order-account-row {
  padding: 10px 12px;
  background: var(--el-bg-color);
  border-radius: 8px;
}

.work-order-account-row > div:last-child,
.work-order-account-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.normal-account-toggle {
  justify-self: start;
}

.environment-open-issue {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
  padding: 12px 14px;
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: 8px;
  background: var(--el-color-warning-light-9);
}

.environment-open-issue > div {
  display: grid;
  gap: 3px;
}

.environment-open-issue span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.credential-safety-hint {
  width: 100%;
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.credential-safety-hint.is-danger {
  color: var(--el-color-danger);
  font-weight: 600;
}

.follow-up-editor {
  display: grid;
  gap: 8px;
  width: 100%;
}

.follow-up-shortcuts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.follow-up-shortcuts .el-button + .el-button {
  margin-left: 0;
}

.work-order-more-settings {
  margin-bottom: 18px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.work-order-more-settings :deep(.el-collapse-item__content) {
  padding-bottom: 0;
}

.special-resolution-alert {
  margin-bottom: 16px;
}

.special-resolution-accounts {
  display: grid;
  gap: 8px;
  width: 100%;
}

.special-resolution-accounts .el-checkbox {
  margin-right: 0;
}

.special-resolution-hint {
  margin: 6px 0 0;
}

.latest-note {
  width: 100%;
  padding: 10px 12px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
  white-space: pre-wrap;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.environment-card-actions {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-extra-light);
}

.form-hint {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 0.9fr);
  gap: 12px;
}

.panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  padding: 16px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-head strong {
  display: block;
  font-size: 15px;
}

.panel-head span {
  display: block;
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.trend-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.trend-row {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) 36px;
  align-items: center;
  gap: 10px;
}

.trend-date {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.trend-track {
  display: flex;
  height: 12px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.trend-bar.self-media {
  background: #2563eb;
}

.trend-bar.forum {
  background: #f59e0b;
}

.trend-legend {
  display: flex;
  gap: 16px;
  margin-top: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.legend-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 2px;
}

.legend-dot.self-media {
  background: #2563eb;
}

.legend-dot.forum {
  background: #f59e0b;
}

.alert-group-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.alert-group-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  padding: 10px;
  text-align: left;
  cursor: pointer;
}

.alert-group-row:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.severity-dot {
  width: 8px;
  height: 34px;
  border-radius: 4px;
  background: var(--el-color-info);
}

.severity-dot.is-danger {
  background: var(--el-color-danger);
}

.severity-dot.is-warning {
  background: var(--el-color-warning);
}

.alert-group-main {
  min-width: 0;
}

.alert-group-main strong,
.alert-group-main small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-group-main small {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
}

.filter-tabs {
  min-width: 300px;
}

.entity-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.entity-avatar {
  display: grid;
  width: 34px;
  height: 34px;
  flex: none;
  place-items: center;
  border-radius: 8px;
  font-weight: 700;
}

.entity-avatar.is-self {
  background: #eff6ff;
  color: #1d4ed8;
}

.entity-avatar.is-forum {
  background: #fffbeb;
  color: #b45309;
}

.entity-main,
.entity-sub {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.entity-main {
  font-weight: 600;
}

.entity-sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.min-w-0 {
  min-width: 0;
}

.work-order-timeline {
  display: grid;
  gap: 10px;
  width: 100%;
  max-height: 220px;
  overflow-y: auto;
}

.work-order-event {
  display: grid;
  gap: 2px;
  padding: 8px 10px;
  border-left: 3px solid var(--el-color-primary-light-5);
  background: var(--el-fill-color-light);
}

.work-order-event span,
.work-order-event small {
  color: var(--el-text-color-secondary);
  line-height: 1.45;
}

@media (max-width: 1100px) {
  .metric-grid,
  .overview-grid {
    grid-template-columns: 1fr 1fr;
  }

  .work-order-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .metric-grid,
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .environment-toolbar {
    grid-template-columns: 1fr;
  }

  .page-actions,
  .panel-head {
    align-items: stretch;
    flex-direction: column;
  }

  .work-order-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .work-order-live-head,
  .work-order-account-row,
  .work-order-toolbar,
  .work-order-toolbar-options {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
