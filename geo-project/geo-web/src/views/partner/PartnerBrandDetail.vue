<template>
  <div class="partner-page partner-brand-detail">
    <div class="partner-page-header">
      <div>
        <div class="partner-page-kicker">品牌资料</div>
        <h1 class="partner-page-title">{{ brand?.brandName || '品牌详情' }}</h1>
        <div class="partner-page-subtitle">维护品牌基础资料、产品服务与文章可用图片素材；总部隐藏交付配置不在合伙人侧展示。</div>
      </div>
      <div class="partner-page-actions">
        <el-button @click="router.back()">返回</el-button>
        <el-button v-if="isPartnerStaff" type="primary" @click="goCreateProject">基于该品牌建项目</el-button>
      </div>
    </div>

    <el-skeleton v-if="loading" :rows="10" animated />
    <template v-else>
      <section class="brand-hero">
        <div class="brand-avatar">{{ brandInitial }}</div>
        <div class="brand-hero-main">
          <div class="brand-eyebrow">{{ companyLabel }}</div>
          <h2>{{ brand?.brandName || '-' }}</h2>
          <p>{{ brand?.brandPositioning || brand?.mainBusiness || '暂未填写品牌定位或主营业务' }}</p>
        </div>
        <div class="brand-hero-meta">
          <span class="partner-status-tag" :class="brandStatusClass">{{ statusLabel }}</span>
          <small>{{ regionText }}</small>
        </div>
      </section>

      <div class="brand-metric-grid">
        <div class="brand-metric-card">
          <span>产品信息</span>
          <strong>{{ offerings.length }}</strong>
          <small>可用于项目与内容生成</small>
        </div>
        <div class="brand-metric-card">
          <span>图片素材</span>
          <strong>{{ imageMaterialCount }}</strong>
          <small>封面、插图等文章来源</small>
        </div>
        <div class="brand-metric-card">
          <span>图库文件夹</span>
          <strong>{{ imageFolders.length }}</strong>
          <small>默认包含插图与封面</small>
        </div>
        <div v-if="isSpecialIndustryBrand" class="brand-metric-card is-warning">
          <span>行业类型</span>
          <strong>特殊</strong>
          <small>{{ complianceIndustryLabel(brand?.complianceIndustryCode) }}</small>
        </div>
      </div>

      <section class="partner-surface brand-section core-section">
        <SectionHead title="基础信息" subtitle="用于客户、品牌和项目归属识别。" />
        <div class="info-grid">
          <InfoTile v-for="item in coreItems" :key="item.label" :label="item.label" :value="item.value" />
        </div>
      </section>

      <section class="partner-surface brand-section contact-section">
        <SectionHead title="联系方式" subtitle="展示可见、可公开或可用于跟进的信息。" />
        <div class="info-grid">
          <InfoTile v-for="item in contactItems" :key="item.label" :label="item.label" :value="item.value" />
        </div>
      </section>

      <section v-if="isSpecialIndustryBrand" class="partner-surface brand-section special-section">
        <SectionHead title="特殊行业资料" subtitle="特殊行业品牌需要补充真实资质、项目范围和合规说明，供总部审核与内容生成闸门使用。" />
        <div class="info-grid">
          <InfoTile v-for="item in specialIndustryItems" :key="item.label" :label="item.label" :value="item.value" />
        </div>
      </section>

      <section class="partner-surface brand-section intro-section">
        <SectionHead title="介绍素材" subtitle="用于后续诊断报告、项目资料和核心问题准备。" />
        <div class="info-grid is-text">
          <InfoTile v-for="item in textItems" :key="item.label" :label="item.label" :value="item.value" wide />
        </div>
      </section>

      <section class="partner-surface brand-section product-section">
        <SectionHead title="产品信息" :subtitle="productSectionSubtitle">
          <el-button v-if="canManageBrand" type="primary" @click="openOfferingCreate">新增产品</el-button>
        </SectionHead>
        <DataState :loading="offeringLoading" :empty="false">
          <div v-if="!offeringLoading && offerings.length === 0" class="offering-empty-action" :class="{ 'is-required': isSpecialIndustryBrand }">
            <div>
              <strong>{{ isSpecialIndustryBrand ? '特殊行业品牌需至少添加一个产品信息' : '暂无产品信息' }}</strong>
              <p>
                {{
                  isSpecialIndustryBrand
                    ? '特殊行业产品会用于合规审核、项目建档和内容生成，请录入可公开使用且可核验的产品、服务或诊疗项目。'
                    : '可按需录入品牌下公开使用的产品、服务项目或特色业务项，便于后续项目资料沉淀。'
                }}
              </p>
            </div>
            <el-button v-if="canManageBrand" type="primary" @click="openOfferingCreate">添加产品信息</el-button>
          </div>
          <div v-else class="offering-grid">
            <article v-for="item in offerings" :key="item.id" class="offering-card">
              <div class="offering-card-head">
                <div>
                  <h3>{{ item.offeringName }}</h3>
                  <p>{{ offeringAliasesText(item) }}</p>
                </div>
                <span class="partner-status-tag" :class="item.status === 'active' ? 'is-success' : 'is-muted'">
                  {{ item.status === 'active' ? '启用' : '停用' }}
                </span>
              </div>
              <div class="offering-fields">
                <InfoLine label="目标人群" :value="item.targetUsers || '-'" />
                <InfoLine label="适用场景" :value="item.useScenarios || '-'" />
                <InfoLine label="产品介绍" :value="item.offeringIntro || '-'" />
                <InfoLine label="资质描述" :value="item.qualificationDescription || '-'" />
                <InfoLine v-if="isSpecialIndustryBrand" label="特殊行业项目" :value="item.medicalProjectEnabled ? '已启用' : '未启用'" />
                <InfoLine v-if="isSpecialIndustryBrand" label="特殊行业品类" :value="item.medicalCategoryName || item.medicalCategoryCode || '-'" />
              </div>
              <div v-if="canManageBrand" class="offering-actions">
                <el-button link type="primary" @click="openOfferingEdit(item)">编辑</el-button>
                <el-button link type="danger" @click="removeOffering(item)">删除</el-button>
              </div>
            </article>
          </div>
        </DataState>
      </section>

      <section class="partner-surface brand-section asset-section">
        <SectionHead
          title="品牌图片资产"
          subtitle="文章正文插图优先使用“插图”开头的启用文件夹，封面优先使用名为“封面”的启用文件夹。请在这两类文件夹中补充可公开使用的图片。"
        >
          <div class="asset-actions">
            <el-button v-if="canManageBrand" @click="openFolderCreate">新建文件夹</el-button>
            <el-button v-if="canManageBrand" type="primary" :loading="uploading" @click="triggerImageUpload">上传图片</el-button>
          </div>
        </SectionHead>
        <input ref="imageInputRef" type="file" class="hidden-input" multiple accept="image/*,.svg" @change="handleImageSelected" />
        <DataState :loading="assetLoading" :empty="!assetLoading && imageFolders.length === 0" empty-text="暂无图库文件夹">
          <div class="asset-layout asset-workspace">
            <div class="asset-folder-board">
              <div class="asset-board-head">
                <div>
                  <strong>图库文件夹</strong>
                  <span>按图片用途归档，封面与插图是文章生成前的必要素材。</span>
                </div>
                <el-tag type="primary" round>{{ imageFolders.length }} 个文件夹</el-tag>
              </div>
              <div class="folder-list">
              <button
                v-for="folder in imageFolders"
                :key="folder.id"
                class="folder-card"
                :class="{ active: selectedFolderId === folder.id }"
                type="button"
                @click="selectFolder(folder.id)"
              >
                <div class="folder-card-head">
                  <span>{{ folder.folderName }}</span>
                  <el-tag size="small" :type="folder.status === 'active' ? 'success' : 'info'">
                    {{ folder.status === 'active' ? '启用' : '停用' }}
                  </el-tag>
                </div>
                <strong>{{ folderMaterialCount(folder.id) }} 张</strong>
                <div v-if="folderDisplayTags(folder).length" class="folder-tags">
                  <el-tag v-for="tag in folderDisplayTags(folder)" :key="tag" size="small" type="info">{{ tag }}</el-tag>
                </div>
                <div v-if="canManageBrand" class="folder-actions">
                  <el-button size="small" @click.stop="openFolderEdit(folder)">编辑</el-button>
                  <el-button size="small" type="danger" @click.stop="confirmDeleteFolder(folder)">删除</el-button>
                </div>
              </button>
              </div>
            </div>

            <div class="asset-panel">
              <div class="asset-panel-head">
                <div>
                  <strong>{{ selectedFolder?.folderName || '图库' }}</strong>
                  <span>共 {{ selectedFolderMaterials.length }} 张图片<span v-if="assetSearchKeyword">，筛选 {{ filteredSelectedFolderMaterials.length }} 张</span></span>
                </div>
                <div class="asset-panel-tools">
                  <el-input
                    v-model="assetSearchKeyword"
                    :prefix-icon="Search"
                    clearable
                    size="small"
                    placeholder="搜索文件名"
                  />
                  <el-button v-if="canManageBrand" size="small" type="primary" :loading="uploading" @click="triggerImageUpload">
                    上传到当前文件夹
                  </el-button>
                </div>
              </div>
              <div v-if="filteredSelectedFolderMaterials.length" class="material-grid">
                <div v-for="mat in filteredSelectedFolderMaterials" :key="mat.id" class="material-card">
                  <div class="material-thumb">
                    <img v-if="materialPreviewUrl(mat)" :src="materialPreviewUrl(mat)" :alt="mat.fileName" />
                    <span v-else>{{ fileTypeLabel(mat.fileType) }}</span>
                  </div>
                  <div class="material-actions">
                    <el-button size="small" type="primary" :loading="downloadingMaterialIds.includes(mat.id)" @click="downloadMaterial(mat)">
                      下载
                    </el-button>
                    <el-button v-if="canManageBrand" size="small" type="danger" :loading="deletingMaterialIds.includes(mat.id)" @click="confirmDeleteMaterial(mat)">
                      删除
                    </el-button>
                  </div>
                  <div class="material-info">
                    <strong :title="mat.fileName">{{ mat.fileName }}</strong>
                    <span>{{ formatFileSize(mat.fileSize) }}</span>
                  </div>
                </div>
              </div>
              <DataState v-else :loading="false" :empty="true" :empty-text="assetSearchKeyword ? '没有匹配的图片' : '当前文件夹暂无图片'" />
            </div>
          </div>
        </DataState>
      </section>
    </template>

    <el-dialog
      v-model="offeringVisible"
      :title="editingOffering ? '编辑产品信息' : '新增产品信息'"
      width="760px"
      class="partner-form-dialog"
    >
      <el-form ref="offeringFormRef" :model="offeringForm" :rules="offeringRules" label-position="top" class="offering-form">
        <div class="form-grid">
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
          <template v-if="isSpecialIndustryBrand">
            <el-form-item label="作为特殊行业项目">
              <el-switch v-model="offeringForm.medicalProjectEnabled" active-text="启用" inactive-text="关闭" />
            </el-form-item>
            <el-form-item label="特殊行业">
              <el-select v-model="offeringForm.medicalIndustryCode" clearable style="width: 100%" @change="() => loadSpecialIndustryCategories(true)">
                <el-option v-for="item in specialIndustryOptions" :key="item.dictKey" :label="item.dictValue" :value="item.dictKey" />
              </el-select>
            </el-form-item>
            <el-form-item label="特殊行业品类">
              <el-select
                v-model="offeringForm.medicalCategoryCode"
                clearable
                filterable
                :loading="specialCategoryLoading"
                :disabled="!offeringForm.medicalIndustryCode"
                placeholder="请选择品类"
                style="width: 100%"
                @change="syncSpecialCategoryName"
              >
                <el-option
                  v-for="item in specialCategoryOptions"
                  :key="`${item.industryCode}-${item.categoryCode}`"
                  :label="item.categoryName || item.categoryCode"
                  :value="item.categoryCode"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="资质引用" class="is-wide">
              <el-input v-model="offeringForm.qualificationRef" type="textarea" :rows="2" maxlength="300" show-word-limit placeholder="填写资质名称、编号或可核验来源，简要说明即可" />
            </el-form-item>
          </template>
          <el-form-item label="目标人群" class="is-wide">
            <el-input v-model="offeringForm.targetUsers" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="例如：周边家庭客群、年轻消费群体、企业采购负责人" />
          </el-form-item>
          <el-form-item label="适用场景" class="is-wide">
            <el-input v-model="offeringForm.useScenarios" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="例如：节假日活动、亲子消费、企业团购、日常便民服务" />
          </el-form-item>
          <el-form-item label="产品介绍" class="is-wide">
            <el-input v-model="offeringForm.offeringIntro" type="textarea" :rows="3" maxlength="400" show-word-limit placeholder="说明解决的问题、主要流程、核心卖点与差异化，控制在一段内。" />
          </el-form-item>
          <el-form-item label="产品资质描述" class="is-wide">
            <el-input v-model="offeringForm.qualificationDescription" type="textarea" :rows="3" maxlength="400" show-word-limit placeholder="填写可公开、可核验的资质、认证、标准、荣誉或服务能力证明。" />
          </el-form-item>
          <el-form-item label="备注" class="is-wide">
            <el-input v-model="offeringForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="仅填写必要补充说明" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="offeringVisible = false">取消</el-button>
        <el-button type="primary" :loading="offeringSaving" @click="submitOffering">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="folderDialogVisible"
      :title="editingFolderId ? '编辑图片文件夹' : '新建图片文件夹'"
      width="560px"
      class="partner-form-dialog"
    >
      <el-form :model="folderForm" label-position="top" class="folder-form">
        <el-form-item label="文件夹名称" required>
          <el-input v-model="folderForm.folderName" maxlength="128" show-word-limit placeholder="例如：插图、封面、门店环境、产品图" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="folderForm.status">
            <el-radio-button label="active">启用</el-radio-button>
            <el-radio-button label="disabled">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标签">
          <el-select
            v-model="folderForm.tags"
            multiple
            filterable
            allow-create
            default-first-option
            remote
            reserve-keyword
            :remote-method="searchFolderTags"
            placeholder="输入或选择标签，单个不超过10字"
            style="width: 100%"
          >
            <el-option v-for="tag in folderTagOptions" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="folderForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明该文件夹适用的图片场景" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="folderDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="folderSaving" @click="saveFolder">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createBrandImageFolder,
  createBrandOffering,
  deleteBrandImageFolder,
  deleteBrandMaterial,
  deleteBrandOffering,
  getCompanyDetail,
  getBrandDetail,
  getBrandImageFolders,
  getBrandMaterialPreviewUrl,
  getBrandMaterialStream,
  getBrandOfferings,
  suggestBrandImageFolderTags,
  updateBrandImageFolder,
  updateBrandOffering,
  uploadBrandMaterial,
} from '@/api/customer'
import { getSpecialIndustryTopicAngleCategories, type SpecialIndustryTopicAngleCategory } from '@/api/content'
import DataState from '@/components/ui/DataState.vue'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import type { Brand, BrandImageFolder, BrandMaterial, BrandOffering } from '@/types'
import { nullableText } from '@/utils/form'
import { errorMessage } from '@/utils/error'
import { specialIndustryCodesFromOptions } from '@/utils/specialIndustry'

