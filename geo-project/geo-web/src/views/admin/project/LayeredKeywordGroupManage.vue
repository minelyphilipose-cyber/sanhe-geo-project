<template>
  <div class="layered-keyword-page">
    <div class="step-switcher">
      <span class="label">分层拓词管理</span>
      <button v-for="s in steps" :key="s.no" class="step-btn" :class="{ active: activeStep === s.no }" @click="goStep(s.no)">
        Step {{ s.no }} {{ s.title }}
      </button>
    </div>

    <div class="wire-layout">
      <main class="main">
        <div class="breadcrumb">GEO 运营后台 <span class="sep">/</span> 问题池 <span class="sep">/</span> <span class="current">分层拓词管理</span></div>
        <StepHeader :active-step="activeStep" />

        <section v-show="activeStep === 1">
          <div class="design-note">
            <strong>Step 1 说明：</strong>
            进入页面默认要求先选择项目。项目按客户 / 品牌 / 项目多级选择；选中项目后自动加载档案、项目问题额度、当前工单未删除题目数与 running 批次预占，计算三级剩余可生成。
          </div>

          <div class="card">
            <div class="card-head"><span>① 选择项目</span><button class="btn btn-sm" @click="loadProjects">刷新</button></div>
            <div class="card-body">
              <div class="search-row">
                <input v-model="keyword" class="text-input" placeholder="输入公司名 / 品牌名 / 项目名模糊搜索" @keyup.enter="loadProjects">
                <button class="btn btn-primary" @click="loadProjects">搜索</button>
                <el-cascader
                  v-model="selectedProjectId"
                  class="project-cascader"
                  filterable
                  clearable
                  :options="projectCascadeOptions"
                  :props="projectCascadeProps"
                  :loading="projectLoading"
                  placeholder="选择客户 / 品牌 / 项目"
                  @change="handleProjectChange"
                />
              </div>
              <div class="customer-list">
                <button
                  v-for="item in projectOptions"
                  :key="item.id"
                  class="customer-card"
                  :class="{ selected: selectedProject?.id === item.id }"
                  @click="selectProject(item)"
                >
                  <div>
                    <b>{{ item.projectName }}</b>
                    <span class="tag tag-primary">额度 A{{ projectQuotaA(item) }} / B{{ projectQuotaB(item) }} / C{{ projectQuotaC(item) }}</span>
                  </div>
                  <div class="meta">{{ item.companyName || '未归属客户' }} · {{ item.brandName || '未绑定品牌' }} · {{ projectStatusLabel(item.status) }}</div>
                </button>
              </div>
              <div v-if="selectedProject" class="selected-info">
                <div class="item"><label>客户</label>{{ selectedProject.companyName || '-' }}</div>
                <div class="item"><label>品牌</label>{{ selectedProject.brandName || '-' }}</div>
                <div class="item"><label>项目</label>{{ selectedProject.projectName }}</div>
                <div class="item"><label>项目状态</label>{{ projectStatusLabel(selectedProject.status) }}</div>
                <div class="item"><label>项目额度</label><span class="tag tag-primary">A {{ projectQuotaA(selectedProject) }} / B {{ projectQuotaB(selectedProject) }} / C {{ projectQuotaC(selectedProject) }}</span></div>
                <div class="item"><label>当前进行中工单</label>{{ workorder ? `WO-${workorder.id}` : '待创建' }}</div>
                <div class="item"><label>已入库版本</label>项目级问题池版本</div>
              </div>
            </div>
          </div>

          <QuotaCard :quota="quota" title="③ 配额信息" />

          <div v-if="selectedProject" class="card">
            <div class="card-head">
              <span>④ 已生成拓词组列表</span>
              <button class="btn btn-sm" @click="loadWorkorderList">刷新</button>
            </div>
            <div class="card-body batch-list">
              <div class="workorder-row head"><div>拓词组</div><div>套餐 / 状态</div><div>已生成 ABC</div><div>批次数</div><div>最近批次</div><div>操作</div></div>
              <div v-if="!workorderList.length" class="empty-row">该项目暂无已生成拓词组。提交生成任务后会在这里展示。</div>
              <div v-for="item in workorderList" :key="item.id" class="workorder-row">
                <div><b>{{ item.workorderNo }}</b><br><span class="meta">{{ formatTime(item.createdAt) }}</span></div>
                <div>{{ item.packageName || '-' }}<br><span class="tag" :class="statusTagClass(item.status)">{{ item.status }}</span></div>
                <div>A {{ item.countA }} / B {{ item.countB }} / C {{ item.countC }}<br><b>合计 {{ item.countTotal }}</b></div>
                <div>{{ item.batchCount }}</div>
                <div>{{ item.latestBatchStatus || '-' }}<br><span class="meta">{{ formatTime(item.latestBatchAt) }}</span></div>
                <div><button class="btn btn-sm btn-primary" :disabled="!item.countTotal" @click="openWorkorderReview(item)">查看问题</button></div>
              </div>
            </div>
          </div>

          <div class="footer-bar">
            <div>已选项目：{{ selectedProject?.projectName || '未选择' }}</div>
            <div>
              <button class="btn">取消</button>
              <button class="btn btn-primary" :disabled="!workorder" @click="goStep2">下一步：信息补全 →</button>
            </div>
          </div>
        </section>

        <section v-show="activeStep === 2">
          <div class="design-note">
            <strong>Step 2 说明：</strong>
            客户主档案带入到工单级快照。同步回客户档案关闭时仅保存当前工单；开启时覆盖客户主档案级竞品和核心需求。
          </div>
          <div class="source-legend">
            <span><i class="src-icon src-from-profile">✓</i> 已带入</span>
            <span><i class="src-icon src-missing">!</i> 需补全</span>
            <span><i class="src-icon src-modified">✎</i> 已修改</span>
            <label class="switch-line"><input v-model="syncToProfile" type="checkbox"> 同步回客户档案</label>
          </div>
          <div class="card">
            <div class="card-head"><span>分组 A · 客户基本信息</span><span class="tag">9 个字段</span></div>
            <div class="card-body form-grid">
              <Field label="公司全称" required><input v-model="profile.companyName" class="text-input"></Field>
              <Field label="品牌名" required><input v-model="profile.brandName" class="text-input"></Field>
              <Field label="品牌关系" required><input v-model="profile.brandRelation" class="text-input" placeholder="可输入自营 / 授权 / 经销 / 加盟等关系"></Field>
              <Field label="核心业务" required><input v-model="coreBusinessText" class="text-input"></Field>
              <Field label="目标区域" required><input v-model="profile.targetRegion" class="text-input"></Field>
              <Field label="所属行业" required><input v-model="industryText" class="text-input"></Field>
              <Field label="客户画像" required><textarea v-model="profile.targetCustomer" class="text-input textarea"></textarea></Field>
              <Field label="核心优势" required><textarea v-model="profile.coreAdvantage" class="text-input textarea"></textarea></Field>
              <Field label="标杆参数"><textarea v-model="profile.benchmarkSpecs" class="text-input textarea"></textarea></Field>
            </div>
          </div>
          <div class="card">
            <div class="card-head"><span>分组 B · 主竞品</span><span class="tag">至少 1 条，建议 ≤6 条</span></div>
            <div class="card-body">
              <div v-for="(c, idx) in profile.competitors" :key="idx" class="competitor-item">
                <div class="need-head"><span class="idx"><span class="src-icon src-from-profile">✓</span>竞品 {{ idx + 1 }}</span><button class="btn btn-sm btn-danger" @click="profile.competitors.splice(idx, 1)">删除</button></div>
                <Field label="名称" required compact><input v-model="c.competitorName" class="text-input"></Field>
                <Field label="优势" required compact><textarea v-model="c.advantages" class="text-input textarea"></textarea></Field>
                <Field label="劣势" required compact><textarea v-model="c.disadvantages" class="text-input textarea"></textarea></Field>
              </div>
              <button class="btn" @click="addCompetitor">+ 增加竞品</button>
            </div>
          </div>
          <div class="card">
            <div class="card-head"><span>分组 C · 客户口头明确说出的核心需求<span class="note-inline">决定 A 类生成 · 3–5 条</span></span></div>
            <div class="card-body">
              <div v-for="(n, idx) in profile.coreNeeds" :key="idx" class="need-item">
                <div class="need-head"><span class="idx">需求 {{ idx + 1 }}</span><button class="btn btn-sm btn-danger" @click="profile.coreNeeds.splice(idx, 1)">删除</button></div>
                <Field label="原话整理" required compact><textarea v-model="n.text" class="text-input textarea"></textarea></Field>
                <Field label="对应场景" required compact><select v-model="n.scene" class="text-input"><option v-for="s in sceneOptions" :key="s.code" :value="s.code">{{ s.label }}</option></select></Field>
                <label class="switch-line"><input v-model="n.urgent" type="checkbox"> 紧急标记</label>
              </div>
              <button class="btn" @click="addNeed">+ 增加需求</button>
            </div>
          </div>
          <div class="footer-bar">
            <div><button class="btn" @click="saveDraft">保存草稿</button><span class="save-tip">自动保存已启用</span></div>
            <div><button class="btn" @click="activeStep = 1">← 上一步</button><button class="btn btn-primary" @click="goStep3">下一步：配置参数 →</button></div>
          </div>
        </section>

        <section v-show="activeStep === 3">
          <div class="design-note">
            <strong>Step 3 说明：</strong>
            本批生成 ABC 数量受三级配额硬约束；合计必须 ≤ 50；下游模型从已接入清单拉取；场景权重总和等于本批合计，整数补差用最大余数法。
          </div>
          <QuotaCard :quota="quota" title="① 剩余可生成" compact />
          <div class="card">
            <div class="card-head">
              <span>② 本次生成配置</span>
              <div><button class="btn btn-sm" @click="splitByRatio">按剩余等比例切分（≤50）</button><button class="btn btn-sm" @click="fillToLimit">一键填满到 50</button></div>
            </div>
            <div class="card-body">
              <div class="batch-input-grid">
                <NumBox label="本批 A 类" v-model="batchForm.batchA" :remain="quota?.remainingA || 0" />
                <NumBox label="本批 B 类" v-model="batchForm.batchB" :remain="quota?.remainingB || 0" />
                <NumBox label="本批 C 类" v-model="batchForm.batchC" :remain="quota?.remainingC || 0" />
                <div class="col-item total"><label>本批合计</label><div class="big-num">{{ batchTotal }} / 50</div><div class="remain-hint">{{ batchTotal <= 50 ? '✓ 单批校验通过' : '单批合计不得超过 50' }}</div></div>
              </div>
              <Field label="下游大模型" required><select v-model="batchForm.modelConfigId" class="text-input"><option v-for="p in providers" :key="p.id" :value="p.id">{{ p.modelName || p.platformName }}</option></select><span class="inline-tip">已接入模型 {{ providers.length }} 个 · 来自系统大模型配置</span></Field>
              <Field label="场景权重" required>
                <table class="quota-table small"><thead><tr><th v-for="s in sceneOptions" :key="s.code">{{ s.label }}</th><th>合计</th></tr></thead><tbody><tr><td v-for="s in sceneOptions" :key="s.code"><input v-model.number="batchForm.sceneWeights[s.code]" class="mini-input"></td><td><b :class="weightTotal === batchTotal ? 'ok' : 'bad'">{{ weightTotal }}</b></td></tr></tbody></table>
                <div class="form-tip">默认按基线 25/30/25/25/25/20 比例换算；最大余数法保证整数和=本批合计</div>
                <button class="btn btn-sm" @click="applyBaselineWeights">应用基线权重</button>
              </Field>
              <Field label="温度（高级）"><input v-model.number="batchForm.temperature" class="text-input short" type="number" step="0.1" min="0" max="1"><span class="inline-tip">默认 0.7</span></Field>
              <div v-if="validationMessage" class="note error-note">{{ validationMessage }}</div>
              <div v-else class="note"><b>校验规则：</b> 各级不超过剩余；合计 ≤ 50；合计 ≥ 1；场景权重总和 == 本批合计。前端实时校验 + 后端二次校验。</div>
            </div>
          </div>
          <div class="footer-bar">
            <div>预估耗时：约 90 秒 · 单批 {{ batchTotal }} 条</div>
            <div><button class="btn" @click="activeStep = 2">← 上一步</button><button class="btn btn-primary" :disabled="!!validationMessage || hasRunningBatch" @click="startBatch">{{ hasRunningBatch ? '已有运行中批次' : `开始生成（${batchTotal} 条）→` }}</button></div>
          </div>
        </section>

        <section v-show="activeStep === 4">
          <div class="design-note"><strong>Step 4 说明：</strong>异步任务。前端轮询批次详情拿结构化进度对象。中断走协作式：标记 cancel_requested，已生成部分保留落库（partial）。</div>
          <div class="card">
            <div class="card-head"><span>当前批次 {{ currentBatch?.batchNo || '-' }}</span><span class="tag tag-warning">{{ currentBatch?.status || '未开始' }}</span></div>
            <div class="card-body">
              <div class="overview-grid">
                <div><label>所属工单</label><b>{{ workorder ? `WO-${workorder.id}` : '-' }}</b></div>
                <div><label>使用模型</label><b>{{ currentBatch?.modelName || selectedProvider?.modelName || '-' }}</b></div>
                <div><label>本批数量</label><b>A {{ currentBatch?.requestA || 0 }} · B {{ currentBatch?.requestB || 0 }} · C {{ currentBatch?.requestC || 0 }}</b></div>
                <div><label>状态</label><b>{{ currentBatch?.partialFlag ? 'partial' : currentBatch?.status || '-' }}</b></div>
              </div>
              <div class="progress-bar"><div :style="{ width: progressPercent + '%' }"></div></div>
              <div class="progress-text">{{ progressObj.message || '等待生成' }} · {{ progressObj.generated || 0 }}/{{ progressObj.target || batchTotal }}</div>
              <button class="btn btn-danger" :disabled="!currentBatch || !['pending','running'].includes(currentBatch.status)" @click="cancelBatch">中断生成</button>
            </div>
          </div>
          <div class="card">
            <div class="card-head"><span>实时日志<span class="note-inline">关键节点级 · 用于审计与排障</span></span></div>
            <div class="card-body"><div class="log-area"><div v-for="log in currentBatch?.logs || []" :key="`${log.createdAt}-${log.eventCode}`"><span class="log-time">{{ formatTime(log.createdAt) }}</span><span class="log-info">[{{ log.eventCode }}] {{ log.message }}</span></div></div></div>
          </div>
          <div class="footer-bar"><div></div><div><button class="btn" @click="activeStep = 3">← 返回配置</button><button class="btn btn-primary" :disabled="!currentBatch || ['pending','running'].includes(currentBatch.status)" @click="goReview">下一步：审核 →</button></div></div>
        </section>

        <section v-show="activeStep === 5">
          <div class="design-note"><strong>Step 5 说明：</strong>累积视图：本工单下所有批次的题目合并展示。重生成单条采用原地软替换，不破坏配额。入库需 A/B/C 全部满额（本期严格模式）。</div>
          <QuotaCard :quota="review?.workorder.quota || quota" title="工单累积统计" />
          <div class="footer-bar quota-actions"><div v-if="duplicateQuestionTexts.length" class="warn">⚠ 存在重复问题，请替换或删除后再入库：{{ duplicateQuestionTexts.slice(0, 3).join('；') }}</div><div v-else-if="!canCommit" class="warn">⚠ 三级配额未满，无法入库。剩余 A {{ quota?.remainingA || 0 }} / B {{ quota?.remainingB || 0 }} / C {{ quota?.remainingC || 0 }}</div><div><button class="btn btn-primary" :disabled="hasRunningBatch || workorder?.status !== 'draft'" @click="continueGenerate">+ 继续生成下一批</button><button class="btn" @click="exportHint">导出 Excel</button><button class="btn" :class="canCommit ? 'btn-primary' : 'btn-disabled'" :disabled="!canCommit" @click="commit">入库为正式版本（v1.0）</button></div></div>
          <div class="card">
            <div class="card-head"><span>本工单生成批次（{{ review?.batches.length || 0 }} 个）</span></div>
            <div class="card-body batch-list">
              <div class="batch-row head"><div>批次号</div><div>生成时间 / 模型</div><div>本批数量</div><div>状态</div><div>替换次数</div><div>操作</div></div>
              <div v-for="b in review?.batches || []" :key="b.id" class="batch-row"><div>{{ b.batchNo }}</div><div>{{ formatTime(b.createdAt) }}<br>{{ b.modelName }}</div><div>A {{ b.actualA }} · B {{ b.actualB }} · C {{ b.actualC }}</div><div><span class="tag tag-success">{{ b.status }}</span></div><div>{{ batchReplaceCount(b.id) }}</div><div><button class="btn btn-sm" @click="currentBatch = b; activeStep = 4">查看</button><button class="btn btn-sm btn-danger" @click="removeBatch(b.id)">删除批次（软删除并释放额度）</button></div></div>
            </div>
          </div>
          <div class="card">
            <div class="card-head"><span>问题清单（共 {{ questionPage.total }} 题）</span></div>
            <div class="card-body">
              <div class="tab-bar"><button v-for="t in questionTabs" :key="t" class="tab" :class="{ active: tierTab === t }" @click="tierTab = t">{{ t === 'all' ? '全部' : `${t} 类` }}</button></div>
              <table class="table"><thead><tr><th>ID</th><th>问题文本</th><th>场景</th><th>分级</th><th>优先级</th><th>频率</th><th>总分</th><th>对应需求</th><th>所属批次</th><th>操作</th></tr></thead><tbody><tr v-if="questionLoading"><td colspan="10" class="empty-row">正在加载问题...</td></tr><tr v-else-if="!pagedQuestions.length"><td colspan="10" class="empty-row">暂无问题。</td></tr><template v-else><tr v-for="q in pagedQuestions" :key="q.id"><td>{{ q.tier }}-{{ q.id }}</td><td><div class="qtext">{{ q.questionText }}</div><div class="reason">{{ q.designReason }}</div></td><td>{{ sceneLabel(q.sceneCode) }}</td><td><span class="tag" :class="`tag-${q.tier.toLowerCase()}`">{{ q.tier }}</span></td><td>{{ q.priority }}</td><td>{{ q.monitorFrequency }}</td><td><b>{{ q.totalScore }}</b></td><td>{{ q.relatedNeedText }}</td><td>{{ q.batchId }}</td><td><button class="btn-text" @click="startEditQuestion(q)">编辑</button><button class="btn-text" @click="replaceQuestion(q.id)">重生成 <span v-if="q.replaceCount" class="warn">({{ q.replaceCount }})</span></button><button class="btn-text danger" @click="removeQuestion(q.id)">删除</button></td></tr></template></tbody></table>
              <div class="pager">
                <span>第 {{ questionPage.current || 1 }} / {{ questionPage.pages || 1 }} 页，每页 {{ questionPage.size || 20 }} 条</span>
                <button class="btn btn-sm" :disabled="questionLoading || (questionPage.current || 1) <= 1" @click="loadQuestionPage((questionPage.current || 1) - 1)">上一页</button>
                <button class="btn btn-sm" :disabled="questionLoading || (questionPage.current || 1) >= (questionPage.pages || 1)" @click="loadQuestionPage((questionPage.current || 1) + 1)">下一页</button>
              </div>
            </div>
          </div>
        </section>
      </main>
    </div>

    <div v-if="editVisible" class="modal-mask">
      <div class="modal-card">
        <div class="modal-head"><span>编辑问题</span><button class="btn btn-sm" @click="editVisible = false">关闭</button></div>
        <div class="modal-body">
          <Field label="问题文本" required><textarea v-model="editForm.questionText" class="text-input textarea large"></textarea></Field>
          <Field label="场景" required><select v-model="editForm.sceneCode" class="text-input"><option v-for="s in sceneOptions" :key="s.code" :value="s.code">{{ s.label }}</option></select></Field>
          <Field label="分级" required><select v-model="editForm.tier" class="text-input short" disabled><option>A</option><option>B</option><option>C</option></select><span class="inline-tip">分级影响配额，编辑时保持原层级</span></Field>
          <Field label="优先级"><input v-model="editForm.priority" class="text-input short"></Field>
          <Field label="监测频率"><input v-model="editForm.monitorFrequency" class="text-input short"></Field>
          <Field label="五项打分">
            <div class="score-grid">
              <input v-model.number="editForm.scoreRelevance" class="mini-input" title="相关性">
              <input v-model.number="editForm.scoreIntent" class="mini-input" title="意图">
              <input v-model.number="editForm.scoreCompetition" class="mini-input" title="竞争">
              <input v-model.number="editForm.scoreConversion" class="mini-input" title="转化">
              <input v-model.number="editForm.scoreCoverage" class="mini-input" title="覆盖">
              <input v-model.number="editForm.totalScore" class="mini-input" title="总分">
            </div>
          </Field>
          <Field label="对应需求"><input v-model="editForm.relatedNeedText" class="text-input"></Field>
          <Field label="设计理由"><textarea v-model="editForm.designReason" class="text-input textarea"></textarea></Field>
        </div>
        <div class="modal-foot"><button class="btn" @click="editVisible = false">取消</button><button class="btn btn-primary" @click="saveEditQuestion">保存</button></div>
      </div>
    </div>
    <div v-if="duplicateResolveVisible" class="modal-mask">
      <div class="modal-card duplicate-modal">
        <div class="modal-head">
          <span>处理重复问题</span>
          <button class="btn btn-sm" @click="duplicateResolveVisible = false">关闭</button>
        </div>
        <div class="modal-body">
          <div class="duplicate-note">以下问题文本重复，需修改为唯一内容后才能入库。</div>
          <div class="duplicate-group" v-for="group in duplicateQuestionGroups" :key="group.key">
            <div class="duplicate-title">重复问题：{{ group.text }} <span>{{ group.items.length }} 条</span></div>
            <div class="duplicate-row head"><div>ID</div><div>问题文本</div><div>分级</div><div>场景</div><div>批次</div></div>
            <div class="duplicate-row" v-for="item in group.items" :key="item.id">
              <div>{{ item.tier }}-{{ item.id }}</div>
              <textarea v-model="duplicateEditForms[item.id]" class="text-input textarea duplicate-input"></textarea>
              <div><span class="tag" :class="`tag-${item.tier.toLowerCase()}`">{{ item.tier }}</span></div>
              <div>{{ sceneLabel(item.sceneCode) }}</div>
              <div>{{ item.batchId }}</div>
            </div>
          </div>
          <div v-if="duplicateResolveError" class="duplicate-error">{{ duplicateResolveError }}</div>
        </div>
        <div class="modal-foot">
          <button class="btn" @click="duplicateResolveVisible = false">取消</button>
          <button class="btn btn-primary" :disabled="duplicateSaving" @click="saveDuplicateEdits">{{ duplicateSaving ? '保存中...' : '保存修改并重新检查' }}</button>
        </div>
      </div>
    </div>  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDictStore } from '@/stores/dict'
