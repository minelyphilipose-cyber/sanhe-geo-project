<template>
  <div class="special-industry-config-page">
    <div class="admin-page-header">
      <div>
        <h2>特殊行业配置中心</h2>
        <p>统一维护金融、教育、法律、医疗等强监管行业的识别、选题、规则、内核和渠道模块。</p>
      </div>
      <div class="header-actions">
        <el-button v-if="activeTab === 'rules'" @click="openRuleTester">规则测试</el-button>
        <el-button type="primary" @click="openCreate">新增配置</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="loadCurrent">
      <el-tab-pane label="行业管理" name="industries" />
      <el-tab-pane label="选题角度" name="angles" />
      <el-tab-pane label="合规规则" name="rules" />
      <el-tab-pane label="合规内核" name="kernels" />
      <el-tab-pane label="渠道模块" name="styles" />
      <el-tab-pane label="命中日志" name="logs" />
    </el-tabs>

    <section class="tab-guide">
      <div>
        <strong>{{ currentGuide.title }}</strong>
        <p>{{ currentGuide.description }}</p>
      </div>
      <ul>
        <li v-for="item in currentGuide.tips" :key="item">{{ item }}</li>
      </ul>
    </section>

    <el-card class="admin-table-card">
      <el-table v-loading="loading" :data="records" border>
        <el-table-column v-for="column in columns" :key="column.prop" :label="column.label" :min-width="column.width || 120" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="column.tag" size="small" :type="columnTagType(column.prop, row[column.prop])">
              {{ formatColumnValue(row, column) }}
            </el-tag>
            <span v-else>{{ formatColumnValue(row, column) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab !== 'logs'" label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        class="table-pagination"
        layout="total, sizes, prev, pager, next"
        :total="pagination.total"
        @current-change="loadCurrent"
        @size-change="loadCurrent"
      />
    </el-card>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑配置' : '新增配置'" width="720px">
      <el-form class="special-industry-form" :model="form" label-position="top">
        <template v-if="activeTab === 'industries'">
          <el-form-item label="行业编码"><el-input v-model="form.industryCode" placeholder="如 finance、education、legal；保存后会同步行业字典" /></el-form-item>
          <el-form-item label="行业名称"><el-input v-model="form.industryName" placeholder="如 金融、教育、法律" /></el-form-item>
          <el-form-item label="监管域">
            <el-select v-model="form.regulatoryDomain" style="width: 100%">
              <el-option label="医疗" value="medical" />
              <el-option label="金融" value="finance" />
              <el-option label="教育" value="education" />
              <el-option label="法律" value="legal" />
              <el-option label="通用特殊行业" value="custom" />
            </el-select>
          </el-form-item>
          <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" /></el-form-item>
          <el-form-item label="识别关键词"><el-input v-model="form.keywords" placeholder="多个关键词用逗号分隔，如 金融,理财,贷款" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" placeholder="说明行业适用范围、客户类型或配置注意事项" /></el-form-item>
        </template>

        <template v-else-if="activeTab === 'angles'">
          <el-form-item label="行业"><MedicalIndustrySelect v-model="form.industryCode" /></el-form-item>
          <el-form-item label="品类编码"><el-input v-model="form.categoryCode" placeholder="如 skin_laser、implant_tooth；需与客户项目资质品类一致" /></el-form-item>
          <el-form-item label="品类名称"><el-input v-model="form.categoryName" placeholder="运营可读名称，如 皮肤光电、种植牙" /></el-form-item>
          <el-form-item label="选题角度"><el-input v-model="form.topicAngle" type="textarea" :rows="3" placeholder="写成可直接进入文章生成的选题方向，避免营销承诺和疗效暗示" /></el-form-item>
          <el-form-item label="推荐侧重"><el-select v-model="form.recommendedFocus" clearable><el-option v-for="item in focuses" :key="item" :label="focusLabel(item)" :value="item" /></el-select></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        </template>

        <template v-else-if="activeTab === 'rules'">
          <el-form-item label="规则类型"><el-input v-model="form.ruleType" placeholder="如 efficacy_claim、ranking_claim、patient_testimonial" /></el-form-item>
          <el-form-item label="行业"><MedicalIndustrySelect v-model="form.industryCode" clearable /></el-form-item>
          <el-form-item label="渠道档位"><MedicalTierSelect v-model="form.channelTier" clearable /></el-form-item>
          <el-form-item label="渠道组"><el-input v-model="form.channelGroupCode" placeholder="可留空表示通用；如 self_media、forum、official_site" /></el-form-item>
          <el-form-item label="渠道子类"><el-input v-model="form.channelSubCode" placeholder="可留空表示通用；如 wechat、douyin、xiaohongshu" /></el-form-item>
          <el-form-item label="匹配方式"><el-select v-model="form.matchMode"><el-option label="包含" value="contains" /><el-option label="正则" value="regex" /></el-select></el-form-item>
          <el-form-item label="严重级别"><el-select v-model="form.severity"><el-option label="阻断" value="block" /><el-option label="提醒" value="warn" /></el-select></el-form-item>
          <el-form-item label="规则内容"><el-input v-model="form.pattern" type="textarea" :rows="3" placeholder="contains 填词或短语；regex 仅用于第一、最、权威等语境依赖表达，避免误杀" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" placeholder="说明这条规则的业务依据、适用范围或排除场景" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        </template>

        <template v-else-if="activeTab === 'kernels'">
          <el-form-item label="行业"><MedicalIndustrySelect v-model="form.industryCode" /></el-form-item>
          <el-form-item label="渠道档位"><MedicalTierSelect v-model="form.channelTier" /></el-form-item>
          <el-form-item label="内核名称"><el-input v-model="form.kernelName" placeholder="如 医美科普合规内核 v1" /></el-form-item>
          <el-form-item label="版本号"><el-input-number v-model="form.versionNo" :min="1" /></el-form-item>
          <el-form-item label="品牌露出上限"><el-input-number v-model="form.brandExposureLimit" :min="0" /></el-form-item>
          <el-form-item label="发布前人工确认"><el-switch v-model="form.requireManualPublishReview" /></el-form-item>
          <el-form-item label="系统提示词"><el-input v-model="form.systemPrompt" type="textarea" :rows="8" placeholder="维护该行业×档位的硬性合规边界，如风险提示、禁用承诺、品牌露出限制、官网发布闸门" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        </template>

        <template v-else-if="activeTab === 'styles'">
          <el-form-item label="渠道组"><el-input v-model="form.channelGroupCode" placeholder="如 self_media、forum、official_site" /></el-form-item>
          <el-form-item label="渠道子类"><el-input v-model="form.channelSubCode" placeholder="可留空；如 wechat、douyin、xiaohongshu" /></el-form-item>
          <el-form-item label="渠道档位"><MedicalTierSelect v-model="form.channelTier" /></el-form-item>
          <el-form-item label="高风险渠道"><el-switch v-model="form.highRisk" /></el-form-item>
          <el-form-item label="文体提示词"><el-input v-model="form.stylePrompt" type="textarea" :rows="8" placeholder="维护该渠道的表达风格和限制，如小红书/抖音禁止体验种草、论坛避免患者证明口吻" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ruleTesterVisible" title="合规规则测试" width="760px">
      <el-form class="rule-test-form" :model="ruleTestForm" label-position="top">
        <div class="form-grid">
          <el-form-item label="行业"><MedicalIndustrySelect v-model="ruleTestForm.industryCode" /></el-form-item>
          <el-form-item label="渠道档位"><MedicalTierSelect v-model="ruleTestForm.channelTier" /></el-form-item>
          <el-form-item label="渠道组"><el-input v-model="ruleTestForm.channelGroupCode" placeholder="可留空" /></el-form-item>
          <el-form-item label="渠道子类"><el-input v-model="ruleTestForm.channelSubCode" placeholder="可留空" /></el-form-item>
          <el-form-item label="品牌名"><el-input v-model="ruleTestForm.brandName" placeholder="用于品牌露出测试" /></el-form-item>
          <el-form-item label="品牌露出上限"><el-input-number v-model="ruleTestForm.brandExposureLimit" :min="0" style="width: 100%" /></el-form-item>
        </div>
        <el-form-item label="高风险渠道"><el-switch v-model="ruleTestForm.highRiskChannel" /></el-form-item>
        <el-form-item label="标题"><el-input v-model="ruleTestForm.title" maxlength="200" /></el-form-item>
        <el-form-item label="测试正文">
          <el-input v-model="ruleTestForm.content" type="textarea" :rows="7" placeholder="请包含风险/个体差异提示，避免基础风险提示规则干扰规则测试" />
        </el-form-item>
      </el-form>
      <div v-if="ruleTestResult" class="rule-test-result">
        <el-alert
          :type="ruleTestResult.passed ? 'success' : 'error'"
          :closable="false"
          show-icon
          :title="ruleTestResult.passed ? '测试通过：未命中阻断规则' : '测试未通过：命中合规规则'"
        />
        <el-table v-if="ruleTestResult.issues.length" :data="ruleTestResult.issues" border>
          <el-table-column prop="ruleType" label="规则类型" min-width="140" />
          <el-table-column prop="severity" label="级别" width="100" />
          <el-table-column prop="matchedText" label="命中内容" min-width="180" show-overflow-tooltip />
          <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="ruleTesterVisible = false">关闭</el-button>
        <el-button type="primary" :loading="ruleTesting" @click="submitRuleTest">开始测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElOption, ElSelect } from 'element-plus'
import {
  createSpecialIndustryChannelStyleModule,
  createSpecialIndustryComplianceKernel,
  createSpecialIndustryProfile,
  createSpecialIndustryComplianceRule,
  createSpecialIndustryTopicAngle,
  getSpecialIndustryChannelStyleModules,
  getSpecialIndustryComplianceHitLogs,
  getSpecialIndustryComplianceKernels,
  getSpecialIndustryProfiles,
  getSpecialIndustryProfileOptions,
  getSpecialIndustryComplianceRules,
  getSpecialIndustryTopicAngles,
  testSpecialIndustryRules,
  updateSpecialIndustryChannelStyleModule,
  updateSpecialIndustryProfile,
  updateSpecialIndustryComplianceRule,
  updateSpecialIndustryTopicAngle,
  type SpecialIndustryProfile,
  type SpecialIndustryRuleTestResult,
} from '@/api/content'
import { useDictStore } from '@/stores/dict'

type TabName = 'industries' | 'angles' | 'rules' | 'kernels' | 'styles' | 'logs'
type ColumnConfig = {
  prop: string
  label: string
  width?: number
  tag?: boolean
}

const activeTab = ref<TabName>('industries')
const dictStore = useDictStore()
const records = ref<any[]>([])
const industryOptions = ref<SpecialIndustryProfile[]>([])
const loading = ref(false)
const saving = ref(false)
const ruleTesting = ref(false)
const editorVisible = ref(false)
const ruleTesterVisible = ref(false)
const editingId = ref<number | null>(null)
const ruleTestResult = ref<SpecialIndustryRuleTestResult | null>(null)
const pagination = reactive({ current: 1, size: 20, total: 0 })
const focuses = ['principle', 'misconception', 'risk', 'rational_decision']
const form = reactive<Record<string, any>>({})
const ruleTestForm = reactive({
  industryCode: 'medical_beauty',
  channelTier: 'education',
  channelGroupCode: '',
  channelSubCode: '',
  brandName: '',
  brandExposureLimit: 2,
  highRiskChannel: false,
  title: '',
  content: '第一步要做基础评估，需结合个体差异和风险判断。',
})

const guideMap: Record<TabName, { title: string, description: string, tips: string[] }> = {
  industries: {
    title: '行业管理用于控制“哪些行业进入特殊链路”',
    description: '这里是特殊行业的统一入口。新增金融、教育、法律等行业后，品牌行业识别、选题角度、合规规则和生成链路都会按该配置联动。',
    tips: ['行业编码会同步到旧行业字典，保证历史字段和旧页面继续兼容。', '识别关键词用于从品牌行业文本中自动判断特殊行业，建议覆盖客户常用叫法。', '监管域决定默认校验口径：医疗域保留医疗许可校验，其他行业走通用资质说明。'],
  },
  angles: {
    title: '选题角度用于控制“能写什么”',
    description: '生成医疗/医美文章前会先按客户项目资质筛选可用选题。这里维护的是合规、克制、可复用的选题方向，不是营销标题库。',
    tips: ['行业和品类必须与品牌项目资质匹配，否则生成链路不会选用。', '选题角度应强调科普、风险、误区和理性决策，避免承诺效果。', '推荐侧重会影响差异化变量和文章结构倾向。'],
  },
  rules: {
    title: '合规规则用于控制“不能出现什么”',
    description: '文章生成后、入库前会执行规则校验。阻断级规则命中后会触发重试，连续失败会落废弃记录。',
    tips: ['行业、档位、渠道留空表示通用规则；填写后只在对应范围生效。', '绝对违禁词优先用“包含”；第一、最、权威等语境词用“正则”降低误杀。', '新增规则后建议先用“规则测试”验证命中和放行场景。'],
  },
  kernels: {
    title: '合规内核用于控制“系统提示词总边界”',
    description: '内核按行业和渠道档位生效，会在模板提示词之前拼入，约束品牌露出、风险提示、官网发布确认等硬规则。',
    tips: ['同一行业和档位通常只启用一个主版本。', '品牌露出上限用于约束正文中品牌/机构名称出现次数。', '官网档建议开启发布前人工确认，避免无审查编号自动发布。'],
  },
  styles: {
    title: '渠道模块用于控制“在哪里怎么写”',
    description: '渠道模块补充公众号、知乎、抖音、小红书、论坛等渠道的文体要求和额外限制。',
    tips: ['渠道组决定大类，如 self_media、forum、official_site。', '渠道子类用于精确到 wechat、douyin、xiaohongshu 等平台。', '高风险渠道会触发更严格的理性提示和医美表达限制。'],
  },
  logs: {
    title: '命中日志用于追溯“为什么被拦”',
    description: '每次生成命中合规规则都会记录命中文本、规则类型、处理动作和任务信息，便于运营复盘规则是否过严或模板是否需要调整。',
    tips: ['action=retry 表示系统尝试重生成；discard 表示三次失败后废弃。', '可结合特殊行业工作台按文章、批次、任务继续定位。', '日志只读，不建议手工修改。'],
  },
}
const currentGuide = computed(() => guideMap[activeTab.value])
const specialIndustryOptions = computed(() =>
  industryOptions.value.length
    ? industryOptions.value.map((item) => ({ dictKey: item.industryCode, dictValue: item.industryName }))
    : dictStore.options('compliance_industry').filter((item) => item.dictKey && item.dictKey !== 'none'),
)

const tierLabels: Record<string, string> = {
  education: '科普',
  source_site: '信源站',
  official_site: '官网',
}

const channelGroupLabels: Record<string, string> = {
  self_media: '自媒体',
  forum: '平台网站/论坛',
  official_site: '官网',
  agent_site: '品牌官网',
  industry_site: '行业站',
  authority_media: '权威媒体',
}

const channelSubLabels: Record<string, string> = {
  wechat: '微信公众号',
  zhihu: '知乎',
  baijiahao: '百家号',
  toutiao: '今日头条',
  netease: '网易号',
  sohu: '搜狐号',
  douyin: '抖音图文',
  xiaohongshu: '小红书',
}

const focusLabels: Record<string, string> = {
  principle: '原理科普',
  misconception: '误区澄清',
  risk: '风险提示',
  rational_decision: '理性决策',
}

const ruleTypeLabels: Record<string, string> = {
  efficacy_claim: '疗效承诺',
  safety_absolute: '绝对安全',
  patient_testimonial: '患者证明',
  urgency_promotion: '促单诱导',
  anxiety_inducement: '容貌焦虑',
  before_after: '前后对比',
  experience_seeding: '体验种草',
  ranking_claim: '排名/权威绝对化',
  brand_exposure: '品牌露出超限',
  risk_disclosure_missing: '缺少风险提示',
  rational_hint_missing: '缺少理性提示',
  oral_absolute: '口腔绝对化',
}

const severityLabels: Record<string, string> = {
  block: '阻断',
  warn: '提醒',
}

const actionLabels: Record<string, string> = {
  retry: '重试',
  discard: '废弃',
  block: '阻断',
  rejected: '驳回',
}

const domainLabels: Record<string, string> = {
  medical: '医疗',
  finance: '金融',
  education: '教育',
  legal: '法律',
  custom: '通用',
}

const columns = computed<ColumnConfig[]>(() => {
  if (activeTab.value === 'industries') return [
    { prop: 'industryCode', label: '行业编码', tag: true },
    { prop: 'industryName', label: '行业名称' },
    { prop: 'regulatoryDomain', label: '监管域', tag: true },
    { prop: 'keywords', label: '识别关键词', width: 220 },
    { prop: 'sortOrder', label: '排序' },
    { prop: 'enabled', label: '启用', tag: true },
  ]
  if (activeTab.value === 'angles') return [
    { prop: 'industryCode', label: '行业', tag: true },
    { prop: 'categoryName', label: '品类' },
    { prop: 'topicAngle', label: '选题角度', width: 260 },
    { prop: 'recommendedFocus', label: '推荐侧重', tag: true },
    { prop: 'enabled', label: '启用', tag: true },
  ]
  if (activeTab.value === 'rules') return [
    { prop: 'ruleType', label: '规则类型', tag: true },
    { prop: 'industryCode', label: '行业', tag: true },
    { prop: 'channelTier', label: '档位', tag: true },
    { prop: 'pattern', label: '规则内容', width: 260 },
    { prop: 'severity', label: '级别', tag: true },
    { prop: 'enabled', label: '启用', tag: true },
  ]
  if (activeTab.value === 'kernels') return [
    { prop: 'industryCode', label: '行业', tag: true },
    { prop: 'channelTier', label: '档位', tag: true },
    { prop: 'kernelName', label: '名称' },
    { prop: 'versionNo', label: '版本' },
    { prop: 'brandExposureLimit', label: '品牌上限' },
    { prop: 'enabled', label: '启用', tag: true },
  ]
  if (activeTab.value === 'styles') return [
    { prop: 'channelGroupCode', label: '渠道组', tag: true },
    { prop: 'channelSubCode', label: '子类', tag: true },
    { prop: 'channelTier', label: '档位', tag: true },
    { prop: 'highRisk', label: '高风险', tag: true },
    { prop: 'enabled', label: '启用', tag: true },
  ]
  return [
    { prop: 'createdAt', label: '时间', width: 170 },
    { prop: 'projectId', label: '项目' },
    { prop: 'taskId', label: '任务' },
    { prop: 'ruleType', label: '规则类型', tag: true },
    { prop: 'matchedText', label: '命中文本', width: 260 },
    { prop: 'action', label: '动作', tag: true },
  ]
})

const MedicalIndustrySelect = defineComponent({
  props: { modelValue: String, clearable: Boolean },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () => h(ElSelect, {
      modelValue: props.modelValue,
      clearable: props.clearable,
      style: 'width: 100%',
      'onUpdate:modelValue': (value: string) => emit('update:modelValue', value),
    }, () => specialIndustryOptions.value.map((item) =>
      h(ElOption, { label: item.dictValue || item.dictKey, value: item.dictKey }),
    ))
  },
})

