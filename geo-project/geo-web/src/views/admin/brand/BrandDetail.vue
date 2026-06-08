<template>
  <div class="admin-page">
    <el-page-header content="品牌详情" @back="$router.back()" />

    <section v-if="brand" class="admin-object-hero">
      <div class="admin-object-hero-main">
        <div>
          <h1 class="admin-object-title">{{ brand.brandName }}</h1>
          <div class="admin-object-meta">
            {{ companyName || '-' }} · {{ industryLabel(brand.industry) }}
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
          <div class="brand-section-bar"><span />联系方式与阵地<i /></div>
          <div class="brand-info-grid">
            <div v-for="item in brandContactInfoItems" :key="item.label" class="brand-info-item">
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

    <el-card class="admin-table-card">
      <template #header>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span>指纹浏览器环境</span>
            <el-tag type="info">AdsPower</el-tag>
          </div>
          <el-button v-if="canUpdateBrand" type="primary" link @click="openBrowserEnvironmentPrimaryAction">
            {{ browserEnvironments.length ? '编辑环境' : '配置环境' }}
          </el-button>
        </div>
      </template>
      <el-alert
        class="mb-3"
        type="info"
        show-icon
        :closable="false"
        title="同一品牌默认使用一个 AdsPower 浏览器环境。新增头条、知乎、小红书账号时会自动绑定当前启用环境；AdsPower API Key 在「个人中心 > 本地助手」配置。"
      />
      <el-table v-loading="browserEnvironmentsLoading" :data="browserEnvironments" border empty-text="暂无指纹浏览器环境">
        <el-table-column prop="name" label="环境名称" min-width="160">
          <template #default="{ row }">{{ row.name || row.environmentKey }}</template>
        </el-table-column>
        <el-table-column prop="environmentKey" label="环境代号" min-width="150" />
        <el-table-column prop="providerProfileId" label="AdsPower 浏览器编号" min-width="190" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="canUpdateBrand" label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openBrowserEnvironmentEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeBrowserEnvironment(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="canUpdateBrand" class="extension-binding-panel">
        <div class="extension-binding-head">
          <div>
            <h3>环境扩展绑定</h3>
            <p>用于 AdsPower 环境内 GEO 扩展绑定当前品牌后台；出现异常时可吊销旧会话后重新绑定。</p>
          </div>
          <div class="flex items-center gap-2">
            <el-button :loading="extensionSessionsLoading" @click="loadExtensionSessions">刷新状态</el-button>
            <el-button type="primary" :loading="extensionBindCodeLoading" @click="generateBrandExtensionBindCode">
              生成绑定码
            </el-button>
          </div>
        </div>

        <div v-if="extensionBindCode" class="extension-bind-code-box">
          <div>
            <span>扩展绑定码</span>
            <strong>{{ extensionBindCode.code }}</strong>
            <small>{{ extensionBindCode.expiresInSeconds }} 秒内有效，请在 AdsPower 环境扩展弹窗中绑定后台。</small>
          </div>
          <el-button type="primary" plain @click="copyBrandExtensionBindCode">复制绑定码</el-button>
        </div>

        <el-table
          v-loading="extensionSessionsLoading"
          :data="extensionSessions"
          border
          empty-text="暂无已绑定扩展会话"
        >
          <el-table-column prop="id" label="Session ID" width="110" />
          <el-table-column prop="extensionVersion" label="版本" width="110">
            <template #default="{ row }">{{ row.extensionVersion || '-' }}</template>
          </el-table-column>
          <el-table-column prop="installId" label="安装标识" min-width="180">
            <template #default="{ row }">{{ row.installId || '-' }}</template>
          </el-table-column>
          <el-table-column label="最近活跃" min-width="170">
            <template #default="{ row }">{{ row.lastSeenAt || row.boundAt || '-' }}</template>
          </el-table-column>
          <el-table-column label="过期时间" min-width="170">
            <template #default="{ row }">{{ row.expiresAt || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" @click="revokeBrandExtension(row)">解绑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-card class="admin-table-card">
      <template #header>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span>自媒体账号</span>
            <el-tag type="info">头条 / 知乎 / 小红书</el-tag>
          </div>
          <div class="flex items-center gap-2">
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
            <el-button v-if="canUpdateBrand" type="primary" link @click="openSelfMediaAccountCreate">新增账号</el-button>
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
      <el-table v-loading="selfMediaAccountsLoading" :data="semiAutoSelfMediaAccounts" border>
        <el-table-column prop="platform" label="平台" width="110">
          <template #default="{ row }">{{ selfMediaPlatformLabel(row.platform) }}</template>
        </el-table-column>
        <el-table-column prop="accountName" label="账号名称" min-width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '启用' : '停用' }}
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
        <el-table-column label="最近上报" min-width="210">
          <template #default="{ row }">
            <div>{{ browserEnvironmentLastReportTime(row) }}</div>
            <div v-if="browserEnvironmentAccountOf(row)?.environmentKey" class="table-subtext">
              已绑定：{{ browserEnvironmentAccountOf(row)?.environmentKey }}
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="canUpdateBrand" label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSelfMediaAccountEdit(row)">编辑</el-button>
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

        <div class="brand-section-bar"><span />联系方式与阵地<i /></div>
        <div class="brand-form-grid">
          <el-form-item label="官网"><el-input v-model="brandForm.website" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model="brandForm.phone" /></el-form-item>
          <el-form-item label="对外公开电话"><el-input v-model="brandForm.publicPhone" /></el-form-item>
          <el-form-item label="微信"><el-input v-model="brandForm.wechat" /></el-form-item>
          <el-form-item class="is-wide" label="对外公开地址"><el-input v-model="brandForm.publicAddress" /></el-form-item>
          <el-form-item label="默认发布位置">
            <el-input v-model="brandForm.selfMediaPublishLocationName" maxlength="64" placeholder="用于头条等自媒体发布页添加位置" />
          </el-form-item>
          <el-form-item label="Agent 官网">
            <el-select
              v-model="brandForm.geoSiteCode"
              clearable
              filterable
              placeholder="选择 Agent 官网，自动带出站点标识"
              style="width: 100%"
              @change="handleAgentSiteChange"
            >
              <el-option
                v-for="site in agentSiteOptions"
                :key="site.siteCode || site.id"
                :label="site.siteName"
                :value="site.siteCode"
              />
            </el-select>
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
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitBrand">保存</el-button>
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
          <el-select v-model="selfMediaAccountForm.platform" style="width: 100%">
            <el-option label="头条" value="toutiao" />
            <el-option label="知乎" value="zhihu" />
            <el-option label="小红书" value="xiaohongshu" />
          </el-select>
        </el-form-item>
        <el-form-item label="账号名称" prop="accountName" required>
          <el-input v-model="selfMediaAccountForm.accountName" placeholder="运营可识别的账号名称" maxlength="128" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  deleteBrandTemplatePerspectiveConfig,
  getBrandTemplatePerspectiveConfigs,
  createSelfMediaAccount,
  saveBrandTemplatePerspectiveConfig,
  getSelfMediaAccountsByBrand,
  updateSelfMediaAccount,
  type BrandChannelTemplatePerspective,
  type TemplatePerspective,
} from '@/api/content'
import {
  getBrandDetail,
  updateBrand,
  deleteBrand,
  getCompanyDetail,
} from '@/api/customer'
import {
  createBrowserEnvironment,
  createBrowserEnvironmentAccount,
  deleteBrowserEnvironment,
  deleteBrowserEnvironmentAccount,
  getBrowserEnvironmentAccountBySelfMedia,
  listBrowserEnvironments,
  resetBrowserEnvironmentAccountLoginIdentity,
  updateBrowserEnvironment,
  type BrowserEnvironment,
  type BrowserEnvironmentAccount,
} from '@/api/browserEnvironment'
import {
  createExtensionBindCode,
  listBrandExtensionSessions,
  revokeBrandExtensionSession,
  type ExtensionBindCode,
  type ExtensionSession,
} from '@/api/extension'
import { getPublishSites } from '@/api/publishSite'
import type { Brand, PublishSite, SelfMediaAccount } from '@/types'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionCodesFromPayload, regionDisplayFromPayload, regionPayloadFromCodes } from '@/constants/region'
import { nullableText } from '@/utils/form'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const dictStore = useDictStore()

