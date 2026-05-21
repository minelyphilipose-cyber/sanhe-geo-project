<template>
  <div class="presale-report-edit">
    <div class="edit-header">
      <div>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/admin/presale/report' }">AI可见度诊断报告</el-breadcrumb-item>
          <el-breadcrumb-item>编辑内容</el-breadcrumb-item>
        </el-breadcrumb>
        <h2>{{ detail?.brandName || 'AI可见度诊断报告' }} · v{{ detail?.version.versionNo || '-' }}</h2>
      </div>
      <div class="header-actions">
        <el-tag v-if="isDirty" type="warning" effect="plain">有未保存修改</el-tag>
        <el-button @click="goDetail">返回详情</el-button>
        <el-tooltip :disabled="saveDisabledReason === ''" :content="saveDisabledReason" placement="bottom">
          <span>
            <el-button
              type="primary"
              :disabled="saveDisabledReason !== ''"
              :loading="saving"
              @click="handleSave(false)"
            >
              保存
            </el-button>
          </span>
        </el-tooltip>
      </div>
    </div>

    <div v-if="loading" class="state-panel">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>正在加载报告...</span>
    </div>

    <el-alert v-else-if="error" type="error" :closable="false" show-icon class="edit-alert">
      <template #title>{{ error }}</template>
      <template #default>
        <el-button size="small" @click="load">重试</el-button>
        <el-button size="small" @click="goDetail">返回详情</el-button>
      </template>
    </el-alert>

    <el-alert v-else-if="lockedReason" type="warning" :closable="false" show-icon class="edit-alert">
      <template #title>{{ lockedReason }}</template>
      <template #default>
        <el-button v-if="canDerive" type="primary" size="small" :loading="deriving" @click="handleDerive">
          派生新版本并编辑
        </el-button>
        <el-button size="small" @click="goDetail">返回详情</el-button>
      </template>
    </el-alert>

    <div v-else-if="draft && raw && computedSnap" class="edit-layout">
      <aside class="edit-nav">
        <div class="edit-nav-title">编辑目录</div>
        <a href="#basic">基本文案</a>
        <a href="#market">AI 搜索新战场</a>
        <a href="#summary">执行摘要</a>
        <a href="#takeaways">关键结论</a>
        <a href="#findings">优化建议</a>
        <a href="#competitors">竞品场景</a>
        <a href="#phases">ROI 路线图</a>
        <a href="#disclaimer">免责声明</a>
      </aside>

      <main class="edit-main">
        <el-card id="basic" shadow="never" class="edit-section">
          <template #header>基本文案</template>
          <EditableText
            label="报告标题"
            :model-value="draft.report_title"
            :maxlength="40"
            :default-text="defaultTitle"
            @update:model-value="draft.report_title = $event"
            @restore="draft.report_title = null"
          />
          <EditableText
            label="报告副标题"
            :model-value="draft.report_subtitle"
            :maxlength="80"
            :default-text="defaultSubtitle"
            @update:model-value="draft.report_subtitle = $event"
            @restore="draft.report_subtitle = null"
          />
        </el-card>

        <el-card id="market" shadow="never" class="edit-section">
          <template #header>AI 搜索新战场</template>
          <el-collapse v-model="marketCollapseActive" class="market-collapse">
            <el-collapse-item name="market-topbar" title="顶部条">
              <EditableText
                label="顶部章节标题"
                :model-value="draft.market_battleground.topbar_title"
                :maxlength="fieldMax('market_battleground.topbar_title', 40)"
                :warn-length="fieldWarn('market_battleground.topbar_title')"
                @update:model-value="draft.market_battleground.topbar_title = $event"
              />
              <EditableText
                label="顶部右侧标识"
                :model-value="draft.market_battleground.topbar_right"
                :maxlength="fieldMax('market_battleground.topbar_right', 24)"
                :warn-length="fieldWarn('market_battleground.topbar_right')"
                @update:model-value="draft.market_battleground.topbar_right = $event"
              />
            </el-collapse-item>

            <el-collapse-item name="market-title" title="页面标题">
              <EditableText
                label="页面主标题"
                :model-value="draft.market_battleground.page_title"
                :maxlength="fieldMax('market_battleground.page_title', 34)"
                :warn-length="fieldWarn('market_battleground.page_title')"
                @update:model-value="draft.market_battleground.page_title = $event"
              />
              <EditableText
                label="英文副标题"
                :model-value="draft.market_battleground.page_kicker"
                :maxlength="fieldMax('market_battleground.page_kicker', 48)"
                :warn-length="fieldWarn('market_battleground.page_kicker')"
                @update:model-value="draft.market_battleground.page_kicker = $event"
              />
            </el-collapse-item>

            <el-collapse-item name="market-card" title="深色市场卡">
              <EditableText
                label="市场卡标签"
                :model-value="draft.market_battleground.market_card.label"
                :maxlength="fieldMax('market_battleground.market_card.label', 32)"
                :warn-length="fieldWarn('market_battleground.market_card.label')"
                @update:model-value="draft.market_battleground.market_card.label = $event"
              />
              <EditableText
                label="数据来源"
                :model-value="draft.market_battleground.market_card.source"
                :maxlength="fieldMax('market_battleground.market_card.source', 32)"
                :warn-length="fieldWarn('market_battleground.market_card.source')"
                @update:model-value="draft.market_battleground.market_card.source = $event"
              />
              <div v-for="(stat, idx) in draft.market_battleground.market_card.stats" :key="idx" class="list-editor-item">
                <div class="row-title"><strong>市场数据 {{ idx + 1 }}</strong></div>
                <EditableText
                  label="数值"
                  :model-value="stat.value"
                  :maxlength="fieldMax(`market_battleground.market_card.stats[${idx}].value`, 12)"
                  :warn-length="fieldWarn(`market_battleground.market_card.stats[${idx}].value`)"
                  @update:model-value="stat.value = $event"
                />
                <EditableText
                  label="单位"
                  :model-value="stat.unit"
                  :maxlength="fieldMax(`market_battleground.market_card.stats[${idx}].unit`, 8)"
                  :warn-length="fieldWarn(`market_battleground.market_card.stats[${idx}].unit`)"
                  @update:model-value="stat.unit = $event"
                />
                <EditableText
                  label="说明"
                  :model-value="stat.label"
                  :maxlength="fieldMax(`market_battleground.market_card.stats[${idx}].label`, 24)"
                  :warn-length="fieldWarn(`market_battleground.market_card.stats[${idx}].label`)"
                  @update:model-value="stat.label = $event"
                />
              </div>
              <EditableText
                label="平台列表标签"
                :model-value="draft.market_battleground.market_card.platform_label"
                :maxlength="fieldMax('market_battleground.market_card.platform_label', 16)"
                :warn-length="fieldWarn('market_battleground.market_card.platform_label')"
                @update:model-value="draft.market_battleground.market_card.platform_label = $event"
              />
              <div v-for="(platform, idx) in draft.market_battleground.market_card.platforms" :key="idx" class="list-editor-item">
                <div class="row-title"><strong>平台 {{ idx + 1 }}</strong></div>
                <EditableText
                  label="名称"
                  :model-value="platform.name"
                  :maxlength="fieldMax(`market_battleground.market_card.platforms[${idx}].name`, 12)"
                  :warn-length="fieldWarn(`market_battleground.market_card.platforms[${idx}].name`)"
                  @update:model-value="platform.name = $event"
                />
                <EditableText
                  label="数值"
                  :model-value="platform.value"
                  :maxlength="fieldMax(`market_battleground.market_card.platforms[${idx}].value`, 12)"
                  :warn-length="fieldWarn(`market_battleground.market_card.platforms[${idx}].value`)"
                  @update:model-value="platform.value = $event"
                />
              </div>
              <EditableText
                label="其他平台说明"
                :model-value="draft.market_battleground.market_card.platform_suffix"
                :maxlength="fieldMax('market_battleground.market_card.platform_suffix', 18)"
                :warn-length="fieldWarn('market_battleground.market_card.platform_suffix')"
                @update:model-value="draft.market_battleground.market_card.platform_suffix = $event"
              />
            </el-collapse-item>

            <el-collapse-item name="national-card" title="全国推导卡">
              <EditableText
                label="卡片标签"
                :model-value="draft.market_battleground.national_card.label"
                :maxlength="fieldMax('market_battleground.national_card.label', 24)"
                :warn-length="fieldWarn('market_battleground.national_card.label')"
                @update:model-value="draft.market_battleground.national_card.label = $event"
              />
              <div class="inline-row">
                <EditableText
                  label="大数字前缀"
                  :model-value="draft.market_battleground.national_card.value_prefix"
                  :maxlength="fieldMax('market_battleground.national_card.value_prefix', 6)"
                  :warn-length="fieldWarn('market_battleground.national_card.value_prefix')"
                  @update:model-value="draft.market_battleground.national_card.value_prefix = $event"
                />
                <EditableText
                  label="大数字"
                  :model-value="draft.market_battleground.national_card.value"
                  :maxlength="fieldMax('market_battleground.national_card.value', 12)"
                  :warn-length="fieldWarn('market_battleground.national_card.value')"
                  @update:model-value="draft.market_battleground.national_card.value = $event"
                />
                <EditableText
                  label="单位"
                  :model-value="draft.market_battleground.national_card.unit"
                  :maxlength="fieldMax('market_battleground.national_card.unit', 8)"
                  :warn-length="fieldWarn('market_battleground.national_card.unit')"
                  @update:model-value="draft.market_battleground.national_card.unit = $event"
                />
              </div>
              <EditableText
                label="大数字说明"
                :model-value="draft.market_battleground.national_card.subtitle"
                :maxlength="fieldMax('market_battleground.national_card.subtitle', 28)"
                :warn-length="fieldWarn('market_battleground.national_card.subtitle')"
                @update:model-value="draft.market_battleground.national_card.subtitle = $event"
              />
              <EditableText
                label="推导标题"
                :model-value="draft.market_battleground.national_card.calculation_label"
                :maxlength="fieldMax('market_battleground.national_card.calculation_label', 24)"
                :warn-length="fieldWarn('market_battleground.national_card.calculation_label')"
                @update:model-value="draft.market_battleground.national_card.calculation_label = $event"
              />
              <div v-for="(row, idx) in draft.market_battleground.national_card.rows" :key="idx" class="list-editor-item">
                <div class="row-title"><strong>推导行 {{ idx + 1 }}</strong></div>
                <EditableText
                  label="标签"
                  :model-value="row.label"
                  :maxlength="fieldMax(`market_battleground.national_card.rows[${idx}].label`, 18)"
                  :warn-length="fieldWarn(`market_battleground.national_card.rows[${idx}].label`)"
                  @update:model-value="row.label = $event"
                />
                <EditableText
                  label="数值"
                  :model-value="row.value"
                  :maxlength="fieldMax(`market_battleground.national_card.rows[${idx}].value`, 30)"
                  :warn-length="fieldWarn(`market_battleground.national_card.rows[${idx}].value`)"
                  @update:model-value="row.value = $event"
                />
              </div>
            </el-collapse-item>

            <el-collapse-item name="regional-card" title="区域推导卡">
              <EditableText
                label="过渡文案"
                :model-value="draft.market_battleground.bridge_text"
                :maxlength="fieldMax('market_battleground.bridge_text', 20)"
                :warn-length="fieldWarn('market_battleground.bridge_text')"
                @update:model-value="draft.market_battleground.bridge_text = $event"
              />
              <EditableText
                label="卡片标签"
                :model-value="draft.market_battleground.regional_card.label"
                :maxlength="fieldMax('market_battleground.regional_card.label', 24)"
                :warn-length="fieldWarn('market_battleground.regional_card.label')"
                @update:model-value="draft.market_battleground.regional_card.label = $event"
              />
              <div class="inline-row">
                <EditableText
                  label="大数字前缀"
                  :model-value="draft.market_battleground.regional_card.value_prefix"
                  :maxlength="fieldMax('market_battleground.regional_card.value_prefix', 6)"
                  :warn-length="fieldWarn('market_battleground.regional_card.value_prefix')"
                  @update:model-value="draft.market_battleground.regional_card.value_prefix = $event"
                />
                <EditableText
                  label="大数字"
                  :model-value="draft.market_battleground.regional_card.value"
                  :maxlength="fieldMax('market_battleground.regional_card.value', 12)"
                  :warn-length="fieldWarn('market_battleground.regional_card.value')"
                  @update:model-value="draft.market_battleground.regional_card.value = $event"
                />
                <EditableText
                  label="单位"
                  :model-value="draft.market_battleground.regional_card.unit"
                  :maxlength="fieldMax('market_battleground.regional_card.unit', 8)"
                  :warn-length="fieldWarn('market_battleground.regional_card.unit')"
                  @update:model-value="draft.market_battleground.regional_card.unit = $event"
                />
              </div>
              <EditableText
                label="大数字说明"
                :model-value="draft.market_battleground.regional_card.subtitle"
                :maxlength="fieldMax('market_battleground.regional_card.subtitle', 28)"
                :warn-length="fieldWarn('market_battleground.regional_card.subtitle')"
                @update:model-value="draft.market_battleground.regional_card.subtitle = $event"
              />
              <EditableText
                label="推导标题"
                :model-value="draft.market_battleground.regional_card.calculation_label"
                :maxlength="fieldMax('market_battleground.regional_card.calculation_label', 24)"
                :warn-length="fieldWarn('market_battleground.regional_card.calculation_label')"
                @update:model-value="draft.market_battleground.regional_card.calculation_label = $event"
              />
              <div v-for="(row, idx) in draft.market_battleground.regional_card.rows" :key="idx" class="list-editor-item">
                <div class="row-title"><strong>推导行 {{ idx + 1 }}</strong></div>
                <EditableText
                  label="标签"
                  :model-value="row.label"
                  :maxlength="fieldMax(`market_battleground.regional_card.rows[${idx}].label`, 18)"
                  :warn-length="fieldWarn(`market_battleground.regional_card.rows[${idx}].label`)"
                  @update:model-value="row.label = $event"
                />
                <EditableText
                  label="数值"
                  :model-value="row.value"
                  :maxlength="fieldMax(`market_battleground.regional_card.rows[${idx}].value`, 30)"
                  :warn-length="fieldWarn(`market_battleground.regional_card.rows[${idx}].value`)"
                  @update:model-value="row.value = $event"
                />
              </div>
            </el-collapse-item>

            <el-collapse-item name="narrative" title="底部叙事">
              <EditableText
                label="问题场景引导"
                type="textarea"
                :model-value="draft.market_battleground.narrative.intro"
                :maxlength="fieldMax('market_battleground.narrative.intro', 56)"
                :warn-length="fieldWarn('market_battleground.narrative.intro')"
                @update:model-value="draft.market_battleground.narrative.intro = $event"
              />
              <EditableText
                v-for="(_, idx) in draft.market_battleground.narrative.questions"
                :key="idx"
                :label="`示例问题 ${idx + 1}`"
                :model-value="draft.market_battleground.narrative.questions[idx]"
                :maxlength="fieldMax(`market_battleground.narrative.questions[${idx}]`, 34)"
                :warn-length="fieldWarn(`market_battleground.narrative.questions[${idx}]`)"
                @update:model-value="draft.market_battleground.narrative.questions[idx] = $event"
              />
              <EditableText
                label="结论句"
                :model-value="draft.market_battleground.narrative.conclusion"
                :maxlength="fieldMax('market_battleground.narrative.conclusion', 44)"
                :warn-length="fieldWarn('market_battleground.narrative.conclusion')"
                @update:model-value="draft.market_battleground.narrative.conclusion = $event"
              />
              <div class="inline-row">
                <EditableText
                  label="品牌句前缀"
                  :model-value="draft.market_battleground.narrative.brand_line_prefix"
                  :maxlength="fieldMax('market_battleground.narrative.brand_line_prefix', 8)"
                  :warn-length="fieldWarn('market_battleground.narrative.brand_line_prefix')"
                  @update:model-value="draft.market_battleground.narrative.brand_line_prefix = $event"
                />
                <EditableText
                  label="品牌名"
                  :model-value="draft.market_battleground.narrative.brand_name"
                  :maxlength="fieldMax('market_battleground.narrative.brand_name', 18)"
                  :warn-length="fieldWarn('market_battleground.narrative.brand_name')"
                  @update:model-value="draft.market_battleground.narrative.brand_name = $event"
                />
              </div>
              <EditableText
                label="品牌句后缀"
                type="textarea"
                :model-value="draft.market_battleground.narrative.brand_line_suffix"
                :maxlength="fieldMax('market_battleground.narrative.brand_line_suffix', 48)"
                :warn-length="fieldWarn('market_battleground.narrative.brand_line_suffix')"
                @update:model-value="draft.market_battleground.narrative.brand_line_suffix = $event"
              />
            </el-collapse-item>

            <el-collapse-item name="market-footer" title="脚注">
              <EditableText
                label="数据脚注"
                type="textarea"
                :model-value="draft.market_battleground.footnote"
                :maxlength="fieldMax('market_battleground.footnote', 150)"
                :warn-length="fieldWarn('market_battleground.footnote')"
                @update:model-value="draft.market_battleground.footnote = $event"
              />
              <EditableText
                label="页脚品牌"
                :model-value="draft.market_battleground.footer_brand"
                :maxlength="fieldMax('market_battleground.footer_brand', 24)"
                :warn-length="fieldWarn('market_battleground.footer_brand')"
                @update:model-value="draft.market_battleground.footer_brand = $event"
              />
            </el-collapse-item>
          </el-collapse>
        </el-card>

        <el-card id="summary" shadow="never" class="edit-section">
          <template #header>
            <div class="section-header">
              <span>执行摘要</span>
              <el-button v-if="draft.executive_summary === null" size="small" @click="createSummary">自定义摘要</el-button>
              <el-button v-else size="small" :icon="RefreshLeft" @click="draft.executive_summary = null">恢复默认</el-button>
            </div>
          </template>
          <div v-if="draft.executive_summary === null" class="default-hint">
            使用默认：暂无执行摘要 / 规则引擎未生成默认摘要,请运营在 L3 编辑页填写。
          </div>
          <template v-else>
            <EditableText
              label="摘要标题"
              :model-value="draft.executive_summary.headline"
              :maxlength="60"
              @update:model-value="draft.executive_summary!.headline = $event"
            />
            <EditableText
              label="摘要正文"
              type="textarea"
              :model-value="draft.executive_summary.paragraph"
              :maxlength="500"
              @update:model-value="draft.executive_summary!.paragraph = $event"
            />
          </template>
        </el-card>

        <el-card id="takeaways" shadow="never" class="edit-section">
          <template #header>
            <div class="section-header">
              <span>关键结论</span>
              <div>
                <el-button size="small" @click="draft.key_takeaways = []">清空所有项</el-button>
                <el-button size="small" type="primary" :disabled="draft.key_takeaways.length >= 8" @click="addTakeaway">
                  新增
                </el-button>
              </div>
            </div>
          </template>
          <div v-if="draft.key_takeaways.length === 0" class="default-hint">关键结论已清空，详情页会显示空态。</div>
          <div v-for="(item, idx) in draft.key_takeaways" :key="idx" class="list-editor-item">
            <div class="row-title">
              <strong>结论 {{ idx + 1 }}</strong>
              <div>
                <el-button text size="small" :disabled="idx === 0" @click="moveTakeaway(idx, -1)">上移</el-button>
                <el-button text size="small" :disabled="idx === draft.key_takeaways.length - 1" @click="moveTakeaway(idx, 1)">下移</el-button>
                <el-button text type="danger" size="small" @click="removeTakeaway(idx)">删除</el-button>
              </div>
            </div>
            <el-input v-model="item.title" maxlength="30" show-word-limit placeholder="标题" />
            <el-input v-model="item.description" maxlength="500" show-word-limit type="textarea" :rows="3" placeholder="描述" />
          </div>
        </el-card>

        <el-card id="findings" shadow="never" class="edit-section">
          <template #header>优化建议文案</template>
          <div
            v-for="finding in computedSnap.optimization_findings"
            :key="finding.finding_id"
            class="list-editor-item"
            :class="{ muted: isFindingHidden(finding.finding_id) }"
          >
            <div class="row-title">
              <strong>{{ finding.finding_id }}</strong>
              <el-switch
                :model-value="isFindingHidden(finding.finding_id)"
                active-text="隐藏"
                inactive-text="展示"
                @update:model-value="setFindingHidden(finding.finding_id, Boolean($event))"
              />
            </div>
            <div class="readonly-line">{{ findingContext(finding.finding_id) }}</div>
            <EditableText
              label="标题"
              :model-value="getFindingContent(finding.finding_id)?.title ?? null"
              :maxlength="50"
              @update:model-value="setFindingField(finding.finding_id, 'title', $event)"
              @restore="setFindingField(finding.finding_id, 'title', null)"
            />
            <EditableText
              label="描述"
              type="textarea"
              :model-value="getFindingContent(finding.finding_id)?.description ?? null"
              :maxlength="500"
              @update:model-value="setFindingField(finding.finding_id, 'description', $event)"
              @restore="setFindingField(finding.finding_id, 'description', null)"
            />
            <EditableText
              label="证据文案"
              type="textarea"
              :model-value="getFindingContent(finding.finding_id)?.evidence_text ?? null"
              :maxlength="300"
              @update:model-value="setFindingField(finding.finding_id, 'evidence_text', $event)"
              @restore="setFindingField(finding.finding_id, 'evidence_text', null)"
            />
            <el-input-number
              :model-value="getFindingContent(finding.finding_id)?.sort_order ?? null"
              :min="1"
              controls-position="right"
              placeholder="默认排序"
              @update:model-value="setFindingField(finding.finding_id, 'sort_order', $event == null ? null : Number($event))"
            />
          </div>
        </el-card>

        <el-card id="competitors" shadow="never" class="edit-section">
          <template #header>竞品场景描述</template>
          <div v-for="competitor in raw.competitors.slice(0, 3)" :key="competitor.rank" class="list-editor-item">
            <div class="row-title">
              <strong>竞品 {{ competitor.rank }} · {{ competitor.name }}</strong>
              <div>
                <el-button size="small" @click="restoreCompetitor(competitor.rank)">恢复默认列表</el-button>
                <el-button size="small" @click="clearCompetitor(competitor.rank)">清空所有项</el-button>
                <el-button size="small" type="primary" @click="addCompetitorLine(competitor.rank)">新增行</el-button>
              </div>
            </div>
            <div v-if="getCompetitorContent(competitor.rank)?.scene_advantages_polished == null" class="default-hint">
              使用 L1 原始场景：{{ competitorRawText(competitor.rank) }}
            </div>
            <div v-else-if="(getCompetitorContent(competitor.rank)?.scene_advantages_polished ?? []).length === 0" class="default-hint">
              已清空，报告中不会展示该竞品优势场景。
            </div>
            <div
              v-for="(_, idx) in getCompetitorContent(competitor.rank)?.scene_advantages_polished ?? []"
              :key="idx"
              class="inline-row"
            >
              <el-input
                :model-value="getCompetitorContent(competitor.rank)?.scene_advantages_polished?.[idx] ?? ''"
                maxlength="100"
                show-word-limit
                @update:model-value="setCompetitorLine(competitor.rank, idx, $event)"
              />
              <el-button text type="danger" @click="removeCompetitorLine(competitor.rank, idx)">删除</el-button>
            </div>
          </div>
        </el-card>

        <el-card id="phases" shadow="never" class="edit-section">
          <template #header>ROI 路线图</template>
          <div v-for="item in draft.phase_descriptions" :key="item.phase_no" class="list-editor-item">
            <div class="row-title">
              <strong>阶段 {{ item.phase_no }}</strong>
              <span class="readonly-line">{{ phaseContext(item.phase_no) }}</span>
            </div>
            <EditableText label="阶段标题" :model-value="item.title ?? null" :maxlength="30" @update:model-value="item.title = $event" @restore="item.title = null" />
            <EditableText label="阶段描述" type="textarea" :model-value="item.description ?? null" :maxlength="300" @update:model-value="item.description = $event" @restore="item.description = null" />
          </div>
        </el-card>

        <el-card id="disclaimer" shadow="never" class="edit-section">
          <template #header>ROI 免责声明</template>
          <EditableText
            label="免责声明"
            type="textarea"
            :model-value="draft.roi_disclaimer"
            :maxlength="200"
            default-text="区间为基于同行业历史优化案例的统计估算,具体效果以实际执行为准。"
            @update:model-value="draft.roi_disclaimer = $event"
            @restore="draft.roi_disclaimer = null"
          />
        </el-card>
      </main>

      <aside class="edit-preview">
        <el-card shadow="never" class="preview-card">
          <template #header>实时预览</template>
          <div v-if="mergedPreview">
            <div class="preview-title">{{ mergedPreview.report_title || '（空标题）' }}</div>
            <div class="preview-subtitle">{{ mergedPreview.report_subtitle || '（空副标题）' }}</div>
            <el-divider />
            <div class="preview-stat">
              <span>关键结论</span>
              <strong>{{ mergedPreview.key_takeaways.length }} 条</strong>
            </div>
            <div class="preview-stat">
              <span>优化建议</span>
              <strong>{{ mergedPreview.merged_findings.length }} 条</strong>
            </div>
            <div class="preview-stat">
              <span>竞品场景</span>
              <strong>{{ mergedPreview.merged_competitors.length }} 个</strong>
            </div>
          </div>
        </el-card>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onBeforeUnmount, onMounted, ref, type PropType } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElButton, ElInput, ElMessage, ElMessageBox, ElTag } from 'element-plus'