import {
  cancelGeoBatch,
  commitGeoWorkorder,
  createOrGetProjectWorkorder,
  deleteGeoBatch,
  deleteGeoQuestion,
  exportGeoWorkorder,
  getGeoBatch,
  getGeoProjectProfile,
  getGeoQuestions,
  getGeoProjectQuota,
  getGeoReview,
  getGeoProjectWorkorders,
  getLlmProviders,
  regenerateGeoQuestion,
  saveGeoDraft,
  startGeoBatch,
  updateGeoQuestion,
  type BatchVO,
  type ProfileVO,
  type ProviderVO,
  type QuestionPageVO,
  type QuestionVO,
  type QuotaSnapshot,
  type ReviewVO,
  type WorkorderListItem,
  type WorkorderVO,
} from '@/api/geoQuestion'
import { getProjectList } from '@/api/project'
import type { Project } from '@/types'

defineOptions({ name: 'LayeredKeywordGroupManage' })

const StepHeader = defineComponent({
  props: { activeStep: { type: Number, required: true } },
  setup(props) {
    const names = ['选择项目', '信息补全', '配置参数', '触发生成', '审核入库']
    const subtitles = ['读取项目额度', '复用+补全', '本批≤50', '异步任务', '累积+导出']
    return () => h('div', { class: 'stepper' }, names.map((name, i) => h('div', { class: ['stepper-item', props.activeStep === i + 1 ? 'active' : '', props.activeStep > i + 1 ? 'done' : ''] }, [
      h('div', { class: 'stepper-num' }, [h('span', String(i + 1))]),
      h('div', { class: 'stepper-text' }, [name, h('span', { class: 'subtitle' }, subtitles[i])]),
    ])))
  },
})
const Field = defineComponent({
  props: { label: String, required: Boolean, compact: Boolean },
  setup(props, { slots }) { return () => h('div', { class: 'form-row' }, [h('div', { class: ['form-label', props.required ? 'required' : '', props.compact ? 'compact' : ''] }, props.label), h('div', { class: 'form-content' }, slots.default?.())]) },
})
const NumBox = defineComponent({
  props: { label: String, remain: Number, modelValue: Number },
  emits: ['update:modelValue'],
  setup(props, { emit }) { return () => h('div', { class: 'col-item' }, [h('label', props.label), h('input', { class: 'num-input', type: 'number', min: 0, max: props.remain, value: props.modelValue, onInput: (e: Event) => emit('update:modelValue', Number((e.target as HTMLInputElement).value)) }), h('div', { class: 'remain-hint' }, `剩余 ${props.remain || 0} · 最大可填 ${props.remain || 0}`)]) },
})
const QuotaCard = defineComponent({
  props: { quota: Object, title: String, compact: Boolean },
  setup(props) {
    const row = (name: string, key: 'A' | 'B' | 'C') => {
      const q: any = props.quota || {}
      return h('tr', [h('td', { class: 'label' }, name), h('td', q[`quota${key}`] || 0), h('td', q[`activeUsed${key}`] || 0), h('td', q[`workorderCount${key}`] || 0), h('td', q[`runningReserved${key}`] || 0), h('td', [h('span', { class: 'quota-remain' }, q[`remaining${key}`] || 0)])])
    }
    return () => h('div', { class: 'card' }, [h('div', { class: 'card-head' }, [h('span', props.title)]), h('div', { class: 'card-body' }, [h('table', { class: 'quota-table' }, [h('thead', [h('tr', ['维度', '项目额度', '已占用', '当前工单未删除题目数', 'running 批次预占', '剩余可生成'].map((x) => h('th', x)))]), h('tbody', [row('A 类（承诺考核）', 'A'), row('B 类（重点观察）', 'B'), row('C 类（长尾铺底）', 'C')])]), h('div', { class: 'note' }, '剩余可生成 = 项目额度 - 当前工单未删除题目数 - running 批次预占数')])])
  },
})