const brandId = Number(route.params.id)
const hasValidId = Number.isFinite(brandId) && brandId > 0

const canUpdateBrand = computed(() => userStore.hasPermission('brand.update'))
const canDeleteBrand = computed(() => userStore.hasPermission('brand.delete'))
const canCreateProject = computed(() => userStore.hasPermission('project.create'))

type SemiAutoPlatform = 'toutiao' | 'zhihu' | 'xiaohongshu'
type SemiAutoSelfMediaAccount = SelfMediaAccount & {
  platform: SemiAutoPlatform | string
  cookieCredentialStatus?: string | null
  cookieCredentialVersion?: number | null
  cookieCredentialCapturedAt?: string | null
}

const loading = ref(false)
const saving = ref(false)
const editVisible = ref(false)
const selfMediaAccountsLoading = ref(false)
const selfMediaAccountSaving = ref(false)
const selfMediaAccountVisible = ref(false)
const perspectiveConfigLoading = ref(false)
const perspectiveConfigSaving = ref(false)
const perspectiveConfigVisible = ref(false)
const brand = ref<Brand | null>(null)
const selfMediaAccounts = ref<SemiAutoSelfMediaAccount[]>([])
const browserEnvironments = ref<BrowserEnvironment[]>([])
const browserEnvironmentsLoading = ref(false)
const browserEnvironmentVisible = ref(false)
const browserEnvironmentSaving = ref(false)
const editingBrowserEnvironment = ref<BrowserEnvironment | null>(null)
const browserEnvironmentAccounts = ref<Record<number, BrowserEnvironmentAccount | null>>({})
const extensionSessions = ref<ExtensionSession[]>([])
const extensionSessionsLoading = ref(false)
const extensionBindCode = ref<ExtensionBindCode | null>(null)
const extensionBindCodeLoading = ref(false)
const environmentBindingVisible = ref(false)
const environmentBindingSaving = ref(false)
const environmentBindingTargetAccount = ref<SemiAutoSelfMediaAccount | null>(null)
const perspectiveConfigs = ref<BrandChannelTemplatePerspective[]>([])
const templatePerspectives = ref<TemplatePerspective[]>([])
const publishSites = ref<PublishSite[]>([])
const GEO_SITE_CODE_PATTERN = /^[a-z0-9](?:[a-z0-9_-]{0,62}[a-z0-9])?$/
const editingSelfMediaAccount = ref<SemiAutoSelfMediaAccount | null>(null)
const companyName = ref('')
const companyIndustryTags = ref<string[]>([])
const brandFormRef = ref<FormInstance>()
const selfMediaAccountFormRef = ref<FormInstance>()
const perspectiveConfigFormRef = ref<FormInstance>()