defineOptions({ name: 'PartnerBrandDetail' })

const route = useRoute()
const router = useRouter()
const dictStore = useDictStore()
const userStore = useUserStore()

const loading = ref(false)
const assetLoading = ref(false)
const uploading = ref(false)
const offeringLoading = ref(false)
const offeringSaving = ref(false)
const folderSaving = ref(false)
const specialCategoryLoading = ref(false)
const brand = ref<Brand | null>(null)
const companyName = ref('')
const imageFolders = ref<BrandImageFolder[]>([])
const materialPreviewMap = ref<Record<number, string>>({})
const selectedFolderId = ref<number | null>(null)
const assetSearchKeyword = ref('')
const offerings = ref<BrandOffering[]>([])
const specialCategoryOptions = ref<SpecialIndustryTopicAngleCategory[]>([])
const folderTagOptions = ref<string[]>([])
const downloadingMaterialIds = ref<number[]>([])
const deletingMaterialIds = ref<number[]>([])
const imageInputRef = ref<HTMLInputElement>()
const offeringFormRef = ref<FormInstance>()
const offeringVisible = ref(false)
const articleFolderRuleMessage = '不能这样修改：文章自动生成需要至少一个启用且名称以“插图”开头的文件夹，并保留一个启用且名称为“封面”的文件夹。'
const editingOffering = ref<BrandOffering | null>(null)
const folderDialogVisible = ref(false)
const editingFolderId = ref<number | null>(null)

