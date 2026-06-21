<template>
  <div class="baseline-page" :class="{ 'print-mode': isPrintMode }">
    <el-page-header v-if="!isPrintMode" content="基线检测报告" @back="$router.back()" />

    <section v-if="!isPrintMode" class="workflow">
      <div class="workflow-main">
        <div>
          <p class="eyebrow">Baseline Snapshot</p>
          <h2>{{ snapshot ? `基线 #${snapshot.id}` : '尚未创建基线快照' }}</h2>
          <p class="muted">
            {{ snapshot ? `状态 ${snapshot.status} · ${snapshot.questionCount} 题` : '先创建 DRAFT，复核题目意图与价值分层后再封板。' }}
          </p>
        </div>
        <div class="workflow-actions">
          <el-button :loading="snapshotLoading" @click="loadSnapshot">刷新</el-button>
          <el-button type="primary" :loading="creating" @click="createDraft">创建 DRAFT</el-button>
          <el-button
            v-if="snapshot?.status === 'DRAFT'"
            type="success"
            :loading="sealing"
            @click="sealDraft"
          >
            封板
          </el-button>
          <el-button
            v-if="snapshot?.status === 'SEALED'"
            type="primary"
            plain
            :loading="collecting"
            @click="collectObservations"
          >
            采集 n=3
          </el-button>
          <el-button
            v-if="snapshot?.status === 'SEALED'"
            type="primary"
            :loading="canonicalLoading"
            @click="recomputeCanonicalReport"
          >
            生成报告
          </el-button>
          <el-button
            v-if="snapshot?.status === 'SEALED' && canonical"
            type="success"
            :loading="exporting"
            @click="exportPdf"
          >
            导出 PDF
          </el-button>
        </div>
      </div>

      <div v-if="collectTask" class="task-strip">
        <span>采集任务 {{ collectTask.taskId }} · {{ taskStatusLabel(collectTask.status) }}</span>
        <el-progress
          :percentage="visibleCollectionPercent"
          :status="collectTask.status === 'FAILED' ? 'exception' : undefined"
          :striped="['PENDING', 'RUNNING'].includes(collectTask.status)"
          :striped-flow="['PENDING', 'RUNNING'].includes(collectTask.status)"
        />
        <span class="muted">
          {{ collectionProgressText }}
        </span>
        <span v-if="collectTask.status === 'PENDING'" class="muted">
          排队第 {{ collectTask.queuePosition || 1 }} 位 / 最大并发 {{ collectTask.maxConcurrentBaselines || 1 }}
        </span>
        <el-button
          v-if="canCancelCollectTask"
          size="small"
          type="danger"
          plain
          :loading="cancelingCollect"
          @click="cancelCollectTask"
        >
          取消采集
        </el-button>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="baseline-tabs">
      <el-tab-pane label="封板复核" name="review">
        <section class="panel">
          <div class="panel-header">
            <div>
              <h3>DRAFT 题目复核</h3>
              <p class="muted">价值层与意图类型在封板前冻结。封板后此表只读。</p>
            </div>
            <el-button
              type="primary"
              :disabled="snapshot?.status !== 'DRAFT'"
              :loading="reviewing"
              @click="saveReview"
            >
              保存复核
            </el-button>
          </div>

          <el-empty v-if="!snapshot" description="暂无基线快照" />
          <el-alert
            v-if="snapshot?.warnings?.length"
            class="snapshot-warning"
            type="warning"
            :closable="false"
            show-icon
          >
            <template #title>{{ snapshot.warnings.join('；') }}</template>
          </el-alert>
          <el-table v-if="snapshot" :data="snapshot.questions" border height="420">
            <el-table-column label="题号" width="88">
              <template #default="{ row }">{{ row.questionKey }}</template>
            </el-table-column>
            <el-table-column label="原分层" width="90">
              <template #default="{ row }">{{ sourceTierLabel(row.sourceQuestionTier) }}</template>
            </el-table-column>
            <el-table-column label="价值层" width="150">
              <template #default="{ row }">
                <el-select v-model="row.valueTier" :disabled="snapshot?.status !== 'DRAFT'" size="small">
                  <el-option label="高价值" value="HIGH" />
                  <el-option label="中价值" value="MID" />
                  <el-option label="低价值" value="LOW" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="意图类型" width="170">
              <template #default="{ row }">
                <el-select v-model="row.intentType" :disabled="snapshot?.status !== 'DRAFT'" size="small">
                  <el-option label="推荐" value="RECOMMENDATION" />
                  <el-option label="对比" value="COMPARISON" />
                  <el-option label="问题" value="PROBLEM" />
                  <el-option label="认知" value="AWARENESS" />
                  <el-option label="场景" value="SCENE" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="questionText" label="问题" min-width="360" show-overflow-tooltip />
          </el-table>
          <div v-if="snapshot" class="competitor-review">
            <div class="panel-subhead">
              <div>
                <h4>竞品集冻结</h4>
                <p class="muted">封板时冻结已核实竞品；未核实竞品不会渲染“为什么 AI 夸它”。</p>
              </div>
              <el-button size="small" :disabled="snapshot.status !== 'DRAFT'" @click="addCompetitorSource">添加竞品</el-button>
            </div>
            <div v-if="competitorSources.length" class="competitor-source-list">
              <div v-for="(item, index) in competitorSources" :key="index" class="competitor-source-row">
                <el-input v-model="item.competitorName" :disabled="snapshot.status !== 'DRAFT'" placeholder="竞品名称" />
                <el-input v-model="item.aliasesJson" :disabled="snapshot.status !== 'DRAFT'" placeholder="别名/简称，逗号分隔" />
                <el-input v-model="item.sourceType" :disabled="snapshot.status !== 'DRAFT'" placeholder="来源类型" />
                <el-input v-model="item.sourceNote" :disabled="snapshot.status !== 'DRAFT'" placeholder="核实说明/为什么 AI 夸它" />
                <el-select v-model="item.reviewStatus" :disabled="snapshot.status !== 'DRAFT'">
                  <el-option label="未核实" value="UNVERIFIED" />
                  <el-option label="已核实" value="VERIFIED" />
                  <el-option label="驳回" value="REJECTED" />
                </el-select>
                <el-button text type="danger" :disabled="snapshot.status !== 'DRAFT'" @click="removeCompetitorSource(index)">删除</el-button>
              </div>
            </div>
            <el-empty v-else description="暂无竞品。封板允许继续，但 Module 03 会缺少核心内容。" />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="报告预览" name="report">
        <section v-if="canonical" class="delivery-report">
          <aside class="report-sidebar">
            <div class="sidebar-header">
              <p class="sidebar-logo">AI 可见度基线报告</p>
              <p class="sidebar-client">{{ canonical.brand.name || '品牌' }}</p>
            </div>
            <nav class="report-nav">
              <a v-for="item in reportNavItems" :key="item.id" class="nav-item" :href="`#${item.id}`">
                <span class="nav-num">{{ item.no }}</span>
                <span>{{ item.label }}</span>
              </a>
            </nav>
            <div class="sidebar-foot">
              <p>封板报告</p>
              <p>{{ baselineCode }}</p>
            </div>
          </aside>

          <main class="report-main">
            <section class="report-banner">
              <div>
                <p class="banner-eyebrow">AI VISIBILITY BASELINE</p>
                <h1>AI 可见度基线报告</h1>
                <p>{{ canonical.brand.name || '品牌' }}　|　优化前起跑线已锁定　|　后续复测按同口径对照</p>
              </div>
              <div class="banner-meta">
                <div class="l">封板快照</div>
                <div class="v accent">{{ baselineCode }}</div>
                <div class="l">测试规模</div>
                <div class="v">{{ testScaleLabel }}</div>
                <div class="l">基线状态</div>
                <div class="v">{{ snapshot?.status || 'SEALED' }}</div>
              </div>
            </section>

            <section id="method" class="module">
              <div class="module-head">
                <span class="module-num">00 · ABOUT THIS BASELINE</span>
                <h2>关于本基线</h2>
                <p>这份报告是优化前的起跑线，已冻结为以后每一次复测的唯一对照基准。</p>
              </div>
              <div class="freeze-card">
                <p class="sub-title">封板配置（已冻结，后续复测完全一致）</p>
                <div class="freeze-grid">
                  <div class="freeze-item">
                    <span class="k">快照编号</span>
                    <strong>{{ baselineCode }}</strong>
                  </div>
                  <div class="freeze-item">
                    <span class="k">采样方式</span>
                    <strong>平台 × 问题 × n=3</strong>
                  </div>
                  <div class="freeze-item">
                    <span class="k">问题集</span>
                    <strong>{{ questionCount }} 题</strong>
                  </div>
                  <div class="freeze-item">
                    <span class="k">平台集</span>
                    <strong>{{ platformCount }} 个 AI 平台</strong>
                  </div>
                  <div class="freeze-item">
                    <span class="k">竞品集</span>
                    <strong>{{ canonical.competitors.tracked.length }} 个已跟踪</strong>
                  </div>
                  <div class="freeze-item">
                    <span class="k">对照口径</span>
                    <strong>已锁定</strong>
                  </div>
                </div>
              </div>
              <div class="method-card">
                <p class="sub-title">这份报告是怎么测出来的</p>
                <div class="method-list">
                  <div><strong>问题怎么来的：</strong>围绕客户真实会问 AI 的问题精选，覆盖推荐、对比、问题、认知、场景五类意图。</div>
                  <div><strong>怎么算“提到/推荐你”：</strong>把同一问题分别问多个 AI 平台，统计回答里是否出现品牌、是否被推荐、情感和排名状态。</div>
                  <div><strong>为什么测 3 次：</strong>AI 回答有随机性，同一问题问 3 次后按稳定性折叠，避免把偶然回答当成结论。</div>
                  <div><strong>关于“次数”：</strong>这里的数字来自标准化测试会话，反映 AI 会不会、会怎么提到你，不是真实用户访问量。</div>
                </div>
              </div>
              <div class="integrity">
                <strong>我们的承诺：</strong>本基线与未来每一期监测使用完全相同的问题、平台和判定口径；每条 AI 原始回答都已存档、可逐条追溯；本报告只记录现状，不含任何预测或承诺。
              </div>
            </section>

            <section id="findings" class="module">
              <div class="module-head">
                <span class="module-num">01 · CORE FINDING</span>
                <h2>一句话告诉你现在的状况</h2>
                <p>先看客户最关心的三个事实：AI 能不能看到你、提到你多少次、有没有负面倾向。</p>
              </div>
              <article class="hero-statement">
                <p class="hero-eyebrow">核心发现</p>
                <p class="hero-text">
                  AI 已经知道 <span class="brand">{{ canonical.brand.name || '品牌' }}</span>，
                  但在“哪家好 / 推荐谁”这类新客入口中，推荐覆盖仅为
                  <span class="accent">{{ formatRate(recommendationIntentRate) }}</span>。
                </p>
                <div class="hero-stats">
                  <div class="hero-stat">
                    <div class="num danger">{{ formatRate(recommendationIntentRate) }}</div>
                    <div class="label">推荐型问题中<br>你被 AI 主动推荐的比例</div>
                  </div>
                  <div class="hero-stat">
                    <div class="num warn">{{ canonical.hero_metrics?.brand_mention_count ?? canonical.sentiment.brand_mention_count }}</div>
                    <div class="label">AI 回答中<br>明确提及你的次数</div>
                  </div>
                  <div class="hero-stat">
                    <div class="num muted">{{ canonical.hero_metrics?.negative_count ?? canonical.sentiment.distribution.NEGATIVE }}</div>
                    <div class="label">明确提及品牌时<br>出现的负面倾向</div>
                  </div>
                </div>
              </article>
              <div class="section-intro">
                <p>
                  <strong>这意味着什么？</strong>
                  当潜在客户打开 AI 问“哪家更好”“怎么选”“适不适合我”时，本页记录的是 AI 在优化前自然给出的答案。后续复测只看同口径真实差值。
                </p>
              </div>
              <div class="finding-list">
                <article v-for="finding in canonical.key_findings" :key="finding.template_id" class="finding-card">
                  {{ finding.rendered_text }}
                </article>
              </div>
              <div v-if="canonical.delta_placeholders?.length" class="delta-note">
                本期为起算基线，暂无环比；下一次复测会在这里并排显示变化。
              </div>
            </section>

            <section id="evidence" class="module">
              <div class="module-head">
                <span class="module-num">02 · REAL USER SCENARIOS</span>
                <h2>用户问 AI 时，AI 是怎么回答的？</h2>
                <p>本报告最重要的一章：截取 AI 真实回答原文，展示用户场景下的实际答案。</p>
              </div>
              <div class="section-intro">
                <p>以下每个案例都是 AI 平台的真实回答节选。颜色标记：<mark class="hl-brand">绿色</mark> = 提到你，<mark class="hl-competitor">黄色</mark> = 提到竞品，<mark class="hl-negative">红色</mark> = 负面表述。</p>
              </div>
              <div v-for="group in evidenceGroups" :key="group.intent" class="evidence-section">
                <p class="sub-title">场景 · {{ intentLabel(group.intent) }}</p>
                <div class="evidence-grid">
                  <article v-for="(card, cardIndex) in group.cards" :key="card.observation_id" class="evidence-card">
                    <div class="evidence-meta">
                      <div class="case-platform-badge">
                        <span class="platform-icon" :class="platformIconClass(card.platform_code)">{{ platformInitial(card.platform_code) }}</span>
                        <span>{{ platformLabel(card.platform_code) }}</span>
                      </div>
                      <div class="case-tags">
                        <span class="case-tag" :class="intentTagClass(card.intent_type)">{{ intentLabel(card.intent_type || 'SCENE') }}</span>
                        <span class="case-tag case-tag-value-high">{{ valueTierLabel(card.value_tier || 'LOW') }}</span>
                      </div>
                    </div>
                    <div class="case-question">
                      <span class="q-prefix">Q</span>
                      <strong class="question-text">{{ card.question_text || card.question_id }}</strong>
                    </div>
                    <p class="case-answer">
                      <template v-for="(segment, index) in highlightedSegments(card)" :key="`${card.observation_id}-${index}`">
                        <mark v-if="segment.highlightType" :class="`hl-${segment.highlightType.toLowerCase()}`">{{ segment.text }}</mark>
                        <span v-else>{{ segment.text }}</span>
                      </template>
                    </p>
                    <div v-if="cardIndex === 0 && card.takeaway" class="takeaway">
                      <span class="takeaway-icon">!</span>
                      <p>{{ card.takeaway }}</p>
                    </div>
                    <div class="trace">{{ card.sample_label }}</div>
                  </article>
                </div>
              </div>
            </section>

            <section id="competitors" class="module">
              <div class="module-head">
                <span class="module-num">03 · YOUR COMPETITORS IN AI</span>
                <h2>对手在 AI 里的样子</h2>
                <p>同样的问题，AI 是怎么推荐和描述你的竞争对手的，以及为什么。</p>
              </div>
              <div class="big-stat-grid">
                <article v-for="item in topTrackedCompetitors" :key="`top-${item.name}`" class="big-stat">
                  <span>被 AI 提及</span>
                  <strong>{{ item.mention_count }} 次</strong>
                  <small>{{ item.name }}</small>
                </article>
              </div>
              <div class="competitor-grid">
                <article v-for="(item, competitorIndex) in canonical.competitors.tracked" :key="item.name" class="competitor-card">
                  <div class="competitor-head">
                    <h3 class="competitor-name">
                      <span class="rank-badge" :class="`rank-${Math.min(competitorIndex + 1, 3)}`">{{ competitorIndex + 1 }}</span>
                      {{ item.name }}
                    </h3>
                    <span :class="item.render_source_explanation ? 'verified' : 'unverified'">
                      {{ item.render_source_explanation ? '已核实来源' : '已配置竞品' }}
                    </span>
                  </div>
                  <p class="competitor-stat">被 AI 提及 <strong>{{ item.mention_count }}</strong> 次</p>
                  <div v-if="item.quotes?.length" class="quote-list">
                    <p v-for="quote in item.quotes" :key="quote.observation_id">
                      {{ truncateAiText(quote.excerpt, 150) }}
                      <span class="source">{{ platformLabel(quote.platform_code) }}</span>
                    </p>
                  </div>
                  <div v-if="item.render_source_explanation" class="source-block why-box">
                    <strong>为什么 AI 夸它</strong>
                    <p v-if="item.source_explanation">{{ item.source_explanation }}</p>
                    <p v-else-if="item.verified_sources?.[0]">
                      {{ item.verified_sources[0].source_note || item.verified_sources[0].source_url || '已核实来源' }}
                    </p>
                  </div>
                  <div v-else class="source-block why-box pending-source">
                    <strong>来源说明</strong>
                    <p>该竞品已纳入封板跟踪；待完成来源核实后，再展示“为什么 AI 夸它”。</p>
                  </div>
                </article>
              </div>
              <el-empty v-if="!canonical.competitors.tracked.length" description="封板时未配置竞品集，本模块缺少可对照对象。" />
              <div v-if="canonical.competitors.untracked_mentions.length" class="untracked">
                <span>其他被提及机构</span>
                <em v-for="item in canonical.competitors.untracked_mentions" :key="item.name">
                  {{ item.name }} · {{ item.mention_count }}
                </em>
              </div>
              <div class="gap-table">
                <div class="gap-head">
                  <span>问题</span>
                  <span>你</span>
                  <span>跟踪竞品</span>
                </div>
                <div v-for="row in visibleGapRows" :key="row.question_id" class="gap-row">
                  <span>{{ row.question_text || row.question_id }}</span>
                  <strong :class="row.you ? 'mark-ok' : 'mark-miss'">{{ row.you ? '✓' : '✗' }}</strong>
                  <p>
                    <em v-for="(present, name) in row.competitors" :key="String(name)" :class="present ? 'present' : ''">
                      {{ name }} {{ present ? '✓' : '✗' }}
                    </em>
                  </p>
                </div>
              </div>
            </section>

            <section id="sentiment" class="module">
              <div class="module-head">
                <span class="module-num">04 · AI'S IMPRESSION & SENTIMENT</span>
                <h2>AI 对你的印象与情感</h2>
                <p>多个平台分别怎么描述你，以及明确提到你时的情感倾向。</p>
              </div>
              <p class="sub-title">各平台对你的主要印象</p>
              <div class="impression-card-grid">
                <article v-for="row in platformImpressionRows" :key="`card-${row.platform_code}`" class="impression-card">
                  <div class="impression-card-head">
                    <span class="platform-icon" :class="platformIconClass(row.platform_code)">{{ platformInitial(row.platform_code) }}</span>
                    <strong>{{ platformLabel(row.platform_code) }}</strong>
                    <em :class="`state-${dominantImpressionKey(row).toLowerCase()}`">{{ dominantImpressionLabel(row) }}</em>
                  </div>
                  <p>{{ impressionSummary(row) }}</p>
                </article>
              </div>
              <div class="sentiment-panel">
                <div class="donut" :style="sentimentDonutStyle">
                  <span>{{ canonical.sentiment.denominator_evaluable_sentiment_count }}</span>
                </div>
                <div class="sentiment-copy">
                  <div class="sentiment-bars">
                    <div v-for="bar in sentimentBars" :key="bar.key" class="senti-bar">
                      <span class="senti-label" :style="{ color: bar.color }">{{ bar.label }}</span>
                      <div class="senti-track">
                        <div
                          class="senti-fill"
                          :style="{ width: formatRate(bar.rate), background: bar.color }"
                        />
                      </div>
                      <span class="senti-value">{{ formatRate(bar.rate) }} · {{ bar.count }} 次</span>
                    </div>
                  </div>
                  <div class="sentiment-legend">
                    <span><i class="seg-positive" />正向</span>
                    <span><i class="seg-neutral" />中性</span>
                    <span><i class="seg-negative" />负向</span>
                    <span><i class="seg-info_missing" />信息缺失</span>
                    <span><i class="seg-no_awareness" />无认知</span>
                  </div>
                  <p class="audit-note">
                    Donut 仅统计明确提及品牌且可判定情感的 {{ canonical.sentiment.denominator_evaluable_sentiment_count }} 次回答；
                    另有 {{ canonical.sentiment.unknown_count }} 次无法判定，仅作审计记录。
                  </p>
                  <div class="keyword-row">
                    <em v-for="word in canonical.sentiment.positive_keywords" :key="word">{{ word }}</em>
                  </div>
                </div>
              </div>
              <div v-if="canonical.sentiment.negative_evidence.length" class="negative-block">
                <p class="sub-title">
                  负面倾向提及 {{ canonical.sentiment.negative_evidence_count ?? canonical.sentiment.negative_evidence.length }} 次 · 代表性引用
                </p>
                <div class="negative-list">
                  <article v-for="item in canonical.sentiment.negative_evidence" :key="item.observation_id">
                    <span class="severity-badge" :class="`severity-${item.severity.toLowerCase()}`">{{ severityLabel(item.severity) }}</span>
                    <strong>{{ platformLabel(item.platform_code) }}</strong>
                    <p>{{ truncateAiText(item.excerpt, 180) }}</p>
                  </article>
                </div>
              </div>
              <div class="impression-list">
                <div class="impression-row impression-head">
                  <span>平台</span><span>印象结构</span><span>主要状态</span>
                </div>
                <div v-for="row in platformImpressionRows" :key="row.platform_code" class="impression-row">
                  <span>{{ platformLabel(row.platform_code) }}</span>
                  <div class="stack-bar" :title="impressionSummary(row)">
                    <i
                      v-for="segment in impressionSegments(row)"
                      :key="segment.key"
                      :class="`seg-${segment.key.toLowerCase()}`"
                      :style="{ width: `${segment.rate * 100}%` }"
                    />
                  </div>
                  <span class="dominant-state">{{ dominantImpressionLabel(row) }}</span>
                </div>
              </div>
            </section>

            <section id="coverage" class="module">
              <div class="module-head">
                <span class="module-num">05 · COVERAGE OVERVIEW</span>
                <h2>场景覆盖全景</h2>
                <p>把封板问题按价值、意图、平台铺开看，这是后续每期复测要逐格追踪的底图。</p>
              </div>
              <div class="metric-row">
                <article v-for="tier in canonical.coverage.value_tiers" :key="tier.value_tier" class="metric-card">
                  <span>{{ valueTierLabel(tier.value_tier) }}</span>
                  <strong>{{ formatRate(tier.appeared_rate) }}</strong>
                  <small>出现 {{ tier.appeared }} / 分母 {{ tier.denominator }} · 推荐 {{ tier.recommended }}</small>
                </article>
              </div>
              <p class="sub-title heatmap-title">意图 × 平台热力图（{{ canonical.brand.name }}主动被提及比例）</p>
              <div class="prototype-heatmap" :style="{ '--platform-count': String(heatmapPlatforms.length || 1) }">
                <div class="prototype-heatmap-spacer" />
                <div v-for="platform in heatmapPlatforms" :key="platform" class="prototype-heatmap-platform">
                  {{ platformLabel(platform) }}
                </div>
                <template v-for="group in groupedHeatmapRows" :key="group.key">
                  <div class="prototype-heatmap-spacer" />
                  <div class="prototype-heatmap-group" :style="{ gridColumn: `span ${heatmapPlatforms.length || 1}` }">
                    {{ group.label }}
                  </div>
                  <template v-for="row in group.rows" :key="row.intent">
                    <div class="prototype-heatmap-intent">
                      <strong>{{ intentLabel(row.intent) }}型</strong>
                      <small>{{ heatMetricLine(row.metricKind) }}</small>
                    </div>
                    <div
                      v-for="cell in row.cells"
                      :key="`${row.intent}-${cell.platform}`"
                      class="prototype-heatmap-cell"
                      :class="{ 'low-sample': cell.item?.low_sample || !cell.item }"
                      :style="prototypeHeatCellStyle(cell.item?.rate ?? 0, cell.item?.low_sample || !cell.item)"
                    >
                      {{ prototypeHeatCellLabel(cell.item) }}
                    </div>
                  </template>
                </template>
              </div>
              <div class="heatmap-legend">
                <span>分值</span>
                <i class="hm-bin hm-bin-0" /><span>0–10%</span>
                <i class="hm-bin hm-bin-1" /><span>10–20%</span>
                <i class="hm-bin hm-bin-2" /><span>20–30%</span>
                <i class="hm-bin hm-bin-3" /><span>30%+</span>
              </div>
              <p class="sub-title">各平台综合表现</p>
              <div class="platform-table">
                <div class="platform-row platform-head">
                  <span>平台</span><span>提及率</span><span>平均排名</span><span>主推荐</span><span>正向情感</span>
                </div>
                <div v-for="item in canonical.coverage.platforms" :key="item.platform_code" class="platform-row">
                  <span>{{ platformLabel(item.platform_code) }}</span>
                  <span><MetricBar :rate="item.mention_rate" /></span>
                  <span>{{ item.avg_ranking ?? '-' }}</span>
                  <span><MetricBar :rate="item.recommended_rate" /></span>
                  <span>{{ item.denominator ? '' : '无数据' }}<MetricBar v-if="item.denominator" :rate="item.positive_sentiment_rate" /></span>
                </div>
              </div>
              <p class="sub-title">四个原始维度（不合成总分、不做行业对标）</p>
              <div class="dimension-table">
                <div v-for="[key, item] in Object.entries(canonical.coverage.dimensions)" :key="key" class="dimension-row">
                  <span class="dimension-name">{{ dimensionLabel(key) }}</span>
                  <span class="dimension-meter">
                    <i><b :style="{ width: formatRate(item.rate) }" /></i>
                    <strong>{{ formatRate(item.rate) }}</strong>
                  </span>
                  <em>{{ formatBand(item.band) }}</em>
                </div>
              </div>
            </section>

            <section id="appendix" class="module appendix">
              <div class="module-head">
                <span class="module-num">06 · HOW WE'LL COMPARE</span>
                <h2>这条基线之后怎么用</h2>
                <p>未来每次复测都与本次封板问题、平台、版本口径对齐，成效只看真实差值。</p>
              </div>
              <div class="baseline-note">
                <div class="bn-head">
                  <div class="bn-icon">✓</div>
                  <h3>起跑线已锁定并存档</h3>
                </div>
                <p>
                  本报告的 <strong>{{ testScaleLabel }}</strong> 已冻结为快照
                  <strong>{{ baselineCode }}</strong>。今后每一次复测都会用完全相同的题目、平台和判定口径重新跑一遍。
                </p>
                <div class="bn-grid">
                  <div><span>基线锁定</span><strong>{{ snapshot?.status || 'SEALED' }}</strong></div>
                  <div><span>复测口径</span><strong>与基线一致</strong></div>
                  <div><span>对照方式</span><strong>逐指标差值并排</strong></div>
                </div>
              </div>
              <footer class="report-footer">
                <span>© 2024-2026 合肥市三合星链数字传媒科技有限公司 版权所有</span>
                <span>皖ICP备2026007423号-3</span>
              </footer>
            </section>
          </main>
        </section>

        <el-empty v-else description="暂无报告">
          <el-button v-if="snapshot?.status === 'SEALED'" type="primary" :loading="canonicalLoading" @click="recomputeCanonicalReport">
            生成报告
          </el-button>
        </el-empty>
      </el-tab-pane>

      <el-tab-pane label="旧轮询结果" name="legacy">
        <section class="panel">
          <div class="panel-header">
            <div>
              <h3>兼容轮询入口</h3>
              <p class="muted">M4 报告不从这里取数；此区域仅保留旧功能入口。</p>
            </div>
            <el-tag v-if="latestBatch" :type="batchTagType(latestBatch.status)">
              {{ batchStatusLabel(latestBatch.status) }}
            </el-tag>
          </div>

          <el-form label-width="110px">
            <el-form-item label="轮询平台">
              <el-checkbox-group v-model="selectedPlatformCodes">
                <el-checkbox v-for="platform in platforms" :key="platform.code" :label="platform.code" border>
                  {{ platform.name || platform.code }}
                  <span class="option-meta">{{ platform.priorityLevel }}</span>
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="问题分组">
              <el-checkbox-group v-model="selectedQuestionTiers">
                <el-checkbox v-for="tier in questionTiers" :key="tier.tier" :label="tier.tier" border>
                  {{ tier.tier }} 类
                  <span class="option-meta">{{ tier.questionCount }} 题</span>
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="polling" :disabled="expectedTotal <= 0" @click="startPoll">
                开始旧轮询
              </el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="resultLoading" :data="results" border empty-text="暂无轮询结果">
            <el-table-column prop="questionTier" label="分组" width="80" />
            <el-table-column prop="platformName" label="平台" width="150">
              <template #default="{ row }">{{ row.platformName || row.platformCode }}</template>
            </el-table-column>
            <el-table-column prop="questionText" label="问题" min-width="260" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'completed' ? 'success' : 'danger'">
                  {{ row.status === 'completed' ? '完成' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="结果" min-width="300" show-overflow-tooltip>
              <template #default="{ row }">{{ row.status === 'completed' ? row.responseText : row.errorMessage }}</template>
            </el-table-column>
          </el-table>
        </section>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelBaselineCollectTask,
  collectBaselineObservations,
  createBaselineExport,
  createBaselineSnapshot,
  downloadBaselineExportPdf,
  getBaselineCanonical,
  getBaselineCollectTask,
  getBaselineExport,
  getBaselinePrintRenderDetail,
  getBaselineReportOptions,
  getBaselineReportResults,
  getLatestBaselineCollectTask,
  getLatestBaselineSnapshot,
  recomputeBaselineCanonical,
  reviewBaselineSnapshot,
  sealBaselineSnapshot,
  startBaselineReportPoll,
  type BaselineCanonicalReport,
  type BaselineCellState,
  type BaselineCollectionTask,
  type BaselineIntentType,
  type BaselinePlatformOption,
  type BaselinePollBatch,
  type BaselinePollResult,
  type BaselineQuestionTierOption,
  type BaselineReportExportResponse,
  type BaselineSnapshot,
  type BaselineSnapshotCompetitorSource,
  type BaselineValueTier,
} from '@/api/baselineReport'

type EvidenceCard = BaselineCanonicalReport['evidence_cards'][number]
type PlatformImpressionRow = {
  platform_code: string
  POSITIVE: number
  NEUTRAL: number
  NEGATIVE: number
  INFO_MISSING: number
  NO_AWARENESS: number
}

const MetricBar = defineComponent({
  name: 'MetricBar',
  props: {
    rate: {
      type: Number,
      required: true,
    },
  },
  setup(props) {
    return () => h('span', { class: 'metric-bar' }, [
      h('i', { style: { width: `${Math.max(0, Math.min(1, props.rate || 0)) * 100}%` } }),
      h('b', formatRate(props.rate || 0)),
    ])
  },
})

declare global {
  interface Window {
    __PRESALE_PRINT_READY__?: boolean
    __PRESALE_PRINT_ERROR__?: string | null
  }
}

const route = useRoute()
const projectId = Number(route.params.id)
const renderToken = computed(() => String(route.params.renderToken || ''))
const isPrintMode = computed(() => Boolean(renderToken.value))

const activeTab = ref('report')
const loading = ref(false)
const snapshotLoading = ref(false)
const creating = ref(false)
const reviewing = ref(false)
const sealing = ref(false)
const collecting = ref(false)
const cancelingCollect = ref(false)
const canonicalLoading = ref(false)
const exporting = ref(false)
const polling = ref(false)
const resultLoading = ref(false)
const taskPollTimer = ref<number | null>(null)
const fakeProgressSeed = ref(0)

const platforms = ref<BaselinePlatformOption[]>([])
const questionTiers = ref<BaselineQuestionTierOption[]>([])
const latestBatch = ref<BaselinePollBatch | null>(null)
const results = ref<BaselinePollResult[]>([])
const snapshot = ref<BaselineSnapshot | null>(null)
const collectTask = ref<BaselineCollectionTask | null>(null)
const canonical = ref<BaselineCanonicalReport | null>(null)
const competitorSources = ref<BaselineSnapshotCompetitorSource[]>([])
const selectedPlatformCodes = ref<string[]>([])
const selectedQuestionTiers = ref<string[]>(['A'])
const page = reactive({ current: 1, size: 20, total: 0 })

const selectedQuestionCount = computed(() => questionTiers.value
  .filter((item) => selectedQuestionTiers.value.includes(item.tier))
  .reduce((sum, item) => sum + Number(item.questionCount || 0), 0))

const expectedTotal = computed(() => selectedPlatformCodes.value.length * selectedQuestionCount.value)

const collectionPercent = computed(() => {
  if (!collectTask.value?.totalObservationCount) return 0
  const done = collectTask.value.successObservationCount + collectTask.value.failedObservationCount
  return Math.min(100, Math.round((done / collectTask.value.totalObservationCount) * 100))
})

const visibleCollectionPercent = computed(() => {
  if (!collectTask.value) return 0
  if (collectionPercent.value > 0) return collectionPercent.value
  if (collectTask.value.status === 'PENDING') return Math.min(8, 2 + fakeProgressSeed.value)
  if (collectTask.value.status === 'RUNNING') return Math.min(12, 5 + fakeProgressSeed.value)
  return 0
})

const collectionProgressText = computed(() => {
  if (!collectTask.value) return ''
  if (collectTask.value.status === 'PENDING') {
    return `排队中 · 第 ${collectTask.value.queuePosition || 1} 位 / 最大并发 ${collectTask.value.maxConcurrentBaselines || 1}`
  }
  if (collectTask.value.status === 'RUNNING' && collectionPercent.value === 0) {
    return `已开始请求，等待首批 AI 返回 · 总计 ${collectTask.value.totalObservationCount}`
  }
  return `成功 ${collectTask.value.successObservationCount} / ${collectTask.value.totalObservationCount}，失败 ${collectTask.value.failedObservationCount}`
})

const canCancelCollectTask = computed(() => collectTask.value
  && ['PENDING', 'RUNNING'].includes(collectTask.value.status))

const reportNavItems = [
  { id: 'method', no: '00', label: '关于本基线' },
  { id: 'findings', no: '01', label: '核心结论' },
  { id: 'evidence', no: '02', label: 'AI 原话证据' },
  { id: 'competitors', no: '03', label: '对手在 AI 里' },
  { id: 'sentiment', no: '04', label: '印象与情感' },
  { id: 'coverage', no: '05', label: '场景覆盖全景' },
  { id: 'appendix', no: '06', label: '可比基线说明' },
]

const platformCount = computed(() => new Set(canonical.value?.cells.map((cell) => cell.platform_code) || []).size)

const questionCount = computed(() => snapshot.value?.questionCount || new Set(canonical.value?.cells.map((cell) => cell.question_id) || []).size)

const testScaleLabel = computed(() => `${questionCount.value} 题 × ${platformCount.value} 平台 × 3 次`)

const baselineCode = computed(() => {
  const id = snapshot.value?.id || canonical.value?.meta?.baseline_id
  return id ? `BSL-${id}` : 'BSL-待生成'
})

const primaryEvidenceCards = computed(() => canonical.value?.evidence_cards.slice(0, 15) || [])

const evidenceGroups = computed(() => {
  const grouped = new Map<BaselineIntentType, EvidenceCard[]>()
  for (const card of primaryEvidenceCards.value) {
    const intent = card.intent_type || 'SCENE'
    if (!grouped.has(intent)) grouped.set(intent, [])
    grouped.get(intent)!.push(card)
  }
  const order: BaselineIntentType[] = ['RECOMMENDATION', 'COMPARISON', 'PROBLEM', 'AWARENESS', 'SCENE']
  return order
    .filter((intent) => grouped.has(intent))
    .map((intent) => ({ intent, cards: grouped.get(intent)! }))
})

const visibleGapRows = computed(() => {
  const rows = canonical.value?.competitor_gap_matrix || []
  const focused = rows.filter((row) => row.intent_type === 'RECOMMENDATION' || row.intent_type === 'COMPARISON')
  return (focused.length ? focused : rows).slice(0, 12)
})

type HeatmapItem = BaselineCanonicalReport['coverage']['heatmap'][number]
const heatmapPlatforms = computed(() => {
  const codes = new Set<string>()
  for (const item of canonical.value?.coverage.heatmap || []) {
    codes.add(item.platform_code)
  }
  return Array.from(codes)
})

const heatmapRows = computed(() => {
  const order: BaselineIntentType[] = ['RECOMMENDATION', 'SCENE', 'PROBLEM', 'AWARENESS', 'COMPARISON']
  const items = canonical.value?.coverage.heatmap || []
  return order
    .filter((intent) => items.some((item) => item.intent_type === intent))
    .map((intent) => {
      const byPlatform = new Map(items
        .filter((item) => item.intent_type === intent)
        .map((item) => [item.platform_code, item] as const))
      const first = items.find((item) => item.intent_type === intent)
      return {
        intent,
        metricKind: first?.metric_kind || 'mention_rate',
        cells: heatmapPlatforms.value.map((platform) => ({
          platform,
          item: byPlatform.get(platform),
        })),
      }
    })
})

const groupedHeatmapRows = computed(() => {
  const byIntent = new Map(heatmapRows.value.map((row) => [row.intent, row]))
  const groups: Array<{ key: string; label: string; intents: BaselineIntentType[] }> = [
    { key: 'new_entry', label: '新客入口', intents: ['RECOMMENDATION', 'SCENE', 'PROBLEM'] },
    { key: 'returning_decision', label: '老客决策', intents: ['AWARENESS', 'COMPARISON'] },
  ]
  return groups
    .map((group) => ({
      key: group.key,
      label: group.label,
      rows: group.intents.map((intent) => byIntent.get(intent)).filter((row): row is NonNullable<typeof row> => Boolean(row)),
    }))
    .filter((group) => group.rows.length > 0)
})

const platformImpressionRows = computed(() => {
  const rows = new Map<string, PlatformImpressionRow>()
  for (const item of canonical.value?.sentiment.platform_impressions || []) {
    const row = rows.get(item.platform_code) || {
      platform_code: item.platform_code,
      POSITIVE: 0,
      NEUTRAL: 0,
      NEGATIVE: 0,
      INFO_MISSING: 0,
      NO_AWARENESS: 0,
    }
    row[item.impression_state] = item.count
    rows.set(item.platform_code, row)
  }
  return Array.from(rows.values())
})

function impressionTotal(row: PlatformImpressionRow) {
  return row.POSITIVE + row.NEUTRAL + row.NEGATIVE + row.INFO_MISSING + row.NO_AWARENESS
}

function impressionSegments(row: PlatformImpressionRow) {
  const total = impressionTotal(row)
  const segments = [
    { key: 'POSITIVE', label: '正向', count: row.POSITIVE },
    { key: 'NEUTRAL', label: '中性', count: row.NEUTRAL },
    { key: 'NEGATIVE', label: '负向', count: row.NEGATIVE },
    { key: 'INFO_MISSING', label: '信息缺失', count: row.INFO_MISSING },
    { key: 'NO_AWARENESS', label: '无认知', count: row.NO_AWARENESS },
  ]
  return segments
    .filter((segment) => segment.count > 0)
    .map((segment) => ({
      ...segment,
      rate: total > 0 ? segment.count / total : 0,
    }))
}

function dominantImpressionLabel(row: PlatformImpressionRow) {
  const first = dominantImpressionSegment(row)
  return first ? `${first.label} ${first.count} 次` : '无数据'
}

function dominantImpressionKey(row: PlatformImpressionRow) {
  return dominantImpressionSegment(row)?.key || 'INFO_MISSING'
}

function dominantImpressionSegment(row: PlatformImpressionRow) {
  return impressionSegments(row)
    .sort((left, right) => right.count - left.count)[0]
}

function impressionSummary(row: PlatformImpressionRow) {
  return impressionSegments(row)
    .map((segment) => `${segment.label} ${segment.count} 次`)
    .join(' / ') || '无数据'
}

const recommendationIntentRate = computed(() => {
  const items = canonical.value?.coverage.heatmap.filter((item) => item.intent_type === 'RECOMMENDATION') || []
  const denominator = items.reduce((sum, item) => sum + item.n, 0)
  if (denominator <= 0) return canonical.value?.coverage.rate || 0
  const positive = items.reduce((sum, item) => sum + item.positive, 0)
  return positive / denominator
})

const topTrackedCompetitors = computed(() =>
  [...(canonical.value?.competitors.tracked || [])].sort((a, b) => b.mention_count - a.mention_count).slice(0, 3),
)

const sentimentDonutStyle = computed(() => {
  if (!canonical.value) return {}
  const denominator = canonical.value.sentiment.denominator_evaluable_sentiment_count || 0
  if (denominator <= 0) {
    return { background: '#e5e7eb' }
  }
  const positive = canonical.value.sentiment.distribution.POSITIVE / denominator * 360
  const neutral = canonical.value.sentiment.distribution.NEUTRAL / denominator * 360
  return {
    background: `conic-gradient(#16a34a 0deg ${positive}deg, #64748b ${positive}deg ${positive + neutral}deg, #dc2626 ${positive + neutral}deg 360deg)`,
  }
})

const sentimentBars = computed(() => {
  const denominator = canonical.value?.sentiment.denominator_evaluable_sentiment_count || 0
  const distribution = canonical.value?.sentiment.distribution
  return [
    { key: 'POSITIVE', label: '正向', count: distribution?.POSITIVE || 0, color: '#0f6e56' },
    { key: 'NEUTRAL', label: '中性', count: distribution?.NEUTRAL || 0, color: '#64748b' },
    { key: 'NEGATIVE', label: '负向', count: distribution?.NEGATIVE || 0, color: '#a32d2d' },
  ].map((item) => ({
    ...item,
    rate: denominator > 0 ? item.count / denominator : 0,
  }))
})

async function loadOptions() {
  loading.value = true
  try {
    const { data } = await getBaselineReportOptions(projectId)
    const payload = data.data
    platforms.value = payload.platforms || []
    questionTiers.value = payload.questionTiers || []
    latestBatch.value = payload.latestBatch || null
    selectedPlatformCodes.value = platforms.value.map((item) => item.code)
    selectedQuestionTiers.value = questionTiers.value
      .filter((item) => Number(item.questionCount || 0) > 0)
      .map((item) => item.tier)
    await loadResults(1)
  } finally {
    loading.value = false
  }
}

async function loadSnapshot() {
  snapshotLoading.value = true
  try {
    const { data } = await getLatestBaselineSnapshot(projectId)
    snapshot.value = data.data || null
    syncCompetitorSources()
    canonical.value = null
    collectTask.value = null
    if (snapshot.value?.status === 'SEALED') {
      await Promise.all([loadLatestTask(), loadCanonicalSilently()])
    }
  } finally {
    snapshotLoading.value = false
  }
}

async function loadLatestTask() {
  if (!snapshot.value) return
  try {
    const { data } = await getLatestBaselineCollectTask(projectId, snapshot.value.id, true)
    collectTask.value = data.data || null
    if (collectTask.value && ['PENDING', 'RUNNING'].includes(collectTask.value.status)) {
      startTaskPolling()
    }
  } catch {
    collectTask.value = null
  }
}

function startTaskPolling() {
  stopTaskPolling()
  taskPollTimer.value = window.setInterval(async () => {
    if (!snapshot.value || !collectTask.value) return
    if (!['PENDING', 'RUNNING'].includes(collectTask.value.status)) {
      stopTaskPolling()
      return
    }
    fakeProgressSeed.value = (fakeProgressSeed.value + 1) % 6
    try {
      const { data } = await getBaselineCollectTask(projectId, snapshot.value.id, collectTask.value.taskId)
      collectTask.value = data.data
      if (collectTask.value && !['PENDING', 'RUNNING'].includes(collectTask.value.status)) {
        stopTaskPolling()
      }
    } catch {
      // Keep the visible progress moving; the next manual refresh can recover status.
    }
  }, 3000)
}

function stopTaskPolling() {
  if (taskPollTimer.value !== null) {
    window.clearInterval(taskPollTimer.value)
    taskPollTimer.value = null
  }
}

async function loadCanonicalSilently() {
  if (!snapshot.value) return
  try {
    const { data } = await getBaselineCanonical(projectId, snapshot.value.id, true)
    canonical.value = parseCanonical(data.data.canonicalJson)
  } catch {
    canonical.value = null
  }
}

async function createDraft() {
  await ElMessageBox.confirm('创建新的 DRAFT 快照会从当前问题池复制题目文本。', '创建基线快照', {
    type: 'warning',
    confirmButtonText: '创建',
    cancelButtonText: '取消',
  })
  creating.value = true
  try {
    const { data } = await createBaselineSnapshot(projectId, { sourcePollBatchId: latestBatch.value?.id })
    snapshot.value = data.data
    syncCompetitorSources()
    canonical.value = null
    activeTab.value = 'review'
    ElMessage.success('DRAFT 已创建')
  } finally {
    creating.value = false
  }
}

async function saveReview() {
  if (!snapshot.value) return
  reviewing.value = true
  try {
    const { data } = await reviewBaselineSnapshot(projectId, snapshot.value.id, {
      questions: snapshot.value.questions.map((question) => ({
        questionSnapshotId: question.id,
        intentType: question.intentType,
        valueTier: question.valueTier,
      })),
      competitorSources: normalizedCompetitorSources(),
    })
    snapshot.value = data.data
    syncCompetitorSources()
    ElMessage.success('复核已保存')
  } finally {
    reviewing.value = false
  }
}

async function sealDraft() {
  if (!snapshot.value) return
  if (!normalizedCompetitorSources().length) {
    await ElMessageBox.confirm('当前未配置竞品集，封板后「对手在 AI 里的样子」模块会缺少核心内容。仍要继续封板吗？', '竞品集为空', {
      type: 'warning',
      confirmButtonText: '继续封板',
      cancelButtonText: '返回配置',
    })
  }
  await ElMessageBox.confirm('封板后题目、价值层和意图类型将不可修改。', '确认封板', {
    type: 'warning',
    confirmButtonText: '封板',
    cancelButtonText: '取消',
  })
  sealing.value = true
  try {
    const { data } = await sealBaselineSnapshot(projectId, snapshot.value.id)
    snapshot.value = data.data
    syncCompetitorSources()
    ElMessage.success('基线已封板')
  } finally {
    sealing.value = false
  }
}

async function collectObservations() {
  if (!snapshot.value) return
  collecting.value = true
  try {
    const { data } = await collectBaselineObservations(projectId, snapshot.value.id, {
      platformCodes: selectedPlatformCodes.value.length ? selectedPlatformCodes.value : undefined,
    })
    collectTask.value = data.data
    startTaskPolling()
    ElMessage.success('采集任务已入队')
  } finally {
    collecting.value = false
  }
}

async function cancelCollectTask() {
  if (!snapshot.value || !collectTask.value) return
  await ElMessageBox.confirm('确认取消当前采集任务？已完成的观测记录会保留，但不会继续发起新查询。', '取消采集', {
    type: 'warning',
    confirmButtonText: '取消采集',
    cancelButtonText: '返回',
  })
  cancelingCollect.value = true
  try {
    const { data } = await cancelBaselineCollectTask(projectId, snapshot.value.id, collectTask.value.taskId)
    collectTask.value = data.data
    stopTaskPolling()
    ElMessage.success('采集任务已取消')
  } finally {
    cancelingCollect.value = false
  }
}

async function recomputeCanonicalReport() {
  if (!snapshot.value) return
  canonicalLoading.value = true
  try {
    const { data } = await recomputeBaselineCanonical(projectId, snapshot.value.id)
    canonical.value = parseCanonical(data.data.canonicalJson)
    activeTab.value = 'report'
    ElMessage.success('canonical 已生成')
  } finally {
    canonicalLoading.value = false
  }
}

function parseCanonical(raw: string): BaselineCanonicalReport {
  const parsed = JSON.parse(raw) as BaselineCanonicalReport
  if (parsed.meta?.schema_version !== 'baseline_canonical_v1') {
    throw new Error('baseline canonical schema mismatch')
  }
  return parsed
}

function syncCompetitorSources() {
  competitorSources.value = (snapshot.value?.competitorSources || []).map((item) => ({
    competitorId: item.competitorId ?? null,
    competitorName: item.competitorName || '',
    aliasesJson: aliasesJsonToInput(item.aliasesJson),
    sourceType: item.sourceType || '',
    sourceUrl: item.sourceUrl || '',
    sourceNote: item.sourceNote || '',
    reviewStatus: item.reviewStatus || 'UNVERIFIED',
  }))
}

function normalizedCompetitorSources() {
  return competitorSources.value
    .map((item) => ({
      ...item,
      competitorName: item.competitorName?.trim() || '',
      aliasesJson: item.aliasesJson?.trim() || '',
      sourceType: item.sourceType?.trim() || '',
      sourceUrl: item.sourceUrl?.trim() || '',
      sourceNote: item.sourceNote?.trim() || '',
      reviewStatus: item.reviewStatus || 'UNVERIFIED',
    }))
    .filter((item) => item.competitorName)
}

function addCompetitorSource() {
  competitorSources.value.push({
    competitorName: '',
    aliasesJson: '',
    sourceType: '',
    sourceUrl: '',
    sourceNote: '',
    reviewStatus: 'UNVERIFIED',
  })
}

function removeCompetitorSource(index: number) {
  competitorSources.value.splice(index, 1)
}

function aliasesJsonToInput(raw?: string | null) {
  if (!raw) return ''
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) {
      return parsed.map((item) => String(item).trim()).filter(Boolean).join('，')
    }
  } catch {
    // Keep manually-entered comma-separated text readable.
  }
  return raw
}