const brandForm = reactive({
  brandName: '',
  brandShortName: '',
  brandSlug: '',
  industry: '',
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
  geoSiteCode: '',
  geoSiteStatus: '',
  industrySiteName: '',
  industrySiteCode: '',
})

const selfMediaAccountForm = reactive({
  platform: 'toutiao' as SemiAutoPlatform,
  accountName: '',
  status: 'active' as 'active' | 'disabled',
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
}

const qualificationDescriptionPlaceholder = '请填写品牌可公开引用的资质与背书信息，包括认证资质、检测报告、执行标准、专利/软著、荣誉奖项、协会或平台背书、生产/服务能力证明等。请写清楚名称、编号、发证机构、适用范围、有效期等可核验信息。没有真实依据的内容不要填写。'
const caseDescriptionPlaceholder = '请填写可公开引用的品牌案例素材，包括客户类型或客户名称、项目背景、服务内容、项目规模、交付周期、合作结果、复购或长期合作情况等。如客户名称不可公开，请使用“某行业客户/某区域客户”表述，不要编造客户名或效果数据。'

const selfMediaAccountRules: FormRules = {
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
  accountName: [{ required: true, message: '请输入账号名称', trigger: 'blur' }],
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
  selfMediaAccounts.value.filter((item) => item.platform === 'toutiao' || item.platform === 'zhihu' || item.platform === 'xiaohongshu'),
)

const activeBrowserEnvironments = computed(() => browserEnvironments.value.filter((item) => item.status === 'active'))
const defaultBrowserEnvironment = computed(() => activeBrowserEnvironments.value[0] || null)
const hasUnboundSemiAutoAccounts = computed(() =>
  semiAutoSelfMediaAccounts.value.some((account) => !browserEnvironmentAccountOf(account)),
)

const enabledPerspectives = computed(() => templatePerspectives.value.filter((item) => item.enabled))

const regionText = computed(() => {
  if (!brand.value) return '-'
  return regionDisplayFromPayload(brand.value) || brand.value.serviceArea || '-'
})

const availableBrandIndustries = computed(() => companyIndustryTags.value)
const agentSiteOptions = computed(() => publishSites.value.filter((site) =>
  isValidGeoSiteCode(site.siteCode)
  && (site.integrationMethod === 'brand_geo_site' || site.siteCode === 'agent_official_site'),
))
const industrySiteOptions = computed(() => publishSites.value.filter((site) =>
  site.integrationMethod !== 'brand_geo_site'
  && site.integrationMethod !== 'forum_playwright'
  && site.integrationMethod !== 'discuz_http'
  && site.siteCode !== 'agent_official_site',
))
const brandCoreInfoItems = computed(() => [
  { label: '品牌名称', value: brand.value?.brandName || '-' },
  { label: '品牌简称', value: brand.value?.brandShortName || '-' },
  { label: '状态', value: dictStore.label('brand_status', brand.value?.status) || '-' },
  { label: '所属客户', value: companyName.value || '-' },
  { label: '品牌行业', value: industryLabel(brand.value?.industry) },
  { label: '主营业务', value: brand.value?.mainBusiness || '-' },
  { label: '核心产品', value: brand.value?.coreProducts || '-' },
  { label: '品牌定位', value: brand.value?.brandPositioning || '-' },
  { label: '所在地区', value: regionText.value },
])

const brandContactInfoItems = computed(() => [
  { label: '官网', value: brand.value?.website || '-' },
  { label: '联系电话', value: brand.value?.phone || '-' },
  { label: '对外公开电话', value: brand.value?.publicPhone || '-' },
  { label: '对外公开地址', value: brand.value?.publicAddress || '-' },
  { label: '自媒体默认发布位置', value: brand.value?.selfMediaPublishLocationName || '-' },
  { label: '微信', value: brand.value?.wechat || '-' },
  { label: 'Agent 官网', value: agentSiteLabel(brand.value?.geoSiteCode) },
  { label: '行业资讯站', value: brand.value?.industrySiteName || '-' },
])

const brandTextInfoItems = computed(() => [
  { label: '业务介绍', value: brand.value?.businessIntro || '-' },
  { label: '品牌资质描述', value: brand.value?.brandQualificationDescription || '-' },
  { label: '品牌案例描述', value: brand.value?.brandCaseDescription || '-' },
  { label: '禁用词', value: brand.value?.forbiddenPhrases || '-' },
])

function industryLabel(value?: string | null) {
  if (!value) return '-'
  return dictStore.label('industry_tag', value) || value
}

function selfMediaPlatformLabel(value?: string | null) {
  if (value === 'toutiao') return '头条'
  if (value === 'zhihu') return '知乎'
  if (value === 'xiaohongshu') return '小红书'
  return value || '-'
}

function browserEnvironmentAccountOf(account: SemiAutoSelfMediaAccount) {
  return browserEnvironmentAccounts.value[account.id] || null
}

function browserEnvironmentLoginStatusLabel(account: SemiAutoSelfMediaAccount) {
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

function browserEnvironmentLoginStatusTagType(account: SemiAutoSelfMediaAccount) {
  const status = browserEnvironmentAccountOf(account)?.loginStatus
  if (status === 'logged_in') return 'success'
  if (status === 'mismatch' || status === 'expired' || status === 'error') return 'danger'
  if (status === 'login_required' || status === 'unknown') return 'warning'
  return 'info'
}

function browserEnvironmentLastReportTime(account: SemiAutoSelfMediaAccount) {
  const binding = browserEnvironmentAccountOf(account)
  return binding?.lastVerifiedAt || binding?.lastLoginSeenAt || '-'
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
  const targets = semiAutoSelfMediaAccounts.value.filter((account) => !browserEnvironmentAccountOf(account))
  if (!targets.length) {
    ElMessage.success('所有头条/知乎/小红书账号均已绑定浏览器环境')
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
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err instanceof Error ? err.message : '解除环境绑定失败')
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

function agentSiteLabel(code?: string | null) {
  if (!code) return '-'
  return agentSiteOptions.value.find((item) => item.siteCode === code)?.siteName || code
}

function normalizeGeoSiteCode(code?: string | null) {
  const normalized = code?.trim().toLowerCase() || ''
  return GEO_SITE_CODE_PATTERN.test(normalized) ? normalized : ''
}

function isValidGeoSiteCode(code?: string | null) {
  return !!normalizeGeoSiteCode(code)
}

function handleAgentSiteChange(value: string) {
  brandForm.geoSiteCode = normalizeGeoSiteCode(value)
  const site = agentSiteOptions.value.find((item) => item.siteCode === brandForm.geoSiteCode)
  brandForm.geoSiteStatus = site ? 'active' : ''
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
  brandForm.geoSiteCode = data.geoSiteCode || ''
  brandForm.geoSiteStatus = data.geoSiteStatus || ''
  brandForm.industrySiteName = data.industrySiteName || ''
  brandForm.industrySiteCode = data.industrySiteCode || ''
}

async function loadPublishSiteOptions() {
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
    await Promise.all([loadBrowserEnvironments(), loadSelfMediaAccounts(), loadPerspectiveConfigs(), loadExtensionSessions()])
  } catch {
    brand.value = null
    companyName.value = ''
    selfMediaAccounts.value = []
    browserEnvironments.value = []
    browserEnvironmentAccounts.value = {}
    extensionSessions.value = []
  } finally {
    loading.value = false
  }
}

async function loadBrowserEnvironments() {
  browserEnvironmentsLoading.value = true
  try {
    const { data } = await listBrowserEnvironments(brandId)
    browserEnvironments.value = data.data || []
  } finally {
    browserEnvironmentsLoading.value = false
  }
}

async function loadExtensionSessions() {
  if (!hasValidId || !canUpdateBrand.value) return
  extensionSessionsLoading.value = true
  try {
    const { data } = await listBrandExtensionSessions(brandId)
    extensionSessions.value = data.data || []
  } catch (error) {
    extensionSessions.value = []
    ElMessage.error(error instanceof Error ? error.message : '加载扩展绑定状态失败')
  } finally {
    extensionSessionsLoading.value = false
  }
}

async function generateBrandExtensionBindCode() {
  if (!brand.value) return
  extensionBindCodeLoading.value = true
  try {
    const { data } = await createExtensionBindCode(brandId)
    extensionBindCode.value = data.data
    ElMessage.success('扩展绑定码已生成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生成扩展绑定码失败')
  } finally {
    extensionBindCodeLoading.value = false
  }
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
    await loadBrowserEnvironmentAccounts(accounts)
  } finally {
    selfMediaAccountsLoading.value = false
  }
}

async function loadBrowserEnvironmentAccounts(accounts: SemiAutoSelfMediaAccount[]) {
  const targets = accounts.filter((item) => isSemiAutoPlatform(item.platform))
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

function openSelfMediaAccountCreate() {
  editingSelfMediaAccount.value = null
  selfMediaAccountForm.platform = 'toutiao'
  selfMediaAccountForm.accountName = ''
  selfMediaAccountForm.status = 'active'
  selfMediaAccountVisible.value = true
}

function openSelfMediaAccountEdit(account: SemiAutoSelfMediaAccount) {
  editingSelfMediaAccount.value = account
  selfMediaAccountForm.platform = isSemiAutoPlatform(account.platform) ? account.platform : 'toutiao'
  selfMediaAccountForm.accountName = account.accountName || ''
  selfMediaAccountForm.status = account.status === 'disabled' ? 'disabled' : 'active'
  selfMediaAccountVisible.value = true
}

function isSemiAutoPlatform(platform?: string | null): platform is SemiAutoPlatform {
  return platform === 'toutiao' || platform === 'zhihu' || platform === 'xiaohongshu'
}

async function submitSelfMediaAccount() {
  const valid = await selfMediaAccountFormRef.value?.validate().catch(() => false)
  if (!valid) return
  selfMediaAccountSaving.value = true
  try {
    const payload = {
      platform: selfMediaAccountForm.platform,
      accountName: selfMediaAccountForm.accountName.trim(),
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
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  }
}

function openEdit() {
  if (!brand.value) return
  fillForm(brand.value)
  editVisible.value = true
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
      geoSiteCode: normalizeGeoSiteCode(brandForm.geoSiteCode) || null,
      geoSiteStatus: normalizeGeoSiteCode(brandForm.geoSiteCode) ? brandForm.geoSiteStatus || 'active' : null,
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

.form-item-hint {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

.table-subtext {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.35;
}

.extension-binding-panel {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #f8fbff;
}

.extension-binding-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.extension-binding-head h3 {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  font-weight: 850;
}

.extension-binding-head p {
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

.environment-binding-summary {
  margin: 14px 0;
}

.environment-binding-select {
  width: 100%;
}

@media (max-width: 960px) {
  .brand-info-grid,
  .brand-form-grid {
    grid-template-columns: 1fr;
  }

  .extension-binding-head,
  .extension-bind-code-box {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
