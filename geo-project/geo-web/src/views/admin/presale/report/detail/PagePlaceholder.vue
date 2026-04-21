<template>
  <!--
    section 外层承载锚点 id(Sidebar 滚动定位用);
    .page 内层复刻原型的 A4 版式维度。
    β/γ 阶段每一页会被替换为 PageXX.vue,PageXX.vue 的根节点保持同样的
    <section :id="..."><div class="page [cover]">...</div></section> 结构。
  -->
  <section :id="anchorId" class="page-anchor">
    <div :class="['page', { cover }]">
      <div class="page-marker">PAGE {{ pageNum }} / {{ pageTitle }}</div>
      <div class="placeholder-body">
        <div class="placeholder-hint">
          <div class="placeholder-num">{{ pageNum }}</div>
          <div class="placeholder-title">{{ pageTitle }}</div>
          <div class="placeholder-note">
            Page SFC 将在 P1·F·1·b·2·{{ pageNum <= '09' ? 'β' : 'γ' }} 阶段实现
          </div>
        </div>
      </div>
      <div class="page-label">{{ pageNum }} / 18</div>
    </div>
  </section>
</template>

<script setup lang="ts">
interface Props {
  anchorId: string
  pageNum: string
  pageTitle: string
  cover?: boolean
}

withDefaults(defineProps<Props>(), {
  cover: false
})
</script>

<style scoped>
/*
 * 下述样式是原型的最小必要子集,保证占位页尺寸/配色/字体 ≈ 真实 A4 页。
 * β/γ 替换为真实 PageXX.vue 后,这份 scoped 样式将随占位组件一起废弃,
 * 真实 Page SFC 各自拥有 scoped 样式(或共享一份 detail-global.css,
 * 到时再决定是否抽公共层)。
 *
 * 注:原型用了 --ink / --paper / --accent 等 CSS 变量。α·2 占位阶段
 * 不引入全局变量(scoped 里放 :root 会被 Vue 作用域污染),硬编码色值;
 * β/γ 做真实 Page SFC 时,建议抽 geo-web/src/assets/presale/report-vars.css
 * 作为全局引入,再让各 Page SFC 走 var(--ink) 风格。
 */

.page-anchor {
  display: flex;
  justify-content: center;
}

.page {
  width: 794px;
  min-height: 1123px;
  background: #fefcf7;
  margin: 0 auto 32px auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.35);
  position: relative;
  overflow: hidden;
  padding: 60px 64px;
  color: #0b1426;
}
.page.cover {
  background: linear-gradient(135deg, #0b1426 0%, #1a2942 100%);
  color: #fefcf7;
}

.page-marker {
  display: inline-block;
  background: rgba(45, 42, 38, 0.95);
  color: #d4cfc2;
  padding: 6px 12px;
  font-size: 10px;
  letter-spacing: 2px;
  text-transform: uppercase;
  border-radius: 4px;
  font-family: 'JetBrains Mono', monospace;
  margin-bottom: 24px;
}
.page.cover .page-marker {
  background: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.8);
}

.placeholder-body {
  min-height: 900px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-hint {
  text-align: center;
  opacity: 0.6;
}
.placeholder-num {
  font-family: 'Playfair Display', serif;
  font-size: 120px;
  font-weight: 900;
  line-height: 1;
  color: #d97706;
}
.page.cover .placeholder-num {
  color: rgba(255, 255, 255, 0.3);
}
.placeholder-title {
  margin-top: 12px;
  font-size: 22px;
  font-weight: 500;
  letter-spacing: 2px;
}
.placeholder-note {
  margin-top: 16px;
  font-size: 11px;
  font-family: 'JetBrains Mono', monospace;
  color: #6b6456;
  letter-spacing: 1px;
}
.page.cover .placeholder-note {
  color: rgba(255, 255, 255, 0.4);
}

.page-label {
  position: absolute;
  bottom: 24px;
  right: 32px;
  font-size: 10px;
  color: #6b6456;
  font-family: 'JetBrains Mono', monospace;
  letter-spacing: 2px;
}
.page.cover .page-label {
  color: rgba(255, 255, 255, 0.4);
}
</style>