async function exportPdf() {
  if (!snapshot.value || !canonical.value) return
  exporting.value = true
  try {
    const { data } = await createBaselineExport(projectId, snapshot.value.id)
    await waitExportAndDownload(data.data)
  } catch (error) {
    const err = error as { code?: number; data?: { runningExportId?: number } }
    if (err.code === 40901 && err.data?.runningExportId && snapshot.value) {
      await waitExportAndDownload({ exportId: err.data.runningExportId, baselineId: snapshot.value.id, projectId, status: 'RUNNING', idempotencyKey: '' })
      return
    }
    throw error
  } finally {
    exporting.value = false
  }
}

async function waitExportAndDownload(task: BaselineReportExportResponse) {
  if (!snapshot.value) return
  let current = task
  for (let i = 0; i < 90; i += 1) {
    if (current.status === 'SUCCESS') {
      await downloadBaselineExportPdf(projectId, snapshot.value.id, current.exportId)
      ElMessage.success('PDF 已生成')
      return
    }
    if (current.status === 'FAILED') {
      ElMessage.error(current.errorMsg || 'PDF 导出失败')
      return
    }
    await new Promise((resolve) => window.setTimeout(resolve, 2000))
    const { data } = await getBaselineExport(projectId, snapshot.value.id, current.exportId)
    current = data.data
  }
  ElMessage.warning('PDF 导出仍在处理中，请稍后重试下载')
}