const MedicalTierSelect = defineComponent({
  props: { modelValue: String, clearable: Boolean },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () => h(ElSelect, {
      modelValue: props.modelValue,
      clearable: props.clearable,
      style: 'width: 100%',
      'onUpdate:modelValue': (value: string) => emit('update:modelValue', value),
    }, () => [
      h(ElOption, { label: '科普', value: 'education' }),
      h(ElOption, { label: '信源站', value: 'source_site' }),
      h(ElOption, { label: '官网', value: 'official_site' }),
    ])
  },
})

function industryLabel(value?: string | null) {
  if (!value) return '通用'
  return dictStore.label('compliance_industry', value)
}

function tierLabel(value?: string | null) {
  if (!value) return '通用'
  return tierLabels[value] || value
}

function channelGroupLabel(value?: string | null) {
  if (!value) return '通用'
  return channelGroupLabels[value] || value
}

function channelSubLabel(value?: string | null) {
  if (!value) return '通用'
  return channelSubLabels[value] || value
}

function focusLabel(value?: string | null) {
  if (!value) return '-'
  return focusLabels[value] || value
}

function ruleTypeLabel(value?: string | null) {
  if (!value) return '通用'
  return ruleTypeLabels[value] || value
}

function severityLabel(value?: string | null) {
  if (!value) return '-'
  return severityLabels[value] || value
}