const steps = [{ no: 1, title: '选择项目' }, { no: 2, title: '信息补全' }, { no: 3, title: '配置参数' }, { no: 4, title: '生成中' }, { no: 5, title: '审核入库' }]
const sceneOptions = [{ code: 'brand', label: '品牌' }, { code: 'decision', label: '决策' }, { code: 'deal', label: '成交' }, { code: 'compare', label: '对比' }, { code: 'qa', label: '问答' }, { code: 'function', label: '功能' }]
const baseline = [25, 30, 25, 25, 25, 20]

const activeStep = ref(1)
const dictStore = useDictStore()
const keyword = ref('')
const projectOptions = ref<Project[]>([])
const projectLoading = ref(false)
const selectedProjectId = ref<number | null>(null)
const workorder = ref<WorkorderVO>()
const quota = ref<QuotaSnapshot>()
const providers = ref<ProviderVO[]>([])
const currentBatch = ref<BatchVO>()
const review = ref<ReviewVO>()
const questionPage = ref<QuestionPageVO>({ records: [], total: 0, current: 1, size: 20, pages: 0 })
const questionLoading = ref(false)
const workorderList = ref<WorkorderListItem[]>([])
const editVisible = ref(false)
const editingQuestionId = ref<number>()
const duplicateResolveVisible = ref(false)
const duplicateSaving = ref(false)
const duplicateResolveError = ref('')
const duplicateEditForms = reactive<Record<number, string>>({})
const tierTab = ref<'all' | 'A' | 'B' | 'C'>('all')
const questionTabs = ['all', 'A', 'B', 'C'] as const
const syncToProfile = ref(false)