async function loadPrintPayload() {
  window.__PRESALE_PRINT_READY__ = false
  window.__PRESALE_PRINT_ERROR__ = null
  try {
    const { data } = await getBaselinePrintRenderDetail(renderToken.value)
    canonical.value = data.data.canonical
    if (canonical.value?.meta?.schema_version !== 'baseline_canonical_v1') {
      throw new Error('baseline canonical schema mismatch')
    }
    activeTab.value = 'report'
    await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    window.__PRESALE_PRINT_METRICS__ = collectPrintMetrics()
    window.__PRESALE_PRINT_READY__ = true
  } catch (error) {
    const message = (error as Error).message || 'Load baseline print failed'
    window.__PRESALE_PRINT_ERROR__ = message
    throw error
  }
}

function collectPrintMetrics() {
  const pageCount = Math.max(1, document.querySelectorAll('.print-page-break').length + 1)
  const clippedCards = Array.from(document.querySelectorAll<HTMLElement>(
    '.evidence-card, .competitor-card, .quote-list p, .negative-list article, .gap-table, .prototype-heatmap',
  ))
    .filter((item) => item.scrollHeight > item.clientHeight + 1)
    .length
  return {
    page_count: pageCount,
    chart_count: 0,
    charts_with_data: 0,
    canvas_non_blank: true,
    bottom_band_ok: true,
    overflow_pages: [],
    content_overflows: clippedCards > 0
      ? [{
          pageId: 'baseline-report',
          block: '证据卡',
          field: 'excerpt',
          overflowPx: clippedCards,
          scrollHeight: clippedCards,
          clientHeight: 0,
          scrollWidth: 0,
          clientWidth: 0,
        }]
      : [],
    ready_elapsed_ms: 0,
    device_scale_factor: window.devicePixelRatio || 1,
    viewport: {
      width: window.innerWidth,
      height: window.innerHeight,
    },
    charts: [],
  }
}

