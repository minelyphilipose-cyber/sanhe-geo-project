<template>
  <div class="admin-page">
    <el-page-header content="品牌详情" @back="$router.back()" />

    <section v-if="brand" class="admin-object-hero">
      <div class="admin-object-hero-main">
        <div>
          <h1 class="admin-object-title">{{ brand.brandName }}</h1>
          <div class="admin-object-meta">
            {{ companyName || '-' }} · {{ industryLabel(brand.industry) }} · {{ complianceIndustryLabel(brand.complianceIndustryCode) }}
          </div>
        </div>
        <span class="admin-status-tag" :class="brand?.status === 'active' ? 'is-success' : 'is-muted'">
          {{ dictStore.label('brand_status', brand?.status) || '-' }}
        </span>
      </div>
      <div class="admin-object-kpis">
        <div class="admin-object-kpi">
          <span>品牌行业</span>
          <strong>{{ industryLabel(brand.industry) }}</strong>
        </div>
        <div class="admin-object-kpi">
          <span>行业合规类型</span>
          <strong>{{ complianceIndustryLabel(brand.complianceIndustryCode) }}</strong>
        </div>
        <div class="admin-object-kpi">
          <span>自媒体账号</span>
          <strong>{{ semiAutoSelfMediaAccounts.length }}</strong>
        </div>
      </div>
    </section>

    <el-card v-loading="loading" class="admin-rich-card brand-detail-card">
      <template #header>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span>基础信息</span>
            <el-tag>{{ dictStore.label('brand_status', brand?.status) }}</el-tag>
          </div>
          <div class="space-x-2">
            <el-button v-if="brand?.companyId" link @click="goCompanyDetail">查看客户</el-button>
            <el-button
              v-if="brand?.geoSiteDomain"
              link
              :loading="geoSiteTesting"
              @click="testCurrentGeoSite"
            >
              测试 Agent 官网
            </el-button>
            <el-button v-if="canCreateProject" type="primary" link @click="goCreateProject">基于该品牌建项目</el-button>
            <el-button v-if="canUpdateBrand" type="primary" link @click="openEdit">编辑</el-button>
            <el-button v-if="canDeleteBrand" type="danger" link @click="removeCurrentBrand">删除品牌</el-button>
          </div>
        </div>
      </template>

      <div class="brand-detail-sections">
        <section class="brand-detail-section">
          <div class="brand-section-bar"><span />基础资料<i /></div>
          <div class="brand-info-grid">
            <div v-for="item in brandCoreInfoItems" :key="item.label" class="brand-info-item">
              <span class="brand-info-label">{{ item.label }}</span>
              <strong class="brand-info-value">{{ item.value }}</strong>
            </div>
          </div>
        </section>
        <section class="brand-detail-section">
          <div class="brand-section-bar"><span />联系方式<i /></div>
          <div class="brand-info-grid">
            <div v-for="item in brandContactInfoItems" :key="item.label" class="brand-info-item">
              <span class="brand-info-label">{{ item.label }}</span>
              <strong class="brand-info-value">{{ item.value }}</strong>
            </div>
          </div>
        </section>
        <section class="brand-detail-section">
          <div class="brand-section-bar"><span />总部交付配置<i /></div>
          <div class="brand-info-grid">
            <div v-for="item in brandDeliveryConfigItems" :key="item.label" class="brand-info-item">
              <span class="brand-info-label">{{ item.label }}</span>
              <strong class="brand-info-value">{{ item.value }}</strong>
            </div>
          </div>
        </section>
        <section class="brand-detail-section">
          <div class="brand-section-bar"><span />业务介绍与内容约束<i /></div>
          <div class="brand-info-grid">
            <div
              v-for="item in brandTextInfoItems"
              :key="item.label"
              class="brand-info-item is-wide"
            >
              <span class="brand-info-label">{{ item.label }}</span>
              <strong class="brand-info-value">{{ item.value }}</strong>
            </div>
          </div>
        </section>
      </div>
    </el-card>

    <el-card v-if="canUpdateBrand" class="admin-table-card">
      <template #header>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span>产品信息</span>
            <el-tag type="info">产品 / 服务项目 / 特色业务项</el-tag>
          </div>
          <el-button v-if="canUpdateBrand" type="primary" link @click="openOfferingCreate">新增产品</el-button>
        </div>
      </template>
      <el-alert
        class="mb-3"
        type="info"
        show-icon
        :closable="false"
        title="维护品牌下可公开引用的业务产品、服务项目或特色业务项。文章生成时会按主题先选中少量相关条目，再把精简资料注入提示词。"
      />
      <el-alert
        v-if="isMedicalComplianceIndustry && !hasEnabledMedicalProject"
        class="mb-3"
        type="warning"
        show-icon
        :closable="false"
        title="当前品牌已标记为特殊行业，但尚未启用匹配的特殊行业项目。特殊行业文章生成会被项目资质闸门拦截，请至少维护一个启用状态的项目。"
      />
      <el-table v-loading="offeringsLoading" :data="offerings" border empty-text="暂无产品信息">
        <el-table-column prop="offeringName" label="产品名称" min-width="160" />
        <el-table-column label="产品简称" min-width="160">
          <template #default="{ row }">{{ offeringAliasesText(row) }}</template>
        </el-table-column>
        <el-table-column prop="targetUsers" label="目标人群" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.targetUsers || '-' }}</template>
        </el-table-column>
        <el-table-column prop="useScenarios" label="适用场景" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.useScenarios || '-' }}</template>
        </el-table-column>
        <el-table-column label="特殊行业项目" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.medicalProjectEnabled" size="small" type="warning">
              {{ row.medicalCategoryName || row.medicalCategoryCode || '已启用' }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ row.updatedAt || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="canUpdateBrand" label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openOfferingEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeOffering(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="admin-table-card">
      <template #header>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span>指纹浏览器环境</span>
            <el-tag type="info">AdsPower</el-tag>
          </div>
          <div v-if="canUpdateBrand" class="flex items-center gap-2">
            <el-button type="primary" link :loading="adspowerProfilesLoading" @click="openAdspowerProfileImport">
              从本机 AdsPower 导入
            </el-button>
            <el-button type="primary" link @click="openBrowserEnvironmentPrimaryAction">
              {{ browserEnvironments.length ? '编辑环境' : '配置环境' }}
            </el-button>
          </div>
        </div>
      </template>
      <el-alert
        class="mb-3"
        type="info"
        show-icon
        :closable="false"
        title="同一品牌默认使用一个 AdsPower 浏览器环境。新增头条、百家号、知乎、小红书账号时会自动绑定当前启用环境；AdsPower API Key 在「个人中心 > 本地助手」配置。"
      />
      <div class="automation-readiness" v-loading="automationReadinessLoading">
        <div class="automation-readiness__head">
          <div>
            <div class="automation-readiness__title">自动化就绪状态</div>
            <div class="automation-readiness__desc">{{ automationReadinessSummary }}</div>
          </div>
          <div class="flex items-center gap-2">
            <el-tag :type="automationReadinessTagType" round>{{ automationReadinessStatusText }}</el-tag>
            <el-button link type="primary" :loading="automationReadinessLoading" @click="loadAutomationReadiness">刷新</el-button>
          </div>
        </div>
        <div class="automation-readiness__checks">
          <div class="automation-check" :class="{ 'is-ok': automationReadiness?.localAgent.online }">
            <span>本地助手</span>
            <strong>{{ automationReadiness?.localAgent.online ? '在线' : automationReadiness?.localAgent.bound ? '已绑定未在线' : '未绑定' }}</strong>
          </div>
          <div class="automation-check" :class="{ 'is-ok': automationReadiness?.browserEnvironment.active }">
            <span>AdsPower 环境</span>
            <strong>{{ automationReadiness?.browserEnvironment.active ? '已启用' : automationReadiness?.browserEnvironment.configured ? '未启用' : '未配置' }}</strong>
          </div>
          <div class="automation-check" :class="{ 'is-ok': automationReadiness?.extensionBinding.online }">
            <span>环境扩展</span>
            <strong>{{ adspowerExtensionStatusText }}</strong>
            <small v-if="adspowerExtensionDetailText">{{ adspowerExtensionDetailText }}</small>
          </div>
          <div class="automation-check" :class="{ 'is-ok': automationAccountReadyCount === automationAccountTotal && automationAccountTotal > 0 }">
            <span>平台账号</span>
            <strong>{{ automationAccountReadyCount }}/{{ automationAccountTotal }} 就绪</strong>
          </div>
        </div>
        <div v-if="automationReadiness?.issues?.length" class="automation-readiness__issues">
          <div v-for="issue in automationReadiness.issues.slice(0, 4)" :key="`${issue.code}-${issue.title}`" class="automation-issue">
            <el-tag size="small" :type="issue.level === 'error' ? 'danger' : 'warning'">
              {{ issue.level === 'error' ? '阻塞' : '提醒' }}
            </el-tag>
            <span>{{ issue.title }}</span>
            <em>{{ issue.action }}</em>
            <el-button
              v-if="issue.actionKey && canUpdateBrand"
              link
              type="primary"
              :loading="automationIssueActionLoading === issue.actionKey"
              @click="handleAutomationIssueAction(issue)"
            >
              继续处理
            </el-button>
          </div>
        </div>
      </div>
      <div v-if="canUpdateBrand" class="environment-toolbar">
        <div>
          <h3>指纹浏览器环境</h3>
          <p>扩展会话按环境名称归属到对应 AdsPower 环境，避免同一实例重复展示。</p>
        </div>
        <div class="flex items-center gap-2">
          <el-button :loading="extensionSessionsLoading" @click="refreshExtensionBindingStatus">刷新扩展状态</el-button>
          <el-button type="primary" :loading="extensionBindCodeLoading" @click="generateBrandExtensionBindCode">
            生成绑定码
          </el-button>
        </div>
      </div>

      <div v-if="extensionBindCode && canUpdateBrand" class="extension-bind-code-box">
        <div>
          <span>扩展绑定码</span>
          <strong>{{ extensionBindCode.code }}</strong>
          <small>{{ extensionBindCode.expiresInSeconds }} 秒内有效；请复制后在 AdsPower 环境扩展弹窗中绑定。</small>
        </div>
        <div class="flex items-center gap-2">
          <el-button plain :disabled="!defaultBrowserEnvironment" :loading="extensionEnvironmentOpening" @click="openDefaultEnvironmentForExtensionBinding">
            打开并自动绑定
          </el-button>
          <el-button type="primary" plain @click="copyBrandExtensionBindCode">复制绑定码</el-button>
        </div>
      </div>

      <el-table v-loading="browserEnvironmentsLoading || extensionSessionsLoading" :data="browserEnvironmentRows" border empty-text="暂无指纹浏览器环境">
        <el-table-column prop="name" label="环境名称" min-width="160">
          <template #default="{ row }">{{ row.name || row.environmentKey }}</template>
        </el-table-column>
        <el-table-column prop="environmentKey" label="环境代号" min-width="190">
          <template #default="{ row }">
            <div class="environment-key-cell">{{ row.environmentKey }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="providerProfileId" label="AdsPower 浏览器编号" min-width="190" />
        <el-table-column v-if="canUpdateBrand" label="扩展绑定" min-width="260">
          <template #default="{ row }">
            <div v-if="row.extensionSession" class="extension-session-inline">
              <el-tag size="small" :type="extensionSessionOnline(row.extensionSession) ? 'success' : 'warning'">
                {{ extensionSessionOnline(row.extensionSession) ? '已绑定' : '已绑定未活跃' }}
              </el-tag>
              <span>Session #{{ row.extensionSession.id }}</span>
              <small>
                v{{ row.extensionSession.extensionVersion || '-' }} · 最近活跃 {{ row.extensionSession.lastSeenAt || row.extensionSession.boundAt || '-' }}
              </small>
            </div>
            <div v-else class="extension-session-inline is-empty">
              <el-tag size="small" type="warning">未绑定</el-tag>
              <span>打开该环境后绑定 GEO 扩展</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="canUpdateBrand" label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openBrowserEnvironmentEdit(row)">编辑</el-button>
            <el-button v-if="row.extensionSession" link type="warning" @click="revokeBrandExtension(row.extensionSession)">解绑扩展</el-button>
            <el-button link type="danger" @click="removeBrowserEnvironment(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="canUpdateBrand && unmatchedExtensionSessions.length" class="unmatched-extension-sessions">
        <el-alert type="warning" :closable="false" show-icon>
          <template #title>
            有 {{ unmatchedExtensionSessions.length }} 个扩展会话未匹配到当前环境，请确认扩展绑定时使用的环境名称或 AdsPower 编号是否正确。
          </template>
        </el-alert>
        <div class="unmatched-extension-list">
          <span v-for="session in unmatchedExtensionSessions" :key="session.id">
            Session #{{ session.id }} · {{ session.environmentKey || '-' }} · {{ session.providerProfileId || '-' }}
          </span>
        </div>
      </div>
    </el-card>

    <el-card class="admin-table-card">
      <template #header>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span>官方 API 自媒体账号</span>
            <el-tag type="success">公众号</el-tag>
          </div>
          <div class="flex items-center gap-2">
            <el-button
              v-if="canUpdateBrand"
              type="primary"
              link
              :loading="wechatAuthorizing"
              @click="authorizeWechatMp"
            >
              {{ hasWechatSelfMediaAccount ? '重新授权公众号' : '授权公众号' }}
            </el-button>
          </div>
        </div>
      </template>
      <el-alert
        class="mb-3"
        type="info"
        show-icon
        :closable="false"
        title="官方 API 账号通过平台授权保存凭证；授权有效时文章可直接发送到客户对应平台账号。"
      />
      <el-table v-loading="selfMediaAccountsLoading" :data="officialApiSelfMediaAccounts" border empty-text="当前客户尚未授权官方 API 自媒体账号">
        <el-table-column prop="platform" label="平台" width="120">
          <template #default="{ row }">{{ selfMediaPlatformLabel(row.platform) }}</template>
        </el-table-column>
        <el-table-column prop="accountName" label="账号名称" min-width="180">
          <template #default="{ row }">
            <div class="flex items-center gap-2">
              <span>{{ row.accountName }}</span>
              <el-tag size="small" :type="selfMediaAccountIdentityTag(row.accountIdentity)">
                {{ selfMediaAccountIdentityLabel(row.accountIdentity) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="授权状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="officialAccountStatusTag(row)">
              {{ officialAccountStatusLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="菜单承接" min-width="260">
          <template #default="{ row }">
            <div v-if="isWechatMpAccount(row)" class="wechat-menu-status">
              <el-tag size="small" :type="wechatMenuStatusTag(row)">
                {{ wechatMenuStatusLabel(row) }}
              </el-tag>
              <span v-if="wechatMenuConfigOf(row)?.listPageUrl" class="wechat-menu-url">
                {{ wechatMenuConfigOf(row)?.listPageUrl }}
              </span>
              <span v-else class="wechat-menu-url is-muted">
                初始化后生成 H5 链接
              </span>
              <small v-if="wechatMenuConfigOf(row)?.lastSyncError" class="table-error-text">
                {{ wechatMenuConfigOf(row)?.lastSyncError }}
              </small>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastAuthCheckedAt" label="最近检测" min-width="180">
          <template #default="{ row }">{{ row.lastAuthCheckedAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="异常原因" min-width="240">
          <template #default="{ row }">
            <span :class="{ 'table-error-text': row.lastAuthError }">{{ row.lastAuthError || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="canUpdateBrand" label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="isWechatMpAccount(row)"
              link
              type="primary"
              :loading="wechatAuthorizing"
              @click="authorizeWechatMp"
            >
              重新授权
            </el-button>
            <el-button
              v-if="isWechatMpAccount(row)"
              link
              type="primary"
              :loading="wechatMenuInitializingId === row.id"
              @click="initializeWechatMenuForAccount(row)"
            >
              初始化菜单
            </el-button>
            <el-button link type="danger" @click="deleteSelfMediaAccountRecord(row)">
              删除记录
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="canUpdateBrand" class="admin-table-card">
      <template #header>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span>指纹浏览器自媒体账号</span>
            <el-tag type="info">头条 / 百家号 / 知乎 / 小红书 / 抖音图文</el-tag>
          </div>
          <div class="flex items-center gap-2">
            <el-button
              v-if="canUpdateBrand"
              plain
              :disabled="!defaultBrowserEnvironment || !syncableEnvironmentSelfMediaAccounts.length"
              :loading="loginStatusSyncing"
              @click="syncBrowserEnvironmentLoginStatus"
            >
              同步登录状态
            </el-button>
            <el-button
              v-if="canUpdateBrand"
              type="primary"
              link
              :disabled="!defaultBrowserEnvironment || !hasUnboundSemiAutoAccounts"
              :loading="environmentBindingSaving"
              @click="bindAllUnboundSemiAutoAccounts"
            >
              补齐环境绑定
            </el-button>
            <el-tooltip
              v-if="canUpdateBrand"
              :disabled="eligibleSelfMediaPlatformOptions.length > 0"
              content="当前客户套餐与排期能力暂无可新增的自媒体平台"
              placement="top"
            >
              <span>
                <el-button
                  type="primary"
                  link
                  :disabled="eligibleSelfMediaPlatformOptions.length === 0"
                  @click="openSelfMediaAccountCreate"
                >
                  新增账号
                </el-button>
              </span>
            </el-tooltip>
          </div>
        </div>
      </template>
      <el-alert
        class="mb-3"
        type="warning"
        show-icon
        :closable="false"
        :title="defaultBrowserEnvironment
          ? `当前默认环境：${browserEnvironmentOptionLabel(defaultBrowserEnvironment)}。新增账号将自动绑定该环境，绑定后需在对应平台完成登录，环境内扩展会自动上报登录状态。`
          : '请先配置并启用品牌 AdsPower 浏览器环境；新增账号后需要绑定环境并完成平台登录，扩展自动上报登录状态后才能分发。'"
      />
      <el-table v-loading="selfMediaAccountsLoading" :data="environmentSelfMediaAccounts" border empty-text="当前客户尚未配置需要指纹浏览器登录的自媒体账号">
        <el-table-column prop="platform" label="平台" width="110">
          <template #default="{ row }">{{ selfMediaPlatformLabel(row.platform) }}</template>
        </el-table-column>
        <el-table-column prop="accountName" label="账号名称" min-width="180">
          <template #default="{ row }">
            <div class="flex items-center gap-2">
              <span>{{ row.accountName }}</span>
              <el-tag size="small" :type="selfMediaAccountIdentityTag(row.accountIdentity)">
                {{ selfMediaAccountIdentityLabel(row.accountIdentity) }}
              </el-tag>
            </div>
            <div v-if="row.platformAccountId" class="table-subtext">
              {{ row.platform === 'baijiahao' ? '百家号 ID / app_id' : '平台账号 ID' }}：{{ row.platformAccountId }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="selfMediaAccountStatusTag(row)">
              {{ selfMediaAccountStatusLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="环境登录" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="browserEnvironmentLoginStatusTagType(row)">
              {{ browserEnvironmentLoginStatusLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="登录授权" min-width="170">
          <template #default="{ row }">
            <el-tag size="small" :type="cookieCredentialRiskTag(row)">
              {{ cookieCredentialRiskLabel(row) }}
            </el-tag>
            <div v-if="row.cookieCredentialExpiresAt" class="table-subtext">
              到期：{{ row.cookieCredentialExpiresAt }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最近上报" min-width="210">
          <template #default="{ row }">
            <div>{{ browserEnvironmentLastReportTime(row) }}</div>
            <div
              v-if="browserEnvironmentLoginStatusReason(row)"
              class="table-subtext"
              :class="{ 'table-error-text': browserEnvironmentLoginStatusIsProblem(row) }"
            >
              {{ browserEnvironmentLoginStatusReason(row) }}
            </div>
            <div v-if="browserEnvironmentAccountOf(row)?.environmentKey" class="table-subtext">
              已绑定：{{ browserEnvironmentAccountOf(row)?.environmentKey }}
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="canUpdateBrand" label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSelfMediaAccountEdit(row)">编辑</el-button>
            <el-button
              link
              type="primary"
              :loading="checkingSelfMediaAccountId === row.id"
              @click="checkSelfMediaAuth(row)"
            >
              重新检测
            </el-button>
            <el-button
              v-if="!browserEnvironmentAccountOf(row)"
              link
              type="primary"
              @click="openEnvironmentBindingDialog(row)"
            >
              绑定环境
            </el-button>
            <el-button
              v-if="browserEnvironmentAccountOf(row)"
              link
              type="warning"
              @click="resetEnvironmentAccountIdentity(row)"
            >
              重置校验
            </el-button>
            <el-button
              v-if="browserEnvironmentAccountOf(row)"
              link
              type="danger"
              @click="unbindEnvironmentAccount(row)"
            >
              解绑
            </el-button>
            <el-button
              v-if="!browserEnvironmentAccountOf(row)"
              link
              type="danger"
              @click="deleteSelfMediaAccountRecord(row)"
            >
              删除记录
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="admin-table-card">
      <template #header>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span>文章视角配置</span>
            <el-tag type="info">按渠道覆盖</el-tag>
          </div>
          <el-button v-if="canUpdateBrand" type="primary" link @click="openPerspectiveConfigCreate">新增配置</el-button>
        </div>
      </template>
      <el-table v-loading="perspectiveConfigLoading" :data="perspectiveConfigs" border empty-text="未配置时默认使用客户视角">
        <el-table-column label="渠道大类" min-width="140">
          <template #default="{ row }">{{ channelGroupLabel(row.channelGroupCode) }}</template>
        </el-table-column>
        <el-table-column label="渠道小类" min-width="140">
          <template #default="{ row }">{{ channelSubLabel(row.channelGroupCode, row.channelSubCode) }}</template>
        </el-table-column>
        <el-table-column label="写作视角" min-width="160">
          <template #default="{ row }">
            <el-tag>{{ row.perspectiveName || perspectiveLabel(row.perspectiveCode) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="canUpdateBrand" label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPerspectiveConfigEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removePerspectiveConfig(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="brand && hasThirdPartySelfMediaPerspective" v-loading="subjectPoolLoading" class="admin-table-card">
      <template #header>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span>第三方主体池预览</span>
            <el-tag :type="subjectPool?.validSource ? 'success' : 'warning'">
              {{ subjectPoolStatusText }}
            </el-tag>
            <el-tag v-if="subjectPool?.unavailableCount" type="danger">失效 {{ subjectPool.unavailableCount }}</el-tag>
          </div>
          <el-button link type="primary" :loading="subjectPoolLoading" @click="loadSubjectPool">刷新页面数据</el-button>
        </div>
      </template>
      <div v-if="subjectPool" class="subject-pool-wrap">
        <el-form label-position="top" class="subject-pool-config">
          <el-form-item label="信源可覆盖行业">
            <el-select
              v-model="subjectPoolDraftIndustries"
              multiple
              clearable
              filterable
              allow-create
              default-first-option
              placeholder="选择或输入覆盖行业"
              style="width: 100%"
            >
              <el-option label="全部行业" value="__ALL__" />
              <el-option
                v-for="tag in availableBrandIndustries"
                :key="tag"
                :label="industryLabel(tag)"
                :value="tag"
              />
            </el-select>
          </el-form-item>
          <div class="subject-pool-actions">
            <el-button
              type="primary"
              :loading="subjectPoolSuggesting"
              :disabled="!subjectPoolDraftIndustries.length"
              @click="generateSubjectPoolSuggestion(subjectPool?.confirmed ? 'incremental' : 'initial')"
            >
              {{ subjectPool?.confirmed ? '刷新覆盖' : '生成覆盖品牌' }}
            </el-button>
            <el-button
              type="success"
              :loading="subjectPoolSaving"
              :disabled="!subjectPoolDraftIndustries.length"
              @click="confirmSubjectPool"
            >
              确认主体池
            </el-button>
            <span v-if="subjectPool?.lastConfirmedAt" class="subject-pool-limit-note">
              最近确认：{{ subjectPool.lastConfirmedAt }}
            </span>
          </div>
        </el-form>
        <el-alert
          v-if="subjectPool?.llmFailed"
          type="warning"
          show-icon
          :closable="false"
          :title="subjectPool.llmFailureMessage || '模型匹配失败，可手动选择主体'"
        />
        <div class="subject-pool-summary">
          <div>
            <span>信源可覆盖行业</span>
            <strong>{{ subjectPoolDraftCoverageText }}</strong>
          </div>
          <div>
            <span>候选主体</span>
            <strong>{{ subjectPoolDraftItems.length }}</strong>
          </div>
          <div>
            <span>排除品牌</span>
            <strong>{{ subjectPool.excludedCount }}</strong>
          </div>
        </div>
        <el-alert
          v-if="!subjectPoolDraftIndustries.length"
          type="warning"
          show-icon
          :closable="false"
          title="当前品牌已启用第三方自媒体视角，但还没有配置可覆盖行业。"
        >
          <div class="subject-pool-warning">
            <span>请在编辑品牌中选择“信源覆盖行业”，否则不会进入主体轮换。</span>
            <el-button v-if="canUpdateBrand" link type="primary" @click="openCoverableIndustryConfig">
              去配置覆盖行业
            </el-button>
          </div>
        </el-alert>
        <div class="subject-pool-manual-add">
          <el-select
            v-model="subjectPoolManualAddIds"
            multiple
            filterable
            clearable
            placeholder="从合格品牌中手动添加"
            style="width: 420px; max-width: 100%"
          >
            <el-option
              v-for="item in subjectPoolManualOptions"
              :key="item.brandId"
              :label="`${item.brandName || item.brandId}（${industryLabel(item.industry)}）`"
              :value="item.brandId"
            />
          </el-select>
          <el-button :disabled="!subjectPoolManualAddIds.length" @click="addManualSubjectPoolItems">添加</el-button>
        </div>
        <el-table :data="subjectPoolDraftItems" border empty-text="暂无可轮换主体">
          <el-table-column label="候选品牌" min-width="180">
            <template #default="{ row }">
              <div class="detail-task-cell">
                <span>{{ row.brandName || row.brandId }}</span>
                <small>{{ row.companyName || '-' }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="行业" width="140">
            <template #default="{ row }">{{ industryLabel(row.industry) }}</template>
          </el-table-column>
          <el-table-column label="主体项目" width="120">
            <template #default="{ row }">{{ row.subjectProjectId || '-' }}</template>
          </el-table-column>
          <el-table-column label="来源" width="120">
            <template #default="{ row }">{{ subjectPoolMatchSourceLabel(row.matchSource) }}</template>
          </el-table-column>
          <el-table-column label="最近选中" width="180">
            <template #default="{ row }">{{ row.lastSelectedAt || '从未选中' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.available === false ? 'danger' : 'success'">
                {{ row.available === false ? '已失效' : '可用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="danger" @click="removeSubjectPoolItem(row.brandId)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-collapse v-if="subjectPool.excluded.length" class="subject-pool-excluded">
          <el-collapse-item :title="`查看排除项 ${subjectPool.excludedDisplayCount}/${subjectPool.excludedCount}`" name="excluded">
            <div v-if="subjectPool.excludedDisplayCount < subjectPool.excludedCount" class="subject-pool-limit-note">
              排除项较多，当前仅展示前 {{ subjectPool.excludedDisplayCount }} / {{ subjectPool.excludedCount }} 条。
            </div>
            <el-table :data="subjectPool.excluded" border>
              <el-table-column label="品牌" min-width="180">
                <template #default="{ row }">{{ row.brandName || row.brandId }}</template>
              </el-table-column>
              <el-table-column label="行业" width="140">
                <template #default="{ row }">{{ industryLabel(row.industry) }}</template>
              </el-table-column>
              <el-table-column label="排除原因" min-width="240">
                <template #default="{ row }">{{ row.reason || row.reasonCode || '-' }}</template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </div>
      <el-empty v-else description="暂无主体池数据" />
    </el-card>

    <el-card class="admin-rich-card">
      <template #header><span>扩展入口</span></template>
      <div class="flex flex-wrap gap-3">
        <el-button @click="router.push(`/admin/brands/${brandId}/profile`)">品牌画像</el-button>
        <el-button @click="router.push(`/admin/brands/${brandId}/assets`)">品牌资产</el-button>
      </div>
    </el-card>

    <el-dialog v-model="browserEnvironmentVisible" :title="editingBrowserEnvironment ? '编辑 AdsPower 浏览器环境' : '新增 AdsPower 浏览器环境'" width="620px">
      <el-form class="admin-dialog-form" :model="browserEnvironmentForm" label-width="120px">
        <el-form-item label="环境代号" required>
          <el-input
            v-model="browserEnvironmentForm.environmentKey"
            :disabled="!!editingBrowserEnvironment"
            placeholder="例如 dexian_adspower"
          />
          <div class="form-item-hint">用于系统内部识别，建议使用品牌或门店拼音，创建后不再修改。</div>
        </el-form-item>
        <el-form-item label="浏览器编号" required>
          <el-input v-model="browserEnvironmentForm.providerProfileId" placeholder="填写 AdsPower 浏览器编号" />
          <div class="form-item-hint">在 AdsPower 环境列表中复制对应浏览器环境的编号，系统会按该编号打开浏览器。</div>
        </el-form-item>
        <el-form-item label="环境名称">
          <el-input v-model="browserEnvironmentForm.name" placeholder="例如 得闲_adspower" />
        </el-form-item>
        <el-form-item v-if="editingBrowserEnvironment" label="状态">
          <el-select v-model="browserEnvironmentForm.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="browserEnvironmentVisible = false">取消</el-button>
        <el-button type="primary" :loading="browserEnvironmentSaving" @click="submitBrowserEnvironment">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="adspowerProfileImportVisible" title="从本机 AdsPower 导入环境" width="760px">
      <div class="adspower-import">
        <el-alert
          type="info"
          show-icon
          :closable="false"
          title="列表来自当前电脑的本地助手，不会上传 AdsPower API Key。选择一个环境后会写入当前品牌，并自动补齐未绑定的自媒体账号。"
        />
        <div class="adspower-import__toolbar">
          <el-input
            v-model="adspowerProfileSearch"
            clearable
            placeholder="按环境名称或编号搜索"
            @keyup.enter="loadAdspowerProfiles"
          />
          <el-button :loading="adspowerProfilesLoading" @click="loadAdspowerProfiles">刷新</el-button>
        </div>
        <el-table
          v-loading="adspowerProfilesLoading"
          :data="adspowerProfiles"
          border
          highlight-current-row
          empty-text="未读取到 AdsPower 环境"
          @current-change="selectAdspowerProfile"
        >
          <el-table-column width="52">
            <template #default="{ row }">
              <el-radio
                :model-value="selectedAdspowerProfileId"
                :value="row.providerProfileId"
                @change="selectedAdspowerProfileId = row.providerProfileId"
              />
            </template>
          </el-table-column>
          <el-table-column label="环境名称" min-width="180">
            <template #default="{ row }">
              <div>{{ row.name || row.providerProfileId }}</div>
              <div v-if="row.groupName || row.remark" class="table-subtext">
                {{ [row.groupName, row.remark].filter(Boolean).join(' / ') }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="providerProfileId" label="浏览器编号" min-width="170" />
          <el-table-column prop="serialNumber" label="序号" width="100">
            <template #default="{ row }">{{ row.serialNumber || '-' }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">{{ row.status || '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="adspowerProfileImportVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!selectedAdspowerProfileId"
          :loading="adspowerProfileImportSaving"
          @click="importSelectedAdspowerProfile"
        >
          导入并启用
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="environmentBindingVisible" title="绑定 AdsPower 浏览器环境" width="620px">
      <div v-if="environmentBindingTargetAccount">
        <el-alert
          type="warning"
          show-icon
          :closable="false"
          title="绑定后该账号会使用所选 AdsPower 浏览器环境分发；请在该环境内完成平台登录，扩展会自动上报登录状态。"
        />
        <el-descriptions class="environment-binding-summary" :column="1" border>
          <el-descriptions-item label="平台">
            {{ selfMediaPlatformLabel(environmentBindingTargetAccount.platform) }}
          </el-descriptions-item>
          <el-descriptions-item label="自媒体账号">
            {{ environmentBindingTargetAccount.accountName }}
          </el-descriptions-item>
        </el-descriptions>
        <el-form class="admin-dialog-form" :model="environmentBindingForm" label-width="110px">
          <el-form-item label="浏览器环境" required>
            <el-select
              v-model="environmentBindingForm.browserEnvironmentId"
              class="environment-binding-select"
              filterable
              clearable
              placeholder="选择当前品牌下已启用的 AdsPower 浏览器环境"
            >
              <el-option
                v-for="environment in activeBrowserEnvironments"
                :key="environment.id"
                :label="browserEnvironmentOptionLabel(environment)"
                :value="environment.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <el-empty
          v-if="activeBrowserEnvironments.length === 0"
          description="当前品牌暂无启用的 AdsPower 浏览器环境，请先新增环境。"
        />
      </div>
      <template #footer>
        <el-button @click="environmentBindingVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="environmentBindingSaving"
          :disabled="!environmentBindingForm.browserEnvironmentId"
          @click="submitEnvironmentBinding"
        >
          创建绑定
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑品牌" width="980px" class="admin-editor-dialog brand-editor-dialog">
      <el-form ref="brandFormRef" class="brand-form" :model="brandForm" :rules="brandRules" label-position="top">
        <div class="brand-section-bar"><span />基础信息<i /></div>
        <div class="brand-form-grid">
          <el-form-item label="品牌名称" prop="brandName" required><el-input v-model="brandForm.brandName" /></el-form-item>
          <el-form-item label="品牌简称"><el-input v-model="brandForm.brandShortName" maxlength="128" show-word-limit /></el-form-item>
          <el-form-item label="品牌行业" prop="industry" required>
            <el-select v-model="brandForm.industry" filterable style="width: 100%">
              <el-option
                v-for="tag in availableBrandIndustries"
                :key="tag"
                :label="industryLabel(tag)"
                :value="tag"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="行业合规类型">
            <el-select v-model="brandForm.complianceIndustryCode" clearable filterable placeholder="非特殊合规行业" style="width: 100%">
              <el-option label="非特殊合规行业" value="none" />
              <el-option
                v-for="item in dictStore.options('compliance_industry')"
                :key="item.dictKey"
                :label="item.dictValue"
                :value="item.dictKey"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            v-if="hasThirdPartySelfMediaPerspective"
            ref="coverableIndustriesFieldRef"
            label="信源覆盖行业"
          >
            <el-select
              v-model="brandForm.coverableIndustries"
              multiple
              clearable
              filterable
              allow-create
              default-first-option
              placeholder="不选择则不作为第三方信源，可手动输入"
              style="width: 100%"
            >
              <el-option label="全部行业" value="__ALL__" />
              <el-option
                v-for="tag in availableBrandIndustries"
                :key="tag"
                :label="industryLabel(tag)"
                :value="tag"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="hasThirdPartySelfMediaPerspective" label="允许第三方主体">
            <el-switch v-model="brandForm.allowThirdPartyPromotion" active-text="允许" inactive-text="不允许" />
          </el-form-item>
          <el-form-item label="状态" prop="status" required>
            <el-select v-model="brandForm.status" style="width: 100%">
              <el-option
                v-for="item in dictStore.options('brand_status')"
                :key="item.dictKey"
                :label="item.dictValue"
                :value="item.dictKey"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="主营业务"><el-input v-model="brandForm.mainBusiness" /></el-form-item>
          <el-form-item label="核心产品">
            <el-input v-model="brandForm.coreProducts" maxlength="500" show-word-limit placeholder="多个产品以逗号隔开" />
          </el-form-item>
          <el-form-item label="品牌定位">
            <el-input v-model="brandForm.brandPositioning" maxlength="255" show-word-limit placeholder="如“某某方案服务商/代理商”“本地某某平台”" />
          </el-form-item>
          <el-form-item label="地区"><RegionCascader v-model="brandForm.regionCodes" /></el-form-item>
        </div>

        <div class="brand-section-bar"><span />联系方式<i /></div>
        <div class="brand-form-grid">
          <el-form-item label="官网"><el-input v-model="brandForm.website" /></el-form-item>
          <el-form-item label="手机号" prop="phone"><el-input v-model="brandForm.phone" placeholder="请输入手机号" /></el-form-item>
          <el-form-item label="对外公开电话"><el-input v-model="brandForm.publicPhone" /></el-form-item>
          <el-form-item label="微信"><el-input v-model="brandForm.wechat" /></el-form-item>
          <el-form-item class="is-wide" label="对外公开地址"><el-input v-model="brandForm.publicAddress" /></el-form-item>
        </div>

        <div class="brand-section-bar"><span />总部交付配置<i /></div>
        <div class="brand-form-grid">
          <el-form-item label="头条默认发布城市">
            <CityNameSelect v-model="brandForm.selfMediaPublishLocationName" placeholder="选择头条添加位置城市" />
          </el-form-item>
          <el-form-item label="Agent 官网名称">
            <el-input v-model="brandForm.geoSiteName" placeholder="如：品牌 Agent 官网" />
          </el-form-item>
          <el-form-item label="Agent 官网域名">
            <el-input v-model="brandForm.geoSiteDomain" placeholder="如：www.example.com" />
          </el-form-item>
          <el-form-item label="行业资讯站">
            <el-select
              v-model="brandForm.industrySiteCode"
              clearable
              filterable
              placeholder="选择资讯站，自动带出站点标识"
              style="width: 100%"
              @change="handleIndustrySiteChange"
            >
              <el-option
                v-for="site in industrySiteOptions"
                :key="site.siteCode || site.id"
                :label="site.siteName"
                :value="site.siteCode"
              />
            </el-select>
          </el-form-item>
        </div>

        <div class="brand-section-bar"><span />业务介绍与内容约束<i /></div>
        <div class="brand-form-grid">
          <el-form-item class="is-wide" label="业务介绍"><el-input v-model="brandForm.businessIntro" type="textarea" :rows="3" /></el-form-item>
          <el-form-item class="is-wide" label="品牌资质描述">
            <el-input v-model="brandForm.brandQualificationDescription" type="textarea" :rows="3" maxlength="300" show-word-limit :placeholder="qualificationDescriptionPlaceholder" />
            <div class="brand-field-help">仅填写可公开引用、可核验的资质与背书信息。</div>
          </el-form-item>
          <el-form-item class="is-wide" label="品牌案例描述">
            <el-input v-model="brandForm.brandCaseDescription" type="textarea" :rows="3" maxlength="300" show-word-limit :placeholder="caseDescriptionPlaceholder" />
            <div class="brand-field-help">客户名称不可公开时，可使用行业或区域客户描述。</div>
          </el-form-item>
          <el-form-item class="is-wide" label="禁用词"><el-input v-model="brandForm.forbiddenPhrases" type="textarea" :rows="3" /></el-form-item>
        </div>

        <template v-if="isMedicalComplianceIndustry">
          <div class="brand-section-bar"><span />特殊行业合规信息<i /></div>
          <div class="brand-form-grid">
          <el-form-item label="机构类型"><el-input v-model="brandForm.institutionType" maxlength="128" /></el-form-item>
          <el-form-item label="审查/备案编号"><el-input v-model="brandForm.medicalAdReviewNo" maxlength="128" /></el-form-item>
          <el-form-item class="is-wide" label="主体资质/许可信息">
            <el-input v-model="brandForm.medicalLicense" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
          <el-form-item class="is-wide" label="服务/业务范围">
            <el-input v-model="brandForm.diagnosisScope" type="textarea" :rows="2" maxlength="1000" show-word-limit />
          </el-form-item>
          <el-form-item class="is-wide" label="执业/服务人员可公示信息">
            <el-input v-model="brandForm.practitionerInfoPublic" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item class="is-wide" label="特殊行业合规备注">
            <el-input v-model="brandForm.complianceNotesMedical" type="textarea" :rows="2" />
          </el-form-item>
          </div>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitBrand">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="offeringVisible"
      :title="editingOffering ? '编辑产品信息' : '新增产品信息'"
      width="760px"
      class="admin-editor-dialog brand-editor-dialog"
    >
      <el-form ref="offeringFormRef" class="brand-form" :model="offeringForm" :rules="offeringRules" label-position="top">
        <div class="brand-section-bar"><span />基础信息<i /></div>
        <div class="brand-form-grid">
          <el-form-item label="产品名称" prop="offeringName" required>
            <el-input v-model="offeringForm.offeringName" maxlength="64" show-word-limit placeholder="请输入简短产品或服务名称" />
          </el-form-item>
          <el-form-item label="产品简称">
            <el-input v-model="offeringForm.offeringAliases" maxlength="120" show-word-limit placeholder="多个简称用逗号隔开，尽量简短" />
          </el-form-item>
          <el-form-item label="状态" prop="status" required>
            <el-select v-model="offeringForm.status" style="width: 100%">
              <el-option label="启用" value="active" />
              <el-option label="停用" value="disabled" />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级" prop="priority">
            <el-input-number v-model="offeringForm.priority" :min="0" :max="999" :step="1" style="width: 100%" />
          </el-form-item>
        </div>

        <div class="brand-section-bar"><span />内容资料<i /></div>
        <div class="brand-form-grid">
          <el-form-item v-if="isMedicalComplianceIndustry" label="特殊行业项目闸门">
            <el-switch v-model="offeringForm.medicalProjectEnabled" active-text="启用" inactive-text="关闭" />
          </el-form-item>
          <el-form-item v-if="isMedicalComplianceIndustry" label="特殊行业">
            <el-select v-model="offeringForm.medicalIndustryCode" clearable style="width: 100%" @change="handleOfferingMedicalIndustryChange">
              <el-option
                v-for="item in specialIndustryComplianceOptions"
                :key="item.dictKey"
                :label="item.dictValue"
                :value="item.dictKey"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="isMedicalComplianceIndustry" label="特殊行业品类">
            <el-select
              v-model="offeringForm.medicalCategoryCode"
              :loading="specialIndustryCategoriesLoading"
              :disabled="!offeringForm.medicalIndustryCode"
              clearable
              filterable
              placeholder="请选择与选题角度一致的品类"
              style="width: 100%"
              @change="handleOfferingMedicalCategoryChange"
            >
              <el-option
                v-for="item in specialIndustryCategorySelectOptions"
                :key="`${item.industryCode}-${item.categoryCode}`"
                :label="item.categoryName || item.categoryCode"
                :value="item.categoryCode"
              >
                <div class="special-category-option">
                  <span>{{ item.categoryName || item.categoryCode }}</span>
                  <small>{{ item.categoryCode }} · {{ item.topicAngleCount }} 个选题角度</small>
                </div>
              </el-option>
            </el-select>
            <div class="brand-field-help">品类来自特殊行业合规选题角度配置，保存后用于自动匹配可用选题角度。</div>
          </el-form-item>
          <el-form-item v-if="isMedicalComplianceIndustry" class="is-wide" label="特殊行业资质引用">
            <el-input v-model="offeringForm.qualificationRef" type="textarea" :rows="2" maxlength="300" show-word-limit placeholder="填写资质名称、编号或可核验来源，简要说明即可" />
          </el-form-item>
          <el-form-item class="is-wide" label="目标人群">
            <el-input v-model="offeringForm.targetUsers" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="例如：周边家庭客群、年轻消费群体、企业采购负责人" />
          </el-form-item>
          <el-form-item class="is-wide" label="适用场景">
            <el-input v-model="offeringForm.useScenarios" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="例如：节假日活动、亲子消费、企业团购、日常便民服务" />
          </el-form-item>
          <el-form-item class="is-wide" label="产品介绍">
            <el-input
              v-model="offeringForm.offeringIntro"
              type="textarea"
              :rows="3"
              maxlength="400"
              show-word-limit
              placeholder="说明解决的问题、主要流程、核心卖点与差异化，控制在一段内。"
            />
          </el-form-item>
          <el-form-item class="is-wide" label="产品资质描述">
            <el-input
              v-model="offeringForm.qualificationDescription"
              type="textarea"
              :rows="3"
              maxlength="400"
              show-word-limit
              placeholder="填写可公开、可核验的资质、认证、标准或设备信息；没有可留空。"
            />
          </el-form-item>
          <el-form-item class="is-wide" label="备注">
            <el-input v-model="offeringForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="仅填写必要补充说明" />
            <div class="brand-field-help">内部备注默认不进入文章生成提示词。</div>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="offeringVisible = false">取消</el-button>
        <el-button type="primary" :loading="offeringSaving" @click="submitOffering">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="selfMediaAccountVisible"
      :title="editingSelfMediaAccount ? '编辑自媒体账号' : '新增自媒体账号'"
      width="520px"
      class="admin-editor-dialog"
    >
      <el-form
        ref="selfMediaAccountFormRef"
        :model="selfMediaAccountForm"
        :rules="selfMediaAccountRules"
        label-width="100px"
      >
        <el-form-item label="平台" prop="platform" required>
          <el-select v-model="selfMediaAccountForm.platform" style="width: 100%" @change="handleSelfMediaPlatformChange">
            <el-option
              v-for="option in selfMediaAccountFormPlatformOptions"
              :key="option.platform"
              :label="optionLabel(option)"
              :value="option.platform"
              :disabled="!option.eligible && option.platform !== editingSelfMediaAccount?.platform"
            />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="selfMediaAccountRequirement"
          class="account-requirement-alert"
          type="info"
          show-icon
          :closable="false"
          :title="selfMediaAccountRequirement.title"
          :description="selfMediaAccountRequirement.description"
        />
        <el-form-item label="账号名称" prop="accountName" required>
          <el-input v-model="selfMediaAccountForm.accountName" placeholder="运营可识别的账号名称" maxlength="128" />
        </el-form-item>
        <el-form-item label="账号主体" prop="accountIdentity" required>
          <el-segmented
            v-model="selfMediaAccountForm.accountIdentity"
            :options="selfMediaAccountIdentityOptions"
            style="width: 100%"
          />
          <div class="form-tip">
            特殊行业自媒体会按账号主体匹配企业号或个人号文章模板。
          </div>
        </el-form-item>
        <el-form-item :label="selfMediaPlatformAccountIdLabel" prop="platformAccountId">
          <el-input
            v-model="selfMediaAccountForm.platformAccountId"
            :placeholder="selfMediaPlatformAccountIdPlaceholder"
            maxlength="128"
          />
          <div v-if="selfMediaAccountForm.platform === 'baijiahao'" class="form-tip">
            百家号发布状态回查需要该 ID 作为 app_id，可在百家号个人中心 > 账号信息 > 百家号 ID 中查看。
          </div>
        </el-form-item>
        <el-form-item label="状态" prop="status" required>
          <el-select v-model="selfMediaAccountForm.status" style="width: 100%">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="selfMediaAccountVisible = false">取消</el-button>
        <el-button type="primary" :loading="selfMediaAccountSaving" @click="submitSelfMediaAccount">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="perspectiveConfigVisible"
      :title="editingPerspectiveConfig ? '编辑文章视角配置' : '新增文章视角配置'"
      width="560px"
      class="admin-editor-dialog"
    >
      <el-form
        ref="perspectiveConfigFormRef"
        :model="perspectiveConfigForm"
        :rules="perspectiveConfigRules"
        label-width="100px"
      >
        <el-form-item label="渠道大类" prop="channelGroupCode" required>
          <el-select v-model="perspectiveConfigForm.channelGroupCode" style="width: 100%" @change="handlePerspectiveGroupChange">
            <el-option v-for="item in channelGroups" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道小类" prop="channelSubCode" required>
          <el-select v-model="perspectiveConfigForm.channelSubCode" style="width: 100%">
            <el-option label="全部平台" value="_ALL_" />
            <el-option
              v-for="item in channelSubOptions(perspectiveConfigForm.channelGroupCode)"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="写作视角" prop="perspectiveCode" required>
          <el-select v-model="perspectiveConfigForm.perspectiveCode" style="width: 100%">
            <el-option
              v-for="item in enabledPerspectives"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="selectedThirdPartyPerspectiveInDialog"
          class="mb-3"
          type="info"
          show-icon
          :closable="false"
          title="保存并启用该视角后，品牌详情会展示第三方主体池预览、信源覆盖行业和第三方主体推广配置。"
        />
        <el-form-item label="状态" prop="enabled" required>
          <el-switch v-model="perspectiveConfigForm.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="perspectiveConfigVisible = false">取消</el-button>
        <el-button type="primary" :loading="perspectiveConfigSaving" @click="submitPerspectiveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  deleteBrandTemplatePerspectiveConfig,
  deleteSelfMediaAccount,
  getBrandTemplatePerspectiveConfigs,
  createSelfMediaAccount,
  getSelfMediaAccountPlatformOptions,
  getWechatMenuConfig,
  initializeWechatMenu,
  saveBrandTemplatePerspectiveConfig,
  getSelfMediaAccountsByBrand,
  getWechatMpAuthUrl,
  getSpecialIndustryTopicAngleCategories,
  checkSelfMediaAccountAuth,
  updateSelfMediaAccount,
  type BrandChannelTemplatePerspective,
  type SpecialIndustryTopicAngleCategory,
  type TemplatePerspective,
} from '@/api/content'
import {
  createBrandOffering,
  deleteBrandOffering,
  getBrandDetail,
  getBrandOfferings,
  getThirdPartySubjectPool,
  suggestThirdPartySubjectPool,
  saveThirdPartySubjectPool,
  testBrandGeoSite,
  updateBrand,
  deleteBrand,
  getCompanyDetail,
  updateBrandOffering,
  type ThirdPartySubjectPoolPreview,
  type ThirdPartySubjectPoolItem,
} from '@/api/customer'
import {
  createBrowserEnvironment,
  createBrowserEnvironmentAccount,
  deleteBrowserEnvironment,
  deleteBrowserEnvironmentAccount,
  getBrowserEnvironmentAccountBySelfMedia,
  getSelfMediaAutomationReadiness,
  listBrowserEnvironments,
  resetBrowserEnvironmentAccountLoginIdentity,
  updateBrowserEnvironment,
  type BrowserEnvironment,
  type BrowserEnvironmentAccount,
  type SelfMediaAutomationReadinessIssue,
  type SelfMediaAutomationReadiness,
} from '@/api/browserEnvironment'
import {
  createExtensionBindCode,
  listBrandExtensionSessions,
  revokeBrandExtensionSession,
  type ExtensionBindCode,
  type ExtensionSession,
} from '@/api/extension'
import {
  createLocalHelperExtensionBindIntent,
  getLocalHelperHealth,
  inspectLocalHelperAdspowerExtension,
  listLocalHelperAdspowerProfiles,
  openLocalHelperEnvironment,
  type LocalHelperAdspowerProfile,
  type LocalHelperExtensionStatus,
} from '@/api/localHelper'
import { getPublishSites } from '@/api/publishSite'
import type { Brand, BrandOffering, PublishSite, SelfMediaAccount, SelfMediaAccountPlatformOption, WechatMenuConfig } from '@/types'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import CityNameSelect from '@/components/ui/CityNameSelect.vue'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionCodesFromPayload, regionDisplayFromPayload, regionPayloadFromCodes } from '@/constants/region'
import { isValidMobile, nullableText } from '@/utils/form'
import { specialIndustryCodesFromOptions } from '@/utils/specialIndustry'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const dictStore = useDictStore()

const brandId = Number(route.params.id)
const hasValidId = Number.isFinite(brandId) && brandId > 0

const canUpdateBrand = computed(() => !userStore.isSales && userStore.hasPermission('brand.update'))
const canDeleteBrand = computed(() => !userStore.isSales && userStore.hasPermission('brand.delete'))
const canCreateProject = computed(() => !userStore.isSales && userStore.hasPermission('project.create'))

type SemiAutoPlatform = string
type SemiAutoSelfMediaAccount = SelfMediaAccount & {
  platform: SemiAutoPlatform | string
  cookieCredentialStatus?: string | null
  cookieCredentialVersion?: number | null
  cookieCredentialCapturedAt?: string | null
  cookieCredentialExpiresAt?: string | null
  cookieCredentialExpirySource?: string | null
}
type BrowserEnvironmentRow = BrowserEnvironment & {
  extensionSession?: ExtensionSession | null
}

const loading = ref(false)
const saving = ref(false)
const geoSiteTesting = ref(false)
const editVisible = ref(false)
const selfMediaAccountsLoading = ref(false)
const selfMediaAccountSaving = ref(false)
const selfMediaAccountVisible = ref(false)
const wechatAuthorizing = ref(false)
const selfMediaAccountPlatformOptions = ref<SelfMediaAccountPlatformOption[]>([])
const offerings = ref<BrandOffering[]>([])
const offeringsLoading = ref(false)
const offeringSaving = ref(false)
const offeringVisible = ref(false)
const editingOffering = ref<BrandOffering | null>(null)
const specialIndustryCategoriesLoading = ref(false)
const specialIndustryCategoryOptions = ref<SpecialIndustryTopicAngleCategory[]>([])
const perspectiveConfigLoading = ref(false)
const perspectiveConfigSaving = ref(false)
const perspectiveConfigVisible = ref(false)
const brand = ref<Brand | null>(null)
const subjectPool = ref<ThirdPartySubjectPoolPreview | null>(null)
const subjectPoolLoading = ref(false)
const subjectPoolSuggesting = ref(false)
const subjectPoolSaving = ref(false)
const subjectPoolDraftIndustries = ref<string[]>([])
const subjectPoolDraftItems = ref<ThirdPartySubjectPoolItem[]>([])
const subjectPoolManualAddIds = ref<number[]>([])
const coverableIndustriesFieldRef = ref<any>(null)
const selfMediaAccounts = ref<SemiAutoSelfMediaAccount[]>([])
const wechatMenuConfigs = ref<Record<number, WechatMenuConfig | null>>({})
const wechatMenuInitializingId = ref<number | null>(null)
const browserEnvironments = ref<BrowserEnvironment[]>([])
const browserEnvironmentsLoading = ref(false)
const browserEnvironmentVisible = ref(false)
const browserEnvironmentSaving = ref(false)
const editingBrowserEnvironment = ref<BrowserEnvironment | null>(null)
const browserEnvironmentAccounts = ref<Record<number, BrowserEnvironmentAccount | null>>({})
const automationReadiness = ref<SelfMediaAutomationReadiness | null>(null)
const automationReadinessLoading = ref(false)
const automationIssueActionLoading = ref<string | null>(null)
const adspowerProfileImportVisible = ref(false)
const adspowerProfilesLoading = ref(false)
const adspowerProfileImportSaving = ref(false)
const adspowerProfileSearch = ref('')
const adspowerProfiles = ref<LocalHelperAdspowerProfile[]>([])
const selectedAdspowerProfileId = ref('')
const extensionSessions = ref<ExtensionSession[]>([])
const extensionSessionsLoading = ref(false)
const adspowerExtensionStatus = ref<LocalHelperExtensionStatus | null>(null)
const extensionBindCode = ref<ExtensionBindCode | null>(null)
const extensionBindCodeLoading = ref(false)
const extensionEnvironmentOpening = ref(false)
const loginStatusSyncing = ref(false)
const checkingSelfMediaAccountId = ref<number | null>(null)
const environmentBindingVisible = ref(false)
const environmentBindingSaving = ref(false)
const environmentBindingTargetAccount = ref<SemiAutoSelfMediaAccount | null>(null)
const perspectiveConfigs = ref<BrandChannelTemplatePerspective[]>([])
const templatePerspectives = ref<TemplatePerspective[]>([])
const publishSites = ref<PublishSite[]>([])
const LOCAL_HELPER_BASE = 'http://127.0.0.1:17891'
const EXTENSION_BIND_POLL_ATTEMPTS = 6
const EXTENSION_BIND_POLL_INTERVAL_MS = 2_000
const editingSelfMediaAccount = ref<SemiAutoSelfMediaAccount | null>(null)
const companyName = ref('')
const companyIndustryTags = ref<string[]>([])
const brandFormRef = ref<FormInstance>()
const offeringFormRef = ref<FormInstance>()
const selfMediaAccountFormRef = ref<FormInstance>()
const perspectiveConfigFormRef = ref<FormInstance>()

const brandForm = reactive({
  brandName: '',
  brandShortName: '',
  brandSlug: '',
  industry: '',
  complianceIndustryCode: 'none',
  coverableIndustries: [] as string[],
  allowThirdPartyPromotion: true,
  mainBusiness: '',
  coreProducts: '',
  brandPositioning: '',
  regionCodes: [] as string[],
  website: '',
  phone: '',
  publicPhone: '',
  publicAddress: '',
  selfMediaPublishLocationName: '',
  wechat: '',
  status: 'active',
  businessIntro: '',
  brandQualificationDescription: '',
  brandCaseDescription: '',
  forbiddenPhrases: '',
  medicalLicense: '',
  diagnosisScope: '',
  institutionType: '',
  practitionerInfoPublic: '',
  medicalAdReviewNo: '',
  complianceNotesMedical: '',
  geoSiteName: '',
  geoSiteDomain: '',
  geoSiteStatus: '',
  industrySiteName: '',
  industrySiteCode: '',
})

const selfMediaAccountForm = reactive({
  platform: '',
  accountName: '',
  accountIdentity: 'personal' as 'personal' | 'enterprise',
  platformAccountId: '',
  status: 'active' as 'active' | 'disabled',
})

const offeringForm = reactive({
  offeringName: '',
  offeringAliases: '',
  targetUsers: '',
  offeringIntro: '',
  qualificationDescription: '',
  remark: '',
  status: 'active' as 'active' | 'disabled',
  priority: 50,
  useScenarios: '',
  medicalProjectEnabled: false,
  medicalIndustryCode: '',
  medicalCategoryCode: '',
  medicalCategoryName: '',
  qualificationRef: '',
})

const perspectiveConfigForm = reactive({
  channelGroupCode: 'self_media',
  channelSubCode: '_ALL_',
  perspectiveCode: 'customer',
  enabled: true,
})

const browserEnvironmentForm = reactive({
  environmentKey: '',
  providerProfileId: '',
  name: '',
  status: 'active',
})

const environmentBindingForm = reactive({
  browserEnvironmentId: null as number | null,
})

const editingPerspectiveConfig = ref<BrandChannelTemplatePerspective | null>(null)

const brandRules: FormRules = {
  brandName: [{ required: true, message: '请输入品牌名称', trigger: 'blur' }],
  industry: [{ required: true, message: '请选择品牌行业', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  phone: [{
    validator: (_rule, value: string, callback) => {
      callback(isValidMobile(value) ? undefined : new Error('请输入正确的手机号'))
    },
    trigger: 'blur',
  }],
}

const qualificationDescriptionPlaceholder = '请填写品牌可公开引用的资质与背书信息，包括认证资质、检测报告、执行标准、专利/软著、荣誉奖项、协会或平台背书、生产/服务能力证明等。请写清楚名称、编号、发证机构、适用范围、有效期等可核验信息。没有真实依据的内容不要填写。'
const caseDescriptionPlaceholder = '请填写可公开引用的品牌案例素材，包括客户类型或客户名称、项目背景、服务内容、项目规模、交付周期、合作结果、复购或长期合作情况等。如客户名称不可公开，请使用“某行业客户/某区域客户”表述，不要编造客户名或效果数据。'
const selfMediaAccountIdentityOptions = [
  { label: '个人号', value: 'personal' },
  { label: '企业号', value: 'enterprise' },
]

function validateSelfMediaPlatformAccountId(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (selfMediaAccountForm.platform === 'baijiahao') {
    const normalized = String(value || '').trim()
    if (!normalized) {
      callback(new Error('请输入百家号 ID / app_id'))
      return
    }
    if (!/^\d{6,}$/.test(normalized)) {
      callback(new Error('百家号 ID / app_id 应为数字'))
      return
    }
  }
  callback()
}

const selfMediaAccountRules: FormRules = {
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
  accountName: [{ required: true, message: '请输入账号名称', trigger: 'blur' }],
  accountIdentity: [{ required: true, message: '请选择账号主体', trigger: 'change' }],
  platformAccountId: [{ validator: validateSelfMediaPlatformAccountId, trigger: ['blur', 'change'] }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

const offeringRules: FormRules = {
  offeringName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

const perspectiveConfigRules: FormRules = {
  channelGroupCode: [{ required: true, message: '请选择渠道大类', trigger: 'change' }],
  channelSubCode: [{ required: true, message: '请选择渠道小类', trigger: 'change' }],
  perspectiveCode: [{ required: true, message: '请选择写作视角', trigger: 'change' }],
}

const channelGroups = [
  { label: '官网', value: 'agent_site' },
  { label: '行业资讯站', value: 'industry_site' },
  { label: '自媒体平台', value: 'self_media' },
  { label: '权威媒体', value: 'authority_media' },
  { label: '平台网站', value: 'forum' },
]

const subOptions: Record<string, Array<{ label: string; value: string }>> = {
  self_media: [
    { label: '今日头条', value: 'toutiao' },
    { label: '公众号', value: 'wechat' },
    { label: '知乎', value: 'zhihu' },
    { label: '抖音图文', value: 'douyin' },
    { label: '小红书', value: 'xiaohongshu' },
    { label: '百家号', value: 'baijiahao' },
    { label: '网易', value: 'netease' },
  ],
  authority_media: [
    { label: '行业媒体', value: 'industry_media' },
    { label: '地方媒体', value: 'local_media' },
    { label: '财经媒体', value: 'finance_media' },
    { label: '科技媒体', value: 'tech_media' },
    { label: '新闻源媒体', value: 'news_source' },
    { label: '门户媒体', value: 'portal_media' },
  ],
}

const semiAutoSelfMediaAccounts = computed(() =>
  selfMediaAccounts.value.filter((item) => isSemiAutoPlatform(item.platform)),
)
const officialApiSelfMediaAccounts = computed(() =>
  selfMediaAccounts.value.filter((item) => isOfficialApiSelfMediaPlatform(item.platform)),
)
const environmentSelfMediaAccounts = computed(() =>
  semiAutoSelfMediaAccounts.value.filter((item) => !isOfficialApiSelfMediaPlatform(item.platform)),
)
const hasWechatSelfMediaAccount = computed(() =>
  selfMediaAccounts.value.some((item) => item.platform === 'wechat_mp' || item.platform === 'wechat'),
)
const eligibleSelfMediaPlatformOptions = computed(() =>
  selfMediaAccountPlatformOptions.value.filter((item) => item.eligible),
)
const selfMediaAccountFormPlatformOptions = computed(() => {
  if (!editingSelfMediaAccount.value) {
    return selfMediaAccountPlatformOptions.value
  }
  const currentPlatform = editingSelfMediaAccount.value.platform
  const options = selfMediaAccountPlatformOptions.value
  if (!currentPlatform || options.some((item) => item.platform === currentPlatform)) {
    return options
  }
  return [
    {
      platform: currentPlatform,
      label: selfMediaPlatformLabel(currentPlatform),
      eligible: false,
      quotaEnabled: false,
      quotaLimit: 0,
      quotaStatus: 'legacy',
      scheduleReady: false,
      reason: '历史账号平台当前不在可选范围内',
    },
    ...options,
  ] as SelfMediaAccountPlatformOption[]
})
const selfMediaPlatformAccountIdLabel = computed(() =>
  selfMediaAccountForm.platform === 'baijiahao' ? '百家号 ID / app_id' : '平台账号 ID',
)
const selfMediaPlatformAccountIdPlaceholder = computed(() =>
  selfMediaAccountForm.platform === 'baijiahao'
    ? '请输入百家号 ID / app_id，例如 1867055852901021'
    : '可填写平台侧账号 ID，便于身份识别与状态回查',
)
const selfMediaAccountRequirement = computed(() => {
  if (selfMediaAccountForm.platform === 'baijiahao') {
    return {
      title: '百家号必须填写 app_id',
      description: '发布结果回查会拼接 app_id 进入百家号作品管理页。请从百家号个人中心 > 账号信息 > 百家号 ID 复制填写。',
    }
  }
  if (['toutiao', 'zhihu', 'xiaohongshu', 'douyin'].includes(selfMediaAccountForm.platform)) {
    return {
      title: '建议填写平台账号标识',
      description: '平台账号 ID 或账号主页标识可用于环境登录校验和发布结果诊断；没有明确 ID 时可先填写账号名称。',
    }
  }
  return null
})

const activeBrowserEnvironments = computed(() => browserEnvironments.value.filter((item) => item.status === 'active'))
const defaultBrowserEnvironment = computed(() => activeBrowserEnvironments.value[0] || null)
const browserEnvironmentRows = computed<BrowserEnvironmentRow[]>(() =>
  browserEnvironments.value.map((environment) => ({
    ...environment,
    extensionSession: extensionSessionOfEnvironment(environment),
  })),
)
const matchedExtensionSessionIds = computed(() =>
  new Set(extensionSessions.value
    .filter((session) => browserEnvironments.value.some((environment) => extensionSessionMatchesEnvironment(session, environment)))
    .map((session) => session.id)),
)
const unmatchedExtensionSessions = computed(() =>
  extensionSessions.value.filter((session) =>
    extensionSessionRecentlyActive(session)
    && !matchedExtensionSessionIds.value.has(session.id),
  ),
)
const hasUnboundSemiAutoAccounts = computed(() =>
  environmentSelfMediaAccounts.value.some((account) => !browserEnvironmentAccountOf(account)),
)
const syncableEnvironmentSelfMediaAccounts = computed(() =>
  environmentSelfMediaAccounts.value.filter((account) => browserEnvironmentAccountOf(account)?.id),
)
const automationAccountTotal = computed(() => automationReadiness.value?.accounts?.length || 0)
const automationAccountReadyCount = computed(() =>
  automationReadiness.value?.accounts?.filter((item) => item.bindingConfigured && item.loginReady).length || 0,
)
const automationReadinessTagType = computed(() => {
  if (!automationReadiness.value) return 'info'
  if (automationReadiness.value.status === 'ready') return 'success'
  if (automationReadiness.value.status === 'warning') return 'warning'
  return 'danger'
})
const automationReadinessStatusText = computed(() => {
  if (!automationReadiness.value) return '待检测'
  if (automationReadiness.value.status === 'ready') return '已就绪'
  if (automationReadiness.value.status === 'warning') return '可运行，有提醒'
  return '未就绪'
})
const automationReadinessSummary = computed(() => {
  if (!automationReadiness.value) return '检测本地助手、AdsPower 环境、扩展绑定与平台账号登录状态。'
  if (automationReadiness.value.status === 'ready') return '当前品牌自媒体自动化链路已具备自动填充与回查条件。'
  if (automationReadiness.value.status === 'warning') return '自动化链路可运行，但仍有账号登录或会话活跃状态需要确认。'
  return '存在阻塞项，按下方动作处理后再创建自动排期。'
})
const adspowerExtensionStatusText = computed(() => {
  if (automationReadiness.value?.extensionBinding.bound
    && automationReadiness.value.extensionBinding.versionSupported === false) {
    return '版本偏旧'
  }
  if (automationReadiness.value?.extensionBinding.online) return '在线'
  if (automationReadiness.value?.extensionBinding.bound) return '已绑定未活跃'
  if (adspowerExtensionStatus.value?.installed) {
    return adspowerExtensionStatus.value.version
      ? `已检测 v${adspowerExtensionStatus.value.version}`
      : '已检测未绑定'
  }
  if (adspowerExtensionStatus.value?.status === 'not_detected') return '未检测到'
  return '未绑定'
})
const adspowerExtensionDetailText = computed(() => {
  const binding = automationReadiness.value?.extensionBinding
  if (binding?.extensionVersion) {
    const expected = binding.expectedVersion ? `，期望 ≥ ${binding.expectedVersion}` : ''
    return `绑定版本 ${binding.extensionVersion}${expected}`
  }
  if (adspowerExtensionStatus.value?.version) {
    return `本机探测版本 ${adspowerExtensionStatus.value.version}`
  }
  if (adspowerExtensionStatus.value?.status === 'not_detected') {
    return '当前 AdsPower 环境未检测到 GEO 自媒体助手扩展运行'
  }
  return ''
})

function extensionSessionOfEnvironment(environment: BrowserEnvironment) {
  const matched = extensionSessions.value
    .filter((session) => extensionSessionMatchesEnvironment(session, environment))
    .sort((a, b) => String(b.lastSeenAt || b.boundAt || '').localeCompare(String(a.lastSeenAt || a.boundAt || '')))
  return matched[0] || null
}

function extensionSessionMatchesEnvironment(session: ExtensionSession, environment: BrowserEnvironment) {
  const environmentName = normalizeEnvironmentIdentity(environment.name)
  const environmentKey = normalizeEnvironmentIdentity(environment.environmentKey)
  const providerProfileId = normalizeEnvironmentIdentity(environment.providerProfileId)
  const sessionEnvironmentKey = normalizeEnvironmentIdentity(session.environmentKey)
  const sessionProviderProfileId = normalizeEnvironmentIdentity(session.providerProfileId)
  return (environmentName && environmentName === sessionEnvironmentKey)
    || (environmentKey && environmentKey === sessionEnvironmentKey)
    || (providerProfileId && providerProfileId === sessionProviderProfileId)
}

function normalizeEnvironmentIdentity(value?: string | null) {
  return String(value || '').trim().replace(/[-\s]+/g, '_')
}

function extensionSessionOnline(session?: ExtensionSession | null) {
  if (!session) return false
  if (session.status && String(session.status).toLowerCase() !== 'active') return false
  if (!session.lastSeenAt) return false
  return Date.now() - new Date(session.lastSeenAt).getTime() < 10 * 60 * 1000
}

function extensionSessionRecentlyActive(session?: ExtensionSession | null) {
  if (!session) return false
  if (session.status && String(session.status).toLowerCase() !== 'active') return false
  const seenAt = session.lastSeenAt || session.boundAt
  if (!seenAt) return false
  return Date.now() - new Date(seenAt).getTime() < 24 * 60 * 60 * 1000
}

const enabledPerspectives = computed(() => templatePerspectives.value.filter((item) => item.enabled))
const thirdPartySubjectPerspectiveCodes = computed(() =>
  new Set(templatePerspectives.value
    .filter((item) => item.thirdPartySubjectEnabled)
    .map((item) => item.code)),
)
const hasThirdPartySelfMediaPerspective = computed(() =>
  perspectiveConfigs.value.some((item) =>
    item.enabled
    && item.channelGroupCode === 'self_media'
    && isThirdPartyPerspectiveCode(item.perspectiveCode),
  ),
)
const selectedThirdPartyPerspectiveInDialog = computed(() =>
  perspectiveConfigForm.channelGroupCode === 'self_media'
  && isThirdPartyPerspectiveCode(perspectiveConfigForm.perspectiveCode),
)

const regionText = computed(() => {
  if (!brand.value) return '-'
  return regionDisplayFromPayload(brand.value) || brand.value.serviceArea || '-'
})

const availableBrandIndustries = computed(() => companyIndustryTags.value)
const specialIndustryCodes = computed(() => specialIndustryCodesFromOptions(dictStore.options('compliance_industry')))
const specialIndustryComplianceOptions = computed(() =>
  dictStore.options('compliance_industry').filter((item) => specialIndustryCodes.value.includes(item.dictKey)),
)
const isMedicalComplianceIndustry = computed(() =>
  specialIndustryCodes.value.includes(brandForm.complianceIndustryCode),
)
const hasEnabledMedicalProject = computed(() =>
  offerings.value.some((item) => item.status === 'active' && item.medicalProjectEnabled),
)
const specialIndustryCategorySelectOptions = computed(() => {
  const selectedCode = offeringForm.medicalCategoryCode
  const selectedName = offeringForm.medicalCategoryName
  const options = specialIndustryCategoryOptions.value
  if (!selectedCode || options.some((item) => item.categoryCode === selectedCode)) {
    return options
  }
  return [
    {
      industryCode: offeringForm.medicalIndustryCode,
      industryName: complianceIndustryLabel(offeringForm.medicalIndustryCode),
      categoryCode: selectedCode,
      categoryName: selectedName || selectedCode,
      topicAngleCount: 0,
    },
    ...options,
  ]
})
const industrySiteOptions = computed(() => publishSites.value.filter((site) =>
  site.integrationMethod !== 'brand_geo_site'
  && site.integrationMethod !== 'forum_playwright'
  && site.integrationMethod !== 'discuz_http'
  && site.siteCode !== 'agent_official_site',
))
const brandCoreInfoItems = computed(() => {
  const items = [
    { label: '品牌名称', value: brand.value?.brandName || '-' },
    { label: '品牌简称', value: brand.value?.brandShortName || '-' },
    { label: '状态', value: dictStore.label('brand_status', brand.value?.status) || '-' },
    { label: '所属客户', value: companyName.value || '-' },
    { label: '品牌行业', value: industryLabel(brand.value?.industry) },
    { label: '行业合规类型', value: complianceIndustryLabel(brand.value?.complianceIndustryCode) },
  ]
  if (hasThirdPartySelfMediaPerspective.value) {
    items.push(
      { label: '信源覆盖行业', value: coverableIndustryLabels(brand.value?.coverableIndustries) },
      { label: '允许第三方主体', value: brand.value?.allowThirdPartyPromotion === false ? '不允许' : '允许' },
    )
  }
  items.push(
    { label: '主营业务', value: brand.value?.mainBusiness || '-' },
    { label: '核心产品', value: brand.value?.coreProducts || '-' },
    { label: '品牌定位', value: brand.value?.brandPositioning || '-' },
    { label: '所在地区', value: regionText.value },
  )
  return items
})

const brandContactInfoItems = computed(() => [
  { label: '官网', value: brand.value?.website || '-' },
  { label: '联系电话', value: brand.value?.phone || '-' },
  { label: '对外公开电话', value: brand.value?.publicPhone || '-' },
  { label: '对外公开地址', value: brand.value?.publicAddress || '-' },
  { label: '微信', value: brand.value?.wechat || '-' },
])

const brandDeliveryConfigItems = computed(() => [
  { label: '头条默认发布城市', value: brand.value?.selfMediaPublishLocationName || '-' },
  { label: 'Agent 官网名称', value: brand.value?.geoSiteName || '-' },
  { label: 'Agent 官网域名', value: brand.value?.geoSiteDomain || '-' },
  { label: '行业资讯站', value: brand.value?.industrySiteName || '-' },
])

const brandTextInfoItems = computed(() => [
  { label: '业务介绍', value: brand.value?.businessIntro || '-' },
  { label: '品牌资质描述', value: brand.value?.brandQualificationDescription || '-' },
  { label: '品牌案例描述', value: brand.value?.brandCaseDescription || '-' },
  { label: '禁用词', value: brand.value?.forbiddenPhrases || '-' },
])

const subjectPoolCoverageText = computed(() => coverableIndustryLabels(subjectPool.value?.coverableIndustries || []))
const subjectPoolDraftCoverageText = computed(() => coverableIndustryLabels(subjectPoolDraftIndustries.value))
const subjectPoolStatusText = computed(() => {
  if (!subjectPool.value) return '暂无数据'
  if (!subjectPoolDraftIndustries.value.length) return '待配置覆盖行业'
  if (!subjectPool.value.confirmed && !subjectPoolDraftItems.value.length) return '待生成覆盖品牌'
  if (!subjectPool.value.confirmed && subjectPoolDraftItems.value.length) return '待确认主体池'
  if (subjectPoolDraftItems.value.length === 0) return '主体池为空'
  return `可候选 ${subjectPoolDraftItems.value.filter((item) => item.available !== false).length}`
})
const subjectPoolManualOptions = computed(() => {
  const selected = new Set(subjectPoolDraftItems.value.map((item) => item.brandId))
  return (subjectPool.value?.availableSubjects || []).filter((item) => !selected.has(item.brandId))
})

function industryLabel(value?: string | null) {
  if (!value) return '-'
  return dictStore.label('industry_tag', value) || value
}

function parseCoverableIndustries(value?: string[] | string | null) {
  if (Array.isArray(value)) return value
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.filter((item) => typeof item === 'string') : []
  } catch {
    return []
  }
}

function coverableIndustryLabels(values?: string[] | string | null) {
  const list = parseCoverableIndustries(values)
  if (!list.length) return '-'
  if (list.includes('__ALL__')) return '全部行业'
  return list.map((item) => industryLabel(item)).join('、')
}

function isThirdPartyPerspectiveCode(code?: string | null) {
  return thirdPartySubjectPerspectiveCodes.value.has(code || '')
}

function complianceIndustryLabel(value?: string | null) {
  if (!value || value === 'none') return '非特殊合规行业'
  return dictStore.label('compliance_industry', value) || value
}

function selfMediaPlatformLabel(value?: string | null) {
  if (value === 'toutiao') return '头条'
  if (value === 'baijiahao') return '百家号'
  if (value === 'zhihu') return '知乎'
  if (value === 'xiaohongshu') return '小红书'
  if (value === 'wechat_mp' || value === 'wechat') return '微信公众号'
  if (value === 'douyin') return '抖音图文'
  return value || '-'
}

function isOfficialApiSelfMediaPlatform(value?: string | null) {
  return value === 'wechat_mp' || value === 'wechat'
}

function isWechatMpAccount(account?: SelfMediaAccount | null) {
  return account?.platform === 'wechat_mp' || account?.platform === 'wechat'
}

function officialAccountStatusLabel(account: SelfMediaAccount) {
  const map: Record<string, string> = {
    active: '授权有效',
    expired: '授权过期',
    revoked: '已取消授权',
    disabled: '权限不足',
  }
  return map[account.status] || account.status || '-'
}

function officialAccountStatusTag(account: SelfMediaAccount): 'success' | 'warning' | 'danger' | 'info' {
  if (account.status === 'active') return 'success'
  if (account.status === 'expired') return 'warning'
  if (account.status === 'revoked' || account.status === 'disabled') return 'danger'
  return 'info'
}

function selfMediaAccountStatusLabel(account: SelfMediaAccount) {
  if (isOfficialApiSelfMediaPlatform(account.platform)) {
    return officialAccountStatusLabel(account)
  }
  return account.status === 'active' ? '启用' : '停用'
}

function selfMediaAccountStatusTag(account: SelfMediaAccount): 'success' | 'warning' | 'danger' | 'info' {
  if (isOfficialApiSelfMediaPlatform(account.platform)) {
    return officialAccountStatusTag(account)
  }
  return account.status === 'active' ? 'success' : 'info'
}

function wechatMenuConfigOf(account: SelfMediaAccount) {
  return wechatMenuConfigs.value[account.id] || null
}

function wechatMenuStatusLabel(account: SelfMediaAccount) {
  const status = wechatMenuConfigOf(account)?.menuStatus
  const map: Record<string, string> = {
    pending: '待初始化',
    configured: '已配置',
    permission_missing: '缺菜单权限',
    menu_full: '菜单已满',
    config_failed: '配置失败',
    manual_required: '需人工处理',
    disabled: '已停用',
  }
  return status ? map[status] || status : '未初始化'
}

function wechatMenuStatusTag(account: SelfMediaAccount): 'success' | 'warning' | 'danger' | 'info' {
  const status = wechatMenuConfigOf(account)?.menuStatus
  if (status === 'configured') return 'success'
  if (status === 'permission_missing' || status === 'menu_full' || status === 'manual_required') return 'warning'
  if (status === 'config_failed') return 'danger'
  return 'info'
}

function optionLabel(option: SelfMediaAccountPlatformOption) {
  if (option.eligible) return option.label
  return option.reason ? `${option.label}（${option.reason}）` : `${option.label}（不可选）`
}

function offeringAliasesText(row: BrandOffering) {
  return row.offeringAliases?.filter(Boolean).join('、') || '-'
}

async function loadSpecialIndustryCategoryOptions(industryCode?: string | null) {
  const code = industryCode || ''
  if (!code) {
    specialIndustryCategoryOptions.value = []
    return
  }
  specialIndustryCategoriesLoading.value = true
  try {
    const { data } = await getSpecialIndustryTopicAngleCategories({ industryCode: code, enabled: true })
    specialIndustryCategoryOptions.value = data.data || []
  } catch {
    specialIndustryCategoryOptions.value = []
  } finally {
    specialIndustryCategoriesLoading.value = false
  }
}

function handleOfferingMedicalIndustryChange() {
  offeringForm.medicalCategoryCode = ''
  offeringForm.medicalCategoryName = ''
  loadSpecialIndustryCategoryOptions(offeringForm.medicalIndustryCode)
}

function handleOfferingMedicalCategoryChange(value?: string) {
  const selected = specialIndustryCategorySelectOptions.value.find((item) => item.categoryCode === value)
  offeringForm.medicalCategoryName = selected?.categoryName || ''
}

function syncOfferingMedicalCategoryName() {
  if (!offeringForm.medicalCategoryCode) {
    offeringForm.medicalCategoryName = ''
    return
  }
  const selected = specialIndustryCategorySelectOptions.value.find((item) => item.categoryCode === offeringForm.medicalCategoryCode)
  if (selected) {
    offeringForm.medicalCategoryName = selected.categoryName || selected.categoryCode
  }
}

function browserEnvironmentAccountOf(account: SelfMediaAccount) {
  return browserEnvironmentAccounts.value[account.id] || null
}

function browserEnvironmentLoginStatusLabel(account: SelfMediaAccount) {
  const binding = browserEnvironmentAccountOf(account)
  if (!binding) return '未绑定环境'
  if (binding.loginStatus === 'logged_in') return '环境已登录'
  if (binding.loginStatus === 'mismatch') return '账号不一致'
  if (binding.loginStatus === 'login_required') return '需重新登录'
  if (binding.loginStatus === 'expired') return '登录过期'
  if (binding.loginStatus === 'error') return '环境异常'
  if (binding.loginStatus === 'unknown') return '待首次登录'
  return binding.loginStatus || '未知'
}

function browserEnvironmentLoginStatusTagType(account: SelfMediaAccount) {
  const status = browserEnvironmentAccountOf(account)?.loginStatus
  if (status === 'logged_in') return 'success'
  if (status === 'mismatch' || status === 'expired' || status === 'error') return 'danger'
  if (status === 'login_required' || status === 'unknown') return 'warning'
  return 'info'
}

function browserEnvironmentLastReportTime(account: SelfMediaAccount) {
  const binding = browserEnvironmentAccountOf(account)
  return binding?.lastVerifiedAt || binding?.lastLoginSeenAt || '-'
}

function browserEnvironmentLoginStatusReason(account: SelfMediaAccount) {
  const binding = browserEnvironmentAccountOf(account)
  if (!binding) return ''
  if (binding.loginStatus === 'logged_in') return ''
  const message = binding.lastErrorMessage || ''
  const code = binding.lastErrorCode || ''
  if (message && code) return `${code}：${message}`
  return message || code || ''
}

function browserEnvironmentLoginStatusIsProblem(account: SelfMediaAccount) {
  const status = browserEnvironmentAccountOf(account)?.loginStatus
  return Boolean(status && status !== 'logged_in' && status !== 'unknown')
}

function cookieCredentialRiskLabel(account: SelfMediaAccount) {
  if (!account.cookieCredentialStatus || account.cookieCredentialStatus === 'none') return '未采集'
  if (account.cookieCredentialStatus === 'expired') return '已过期'
  if (!account.cookieCredentialExpiresAt) return '未记录到期'
  const days = daysUntil(account.cookieCredentialExpiresAt)
  if (days === null) return '到期未知'
  if (days < 0) return '已过期'
  if (days <= 3) return '即将到期'
  if (days <= 7) return '临期'
  return '正常'
}

function cookieCredentialRiskTag(account: SelfMediaAccount): 'success' | 'warning' | 'danger' | 'info' {
  if (!account.cookieCredentialStatus || account.cookieCredentialStatus === 'none') return 'warning'
  if (account.cookieCredentialStatus === 'expired') return 'danger'
  if (!account.cookieCredentialExpiresAt) return 'info'
  const days = daysUntil(account.cookieCredentialExpiresAt)
  if (days === null) return 'info'
  if (days < 0) return 'danger'
  if (days <= 7) return 'warning'
  return 'success'
}

function daysUntil(value?: string | null) {
  if (!value) return null
  const time = Date.parse(value)
  if (Number.isNaN(time)) return null
  return Math.ceil((time - Date.now()) / 86_400_000)
}

function browserEnvironmentOptionLabel(environment: BrowserEnvironment) {
  const name = environment.name || environment.environmentKey
  return `${name}（${environment.environmentKey}）`
}

function openBrowserEnvironmentCreate() {
  editingBrowserEnvironment.value = null
  browserEnvironmentForm.environmentKey = ''
  browserEnvironmentForm.providerProfileId = ''
  browserEnvironmentForm.name = ''
  browserEnvironmentForm.status = 'active'
  browserEnvironmentVisible.value = true
}

function openBrowserEnvironmentPrimaryAction() {
  const environment = defaultBrowserEnvironment.value || browserEnvironments.value[0]
  if (environment) {
    openBrowserEnvironmentEdit(environment)
  } else {
    openBrowserEnvironmentCreate()
  }
}

function openBrowserEnvironmentEdit(environment: BrowserEnvironment) {
  editingBrowserEnvironment.value = environment
  browserEnvironmentForm.environmentKey = environment.environmentKey
  browserEnvironmentForm.providerProfileId = environment.providerProfileId
  browserEnvironmentForm.name = environment.name || ''
  browserEnvironmentForm.status = environment.status || 'active'
  browserEnvironmentVisible.value = true
}

async function openAdspowerProfileImport() {
  adspowerProfileImportVisible.value = true
  if (!adspowerProfiles.value.length) {
    await loadAdspowerProfiles()
  }
}

async function loadAdspowerProfiles() {
  adspowerProfilesLoading.value = true
  try {
    const response = await listLocalHelperAdspowerProfiles(
      {
        helperBase: LOCAL_HELPER_BASE,
        localAgentSessionId: automationReadiness.value?.localAgent.sessionId || null,
      },
      {
        page: 1,
        pageSize: 50,
        search: adspowerProfileSearch.value,
      },
    )
    adspowerProfiles.value = response.list || []
    if (selectedAdspowerProfileId.value && !adspowerProfiles.value.some((item) => item.providerProfileId === selectedAdspowerProfileId.value)) {
      selectedAdspowerProfileId.value = ''
    }
  } catch (error) {
    adspowerProfiles.value = []
    ElMessage.error(error instanceof Error ? error.message : '读取本机 AdsPower 环境失败')
  } finally {
    adspowerProfilesLoading.value = false
  }
}

function selectAdspowerProfile(profile?: LocalHelperAdspowerProfile | null) {
  selectedAdspowerProfileId.value = profile?.providerProfileId || ''
}

function generatedBrowserEnvironmentKey() {
  return `brand_${brandId}_adspower`
}

async function importSelectedAdspowerProfile() {
  const profile = adspowerProfiles.value.find((item) => item.providerProfileId === selectedAdspowerProfileId.value)
  if (!profile) {
    ElMessage.warning('请选择要导入的 AdsPower 环境')
    return
  }
  const target = defaultBrowserEnvironment.value || browserEnvironments.value[0] || null
  const name = profile.name || profile.providerProfileId
  adspowerProfileImportSaving.value = true
  try {
    if (target) {
      await updateBrowserEnvironment(target.id, {
        providerProfileId: profile.providerProfileId,
        name,
        status: 'active',
      })
    } else {
      await createBrowserEnvironment({
        brandId,
        provider: 'adspower',
        environmentKey: generatedBrowserEnvironmentKey(),
        providerProfileId: profile.providerProfileId,
        name,
      })
    }
    ElMessage.success('AdsPower 浏览器环境已导入并启用')
    adspowerProfileImportVisible.value = false
    await loadBrowserEnvironments()
    if (hasUnboundSemiAutoAccounts.value) {
      await bindAllUnboundSemiAutoAccounts()
    }
    await loadAutomationReadiness()
    if (!automationReadiness.value?.extensionBinding.online) {
      await openDefaultEnvironmentForExtensionBinding({ autoTriggered: true })
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导入 AdsPower 浏览器环境失败')
  } finally {
    adspowerProfileImportSaving.value = false
  }
}

async function submitBrowserEnvironment() {
  const environmentKey = browserEnvironmentForm.environmentKey.trim()
  const providerProfileId = browserEnvironmentForm.providerProfileId.trim()
  if (!environmentKey || !providerProfileId) {
    ElMessage.warning('请填写环境代号和 AdsPower 浏览器编号')
    return
  }
  browserEnvironmentSaving.value = true
  try {
    if (editingBrowserEnvironment.value) {
      await updateBrowserEnvironment(editingBrowserEnvironment.value.id, {
        providerProfileId,
        name: browserEnvironmentForm.name.trim() || environmentKey,
        status: browserEnvironmentForm.status,
      })
    } else {
      await createBrowserEnvironment({
        brandId,
        provider: 'adspower',
        environmentKey,
        providerProfileId,
        name: browserEnvironmentForm.name.trim() || environmentKey,
      })
    }
    ElMessage.success('AdsPower 浏览器环境已保存')
    browserEnvironmentVisible.value = false
    await loadBrowserEnvironments()
    if (!editingBrowserEnvironment.value) {
      await bindAllUnboundSemiAutoAccounts()
    }
    await loadAutomationReadiness()
    if (!automationReadiness.value?.extensionBinding.online) {
      await openDefaultEnvironmentForExtensionBinding({ autoTriggered: true })
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存 AdsPower 浏览器环境失败')
  } finally {
    browserEnvironmentSaving.value = false
  }
}

async function removeBrowserEnvironment(environment: BrowserEnvironment) {
  try {
    await ElMessageBox.confirm(
      `确认删除 AdsPower 浏览器环境「${environment.name || environment.environmentKey}」？已绑定账号需要先解绑后才能删除。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )
    await deleteBrowserEnvironment(environment.id)
    ElMessage.success('AdsPower 浏览器环境已删除')
    await loadBrowserEnvironments()
    await loadAutomationReadiness()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err instanceof Error ? err.message : '删除 AdsPower 浏览器环境失败')
  }
}

function openEnvironmentBindingDialog(account: SemiAutoSelfMediaAccount) {
  environmentBindingTargetAccount.value = account
  environmentBindingForm.browserEnvironmentId = defaultBrowserEnvironment.value?.id || null
  environmentBindingVisible.value = true
}

async function bindAccountToDefaultEnvironment(account: SemiAutoSelfMediaAccount, silent = false) {
  const environment = defaultBrowserEnvironment.value
  if (!environment) {
    if (!silent) ElMessage.warning('请先配置并启用品牌 AdsPower 浏览器环境')
    return false
  }
  if (browserEnvironmentAccountOf(account)) {
    return true
  }
  await createBrowserEnvironmentAccount({
    browserEnvironmentId: environment.id,
    selfMediaAccountId: account.id,
    expectedPlatformAccountId: null,
    expectedAccountName: null,
  })
  return true
}

async function submitEnvironmentBinding() {
  const account = environmentBindingTargetAccount.value
  if (!account || !environmentBindingForm.browserEnvironmentId) {
    ElMessage.warning('请选择要绑定的 AdsPower 浏览器环境')
    return
  }
  environmentBindingSaving.value = true
  try {
    await createBrowserEnvironmentAccount({
      browserEnvironmentId: environmentBindingForm.browserEnvironmentId,
      selfMediaAccountId: account.id,
      expectedPlatformAccountId: null,
      expectedAccountName: null,
    })
    ElMessage.success('已绑定浏览器环境，请在对应环境登录平台，扩展会自动上报登录状态')
    environmentBindingVisible.value = false
    environmentBindingTargetAccount.value = null
    environmentBindingForm.browserEnvironmentId = null
    await loadSelfMediaAccounts()
    await loadAutomationReadiness()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建浏览器环境绑定失败')
  } finally {
    environmentBindingSaving.value = false
  }
}

async function bindAllUnboundSemiAutoAccounts() {
  if (!defaultBrowserEnvironment.value) {
    ElMessage.warning('请先配置并启用品牌 AdsPower 浏览器环境')
    return
  }
  const targets = environmentSelfMediaAccounts.value.filter((account) => !browserEnvironmentAccountOf(account))
  if (!targets.length) {
    ElMessage.success('所有头条/百家号/知乎/小红书账号均已绑定浏览器环境')
    return
  }
  environmentBindingSaving.value = true
  try {
    let count = 0
    for (const account of targets) {
      if (await bindAccountToDefaultEnvironment(account, true)) count += 1
    }
    ElMessage.success(`已为 ${count} 个账号绑定默认浏览器环境`)
    await loadSelfMediaAccounts()
    await loadAutomationReadiness()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '补齐环境绑定失败')
  } finally {
    environmentBindingSaving.value = false
  }
}

async function resetEnvironmentAccountIdentity(account: SemiAutoSelfMediaAccount) {
  const binding = browserEnvironmentAccountOf(account)
  if (!binding) return
  try {
    await ElMessageBox.confirm(
      `确认重置「${account.accountName}」的账号校验？重置后请重新打开环境登录，扩展会自动上报登录状态。`,
      '重置确认',
      {
        type: 'warning',
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
      },
    )
    const { data } = await resetBrowserEnvironmentAccountLoginIdentity(binding.id)
    browserEnvironmentAccounts.value = {
      ...browserEnvironmentAccounts.value,
      [account.id]: data.data,
    }
    ElMessage.success('账号校验已重置')
    await loadAutomationReadiness()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err instanceof Error ? err.message : '重置账号校验失败')
  }
}

async function unbindEnvironmentAccount(account: SemiAutoSelfMediaAccount) {
  const binding = browserEnvironmentAccountOf(account)
  if (!binding) return
  try {
    await ElMessageBox.confirm(
      `确认解除「${account.accountName}」与浏览器环境「${binding.environmentKey || '-'}」的绑定？解绑后该账号不能使用 AdsPower 浏览器环境分发。`,
      '解绑确认',
      {
        type: 'warning',
        confirmButtonText: '确认解绑',
        cancelButtonText: '取消',
      },
    )
    await deleteBrowserEnvironmentAccount(binding.id)
    browserEnvironmentAccounts.value = {
      ...browserEnvironmentAccounts.value,
      [account.id]: null,
    }
    ElMessage.success('环境绑定已解除')
    await loadAutomationReadiness()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err instanceof Error ? err.message : '解除环境绑定失败')
  }
}

async function deleteSelfMediaAccountRecord(account: SelfMediaAccount) {
  if (browserEnvironmentAccountOf(account)) {
    ElMessage.warning('请先解绑浏览器环境后再删除账号记录')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除「${account.accountName}」的${selfMediaPlatformLabel(account.platform)}账号记录？删除后如需继续分发，需要重新配置或授权。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )
    await deleteSelfMediaAccount(account.id)
    ElMessage.success('自媒体账号记录已删除')
    await loadSelfMediaAccounts()
    await loadAutomationReadiness()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err instanceof Error ? err.message : '删除自媒体账号记录失败')
  }
}

function channelGroupLabel(value?: string | null) {
  if (!value) return '-'
  return channelGroups.find((item) => item.value === value)?.label || value
}

function channelSubOptions(groupCode?: string | null) {
  return groupCode ? (subOptions[groupCode] || []) : []
}

function channelSubLabel(groupCode?: string | null, subCode?: string | null) {
  if (!subCode || subCode === '_ALL_') return '全部平台'
  return channelSubOptions(groupCode).find((item) => item.value === subCode)?.label || subCode
}

function perspectiveLabel(code?: string | null) {
  if (!code) return '-'
  return templatePerspectives.value.find((item) => item.code === code)?.name || code
}

function handleIndustrySiteChange(value: string) {
  const site = industrySiteOptions.value.find((item) => item.siteCode === value)
  brandForm.industrySiteName = site?.siteName || ''
}

function parseIndustryTags(value?: string | string[] | null) {
  if (Array.isArray(value)) return value
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function fillForm(data: Brand) {
  brandForm.brandName = data.brandName
  brandForm.brandShortName = data.brandShortName || ''
  brandForm.brandSlug = data.brandSlug
  brandForm.industry = data.industry || availableBrandIndustries.value[0] || ''
  brandForm.complianceIndustryCode = data.complianceIndustryCode || 'none'
  brandForm.coverableIndustries = parseCoverableIndustries(data.coverableIndustries)
  brandForm.allowThirdPartyPromotion = data.allowThirdPartyPromotion !== false
  brandForm.mainBusiness = data.mainBusiness || ''
  brandForm.coreProducts = data.coreProducts || ''
  brandForm.brandPositioning = data.brandPositioning || ''
  brandForm.regionCodes = regionCodesFromPayload(data)
  brandForm.website = data.website || ''
  brandForm.phone = data.phone || ''
  brandForm.publicPhone = data.publicPhone || ''
  brandForm.publicAddress = data.publicAddress || ''
  brandForm.selfMediaPublishLocationName = data.selfMediaPublishLocationName || ''
  brandForm.wechat = data.wechat || ''
  brandForm.status = data.status || 'active'
  brandForm.businessIntro = data.businessIntro || ''
  brandForm.brandQualificationDescription = data.brandQualificationDescription || ''
  brandForm.brandCaseDescription = data.brandCaseDescription || ''
  brandForm.forbiddenPhrases = Array.isArray(data.forbiddenPhrases)
    ? data.forbiddenPhrases.join('，')
    : (data.forbiddenPhrases || '')
  brandForm.medicalLicense = data.medicalLicense || ''
  brandForm.diagnosisScope = data.diagnosisScope || ''
  brandForm.institutionType = data.institutionType || ''
  brandForm.practitionerInfoPublic = data.practitionerInfoPublic || ''
  brandForm.medicalAdReviewNo = data.medicalAdReviewNo || ''
  brandForm.complianceNotesMedical = data.complianceNotesMedical || ''
  brandForm.geoSiteName = data.geoSiteName || ''
  brandForm.geoSiteDomain = data.geoSiteDomain || ''
  brandForm.geoSiteStatus = data.geoSiteStatus || ''
  brandForm.industrySiteName = data.industrySiteName || ''
  brandForm.industrySiteCode = data.industrySiteCode || ''
}

async function loadPublishSiteOptions() {
  if (!canUpdateBrand.value) {
    publishSites.value = []
    return
  }
  try {
    const { data } = await getPublishSites({ status: 'active' })
    publishSites.value = data.data || []
  } catch {
    publishSites.value = []
  }
}

async function load() {
  loading.value = true
  try {
    await loadPublishSiteOptions()
    const { data } = await getBrandDetail(brandId)
    brand.value = data.data
    fillForm(data.data)
    if (data.data.companyId) {
      const companyRes = await getCompanyDetail(data.data.companyId)
      companyName.value = companyRes.data.data.companyName || ''
      companyIndustryTags.value = parseIndustryTags((companyRes.data.data as any).industryTags)
      if (!brandForm.industry) {
        brandForm.industry = companyIndustryTags.value[0] || ''
      }
    } else {
      companyName.value = ''
      companyIndustryTags.value = []
    }
    await Promise.all([
      loadOfferings(),
      loadSelfMediaAccountContext(),
      loadPerspectiveConfigs(),
      loadSubjectPool(),
      canUpdateBrand.value ? loadBrowserEnvironments() : Promise.resolve(),
      canUpdateBrand.value ? loadExtensionSessions() : Promise.resolve(),
      canUpdateBrand.value ? loadAutomationReadiness() : Promise.resolve(),
    ])
  } catch {
    brand.value = null
    companyName.value = ''
    offerings.value = []
    selfMediaAccounts.value = []
    selfMediaAccountPlatformOptions.value = []
    browserEnvironments.value = []
    browserEnvironmentAccounts.value = {}
    extensionSessions.value = []
    automationReadiness.value = null
    subjectPool.value = null
  } finally {
    loading.value = false
  }
}

async function loadSubjectPool() {
  if (!hasValidId) return
  subjectPoolLoading.value = true
  try {
    const { data } = await getThirdPartySubjectPool(brandId)
    subjectPool.value = data.data || null
    subjectPoolDraftIndustries.value = parseCoverableIndustries(subjectPool.value?.coverableIndustries || brand.value?.coverableIndustries)
    subjectPoolDraftItems.value = [...(subjectPool.value?.candidates || [])]
    subjectPoolManualAddIds.value = []
  } catch {
    subjectPool.value = null
    subjectPoolDraftIndustries.value = []
    subjectPoolDraftItems.value = []
    subjectPoolManualAddIds.value = []
  } finally {
    subjectPoolLoading.value = false
  }
}

async function generateSubjectPoolSuggestion(mode: 'initial' | 'incremental') {
  if (!hasValidId) return
  const coverableIndustries = normalizeSubjectPoolIndustries(subjectPoolDraftIndustries.value)
  if (!coverableIndustries.length) {
    ElMessage.warning('请先填写信源可覆盖行业')
    return
  }
  subjectPoolSuggesting.value = true
  try {
    const { data } = await suggestThirdPartySubjectPool(brandId, { coverableIndustries, mode })
    subjectPool.value = data.data || null
    subjectPoolDraftIndustries.value = coverableIndustries
    const existing = mode === 'incremental' ? subjectPoolDraftItems.value : []
    subjectPoolDraftItems.value = mergeSubjectPoolItems(existing, subjectPool.value?.candidates || [])
    subjectPoolManualAddIds.value = []
    if (subjectPool.value?.llmFailed) {
      ElMessage.warning(subjectPool.value.llmFailureMessage || '模型匹配失败，可手动选择主体')
    } else {
      ElMessage.success(mode === 'incremental' ? '刷新覆盖完成，请确认主体池' : '覆盖品牌已生成，请确认主体池')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生成覆盖品牌失败')
  } finally {
    subjectPoolSuggesting.value = false
  }
}

async function confirmSubjectPool() {
  if (!hasValidId) return
  const coverableIndustries = normalizeSubjectPoolIndustries(subjectPoolDraftIndustries.value)
  if (!coverableIndustries.length) {
    ElMessage.warning('请先填写信源可覆盖行业')
    return
  }
  subjectPoolSaving.value = true
  try {
    const { data } = await saveThirdPartySubjectPool(brandId, {
      coverableIndustries,
      subjects: subjectPoolDraftItems.value.map((item) => ({
        brandId: item.brandId,
        matchSource: item.matchSource || 'manual',
        matchedIndustry: item.matchedIndustry || item.industry || null,
      })),
    })
    subjectPool.value = data.data || null
    subjectPoolDraftIndustries.value = parseCoverableIndustries(subjectPool.value?.coverableIndustries || coverableIndustries)
    subjectPoolDraftItems.value = [...(subjectPool.value?.candidates || [])]
    subjectPoolManualAddIds.value = []
    if (brand.value) {
      brand.value.coverableIndustries = subjectPoolDraftIndustries.value
    }
    ElMessage.success('第三方主体池已确认')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '确认主体池失败')
  } finally {
    subjectPoolSaving.value = false
  }
}

function addManualSubjectPoolItems() {
  if (!subjectPoolManualAddIds.value.length) return
  const optionMap = new Map((subjectPool.value?.availableSubjects || []).map((item) => [item.brandId, item]))
  const additions = subjectPoolManualAddIds.value
    .map((id) => optionMap.get(id))
    .filter((item): item is ThirdPartySubjectPoolItem => !!item)
    .map((item) => ({ ...item, matchSource: 'manual', matchedIndustry: item.matchedIndustry || item.industry || null }))
  subjectPoolDraftItems.value = mergeSubjectPoolItems(subjectPoolDraftItems.value, additions)
  subjectPoolManualAddIds.value = []
}

function removeSubjectPoolItem(brandId: number) {
  subjectPoolDraftItems.value = subjectPoolDraftItems.value.filter((item) => item.brandId !== brandId)
}

function mergeSubjectPoolItems(base: ThirdPartySubjectPoolItem[], additions: ThirdPartySubjectPoolItem[]) {
  const map = new Map<number, ThirdPartySubjectPoolItem>()
  for (const item of base) {
    if (item?.brandId) map.set(item.brandId, item)
  }
  for (const item of additions) {
    if (item?.brandId) map.set(item.brandId, item)
  }
  return Array.from(map.values())
}

function normalizeSubjectPoolIndustries(values: string[]) {
  const list = (values || []).map((item) => String(item || '').trim()).filter(Boolean)
  if (list.some((item) => item.toUpperCase() === '__ALL__')) return ['__ALL__']
  return Array.from(new Set(list))
}

function subjectPoolMatchSourceLabel(value?: string | null) {
  if (value === 'direct') return '直接匹配'
  if (value === 'llm') return '模型匹配'
  if (value === 'manual') return '人工添加'
  return '-'
}

async function refreshSubjectPoolForPerspective() {
  if (hasThirdPartySelfMediaPerspective.value) {
    await loadSubjectPool()
  } else {
    subjectPool.value = null
  }
}

async function loadOfferings() {
  offeringsLoading.value = true
  try {
    const { data } = await getBrandOfferings(brandId)
    offerings.value = data.data || []
  } finally {
    offeringsLoading.value = false
  }
}

async function loadBrowserEnvironments() {
  if (!canUpdateBrand.value) {
    browserEnvironments.value = []
    return
  }
  browserEnvironmentsLoading.value = true
  try {
    const { data } = await listBrowserEnvironments(brandId)
    browserEnvironments.value = data.data || []
  } finally {
    browserEnvironmentsLoading.value = false
  }
}

async function loadAutomationReadiness(options: { silent?: boolean } = {}) {
  if (!hasValidId) return
  if (!canUpdateBrand.value) {
    automationReadiness.value = null
    return
  }
  if (!options.silent) automationReadinessLoading.value = true
  try {
    const { data } = await getSelfMediaAutomationReadiness(brandId)
    automationReadiness.value = data.data
  } catch (error) {
    automationReadiness.value = null
    if (!options.silent) {
      ElMessage.error(error instanceof Error ? error.message : '加载自动化就绪状态失败')
    }
  } finally {
    if (!options.silent) automationReadinessLoading.value = false
  }
}

async function loadExtensionSessions(options: { silent?: boolean } = {}) {
  if (!hasValidId || !canUpdateBrand.value) return
  if (!options.silent) extensionSessionsLoading.value = true
  try {
    const { data } = await listBrandExtensionSessions(brandId)
    extensionSessions.value = data.data || []
  } catch (error) {
    extensionSessions.value = []
    if (!options.silent) {
      ElMessage.error(error instanceof Error ? error.message : '加载扩展绑定状态失败')
    }
  } finally {
    if (!options.silent) extensionSessionsLoading.value = false
  }
}

async function refreshExtensionBindingStatus() {
  extensionSessionsLoading.value = true
  try {
    await Promise.all([
      loadExtensionSessions({ silent: true }),
      loadAutomationReadiness({ silent: true }),
      inspectDefaultEnvironmentExtension({ silent: true }),
    ])
  } catch {
    // Individual loaders already normalize their own error state.
  } finally {
    extensionSessionsLoading.value = false
  }
}

async function handleAutomationIssueAction(issue: SelfMediaAutomationReadinessIssue) {
  const actionKey = issue.actionKey
  if (!actionKey) return
  automationIssueActionLoading.value = actionKey
  try {
    switch (actionKey) {
      case 'OPEN_LOCAL_HELPER_SETUP':
        await router.push(userStore.isPartner ? '/partner/profile' : '/admin/profile')
        return
      case 'IMPORT_ADSPOWER_ENVIRONMENT':
        await openAdspowerProfileImport()
        return
      case 'EDIT_BROWSER_ENVIRONMENT':
        openBrowserEnvironmentPrimaryAction()
        return
      case 'OPEN_AND_BIND_EXTENSION':
        await openDefaultEnvironmentForExtensionBinding({ autoTriggered: true })
        return
      case 'BIND_UNBOUND_ACCOUNTS':
        await bindAllUnboundSemiAutoAccounts()
        return
      case 'OPEN_ADSPOWER_ENVIRONMENT':
        await openDefaultBrowserEnvironment()
        return
      case 'CREATE_SELF_MEDIA_ACCOUNT':
        openSelfMediaAccountCreate()
        return
      default:
        ElMessage.info(issue.action || '请按提示继续处理')
    }
  } finally {
    automationIssueActionLoading.value = null
  }
}

async function generateBrandExtensionBindCode() {
  if (!brand.value) return
  extensionBindCodeLoading.value = true
  try {
    const { data } = await createExtensionBindCode(brandId)
    extensionBindCode.value = data.data
    ElMessage.success('扩展绑定码已生成')
    await loadAutomationReadiness()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生成扩展绑定码失败')
  } finally {
    extensionBindCodeLoading.value = false
  }
}

function localHelperClientConfig() {
  return {
    helperBase: LOCAL_HELPER_BASE,
    localAgentSessionId: automationReadiness.value?.localAgent.sessionId || null,
  }
}

async function inspectDefaultEnvironmentExtension(options: { silent?: boolean } = {}) {
  const environment = defaultBrowserEnvironment.value
  if (!environment?.environmentKey || !environment.providerProfileId) return null
  try {
    const response = await inspectLocalHelperAdspowerExtension(localHelperClientConfig(), {
      environmentKey: environment.environmentKey,
      providerProfileId: environment.providerProfileId,
      environmentName: environment.name || environment.environmentKey,
    })
    adspowerExtensionStatus.value = response?.extensionStatus || null
    return adspowerExtensionStatus.value
  } catch (error) {
    adspowerExtensionStatus.value = {
      installed: false,
      detected: false,
      status: 'unknown',
      reason: error instanceof Error ? error.message : String(error),
    }
    if (!options.silent) {
      ElMessage.warning(error instanceof Error ? error.message : '未能探测 AdsPower 环境扩展状态')
    }
    return adspowerExtensionStatus.value
  }
}

async function syncBrowserEnvironmentLoginStatus() {
  const environment = defaultBrowserEnvironment.value
  if (!environment?.environmentKey || !environment.providerProfileId) {
    ElMessage.warning('请先配置并启用 AdsPower 浏览器环境')
    return
  }
  const accounts = syncableEnvironmentSelfMediaAccounts.value.map((account) => {
    const binding = browserEnvironmentAccountOf(account)
    const platform = normalizeSelfMediaPlatformForLogin(account.platform)
    return {
      platform,
      loginUrl: selfMediaPlatformLoginReportUrl(platform, {
        environmentKey: environment.environmentKey,
        browserEnvironmentAccountId: binding?.id || null,
        selfMediaAccountId: account.id || null,
      }),
      browserEnvironmentAccountId: binding?.id || null,
    }
  }).filter((account) => account.platform && account.loginUrl && account.browserEnvironmentAccountId)
  if (!accounts.length) {
    ElMessage.warning('当前品牌没有已绑定环境的自媒体账号，请先补齐环境绑定')
    return
  }
  loginStatusSyncing.value = true
  try {
    const openedPlatforms = new Set<string>()
    const openFailures: string[] = []
    for (const account of accounts) {
      if (openedPlatforms.has(account.platform)) continue
      openedPlatforms.add(account.platform)
      try {
        await openLocalHelperEnvironment(localHelperClientConfig(), {
          environmentKey: environment.environmentKey,
          providerProfileId: environment.providerProfileId,
          environmentName: environment.name || environment.environmentKey,
          url: account.loginUrl,
        })
      } catch (error) {
        const platformName = selfMediaPlatformLabel(account.platform)
        const message = error instanceof Error ? error.message : String(error || '')
        openFailures.push(`${platformName}：${message || '打开失败'}`)
      }
      await delay(2200)
    }
    if (openFailures.length) {
      ElMessage.warning(`部分平台登录身份页打开失败：${openFailures.slice(0, 2).join('；')}${openFailures.length > 2 ? ' 等' : ''}`)
    } else {
      ElMessage.info('已打开各平台登录身份页；扩展将在页面加载完成后自动上报登录状态')
    }
    await delay(7000)
    await Promise.all([
      loadAutomationReadiness({ silent: true }),
      loadSelfMediaAccounts(),
      refreshExtensionBindingStatus(),
    ])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '同步 AdsPower 登录状态失败')
  } finally {
    loginStatusSyncing.value = false
  }
}

function normalizeSelfMediaPlatformForLogin(value?: string | null) {
  const text = String(value || '').trim().toLowerCase()
  if (text === 'douyin_image_text') return 'douyin'
  if (text === 'wechat') return 'wechat_mp'
  return text
}

function selfMediaPlatformLoginReportUrl(platform?: string | null, options: {
  environmentKey?: string | null
  browserEnvironmentAccountId?: number | string | null
  selfMediaAccountId?: number | string | null
} = {}) {
  const normalized = normalizeSelfMediaPlatformForLogin(platform)
  let url = ''
  if (normalized === 'toutiao') url = 'https://mp.toutiao.com/profile_v4/personal/info'
  if (normalized === 'zhihu') url = 'https://www.zhihu.com/creator/manage/creation/article'
  if (normalized === 'xiaohongshu') url = 'https://creator.xiaohongshu.com/new/home?source=official'
  if (normalized === 'baijiahao') url = 'https://baijiahao.baidu.com/builder/rc/settings/accountSet'
  if (normalized === 'douyin') url = 'https://creator.douyin.com/creator-micro/home'
  if (!url) return ''
  return withLoginReportContext(url, {
    platform: normalized,
    environmentKey: options.environmentKey,
    browserEnvironmentAccountId: options.browserEnvironmentAccountId,
    selfMediaAccountId: options.selfMediaAccountId,
  })
}

function withLoginReportContext(urlValue: string, options: {
  platform?: string | null
  environmentKey?: string | null
  browserEnvironmentAccountId?: number | string | null
  selfMediaAccountId?: number | string | null
}) {
  const url = new URL(urlValue)
  url.searchParams.set('geoEnvLoginReport', '1')
  if (options.platform) url.searchParams.set('geoEnvPlatform', options.platform)
  if (options.environmentKey) url.searchParams.set('geoEnvEnvironmentKey', String(options.environmentKey))
  if (options.browserEnvironmentAccountId) {
    url.searchParams.set('geoEnvEnvironmentAccountId', String(options.browserEnvironmentAccountId))
  }
  if (options.selfMediaAccountId) {
    url.searchParams.set('geoEnvSelfMediaAccountId', String(options.selfMediaAccountId))
  }
  return url.toString()
}

async function openDefaultBrowserEnvironment() {
  const environment = defaultBrowserEnvironment.value
  if (!environment?.environmentKey || !environment.providerProfileId) {
    ElMessage.warning('请先配置并启用 AdsPower 浏览器环境')
    return
  }
  await openLocalHelperEnvironment(localHelperClientConfig(), {
    environmentKey: environment.environmentKey,
    providerProfileId: environment.providerProfileId,
    environmentName: environment.name || environment.environmentKey,
  })
  ElMessage.success('已打开 AdsPower 环境，请完成平台登录后等待扩展自动上报')
  await refreshExtensionBindingStatus()
}

async function openDefaultEnvironmentForExtensionBinding(options: { autoTriggered?: boolean } = {}) {
  const environment = defaultBrowserEnvironment.value
  if (!environment?.environmentKey || !environment.providerProfileId) {
    ElMessage.warning('请先配置并启用 AdsPower 浏览器环境')
    return
  }
  extensionEnvironmentOpening.value = true
  try {
    const existingSessionIds = new Set(extensionSessions.value.map((item) => item.id))
    await inspectDefaultEnvironmentExtension({ silent: true })
    const { data } = await createExtensionBindCode(brandId)
    extensionBindCode.value = data.data
    const helperConfig = localHelperClientConfig()
    const helperHealth = await getLocalHelperHealth(LOCAL_HELPER_BASE).catch(() => null)
    const apiBase = helperHealth?.config?.trustedBackendBase || helperHealth?.config?.backendBase || window.location.origin
    const intent = await createLocalHelperExtensionBindIntent(helperConfig, {
      bindCode: data.data.code,
      brandId,
      apiBase,
      helperBase: LOCAL_HELPER_BASE,
      environmentKey: environment.environmentKey,
      providerProfileId: environment.providerProfileId,
      environmentName: environment.name || environment.environmentKey,
      expiresInSeconds: Math.min(data.data.expiresInSeconds || 120, 120),
    })
    const bindUrl = extensionAutoBindUrl(intent.intentToken)
    const openResult = await openLocalHelperEnvironment(
      helperConfig,
      {
        environmentKey: environment.environmentKey,
        providerProfileId: environment.providerProfileId,
        environmentName: environment.name || environment.environmentKey,
        url: bindUrl,
      },
    )
    adspowerExtensionStatus.value = openResult?.extensionStatus || adspowerExtensionStatus.value
    ElMessage.success(options.autoTriggered
      ? 'AdsPower 环境已导入，正在自动打开环境并绑定扩展'
      : '已打开 AdsPower 环境，环境扩展会自动尝试绑定当前品牌')
    const bound = await waitForExtensionBindingStatus(existingSessionIds)
    if (bound) {
      ElMessage.success('已检测到环境扩展完成绑定')
    } else if (adspowerExtensionStatus.value?.status === 'not_detected') {
      ElMessage.warning('未检测到 GEO 自媒体助手扩展运行；请确认该 AdsPower 环境已安装并启用 geo-env-extension 后再重试')
    } else {
      ElMessage.warning('暂未检测到扩展绑定结果；如未自动完成，请打开环境扩展弹窗重试或复制绑定码手动绑定')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '打开并绑定 AdsPower 环境失败')
  } finally {
    extensionEnvironmentOpening.value = false
  }
}

function extensionAutoBindUrl(intentToken: string) {
  const url = new URL(window.location.href)
  const environment = defaultBrowserEnvironment.value
  url.searchParams.set('geoEnvBindIntent', intentToken)
  url.searchParams.set('geoEnvHelperBase', LOCAL_HELPER_BASE)
  if (environment?.environmentKey) url.searchParams.set('geoEnvEnvironmentKey', environment.environmentKey)
  if (environment?.providerProfileId) url.searchParams.set('geoEnvProviderProfileId', environment.providerProfileId)
  url.searchParams.set('geoEnvAutoBind', '1')
  return url.toString()
}

function delay(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

async function waitForExtensionBindingStatus(existingSessionIds = new Set<number>()) {
  for (let attempt = 0; attempt < EXTENSION_BIND_POLL_ATTEMPTS; attempt += 1) {
    if (attempt > 0) {
      await delay(EXTENSION_BIND_POLL_INTERVAL_MS)
    }
    await Promise.all([
      loadExtensionSessions({ silent: true }),
      loadAutomationReadiness({ silent: true }),
    ])
    const hasNewSession = extensionSessions.value.some((item) => !existingSessionIds.has(item.id))
    if (automationReadiness.value?.extensionBinding.online || hasNewSession) {
      return true
    }
  }
  return false
}

async function copyBrandExtensionBindCode() {
  if (!extensionBindCode.value?.code) return
  await copyText(extensionBindCode.value.code)
  ElMessage.success('绑定码已复制')
}

async function revokeBrandExtension(session: ExtensionSession) {
  try {
    await ElMessageBox.confirm(
      `确认解绑扩展会话 ${session.id}？解绑后该 AdsPower 环境里的旧扩展 token 会失效，需要重新生成绑定码并绑定。`,
      '扩展解绑确认',
      {
        type: 'warning',
        confirmButtonText: '确认解绑',
        cancelButtonText: '取消',
      },
    )
    await revokeBrandExtensionSession(brandId, session.id)
    ElMessage.success('扩展会话已解绑')
    await loadExtensionSessions()
    await loadAutomationReadiness()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err instanceof Error ? err.message : '扩展解绑失败')
  }
}

async function copyText(text: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return
  }
  const input = document.createElement('textarea')
  input.value = text
  input.setAttribute('readonly', 'readonly')
  input.style.position = 'fixed'
  input.style.opacity = '0'
  document.body.appendChild(input)
  input.select()
  document.execCommand('copy')
  document.body.removeChild(input)
}

async function loadSelfMediaAccounts() {
  selfMediaAccountsLoading.value = true
  try {
    const { data } = await getSelfMediaAccountsByBrand(brandId)
    const accounts = data.data as SemiAutoSelfMediaAccount[]
    selfMediaAccounts.value = accounts
    if (canUpdateBrand.value) {
      await loadBrowserEnvironmentAccounts(accounts)
      await loadWechatMenuConfigs(accounts)
    } else {
      browserEnvironmentAccounts.value = {}
      wechatMenuConfigs.value = {}
    }
  } finally {
    selfMediaAccountsLoading.value = false
  }
}

async function loadWechatMenuConfigs(accounts: SemiAutoSelfMediaAccount[]) {
  const wechatAccounts = accounts.filter((account) => isWechatMpAccount(account))
  const next: Record<number, WechatMenuConfig | null> = {}
  await Promise.all(wechatAccounts.map(async (account) => {
    try {
      const { data } = await getWechatMenuConfig(account.id)
      next[account.id] = data.data || null
    } catch {
      next[account.id] = null
    }
  }))
  wechatMenuConfigs.value = next
}

async function loadSelfMediaAccountContext() {
  await loadSelfMediaAccountPlatformOptions()
  await loadSelfMediaAccounts()
}

async function checkSelfMediaAuth(account: SelfMediaAccount) {
  checkingSelfMediaAccountId.value = account.id
  try {
    await checkSelfMediaAccountAuth(account.id)
    ElMessage.success('账号授权状态已重新检测')
    await loadSelfMediaAccounts()
  } finally {
    checkingSelfMediaAccountId.value = null
  }
}

async function loadSelfMediaAccountPlatformOptions() {
  try {
    const { data } = await getSelfMediaAccountPlatformOptions(brandId)
    selfMediaAccountPlatformOptions.value = data.data || []
  } catch {
    selfMediaAccountPlatformOptions.value = []
  }
}

async function authorizeWechatMp() {
  if (!brand.value?.id) {
    ElMessage.warning('请先保存客户信息后再授权公众号')
    return
  }
  wechatAuthorizing.value = true
  try {
    const { data } = await getWechatMpAuthUrl({ brandId: brand.value.id })
    window.location.href = data.data.authUrl
  } finally {
    wechatAuthorizing.value = false
  }
}

async function initializeWechatMenuForAccount(account: SelfMediaAccount) {
  if (!isWechatMpAccount(account)) return
  try {
    await ElMessageBox.confirm(
      `确认为公众号「${account.accountName}」写入「往期文章」菜单？系统会先备份当前菜单；若菜单已满或存在冲突，会转为人工处理，不会覆盖客户现有关键菜单。`,
      '初始化公众号菜单',
      {
        type: 'warning',
        confirmButtonText: '确认初始化',
        cancelButtonText: '取消',
      },
    )
    wechatMenuInitializingId.value = account.id
    const { data } = await initializeWechatMenu(account.id)
    wechatMenuConfigs.value = {
      ...wechatMenuConfigs.value,
      [account.id]: data.data || null,
    }
    const config = data.data
    if (config?.menuStatus === 'configured') {
      ElMessage.success('菜单承接已配置，可打开 H5 页面并在微信客户端验收')
    } else {
      ElMessage.warning(`菜单初始化未完成：${wechatMenuStatusLabel(account)}`)
    }
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err instanceof Error ? err.message : '初始化公众号菜单失败')
  } finally {
    wechatMenuInitializingId.value = null
  }
}

async function loadBrowserEnvironmentAccounts(accounts: SemiAutoSelfMediaAccount[]) {
  const targets = accounts.filter((item) => isSemiAutoPlatform(item.platform) && !isOfficialApiSelfMediaPlatform(item.platform))
  if (!targets.length) {
    browserEnvironmentAccounts.value = {}
    return
  }
  const entries = await Promise.all(targets.map(async (account) => {
    try {
      const { data } = await getBrowserEnvironmentAccountBySelfMedia(account.id)
      return [account.id, data.data || null] as const
    } catch {
      return [account.id, null] as const
    }
  }))
  browserEnvironmentAccounts.value = Object.fromEntries(entries)
}

async function loadPerspectiveConfigs() {
  perspectiveConfigLoading.value = true
  try {
    const { data } = await getBrandTemplatePerspectiveConfigs(brandId)
    templatePerspectives.value = data.data.perspectives || []
    perspectiveConfigs.value = data.data.configs || []
  } finally {
    perspectiveConfigLoading.value = false
  }
}

function resetOfferingForm() {
  offeringForm.offeringName = ''
  offeringForm.offeringAliases = ''
  offeringForm.targetUsers = ''
  offeringForm.offeringIntro = ''
  offeringForm.qualificationDescription = ''
  offeringForm.remark = ''
  offeringForm.status = 'active'
  offeringForm.priority = 50
  offeringForm.useScenarios = ''
  offeringForm.medicalProjectEnabled = false
  offeringForm.medicalIndustryCode = isMedicalComplianceIndustry.value ? brandForm.complianceIndustryCode : ''
  offeringForm.medicalCategoryCode = ''
  offeringForm.medicalCategoryName = ''
  offeringForm.qualificationRef = ''
}

function openOfferingCreate() {
  editingOffering.value = null
  resetOfferingForm()
  offeringVisible.value = true
  loadSpecialIndustryCategoryOptions(offeringForm.medicalIndustryCode)
}

function openOfferingEdit(offering: BrandOffering) {
  editingOffering.value = offering
  offeringForm.offeringName = offering.offeringName || ''
  offeringForm.offeringAliases = offering.offeringAliases?.join('，') || ''
  offeringForm.targetUsers = offering.targetUsers || ''
  offeringForm.offeringIntro = offering.offeringIntro || ''
  offeringForm.qualificationDescription = offering.qualificationDescription || ''
  offeringForm.remark = offering.remark || ''
  offeringForm.status = offering.status === 'disabled' ? 'disabled' : 'active'
  offeringForm.priority = offering.priority ?? 50
  offeringForm.useScenarios = offering.useScenarios || ''
  offeringForm.medicalProjectEnabled = !!offering.medicalProjectEnabled
  offeringForm.medicalIndustryCode = offering.medicalIndustryCode || (isMedicalComplianceIndustry.value ? brandForm.complianceIndustryCode : '')
  offeringForm.medicalCategoryCode = offering.medicalCategoryCode || ''
  offeringForm.medicalCategoryName = offering.medicalCategoryName || ''
  offeringForm.qualificationRef = offering.qualificationRef || ''
  offeringVisible.value = true
  loadSpecialIndustryCategoryOptions(offeringForm.medicalIndustryCode)
}

async function submitOffering() {
  const valid = await offeringFormRef.value?.validate().catch(() => false)
  if (!valid) return
  syncOfferingMedicalCategoryName()
  if (isMedicalComplianceIndustry.value && offeringForm.medicalProjectEnabled) {
    if (!nullableText(offeringForm.medicalIndustryCode)) {
      ElMessage.warning('请选择特殊行业')
      return
    }
    if (!nullableText(offeringForm.medicalCategoryCode)) {
      ElMessage.warning('请选择特殊行业品类')
      return
    }
  }
  offeringSaving.value = true
  try {
    const payload = {
      offeringName: offeringForm.offeringName.trim(),
      offeringAliases: nullableText(offeringForm.offeringAliases),
      targetUsers: nullableText(offeringForm.targetUsers),
      offeringIntro: nullableText(offeringForm.offeringIntro),
      qualificationDescription: nullableText(offeringForm.qualificationDescription),
      remark: nullableText(offeringForm.remark),
      status: offeringForm.status,
      priority: Number(offeringForm.priority) || 50,
      useScenarios: nullableText(offeringForm.useScenarios),
      medicalProjectEnabled: isMedicalComplianceIndustry.value && offeringForm.medicalProjectEnabled,
      medicalIndustryCode: isMedicalComplianceIndustry.value ? nullableText(offeringForm.medicalIndustryCode) : null,
      medicalCategoryCode: isMedicalComplianceIndustry.value ? nullableText(offeringForm.medicalCategoryCode) : null,
      medicalCategoryName: isMedicalComplianceIndustry.value ? nullableText(offeringForm.medicalCategoryName) : null,
      qualificationRef: isMedicalComplianceIndustry.value ? nullableText(offeringForm.qualificationRef) : null,
    }
    if (editingOffering.value) {
      await updateBrandOffering(brandId, editingOffering.value.id, payload)
    } else {
      await createBrandOffering(brandId, payload)
    }
    ElMessage.success('产品信息已保存')
    offeringVisible.value = false
    await loadOfferings()
  } finally {
    offeringSaving.value = false
  }
}

async function removeOffering(offering: BrandOffering) {
  try {
    await ElMessageBox.confirm(
      `确认删除产品信息「${offering.offeringName}」？删除后文章生成不会再引用该条资料。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )
    await deleteBrandOffering(brandId, offering.id)
    ElMessage.success('产品信息已删除')
    await loadOfferings()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err instanceof Error ? err.message : '删除产品信息失败')
  }
}

function openSelfMediaAccountCreate() {
  const firstPlatform = eligibleSelfMediaPlatformOptions.value[0]?.platform
  if (!firstPlatform) {
    ElMessage.warning('当前客户套餐与排期能力暂无可新增的自媒体平台')
    return
  }
  editingSelfMediaAccount.value = null
  selfMediaAccountForm.platform = firstPlatform
  selfMediaAccountForm.accountName = ''
  selfMediaAccountForm.accountIdentity = defaultSelfMediaAccountIdentity(firstPlatform)
  selfMediaAccountForm.platformAccountId = ''
  selfMediaAccountForm.status = 'active'
  selfMediaAccountVisible.value = true
}

function openSelfMediaAccountEdit(account: SemiAutoSelfMediaAccount) {
  editingSelfMediaAccount.value = account
  selfMediaAccountForm.platform = account.platform || eligibleSelfMediaPlatformOptions.value[0]?.platform || ''
  selfMediaAccountForm.accountName = account.accountName || ''
  selfMediaAccountForm.accountIdentity = normalizeSelfMediaAccountIdentity(account.accountIdentity, selfMediaAccountForm.platform)
  selfMediaAccountForm.platformAccountId = account.platformAccountId || ''
  selfMediaAccountForm.status = account.status === 'disabled' ? 'disabled' : 'active'
  selfMediaAccountVisible.value = true
}

function handleSelfMediaPlatformChange() {
  selfMediaAccountForm.accountIdentity = defaultSelfMediaAccountIdentity(selfMediaAccountForm.platform)
  selfMediaAccountFormRef.value?.validateField('platformAccountId').catch(() => undefined)
}

function defaultSelfMediaAccountIdentity(platform?: string | null): 'personal' | 'enterprise' {
  return platform === 'baijiahao' ? 'enterprise' : 'personal'
}

function normalizeSelfMediaAccountIdentity(identity?: string | null, platform?: string | null): 'personal' | 'enterprise' {
  return identity === 'enterprise' || identity === 'personal'
    ? identity
    : defaultSelfMediaAccountIdentity(platform)
}

function selfMediaAccountIdentityLabel(identity?: string | null) {
  return normalizeSelfMediaAccountIdentity(identity) === 'enterprise' ? '企业号' : '个人号'
}

function selfMediaAccountIdentityTag(identity?: string | null): 'success' | 'info' {
  return normalizeSelfMediaAccountIdentity(identity) === 'enterprise' ? 'success' : 'info'
}

function isSemiAutoPlatform(platform?: string | null): platform is SemiAutoPlatform {
  if (!platform) return false
  const normalized = platform.trim().toLowerCase()
  if (selfMediaAccountPlatformOptions.value.some((item) => item.platform === normalized && (item.eligible || item.scheduleReady))) {
    return true
  }
  return normalized === 'toutiao'
    || normalized === 'baijiahao'
    || normalized === 'zhihu'
    || normalized === 'xiaohongshu'
    || normalized === 'douyin'
}

async function submitSelfMediaAccount() {
  const valid = await selfMediaAccountFormRef.value?.validate().catch(() => false)
  if (!valid) return
  selfMediaAccountSaving.value = true
  try {
    const payload = {
      platform: selfMediaAccountForm.platform,
      accountName: selfMediaAccountForm.accountName.trim(),
      accountIdentity: selfMediaAccountForm.accountIdentity,
      platformAccountId: selfMediaAccountForm.platformAccountId.trim() || undefined,
      status: selfMediaAccountForm.status,
    }
    if (editingSelfMediaAccount.value) {
      await updateSelfMediaAccount(editingSelfMediaAccount.value.id, payload)
    } else {
      const { data } = await createSelfMediaAccount(brandId, payload)
      const created = data.data as SemiAutoSelfMediaAccount | undefined
      if (created && isSemiAutoPlatform(created.platform)) {
        await bindAccountToDefaultEnvironment(created, true)
      }
    }
    ElMessage.success(defaultBrowserEnvironment.value ? '自媒体账号已保存，并已绑定默认环境' : '自媒体账号已保存')
    selfMediaAccountVisible.value = false
    await loadSelfMediaAccounts()
  } finally {
    selfMediaAccountSaving.value = false
  }
}

function openPerspectiveConfigCreate() {
  editingPerspectiveConfig.value = null
  perspectiveConfigForm.channelGroupCode = 'self_media'
  perspectiveConfigForm.channelSubCode = '_ALL_'
  perspectiveConfigForm.perspectiveCode = enabledPerspectives.value[0]?.code || 'customer'
  perspectiveConfigForm.enabled = true
  perspectiveConfigVisible.value = true
}

function openPerspectiveConfigEdit(config: BrandChannelTemplatePerspective) {
  editingPerspectiveConfig.value = config
  perspectiveConfigForm.channelGroupCode = config.channelGroupCode
  perspectiveConfigForm.channelSubCode = config.channelSubCode || '_ALL_'
  perspectiveConfigForm.perspectiveCode = config.perspectiveCode
  perspectiveConfigForm.enabled = config.enabled
  perspectiveConfigVisible.value = true
}

function handlePerspectiveGroupChange() {
  perspectiveConfigForm.channelSubCode = '_ALL_'
}

async function submitPerspectiveConfig() {
  const valid = await perspectiveConfigFormRef.value?.validate().catch(() => false)
  if (!valid) return
  perspectiveConfigSaving.value = true
  try {
    await saveBrandTemplatePerspectiveConfig({
      brandId,
      channelGroupCode: perspectiveConfigForm.channelGroupCode,
      channelSubCode: perspectiveConfigForm.channelSubCode || '_ALL_',
      perspectiveCode: perspectiveConfigForm.perspectiveCode,
      enabled: perspectiveConfigForm.enabled,
    })
    ElMessage.success('文章视角配置已保存')
    perspectiveConfigVisible.value = false
    await loadPerspectiveConfigs()
    await refreshSubjectPoolForPerspective()
  } finally {
    perspectiveConfigSaving.value = false
  }
}

async function removePerspectiveConfig(config: BrandChannelTemplatePerspective) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${channelGroupLabel(config.channelGroupCode)} / ${channelSubLabel(config.channelGroupCode, config.channelSubCode)}」的文章视角配置？`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )
    await deleteBrandTemplatePerspectiveConfig(config.id)
    ElMessage.success('文章视角配置已删除')
    await loadPerspectiveConfigs()
    await refreshSubjectPoolForPerspective()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  }
}

function openEdit() {
  if (!brand.value) return
  fillForm(brand.value)
  editVisible.value = true
}

async function testCurrentGeoSite() {
  if (!brand.value?.id) return
  geoSiteTesting.value = true
  try {
    const { data } = await testBrandGeoSite(brand.value.id)
    const result = data.data
    const detail = result.statusCode ? `（HTTP ${result.statusCode}）` : ''
    const endpoint = result.endpoint ? `：${result.endpoint}` : ''
    if (result.passed) {
      ElMessage.success(`${result.message}${detail}${endpoint}`)
    } else {
      ElMessage.warning(`${result.message}${detail}${endpoint}`)
    }
  } finally {
    geoSiteTesting.value = false
  }
}

async function openCoverableIndustryConfig() {
  openEdit()
  await nextTick()
  await nextTick()
  const field = coverableIndustriesFieldRef.value?.$el as HTMLElement | undefined
  field?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  const input = field?.querySelector('input') as HTMLInputElement | null
  input?.focus()
}

async function submitBrand() {
  const valid = await brandFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const region = regionPayloadFromCodes(brandForm.regionCodes)
    await updateBrand(brandId, {
      companyId: brand.value?.companyId,
      brandName: brandForm.brandName,
      brandShortName: nullableText(brandForm.brandShortName),
      brandSlug: brandForm.brandSlug,
      industry: brandForm.industry,
      complianceIndustryCode: brandForm.complianceIndustryCode === 'none' ? null : brandForm.complianceIndustryCode,
      coverableIndustries: brandForm.coverableIndustries,
      allowThirdPartyPromotion: brandForm.allowThirdPartyPromotion,
      mainBusiness: nullableText(brandForm.mainBusiness),
      coreProducts: nullableText(brandForm.coreProducts),
      brandPositioning: nullableText(brandForm.brandPositioning),
      serviceArea: region.displayName,
      provinceCode: region.provinceCode,
      provinceName: region.provinceName,
      cityCode: region.cityCode,
      cityName: region.cityName,
      districtCode: region.districtCode,
      districtName: region.districtName,
      website: nullableText(brandForm.website),
      officialAccount: nullableText(brand.value?.officialAccount),
      videoAccount: nullableText(brand.value?.videoAccount),
      douyinAccount: nullableText(brand.value?.douyinAccount),
      phone: nullableText(brandForm.phone),
      publicPhone: nullableText(brandForm.publicPhone),
      publicAddress: nullableText(brandForm.publicAddress),
      selfMediaPublishLocationName: nullableText(brandForm.selfMediaPublishLocationName),
      wechat: nullableText(brandForm.wechat),
      status: brandForm.status,
      description: nullableText(brandForm.businessIntro),
      businessIntro: nullableText(brandForm.businessIntro),
      brandQualificationDescription: nullableText(brandForm.brandQualificationDescription),
      brandCaseDescription: nullableText(brandForm.brandCaseDescription),
      forbiddenPhrases: nullableText(brandForm.forbiddenPhrases),
      medicalLicense: isMedicalComplianceIndustry.value ? nullableText(brandForm.medicalLicense) : null,
      diagnosisScope: isMedicalComplianceIndustry.value ? nullableText(brandForm.diagnosisScope) : null,
      institutionType: isMedicalComplianceIndustry.value ? nullableText(brandForm.institutionType) : null,
      practitionerInfoPublic: isMedicalComplianceIndustry.value ? nullableText(brandForm.practitionerInfoPublic) : null,
      medicalAdReviewNo: isMedicalComplianceIndustry.value ? nullableText(brandForm.medicalAdReviewNo) : null,
      complianceNotesMedical: isMedicalComplianceIndustry.value ? nullableText(brandForm.complianceNotesMedical) : null,
      geoSiteName: nullableText(brandForm.geoSiteName),
      geoSiteDomain: nullableText(brandForm.geoSiteDomain),
      geoSiteStatus: nullableText(brandForm.geoSiteDomain) ? brandForm.geoSiteStatus || 'active' : null,
      industrySiteName: nullableText(brandForm.industrySiteName),
      industrySiteCode: nullableText(brandForm.industrySiteCode),
    })
    ElMessage.success('品牌信息已更新')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function removeCurrentBrand() {
  if (!brand.value) return
  try {
    await ElMessageBox.confirm(`确认删除品牌「${brand.value.brandName}」？该操作不可撤销。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    await deleteBrand(brandId)
    ElMessage.success('删除成功')
    if (brand.value.companyId) {
      router.push(`/admin/customers/${brand.value.companyId}`)
    } else {
      router.push('/admin/customers')
    }
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  }
}

function goCreateProject() {
  if (!brand.value?.companyId) {
    ElMessage.warning('未找到所属客户，无法创建项目')
    return
  }
  router.push({ path: '/admin/projects', query: { companyId: String(brand.value.companyId), brandId: String(brandId) } })
}

function goCompanyDetail() {
  if (!brand.value?.companyId) return
  router.push(`/admin/customers/${brand.value.companyId}`)
}

watch(
  () => brandForm.complianceIndustryCode,
  (code) => {
    if (!isMedicalComplianceIndustry.value) {
      specialIndustryCategoryOptions.value = []
      return
    }
    if (!offeringVisible.value || editingOffering.value) return
    offeringForm.medicalIndustryCode = code
    offeringForm.medicalCategoryCode = ''
    offeringForm.medicalCategoryName = ''
    loadSpecialIndustryCategoryOptions(code)
  },
)

onMounted(async () => {
  if (!hasValidId) {
    ElMessage.error('品牌参数无效')
    return
  }
  await dictStore.ensureLoaded()
  await load()
})
</script>

<style scoped>
.brand-detail-card :deep(.el-card__body) {
  padding: 20px;
}

.brand-detail-sections {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.brand-section-bar {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0 0 14px;
  color: #1e40af;
  font-size: 14px;
  font-weight: 850;
}

.brand-section-bar span {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #2563eb;
  box-shadow: 0 0 0 4px #dbeafe;
}

.brand-section-bar i {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, #bfdbfe, transparent);
}

.brand-info-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.brand-info-item {
  min-height: 72px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 7px;
  min-width: 0;
  padding: 13px 14px;
  border: 1px solid #e7edf5;
  border-radius: 12px;
  background: linear-gradient(135deg, #ffffff 0%, #fbfdff 66%, #f8fbff 100%);
  box-shadow: inset 3px 0 0 #dbeafe;
}

.brand-info-item.is-wide {
  grid-column: 1 / -1;
}

.brand-info-label {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.brand-info-value {
  min-width: 0;
  color: var(--admin-text-strong);
  font-size: 14px;
  line-height: 1.58;
  white-space: pre-wrap;
  word-break: break-word;
}

.brand-editor-dialog :deep(.el-dialog__body) {
  padding: 18px 22px 4px;
}

.brand-form {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.brand-form .brand-section-bar:not(:first-child) {
  margin-top: 26px;
}

.brand-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 22px;
  row-gap: 18px;
}

.brand-form-grid :deep(.el-form-item) {
  min-width: 0;
  margin-bottom: 0;
}

.brand-form-grid .is-wide {
  grid-column: 1 / -1;
}

.brand-form :deep(.el-form-item__label) {
  color: #334155;
  font-weight: 750;
  line-height: 1.35;
}

.brand-form :deep(.el-input__wrapper),
.brand-form :deep(.el-select__wrapper),
.brand-form :deep(.el-textarea__inner) {
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 0 0 1px #dbe3ee inset;
}

.brand-form :deep(.el-input__wrapper:hover),
.brand-form :deep(.el-select__wrapper:hover),
.brand-form :deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 1px #93c5fd inset;
}

.brand-field-help {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

.special-category-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.special-category-option span {
  min-width: 0;
  overflow: hidden;
  color: #0f172a;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.special-category-option small {
  flex: none;
  color: #94a3b8;
  font-size: 12px;
}

.form-item-hint {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

.account-requirement-alert {
  margin: -2px 0 18px 100px;
  width: calc(100% - 100px);
}

.table-subtext {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.35;
}

.table-subtext.is-error {
  color: #ef4444;
}

.table-error-text {
  color: #ef4444;
}

.subject-pool-wrap {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.subject-pool-config {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.subject-pool-actions,
.subject-pool-manual-add {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.subject-pool-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.subject-pool-summary > div {
  min-width: 0;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.subject-pool-summary span {
  display: block;
  margin-bottom: 6px;
  color: #64748b;
  font-size: 12px;
  font-weight: 750;
}

.subject-pool-summary strong {
  display: block;
  overflow-wrap: anywhere;
  color: #0f172a;
  font-size: 18px;
  font-weight: 850;
  line-height: 1.35;
}

.subject-pool-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.subject-pool-warning span {
  color: #92400e;
  line-height: 1.5;
}

.subject-pool-limit-note {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.subject-pool-excluded {
  margin-top: 2px;
}

.automation-readiness {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #f8fbff;
}

.automation-readiness__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.automation-readiness__title {
  color: #0f172a;
  font-size: 15px;
  font-weight: 850;
}

.automation-readiness__desc {
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

.automation-readiness__checks {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.automation-check {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
}

.automation-check.is-ok {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.automation-check span {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 750;
}

.automation-check strong {
  display: block;
  margin-top: 5px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 850;
}

.automation-check small {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 11px;
  line-height: 1.35;
}

.automation-readiness__issues {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.automation-issue {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #334155;
  font-size: 13px;
}

.automation-issue span {
  font-weight: 800;
}

.automation-issue em {
  min-width: 0;
  flex: 1;
  color: #64748b;
  font-style: normal;
}

.automation-issue .el-button {
  flex: none;
}

.adspower-import {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.adspower-import__toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.environment-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-top: 18px;
  margin-bottom: 14px;
  padding: 16px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #f8fbff;
}

.environment-toolbar h3 {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  font-weight: 850;
}

.environment-toolbar p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

.extension-bind-code-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding: 14px;
  border: 1px dashed #93c5fd;
  border-radius: 10px;
  background: #eff6ff;
}

.extension-bind-code-box div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}

.extension-bind-code-box span {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.extension-bind-code-box strong {
  color: #0f172a;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 20px;
  letter-spacing: 0;
}

.extension-bind-code-box small {
  color: #64748b;
  font-size: 12px;
}

.environment-key-cell {
  color: #475569;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.extension-session-inline {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  color: #334155;
  font-size: 13px;
}

.extension-session-inline small {
  flex-basis: 100%;
  color: #64748b;
  font-size: 12px;
  line-height: 1.35;
}

.extension-session-inline.is-empty span {
  color: #64748b;
}

.unmatched-extension-sessions {
  margin-top: 12px;
}

.unmatched-extension-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.unmatched-extension-list span {
  padding: 4px 8px;
  border-radius: 6px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 12px;
}

.environment-binding-summary {
  margin: 14px 0;
}

.environment-binding-select {
  width: 100%;
}

.wechat-menu-status {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
}

.wechat-menu-url {
  max-width: 100%;
  overflow: hidden;
  color: #475569;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wechat-menu-url.is-muted {
  color: #94a3b8;
}

@media (max-width: 960px) {
  .brand-info-grid,
  .brand-form-grid {
    grid-template-columns: 1fr;
  }

  .environment-toolbar,
  .extension-bind-code-box,
  .automation-readiness__head {
    align-items: stretch;
    flex-direction: column;
  }

  .automation-readiness__checks {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .subject-pool-summary {
    grid-template-columns: 1fr;
  }

  .adspower-import__toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