const profile = reactive<ProfileVO>({
  companyId: 0, companyName: '', brandName: '', brandRelation: '自营', coreBusiness: [], targetRegion: '', industry: '',
  targetCustomer: '', coreAdvantage: '', benchmarkSpecs: '', competitors: [], coreNeeds: [],
})
const projectCascadeProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: false,
}
const projectCascadeOptions = computed(() => buildProjectCascadeOptions(projectOptions.value))
const selectedProject = computed(() => projectOptions.value.find((project) => project.id === selectedProjectId.value) || null)
const coreBusinessText = computed({
  get: () => normalizeTags(profile.coreBusiness).map(industryLabel).join('、'),
  set: (v: string) => { profile.coreBusiness = splitText(v).map(industryKey) },
})
const industryText = computed({
  get: () => industryLabel(profile.industry),
  set: (v: string) => { profile.industry = industryKey(v) },
})
const batchForm = reactive({ batchA: 10, batchB: 20, batchC: 20, modelConfigId: undefined as number | undefined, sceneWeights: {} as Record<string, number>, temperature: 0.7 })
const editForm = reactive<Partial<QuestionVO>>({})
const selectedProvider = computed(() => providers.value.find((p) => p.id === batchForm.modelConfigId))
const batchTotal = computed(() => Number(batchForm.batchA || 0) + Number(batchForm.batchB || 0) + Number(batchForm.batchC || 0))
const weightTotal = computed(() => Object.values(batchForm.sceneWeights).reduce((sum, v) => sum + Number(v || 0), 0))
const hasRunningBatch = computed(() => review.value?.batches.some((b) => ['pending', 'running'].includes(b.status)) || ['pending', 'running'].includes(currentBatch.value?.status || ''))
const validationMessage = computed(() => {
  if (batchTotal.value < 1) return '本批合计必须至少 1 条'
  if (batchTotal.value > 50) return '单批合计不得超过 50'
  if (batchForm.batchA > (quota.value?.remainingA || 0)) return `A 类剩余仅 ${quota.value?.remainingA || 0}`
  if (batchForm.batchB > (quota.value?.remainingB || 0)) return `B 类剩余仅 ${quota.value?.remainingB || 0}`
  if (batchForm.batchC > (quota.value?.remainingC || 0)) return `C 类剩余仅 ${quota.value?.remainingC || 0}`
  if (weightTotal.value !== batchTotal.value) return '场景权重总和必须等于本批合计'
  return ''
})
const progressObj = computed(() => {
  try { return currentBatch.value?.progressJson ? JSON.parse(currentBatch.value.progressJson) : {} } catch { return {} }
})
const progressPercent = computed(() => Math.min(100, Math.round(((progressObj.value.generated || 0) / Math.max(progressObj.value.target || batchTotal.value || 1, 1)) * 100)))
const pagedQuestions = computed(() => questionPage.value.records || [])
const duplicateQuestionGroups = computed(() => findDuplicateQuestionGroups(review.value?.questions || []))
const duplicateQuestionTexts = computed(() => duplicateQuestionGroups.value.map((group) => group.text))
const canCommit = computed(() => !!quota.value && quota.value.remainingA === 0 && quota.value.remainingB === 0 && quota.value.remainingC === 0 && quota.value.runningReservedTotal === 0)