async function loadResults(current = page.current) {
  resultLoading.value = true
  try {
    const { data } = await getBaselineReportResults(projectId, {
      batchId: latestBatch.value?.id,
      current,
      size: page.size,
    })
    const payload = data.data
    results.value = payload.records || []
    page.current = payload.current || current
    page.size = payload.size || page.size
    page.total = payload.total || 0
  } finally {
    resultLoading.value = false
  }
}

async function startPoll() {
  if (expectedTotal.value <= 0) {
    ElMessage.warning('请选择平台和问题分组')
    return
  }
  await ElMessageBox.confirm(`确认开始 ${expectedTotal.value} 次旧轮询？`, '基线检测报告', {
    type: 'warning',
    confirmButtonText: '开始',
    cancelButtonText: '取消',
  })
  polling.value = true
  try {
    const { data } = await startBaselineReportPoll(projectId, {
      platformCodes: selectedPlatformCodes.value,
      questionTiers: selectedQuestionTiers.value,
    })
    latestBatch.value = data.data
    ElMessage.success('轮询结果已保存')
    await loadResults(1)
  } finally {
    polling.value = false
  }
}

function formatRate(rate?: number | null) {
  if (rate === null || rate === undefined) return '-'
  return `${Math.round(rate * 100)}%`
}