import { Loading, RefreshLeft } from '@element-plus/icons-vue'
import {
  deriveVersion,
  editVersionContent,
  getLatestDetail,
  getVersionDetail,
  type ReportDetailVO
} from '@/api/presaleReport'
import type { ComputedSnapshotDTO } from '@/types/presale/computed'
import type { CompetitorSceneDescription, EditableContentDTO, FindingContent } from '@/types/presale/editable'
import type { MergedViewDTO } from '@/types/presale'
import type { RawSnapshotDTO } from '@/types/presale/raw'
import { mergeSnapshot, type VersionRowMeta } from '@/utils/presale/merge-snapshot'
import {
  collectClearedFields,
  parseEditableContent,
  serializeEditableContent,
  stableStringify,
  validateEditableContent
} from '@/utils/presale/editable-content'

const EditableText = defineComponent({
  props: {
    label: { type: String, required: true },
    modelValue: { type: String as PropType<string | null>, default: null },
    maxlength: { type: Number, required: true },
    warnLength: { type: Number, default: 0 },
    defaultText: { type: String, default: '' },
    type: { type: String, default: 'text' }
  },
  emits: ['update:modelValue', 'restore'],
  setup(props, { emit }) {
    const isWarn = () => props.warnLength > 0 && (props.modelValue?.length ?? 0) >= props.warnLength
    return () =>
      h('div', { class: 'field-block' }, [
        h('div', { class: 'field-label' }, [
          h('span', props.label),
          h(ElTag, { size: 'small', type: props.modelValue == null ? 'info' : 'success', effect: 'plain' }, () =>
            props.modelValue == null ? '默认' : '已修改'
          ),
          props.defaultText
            ? h(
                ElButton,
                { size: 'small', text: true, icon: RefreshLeft, onClick: () => emit('restore') },
                () => '恢复默认'
              )
            : null
        ]),
        props.modelValue == null && props.defaultText
          ? h('div', { class: 'default-hint' }, `使用默认：${props.defaultText}`)
          : null,
        h(ElInput, {
          modelValue: props.modelValue ?? '',
          type: props.type,
          rows: props.type === 'textarea' ? 3 : undefined,
          maxlength: props.maxlength,
          showWordLimit: true,
          placeholder: props.modelValue == null ? '当前使用默认文案，输入后将改为自定义' : '',
          'onUpdate:modelValue': (value: string) => emit('update:modelValue', value)
        }),
        isWarn()
          ? h('div', { class: 'field-warning' }, `接近排版上限,建议控制在 ${props.warnLength} 字以内`)
          : null
      ])
  }
})