function actionLabel(value?: string | null) {
  if (!value) return '-'
  return actionLabels[value] || value
}

function domainLabel(value?: string | null) {
  if (!value) return '通用'
  return domainLabels[value] || value
}

function booleanLabel(value: unknown) {
  return value === true || value === 1 || value === '1' || value === 'true' ? '是' : '否'
}

function formatColumnValue(row: Record<string, any>, column: ColumnConfig) {
  const value = row[column.prop]
  if (value === null || value === undefined || value === '') {
    if (column.prop === 'industryCode' || column.prop === 'channelTier' || column.prop === 'channelGroupCode' || column.prop === 'channelSubCode') {
      return '通用'
    }
    return '-'
  }
  if (column.prop === 'industryCode') return industryLabel(value)
  if (column.prop === 'channelTier') return tierLabel(value)
  if (column.prop === 'channelGroupCode') return channelGroupLabel(value)
  if (column.prop === 'channelSubCode') return channelSubLabel(value)
  if (column.prop === 'recommendedFocus') return focusLabel(value)
  if (column.prop === 'regulatoryDomain') return domainLabel(value)
  if (column.prop === 'ruleType') return ruleTypeLabel(value)
  if (column.prop === 'severity') return severityLabel(value)
  if (column.prop === 'action') return actionLabel(value)
  if (column.prop === 'enabled' || column.prop === 'highRisk') return booleanLabel(value)
  return String(value)
}