function formatBand(band?: { low: number; high: number; method: string }) {
  if (!band || band.method === 'NO_SAMPLE') return '无数据'
  return `${formatRate(band.low)}-${formatRate(band.high)}`
}

function platformLabel(code?: string) {
  const fallback: Record<string, string> = {
    deepseek: 'Deepseek',
    doubao: '豆包',
    wenxin: '文心一言',
    zhipu: '智谱',
    kimi: 'Kimi',
    hunyuan: '腾讯混元',
    mimo: '小米 mimo',
    hailuo: '海螺',
    zhinao: '360 智脑',
    tongyi: '通义千问',
  }
  return platforms.value.find((item) => item.code === code)?.name || (code ? fallback[code] || code : '-')
}

function platformInitial(code?: string) {
  const label = platformLabel(code)
  if (!label || label === '-') return 'AI'
  if (/deepseek/i.test(label)) return 'D'
  if (/kimi/i.test(label)) return 'K'
  return label.slice(0, 1)
}

function platformIconClass(code?: string) {
  const normalized = (code || '').toLowerCase()
  if (normalized.includes('doubao')) return 'pi-doubao'
  if (normalized.includes('tongyi')) return 'pi-tongyi'
  if (normalized.includes('deepseek')) return 'pi-deepseek'
  if (normalized.includes('hailuo')) return 'pi-hailuo'
  if (normalized.includes('hunyuan')) return 'pi-hunyuan'
  if (normalized.includes('mimo')) return 'pi-mimo'
  if (normalized.includes('zhinao') || normalized.includes('360')) return 'pi-360'
  if (normalized.includes('zhipu')) return 'pi-zhipu'
  return ''
}

function valueTierLabel(tier: BaselineValueTier) {
  return ({ HIGH: '高价值', MID: '中价值', LOW: '低价值' } as Record<BaselineValueTier, string>)[tier]
}

function sourceTierLabel(tier?: string | null) {
  if (tier === 'A') return 'A 类'
  if (tier === 'B') return 'B 类'
  if (tier === 'C') return 'C 类'
  return '-'
}

function intentLabel(intent: BaselineIntentType) {
  return ({
    RECOMMENDATION: '推荐',
    COMPARISON: '对比',
    PROBLEM: '问题',
    AWARENESS: '认知',
    SCENE: '场景',
  } as Record<BaselineIntentType, string>)[intent]
}

