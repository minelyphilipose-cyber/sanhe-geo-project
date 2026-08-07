<template>
  <section id="page-03" class="page-anchor">
    <div class="page" data-page-id="page-03" data-print-bottom-band="content-overflow-only">
      <div class="page-topbar p03-topbar">
        <div class="topbar-left">
          <span class="topbar-num">03</span>
          <span
            data-page-id="page-03"
            data-block="顶部条"
            data-field="market_battleground.topbar_title"
          >
            {{ market.topbar_title }}
          </span>
        </div>
        <span
          data-page-id="page-03"
          data-block="顶部条"
          data-field="market_battleground.topbar_right"
        >
          {{ market.topbar_right }}
        </span>
      </div>

      <div class="p03-content-stage">
        <div class="p03-title-block">
          <h1
            class="chinese-serif p03-title"
            data-page-id="page-03"
            data-block="页面标题"
            data-field="market_battleground.page_title"
          >
            {{ market.page_title }}
          </h1>
          <div
            class="mono p03-kicker"
            data-page-id="page-03"
            data-block="页面标题"
            data-field="market_battleground.page_kicker"
          >
            {{ market.page_kicker }}
          </div>
        </div>

        <div class="p03-dark-card">
          <div
            class="dark-card-label mono"
            data-page-id="page-03"
            data-block="深色市场卡"
            data-field="market_battleground.market_card.label"
          >
            {{ market.market_card.label }}
          </div>
          <div
            class="dark-card-source"
            data-page-id="page-03"
            data-block="深色市场卡"
            data-field="market_battleground.market_card.source"
          >
            {{ market.market_card.source }}
          </div>

          <div class="p03-stat-grid">
            <div v-for="(stat, idx) in market.market_card.stats" :key="idx" class="p03-stat-item">
              <div class="p03-stat-line">
                <span
                  class="stat-num"
                  data-page-id="page-03"
                  data-block="深色市场卡"
                  :data-field="`market_battleground.market_card.stats[${idx}].value`"
                >
                  {{ stat.value }}
                </span>
                <span
                  class="stat-unit"
                  data-page-id="page-03"
                  data-block="深色市场卡"
                  :data-field="`market_battleground.market_card.stats[${idx}].unit`"
                >
                  {{ stat.unit }}
                </span>
              </div>
              <div
                class="stat-label"
                data-page-id="page-03"
                data-block="深色市场卡"
                :data-field="`market_battleground.market_card.stats[${idx}].label`"
              >
                {{ stat.label }}
              </div>
            </div>
          </div>

          <div class="dark-card-divider"></div>

          <div class="platform-row">
            <span
              class="plat-tag mono"
              data-page-id="page-03"
              data-block="深色市场卡"
              data-field="market_battleground.market_card.platform_label"
            >
              {{ market.market_card.platform_label }}
            </span>
            <span v-for="(platform, idx) in market.market_card.platforms" :key="idx">
              <span
                class="plat-name"
                data-page-id="page-03"
                data-block="深色市场卡"
                :data-field="`market_battleground.market_card.platforms[${idx}].name`"
              >
                {{ platform.name }}
              </span>
              <span
                class="plat-val"
                data-page-id="page-03"
                data-block="深色市场卡"
                :data-field="`market_battleground.market_card.platforms[${idx}].value`"
              >
                {{ platform.value }}
              </span>
            </span>
            <span
              class="plat-more"
              data-page-id="page-03"
              data-block="深色市场卡"
              data-field="market_battleground.market_card.platform_suffix"
            >
              {{ market.market_card.platform_suffix }}
            </span>
          </div>
        </div>

        <CalculationCardView
          card="national_card"
          block="全国推导卡"
          :data="market.national_card"
        />

        <div
          class="p03-bridge"
          data-page-id="page-03"
          data-block="过渡"
          data-field="market_battleground.bridge_text"
        >
          {{ market.bridge_text }}
        </div>

        <CalculationCardView
          card="regional_card"
          block="区域推导卡"
          accent
          :data="market.regional_card"
        />

        <div class="p03-narrative">
          <div
            class="p03-narrative-intro"
            data-page-id="page-03"
            data-block="底部叙事"
            data-field="market_battleground.narrative.intro"
          >
            {{ market.narrative.intro }}
          </div>

          <div class="p03-question-row">
            <span
              v-for="(question, idx) in market.narrative.questions"
              :key="idx"
              class="question-bubble"
              data-page-id="page-03"
              data-block="底部叙事"
              :data-field="`market_battleground.narrative.questions[${idx}]`"
            >
              {{ question }}
            </span>
          </div>

          <div
            class="p03-narrative-line"
            data-page-id="page-03"
            data-block="底部叙事"
            data-field="market_battleground.narrative.conclusion"
          >
            {{ market.narrative.conclusion }}
          </div>
          <div class="p03-narrative-line">
            <span
              class="arrow-marker"
              data-page-id="page-03"
              data-block="底部叙事"
              data-field="market_battleground.narrative.brand_line_prefix"
            >
              {{ market.narrative.brand_line_prefix }}
            </span>
            <strong
              data-page-id="page-03"
              data-block="底部叙事"
              data-field="market_battleground.narrative.brand_name"
            >
              {{ market.narrative.brand_name }}
            </strong>
            <span
              data-page-id="page-03"
              data-block="底部叙事"
              data-field="market_battleground.narrative.brand_line_suffix"
            >
              {{ market.narrative.brand_line_suffix }}
            </span>
          </div>
        </div>
      </div>

      <div
        class="p03-footnote"
        data-print-footer="true"
        data-page-id="page-03"
        data-block="脚注"
        data-field="market_battleground.footnote"
      >
        {{ market.footnote }}
      </div>

      <div
        class="page-footer-brand p03-footer-brand"
        data-page-id="page-03"
        data-block="脚注"
        data-field="market_battleground.footer_brand"
      >
        {{ market.footer_brand }}
      </div>
      <div class="page-label">03</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, defineComponent, h } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'