function columnTagType(prop: string, value: unknown): 'success' | 'warning' | 'danger' | 'info' {
  if (prop === 'enabled') return value === true || value === 1 || value === '1' || value === 'true' ? 'success' : 'info'
  if (prop === 'highRisk') return value === true || value === 1 || value === '1' || value === 'true' ? 'danger' : 'info'
  if (prop === 'regulatoryDomain') return value === 'medical' ? 'warning' : 'info'
  if (prop === 'severity') return value === 'block' ? 'danger' : 'warning'
  if (prop === 'action') return value === 'discard' || value === 'block' ? 'danger' : 'warning'
  return 'info'
}

function resetForm() {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, {
    enabled: true,
    sortOrder: 100,
    regulatoryDomain: 'custom',
    matchMode: 'contains',
    severity: 'block',
    versionNo: 1,
    requireManualPublishReview: false,
    highRisk: false,
  })
}

async function loadCurrent() {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    const api = {
      industries: getSpecialIndustryProfiles,
      angles: getSpecialIndustryTopicAngles,
      rules: getSpecialIndustryComplianceRules,
      kernels: getSpecialIndustryComplianceKernels,
      styles: getSpecialIndustryChannelStyleModules,
      logs: getSpecialIndustryComplianceHitLogs,
    }[activeTab.value]
    const { data } = await api(params)
    records.value = data.data.records || []
    pagination.total = data.data.total || 0
    if (activeTab.value === 'industries') {
      await loadIndustryOptions()
    }
  } finally {
    loading.value = false
  }
}