function intentTagClass(intent?: BaselineIntentType | null) {
  if (intent === 'RECOMMENDATION') return 'case-tag-recommend'
  if (intent === 'COMPARISON') return 'case-tag-compare'
  if (intent === 'PROBLEM') return 'case-tag-question'
  if (intent === 'AWARENESS') return 'case-tag-cognitive'
  return 'case-tag-scenario'
}

function metricKindLabel(kind: string) {
  if (kind === 'awareness') return 'AI 了解度'
  if (kind === 'favorability') return '偏向度'
  return '主动提及率'
}

function metricKindNote(kind: string) {
  if (kind === 'awareness') return '认知型为了解度, 不与提及率横比'
  if (kind === 'favorability') return '对比型为偏向度, 不与提及率横比'
  return '推荐/问题/场景型为主动提及率'
}

function heatCellLabel(item?: HeatmapItem) {
  if (!item) return '无数据'
  if (item.low_sample) return `${item.positive}/${item.n} 题`
  return formatRate(item.rate)
}

function heatCellStyle(rate: number, lowSample?: boolean) {
  if (lowSample) return {}
  let background = 'var(--hm-0)'
  let color = 'var(--c-text-2)'
  if (rate >= 0.75) {
    background = 'var(--hm-4)'
    color = '#fff'
  } else if (rate >= 0.5) {
    background = 'var(--hm-3)'
    color = '#0a1f3d'
  } else if (rate >= 0.25) {
    background = 'var(--hm-2)'
    color = '#0a1f3d'
  } else if (rate > 0) {
    background = 'var(--hm-1)'
    color = '#0a1f3d'
  }
  return {
    background,
    color,
  }
}

function heatMetricLine(kind: string) {
  if (kind === 'awareness') return 'AI 了解度'
  if (kind === 'favorability') return '是否偏向你'
  return '主动提及'
}

function prototypeHeatCellLabel(item?: HeatmapItem) {
  if (!item) return '0%'
  return formatRate(item.rate)
}

function prototypeHeatCellStyle(rate: number, lowSample?: boolean) {
  if (lowSample) {
    return {
      background: 'var(--hm0)',
      color: 'var(--c-text-3)',
    }
  }
  let background = 'var(--hm0)'
  if (rate >= 0.3) {
    background = 'var(--hm3)'
  } else if (rate >= 0.2) {
    background = 'var(--hm2)'
  } else if (rate >= 0.1) {
    background = 'var(--hm1)'
  } else if (rate > 0) {
    background = 'var(--hm0)'
  }
  return {
    background,
    color: '#0a1f3d',
  }
}

function dimensionLabel(key: string) {
  return ({
    visibility: '可见覆盖',
    recommendation: '推荐覆盖',
    sentiment_positive: '正向情感',
    stability: '稳定性',
  } as Record<string, string>)[key] || key
}

function cellStateMeta(state: BaselineCellState) {
  const meta: Record<BaselineCellState, { label: string; type: 'success' | 'warning' | 'info' | 'danger' }> = {
    STABLE_PRESENT: { label: '稳定出现', type: 'success' },
    STABLE_ABSENT: { label: '稳定缺席', type: 'info' },
    UNSTABLE_PARTIAL: { label: '不稳定', type: 'warning' },
    INSUFFICIENT_SAMPLE: { label: '采样不足', type: 'warning' },
    NO_DATA: { label: '无数据', type: 'danger' },
  }
  return meta[state]
}

function severityType(severity: 'HIGH' | 'MID' | 'LOW') {
  if (severity === 'HIGH') return 'danger'
  if (severity === 'MID') return 'warning'
  return 'info'
}

function severityLabel(severity: 'HIGH' | 'MID' | 'LOW') {
  if (severity === 'HIGH') return '需重点关注'
  if (severity === 'MID') return '需关注'
  return '轻度关注'
}

function impressionLabel(state: string) {
  if (state === 'INFO_MISSING') return '信息缺失'
  if (state === 'NO_AWARENESS') return '无认知'
  if (state === 'POSITIVE') return '正向'
  if (state === 'NEGATIVE') return '负向'
  return '中性'
}

function highlightedSegments(card: EvidenceCard) {
  const text = card.raw_response_excerpt || ''
  const spans = [...card.highlight_spans]
    .filter((span) => span.start_offset >= 0 && span.end_offset > span.start_offset && span.end_offset <= text.length)
    .sort((left, right) => left.start_offset - right.start_offset)
  const segments: Array<{ text: string; highlightType?: string }> = []
  let cursor = 0
  for (const span of spans) {
    if (span.start_offset < cursor) continue
    if (span.start_offset > cursor) {
      segments.push({ text: cleanAiText(text.slice(cursor, span.start_offset)) })
    }
    segments.push({ text: cleanAiText(text.slice(span.start_offset, span.end_offset)), highlightType: span.type })
    cursor = span.end_offset
  }
  if (cursor < text.length) {
    segments.push({ text: cleanAiText(text.slice(cursor)) })
  }
  return segments.length ? segments.filter((segment) => segment.text) : [{ text: cleanAiText(text) }]
}

function cleanAiText(text?: string | null) {
  if (!text) return ''
  return text
    .replace(/\\r\\n/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, ' ')
    .replace(/```[\s\S]*?```/g, (block) => block.replace(/```[a-zA-Z]*|```/g, ''))
    .replace(/^#{1,6}\s*/gm, '')
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/__(.*?)__/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/^\s*\d+[.)]\s+/gm, '')
    .replace(/[ \t\v\f\r]+/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function truncateAiText(text?: string | null, maxLength = 160) {
  const cleaned = cleanAiText(text).replace(/\s*\n\s*/g, ' ')
  if (cleaned.length <= maxLength) return cleaned
  return `${cleaned.slice(0, maxLength).trim()}...`
}

function taskStatusLabel(status?: string) {
  if (status === 'COMPLETED') return '完成'
  if (status === 'PARTIAL_FAILED') return '部分失败'
  if (status === 'FAILED') return '失败'
  if (status === 'CANCELED') return '已取消'
  if (status === 'RUNNING') return '运行中'
  return '等待中'
}

function batchStatusLabel(status?: string) {
  if (status === 'completed') return '已完成'
  if (status === 'failed') return '失败'
  if (status === 'running') return '运行中'
  return status || '-'
}

function batchTagType(status?: string) {
  if (status === 'completed') return 'success'
  if (status === 'failed') return 'danger'
  return 'warning'
}

onMounted(async () => {
  if (isPrintMode.value) {
    await loadPrintPayload()
    return
  }
  await loadOptions()
  await loadSnapshot()
})

onUnmounted(() => {
  stopTaskPolling()
})
</script>

<style scoped>
.baseline-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.baseline-page,
.baseline-page * {
  box-sizing: border-box;
}

.baseline-page.print-mode {
  width: 210mm;
  min-height: 297mm;
  margin: 0 auto;
  gap: 0;
  background: #fff;
}

.workflow,
.panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 18px;
}

.print-mode :deep(.el-tabs__header) {
  display: none;
}

.print-mode .baseline-tabs {
  width: 100%;
}

.workflow-main,
.panel-header,
.panel-subhead,
.competitor-head,
.evidence-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.workflow-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.task-strip {
  display: grid;
  grid-template-columns: 180px minmax(180px, 1fr) auto auto;
  align-items: center;
  gap: 14px;
  margin-top: 16px;
}

.snapshot-warning {
  margin-bottom: 12px;
}

.competitor-review {
  margin-top: 18px;
}

.panel-subhead {
  margin-bottom: 12px;
}

.panel-subhead h4 {
  margin: 0 0 2px;
}

.competitor-source-list {
  display: grid;
  gap: 10px;
}

.competitor-source-row {
  display: grid;
  grid-template-columns: 1.1fr 1.2fr 0.8fr 2fr 140px 70px;
  gap: 8px;
  align-items: center;
}

.eyebrow {
  margin: 0 0 4px;
  color: #64748b;
  font-size: 12px;
  text-transform: uppercase;
}

h1,
h2,
h3,
p {
  margin: 0;
}

.muted {
  color: #64748b;
  font-size: 13px;
}

.baseline-tabs {
  --el-tabs-header-height: 44px;
}

.delivery-report {
  --c-bg: #fafaf7;
  --c-surface: #fff;
  --c-surface-2: #f5f4ee;
  --c-border: rgba(0, 0, 0, 0.08);
  --c-border-strong: rgba(0, 0, 0, 0.16);
  --c-text-1: #1a1a1a;
  --c-text-2: #5a5a56;
  --c-text-3: #8a8a85;
  --c-accent: #185fa5;
  --c-accent-bg: #e6f1fb;
  --c-success: #0f6e56;
  --c-success-bg: #e1f5ee;
  --c-warn: #854f0b;
  --c-warn-bg: #faeeda;
  --c-danger: #a32d2d;
  --c-danger-bg: #fcebeb;
  --c-purple: #534ab7;
  --c-purple-bg: #eeedfe;
  --c-orange: #d85a30;
  --c-orange-bg: #faece7;
  --c-navy: #0a1f3d;
  --hm-0: #f5f8ff;
  --hm-1: #e4edff;
  --hm-2: #c7d9ff;
  --hm-3: #92b4ff;
  --hm-4: #5f8df2;
  --hm0: #ffffff;
  --hm1: #eaf0f7;
  --hm2: #cfe0f1;
  --hm3: #9cbfe3;
  --r-md: 8px;
  --r-lg: 12px;
  --r-xl: 16px;
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  min-height: 100vh;
  background: var(--c-bg);
  color: var(--c-text-1);
  font-family: Inter, 'Noto Sans SC', -apple-system, BlinkMacSystemFont, sans-serif;
  line-height: 1.6;
}

.report-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
  background: var(--c-surface);
  border-right: 1px solid var(--c-border);
  padding: 24px 0;
}

.sidebar-header {
  padding: 0 24px 20px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--c-border);
}

.sidebar-logo {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.sidebar-client {
  margin: 4px 0 0;
  color: var(--c-text-3);
  font-size: 11px;
}

.report-nav {
  padding: 0 12px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  margin: 1px 0;
  border-radius: var(--r-md);
  color: var(--c-text-2);
  font-size: 13px;
  text-decoration: none;
}

.nav-item:hover {
  background: var(--c-surface-2);
  color: var(--c-accent);
}

.nav-num {
  min-width: 18px;
  color: var(--c-text-3);
  font-size: 10px;
  font-weight: 600;
}

.sidebar-foot {
  margin-top: 16px;
  padding: 18px 24px 0;
  border-top: 1px solid var(--c-border);
  color: var(--c-text-3);
  font-size: 10px;
}

.report-main {
  width: 100%;
  max-width: 1080px;
  padding: 32px 48px 80px;
}

.module {
  margin-bottom: 56px;
  scroll-margin-top: 24px;
}

.module-head {
  margin-bottom: 24px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--c-border);
}

