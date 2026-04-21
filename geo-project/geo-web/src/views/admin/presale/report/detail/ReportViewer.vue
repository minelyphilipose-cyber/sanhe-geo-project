<template>
  <main class="report-viewer ps-page-scope">
    <!-- 降级警示条(若 L1 benchmarks_frozen.match_level 非 EXACT) -->
    <el-alert
      v-if="isDone && matchLevel && matchLevel !== 'EXACT'"
      type="warning"
      :closable="false"
      show-icon
      class="viewer-alert"
    >
      <template #title>
        行业基线匹配级别为 {{ matchLevel }},部分对比数据使用回退基线。
      </template>
    </el-alert>

    <el-alert
      v-if="isDone && degradedPlatforms.length > 0"
      type="warning"
      :closable="false"
      show-icon
      class="viewer-alert"
    >
      <template #title>
        以下 {{ degradedPlatforms.length }} 个平台在本次生成中降级:{{ degradedPlatforms.join('、') }}
      </template>
    </el-alert>

    <!-- ═══ 18 页渲染 ═══ -->
    <!--
      β·1 阶段:Page01(封面)和 Page02(诊断对象)用真实 SFC,其余 16 页仍占位。
      β·2 / β·3 / γ 依次替换剩余页面。
      每页的外层结构约定:<section :id="page-XX"><div class="page [cover]">...</div></section>,
      Sidebar 锚点依赖此 id。
    -->
    <template v-if="isDone && mergedView">
      <!-- P01 封面(β·1 真实实现) -->
      <Page01Cover />

      <!-- P02 诊断对象(β·1 真实实现) -->
      <Page02Target />

      <!-- P03~P18 占位(β·2 / β·3 / γ 将逐步替换) -->
      <PagePlaceholder
        v-for="p in PLACEHOLDER_PAGES"
        :key="p.id"
        :anchor-id="p.id"
        :page-num="p.num"
        :page-title="p.title"
        :cover="p.cover"
      />
    </template>

    <!-- 非 DONE 理论上不会到这里(Detail 顶层已拦截),留兜底占位 -->
    <div v-else class="viewer-empty">
      <el-empty description="报告数据未就绪" />
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'
import PagePlaceholder from './PagePlaceholder.vue'
import Page01Cover from './Page01Cover.vue'
import Page02Target from './Page02Target.vue'

const { mergedView } = useMergedView()

const meta = computed(() => mergedView.value?.meta)
const isDone = computed(() => meta.value?.generation_status === 'DONE')
const matchLevel = computed(() => meta.value?.match_level)
const degradedPlatforms = computed(() => meta.value?.degraded_platforms ?? [])

/**
 * P03~P18 占位规格(与 DetailSidebar.PAGE_ANCHORS 的 P03~P18 一一对应)。
 * cover=true 对应原型里的 `.page.cover`(仅 P18 关于我们)。
 *
 * β·1:P01/P02 用真实 SFC,不在本数组里;数组只含仍需占位的 16 页。
 * β·2 将把 P03/P04/P05/P06 从本数组移除并替换为真实 SFC。
 * β·3 将把 P07/P08/P09 移除并替换。
 * γ 将把 P10~P18 移除并替换,此时数组清空,PagePlaceholder import 亦可删除。
 */
const PLACEHOLDER_PAGES = [
  { id: 'page-03', num: '03', title: '执行摘要(关键一页)', cover: false },
  { id: 'page-04', num: '04', title: '可见度评分详情', cover: false },
  { id: 'page-05', num: '05', title: '多平台热力图', cover: false },
  { id: 'page-06', num: '06', title: '平台详细数据', cover: false },
  { id: 'page-07', num: '07', title: '竞品对标总览', cover: false },
  { id: 'page-08', num: '08', title: '竞品场景差异', cover: false },
  { id: 'page-09', num: '09', title: '情感倾向', cover: false },
  { id: 'page-10', num: '10', title: '覆盖度总览', cover: false },
  { id: 'page-11', num: '11', title: '覆盖度详情', cover: false },
  { id: 'page-12', num: '12', title: '优化机会(高优先级)', cover: false },
  { id: 'page-13', num: '13', title: '优化机会(中优先级)', cover: false },
  { id: 'page-14', num: '14', title: '优化机会(建议关注)', cover: false },
  { id: 'page-15', num: '15', title: '预期收益', cover: false },
  { id: 'page-16', num: '16', title: '分阶段路径', cover: false },
  { id: 'page-17', num: '17', title: '关键发现总结', cover: false },
  { id: 'page-18', num: '18', title: '关于我们', cover: true }
] as const
</script>

<style scoped>
.report-viewer {
  padding: 24px;
  min-height: 100vh;
  /* 背景沿用原型的深灰纸外框 */
  background: #2d2a26;
}

.viewer-alert {
  max-width: 794px;
  margin: 0 auto 16px auto;
}

.viewer-empty {
  padding: 120px 0;
  color: #d4cfc2;
}
</style>