const route = useRoute()
const router = useRouter()
const reportId = computed(() => Number(route.params.id))

const detail = ref<ReportDetailVO | null>(null)
const raw = ref<RawSnapshotDTO | null>(null)
const computedSnap = ref<ComputedSnapshotDTO | null>(null)
const draft = ref<EditableContentDTO | null>(null)
const baselineJson = ref('')
const baselineUpdatedAt = ref<string | null>(null)
const loading = ref(false)
const saving = ref(false)
const deriving = ref(false)
const error = ref('')
const generationLocked = ref(false)
const emptyWarningKey = ref('')
const marketCollapseActive = ref(['market-topbar', 'market-title', 'market-footer'])
let pollTimer: number | undefined
const LOCAL_DRAFT_TTL = 24 * 60 * 60 * 1000

const isDirty = computed(() => draft.value != null && serializeEditableContent(draft.value) !== baselineJson.value)
const validationErrors = computed(() => (draft.value ? validateEditableContent(draft.value) : []))
const fieldMetaByPath = computed(() => {
  const map = new Map<string, { maxLength: number; warnLength: number; label: string; block: string }>()
  detail.value?.editableFieldMeta?.forEach((item) => map.set(item.field, item))
  return map
})
const marketWarnings = computed(() => (draft.value ? collectMarketWarnings(draft.value) : []))
const saveDisabledReason = computed(() => {
  if (saving.value) return '正在保存'
  if (generationLocked.value) return '报告正在重新生成，编辑已锁定'
  if (!isDirty.value) return '没有需要保存的修改'
  if (validationErrors.value.length > 0) return validationErrors.value[0].message
  return ''
})
const lockedReason = computed(() => {
  if (!detail.value) return ''
  const v = detail.value.version
  if (v.frozen) return '当前版本已冻结，不能直接编辑。请派生新版本后继续编辑。'
  if (v.generationStatus !== 'DONE') return `当前版本状态为 ${v.generationStatus}，报告内容暂不可编辑。`
  if (generationLocked.value) return '该报告正在重新生成，编辑已锁定。'
  return ''
})
const canDerive = computed(() => detail.value?.version.generationStatus === 'DONE' && detail.value.version.frozen)