const isPartnerStaff = computed(() => userStore.role === 'partner_staff')
const canManageBrand = computed(() => isPartnerStaff.value && userStore.hasPermission('brand.update'))
const brandId = computed(() => Number(route.params.id) || 0)

const SectionHead = defineComponent({
  name: 'SectionHead',
  props: {
    title: { type: String, required: true },
    subtitle: { type: String, required: true },
  },
  setup(props, { slots }) {
    return () => h('div', { class: 'section-head' }, [
      h('div', [h('h2', props.title), h('p', props.subtitle)]),
      slots.default ? h('div', { class: 'section-actions' }, slots.default()) : null,
    ])
  },
})

const InfoTile = defineComponent({
  name: 'InfoTile',
  props: {
    label: { type: String, required: true },
    value: { type: String, default: '-' },
    wide: { type: Boolean, default: false },
  },
  setup(props) {
    return () => h('div', { class: ['info-tile', props.wide ? 'is-wide' : ''] }, [
      h('span', { class: 'info-label' }, props.label),
      h('strong', { class: 'info-value' }, props.value || '-'),
    ])
  },
})

const InfoLine = defineComponent({
  name: 'InfoLine',
  props: {
    label: { type: String, required: true },
    value: { type: String, default: '-' },
  },
  setup(props) {
    return () => h('div', { class: 'info-line' }, [
      h('span', props.label),
      h('strong', props.value || '-'),
    ])
  },
})