import type { CalculationCard } from '@/types/presale/editable'

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)
const market = computed(() => mergedView.value.market_battleground)

const CalculationCardView = defineComponent({
  name: 'CalculationCardView',
  props: {
    data: {
      type: Object as () => CalculationCard,
      required: true
    },
    card: {
      type: String,
      required: true
    },
    block: {
      type: String,
      required: true
    },
    accent: {
      type: Boolean,
      default: false
    }
  },
  setup(props) {
    return () =>
      h('div', { class: ['p03-calc-card', { 'has-accent': props.accent }] }, [
        h('div', { class: 'p03-calc-left' }, [
          h('div', {
            class: 'calc-card-label mono',
            'data-page-id': 'page-03',
            'data-block': props.block,
            'data-field': `market_battleground.${props.card}.label`
          }, props.data.label),
          h('div', { class: 'p03-calc-number' }, [
            props.data.value_prefix
              ? h('span', {
                  class: ['calc-big-num', props.accent ? 'orange small-prefix' : 'blue'],
                  'data-page-id': 'page-03',
                  'data-block': props.block,
                  'data-field': `market_battleground.${props.card}.value_prefix`
                }, props.data.value_prefix)
              : null,
            h('span', {
              class: ['calc-big-num main-num', props.accent ? 'orange' : 'blue'],
              'data-page-id': 'page-03',
              'data-block': props.block,
              'data-field': `market_battleground.${props.card}.value`
            }, props.data.value),
            h('span', {
              class: ['calc-big-num unit', props.accent ? 'orange' : 'blue'],
              'data-page-id': 'page-03',
              'data-block': props.block,
              'data-field': `market_battleground.${props.card}.unit`
            }, props.data.unit)
          ]),
          h('div', {
            class: 'calc-big-sub',
            'data-page-id': 'page-03',
            'data-block': props.block,
            'data-field': `market_battleground.${props.card}.subtitle`
          }, props.data.subtitle)
        ]),
        h('div', { class: 'calc-divider' }),
        h('div', { class: 'p03-calc-right' }, [
          h('div', {
            class: 'calc-card-label mono',
            'data-page-id': 'page-03',
            'data-block': props.block,
            'data-field': `market_battleground.${props.card}.calculation_label`
          }, props.data.calculation_label),
          h('div', { class: 'calc-table' }, props.data.rows.map((row, idx) =>
            h('div', { class: ['calc-row', { total: row.is_total, orange: props.accent && row.is_total }] }, [
              h('div', {
                class: 'k',
                'data-page-id': 'page-03',
                'data-block': props.block,
                'data-field': `market_battleground.${props.card}.rows[${idx}].label`
              }, row.label),
              h('div', {
                class: 'v',
                'data-page-id': 'page-03',
                'data-block': props.block,
                'data-field': `market_battleground.${props.card}.rows[${idx}].value`
              }, row.value)
            ])
          ))
        ])
      ])
  }
})
</script>

<style>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p03-topbar {
  align-items: baseline;
}

.topbar-left {
  display: flex;
  align-items: baseline;
  gap: 16px;
}

.topbar-num {
  color: #0b1426;
  font-family: 'Playfair Display', 'Noto Serif SC', serif;
  font-size: 18px;
  font-style: italic;
  font-weight: 700;
  letter-spacing: 0;
}

.p03-content-stage {
  width: calc(100% / 0.88);
  transform: scale(0.88);
  transform-origin: top left;
}

.p03-title-block {
  margin: 56px 0 28px;
}

.p03-title {
  max-height: 82px;
  margin: 0;
  overflow: hidden;
  color: #0b1426;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1.35;
}

.p03-kicker {
  max-height: 18px;
  margin-top: 10px;
  overflow: hidden;
  color: #6b6456;
  font-size: 11px;
  letter-spacing: 4px;
  text-transform: uppercase;
}

.p03-dark-card {
  position: relative;
  padding: 28px 32px;
  overflow: hidden;
  border-radius: 2px;
  background: linear-gradient(135deg, #0b1426 0%, #16264a 100%);
  color: #fefcf7;
}

.dark-card-label {
  max-width: 360px;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.55);
  font-size: 10px;
  letter-spacing: 3px;
  text-transform: uppercase;
  white-space: nowrap;
}

