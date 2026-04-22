<template>
  <div class="finding-card" :class="priorityClass" :style="cardStyle">
    <div class="finding-card-body">
      <div class="display-serif finding-card-num" :style="numStyle">
        {{ number }}
      </div>
      <div class="finding-card-content">
        <div class="chinese-serif finding-card-title" :style="titleStyle">
          {{ title }}
        </div>
        <div class="finding-card-desc" :style="descStyle">
          {{ description }}
        </div>
        <div
          v-if="evidenceText"
          class="mono finding-card-evidence"
          :style="evidenceStyle"
        >
          证据:{{ evidenceText }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * FindingCard — 优化发现卡片,P12/P13/P14 共用。
 *
 * 视觉差异:
 *   - HIGH:红色左边线 + 稍大尺寸(原型 P12 编号字号 32px,title 16px,padding 16)
 *   - MID: 橙色左边线 + 中等尺寸(原型 P13 编号 28px,title 15px,padding 14)
 *   - LOW: 灰色左边线 + 中等尺寸(原型 P14 与 MID 尺寸一致,颜色不同)
 *   - evidence:HIGH 显示红色证据行;MID/LOW 在原型中不显示证据行(视觉节制)
 *
 * 颜色对齐 report-theme.css:
 *   - HIGH: var(--presale-accent-red)  #b91c1c
 *   - MID:  var(--presale-accent)      #d97706
 *   - LOW:  var(--presale-muted)       #6b6456
 */

type Priority = 'HIGH' | 'MEDIUM' | 'LOW'

interface Props {
  /** 卡片序号,字符串(已 padStart '01')。 */
  number: string
  priority: Priority
  title: string
  description: string
  /** 仅 HIGH 优先级时展示;MID/LOW 建议传空串隐藏证据行。 */
  evidenceText?: string
}

const props = withDefaults(defineProps<Props>(), {
  evidenceText: ''
})

const priorityClass = computed(() => {
  switch (props.priority) {
    case 'HIGH':
      return 'finding-card-high'
    case 'MEDIUM':
      return 'finding-card-mid'
    case 'LOW':
      return 'finding-card-low'
  }
})

// 根据 priority 调整尺寸(对齐原型各页视觉节奏)
const isHigh = computed(() => props.priority === 'HIGH')

const cardStyle = computed(() => ({
  padding: isHigh.value ? '16px 20px' : '14px 20px',
  marginBottom: isHigh.value ? '12px' : '10px'
}))
const numStyle = computed(() => ({
  fontSize: isHigh.value ? '32px' : '28px',
  color:
    props.priority === 'HIGH'
      ? '#b91c1c'
      : props.priority === 'MEDIUM'
        ? '#d97706'
        : '#6b6456'
}))
const titleStyle = computed(() => ({
  fontSize: isHigh.value ? '16px' : '15px',
  marginBottom: isHigh.value ? '6px' : '4px'
}))
const descStyle = computed(() => ({
  fontSize: '12px',
  lineHeight: isHigh.value ? '1.7' : '1.6'
}))
const evidenceStyle = computed(() => ({
  fontSize: '10px',
  color:
    props.priority === 'HIGH'
      ? '#b91c1c'
      : props.priority === 'MEDIUM'
        ? '#d97706'
        : '#6b6456'
}))
</script>

<style scoped>
.finding-card {
  background: #f7f3ea;
}
.finding-card-high {
  border-left: 3px solid #b91c1c;
}
.finding-card-mid {
  border-left: 3px solid #d97706;
}
.finding-card-low {
  border-left: 3px solid #6b6456;
}

.finding-card-body {
  display: flex;
  gap: 16px;
}
.finding-card-num {
  font-weight: 900;
  line-height: 1;
  font-style: italic;
}
.finding-card-content {
  flex: 1;
}
.finding-card-title {
  font-weight: 600;
  color: #0b1426;
}
.finding-card-desc {
  color: #1a2942;
}
.finding-card-evidence {
  margin-top: 8px;
  letter-spacing: 1px;
}
</style>