const offeringForm = reactive({
  offeringName: '',
  offeringAliases: '',
  targetUsers: '',
  offeringIntro: '',
  qualificationDescription: '',
  remark: '',
  status: 'active' as 'active' | 'disabled',
  useScenarios: '',
  medicalProjectEnabled: false,
  medicalIndustryCode: '',
  medicalCategoryCode: '',
  medicalCategoryName: '',
  qualificationRef: '',
})

const folderForm = reactive({
  folderName: '',
  status: 'active' as 'active' | 'disabled',
  description: '',
  tags: [] as string[],
})

const offeringRules: FormRules = {
  offeringName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

const specialIndustryCodes = computed(() => specialIndustryCodesFromOptions(dictStore.options('compliance_industry')))
const specialIndustryOptions = computed(() =>
  dictStore.options('compliance_industry').filter((item) => specialIndustryCodes.value.includes(item.dictKey)),
)
const isSpecialIndustryBrand = computed(() => specialIndustryCodes.value.includes(brand.value?.complianceIndustryCode || ''))
const selectedFolder = computed(() => imageFolders.value.find((folder) => folder.id === selectedFolderId.value) || imageFolders.value[0] || null)
const selectedFolderMaterials = computed(() => {
  const folder = selectedFolder.value
  if (!folder) return []
  return (folder.materials || []).filter((item) => item.category === 'brand_image' && isImageType(item.fileType))
})
const filteredSelectedFolderMaterials = computed(() => {
  const keyword = assetSearchKeyword.value.trim().toLowerCase()
  if (!keyword) return selectedFolderMaterials.value
  return selectedFolderMaterials.value.filter((item) => (item.fileName || '').toLowerCase().includes(keyword))
})
const imageMaterialCount = computed(() =>
  imageFolders.value.reduce((sum, folder) => sum + folderMaterialCount(folder.id), 0)
)

const brandInitial = computed(() => {
  const text = brand.value?.brandName || ''
  return text ? Array.from(text)[0] : '品'
})
const companyLabel = computed(() => (brand.value as any)?.companyName || companyName.value || '未关联客户')
const statusLabel = computed(() => dictStore.label('brand_status', brand.value?.status) || (brand.value?.status === 'active' ? '启用' : '-'))
const brandStatusClass = computed(() => (brand.value?.status === 'active' ? 'is-success' : 'is-muted'))
const productSectionSubtitle = computed(() =>
  isSpecialIndustryBrand.value
    ? '特殊行业品牌至少需要一个产品、服务或诊疗项目，用于总部合规审核与内容生成。'
    : '维护品牌下可公开使用的产品、服务项目或特色业务项。',
)
const regionText = computed(() => {
  const parts = [brand.value?.provinceName, brand.value?.cityName, brand.value?.districtName].filter(Boolean)
  return parts.length ? parts.join(' / ') : brand.value?.serviceArea || '未填写地区'
})

const coreItems = computed(() => [
  { label: '品牌名称', value: brand.value?.brandName || '-' },
  { label: '品牌简称', value: brand.value?.brandShortName || '-' },
  { label: '所属客户', value: companyLabel.value },
  { label: '品牌行业', value: industryLabel(brand.value?.industry) },
  { label: '主营业务方向', value: brand.value?.mainBusiness || '-' },
  { label: '核心产品', value: brand.value?.coreProducts || '-' },
  { label: '品牌定位', value: brand.value?.brandPositioning || '-' },
  { label: '所在地区', value: regionText.value },
])

const contactItems = computed(() => [
  { label: '官网', value: brand.value?.website || '-' },
  { label: '手机号', value: brand.value?.phone || '-' },
  { label: '微信', value: brand.value?.wechat || '-' },
  { label: '对外公开电话', value: brand.value?.publicPhone || '-' },
  { label: '对外公开地址', value: brand.value?.publicAddress || '-' },
])

const textItems = computed(() => [
  { label: '业务介绍', value: brand.value?.businessIntro || brand.value?.description || '-' },
  { label: '品牌资质描述', value: brand.value?.brandQualificationDescription || '-' },
  { label: '品牌案例描述', value: brand.value?.brandCaseDescription || '-' },
])

const specialIndustryItems = computed(() => [
  { label: '行业合规类型', value: complianceIndustryLabel(brand.value?.complianceIndustryCode) },
  { label: '主体资质/许可信息', value: brand.value?.medicalLicense || '-' },
  { label: '诊疗范围', value: brand.value?.diagnosisScope || '-' },
  { label: '机构类型', value: brand.value?.institutionType || '-' },
  { label: '公开执业信息', value: brand.value?.practitionerInfoPublic || '-' },
  { label: '审查/备案编号', value: brand.value?.medicalAdReviewNo || '-' },
  { label: '合规备注', value: brand.value?.complianceNotesMedical || '-' },
  { label: '禁用词', value: normalizeForbiddenPhrases(brand.value?.forbiddenPhrases) },
])

function industryLabel(value?: string | null) {
  return value ? dictStore.label('industry_tag', value) || value : '-'
}

function complianceIndustryLabel(value?: string | null) {
  if (!value || value === 'none') return '非特殊合规行业'
  return dictStore.label('compliance_industry', value) || value
}

function normalizeForbiddenPhrases(value?: string | string[] | null) {
  if (Array.isArray(value)) return value.join('、') || '-'
  if (!value) return '-'
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.join('、') || '-' : String(value)
  } catch {
    return String(value)
  }
}