.module-num {
  display: inline-block;
  margin-bottom: 6px;
  color: var(--c-text-3);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.module-head h2 {
  margin: 0 0 6px;
  color: var(--c-text-1);
  font-size: 22px;
  font-weight: 600;
}

.module-head p {
  margin: 0;
  color: var(--c-text-2);
  font-size: 13px;
}

.sub-title {
  margin: 0 0 14px;
  color: var(--c-text-1);
  font-size: 14px;
  font-weight: 600;
}

.report-banner {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 32px;
  align-items: center;
  margin-bottom: 40px;
  padding: 36px 40px;
  color: #fff;
  background: linear-gradient(135deg, #0a1f3d 0%, #1a3866 100%);
  border-radius: var(--r-xl);
}

.banner-eyebrow {
  margin: 0 0 10px;
  color: rgba(255, 255, 255, 0.56);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.report-banner h1 {
  margin: 0 0 8px;
  font-size: 32px;
  font-weight: 600;
  line-height: 1.2;
}

.report-banner p {
  margin: 0;
  color: rgba(255, 255, 255, 0.72);
  font-size: 13px;
}

.banner-meta {
  min-width: 132px;
  text-align: right;
}

.banner-meta .l {
  margin-bottom: 2px;
  color: rgba(255, 255, 255, 0.45);
  font-size: 12px;
}

.banner-meta .v {
  margin-bottom: 10px;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.banner-meta .accent {
  color: #fac775;
  font-size: 24px;
}

.freeze-card,
.method-card,
.hero-statement,
.finding-card,
.evidence-card,
.metric-card,
.big-stat,
.competitor-card,
.sentiment-panel,
.baseline-note,
.negative-list article {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-lg);
}

.freeze-card {
  padding: 24px 28px;
  margin-bottom: 16px;
}

.freeze-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px 28px;
}

.freeze-item {
  display: grid;
  gap: 4px;
}

.freeze-item .k {
  color: var(--c-text-3);
  font-size: 11px;
}

.freeze-item strong {
  font-size: 14px;
}

.method-card {
  padding: 22px 26px;
  margin-bottom: 16px;
}

.method-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  color: var(--c-text-2);
  font-size: 13px;
}

.integrity {
  padding: 14px 18px;
  color: #0c5a46;
  background: var(--c-success-bg);
  border-left: 3px solid var(--c-success);
  border-radius: 0 var(--r-md) var(--r-md) 0;
  font-size: 13px;
}

.section-intro {
  margin: 0 0 20px;
  padding: 16px 20px;
  color: var(--c-text-1);
  background: var(--c-surface);
  border-left: 3px solid var(--c-accent);
  border-radius: 0 var(--r-md) var(--r-md) 0;
}

.section-intro p {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
}

.hero-statement {
  padding: 36px 40px;
  margin-bottom: 24px;
  border-left: 4px solid var(--c-danger);
  box-shadow: 0 18px 36px rgba(20, 24, 32, 0.04);
}

.hero-eyebrow {
  margin: 0 0 14px;
  color: var(--c-text-3);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.hero-text {
  margin: 0 0 20px;
  color: var(--c-text-1);
  font-size: 22px;
  font-weight: 500;
  line-height: 1.6;
}

.hero-text .brand {
  color: var(--c-accent);
  font-weight: 600;
}

.hero-text .accent {
  color: var(--c-danger);
  font-weight: 600;
}

.hero-stats,
.metric-row,
.finding-list,
.evidence-grid,
.competitor-grid,
.audit-grid {
  display: grid;
  gap: 16px;
}

.hero-stats {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding-top: 20px;
  border-top: 1px solid var(--c-border);
}

.metric-card span {
  display: block;
  color: var(--c-text-3);
  font-size: 12px;
}

.metric-card strong {
  display: block;
  margin-top: 2px;
  font-size: 24px;
}

.hero-stat .num {
  margin-bottom: 6px;
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
}

.hero-stat .num.danger {
  color: var(--c-danger);
}

.hero-stat .num.warn {
  color: var(--c-orange);
}

.hero-stat .num.muted {
  color: var(--c-text-2);
}

.hero-stat .label {
  color: var(--c-text-2);
  font-size: 12px;
  line-height: 1.5;
}

.finding-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.finding-card {
  padding: 18px 20px;
  color: var(--c-text-1);
  font-size: 14px;
  border-left: 3px solid var(--c-accent);
}

.delta-note {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
  color: var(--c-text-3);
  font-size: 12px;
}

.evidence-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.evidence-section + .evidence-section {
  margin-top: 24px;
}

.evidence-card {
  padding: 0;
  overflow: hidden;
  box-shadow: 0 10px 24px rgba(20, 24, 32, 0.035);
}

.question-text {
  display: block;
  margin: 0;
  color: var(--c-text-1);
  font-size: 15px;
  font-weight: 600;
}

.takeaway {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin: 16px 20px 18px;
  padding: 14px 18px;
  color: var(--c-text-1);
  background: var(--c-accent-bg);
  border-radius: var(--r-md);
  font-size: 13px;
}

.takeaway p {
  margin: 0;
}

.takeaway-icon {
  width: 24px;
  height: 24px;
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  color: #fff;
  background: var(--c-accent);
  border-radius: 50%;
  font-weight: 700;
}

.evidence-meta,
.competitor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.evidence-meta {
  padding: 14px 20px;
  color: var(--c-text-3);
  background: var(--c-surface-2);
  border-bottom: 1px solid var(--c-border);
  font-size: 12px;
}

.negative-list p,
.competitor-card p {
  margin: 10px 0 0;
  color: var(--c-text-2);
  font-size: 13px;
  line-height: 1.75;
}

.case-platform-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--c-text-2);
  font-weight: 600;
}

.platform-icon {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  color: #fff;
  background: var(--c-text-1);
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
}

