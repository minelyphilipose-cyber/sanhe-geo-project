<template>
  <main class="mobile-dashboard-shell">
    <template v-if="loading">
      <div class="mobile-dashboard-loading">
        <van-skeleton title :row="8" />
      </div>
    </template>
    <template v-else-if="errorMessage">
      <div class="mobile-dashboard-error">
        <MobileIcon name="info" />
        <h1>链接不可用</h1>
        <p>{{ errorMessage }}</p>
      </div>
    </template>
    <template v-else>
      <AppHeader
        :brand-name="mobileDashboardStore.brandName"
        :page-name="currentPageName"
        :filter-label="currentFilterLabel"
        :icon-name="currentHeaderConfig.icon"
        :icon-size="currentHeaderConfig.iconSize"
        :title-tone="currentHeaderConfig.titleTone"
        :subtitle-tone="currentHeaderConfig.subtitleTone"
        :data-updated-at="currentDataUpdatedAt"
      />
      <aside
        v-if="wechatShare.guideVisible.value"
        class="wechat-share-guide"
        role="status"
        aria-live="polite"
      >
        <div>
          <strong>微信分享已就绪</strong>
          <p>点击右上角“···”，选择“发送给朋友”。</p>
        </div>
        <button
          type="button"
          aria-label="关闭微信分享提示"
          @click="wechatShare.dismissGuide"
        >
          ×
        </button>
      </aside>
      <section
        ref="contentRef"
        class="mobile-dashboard-content"
        @touchstart.passive="handleTouchStart"
        @touchmove="handleTouchMove"
        @touchend.passive="handleTouchEnd"
        @touchcancel.passive="resetTouchState"
      >
        <router-view />
      </section>
      <BottomTabbar />
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '@/components/mobile-dashboard/AppHeader.vue'
import BottomTabbar from '@/components/mobile-dashboard/BottomTabbar.vue'
import MobileIcon from '@/components/mobile-dashboard/MobileIcon.vue'
import { useMobileDashboardStore } from '@/stores/mobileDashboard'
import { useWechatShare } from '@/composables/useWechatShare'
import { buildMobileDashboardDocumentTitle } from '@/utils/wechatShare'

const route = useRoute()
const router = useRouter()
const mobileDashboardStore = useMobileDashboardStore()
const loading = ref(true)
const errorMessage = ref('')
const contentRef = ref<HTMLElement | null>(null)

type HeaderTone = 'default' | 'primary'
type SubtitleTone = 'body' | 'label' | 'micro'

const pageConfig: Record<string, {
  name: string
  filter?: string
  icon: string
  iconSize: number
  titleTone: HeaderTone
  subtitleTone: SubtitleTone
  showDataCutoff?: boolean
}> = {
  home: { name: '客户总览', icon: 'dashboard', iconSize: 24, titleTone: 'primary', subtitleTone: 'label', showDataCutoff: true },
  monitor: { name: 'AI监测', icon: 'dashboard', iconSize: 28, titleTone: 'primary', subtitleTone: 'label', showDataCutoff: true },
  content: { name: '内容交付', icon: 'bubble', iconSize: 28, titleTone: 'primary', subtitleTone: 'label', showDataCutoff: true },
  question: { name: '问答详情', icon: 'dashboard', iconSize: 28, titleTone: 'primary', subtitleTone: 'label' },
}

const currentPageKey = computed(() => {
  if (route.name === 'MobileDashboardQuestionDetail') return 'question'
  if (route.name === 'MobileDashboardMonitor') return 'monitor'
  if (route.name === 'MobileDashboardContent') return 'content'
  return 'home'
})
const currentPageName = computed(() => {
  return pageConfig[currentPageKey.value]?.name || '客户总览'
})
const currentFilterLabel = computed(() => pageConfig[currentPageKey.value]?.filter)
const currentHeaderConfig = computed(() => pageConfig[currentPageKey.value] || pageConfig.home)
const currentDataUpdatedAt = computed(() => (
  currentHeaderConfig.value.showDataCutoff ? mobileDashboardStore.measurementUpdatedAt : ''
))

const swipeTabRouteNames = ['MobileDashboardHome', 'MobileDashboardMonitor', 'MobileDashboardContent']
const swipeGuardThreshold = 14
const swipeThreshold = 56
let touchState: {
  startX: number
  startY: number
  currentX: number
  currentY: number
  scroller: HTMLElement | null
} | null = null