function openCreate() {
  if (activeTab.value === 'logs') return
  editingId.value = null
  resetForm()
  editorVisible.value = true
}

function openEdit(row: any) {
  editingId.value = row.id
  resetForm()
  Object.assign(form, row)
  editorVisible.value = true
}

function openRuleTester() {
  ruleTestResult.value = null
  ruleTesterVisible.value = true
}

async function loadIndustryOptions() {
  const { data } = await getSpecialIndustryProfileOptions({ enabled: true })
  industryOptions.value = data.data || []
}

async function submit() {
  saving.value = true
  try {
    if (activeTab.value === 'industries') {
      const payload = industryProfilePayload()
      editingId.value ? await updateSpecialIndustryProfile(editingId.value, payload) : await createSpecialIndustryProfile(payload)
      await dictStore.ensureLoaded()
      await loadIndustryOptions()
    } else if (activeTab.value === 'angles') {
      const payload = topicAnglePayload()
      editingId.value ? await updateSpecialIndustryTopicAngle(editingId.value, payload) : await createSpecialIndustryTopicAngle(payload)
    } else if (activeTab.value === 'rules') {
      editingId.value ? await updateSpecialIndustryComplianceRule(editingId.value, form) : await createSpecialIndustryComplianceRule(form)
    } else if (activeTab.value === 'kernels') {
      await createSpecialIndustryComplianceKernel(form)
    } else if (activeTab.value === 'styles') {
      editingId.value ? await updateSpecialIndustryChannelStyleModule(editingId.value, form) : await createSpecialIndustryChannelStyleModule(form)
    }
    ElMessage.success('配置已保存')
    editorVisible.value = false
    await loadCurrent()
  } finally {
    saving.value = false
  }
}