.dark-card-source {
  position: absolute;
  top: 28px;
  right: 32px;
  max-width: 210px;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.55);
  font-size: 11px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.p03-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-top: 22px;
}

.p03-stat-line {
  height: 48px;
  overflow: hidden;
}

.stat-num {
  color: #d97706;
  font-family: 'Playfair Display', 'Noto Serif SC', serif;
  font-size: 44px;
  font-weight: 900;
  letter-spacing: 0;
  line-height: 1;
}

.stat-unit {
  margin-left: 2px;
  color: #d97706;
  font-size: 16px;
  font-weight: 500;
}

.stat-label {
  height: 18px;
  margin-top: 10px;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.7);
  font-size: 12px;
  letter-spacing: 0.5px;
}

.dark-card-divider {
  height: 1px;
  margin: 22px 0 18px;
  background: rgba(255, 255, 255, 0.12);
}

.platform-row {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  align-items: baseline;
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
}

.plat-tag {
  color: rgba(255, 255, 255, 0.55);
  font-size: 10px;
  letter-spacing: 2px;
  text-transform: uppercase;
}

.plat-name {
  color: rgba(255, 255, 255, 0.92);
}

.plat-val {
  margin-left: 4px;
  color: #d97706;
  font-weight: 500;
}

.plat-more {
  color: rgba(255, 255, 255, 0.7);
}

.p03-calc-card {
  position: relative;
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 36px;
  margin-top: 28px;
  padding: 28px 32px;
  background: #f7f3ea;
}

.p03-calc-card.has-accent {
  margin-top: 0;
  padding-left: 28px;
  border-left: 4px solid #d97706;
}

.calc-card-label {
  height: 32px;
  margin-bottom: 9px;
  overflow: hidden;
  color: #6b6456;
  font-size: 10px;
  letter-spacing: 2.5px;
  line-height: 1.45;
  text-transform: uppercase;
  overflow-wrap: anywhere;
}

.p03-calc-number {
  height: 70px;
  overflow: hidden;
}

.calc-big-num {
  font-family: 'Playfair Display', 'Noto Serif SC', serif;
  font-weight: 900;
  letter-spacing: 0;
  line-height: 0.95;
}

.calc-big-num.blue {
  color: #1e3a8a;
}

.calc-big-num.orange {
  color: #d97706;
}

.calc-big-num.main-num {
  font-size: 40px;
}

.calc-big-num.unit {
  margin-left: 2px;
  font-size: 26px;
  vertical-align: top;
}

.small-prefix {
  margin-right: 6px;
  font-size: 26px;
}

.calc-big-sub {
  max-height: 36px;
  margin-top: 14px;
  overflow: hidden;
  color: #6b6456;
  font-size: 12px;
  line-height: 1.5;
}

.calc-divider {
  position: absolute;
  top: 28px;
  bottom: 28px;
  left: 272px;
  width: 1px;
  background: #c8bfa8;
}

.calc-table {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.calc-row {
  display: grid;
  /* 数值列固定起点，长行业说明只在左列换行，不会挤压或贴近数值。 */
  grid-template-columns: minmax(0, 180px) minmax(150px, 1fr);
  column-gap: 28px;
  align-items: baseline;
  color: #1a2942;
  font-size: 13px;
}

.calc-row .k,
.calc-row .v {
  min-height: 18px;
  overflow-wrap: anywhere;
}

.calc-row .k {
  max-height: 38px;
  overflow: hidden;
  line-height: 1.45;
}

.calc-row .v {
  white-space: nowrap;
}

.calc-row.total {
  padding-top: 10px;
  margin-top: 4px;
  border-top: 1px solid #c8bfa8;
}

.calc-row.total .v {
  color: #1e3a8a;
}

.calc-row.total.orange .v {
  color: #d97706;
}

.p03-bridge {
  height: 18px;
  margin: 20px 0;
  overflow: hidden;
  color: #6b6456;
  font-size: 12px;
  letter-spacing: 2px;
  text-align: center;
}

.p03-narrative {
  padding: 8px 0 8px 20px;
  margin-top: 18px;
  border-left: 3px solid #d97706;
}

.p03-narrative-intro {
  max-height: 22px;
  margin-bottom: 14px;
  overflow: hidden;
  color: #0b1426;
  font-size: 14px;
}

.p03-question-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.question-bubble {
  max-width: 100%;
  padding: 9px 16px;
  overflow: hidden;
  border: 1px solid #c8bfa8;
  background: #fefcf7;
  color: #1a2942;
  font-size: 12.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.p03-narrative-line {
  max-height: 24px;
  overflow: hidden;
  color: #1a2942;
  font-size: 13px;
  line-height: 1.8;
}

.p03-narrative-line strong {
  color: #0b1426;
}

.arrow-marker {
  margin-right: 4px;
  color: #d97706;
  font-weight: 700;
}

.p03-footnote {
  position: absolute;
  right: 64px;
  bottom: 40px;
  left: 64px;
  max-height: 28px;
  overflow: hidden;
  color: #6b6456;
  font-size: 9px;
  line-height: 1.4;
}

.p03-footer-brand {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

</style>