const shareCode = computed(() => String(route.params.shareCode || ''))
const wechatShare = useWechatShare({
  sessionToken: () => mobileDashboardStore.sessionToken,
  shareCode: () => shareCode.value,
})
const isSwipeTabPage = computed(() => swipeTabRouteNames.includes(String(route.name || '')))

function syncDocumentTitle() {
  const pageTitle = typeof route.meta.title === 'string' ? route.meta.title : undefined
  document.title = buildMobileDashboardDocumentTitle(
    pageTitle,
    mobileDashboardStore.context?.brandName,
  )
}

function findHorizontalScroller(target: EventTarget | null) {
  const root = contentRef.value
  if (!root || !(target instanceof HTMLElement)) return null

  let element: HTMLElement | null = target
  while (element && root.contains(element)) {
    const style = window.getComputedStyle(element)
    const canScrollX = element.scrollWidth > element.clientWidth + 4
      && ['auto', 'scroll'].includes(style.overflowX)
    if (canScrollX) return element
    if (element === root) break
    element = element.parentElement
  }
  return null
}

function canScrollerConsumeSwipe(scroller: HTMLElement | null, deltaX: number) {
  if (!scroller) return false
  if (deltaX < 0) {
    return scroller.scrollLeft + scroller.clientWidth < scroller.scrollWidth - 2
  }
  return scroller.scrollLeft > 2
}

function handleTouchStart(event: TouchEvent) {
  if (!isSwipeTabPage.value) {
    resetTouchState()
    return
  }
  if (event.touches.length !== 1) {
    resetTouchState()
    return
  }
  const touch = event.touches[0]
  touchState = {
    startX: touch.clientX,
    startY: touch.clientY,
    currentX: touch.clientX,
    currentY: touch.clientY,
    scroller: findHorizontalScroller(event.target),
  }
}

function handleTouchMove(event: TouchEvent) {
  if (!touchState || event.touches.length !== 1) return
  const touch = event.touches[0]
  touchState.currentX = touch.clientX
  touchState.currentY = touch.clientY
  const deltaX = touchState.currentX - touchState.startX
  const deltaY = touchState.currentY - touchState.startY
  const isHorizontalIntent = Math.abs(deltaX) >= swipeGuardThreshold && Math.abs(deltaX) > Math.abs(deltaY)

  if (
    isSwipeTabPage.value
    && isHorizontalIntent
    && !canScrollerConsumeSwipe(touchState.scroller, deltaX)
  ) {
    event.preventDefault()
  }
}

function resetTouchState() {
  touchState = null
}

function handleTouchEnd() {
  if (!touchState) return

  const deltaX = touchState.currentX - touchState.startX
  const deltaY = touchState.currentY - touchState.startY
  const isHorizontalSwipe = Math.abs(deltaX) >= swipeThreshold && Math.abs(deltaX) > Math.abs(deltaY) * 1.2
  const currentIndex = swipeTabRouteNames.indexOf(String(route.name || ''))

  if (
    isSwipeTabPage.value
    && isHorizontalSwipe
    && currentIndex >= 0
    && !canScrollerConsumeSwipe(touchState.scroller, deltaX)
  ) {
    const targetIndex = deltaX < 0 ? currentIndex + 1 : currentIndex - 1
    const targetName = swipeTabRouteNames[targetIndex]
    if (targetName && shareCode.value) {
      router.replace({ name: targetName, params: { shareCode: shareCode.value } })
    }
  }

  resetTouchState()
}

onMounted(async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    await mobileDashboardStore.initialize(shareCode.value)
    syncDocumentTitle()
    void wechatShare.configure()
  } catch (error: any) {
    mobileDashboardStore.clearAll()
    errorMessage.value = error?.message || '分享链接已失效或已过期，请联系交付顾问重新获取。'
  } finally {
    loading.value = false
  }
})

watch(
  () => route.fullPath,
  () => {
    if (!loading.value && !errorMessage.value) {
      syncDocumentTitle()
      void wechatShare.configure()
    }
  },
  { flush: 'post' },
)
</script>