onMounted(async () => { await Promise.all([dictStore.ensureLoaded(), loadProjects(), loadProviders()]); applyBaselineWeights() })
watch(batchTotal, () => applyBaselineWeights())
watch(tierTab, () => loadQuestionPage(1))

async function loadProjects() {
  projectLoading.value = true
  try {
    const { data } = await getProjectList({ current: 1, size: 500, keyword: keyword.value || undefined, status: 'paused' })
    projectOptions.value = data.data.records || []
  } finally {
    projectLoading.value = false
  }
}
async function loadProviders() {
  const { data } = await getLlmProviders()
  providers.value = data.data || []
  batchForm.modelConfigId ||= providers.value[0]?.id
}
async function handleProjectChange(projectId: number | null) {
  const project = projectOptions.value.find((item) => item.id === projectId)
  if (!project) {
    selectedProjectId.value = null
    return
  }
  await selectProject(project)
}
async function selectProject(item: Project) {
  selectedProjectId.value = item.id
  if (projectQuotaTotal(item) <= 0) { ElMessage.warning('当前项目未配置问题额度，不能进入分层拓词管理'); return }
  const { data } = await createOrGetProjectWorkorder(item.id)
  workorder.value = data.data
  quota.value = data.data.quota
  await loadWorkorderList()
  const resumableWorkorder = workorderList.value.find((row) => row.id === data.data.id && ['draft', 'paused'].includes(row.status) && row.countTotal > 0)
  if (resumableWorkorder) {
    await openWorkorderReview(resumableWorkorder)
  }
}
async function loadWorkorderList() {
  if (!selectedProject.value) return
  const { data } = await getGeoProjectWorkorders(selectedProject.value.id)
  workorderList.value = data.data || []
}
async function openWorkorderReview(item: WorkorderListItem) {
  const { data } = await getGeoReview(item.id)
  review.value = data.data
  workorder.value = data.data.workorder
  quota.value = data.data.workorder.quota
  questionPage.value = { ...questionPage.value, current: 1 }
  await loadQuestionPage(1)
  activeStep.value = 5
}
async function goStep(stepNo: number) {
  if (stepNo === activeStep.value) return
  if (stepNo > 1 && (!selectedProject.value || !workorder.value)) {
    ElMessage.warning('请先选择项目')
    return
  }
  if (stepNo === 2) {
    await goStep2()
    return
  }
  activeStep.value = stepNo
}
async function goStep2() {
  if (!selectedProject.value || !workorder.value) return
  const { data } = await getGeoProjectProfile(selectedProject.value.id)
  Object.assign(profile, data.data)
  if (!profile.competitors.length) addCompetitor()
  if (!profile.coreNeeds.length) { addNeed(); addNeed(); addNeed() }
  activeStep.value = 2
}
async function saveDraft() {
  if (!workorder.value) return
  await saveGeoDraft({ workorderId: workorder.value.id, profileJson: JSON.stringify(profile), syncToCustomerProfile: syncToProfile.value, validationStatus: 'valid' })
  ElMessage.success('草稿已保存')
}
async function goStep3() {
  await saveDraft()
  await refreshQuota()
  fillToLimit()
  activeStep.value = 3
}
async function refreshQuota() {
  if (!selectedProject.value || !workorder.value) return
  const { data } = await getGeoProjectQuota(selectedProject.value.id, workorder.value.id)
  quota.value = data.data
}
function addCompetitor() { profile.competitors.push({ competitorName: '', advantages: '', disadvantages: '' }) }
function addNeed() { profile.coreNeeds.push({ text: '', scene: 'brand', urgent: false }) }
function industryLabel(value?: string | null) {
  if (!value) return '-'
  return dictStore.label('industry_tag', value) || value
}
function projectStatusLabel(value?: string | null) {
  return dictStore.label('project_status', value) || value || '-'
}
function industryKey(value?: string | null) {
  if (!value) return ''
  const raw = value.trim()
  const hit = dictStore.options('industry_tag').find((item) => item.dictValue === raw || item.dictKey === raw)
  return hit?.dictKey || raw
}
function splitText(value: string): string[] {
  return value.split(/[、,，\s]+/).map((item) => item.trim()).filter(Boolean)
}
interface ProjectCascadeNode {
  value: string | number
  label: string
  children?: ProjectCascadeNode[]
}
function buildProjectCascadeOptions(projects: Project[]): ProjectCascadeNode[] {
  const companyMap = new Map<string, ProjectCascadeNode>()
  const brandMap = new Map<string, ProjectCascadeNode>()
  const sortedProjects = [...projects].sort(compareProjectsForCascade)
  for (const project of sortedProjects) {
    const companyKey = `company:${project.companyId ?? 'none'}:${project.companyName || '未归属客户'}`
    let companyNode = companyMap.get(companyKey)
    if (!companyNode) {
      companyNode = { value: companyKey, label: project.companyName || '未归属客户', children: [] }
      companyMap.set(companyKey, companyNode)
    }
    const brandKey = `${companyKey}:brand:${project.brandId ?? 'none'}:${project.brandName || '未绑定品牌'}`
    let brandNode = brandMap.get(brandKey)
    if (!brandNode) {
      brandNode = { value: brandKey, label: project.brandName || '未绑定品牌', children: [] }
      brandMap.set(brandKey, brandNode)
      companyNode.children?.push(brandNode)
    }
    brandNode.children?.push({ value: project.id, label: project.projectName || `项目 #${project.id}` })
  }
  return Array.from(companyMap.values())
}
function compareProjectsForCascade(a: Project, b: Project) {
  return [
    compareText(a.companyName, b.companyName),
    compareText(a.brandName, b.brandName),
    compareText(a.projectName, b.projectName),
    a.id - b.id,
  ].find((result) => result !== 0) || 0
}
function compareText(a?: string | null, b?: string | null) {
  return (a || '').localeCompare(b || '', 'zh-Hans-CN')
}
function projectQuotaA(project?: Project | null) {
  if (!project) return 0
  const a = project.planKeywordGroupLimitA ?? 0
  const b = project.planKeywordGroupLimitB ?? 0
  const c = project.planKeywordGroupLimitC ?? 0
  return a || (b || c ? 0 : project.planKeywordGroupLimit ?? 0)
}
function projectQuotaB(project?: Project | null) {
  return project?.planKeywordGroupLimitB ?? 0
}
function projectQuotaC(project?: Project | null) {
  return project?.planKeywordGroupLimitC ?? 0
}
function projectQuotaTotal(project?: Project | null) {
  return projectQuotaA(project) + projectQuotaB(project) + projectQuotaC(project)
}
function normalizeTags(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.flatMap((item) => normalizeTags(item)).filter(Boolean)
  }
  if (typeof value !== 'string') return []
  const raw = value.trim()
  if (!raw) return []
  if (raw.startsWith('[')) {
    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) return parsed.map(String).filter(Boolean)
    } catch {
      // fall through to delimiter split
    }
  }
  return splitText(raw)
}
function largestRemainder(total: number, ratios: number[]) {
  const sum = ratios.reduce((a, b) => a + b, 0)
  if (!sum || !total) return ratios.map(() => 0)
  const raw = ratios.map((r) => (total * r) / sum)
  const base = raw.map(Math.floor)
  let remain = total - base.reduce((a, b) => a + b, 0)
  raw.map((v, i) => ({ i, rest: v - Math.floor(v) })).sort((a, b) => b.rest - a.rest || a.i - b.i).forEach(({ i }) => { if (remain > 0) { base[i] += 1; remain -= 1 } })
  return base
}
function applyBaselineWeights() {
  const values = largestRemainder(batchTotal.value, baseline)
  sceneOptions.forEach((s, i) => { batchForm.sceneWeights[s.code] = values[i] || 0 })
}
function fillToLimit() {
  const r = quota.value
  if (!r) return
  let left = Math.min(50, r.remainingTotal)
  batchForm.batchA = Math.min(r.remainingA, left); left -= batchForm.batchA
  batchForm.batchB = Math.min(r.remainingB, left); left -= batchForm.batchB
  batchForm.batchC = Math.min(r.remainingC, left)
}
function splitByRatio() {
  const r = quota.value
  if (!r) return
  const total = Math.min(50, r.remainingTotal)
  const [a, b, c] = largestRemainder(total, [r.remainingA, r.remainingB, r.remainingC])
  batchForm.batchA = Math.min(a, r.remainingA); batchForm.batchB = Math.min(b, r.remainingB); batchForm.batchC = Math.min(c, r.remainingC)
}
async function startBatch() {
  if (!workorder.value || validationMessage.value) return
  const provider = selectedProvider.value
  const { data } = await startGeoBatch({ workorderId: workorder.value.id, ...batchForm, modelProvider: provider?.platformCode, modelId: provider?.modelId, modelName: provider?.modelName })
  currentBatch.value = data.data
  activeStep.value = 4
  pollBatch()
}
async function pollBatch() {
  if (!currentBatch.value) return
  const { data } = await getGeoBatch(currentBatch.value.id)
  currentBatch.value = data.data
  if (['pending', 'running'].includes(currentBatch.value.status)) window.setTimeout(pollBatch, 2000)
}
async function cancelBatch() {
  if (!currentBatch.value) return
  await cancelGeoBatch(currentBatch.value.id)
  ElMessage.success('已请求中断')
  await pollBatch()
}
async function goReview() { if (!workorder.value) return; await refreshReview(); activeStep.value = 5 }
async function refreshReview() {
  if (!workorder.value) return
  const { data } = await getGeoReview(workorder.value.id)
  review.value = data.data
  quota.value = data.data.workorder.quota
  await loadQuestionPage(questionPage.value.current || 1)
  await loadWorkorderList()
}
async function loadQuestionPage(current = 1) {
  if (!workorder.value) return
  questionLoading.value = true
  try {
    const { data } = await getGeoQuestions(workorder.value.id, { tier: tierTab.value, current, size: questionPage.value.size || 20 })
    questionPage.value = data.data || { records: [], total: 0, current, size: questionPage.value.size || 20, pages: 0 }
  } finally {
    questionLoading.value = false
  }
}
function continueGenerate() { fillToLimit(); activeStep.value = 3 }
async function removeBatch(id: number) { await ElMessageBox.confirm('确认软删除该批次并释放额度？'); await deleteGeoBatch(id); await refreshReview() }
async function removeQuestion(id: number) { await ElMessageBox.confirm('确认软删除该题并释放所属层级 1 个额度？'); await deleteGeoQuestion(id); await refreshReview() }
async function replaceQuestion(id: number) { const { data } = await regenerateGeoQuestion(id); if (data.data?.softWarning) ElMessage.warning(data.data.warningMessage); await refreshReview() }
function startEditQuestion(question: QuestionVO) {
  editingQuestionId.value = question.id
  Object.assign(editForm, { ...question })
  editVisible.value = true
}
async function saveEditQuestion() {
  if (!editingQuestionId.value) return
  await updateGeoQuestion(editingQuestionId.value, editForm)
  ElMessage.success('问题已保存')
  editVisible.value = false
  await refreshReview()
}
async function commit() {
  if (!workorder.value) return
  await refreshReview()
  if (duplicateQuestionTexts.value.length) {
    ElMessage.warning(`问题池存在重复问题，请替换或删除后再入库：${duplicateQuestionTexts.value.slice(0, 5).join('；')}`)
    openDuplicateResolveDialog()
    return
  }
  if (!canCommit.value) {
    ElMessage.warning('三级配额未满或存在运行中批次，无法入库')
    return
  }
  await commitGeoWorkorder(workorder.value.id, 'v1.0')
  ElMessage.success('已入库为客户级正式版本')
  await refreshReview()
}async function exportHint() {
  if (!workorder.value) return
  const response = await exportGeoWorkorder(workorder.value.id)
  const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `问题池工单-${workorder.value.id}.csv`
  link.click()
  URL.revokeObjectURL(url)
}
function batchReplaceCount(batchId: number) { return review.value?.batches.find((b) => b.id === batchId)?.replaceCountTotal || 0 }
function sceneLabel(code?: string) { return sceneOptions.find((s) => s.code === code)?.label || code || '-' }
function formatTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }
function questionDedupeKey(value?: string | null) {
  return (value || '').trim().replace(/\s+/g, ' ').toLowerCase()
}
function findDuplicateQuestionGroups(questions: QuestionVO[]) {
  const groups = new Map<string, QuestionVO[]>()
  questions.forEach((question) => {
    const key = questionDedupeKey(question.questionText)
    if (!key) return
    groups.set(key, [...(groups.get(key) || []), question])
  })
  return Array.from(groups.entries())
    .filter(([, items]) => items.length > 1)
    .map(([key, items]) => ({ key, text: items[0].questionText.trim(), items }))
}
function openDuplicateResolveDialog() {
  duplicateResolveError.value = ''
  Object.keys(duplicateEditForms).forEach((id) => delete duplicateEditForms[Number(id)])
  duplicateQuestionGroups.value.forEach((group) => {
    group.items.forEach((item) => {
      duplicateEditForms[item.id] = item.questionText
    })
  })
  duplicateResolveVisible.value = true
}
async function saveDuplicateEdits() {
  duplicateResolveError.value = ''
  const allQuestions = review.value?.questions || []
  const nextTexts = new Map<number, string>()
  for (const question of allQuestions) {
    const nextText = (duplicateEditForms[question.id] ?? question.questionText).trim()
    if (!nextText) {
      duplicateResolveError.value = `问题 ${question.tier}-${question.id} 的文本不能为空`
      return
    }
    if (nextText.length > 500) {
      duplicateResolveError.value = `问题 ${question.tier}-${question.id} 的文本最多 500 字`
      return
    }
    nextTexts.set(question.id, nextText)
  }
  const duplicateAfterEdit = findDuplicateQuestionGroups(allQuestions.map((question) => ({ ...question, questionText: nextTexts.get(question.id) || question.questionText })))
  if (duplicateAfterEdit.length) {
    duplicateResolveError.value = `仍存在重复问题：${duplicateAfterEdit.map((group) => group.text).slice(0, 5).join('；')}`
    return
  }
  const changedQuestions = allQuestions.filter((question) => {
    const nextText = nextTexts.get(question.id)
    return nextText !== undefined && nextText !== question.questionText
  })
  if (!changedQuestions.length) {
    duplicateResolveError.value = '请先修改重复的问题文本'
    return
  }
  duplicateSaving.value = true
  try {
    for (const question of changedQuestions) {
      await updateGeoQuestion(question.id, { ...question, questionText: nextTexts.get(question.id) })
    }
    await refreshReview()
    if (duplicateQuestionTexts.value.length) {
      openDuplicateResolveDialog()
      duplicateResolveError.value = `仍存在重复问题：${duplicateQuestionTexts.value.slice(0, 5).join('；')}`
      return
    }
    duplicateResolveVisible.value = false
    ElMessage.success('重复问题已处理，可以继续入库')
  } finally {
    duplicateSaving.value = false
  }
}function statusTagClass(status?: string) {
  if (status === 'committed') return 'tag-success'
  if (status === 'draft') return 'tag-primary'
  if (status === 'discarded') return 'tag-danger'
  return 'tag-warning'
}
</script>