const defaultTitle = computed(() => `${raw.value?.client_info.brand_name ?? detail.value?.brandName ?? ''} GEO 可见度诊断报告`)
const defaultSubtitle = computed(() => {
  const s = raw.value?.test_summary
  return s ? `基于 ${s.total_platforms} 个 AI 平台 × ${s.total_prompts} 条查询的深度分析` : ''
})
const mergedPreview = computed<MergedViewDTO | null>(() => {
  if (!raw.value || !computedSnap.value || !draft.value || !detail.value) return null
  return mergeSnapshot(raw.value, computedSnap.value, draft.value, toVersionRowMeta(detail.value))
})

function toVersionRowMeta(d: ReportDetailVO): VersionRowMeta {
  const v = d.version
  return {
    version_id: v.versionId,
    report_id: d.reportId,
    version_no: v.versionNo,
    schema_version: 'v1.2',
    generation_status: v.generationStatus,
    frozen_at: v.frozenAt,
    frozen_by: null,
    frozen_reason: null,
    content_updated_at: v.contentUpdatedAt,
    content_updated_by: null,
    is_degraded: v.isDegraded ?? false,
    degraded_platforms: v.degradedPlatforms ?? null,
    export_success_count: v.exportSuccessCount,
    export_success_at: v.exportSuccessAt
  }
}