<style scoped>
.mobile-dashboard-shell {
  --mobile-primary: #006D44;
  --mobile-primary-strong: #07A66B;
  --mobile-primary-soft: #e6f7ef;
  --mobile-text: #131b2e;
  --mobile-muted: #52625C;
  --mobile-subtle: #f8fafc;
  --mobile-border: #eef0f2;
  --mobile-card-shadow: 0 4px 20px rgba(15, 23, 42, 0.04);
  --mobile-font: 'Plus Jakarta Sans', 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, sans-serif;
  --mobile-text-2xs: 10px;
  --mobile-text-xs: 12px;
  --mobile-text-sm: 12px;
  --mobile-text-md: 14px;
  --mobile-text-lg: 16px;
  --mobile-title: 16px;
  --mobile-title-md: 18px;
  --mobile-title-lg: 20px;
  --mobile-title-xl: 22px;
  --mobile-metric: 18px;
  --mobile-metric-lg: 36px;
  --mobile-leading-label-sm: 14px;
  --mobile-leading-label: 16px;
  --mobile-leading-md: 20px;
  --mobile-leading-lg: 22px;
  --mobile-leading-title: 24px;
  --mobile-leading-title-lg: 26px;
  --mobile-leading-display: 40px;

  min-height: 100vh;
  width: 100%;
  max-width: 100vw;
  background: #fff;
  color: var(--mobile-text);
  font-family: var(--mobile-font);
  font-size: var(--mobile-text-md);
  line-height: var(--mobile-leading-md);
  font-weight: 400;
  overflow-x: hidden;
  scrollbar-width: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
}

.mobile-dashboard-shell,
.mobile-dashboard-shell * {
  box-sizing: border-box;
}

.mobile-dashboard-shell::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.mobile-dashboard-loading {
  padding: 28px 16px;
}

.mobile-dashboard-content {
  min-height: calc(100dvh - 72px);
  width: 100%;
  max-width: min(760px, 100vw);
  margin: 0 auto;
  padding: 14px 16px calc(88px + env(safe-area-inset-bottom));
  background: #fff;
  overflow-x: hidden;
  scrollbar-width: none;
}

.mobile-dashboard-content::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.wechat-share-guide {
  position: fixed;
  z-index: 80;
  top: max(12px, env(safe-area-inset-top));
  right: 12px;
  left: 12px;
  max-width: 480px;
  margin-left: auto;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 14px 14px 16px;
  border: 1px solid rgba(7, 166, 107, 0.22);
  border-radius: 14px;
  background: rgba(247, 255, 251, 0.97);
  color: var(--mobile-text);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.14);
  backdrop-filter: blur(12px);
}

.wechat-share-guide::before {
  position: absolute;
  top: -8px;
  right: 20px;
  width: 14px;
  height: 14px;
  border-top: 1px solid rgba(7, 166, 107, 0.22);
  border-left: 1px solid rgba(7, 166, 107, 0.22);
  background: #f7fffb;
  content: '';
  transform: rotate(45deg);
}

.wechat-share-guide strong {
  display: block;
  color: var(--mobile-primary);
  font-size: 14px;
  line-height: 20px;
}

.wechat-share-guide p {
  margin: 2px 0 0;
  color: var(--mobile-muted);
  font-size: 12px;
  line-height: 18px;
}

.wechat-share-guide button {
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  margin: -8px -8px -8px 0;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--mobile-muted);
  font: inherit;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  touch-action: manipulation;
}

.wechat-share-guide button:focus-visible {
  outline: 2px solid var(--mobile-primary-strong);
  outline-offset: 2px;
}

.mobile-dashboard-error {
  min-height: 100vh;
  display: grid;
  place-content: center;
  gap: 10px;
  padding: 24px;
  text-align: center;
  background: #fff;
}

.mobile-dashboard-error .mobile-icon {
  justify-self: center;
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: #fff7ed;
  color: #f97316;
  font-size: 26px;
}

.mobile-dashboard-error h1 {
  margin: 0;
  color: var(--mobile-text);
  font-size: 20px;
  font-weight: 700;
  line-height: var(--mobile-leading-title-lg, 26px);
}

.mobile-dashboard-error p {
  max-width: 280px;
  margin: 0;
  color: var(--mobile-muted);
  font-size: 14px;
  line-height: var(--mobile-leading-md, 20px);
}
</style>