function isImageType(fileType?: string | null) {
  if (!fileType) return false
  return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(fileType.toLowerCase())
}

function fileTypeLabel(fileType?: string | null) {
  return (fileType || '图片').toUpperCase()
}

function formatFileSize(bytes?: number | null) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function folderMaterialCount(folderId: number) {
  const folder = imageFolders.value.find((item) => item.id === folderId)
  return (folder?.materials || []).filter((material) => material.category === 'brand_image' && isImageType(material.fileType)).length
}

function selectFolder(folderId: number) {
  selectedFolderId.value = folderId
  assetSearchKeyword.value = ''
}

function defaultArticleFolderTags(folder: Pick<BrandImageFolder, 'folderName'>) {
  const folderName = (folder.folderName || '').trim()
  if (folderName.startsWith('插图')) return ['插图', '场景图']
  if (folderName === '封面') return ['封面', '首图']
  return []
}

function folderDisplayTags(folder: BrandImageFolder) {
  return Array.from(new Set([...defaultArticleFolderTags(folder), ...(folder.tags || [])].map((tag) => tag.trim()).filter(Boolean)))
}

function materialPreviewUrl(material: BrandMaterial) {
  return materialPreviewMap.value[material.id] || material.publicUrl || ''
}

function isActiveArticleIllustrationFolder(folder: Pick<BrandImageFolder, 'folderName' | 'status'>) {
  return folder.status === 'active' && (folder.folderName || '').trim().startsWith('插图')
}

function isActiveArticleCoverFolder(folder: Pick<BrandImageFolder, 'folderName' | 'status'>) {
  return folder.status === 'active' && (folder.folderName || '').trim() === '封面'
}

function isRequiredArticleFolder(folder: Pick<BrandImageFolder, 'folderName' | 'status'>) {
  return isActiveArticleIllustrationFolder(folder) || isActiveArticleCoverFolder(folder)
}

function hasRequiredArticleFolders(folders: Array<Pick<BrandImageFolder, 'id' | 'folderName' | 'status'>>) {
  return folders.some(isActiveArticleIllustrationFolder) && folders.some(isActiveArticleCoverFolder)
}

function assertRequiredArticleFolderAfterEdit(nextName: string, nextStatus: string) {
  const nextFolders = imageFolders.value.map((folder) => (
    folder.id === editingFolderId.value
      ? { ...folder, folderName: nextName, status: nextStatus }
      : folder
  ))
  if (!hasRequiredArticleFolders(nextFolders)) {
    ElMessage.warning(articleFolderRuleMessage)
    return false
  }
  return true
}

function assertRequiredArticleFolderAfterDelete(folderId: number) {
  const nextFolders = imageFolders.value.filter((folder) => folder.id !== folderId)
  if (!hasRequiredArticleFolders(nextFolders)) {
    ElMessage.warning(articleFolderRuleMessage)
    return false
  }
  return true
}

function offeringAliasesText(row: BrandOffering) {
  return row.offeringAliases?.filter(Boolean).join('、') || '暂无简称'
}

async function loadBrand() {
  if (!brandId.value) return
  loading.value = true
  companyName.value = ''
  try {
    const { data } = await getBrandDetail(brandId.value)
    brand.value = data.data
    await loadCompanyName()
  } catch {
    ElMessage.error('加载品牌详情失败')
  } finally {
    loading.value = false
  }
}

async function loadCompanyName() {
  const companyId = brand.value?.companyId
  if (!companyId || (brand.value as any)?.companyName) return
  try {
    const { data } = await getCompanyDetail(companyId)
    companyName.value = data.data?.companyName || ''
  } catch {
    companyName.value = ''
  }
}

async function loadOfferings() {
  if (!brandId.value) return
  offeringLoading.value = true
  try {
    const { data } = await getBrandOfferings(brandId.value)
    offerings.value = data.data || []
  } catch {
    offerings.value = []
  } finally {
    offeringLoading.value = false
  }
}

async function loadImageFolders() {
  if (!brandId.value) return
  assetLoading.value = true
  try {
    const { data } = await getBrandImageFolders(brandId.value, { activeOnly: true, includeMaterials: true })
    imageFolders.value = data.data || []
    if (!selectedFolderId.value || !imageFolders.value.some((item) => item.id === selectedFolderId.value)) {
      selectedFolderId.value = imageFolders.value.find((item) => (item.folderName || '').trim().startsWith('插图'))?.id || imageFolders.value[0]?.id || null
    }
    await loadMaterialPreviews()
  } catch {
    imageFolders.value = []
    selectedFolderId.value = null
  } finally {
    assetLoading.value = false
  }
}

async function loadMaterialPreviews() {
  const materials = imageFolders.value.flatMap((folder) => folder.materials || []).filter((item) => isImageType(item.fileType))
  const next = { ...materialPreviewMap.value }
  await Promise.all(materials.slice(0, 24).map(async (material) => {
    if (next[material.id]) return
    try {
      const { data } = await getBrandMaterialPreviewUrl(brandId.value, material.id)
      if (data.data?.url) {
        next[material.id] = data.data.url
      }
    } catch {
      // 缩略图失败不影响资料展示。
    }
  }))
  materialPreviewMap.value = next
}

function triggerImageUpload() {
  if (!selectedFolder.value) {
    ElMessage.warning('请先选择图片文件夹')
    return
  }
  imageInputRef.value?.click()
}

async function handleImageSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length || !selectedFolder.value) return
  uploading.value = true
  try {
    for (const file of files) {
      await uploadBrandMaterial(brandId.value, 'brand_image', file, selectedFolder.value.id)
    }
    ElMessage.success('图片已上传')
    await loadImageFolders()
  } catch {
    ElMessage.error('上传图片失败')
  } finally {
    uploading.value = false
  }
}

function setIdLoading(target: typeof downloadingMaterialIds, id: number, loading: boolean) {
  const ids = new Set(target.value)
  if (loading) {
    ids.add(id)
  } else {
    ids.delete(id)
  }
  target.value = Array.from(ids)
}

