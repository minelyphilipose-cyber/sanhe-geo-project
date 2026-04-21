<template>
  <v-chart
    ref="chartRef"
    :option="option"
    :theme="theme"
    :autoresize="autoresize"
    class="presale-chart"
    :style="{ height: height, width: '100%' }"
  />
</template>

<script setup lang="ts">
/**
 * PresaleChart — presale 报告详情页 ECharts 统一包装。
 *
 * 职责:
 *   1. 用 echarts/core + 按需 use() 注册本项目需要的 chart 类型和 components,
 *      保持 tree-shake 效果(vue-echarts 官方推荐写法)
 *   2. 统一 autoresize 默认开,响应容器大小变化
 *   3. 统一高度写法(通过 height prop 控制容器高度)
 *   4. Page SFC 只需传 option,不关心 module 注册
 *
 * 注册的 echarts 模块:
 *   - renderers: CanvasRenderer
 *   - charts: RadarChart / BarChart / PieChart / LineChart(覆盖 β·2+β·3 全部 5 个图表)
 *   - components: Title / Tooltip / Legend / Grid / Polar / Radar / DataZoom
 *
 * 为什么一次注册全部而不是每个 Page 按用到的注册:
 *   - 项目一共 5 个图表,类型重合度高
 *   - 每个 Page 独立 use() 会导致相同 module 被 use 多次(无功能问题,只是冗余)
 *   - 集中在 shared 里注册一次,Page SFC 模板干净
 *
 * Bundle size:
 *   按需引入 5 个 chart 类型 + 若干 component,gzip 后约 150-200KB,
 *   比 `import 'echarts'` 全量(~400KB gzip)小一半以上。
 *
 * 使用:
 *   <PresaleChart :option="radarOption" height="340px" />
 */

import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import {
  RadarChart,
  BarChart,
  PieChart,
  LineChart
} from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  PolarComponent,
  RadarComponent,
  DataZoomComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts'
import { ref } from 'vue'

// 一次性注册所有 presale 图表会用到的模块
use([
  CanvasRenderer,
  RadarChart,
  BarChart,
  PieChart,
  LineChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  PolarComponent,
  RadarComponent,
  DataZoomComponent
])

interface Props {
  option: EChartsOption
  height?: string
  theme?: string
  autoresize?: boolean
}

withDefaults(defineProps<Props>(), {
  height: '320px',
  theme: '',
  autoresize: true
})

/**
 * 暴露底层 echarts 实例供 Page SFC 做高级操作(如 setOption 流式更新、
 * getDataURL 导出图片)。β·2 Page SFC 暂不用到,保留扩展点。
 */
const chartRef = ref<InstanceType<typeof VChart> | null>(null)

defineExpose({
  /** 获取底层 echarts 实例(可能为 null,未挂载完成)。 */
  getChart: () => chartRef.value
})
</script>

<style scoped>
.presale-chart {
  /* 宽度在父元素控制;vue-echarts 默认 100%,这里显式声明一次避免边界场景 */
  width: 100%;
}
</style>
