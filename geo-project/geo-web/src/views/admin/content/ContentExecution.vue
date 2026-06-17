<template>
  <div class="content-execution-page admin-page">
    <div class="admin-page-header">
      <div>
        <div class="admin-page-kicker">内容运营</div>
        <h1 class="admin-page-title">内容与执行</h1>
        <div class="admin-page-subtitle">让内容从生成到分发汇于一处，质量与节奏一目了然。</div>
      </div>
    </div>

    <el-card shadow="never" class="mb-3 admin-surface content-toolbar-card">
      <div class="toolbar">
        <div class="toolbar-filter-row">
          <div class="toolbar-filter-line is-primary">
            <el-input
              v-model="query.projectName"
              class="toolbar-project-input"
              clearable
              placeholder="搜索项目名称"
              :prefix-icon="Search"
              @keyup.enter="search"
            />
            <el-select v-model="query.articleType" class="toolbar-filter-control" clearable placeholder="全部">
              <template #prefix><span class="toolbar-select-prefix">类型</span></template>
              <el-option label="FAQ" value="faq" />
              <el-option label="场景内容" value="scenario_content" />
              <el-option label="行业文章" value="industry_article" />
              <el-option label="阶段建议" value="stage_advice" />
            </el-select>
            <el-select v-model="query.status" class="toolbar-filter-control" clearable placeholder="全部">
              <template #prefix><span class="toolbar-select-prefix">状态</span></template>
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <span class="toolbar-filter-divider" />
            <div class="toolbar-filter-actions">
              <el-button class="toolbar-search-action" type="primary" :icon="Search" @click="search">查询</el-button>
              <el-button class="toolbar-reset-action" :icon="Refresh" @click="resetQuery">重置</el-button>
              <el-button class="toolbar-toggle-action" text @click="showAdvancedFilters = !showAdvancedFilters">
                {{ showAdvancedFilters ? '收起' : '更多筛选' }}
                <el-icon class="toolbar-toggle-icon">
                  <ArrowUp v-if="showAdvancedFilters" />
                  <ArrowDown v-else />
                </el-icon>
              </el-button>
            </div>
          </div>
          <div v-show="showAdvancedFilters" class="toolbar-filter-line is-secondary">
            <el-select v-model="query.channelKey" class="toolbar-filter-control is-wide" clearable filterable placeholder="全部">
              <template #prefix><span class="toolbar-select-prefix">渠道</span></template>
              <el-option v-for="item in channelFilterOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="query.articleTypeCode" class="toolbar-filter-control is-wide" clearable filterable placeholder="全部">
              <template #prefix><span class="toolbar-select-prefix">形态</span></template>
              <el-option v-for="item in contentShapeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="query.generationMode" class="toolbar-filter-control" clearable placeholder="全部">
              <template #prefix><span class="toolbar-select-prefix">方式</span></template>
              <el-option label="批量生成" value="batch" />
              <el-option label="单篇生成" value="single" />
            </el-select>
            <el-date-picker
              v-model="query.createdRange"
              class="toolbar-date-range"
              type="daterange"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DD"
              :prefix-icon="Calendar"
              unlink-panels
              clearable
            />
          </div>
        </div>
        <div v-if="hasToolbarActions" class="toolbar-action-row">
          <div class="toolbar-primary-actions">
            <el-button v-if="canAiGenerate" type="primary" @click="openBatchGeneration">批量生成文章</el-button>
            <el-button v-if="canArticleWrite" @click="goManualCreate">单篇生成文章</el-button>
          </div>
          <div class="toolbar-secondary-actions">
            <span v-if="selectedRows.length" class="toolbar-selection-hint">已选 {{ selectedRows.length }} 篇</span>
            <el-button v-if="canPublish" :disabled="!selectedRows.length || batchPublishChecking" :loading="batchPublishChecking" @click="openBatchPublish">
              批量发布{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}
            </el-button>
            <el-dropdown v-if="hasToolbarMoreActions" trigger="click" @command="handleToolbarMoreCommand">
              <el-button class="toolbar-more-action">更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="jobs">批量任务列表</el-dropdown-item>
                  <el-dropdown-item v-if="canViewSelfMediaSchedules" command="schedules">发布排期</el-dropdown-item>
                  <el-dropdown-item v-if="canManagePromptTemplates" command="templates">文章提示词模板</el-dropdown-item>
                  <el-dropdown-item v-if="canManagePromptTemplates" command="special-compliance">行业专项</el-dropdown-item>
                  <el-dropdown-item v-if="canManagePublishPlatforms" command="platforms">发布平台管理</el-dropdown-item>
                  <el-dropdown-item v-if="canManagePublishPlatforms" command="schedule-capabilities">排期能力管理</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </el-card>

    <div class="admin-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">文章总数</span>
        <strong class="admin-metric-value">{{ page.total }}</strong>
        <span class="admin-metric-hint">当前筛选结果</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">已发布</span>
        <strong class="admin-metric-value">{{ publishedCount }}</strong>
        <span class="admin-metric-hint">本页可见</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">可分发</span>
        <strong class="admin-metric-value">{{ distributableCount }}</strong>
        <span class="admin-metric-hint">本页可见</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #ef4444; --metric-tone: #fef2f2">
        <span class="admin-metric-label">需处理</span>
        <strong class="admin-metric-value">{{ blockedCount }}</strong>
        <span class="admin-metric-hint">驳回/失败/风险</span>
      </div>
    </div>

    <el-card shadow="never" class="admin-table-card">
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无文章数据">
        <el-table class="content-list-table" :data="rows" border table-layout="fixed" @selection-change="onSelectionChange">
          <el-table-column type="selection" width="48" :selectable="canSelectForBatchPublish" />
          <el-table-column label="内容对象" min-width="280" show-overflow-tooltip>
            <template #default="scope">
              <div class="admin-entity-cell">
                <div class="admin-entity-avatar content-avatar" :class="articleTypeClass(scope.row.articleTypeCode)">
                  {{ articleTypeInitial(scope.row.articleTypeCode) }}
                </div>
                <div class="min-w-0">
                  <div class="admin-entity-main">{{ scope.row.projectName || `#${scope.row.projectId}` }}</div>
                  <div class="admin-entity-sub">
                    {{ articleTypeLabel(scope.row.articleTypeCode) }} · {{ articleChannelLabel(scope.row) }} · {{ generationModeLabel(scope.row) }}
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="标题" min-width="360" show-overflow-tooltip>
            <template #default="scope">
              <div class="admin-cell-stack">
                <span class="admin-cell-main">{{ scope.row.title || '-' }}</span>
                <span class="admin-cell-sub">{{ templateUsageLabel(scope.row) }}</span>
                <span v-if="riskWordHits(scope.row).length" class="risk-word-line">
                  <el-tag
                    v-for="hit in riskWordHits(scope.row)"
                    :key="`${hit.severity}-${hit.source}-${hit.word}`"
                    size="small"
                    :type="hit.severity === 'block' ? 'danger' : 'warning'"
                    effect="light"
                  >
                    {{ riskSeverityLabel(hit.severity) }}: {{ hit.word }}
                  </el-tag>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="scope">
              <span class="content-status-text" :class="contentStatusClass(scope.row.status)">
                <span class="content-status-dot" />
                {{ statusLabel(scope.row.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="医疗合规" width="150">
            <template #default="scope">
              <div v-if="isMedicalArticle(scope.row)" class="medical-status-cell">
                <el-tag size="small" :type="medicalComplianceTag(scope.row.complianceStatus)">
                  {{ medicalComplianceLabel(scope.row.complianceStatus) }}
                </el-tag>
                <el-tag v-if="scope.row.medicalChannelTier === 'official_site'" size="small" :type="medicalReviewTag(scope.row.publishReviewStatus)">
                  {{ medicalReviewLabel(scope.row.publishReviewStatus) }}
                </el-tag>
              </div>
              <span v-else class="admin-cell-sub">-</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="360" fixed="right">
            <template #default="scope">
              <div class="admin-row-actions">
                <el-button link type="primary" @click="openDetail(scope.row.id)">详情</el-button>
                <el-button v-if="canArticleWrite && canEdit(scope.row.status)" class="content-neutral-action" link @click="openRevision(scope.row)">修订</el-button>
                <el-button v-if="canReviewMedicalPublish(scope.row)" link type="warning" @click="reviewMedicalPublish(scope.row, 'approve')">法务通过</el-button>
                <el-button v-if="canReviewMedicalPublish(scope.row)" link type="danger" @click="reviewMedicalPublish(scope.row, 'reject')">驳回</el-button>
                <el-button v-if="canDistributeOperate && canDistribute(scope.row.status)" link type="success" @click="openDistributionChannel(scope.row)">分发</el-button>
                <el-button v-if="canArticleWrite && canDeleteArticle(scope.row.status)" link type="danger" @click="deleteArticle(scope.row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="admin-table-footer">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="page.current"
            :page-size="page.size"
            :total="page.total"
            @current-change="onPageChange"
          />
        </div>
      </DataState>
    </el-card>

    <ArticleDetailDrawer
      v-model="detailVisible"
      v-model:view-mode="detailViewMode"
      :detail-data="detailData"
      :cover-image-url="detailCoverImageUrl"
      :markdown="detailMarkdown"
      :html="detailHtml"
      :can-article-write="canArticleWrite"
      :can-edit-from-detail="canEditFromDetail"
      :can-style-render="canStyleRender"
      :status-tag-type="statusTagType"
      :status-label="statusLabel"
      :article-type-label="articleTypeLabel"
      :detail-channel-label="detailChannelLabel"
      :detail-template-usage-label="detailTemplateUsageLabel"
      :detail-topic="detailTopic"
      :risk-word-hits="riskWordHits"
      :risk-severity-label="riskSeverityLabel"
      :risk-source-label="riskSourceLabel"
      :generated-by-label="generatedByLabel"
      :medical-compliance-label="medicalComplianceLabel"
      :medical-compliance-tag="medicalComplianceTag"
      :medical-review-label="medicalReviewLabel"
      :medical-review-tag="medicalReviewTag"
      :can-review-medical-publish="canReviewMedicalPublish"
      @revision="openRevisionFromDetail"
      @style-render="handleDetailStyleRenderCommand"
      @medical-publish-review="handleDetailMedicalPublishReview"
    />

    <ArticleRevisionDialog
      v-model="revisionVisible"
      v-model:view-mode="revisionViewMode"
      :form="revisionForm"
      :html="revisionHtml"
      :submitting="submitting"
      @submit="submitRevision"
    />

    <el-dialog v-model="distributionChannelVisible" title="选择分发渠道" width="760px" class="distribution-channel-dialog">
      <div class="distribution-channel-intro">
        选择本篇文章的发布去向，后续会进入对应平台配置。
      </div>
      <div class="distribution-channel-grid">
        <button
          v-for="channel in distributionChannels"
          :key="channel.value"
          type="button"
          class="distribution-channel-card"
          :class="{ disabled: channel.disabled }"
          @click="selectDistributionChannel(channel.value)"
        >
          <span class="distribution-channel-mark" :class="distributionChannelClass(channel.value)">
            {{ distributionChannelInitial(channel.value) }}
          </span>
          <span class="distribution-channel-status" :class="{ disabled: channel.disabled }">
            <span class="distribution-channel-status-dot" />
            {{ channel.disabled ? '待接入' : '可分发' }}
          </span>
          <span class="distribution-channel-title">{{ channel.label }}</span>
          <span class="distribution-channel-desc">{{ channel.description }}</span>
          <span class="distribution-channel-action">选择</span>
        </button>
      </div>
      <template #footer>
        <el-button @click="distributionChannelVisible = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="industrySiteVisible" title="行业资讯站分发" width="900px" class="industry-site-dialog">
      <div class="industry-site-intro">
        选择一个已启用的行业资讯站，系统会将当前文章发布到对应站点。
      </div>
      <DataState :loading="industrySiteLoading" :empty="!industrySiteLoading && industrySites.length === 0" empty-text="暂无可用行业资讯站">
        <div class="industry-site-list">
          <button
            v-for="site in industrySites"
            :key="site.id"
            type="button"
            class="industry-site-card"
            :class="{ selected: selectedIndustrySiteId === site.id }"
            @click="selectIndustrySite(site)"
          >
            <span class="industry-site-radio" :class="{ selected: selectedIndustrySiteId === site.id }" />
            <span class="industry-site-content">
              <span class="industry-site-head">
                <span class="site-cell">
                  <el-avatar v-if="site.iconUrl" :src="site.iconUrl" shape="square" :size="34" />
                  <span v-else class="industry-site-avatar">{{ industrySiteInitial(site) }}</span>
                  <span>
                    <span class="site-name">{{ site.siteName }}</span>
                    <span class="site-domain">{{ site.domain || '-' }}</span>
                  </span>
                </span>
                <span v-if="selectedIndustrySiteId === site.id" class="industry-site-selected">已选择</span>
              </span>
              <span class="industry-site-meta">
                <span>
                  <span class="industry-site-meta-label">行业分类</span>
                  {{ industrySiteTagText(site) }}
                </span>
                <span>
                  <span class="industry-site-meta-label">接入方式</span>
                  {{ distributionPlatformLabel(site.integrationMethod) }}
                </span>
              </span>
            </span>
          </button>
        </div>
      </DataState>
      <template #footer>
        <el-button @click="industrySiteVisible = false">取消</el-button>
        <el-button type="primary" :loading="industrySiteSubmitting" :disabled="!selectedIndustrySiteId" @click="submitIndustrySite">
          确认分发
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="forumSiteVisible" title="平台网站分发" width="900px" class="industry-site-dialog">
      <div class="industry-site-intro">
        选择一个已启用的平台网站，系统会将当前文章作为平台网站内容发布到对应站点。
      </div>
      <DataState :loading="forumSiteLoading" :empty="!forumSiteLoading && forumSites.length === 0" empty-text="暂无可用平台网站">
        <div class="industry-site-list">
          <button
            v-for="site in forumSites"
            :key="site.id"
            type="button"
            class="industry-site-card"
            :class="{ selected: selectedForumSiteId === site.id }"
            @click="selectForumSite(site)"
          >
            <span class="industry-site-radio" :class="{ selected: selectedForumSiteId === site.id }" />
            <span class="industry-site-content">
              <span class="industry-site-head">
                <span class="site-cell">
                  <el-avatar v-if="site.iconUrl" :src="site.iconUrl" shape="square" :size="34" />
                  <span v-else class="industry-site-avatar">{{ industrySiteInitial(site) }}</span>
                  <span>
                    <span class="site-name">{{ site.siteName }}</span>
                    <span class="site-domain">{{ site.domain || '-' }}</span>
                  </span>
                </span>
                <span v-if="selectedForumSiteId === site.id" class="industry-site-selected">已选择</span>
              </span>
              <span class="industry-site-meta">
                <span>
                  <span class="industry-site-meta-label">平台网站分类</span>
                  {{ industrySiteTagText(site) }}
                </span>
                <span>
                  <span class="industry-site-meta-label">接入方式</span>
                  {{ distributionPlatformLabel(site.integrationMethod) }}
                </span>
              </span>
            </span>
          </button>
        </div>
      </DataState>
      <div v-if="selectedForumSiteId && selectedForumBoards.length > 1" class="forum-board-picker">
        <div class="industry-site-meta-label">发布版块</div>
        <el-select v-model="selectedForumFid" placeholder="自动匹配发布版块" style="width: 100%">
          <el-option label="自动匹配（服务区域 / 行业 / 默认版块）" :value="0" />
          <el-option
            v-for="board in selectedForumBoards"
            :key="board.fid"
            :label="board.name"
            :value="board.fid"
          />
        </el-select>
      </div>
      <template #footer>
        <el-button @click="forumSiteVisible = false">取消</el-button>
        <el-button type="primary" :loading="forumSiteSubmitting" :disabled="!canSubmitForumSite" @click="submitForumSite">
          确认分发
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="authorityMediaVisible" title="权威媒体分发" width="1080px" class="authority-media-modal">
      <div class="authority-media-dialog">
        <div class="authority-media-notice">
          <span class="authority-media-notice-icon">i</span>
          <span>选择权威媒体资源并提交订单，出稿状态将由系统自动同步。</span>
        </div>
        <div class="authority-filter">
          <el-input v-model="authorityQuery.keyword" clearable placeholder="媒体名称" @keyup.enter="searchAuthorityMedia" />
          <el-input v-model="authorityQuery.industry" clearable placeholder="频道/行业" @keyup.enter="searchAuthorityMedia" />
          <el-input v-model="authorityQuery.province" clearable placeholder="地区" @keyup.enter="searchAuthorityMedia" />
          <el-select v-model="authorityQuery.entranceLevel" clearable placeholder="入口级别">
            <el-option label="无入口" :value="0" />
            <el-option label="首页入口" :value="1" />
            <el-option label="频道入口" :value="2" />
            <el-option label="上级入口" :value="3" />
          </el-select>
          <el-select v-model="authorityQuery.newsResource" clearable placeholder="新闻源">
            <el-option label="非新闻源" :value="0" />
            <el-option label="百度新闻源" :value="1" />
            <el-option label="头条新闻源" :value="2" />
            <el-option label="百度&头条" :value="3" />
          </el-select>
          <el-select v-model="authorityQuery.includeCondition" clearable placeholder="收录">
            <el-option label="不包收录" :value="0" />
            <el-option label="百度包收录" :value="1" />
            <el-option label="头条包收录" :value="2" />
          </el-select>
          <div class="authority-filter-actions">
            <el-button type="primary" @click="searchAuthorityMedia">查询</el-button>
            <el-button @click="resetAuthorityMediaQuery">重置</el-button>
          </div>
        </div>
        <div class="authority-table-wrap">
          <el-table
            :data="authorityResources"
            border
            max-height="360"
            v-loading="authorityLoading"
            highlight-current-row
            :row-class-name="authorityRowClass"
            @current-change="selectAuthorityResource"
          >
            <el-table-column width="52">
              <template #default="scope">
                <el-radio :model-value="authorityForm.resourceId" :label="scope.row.id" @change="selectAuthorityResource(scope.row)" />
              </template>
            </el-table-column>
            <el-table-column label="媒体资源" min-width="230" show-overflow-tooltip>
              <template #default="scope">
                <div class="authority-media-cell">
                  <span class="authority-media-avatar">{{ authorityMediaInitial(scope.row) }}</span>
                  <span class="authority-media-main">
                    <span class="authority-media-name">{{ scope.row.name }}</span>
                    <span class="authority-media-sub">{{ scope.row.industry || '未分类' }} · {{ scope.row.province || '全国' }}</span>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="价格" width="110">
              <template #default="scope">
                <span class="authority-price">￥{{ money(scope.row.price) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="入口" width="110">
              <template #default="scope">
                <span class="authority-soft-chip">{{ entranceLevelLabel(scope.row.entranceLevel) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="新闻源" width="125">
              <template #default="scope">
                <span class="authority-soft-chip">{{ newsResourceLabel(scope.row.newsResource) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="收录" width="125">
              <template #default="scope">
                <span class="authority-soft-chip">{{ includeConditionLabel(scope.row.includeCondition) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="权重" width="115">
              <template #default="scope">
                <span class="authority-weight">{{ authorityWeightText(scope.row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="链接" width="120">
              <template #default="scope">
                <div class="authority-link-actions">
                  <el-button v-if="scope.row.caseLink" link type="primary" @click.stop="openExternalLink(scope.row.caseLink)">案例</el-button>
                  <el-button v-if="scope.row.entranceLink" link type="primary" @click.stop="openExternalLink(scope.row.entranceLink)">入口</el-button>
                  <span v-if="!scope.row.caseLink && !scope.row.entranceLink" class="authority-empty-link">-</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          </el-table>
        </div>
        <div class="pager compact">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="authorityPage.current"
            :page-size="authorityPage.size"
            :total="authorityPage.total"
            @current-change="onAuthorityPageChange"
          />
        </div>
        <div v-if="selectedAuthorityResource" class="authority-confirm">
          <div class="authority-confirm-head">
            <div class="authority-selected">
              <strong>{{ selectedAuthorityResource.name }}</strong>
              <span>底价 ￥{{ money(selectedAuthorityResource.price) }}</span>
              <span>{{ entranceLevelLabel(selectedAuthorityResource.entranceLevel) }}</span>
              <span>{{ newsResourceLabel(selectedAuthorityResource.newsResource) }}</span>
            </div>
          </div>
          <el-form :model="authorityForm" label-width="100px" class="authority-form">
            <el-form-item label="订单售价" required>
              <el-input-number v-model="authorityForm.salingPrice" :min="Number(selectedAuthorityResource.price || 0)" :precision="2" :controls="false" style="width: 180px" />
            </el-form-item>
            <el-form-item label="限时发布">
              <el-date-picker
                v-model="authorityForm.publishedAt"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="可选，需晚于当前1小时"
                style="width: 220px"
              />
            </el-form-item>
            <el-form-item label="订单备注">
              <el-input v-model="authorityForm.remark" type="textarea" :rows="2" maxlength="100" show-word-limit />
            </el-form-item>
          </el-form>
        </div>
      </div>
      <template #footer>
        <div class="authority-footer">
          <div class="authority-footer-summary">
            <template v-if="selectedAuthorityResource">
              <span class="authority-footer-label">已选择</span>
              <strong>{{ selectedAuthorityResource.name }}</strong>
              <span>￥{{ money(authorityForm.salingPrice) }}</span>
              <span>{{ entranceLevelLabel(selectedAuthorityResource.entranceLevel) }}</span>
            </template>
            <span v-else>请选择一个媒体资源后提交订单。</span>
          </div>
          <div class="authority-footer-actions">
            <el-button @click="authorityMediaVisible = false">取消</el-button>
            <el-button type="primary" :loading="authoritySubmitting" :disabled="!authorityForm.resourceId" @click="submitAuthorityMedia">
              提交权威媒体订单
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <SelfMediaDistributeDialog
      v-model="mediaDistributeVisible"
      v-model:image-folder-scope="imageFolderScope"
      v-model:selected-cover-material-id="selectedCoverMaterialId"
      v-model:douyin-text="douyinText"
      :selected-media-platform="selectedMediaPlatform"
      :local-helper-health="lastLocalHelperHealth"
      :wechat-capability="wechatCapability"
      :wechat-distribution-available="wechatDistributionAvailable"
      :wechat-quick-schedule-available="wechatQuickScheduleAvailable"
      :wechat-status-tag-type="wechatStatusTagType"
      :wechat-status-label="wechatStatusLabel"
      :douyin-capability="douyinCapability"
      :douyin-distribution-available="douyinDistributionAvailable"
      :douyin-status-tag-type="douyinStatusTagType"
      :douyin-status-label="douyinStatusLabel"
      :toutiao-accounts="toutiaoAccounts"
      :baijiahao-accounts="baijiahaoAccounts"
      :zhihu-accounts="zhihuAccounts"
      :xiaohongshu-accounts="xiaohongshuAccounts"
      :current-platform-accounts="currentPlatformAccounts"
      :selected-self-media-quota-hint="selectedSelfMediaQuotaHint"
      :selected-self-media-account-id="selectedSelfMediaAccountId"
      :checking-self-media-account-id="checkingSelfMediaAccountId"
      :media-distribute-brand-id="mediaDistributeBrandId"
      :semi-auto-login-opening-account-id="semiAutoLoginOpeningAccountId"
      :environment-account-resetting-id="environmentAccountResettingId"
      :image-materials="imageMaterials"
      :display-image-folders="displayImageFolders"
      :selected-image-folder-id="selectedImageFolderId"
      :douyin-image-materials="douyinImageMaterials"
      :selected-douyin-image-material-ids="selectedDouyinImageMaterialIds"
      :selected-douyin-materials="selectedDouyinMaterials"
      :distribution-attempts="distributionAttempts"
      :refreshing-review-task-id="refreshingReviewTaskId"
      :semi-auto-confirming-task-id="semiAutoConfirmingTaskId"
      :semi-auto-abandoning-task-id="semiAutoAbandoningTaskId"
      :self-media-submitting="selfMediaSubmitting"
      :douyin-submit-button-text="douyinSubmitButtonText"
      :actions="selfMediaDistributeActions"
      @brand-config="openBrandConfig"
    />

    <SelfMediaScheduleDrawer
      v-model="scheduleDrawerVisible"
      :can-publish="canPublish"
      :initial-failure-code="scheduleInitialFailureCode"
      :initial-status="scheduleInitialStatus"
      @open-article="openDetail"
    />

  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowUp, Calendar, Refresh, Search } from '@element-plus/icons-vue'
import DataState from '@/components/ui/DataState.vue'
import ArticleDetailDrawer from './components/ArticleDetailDrawer.vue'
import ArticleRevisionDialog from './components/ArticleRevisionDialog.vue'
import SelfMediaDistributeDialog from './components/SelfMediaDistributeDialog.vue'
import SelfMediaScheduleDrawer from './components/SelfMediaScheduleDrawer.vue'
import { useArticleDetailRevision } from './composables/useArticleDetailRevision'
import { useArticleDistributionChannels } from './composables/useArticleDistributionChannels'
import { useSelfMediaDistribution } from './composables/useSelfMediaDistribution'
import { useUserStore } from '@/stores/user'
import type { ArticleDetailResponse, ArticleDraft } from '@/types'
import {
  deleteContentArticle,
  getContentArticleDetail,
  getContentArticles,
  reviewMedicalPublishArticle,
} from '@/api/content'
import { getProjectDetail } from '@/api/project'
import { formatDateTime } from '@/utils/format'
interface BatchPublishBlockedItem {
  title: string
  styleLabel: string
  reason: string
}
interface RiskWordHit {
  word: string
  severity: string
  source: string
}

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const canArticleWrite = computed(() => userStore.hasPermission('content.article.write'))
const canAiGenerate = computed(() => userStore.hasPermission('content.ai.generate'))
const canDistributeOperate = computed(() => userStore.hasPermission('content.distribution.operate'))
const canPublish = computed(() => userStore.hasPermission('content.publish.operate'))
const canManagePromptTemplates = computed(() => userStore.hasPermission('content.prompt_template.manage'))
const canManagePublishPlatforms = computed(() => userStore.hasPermission('user.manage'))
const canViewBatchPublishJobs = computed(() => userStore.hasPermission('content.read'))
const canViewSelfMediaSchedules = computed(() => userStore.hasPermission('content.read'))
const hasToolbarMoreActions = computed(() =>
  canViewBatchPublishJobs.value || canViewSelfMediaSchedules.value || canManagePromptTemplates.value || canManagePublishPlatforms.value,
)
const hasToolbarActions = computed(() =>
  canArticleWrite.value
  || canAiGenerate.value
  || canPublish.value
  || canViewBatchPublishJobs.value
  || canViewSelfMediaSchedules.value
  || canManagePromptTemplates.value
  || canManagePublishPlatforms.value,
)

const loading = ref(false)
const submitting = ref(false)
const rows = ref<ArticleDraft[]>([])
const selectedRows = ref<ArticleDraft[]>([])
const batchPublishChecking = ref(false)
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive({
  projectName: '',
  status: '',
  articleType: '',
  articleTypeCode: '',
  channelKey: '',
  generationMode: '' as '' | 'batch' | 'single',
  createdRange: [] as string[],
})
const publishedCount = computed(() => rows.value.filter((row) => row.status === 'published').length)
const distributableCount = computed(() => rows.value.filter((row) => canDistribute(row.status)).length)
const showAdvancedFilters = ref(false)
const blockedCount = computed(() => rows.value.filter((row) =>
  ['failed', 'risk_blocked'].includes(row.status)
  || row.complianceStatus === 'discarded_compliance_failed'
  || row.publishReviewStatus === 'rejected',
).length)
const scheduleDrawerVisible = ref(false)
const scheduleInitialFailureCode = ref('')
const scheduleInitialStatus = ref('')

const {
  detailVisible,
  detailData,
  detailViewMode,
  detailCoverImageUrl,
  detailMarkdown,
  detailHtml,
  revisionVisible,
  revisionViewMode,
  revisionForm,
  revisionHtml,
  openDetail,
  openRevision,
  openRevisionFromDetail,
  submitRevision,
  handleDetailStyleRenderCommand,
} = useArticleDetailRevision({ load, submitting })

const {
  mediaDistributeVisible,
  mediaDistributeBrandId,
  lastLocalHelperHealth,
  wechatCapability,
  douyinCapability,
  toutiaoAccounts,
  baijiahaoAccounts,
  zhihuAccounts,
  xiaohongshuAccounts,
  checkingSelfMediaAccountId,
  imageFolderScope,
  selectedImageFolderId,
  selectedMediaPlatform,
  selectedSelfMediaAccountId,
  selectedCoverMaterialId,
  selectedDouyinImageMaterialIds,
  douyinText,
  distributionAttempts,
  refreshingReviewTaskId,
  semiAutoConfirmingTaskId,
  semiAutoAbandoningTaskId,
  semiAutoLoginOpeningAccountId,
  environmentAccountResettingId,
  selfMediaSubmitting,
  wechatDistributionAvailable,
  wechatQuickScheduleAvailable,
  wechatStatusLabel,
  wechatStatusTagType,
  douyinDistributionAvailable,
  douyinStatusLabel,
  douyinStatusTagType,
  douyinSubmitButtonText,
  currentPlatformAccounts,
  selectedSelfMediaQuotaHint,
  displayImageFolders,
  imageMaterials,
  douyinImageMaterials,
  selectedDouyinMaterials,
  selfMediaDistributeActions,
  openMediaDistribute,
} = useSelfMediaDistribution({ rows, load })

const {
  distributionChannelVisible,
  distributionChannels,
  industrySiteVisible,
  industrySiteLoading,
  industrySiteSubmitting,
  industrySites,
  selectedIndustrySiteId,
  forumSiteVisible,
  forumSiteLoading,
  forumSiteSubmitting,
  forumSites,
  selectedForumSiteId,
  selectedForumFid,
  selectedForumBoards,
  canSubmitForumSite,
  authorityMediaVisible,
  authorityLoading,
  authoritySubmitting,
  authorityResources,
  selectedAuthorityResource,
  authorityPage,
  authorityQuery,
  authorityForm,
  openDistributionChannel,
  selectDistributionChannel,
  selectIndustrySite,
  selectForumSite,
  submitIndustrySite,
  submitForumSite,
  searchAuthorityMedia,
  resetAuthorityMediaQuery,
  onAuthorityPageChange,
  selectAuthorityResource,
  submitAuthorityMedia,
  distributionChannelInitial,
  distributionChannelClass,
  industrySiteInitial,
  industrySiteTagText,
  entranceLevelLabel,
  newsResourceLabel,
  includeConditionLabel,
  authorityMediaInitial,
  authorityWeightText,
  authorityRowClass,
  money,
  openExternalLink,
} = useArticleDistributionChannels({
  load,
  submitting,
  openSelfMediaDistribute: openMediaDistribute,
})

const statusOptions = [
  { label: '已就绪', value: 'approved' },
  { label: '分发中', value: 'distributing' },
  { label: '已分发', value: 'distributed' },
  { label: '已发布', value: 'published' },
  { label: '已下架', value: 'unpublished' },
]

const channelFilterOptions = [
  { label: '官网', value: 'agent_site:' },
  { label: '行业资讯站', value: 'industry_site:' },
  { label: '平台网站', value: 'forum:' },
  { label: '自媒体平台 / 今日头条', value: 'self_media:toutiao' },
  { label: '自媒体平台 / 公众号', value: 'self_media:wechat' },
  { label: '自媒体平台 / 知乎', value: 'self_media:zhihu' },
  { label: '自媒体平台 / 抖音图文', value: 'self_media:douyin' },
  { label: '自媒体平台 / 小红书', value: 'self_media:xiaohongshu' },
  { label: '自媒体平台 / 百家号', value: 'self_media:baijiahao' },
  { label: '自媒体平台 / 网易', value: 'self_media:netease' },
  { label: '自媒体平台 / 搜狐', value: 'self_media:sohu' },
  { label: '权威媒体 / 行业媒体', value: 'authority_media:industry_media' },
  { label: '权威媒体 / 地方媒体', value: 'authority_media:local_media' },
  { label: '权威媒体 / 财经媒体', value: 'authority_media:finance_media' },
  { label: '权威媒体 / 科技媒体', value: 'authority_media:tech_media' },
  { label: '权威媒体 / 新闻源媒体', value: 'authority_media:news_source' },
  { label: '权威媒体 / 门户媒体', value: 'authority_media:portal_media' },
]

const contentShapeOptions = [
  { label: '问答文章', value: 'faq' },
  { label: '场景内容文', value: 'scenario_content' },
  { label: '行业分析文', value: 'industry_article' },
  { label: '阶段建议文', value: 'stage_advice' },
  { label: '选择指南', value: 'buying_guide' },
  { label: '对比评测', value: 'comparison' },
  { label: '费用解析', value: 'cost_analysis' },
  { label: '避坑指南', value: 'pitfall_guide' },
  { label: '经验笔记', value: 'social_note' },
  { label: '资讯简讯', value: 'news_brief' },
  { label: '讨论帖', value: 'forum_discussion' },
]

function parseChannelKey(value: string) {
  const [channelGroupCode, channelSubCode = ''] = value.split(':')
  return {
    channelGroupCode: channelGroupCode || undefined,
    channelSubCode: channelSubCode || undefined,
  }
}

function articleTypeLabel(v?: string | null) {
  const map: Record<string, string> = {
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
  return v ? map[v] || v : '-'
}

function articleTypeInitial(v?: string | null) {
  const map: Record<string, string> = {
    faq: '问',
    scenario_content: '景',
    industry_article: '文',
    stage_advice: '议',
    buying_guide: '选',
    comparison: '比',
    cost_analysis: '费',
    pitfall_guide: '坑',
    social_note: '记',
    news_brief: '讯',
    forum_discussion: '帖',
  }
  return v ? map[v] || '文' : '-'
}

function articleTypeClass(v?: string | null) {
  const map: Record<string, string> = {
    faq: 'is-faq',
    scenario_content: 'is-scene',
    industry_article: 'is-article',
    stage_advice: 'is-advice',
    buying_guide: 'is-article',
    comparison: 'is-article',
    cost_analysis: 'is-article',
    pitfall_guide: 'is-advice',
    social_note: 'is-scene',
    news_brief: 'is-article',
    forum_discussion: 'is-scene',
  }
  return v ? map[v] || 'is-article' : 'is-article'
}

function contentStyleLabel(v?: string | null) {
  if (!v) return '-'
  const map: Record<string, string> = {
    toutiao: '今日头条',
    wechat: '公众号',
    zhihu: '知乎',
    douyin: '抖音图文',
    linkedin: '领英风格',
    agent_site_article: 'Agent 官网文章',
    industry_site: '行业资讯站',
    authority_media: '权威媒体',
    forum: '平台网站',
    xiaohongshu: '小红书',
    baijiahao: '百家号',
    netease: '网易',
    sohu: '搜狐',
  }
  return map[v] || v
}

function channelGroupLabel(v?: string | null) {
  const map: Record<string, string> = {
    agent_site: '官网',
    industry_site: '行业资讯站',
    self_media: '自媒体平台',
    authority_media: '权威媒体',
    forum: '平台网站',
  }
  return v ? map[v] || v : ''
}

function channelSubLabel(v?: string | null) {
  const map: Record<string, string> = {
    toutiao: '今日头条',
    wechat: '公众号',
    zhihu: '知乎',
    douyin: '抖音图文',
    xiaohongshu: '小红书',
    baijiahao: '百家号',
    netease: '网易',
    sohu: '搜狐',
    industry_media: '行业媒体',
    local_media: '地方媒体',
    finance_media: '财经媒体',
    tech_media: '科技媒体',
    news_source: '新闻源媒体',
    portal_media: '门户媒体',
  }
  return v ? map[v] || v : ''
}

function platformLabel(v?: string | null) {
  const map: Record<string, string> = {
    toutiao: '今日头条',
    wechat: '公众号',
    wechat_mp: '微信公众号',
    zhihu: '知乎',
    douyin: '抖音图文',
    xiaohongshu: '小红书',
    baijiahao: '百家号',
    netease: '网易',
    sohu: '搜狐',
  }
  return v ? map[v] || v : '-'
}

function articleChannelLabel(row: ArticleDraft) {
  if (row.channelGroupCode) {
    const group = channelGroupLabel(row.channelGroupCode)
    const sub = channelSubLabel(row.channelSubCode)
    return sub ? `${group}/${sub}` : group
  }
  return '-'
}

function isWechatArticle(row: Pick<ArticleDraft, 'channelGroupCode' | 'channelSubCode'>) {
  return row.channelGroupCode === 'self_media' && row.channelSubCode === 'wechat'
}

function canStyleRender(row: Pick<ArticleDraft, 'channelGroupCode' | 'channelSubCode'>) {
  return isWechatArticle(row)
}

function templateSourceLabel(v?: string | null) {
  const label = templateSourceValueLabel(v)
  return label === '-' ? '模板来源：-' : `模板来源：${label}`
}

function templateUsageLabel(row: ArticleDraft) {
  if (row.promptTemplateName) {
    return `模板：${row.promptTemplateName}（${templateSourceValueLabel(row.templateSource)}）`
  }
  if (row.promptTemplateId) {
    return `模板：#${row.promptTemplateId}（${templateSourceValueLabel(row.templateSource)}）`
  }
  return templateSourceLabel(row.templateSource)
}

function detailTemplateUsageLabel(detail: ArticleDetailResponse) {
  const task = detail.batchGenerationTask
  const templateName = detail.article.promptTemplateName || task?.promptTemplateName
  const templateId = detail.article.promptTemplateId || task?.promptTemplateId
  const source = detail.article.templateSource || task?.templateSource
  if (templateName) {
    return `${templateName}（${templateSourceValueLabel(source)}）`
  }
  if (templateId) {
    return `模板 #${templateId}（${templateSourceValueLabel(source)}）`
  }
  return templateSourceValueLabel(source)
}

function templateSourceValueLabel(v?: string | null) {
  const map: Record<string, string> = {
    smart: '智能匹配',
    weighted: '权重分配',
    custom: '手动指定',
    fallback_default_prompt: '默认兜底',
  }
  return v ? map[v] || v : '-'
}

function generationModeLabel(row: ArticleDraft) {
  if (row.generationMode === 'batch') return '批量生成'
  if (row.generatedBy === 'template_ai') return '单篇模板'
  if (row.generationMode === 'single') return '单篇生成'
  return row.systemGenerated ? '批量生成' : '单篇生成'
}

function generatedByLabel(v?: string | null) {
  const map: Record<string, string> = {
    ai: '自由 AI',
    ai_preview: 'AI 试写',
    template_ai: '单篇模板',
    batch_ai: '批量 AI',
    manual: '手动',
    system: '系统',
  }
  return v ? map[v] || v : '-'
}

function statusLabel(v: string) {
  return statusOptions.find((s) => s.value === v)?.label || v
}

function isMedicalArticle(row?: ArticleDraft | null) {
  return !!row?.medicalIndustryCode || !!row?.medicalChannelTier || !!row?.complianceStatus
}

function medicalComplianceLabel(v?: string | null) {
  const map: Record<string, string> = {
    pending: '待校验',
    passed: '合规通过',
    failed: '合规失败',
    discarded_compliance_failed: '已废弃',
  }
  return v ? map[v] || v : '未校验'
}

function medicalComplianceTag(v?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (v === 'passed') return 'success'
  if (v === 'pending') return 'warning'
  if (v === 'failed' || v === 'discarded_compliance_failed') return 'danger'
  return 'info'
}

function medicalReviewLabel(v?: string | null) {
  const map: Record<string, string> = {
    not_required: '无需法务',
    pending: '待法务确认',
    passed: '法务通过',
    rejected: '法务驳回',
  }
  return v ? map[v] || v : '未确认'
}

function medicalReviewTag(v?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (v === 'passed' || v === 'not_required') return 'success'
  if (v === 'pending') return 'warning'
  if (v === 'rejected') return 'danger'
  return 'info'
}

function canReviewMedicalPublish(row?: ArticleDraft | null) {
  return canArticleWrite.value
    && row?.medicalChannelTier === 'official_site'
    && row?.complianceStatus === 'passed'
    && row?.publishReviewStatus !== 'passed'
}

async function reviewMedicalPublish(row: ArticleDraft, action: 'approve' | 'reject') {
  const verb = action === 'approve' ? '通过' : '驳回'
  let comment = ''
  try {
    const result = await ElMessageBox.prompt(`请输入医疗官网发布法务${verb}说明`, `医疗发布${verb}`, {
      confirmButtonText: verb,
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: action === 'approve' ? '例如：已核对广告审查编号/资质信息' : '例如：缺少审查证明或内容需调整',
      inputValidator: (value) => action === 'approve' || !!value.trim() || '驳回时需填写原因',
    })
    comment = result.value || ''
  } catch {
    return
  }
  try {
    await reviewMedicalPublishArticle(row.id, { action, comment })
    ElMessage.success(`医疗发布已${verb}`)
    await load()
    if (detailData.value?.article?.id === row.id) {
      await openDetail(row.id)
    }
  } catch {
    // Global request handler displays the backend error.
  }
}

function handleDetailMedicalPublishReview(action: 'approve' | 'reject') {
  const article = detailData.value?.article
  if (article) {
    void reviewMedicalPublish(article, action)
  }
}

function distributionPlatformLabel(v?: string | null) {
  const map: Record<string, string> = {
    wechat_mp: '微信公众号',
    douyin: '抖音图文',
    toutiao: '今日头条',
    zhihu: '知乎',
    rest_api: 'REST API',
    ftp: 'FTP',
    email: '邮件',
    manual: '手动分发',
    brand_geo_site: '品牌官网',
    agent_official_site: 'Agent 官网',
    discuz_http: 'Discuz HTTP 直发',
    forum_playwright: '平台网站浏览器自动化',
  }
  return v ? map[v] || v : '-'
}

function distributionPlatformInitial(v?: string | null) {
  const label = distributionPlatformLabel(v)
  return label === '-' ? '发' : label.slice(0, 1)
}

function statusTagType(v: string): 'success' | 'warning' | 'danger' | 'info' {
  if (v === 'approved' || v === 'distributed' || v === 'published') return 'success'
  if (v === 'failed' || v === 'risk_blocked') return 'danger'
  if (v === 'distributing' || v === 'unpublished') return 'warning'
  return 'info'
}

function contentStatusClass(v: string) {
  if (v === 'approved' || v === 'distributed' || v === 'published') return 'is-success'
  if (v === 'failed' || v === 'risk_blocked') return 'is-danger'
  if (v === 'distributing' || v === 'unpublished') return 'is-warning'
  return 'is-muted'
}

function canEdit(status: string) {
  return status === 'approved' || status === 'unpublished'
}

function canEditFromDetail(status: string) {
  return canEdit(status)
}

function canDistribute(status: string) {
  return status === 'approved' || status === 'unpublished'
}

function isPublishedLockedStatus(status?: string | null) {
  return ['published', 'distributed'].includes(String(status || '').toLowerCase())
}

function canDeleteArticle(status: string) {
  return !['published', 'distributed', 'distributing'].includes(status)
}

async function deleteArticle(row: ArticleDraft) {
  try {
    await ElMessageBox.confirm(`确认删除文章「${row.title || row.id}」？删除后将不再显示在内容列表中。`, '删除文章', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
  } catch {
    return
  }
  try {
    await deleteContentArticle(row.id)
    ElMessage.success('文章已删除')
    if (rows.value.length === 1 && page.current > 1) {
      page.current -= 1
    }
    await load()
  } catch {
    ElMessage.error('删除文章失败')
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await getContentArticles({
      current: page.current,
      size: page.size,
      projectName: query.projectName.trim() || undefined,
      status: query.status || undefined,
      articleType: query.articleType || undefined,
      articleTypeCode: query.articleTypeCode || undefined,
      ...parseChannelKey(query.channelKey),
      generationMode: query.generationMode || undefined,
      createdStartDate: query.createdRange[0] || undefined,
      createdEndDate: query.createdRange[1] || undefined,
    })
    rows.value = data.data.records || []
    page.total = data.data.total || 0
  } catch {
    rows.value = []
    page.total = 0
    ElMessage.error('加载文章失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.current = 1
  load()
}

function resetQuery() {
  query.projectName = ''
  query.status = ''
  query.articleType = ''
  query.articleTypeCode = ''
  query.channelKey = ''
  query.generationMode = ''
  query.createdRange = []
  search()
}

function onPageChange(v: number) {
  page.current = v
  load()
}

function onSelectionChange(selection: ArticleDraft[]) {
  selectedRows.value = selection
}

function canSelectForBatchPublish(row: ArticleDraft) {
  return canPublish.value && canDistribute(row.status)
}

function goManualCreate() {
  router.push({
    path: '/admin/content/articles/manual-create',
    query: {
      articleType: query.articleType || undefined,
    },
  })
}

function openBatchGeneration() {
  router.push({
    path: '/admin/content/articles/batch-generate',
  })
}

function openPublishPlatformManagement() {
  router.push({
    path: '/admin/content/publish-platforms',
  })
}

function openPromptTemplateManagement() {
  router.push({
    path: '/admin/content/article-prompt-templates',
  })
}

function openSpecialIndustryComplianceWorkbench() {
  router.push({
    path: '/admin/content/special-industry-compliance',
  })
}

function openBatchPublishJobs() {
  router.push({
    path: '/admin/content/articles/batch-publish-jobs',
  })
}

function openScheduleDrawer() {
  scheduleDrawerVisible.value = true
}

function openBrandConfig(brandId?: number | null) {
  if (!brandId) return
  router.push(`/admin/brands/${brandId}`)
}

function handleToolbarMoreCommand(command: string) {
  if (command === 'jobs') {
    openBatchPublishJobs()
  } else if (command === 'schedules') {
    openScheduleDrawer()
  } else if (command === 'templates') {
    openPromptTemplateManagement()
  } else if (command === 'special-compliance') {
    openSpecialIndustryComplianceWorkbench()
  } else if (command === 'platforms') {
    openPublishPlatformManagement()
  } else if (command === 'schedule-capabilities') {
    router.push('/admin/content/self-media-schedule-capabilities')
  }
}

async function openBatchPublish() {
  if (!canPublish.value) {
    ElMessage.warning('当前账号没有批量发布权限')
    return
  }
  const selected = selectedRows.value.filter((row) => canDistribute(row.status))
  if (!selected.length) {
    ElMessage.warning('请选择已就绪或已下架的文章')
    return
  }
  batchPublishChecking.value = true
  try {
    const details = await Promise.all(selected.map((row) => getContentArticleDetail(row.id).then((res) => res.data.data)))
    const blocked = details
      .map((detail) => {
        const style = detailContentStyle(detail) || ''
        const reason = batchPublishBlockReason(style) || medicalBatchPublishBlockReason(detail)
        return reason
          ? {
              title: detail.article.title || '未命名文章',
              styleLabel: contentStyleLabel(style),
              reason,
            }
          : null
      })
      .filter((item): item is BatchPublishBlockedItem => item !== null)
    if (blocked.length) {
      await ElMessageBox.alert(renderBatchPublishBlockMessage(blocked), '当前存在平台不满足自动发布', {
        confirmButtonText: '知道了',
        customClass: 'batch-publish-block-alert',
      })
      return
    }
    router.push({
      path: '/admin/content/articles/batch-publish',
      query: { ids: selected.map((row) => row.id).join(',') },
    })
  } finally {
    batchPublishChecking.value = false
  }
}

function renderBatchPublishBlockMessage(items: BatchPublishBlockedItem[]) {
  return h('div', { class: 'batch-publish-block-dialog' }, [
    h('p', { class: 'batch-publish-block-summary' }, '以下文章的平台风格暂不支持自动发布，请调整后再发起批量发布。'),
    h(
      'div',
      { class: 'batch-publish-block-list' },
      items.map((item) =>
        h('div', { class: 'batch-publish-block-item' }, [
          h('div', { class: 'batch-publish-block-title' }, item.title),
          h('div', { class: 'batch-publish-block-meta' }, [
            h('span', { class: 'batch-publish-block-style' }, item.styleLabel),
            h('span', { class: 'batch-publish-block-reason' }, item.reason),
          ]),
        ]),
      ),
    ),
    h('div', { class: 'batch-publish-block-tip' }, '可继续使用单篇分发，或将文章调整为 Agent 官网/行业资讯站后批量发布。'),
  ])
}

function batchPublishBlockReason(contentStyle?: string | null) {
  if (contentStyle === 'agent_site_article' || contentStyle === 'linkedin') return ''
  if (contentStyle === 'industry_site') return ''
  if (contentStyle === 'forum') return ''
  if (contentStyle === 'toutiao') return '今日头条不允许自动发布'
  if (contentStyle === 'wechat') return '公众号不允许自动发布'
  if (contentStyle === 'zhihu') return '知乎不允许自动发布'
  if (contentStyle === 'douyin') return '抖音图文暂不纳入批量发布'
  if (contentStyle === 'authority_media') return '权威媒体不允许自动发布'
  return '文章未绑定可自动发布的平台风格'
}

function medicalBatchPublishBlockReason(detail: ArticleDetailResponse) {
  const article = detail.article
  if (article.medicalChannelTier !== 'official_site') {
    return ''
  }
  if (article.complianceStatus !== 'passed') {
    return '医疗合规未通过，不能发布官网档'
  }
  if (article.medicalAdReviewNo || article.publishReviewStatus === 'passed') {
    return ''
  }
  return '医疗官网档缺少广告审查号或人工法务确认'
}

function riskWordHits(article?: Pick<ArticleDraft, 'riskWordsJson'> | null): RiskWordHit[] {
  if (!article?.riskWordsJson) return []
  try {
    const parsed = JSON.parse(article.riskWordsJson) as unknown
    if (!Array.isArray(parsed)) return []
    return parsed
      .map((item) => {
        if (!item || typeof item !== 'object') return null
        const row = item as Record<string, unknown>
        const word = typeof row.word === 'string' ? row.word.trim() : ''
        if (!word) return null
        return {
          word,
          severity: typeof row.severity === 'string' ? row.severity : 'block',
          source: typeof row.source === 'string' ? row.source : 'unknown',
        }
      })
      .filter((item): item is RiskWordHit => item !== null)
  } catch {
    return []
  }
}

function riskSeverityLabel(severity?: string | null) {
  return severity === 'warn' ? '提醒' : '阻断'
}

function riskSourceLabel(source?: string | null) {
  if (source === 'brand') return '品牌'
  if (source === 'project') return '项目'
  if (source === 'global') return '全局'
  return '未知'
}

function detailContentStyle(detail: ArticleDetailResponse) {
  return detail.batchGenerationTask?.contentStyle || detail.article.contentStyle || ''
}

function detailChannelLabel(detail: ArticleDetailResponse) {
  const group = detail.article.channelGroupCode
  if (!group) return '-'
  const groupLabel = channelGroupLabel(group)
  const subLabel = channelSubLabel(detail.article.channelSubCode)
  return subLabel ? `${groupLabel}/${subLabel}` : groupLabel
}

function detailTopic(detail: ArticleDetailResponse) {
  return detail.batchGenerationTask?.topic || detail.article.topic || ''
}

onMounted(async () => {
  handleWechatAuthResult()
  handleDouyinAuthResult()
  await handleManualCreateResult()
  await load()
  await openCreatedArticleDetail()
  openScheduleDrawerFromRoute()
})

watch(() => [route.query.scheduleFailureCode, route.query.scheduleStatus], () => {
  openScheduleDrawerFromRoute()
})

async function handleManualCreateResult() {
  const projectId = Number(route.query.projectId || 0)
  if (projectId > 0) {
    try {
      const { data } = await getProjectDetail(projectId)
      query.projectName = data.data.projectName || ''
    } catch {
      query.projectName = ''
    }
  }
  const articleType = String(route.query.articleType || '')
  if (articleType) {
    query.articleType = articleType
  }
}

async function openCreatedArticleDetail() {
  const articleId = Number(route.query.articleId || 0)
  if (articleId > 0) {
    await openDetail(articleId)
  }
}

function openScheduleDrawerFromRoute() {
  const failureCode = String(route.query.scheduleFailureCode || '').trim()
  const status = String(route.query.scheduleStatus || '').trim()
  if (!failureCode && !status) return
  scheduleInitialFailureCode.value = failureCode
  scheduleInitialStatus.value = status
  scheduleDrawerVisible.value = true
}

function handleWechatAuthResult() {
  const auth = route.query.wechatAuth
  if (auth === 'success') {
    ElMessage.success('微信公众号授权成功')
    return
  }
  if (auth === 'permission_missing') {
    ElMessage.warning('微信公众号授权完成，但权限不足，请重新授权并勾选素材管理/群发权限')
    return
  }
  if (auth === 'callback_failed') {
    ElMessage.error('微信公众号授权回调失败，请重试')
  }
}

function handleDouyinAuthResult() {
  const auth = route.query.douyinAuth
  if (auth === 'success') {
    ElMessage.success('抖音账号授权成功')
    return
  }
  if (auth === 'scope_missing') {
    ElMessage.warning('授权成功但缺少必要权限，请重新授权并勾选必需权限')
    return
  }
  if (auth === 'callback_failed') {
    ElMessage.error(`抖音授权失败：${route.query.errorMessage || '未知错误'}`)
  }
}

</script>

<style scoped>
.content-execution-page {
  padding: 8px 0;
}

.toolbar {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.content-toolbar-card {
  border-radius: 12px;
  overflow: hidden;
}

.content-toolbar-card :deep(.el-card__body) {
  padding: 20px 22px;
}

.toolbar-filter-row {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar-filter-line {
  display: grid;
  align-items: center;
  gap: 10px;
}

.toolbar-filter-line.is-primary {
  grid-template-columns:
    minmax(240px, 300px)
    minmax(170px, 200px)
    minmax(150px, 180px)
    1px
    auto;
  justify-content: start;
}

.toolbar-filter-line.is-secondary {
  padding-top: 16px;
  border-top: 1px dashed #e2e8f0;
  grid-template-columns:
    minmax(180px, 220px)
    minmax(180px, 220px)
    minmax(180px, 220px)
    minmax(300px, 360px);
  justify-content: start;
}

.toolbar-project-input,
.toolbar-filter-control,
.toolbar-date-range {
  width: 100%;
}

.toolbar-filter-control.is-narrow {
  min-width: 128px;
}

.toolbar-filter-control.is-wide {
  min-width: 170px;
}

.toolbar-filter-divider {
  width: 1px;
  height: 32px;
  background: #e5ebf3;
}

.toolbar-select-prefix {
  color: #8ea0ba;
  font-weight: 400;
  margin-right: 18px;
}

.toolbar-project-input :deep(.el-input__wrapper),
.toolbar-date-range :deep(.el-input__wrapper),
.toolbar-date-range :deep(.el-range-input),
.toolbar-filter-control :deep(.el-select__wrapper) {
  min-height: 50px;
  border-radius: 11px;
  background: #f8fafd;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
}

.toolbar-project-input :deep(.el-input__wrapper:hover),
.toolbar-date-range :deep(.el-input__wrapper:hover),
.toolbar-filter-control :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px #cbd8e8 inset;
}

.toolbar-project-input :deep(.el-input__inner),
.toolbar-filter-control :deep(.el-select__placeholder),
.toolbar-filter-control :deep(.el-select__selected-item),
.toolbar-date-range :deep(.el-range-input) {
  color: #334155;
  font-size: 14px;
  font-weight: 400;
}

.toolbar-project-input :deep(.el-input__inner::placeholder),
.toolbar-date-range :deep(.el-range-input::placeholder) {
  color: #8ea0ba;
  font-weight: 400;
}

.toolbar-date-range :deep(.el-range-separator) {
  color: #c2ccda;
  font-weight: 400;
}

.toolbar-filter-actions,
.toolbar-action-row,
.toolbar-primary-actions,
.toolbar-secondary-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar-filter-actions {
  justify-content: flex-end;
  white-space: nowrap;
  flex-wrap: nowrap;
}

.toolbar-search-action,
.toolbar-reset-action {
  height: 50px;
  min-width: 112px;
  border-radius: 11px;
  font-size: 15px;
  font-weight: 700;
}

.toolbar-search-action {
  background: #2563eb;
  border-color: #2563eb;
  box-shadow: 0 10px 20px rgba(37, 99, 235, 0.28);
}

.toolbar-reset-action {
  color: #334155;
  background: #fff;
  border-color: #e2e8f0;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
}

.toolbar-toggle-action {
  color: #2563eb;
  font-size: 15px;
  font-weight: 800;
  padding: 0 4px 0 10px;
}

.toolbar-toggle-action:hover {
  color: #1d4ed8;
  background: transparent;
}

.toolbar-toggle-icon {
  margin-left: 4px;
  font-size: 14px;
}

.toolbar-action-row {
  justify-content: space-between;
  min-height: 44px;
  padding-top: 12px;
  border-top: 1px solid #edf2f7;
  flex-wrap: wrap;
}

.toolbar-primary-actions,
.toolbar-secondary-actions {
  flex-wrap: wrap;
}

.toolbar-secondary-actions {
  justify-content: flex-end;
}

.toolbar-selection-hint {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.toolbar-more-action {
  color: #64748b;
}

.toolbar-more-action:hover {
  color: #334155;
}

@media (max-width: 1180px) {
  .toolbar-filter-line.is-primary,
  .toolbar-filter-line.is-secondary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .toolbar-filter-divider {
    display: none;
  }

  .toolbar-filter-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .toolbar-filter-line.is-primary,
  .toolbar-filter-line.is-secondary {
    grid-template-columns: 1fr;
  }

  .toolbar-action-row,
  .toolbar-primary-actions,
  .toolbar-secondary-actions {
    align-items: stretch;
    width: 100%;
  }

  .toolbar-action-row {
    flex-direction: column;
  }

  .toolbar-primary-actions .el-button,
  .toolbar-secondary-actions .el-button,
  .toolbar-filter-actions .el-button,
  .toolbar-secondary-actions :deep(.el-dropdown) {
    flex: 1 1 0;
  }

  .toolbar-filter-actions {
    align-items: stretch;
    width: 100%;
  }

  .toolbar-toggle-action {
    justify-content: center;
  }

  .toolbar-selection-hint {
    width: 100%;
  }
}

.mb-3 {
  margin-bottom: 12px;
}

.pager.compact {
  margin-top: 8px;
}

.content-avatar.is-faq {
  background: linear-gradient(135deg, #2563eb, #0891b2);
}

.content-avatar.is-scene {
  background: linear-gradient(135deg, #0d9488, #14b8a6);
}

.content-avatar.is-article {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
}

.content-avatar.is-advice {
  background: linear-gradient(135deg, #475569, #64748b);
}

.content-status-text {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #92400e;
  font-weight: 700;
}

.content-status-text.is-success {
  color: #047857;
}

.content-status-text.is-danger {
  color: #b91c1c;
}

.content-status-text.is-muted {
  color: #64748b;
}

.content-status-dot {
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  border-radius: 999px;
  background: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.14);
}

.content-status-text.is-success .content-status-dot {
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.14);
}

.content-status-text.is-danger .content-status-dot {
  background: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.14);
}

.content-status-text.is-muted .content-status-dot {
  background: #94a3b8;
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.16);
}

.content-neutral-action {
  color: #64748b;
}

.content-neutral-action:hover {
  color: #334155;
}

.medical-status-cell {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
}

.distribution-channel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.distribution-channel-dialog :deep(.el-dialog__body) {
  padding-top: 4px;
}

.distribution-channel-dialog :deep(.el-dialog__footer) {
  padding: 14px 20px;
  border-top: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #fff, #f8fafc);
}

.distribution-channel-intro {
  margin: 0 0 14px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.distribution-channel-card {
  position: relative;
  min-height: 124px;
  padding: 16px 18px 14px 64px;
  border: 1px solid #dbe3ef;
  border-radius: 12px;
  background:
    linear-gradient(135deg, rgba(248, 251, 255, 0.96), #fff 54%);
  color: #0f172a;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.distribution-channel-card:hover {
  border-color: #93c5fd;
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.1);
  transform: translateY(-1px);
}

.distribution-channel-card.disabled {
  background: #f8fafc;
  cursor: not-allowed;
  opacity: 0.72;
}

.distribution-channel-card.disabled:hover {
  border-color: #dbe3ef;
  box-shadow: none;
  transform: none;
}

.distribution-channel-mark {
  position: absolute;
  top: 18px;
  left: 18px;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  color: #fff;
  font-size: 15px;
  font-weight: 800;
  box-shadow: 0 10px 18px rgba(15, 23, 42, 0.14);
}

.distribution-channel-mark.is-official {
  background: linear-gradient(135deg, #2563eb, #0891b2);
}

.distribution-channel-mark.is-industry {
  background: linear-gradient(135deg, #0d9488, #14b8a6);
}

.distribution-channel-mark.is-forum {
  background: linear-gradient(135deg, #16a34a, #0f766e);
}

.distribution-channel-mark.is-media {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
}

.distribution-channel-mark.is-authority {
  background: linear-gradient(135deg, #475569, #64748b);
}

.distribution-channel-status {
  position: absolute;
  top: 16px;
  right: 18px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #047857;
  font-size: 12px;
  font-weight: 700;
}

.distribution-channel-status.disabled {
  color: #64748b;
}

.distribution-channel-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.14);
}

.distribution-channel-status.disabled .distribution-channel-status-dot {
  background: #94a3b8;
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.16);
}

.distribution-channel-title {
  display: block;
  margin: 4px 76px 8px 0;
  font-size: 15px;
  font-weight: 800;
}

.distribution-channel-desc {
  display: block;
  min-height: 38px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.distribution-channel-action {
  display: inline-flex;
  align-items: center;
  margin-top: 10px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

.distribution-channel-action::after {
  content: ">";
  margin-left: 6px;
}

.industry-site-dialog :deep(.el-dialog__body) {
  padding-top: 4px;
}

.industry-site-dialog :deep(.el-dialog__footer) {
  padding: 14px 20px;
  border-top: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #fff, #f8fafc);
}

.industry-site-intro {
  margin: 0 0 14px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.industry-site-list {
  display: grid;
  gap: 12px;
}

.industry-site-card {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  gap: 12px;
  width: 100%;
  padding: 16px;
  border: 1px solid #dbe3ef;
  border-radius: 14px;
  background:
    linear-gradient(135deg, rgba(248, 251, 255, 0.96), #ffffff 58%);
  color: #0f172a;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease, transform 0.18s ease;
}

.industry-site-card:hover {
  border-color: #93c5fd;
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.1);
  transform: translateY(-1px);
}

.industry-site-card.selected {
  border-color: #60a5fa;
  background:
    linear-gradient(135deg, #eff6ff, #ffffff 62%, #f0fdfa);
  box-shadow:
    0 14px 30px rgba(37, 99, 235, 0.12),
    inset 0 0 0 1px rgba(96, 165, 250, 0.35);
}

.industry-site-radio {
  position: relative;
  width: 16px;
  height: 16px;
  margin-top: 9px;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  background: #ffffff;
  flex-shrink: 0;
}

.industry-site-radio.selected {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.industry-site-radio.selected::after {
  content: "";
  position: absolute;
  inset: 4px;
  border-radius: inherit;
  background: #2563eb;
}

.industry-site-content,
.industry-site-head,
.industry-site-meta {
  display: flex;
  min-width: 0;
}

.industry-site-content {
  flex-direction: column;
  gap: 12px;
}

.industry-site-head {
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.industry-site-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, #0d9488, #14b8a6);
  color: #ffffff;
  font-size: 15px;
  font-weight: 800;
  flex-shrink: 0;
  box-shadow: 0 10px 18px rgba(15, 23, 42, 0.12);
}

.industry-site-selected {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.industry-site-meta {
  flex-wrap: wrap;
  gap: 10px 18px;
  color: #475569;
  font-size: 13px;
  line-height: 1.5;
}

.industry-site-meta-label {
  margin-right: 6px;
  color: #94a3b8;
  font-weight: 700;
}

.forum-board-picker {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid #dbe3ef;
  border-radius: 10px;
  background: #f8fafc;
}

.authority-media-dialog {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.authority-media-modal :deep(.el-dialog) {
  overflow: hidden;
  border-radius: 18px;
  box-shadow: 0 28px 72px rgba(15, 23, 42, 0.18);
}

.authority-media-modal :deep(.el-dialog__header) {
  margin: 0;
  padding: 20px 24px 14px;
  border-bottom: 1px solid #e2e8f0;
  background: linear-gradient(135deg, #ffffff, #eff6ff 62%, #ecfdf5);
}

.authority-media-modal :deep(.el-dialog__title) {
  color: #0f172a;
  font-size: 20px;
  font-weight: 800;
}

.authority-media-modal :deep(.el-dialog__body) {
  padding: 16px 20px 12px;
  background: #f8fbff;
}

.authority-media-modal :deep(.el-dialog__footer) {
  padding: 0;
  border-top: 1px solid #e2e8f0;
  background: #ffffff;
  overflow: hidden;
}

.authority-media-notice {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: linear-gradient(135deg, #eff6ff, #f8fbff);
  color: #475569;
  font-size: 14px;
  line-height: 1.5;
}

.authority-media-notice-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 999px;
  background: #2563eb;
  color: #ffffff;
  font-size: 12px;
  font-weight: 800;
  flex-shrink: 0;
}

.authority-filter {
  display: grid;
  grid-template-columns:
    minmax(160px, 1.12fr)
    minmax(140px, 1fr)
    minmax(120px, 0.86fr)
    minmax(120px, 0.86fr)
    minmax(130px, 0.92fr)
    minmax(120px, 0.86fr)
    auto;
  gap: 10px;
  align-items: center;
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.045);
}

.authority-filter :deep(.el-input),
.authority-filter :deep(.el-select) {
  width: 100%;
}

.authority-filter :deep(.el-input__wrapper),
.authority-filter :deep(.el-select__wrapper) {
  min-height: 40px;
  border-radius: 10px;
}

.authority-filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  white-space: nowrap;
}

.authority-filter-actions .el-button {
  margin-left: 0;
}

.authority-table-wrap {
  overflow: hidden;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
}

.authority-table-wrap :deep(.el-table__header th) {
  border-right-color: transparent;
  background: #eff6ff;
  color: #334155;
  font-weight: 800;
}

.authority-table-wrap :deep(.el-table__body td) {
  border-right-color: transparent;
  border-bottom-color: #edf2f7;
}

.authority-table-wrap :deep(.el-table__row) {
  cursor: pointer;
}

.authority-table-wrap :deep(.el-table__row:hover > td) {
  background: #f8fbff !important;
}

.authority-table-wrap :deep(.el-table__row.is-selected-authority > td) {
  background: #eff6ff !important;
}

.authority-table-wrap :deep(.el-table__row.is-selected-authority td:first-child) {
  box-shadow: inset 3px 0 0 #2563eb;
}

.authority-media-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.authority-media-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, #475569, #2563eb);
  color: #ffffff;
  font-weight: 800;
  flex-shrink: 0;
}

.authority-media-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.authority-media-name {
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.authority-media-sub {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.authority-price {
  color: #0f172a;
  font-weight: 800;
}

.authority-soft-chip {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  height: 24px;
  padding: 0 9px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.authority-weight {
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.authority-link-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.authority-link-actions .el-button {
  margin-left: 0;
}

.authority-empty-link {
  color: #94a3b8;
}

.authority-confirm {
  padding: 14px;
  border: 1px solid #bfdbfe;
  border-radius: 14px;
  background:
    linear-gradient(135deg, #eff6ff, #ffffff 64%);
}

.authority-confirm-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.authority-selected {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: #475569;
}

.authority-selected strong {
  color: #0f172a;
}

.authority-selected span {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.authority-form {
  display: grid;
  align-items: start;
  gap: 0 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.authority-form .el-form-item:last-child {
  grid-column: 1 / -1;
}

.authority-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  box-sizing: border-box;
  min-width: 0;
  padding: 14px 20px;
}

.authority-footer-summary {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #64748b;
  font-size: 13px;
}

.authority-footer-summary strong {
  max-width: 260px;
  overflow: hidden;
  color: #0f172a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.authority-footer-summary span:not(.authority-footer-label) {
  white-space: nowrap;
}

.authority-footer-label {
  color: #2563eb;
  font-weight: 800;
}

.authority-footer-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 10px;
  max-width: 100%;
}

.authority-footer-actions .el-button {
  margin-left: 0;
  white-space: nowrap;
}

.site-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.site-name {
  display: block;
  overflow: hidden;
  font-size: 14px;
  font-weight: 800;
  color: #0f172a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.site-domain {
  display: block;
  overflow: hidden;
  margin-top: 2px;
  font-size: 12px;
  color: #64748b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:global(.batch-publish-block-alert) {
  width: min(560px, calc(100vw - 40px));
  padding: 0;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.18);
}

:global(.batch-publish-block-alert .el-message-box__header) {
  padding: 22px 24px 8px;
}

:global(.batch-publish-block-alert .el-message-box__title) {
  color: #111827;
  font-size: 20px;
  font-weight: 650;
  line-height: 1.35;
}

:global(.batch-publish-block-alert .el-message-box__headerbtn) {
  top: 18px;
  right: 18px;
  width: 28px;
  height: 28px;
}

:global(.batch-publish-block-alert .el-message-box__content) {
  padding: 0 24px 16px;
  color: #475569;
}

:global(.batch-publish-block-dialog) {
  display: grid;
  gap: 12px;
}

:global(.batch-publish-block-summary) {
  margin: 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.7;
}

:global(.batch-publish-block-list) {
  display: grid;
  gap: 10px;
  max-height: 260px;
  overflow: auto;
}

:global(.batch-publish-block-item) {
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}

:global(.batch-publish-block-title) {
  color: #1f2937;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.6;
}

:global(.batch-publish-block-meta) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

:global(.batch-publish-block-style) {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  color: #3b6df5;
  background: #eaf0ff;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

:global(.batch-publish-block-reason) {
  color: #64748b;
  font-size: 13px;
}

.risk-word-line,
.risk-word-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.risk-word-line {
  margin-top: 2px;
}

:global(.batch-publish-block-tip) {
  padding: 10px 12px;
  color: #8a5b06;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.6;
}

:global(.batch-publish-block-alert .el-message-box__btns) {
  padding: 0 24px 24px;
}

:global(.batch-publish-block-alert .el-button--primary) {
  min-width: 92px;
  height: 40px;
  padding: 0 22px;
  background: #3b6df5;
  border-color: #3b6df5;
  border-radius: 9px;
  font-weight: 600;
}

</style>