.pi-doubao { background: #4e5bff; }
.pi-tongyi { background: #615ced; }
.pi-deepseek { background: #4d6bfe; }
.pi-hailuo { background: #00b5b5; }
.pi-hunyuan { background: #1664ff; }
.pi-mimo { background: #ff6f00; }
.pi-360 { background: #2ec453; }
.pi-zhipu { background: #5b5ff0; }

.case-tags {
  display: flex;
  gap: 6px;
}

.case-tag {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.case-tag-scenario { color: var(--c-accent); background: var(--c-accent-bg); }
.case-tag-recommend { color: var(--c-purple); background: var(--c-purple-bg); }
.case-tag-compare { color: var(--c-warn); background: var(--c-warn-bg); }
.case-tag-question { color: var(--c-success); background: var(--c-success-bg); }
.case-tag-cognitive { color: var(--c-orange); background: var(--c-orange-bg); }
.case-tag-value-high { color: var(--c-danger); background: var(--c-danger-bg); }

.case-question {
  display: flex;
  gap: 10px;
  padding: 20px 22px 0;
}

.q-prefix {
  color: var(--c-text-3);
  font-weight: 600;
}

.case-answer {
  margin: 16px 20px 0;
  padding: 16px 18px;
  color: var(--c-text-1);
  background: var(--c-surface-2);
  border-left: 3px solid var(--c-accent);
  border-radius: var(--r-md);
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-line;
  max-height: 210px;
  overflow: hidden;
}

.trace {
  margin: -8px 20px 18px;
  color: var(--c-text-3);
  font-size: 11px;
}

.metric-row {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 18px;
}

.metric-card {
  padding: 18px 20px;
}

.metric-card small {
  display: block;
  margin-top: 6px;
  color: var(--c-text-3);
}

.dimension-table {
  display: grid;
  margin: 0 0 46px;
}

.platform-table {
  margin-bottom: 18px;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--c-border);
  border-radius: var(--r-lg);
}

.platform-row {
  display: grid;
  grid-template-columns: 1.2fr 1.4fr 80px 1.4fr 1.4fr;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-top: 1px solid var(--c-border);
  font-size: 13px;
}

.platform-row:first-child {
  border-top: 0;
}

.platform-head {
  color: var(--c-text-3);
  background: var(--c-surface-2);
  font-size: 12px;
}

.dimension-row {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(220px, 260px) 150px;
  align-items: center;
  gap: 18px;
  min-height: 78px;
  padding: 18px 0;
  border-bottom: 1px solid var(--c-border);
}

.metric-bar {
  display: grid;
  grid-template-columns: minmax(82px, 1fr) 42px;
  align-items: center;
  gap: 8px;
  min-width: 128px;
}

.metric-bar i {
  height: 8px;
  display: block;
  background: linear-gradient(90deg, #8bb8ff, #2f6feb);
  border-radius: 999px;
  box-shadow: inset 0 0 0 1px rgba(47, 111, 235, 0.08);
}

.metric-bar::before {
  content: '';
  grid-column: 1;
  grid-row: 1;
  height: 8px;
  display: block;
  background: var(--c-surface-2);
  border-radius: 999px;
}

.metric-bar i {
  grid-column: 1;
  grid-row: 1;
}

.metric-bar b {
  color: var(--c-text-1);
  font-size: 12px;
  text-align: right;
}

.dimension-name {
  color: var(--c-text-2);
  font-size: 18px;
}

.dimension-meter {
  display: grid;
  gap: 8px;
}

.dimension-meter i {
  width: 100%;
  height: 8px;
  display: block;
  overflow: hidden;
  background: #f4f3ee;
  border-radius: 999px;
}

.dimension-meter b {
  height: 100%;
  display: block;
  background: var(--c-accent);
  border-radius: inherit;
}

.dimension-meter strong {
  color: var(--c-text-2);
  font-size: 20px;
  line-height: 1;
}

.dimension-row em {
  color: var(--c-text-3);
  font-style: normal;
  font-size: 20px;
  text-align: right;
}

.heatmap-title {
  margin-bottom: 22px;
  font-size: 18px;
}

.prototype-heatmap {
  display: grid;
  grid-template-columns: 120px repeat(var(--platform-count), minmax(74px, 1fr));
  gap: 5px;
  margin-bottom: 14px;
}

.prototype-heatmap-spacer {
  min-height: 24px;
}

.prototype-heatmap-platform {
  min-height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-3);
  font-size: 12px;
  font-weight: 600;
  text-align: center;
}

.prototype-heatmap-group {
  min-height: 28px;
  display: flex;
  align-items: end;
  justify-content: center;
  color: var(--c-accent);
  font-size: 13px;
  font-weight: 700;
}

.prototype-heatmap-intent {
  min-height: 44px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.prototype-heatmap-intent strong,
.prototype-heatmap-intent small {
  display: block;
  width: 100%;
}

.prototype-heatmap-intent strong {
  font-size: 14px;
  line-height: 1.2;
}

.prototype-heatmap-intent small {
  color: var(--c-text-3);
  font-size: 12px;
  line-height: 1.35;
}

.prototype-heatmap-cell {
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #0a1f3d;
  background: var(--hm0);
  border-radius: 4px;
  font-size: 13px;
  text-align: center;
}

.prototype-heatmap-cell.low-sample {
  color: var(--c-text-3);
}

.heatmap-legend {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 18px;
  color: var(--c-text-3);
  font-size: 12px;
}

.hm-bin {
  width: 16px;
  height: 12px;
  display: inline-block;
  border: 1px solid #dbe3ee;
  border-radius: 2px;
}

.hm-bin-0 {
  background: var(--hm0);
}

.hm-bin-1 {
  background: var(--hm1);
}

.hm-bin-2 {
  background: var(--hm2);
}

.hm-bin-3 {
  background: var(--hm3);
}

.competitor-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.big-stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.big-stat {
  padding: 24px;
  text-align: center;
}

.big-stat span {
  display: block;
  color: var(--c-text-3);
  font-size: 12px;
}

.big-stat strong {
  display: block;
  margin: 6px 0;
  color: var(--c-danger);
  font-size: 40px;
  line-height: 1;
}

.big-stat small {
  color: var(--c-text-2);
  font-size: 13px;
}

.competitor-card {
  padding: 20px 24px;
}

.competitor-name {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.rank-badge {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
}

.rank-1 {
  color: var(--c-warn);
  background: var(--c-warn-bg);
}

.rank-2 {
  color: var(--c-text-2);
  background: var(--c-surface-2);
}

.rank-3 {
  color: var(--c-orange);
  background: var(--c-orange-bg);
}

.competitor-stat {
  color: var(--c-text-2);
  font-size: 14px;
}

.competitor-stat strong {
  color: var(--c-text-1);
  font-size: 17px;
}

.verified,
.unverified {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
}

.verified {
  color: var(--c-success);
  background: var(--c-success-bg);
}

.unverified {
  color: var(--c-text-3);
  background: var(--c-surface-2);
}

.source-block {
  margin-top: 10px;
  padding: 12px;
  color: #0c5a46;
  background: transparent;
  border-top: 1px solid var(--c-border);
  border-radius: 0;
  line-height: 1.7;
}

.pending-source {
  color: var(--c-text-3);
}

.quote-list {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}

.quote-list p {
  position: relative;
  margin: 0;
  padding: 12px 14px 12px 26px;
  background: var(--c-surface-2);
  border-radius: var(--r-md);
  font-style: italic;
}

.quote-list p::before {
  content: '"';
  position: absolute;
  left: 9px;
  top: 2px;
  color: var(--c-text-3);
  font-family: Georgia, serif;
  font-size: 26px;
  line-height: 1;
}

.quote-list .source {
  display: block;
  margin-top: 6px;
  color: var(--c-text-3);
  font-size: 11px;
  font-style: normal;
}

.untracked {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0 20px;
  padding-top: 14px;
  border-top: 1px solid var(--c-border);
  color: var(--c-text-3);
  font-size: 12px;
}

.untracked em,
.keyword-row em,
.gap-row em {
  padding: 2px 8px;
  background: var(--c-surface-2);
  border-radius: 999px;
  font-style: normal;
}

.gap-table {
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--c-border);
  border-radius: var(--r-lg);
}

.gap-head,
.gap-row {
  display: grid;
  grid-template-columns: 140px 70px 1fr;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
}

.gap-head {
  color: var(--c-text-3);
  background: var(--c-surface-2);
  font-size: 12px;
}

.gap-row + .gap-row {
  border-top: 1px solid var(--c-border);
}

.gap-row p {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
}

.gap-row em.present {
  color: var(--c-warn);
  background: var(--c-warn-bg);
}

.sentiment-panel {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 24px;
  align-items: center;
  padding: 24px;
  margin-bottom: 18px;
}

.impression-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.impression-card {
  padding: 16px 18px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-lg);
}

.impression-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.impression-card-head strong {
  flex: 1;
  font-size: 14px;
}

.impression-card-head em {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-style: normal;
  font-weight: 700;
}

.impression-card p {
  margin: 0;
  color: var(--c-text-2);
  font-size: 12px;
  line-height: 1.7;
}

.state-positive {
  color: var(--c-success);
  background: var(--c-success-bg);
}

.state-neutral {
  color: var(--c-text-2);
  background: var(--c-surface-2);
}

.state-negative {
  color: var(--c-danger);
  background: var(--c-danger-bg);
}

.state-info_missing,
.state-no_awareness {
  color: var(--c-warn);
  background: var(--c-warn-bg);
}

mark {
  border-radius: 3px;
  padding: 0 2px;
  font-weight: 700;
}

.hl-brand {
  background: rgba(15, 110, 86, 0.15);
  color: var(--c-success);
}

.hl-competitor {
  background: rgba(133, 79, 11, 0.16);
  color: var(--c-warn);
}

.hl-negative {
  background: rgba(163, 45, 45, 0.14);
  color: var(--c-danger);
}

.negative-block {
  margin: 18px 0;
}

.negative-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.negative-list article {
  padding: 16px 20px;
  background: var(--c-danger-bg);
  border-color: rgba(163, 45, 45, 0.25);
}

.negative-list article > span {
  display: inline-flex;
  padding: 2px 8px;
  color: var(--c-danger);
  background: #fff;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.severity-high {
  color: var(--c-danger);
}

.severity-mid {
  color: var(--c-warn);
}

.severity-low {
  color: var(--c-text-2);
}

.negative-list article > strong {
  margin-left: 8px;
  color: var(--c-danger);
  font-size: 12px;
}

.negative-list article p {
  padding: 12px 14px;
  color: var(--c-text-1);
  background: var(--c-surface);
  border-radius: var(--r-md);
  font-size: 14px;
  font-weight: 500;
}

.donut {
  width: 132px;
  height: 132px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  position: relative;
}

.donut::after {
  content: '';
  position: absolute;
  width: 78px;
  height: 78px;
  border-radius: 50%;
  background: #fff;
}

.donut span {
  position: relative;
  z-index: 1;
  font-weight: 700;
}

.sentiment-bars {
  display: grid;
  gap: 10px;
}

.senti-bar {
  display: grid;
  grid-template-columns: 52px minmax(120px, 1fr) 104px;
  align-items: center;
  gap: 10px;
  color: var(--c-text-2);
  font-size: 13px;
}

.senti-label {
  font-weight: 700;
}

.senti-track {
  height: 8px;
  overflow: hidden;
  background: var(--c-surface-2);
  border-radius: 999px;
}

.senti-fill {
  height: 100%;
  border-radius: 999px;
}

.senti-value {
  color: var(--c-text-3);
  text-align: right;
}

.audit-note {
  margin: 12px 0 0;
  color: var(--c-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.sentiment-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-top: 12px;
  color: var(--c-text-3);
  font-size: 12px;
}

.sentiment-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.sentiment-legend i {
  width: 18px;
  height: 8px;
  display: inline-block;
  border-radius: 999px;
}

.impression-list {
  display: grid;
  margin-top: 12px;
  overflow: hidden;
  color: var(--c-text-2);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  font-size: 13px;
}

.impression-row {
  display: grid;
  grid-template-columns: 140px minmax(260px, 1fr) 120px;
  align-items: center;
  gap: 14px;
  padding: 10px 14px;
  border-top: 1px solid var(--c-border);
}

.impression-row:first-child {
  border-top: 0;
}

.impression-head {
  color: var(--c-text-3);
  background: var(--c-surface-2);
  font-size: 12px;
}

.stack-bar {
  height: 12px;
  display: flex;
  overflow: hidden;
  background: var(--c-surface-2);
  border-radius: 999px;
}

.stack-bar i {
  min-width: 2px;
}

.seg-positive {
  background: #16a34a;
}

.seg-neutral {
  background: #64748b;
}

.seg-negative {
  background: #dc2626;
}

.seg-info_missing {
  background: #93a4bb;
}

.seg-no_awareness {
  background: #d8dee8;
}

.dominant-state {
  color: var(--c-text-3);
  font-size: 12px;
  text-align: right;
}

.keyword-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.low-sample {
  color: var(--c-text-3);
  background: var(--c-surface-2);
}

.mark-ok {
  color: #16a34a;
  font-weight: 700;
}

.mark-miss {
  color: #dc2626;
  font-weight: 700;
}

.baseline-note {
  padding: 28px 32px;
  margin-bottom: 18px;
  background: linear-gradient(135deg, #e1f5ee 0%, #eaf0f7 100%);
  border-color: rgba(15, 110, 86, 0.25);
}

.bn-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.bn-icon {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  color: #fff;
  background: var(--c-success);
  border-radius: 50%;
  font-weight: 700;
}

.bn-head h3 {
  margin: 0;
  font-size: 17px;
}

.baseline-note p {
  margin: 0;
  color: #244237;
  font-size: 14px;
  line-height: 1.8;
}

.bn-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.bn-grid div {
  padding: 12px;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: var(--r-md);
}

.bn-grid span,
.bn-grid strong {
  display: block;
}

.bn-grid span {
  color: var(--c-text-3);
  font-size: 12px;
}

.bn-grid strong {
  margin-top: 2px;
  font-size: 14px;
}

.report-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 34px;
  margin: 72px 0 0;
  padding-top: 20px;
  color: #3b5d82;
  font-size: 12px;
  border-top: 1px solid #e7edf3;
}

.option-meta {
  margin-left: 6px;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .delivery-report {
    grid-template-columns: 1fr;
  }

  .report-sidebar {
    position: static;
    height: auto;
    border-right: 0;
    border-bottom: 1px solid var(--c-border);
  }

  .report-main {
    padding: 24px;
  }

  .report-banner,
  .sentiment-panel {
    grid-template-columns: 1fr;
  }

  .finding-list,
  .evidence-grid,
  .metric-row,
  .competitor-grid,
  .big-stat-grid,
  .impression-card-grid,
  .method-list,
  .freeze-grid,
  .bn-grid,
  .negative-list {
    grid-template-columns: 1fr;
  }

  .prototype-heatmap {
    min-width: 720px;
    overflow-x: auto;
  }

  .task-strip {
    grid-template-columns: 1fr;
  }
}

@media print {
  @page {
    size: A4;
    margin: 0;
  }

  html,
  body {
    margin: 0;
    background: #fff;
  }

  .baseline-page.print-mode {
    width: 210mm;
    margin: 0;
    padding: 8mm 9mm;
    background: #fff;
  }

  .print-mode .panel,
  .print-mode .module {
    box-shadow: none;
  }

  .print-mode .delivery-report {
    display: block;
    min-height: auto;
    background: #fff;
  }

  .print-mode .report-sidebar {
    display: none;
  }

  .print-mode .report-main {
    max-width: none;
    padding: 0;
  }

  .print-mode .report-banner,
  .print-mode .hero-statement,
  .print-mode .evidence-card,
  .print-mode .competitor-card,
  .print-mode .baseline-note,
  .print-mode .freeze-card,
  .print-mode .method-card,
  .print-mode .sentiment-panel,
  .print-mode .negative-list article {
    page-break-inside: avoid;
    break-inside: avoid;
  }

  .print-mode .module {
    margin-bottom: 34px;
  }

  .print-mode .module:not(#method) {
    break-before: auto;
  }

  .print-mode .report-banner {
    margin-bottom: 24px;
    padding: 28px 34px;
    border-radius: 12px;
  }

  .print-mode .module-head {
    margin-bottom: 18px;
  }

  .print-mode .freeze-card,
  .print-mode .method-card {
    padding: 18px 22px;
    margin-bottom: 12px;
  }

  .print-mode .method-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px 20px;
  }

  .print-mode .integrity,
  .print-mode .section-intro {
    padding: 12px 16px;
  }

  .print-mode .case-answer {
    max-height: 158px;
  }

  .print-mode .quote-list p {
    max-height: 112px;
    overflow: hidden;
  }

  .print-mode .competitor-grid,
  .print-mode .impression-card-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .print-mode .negative-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .print-mode .prototype-heatmap {
    grid-template-columns: 88px repeat(var(--platform-count), minmax(46px, 1fr));
    gap: 4px;
  }

  .print-mode .prototype-heatmap-platform,
  .print-mode .prototype-heatmap-group,
  .print-mode .prototype-heatmap-intent,
  .print-mode .prototype-heatmap-cell {
    min-height: 34px;
    font-size: 10px;
  }

}
</style>
