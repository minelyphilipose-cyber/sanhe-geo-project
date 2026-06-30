<template>
  <v-chart class="mobile-trend-chart" :option="chartOption" autoresize />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent])

const props = defineProps<{
  labels?: string[]
  values?: Array<number | null>
}>()

const chartData = computed(() => {
  const labels = props.labels || []
  const values = props.values || []
  return values.reduce<{ labels: string[]; values: number[] }>((acc, value, index) => {
    if (value === null || value === undefined) return acc
    acc.labels.push(labels[index] || '')
    acc.values.push(value)
    return acc
  }, { labels: [], values: [] })
})

const chartOption = computed(() => ({
  grid: { left: 6, right: 8, top: 10, bottom: 6, containLabel: false },
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    show: false,
    data: chartData.value.labels,
  },
  yAxis: {
    type: 'value',
    min: 0,
    max: 100,
    show: false,
  },
  series: [
    {
      type: 'line',
      data: chartData.value.values,
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { color: '#006D44', width: 3 },
      itemStyle: { color: '#006D44' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(7, 166, 107, 0.28)' },
            { offset: 1, color: 'rgba(7, 166, 107, 0)' },
          ],
        },
      },
    },
  ],
}))
</script>

<style scoped>
.mobile-trend-chart {
  width: 112px;
  height: 72px;
}
</style>