async function downloadMaterial(material: BrandMaterial) {
  setIdLoading(downloadingMaterialIds, material.id, true)
  try {
    const { data: blob } = await getBrandMaterialStream(brandId.value, material.id, true)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = material.fileName || 'brand-image'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.setTimeout(() => URL.revokeObjectURL(url), 5000)
  } catch (err: any) {
    ElMessage.error(errorMessage(err, '图片下载失败'))
  } finally {
    setIdLoading(downloadingMaterialIds, material.id, false)
  }
}

async function confirmDeleteMaterial(material: BrandMaterial) {
  try {
    await ElMessageBox.confirm(`确认删除图片「${material.fileName}」？删除后将无法在品牌图片资产中恢复。`, '删除图片', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  setIdLoading(deletingMaterialIds, material.id, true)
  try {
    await deleteBrandMaterial(brandId.value, material.id)
    const { [material.id]: _, ...next } = materialPreviewMap.value
    materialPreviewMap.value = next
    ElMessage.success('图片已删除')
    await loadImageFolders()
  } catch (err: any) {
    ElMessage.error(errorMessage(err, '图片删除失败'))
  } finally {
    setIdLoading(deletingMaterialIds, material.id, false)
  }
}

function resetFolderForm() {
  folderForm.folderName = ''
  folderForm.status = 'active'
  folderForm.description = ''
  folderForm.tags = []
}

function openFolderCreate() {
  editingFolderId.value = null
  resetFolderForm()
  folderDialogVisible.value = true
  void searchFolderTags('')
}

function openFolderEdit(folder: BrandImageFolder) {
  editingFolderId.value = folder.id
  folderForm.folderName = folder.folderName || ''
  folderForm.status = folder.status === 'disabled' ? 'disabled' : 'active'
  folderForm.description = folder.description || ''
  folderForm.tags = folderDisplayTags(folder)
  folderDialogVisible.value = true
  void searchFolderTags('')
}

async function searchFolderTags(keyword?: string) {
  if (!brandId.value) return
  try {
    const { data } = await suggestBrandImageFolderTags(brandId.value, keyword)
    folderTagOptions.value = Array.from(new Set([...(folderForm.tags || []), ...(data.data || [])]))
  } catch {
    folderTagOptions.value = [...(folderForm.tags || [])]
  }
}

async function saveFolder() {
  const folderName = folderForm.folderName.trim()
  if (!folderName) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  const tags = Array.from(new Set(folderForm.tags.map((tag) => tag.trim()).filter(Boolean)))
  if (tags.some((tag) => tag.length > 10)) {
    ElMessage.warning('单个标签不能超过10个字')
    return
  }
  if (editingFolderId.value && !assertRequiredArticleFolderAfterEdit(folderName, folderForm.status)) {
    return
  }
  folderSaving.value = true
  try {
    const payload = {
      folderName,
      status: folderForm.status,
      description: nullableText(folderForm.description) || undefined,
      tags,
      projectIds: [] as number[],
    }
    if (editingFolderId.value) {
      await updateBrandImageFolder(brandId.value, editingFolderId.value, payload)
    } else {
      await createBrandImageFolder(brandId.value, payload)
    }
    ElMessage.success('图片文件夹已保存')
    folderDialogVisible.value = false
    await loadImageFolders()
  } catch {
    ElMessage.error('保存图片文件夹失败')
  } finally {
    folderSaving.value = false
  }
}

async function confirmDeleteFolder(folder: BrandImageFolder) {
  if (!assertRequiredArticleFolderAfterDelete(folder.id)) return
  try {
    await ElMessageBox.confirm(
      `确认删除图片文件夹「${folder.folderName}」？文件夹内图片会保留，但会移出该文件夹。`,
      '删除文件夹',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  try {
    await deleteBrandImageFolder(brandId.value, folder.id)
    if (selectedFolderId.value === folder.id) {
      selectedFolderId.value = null
    }
    ElMessage.success('图片文件夹已删除')
    await loadImageFolders()
  } catch (err: any) {
    ElMessage.error(errorMessage(err, '删除图片文件夹失败'))
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
  offeringForm.useScenarios = ''
  offeringForm.medicalProjectEnabled = false
  offeringForm.medicalIndustryCode = isSpecialIndustryBrand.value ? brand.value?.complianceIndustryCode || '' : ''
  offeringForm.medicalCategoryCode = ''
  offeringForm.medicalCategoryName = ''
  offeringForm.qualificationRef = ''
}

function openOfferingCreate() {
  editingOffering.value = null
  resetOfferingForm()
  offeringVisible.value = true
  void loadSpecialIndustryCategories(false)
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
  offeringForm.useScenarios = offering.useScenarios || ''
  offeringForm.medicalProjectEnabled = !!offering.medicalProjectEnabled
  offeringForm.medicalIndustryCode = offering.medicalIndustryCode || (isSpecialIndustryBrand.value ? brand.value?.complianceIndustryCode || '' : '')
  offeringForm.medicalCategoryCode = offering.medicalCategoryCode || ''
  offeringForm.medicalCategoryName = offering.medicalCategoryName || ''
  offeringForm.qualificationRef = offering.qualificationRef || ''
  offeringVisible.value = true
  void loadSpecialIndustryCategories(false)
}

async function loadSpecialIndustryCategories(resetSelection = true) {
  const code = offeringForm.medicalIndustryCode
  if (resetSelection) {
    offeringForm.medicalCategoryCode = ''
    offeringForm.medicalCategoryName = ''
  }
  if (!code) {
    specialCategoryOptions.value = []
    return
  }
  specialCategoryLoading.value = true
  try {
    const { data } = await getSpecialIndustryTopicAngleCategories({ industryCode: code, enabled: true })
    specialCategoryOptions.value = data.data || []
  } catch {
    specialCategoryOptions.value = []
  } finally {
    specialCategoryLoading.value = false
  }
}

function syncSpecialCategoryName(value?: string) {
  const selected = specialCategoryOptions.value.find((item) => item.categoryCode === value)
  offeringForm.medicalCategoryName = selected?.categoryName || ''
}

async function submitOffering() {
  const valid = await offeringFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (isSpecialIndustryBrand.value && offeringForm.medicalProjectEnabled) {
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
      useScenarios: nullableText(offeringForm.useScenarios),
      medicalProjectEnabled: isSpecialIndustryBrand.value && offeringForm.medicalProjectEnabled,
      medicalIndustryCode: isSpecialIndustryBrand.value ? nullableText(offeringForm.medicalIndustryCode) : null,
      medicalCategoryCode: isSpecialIndustryBrand.value ? nullableText(offeringForm.medicalCategoryCode) : null,
      medicalCategoryName: isSpecialIndustryBrand.value ? nullableText(offeringForm.medicalCategoryName) : null,
      qualificationRef: isSpecialIndustryBrand.value ? nullableText(offeringForm.qualificationRef) : null,
    }
    if (editingOffering.value) {
      await updateBrandOffering(brandId.value, editingOffering.value.id, payload)
    } else {
      await createBrandOffering(brandId.value, payload)
    }
    ElMessage.success('产品信息已保存')
    offeringVisible.value = false
    await loadOfferings()
  } catch {
    ElMessage.error('保存产品信息失败')
  } finally {
    offeringSaving.value = false
  }
}

async function removeOffering(offering: BrandOffering) {
  try {
    await ElMessageBox.confirm(`确认删除产品信息「${offering.offeringName}」？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    await deleteBrandOffering(brandId.value, offering.id)
    ElMessage.success('产品信息已删除')
    await loadOfferings()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error('删除产品信息失败')
  }
}

function goCreateProject() {
  if (!brand.value) return
  router.push({
    path: '/partner/my-projects',
    query: {
      companyId: String(brand.value.companyId),
      brandId: String(brand.value.id),
      source: 'brand_detail',
    },
  })
}

async function reload() {
  await dictStore.ensureLoaded()
  await Promise.all([loadBrand(), loadOfferings(), loadImageFolders()])
}

onMounted(reload)
watch(() => route.params.id, reload)
</script>

<style scoped>
.partner-brand-detail {
  display: grid;
  gap: 14px;
}

.partner-brand-detail :deep(.partner-page-header) {
  overflow: hidden;
  position: relative;
  border-color: #dbeafe;
  background:
    radial-gradient(circle at 92% 12%, rgba(14, 165, 233, 0.16) 0, rgba(14, 165, 233, 0.16) 120px, transparent 122px),
    linear-gradient(135deg, #fff 0%, #f1f7ff 58%, #ecfdf5 100%);
}

.partner-brand-detail :deep(.partner-page-header)::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: linear-gradient(180deg, #2563eb 0%, #14b8a6 100%);
  content: '';
}

.brand-hero {
  display: flex;
  align-items: center;
  gap: 18px;
  overflow: hidden;
  position: relative;
  padding: 18px 20px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.08) 0%, rgba(20, 184, 166, 0.09) 100%),
    #fff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
}

.brand-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: linear-gradient(135deg, #2563eb 0%, #14b8a6 100%);
  color: #fff;
  font-size: 24px;
  font-weight: 900;
  flex: 0 0 auto;
}

.brand-hero-main {
  min-width: 0;
  flex: 1;
}

.brand-eyebrow {
  color: #2563eb;
  font-size: 13px;
  font-weight: 900;
  margin-bottom: 6px;
}

.brand-hero h2 {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 900;
}

.brand-hero p {
  margin: 8px 0 0;
  max-width: 920px;
  color: #475569;
  line-height: 1.65;
  font-weight: 650;
}

.brand-hero-meta {
  display: flex;
  align-items: flex-end;
  flex-direction: column;
  gap: 8px;
  color: #64748b;
  font-weight: 700;
}

.brand-metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}

.brand-metric-card {
  position: relative;
  overflow: hidden;
  min-height: 92px;
  padding: 14px 16px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: linear-gradient(180deg, #fff 0%, #f8fbff 100%);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.045);
}

.brand-metric-card::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: #2563eb;
  content: '';
}

.brand-metric-card:nth-child(2)::before {
  background: #0f766e;
}

.brand-metric-card:nth-child(3)::before {
  background: #7c3aed;
}

.brand-metric-card.is-warning {
  border-color: #fde68a;
  background: linear-gradient(180deg, #fff 0%, #fffbeb 100%);
}

.brand-metric-card.is-warning::before {
  background: #f59e0b;
}

.brand-metric-card span,
.brand-metric-card small {
  display: block;
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.brand-metric-card strong {
  display: block;
  margin: 8px 0 4px;
  color: #0f172a;
  font-size: 23px;
  line-height: 1;
  font-weight: 900;
}

.brand-section {
  --section-accent: #2563eb;
  --section-soft: #f8fbff;
  --section-border: #dbeafe;
  padding: 18px;
  border-color: var(--section-border);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.045);
}

.core-section {
  --section-accent: #2563eb;
  --section-soft: #eff6ff;
}

.contact-section {
  --section-accent: #0f766e;
  --section-soft: #ecfdf5;
  --section-border: #b8eee5;
}

.intro-section {
  --section-accent: #7c3aed;
  --section-soft: #f5f3ff;
  --section-border: #ddd6fe;
}

.product-section {
  --section-accent: #f59e0b;
  --section-soft: #fffbeb;
  --section-border: #fde68a;
}

.asset-section {
  --section-accent: #0284c7;
  --section-soft: #eff6ff;
  --section-border: #bfdbfe;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.section-head h2 {
  position: relative;
  padding-left: 12px;
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 900;
}

.section-head h2::before {
  position: absolute;
  top: 2px;
  bottom: 2px;
  left: 0;
  width: 4px;
  border-radius: 999px;
  background: var(--section-accent);
  content: '';
}

.section-head p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  font-weight: 650;
}

.section-actions {
  flex: 0 0 auto;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.info-grid.is-text {
  grid-template-columns: 1fr;
}

:deep(.info-tile) {
  min-height: 78px;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: linear-gradient(180deg, #fff 0%, #f8fafc 100%);
}

:deep(.info-tile:hover) {
  border-color: var(--section-border);
  background: linear-gradient(180deg, #fff 0%, var(--section-soft) 100%);
}

:deep(.info-tile.is-wide) {
  min-height: 88px;
}

:deep(.info-label) {
  display: block;
  margin-bottom: 7px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.2;
  font-weight: 800;
}

:deep(.info-value) {
  display: block;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.75;
  font-weight: 800;
  white-space: pre-wrap;
  word-break: break-word;
}

.special-section {
  border-color: #fde68a;
  background: linear-gradient(180deg, #fff 0%, #fffbeb 100%);
}

.hidden-input {
  display: none;
}

.asset-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.asset-layout {
  display: grid;
  gap: 16px;
}

.asset-workspace {
  grid-template-columns: 1fr;
}

.asset-folder-board {
  padding: 16px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.05), rgba(16, 185, 129, 0.04)),
    #ffffff;
}

.asset-board-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.asset-board-head strong,
.asset-board-head span {
  display: block;
}

.asset-board-head strong {
  color: #0f172a;
  font-weight: 900;
}

.asset-board-head span {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
  font-weight: 700;
}

.folder-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  align-content: start;
  gap: 12px;
}

.folder-card {
  width: 100%;
  min-height: 124px;
  padding: 14px;
  border: 1px solid #dbe4f0;
  border-radius: 12px;
  background: linear-gradient(180deg, #fff 0%, #f8fafc 100%);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.folder-card:hover,
.folder-card.active {
  border-color: #2563eb;
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.12);
  transform: translateY(-1px);
}

.folder-card.active {
  background: linear-gradient(180deg, #ffffff 0%, #eff6ff 100%);
}

.folder-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.folder-card-head span,
.folder-card small {
  display: block;
}

.folder-card-head span {
  min-width: 0;
  color: #0f172a;
  font-weight: 900;
  word-break: break-word;
}

.folder-card strong {
  display: block;
  margin: 10px 0 4px;
  color: #2563eb;
  font-size: 22px;
  font-weight: 900;
}

.folder-card small {
  color: #64748b;
  line-height: 1.5;
  font-weight: 650;
}

.folder-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.folder-tags :deep(.el-tag) {
  border-color: #dbeafe;
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 800;
}

.folder-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.asset-panel {
  min-width: 0;
  min-height: 420px;
  padding: 16px;
  border: 1px solid #bfdbfe;
  border-radius: 14px;
  background: linear-gradient(180deg, #fff 0%, #f8fbff 100%);
}

.asset-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.asset-panel-head strong,
.asset-panel-head span {
  display: block;
}

.asset-panel-head strong {
  color: #0f172a;
  font-weight: 900;
}

.asset-panel-head span {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
  font-weight: 750;
}

.asset-panel-tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-width: 300px;
}

.asset-panel-tools :deep(.el-input) {
  width: 190px;
}

.material-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(148px, 1fr));
  gap: 12px;
  max-height: 620px;
  overflow: auto;
  padding-right: 2px;
}

.material-card {
  position: relative;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.035);
}

.material-thumb {
  aspect-ratio: 4 / 3;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef4ff;
  color: #64748b;
  font-weight: 900;
}

.material-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.material-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 6px;
  opacity: 0;
  transform: translateY(-4px);
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.material-card:hover .material-actions {
  opacity: 1;
  transform: translateY(0);
}

.material-info {
  padding: 10px;
}

.material-info strong,
.material-info span {
  display: block;
}

.material-info strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 13px;
  font-weight: 850;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-info span {
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.offering-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}

.offering-empty-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px;
  border: 1px dashed #bfdbfe;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fbff 0%, #eff6ff 100%);
}

.offering-empty-action.is-required {
  border-color: #fed7aa;
  background: linear-gradient(135deg, #fff7ed 0%, #eff6ff 100%);
}

.offering-empty-action strong {
  display: block;
  color: #0f172a;
  font-size: 16px;
  font-weight: 900;
}

.offering-empty-action p {
  margin: 8px 0 0;
  color: #64748b;
  line-height: 1.6;
  font-weight: 650;
}

.offering-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid #fde68a;
  border-radius: 12px;
  background: linear-gradient(180deg, #fff 0%, #fffbeb 100%);
}

.offering-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.offering-card h3 {
  margin: 0;
  color: #0f172a;
  font-size: 17px;
  font-weight: 900;
}

.offering-card p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.offering-fields {
  display: grid;
  gap: 8px;
}

:deep(.info-line) {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
}

:deep(.info-line span) {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

:deep(.info-line strong) {
  color: #0f172a;
  font-size: 13px;
  line-height: 1.6;
  font-weight: 750;
  white-space: pre-wrap;
  word-break: break-word;
}

.offering-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  border-top: 1px solid #eef2f7;
  padding-top: 10px;
}

.offering-form {
  display: grid;
  gap: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 18px;
}

.form-grid .is-wide {
  grid-column: 1 / -1;
}

.folder-form {
  display: grid;
  gap: 4px;
}

@media (max-width: 1200px) {
  .brand-metric-grid,
  .info-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .asset-layout,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .asset-panel-head,
  .asset-board-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .asset-panel-tools {
    width: 100%;
    min-width: 0;
    justify-content: flex-start;
  }

  .asset-panel-tools :deep(.el-input) {
    width: 100%;
  }

  .material-grid {
    max-height: none;
  }
}

@media (max-width: 760px) {
  .brand-hero,
  .section-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .brand-hero-meta {
    align-items: flex-start;
  }

  .brand-metric-grid,
  .info-grid {
    grid-template-columns: 1fr;
  }

  .offering-empty-action {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