function industryProfilePayload() {
  return {
    ...form,
    industryCode: String(form.industryCode || '').trim(),
    industryName: String(form.industryName || '').trim(),
    keywords: nullableText(form.keywords),
    remark: nullableText(form.remark),
  }
}

function topicAnglePayload() {
  return {
    ...form,
    industryName: industryLabel(form.industryCode),
  }
}

function nullableText(value: unknown) {
  const text = typeof value === 'string' ? value.trim() : ''
  return text || null
}

async function submitRuleTest() {
  if (!ruleTestForm.content.trim()) {
    ElMessage.warning('请先填写测试正文')
    return
  }
  ruleTesting.value = true
  try {
    const { data } = await testSpecialIndustryRules({
      ...ruleTestForm,
      channelGroupCode: ruleTestForm.channelGroupCode.trim() || null,
      channelSubCode: ruleTestForm.channelSubCode.trim() || null,
      brandName: ruleTestForm.brandName.trim() || null,
      title: ruleTestForm.title.trim() || null,
    })
    ruleTestResult.value = data.data
  } finally {
    ruleTesting.value = false
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await loadIndustryOptions()
  await loadCurrent()
})
</script>

<style scoped>
.special-industry-config-page {
  padding: 18px;
}

.admin-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.admin-page-header h2 {
  margin: 0 0 6px;
  font-size: 20px;
}

.admin-page-header p {
  margin: 0;
  color: #667085;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tab-guide {
  display: grid;
  grid-template-columns: minmax(260px, 0.9fr) minmax(320px, 1.2fr);
  gap: 18px;
  margin: 12px 0 14px;
  padding: 14px 16px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.tab-guide strong {
  display: block;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.tab-guide p {
  margin: 6px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.65;
}

.tab-guide ul {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 18px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.table-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.special-industry-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
}

.special-industry-form :deep(.el-form-item:has(textarea)) {
  grid-column: 1 / -1;
}

.rule-test-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.rule-test-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}

@media (max-width: 760px) {
  .special-industry-form,
  .form-grid,
  .tab-guide {
    grid-template-columns: 1fr;
  }
}
</style>