<style scoped>
.layered-keyword-page{--bg:#f5f6f8;--surface:#fff;--border:#d9d9d9;--border-dashed:#bfbfbf;--text:#1f2329;--text-2:#4e5969;--text-3:#86909c;--text-4:#c9cdd4;--primary:#1677ff;--primary-light:#e6f4ff;--success:#52c41a;--warning:#faad14;--danger:#ff4d4f;background:var(--bg);color:var(--text);font-size:13px;line-height:1.5;min-height:100vh;font-family:-apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif}
.step-switcher{position:sticky;top:0;z-index:20;background:#001529;padding:12px 24px;display:flex;align-items:center;gap:8px;box-shadow:0 2px 8px rgba(0,0,0,.15);margin:-24px -24px 16px}.step-switcher .label{color:#fff;font-weight:600;margin-right:16px}.step-btn{background:transparent;border:1px solid #2c3e50;color:#c9cdd4;padding:6px 14px;border-radius:4px;cursor:pointer}.step-btn.active{background:var(--primary);border-color:var(--primary);color:#fff}
.main{padding:0 0 24px}.breadcrumb{color:var(--text-2);margin-bottom:12px;font-size:12px}.sep{color:#c9cdd4;margin:0 6px}.current{color:var(--text)}
.stepper{background:var(--surface);border:1px solid var(--border);border-radius:4px;padding:20px 32px;margin-bottom:16px;display:flex;justify-content:space-between}.stepper-item{display:flex;align-items:center;gap:8px;flex:1}.stepper-item:not(:last-child)::after{content:'';flex:1;height:1px;background:var(--border);margin-left:12px}.stepper-num{width:24px;height:24px;border-radius:50%;background:#fff;border:1px solid var(--border);color:var(--text-3);display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:600;flex-shrink:0}.stepper-item.active .stepper-num,.stepper-item.done .stepper-num{background:var(--primary);border-color:var(--primary);color:#fff}.stepper-item.done .stepper-num::before{content:'✓'}.stepper-item.done .stepper-num span{display:none}.stepper-text{font-size:13px;color:var(--text)}.stepper-item.active .stepper-text{color:var(--primary);font-weight:600}.stepper-text .subtitle{font-size:11px;color:var(--text-3);display:block;font-weight:400}
.card{background:var(--surface);border:1px solid var(--border);border-radius:4px;margin-bottom:16px}.card-head{padding:12px 20px;border-bottom:1px solid var(--border);font-size:14px;font-weight:600;display:flex;justify-content:space-between;align-items:center}.card-body{padding:20px}.design-note{background:#f0f5ff;border-left:3px solid var(--primary);border-top:0;border-right:0;border-bottom:0;border-radius:0 2px 2px 0;padding:10px 14px;margin-bottom:16px;color:var(--text-2)}.design-note strong{color:var(--primary)}.note{background:#fffbe6;border:1px dashed #ffe58f;border-radius:2px;padding:8px 12px;margin:12px 0 0;color:#874d00}.note-inline{display:inline-block;background:#fffbe6;border:1px dashed #ffe58f;padding:1px 8px;border-radius:2px;font-size:11px;color:#874d00;margin-left:8px;font-weight:400}.error-note{background:#fff1f0;border-color:#ffccc7;color:#a8071a}
.btn{display:inline-block;padding:5px 14px;border:1px solid var(--border);border-radius:2px;background:#fff;color:var(--text);cursor:pointer;font-size:13px;margin-left:8px}.btn-primary{background:var(--primary);border-color:var(--primary);color:#fff}.btn-danger{color:var(--danger);border-color:#ffccc7}.btn-disabled,.btn:disabled{background:#f5f5f5;color:#bfbfbf;cursor:not-allowed}.btn-sm{padding:3px 10px;font-size:12px}.btn-text{border:0;background:transparent;color:var(--primary);cursor:pointer;margin-right:8px}.btn-text.danger{color:var(--danger)}
.text-input{border:1px solid var(--border);background:#fff;padding:7px 10px;border-radius:2px;min-width:260px;font-size:13px}.text-input.short{min-width:100px;width:100px}.textarea{width:100%;min-height:64px}.textarea.large{min-height:110px}.mini-input{width:52px;border:1px dashed #bfbfbf;padding:4px 6px;text-align:center}.search-row{display:flex;gap:8px;margin-bottom:14px;align-items:center}.project-cascader{width:360px}.customer-list{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.customer-card{text-align:left;background:#fafafa;border:1px dashed #bfbfbf;border-radius:4px;padding:12px;cursor:pointer}.customer-card.selected{border-color:var(--primary);background:var(--primary-light)}.meta{color:var(--text-3);margin-top:6px}.selected-info{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-top:16px}.item label,.overview-grid label{display:block;color:var(--text-3);font-size:12px}
.duplicate-modal{width:920px;max-width:calc(100vw - 48px)}.duplicate-modal .modal-body{max-height:68vh;overflow:auto}.duplicate-note{background:#fffbe6;border:1px solid #ffe58f;color:#874d00;border-radius:4px;padding:10px 12px;margin-bottom:14px}.duplicate-group{border:1px solid var(--border);border-radius:4px;margin-bottom:14px;overflow:hidden}.duplicate-title{background:#fafafa;border-bottom:1px solid var(--border);padding:10px 12px;font-weight:600;display:flex;justify-content:space-between;gap:12px}.duplicate-title span{color:var(--danger);font-weight:400}.duplicate-row{display:grid;grid-template-columns:76px minmax(260px,1fr) 64px 80px 80px;gap:10px;align-items:center;padding:10px 12px;border-top:1px solid #f0f0f0}.duplicate-row.head{border-top:0;background:#fff;color:var(--text-2);font-size:12px;font-weight:500}.duplicate-input{width:100%;min-width:0;min-height:54px;box-sizing:border-box}.duplicate-error{background:#fff1f0;border:1px solid #ffccc7;color:#a8071a;border-radius:4px;padding:10px 12px;margin-top:12px}
.tag{display:inline-block;padding:1px 6px;border-radius:2px;background:#f0f2f5;font-size:12px;margin-left:6px}.tag-primary{background:#e6f4ff;color:var(--primary)}.tag-success{background:#f6ffed;color:#389e0d}.tag-warning{background:#fff7e6;color:#d48806}.tag-danger{background:#fff1f0;color:var(--danger)}.tag-a{background:#fff1f0;color:#cf1322}.tag-b{background:#fff7e6;color:#d48806}.tag-c{background:#f6ffed;color:#389e0d}
.quota-table{width:100%;border-collapse:collapse;font-size:13px;background:#fff}.quota-table th,.quota-table td{padding:8px 16px;border:1px solid var(--border);text-align:center;vertical-align:middle}.quota-table th{background:#fafafa;color:var(--text-2);font-weight:600}.quota-table .label{background:#fafafa;font-weight:600;text-align:left}.quota-remain{font-weight:700;color:var(--primary);font-size:16px}.quota-table.small th,.quota-table.small td{padding:6px 8px}.ok{color:var(--success)}.bad,.warn{color:var(--warning)}
.source-legend{display:flex;align-items:center;gap:20px;background:#fff;border:1px solid var(--border);padding:10px 16px;margin-bottom:16px}.src-icon{display:inline-block;width:16px;height:16px;border-radius:2px;text-align:center;line-height:16px;font-size:10px;margin-right:6px}.src-from-profile{background:#e6f4ff;color:var(--primary)}.src-modified{background:#fff7e6;color:var(--warning)}.src-missing{background:#fff1f0;color:var(--danger)}
.form-row{display:flex;align-items:flex-start;margin-bottom:14px}.form-label{width:140px;text-align:right;padding-top:8px;padding-right:12px;color:var(--text-2);flex-shrink:0}.form-label.compact{width:80px}.form-label.required::before{content:'* ';color:var(--danger)}.form-content{flex:1}.form-tip,.inline-tip,.save-tip{font-size:12px;color:var(--text-3);margin-left:8px}.competitor-item,.need-item{border:1px solid var(--border);border-radius:4px;padding:12px;margin-bottom:12px;background:#fafafa}.need-head{display:flex;justify-content:space-between;margin-bottom:10px}.switch-line{margin-left:auto;color:var(--text-2)}
.batch-input-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:20px}.col-item{border:1px solid var(--border);background:#fafafa;padding:14px;border-radius:4px}.col-item label{display:block;color:var(--text-3);margin-bottom:8px}.col-item.total{background:var(--primary-light);border-color:#91caff}.num-input{width:88px;border:1px dashed #bfbfbf;padding:4px 8px;font-size:18px;font-weight:600}.big-num{font-size:24px;font-weight:700;color:var(--primary)}.remain-hint{font-size:12px;color:var(--text-3);margin-top:6px}
.footer-bar{background:#fff;border:1px solid var(--border);border-radius:4px;padding:12px 20px;display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;color:var(--text-3)}.quota-actions{background:#fafafa}.overview-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:16px}.progress-bar{height:12px;background:#f0f2f5;border-radius:99px;overflow:hidden;margin:12px 0}.progress-bar div{height:100%;background:var(--primary)}.progress-text{color:var(--text-2);margin-bottom:12px}.log-area{background:#001529;color:#c9cdd4;border-radius:4px;padding:12px;font-family:monospace;min-height:120px}.log-time{color:#86909c;margin-right:10px}.log-info{color:#e6f4ff}
.batch-list{padding:0}.batch-row{display:grid;grid-template-columns:1fr 1.6fr 1.2fr .8fr .7fr 2fr;gap:12px;padding:10px 16px;border-bottom:1px solid #f0f0f0;align-items:center}.batch-row.head{font-weight:600;background:#fafafa;color:var(--text-2)}.workorder-row{display:grid;grid-template-columns:1fr 1.3fr 1.4fr .7fr 1.2fr 1fr;gap:12px;padding:10px 16px;border-bottom:1px solid #f0f0f0;align-items:center}.workorder-row.head{font-weight:600;background:#fafafa;color:var(--text-2)}.empty-row{padding:18px 16px;color:var(--text-3);text-align:center}
.tab-bar{display:flex;gap:8px;margin-bottom:12px}.tab{border:1px solid var(--border);background:#fff;padding:6px 14px;cursor:pointer}.tab.active{background:var(--primary);border-color:var(--primary);color:#fff}.table{width:100%;border-collapse:collapse;font-size:12px}.table th,.table td{border-bottom:1px solid #f0f0f0;padding:8px;text-align:left;vertical-align:top}.table th{background:#fafafa;color:var(--text-2)}.qtext{max-width:420px}.reason{color:var(--text-3);font-size:11px;margin-top:3px}
.pager{display:flex;justify-content:flex-end;align-items:center;gap:8px;margin-top:12px;color:var(--text-3)}
.modal-mask{position:fixed;inset:0;background:rgba(0,0,0,.35);z-index:50;display:flex;align-items:center;justify-content:center}.modal-card{width:760px;max-height:86vh;background:#fff;border-radius:4px;border:1px solid var(--border);box-shadow:0 8px 24px rgba(0,0,0,.18);display:flex;flex-direction:column}.modal-head,.modal-foot{padding:12px 16px;border-bottom:1px solid var(--border);display:flex;justify-content:space-between;align-items:center;font-weight:600}.modal-foot{border-top:1px solid var(--border);border-bottom:0;justify-content:flex-end}.modal-body{padding:16px;overflow:auto}.score-grid{display:flex;gap:8px;align-items:center}
.layered-keyword-page :deep(.stepper){background:var(--surface);border:1px solid var(--border);border-radius:4px;padding:20px 32px;margin-bottom:16px;display:flex;justify-content:space-between}
.layered-keyword-page :deep(.stepper-item){display:flex;align-items:center;gap:8px;flex:1}
.layered-keyword-page :deep(.stepper-item:not(:last-child)::after){content:'';flex:1;height:1px;background:var(--border);margin-left:12px}
.layered-keyword-page :deep(.stepper-num){width:24px;height:24px;border-radius:50%;background:#fff;border:1px solid var(--border);color:var(--text-3);display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:600;flex-shrink:0}
.layered-keyword-page :deep(.stepper-item.active .stepper-num),.layered-keyword-page :deep(.stepper-item.done .stepper-num){background:var(--primary);border-color:var(--primary);color:#fff}
.layered-keyword-page :deep(.stepper-item.done .stepper-num::before){content:'✓'}
.layered-keyword-page :deep(.stepper-item.done .stepper-num span){display:none}
.layered-keyword-page :deep(.stepper-text){font-size:13px;color:var(--text)}
.layered-keyword-page :deep(.stepper-item.active .stepper-text){color:var(--primary);font-weight:600}
.layered-keyword-page :deep(.stepper-text .subtitle){font-size:11px;color:var(--text-3);display:block;font-weight:400}
.layered-keyword-page :deep(.card){background:var(--surface);border:1px solid var(--border);border-radius:4px;margin-bottom:16px}
.layered-keyword-page :deep(.card-head){padding:12px 20px;border-bottom:1px solid var(--border);font-size:14px;font-weight:600;display:flex;justify-content:space-between;align-items:center}
.layered-keyword-page :deep(.card-body){padding:20px}
.layered-keyword-page :deep(.quota-table){width:100%;border-collapse:collapse;font-size:13px;background:#fff}
.layered-keyword-page :deep(.quota-table th),.layered-keyword-page :deep(.quota-table td){padding:8px 16px;border:1px solid var(--border);text-align:center;vertical-align:middle}
.layered-keyword-page :deep(.quota-table th){background:#fafafa;color:var(--text-2);font-weight:600}
.layered-keyword-page :deep(.quota-table .label){background:#fafafa;font-weight:600;text-align:left}
.layered-keyword-page :deep(.quota-remain){font-weight:700;color:var(--primary);font-size:16px}
.layered-keyword-page :deep(.note){background:#fffbe6;border:1px dashed #ffe58f;border-radius:2px;padding:8px 12px;margin:12px 0 0;color:#874d00}
.layered-keyword-page :deep(.form-row){display:flex;align-items:flex-start;margin-bottom:14px}
.layered-keyword-page :deep(.form-label){width:140px;text-align:right;padding-top:8px;padding-right:12px;color:var(--text-2);flex-shrink:0}
.layered-keyword-page :deep(.form-label.compact){width:80px}
.layered-keyword-page :deep(.form-label.required::before){content:'* ';color:var(--danger)}
.layered-keyword-page :deep(.form-content){flex:1}
.layered-keyword-page :deep(.col-item){border:1px solid var(--border);background:#fafafa;padding:14px;border-radius:4px}
.layered-keyword-page :deep(.col-item label){display:block;color:var(--text-3);margin-bottom:8px}
.layered-keyword-page :deep(.col-item.total){background:var(--primary-light);border-color:#91caff}
.layered-keyword-page :deep(.num-input){width:88px;border:1px dashed #bfbfbf;padding:4px 8px;font-size:18px;font-weight:600}
.layered-keyword-page :deep(.big-num){font-size:24px;font-weight:700;color:var(--primary)}
.layered-keyword-page :deep(.remain-hint){font-size:12px;color:var(--text-3);margin-top:6px}
</style>
