<template>
  <main class="wechat-page">
    <section class="wechat-header">
      <div class="wechat-header__visual">
        <img v-if="state.data?.visualUrl" :src="state.data.visualUrl" alt="" />
        <span v-else>{{ brandInitial }}</span>
      </div>
      <div class="wechat-header__body">
        <h1>往期文章</h1>
        <p v-if="state.data?.brandName || state.data?.accountName">
          {{ state.data?.brandName || state.data?.accountName }}
        </p>
      </div>
    </section>

    <section v-if="state.data?.publicPhone || state.data?.publicAddress" class="contact-strip">
      <a v-if="state.data?.publicPhone" class="contact-item" :href="`tel:${state.data.publicPhone}`">
        <span class="contact-item__main">
          <Phone class="contact-item__icon" />
          <span>{{ state.data.publicPhone }}</span>
        </span>
        <span class="contact-item__action">拨打</span>
      </a>
      <div v-if="state.data?.publicAddress" class="contact-item">
        <span class="contact-item__main">
          <Location class="contact-item__icon" />
          <span>{{ state.data.publicAddress }}</span>
        </span>
      </div>
    </section>

    <section class="article-section">
      <div class="section-heading">
        <div>
          <h2>往期文章</h2>
          <p v-if="state.data && state.data.total > 0">已收录 {{ state.data.total }} 篇内容</p>
        </div>
      </div>

      <div v-if="state.loading && !state.data" class="state-text">
        <Loading class="state-icon is-spin" />
        <span>加载中...</span>
      </div>
      <div v-else-if="state.error" class="state-text">
        <strong>页面暂时无法打开</strong>
        <span>请稍后重试，或通过上方电话联系商家</span>
      </div>
      <div v-else-if="!state.data?.articles?.length" class="state-text">
        <strong>暂无往期文章</strong>
        <span>后续发布的品牌内容会在这里展示</span>
      </div>
      <template v-else>
        <a
          v-for="article in state.data?.articles || []"
          :key="article.id"
          class="article-item"
          :class="{ 'article-item--text-only': !article.coverUrl }"
          :href="article.articleUrl"
          target="_blank"
          rel="noopener"
        >
          <div class="article-item__body">
            <h2>{{ article.title }}</h2>
            <p v-if="article.digest">{{ article.digest }}</p>
            <time v-if="article.publishedAt">{{ formatDate(article.publishedAt) }}</time>
          </div>
          <img v-if="article.coverUrl" :src="article.coverUrl" alt="" loading="lazy" />
        </a>

        <button v-if="hasMore" class="load-more" type="button" :disabled="state.loadingMore" @click="loadMore">
          <Loading v-if="state.loadingMore" class="load-more__icon is-spin" />
          <span>{{ state.loadingMore ? '加载中' : '加载更多' }}</span>
        </button>
        <div v-else class="list-end">已展示全部文章</div>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive } from 'vue'
import { useRoute } from 'vue-router'
import { Loading, Location, Phone } from '@element-plus/icons-vue'
import { getWechatPublicArticles, type WechatPublicArticleList } from '@/api/wechatPublic'

const route = useRoute()
const publicSlug = computed(() => String(route.params.publicSlug || ''))
const PAGE_SIZE = 20

const state = reactive<{
  loading: boolean
  loadingMore: boolean
  error: boolean
  data: WechatPublicArticleList | null
}>({
  loading: true,
  loadingMore: false,
  error: false,
  data: null,
})

const brandInitial = computed(() => {
  const name = state.data?.brandName || state.data?.accountName || '往'
  return name.trim().slice(0, 1)
})

const hasMore = computed(() => {
  if (!state.data) return false
  return state.data.articles.length < state.data.total
})

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

async function loadPage(page: number) {
  const response = await getWechatPublicArticles(publicSlug.value, { page, size: PAGE_SIZE })
  return response.data.data
}

async function loadMore() {
  if (!state.data || state.loadingMore || !hasMore.value) return
  state.loadingMore = true
  try {
    const nextPage = state.data.page + 1
    const data = await loadPage(nextPage)
    state.data = {
      ...data,
      articles: [...state.data.articles, ...data.articles],
    }
  } finally {
    state.loadingMore = false
  }
}

onMounted(async () => {
  state.loading = true
  state.error = false
  try {
    state.data = await loadPage(1)
  } catch {
    state.error = true
  } finally {
    state.loading = false
  }
})
</script>