function draftStorageKey(versionNo?: number) {
  return `presale-edit-draft:${reportId.value}:${versionNo ?? detail.value?.version.versionNo ?? 'latest'}`
}

async function load() {
  loading.value = true
  error.value = ''
  emptyWarningKey.value = ''
  try {
    const d = await getLatestDetail(reportId.value)
    detail.value = d
    if (d.version.generationStatus !== 'DONE' || d.version.frozen) return
    if (!d.rawSnapshotJson || !d.computedSnapshotJson) {
      throw new Error('报告数据不完整:L1/L2 快照缺失')
    }
    raw.value = JSON.parse(d.rawSnapshotJson)
    computedSnap.value = JSON.parse(d.computedSnapshotJson)
    const parsedDraft = parseEditableContent(d.editableContentJson, raw.value!, computedSnap.value!)
    baselineJson.value = serializeEditableContent(parsedDraft)
    baselineUpdatedAt.value = d.version.contentUpdatedAt
    draft.value = parsedDraft
    await maybeRestoreLocalDraft()
    startPolling()
  } catch (e: unknown) {
    error.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function maybeRestoreLocalDraft() {
  const rawDraft = localStorage.getItem(draftStorageKey())
  if (!rawDraft || !raw.value || !computedSnap.value) return
  const payload = JSON.parse(rawDraft) as { savedAt: number; draft: EditableContentDTO }
  if (Date.now() - payload.savedAt > LOCAL_DRAFT_TTL) {
    localStorage.removeItem(draftStorageKey())
    return
  }
  const normalizedDraft = parseEditableContent(JSON.stringify(payload.draft), raw.value, computedSnap.value)
  if (serializeEditableContent(normalizedDraft) === baselineJson.value) {
    localStorage.removeItem(draftStorageKey())
    return
  }
  const confirmed = await ElMessageBox.confirm('上次有未提交的修改，是否恢复？', '恢复本地草稿', {
    confirmButtonText: '恢复',
    cancelButtonText: '忽略',
    type: 'warning'
  }).catch(() => false)
  if (confirmed) {
    draft.value = normalizedDraft
  } else {
    localStorage.removeItem(draftStorageKey())
  }
}

function startPolling() {
  if (pollTimer != null || !detail.value) return
  pollTimer = window.setInterval(async () => {
    if (!detail.value || generationLocked.value) return
    const v = await getVersionDetail(reportId.value, detail.value.version.versionNo)
    if (v.version.generationStatus !== 'DONE') {
      generationLocked.value = true
      await ElMessageBox.confirm('该报告正在重新生成，编辑已锁定。', '编辑已锁定', {
        confirmButtonText: '保留我的草稿到本地',
        cancelButtonText: '放弃并返回详情页',
        closeOnClickModal: false,
        closeOnPressEscape: false,
        showClose: false,
        type: 'warning'
      })
        .then(() => persistLocalDraft())
        .catch(() => router.push(`/admin/presale/report/${reportId.value}/detail`))
    }
  }, 30_000)
}

function persistLocalDraft() {
  if (!draft.value) return
  localStorage.setItem(draftStorageKey(), JSON.stringify({ savedAt: Date.now(), draft: draft.value }))
  ElMessage.success('草稿已保留在本地')
}

function fieldMax(field: string, fallback: number) {
  return fieldMetaByPath.value.get(field)?.maxLength ?? fallback
}

function fieldWarn(field: string) {
  return fieldMetaByPath.value.get(field)?.warnLength ?? 0
}

function collectMarketWarnings(value: EditableContentDTO) {
  const warnings: string[] = []
  const visit = (field: string, text: string | null | undefined) => {
    const meta = fieldMetaByPath.value.get(field)
    if (!meta || !text) return
    if (text.length >= meta.warnLength) {
      warnings.push(`${meta.block} · ${meta.label}`)
    }
  }
  const m = value.market_battleground
  visit('market_battleground.topbar_title', m.topbar_title)
  visit('market_battleground.topbar_right', m.topbar_right)
  visit('market_battleground.page_title', m.page_title)
  visit('market_battleground.page_kicker', m.page_kicker)
  visit('market_battleground.market_card.label', m.market_card.label)
  visit('market_battleground.market_card.source', m.market_card.source)
  m.market_card.stats.forEach((item, idx) => {
    visit(`market_battleground.market_card.stats[${idx}].value`, item.value)
    visit(`market_battleground.market_card.stats[${idx}].unit`, item.unit)
    visit(`market_battleground.market_card.stats[${idx}].label`, item.label)
  })
  visit('market_battleground.market_card.platform_label', m.market_card.platform_label)
  m.market_card.platforms.forEach((item, idx) => {
    visit(`market_battleground.market_card.platforms[${idx}].name`, item.name)
    visit(`market_battleground.market_card.platforms[${idx}].value`, item.value)
  })
  visit('market_battleground.market_card.platform_suffix', m.market_card.platform_suffix)
  collectCardWarnings('national_card', m.national_card, visit)
  visit('market_battleground.bridge_text', m.bridge_text)
  collectCardWarnings('regional_card', m.regional_card, visit)
  visit('market_battleground.narrative.intro', m.narrative.intro)
  m.narrative.questions.forEach((item, idx) => visit(`market_battleground.narrative.questions[${idx}]`, item))
  visit('market_battleground.narrative.conclusion', m.narrative.conclusion)
  visit('market_battleground.narrative.brand_line_prefix', m.narrative.brand_line_prefix)
  visit('market_battleground.narrative.brand_name', m.narrative.brand_name)
  visit('market_battleground.narrative.brand_line_suffix', m.narrative.brand_line_suffix)
  visit('market_battleground.footnote', m.footnote)
  visit('market_battleground.footer_brand', m.footer_brand)
  return Array.from(new Set(warnings))
}

function collectCardWarnings(
  card: 'national_card' | 'regional_card',
  value: EditableContentDTO['market_battleground']['national_card'],
  visit: (field: string, text: string | null | undefined) => void
) {
  visit(`market_battleground.${card}.label`, value.label)
  visit(`market_battleground.${card}.value_prefix`, value.value_prefix)
  visit(`market_battleground.${card}.value`, value.value)
  visit(`market_battleground.${card}.unit`, value.unit)
  visit(`market_battleground.${card}.subtitle`, value.subtitle)
  visit(`market_battleground.${card}.calculation_label`, value.calculation_label)
  value.rows.forEach((item, idx) => {
    visit(`market_battleground.${card}.rows[${idx}].label`, item.label)
    visit(`market_battleground.${card}.rows[${idx}].value`, item.value)
  })
}

async function handleSave(forceOverwrite: boolean) {
  if (!draft.value || validationErrors.value.length > 0 || saving.value) return
  const cleared = collectClearedFields(draft.value)
  const warningKey = stableStringify(cleared)
  if (!forceOverwrite && cleared.length > 0 && emptyWarningKey.value !== warningKey) {
    const confirmed = await ElMessageBox.confirm(
      `检测到 ${cleared.length} 个字段被清空：${cleared.slice(0, 5).join('、')}。这些字段在导出报告时将不显示。是否继续？`,
      '确认保存空值',
      { confirmButtonText: '继续保存', cancelButtonText: '返回编辑', type: 'warning' }
    ).catch(() => false)
    if (!confirmed) return
    emptyWarningKey.value = warningKey
  }
  if (marketWarnings.value.length > 0) {
    ElMessage.warning(`有 ${marketWarnings.value.length} 个 AI 搜索新战场字段接近排版上限，保存后导出前请检查。`)
  }

  saving.value = true
  try {
    const json = serializeEditableContent(draft.value)
    const res = await editVersionContent(reportId.value, detail.value!.version.versionNo, {
      editableContentJson: json,
      expectedContentUpdatedAt: baselineUpdatedAt.value,
      forceOverwrite
    })
    baselineJson.value = json
    baselineUpdatedAt.value = res.updatedAt
    detail.value!.version.contentUpdatedAt = res.updatedAt
    emptyWarningKey.value = ''
    localStorage.removeItem(draftStorageKey())
    ElMessage.success('已保存')
  } catch (e: unknown) {
    const err = e as { code?: number; status?: number; message?: string; data?: { errorCode?: string } }
    if ((err.code === 409 || err.status === 409) && err.data?.errorCode === 'content_conflict') {
      await handleConflict(err.message || '内容已被他人更新')
    } else if (err.code === 409 || err.status === 409) {
      ElMessage.error(err.message || '当前版本状态不允许保存')
      await load()
    } else {
      ElMessage.error(err.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}

async function handleConflict(message: string) {
  const action = await ElMessageBox.confirm(message, '保存冲突', {
    confirmButtonText: '重新加载',
    cancelButtonText: '强制覆盖',
    distinguishCancelAndClose: true,
    type: 'warning'
  }).catch((actionName) => actionName)
  if (action === 'confirm') {
    await load()
    return
  }
  if (action === 'cancel') {
    await handleSave(true)
  }
}

async function handleDerive() {
  if (!detail.value || deriving.value) return
  const confirmed = await ElMessageBox.confirm(
    `将基于版本 v${detail.value.version.versionNo} 创建新版本，新版本会复制当前的所有内容，原版本保持冻结不变。`,
    '派生新版本并编辑',
    { confirmButtonText: '创建并编辑', cancelButtonText: '取消', type: 'warning' }
  ).catch(() => false)
  if (!confirmed) return
  deriving.value = true
  try {
    const res = await deriveVersion(reportId.value, detail.value.version.versionNo)
    ElMessage.success(`已创建 v${res.newVersionNo}`)
    await load()
  } finally {
    deriving.value = false
  }
}

function createSummary() {
  if (!draft.value) return
  draft.value.executive_summary = { headline: '', paragraph: '' }
}

function addTakeaway() {
  if (!draft.value || draft.value.key_takeaways.length >= 8) return
  draft.value.key_takeaways.push({ order_no: draft.value.key_takeaways.length + 1, title: '', description: '' })
}

function removeTakeaway(idx: number) {
  draft.value?.key_takeaways.splice(idx, 1)
}

function moveTakeaway(idx: number, delta: number) {
  if (!draft.value) return
  const next = idx + delta
  const arr = draft.value.key_takeaways
  if (next < 0 || next >= arr.length) return
  const [item] = arr.splice(idx, 1)
  arr.splice(next, 0, item)
}

function findingContext(id: string) {
  const item = computedSnap.value?.optimization_findings.find((x) => x.finding_id === id)
  return item ? `${item.priority} · ${item.category} · ${item.rule_code}` : '关联的计算结果不存在'
}

function getFindingContent(findingId: string): FindingContent | null {
  return draft.value?.optimization_findings_content.find((item) => item.finding_id === findingId) ?? null
}

function ensureFindingContent(findingId: string): FindingContent {
  const existing = getFindingContent(findingId)
  if (existing) return existing
  const item: FindingContent = { finding_id: findingId }
  draft.value!.optimization_findings_content.push(item)
  return item
}

function isFindingHidden(findingId: string) {
  return getFindingContent(findingId)?.is_hidden === true
}

function setFindingHidden(findingId: string, hidden: boolean) {
  ensureFindingContent(findingId).is_hidden = hidden
}

function setFindingField<K extends keyof Omit<FindingContent, 'finding_id' | 'is_hidden'>>(
  findingId: string,
  field: K,
  value: FindingContent[K]
) {
  ensureFindingContent(findingId)[field] = value
}

function phaseContext(phaseNo: number) {
  const item = computedSnap.value?.roi_simulation.phases.find((x) => x.phase_no === phaseNo)
  return item ? `${item.duration_label} · 目标 ${Math.round(item.target_score)} 分` : ''
}

function competitorRawText(rank: number) {
  return raw.value?.competitors.find((x) => x.rank === rank)?.scene_advantages_raw?.join('、') || '无'
}

function getCompetitorContent(rank: number): CompetitorSceneDescription | null {
  return draft.value?.competitor_scene_descriptions.find((item) => item.competitor_rank === rank) ?? null
}

function ensureCompetitorContent(rank: number): CompetitorSceneDescription {
  const existing = getCompetitorContent(rank)
  if (existing) return existing
  const item: CompetitorSceneDescription = { competitor_rank: rank as 1 | 2 | 3, scene_advantages_polished: null }
  draft.value!.competitor_scene_descriptions.push(item)
  return item
}

function restoreCompetitor(rank: number) {
  ensureCompetitorContent(rank).scene_advantages_polished = null
}

function clearCompetitor(rank: number) {
  ensureCompetitorContent(rank).scene_advantages_polished = []
}

function addCompetitorLine(rank: number) {
  const item = ensureCompetitorContent(rank)
  if (item.scene_advantages_polished == null) {
    item.scene_advantages_polished = []
  }
  if ((item.scene_advantages_polished ?? []).length >= 6) return
  item.scene_advantages_polished.push('')
}

function setCompetitorLine(rank: number, idx: number, value: string) {
  const item = ensureCompetitorContent(rank)
  if (item.scene_advantages_polished == null) item.scene_advantages_polished = []
  item.scene_advantages_polished[idx] = value
}

function removeCompetitorLine(rank: number, idx: number) {
  getCompetitorContent(rank)?.scene_advantages_polished?.splice(idx, 1)
}

function goDetail() {
  router.push(`/admin/presale/report/${reportId.value}/detail`)
}

onBeforeRouteLeave(async () => {
  if (!isDirty.value || saving.value) return true
  const confirmed = await ElMessageBox.confirm('有未保存内容，确定要离开吗？', '提示', {
    confirmButtonText: '离开',
    cancelButtonText: '继续编辑',
    type: 'warning'
  }).catch(() => false)
  return confirmed === true
})

onMounted(load)
onBeforeUnmount(() => {
  if (pollTimer != null) window.clearInterval(pollTimer)
})
</script>

<style scoped>
.presale-report-edit {
  min-height: calc(100vh - 60px);
  padding: 24px 28px 36px;
  background:
    linear-gradient(180deg, rgba(230, 240, 255, 0.72) 0, rgba(248, 250, 252, 0) 180px),
    #f8fafc;
  color: #1f2937;
}
.edit-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 18px;
  margin-bottom: 20px;
  padding: 22px 24px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.06);
}
.edit-header h2 {
  margin: 10px 0 0;
  color: #111827;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.25;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.header-actions :deep(.el-button) {
  height: 36px;
  border-radius: 6px;
}
.state-panel {
  padding: 80px;
  display: flex;
  justify-content: center;
  gap: 12px;
  color: #606266;
}
.edit-alert {
  max-width: 860px;
}
.edit-layout {
  display: grid;
  grid-template-columns: 184px minmax(0, 1fr) 280px;
  gap: 18px;
  align-items: start;
}
.edit-nav,
.edit-preview {
  position: sticky;
  top: 84px;
  align-self: start;
}
.edit-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}
.edit-nav-title {
  margin: 0 4px 8px;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.edit-nav a {
  color: #64748b;
  text-decoration: none;
  padding: 9px 10px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.edit-nav a:hover {
  background: #eef4ff;
  color: #1d4ed8;
}
.edit-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.edit-section {
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.04);
}
.edit-section :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid #eef2f7;
  background: #fbfdff;
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}
.edit-section :deep(.el-card__body) {
  padding: 18px 20px 20px;
}
.section-header,
.row-title,
.field-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.section-header > div,
.row-title > div {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.field-block {
  margin-bottom: 14px;
}
.field-label {
  margin-bottom: 7px;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
}
.default-hint,
.readonly-line,
.field-warning {
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.6;
  margin-bottom: 8px;
}
.field-warning {
  margin-top: 6px;
  color: #b45309;
}
.market-collapse {
  overflow: hidden;
  border: 1px solid #e5eaf3;
  border-radius: 8px;
  --el-collapse-header-bg-color: #fbfdff;
  --el-collapse-content-bg-color: #fff;
}
.market-collapse :deep(.el-collapse-item__header) {
  padding: 0 14px;
  color: #1f2937;
  font-weight: 600;
}
.market-collapse :deep(.el-collapse-item__content) {
  padding: 16px 14px 18px;
}
.list-editor-item {
  border: 1px solid #e5eaf3;
  border-radius: 8px;
  padding: 15px;
  margin-bottom: 12px;
  background: #fbfdff;
}
.list-editor-item.muted {
  opacity: 0.64;
}
.list-editor-item :deep(.el-input),
.list-editor-item :deep(.el-textarea) {
  margin-top: 8px;
}
.inline-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: center;
  margin-top: 8px;
}
.edit-section :deep(.el-input__wrapper),
.edit-section :deep(.el-textarea__inner) {
  border-radius: 6px;
  box-shadow: 0 0 0 1px #dbe3ef inset;
}
.edit-section :deep(.el-input__wrapper:hover),
.edit-section :deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 1px #b8c3d4 inset;
}
.edit-section :deep(.el-input__wrapper.is-focus),
.edit-section :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px #3b82f6 inset, 0 0 0 3px rgba(59, 130, 246, 0.1);
}
.preview-card {
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 10px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}
.preview-card :deep(.el-card__header) {
  padding: 15px 16px;
  border-bottom: 1px solid #eef2f7;
  background: #fbfdff;
  color: #111827;
  font-weight: 700;
}
.preview-card :deep(.el-card__body) {
  padding: 16px;
}
.preview-title {
  color: #111827;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.45;
  margin-bottom: 8px;
}
.preview-subtitle {
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}
.preview-stat {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  color: #64748b;
  font-size: 13px;
}
.preview-stat + .preview-stat {
  border-top: 1px solid #eef2f7;
}
.preview-stat strong {
  color: #1d4ed8;
  font-size: 14px;
}
@media (max-width: 1180px) {
  .edit-layout {
    grid-template-columns: 1fr;
  }
  .edit-nav,
  .edit-preview {
    position: static;
  }
  .edit-nav {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(136px, 1fr));
  }
  .edit-nav-title {
    grid-column: 1 / -1;
  }
}
@media (max-width: 768px) {
  .presale-report-edit {
    padding: 18px 16px 28px;
  }
  .edit-header {
    align-items: flex-start;
    padding: 18px;
  }
  .header-actions {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }
  .inline-row {
    grid-template-columns: 1fr;
  }
}
</style>
