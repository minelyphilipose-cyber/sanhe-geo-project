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
        <div class="toolbar-left">
          <el-input v-model="query.projectName" clearable placeholder="项目名称" style="width: 220px" @keyup.enter="search" />
          <el-select v-model="query.articleType" clearable placeholder="文章类型" style="width: 160px">
            <el-option label="FAQ" value="faq" />
            <el-option label="场景内容" value="scenario_content" />
            <el-option label="行业文章" value="industry_article" />
            <el-option label="阶段建议" value="stage_advice" />
          </el-select>
          <el-select v-model="query.status" clearable placeholder="状态" style="width: 150px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </div>
        <div class="toolbar-right">
          <el-button v-if="canWrite" type="primary" @click="openBatchGeneration">批量生成文章</el-button>
          <el-button v-if="canWrite" @click="goManualCreate">手动生成文章</el-button>
          <el-button v-if="canWrite" :disabled="!selectedRows.length || batchPublishChecking" :loading="batchPublishChecking" @click="openBatchPublish">
            批量发布
          </el-button>
          <span v-if="canWrite" class="toolbar-divider" />
          <el-button v-if="canWrite" class="toolbar-minor-action" @click="openBatchPublishJobs">发布任务</el-button>
          <el-button v-if="canWrite" class="toolbar-minor-action" @click="openPublishPlatformManagement">发布平台管理</el-button>
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
        <span class="admin-metric-label">待审核</span>
        <strong class="admin-metric-value">{{ reviewCount }}</strong>
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
                <div class="admin-entity-avatar content-avatar" :class="articleTypeClass(scope.row.articleType)">
                  {{ articleTypeInitial(scope.row.articleType) }}
                </div>
                <div class="min-w-0">
                  <div class="admin-entity-main">{{ scope.row.projectName || `#${scope.row.projectId}` }}</div>
                  <div class="admin-entity-sub">
                    {{ articleTypeLabel(scope.row.articleType) }} · {{ contentStyleLabel(scope.row.contentStyle) }}
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="标题" min-width="360" show-overflow-tooltip>
            <template #default="scope">
              <div class="admin-cell-stack">
                <span class="admin-cell-main">{{ scope.row.title || '-' }}</span>
                <span class="admin-cell-sub">{{ contentStyleLabel(scope.row.contentStyle) }}</span>
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
          <el-table-column label="创建时间" width="180">
            <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="scope">
              <div class="admin-row-actions">
                <el-button link type="primary" @click="openDetail(scope.row.id)">详情</el-button>
                <el-button v-if="canWrite && canReview(scope.row)" link type="primary" @click="openReview(scope.row)">审核</el-button>
                <el-button v-if="canWrite && canEdit(scope.row.status)" class="content-neutral-action" link @click="openRevision(scope.row)">修订</el-button>
                <el-button v-if="canWrite && canDistribute(scope.row.status)" link type="success" @click="openDistributionChannel(scope.row)">分发</el-button>
                <el-button v-if="canWrite && canDeleteArticle(scope.row.status)" link type="danger" @click="deleteArticle(scope.row)">删除</el-button>
                <el-button v-if="canWrite && canResubmit(scope.row.status)" class="is-wide" link type="primary" @click="openResubmit(scope.row)">重新提交</el-button>
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

    <el-drawer v-model="detailVisible" title="文章详情" size="70%" class="content-detail-drawer">
      <div v-if="detailData" class="detail-wrap">
        <div class="detail-summary-panel">
          <div class="detail-summary-head">
            <div>
              <span class="detail-kicker">文章信息</span>
              <h3>{{ detailData.article.title || '未命名文章' }}</h3>
            </div>
            <el-tag :type="statusTagType(detailData.article.status)">
              {{ statusLabel(detailData.article.status) }}
            </el-tag>
          </div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="文章ID">{{ detailData.article.id }}</el-descriptions-item>
            <el-descriptions-item label="项目">{{ detailData.project?.projectName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="文章类型">{{ articleTypeLabel(detailData.article.articleType) }}</el-descriptions-item>
            <el-descriptions-item label="平台风格">{{ contentStyleLabel(detailContentStyle(detailData)) }}</el-descriptions-item>
            <el-descriptions-item label="文章主题" :span="2">{{ detailTopic(detailData) || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="riskWordHits(detailData.article).length" label="风险词" :span="2">
              <div class="risk-word-list">
                <el-tag
                  v-for="hit in riskWordHits(detailData.article)"
                  :key="`${hit.severity}-${hit.source}-${hit.word}`"
                  size="small"
                  :type="hit.severity === 'block' ? 'danger' : 'warning'"
                  effect="light"
                >
                  {{ riskSeverityLabel(hit.severity) }} · {{ riskSourceLabel(hit.source) }}: {{ hit.word }}
                </el-tag>
              </div>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section-panel">
          <h4 class="detail-title">版本记录</h4>
          <el-table :data="detailData.versions" border>
            <el-table-column prop="versionNo" label="版本" width="80" />
            <el-table-column prop="title" label="标题" min-width="220" />
            <el-table-column prop="generatedBy" label="来源" width="130" />
            <el-table-column label="时间" width="180">
              <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="detail-section-panel detail-preview-panel">
          <div class="detail-header">
            <h4 class="detail-title">内容预览</h4>
            <el-radio-group v-model="detailViewMode" size="small">
              <el-radio-button label="preview">预览</el-radio-button>
              <el-radio-button label="markdown">Markdown</el-radio-button>
            </el-radio-group>
          </div>
          <el-input v-if="detailViewMode === 'markdown'" type="textarea" :rows="14" :model-value="detailMarkdown" readonly />
          <div v-else class="markdown-preview" v-html="detailHtml"></div>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="reviewVisible" title="审核文章" width="560px" class="admin-editor-dialog">
      <el-form class="admin-dialog-form content-review-form" :model="reviewForm" label-width="110px">
        <el-form-item label="审核动作" required>
          <el-select v-model="reviewForm.action" style="width: 100%">
            <el-option label="通过" value="approve" />
            <el-option label="驳回" value="reject" />
            <el-option label="退回修改" value="return_for_revision" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedArticleHasRisk" label="风险覆盖">
          <el-checkbox v-model="reviewForm.riskOverride">强制通过提醒级风险</el-checkbox>
        </el-form-item>
        <el-form-item v-if="selectedArticleRiskHits.length" label="命中风险词">
          <div class="risk-word-list">
            <el-tag
              v-for="hit in selectedArticleRiskHits"
              :key="`${hit.severity}-${hit.source}-${hit.word}`"
              size="small"
              :type="hit.severity === 'block' ? 'danger' : 'warning'"
              effect="light"
            >
              {{ riskSeverityLabel(hit.severity) }} · {{ riskSourceLabel(hit.source) }}: {{ hit.word }}
            </el-tag>
          </div>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="reviewForm.comment" type="textarea" :rows="4" placeholder="驳回或退回修改时必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReview">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="revisionVisible" title="修订文章" width="840px" class="admin-editor-dialog">
      <el-form class="admin-dialog-form content-revision-form" :model="revisionForm" label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="revisionForm.title" />
        </el-form-item>
        <el-form-item class="is-full" label="正文" required>
          <div class="editor-wrap">
            <div class="detail-header editor-header">
              <span class="editor-title">内容编辑</span>
              <el-radio-group v-model="revisionViewMode" size="small">
                <el-radio-button label="markdown">Markdown</el-radio-button>
                <el-radio-button label="preview">预览</el-radio-button>
              </el-radio-group>
            </div>
            <el-input v-if="revisionViewMode === 'markdown'" v-model="revisionForm.contentMarkdown" type="textarea" :rows="14" />
            <div v-else class="markdown-preview editor-preview" v-html="revisionHtml"></div>
          </div>
        </el-form-item>
        <el-form-item class="is-full" label="备注">
          <el-input v-model="revisionForm.note" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="revisionVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRevision">保存修订</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resubmitVisible" title="重新提交" width="520px">
      <el-form :model="resubmitForm" label-width="90px">
        <el-form-item label="备注">
          <el-input v-model="resubmitForm.comment" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resubmitVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitResubmit">提交</el-button>
      </template>
    </el-dialog>

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

    <el-dialog v-model="mediaDistributeVisible" title="自媒体分发" width="980px" class="media-distribute-dialog">
      <div class="media-distribute">
        <el-alert
          v-if="wechatCapability && !wechatCapability.draftDistributionEnabled"
          class="media-capability-alert"
          type="warning"
          :closable="false"
          show-icon
          title="微信公众号能力审核中"
        />
        <el-alert
          v-if="douyinCapability && !douyinCapability.enabled"
          class="media-capability-alert"
          type="warning"
          :closable="false"
          show-icon
          :title="`抖音图文未开启：${douyinCapability.disabledReason || 'feature flag disabled'}`"
        />

        <div class="media-grid">
          <button
            class="media-platform"
            :class="{ active: selectedMediaPlatform === 'wechat_mp', disabled: !wechatCapability?.draftDistributionEnabled }"
            type="button"
            @click="handleWechatPlatformClick"
          >
            <span class="wechat-mark">微</span>
            <span class="media-name">微信公众号</span>
            <el-tag size="small" :type="wechatStatusTagType">{{ wechatStatusLabel }}</el-tag>
          </button>
          <button
            class="media-platform"
            :class="{ active: selectedMediaPlatform === 'douyin', disabled: !douyinCapability?.enabled }"
            type="button"
            @click="handleDouyinPlatformClick"
          >
            <span class="douyin-mark">抖</span>
            <span class="media-name">抖音图文</span>
            <el-tag size="small" :type="douyinStatusTagType">{{ douyinStatusLabel }}</el-tag>
          </button>
          <button
            class="media-platform"
            :class="{ active: selectedMediaPlatform === 'toutiao', disabled: !toutiaoAccounts.length }"
            type="button"
            @click="handleSemiAutoPlatformClick('toutiao')"
          >
            <span class="toutiao-mark">头</span>
            <span class="media-name">头条</span>
            <el-tag size="small" :type="semiAutoStatusTagType(toutiaoAccounts)">{{ semiAutoStatusLabel(toutiaoAccounts) }}</el-tag>
          </button>
          <button
            class="media-platform"
            :class="{ active: selectedMediaPlatform === 'zhihu', disabled: !zhihuAccounts.length }"
            type="button"
            @click="handleSemiAutoPlatformClick('zhihu')"
          >
            <span class="zhihu-mark">知</span>
            <span class="media-name">知乎</span>
            <el-tag size="small" :type="semiAutoStatusTagType(zhihuAccounts)">{{ semiAutoStatusLabel(zhihuAccounts) }}</el-tag>
          </button>
        </div>

        <div v-if="currentPlatformAccounts.length" class="self-media-account-list">
          <div v-for="account in currentPlatformAccounts" :key="account.id" class="self-media-account-row">
            <div class="self-media-account-main">
              <div class="self-media-account-title">{{ account.accountName }}</div>
              <div class="self-media-account-meta">{{ account.platformAccountId }}</div>
            </div>
            <el-tag size="small" :type="selfMediaAccountStatusTag(account)">
              {{ selfMediaAccountStatusLabel(account) }}
            </el-tag>
            <el-tag
              v-if="isSemiAutoPlatform(selectedMediaPlatform)"
              size="small"
              :type="semiAutoCredentialTagType(account)"
            >
              {{ semiAutoCredentialLabel(account) }}
            </el-tag>
            <el-button
              v-if="selectedMediaPlatform === 'wechat_mp' && account.status === 'active'"
              size="small"
              :loading="checkingSelfMediaAccountId === account.id"
              @click="checkWechatAccount(account.id)"
            >
              检测登录
            </el-button>
            <el-button
              v-if="selectedMediaPlatform === 'wechat_mp' && account.status === 'active'"
              size="small"
              type="primary"
              @click="startWechatDraft(account)"
            >
              保存草稿
            </el-button>
            <el-button
              v-if="selectedMediaPlatform === 'douyin' && account.status === 'active'"
              size="small"
              type="primary"
              @click="startDouyinImageText(account)"
            >
              选择账号
            </el-button>
            <el-button
              v-if="isSemiAutoPlatform(selectedMediaPlatform) && account.status === 'active' && !hasActiveCookieCredential(account)"
              size="small"
              type="warning"
              :loading="semiAutoAccountActionLoading(account)"
              @click="submitSemiAutoExtensionTask(account)"
            >
              去登录并捕获
            </el-button>
          </div>
        </div>
        <el-empty
          v-else-if="isSemiAutoPlatform(selectedMediaPlatform)"
          description="当前品牌暂无可用的头条/知乎账号"
        />

        <el-alert
          v-if="extensionBindCode"
          class="extension-bind-guide"
          type="success"
          show-icon
          :closable="false"
        >
          <template #title>
            <span>扩展绑定码：<strong class="bind-code">{{ extensionBindCode.code }}</strong></span>
          </template>
          <div class="extension-bind-content">
            <span>请打开 GEO 浏览器扩展，输入绑定码完成绑定；然后在同一浏览器登录目标平台，回到扩展里捕获凭证。</span>
            <span>有效期：{{ formatTtlSeconds(extensionBindCode.expiresInSeconds) }}</span>
            <el-button size="small" type="success" link @click="copyExtensionBindCode">复制绑定码</el-button>
          </div>
        </el-alert>

        <div v-if="selectedMediaPlatform === 'wechat_mp' && selectedSelfMediaAccountId" class="cover-picker">
          <div class="cover-picker-header">
            <span>选择公众号封面</span>
            <el-tag size="small" type="info">{{ imageMaterials.length }} 张图片</el-tag>
          </div>
          <div class="folder-toolbar">
            <el-radio-group v-model="imageFolderScope" size="small" @change="handleFolderScopeChange">
              <el-radio-button label="project">项目关联</el-radio-button>
              <el-radio-button label="all">品牌全部</el-radio-button>
            </el-radio-group>
          </div>
          <div v-if="displayImageFolders.length" class="folder-list">
            <button
              v-for="folder in displayImageFolders"
              :key="folder.id"
              type="button"
              class="folder-item"
              :class="{ selected: selectedImageFolderId === folder.id }"
              @click="selectImageFolder(folder.id)"
            >
              <span>{{ folder.folderName }}</span>
              <el-tag v-if="folder.projectRelated" size="small" type="success">项目</el-tag>
              <el-tag size="small" type="info">{{ folder.materialCount || folder.materials.length }}</el-tag>
            </button>
          </div>
          <el-empty v-if="!imageMaterials.length" description="当前品牌暂无可用图片素材" />
          <div v-else class="cover-grid">
            <button
              v-for="material in imageMaterials"
              :key="material.id"
              type="button"
              class="cover-item"
              :class="{ selected: selectedCoverMaterialId === material.id }"
              @click="selectedCoverMaterialId = material.id"
            >
              <img :src="materialThumbUrl(material)" :alt="material.fileName" loading="lazy" />
              <span>{{ material.fileName }}</span>
            </button>
          </div>
        </div>

        <div v-if="selectedMediaPlatform === 'douyin' && selectedSelfMediaAccountId" class="cover-picker">
          <div class="cover-picker-header">
            <span>选择抖音图文图片</span>
            <el-tag size="small" type="info">{{ selectedDouyinImageMaterialIds.length }}/30</el-tag>
          </div>
          <div class="folder-toolbar">
            <el-radio-group v-model="imageFolderScope" size="small" @change="handleFolderScopeChange">
              <el-radio-button label="project">项目关联</el-radio-button>
              <el-radio-button label="all">品牌全部</el-radio-button>
            </el-radio-group>
          </div>
          <div v-if="displayImageFolders.length" class="folder-list">
            <button
              v-for="folder in displayImageFolders"
              :key="folder.id"
              type="button"
              class="folder-item"
              :class="{ selected: selectedImageFolderId === folder.id }"
              @click="selectImageFolder(folder.id)"
            >
              <span>{{ folder.folderName }}</span>
              <el-tag v-if="folder.projectRelated" size="small" type="success">项目</el-tag>
              <el-tag size="small" type="info">{{ folder.materialCount || folder.materials.length }}</el-tag>
            </button>
          </div>
          <el-empty v-if="!douyinImageMaterials.length" description="当前品牌暂无 JPG/PNG 图片素材" />
          <div v-else class="cover-grid">
            <button
              v-for="material in douyinImageMaterials"
              :key="material.id"
              type="button"
              class="cover-item"
              :class="{ selected: selectedDouyinImageMaterialIds.includes(material.id) }"
              @click="toggleDouyinImage(material.id)"
            >
              <img :src="materialThumbUrl(material)" :alt="material.fileName" loading="lazy" />
              <span>{{ material.fileName }}</span>
            </button>
          </div>
          <div v-if="selectedDouyinMaterials.length" class="douyin-selected-list">
            <div v-for="(material, index) in selectedDouyinMaterials" :key="material.id" class="douyin-selected-row">
              <span class="douyin-selected-index">{{ index + 1 }}</span>
              <span class="douyin-selected-name">{{ material.fileName }}</span>
              <el-button size="small" :disabled="index === 0" @click="moveDouyinImage(index, -1)">上移</el-button>
              <el-button size="small" :disabled="index === selectedDouyinMaterials.length - 1" @click="moveDouyinImage(index, 1)">下移</el-button>
            </div>
          </div>
          <div class="douyin-text-editor">
            <div class="cover-picker-header">
              <span>图文文案</span>
              <el-tag size="small" :type="douyinText.length > 1000 ? 'danger' : 'info'">{{ douyinText.length }}/1000</el-tag>
            </div>
            <el-input
              v-model="douyinText"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
              placeholder="可填写抖音图文文案；不填时后端使用文章标题"
            />
          </div>
        </div>

        <div v-if="distributionAttempts.length" class="distribution-history">
          <div class="cover-picker-header">
            <span>分发记录</span>
            <el-tag size="small" type="info">{{ distributionAttempts.length }} 条</el-tag>
          </div>
          <el-table class="distribution-history-table" :data="distributionAttempts" max-height="260">
            <el-table-column label="平台" min-width="180">
              <template #default="scope">
                <div class="distribution-target-cell">
                  <span class="distribution-target-avatar">{{ distributionPlatformInitial(scope.row.integrationMethod) }}</span>
                  <span class="distribution-target-main">
                    <span class="distribution-target-title">{{ distributionPlatformLabel(scope.row.integrationMethod) }}</span>
                    <span class="distribution-target-sub">{{ scope.row.siteName || scope.row.domain || `任务 #${scope.row.id}` }}</span>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="任务状态" width="130">
              <template #default="scope">
                <el-tag size="small" :type="distributionStatusTag(scope.row.status)">
                  {{ distributionStatusLabel(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="审核状态" width="120">
              <template #default="scope">
                <el-tag v-if="scope.row.reviewStatus" size="small" :type="reviewStatusTag(scope.row.reviewStatus)">
                  {{ reviewStatusLabel(scope.row.reviewStatus) }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="平台反馈" min-width="230" show-overflow-tooltip>
              <template #default="scope">
                <div class="distribution-feedback">
                  <span class="distribution-feedback-main">{{ scope.row.externalStatus || '暂无平台状态' }}</span>
                  <span v-if="scope.row.errorMessage" class="distribution-feedback-error">{{ scope.row.errorMessage }}</span>
                  <span v-else class="distribution-feedback-muted">未返回错误</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="scope">
                <div class="admin-row-actions distribution-actions">
                  <el-button
                    v-if="scope.row.reviewStatus === 'under_review'"
                    link
                    type="primary"
                    :loading="refreshingReviewTaskId === scope.row.id"
                    @click="refreshReviewStatus(scope.row)"
                  >
                    刷新
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="mediaDistributeVisible = false">关闭</el-button>
        <el-button
          v-if="selectedMediaPlatform === 'wechat_mp' && selectedSelfMediaAccountId"
          type="primary"
          :loading="selfMediaSubmitting"
          :disabled="!selectedCoverMaterialId"
          @click="submitWechatDraft"
        >
          保存至公众号草稿箱
        </el-button>
        <el-button
          v-if="selectedMediaPlatform === 'douyin' && selectedSelfMediaAccountId"
          type="primary"
          :loading="selfMediaSubmitting"
          :disabled="!selectedDouyinImageMaterialIds.length || douyinText.length > 1000"
          @click="submitDouyinImageText"
        >
          发布抖音图文
        </el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import { useUserStore } from '@/stores/user'
import type { ArticleDetailResponse, ArticleDraft, AuthorityMediaResource, BrandImageFolder, BrandMaterial, DistributionTask, DouyinCapability, PublishSite, SelfMediaAccount, WechatMpCapability } from '@/types'
import {
  checkSelfMediaAccountAuth,
  deleteContentArticle,
  distributeContentArticleToAgentSite,
  distributeContentArticleToAuthorityMedia,
  distributeContentArticleToIndustrySite,
  distributeContentArticleToSelfMediaAccount,
  getArticleDistribution,
  getAuthorityMediaResources,
  getContentArticleDetail,
  getContentArticles,
  getDouyinAuthUrl,
  getDouyinCapability,
  getSelfMediaAccountsByBrand,
  getWechatMpAuthUrl,
  getWechatMpCapability,
  refreshDistributionTaskReviewStatus,
  resubmitContentArticle,
  reviewContentArticle,
  saveContentArticleRevision,
} from '@/api/content'
import { getPublishSites } from '@/api/publishSite'
import { getBrandDetail, getBrandImageFolders, getBrandMaterialStream } from '@/api/customer'
import { createExtensionBindCode, type ExtensionBindCode } from '@/api/extension'
import { getProjectDetail } from '@/api/project'
import { pingExtensionBridge, startExtensionCookieCapture, startExtensionFill } from '@/composables/useExtensionBridge'
import { formatDateTime } from '@/utils/format'

type MediaPlatform = 'wechat_mp' | 'douyin' | 'toutiao' | 'zhihu'
type SemiAutoPlatform = 'toutiao' | 'zhihu'
type ExtensionBridgeStatus = 'unknown' | 'checking' | 'bound' | 'unbound' | 'missing' | 'error'
type DistributionChannel = 'official_site' | 'industry_site' | 'self_media' | 'authority_media'
type SelfMediaAccountWithCredential = SelfMediaAccount & {
  cookieCredentialStatus?: string | null
  cookieCredentialVersion?: number | null
  cookieCredentialCapturedAt?: string | null
}
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
const canWrite = computed(() => userStore.hasPermission('project.write'))

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
})
const reviewCount = computed(() => rows.value.filter((row) => canReview(row)).length)
const distributableCount = computed(() => rows.value.filter((row) => canDistribute(row.status)).length)
const blockedCount = computed(() => rows.value.filter((row) => ['rejected', 'failed', 'risk_blocked'].includes(row.status)).length)

const detailVisible = ref(false)
const detailData = ref<ArticleDetailResponse | null>(null)
const detailViewMode = ref<'preview' | 'markdown'>('preview')
const currentArticleId = ref<number | null>(null)
const selectedArticleHasRisk = ref(false)
const selectedArticleRiskHits = ref<RiskWordHit[]>([])

const reviewVisible = ref(false)
const reviewForm = reactive({
  action: 'approve' as 'approve' | 'reject' | 'return_for_revision',
  comment: '',
  riskOverride: false,
})

const revisionVisible = ref(false)
const revisionViewMode = ref<'preview' | 'markdown'>('markdown')
const revisionForm = reactive({
  title: '',
  contentMarkdown: '',
  note: '',
})

const resubmitVisible = ref(false)
const resubmitForm = reactive({ comment: '' })

const distributionChannelVisible = ref(false)
const distributionChannelArticle = ref<ArticleDraft | null>(null)
const distributionChannels: Array<{
  value: DistributionChannel
  label: string
  description: string
  disabled?: boolean
}> = [
  { value: 'official_site', label: 'Agent 官网', description: '发布到项目品牌的 Agent 官网站点。' },
  { value: 'industry_site', label: '行业资讯站', description: '选择已启用的行业资讯站并发布。' },
  { value: 'self_media', label: '自媒体平台', description: '分发到微信公众号、抖音、头条、知乎等账号。' },
  { value: 'authority_media', label: '权威媒体', description: '选择特价网新闻媒体资源并创建出稿订单。' },
]

const industrySiteVisible = ref(false)
const industrySiteLoading = ref(false)
const industrySiteSubmitting = ref(false)
const industrySiteArticle = ref<ArticleDraft | null>(null)
const industrySites = ref<PublishSite[]>([])
const selectedIndustrySiteId = ref<number | null>(null)

const authorityMediaVisible = ref(false)
const authorityLoading = ref(false)
const authoritySubmitting = ref(false)
const authorityResources = ref<AuthorityMediaResource[]>([])
const selectedAuthorityResource = ref<AuthorityMediaResource | null>(null)
const authorityPage = reactive({ current: 1, size: 10, total: 0 })
const authorityQuery = reactive<{
  keyword: string
  industry: string
  province: string
  entranceLevel?: number
  newsResource?: number
  includeCondition?: number
}>({
  keyword: '',
  industry: '',
  province: '',
  entranceLevel: undefined,
  newsResource: undefined,
  includeCondition: undefined,
})
const authorityForm = reactive({
  articleId: 0,
  resourceId: 0,
  salingPrice: 0,
  publishedAt: '',
  remark: '',
})

const mediaDistributeVisible = ref(false)
const mediaDistributeArticleId = ref<number | null>(null)
const mediaDistributeBrandId = ref<number | null>(null)
const wechatCapability = ref<WechatMpCapability | null>(null)
const douyinCapability = ref<DouyinCapability | null>(null)
const wechatAccounts = ref<SelfMediaAccount[]>([])
const douyinAccounts = ref<SelfMediaAccount[]>([])
const toutiaoAccounts = ref<SelfMediaAccount[]>([])
const zhihuAccounts = ref<SelfMediaAccount[]>([])
const checkingSelfMediaAccountId = ref<number | null>(null)
const brandImageFolders = ref<BrandImageFolder[]>([])
const materialThumbUrls = ref<Record<number, string | null>>({})
const materialThumbObjectUrls = ref<string[]>([])
const imageFolderScope = ref<'project' | 'all'>('project')
const selectedImageFolderId = ref<number | null>(null)
const selectedMediaPlatform = ref<MediaPlatform>('wechat_mp')
const selectedSelfMediaAccountId = ref<number | null>(null)
const selectedCoverMaterialId = ref<number | null>(null)
const selectedDouyinImageMaterialIds = ref<number[]>([])
const douyinText = ref('')
const distributionAttempts = ref<DistributionTask[]>([])
const refreshingReviewTaskId = ref<number | null>(null)
const selfMediaSubmitting = ref(false)
const extensionBindCode = ref<ExtensionBindCode | null>(null)
const extensionBindCodeLoadingAccountId = ref<number | null>(null)
const semiAutoCookieCaptureLoadingAccountId = ref<number | null>(null)
const pendingCookieCaptureAccountId = ref<number | null>(null)
const extensionBridgeChecking = ref(false)
const extensionBridgeState = reactive({
  status: 'unknown' as ExtensionBridgeStatus,
  message: '正在等待检测浏览器扩展状态',
  extensionVersion: '',
})

let cookieCapturePollTimer: number | null = null

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const detailMarkdown = computed(() => detailData.value?.versions?.[0]?.contentMarkdown || '')
const detailHtml = computed(() => markdown.render(detailMarkdown.value || ''))
const revisionHtml = computed(() => markdown.render(revisionForm.contentMarkdown || ''))
const wechatActive = computed(() => wechatAccounts.value.some((account) => account.status === 'active'))
const wechatStatusLabel = computed(() => {
  if (!wechatCapability.value?.draftDistributionEnabled) return '审核中'
  if (wechatActive.value) return '已登录'
  return '未登录'
})
const wechatStatusTagType = computed<'success' | 'warning' | 'info'>(() => {
  if (!wechatCapability.value?.draftDistributionEnabled) return 'warning'
  return wechatActive.value ? 'success' : 'info'
})
const douyinActive = computed(() => douyinAccounts.value.some((account) => account.status === 'active'))
const douyinStatusLabel = computed(() => {
  if (!douyinCapability.value?.enabled) return '未开启'
  if (douyinActive.value) return '已登录'
  return '未登录'
})
const douyinStatusTagType = computed<'success' | 'warning' | 'info'>(() => {
  if (!douyinCapability.value?.enabled) return 'warning'
  return douyinActive.value ? 'success' : 'info'
})
const currentPlatformAccounts = computed(() => {
  switch (selectedMediaPlatform.value) {
    case 'douyin':
      return douyinAccounts.value
    case 'toutiao':
      return toutiaoAccounts.value
    case 'zhihu':
      return zhihuAccounts.value
    default:
      return wechatAccounts.value
  }
})
const projectImageFolders = computed(() => brandImageFolders.value.filter((folder) => folder.projectRelated))
const displayImageFolders = computed(() => {
  if (imageFolderScope.value === 'project' && projectImageFolders.value.length) {
    return projectImageFolders.value
  }
  return brandImageFolders.value
})
const selectedImageFolder = computed(() => displayImageFolders.value.find((folder) => folder.id === selectedImageFolderId.value) || null)
const currentFolderMaterials = computed(() => selectedImageFolder.value?.materials || [])
const imageMaterials = computed(() => currentFolderMaterials.value.filter((item) => {
  const type = (item.fileType || '').toLowerCase()
  return ['jpg', 'jpeg', 'png', 'gif', 'bmp'].includes(type)
}))
const douyinImageMaterials = computed(() => currentFolderMaterials.value.filter((item) => {
  const type = (item.fileType || '').toLowerCase()
  return ['jpg', 'jpeg', 'png'].includes(type)
}))
const selectedDouyinMaterials = computed(() => selectedDouyinImageMaterialIds.value
  .map((id) => douyinImageMaterials.value.find((item) => item.id === id))
  .filter((item): item is BrandMaterial => !!item))
const statusOptions = [
  { label: '待审核', value: 'pending_review' },
  { label: '已就绪', value: 'approved' },
  { label: '已驳回', value: 'rejected' },
  { label: '修改中', value: 'under_revision' },
  { label: '已发布', value: 'published' },
  { label: '已下架', value: 'unpublished' },
]

function articleTypeLabel(v: string) {
  const map: Record<string, string> = {
    faq: 'FAQ',
    scenario_content: '场景内容',
    industry_article: '行业文章',
    stage_advice: '阶段建议',
  }
  return map[v] || v
}

function articleTypeInitial(v: string) {
  const map: Record<string, string> = {
    faq: '问',
    scenario_content: '景',
    industry_article: '文',
    stage_advice: '议',
  }
  return map[v] || '文'
}

function articleTypeClass(v: string) {
  const map: Record<string, string> = {
    faq: 'is-faq',
    scenario_content: 'is-scene',
    industry_article: 'is-article',
    stage_advice: 'is-advice',
  }
  return map[v] || 'is-article'
}

function contentStyleLabel(v?: string | null) {
  if (!v) return '-'
  const map: Record<string, string> = {
    toutiao: '今日头条',
    wechat: '公众号',
    zhihu: '知乎',
    douyin_image_text: '抖音图文',
    linkedin: '领英风格',
    agent_site_article: 'Agent 官网文章',
    industry_site: '行业资讯站',
    authority_media: '权威媒体',
    forum: '论坛',
    xiaohongshu: '小红书',
  }
  return map[v] || v
}

function statusLabel(v: string) {
  return statusOptions.find((s) => s.value === v)?.label || v
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
  }
  return v ? map[v] || v : '-'
}

function distributionPlatformInitial(v?: string | null) {
  const label = distributionPlatformLabel(v)
  return label === '-' ? '发' : label.slice(0, 1)
}

function distributionChannelInitial(v: DistributionChannel) {
  const map: Record<DistributionChannel, string> = {
    official_site: '站',
    industry_site: '讯',
    self_media: '媒',
    authority_media: '权',
  }
  return map[v]
}

function distributionChannelClass(v: DistributionChannel) {
  const map: Record<DistributionChannel, string> = {
    official_site: 'is-official',
    industry_site: 'is-industry',
    self_media: 'is-media',
    authority_media: 'is-authority',
  }
  return map[v]
}

function distributionStatusLabel(v?: string | null) {
  const map: Record<string, string> = {
    pending: '待提交',
    submitting: '提交中',
    submitted: '已提交',
    filled: '已填充',
    failed: '失败',
    confirmed: '已确认',
    published: '已发布',
  }
  return v ? map[v] || v : '-'
}

function distributionStatusTag(v?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (v === 'submitted' || v === 'confirmed' || v === 'published' || v === 'filled') return 'success'
  if (v === 'submitting' || v === 'pending') return 'warning'
  if (v === 'failed') return 'danger'
  return 'info'
}

function statusTagType(v: string): 'success' | 'warning' | 'danger' | 'info' {
  if (v === 'approved' || v === 'published') return 'success'
  if (v === 'rejected') return 'danger'
  if (v === 'under_revision' || v === 'unpublished') return 'warning'
  return 'info'
}

function contentStatusClass(v: string) {
  if (v === 'approved' || v === 'published') return 'is-success'
  if (v === 'rejected' || v === 'failed' || v === 'risk_blocked') return 'is-danger'
  if (v === 'pending_review' || v === 'under_revision' || v === 'unpublished') return 'is-warning'
  return 'is-muted'
}

function canReview(row: ArticleDraft) {
  return row.status === 'pending_review' && !row.systemGenerated
}

function canEdit(status: string) {
  return status === 'pending_review' || status === 'under_revision' || status === 'rejected'
}

function canResubmit(status: string) {
  return status === 'under_revision' || status === 'rejected'
}

function canDistribute(status: string) {
  return status === 'approved' || status === 'unpublished'
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
  return canDistribute(row.status)
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

function openBatchPublishJobs() {
  router.push({
    path: '/admin/content/articles/batch-publish-jobs',
  })
}

async function openBatchPublish() {
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
        const reason = batchPublishBlockReason(style)
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
  if (contentStyle === 'douyin_image_text') return '抖音图文不允许自动发布'
  if (contentStyle === 'authority_media') return '权威媒体不允许自动发布'
  return '文章未绑定可自动发布的平台风格'
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

function detailTopic(detail: ArticleDetailResponse) {
  return detail.batchGenerationTask?.topic || detail.article.topic || ''
}

async function openDetail(articleId: number) {
  try {
    const { data } = await getContentArticleDetail(articleId)
    detailData.value = data.data
    detailViewMode.value = 'preview'
    detailVisible.value = true
  } catch {
    ElMessage.error('加载详情失败')
  }
}

function openReview(row: ArticleDraft) {
  currentArticleId.value = row.id
  selectedArticleHasRisk.value = !!row.hasRisk
  selectedArticleRiskHits.value = riskWordHits(row)
  reviewForm.action = 'approve'
  reviewForm.comment = ''
  reviewForm.riskOverride = false
  reviewVisible.value = true
}

async function openRevision(row: ArticleDraft) {
  currentArticleId.value = row.id
  revisionForm.title = row.title
  revisionForm.note = ''
  revisionViewMode.value = 'markdown'
  try {
    const { data } = await getContentArticleDetail(row.id)
    revisionForm.contentMarkdown = data.data.versions?.[0]?.contentMarkdown || ''
  } catch {
    revisionForm.contentMarkdown = ''
  }
  revisionVisible.value = true
}

function openResubmit(row: ArticleDraft) {
  currentArticleId.value = row.id
  resubmitForm.comment = ''
  resubmitVisible.value = true
}

function openDistributionChannel(row: ArticleDraft) {
  distributionChannelArticle.value = row
  distributionChannelVisible.value = true
}

async function selectDistributionChannel(channel: DistributionChannel) {
  const row = distributionChannelArticle.value
  if (!row) return
  if (channel === 'industry_site') {
    distributionChannelVisible.value = false
    await openIndustrySiteDistribute(row)
    return
  }
  distributionChannelVisible.value = false
  if (channel === 'official_site') {
    await distributeToAgentSite(row)
    return
  }
  if (channel === 'self_media') {
    await openMediaDistribute(row)
    return
  }
  await openAuthorityMedia(row)
}

async function openIndustrySiteDistribute(row: ArticleDraft) {
  industrySiteArticle.value = row
  selectedIndustrySiteId.value = null
  industrySites.value = []
  industrySiteVisible.value = true
  industrySiteLoading.value = true
  try {
    const { data } = await getPublishSites({ status: 'active' })
    industrySites.value = data.data || []
  } catch {
    ElMessage.error('加载行业资讯站失败')
  } finally {
    industrySiteLoading.value = false
  }
}

function selectIndustrySite(row?: PublishSite) {
  selectedIndustrySiteId.value = row?.id || null
}

async function submitIndustrySite() {
  const row = industrySiteArticle.value
  if (!row || !selectedIndustrySiteId.value) return
  industrySiteSubmitting.value = true
  try {
    const result = await distributeContentArticleToIndustrySite(row.id, selectedIndustrySiteId.value)
    const task = result.data.data
    if (task.status === 'submitted') {
      ElMessage.success('行业资讯站分发成功')
      industrySiteVisible.value = false
      await load()
    } else {
      ElMessage.error(task.errorMessage || '行业资讯站分发失败')
    }
  } finally {
    industrySiteSubmitting.value = false
  }
}

async function openAuthorityMedia(row: ArticleDraft) {
  authorityForm.articleId = row.id
  authorityForm.resourceId = 0
  authorityForm.salingPrice = 0
  authorityForm.publishedAt = ''
  authorityForm.remark = ''
  selectedAuthorityResource.value = null
  authorityMediaVisible.value = true
  authorityPage.current = 1
  await loadAuthorityMediaResources()
}

async function loadAuthorityMediaResources() {
  authorityLoading.value = true
  try {
    const { data } = await getAuthorityMediaResources({
      current: authorityPage.current,
      size: authorityPage.size,
      keyword: authorityQuery.keyword.trim() || undefined,
      industry: authorityQuery.industry.trim() || undefined,
      province: authorityQuery.province.trim() || undefined,
      entranceLevel: authorityQuery.entranceLevel,
      newsResource: authorityQuery.newsResource,
      includeCondition: authorityQuery.includeCondition,
    })
    authorityResources.value = data.data.records || []
    authorityPage.total = data.data.total || 0
  } catch {
    authorityResources.value = []
    authorityPage.total = 0
    ElMessage.error('加载权威媒体资源失败')
  } finally {
    authorityLoading.value = false
  }
}

function searchAuthorityMedia() {
  authorityPage.current = 1
  loadAuthorityMediaResources()
}

function resetAuthorityMediaQuery() {
  authorityQuery.keyword = ''
  authorityQuery.industry = ''
  authorityQuery.province = ''
  authorityQuery.entranceLevel = undefined
  authorityQuery.newsResource = undefined
  authorityQuery.includeCondition = undefined
  searchAuthorityMedia()
}

function onAuthorityPageChange(v: number) {
  authorityPage.current = v
  loadAuthorityMediaResources()
}

function selectAuthorityResource(row?: AuthorityMediaResource) {
  if (!row) return
  selectedAuthorityResource.value = row
  authorityForm.resourceId = row.id
  authorityForm.salingPrice = Number(row.price || 0)
}

async function submitAuthorityMedia() {
  if (!authorityForm.articleId || !authorityForm.resourceId) return
  authoritySubmitting.value = true
  try {
    const result = await distributeContentArticleToAuthorityMedia(authorityForm.articleId, {
      resourceId: authorityForm.resourceId,
      salingPrice: authorityForm.salingPrice,
      publishedAt: authorityForm.publishedAt || undefined,
      remark: authorityForm.remark || undefined,
    })
    const task = result.data.data
    if (task.status === 'submitted') {
      ElMessage.success('权威媒体订单已提交，等待出稿')
      authorityMediaVisible.value = false
      await load()
      return
    }
    ElMessage.error(task.errorMessage || '权威媒体订单提交失败')
  } finally {
    authoritySubmitting.value = false
  }
}

async function openMediaDistribute(row: ArticleDraft) {
  mediaDistributeArticleId.value = row.id
  mediaDistributeBrandId.value = null
  selectedMediaPlatform.value = 'wechat_mp'
  wechatAccounts.value = []
  douyinAccounts.value = []
  toutiaoAccounts.value = []
  zhihuAccounts.value = []
  brandImageFolders.value = []
  imageFolderScope.value = 'project'
  selectedImageFolderId.value = null
  selectedSelfMediaAccountId.value = null
  selectedCoverMaterialId.value = null
  selectedDouyinImageMaterialIds.value = []
  douyinText.value = row.title || ''
  distributionAttempts.value = []
  extensionBindCode.value = null
  extensionBindCodeLoadingAccountId.value = null
  try {
    const [detailRes, wechatCapabilityRes, douyinCapabilityRes, distributionRes] = await Promise.all([
      getContentArticleDetail(row.id),
      getWechatMpCapability(),
      getDouyinCapability(),
      getArticleDistribution(row.id),
    ])
    const brandId = detailRes.data.data.project?.brandId
    if (!brandId) {
      ElMessage.error('当前文章未绑定品牌，无法分发到自媒体')
      return
    }
    mediaDistributeBrandId.value = brandId
    wechatCapability.value = wechatCapabilityRes.data.data
    douyinCapability.value = douyinCapabilityRes.data.data
    distributionAttempts.value = distributionRes.data.data.attempts || []
    const [accountRes, folderRes] = await Promise.all([
      getSelfMediaAccountsByBrand(brandId),
      getBrandImageFolders(brandId, {
        projectId: row.projectId,
        activeOnly: true,
        includeMaterials: true,
      }),
    ])
    applySelfMediaAccounts(accountRes.data.data || [])
    brandImageFolders.value = folderRes.data.data || []
    ensureSelectedImageFolder()
    await loadMaterialThumbs()
    mediaDistributeVisible.value = true
  } catch {
    ElMessage.error('加载自媒体账号失败')
  }
}

function applySelfMediaAccounts(accounts: SelfMediaAccount[]) {
  wechatAccounts.value = accounts.filter((account) => account.platform === 'wechat_mp')
  douyinAccounts.value = accounts.filter((account) => account.platform === 'douyin')
  toutiaoAccounts.value = accounts.filter((account) => account.platform === 'toutiao')
  zhihuAccounts.value = accounts.filter((account) => account.platform === 'zhihu')
}

async function refreshSelfMediaAccounts() {
  if (!mediaDistributeBrandId.value) return
  const { data } = await getSelfMediaAccountsByBrand(mediaDistributeBrandId.value)
  applySelfMediaAccounts(data.data || [])
}

async function handleWechatPlatformClick() {
  if (!wechatCapability.value?.draftDistributionEnabled) {
    ElMessage.info('微信公众号能力审核中，暂未开放授权')
    return
  }
  if (!wechatActive.value) {
    if (!mediaDistributeBrandId.value) {
      ElMessage.error('当前文章未绑定品牌，无法授权公众号')
      return
    }
    const { data } = await getWechatMpAuthUrl({
      brandId: mediaDistributeBrandId.value,
      redirectArticleId: mediaDistributeArticleId.value || undefined,
    })
    window.location.href = data.data.authUrl
    return
  }
  const account = wechatAccounts.value.find((item) => item.status === 'active')
  if (account) {
    startWechatDraft(account)
  }
}

function startWechatDraft(account: SelfMediaAccount) {
  selectedMediaPlatform.value = 'wechat_mp'
  selectedSelfMediaAccountId.value = account.id
  ensureSelectedImageFolder()
  selectedCoverMaterialId.value = imageMaterials.value[0]?.id || null
}

async function handleDouyinPlatformClick() {
  selectedMediaPlatform.value = 'douyin'
  selectedSelfMediaAccountId.value = null
  selectedCoverMaterialId.value = null
  if (!douyinCapability.value?.enabled) {
    ElMessage.info(douyinCapability.value?.disabledReason || '抖音图文暂未开启')
    return
  }
  if (!douyinActive.value) {
    if (!mediaDistributeBrandId.value) {
      ElMessage.error('当前文章未绑定品牌，无法授权抖音')
      return
    }
    const { data } = await getDouyinAuthUrl({
      brandId: mediaDistributeBrandId.value,
      redirectArticleId: mediaDistributeArticleId.value || undefined,
    })
    window.location.href = data.data.authUrl
    return
  }
  const account = douyinAccounts.value.find((item) => item.status === 'active')
  if (account) {
    startDouyinImageText(account)
  }
}

function startDouyinImageText(account: SelfMediaAccount) {
  selectedMediaPlatform.value = 'douyin'
  selectedSelfMediaAccountId.value = account.id
  ensureSelectedImageFolder()
}

function isSemiAutoPlatform(platform: MediaPlatform): platform is SemiAutoPlatform {
  return platform === 'toutiao' || platform === 'zhihu'
}

function semiAutoStatusLabel(accounts: SelfMediaAccount[]) {
  if (!accounts.length) return '未配置'
  if (!accounts.some((account) => account.status === 'active')) return '不可用'
  return accounts.some(hasActiveCookieCredential) ? '可自动填表' : '待登录捕获'
}

function semiAutoStatusTagType(accounts: SelfMediaAccount[]): 'success' | 'warning' | 'info' {
  if (!accounts.length) return 'info'
  return accounts.some((account) => account.status === 'active' && hasActiveCookieCredential(account)) ? 'success' : 'warning'
}

function hasActiveCookieCredential(account: SelfMediaAccount) {
  return (account as SelfMediaAccountWithCredential).cookieCredentialStatus === 'active'
}

function semiAutoCredentialLabel(account: SelfMediaAccount) {
  const credential = account as SelfMediaAccountWithCredential
  if (credential.cookieCredentialStatus === 'active') {
    return credential.cookieCredentialVersion ? `凭证 v${credential.cookieCredentialVersion}` : '凭证已捕获'
  }
  return '未捕获凭证'
}

function semiAutoCredentialTagType(account: SelfMediaAccount): 'success' | 'warning' | 'info' {
  if (hasActiveCookieCredential(account)) return 'success'
  return account.status === 'active' ? 'warning' : 'info'
}

function semiAutoAccountActionLoading(account: SelfMediaAccount) {
  if (hasActiveCookieCredential(account)) {
    return selfMediaSubmitting.value && selectedSelfMediaAccountId.value === account.id
  }
  return semiAutoCookieCaptureLoadingAccountId.value === account.id || extensionBindCodeLoadingAccountId.value === account.id
}

function handleSemiAutoPlatformClick(platform: SemiAutoPlatform) {
  selectedMediaPlatform.value = platform
  selectedSelfMediaAccountId.value = null
  selectedCoverMaterialId.value = null
  selectedDouyinImageMaterialIds.value = []
  const accounts = platform === 'toutiao' ? toutiaoAccounts.value : zhihuAccounts.value
  if (!accounts.length) {
    ElMessage.info(`当前品牌暂无${platform === 'toutiao' ? '头条' : '知乎'}账号`)
    return
  }
  const readyAccount = accounts.find((account) => account.status === 'active' && hasActiveCookieCredential(account))
  if (readyAccount) {
    void submitSemiAutoExtensionTask(readyAccount)
    return
  }
  const loginAccount = accounts.find((account) => account.status === 'active')
  if (loginAccount) {
    void startSemiAutoCookieCapture(loginAccount)
  }
}

function handleFolderScopeChange() {
  ensureSelectedImageFolder()
  selectedCoverMaterialId.value = selectedMediaPlatform.value === 'wechat_mp' ? imageMaterials.value[0]?.id || null : null
  selectedDouyinImageMaterialIds.value = selectedDouyinImageMaterialIds.value.filter((id) => douyinImageMaterials.value.some((item) => item.id === id))
}

function selectImageFolder(folderId: number) {
  selectedImageFolderId.value = folderId
  selectedCoverMaterialId.value = selectedMediaPlatform.value === 'wechat_mp' ? imageMaterials.value[0]?.id || null : null
  selectedDouyinImageMaterialIds.value = selectedDouyinImageMaterialIds.value.filter((id) => douyinImageMaterials.value.some((item) => item.id === id))
}

function ensureSelectedImageFolder() {
  const folders = displayImageFolders.value
  if (!folders.length) {
    selectedImageFolderId.value = null
    return
  }
  if (!folders.some((folder) => folder.id === selectedImageFolderId.value)) {
    selectedImageFolderId.value = folders[0].id
  }
}

function materialThumbUrl(material: BrandMaterial) {
  return materialThumbUrls.value[material.id] || material.fileUrl
}

async function loadMaterialThumbs() {
  const brandId = mediaDistributeBrandId.value
  if (!brandId) {
    cleanupMaterialThumbs()
    return
  }
  cleanupMaterialThumbs()
  const seen = new Set<number>()
  const targets = brandImageFolders.value
    .flatMap((folder) => folder.materials || [])
    .filter((material) => {
      if (!isImageFileType(material.fileType) || seen.has(material.id)) return false
      seen.add(material.id)
      return true
    })
  const concurrency = Math.min(6, targets.length)
  let cursor = 0

  const worker = async () => {
    while (cursor < targets.length) {
      const material = targets[cursor++]
      try {
        const { data: blob } = await getBrandMaterialStream(brandId, material.id, false)
        const url = URL.createObjectURL(blob)
        materialThumbObjectUrls.value.push(url)
        materialThumbUrls.value = { ...materialThumbUrls.value, [material.id]: url }
      } catch {
        materialThumbUrls.value = { ...materialThumbUrls.value, [material.id]: null }
      }
    }
  }

  await Promise.all(Array.from({ length: concurrency }, () => worker()))
}

function cleanupMaterialThumbs() {
  materialThumbObjectUrls.value.forEach((url) => URL.revokeObjectURL(url))
  materialThumbObjectUrls.value = []
  materialThumbUrls.value = {}
}

function isImageFileType(fileType?: string | null) {
  return ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes((fileType || '').toLowerCase())
}

function toggleDouyinImage(materialId: number) {
  const index = selectedDouyinImageMaterialIds.value.indexOf(materialId)
  if (index >= 0) {
    selectedDouyinImageMaterialIds.value.splice(index, 1)
    return
  }
  if (selectedDouyinImageMaterialIds.value.length >= 30) {
    ElMessage.warning('抖音图文最多选择 30 张图片')
    return
  }
  selectedDouyinImageMaterialIds.value.push(materialId)
}

function moveDouyinImage(index: number, offset: number) {
  const nextIndex = index + offset
  if (nextIndex < 0 || nextIndex >= selectedDouyinImageMaterialIds.value.length) {
    return
  }
  const next = [...selectedDouyinImageMaterialIds.value]
  const [item] = next.splice(index, 1)
  next.splice(nextIndex, 0, item)
  selectedDouyinImageMaterialIds.value = next
}

async function submitWechatDraft() {
  if (!mediaDistributeArticleId.value || !selectedSelfMediaAccountId.value || !selectedCoverMaterialId.value) {
    ElMessage.warning('请选择公众号和封面图片')
    return
  }
  selfMediaSubmitting.value = true
  try {
    const result = await distributeContentArticleToSelfMediaAccount(mediaDistributeArticleId.value, {
      selfMediaAccountId: selectedSelfMediaAccountId.value,
      coverMaterialId: selectedCoverMaterialId.value,
      requestId: createRequestId(),
    })
    const task = result.data.data
    if (task.status === 'submitted') {
      mediaDistributeVisible.value = false
      ElMessage.success('已保存至公众号草稿箱')
      await load()
      return
    }
    ElMessage.error(task.errorMessage || '保存公众号草稿失败')
  } finally {
    selfMediaSubmitting.value = false
  }
}

async function submitDouyinImageText() {
  if (!mediaDistributeArticleId.value || !selectedSelfMediaAccountId.value) {
    ElMessage.warning('请选择抖音账号')
    return
  }
  if (!selectedDouyinImageMaterialIds.value.length) {
    ElMessage.warning('请选择至少 1 张抖音图文图片')
    return
  }
  if (douyinText.value.length > 1000) {
    ElMessage.warning('抖音文案不能超过 1000 字')
    return
  }
  selfMediaSubmitting.value = true
  try {
    const result = await distributeContentArticleToSelfMediaAccount(mediaDistributeArticleId.value, {
      selfMediaAccountId: selectedSelfMediaAccountId.value,
      imageMaterialIds: selectedDouyinImageMaterialIds.value,
      platformOptions: {
        text: douyinText.value.trim() || undefined,
      },
      requestId: createRequestId('douyin'),
    })
    const task = result.data.data
    if (task.status === 'submitted') {
      ElMessage.success('抖音图文提交成功')
      await refreshDistributionHistory()
      await load()
      return
    }
    ElMessage.error(task.errorMessage || '抖音图文提交失败')
  } finally {
    selfMediaSubmitting.value = false
  }
}

async function submitSemiAutoExtensionTask(account: SelfMediaAccount) {
  if (!mediaDistributeArticleId.value) {
    ElMessage.warning('请选择文章')
    return
  }
  if (!hasActiveCookieCredential(account)) {
    await startSemiAutoCookieCapture(account)
    return
  }
  selectedSelfMediaAccountId.value = account.id
  selfMediaSubmitting.value = true
  try {
    await startSemiAutoExtensionTask(mediaDistributeArticleId.value, account.id, account.platform)
  } finally {
    selfMediaSubmitting.value = false
  }
}

async function startSemiAutoCookieCapture(account: SelfMediaAccount) {
  if (!mediaDistributeBrandId.value) {
    ElMessage.error('当前文章未绑定品牌，无法捕获自媒体登录状态')
    return
  }
  if (!await ensureExtensionBridgeReady()) {
    await generateExtensionBindCodeForCapture(account)
    return
  }
  selectedSelfMediaAccountId.value = account.id
  pendingCookieCaptureAccountId.value = account.id
  semiAutoCookieCaptureLoadingAccountId.value = account.id
  try {
    const bridgeResult = await startExtensionCookieCapture({
      type: 'GEO_START_COOKIE_CAPTURE',
      requestId: createRequestId(`capture_${account.platform}`),
      brandId: mediaDistributeBrandId.value,
      accountId: account.id,
      platform: account.platform,
      accountName: account.accountName,
    })
    if (bridgeResult.type === 'GEO_FILL_ERROR') {
      ElMessage.warning(bridgeResult.payload?.message || '暂时无法打开登录页，请确认扩展已安装并绑定')
      return
    }
    if (bridgeResult.payload?.status === 'captured') {
      ElMessage.success(bridgeResult.payload.message || '已自动捕获登录凭证')
      await refreshSelfMediaAccountsAfterCookieCapture(account.id, true)
      return
    }
    ElMessage.info(bridgeResult.payload?.message || '已打开登录页，登录成功后将自动捕获凭证')
    startCookieCaptureStatusPolling(account.id)
  } catch (error) {
    const message = error instanceof Error ? error.message : '扩展桥接失败'
    ElMessage.warning(`${message}；请确认扩展已安装并启用。`)
  } finally {
    semiAutoCookieCaptureLoadingAccountId.value = null
  }
}

function startCookieCaptureStatusPolling(accountId: number) {
  stopCookieCaptureStatusPolling()
  pendingCookieCaptureAccountId.value = accountId
  let attempts = 0
  const poll = async () => {
    attempts += 1
    try {
      if (await refreshSelfMediaAccountsAfterCookieCapture(accountId, true)) {
        return
      }
    } catch {
      // The next poll will retry while the operator is completing platform login.
    }
    if (attempts >= 60 || !mediaDistributeVisible.value) {
      stopCookieCaptureStatusPolling()
      return
    }
    cookieCapturePollTimer = window.setTimeout(poll, 3_000)
  }
  cookieCapturePollTimer = window.setTimeout(poll, 3_000)
}

function stopCookieCaptureStatusPolling() {
  if (cookieCapturePollTimer !== null) {
    window.clearTimeout(cookieCapturePollTimer)
    cookieCapturePollTimer = null
  }
}

async function refreshSelfMediaAccountsAfterCookieCapture(accountId: number, notify: boolean) {
  await refreshSelfMediaAccounts()
  const account = [...toutiaoAccounts.value, ...zhihuAccounts.value].find(item => item.id === accountId)
  if (!account || !hasActiveCookieCredential(account)) return false
  selectedSelfMediaAccountId.value = account.id
  pendingCookieCaptureAccountId.value = null
  semiAutoCookieCaptureLoadingAccountId.value = null
  stopCookieCaptureStatusPolling()
  if (notify) {
    ElMessage.success('自媒体登录状态已刷新，可以再次点击平台进行文章分发')
  }
  return true
}

async function refreshPendingCookieCaptureStatus() {
  if (!mediaDistributeVisible.value || !pendingCookieCaptureAccountId.value) return
  try {
    await refreshSelfMediaAccountsAfterCookieCapture(pendingCookieCaptureAccountId.value, true)
  } catch {
    // Keep the polling path alive; transient refresh failures should not interrupt login capture.
  }
}

function handleWindowFocusForCookieCapture() {
  void refreshPendingCookieCaptureStatus()
  scheduleDistributionStatusRefresh()
}

function handleVisibilityChangeForCookieCapture() {
  if (document.visibilityState === 'visible') {
    void refreshPendingCookieCaptureStatus()
    scheduleDistributionStatusRefresh()
  }
}

function scheduleDistributionStatusRefresh() {
  if (!rows.value.some(row => row.status === 'distributing')) return
  window.setTimeout(() => {
    void load()
  }, 800)
}

async function startSemiAutoExtensionTask(articleId: number, accountId: number, platform: string) {
  if (!await ensureExtensionBridgeReady()) {
    if (mediaDistributeBrandId.value) {
      await generateExtensionBindCode(mediaDistributeBrandId.value, accountId)
    }
    return
  }
  const requestId = createRequestId(platform)
  const result = await distributeContentArticleToSelfMediaAccount(articleId, {
    selfMediaAccountId: accountId,
    requestId,
  })
  const task = result.data.data
  if (task.status === 'token_issued') {
    try {
      const bridgeResult = await startExtensionFill({
        type: 'GEO_START_FILL',
        requestId,
        taskId: task.id,
        articleId,
        accountId,
        platform,
      })
      if (bridgeResult.type === 'GEO_FILL_ERROR') {
        ElMessage.warning(bridgeResult.payload?.message || '扩展未能自动打开发布页，请打开浏览器扩展继续处理')
      } else {
        ElMessage.success(bridgeResult.payload?.message || '已打开发布页并完成填表，请人工确认后发布')
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : '扩展桥接失败'
      ElMessage.warning(`${message}；发布请求已生成，可打开浏览器扩展继续处理。`)
    }
    if (mediaDistributeArticleId.value === articleId) {
      await refreshDistributionHistory()
    }
    await load()
    return
  }
  if (['filling', 'filled', 'published'].includes(task.status)) {
    ElMessage.info('该发布页已在处理中，请在浏览器扩展或已打开的平台页面继续处理')
    if (mediaDistributeArticleId.value === articleId) {
      await refreshDistributionHistory()
    }
    await load()
    return
  }
  ElMessage.error(task.errorMessage || '打开发布页失败')
}

async function ensureExtensionBridgeReady() {
  if (await refreshExtensionBridgeStatus(false)) {
    return true
  }
  ElMessage.warning(extensionBridgeState.message)
  return false
}

async function refreshExtensionBridgeStatus(showMessage = false) {
  extensionBridgeChecking.value = true
  extensionBridgeState.status = 'checking'
  extensionBridgeState.message = '正在检测浏览器扩展状态'
  try {
    const bridgeResult = await pingExtensionBridge()
    if (bridgeResult.type === 'GEO_PONG' && bridgeResult.payload?.bound) {
      extensionBridgeState.status = 'bound'
      extensionBridgeState.extensionVersion = bridgeResult.payload.extensionVersion || ''
      extensionBridgeState.message = bridgeResult.payload.extensionVersion
        ? `扩展已安装并绑定，版本 ${bridgeResult.payload.extensionVersion}`
        : '扩展已安装并绑定'
      if (showMessage) ElMessage.success(extensionBridgeState.message)
      return true
    }
    if (bridgeResult.type === 'GEO_PONG') {
      extensionBridgeState.status = 'unbound'
      extensionBridgeState.extensionVersion = bridgeResult.payload?.extensionVersion || ''
      extensionBridgeState.message = '已检测到浏览器扩展，但扩展尚未绑定，请先打开扩展完成绑定。'
      if (showMessage) ElMessage.warning(extensionBridgeState.message)
      return false
    }
    extensionBridgeState.status = 'error'
    extensionBridgeState.message = bridgeResult.payload?.message || '扩展当前不可用，请打开浏览器扩展检查状态。'
    if (showMessage) ElMessage.warning(extensionBridgeState.message)
    return false
  } catch (error) {
    const message = error instanceof Error ? error.message : '未检测到浏览器扩展'
    extensionBridgeState.status = 'missing'
    extensionBridgeState.message = `${message}；请先安装并启用 GEO 浏览器扩展。`
    if (showMessage) ElMessage.warning(extensionBridgeState.message)
    return false
  } finally {
    extensionBridgeChecking.value = false
  }
}

async function generateExtensionBindCodeForCapture(account: SelfMediaAccount) {
  if (!mediaDistributeBrandId.value) {
    ElMessage.error('当前文章未绑定品牌，无法生成扩展绑定码')
    return
  }
  selectedSelfMediaAccountId.value = account.id
  await generateExtensionBindCode(mediaDistributeBrandId.value, account.id)
}

async function generateExtensionBindCode(brandId: number, accountId: number) {
  extensionBindCodeLoadingAccountId.value = accountId
  try {
    const { data } = await createExtensionBindCode(brandId)
    extensionBindCode.value = data.data
    ElMessage.success('扩展绑定码已生成，请打开浏览器扩展继续绑定并捕获凭证')
    await ElMessageBox.alert(
      [
        `扩展绑定码：${data.data.code}`,
        `有效期：${formatTtlSeconds(data.data.expiresInSeconds)}`,
        '',
        '请打开 GEO 浏览器扩展，输入该绑定码完成绑定。绑定后，在同一浏览器登录目标平台，再回到扩展里捕获凭证。',
      ].join('\n'),
      '扩展绑定码',
      {
        confirmButtonText: '知道了',
      },
    )
  } finally {
    extensionBindCodeLoadingAccountId.value = null
  }
}

function formatTtlSeconds(seconds: number) {
  if (seconds >= 60 && seconds % 60 === 0) {
    return `${seconds / 60} 分钟`
  }
  return `${seconds} 秒`
}

async function copyExtensionBindCode() {
  if (!extensionBindCode.value) return
  await copyText(extensionBindCode.value.code)
  ElMessage.success('绑定码已复制')
}

async function copyText(text: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return
  }
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', 'readonly')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}

async function refreshDistributionHistory() {
  if (!mediaDistributeArticleId.value) return
  const { data } = await getArticleDistribution(mediaDistributeArticleId.value)
  distributionAttempts.value = data.data.attempts || []
}

async function refreshReviewStatus(task: DistributionTask) {
  refreshingReviewTaskId.value = task.id
  try {
    await refreshDistributionTaskReviewStatus(task.id)
    await refreshDistributionHistory()
    ElMessage.success('审核状态已刷新')
  } finally {
    refreshingReviewTaskId.value = null
  }
}

async function checkWechatAccount(id: number) {
  checkingSelfMediaAccountId.value = id
  try {
    const { data } = await checkSelfMediaAccountAuth(id)
    const next = data.data
    wechatAccounts.value = wechatAccounts.value.map((account) => account.id === id ? next : account)
    ElMessage.success(next.status === 'active' ? '登录状态有效' : '登录状态已更新')
  } finally {
    checkingSelfMediaAccountId.value = null
  }
}

function selfMediaAccountStatusLabel(account: SelfMediaAccount) {
  if (isSemiAutoPlatform(account.platform as MediaPlatform)) {
    return account.status === 'active' ? '启用' : '停用'
  }
  const map: Record<string, string> = {
    active: '已登录',
    expired: '已过期',
    revoked: '已取消',
    disabled: '不可用',
  }
  return map[account.status] || account.status
}

function selfMediaAccountStatusTag(account: SelfMediaAccount): 'success' | 'warning' | 'danger' | 'info' {
  if (isSemiAutoPlatform(account.platform as MediaPlatform)) {
    return account.status === 'active' ? 'info' : 'danger'
  }
  if (account.status === 'active') return 'success'
  if (account.status === 'expired') return 'warning'
  if (account.status === 'revoked' || account.status === 'disabled') return 'danger'
  return 'info'
}

function createRequestId(prefix = 'self_media') {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${prefix}_${Date.now()}_${Math.random().toString(16).slice(2)}`
}

function money(value: number | string | null | undefined) {
  const n = Number(value || 0)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

function parseIndustryTags(raw?: string | string[] | null) {
  if (Array.isArray(raw)) return raw
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function industrySiteInitial(site: PublishSite) {
  const text = (site.siteName || site.domain || '站').trim()
  return Array.from(text)[0] || '站'
}

function industrySiteTagText(site: PublishSite) {
  const tags = parseIndustryTags(site.industryTags)
  return tags.length ? tags.join(' / ') : '未分类'
}

function entranceLevelLabel(value?: number | null) {
  const map: Record<number, string> = {
    0: '无入口',
    1: '首页入口',
    2: '频道入口',
    3: '上级入口',
  }
  return value == null ? '-' : (map[value] || String(value))
}

function newsResourceLabel(value?: number | null) {
  const map: Record<number, string> = {
    0: '非新闻源',
    1: '百度新闻源',
    2: '头条新闻源',
    3: '百度&头条',
  }
  return value == null ? '-' : (map[value] || String(value))
}

function includeConditionLabel(value?: number | null) {
  const map: Record<number, string> = {
    0: '不包收录',
    1: '百度包收录',
    2: '头条包收录',
  }
  return value == null ? '-' : (map[value] || String(value))
}

function authorityMediaInitial(resource: AuthorityMediaResource) {
  const text = (resource.name || resource.industry || '媒').trim()
  return Array.from(text)[0] || '媒'
}

function authorityWeightText(resource: AuthorityMediaResource) {
  return `PC ${resource.pcWeight ?? '-'} / M ${resource.mWeight ?? '-'}`
}

function authorityRowClass({ row }: { row: AuthorityMediaResource }) {
  return row.id === authorityForm.resourceId ? 'is-selected-authority' : ''
}

function openExternalLink(url?: string | null) {
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function distributeToAgentSite(row: ArticleDraft) {
  submitting.value = true
  try {
    const detailRes = await getContentArticleDetail(row.id)
    const brandId = detailRes.data.data.project?.brandId
    if (!brandId) {
      ElMessage.error('当前文章未绑定品牌，无法分发到 Agent 官网')
      return
    }
    const brandRes = await getBrandDetail(brandId)
    const brand = brandRes.data.data
    const result = await distributeContentArticleToAgentSite(row.id, brandId)
    const task = result.data.data
    if (task.status === 'submitted') {
      ElMessage.success(`已分发到 ${brand.brandName || '品牌'} Agent 官网`)
    } else {
      ElMessage.error(task.errorMessage || 'Agent 官网分发失败')
    }
    await load()
  } finally {
    submitting.value = false
  }
}

async function submitReview() {
  if (!currentArticleId.value) return
  if ((reviewForm.action === 'reject' || reviewForm.action === 'return_for_revision') && !reviewForm.comment.trim()) {
    ElMessage.warning('驳回或退回修改时，审核意见不能为空')
    return
  }
  submitting.value = true
  try {
    await reviewContentArticle(currentArticleId.value, {
      action: reviewForm.action,
      comment: reviewForm.comment || undefined,
      riskOverride: reviewForm.riskOverride,
    })
    reviewVisible.value = false
    ElMessage.success('审核提交成功')
    await load()
  } finally {
    submitting.value = false
  }
}

async function submitRevision() {
  if (!currentArticleId.value) return
  if (!revisionForm.contentMarkdown.trim()) {
    ElMessage.warning('正文不能为空')
    return
  }
  submitting.value = true
  try {
    await saveContentArticleRevision(currentArticleId.value, {
      title: revisionForm.title || undefined,
      contentMarkdown: revisionForm.contentMarkdown,
      note: revisionForm.note || undefined,
    })
    revisionVisible.value = false
    ElMessage.success('修订保存成功')
    await load()
  } finally {
    submitting.value = false
  }
}

async function submitResubmit() {
  if (!currentArticleId.value) return
  submitting.value = true
  try {
    await resubmitContentArticle(currentArticleId.value, {
      comment: resubmitForm.comment || undefined,
    })
    resubmitVisible.value = false
    ElMessage.success('已重新提交')
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  handleWechatAuthResult()
  handleDouyinAuthResult()
  await handleManualCreateResult()
  await load()
  await openCreatedArticleDetail()
})

watch(mediaDistributeVisible, (visible) => {
  if (!visible) {
    stopCookieCaptureStatusPolling()
    pendingCookieCaptureAccountId.value = null
    semiAutoCookieCaptureLoadingAccountId.value = null
    cleanupMaterialThumbs()
  }
})

window.addEventListener('focus', handleWindowFocusForCookieCapture)
document.addEventListener('visibilitychange', handleVisibilityChangeForCookieCapture)

onBeforeUnmount(() => {
  window.removeEventListener('focus', handleWindowFocusForCookieCapture)
  document.removeEventListener('visibilitychange', handleVisibilityChangeForCookieCapture)
  stopCookieCaptureStatusPolling()
  cleanupMaterialThumbs()
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

function reviewStatusLabel(status?: string | null) {
  const map: Record<string, string> = {
    under_review: '审核中',
    published: '已通过',
    rejected: '已拒审',
    offline: '已下线',
    unknown: '未知',
  }
  return status ? map[status] || status : '-'
}

function reviewStatusTag(status?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'published') return 'success'
  if (status === 'rejected' || status === 'offline') return 'danger'
  if (status === 'under_review') return 'warning'
  return 'info'
}
</script>

<style scoped>
.content-execution-page {
  padding: 8px 0;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.toolbar-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.toolbar-divider {
  width: 1px;
  height: 24px;
  background: #e2e8f0;
}

.toolbar-minor-action {
  color: #64748b;
}

.toolbar-minor-action:hover {
  color: #334155;
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

.detail-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.content-detail-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 18px 22px;
  border-bottom: 1px solid #e2e8f0;
  background: linear-gradient(135deg, #f8fbff, #eff6ff 58%, #ecfdf5);
}

.content-detail-drawer :deep(.el-drawer__title) {
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.content-detail-drawer :deep(.el-drawer__body) {
  padding: 18px 22px 24px;
  background: #f7fbff;
}

.detail-summary-panel,
.detail-section-panel {
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.detail-summary-panel {
  padding: 16px;
  background:
    radial-gradient(circle at top right, rgba(37, 99, 235, 0.08), transparent 30%),
    linear-gradient(135deg, #ffffff, #f8fbff);
}

.detail-summary-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.detail-summary-head h3 {
  margin: 5px 0 0;
  color: #0f172a;
  font-size: 20px;
  font-weight: 800;
  line-height: 1.35;
}

.detail-kicker {
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-title {
  margin: 0;
  padding: 14px 16px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.detail-section-panel > .el-table {
  border-top: 1px solid #e2e8f0;
}

.detail-preview-panel {
  padding-bottom: 16px;
}

.detail-preview-panel .detail-header {
  padding-right: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.markdown-preview {
  min-height: 360px;
  margin: 16px;
  padding: 22px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background:
    linear-gradient(180deg, #ffffff 0%, #ffffff 74%, #f8fafc 100%);
  overflow: auto;
  line-height: 1.75;
  color: var(--el-text-color-primary);
}

.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3),
.markdown-preview :deep(h4) {
  margin: 1.1em 0 0.6em;
  font-weight: 700;
  line-height: 1.35;
}

.markdown-preview :deep(p),
.markdown-preview :deep(ul),
.markdown-preview :deep(ol),
.markdown-preview :deep(blockquote) {
  margin: 0 0 0.9em;
}

.markdown-preview :deep(ul),
.markdown-preview :deep(ol) {
  padding-left: 1.4em;
}

.markdown-preview :deep(code) {
  padding: 0.15em 0.4em;
  border-radius: 4px;
  background: #f5f7fa;
  font-size: 0.92em;
}

.markdown-preview :deep(pre) {
  padding: 12px 14px;
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  overflow: auto;
}

.markdown-preview :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.markdown-preview :deep(blockquote) {
  margin-left: 0;
  padding-left: 12px;
  border-left: 4px solid #cbd5e1;
  color: #475569;
}

.markdown-preview :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 14px auto;
  border-radius: 6px;
}

.markdown-preview :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 1em;
}

.markdown-preview :deep(th),
.markdown-preview :deep(td) {
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  text-align: left;
}

.editor-wrap {
  width: 100%;
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #f8fbff;
}

.editor-header {
  margin-bottom: 8px;
}

.editor-title {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.editor-preview {
  min-height: 360px;
  margin: 0;
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

.media-distribute-dialog :deep(.el-dialog) {
  overflow: hidden;
  border-radius: 18px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.99), rgba(248, 251, 255, 0.98));
  box-shadow: 0 28px 72px rgba(15, 23, 42, 0.18);
}

.media-distribute-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 20px 24px 14px;
  border-bottom: 1px solid #e2e8f0;
  background:
    linear-gradient(135deg, #ffffff, #eff6ff 62%, #ecfdf5);
}

.media-distribute-dialog :deep(.el-dialog__title) {
  color: #0f172a;
  font-size: 20px;
  font-weight: 800;
}

.media-distribute-dialog :deep(.el-dialog__body) {
  padding: 18px 20px 20px;
}

.media-distribute-dialog :deep(.el-dialog__footer) {
  padding: 14px 20px 18px;
  border-top: 1px solid #e2e8f0;
  background: #f8fafc;
}

.media-distribute {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.media-capability-alert {
  border: 1px solid #fed7aa;
  border-radius: 12px;
  background: #fff7ed;
}

.media-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.media-platform {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 128px;
  padding: 16px 12px;
  overflow: hidden;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  background:
    linear-gradient(135deg, #ffffff 0%, #ffffff 68%, #f8fbff 100%);
  color: #0f172a;
  cursor: pointer;
  flex-direction: column;
  gap: 9px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.media-platform::after {
  content: "";
  position: absolute;
  right: -34px;
  bottom: -42px;
  width: 110px;
  height: 110px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.06);
  pointer-events: none;
}

.media-platform:hover {
  transform: translateY(-2px);
  border-color: #93c5fd;
  box-shadow: 0 18px 40px rgba(37, 99, 235, 0.1);
}

.media-platform.active {
  border-color: #22c55e;
  background:
    linear-gradient(135deg, #ffffff 0%, #f0fdf4 100%);
  box-shadow:
    0 18px 42px rgba(34, 197, 94, 0.13),
    inset 0 0 0 1px rgba(34, 197, 94, 0.28);
}

.media-platform.disabled {
  cursor: not-allowed;
  background:
    linear-gradient(135deg, #ffffff, #f8fafc);
  color: #94a3b8;
  opacity: 0.78;
}

.wechat-mark,
.douyin-mark,
.toutiao-mark,
.zhihu-mark {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  color: #fff;
  font-size: 23px;
  font-weight: 800;
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.12);
}

.wechat-mark {
  background: linear-gradient(135deg, #16a34a, #22c55e);
}

.douyin-mark {
  background: linear-gradient(135deg, #020617, #1e293b);
}

.toutiao-mark {
  background: linear-gradient(135deg, #dc2626, #ef4444);
}

.zhihu-mark {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
}

.media-name {
  position: relative;
  z-index: 1;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.self-media-account-list {
  overflow: hidden;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
}

.self-media-account-row {
  display: grid;
  align-items: center;
  min-height: 64px;
  padding: 12px 14px;
  border-bottom: 1px solid #e2e8f0;
  grid-template-columns: minmax(0, 1fr) auto auto auto;
  gap: 12px;
}

.self-media-account-row:last-child {
  border-bottom: 0;
}

.self-media-account-row:hover {
  background: #f8fbff;
}

.self-media-account-title {
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.self-media-account-meta {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.extension-bind-guide {
  margin-top: 12px;
}

.extension-bind-content {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  line-height: 1.7;
}

.bind-code {
  font-family: "JetBrains Mono", Consolas, monospace;
  letter-spacing: 0;
}

.cover-picker {
  border: 1px solid #dbeafe;
  border-radius: 14px;
  padding: 14px;
  background:
    linear-gradient(180deg, #ffffff, #f8fbff);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.cover-picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.folder-toolbar {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 10px;
}

.folder-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.folder-item {
  min-height: 34px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 6px 10px;
  background: #fff;
  color: var(--el-text-color-primary);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.folder-item.selected {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}

.cover-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
  gap: 10px;
  max-height: 260px;
  overflow: auto;
}

.cover-item {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 6px;
  background: #fff;
  cursor: pointer;
  text-align: left;
}

.cover-item.selected {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}

.cover-item img {
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  border-radius: 4px;
  background: #f2f3f5;
  display: block;
}

.cover-item span {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.douyin-selected-list {
  margin-top: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}

.douyin-selected-row {
  min-height: 44px;
  padding: 8px 10px;
  display: grid;
  grid-template-columns: 32px 1fr auto auto;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid #ebeef5;
}

.douyin-selected-row:last-child {
  border-bottom: 0;
}

.douyin-selected-index {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #f2f3f5;
  color: var(--el-text-color-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.douyin-selected-name {
  min-width: 0;
  font-size: 13px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.douyin-text-editor,
.distribution-history {
  margin-top: 12px;
}

.distribution-history {
  padding: 14px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background:
    linear-gradient(180deg, #ffffff, #f8fbff);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
}

.distribution-history-table {
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.distribution-history-table :deep(.el-table__header th) {
  border-right-color: transparent;
  background: #eff6ff;
  color: #334155;
  font-weight: 800;
}

.distribution-history-table :deep(.el-table__body td) {
  border-right-color: transparent;
  border-bottom-color: #edf2f7;
}

.distribution-history-table :deep(.el-table__row:hover > td) {
  background: #f8fbff !important;
}

.distribution-target-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.distribution-target-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, #2563eb, #06b6d4);
  color: #ffffff;
  font-weight: 800;
  flex-shrink: 0;
}

.distribution-target-main,
.distribution-feedback {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.distribution-target-title,
.distribution-feedback-main {
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.distribution-target-sub,
.distribution-feedback-muted {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.distribution-feedback-error {
  overflow: hidden;
  color: #ef4444;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.distribution-actions {
  grid-template-columns: repeat(auto-fit, minmax(44px, 1fr));
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