<style scoped>
.wechat-page {
  min-height: 100vh;
  background:
    linear-gradient(180deg, #edf7f4 0, rgba(237, 247, 244, 0) 188px),
    #f6f7f9;
  color: #101828;
  padding: 18px 14px calc(40px + env(safe-area-inset-bottom));
}

.wechat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 720px;
  margin: 0 auto;
  padding: 16px 2px 14px;
}

.wechat-header__visual {
  width: 58px;
  height: 58px;
  border-radius: 8px;
  overflow: hidden;
  background: linear-gradient(135deg, #047857, #0f766e);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 24px;
  font-weight: 700;
  flex: 0 0 auto;
  box-shadow: 0 10px 24px rgba(15, 118, 110, 0.18);
}

.wechat-header__visual img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.wechat-header__body {
  min-width: 0;
}

.wechat-header h1 {
  margin: 0;
  font-size: 21px;
  line-height: 1.25;
  font-weight: 750;
  letter-spacing: 0;
}

.wechat-header p {
  margin: 5px 0 0;
  color: #667085;
  font-size: 14px;
  line-height: 1.4;
}

.contact-strip {
  max-width: 720px;
  margin: 0 auto 16px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(209, 250, 229, 0.95);
  border-radius: 8px;
  display: grid;
  gap: 6px;
  box-shadow: 0 8px 22px rgba(16, 24, 40, 0.04);
}

.contact-item {
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #344054;
  font-size: 14px;
  line-height: 1.45;
  text-decoration: none;
}

.contact-item__main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 9px;
}

.contact-item__icon {
  width: 16px;
  height: 16px;
  color: #059669;
  flex: 0 0 auto;
}

.contact-item__action {
  flex: 0 0 auto;
  color: #047857;
  font-size: 13px;
  font-weight: 700;
}

.article-section {
  max-width: 720px;
  margin: 0 auto;
  display: grid;
  gap: 10px;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  min-height: 38px;
  padding: 0 2px;
}

.section-heading h2 {
  margin: 0;
  color: #101828;
  font-size: 17px;
  line-height: 1.3;
  font-weight: 750;
  letter-spacing: 0;
}

.section-heading p {
  margin: 4px 0 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.3;
}

.article-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 104px;
  gap: 12px;
  padding: 14px;
  background: #fff;
  border: 1px solid #eaecf0;
  border-radius: 8px;
  text-decoration: none;
  color: inherit;
  box-shadow: 0 6px 18px rgba(16, 24, 40, 0.035);
  transition: border-color 160ms ease, transform 160ms ease, box-shadow 160ms ease;
}

.article-item:active {
  transform: scale(0.992);
  border-color: #c7eadc;
  box-shadow: 0 4px 12px rgba(16, 24, 40, 0.045);
}

.article-item--text-only {
  grid-template-columns: minmax(0, 1fr);
}

.article-item__body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.article-item h2 {
  margin: 0;
  color: #101828;
  font-size: 16px;
  line-height: 1.45;
  font-weight: 700;
  letter-spacing: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-item p {
  margin: 8px 0 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-item time {
  display: block;
  margin-top: 10px;
  color: #98a2b3;
  font-size: 12px;
  line-height: 1.2;
}

.article-item img {
  width: 104px;
  height: 78px;
  border-radius: 6px;
  object-fit: cover;
  background: #eef2f6;
}

.state-text {
  min-height: 168px;
  padding: 48px 16px;
  text-align: center;
  color: #667085;
  background: #fff;
  border: 1px solid #eaecf0;
  border-radius: 8px;
  display: grid;
  place-items: center;
  gap: 8px;
}

.state-text strong {
  color: #344054;
  font-size: 16px;
  line-height: 1.4;
}

.state-text span {
  max-width: 240px;
  line-height: 1.55;
}

.state-icon,
.load-more__icon {
  width: 18px;
  height: 18px;
}

.load-more {
  min-height: 44px;
  border: 1px solid #d0d5dd;
  border-radius: 8px;
  background: #fff;
  color: #344054;
  font-size: 14px;
  font-weight: 650;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.load-more:disabled {
  color: #98a2b3;
}

.list-end {
  padding: 10px 0 2px;
  text-align: center;
  color: #98a2b3;
  font-size: 12px;
}

.is-spin {
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 420px) {
  .article-item {
    grid-template-columns: minmax(0, 1fr) 82px;
  }

  .article-item img {
    width: 82px;
    height: 64px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .article-item,
  .is-spin {
    transition: none;
    animation: none;
  }
}
</style>
